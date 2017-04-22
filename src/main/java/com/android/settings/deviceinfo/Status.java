package com.android.settings.deviceinfo;

import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.net.ConnectivityManager;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.telephony.CellBroadcastMessage;
import android.telephony.PhoneNumberUtils;
import android.telephony.PhoneStateListener;
import android.telephony.ServiceState;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.ListAdapter;
import android.widget.Toast;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.PhoneStateIntentReceiver;
import com.android.internal.util.ArrayUtils;
import com.android.settings.R;
import com.android.settings.Utils;
import java.lang.ref.WeakReference;

public class Status extends PreferenceActivity {
    private static final String[] CONNECTIVITY_INTENTS = new String[]{"android.bluetooth.adapter.action.STATE_CHANGED", "android.net.conn.CONNECTIVITY_CHANGE_IMMEDIATE", "android.net.wifi.LINK_CONFIGURATION_CHANGED", "android.net.wifi.STATE_CHANGE"};
    private static final String[] PHONE_RELATED_ENTRIES = new String[]{"data_state", "service_state", "operator_name", "roaming_state", "network_type", "latest_area_info", "number", "imei", "imei_sv", "prl_version", "min_number", "meid_number", "signal_strength", "icc_id"};
    private BroadcastReceiver mAreaInfoReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            if ("android.cellbroadcastreceiver.CB_AREA_INFO_RECEIVED".equals(intent.getAction())) {
                Bundle extras = intent.getExtras();
                if (extras != null) {
                    CellBroadcastMessage cbMessage = (CellBroadcastMessage) extras.get("message");
                    if (cbMessage != null && cbMessage.getServiceCategory() == 50) {
                        Status.this.updateAreaInfo(cbMessage.getMessageBody());
                    }
                }
            }
        }
    };
    private BroadcastReceiver mBatteryInfoReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            if ("android.intent.action.BATTERY_CHANGED".equals(intent.getAction())) {
                Status.this.mBatteryLevel.setSummary(Utils.getBatteryPercentage(intent));
                Status.this.mBatteryStatus.setSummary(Utils.getBatteryStatus(Status.this.getResources(), intent));
            }
        }
    };
    private Preference mBatteryLevel;
    private Preference mBatteryStatus;
    private Preference mBtAddress;
    private ConnectivityManager mCM;
    private IntentFilter mConnectivityIntentFilter;
    private final BroadcastReceiver mConnectivityReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            if (ArrayUtils.contains(Status.CONNECTIVITY_INTENTS, intent.getAction())) {
                Status.this.mHandler.sendEmptyMessage(600);
            }
        }
    };
    private Handler mHandler;
    private Preference mIpAddress;
    private Phone mPhone = null;
    private PhoneStateListener mPhoneStateListener = new PhoneStateListener() {
        public void onDataConnectionStateChanged(int state) {
            Status.this.updateDataState();
            Status.this.updateNetworkType();
        }
    };
    private PhoneStateIntentReceiver mPhoneStateReceiver;
    private Resources mRes;
    private boolean mShowLatestAreaInfo;
    private Preference mSignalStrength;
    private TelephonyManager mTelephonyManager;
    private String mUnavailable;
    private String mUnknown;
    private Preference mUptime;
    private Preference mWifiMacAddress;
    private WifiManager mWifiManager;
    private Preference mWimaxMacAddress;

    private static class MyHandler extends Handler {
        private WeakReference<Status> mStatus;

        public MyHandler(Status activity) {
            this.mStatus = new WeakReference(activity);
        }

        public void handleMessage(Message msg) {
            Status status = (Status) this.mStatus.get();
            if (status != null) {
                switch (msg.what) {
                    case 200:
                        status.updateSignalStrength();
                        return;
                    case 300:
                        status.updateServiceState(status.mPhoneStateReceiver.getServiceState());
                        return;
                    case 500:
                        status.updateTimes();
                        sendEmptyMessageDelayed(500, 1000);
                        return;
                    case 600:
                        status.updateConnectivity();
                        return;
                    default:
                        return;
                }
            }
        }
    }

    private boolean hasBluetooth() {
        return BluetoothAdapter.getDefaultAdapter() != null;
    }

    private boolean hasWimax() {
        return this.mCM.getNetworkInfo(6) != null;
    }

    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        this.mHandler = new MyHandler(this);
        this.mCM = (ConnectivityManager) getSystemService("connectivity");
        this.mTelephonyManager = (TelephonyManager) getSystemService("phone");
        this.mWifiManager = (WifiManager) getSystemService("wifi");
        addPreferencesFromResource(R.xml.device_info_status);
        this.mBatteryLevel = findPreference("battery_level");
        this.mBatteryStatus = findPreference("battery_status");
        this.mBtAddress = findPreference("bt_address");
        this.mWifiMacAddress = findPreference("wifi_mac_address");
        this.mWimaxMacAddress = findPreference("wimax_mac_address");
        this.mIpAddress = findPreference("wifi_ip_address");
        this.mRes = getResources();
        this.mUnknown = this.mRes.getString(R.string.device_info_default);
        this.mUnavailable = this.mRes.getString(R.string.status_unavailable);
        if (UserHandle.myUserId() == 0) {
            this.mPhone = PhoneFactory.getDefaultPhone();
        }
        this.mSignalStrength = findPreference("signal_strength");
        this.mUptime = findPreference("up_time");
        if (this.mPhone == null || Utils.isWifiOnly(getApplicationContext())) {
            for (String key : PHONE_RELATED_ENTRIES) {
                removePreferenceFromScreen(key);
            }
        } else {
            if (this.mPhone.getPhoneName().equals("CDMA")) {
                setSummaryText("meid_number", this.mPhone.getMeid());
                setSummaryText("min_number", this.mPhone.getCdmaMin());
                if (getResources().getBoolean(R.bool.config_msid_enable)) {
                    findPreference("min_number").setTitle(R.string.status_msid_number);
                }
                setSummaryText("prl_version", this.mPhone.getCdmaPrlVersion());
                removePreferenceFromScreen("imei_sv");
                if (this.mPhone.getLteOnCdmaMode() == 1) {
                    setSummaryText("icc_id", this.mPhone.getIccSerialNumber());
                    setSummaryText("imei", this.mPhone.getImei());
                } else {
                    removePreferenceFromScreen("imei");
                    removePreferenceFromScreen("icc_id");
                }
            } else {
                setSummaryText("imei", this.mPhone.getDeviceId());
                setSummaryText("imei_sv", ((TelephonyManager) getSystemService("phone")).getDeviceSoftwareVersion());
                removePreferenceFromScreen("prl_version");
                removePreferenceFromScreen("meid_number");
                removePreferenceFromScreen("min_number");
                removePreferenceFromScreen("icc_id");
                if ("br".equals(this.mTelephonyManager.getSimCountryIso())) {
                    this.mShowLatestAreaInfo = true;
                }
            }
            String rawNumber = this.mTelephonyManager.getLine1Number();
            String formattedNumber = null;
            if (!TextUtils.isEmpty(rawNumber)) {
                formattedNumber = PhoneNumberUtils.formatNumber(rawNumber);
            }
            setSummaryText("number", formattedNumber);
            this.mPhoneStateReceiver = new PhoneStateIntentReceiver(this, this.mHandler);
            this.mPhoneStateReceiver.notifySignalStrength(200);
            this.mPhoneStateReceiver.notifyServiceState(300);
            if (!this.mShowLatestAreaInfo) {
                removePreferenceFromScreen("latest_area_info");
            }
        }
        if (!hasBluetooth()) {
            getPreferenceScreen().removePreference(this.mBtAddress);
            this.mBtAddress = null;
        }
        if (!hasWimax()) {
            getPreferenceScreen().removePreference(this.mWimaxMacAddress);
            this.mWimaxMacAddress = null;
        }
        this.mConnectivityIntentFilter = new IntentFilter();
        for (String intent : CONNECTIVITY_INTENTS) {
            this.mConnectivityIntentFilter.addAction(intent);
        }
        updateConnectivity();
        String serial = Build.SERIAL;
        if (serial == null || serial.equals("")) {
            removePreferenceFromScreen("serial_number");
        } else {
            setSummaryText("serial_number", serial);
        }
        getListView().setOnItemLongClickListener(new OnItemLongClickListener() {
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                ((ClipboardManager) Status.this.getSystemService("clipboard")).setText(((Preference) ((ListAdapter) parent.getAdapter()).getItem(position)).getSummary());
                Toast.makeText(Status.this, 17040426, 0).show();
                return true;
            }
        });
    }

    protected void onResume() {
        super.onResume();
        if (!(this.mPhone == null || Utils.isWifiOnly(getApplicationContext()))) {
            this.mPhoneStateReceiver.registerIntent();
            updateSignalStrength();
            updateServiceState(this.mPhone.getServiceState());
            updateDataState();
            this.mTelephonyManager.listen(this.mPhoneStateListener, 64);
            if (this.mShowLatestAreaInfo) {
                registerReceiver(this.mAreaInfoReceiver, new IntentFilter("android.cellbroadcastreceiver.CB_AREA_INFO_RECEIVED"), "android.permission.RECEIVE_EMERGENCY_BROADCAST", null);
                sendBroadcastAsUser(new Intent("android.cellbroadcastreceiver.GET_LATEST_CB_AREA_INFO"), UserHandle.ALL, "android.permission.RECEIVE_EMERGENCY_BROADCAST");
            }
        }
        registerReceiver(this.mConnectivityReceiver, this.mConnectivityIntentFilter, "android.permission.CHANGE_NETWORK_STATE", null);
        registerReceiver(this.mBatteryInfoReceiver, new IntentFilter("android.intent.action.BATTERY_CHANGED"));
        this.mHandler.sendEmptyMessage(500);
    }

    public void onPause() {
        super.onPause();
        if (!(this.mPhone == null || Utils.isWifiOnly(getApplicationContext()))) {
            this.mPhoneStateReceiver.unregisterIntent();
            this.mTelephonyManager.listen(this.mPhoneStateListener, 0);
        }
        if (this.mShowLatestAreaInfo) {
            unregisterReceiver(this.mAreaInfoReceiver);
        }
        unregisterReceiver(this.mBatteryInfoReceiver);
        unregisterReceiver(this.mConnectivityReceiver);
        this.mHandler.removeMessages(500);
    }

    private void removePreferenceFromScreen(String key) {
        Preference pref = findPreference(key);
        if (pref != null) {
            getPreferenceScreen().removePreference(pref);
        }
    }

    private void setSummaryText(String preference, String text) {
        if (TextUtils.isEmpty(text)) {
            text = this.mUnknown;
        }
        if (findPreference(preference) != null) {
            findPreference(preference).setSummary(text);
        }
    }

    private void updateNetworkType() {
        String networktype = null;
        if (this.mTelephonyManager.getNetworkType() != 0) {
            networktype = this.mTelephonyManager.getNetworkTypeName();
        }
        setSummaryText("network_type", networktype);
    }

    private void updateDataState() {
        int state = this.mTelephonyManager.getDataState();
        String display = this.mRes.getString(R.string.radioInfo_unknown);
        switch (state) {
            case 0:
                display = this.mRes.getString(R.string.radioInfo_data_disconnected);
                break;
            case 1:
                display = this.mRes.getString(R.string.radioInfo_data_connecting);
                break;
            case 2:
                display = this.mRes.getString(R.string.radioInfo_data_connected);
                break;
            case 3:
                display = this.mRes.getString(R.string.radioInfo_data_suspended);
                break;
        }
        setSummaryText("data_state", display);
    }

    private void updateServiceState(ServiceState serviceState) {
        int state = serviceState.getState();
        String display = this.mRes.getString(R.string.radioInfo_unknown);
        switch (state) {
            case 0:
                display = this.mRes.getString(R.string.radioInfo_service_in);
                break;
            case 1:
            case 2:
                display = this.mRes.getString(R.string.radioInfo_service_out);
                break;
            case 3:
                display = this.mRes.getString(R.string.radioInfo_service_off);
                break;
        }
        setSummaryText("service_state", display);
        if (serviceState.getRoaming()) {
            setSummaryText("roaming_state", this.mRes.getString(R.string.radioInfo_roaming_in));
        } else {
            setSummaryText("roaming_state", this.mRes.getString(R.string.radioInfo_roaming_not));
        }
        setSummaryText("operator_name", serviceState.getOperatorAlphaLong());
    }

    private void updateAreaInfo(String areaInfo) {
        if (areaInfo != null) {
            setSummaryText("latest_area_info", areaInfo);
        }
    }

    void updateSignalStrength() {
        if (this.mSignalStrength != null) {
            int state = this.mPhoneStateReceiver.getServiceState().getState();
            Resources r = getResources();
            if (1 == state || 3 == state) {
                this.mSignalStrength.setSummary("0");
                return;
            }
            int signalDbm = this.mPhoneStateReceiver.getSignalStrengthDbm();
            if (-1 == signalDbm) {
                signalDbm = 0;
            }
            int signalAsu = this.mPhoneStateReceiver.getSignalStrengthLevelAsu();
            if (-1 == signalAsu) {
                signalAsu = 0;
            }
            this.mSignalStrength.setSummary(String.valueOf(signalDbm) + " " + r.getString(R.string.radioInfo_display_dbm) + "   " + String.valueOf(signalAsu) + " " + r.getString(R.string.radioInfo_display_asu));
        }
    }

    private void setWimaxStatus() {
        if (this.mWimaxMacAddress != null) {
            this.mWimaxMacAddress.setSummary(SystemProperties.get("net.wimax.mac.address", this.mUnavailable));
        }
    }

    private void setWifiStatus() {
        WifiInfo wifiInfo = this.mWifiManager.getConnectionInfo();
        String macAddress = wifiInfo == null ? null : wifiInfo.getMacAddress();
        Preference preference = this.mWifiMacAddress;
        if (TextUtils.isEmpty(macAddress)) {
            macAddress = this.mUnavailable;
        }
        preference.setSummary(macAddress);
    }

    private void setIpAddressStatus() {
        String ipAddress = Utils.getDefaultIpAddresses(this.mCM);
        if (ipAddress != null) {
            this.mIpAddress.setSummary(ipAddress);
        } else {
            this.mIpAddress.setSummary(this.mUnavailable);
        }
    }

    private void setBtStatus() {
        BluetoothAdapter bluetooth = BluetoothAdapter.getDefaultAdapter();
        if (bluetooth != null && this.mBtAddress != null) {
            String address = bluetooth.isEnabled() ? bluetooth.getAddress() : null;
            if (TextUtils.isEmpty(address)) {
                this.mBtAddress.setSummary(this.mUnavailable);
            } else {
                this.mBtAddress.setSummary(address.toLowerCase());
            }
        }
    }

    void updateConnectivity() {
        setWimaxStatus();
        setWifiStatus();
        setBtStatus();
        setIpAddressStatus();
    }

    void updateTimes() {
        long at = SystemClock.uptimeMillis() / 1000;
        long ut = SystemClock.elapsedRealtime() / 1000;
        if (ut == 0) {
            ut = 1;
        }
        this.mUptime.setSummary(convert(ut));
    }

    private String pad(int n) {
        if (n >= 10) {
            return String.valueOf(n);
        }
        return "0" + String.valueOf(n);
    }

    private String convert(long t) {
        return ((int) (t / 3600)) + ":" + pad((int) ((t / 60) % 60)) + ":" + pad((int) (t % 60));
    }
}
