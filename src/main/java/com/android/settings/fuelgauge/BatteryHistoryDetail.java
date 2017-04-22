package com.android.settings.fuelgauge;

import android.app.Fragment;
import android.content.Intent;
import android.os.BatteryStats;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import com.android.internal.os.BatteryStatsHelper;
import com.android.settings.R;

public class BatteryHistoryDetail extends Fragment {
    private Intent mBatteryBroadcast;
    private BatteryStats mStats;

    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        this.mStats = BatteryStatsHelper.statsFromFile(getActivity(), getArguments().getString("stats"));
        this.mBatteryBroadcast = (Intent) getArguments().getParcelable("broadcast");
    }

    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.battery_history_chart, null);
        ((BatteryHistoryChart) view.findViewById(R.id.battery_history_chart)).setStats(this.mStats, this.mBatteryBroadcast);
        return view;
    }
}
