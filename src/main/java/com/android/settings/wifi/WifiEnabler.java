package com.android.settings.wifi;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.NetworkInfo;
import android.net.NetworkInfo.DetailedState;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Message;
import android.widget.Switch;
import android.widget.Toast;
import com.android.settings.R;
import com.android.settings.WirelessSettings;
import com.android.settings.search.Index;
import com.android.settings.widget.SwitchBar;
import com.android.settings.widget.SwitchBar.OnSwitchChangeListener;
import java.util.concurrent.atomic.AtomicBoolean;

public class WifiEnabler implements OnSwitchChangeListener {
    private AtomicBoolean mConnected = new AtomicBoolean(false);
    private Context mContext;
    private Handler mHandler = new Handler() {
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case 0:
                    Index.getInstance(WifiEnabler.this.mContext).updateFromClassNameResource(WifiSettings.class.getName(), true, msg.getData().getBoolean("is_wifi_on"));
                    return;
                default:
                    return;
            }
        }
    };
    private final IntentFilter mIntentFilter;
    private boolean mListeningToOnSwitchChange = false;
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if ("android.net.wifi.WIFI_STATE_CHANGED".equals(action)) {
                WifiEnabler.this.handleWifiStateChanged(intent.getIntExtra("wifi_state", 4));
            } else if ("android.net.wifi.supplicant.STATE_CHANGE".equals(action)) {
                if (!WifiEnabler.this.mConnected.get()) {
                    WifiEnabler.this.handleStateChanged(WifiInfo.getDetailedStateOf((SupplicantState) intent.getParcelableExtra("newState")));
                }
            } else if ("android.net.wifi.STATE_CHANGE".equals(action)) {
                NetworkInfo info = (NetworkInfo) intent.getParcelableExtra("networkInfo");
                WifiEnabler.this.mConnected.set(info.isConnected());
                WifiEnabler.this.handleStateChanged(info.getDetailedState());
            }
        }
    };
    private boolean mStateMachineEvent;
    private SwitchBar mSwitchBar;
    private final WifiManager mWifiManager;

    public WifiEnabler(Context context, SwitchBar switchBar) {
        this.mContext = context;
        this.mSwitchBar = switchBar;
        this.mWifiManager = (WifiManager) context.getSystemService("wifi");
        this.mIntentFilter = new IntentFilter("android.net.wifi.WIFI_STATE_CHANGED");
        this.mIntentFilter.addAction("android.net.wifi.supplicant.STATE_CHANGE");
        this.mIntentFilter.addAction("android.net.wifi.STATE_CHANGE");
        setupSwitchBar();
    }

    public void setupSwitchBar() {
        handleWifiStateChanged(this.mWifiManager.getWifiState());
        if (!this.mListeningToOnSwitchChange) {
            this.mSwitchBar.addOnSwitchChangeListener(this);
            this.mListeningToOnSwitchChange = true;
        }
        this.mSwitchBar.show();
    }

    public void teardownSwitchBar() {
        if (this.mListeningToOnSwitchChange) {
            this.mSwitchBar.removeOnSwitchChangeListener(this);
            this.mListeningToOnSwitchChange = false;
        }
        this.mSwitchBar.hide();
    }

    public void resume(Context context) {
        this.mContext = context;
        this.mContext.registerReceiver(this.mReceiver, this.mIntentFilter);
        if (!this.mListeningToOnSwitchChange) {
            this.mSwitchBar.addOnSwitchChangeListener(this);
            this.mListeningToOnSwitchChange = true;
        }
    }

    public void pause() {
        this.mContext.unregisterReceiver(this.mReceiver);
        if (this.mListeningToOnSwitchChange) {
            this.mSwitchBar.removeOnSwitchChangeListener(this);
            this.mListeningToOnSwitchChange = false;
        }
    }

    private void handleWifiStateChanged(int state) {
        switch (state) {
            case 0:
                this.mSwitchBar.setEnabled(false);
                return;
            case 1:
                setSwitchBarChecked(false);
                this.mSwitchBar.setEnabled(true);
                updateSearchIndex(false);
                return;
            case 2:
                this.mSwitchBar.setEnabled(false);
                return;
            case 3:
                setSwitchBarChecked(true);
                this.mSwitchBar.setEnabled(true);
                updateSearchIndex(true);
                return;
            default:
                setSwitchBarChecked(false);
                this.mSwitchBar.setEnabled(true);
                updateSearchIndex(false);
                return;
        }
    }

    private void updateSearchIndex(boolean isWiFiOn) {
        this.mHandler.removeMessages(0);
        Message msg = new Message();
        msg.what = 0;
        msg.getData().putBoolean("is_wifi_on", isWiFiOn);
        this.mHandler.sendMessage(msg);
    }

    private void setSwitchBarChecked(boolean checked) {
        this.mStateMachineEvent = true;
        this.mSwitchBar.setChecked(checked);
        this.mStateMachineEvent = false;
    }

    private void handleStateChanged(DetailedState state) {
    }

    public void onSwitchChanged(Switch switchView, boolean isChecked) {
        if (!this.mStateMachineEvent) {
            if (!isChecked || WirelessSettings.isRadioAllowed(this.mContext, "wifi")) {
                int wifiApState = this.mWifiManager.getWifiApState();
                if (isChecked && (wifiApState == 12 || wifiApState == 13)) {
                    this.mWifiManager.setWifiApEnabled(null, false);
                }
                if (!this.mWifiManager.setWifiEnabled(isChecked)) {
                    this.mSwitchBar.setEnabled(true);
                    Toast.makeText(this.mContext, R.string.wifi_error, 0).show();
                    return;
                }
                return;
            }
            Toast.makeText(this.mContext, R.string.wifi_in_airplane_mode, 0).show();
            this.mSwitchBar.setChecked(false);
        }
    }
}
