package com.android.settings.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import android.widget.ListView;

public class StickyHeaderListView extends ListView {
    private boolean mDrawScrollBar;
    private int mStatusBarInset = 0;
    private View mSticky;
    private View mStickyContainer;
    private RectF mStickyRect = new RectF();

    public StickyHeaderListView(Context context) {
        super(context);
    }

    public StickyHeaderListView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public StickyHeaderListView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public StickyHeaderListView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        if (this.mSticky == null) {
            updateStickyView();
        }
    }

    public void updateStickyView() {
        this.mSticky = findViewWithTag("sticky");
        this.mStickyContainer = findViewWithTag("stickyContainer");
    }

    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (!this.mStickyRect.contains(ev.getX(), ev.getY())) {
            return super.dispatchTouchEvent(ev);
        }
        ev.offsetLocation(-this.mStickyRect.left, -this.mStickyRect.top);
        return this.mStickyContainer.dispatchTouchEvent(ev);
    }

    public void draw(Canvas canvas) {
        this.mDrawScrollBar = false;
        super.draw(canvas);
        if (this.mSticky != null) {
            int drawOffset;
            int saveCount = canvas.save();
            View drawTarget = this.mStickyContainer != null ? this.mStickyContainer : this.mSticky;
            if (this.mStickyContainer != null) {
                drawOffset = this.mSticky.getTop();
            } else {
                drawOffset = 0;
            }
            if (drawTarget.getTop() + drawOffset < this.mStatusBarInset || !drawTarget.isShown()) {
                canvas.translate(0.0f, (float) ((-drawOffset) + this.mStatusBarInset));
                canvas.clipRect(0, 0, drawTarget.getWidth(), drawTarget.getHeight());
                drawTarget.draw(canvas);
                this.mStickyRect.set(0.0f, (float) ((-drawOffset) + this.mStatusBarInset), (float) drawTarget.getWidth(), (float) ((drawTarget.getHeight() - drawOffset) + this.mStatusBarInset));
            } else {
                this.mStickyRect.setEmpty();
            }
            canvas.restoreToCount(saveCount);
        }
        this.mDrawScrollBar = true;
        onDrawScrollBars(canvas);
    }

    protected boolean isVerticalScrollBarHidden() {
        return super.isVerticalScrollBarHidden() || !this.mDrawScrollBar;
    }

    public WindowInsets onApplyWindowInsets(WindowInsets insets) {
        if (getFitsSystemWindows()) {
            this.mStatusBarInset = insets.getSystemWindowInsetTop();
            insets.consumeSystemWindowInsets(false, true, false, false);
        }
        return insets;
    }
}
