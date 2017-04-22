package com.android.settings.dashboard;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import com.android.settings.R;
import com.android.settings.Utils;

public class DashboardTileView extends FrameLayout implements OnClickListener {
    private int mColSpan;
    private View mDivider;
    private ImageView mImageView;
    private TextView mStatusTextView;
    private DashboardTile mTile;
    private TextView mTitleTextView;

    public DashboardTileView(Context context) {
        this(context, null);
    }

    public DashboardTileView(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.mColSpan = 1;
        View view = LayoutInflater.from(context).inflate(R.layout.dashboard_tile, this);
        this.mImageView = (ImageView) view.findViewById(R.id.icon);
        this.mTitleTextView = (TextView) view.findViewById(R.id.title);
        this.mStatusTextView = (TextView) view.findViewById(R.id.status);
        this.mDivider = view.findViewById(R.id.tile_divider);
        setOnClickListener(this);
        setBackgroundResource(R.drawable.dashboard_tile_background);
        setFocusable(true);
    }

    public TextView getTitleTextView() {
        return this.mTitleTextView;
    }

    public TextView getStatusTextView() {
        return this.mStatusTextView;
    }

    public ImageView getImageView() {
        return this.mImageView;
    }

    public void setTile(DashboardTile tile) {
        this.mTile = tile;
    }

    public void setDividerVisibility(boolean visible) {
        this.mDivider.setVisibility(visible ? 0 : 8);
    }

    int getColumnSpan() {
        return this.mColSpan;
    }

    public void onClick(View v) {
        if (this.mTile.fragment != null) {
            Utils.startWithFragment(getContext(), this.mTile.fragment, this.mTile.fragmentArguments, null, 0, this.mTile.titleRes, this.mTile.getTitle(getResources()));
        } else if (this.mTile.intent != null) {
            getContext().startActivity(this.mTile.intent);
        }
    }
}
