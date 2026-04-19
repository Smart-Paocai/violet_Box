package com.violet.safe.ui.widget;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.violet.safe.R;

/**
 * CPU 占用圆环：{@code semi} 为上半圆弧；{@code donut} 为整圈圆环（百分比叠在环心由外层 TextView 展示）。
 */
public class CpuRingGaugeView extends View {

    private static final int STYLE_SEMI = 0;
    private static final int STYLE_DONUT = 1;

    private static final float SEMI_ARC_START = 180f;
    private static final float SEMI_ARC_SWEEP = -180f;
    /** 从 12 点顺时针 */
    private static final float DONUT_ARC_START = -90f;

    private final Paint paintTrack = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintProgress = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF arcRect = new RectF();

    private int gaugeStyle = STYLE_SEMI;

    /** 当前帧绘制值（动画中变化） */
    private float displayedProgress;
    /** 外部设定目标，{@link #getProgress()} 返回此值 */
    private float targetProgress;
    private ValueAnimator progressAnimator;

    private float strokePx;
    /** donut：在「半宽 − 线宽」基础上再内缩，避免 ROUND 描边与抗锯齿贴边被裁 */
    private float donutRadiusInsetPx;
    private int minSizePx;
    private int minHeightPx;
    private int donutSizePx;

    public CpuRingGaugeView(Context context) {
        super(context);
        init(context, null);
    }

    public CpuRingGaugeView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public CpuRingGaugeView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    private void init(Context context, @Nullable AttributeSet attrs) {
        setWillNotDraw(false);
        setBackgroundColor(Color.TRANSPARENT);

        if (attrs != null) {
            TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.CpuRingGaugeView);
            gaugeStyle = a.getInt(R.styleable.CpuRingGaugeView_gaugeStyle, STYLE_SEMI);
            a.recycle();
        }

