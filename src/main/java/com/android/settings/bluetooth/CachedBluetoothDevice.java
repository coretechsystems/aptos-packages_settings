package com.android.settings.bluetooth;

import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.ParcelUuid;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

final class CachedBluetoothDevice implements Comparable<CachedBluetoothDevice> {
    private BluetoothClass mBtClass;
    private final Collection<Callback> mCallbacks = new ArrayList();
    private boolean mConnectAfterPairing;
    private long mConnectAttempted;
    private final Context mContext;
    private final BluetoothDevice mDevice;
    private boolean mIsConnectingErrorPossible;
    private final LocalBluetoothAdapter mLocalAdapter;
    private boolean mLocalNapRoleConnected;
    private int mMessageRejectionCount;
    private String mName;
    private HashMap<LocalBluetoothProfile, Integer> mProfileConnectionState;
    private final LocalBluetoothProfileManager mProfileManager;
    private final List<LocalBluetoothProfile> mProfiles = new ArrayList();
    private final List<LocalBluetoothProfile> mRemovedProfiles = new ArrayList();
    private short mRssi;
    private boolean mVisible;

    public interface Callback {
        void onDeviceAttributesChanged();
    }

    private String describe(LocalBluetoothProfile profile) {
        StringBuilder sb = new StringBuilder();
        sb.append("Address:").append(this.mDevice);
        if (profile != null) {
            sb.append(" Profile:").append(profile);
        }
        return sb.toString();
    }

    void onProfileStateChanged(LocalBluetoothProfile profile, int newProfileState) {
        Log.d("CachedBluetoothDevice", "onProfileStateChanged: profile " + profile + " newProfileState " + newProfileState);
        if (this.mLocalAdapter.getBluetoothState() == 13) {
            Log.d("CachedBluetoothDevice", " BT Turninig Off...Profile conn state change ignored...");
            return;
        }
        this.mProfileConnectionState.put(profile, Integer.valueOf(newProfileState));
        if (newProfileState == 2) {
            if (profile instanceof MapProfile) {
                profile.setPreferred(this.mDevice, true);
            } else if (!this.mProfiles.contains(profile)) {
                this.mRemovedProfiles.remove(profile);
                this.mProfiles.add(profile);
                if ((profile instanceof PanProfile) && ((PanProfile) profile).isLocalRoleNap(this.mDevice)) {
                    this.mLocalNapRoleConnected = true;
                }
            }
        } else if ((profile instanceof MapProfile) && newProfileState == 0) {
            profile.setPreferred(this.mDevice, false);
        } else if (this.mLocalNapRoleConnected && (profile instanceof PanProfile) && ((PanProfile) profile).isLocalRoleNap(this.mDevice) && newProfileState == 0) {
            Log.d("CachedBluetoothDevice", "Removing PanProfile from device after NAP disconnect");
            this.mProfiles.remove(profile);
            this.mRemovedProfiles.add(profile);
            this.mLocalNapRoleConnected = false;
        }
    }

    CachedBluetoothDevice(Context context, LocalBluetoothAdapter adapter, LocalBluetoothProfileManager profileManager, BluetoothDevice device) {
        this.mContext = context;
        this.mLocalAdapter = adapter;
        this.mProfileManager = profileManager;
        this.mDevice = device;
        this.mProfileConnectionState = new HashMap();
        fillData();
    }

    void disconnect() {
        for (LocalBluetoothProfile profile : this.mProfiles) {
            disconnect(profile);
        }
        PbapServerProfile PbapProfile = this.mProfileManager.getPbapProfile();
        if (PbapProfile.getConnectionStatus(this.mDevice) == 2) {
            PbapProfile.disconnect(this.mDevice);
        }
    }

    void disconnect(LocalBluetoothProfile profile) {
        if (profile.disconnect(this.mDevice)) {
            Log.d("CachedBluetoothDevice", "Command sent successfully:DISCONNECT " + describe(profile));
        }
    }

    void connect(boolean connectAllProfiles) {
        if (ensurePaired()) {
            this.mConnectAttempted = SystemClock.elapsedRealtime();
            connectWithoutResettingTimer(connectAllProfiles);
        }
    }

    void onBondingDockConnect() {
        connect(false);
    }

