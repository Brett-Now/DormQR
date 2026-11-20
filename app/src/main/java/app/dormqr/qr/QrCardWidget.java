package app.dormqr.qr;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.widget.RemoteViews;

/**
 * 门禁二维码卡片。
 *
 * 使用标准 Android AppWidget
 *
 * 尺寸策略：手机上可缩放，二维码按卡片实际尺寸动态渲染；
 * 手表固定一张卡，取不到尺寸时回落到 300px（尝试失败，遂放弃）。
 */
public class QrCardWidget extends AppWidgetProvider {

    /** 点卡片 = 立刻刷新。 */
    public static final String ACTION_REFRESH = "app.dormqr.qr.WIDGET_REFRESH";

    private static final int DEFAULT_QR_PX = 300;
    private static final int MAX_QR_PX = 720;

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        for (int id : ids) {
            manager.updateAppWidget(id, buildViews(context, id));
        }
        // 顺手触发一次后台换码
        try {
            PrefetchService.refreshNow(context);
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        if (intent != null && ACTION_REFRESH.equals(intent.getAction())) {
            try {
                PrefetchService.refreshNow(context);
            } catch (Exception ignored) {
            }
            updateAll(context);
        }
    }

    @Override
    public void onAppWidgetOptionsChanged(Context context, AppWidgetManager manager,
                                          int appWidgetId, Bundle newOptions) {
        manager.updateAppWidget(appWidgetId, buildViews(context, appWidgetId));
    }

    /** 换码成功后由服务调用：把所有卡片实例刷成当前缓存里的码。 */
    public static void updateAll(Context context) {
        try {
            AppWidgetManager manager = AppWidgetManager.getInstance(context);
            ComponentName me = new ComponentName(context, QrCardWidget.class);
            int[] ids = manager.getAppWidgetIds(me);
            if (ids == null || ids.length == 0) return;

            for (int id : ids) {
                manager.updateAppWidget(id, buildViews(context, id));
            }
        } catch (Exception ignored) {
            // 没有卡片实例或系统拒绝时静默忽略
        }
    }

    private static RemoteViews buildViews(Context context, int appWidgetId) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_qr);

        QrStore.Entry entry = new QrStore(context).load();
        QrSettings settings = new QrSettings(context);

        if (entry == null) {
            views.setTextViewText(R.id.widgetStatus, "暂无二维码 · 点一下获取");
        } else {
            Bitmap bmp = QrRenderer.render(entry.content, qrPx(context, appWidgetId), 4);
            views.setImageViewBitmap(R.id.widgetQr, bmp);

            QrFreshness.Snapshot snap = QrFreshness.evaluate(
                    entry.retrievedAt, System.currentTimeMillis(),
                    settings.ttlSeconds(), settings.refreshPercent());
            views.setTextViewText(R.id.widgetStatus, QrFreshness.describeShort(snap));
        }

        // 点卡片任意位置 → 换码
        Intent refresh = new Intent(context, QrCardWidget.class).setAction(ACTION_REFRESH);
        PendingIntent pi = PendingIntent.getBroadcast(context, 0, refresh,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widgetRoot, pi);

        // 点二维码 → 进应用看大图
        Intent open = new Intent(context, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent openPi = PendingIntent.getActivity(context, 1, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widgetQr, openPi);

        return views;
    }

    /**
     * 按卡片实际尺寸算二维码边长：取宽高的较小值，留出内边距。
     */
    private static int qrPx(Context context, int appWidgetId) {
        try {
            AppWidgetManager manager = AppWidgetManager.getInstance(context);
            Bundle options = manager.getAppWidgetOptions(appWidgetId);
            if (options != null) {
                int wDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0);
                int hDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0);
                if (wDp > 0 && hDp > 0) {
                    float density = context.getResources().getDisplayMetrics().density;
                    int px = (int) (Math.min(wDp, hDp) * density * 0.9f);
                    return Math.max(160, Math.min(MAX_QR_PX, px));
                }
            }
        } catch (Exception ignored) {
        }
        return DEFAULT_QR_PX;
    }
}

