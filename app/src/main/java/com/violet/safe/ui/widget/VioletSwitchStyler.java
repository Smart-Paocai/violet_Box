package com.violet.safe.ui.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.ContextCompat;

import com.violet.safe.R;

public final class VioletSwitchStyler {

    private VioletSwitchStyler() {
    }

    public static void apply(@NonNull Context context, @Nullable SwitchCompat switchCompat) {
        if (switchCompat == null) {
            return;
        }
        try {
            Drawable thumb = ContextCompat.getDrawable(context, R.drawable.switch_ios26_thumb);
            Drawable track = ContextCompat.getDrawable(context, R.drawable.switch_ios26_track);
            if (thumb != null) {
                switchCompat.setThumbDrawable(new FixedSizeDrawable(thumb, dpToPx(context, 24), dpToPx(context, 24), true));
            }
            if (track != null) {
                switchCompat.setTrackDrawable(new FixedSizeDrawable(track, dpToPx(context, 44), dpToPx(context, 28), false));
            }
            switchCompat.setSplitTrack(false);
            switchCompat.setMinHeight(dpToPx(context, 28));
            switchCompat.setPadding(0, 0, 0, 0);
        } catch (Exception ignored) {
        }
    }

    private static int dpToPx(Context context, int dp) {
        return (int) (dp * context.getResources().getDisplayMetrics().density + 0.5f);
    }

    private static final class FixedSizeDrawable extends Drawable implements Drawable.Callback {
        private final Drawable wrapped;
        private final int widthPx;
        private final int heightPx;
        private final boolean forceSquareCenter;

        FixedSizeDrawable(Drawable wrapped, int widthPx, int heightPx, boolean forceSquareCenter) {
            this.wrapped = wrapped;
            this.widthPx = widthPx;
            this.heightPx = heightPx;
            this.forceSquareCenter = forceSquareCenter;
            this.wrapped.setCallback(this);
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            wrapped.draw(canvas);
        }

        @Override
        protected void onBoundsChange(Rect bounds) {
            super.onBoundsChange(bounds);
            if (!forceSquareCenter) {
                wrapped.setBounds(bounds);
                return;
            }
            int size = Math.min(bounds.width(), bounds.height());
            int left = bounds.left + (bounds.width() - size) / 2;
            int top = bounds.top + (bounds.height() - size) / 2;
            wrapped.setBounds(left, top, left + size, top + size);
        }

        @Override
        public void setAlpha(int alpha) {
            wrapped.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(@Nullable ColorFilter colorFilter) {
            wrapped.setColorFilter(colorFilter);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }

        @Override
        public boolean isStateful() {
            return wrapped.isStateful();
        }

        @Override
        protected boolean onStateChange(int[] state) {
            boolean changed = wrapped.setState(state);
            if (changed) {
                invalidateSelf();
            }
            return changed;
        }

        @Override
        protected boolean onLevelChange(int level) {
            boolean changed = wrapped.setLevel(level);
            if (changed) {
                invalidateSelf();
            }
            return changed;
        }

        @Override
        public Drawable mutate() {
            wrapped.mutate();
            return this;
        }

        @Override
        public void invalidateDrawable(@NonNull Drawable who) {
            invalidateSelf();
        }

        @Override
        public void scheduleDrawable(@NonNull Drawable who, @NonNull Runnable what, long when) {
            scheduleSelf(what, when);
        }

        @Override
        public void unscheduleDrawable(@NonNull Drawable who, @NonNull Runnable what) {
            unscheduleSelf(what);
        }

        @Override
        public int getIntrinsicWidth() {
            return widthPx;
        }

        @Override
        public int getIntrinsicHeight() {
            return heightPx;
        }
    }
}
