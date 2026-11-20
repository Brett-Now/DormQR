package app.dormqr.qr;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * 主界面：全屏二维码。
 *
 * 手机布局：顶部标题+设置，中间二维码卡片，底部状态胶囊 + 刷新按钮。
 * 圆表布局：只有标题、二维码、剩余时间；点屏幕任意处刷新。
 */
public class MainActivity extends Activity {

    private DeviceProfile profile;

    private ImageView qrView;
    private View statusDot;
    private TextView statusView;
    private TextView titleView;
    private View mask;
    private TextView maskTitle;
    private TextView maskHint;
    private TextView hintTap;

    private QrSettings settings;
    private QrStore store;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private String shownContent;
    private boolean fetching;
    private boolean failed;
    private int lastQrPx;

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            updateStatus();
            maybeAutoRefresh();
            handler.postDelayed(this, profile == null ? 1000L : profile.tickMillis());
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        profile = DeviceProfile.of(this);
        settings = new QrSettings(this);
        store = new QrStore(this);

        // 调试用：带 phone_layout=true 启动可强制手机布局，
        // 便于在手表上回归"布局能否正常 inflate"而不必换设备
        boolean forcePhone = getIntent() != null
                && getIntent().getBooleanExtra("phone_layout", false);

        setContentView(profile.watch && !forcePhone
                ? R.layout.activity_main_watch
                : R.layout.activity_main);

        qrView = findViewById(R.id.qr);
        statusView = findViewById(R.id.status);
        statusDot = findViewById(R.id.statusDot);
        titleView = findViewById(R.id.title);
        mask = findViewById(R.id.mask);
        maskTitle = findViewById(R.id.maskTitle);
        maskHint = findViewById(R.id.maskHint);

