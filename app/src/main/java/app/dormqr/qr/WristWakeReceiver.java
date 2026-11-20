package app.dormqr.qr;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

/**
 * 翻腕熄屏对抗。
 *
 * 收到熄屏广播后立刻把二维码页顶回前台，
 *
 * 前提：
 *   "后台弹出界面" / "显示在其他应用上层" 权限
 *
 * 只在二维码页处于前台时对抗；用户主动退出应用后不再拉起。
 */
public class WristWakeReceiver extends BroadcastReceiver {

    private static final String TAG = "DormQrWake";

    /**
     * 只给一个很短的宽限期（1.5 秒）：
     * 熄屏时 onPause 与 SCREEN_OFF 广播的先后顺序不固定，幕。
     */
    private static final long GRACE_MS = 1_500L;

    public static volatile long lastShownAt;
    public static volatile boolean pageVisible;

    @Override
    public void onReceive(final Context context, Intent intent) {
        if (intent == null || !Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) return;

        final QrSettings settings = new QrSettings(context);
        if (!settings.keepAwakeOnWrist()) return;
        if (!settings.backgroundEnabled()) return;

        // 关键：应用不在前台就不点亮。pageVisible 只在 onResume→onPause 之间为真
        long now = System.currentTimeMillis();
        boolean pageInFront = pageVisible || (now - lastShownAt) < GRACE_MS;
        if (!pageInFront) {
            Log.i(TAG, "熄屏，但二维码页不在前台，不点亮");
            return;
        }

        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                try {
                    Intent open = new Intent(context, MainActivity.class)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                                    | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                                    | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    context.startActivity(open);
                    Log.i(TAG, "熄屏后重新拉起二维码页");
                } catch (Exception e) {
                    Log.w(TAG, "拉起失败（多半缺后台弹出界面权限）", e);
                }
            }
        }, 250L);
    }
}

