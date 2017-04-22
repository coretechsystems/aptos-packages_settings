package com.android.settings.bluetooth;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile.ServiceListener;
import android.content.Context;
import android.os.ParcelUuid;
import java.util.Set;

public final class LocalBluetoothAdapter {
    private static LocalBluetoothAdapter sInstance;
    private final BluetoothAdapter mAdapter;
    private long mLastScan;
    private LocalBluetoothProfileManager mProfileManager;
    private int mState = Integer.MIN_VALUE;

    private LocalBluetoothAdapter(BluetoothAdapter adapter) {
        this.mAdapter = adapter;
    }

    void setProfileManager(LocalBluetoothProfileManager manager) {
        this.mProfileManager = manager;
    }

    static synchronized LocalBluetoothAdapter getInstance() {
        LocalBluetoothAdapter localBluetoothAdapter;
        synchronized (LocalBluetoothAdapter.class) {
            if (sInstance == null) {
                BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
                if (adapter != null) {
                    sInstance = new LocalBluetoothAdapter(adapter);
                }
            }
            localBluetoothAdapter = sInstance;
        }
        return localBluetoothAdapter;
    }

    void cancelDiscovery() {
        this.mAdapter.cancelDiscovery();
    }

    boolean enable() {
        return this.mAdapter.enable();
    }

    boolean disable() {
        return this.mAdapter.disable();
    }

    void getProfileProxy(Context context, ServiceListener listener, int profile) {
        this.mAdapter.getProfileProxy(context, listener, profile);
    }

    Set<BluetoothDevice> getBondedDevices() {
        return this.mAdapter.getBondedDevices();
    }

    String getName() {
        return this.mAdapter.getName();
    }

    int getScanMode() {
        return this.mAdapter.getScanMode();
    }

    int getState() {
        return this.mAdapter.getState();
    }

    ParcelUuid[] getUuids() {
        return this.mAdapter.getUuids();
    }

    boolean isDiscovering() {
        return this.mAdapter.isDiscovering();
    }

    boolean isEnabled() {
        return this.mAdapter.isEnabled();
    }

    void setName(String name) {
        this.mAdapter.setName(name);
    }

    void setScanMode(int mode) {
        this.mAdapter.setScanMode(mode);
    }

    boolean setScanMode(int mode, int duration) {
        return this.mAdapter.setScanMode(mode, duration);
    }

    void startScanning(boolean force) {
        if (!this.mAdapter.isDiscovering()) {
            if (!force) {
                if (this.mLastScan + 300000 <= System.currentTimeMillis()) {
                    A2dpProfile a2dp = this.mProfileManager.getA2dpProfile();
                    if (a2dp != null && a2dp.isA2dpPlaying()) {
                        return;
                    }
                }
                return;
            }
            if (this.mAdapter.startDiscovery()) {
                this.mLastScan = System.currentTimeMillis();
            }
        }
    }

    void stopScanning() {
        if (this.mAdapter.isDiscovering()) {
            this.mAdapter.cancelDiscovery();
        }
    }

    public synchronized int getBluetoothState() {
        syncBluetoothState();
        return this.mState;
    }

    synchronized void setBluetoothStateInt(int state) {
        this.mState = state;
        if (state == 12 && this.mProfileManager != null) {
            this.mProfileManager.setBluetoothStateOn();
        }
    }

    boolean syncBluetoothState() {
        if (this.mAdapter.getState() == this.mState) {
            return false;
        }
        setBluetoothStateInt(this.mAdapter.getState());
        return true;
    }

    public void setBluetoothEnabled(boolean enabled) {
        if (enabled ? this.mAdapter.enable() : this.mAdapter.disable()) {
            int i;
            if (enabled) {
                i = 11;
            } else {
                i = 13;
            }
            setBluetoothStateInt(i);
            return;
        }
        syncBluetoothState();
    }
}
