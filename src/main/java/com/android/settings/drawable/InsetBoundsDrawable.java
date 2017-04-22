package com.android.settings.drawable;

import android.graphics.drawable.Drawable;

public class InsetBoundsDrawable extends DrawableWrapper {
    private final int mInsetBoundsSides;

    public InsetBoundsDrawable(Drawable drawable, int insetBoundsSides) {
        super(drawable);
        this.mInsetBoundsSides = insetBoundsSides;
    }

    public void setBounds(int left, int top, int right, int bottom) {
        super.setBounds(this.mInsetBoundsSides + left, top, right - this.mInsetBoundsSides, bottom);
    }
}
