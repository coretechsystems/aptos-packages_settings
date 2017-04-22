package com.android.settings.bluetooth;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothUuid;
import android.content.Context;
import android.content.Intent;
import android.os.ParcelUuid;
import android.util.Log;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

final class LocalBluetoothProfileManager {
    private A2dpProfile mA2dpProfile;
    private final Context mContext;
    private final CachedBluetoothDeviceManager mDeviceManager;
    private final BluetoothEventManager mEventManager;
    private HeadsetProfile mHeadsetProfile;
    private final HidProfile mHidProfile;
    private final LocalBluetoothAdapter mLocalAdapter;
    private MapProfile mMapProfile;
    private OppProfile mOppProfile;
    private final PanProfile mPanProfile;
    private final PbapServerProfile mPbapProfile;
    private final Map<String, LocalBluetoothProfile> mProfileNameMap = new HashMap();
    private final Collection<ServiceListener> mServiceListeners = new ArrayList();

    public interface ServiceListener {
        void onServiceConnected();

        void onServiceDisconnected();
    }

    private class StateChangedHandler implements Handler {
        final LocalBluetoothProfile mProfile;

        StateChangedHandler(LocalBluetoothProfile profile) {
            this.mProfile = profile;
        }

        public void onReceive(Context context, Intent intent, BluetoothDevice device) {
            CachedBluetoothDevice cachedDevice = LocalBluetoothProfileManager.this.mDeviceManager.findDevice(device);
            if (cachedDevice == null) {
                Log.w("LocalBluetoothProfileManager", "StateChangedHandler found new device: " + device);
                cachedDevice = LocalBluetoothProfileManager.this.mDeviceManager.addDevice(LocalBluetoothProfileManager.this.mLocalAdapter, LocalBluetoothProfileManager.this, device);
            }
            int newState = intent.getIntExtra("android.bluetooth.profile.extra.STATE", 0);
            int oldState = intent.getIntExtra("android.bluetooth.profile.extra.PREVIOUS_STATE", 0);
            if (newState == 0 && oldState == 1) {
                Log.i("LocalBluetoothProfileManager", "Failed to connect " + this.mProfile + " device");
            }
            cachedDevice.onProfileStateChanged(this.mProfile, newState);
            cachedDevice.refresh();
        }
    }

    private class PanStateChangedHandler extends StateChangedHandler {
        PanStateChangedHandler(LocalBluetoothProfile profile) {
            super(profile);
        }

        public void onReceive(Context context, Intent intent, BluetoothDevice device) {
            this.mProfile.setLocalRole(device, intent.getIntExtra("android.bluetooth.pan.extra.LOCAL_ROLE", 0));
            super.onReceive(context, intent, device);
        }
    }

    LocalBluetoothProfileManager(Context context, LocalBluetoothAdapter adapter, CachedBluetoothDeviceManager deviceManager, BluetoothEventManager eventManager) {
        this.mContext = context;
        this.mLocalAdapter = adapter;
        this.mDeviceManager = deviceManager;
        this.mEventManager = eventManager;
        this.mLocalAdapter.setProfileManager(this);
        this.mEventManager.setProfileManager(this);
        ParcelUuid[] uuids = adapter.getUuids();
        if (uuids != null) {
            updateLocalProfiles(uuids);
        }
        this.mHidProfile = new HidProfile(context, this.mLocalAdapter, this.mDeviceManager, this);
        addProfile(this.mHidProfile, "HID", "android.bluetooth.input.profile.action.CONNECTION_STATE_CHANGED");
        this.mPanProfile = new PanProfile(context);
        addPanProfile(this.mPanProfile, "PAN", "android.bluetooth.pan.profile.action.CONNECTION_STATE_CHANGED");
        Log.d("LocalBluetoothProfileManager", "Adding local MAP profile");
        this.mMapProfile = new MapProfile(this.mContext, this.mLocalAdapter, this.mDeviceManager, this);
        addProfile(this.mMapProfile, "MAP", "android.bluetooth.map.profile.action.CONNECTION_STATE_CHANGED");
        this.mPbapProfile = new PbapServerProfile(context);
        Log.d("LocalBluetoothProfileManager", "LocalBluetoothProfileManager construction complete");
    }

    void updateLocalProfiles(ParcelUuid[] uuids) {
        if (BluetoothUuid.isUuidPresent(uuids, BluetoothUuid.AudioSource)) {
            if (this.mA2dpProfile == null) {
                Log.d("LocalBluetoothProfileManager", "Adding local A2DP profile");
                this.mA2dpProfile = new A2dpProfile(this.mContext, this.mLocalAdapter, this.mDeviceManager, this);
                addProfile(this.mA2dpProfile, "A2DP", "android.bluetooth.a2dp.profile.action.CONNECTION_STATE_CHANGED");
            }
        } else if (this.mA2dpProfile != null) {
            Log.w("LocalBluetoothProfileManager", "Warning: A2DP profile was previously added but the UUID is now missing.");
        }
        if (BluetoothUuid.isUuidPresent(uuids, BluetoothUuid.Handsfree_AG) || BluetoothUuid.isUuidPresent(uuids, BluetoothUuid.HSP_AG)) {
            if (this.mHeadsetProfile == null) {
                Log.d("LocalBluetoothProfileManager", "Adding local HEADSET profile");
                this.mHeadsetProfile = new HeadsetProfile(this.mContext, this.mLocalAdapter, this.mDeviceManager, this);
                addProfile(this.mHeadsetProfile, "HEADSET", "android.bluetooth.headset.profile.action.CONNECTION_STATE_CHANGED");
            }
        } else if (this.mHeadsetProfile != null) {
            Log.w("LocalBluetoothProfileManager", "Warning: HEADSET profile was previously added but the UUID is now missing.");
        }
        if (BluetoothUuid.isUuidPresent(uuids, BluetoothUuid.ObexObjectPush)) {
            if (this.mOppProfile == null) {
                Log.d("LocalBluetoothProfileManager", "Adding local OPP profile");
                this.mOppProfile = new OppProfile();
                this.mProfileNameMap.put("OPP", this.mOppProfile);
            }
        } else if (this.mOppProfile != null) {
            Log.w("LocalBluetoothProfileManager", "Warning: OPP profile was previously added but the UUID is now missing.");
        }
        this.mEventManager.registerProfileIntentReceiver();
    }

