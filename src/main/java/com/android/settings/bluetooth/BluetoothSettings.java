package com.android.settings.bluetooth;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnDismissListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceGroup;
import android.preference.PreferenceScreen;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.TextView.BufferType;
import com.android.settings.R;
import com.android.settings.SettingsActivity;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settings.search.Indexable;
import com.android.settings.search.Indexable.SearchIndexProvider;
import com.android.settings.search.SearchIndexableRaw;
import com.android.settings.widget.SwitchBar;
import java.util.ArrayList;
import java.util.List;

public final class BluetoothSettings extends DeviceListPreferenceFragment implements Indexable {
    public static final SearchIndexProvider SEARCH_INDEX_DATA_PROVIDER = new BaseSearchIndexProvider() {
        public List<SearchIndexableRaw> getRawDataToIndex(Context context, boolean enabled) {
            List<SearchIndexableRaw> result = new ArrayList();
            Resources res = context.getResources();
            SearchIndexableRaw data = new SearchIndexableRaw(context);
            data.title = res.getString(R.string.bluetooth_settings);
            data.screenTitle = res.getString(R.string.bluetooth_settings);
            result.add(data);
            LocalBluetoothManager lbtm = LocalBluetoothManager.getInstance(context);
            if (lbtm != null) {
                for (BluetoothDevice device : lbtm.getBluetoothAdapter().getBondedDevices()) {
                    data = new SearchIndexableRaw(context);
                    data.title = device.getName();
                    data.screenTitle = res.getString(R.string.bluetooth_settings);
                    data.enabled = enabled;
                    result.add(data);
                }
            }
            return result;
        }
    };
    private static View mSettingsDialogView = null;
    private PreferenceGroup mAvailableDevicesCategory;
    private boolean mAvailableDevicesCategoryIsPresent;
    private BluetoothEnabler mBluetoothEnabler;
    private final OnClickListener mDeviceProfilesListener = new OnClickListener() {
        public void onClick(View v) {
            if (v.getTag() instanceof CachedBluetoothDevice) {
                final CachedBluetoothDevice device = (CachedBluetoothDevice) v.getTag();
                final Activity activity = BluetoothSettings.this.getActivity();
                DeviceProfilesSettings profileFragment = (DeviceProfilesSettings) activity.getFragmentManager().findFragmentById(R.id.bluetooth_fragment_settings);
                if (BluetoothSettings.mSettingsDialogView != null) {
                    ViewGroup parent = (ViewGroup) BluetoothSettings.mSettingsDialogView.getParent();
                    if (parent != null) {
                        parent.removeView(BluetoothSettings.mSettingsDialogView);
                    }
                }
                if (profileFragment == null) {
                    LayoutInflater inflater = BluetoothSettings.this.getActivity().getLayoutInflater();
                    BluetoothSettings.mSettingsDialogView = inflater.inflate(R.layout.bluetooth_device_settings, null);
                    profileFragment = (DeviceProfilesSettings) activity.getFragmentManager().findFragmentById(R.id.bluetooth_fragment_settings);
                    profileFragment.getListView().addHeaderView(inflater.inflate(R.layout.bluetooth_device_settings_header, null));
                }
                final View dialogLayout = BluetoothSettings.mSettingsDialogView;
                Builder settingsDialog = new Builder(activity);
                profileFragment.setDevice(device);
                ((EditText) dialogLayout.findViewById(R.id.name)).setText(device.getName(), BufferType.EDITABLE);
                final DeviceProfilesSettings dpsFragment = profileFragment;
                final Context context = v.getContext();
                settingsDialog.setView(dialogLayout);
                settingsDialog.setTitle(R.string.bluetooth_preference_paired_devices);
                settingsDialog.setPositiveButton(R.string.okay, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        device.setName(((EditText) dialogLayout.findViewById(R.id.name)).getText().toString());
                    }
                });
                settingsDialog.setNegativeButton(R.string.forget, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        device.unpair();
                        Utils.updateSearchIndex(activity, BluetoothSettings.class.getName(), device.getName(), context.getResources().getString(R.string.bluetooth_settings), R.drawable.ic_settings_bluetooth2, false);
                    }
                });
                settingsDialog.setOnDismissListener(new OnDismissListener() {
                    public void onDismiss(DialogInterface dialog) {
                        if (!activity.isDestroyed()) {
                            activity.getFragmentManager().beginTransaction().remove(dpsFragment).commitAllowingStateLoss();
                        }
                    }
                });
                AlertDialog dialog = settingsDialog.create();
                dialog.create();
                dialog.show();
                dialog.getWindow().clearFlags(131080);
                return;
            }
            Log.w("BluetoothSettings", "onClick() called for other View: " + v);
        }
    };
    private TextView mEmptyView;
    private boolean mInitialScanStarted;
    private boolean mInitiateDiscoverable;
    private final IntentFilter mIntentFilter = new IntentFilter("android.bluetooth.adapter.action.LOCAL_NAME_CHANGED");
    Preference mMyDevicePreference;
    private PreferenceGroup mPairedDevicesCategory;
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            int state = intent.getIntExtra("android.bluetooth.adapter.extra.STATE", Integer.MIN_VALUE);
            if (action.equals("android.bluetooth.adapter.action.LOCAL_NAME_CHANGED")) {
                updateDeviceName(context);
            }
            if (state == 12) {
                BluetoothSettings.this.mInitiateDiscoverable = true;
            }
        }

        private void updateDeviceName(Context context) {
            if (BluetoothSettings.this.mLocalAdapter.isEnabled() && BluetoothSettings.this.mMyDevicePreference != null) {
                BluetoothSettings.this.mMyDevicePreference.setSummary(context.getResources().getString(R.string.bluetooth_is_visible_message, new Object[]{BluetoothSettings.this.mLocalAdapter.getName()}));
            }
        }
    };
    private SwitchBar mSwitchBar;

    public BluetoothSettings() {
        super("no_config_bluetooth");
    }

    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        this.mInitialScanStarted = savedInstanceState != null;
        this.mInitiateDiscoverable = true;
        this.mEmptyView = (TextView) getView().findViewById(16908292);
        getListView().setEmptyView(this.mEmptyView);
        SettingsActivity activity = (SettingsActivity) getActivity();
        this.mSwitchBar = activity.getSwitchBar();
        this.mBluetoothEnabler = new BluetoothEnabler(activity, this.mSwitchBar);
        this.mBluetoothEnabler.setupSwitchBar();
    }

    public void onDestroyView() {
        super.onDestroyView();
        this.mBluetoothEnabler.teardownSwitchBar();
    }

    void addPreferencesForActivity() {
        addPreferencesFromResource(R.xml.bluetooth_settings);
        setHasOptionsMenu(true);
    }

    public void onResume() {
        if (this.mBluetoothEnabler != null) {
            this.mBluetoothEnabler.resume(getActivity());
        }
        super.onResume();
        this.mInitiateDiscoverable = true;
        if (isUiRestricted()) {
            setDeviceListGroup(getPreferenceScreen());
            removeAllDevices();
            this.mEmptyView.setText(R.string.bluetooth_empty_list_user_restricted);
            return;
        }
        getActivity().registerReceiver(this.mReceiver, this.mIntentFilter);
        if (this.mLocalAdapter != null) {
            updateContent(this.mLocalAdapter.getBluetoothState());
        }
    }

    public void onPause() {
        super.onPause();
        if (this.mBluetoothEnabler != null) {
            this.mBluetoothEnabler.pause();
        }
        this.mLocalAdapter.setScanMode(21);
        if (!isUiRestricted()) {
            getActivity().unregisterReceiver(this.mReceiver);
        }
    }

    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        boolean z = true;
        if (this.mLocalAdapter != null && !isUiRestricted()) {
            boolean bluetoothIsEnabled;
            if (this.mLocalAdapter.getBluetoothState() == 12) {
                bluetoothIsEnabled = true;
            } else {
                bluetoothIsEnabled = false;
            }
            boolean isDiscovering = this.mLocalAdapter.isDiscovering();
            MenuItem add = menu.add(0, 1, 0, isDiscovering ? R.string.bluetooth_searching_for_devices : R.string.bluetooth_search_for_devices);
            if (!bluetoothIsEnabled || isDiscovering) {
                z = false;
            }
            add.setEnabled(z).setShowAsAction(0);
            menu.add(0, 2, 0, R.string.bluetooth_rename_device).setEnabled(bluetoothIsEnabled).setShowAsAction(0);
            menu.add(0, 3, 0, R.string.bluetooth_show_received_files).setShowAsAction(0);
            super.onCreateOptionsMenu(menu, inflater);
        }
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case 1:
                if (this.mLocalAdapter.getBluetoothState() != 12) {
                    return true;
                }
                startScanning();
                return true;
            case 2:
                new BluetoothNameDialogFragment().show(getFragmentManager(), "rename device");
                return true;
            case 3:
                getActivity().sendBroadcast(new Intent("android.btopp.intent.action.OPEN_RECEIVED_FILES"));
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void startScanning() {
        if (!isUiRestricted()) {
            if (!this.mAvailableDevicesCategoryIsPresent) {
                getPreferenceScreen().addPreference(this.mAvailableDevicesCategory);
                this.mAvailableDevicesCategoryIsPresent = true;
            }
            if (this.mAvailableDevicesCategory != null) {
                setDeviceListGroup(this.mAvailableDevicesCategory);
                removeAllDevices();
            }
            this.mLocalManager.getCachedDeviceManager().clearNonBondedDevices();
            this.mAvailableDevicesCategory.removeAll();
            this.mInitialScanStarted = true;
            this.mLocalAdapter.startScanning(true);
        }
    }

    void onDevicePreferenceClick(BluetoothDevicePreference btPreference) {
        this.mLocalAdapter.stopScanning();
        super.onDevicePreferenceClick(btPreference);
    }

    private void addDeviceCategory(PreferenceGroup preferenceGroup, int titleId, Filter filter, boolean addCachedDevices) {
        preferenceGroup.setTitle(titleId);
        getPreferenceScreen().addPreference(preferenceGroup);
        setFilter(filter);
        setDeviceListGroup(preferenceGroup);
        if (addCachedDevices) {
            addCachedDevices();
        }
        preferenceGroup.setEnabled(true);
    }

    private void updateContent(int bluetoothState) {
        PreferenceScreen preferenceScreen = getPreferenceScreen();
        int messageId = 0;
        switch (bluetoothState) {
            case 10:
                messageId = R.string.bluetooth_empty_list_bluetooth_off;
                if (isUiRestricted()) {
                    messageId = R.string.bluetooth_empty_list_user_restricted;
                    break;
                }
                break;
            case 11:
                messageId = R.string.bluetooth_turning_on;
                this.mInitialScanStarted = false;
                break;
            case 12:
                preferenceScreen.removeAll();
                preferenceScreen.setOrderingAsAdded(true);
                this.mDevicePreferenceMap.clear();
                if (isUiRestricted()) {
                    messageId = R.string.bluetooth_empty_list_user_restricted;
                    break;
                }
                if (this.mPairedDevicesCategory == null) {
                    this.mPairedDevicesCategory = new PreferenceCategory(getActivity());
                } else {
                    this.mPairedDevicesCategory.removeAll();
                }
                addDeviceCategory(this.mPairedDevicesCategory, R.string.bluetooth_preference_paired_devices, BluetoothDeviceFilter.BONDED_DEVICE_FILTER, true);
                int numberOfPairedDevices = this.mPairedDevicesCategory.getPreferenceCount();
                if (isUiRestricted() || numberOfPairedDevices <= 0) {
                    preferenceScreen.removePreference(this.mPairedDevicesCategory);
                }
                if (this.mAvailableDevicesCategory == null) {
                    this.mAvailableDevicesCategory = new BluetoothProgressCategory(getActivity());
                    this.mAvailableDevicesCategory.setSelectable(false);
                } else {
                    this.mAvailableDevicesCategory.removeAll();
                }
                addDeviceCategory(this.mAvailableDevicesCategory, R.string.bluetooth_preference_found_devices, BluetoothDeviceFilter.UNBONDED_DEVICE_FILTER, this.mInitialScanStarted);
                int numberOfAvailableDevices = this.mAvailableDevicesCategory.getPreferenceCount();
                if (!this.mInitialScanStarted) {
                    startScanning();
                }
                if (this.mMyDevicePreference == null) {
                    this.mMyDevicePreference = new Preference(getActivity());
                }
                this.mMyDevicePreference.setSummary(getResources().getString(R.string.bluetooth_is_visible_message, new Object[]{this.mLocalAdapter.getName()}));
                this.mMyDevicePreference.setSelectable(false);
                preferenceScreen.addPreference(this.mMyDevicePreference);
                getActivity().invalidateOptionsMenu();
                if (this.mInitiateDiscoverable) {
                    this.mLocalAdapter.setScanMode(23);
                    this.mInitiateDiscoverable = false;
                    return;
                }
                return;
            case 13:
                messageId = R.string.bluetooth_turning_off;
                break;
        }
        setDeviceListGroup(preferenceScreen);
        removeAllDevices();
        this.mEmptyView.setText(messageId);
        if (!isUiRestricted()) {
            getActivity().invalidateOptionsMenu();
        }
    }

    public void onBluetoothStateChanged(int bluetoothState) {
        super.onBluetoothStateChanged(bluetoothState);
        updateContent(bluetoothState);
    }

    public void onScanningStateChanged(boolean started) {
        super.onScanningStateChanged(started);
        if (getActivity() != null) {
            getActivity().invalidateOptionsMenu();
        }
    }

    public void onDeviceBondStateChanged(CachedBluetoothDevice cachedDevice, int bondState) {
        setDeviceListGroup(getPreferenceScreen());
        removeAllDevices();
        updateContent(this.mLocalAdapter.getBluetoothState());
    }

    void initDevicePreference(BluetoothDevicePreference preference) {
        if (preference.getCachedDevice().getBondState() == 12) {
            preference.setOnSettingsClickListener(this.mDeviceProfilesListener);
        }
    }

    protected int getHelpResource() {
        return R.string.help_url_bluetooth;
    }
}
