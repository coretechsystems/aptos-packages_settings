package com.android.settings.location;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceCategory;
import android.preference.PreferenceGroup;
import android.preference.PreferenceScreen;
import android.util.Log;
import android.widget.Switch;
import com.android.settings.R;
import com.android.settings.SettingsActivity;
import com.android.settings.widget.SwitchBar;
import com.android.settings.widget.SwitchBar.OnSwitchChangeListener;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class LocationSettings extends LocationSettingsBase implements OnSwitchChangeListener {
    private SettingsInjector injector;
    private PreferenceCategory mCategoryRecentLocationRequests;
    private Preference mLocationMode;
    private BroadcastReceiver mReceiver;
    private Switch mSwitch;
    private SwitchBar mSwitchBar;
    private boolean mValidListener = false;

    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        this.mSwitchBar = ((SettingsActivity) getActivity()).getSwitchBar();
        this.mSwitch = this.mSwitchBar.getSwitch();
        this.mSwitchBar.show();
    }

    public void onDestroyView() {
        super.onDestroyView();
        this.mSwitchBar.hide();
    }

    public void onResume() {
        super.onResume();
        createPreferenceHierarchy();
        if (!this.mValidListener) {
            this.mSwitchBar.addOnSwitchChangeListener(this);
            this.mValidListener = true;
        }
    }

    public void onPause() {
        try {
            getActivity().unregisterReceiver(this.mReceiver);
        } catch (RuntimeException e) {
            if (Log.isLoggable("LocationSettings", 2)) {
                Log.v("LocationSettings", "Swallowing " + e);
            }
        }
        if (this.mValidListener) {
            this.mSwitchBar.removeOnSwitchChangeListener(this);
            this.mValidListener = false;
        }
        super.onPause();
    }

    private void addPreferencesSorted(List<Preference> prefs, PreferenceGroup container) {
        Collections.sort(prefs, new Comparator<Preference>() {
            public int compare(Preference lhs, Preference rhs) {
                return lhs.getTitle().toString().compareTo(rhs.getTitle().toString());
            }
        });
        for (Preference entry : prefs) {
            container.addPreference(entry);
        }
    }

    private PreferenceScreen createPreferenceHierarchy() {
        final SettingsActivity activity = (SettingsActivity) getActivity();
        PreferenceScreen root = getPreferenceScreen();
        if (root != null) {
            root.removeAll();
        }
        addPreferencesFromResource(R.xml.location_settings);
        root = getPreferenceScreen();
        this.mLocationMode = root.findPreference("location_mode");
        this.mLocationMode.setOnPreferenceClickListener(new OnPreferenceClickListener() {
            public boolean onPreferenceClick(Preference preference) {
                activity.startPreferencePanel(LocationMode.class.getName(), null, R.string.location_mode_screen_title, null, LocationSettings.this, 0);
                return true;
            }
        });
        this.mCategoryRecentLocationRequests = (PreferenceCategory) root.findPreference("recent_location_requests");
        List<Preference> recentLocationRequests = new RecentLocationApps(activity).getAppList();
        if (recentLocationRequests.size() > 0) {
            addPreferencesSorted(recentLocationRequests, this.mCategoryRecentLocationRequests);
        } else {
            Preference banner = new Preference(activity);
            banner.setLayoutResource(R.layout.location_list_no_item);
            banner.setTitle(R.string.location_no_recent_apps);
            banner.setSelectable(false);
            this.mCategoryRecentLocationRequests.addPreference(banner);
        }
        addLocationServices(activity, root);
        refreshLocationMode();
        return root;
    }

    private void addLocationServices(Context context, PreferenceScreen root) {
        PreferenceCategory categoryLocationServices = (PreferenceCategory) root.findPreference("location_services");
        this.injector = new SettingsInjector(context);
        List<Preference> locationServices = this.injector.getInjectedSettings();
        this.mReceiver = new BroadcastReceiver() {
            public void onReceive(Context context, Intent intent) {
                if (Log.isLoggable("LocationSettings", 3)) {
                    Log.d("LocationSettings", "Received settings change intent: " + intent);
                }
                LocationSettings.this.injector.reloadStatusMessages();
            }
        };
        IntentFilter filter = new IntentFilter();
        filter.addAction("android.location.InjectedSettingChanged");
        context.registerReceiver(this.mReceiver, filter);
        if (locationServices.size() > 0) {
            addPreferencesSorted(locationServices, categoryLocationServices);
        } else {
            root.removePreference(categoryLocationServices);
        }
    }

    public int getHelpResource() {
        return R.string.help_url_location_access;
    }

    public void onModeChanged(int mode, boolean restricted) {
        boolean enabled;
        boolean z;
        boolean z2 = true;
        switch (mode) {
            case 0:
                this.mLocationMode.setSummary(R.string.location_mode_location_off_title);
                break;
            case 1:
                this.mLocationMode.setSummary(R.string.location_mode_sensors_only_title);
                break;
            case 2:
                this.mLocationMode.setSummary(R.string.location_mode_battery_saving_title);
                break;
            case 3:
                this.mLocationMode.setSummary(R.string.location_mode_high_accuracy_title);
                break;
        }
        if (mode != 0) {
            enabled = true;
        } else {
            enabled = false;
        }
        SwitchBar switchBar = this.mSwitchBar;
        if (restricted) {
            z = false;
        } else {
            z = true;
        }
        switchBar.setEnabled(z);
        Preference preference = this.mLocationMode;
        if (!enabled || restricted) {
            z2 = false;
        }
        preference.setEnabled(z2);
        this.mCategoryRecentLocationRequests.setEnabled(enabled);
        if (enabled != this.mSwitch.isChecked()) {
            if (this.mValidListener) {
                this.mSwitchBar.removeOnSwitchChangeListener(this);
            }
            this.mSwitch.setChecked(enabled);
            if (this.mValidListener) {
                this.mSwitchBar.addOnSwitchChangeListener(this);
            }
        }
        this.injector.reloadStatusMessages();
    }

    public void onSwitchChanged(Switch switchView, boolean isChecked) {
        if (isChecked) {
            setLocationMode(3);
        } else {
            setLocationMode(0);
        }
    }
}
