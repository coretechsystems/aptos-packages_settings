package com.android.settings.wifi;

import android.app.Dialog;
import android.app.DialogFragment;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.NetworkScoreManager;
import android.net.NetworkScorerAppManager;
import android.net.NetworkScorerAppManager.NetworkScorerAppData;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceScreen;
import android.preference.SwitchPreference;
import android.provider.Settings.Global;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;
import com.android.settings.R;
import com.android.settings.Settings.WifiP2pSettingsActivity;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.Utils;

public class AdvancedWifiSettings extends SettingsPreferenceFragment implements OnPreferenceChangeListener {
    private IntentFilter mFilter;
    private NetworkScoreManager mNetworkScoreManager;
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals("android.net.wifi.LINK_CONFIGURATION_CHANGED") || action.equals("android.net.wifi.STATE_CHANGE")) {
                AdvancedWifiSettings.this.refreshWifiInfo();
            }
        }
    };
    private WifiManager mWifiManager;

    public static class WpsFragment extends DialogFragment {
        private static int mWpsSetup;

        public WpsFragment(int wpsSetup) {
            mWpsSetup = wpsSetup;
        }

        public Dialog onCreateDialog(Bundle savedInstanceState) {
            return new WpsDialog(getActivity(), mWpsSetup);
        }
    }

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.wifi_advanced_settings);
    }

    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        this.mWifiManager = (WifiManager) getSystemService("wifi");
        this.mFilter = new IntentFilter();
        this.mFilter.addAction("android.net.wifi.LINK_CONFIGURATION_CHANGED");
        this.mFilter.addAction("android.net.wifi.STATE_CHANGE");
        this.mNetworkScoreManager = (NetworkScoreManager) getSystemService("network_score");
    }

    public void onResume() {
        super.onResume();
        initPreferences();
        getActivity().registerReceiver(this.mReceiver, this.mFilter);
        refreshWifiInfo();
    }

    public void onPause() {
        super.onPause();
        getActivity().unregisterReceiver(this.mReceiver);
    }

    private void initPreferences() {
        SwitchPreference notifyOpenNetworks = (SwitchPreference) findPreference("notify_open_networks");
        notifyOpenNetworks.setChecked(Global.getInt(getContentResolver(), "wifi_networks_available_notification_on", 0) == 1);
        notifyOpenNetworks.setEnabled(this.mWifiManager.isWifiEnabled());
        ((SwitchPreference) findPreference("wifi_scan_always_available")).setChecked(Global.getInt(getContentResolver(), "wifi_scan_always_enabled", 0) == 1);
        Intent intent = new Intent("android.credentials.INSTALL_AS_USER");
        intent.setClassName("com.android.certinstaller", "com.android.certinstaller.CertInstallerMain");
        intent.putExtra("install_as_uid", 1010);
        findPreference("install_credentials").setIntent(intent);
        Context context = getActivity();
        SwitchPreference wifiAssistant = (SwitchPreference) findPreference("wifi_assistant");
        if (WifiSettings.getWifiAssistantApp(context) != null) {
            boolean checked = NetworkScorerAppManager.getActiveScorer(context) != null;
            wifiAssistant.setSummary(getResources().getString(R.string.wifi_automatically_manage_summary, new Object[]{scorer.mScorerName}));
            wifiAssistant.setOnPreferenceChangeListener(this);
            wifiAssistant.setChecked(checked);
        } else if (wifiAssistant != null) {
            getPreferenceScreen().removePreference(wifiAssistant);
        }
        findPreference("wifi_direct").setIntent(new Intent(context, WifiP2pSettingsActivity.class));
        findPreference("wps_push_button").setOnPreferenceClickListener(new OnPreferenceClickListener() {
            public boolean onPreferenceClick(Preference arg0) {
                new WpsFragment(0).show(AdvancedWifiSettings.this.getFragmentManager(), "wps_push_button");
                return true;
            }
        });
        findPreference("wps_pin_entry").setOnPreferenceClickListener(new OnPreferenceClickListener() {
            public boolean onPreferenceClick(Preference arg0) {
                new WpsFragment(1).show(AdvancedWifiSettings.this.getFragmentManager(), "wps_pin_entry");
                return true;
            }
        });
        ListPreference frequencyPref = (ListPreference) findPreference("frequency_band");
        if (this.mWifiManager.isDualBandSupported()) {
            frequencyPref.setOnPreferenceChangeListener(this);
            int value = this.mWifiManager.getFrequencyBand();
            if (value != -1) {
                frequencyPref.setValue(String.valueOf(value));
                updateFrequencyBandSummary(frequencyPref, value);
            } else {
                Log.e("AdvancedWifiSettings", "Failed to fetch frequency band");
            }
        } else if (frequencyPref != null) {
            getPreferenceScreen().removePreference(frequencyPref);
        }
        ListPreference sleepPolicyPref = (ListPreference) findPreference("sleep_policy");
        if (sleepPolicyPref != null) {
            if (Utils.isWifiOnly(context)) {
                sleepPolicyPref.setEntries(R.array.wifi_sleep_policy_entries_wifi_only);
            }
            sleepPolicyPref.setOnPreferenceChangeListener(this);
            String stringValue = String.valueOf(Global.getInt(getContentResolver(), "wifi_sleep_policy", 2));
            sleepPolicyPref.setValue(stringValue);
            updateSleepPolicySummary(sleepPolicyPref, stringValue);
        }
    }

    private void updateSleepPolicySummary(Preference sleepPolicyPref, String value) {
        if (value != null) {
            String[] values = getResources().getStringArray(R.array.wifi_sleep_policy_values);
            String[] summaries = getResources().getStringArray(Utils.isWifiOnly(getActivity()) ? R.array.wifi_sleep_policy_entries_wifi_only : R.array.wifi_sleep_policy_entries);
            int i = 0;
            while (i < values.length) {
                if (!value.equals(values[i]) || i >= summaries.length) {
                    i++;
                } else {
                    sleepPolicyPref.setSummary(summaries[i]);
                    return;
                }
            }
        }
        sleepPolicyPref.setSummary("");
        Log.e("AdvancedWifiSettings", "Invalid sleep policy value: " + value);
    }

    private void updateFrequencyBandSummary(Preference frequencyBandPref, int index) {
        frequencyBandPref.setSummary(getResources().getStringArray(R.array.wifi_frequency_band_entries)[index]);
    }

    public boolean onPreferenceTreeClick(PreferenceScreen screen, Preference preference) {
        int i = 0;
        String key = preference.getKey();
        ContentResolver contentResolver;
        String str;
        if ("notify_open_networks".equals(key)) {
            contentResolver = getContentResolver();
            str = "wifi_networks_available_notification_on";
            if (((SwitchPreference) preference).isChecked()) {
                i = 1;
            }
            Global.putInt(contentResolver, str, i);
            return true;
        } else if (!"wifi_scan_always_available".equals(key)) {
            return super.onPreferenceTreeClick(screen, preference);
        } else {
            contentResolver = getContentResolver();
            str = "wifi_scan_always_enabled";
            if (((SwitchPreference) preference).isChecked()) {
                i = 1;
            }
            Global.putInt(contentResolver, str, i);
            return true;
        }
    }

    public boolean onPreferenceChange(Preference preference, Object newValue) {
        Context context = getActivity();
        String key = preference.getKey();
        if ("frequency_band".equals(key)) {
            try {
                int value = Integer.parseInt((String) newValue);
                this.mWifiManager.setFrequencyBand(value, true);
                updateFrequencyBandSummary(preference, value);
            } catch (NumberFormatException e) {
                Toast.makeText(context, R.string.wifi_setting_frequency_band_error, 0).show();
                return false;
            }
        } else if ("wifi_assistant".equals(key)) {
            if (((Boolean) newValue).booleanValue()) {
                NetworkScorerAppData wifiAssistant = WifiSettings.getWifiAssistantApp(context);
                Intent intent = new Intent();
                if (wifiAssistant.mConfigurationActivityClassName != null) {
                    intent.setClassName(wifiAssistant.mPackageName, wifiAssistant.mConfigurationActivityClassName);
                } else {
                    intent.setAction("android.net.scoring.CHANGE_ACTIVE");
                    intent.putExtra("packageName", wifiAssistant.mPackageName);
                }
                startActivity(intent);
            } else {
                this.mNetworkScoreManager.setActiveScorer(null);
                return true;
            }
        }
        if ("sleep_policy".equals(key)) {
            try {
                String stringValue = (String) newValue;
                Global.putInt(getContentResolver(), "wifi_sleep_policy", Integer.parseInt(stringValue));
                updateSleepPolicySummary(preference, stringValue);
            } catch (NumberFormatException e2) {
                Toast.makeText(context, R.string.wifi_setting_sleep_policy_error, 0).show();
                return false;
            }
        }
        return true;
    }

    private void refreshWifiInfo() {
        Context context = getActivity();
        WifiInfo wifiInfo = this.mWifiManager.getConnectionInfo();
        Preference wifiMacAddressPref = findPreference("mac_address");
        String macAddress = wifiInfo == null ? null : wifiInfo.getMacAddress();
        if (TextUtils.isEmpty(macAddress)) {
            macAddress = context.getString(R.string.status_unavailable);
        }
        wifiMacAddressPref.setSummary(macAddress);
        wifiMacAddressPref.setSelectable(false);
        Preference wifiIpAddressPref = findPreference("current_ip_address");
        String ipAddress = Utils.getWifiIpAddresses(context);
        if (ipAddress == null) {
            ipAddress = context.getString(R.string.status_unavailable);
        }
        wifiIpAddressPref.setSummary(ipAddress);
        wifiIpAddressPref.setSelectable(false);
    }
}
