package app.dormqr.qr;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/**
 * 圆屏滚动指示：与 ColorOS 系统应用一致的白色短弧，锚在中间右侧。
 *
 *   圆心 = 屏幕中心，半径 = R − 10dp，张角 50°，0° 指向 3 点钟。
 */
public class ArcScrollBarView extends View {

    private static final float SPAN_DEG = 50f;
    private static final float MIN_THUMB_DEG = 9f;
    private static final long FADE_DELAY_MS = 1100L;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF oval = new RectF();
    private final int insetPx;

    private float progress;
    private float viewportRatio = 1f;
    private boolean scrollable;

    private final Runnable fadeOut = new Runnable() {
        @Override
        public void run() {
            animate().alpha(0.35f).setDuration(300L).start();
        }
    };

    public ArcScrollBarView(Context context, int insetPx) {
        super(context);
        this.insetPx = insetPx;

        float density = context.getResources().getDisplayMetrics().density;
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(4f * density);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setColor(Color.argb(0xE6, 0xFF, 0xFF, 0xFF));

        setClickable(false);
        setFocusable(false);
    }

    public void update(float progress, float viewportRatio) {
        this.progress = Math.max(0f, Math.min(1f, progress));
        this.viewportRatio = Math.max(0f, Math.min(1f, viewportRatio));

        boolean canScroll = this.viewportRatio < 0.999f;
        if (canScroll != scrollable) {
            scrollable = canScroll;
            setVisibility(canScroll ? VISIBLE : GONE);
        }
        if (!canScroll) return;

        setAlpha(1f);
        removeCallbacks(fadeOut);
        postDelayed(fadeOut, FADE_DELAY_MS);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!scrollable) return;

        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float radius = Math.min(cx, cy) - insetPx;
        if (radius <= 0) return;

        oval.set(cx - radius, cy - radius, cx + radius, cy + radius);

        float start = -SPAN_DEG / 2f;
        float thumbSpan = Math.max(MIN_THUMB_DEG, SPAN_DEG * viewportRatio);
        float travel = SPAN_DEG - thumbSpan;
        float thumbStart = start + travel * progress;

        canvas.drawArc(oval, thumbStart, thumbSpan, false, paint);
    }
}
