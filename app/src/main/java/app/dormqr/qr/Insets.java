package app.dormqr.qr;

import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;

/**
 * Insets 处理：计算状态栏 / 刘海 / 输入法的占位
 */
public final class Insets {

    private Insets() {
    }

    /** 顶部需要让出的高度：状态栏 + 刘海取较大值。 */
    public static int top(WindowInsets insets) {
        if (insets == null) return 0;

        int status = 0;
        if (insets.getSystemWindowInsetTop() > 0) {
            status = insets.getSystemWindowInsetTop();
        }
        if (insets.getDisplayCutout() != null) {
            status = Math.max(status, insets.getDisplayCutout().getSafeInsetTop());
        }
        return status;
    }

    public static int bottom(WindowInsets insets) {
        return insets == null ? 0 : insets.getSystemWindowInsetBottom();
    }

    /**
     * 给顶部内容加安全边距，同时保留视图原有 padding（不覆盖设计间距）。
     */
    public static void applyTopPadding(final View view) {
        final int left = view.getPaddingLeft();
        final int top = view.getPaddingTop();
        final int right = view.getPaddingRight();
        final int bottom = view.getPaddingBottom();

        view.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override
            public WindowInsets onApplyWindowInsets(View v, WindowInsets insets) {
                v.setPadding(left, top + top(insets), right, bottom);
                return insets;
            }
        });
    }

    /** 底部固定栏：让出导航栏 + 输入法。 */
    public static void applyBottomPadding(final View view) {
        final int left = view.getPaddingLeft();
        final int top = view.getPaddingTop();
        final int right = view.getPaddingRight();
        final int bottom = view.getPaddingBottom();

        view.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override
            public WindowInsets onApplyWindowInsets(View v, WindowInsets insets) {
                int ime = insets == null ? 0 : insets.getSystemWindowInsetBottom();
                v.setPadding(left, top, right, bottom + Math.max(ime, bottom(insets)));
                return insets;
            }
        });
    }

    /** 状态栏图标明暗：深色背景用浅色图标。 */
    public static void lightStatusBar(Window window, boolean lightIcons) {
        View decor = window.getDecorView();
        int flags = decor.getSystemUiVisibility();
        if (lightIcons) {
            flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        } else {
            flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        }
        decor.setSystemUiVisibility(flags);
    }

    /** 全屏沉浸（手表/手机二维码页）。 */
    public static void immersive(Window window) {
        View decor = window.getDecorView();
        decor.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
    }
}