    private void addProfile(LocalBluetoothProfile profile, String profileName, String stateChangedAction) {
        this.mEventManager.addProfileHandler(stateChangedAction, new StateChangedHandler(profile));
        this.mProfileNameMap.put(profileName, profile);
    }

    private void addPanProfile(LocalBluetoothProfile profile, String profileName, String stateChangedAction) {
        this.mEventManager.addProfileHandler(stateChangedAction, new PanStateChangedHandler(profile));
        this.mProfileNameMap.put(profileName, profile);
    }

    LocalBluetoothProfile getProfileByName(String name) {
        return (LocalBluetoothProfile) this.mProfileNameMap.get(name);
    }

    void setBluetoothStateOn() {
        ParcelUuid[] uuids = this.mLocalAdapter.getUuids();
        if (uuids != null) {
            updateLocalProfiles(uuids);
        }
        this.mEventManager.readPairedDevices();
    }

    void addServiceListener(ServiceListener l) {
        this.mServiceListeners.add(l);
    }

    void removeServiceListener(ServiceListener l) {
        this.mServiceListeners.remove(l);
    }

    void callServiceConnectedListeners() {
        for (ServiceListener l : this.mServiceListeners) {
            l.onServiceConnected();
        }
    }

    void callServiceDisconnectedListeners() {
        for (ServiceListener listener : this.mServiceListeners) {
            listener.onServiceDisconnected();
        }
    }

    public synchronized boolean isManagerReady() {
        boolean isProfileReady;
        LocalBluetoothProfile profile = this.mHeadsetProfile;
        if (profile != null) {
            isProfileReady = profile.isProfileReady();
        } else {
            profile = this.mA2dpProfile;
            if (profile != null) {
                isProfileReady = profile.isProfileReady();
            } else {
                isProfileReady = false;
            }
        }
        return isProfileReady;
    }

    A2dpProfile getA2dpProfile() {
        return this.mA2dpProfile;
    }

    HeadsetProfile getHeadsetProfile() {
        return this.mHeadsetProfile;
    }

    PbapServerProfile getPbapProfile() {
        return this.mPbapProfile;
    }

    MapProfile getMapProfile() {
        return this.mMapProfile;
    }

    synchronized void updateProfiles(ParcelUuid[] uuids, ParcelUuid[] localUuids, Collection<LocalBluetoothProfile> profiles, Collection<LocalBluetoothProfile> removedProfiles, boolean isPanNapConnected, BluetoothDevice device) {
        removedProfiles.clear();
        removedProfiles.addAll(profiles);
        profiles.clear();
        if (uuids != null) {
            if (this.mHeadsetProfile != null && ((BluetoothUuid.isUuidPresent(localUuids, BluetoothUuid.HSP_AG) && BluetoothUuid.isUuidPresent(uuids, BluetoothUuid.HSP)) || (BluetoothUuid.isUuidPresent(localUuids, BluetoothUuid.Handsfree_AG) && BluetoothUuid.isUuidPresent(uuids, BluetoothUuid.Handsfree)))) {
                profiles.add(this.mHeadsetProfile);
                removedProfiles.remove(this.mHeadsetProfile);
            }
            if (BluetoothUuid.containsAnyUuid(uuids, A2dpProfile.SINK_UUIDS) && this.mA2dpProfile != null) {
                profiles.add(this.mA2dpProfile);
                removedProfiles.remove(this.mA2dpProfile);
            }
            if (BluetoothUuid.isUuidPresent(uuids, BluetoothUuid.ObexObjectPush) && this.mOppProfile != null) {
                profiles.add(this.mOppProfile);
                removedProfiles.remove(this.mOppProfile);
            }
            if ((BluetoothUuid.isUuidPresent(uuids, BluetoothUuid.Hid) || BluetoothUuid.isUuidPresent(uuids, BluetoothUuid.Hogp)) && this.mHidProfile != null) {
                profiles.add(this.mHidProfile);
                removedProfiles.remove(this.mHidProfile);
            }
            if (isPanNapConnected) {
                Log.d("LocalBluetoothProfileManager", "Valid PAN-NAP connection exists.");
            }
            if ((BluetoothUuid.isUuidPresent(uuids, BluetoothUuid.NAP) && this.mPanProfile != null) || isPanNapConnected) {
                profiles.add(this.mPanProfile);
                removedProfiles.remove(this.mPanProfile);
            }
            if (this.mMapProfile != null && this.mMapProfile.getConnectionStatus(device) == 2) {
                profiles.add(this.mMapProfile);
                removedProfiles.remove(this.mMapProfile);
                this.mMapProfile.setPreferred(device, true);
            }
        }
    }
}
