package com.android.settings.wifi;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.preference.SwitchPreference;
import android.provider.Settings.Global;
import android.provider.Settings.SettingNotFoundException;
import com.android.settings.R;

public class WifiApEnabler {
    ConnectivityManager mCm;
    private final Context mContext;
    private final IntentFilter mIntentFilter;
    private final CharSequence mOriginalSummary;
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if ("android.net.wifi.WIFI_AP_STATE_CHANGED".equals(action)) {
                WifiApEnabler.this.handleWifiApStateChanged(intent.getIntExtra("wifi_state", 14));
            } else if ("android.net.conn.TETHER_STATE_CHANGED".equals(action)) {
                WifiApEnabler.this.updateTetherState(intent.getStringArrayListExtra("availableArray").toArray(), intent.getStringArrayListExtra("activeArray").toArray(), intent.getStringArrayListExtra("erroredArray").toArray());
            } else if ("android.intent.action.AIRPLANE_MODE".equals(action)) {
                WifiApEnabler.this.enableWifiSwitch();
            }
        }
    };
    private final SwitchPreference mSwitch;
    private WifiManager mWifiManager;
    private String[] mWifiRegexs;

    public WifiApEnabler(Context context, SwitchPreference switchPreference) {
        this.mContext = context;
        this.mSwitch = switchPreference;
        this.mOriginalSummary = switchPreference.getSummary();
        switchPreference.setPersistent(false);
        this.mWifiManager = (WifiManager) context.getSystemService("wifi");
        this.mCm = (ConnectivityManager) this.mContext.getSystemService("connectivity");
        this.mWifiRegexs = this.mCm.getTetherableWifiRegexs();
        this.mIntentFilter = new IntentFilter("android.net.wifi.WIFI_AP_STATE_CHANGED");
        this.mIntentFilter.addAction("android.net.conn.TETHER_STATE_CHANGED");
        this.mIntentFilter.addAction("android.intent.action.AIRPLANE_MODE");
    }

    public void resume() {
        this.mContext.registerReceiver(this.mReceiver, this.mIntentFilter);
        enableWifiSwitch();
    }

    public void pause() {
        this.mContext.unregisterReceiver(this.mReceiver);
    }

    private void enableWifiSwitch() {
        boolean isAirplaneMode;
        if (Global.getInt(this.mContext.getContentResolver(), "airplane_mode_on", 0) != 0) {
            isAirplaneMode = true;
        } else {
            isAirplaneMode = false;
        }
        if (isAirplaneMode) {
            this.mSwitch.setSummary(this.mOriginalSummary);
            this.mSwitch.setEnabled(false);
            return;
        }
        this.mSwitch.setEnabled(true);
    }

    public void setSoftapEnabled(boolean enable) {
        ContentResolver cr = this.mContext.getContentResolver();
        int wifiState = this.mWifiManager.getWifiState();
        if (enable && (wifiState == 2 || wifiState == 3)) {
            this.mWifiManager.setWifiEnabled(false);
            Global.putInt(cr, "wifi_saved_state", 1);
        }
        if (this.mWifiManager.setWifiApEnabled(null, enable)) {
            this.mSwitch.setEnabled(false);
        } else {
            this.mSwitch.setSummary(R.string.wifi_error);
        }
        if (!enable) {
            int wifiSavedState = 0;
            try {
                wifiSavedState = Global.getInt(cr, "wifi_saved_state");
            } catch (SettingNotFoundException e) {
            }
            if (wifiSavedState == 1) {
                this.mWifiManager.setWifiEnabled(true);
                Global.putInt(cr, "wifi_saved_state", 0);
            }
        }
    }

    public void updateConfigSummary(WifiConfiguration wifiConfig) {
        String s = this.mContext.getString(17040553);
        SwitchPreference switchPreference = this.mSwitch;
        String string = this.mContext.getString(R.string.wifi_tether_enabled_subtext);
        Object[] objArr = new Object[1];
        if (wifiConfig != null) {
            s = wifiConfig.SSID;
        }
        objArr[0] = s;
        switchPreference.setSummary(String.format(string, objArr));
    }

    private void updateTetherState(Object[] available, Object[] tethered, Object[] errored) {
        String s;
        boolean wifiTethered = false;
        boolean wifiErrored = false;
        for (String s2 : tethered) {
            for (String regex : this.mWifiRegexs) {
                if (s2.matches(regex)) {
                    wifiTethered = true;
                }
            }
        }
        for (Object o : errored) {
            s2 = (String) o;
            for (String regex2 : this.mWifiRegexs) {
                if (s2.matches(regex2)) {
                    wifiErrored = true;
                }
            }
        }
        if (wifiTethered) {
            updateConfigSummary(this.mWifiManager.getWifiApConfiguration());
        } else if (wifiErrored) {
            this.mSwitch.setSummary(R.string.wifi_error);
        }
    }

    private void handleWifiApStateChanged(int state) {
        switch (state) {
            case 10:
                this.mSwitch.setSummary(R.string.wifi_tether_stopping);
                this.mSwitch.setEnabled(false);
                return;
            case 11:
                this.mSwitch.setChecked(false);
                this.mSwitch.setSummary(this.mOriginalSummary);
                enableWifiSwitch();
                return;
            case 12:
                this.mSwitch.setSummary(R.string.wifi_tether_starting);
                this.mSwitch.setEnabled(false);
                return;
            case 13:
                this.mSwitch.setChecked(true);
                this.mSwitch.setEnabled(true);
                return;
            default:
                this.mSwitch.setChecked(false);
                this.mSwitch.setSummary(R.string.wifi_error);
                enableWifiSwitch();
                return;
        }
    }
}
