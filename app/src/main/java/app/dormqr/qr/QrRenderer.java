package app.dormqr.qr;

import android.graphics.Bitmap;
import android.graphics.Color;

import io.nayuki.qrcodegen.QrCode;

/**
 * 内容 → 位图。
 */
public final class QrRenderer {

    private QrRenderer() {
    }

    /**
     * @param content 二维码内容（VMS://v001.xxx）
     * @param maxPx   目标边长像素（实际会向下取整到整模块）
     * @param quietZoneModules 留白模块数，规范要求 4
     */
    public static Bitmap render(String content, int maxPx, int quietZoneModules) {
        QrCode qr = QrCode.encodeText(content, QrCode.Ecc.MEDIUM);

        int modules = qr.size + quietZoneModules * 2;
        int scale = Math.max(1, maxPx / modules);
        int sizePx = modules * scale;

        Bitmap bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
        bmp.eraseColor(Color.WHITE);

        int[] row = new int[sizePx];
        for (int y = 0; y < sizePx; y++) {
            int my = y / scale - quietZoneModules;
            for (int x = 0; x < sizePx; x++) {
                int mx = x / scale - quietZoneModules;
                boolean dark = mx >= 0 && my >= 0 && mx < qr.size && my < qr.size
                        && qr.getModule(mx, my);
                row[x] = dark ? Color.BLACK : Color.WHITE;
            }
            bmp.setPixels(row, 0, sizePx, 0, y, sizePx, 1);
        }

        return bmp;
    }

    public static Bitmap render(String content, int maxPx) {
        return render(content, maxPx, 4);
    }
}
