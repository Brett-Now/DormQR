package app.dormqr.qr;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** 到点唤醒服务进行换码。 */
public class AlarmReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        try {
            PrefetchService.start(context);
        } catch (Exception ignored) {
            // 后台启动受限时静默跳过，下次用户打开 App 会自行补上
        }
    }
}