    private void connectWithoutResettingTimer(boolean connectAllProfiles) {
        if (this.mProfiles.isEmpty()) {
            Log.d("CachedBluetoothDevice", "No profiles. Maybe we will connect later");
            return;
        }
        this.mIsConnectingErrorPossible = true;
        int preferredProfiles = 0;
        for (LocalBluetoothProfile profile : this.mProfiles) {
            if (connectAllProfiles) {
                if (!profile.isConnectable()) {
                }
            } else if (profile.isAutoConnectable()) {
            }
            if (profile.isPreferred(this.mDevice)) {
                preferredProfiles++;
                connectInt(profile);
            }
        }
        if (preferredProfiles == 0) {
            connectAutoConnectableProfiles();
        }
    }

    private void connectAutoConnectableProfiles() {
        if (ensurePaired()) {
            this.mIsConnectingErrorPossible = true;
            for (LocalBluetoothProfile profile : this.mProfiles) {
                if (profile.isAutoConnectable()) {
                    profile.setPreferred(this.mDevice, true);
                    connectInt(profile);
                }
            }
        }
    }

    void connectProfile(LocalBluetoothProfile profile) {
        this.mConnectAttempted = SystemClock.elapsedRealtime();
        this.mIsConnectingErrorPossible = true;
        connectInt(profile);
        refresh();
    }

    synchronized void connectInt(LocalBluetoothProfile profile) {
        if (ensurePaired()) {
            if (profile.connect(this.mDevice)) {
                Log.d("CachedBluetoothDevice", "Command sent successfully:CONNECT " + describe(profile));
            } else {
                Log.i("CachedBluetoothDevice", "Failed to connect " + profile.toString() + " to " + this.mName);
            }
        }
    }

    private boolean ensurePaired() {
        if (getBondState() != 10) {
            return true;
        }
        startPairing();
        return false;
    }

    boolean startPairing() {
        if (this.mLocalAdapter.isDiscovering()) {
            this.mLocalAdapter.cancelDiscovery();
        }
        if (!this.mDevice.createBond()) {
            return false;
        }
        this.mConnectAfterPairing = true;
        return true;
    }

    void unpair() {
        int state = getBondState();
        if (state == 11) {
            this.mDevice.cancelBondProcess();
        }
        if (state != 10) {
            BluetoothDevice dev = this.mDevice;
            if (dev != null && dev.removeBond()) {
                Log.d("CachedBluetoothDevice", "Command sent successfully:REMOVE_BOND " + describe(null));
            }
        }
    }

    int getProfileConnectionState(LocalBluetoothProfile profile) {
        if (this.mProfileConnectionState == null || this.mProfileConnectionState.get(profile) == null) {
            this.mProfileConnectionState.put(profile, Integer.valueOf(profile.getConnectionStatus(this.mDevice)));
        }
        return ((Integer) this.mProfileConnectionState.get(profile)).intValue();
    }

    public void clearProfileConnectionState() {
        Log.d("CachedBluetoothDevice", " Clearing all connection state for dev:" + this.mDevice.getName());
        for (LocalBluetoothProfile profile : getProfiles()) {
            this.mProfileConnectionState.put(profile, Integer.valueOf(0));
        }
    }

    private void fillData() {
        fetchName();
        fetchBtClass();
        updateProfiles();
        migratePhonebookPermissionChoice();
        migrateMessagePermissionChoice();
        fetchMessageRejectionCount();
        this.mVisible = false;
        dispatchAttributesChanged();
    }

    BluetoothDevice getDevice() {
        return this.mDevice;
    }

    String getName() {
        return this.mName;
    }

    void setNewName(String name) {
        if (this.mName == null) {
            this.mName = name;
            if (this.mName == null || TextUtils.isEmpty(this.mName)) {
                this.mName = this.mDevice.getAddress();
            }
            dispatchAttributesChanged();
        }
    }

    void setName(String name) {
        if (!this.mName.equals(name)) {
            this.mName = name;
            this.mDevice.setAlias(name);
            dispatchAttributesChanged();
        }
    }

    void refreshName() {
        fetchName();
        dispatchAttributesChanged();
    }

    private void fetchName() {
        this.mName = this.mDevice.getAliasName();
        if (TextUtils.isEmpty(this.mName)) {
            this.mName = this.mDevice.getAddress();
        }
    }

