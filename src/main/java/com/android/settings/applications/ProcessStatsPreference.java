package com.android.settings.applications;

import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.preference.Preference;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import com.android.settings.R;
import com.android.settings.Utils;

public class ProcessStatsPreference extends Preference {
    private ProcStatsEntry mEntry;
    private int mProgress;
    private CharSequence mProgressText;

    public ProcessStatsPreference(Context context) {
        this(context, null);
    }

    public ProcessStatsPreference(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ProcessStatsPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public ProcessStatsPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        setLayoutResource(R.layout.preference_app_percentage);
    }

    public void init(Drawable icon, ProcStatsEntry entry) {
        this.mEntry = entry;
        if (icon == null) {
            icon = new ColorDrawable(0);
        }
        setIcon(icon);
    }

    public ProcStatsEntry getEntry() {
        return this.mEntry;
    }

    public void setPercent(double percentOfWeight, double percentOfTime) {
        this.mProgress = (int) Math.ceil(percentOfWeight);
        this.mProgressText = Utils.formatPercentage((int) percentOfTime);
        notifyChanged();
    }

    protected void onBindView(View view) {
        super.onBindView(view);
        ((ProgressBar) view.findViewById(16908301)).setProgress(this.mProgress);
        ((TextView) view.findViewById(16908308)).setText(this.mProgressText);
    }
}
