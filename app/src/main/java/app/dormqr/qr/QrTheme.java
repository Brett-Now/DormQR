package app.dormqr.qr;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Build;

/**
 * 取色：Android 12+ 优先用系统动态色，低版本回落到品牌蓝。
 */
public final class QrTheme {

    private QrTheme() {
    }

    /** 主操作色：动态色 accent1 或品牌蓝。 */
    public static int accent(Context ctx) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Integer c = systemColor(ctx, android.R.color.system_accent1_200);
            if (c != null) {
                // 动态色在浅色背景可能过亮，压一点亮度保证对比
                return darkenIfTooBright(c, 0.92f);
            }
        }
        return ctx.getColor(R.color.brand);
    }

    /** 容器色（按钮/胶囊底色）。 */
    public static int accentContainer(Context ctx) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Integer c = systemColor(ctx, android.R.color.system_accent1_500);
            if (c != null) {
                return Color.argb(38, Color.red(c), Color.green(c), Color.blue(c));
            }
        }
        return ctx.getColor(R.color.brand_container);
    }

    private static Integer systemColor(Context ctx, int resId) {
        try {
            return ctx.getColor(resId);
        } catch (Exception e) {
            return null;
        }
    }

    private static int darkenIfTooBright(int color, float factor) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        if (hsv[2] > 0.9f) hsv[2] *= factor;
        return Color.HSVToColor(hsv);
    }

    /** 把主色刷到按钮背景上（保留圆角形状）。 */
    public static void tintPrimary(android.widget.Button b, Context ctx) {
        Drawable bg = b.getBackground();
        if (bg != null) {
            bg.mutate().setTintList(ColorStateList.valueOf(accent(ctx)));
        }
    }

    /** 把容器色刷到次级按钮/胶囊上。 */
    public static void tintTonal(android.widget.Button b, Context ctx) {
        Drawable bg = b.getBackground();
        if (bg != null) {
            bg.mutate().setTintList(ColorStateList.valueOf(accentContainer(ctx)));
        }
    }
}
