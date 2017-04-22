package com.android.settings;

import android.app.Activity;
import android.app.Dialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothPan;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothProfile.ServiceListener;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.os.UserManager;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceScreen;
import android.preference.SwitchPreference;
import android.webkit.WebView;
import android.widget.TextView;
import com.android.settings.wifi.WifiApDialog;
import com.android.settings.wifi.WifiApEnabler;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicReference;

public class TetherSettings extends SettingsPreferenceFragment implements OnClickListener, OnPreferenceChangeListener {
    private boolean mBluetoothEnableForTether;
    private AtomicReference<BluetoothPan> mBluetoothPan = new AtomicReference();
    private String[] mBluetoothRegexs;
    private SwitchPreference mBluetoothTether;
    private Preference mCreateNetwork;
    private WifiApDialog mDialog;
    private SwitchPreference mEnableWifiAp;
    private boolean mMassStorageActive;
    private ServiceListener mProfileServiceListener = new ServiceListener() {
        public void onServiceConnected(int profile, BluetoothProfile proxy) {
            TetherSettings.this.mBluetoothPan.set((BluetoothPan) proxy);
        }

        public void onServiceDisconnected(int profile) {
            TetherSettings.this.mBluetoothPan.set(null);
        }
    };
    private String[] mProvisionApp;
    private String[] mSecurityType;
    private BroadcastReceiver mTetherChangeReceiver;
    private int mTetherChoice = -1;
    private UserManager mUm;
    private boolean mUnavailable;
    private boolean mUsbConnected;
    private String[] mUsbRegexs;
    private SwitchPreference mUsbTether;
    private WebView mView;
    private WifiApEnabler mWifiApEnabler;
    private WifiConfiguration mWifiConfig = null;
    private WifiManager mWifiManager;
    private String[] mWifiRegexs;

    private class TetherChangeReceiver extends BroadcastReceiver {
        private TetherChangeReceiver() {
        }

