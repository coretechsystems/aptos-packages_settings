package com.android.settings.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View.MeasureSpec;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import com.android.settings.R;

public class SetupWizardIllustration extends FrameLayout {
    private float mAspectRatio;
    private Drawable mBackground;
    private float mBaselineGridSize;
    private Drawable mForeground;
    private final Rect mForegroundBounds;
    private float mScale;
    private final Rect mViewBounds;

    public SetupWizardIllustration(Context context) {
        this(context, null);
    }

    public SetupWizardIllustration(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SetupWizardIllustration(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public SetupWizardIllustration(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        this.mViewBounds = new Rect();
        this.mForegroundBounds = new Rect();
        this.mScale = 1.0f;
        this.mAspectRatio = 0.0f;
        if (attrs != null) {
            TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.SetupWizardIllustration, 0, 0);
            this.mAspectRatio = a.getFloat(0, 0.0f);
            a.recycle();
        }
        this.mBaselineGridSize = getResources().getDisplayMetrics().density * 8.0f;
        setWillNotDraw(false);
    }

    public void setBackground(Drawable background) {
        this.mBackground = background;
    }

    public void setForeground(Drawable foreground) {
        this.mForeground = foreground;
    }

    public void onResolveDrawables(int layoutDirection) {
        this.mBackground.setLayoutDirection(layoutDirection);
        this.mForeground.setLayoutDirection(layoutDirection);
    }

    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (this.mAspectRatio != 0.0f) {
            int illustrationHeight = (int) (((float) MeasureSpec.getSize(widthMeasureSpec)) / this.mAspectRatio);
            setPaddingRelative(0, (int) (((float) illustrationHeight) - (((float) illustrationHeight) % this.mBaselineGridSize)), 0, 0);
        }
        setOutlineProvider(ViewOutlineProvider.BOUNDS);
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int layoutWidth = right - left;
        int layoutHeight = bottom - top;
        if (this.mForeground != null) {
            int intrinsicWidth = this.mForeground.getIntrinsicWidth();
            int intrinsicHeight = this.mForeground.getIntrinsicHeight();
            int layoutDirection = getLayoutDirection();
            this.mViewBounds.set(0, 0, layoutWidth, layoutHeight);
            if (this.mAspectRatio != 0.0f) {
                this.mScale = ((float) layoutWidth) / ((float) intrinsicWidth);
                intrinsicWidth = layoutWidth;
                intrinsicHeight = (int) (((float) intrinsicHeight) * this.mScale);
            }
            Gravity.apply(55, intrinsicWidth, intrinsicHeight, this.mViewBounds, this.mForegroundBounds, layoutDirection);
            this.mForeground.setBounds(this.mForegroundBounds);
        }
        if (this.mBackground != null) {
            this.mBackground.setBounds(0, 0, (int) Math.ceil((double) (((float) layoutWidth) / this.mScale)), (int) Math.ceil((double) (((float) (layoutHeight - this.mForegroundBounds.height())) / this.mScale)));
        }
        super.onLayout(changed, left, top, right, bottom);
    }

    public void onDraw(Canvas canvas) {
        if (this.mBackground != null) {
            canvas.save();
            canvas.translate(0.0f, (float) this.mForegroundBounds.height());
            canvas.scale(this.mScale, this.mScale, 0.0f, 0.0f);
            this.mBackground.draw(canvas);
            canvas.restore();
        }
        if (this.mForeground != null) {
            canvas.save();
            this.mForeground.draw(canvas);
            canvas.restore();
        }
        super.onDraw(canvas);
    }
}
