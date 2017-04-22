package com.android.settings.bluetooth;

import android.bluetooth.BluetoothDevice;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceGroup;
import android.preference.PreferenceScreen;
import android.util.Log;
import com.android.settings.RestrictedSettingsFragment;
import java.util.WeakHashMap;

public abstract class DeviceListPreferenceFragment extends RestrictedSettingsFragment implements BluetoothCallback {
    private PreferenceGroup mDeviceListGroup;
    final WeakHashMap<CachedBluetoothDevice, BluetoothDevicePreference> mDevicePreferenceMap = new WeakHashMap();
    private Filter mFilter = BluetoothDeviceFilter.ALL_FILTER;
    LocalBluetoothAdapter mLocalAdapter;
    LocalBluetoothManager mLocalManager;
    BluetoothDevice mSelectedDevice;

    abstract void addPreferencesForActivity();

    DeviceListPreferenceFragment(String restrictedKey) {
        super(restrictedKey);
    }

    final void setFilter(Filter filter) {
        this.mFilter = filter;
    }

    final void setFilter(int filterType) {
        this.mFilter = BluetoothDeviceFilter.getFilter(filterType);
    }

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.mLocalManager = LocalBluetoothManager.getInstance(getActivity());
        if (this.mLocalManager == null) {
            Log.e("DeviceListPreferenceFragment", "Bluetooth is not supported on this device");
            return;
        }
        this.mLocalAdapter = this.mLocalManager.getBluetoothAdapter();
        addPreferencesForActivity();
        this.mDeviceListGroup = (PreferenceCategory) findPreference("bt_device_list");
    }

    void setDeviceListGroup(PreferenceGroup preferenceGroup) {
        this.mDeviceListGroup = preferenceGroup;
    }

    public void onResume() {
        super.onResume();
        if (this.mLocalManager != null && !isUiRestricted()) {
            this.mLocalManager.setForegroundActivity(getActivity());
            this.mLocalManager.getEventManager().registerCallback(this);
            updateProgressUi(this.mLocalAdapter.isDiscovering());
        }
    }

    public void onPause() {
        super.onPause();
        if (this.mLocalManager != null && !isUiRestricted()) {
            removeAllDevices();
            this.mLocalManager.setForegroundActivity(null);
            this.mLocalManager.getEventManager().unregisterCallback(this);
        }
    }

    void removeAllDevices() {
        this.mLocalAdapter.stopScanning();
        this.mDevicePreferenceMap.clear();
        this.mDeviceListGroup.removeAll();
    }

    void addCachedDevices() {
        for (CachedBluetoothDevice cachedDevice : this.mLocalManager.getCachedDeviceManager().getCachedDevicesCopy()) {
            onDeviceAdded(cachedDevice);
        }
    }

    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        if ("bt_scan".equals(preference.getKey())) {
            this.mLocalAdapter.startScanning(true);
            return true;
        } else if (!(preference instanceof BluetoothDevicePreference)) {
            return super.onPreferenceTreeClick(preferenceScreen, preference);
        } else {
            BluetoothDevicePreference btPreference = (BluetoothDevicePreference) preference;
            this.mSelectedDevice = btPreference.getCachedDevice().getDevice();
            onDevicePreferenceClick(btPreference);
            return true;
        }
    }

    void onDevicePreferenceClick(BluetoothDevicePreference btPreference) {
        btPreference.onClicked();
    }

    public void onDeviceAdded(CachedBluetoothDevice cachedDevice) {
        if (this.mDevicePreferenceMap.get(cachedDevice) == null && this.mLocalAdapter.getBluetoothState() == 12 && this.mFilter.matches(cachedDevice.getDevice())) {
            createDevicePreference(cachedDevice);
        }
    }

    void createDevicePreference(CachedBluetoothDevice cachedDevice) {
        if (this.mDeviceListGroup == null) {
            Log.w("DeviceListPreferenceFragment", "Trying to create a device preference before the list group/category exists!");
            return;
        }
        BluetoothDevicePreference preference = new BluetoothDevicePreference(getActivity(), cachedDevice);
        initDevicePreference(preference);
        this.mDeviceListGroup.addPreference(preference);
        this.mDevicePreferenceMap.put(cachedDevice, preference);
    }

    void initDevicePreference(BluetoothDevicePreference preference) {
    }

    public void onDeviceDeleted(CachedBluetoothDevice cachedDevice) {
        BluetoothDevicePreference preference = (BluetoothDevicePreference) this.mDevicePreferenceMap.remove(cachedDevice);
        if (preference != null) {
            this.mDeviceListGroup.removePreference(preference);
        }
    }

    public void onScanningStateChanged(boolean started) {
        updateProgressUi(started);
    }

    private void updateProgressUi(boolean start) {
        if (this.mDeviceListGroup instanceof BluetoothProgressCategory) {
            ((BluetoothProgressCategory) this.mDeviceListGroup).setProgress(start);
        }
    }

    public void onBluetoothStateChanged(int bluetoothState) {
        if (bluetoothState == 10) {
            updateProgressUi(false);
        }
    }
}
