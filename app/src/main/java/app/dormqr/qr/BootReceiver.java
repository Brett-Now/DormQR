package app.dormqr.qr;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** 开机/更新后恢复预取。 */
public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        QrSettings settings = new QrSettings(context);
        if (!settings.backgroundEnabled()) return;

        try {
            PrefetchService.start(context);
        } catch (Exception ignored) {
            // Android 12+ 后台启动前台服务可能被拦截，用户打开 App 时自然恢复
        }
    }
}
