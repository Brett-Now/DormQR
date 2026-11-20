package app.dormqr.qr;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

/**
 * 低功耗预取服务。
 *
 * 设计要点：
 *  - 不做轮询。只在"该换码的时刻"（有效期 × 80%）唤醒一次。
 *  - 用 AlarmManager.setAndAllowWhileIdle 兜住 Doze。
 *  - 常驻通知显示二维码大图。
 *  - 任何失败都保留旧码。
 */
public class PrefetchService extends Service {

    private static final String TAG = "DormQrService";
    private static final long TICK_MS = 60_000L;
    private static final int ALARM_REQUEST = 2001;

    /** 供界面判断"后台保活"。 */
    public static volatile boolean RUNNING;

    private Handler handler;
    private QrSettings settings;
    private QrStore store;
    private volatile boolean refreshing;
    /** Android 12+ 后台启动前台服务可能被系统拒绝，拒绝后本实例不再干活。 */
    private boolean foregroundOk;

    /**
     * 亮屏/解锁时顺手检查一次：Doze 会把闹钟拉长到 9~15 分钟，用户拿起手机的瞬间补一次。
     * 熄屏对抗：动态注册。
     */
    private final BroadcastReceiver wristReceiver = new WristWakeReceiver();

    private final BroadcastReceiver screenOnReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            refreshIfDue(false);
        }
    };

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            refreshIfDue(false);
            if (handler != null) handler.postDelayed(this, TICK_MS);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        settings = new QrSettings(this);
        store = new QrStore(this);
        handler = new Handler(Looper.getMainLooper());

        try {
            startForeground(QrNotifier.NOTIFICATION_ID,
                    QrNotifier.build(this, currentContent(), statusLine()));
            foregroundOk = true;
            RUNNING = true;

            IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_SCREEN_ON);
            filter.addAction(Intent.ACTION_USER_PRESENT);
            registerReceiver(screenOnReceiver, filter);

            IntentFilter off = new IntentFilter(Intent.ACTION_SCREEN_OFF);
            registerReceiver(wristReceiver, off);
        } catch (Throwable t) {
            // ForegroundServiceStartNotAllowedException 等：安静退出，
            // 用户下次打开 App 时前台启动即可恢复
            Log.w(TAG, "无法进入前台服务，停止自身", t);
            foregroundOk = false;
            stopSelf();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!foregroundOk) {
            stopSelf();
            return START_NOT_STICKY;
        }

        // 每次唤醒都检查一次是否该换码
        boolean force = intent != null && "app.dormqr.qr.REFRESH_NOW".equals(intent.getAction());
        refreshIfDue(force);
        if (handler != null) {
            handler.removeCallbacks(tick);
            handler.postDelayed(tick, TICK_MS);
        }
        scheduleNextAlarm();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        RUNNING = false;
        if (handler != null) handler.removeCallbacksAndMessages(null);
        try {
            unregisterReceiver(screenOnReceiver);
        } catch (Exception ignored) {
            // 未注册成功时忽略
        }
        try {
            unregisterReceiver(wristReceiver);
        } catch (Exception ignored) {
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    /* ---------------- 核心逻辑 ---------------- */

    private String currentContent() {
        QrStore.Entry e = store.load();
        return e == null ? null : e.content;
    }

    private String statusLine() {
        QrStore.Entry e = store.load();
        if (e == null) return "尚无可用二维码";
        QrFreshness.Snapshot snap = QrFreshness.evaluate(
                e.retrievedAt, System.currentTimeMillis(),
                settings.ttlSeconds(), settings.refreshPercent());
        return QrFreshness.describe(snap);
    }

    /**
     * @param force true 表示不管新旧都换一张
     */
    void refreshIfDue(boolean force) {
        if (refreshing) return;

        QrStore.Entry e = store.load();
        long now = System.currentTimeMillis();

        if (!force && e != null) {
            QrFreshness.Snapshot snap = QrFreshness.evaluate(
                    e.retrievedAt, now, settings.ttlSeconds(), settings.refreshPercent());
            if (!QrFreshness.refreshNeeded(snap)) {
                // 先刷新通知文案（改变剩余时间）
                QrNotifier.show(this, e.content, QrFreshness.describe(snap));
                return;
            }
        }

        refreshing = true;
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    QrApi.Result r = QrApi.fetch(settings);
                    store.save(r.content, r.retrievedAt,
                            settings.demoMode() ? "demo" : "authorized");
                    QrCardWidget.updateAll(PrefetchService.this);
                    QrNotifier.show(PrefetchService.this, r.content,
                            "刚更新 · " + settings.ttlSeconds() / 60 + " 分钟内有效");
                    Log.i(TAG, "已换码，长度 " + r.content.length());
                } catch (Exception ex) {
                    // 保留旧码
                    QrStore.Entry old = store.load();
                    String text = old == null
                            ? "获取失败：" + ex.getMessage()
                            : "换码失败，继续使用上一次的码";
                    QrNotifier.show(PrefetchService.this,
                            old == null ? null : old.content, text);
                    Log.w(TAG, "换码失败", ex);
                } finally {
                    refreshing = false;
                    scheduleNextAlarm();
                }
            }
        }, "qr-fetch").start();
    }

    /* ---------------- 定时 ---------------- */

    /** 在"下一次该换码的时刻"排一个允许在 Doze 中触发的闹钟。 */
    private void scheduleNextAlarm() {
        AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        Intent i = new Intent(this, AlarmReceiver.class);
        PendingIntent pi = PendingIntent.getBroadcast(this, ALARM_REQUEST, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        long triggerAt;
        QrStore.Entry e = store.load();
        if (e == null) {
            triggerAt = System.currentTimeMillis() + 60_000L;
        } else {
            long refreshAt = e.retrievedAt + settings.refreshAtMillis();
            triggerAt = Math.max(refreshAt, System.currentTimeMillis() + 5_000L);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
            // 没有精确闹钟权限就退让，允许系统窗口内触发即可
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi);
        } else {
            am.set(AlarmManager.RTC_WAKEUP, triggerAt, pi);
        }
    }

    /* ---------------- 对外入口 ---------------- */

    public static void start(Context ctx) {
        Intent i = new Intent(ctx, PrefetchService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ctx.startForegroundService(i);
        } else {
            ctx.startService(i);
        }
    }

    public static void stop(Context ctx) {
        ctx.stopService(new Intent(ctx, PrefetchService.class));
        QrNotifier.cancel(ctx);
    }

    /** 立刻刷新并在通知里更新。 */
    public static void refreshNow(Context ctx) {
        Intent i = new Intent(ctx, PrefetchService.class).setAction("app.dormqr.qr.REFRESH_NOW");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ctx.startForegroundService(i);
        } else {
            ctx.startService(i);
        }
    }
}