        public void onReceive(Context content, Intent intent) {
            String action = intent.getAction();
            if (action.equals("android.net.conn.TETHER_STATE_CHANGED")) {
                ArrayList<String> available = intent.getStringArrayListExtra("availableArray");
                ArrayList<String> active = intent.getStringArrayListExtra("activeArray");
                ArrayList<String> errored = intent.getStringArrayListExtra("erroredArray");
                TetherSettings.this.updateState((String[]) available.toArray(new String[available.size()]), (String[]) active.toArray(new String[active.size()]), (String[]) errored.toArray(new String[errored.size()]));
            } else if (action.equals("android.intent.action.MEDIA_SHARED")) {
                TetherSettings.this.mMassStorageActive = true;
                TetherSettings.this.updateState();
            } else if (action.equals("android.intent.action.MEDIA_UNSHARED")) {
                TetherSettings.this.mMassStorageActive = false;
                TetherSettings.this.updateState();
            } else if (action.equals("android.hardware.usb.action.USB_STATE")) {
                TetherSettings.this.mUsbConnected = intent.getBooleanExtra("connected", false);
                TetherSettings.this.updateState();
            } else if (action.equals("android.bluetooth.adapter.action.STATE_CHANGED")) {
                if (TetherSettings.this.mBluetoothEnableForTether) {
                    switch (intent.getIntExtra("android.bluetooth.adapter.extra.STATE", Integer.MIN_VALUE)) {
                        case Integer.MIN_VALUE:
                        case 10:
                            TetherSettings.this.mBluetoothEnableForTether = false;
                            break;
                        case 12:
                            BluetoothPan bluetoothPan = (BluetoothPan) TetherSettings.this.mBluetoothPan.get();
                            if (bluetoothPan != null) {
                                bluetoothPan.setBluetoothTethering(true);
                                TetherSettings.this.mBluetoothEnableForTether = false;
                                break;
                            }
                            break;
                    }
                }
                TetherSettings.this.updateState();
            }
        }
    }

    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        if (icicle != null) {
            this.mTetherChoice = icicle.getInt("TETHER_TYPE");
        }
        addPreferencesFromResource(R.xml.tether_prefs);
        this.mUm = (UserManager) getSystemService("user");
        if (this.mUm.hasUserRestriction("no_config_tethering")) {
            this.mUnavailable = true;
            setPreferenceScreen(new PreferenceScreen(getActivity(), null));
            return;
        }
        boolean usbAvailable;
        boolean wifiAvailable;
        boolean bluetoothAvailable;
        Activity activity = getActivity();
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter != null) {
            adapter.getProfileProxy(activity.getApplicationContext(), this.mProfileServiceListener, 5);
        }
        this.mEnableWifiAp = (SwitchPreference) findPreference("enable_wifi_ap");
        Preference wifiApSettings = findPreference("wifi_ap_ssid_and_security");
        this.mUsbTether = (SwitchPreference) findPreference("usb_tether_settings");
        this.mBluetoothTether = (SwitchPreference) findPreference("enable_bluetooth_tethering");
        ConnectivityManager cm = (ConnectivityManager) getSystemService("connectivity");
        this.mUsbRegexs = cm.getTetherableUsbRegexs();
        this.mWifiRegexs = cm.getTetherableWifiRegexs();
        this.mBluetoothRegexs = cm.getTetherableBluetoothRegexs();
        if (this.mUsbRegexs.length != 0) {
            usbAvailable = true;
        } else {
            usbAvailable = false;
        }
        if (this.mWifiRegexs.length != 0) {
            wifiAvailable = true;
        } else {
            wifiAvailable = false;
        }
        if (this.mBluetoothRegexs.length != 0) {
            bluetoothAvailable = true;
        } else {
            bluetoothAvailable = false;
        }
        if (!usbAvailable || Utils.isMonkeyRunning()) {
            getPreferenceScreen().removePreference(this.mUsbTether);
        }
        if (!wifiAvailable || Utils.isMonkeyRunning()) {
            getPreferenceScreen().removePreference(this.mEnableWifiAp);
            getPreferenceScreen().removePreference(wifiApSettings);
        } else {
            this.mWifiApEnabler = new WifiApEnabler(activity, this.mEnableWifiAp);
            initWifiTethering();
        }
        if (bluetoothAvailable) {
            BluetoothPan pan = (BluetoothPan) this.mBluetoothPan.get();
            if (pan == null || !pan.isTetheringOn()) {
                this.mBluetoothTether.setChecked(false);
            } else {
                this.mBluetoothTether.setChecked(true);
            }
        } else {
            getPreferenceScreen().removePreference(this.mBluetoothTether);
        }
        this.mProvisionApp = getResources().getStringArray(17235989);
        this.mView = new WebView(activity);
    }

    public void onSaveInstanceState(Bundle savedInstanceState) {
        savedInstanceState.putInt("TETHER_TYPE", this.mTetherChoice);
        super.onSaveInstanceState(savedInstanceState);
    }

    private void initWifiTethering() {
        Activity activity = getActivity();
        this.mWifiManager = (WifiManager) getSystemService("wifi");
        this.mWifiConfig = this.mWifiManager.getWifiApConfiguration();
        this.mSecurityType = getResources().getStringArray(R.array.wifi_ap_security);
        this.mCreateNetwork = findPreference("wifi_ap_ssid_and_security");
        if (this.mWifiConfig == null) {
            String s = activity.getString(17040553);
            this.mCreateNetwork.setSummary(String.format(activity.getString(R.string.wifi_tether_configure_subtext), new Object[]{s, this.mSecurityType[0]}));
            return;
        }
        int index = WifiApDialog.getSecurityTypeIndex(this.mWifiConfig);
        this.mCreateNetwork.setSummary(String.format(activity.getString(R.string.wifi_tether_configure_subtext), new Object[]{this.mWifiConfig.SSID, this.mSecurityType[index]}));
    }

    public Dialog onCreateDialog(int id) {
        if (id != 1) {
            return null;
        }
        this.mDialog = new WifiApDialog(getActivity(), this, this.mWifiConfig);
        return this.mDialog;
    }

    public void onStart() {
        super.onStart();
        if (this.mUnavailable) {
            TextView emptyView = (TextView) getView().findViewById(16908292);
            getListView().setEmptyView(emptyView);
            if (emptyView != null) {
                emptyView.setText(R.string.tethering_settings_not_available);
                return;
            }
            return;
        }
        Activity activity = getActivity();
        this.mMassStorageActive = "shared".equals(Environment.getExternalStorageState());
        this.mTetherChangeReceiver = new TetherChangeReceiver();
        Intent intent = activity.registerReceiver(this.mTetherChangeReceiver, new IntentFilter("android.net.conn.TETHER_STATE_CHANGED"));
        IntentFilter filter = new IntentFilter();
        filter.addAction("android.hardware.usb.action.USB_STATE");
        activity.registerReceiver(this.mTetherChangeReceiver, filter);
        filter = new IntentFilter();
        filter.addAction("android.intent.action.MEDIA_SHARED");
        filter.addAction("android.intent.action.MEDIA_UNSHARED");
        filter.addDataScheme("file");
        activity.registerReceiver(this.mTetherChangeReceiver, filter);
        filter = new IntentFilter();
        filter.addAction("android.bluetooth.adapter.action.STATE_CHANGED");
        activity.registerReceiver(this.mTetherChangeReceiver, filter);
        if (intent != null) {
            this.mTetherChangeReceiver.onReceive(activity, intent);
        }
        if (this.mWifiApEnabler != null) {
            this.mEnableWifiAp.setOnPreferenceChangeListener(this);
            this.mWifiApEnabler.resume();
        }
        updateState();
    }

    public void onStop() {
        super.onStop();
        if (!this.mUnavailable) {
            getActivity().unregisterReceiver(this.mTetherChangeReceiver);
            this.mTetherChangeReceiver = null;
            if (this.mWifiApEnabler != null) {
                this.mEnableWifiAp.setOnPreferenceChangeListener(null);
                this.mWifiApEnabler.pause();
            }
        }
    }

    private void updateState() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService("connectivity");
        updateState(cm.getTetherableIfaces(), cm.getTetheredIfaces(), cm.getTetheringErroredIfaces());
    }

    private void updateState(String[] available, String[] tethered, String[] errored) {
        updateUsbState(available, tethered, errored);
        updateBluetoothState(available, tethered, errored);
    }

    private void updateUsbState(String[] available, String[] tethered, String[] errored) {
        ConnectivityManager cm = (ConnectivityManager) getSystemService("connectivity");
        boolean usbAvailable = this.mUsbConnected && !this.mMassStorageActive;
        int usbError = 0;
        for (String s : available) {
            for (String regex : this.mUsbRegexs) {
                if (s.matches(regex) && usbError == 0) {
                    usbError = cm.getLastTetherError(s);
                }
            }
        }
        boolean usbTethered = false;
        for (String s2 : tethered) {
            for (String regex2 : this.mUsbRegexs) {
                if (s2.matches(regex2)) {
                    usbTethered = true;
                }
            }
        }
        boolean usbErrored = false;
        for (String s22 : errored) {
            for (String regex22 : this.mUsbRegexs) {
                if (s22.matches(regex22)) {
                    usbErrored = true;
                }
            }
        }
        if (usbTethered) {
            this.mUsbTether.setSummary(R.string.usb_tethering_active_subtext);
            this.mUsbTether.setEnabled(true);
            this.mUsbTether.setChecked(true);
        } else if (usbAvailable) {
            if (usbError == 0) {
                this.mUsbTether.setSummary(R.string.usb_tethering_available_subtext);
            } else {
                this.mUsbTether.setSummary(R.string.usb_tethering_errored_subtext);
            }
            this.mUsbTether.setEnabled(true);
            this.mUsbTether.setChecked(false);
        } else if (usbErrored) {
            this.mUsbTether.setSummary(R.string.usb_tethering_errored_subtext);
            this.mUsbTether.setEnabled(false);
            this.mUsbTether.setChecked(false);
        } else if (this.mMassStorageActive) {
            this.mUsbTether.setSummary(R.string.usb_tethering_storage_active_subtext);
            this.mUsbTether.setEnabled(false);
            this.mUsbTether.setChecked(false);
        } else {
            this.mUsbTether.setSummary(R.string.usb_tethering_unavailable_subtext);
            this.mUsbTether.setEnabled(false);
            this.mUsbTether.setChecked(false);
        }
    }

    private void updateBluetoothState(String[] available, String[] tethered, String[] errored) {
        boolean bluetoothErrored = false;
        for (String s : errored) {
            for (String regex : this.mBluetoothRegexs) {
                if (s.matches(regex)) {
                    bluetoothErrored = true;
                }
            }
        }
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter != null) {
            int btState = adapter.getState();
            if (btState == 13) {
                this.mBluetoothTether.setEnabled(false);
                this.mBluetoothTether.setSummary(R.string.bluetooth_turning_off);
            } else if (btState == 11) {
                this.mBluetoothTether.setEnabled(false);
                this.mBluetoothTether.setSummary(R.string.bluetooth_turning_on);
            } else {
                BluetoothPan bluetoothPan = (BluetoothPan) this.mBluetoothPan.get();
                if (btState == 12 && bluetoothPan != null && bluetoothPan.isTetheringOn()) {
                    this.mBluetoothTether.setChecked(true);
                    this.mBluetoothTether.setEnabled(true);
                    int bluetoothTethered = bluetoothPan.getConnectedDevices().size();
                    if (bluetoothTethered > 1) {
                        this.mBluetoothTether.setSummary(getString(R.string.bluetooth_tethering_devices_connected_subtext, new Object[]{Integer.valueOf(bluetoothTethered)}));
                        return;
                    } else if (bluetoothTethered == 1) {
                        this.mBluetoothTether.setSummary(R.string.bluetooth_tethering_device_connected_subtext);
                        return;
                    } else if (bluetoothErrored) {
                        this.mBluetoothTether.setSummary(R.string.bluetooth_tethering_errored_subtext);
                        return;
                    } else {
                        this.mBluetoothTether.setSummary(R.string.bluetooth_tethering_available_subtext);
                        return;
                    }
                }
                this.mBluetoothTether.setEnabled(true);
                this.mBluetoothTether.setChecked(false);
                this.mBluetoothTether.setSummary(R.string.bluetooth_tethering_off_subtext);
            }
        }
    }

    public boolean onPreferenceChange(Preference preference, Object value) {
        if (((Boolean) value).booleanValue()) {
            startProvisioningIfNecessary(0);
        } else {
            this.mWifiApEnabler.setSoftapEnabled(false);
        }
        return false;
    }

    public static boolean isProvisioningNeededButUnavailable(Context context) {
        String[] provisionApp = context.getResources().getStringArray(17235989);
        return isProvisioningNeeded(provisionApp) && !isIntentAvailable(context, provisionApp);
    }

    private static boolean isIntentAvailable(Context context, String[] provisionApp) {
        if (provisionApp.length < 2) {
            throw new IllegalArgumentException("provisionApp length should at least be 2");
        }
        PackageManager packageManager = context.getPackageManager();
        Intent intent = new Intent("android.intent.action.MAIN");
        intent.setClassName(provisionApp[0], provisionApp[1]);
        if (packageManager.queryIntentActivities(intent, 65536).size() > 0) {
            return true;
        }
        return false;
    }

    private static boolean isProvisioningNeeded(String[] provisionApp) {
        if (SystemProperties.getBoolean("net.tethering.noprovisioning", false) || provisionApp == null || provisionApp.length != 2) {
            return false;
        }
        return true;
    }

    private void startProvisioningIfNecessary(int choice) {
        this.mTetherChoice = choice;
        if (isProvisioningNeeded(this.mProvisionApp)) {
            Intent intent = new Intent("android.intent.action.MAIN");
            intent.setClassName(this.mProvisionApp[0], this.mProvisionApp[1]);
            intent.putExtra("TETHER_TYPE", this.mTetherChoice);
            startActivityForResult(intent, 0);
            return;
        }
        startTethering();
    }

    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);
        if (requestCode != 0) {
            return;
        }
        if (resultCode == -1) {
            startTethering();
            return;
        }
        switch (this.mTetherChoice) {
            case 1:
                this.mUsbTether.setChecked(false);
                break;
            case 2:
                this.mBluetoothTether.setChecked(false);
                break;
        }
        this.mTetherChoice = -1;
    }

    private void startTethering() {
        switch (this.mTetherChoice) {
            case 0:
                this.mWifiApEnabler.setSoftapEnabled(true);
                return;
            case 1:
                setUsbTethering(true);
                return;
            case 2:
                BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
                if (adapter.getState() == 10) {
                    this.mBluetoothEnableForTether = true;
                    adapter.enable();
                    this.mBluetoothTether.setSummary(R.string.bluetooth_turning_on);
                    this.mBluetoothTether.setEnabled(false);
                    return;
                }
                BluetoothPan bluetoothPan = (BluetoothPan) this.mBluetoothPan.get();
                if (bluetoothPan != null) {
                    bluetoothPan.setBluetoothTethering(true);
                }
                this.mBluetoothTether.setSummary(R.string.bluetooth_tethering_available_subtext);
                return;
            default:
                return;
        }
    }

    private void setUsbTethering(boolean enabled) {
        ConnectivityManager cm = (ConnectivityManager) getSystemService("connectivity");
        this.mUsbTether.setChecked(false);
        if (cm.setUsbTethering(enabled) != 0) {
            this.mUsbTether.setSummary(R.string.usb_tethering_errored_subtext);
        } else {
            this.mUsbTether.setSummary("");
        }
    }

    public boolean onPreferenceTreeClick(PreferenceScreen screen, Preference preference) {
        ConnectivityManager cm = (ConnectivityManager) getSystemService("connectivity");
        if (preference == this.mUsbTether) {
            boolean newState = this.mUsbTether.isChecked();
            if (newState) {
                startProvisioningIfNecessary(1);
            } else {
                setUsbTethering(newState);
            }
        } else if (preference == this.mBluetoothTether) {
            if (this.mBluetoothTether.isChecked()) {
                startProvisioningIfNecessary(2);
            } else {
                boolean errored = false;
                String bluetoothIface = findIface(cm.getTetheredIfaces(), this.mBluetoothRegexs);
                if (!(bluetoothIface == null || cm.untether(bluetoothIface) == 0)) {
                    errored = true;
                }
                BluetoothPan bluetoothPan = (BluetoothPan) this.mBluetoothPan.get();
                if (bluetoothPan != null) {
                    bluetoothPan.setBluetoothTethering(false);
                }
                if (errored) {
                    this.mBluetoothTether.setSummary(R.string.bluetooth_tethering_errored_subtext);
                } else {
                    this.mBluetoothTether.setSummary(R.string.bluetooth_tethering_off_subtext);
                }
            }
        } else if (preference == this.mCreateNetwork) {
            showDialog(1);
        }
        return super.onPreferenceTreeClick(screen, preference);
    }

    private static String findIface(String[] ifaces, String[] regexes) {
        for (String iface : ifaces) {
            for (String regex : regexes) {
                if (iface.matches(regex)) {
                    return iface;
                }
            }
        }
        return null;
    }

    public void onClick(DialogInterface dialogInterface, int button) {
        if (button == -1) {
            this.mWifiConfig = this.mDialog.getConfig();
            if (this.mWifiConfig != null) {
                if (this.mWifiManager.getWifiApState() == 13) {
                    this.mWifiManager.setWifiApEnabled(null, false);
                    this.mWifiManager.setWifiApEnabled(this.mWifiConfig, true);
                } else {
                    this.mWifiManager.setWifiApConfiguration(this.mWifiConfig);
                }
                int index = WifiApDialog.getSecurityTypeIndex(this.mWifiConfig);
                this.mCreateNetwork.setSummary(String.format(getActivity().getString(R.string.wifi_tether_configure_subtext), new Object[]{this.mWifiConfig.SSID, this.mSecurityType[index]}));
            }
        }
    }

    public int getHelpResource() {
        return R.string.help_url_tether;
    }

    public static boolean showInShortcuts(Context context) {
        boolean isSecondaryUser;
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService("connectivity");
        if (UserHandle.myUserId() != 0) {
            isSecondaryUser = true;
        } else {
            isSecondaryUser = false;
        }
        if (isSecondaryUser || !cm.isTetheringSupported()) {
            return false;
        }
        return true;
    }
}
