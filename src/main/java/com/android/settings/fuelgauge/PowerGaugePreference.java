package com.android.settings.fuelgauge;

import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.preference.Preference;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import com.android.settings.R;
import com.android.settings.Utils;

public class PowerGaugePreference extends Preference {
    private final CharSequence mContentDescription;
    private BatteryEntry mInfo;
    private int mProgress;
    private CharSequence mProgressText;

    public PowerGaugePreference(Context context, Drawable icon, CharSequence contentDescription, BatteryEntry info) {
        super(context);
        setLayoutResource(R.layout.preference_app_percentage);
        if (icon == null) {
            icon = new ColorDrawable(0);
        }
        setIcon(icon);
        this.mInfo = info;
        this.mContentDescription = contentDescription;
    }

    public void setPercent(double percentOfMax, double percentOfTotal) {
        this.mProgress = (int) Math.ceil(percentOfMax);
        this.mProgressText = Utils.formatPercentage((int) (0.5d + percentOfTotal));
        notifyChanged();
    }

    BatteryEntry getInfo() {
        return this.mInfo;
    }

    protected void onBindView(View view) {
        super.onBindView(view);
        ((ProgressBar) view.findViewById(16908301)).setProgress(this.mProgress);
        ((TextView) view.findViewById(16908308)).setText(this.mProgressText);
        if (this.mContentDescription != null) {
            ((TextView) view.findViewById(16908310)).setContentDescription(this.mContentDescription);
        }
    }
}