    void refresh() {
        dispatchAttributesChanged();
    }

    void setVisible(boolean visible) {
        if (this.mVisible != visible) {
            this.mVisible = visible;
            dispatchAttributesChanged();
        }
    }

    int getBondState() {
        return this.mDevice.getBondState();
    }

    void setRssi(short rssi) {
        if (this.mRssi != rssi) {
            this.mRssi = rssi;
            dispatchAttributesChanged();
        }
    }

    boolean isConnected() {
        for (LocalBluetoothProfile profile : this.mProfiles) {
            if (getProfileConnectionState(profile) == 2) {
                return true;
            }
        }
        return false;
    }

    boolean isBusy() {
        for (LocalBluetoothProfile profile : this.mProfiles) {
            int status = getProfileConnectionState(profile);
            if (status == 1) {
                return true;
            }
            if (status == 3) {
                return true;
            }
        }
        if (getBondState() != 11) {
            return false;
        }
        return true;
    }

    private void fetchBtClass() {
        this.mBtClass = this.mDevice.getBluetoothClass();
    }

    private boolean updateProfiles() {
        ParcelUuid[] uuids = this.mDevice.getUuids();
        if (uuids == null) {
            return false;
        }
        ParcelUuid[] localUuids = this.mLocalAdapter.getUuids();
        if (localUuids == null) {
            return false;
        }
        this.mProfileManager.updateProfiles(uuids, localUuids, this.mProfiles, this.mRemovedProfiles, this.mLocalNapRoleConnected, this.mDevice);
        return true;
    }

    void refreshBtClass() {
        fetchBtClass();
        dispatchAttributesChanged();
    }

    void onUuidChanged() {
        updateProfiles();
        if (!this.mProfiles.isEmpty() && this.mConnectAttempted + 5000 > SystemClock.elapsedRealtime()) {
            connectWithoutResettingTimer(false);
        }
        dispatchAttributesChanged();
    }

    void onBondingStateChanged(int bondState) {
        if (bondState == 10) {
            this.mProfiles.clear();
            this.mConnectAfterPairing = false;
            setPhonebookPermissionChoice(0);
            setMessagePermissionChoice(0);
            this.mMessageRejectionCount = 0;
            saveMessageRejectionCount();
        }
        refresh();
        if (bondState == 12) {
            if (this.mDevice.isBluetoothDock()) {
                onBondingDockConnect();
            } else if (this.mConnectAfterPairing) {
                connect(false);
            }
            this.mConnectAfterPairing = false;
        }
    }

    void setBtClass(BluetoothClass btClass) {
        if (btClass != null && this.mBtClass != btClass) {
            this.mBtClass = btClass;
            dispatchAttributesChanged();
        }
    }

    BluetoothClass getBtClass() {
        return this.mBtClass;
    }

    List<LocalBluetoothProfile> getProfiles() {
        return Collections.unmodifiableList(this.mProfiles);
    }

    List<LocalBluetoothProfile> getConnectableProfiles() {
        List<LocalBluetoothProfile> connectableProfiles = new ArrayList();
        for (LocalBluetoothProfile profile : this.mProfiles) {
            if (profile.isConnectable()) {
                connectableProfiles.add(profile);
            }
        }
        return connectableProfiles;
    }

    List<LocalBluetoothProfile> getRemovedProfiles() {
        return this.mRemovedProfiles;
    }

    void registerCallback(Callback callback) {
        synchronized (this.mCallbacks) {
            this.mCallbacks.add(callback);
        }
    }

    void unregisterCallback(Callback callback) {
        synchronized (this.mCallbacks) {
            this.mCallbacks.remove(callback);
        }
    }

    private void dispatchAttributesChanged() {
        synchronized (this.mCallbacks) {
            for (Callback callback : this.mCallbacks) {
                callback.onDeviceAttributesChanged();
            }
        }
    }

    public String toString() {
        return this.mDevice.toString();
    }

    public boolean equals(Object o) {
        if (o == null || !(o instanceof CachedBluetoothDevice)) {
            return false;
        }
        return this.mDevice.equals(((CachedBluetoothDevice) o).mDevice);
    }

    public int hashCode() {
        return this.mDevice.getAddress().hashCode();
    }

