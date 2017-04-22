package com.android.settings.deviceinfo;

import android.content.Context;
import android.preference.Preference;
import android.util.AttributeSet;
import android.view.View;
import com.android.settings.R;
import com.android.settings.deviceinfo.PercentageBarChart.Entry;
import com.google.android.collect.Lists;
import java.util.Collections;
import java.util.List;

public class UsageBarPreference extends Preference {
    private PercentageBarChart mChart;
    private final List<Entry> mEntries;

    public UsageBarPreference(Context context) {
        this(context, null);
    }

    public UsageBarPreference(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public UsageBarPreference(Context context, AttributeSet attrs, int defStyle) {
        this(context, attrs, defStyle, 0);
    }

    public UsageBarPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        this.mChart = null;
        this.mEntries = Lists.newArrayList();
        setLayoutResource(R.layout.preference_memoryusage);
    }

    public void addEntry(int order, float percentage, int color) {
        this.mEntries.add(PercentageBarChart.createEntry(order, percentage, color));
        Collections.sort(this.mEntries);
    }

    protected void onBindView(View view) {
        super.onBindView(view);
        this.mChart = (PercentageBarChart) view.findViewById(R.id.percentage_bar_chart);
        this.mChart.setEntries(this.mEntries);
    }

    public void commit() {
        if (this.mChart != null) {
            this.mChart.invalidate();
        }
    }

    public void clear() {
        this.mEntries.clear();
    }
}
