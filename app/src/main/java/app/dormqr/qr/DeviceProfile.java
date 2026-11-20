package app.dormqr.qr;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Point;
import android.util.DisplayMetrics;
import android.view.WindowManager;

/**
 * 设备画像：决定用手机布局还是圆表布局。
 *
 * OPPO Watch X3 = 466×466 px / 320dpi → 233×233 dp，正方形。
 * 判定条件：正方形 + 短边 dp 较小
 */
public final class DeviceProfile {

    public final boolean watch;
    public final int widthPx;
    public final int heightPx;
    public final float density;
    public final int widthDp;
    public final int heightDp;

    private DeviceProfile(boolean watch, int widthPx, int heightPx, float density) {
        this.watch = watch;
        this.widthPx = widthPx;
        this.heightPx = heightPx;
        this.density = density;
        this.widthDp = Math.round(widthPx / density);
        this.heightDp = Math.round(heightPx / density);
    }

    /** 仅调试用：强制按手表形态计算，用于在手机/模拟器上回归圆表布局。 */
    public static DeviceProfile forced(Context ctx, boolean watch) {
        DisplayMetrics dm = ctx.getResources().getDisplayMetrics();
        DeviceProfile p = new DeviceProfile(watch, dm.widthPixels, dm.heightPixels, dm.density);
        return p;
    }

    public static DeviceProfile of(Context ctx) {
        WindowManager wm = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
        DisplayMetrics dm = ctx.getResources().getDisplayMetrics();

        int w = dm.widthPixels;
        int h = dm.heightPixels;
        if (wm != null) {
            Point p = new Point();
            wm.getDefaultDisplay().getRealSize(p);
            // 用真实尺寸判断形状，避免被软键盘/分屏影响
            if (p.x > 0 && p.y > 0) {
                w = p.x;
                h = p.y;
            }
        }

        float density = dm.density <= 0 ? 1f : dm.density;
        boolean square = Math.abs(w - h) <= Math.max(2, w / 40);
        boolean small = Math.min(w, h) / density <= 300f;
        boolean uiWatch = (ctx.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_TYPE_MASK) == Configuration.UI_MODE_TYPE_WATCH;

        return new DeviceProfile(uiWatch || (square && small), w, h, density);
    }

    /**
     * 圆屏内切正方形边长：直径 / √2。
     * 二维码（含静区）必须整体落在这个正方形内。
     */
    public int inscribedSquarePx() {
        int min = Math.min(widthPx, heightPx);
        return (int) Math.floor(min / Math.sqrt(2.0));
    }

    /** 二维码可用像素：圆表按内切正方形再打 8% 余量，手机按短边比例。 */
    public int qrBudgetPx() {
        if (watch) {
            return (int) (inscribedSquarePx() * 0.86f);
        }
        int min = Math.min(widthPx, heightPx);
        return (int) (min * 0.72f);
    }

    /** 状态文字刷新频率：手表设置为 5 秒（省电）。 */
    public long tickMillis() {
        return watch ? 5000L : 1000L;
    }
}