    public int compareTo(CachedBluetoothDevice another) {
        int i;
        int i2 = 1;
        if (another.isConnected()) {
            i = 1;
        } else {
            i = 0;
        }
        int comparison = i - (isConnected() ? 1 : 0);
        if (comparison != 0) {
            return comparison;
        }
        if (another.getBondState() == 12) {
            i = 1;
        } else {
            i = 0;
        }
        comparison = i - (getBondState() == 12 ? 1 : 0);
        if (comparison != 0) {
            return comparison;
        }
        if (another.mVisible) {
            i = 1;
        } else {
            i = 0;
        }
        if (!this.mVisible) {
            i2 = 0;
        }
        comparison = i - i2;
        if (comparison != 0) {
            return comparison;
        }
        comparison = another.mRssi - this.mRssi;
        if (comparison != 0) {
            return comparison;
        }
        return this.mName.compareTo(another.mName);
    }

    int getPhonebookPermissionChoice() {
        int permission = this.mDevice.getPhonebookAccessPermission();
        if (permission == 1) {
            return 1;
        }
        if (permission == 2) {
            return 2;
        }
        return 0;
    }

    void setPhonebookPermissionChoice(int permissionChoice) {
        int permission = 0;
        if (permissionChoice == 1) {
            permission = 1;
        } else if (permissionChoice == 2) {
            permission = 2;
        }
        this.mDevice.setPhonebookAccessPermission(permission);
    }

    private void migratePhonebookPermissionChoice() {
        SharedPreferences preferences = this.mContext.getSharedPreferences("bluetooth_phonebook_permission", 0);
        if (preferences.contains(this.mDevice.getAddress())) {
            if (this.mDevice.getPhonebookAccessPermission() == 0) {
                int oldPermission = preferences.getInt(this.mDevice.getAddress(), 0);
                if (oldPermission == 1) {
                    this.mDevice.setPhonebookAccessPermission(1);
                } else if (oldPermission == 2) {
                    this.mDevice.setPhonebookAccessPermission(2);
                }
            }
            Editor editor = preferences.edit();
            editor.remove(this.mDevice.getAddress());
            editor.commit();
        }
    }

    int getMessagePermissionChoice() {
        int permission = this.mDevice.getMessageAccessPermission();
        if (permission == 1) {
            return 1;
        }
        if (permission == 2) {
            return 2;
        }
        return 0;
    }

    void setMessagePermissionChoice(int permissionChoice) {
        int permission = 0;
        if (permissionChoice == 1) {
            permission = 1;
        } else if (permissionChoice == 2) {
            permission = 2;
        }
        this.mDevice.setMessageAccessPermission(permission);
    }

    private void migrateMessagePermissionChoice() {
        SharedPreferences preferences = this.mContext.getSharedPreferences("bluetooth_message_permission", 0);
        if (preferences.contains(this.mDevice.getAddress())) {
            if (this.mDevice.getMessageAccessPermission() == 0) {
                int oldPermission = preferences.getInt(this.mDevice.getAddress(), 0);
                if (oldPermission == 1) {
                    this.mDevice.setMessageAccessPermission(1);
                } else if (oldPermission == 2) {
                    this.mDevice.setMessageAccessPermission(2);
                }
            }
            Editor editor = preferences.edit();
            editor.remove(this.mDevice.getAddress());
            editor.commit();
        }
    }

    boolean checkAndIncreaseMessageRejectionCount() {
        if (this.mMessageRejectionCount < 2) {
            this.mMessageRejectionCount++;
            saveMessageRejectionCount();
        }
        return this.mMessageRejectionCount >= 2;
    }

    private void fetchMessageRejectionCount() {
        this.mMessageRejectionCount = this.mContext.getSharedPreferences("bluetooth_message_reject", 0).getInt(this.mDevice.getAddress(), 0);
    }

    private void saveMessageRejectionCount() {
        Editor editor = this.mContext.getSharedPreferences("bluetooth_message_reject", 0).edit();
        if (this.mMessageRejectionCount == 0) {
            editor.remove(this.mDevice.getAddress());
        } else {
            editor.putInt(this.mDevice.getAddress(), this.mMessageRejectionCount);
        }
        editor.commit();
    }
}
