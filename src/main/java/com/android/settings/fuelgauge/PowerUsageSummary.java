package com.android.settings.fuelgauge;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryStats;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.UserHandle;
import android.os.UserManager;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceGroup;
import android.preference.PreferenceScreen;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import com.android.internal.os.BatterySipper;
import com.android.internal.os.BatterySipper.DrainType;
import com.android.internal.os.BatteryStatsHelper;
import com.android.internal.os.PowerProfile;
import com.android.settings.HelpUtils;
import com.android.settings.R;
import com.android.settings.SettingsActivity;
import com.android.settings.Utils;
import java.util.List;

public class PowerUsageSummary extends PreferenceFragment {
    private PreferenceGroup mAppListGroup;
    private BroadcastReceiver mBatteryInfoReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            if ("android.intent.action.BATTERY_CHANGED".equals(intent.getAction()) && PowerUsageSummary.this.updateBatteryStatus(intent) && !PowerUsageSummary.this.mHandler.hasMessages(100)) {
                PowerUsageSummary.this.mHandler.sendEmptyMessageDelayed(100, 500);
            }
        }
    };
    private String mBatteryLevel;
    private String mBatteryStatus;
    Handler mHandler = new Handler() {
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case 1:
                    BatteryEntry entry = msg.obj;
                    PowerGaugePreference pgp = (PowerGaugePreference) PowerUsageSummary.this.findPreference(Integer.toString(entry.sipper.uidObj.getUid()));
                    if (pgp != null) {
                        pgp.setIcon(PowerUsageSummary.this.mUm.getBadgedIconForUser(entry.getIcon(), new UserHandle(UserHandle.getUserId(entry.sipper.getUid()))));
                        pgp.setTitle(entry.name);
                        break;
                    }
                    break;
                case 2:
                    Activity activity = PowerUsageSummary.this.getActivity();
                    if (activity != null) {
                        activity.reportFullyDrawn();
                        break;
                    }
                    break;
                case 100:
                    PowerUsageSummary.this.mStatsHelper.clearStats();
                    PowerUsageSummary.this.refreshStats();
                    break;
            }
            super.handleMessage(msg);
        }
    };
    private BatteryHistoryPreference mHistPref;
    private BatteryStatsHelper mStatsHelper;
    private int mStatsType = 0;
    private UserManager mUm;

    public void onAttach(Activity activity) {
        super.onAttach(activity);
        this.mUm = (UserManager) activity.getSystemService("user");
        this.mStatsHelper = new BatteryStatsHelper(activity, true);
    }

    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        this.mStatsHelper.create(icicle);
        addPreferencesFromResource(R.xml.power_usage_summary);
        this.mAppListGroup = (PreferenceGroup) findPreference("app_list");
        setHasOptionsMenu(true);
    }

    public void onStart() {
        super.onStart();
        this.mStatsHelper.clearStats();
    }

    public void onResume() {
        super.onResume();
        BatteryStatsHelper.dropFile(getActivity(), "tmp_bat_history.bin");
        updateBatteryStatus(getActivity().registerReceiver(this.mBatteryInfoReceiver, new IntentFilter("android.intent.action.BATTERY_CHANGED")));
        if (this.mHandler.hasMessages(100)) {
            this.mHandler.removeMessages(100);
            this.mStatsHelper.clearStats();
        }
        refreshStats();
    }

    public void onPause() {
        BatteryEntry.stopRequestQueue();
        this.mHandler.removeMessages(1);
        getActivity().unregisterReceiver(this.mBatteryInfoReceiver);
        super.onPause();
    }

    public void onStop() {
        super.onStop();
        this.mHandler.removeMessages(100);
    }

    public void onDestroy() {
        super.onDestroy();
        if (getActivity().isChangingConfigurations()) {
            this.mStatsHelper.storeState();
            BatteryEntry.clearUidCache();
        }
    }

    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        if (preference instanceof BatteryHistoryPreference) {
            this.mStatsHelper.storeStatsHistoryInFile("tmp_bat_history.bin");
            Bundle args = new Bundle();
            args.putString("stats", "tmp_bat_history.bin");
            args.putParcelable("broadcast", this.mStatsHelper.getBatteryBroadcast());
            ((SettingsActivity) getActivity()).startPreferencePanel(BatteryHistoryDetail.class.getName(), args, R.string.history_details_title, null, null, 0);
            return super.onPreferenceTreeClick(preferenceScreen, preference);
        } else if (!(preference instanceof PowerGaugePreference)) {
            return false;
        } else {
            PowerUsageDetail.startBatteryDetailPage((SettingsActivity) getActivity(), this.mStatsHelper, this.mStatsType, ((PowerGaugePreference) preference).getInfo(), true);
            return super.onPreferenceTreeClick(preferenceScreen, preference);
        }
    }

    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        menu.add(0, 2, 0, R.string.menu_stats_refresh).setIcon(17302525).setAlphabeticShortcut('r').setShowAsAction(5);
        menu.add(0, 3, 0, R.string.battery_saver).setShowAsAction(0);
        String helpUrl = getResources().getString(R.string.help_url_battery);
        if (!TextUtils.isEmpty(helpUrl)) {
            HelpUtils.prepareHelpMenuItem(getActivity(), menu.add(0, 4, 0, R.string.help_label), helpUrl);
        }
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case 1:
                if (this.mStatsType == 0) {
                    this.mStatsType = 2;
                } else {
                    this.mStatsType = 0;
                }
                refreshStats();
                return true;
            case 2:
                this.mStatsHelper.clearStats();
                refreshStats();
                this.mHandler.removeMessages(100);
                return true;
            case 3:
                ((SettingsActivity) getActivity()).startPreferencePanel(BatterySaverSettings.class.getName(), null, R.string.battery_saver, null, null, 0);
                return true;
            default:
                return false;
        }
    }

    private void addNotAvailableMessage() {
        Preference notAvailable = new Preference(getActivity());
        notAvailable.setTitle(R.string.power_usage_not_available);
        this.mHistPref.setHideLabels(true);
        this.mAppListGroup.addPreference(notAvailable);
    }

    private boolean updateBatteryStatus(Intent intent) {
        if (intent != null) {
            String batteryLevel = Utils.getBatteryPercentage(intent);
            String batteryStatus = Utils.getBatteryStatus(getResources(), intent);
            if (!(batteryLevel.equals(this.mBatteryLevel) && batteryStatus.equals(this.mBatteryStatus))) {
                this.mBatteryLevel = batteryLevel;
                this.mBatteryStatus = batteryStatus;
                return true;
            }
        }
        return false;
    }

    private void refreshStats() {
        this.mAppListGroup.removeAll();
        this.mAppListGroup.setOrderingAsAdded(false);
        this.mHistPref = new BatteryHistoryPreference(getActivity(), this.mStatsHelper.getStats(), this.mStatsHelper.getBatteryBroadcast());
        this.mHistPref.setOrder(-1);
        this.mAppListGroup.addPreference(this.mHistPref);
        boolean addedSome = false;
        PowerProfile powerProfile = this.mStatsHelper.getPowerProfile();
        BatteryStats stats = this.mStatsHelper.getStats();
        if (powerProfile.getAveragePower("screen.full") >= 10.0d) {
            int dischargeAmount;
            this.mStatsHelper.refreshStats(0, this.mUm.getUserProfiles());
            List<BatterySipper> usageList = this.mStatsHelper.getUsageList();
            if (stats != null) {
                dischargeAmount = stats.getDischargeAmount(this.mStatsType);
            } else {
                dischargeAmount = 0;
            }
            int numSippers = usageList.size();
            for (int i = 0; i < numSippers; i++) {
                BatterySipper sipper = (BatterySipper) usageList.get(i);
                if (sipper.value * 3600.0d >= 5.0d) {
                    double percentOfTotal = (sipper.value / this.mStatsHelper.getTotalPower()) * ((double) dischargeAmount);
                    if (((int) (0.5d + percentOfTotal)) >= 1 && ((sipper.drainType != DrainType.OVERCOUNTED || (sipper.value >= (this.mStatsHelper.getMaxRealPower() * 2.0d) / 3.0d && percentOfTotal >= 10.0d && !"user".equals(Build.TYPE))) && (sipper.drainType != DrainType.UNACCOUNTED || (sipper.value >= this.mStatsHelper.getMaxRealPower() / 2.0d && percentOfTotal >= 5.0d && !"user".equals(Build.TYPE))))) {
                        UserHandle userHandle = new UserHandle(UserHandle.getUserId(sipper.getUid()));
                        BatteryEntry entry = new BatteryEntry(getActivity(), this.mHandler, this.mUm, sipper);
                        Preference powerGaugePreference = new PowerGaugePreference(getActivity(), this.mUm.getBadgedIconForUser(entry.getIcon(), userHandle), this.mUm.getBadgedLabelForUser(entry.getLabel(), userHandle), entry);
                        double percentOfMax = (sipper.value * 100.0d) / this.mStatsHelper.getMaxPower();
                        sipper.percent = percentOfTotal;
                        powerGaugePreference.setTitle(entry.getLabel());
                        powerGaugePreference.setOrder(i + 1);
                        powerGaugePreference.setPercent(percentOfMax, percentOfTotal);
                        if (sipper.uidObj != null) {
                            powerGaugePreference.setKey(Integer.toString(sipper.uidObj.getUid()));
                        }
                        addedSome = true;
                        this.mAppListGroup.addPreference(powerGaugePreference);
                        if (this.mAppListGroup.getPreferenceCount() > 11) {
                            break;
                        }
                    }
                }
            }
        }
        if (!addedSome) {
            addNotAvailableMessage();
        }
        BatteryEntry.startRequestQueue();
    }
}
