package app.dormqr.qr;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;

/**
 * 常驻通知：直接把二维码画进大图通知。
 * 点通知则打开二维码页。
 */
public final class QrNotifier {

    public static final String CHANNEL_ID = "dormqr_prefetch";
    public static final int NOTIFICATION_ID = 1001;

    // 通知每分钟刷一次文案
    private static String cachedContent;
    private static Bitmap cachedBitmap;

    private QrNotifier() {
    }

    public static void ensureChannel(Context ctx) {
        NotificationManager nm = ctx.getSystemService(NotificationManager.class);
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return;

        NotificationChannel ch = new NotificationChannel(
                CHANNEL_ID,
                ctx.getString(R.string.channel_name),
                NotificationManager.IMPORTANCE_LOW);   // 静音、不弹窗、不亮屏
        ch.setDescription(ctx.getString(R.string.channel_desc));
        ch.setShowBadge(false);
        ch.setSound(null, null);
        ch.enableVibration(false);
        nm.createNotificationChannel(ch);
    }

    public static Notification build(Context ctx, String content, String statusText) {
        ensureChannel(ctx);

        Intent open = new Intent(ctx, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(
                ctx, 0, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder b = new Notification.Builder(ctx, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(ctx.getString(R.string.notif_title))
                .setContentText(statusText)
                .setContentIntent(pi)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setCategory(Notification.CATEGORY_SERVICE)
                .setVisibility(Notification.VISIBILITY_SECRET);   // 锁屏不露出内容

        if (content != null && !content.isEmpty()) {
            // 通知栏大图：宽度按通知栏常见宽度设置，扫码器可直接识别
            Bitmap big = bitmapFor(content);
            b.setLargeIcon(big);
            b.setStyle(new Notification.BigPictureStyle()
                    .bigPicture(big)
                    .setSummaryText(statusText));
        }

        return b.build();
    }

    private static synchronized Bitmap bitmapFor(String content) {
        if (!content.equals(cachedContent) || cachedBitmap == null) {
            cachedBitmap = QrRenderer.render(content, 720, 4);
            cachedContent = content;
        }
        return cachedBitmap;
    }

    public static void show(Context ctx, String content, String statusText) {
        NotificationManager nm = ctx.getSystemService(NotificationManager.class);
        nm.notify(NOTIFICATION_ID, build(ctx, content, statusText));
    }

    public static void cancel(Context ctx) {
        NotificationManager nm = ctx.getSystemService(NotificationManager.class);
        nm.cancel(NOTIFICATION_ID);
    }
}

