package com.android.settings.applications;

import android.content.Context;
import android.preference.Preference;
import android.view.View;
import com.android.settings.R;
import com.android.settings.applications.LinearColorBar.OnRegionTappedListener;

public class LinearColorPreference extends Preference {
    int mColoredRegions = 7;
    int mGreenColor = -13587888;
    float mGreenRatio;
    OnRegionTappedListener mOnRegionTappedListener;
    int mRedColor = -5615568;
    float mRedRatio;
    int mYellowColor = -5592528;
    float mYellowRatio;

    public LinearColorPreference(Context context) {
        super(context);
        setLayoutResource(R.layout.preference_linearcolor);
    }

    public void setRatios(float red, float yellow, float green) {
        this.mRedRatio = red;
        this.mYellowRatio = yellow;
        this.mGreenRatio = green;
        notifyChanged();
    }

    public void setColors(int red, int yellow, int green) {
        this.mRedColor = red;
        this.mYellowColor = yellow;
        this.mGreenColor = green;
        notifyChanged();
    }

    public void setColoredRegions(int regions) {
        this.mColoredRegions = regions;
        notifyChanged();
    }

    protected void onBindView(View view) {
        super.onBindView(view);
        LinearColorBar colors = (LinearColorBar) view.findViewById(R.id.linear_color_bar);
        colors.setShowIndicator(false);
        colors.setColors(this.mRedColor, this.mYellowColor, this.mGreenColor);
        colors.setRatios(this.mRedRatio, this.mYellowRatio, this.mGreenRatio);
        colors.setColoredRegions(this.mColoredRegions);
        colors.setOnRegionTappedListener(this.mOnRegionTappedListener);
    }
}