        // 点屏幕任意处刷新。
        // 点击齿轮或长按屏幕进入设置。
        View settingsButton = findViewById(R.id.btnSettings);
        if (settingsButton != null) {
            settingsButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    openSettings();
                }
            });
        }

        hintTap = findViewById(R.id.hintTap);

        View root = findViewById(R.id.root);
        root.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mask.getVisibility() == View.VISIBLE) {
                    forceRefresh(true);
                } else {
                    // 正常状态下点一下也能换码，但不打断正在进行的请求
                    forceRefresh(false);
                }
            }
        });
        root.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                openSettings();
                return true;
            }
        });

        // 被 WristWakeReceiver 拉回前台时，允许直接把屏幕点亮
        setTurnScreenOn(true);
        setShowWhenLocked(true);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);

        Insets.immersive(getWindow());
        Insets.lightStatusBar(getWindow(), profile.watch);

        if (!profile.watch) {
            Insets.applyTopPadding(findViewById(R.id.topBar));
            Insets.applyBottomPadding(findViewById(R.id.bottomBar));
        }

        askNotificationPermission();

        renderCached();
    }

    @Override
    protected void onResume() {
        super.onResume();
        WristWakeReceiver.pageVisible = true;
        WristWakeReceiver.lastShownAt = System.currentTimeMillis();
        // 常亮只跟随本页面：进入加标志，离开清标志
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getAttributes().screenBrightness =
                profile != null && profile.watch
                        ? WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                        : WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL;
        getWindow().setAttributes(getWindow().getAttributes());

        handler.removeCallbacks(ticker);
        handler.postDelayed(ticker, 300L);

        if (settings.backgroundEnabled()) {
            try {
                PrefetchService.start(this);
            } catch (Exception ignored) {
            }
        }

        if (titleView != null) {
            titleView.setText(settings.demoMode() ? "门禁码 · 模拟" : "门禁码");
        }

        maybeAutoRefresh();
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(ticker);
        WristWakeReceiver.pageVisible = false;
        WristWakeReceiver.lastShownAt = System.currentTimeMillis();
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    private void openSettings() {
        Intent i = new Intent(this, SettingsActivity.class);
        if (getIntent() != null && getIntent().getBooleanExtra("watch_layout", false)) {
            i.putExtra("watch_layout", true);
        }
        startActivity(i);
    }

    /* ---------------- 显示 ---------------- */

    private void renderCached() {
        QrStore.Entry e = store.load();
        if (e == null) {
            setMask(true, getString(R.string.state_no_code), "正在获取…");
            forceRefresh(false);
            return;
        }
        show(e.content);
    }

    private void show(String content) {
        if (content == null || content.isEmpty()) return;

        if (!content.equals(shownContent)) {
            int budget = profile.qrBudgetPx();
            // 内容变更则重新渲染；同内容复用位图
            Bitmap bmp = QrRenderer.render(content, budget, 4);
            qrView.setImageBitmap(bmp);
            sizeCardAndMask(bmp.getWidth(), bmp.getHeight());

            // 淡入
            qrView.setAlpha(0.55f);
            qrView.animate().alpha(1f).setDuration(180L).start();
            shownContent = content;
            lastQrPx = bmp.getWidth();
        }

        setMask(false, null, null);
        updateStatus();
    }

    /**
     * FrameLayout 是 wrap_content 时，子项的 match_parent 只会缩成自身内容大小，
     * 所以这里给卡片和遮罩都算成"二维码尺寸 + 内边距"，遮蔽才能盖满整块码。
     */
    private void sizeCardAndMask(int qrW, int qrH) {
        View card = findViewById(R.id.qrCard);
        if (card == null) return;

        int padH = card.getPaddingLeft() + card.getPaddingRight();
        int padV = card.getPaddingTop() + card.getPaddingBottom();
        int w = qrW + padH;
        int h = qrH + padV;

        ViewGroup.LayoutParams lp = card.getLayoutParams();
        lp.width = w;
        lp.height = h;
        card.setLayoutParams(lp);

        ViewGroup.LayoutParams qlp = qrView.getLayoutParams();
        qlp.width = qrW;
        qlp.height = qrH;
        qrView.setLayoutParams(qlp);

        if (mask != null) {
            ViewGroup.LayoutParams mlp = mask.getLayoutParams();
            mlp.width = ViewGroup.LayoutParams.MATCH_PARENT;
            mlp.height = ViewGroup.LayoutParams.MATCH_PARENT;
            mask.setLayoutParams(mlp);
        }
    }

    private void setMask(boolean visible, String title, String hint) {
        if (mask == null) return;
        mask.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (visible) {
            maskTitle.setText(title);
            maskHint.setText(hint);
        }
    }

    private void updateStatus() {
        QrStore.Entry e = store.load();
        if (e == null) {
            statusView.setText(getString(R.string.state_no_code));
            tintDot(R.color.text_secondary);
            return;
        }

        QrFreshness.Snapshot snap = QrFreshness.evaluate(
                e.retrievedAt, System.currentTimeMillis(),
                settings.ttlSeconds(), settings.refreshPercent());

        String text = QrFreshness.describe(snap);
        if (profile.watch) {
            // 圆表只留"剩余 8:12"
            text = QrFreshness.describeShort(snap);
        }

        if (failed) {
            text = snap.state == QrFreshness.State.EXPIRED
                    ? "已过期 · 获取失败"
                    : text + " · 获取失败";
        }

        String suffix = settings.demoMode() ? " · 模拟" : "";
        statusView.setText(text + suffix);

        switch (snap.state) {
            case EXPIRED:
                tintDot(R.color.state_error);
                setMask(true, getString(R.string.mask_expired_title),
                        getString(R.string.mask_expired_hint));
                break;
            case DUE:
                tintDot(R.color.state_warn);
                setMask(false, null, null);
                break;
            case AGING:
                tintDot(R.color.state_warn);
                setMask(false, null, null);
                break;
            default:
                tintDot(R.color.state_ok);
                setMask(false, null, null);
        }

        if (failed && snap.state == QrFreshness.State.EXPIRED) {
            setMask(true, getString(R.string.mask_failed_title),
                    getString(R.string.mask_failed_hint));
        }
    }

    private void tintDot(int colorRes) {
        if (statusDot == null) return;
        int color = getColor(colorRes);
        statusDot.setBackgroundTintList(ColorStateList.valueOf(color));
        if (statusView != null) statusView.setTextColor(color);
    }

    /* ---------------- 获取 ---------------- */

    private void maybeAutoRefresh() {
        QrStore.Entry e = store.load();
        if (e == null) return;

        QrFreshness.Snapshot snap = QrFreshness.evaluate(
                e.retrievedAt, System.currentTimeMillis(),
                settings.ttlSeconds(), settings.refreshPercent());

        if (QrFreshness.refreshNeeded(snap)) {
            forceRefresh(false);
        }
    }

    /**
     * @param userTriggered 用户明确点了刷新：给出即时反馈
     */
    private void forceRefresh(boolean userTriggered) {
        if (fetching) {
            if (userTriggered) {
                Toast.makeText(this, "正在获取，请稍候", Toast.LENGTH_SHORT).show();
            }
            return;
        }

        fetching = true;
        if (userTriggered) {
            statusView.setText(getString(R.string.state_loading));
            tintDot(R.color.state_info);
        }

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    QrApi.Result r = QrApi.fetch(settings);
                    store.save(r.content, r.retrievedAt,
                            settings.demoMode() ? "demo" : "authorized");
                    final String got = r.content;
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            failed = false;
                            show(got);
                            // 用过一次之后就不再打扰提示
                            if (hintTap != null) {
                                hintTap.animate().alpha(0f).setDuration(200L).start();
                            }
                        }
                    });
                } catch (final Exception ex) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            failed = true;
                            if (shownContent == null) {
                                setMask(true, getString(R.string.mask_failed_title),
                                        ex.getMessage());
                            } else {
                                Toast.makeText(MainActivity.this,
                                        "换码失败，继续使用当前码",
                                        Toast.LENGTH_SHORT).show();
                            }
                            updateStatus();
                        }
                    });
                } finally {
                    fetching = false;
                }
            }
        }, "qr-fetch-ui").start();
    }



    /* ---------------- 杂项 ---------------- */

    private void askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
    }
}









