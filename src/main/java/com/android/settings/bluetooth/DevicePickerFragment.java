package com.android.settings.bluetooth;

import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.os.Bundle;
import android.os.UserManager;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import com.android.settings.R;

public final class DevicePickerFragment extends DeviceListPreferenceFragment {
    private String mLaunchClass;
    private String mLaunchPackage;
    private boolean mNeedAuth;
    private boolean mStartScanOnResume;

    public DevicePickerFragment() {
        super(null);
    }

    void addPreferencesForActivity() {
        addPreferencesFromResource(R.xml.device_picker);
        Intent intent = getActivity().getIntent();
        this.mNeedAuth = intent.getBooleanExtra("android.bluetooth.devicepicker.extra.NEED_AUTH", false);
        setFilter(intent.getIntExtra("android.bluetooth.devicepicker.extra.FILTER_TYPE", 0));
        this.mLaunchPackage = intent.getStringExtra("android.bluetooth.devicepicker.extra.LAUNCH_PACKAGE");
        this.mLaunchClass = intent.getStringExtra("android.bluetooth.devicepicker.extra.DEVICE_PICKER_LAUNCH_CLASS");
    }

    void initDevicePreference(BluetoothDevicePreference preference) {
        preference.setWidgetLayoutResource(R.layout.preference_empty_list);
    }

    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        menu.add(0, 1, 0, R.string.bluetooth_search_for_devices).setEnabled(true).setShowAsAction(0);
        super.onCreateOptionsMenu(menu, inflater);
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case 1:
                this.mLocalAdapter.startScanning(true);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getActivity().setTitle(getString(R.string.device_picker));
        boolean z = !((UserManager) getSystemService("user")).hasUserRestriction("no_config_bluetooth") && savedInstanceState == null;
        this.mStartScanOnResume = z;
        setHasOptionsMenu(true);
    }

    public void onResume() {
        super.onResume();
        addCachedDevices();
        if (this.mStartScanOnResume) {
            this.mLocalAdapter.startScanning(true);
            this.mStartScanOnResume = false;
        }
    }

    void onDevicePreferenceClick(BluetoothDevicePreference btPreference) {
        this.mLocalAdapter.stopScanning();
        LocalBluetoothPreferences.persistSelectedDeviceInPicker(getActivity(), this.mSelectedDevice.getAddress());
        if (btPreference.getCachedDevice().getBondState() == 12 || !this.mNeedAuth) {
            sendDevicePickedIntent(this.mSelectedDevice);
            finish();
            return;
        }
        super.onDevicePreferenceClick(btPreference);
    }

    public void onDeviceBondStateChanged(CachedBluetoothDevice cachedDevice, int bondState) {
        if (bondState == 12) {
            BluetoothDevice device = cachedDevice.getDevice();
            if (device.equals(this.mSelectedDevice)) {
                sendDevicePickedIntent(device);
                finish();
            }
        }
    }

    public void onBluetoothStateChanged(int bluetoothState) {
        super.onBluetoothStateChanged(bluetoothState);
        if (bluetoothState == 12) {
            this.mLocalAdapter.startScanning(false);
        }
    }

    private void sendDevicePickedIntent(BluetoothDevice device) {
        Intent intent = new Intent("android.bluetooth.devicepicker.action.DEVICE_SELECTED");
        intent.putExtra("android.bluetooth.device.extra.DEVICE", device);
        if (!(this.mLaunchPackage == null || this.mLaunchClass == null)) {
            intent.setClassName(this.mLaunchPackage, this.mLaunchClass);
        }
        getActivity().sendBroadcast(intent);
    }
}