        strokePx = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, gaugeStyle == STYLE_DONUT ? 3.5f : 4f, context.getResources().getDisplayMetrics());
        minSizePx = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 46f, context.getResources().getDisplayMetrics());
        minHeightPx = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 23f, context.getResources().getDisplayMetrics());
        donutSizePx = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 58f, context.getResources().getDisplayMetrics());
        donutRadiusInsetPx = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 3f, context.getResources().getDisplayMetrics());

        if (gaugeStyle == STYLE_DONUT) {
            int edgePad = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1f, context.getResources().getDisplayMetrics());
            setPadding(edgePad, edgePad, edgePad, edgePad);
        }

        paintTrack.setStyle(Paint.Style.STROKE);
        paintTrack.setStrokeCap(Paint.Cap.ROUND);
        paintTrack.setStrokeJoin(Paint.Join.ROUND);
        paintTrack.setColor(ContextCompat.getColor(context, R.color.stitch_gauge_track));
        paintTrack.setStrokeWidth(strokePx);

        paintProgress.setStyle(Paint.Style.STROKE);
        paintProgress.setStrokeCap(Paint.Cap.ROUND);
        paintProgress.setStrokeJoin(Paint.Join.ROUND);
        paintProgress.setStrokeWidth(strokePx);
        updateFillColor(0f);
    }

    private boolean isDonut() {
        return gaugeStyle == STYLE_DONUT;
    }

    private void updateFillColor(float p) {
        Context ctx = getContext();
        if (ctx != null) {
            paintProgress.setColor(ContextCompat.getColor(ctx, fillColorResForProgress(p)));
        }
    }

    /**
     * 设置目标进度 0–100，会自当前显示值插值过去并触发重绘。
     */
    public void setProgress(float p) {
        targetProgress = Math.max(0f, Math.min(100f, p));
        if (progressAnimator != null) {
            progressAnimator.cancel();
            progressAnimator = null;
        }
        float start = displayedProgress;
        float end = targetProgress;
        if (Math.abs(end - start) < 0.01f) {
            displayedProgress = end;
            updateFillColor(displayedProgress);
            invalidate();
            return;
        }
        progressAnimator = ValueAnimator.ofFloat(start, end);
        progressAnimator.setDuration(isDonut() ? 220 : 160);
        progressAnimator.setInterpolator(new DecelerateInterpolator(1.2f));
        progressAnimator.addUpdateListener(a -> {
            displayedProgress = (float) a.getAnimatedValue();
            updateFillColor(displayedProgress);
            invalidate();
        });
        progressAnimator.start();
    }

    /** 最近一次设定的目标进度（非动画中间值） */
    public float getProgress() {
        return targetProgress;
    }

    public static int fillColorResForProgress(float p) {
        if (p >= 67f) {
            return R.color.stitch_gauge_fill_high;
        }
        if (p >= 34f) {
            return R.color.stitch_gauge_fill_med;
        }
        return R.color.stitch_gauge_fill_low;
    }

    public static int stitchTextColorResForProgress(float p) {
        if (p >= 67f) {
            return R.color.stitch_pct_high;
        }
        if (p >= 34f) {
            return R.color.stitch_pct_med;
        }
        return R.color.stitch_pct_low;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (isDonut()) {
            // 各核 4 列格宽常小于 donutSizePx：边长跟「可用宽度」走，禁止强行撑满 58dp 导致左右被父布局裁切。
            int w = resolveSize(donutSizePx, widthMeasureSpec);
            if (w <= 0) {
                w = donutSizePx;
            }
            int s = Math.min(donutSizePx, w);
            setMeasuredDimension(s, s);
        } else {
            int w = resolveSize(minSizePx, widthMeasureSpec);
            int h = resolveSize(minHeightPx, heightMeasureSpec);
            w = Math.max(w, minSizePx);
            h = Math.max(h, minHeightPx);
            setMeasuredDimension(w, h);
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w > 0 && h > 0 && (oldw <= 0 || oldh <= 0 || w != oldw || h != oldh)) {
            invalidate();
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        postInvalidateOnAnimation();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (progressAnimator != null) {
            progressAnimator.cancel();
            progressAnimator = null;
        }
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (isDonut()) {
            drawDonut(canvas);
        } else {
            drawSemi(canvas);
        }
    }

    private void drawDonut(Canvas canvas) {
        float pl = getPaddingLeft();
        float pr = getPaddingRight();
        float pt = getPaddingTop();
        float pb = getPaddingBottom();
        float w = getWidth() - pl - pr;
        float h = getHeight() - pt - pb;
        if (w <= 0f || h <= 0f) {
            return;
        }
        float cx = pl + w * 0.5f;
        float cy = pt + h * 0.5f;
        float maxR = Math.min(w, h) * 0.5f;
        // 外侧不超过 maxR：r + stroke/2 ≤ maxR ⇒ r ≤ maxR − stroke/2；再减 donutRadiusInsetPx 抑制 ROUND 帽与 AA 外扩
        float r = maxR - strokePx - donutRadiusInsetPx;
        if (r <= 0f) {
            return;
        }
        arcRect.set(cx - r, cy - r, cx + r, cy + r);

        canvas.drawArc(arcRect, DONUT_ARC_START, 360f, false, paintTrack);

        float sweep = 360f * displayedProgress / 100f;
        if (displayedProgress > 0f && sweep > 0f && sweep < 2.5f) {
            sweep = 2.5f;
        }
        if (displayedProgress > 0f) {
            canvas.drawArc(arcRect, DONUT_ARC_START, sweep, false, paintProgress);
        }
    }

    private void drawSemi(Canvas canvas) {
        float w = getWidth();
        float h = getHeight();
        float halfStroke = strokePx * 0.5f;
        float cx = w * 0.5f;
        float cy = h;
        float r = Math.min(w * 0.5f - strokePx, h - strokePx);
        if (r <= 0f) {
            return;
        }
        arcRect.set(cx - r, cy - r, cx + r, cy + r);

        int save = canvas.save();
        canvas.clipRect(0f, 0f, w, h);

        canvas.drawArc(arcRect, SEMI_ARC_START, SEMI_ARC_SWEEP, false, paintTrack);

        float sweepProg = SEMI_ARC_SWEEP * displayedProgress / 100f;
        if (displayedProgress > 0f && Math.abs(sweepProg) > 0f && Math.abs(sweepProg) < 3.5f) {
            sweepProg = -3.5f;
        }
        if (displayedProgress > 0f) {
            canvas.drawArc(arcRect, SEMI_ARC_START, sweepProg, false, paintProgress);
        }
        canvas.restoreToCount(save);
    }
}
