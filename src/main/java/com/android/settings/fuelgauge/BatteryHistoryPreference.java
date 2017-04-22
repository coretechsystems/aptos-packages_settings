package com.android.settings.fuelgauge;

import android.content.Context;
import android.content.Intent;
import android.os.BatteryStats;
import android.preference.Preference;
import android.view.View;
import android.view.ViewGroup;
import com.android.settings.R;

public class BatteryHistoryPreference extends Preference {
    private final Intent mBatteryBroadcast;
    private BatteryHistoryChart mChart;
    private boolean mHideLabels;
    private View mLabelHeader;
    private final BatteryStats mStats;

    public BatteryHistoryPreference(Context context, BatteryStats stats, Intent batteryBroadcast) {
        super(context);
        setLayoutResource(R.layout.preference_batteryhistory);
        this.mStats = stats;
        this.mBatteryBroadcast = batteryBroadcast;
    }

    public void setHideLabels(boolean hide) {
        if (this.mHideLabels != hide) {
            this.mHideLabels = hide;
            if (this.mLabelHeader != null) {
                this.mLabelHeader.setVisibility(hide ? 8 : 0);
            }
        }
    }

    protected void onBindView(View view) {
        super.onBindView(view);
        BatteryHistoryChart chart = (BatteryHistoryChart) view.findViewById(R.id.battery_history_chart);
        if (this.mChart == null) {
            chart.setStats(this.mStats, this.mBatteryBroadcast);
            this.mChart = chart;
        } else {
            ViewGroup parent = (ViewGroup) chart.getParent();
            int index = parent.indexOfChild(chart);
            parent.removeViewAt(index);
            if (this.mChart.getParent() != null) {
                ((ViewGroup) this.mChart.getParent()).removeView(this.mChart);
            }
            parent.addView(this.mChart, index);
        }
        this.mLabelHeader = view.findViewById(R.id.labelsHeader);
        this.mLabelHeader.setVisibility(this.mHideLabels ? 8 : 0);
    }
}
