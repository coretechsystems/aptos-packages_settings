package com.android.settings.bluetooth;

import android.app.AlertDialog;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceGroup;
import android.text.Html;
import android.text.TextUtils;
import android.util.Log;
import android.widget.EditText;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.bluetooth.CachedBluetoothDevice.Callback;
import java.util.HashMap;

public final class DeviceProfilesSettings extends SettingsPreferenceFragment implements OnPreferenceChangeListener, Callback {
    private final HashMap<LocalBluetoothProfile, CheckBoxPreference> mAutoConnectPrefs = new HashMap();
    private CachedBluetoothDevice mCachedDevice;
    private EditTextPreference mDeviceNamePref;
    private AlertDialog mDisconnectDialog;
    private LocalBluetoothManager mManager;
    private PreferenceGroup mProfileContainer;
    private boolean mProfileGroupIsRemoved;
    private LocalBluetoothProfileManager mProfileManager;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.bluetooth_device_advanced);
        getPreferenceScreen().setOrderingAsAdded(false);
        this.mProfileContainer = (PreferenceGroup) findPreference("profile_container");
        this.mProfileContainer.setLayoutResource(R.layout.bluetooth_preference_category);
        this.mManager = LocalBluetoothManager.getInstance(getActivity());
        CachedBluetoothDeviceManager deviceManager = this.mManager.getCachedDeviceManager();
        this.mProfileManager = this.mManager.getProfileManager();
    }

    public void onDestroy() {
        super.onDestroy();
        if (this.mDisconnectDialog != null) {
            this.mDisconnectDialog.dismiss();
            this.mDisconnectDialog = null;
        }
        if (this.mCachedDevice != null) {
            this.mCachedDevice.unregisterCallback(this);
        }
    }

    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
    }

    public void onResume() {
        super.onResume();
        this.mManager.setForegroundActivity(getActivity());
        if (this.mCachedDevice != null) {
            this.mCachedDevice.registerCallback(this);
            if (this.mCachedDevice.getBondState() == 10) {
                finish();
            } else {
                refresh();
            }
        }
    }

    public void onPause() {
        super.onPause();
        if (this.mCachedDevice != null) {
            this.mCachedDevice.unregisterCallback(this);
        }
        this.mManager.setForegroundActivity(null);
    }

    public void setDevice(CachedBluetoothDevice cachedDevice) {
        this.mCachedDevice = cachedDevice;
        if (isResumed()) {
            this.mCachedDevice.registerCallback(this);
            addPreferencesForProfiles();
            refresh();
        }
    }

    private void addPreferencesForProfiles() {
        this.mProfileContainer.removeAll();
        for (LocalBluetoothProfile profile : this.mCachedDevice.getConnectableProfiles()) {
            this.mProfileContainer.addPreference(createProfilePreference(profile));
        }
        if (this.mCachedDevice.getPhonebookPermissionChoice() != 0) {
            this.mProfileContainer.addPreference(createProfilePreference(this.mManager.getProfileManager().getPbapProfile()));
        }
        MapProfile mapProfile = this.mManager.getProfileManager().getMapProfile();
        if (this.mCachedDevice.getMessagePermissionChoice() != 0) {
            this.mProfileContainer.addPreference(createProfilePreference(mapProfile));
        }
        showOrHideProfileGroup();
    }

    private void showOrHideProfileGroup() {
        int numProfiles = this.mProfileContainer.getPreferenceCount();
        if (!this.mProfileGroupIsRemoved && numProfiles == 0) {
            getPreferenceScreen().removePreference(this.mProfileContainer);
            this.mProfileGroupIsRemoved = true;
        } else if (this.mProfileGroupIsRemoved && numProfiles != 0) {
            getPreferenceScreen().addPreference(this.mProfileContainer);
            this.mProfileGroupIsRemoved = false;
        }
    }

    private CheckBoxPreference createProfilePreference(LocalBluetoothProfile profile) {
        CheckBoxPreference pref = new CheckBoxPreference(getActivity());
        pref.setLayoutResource(R.layout.preference_start_widget);
        pref.setKey(profile.toString());
        pref.setTitle(profile.getNameResource(this.mCachedDevice.getDevice()));
        pref.setPersistent(false);
        pref.setOrder(getProfilePreferenceIndex(profile.getOrdinal()));
        pref.setOnPreferenceChangeListener(this);
        int iconResource = profile.getDrawableResource(this.mCachedDevice.getBtClass());
        if (iconResource != 0) {
            pref.setIcon(getResources().getDrawable(iconResource));
        }
        refreshProfilePreference(pref, profile);
        return pref;
    }

    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (preference == this.mDeviceNamePref) {
            this.mCachedDevice.setName((String) newValue);
            return true;
        } else if (!(preference instanceof CheckBoxPreference)) {
            return false;
        } else {
            onProfileClicked(getProfileOf(preference), (CheckBoxPreference) preference);
            return false;
        }
    }

    private void onProfileClicked(LocalBluetoothProfile profile, CheckBoxPreference profilePref) {
        int newPermission = 2;
        boolean z = true;
        BluetoothDevice device = this.mCachedDevice.getDevice();
        if (profilePref.getKey().equals("PBAP Server")) {
            if (this.mCachedDevice.getPhonebookPermissionChoice() != 1) {
                newPermission = 1;
            }
            this.mCachedDevice.setPhonebookPermissionChoice(newPermission);
            if (newPermission != 1) {
                z = false;
            }
            profilePref.setChecked(z);
            return;
        }
        boolean isConnected;
        if (profile.getConnectionStatus(device) == 2) {
            isConnected = true;
        } else {
            isConnected = false;
        }
        if (profilePref.isChecked()) {
            askDisconnect(this.mManager.getForegroundActivity(), profile);
            return;
        }
        if (profile instanceof MapProfile) {
            this.mCachedDevice.setMessagePermissionChoice(1);
            refreshProfilePreference(profilePref, profile);
        }
        if (profile.isPreferred(device)) {
            profile.setPreferred(device, false);
            refreshProfilePreference(profilePref, profile);
            return;
        }
        profile.setPreferred(device, true);
        this.mCachedDevice.connectProfile(profile);
    }

    private void askDisconnect(Context context, final LocalBluetoothProfile profile) {
        final CachedBluetoothDevice device = this.mCachedDevice;
        String name = device.getName();
        if (TextUtils.isEmpty(name)) {
            name = context.getString(R.string.bluetooth_device);
        }
        String profileName = context.getString(profile.getNameResource(device.getDevice()));
        String title = context.getString(R.string.bluetooth_disable_profile_title);
        String message = context.getString(R.string.bluetooth_disable_profile_message, new Object[]{profileName, name});
        this.mDisconnectDialog = Utils.showDisconnectDialog(context, this.mDisconnectDialog, new OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                device.disconnect(profile);
                profile.setPreferred(device.getDevice(), false);
                if (profile instanceof MapProfile) {
                    device.setMessagePermissionChoice(2);
                    DeviceProfilesSettings.this.refreshProfilePreference((CheckBoxPreference) DeviceProfilesSettings.this.findPreference(profile.toString()), profile);
                }
            }
        }, title, Html.fromHtml(message));
    }

    public void onDeviceAttributesChanged() {
        refresh();
    }

    private void refresh() {
        EditText deviceNameField = (EditText) getView().findViewById(R.id.name);
        if (deviceNameField != null) {
            deviceNameField.setText(this.mCachedDevice.getName());
        }
        refreshProfiles();
    }

    private void refreshProfiles() {
        for (LocalBluetoothProfile profile : this.mCachedDevice.getConnectableProfiles()) {
            CheckBoxPreference profilePref = (CheckBoxPreference) findPreference(profile.toString());
            if (profilePref == null) {
                this.mProfileContainer.addPreference(createProfilePreference(profile));
            } else {
                refreshProfilePreference(profilePref, profile);
            }
        }
        for (LocalBluetoothProfile profile2 : this.mCachedDevice.getRemovedProfiles()) {
            Preference profilePref2 = findPreference(profile2.toString());
            if (profilePref2 != null) {
                Log.d("DeviceProfilesSettings", "Removing " + profile2.toString() + " from profile list");
                this.mProfileContainer.removePreference(profilePref2);
            }
        }
        showOrHideProfileGroup();
    }

    private void refreshProfilePreference(CheckBoxPreference profilePref, LocalBluetoothProfile profile) {
        boolean z = true;
        BluetoothDevice device = this.mCachedDevice.getDevice();
        profilePref.setEnabled(!this.mCachedDevice.isBusy());
        if (profile instanceof MapProfile) {
            if (this.mCachedDevice.getMessagePermissionChoice() != 1) {
                z = false;
            }
            profilePref.setChecked(z);
        } else if (profile instanceof PbapServerProfile) {
            if (this.mCachedDevice.getPhonebookPermissionChoice() != 1) {
                z = false;
            }
            profilePref.setChecked(z);
        } else {
            profilePref.setChecked(profile.isPreferred(device));
        }
    }

    private LocalBluetoothProfile getProfileOf(Preference pref) {
        LocalBluetoothProfile localBluetoothProfile = null;
        if ((pref instanceof CheckBoxPreference) && !TextUtils.isEmpty(pref.getKey())) {
            try {
                localBluetoothProfile = this.mProfileManager.getProfileByName(pref.getKey());
            } catch (IllegalArgumentException e) {
            }
        }
        return localBluetoothProfile;
    }

    private int getProfilePreferenceIndex(int profIndex) {
        return this.mProfileContainer.getOrder() + (profIndex * 10);
    }
}
