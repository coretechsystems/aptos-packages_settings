package com.android.settings.dashboard;

import android.content.Context;
import android.content.res.Resources;
import android.util.AttributeSet;
import android.view.View.MeasureSpec;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import com.android.settings.R;

public class DashboardContainerView extends ViewGroup {
    private float mCellGapX;
    private float mCellGapY;
    private int mNumColumns;
    private int mNumRows;

    public DashboardContainerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        Resources res = context.getResources();
        this.mCellGapX = res.getDimension(R.dimen.dashboard_cell_gap_x);
        this.mCellGapY = res.getDimension(R.dimen.dashboard_cell_gap_y);
        this.mNumColumns = res.getInteger(R.integer.dashboard_num_columns);
    }

    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        float cellWidth = (float) Math.ceil((double) (((float) ((int) (((float) ((width - getPaddingLeft()) - getPaddingRight())) - (((float) (this.mNumColumns - 1)) * this.mCellGapX)))) / ((float) this.mNumColumns)));
        int N = getChildCount();
        int cellHeight = 0;
        int cursor = 0;
        for (int i = 0; i < N; i++) {
            DashboardTileView v = (DashboardTileView) getChildAt(i);
            if (v.getVisibility() != 8) {
                LayoutParams lp = v.getLayoutParams();
                int colSpan = v.getColumnSpan();
                lp.width = (int) ((((float) colSpan) * cellWidth) + (((float) (colSpan - 1)) * this.mCellGapX));
                v.measure(getChildMeasureSpec(widthMeasureSpec, 0, lp.width), getChildMeasureSpec(heightMeasureSpec, 0, lp.height));
                if (cellHeight <= 0) {
                    cellHeight = v.getMeasuredHeight();
                }
                lp.height = cellHeight;
                cursor += colSpan;
            }
        }
        this.mNumRows = (int) Math.ceil((double) (((float) cursor) / ((float) this.mNumColumns)));
        setMeasuredDimension(width, (((int) (((float) (this.mNumRows * cellHeight)) + (((float) (this.mNumRows - 1)) * this.mCellGapY))) + getPaddingTop()) + getPaddingBottom());
    }

    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int N = getChildCount();
        boolean isLayoutRtl = isLayoutRtl();
        int width = getWidth();
        int x = getPaddingStart();
        int y = getPaddingTop();
        int cursor = 0;
        for (int i = 0; i < N; i++) {
            DashboardTileView child = (DashboardTileView) getChildAt(i);
            LayoutParams lp = child.getLayoutParams();
            if (child.getVisibility() != 8) {
                int childLeft;
                int col = cursor % this.mNumColumns;
                int colSpan = child.getColumnSpan();
                int childWidth = lp.width;
                int childHeight = lp.height;
                int row = cursor / this.mNumColumns;
                if (row == this.mNumRows - 1) {
                    child.setDividerVisibility(false);
                }
                if (col + colSpan > this.mNumColumns) {
                    x = getPaddingStart();
                    y = (int) (((float) y) + (((float) childHeight) + this.mCellGapY));
                    row++;
                }
                if (isLayoutRtl) {
                    childLeft = (width - x) - childWidth;
                } else {
                    childLeft = x;
                }
                int childTop = y;
                child.layout(childLeft, childTop, childLeft + childWidth, childTop + childHeight);
                cursor += child.getColumnSpan();
                if (cursor < (row + 1) * this.mNumColumns) {
                    x = (int) (((float) x) + (((float) childWidth) + this.mCellGapX));
                } else {
                    x = getPaddingStart();
                    y = (int) (((float) y) + (((float) childHeight) + this.mCellGapY));
                }
            }
        }
    }
}
