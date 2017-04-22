package com.android.settings.bluetooth;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

final class CachedBluetoothDeviceManager {
    private final List<CachedBluetoothDevice> mCachedDevices = new ArrayList();
    private Context mContext;

    CachedBluetoothDeviceManager(Context context) {
        this.mContext = context;
    }

    public synchronized Collection<CachedBluetoothDevice> getCachedDevicesCopy() {
        return new ArrayList(this.mCachedDevices);
    }

    public static boolean onDeviceDisappeared(CachedBluetoothDevice cachedDevice) {
        cachedDevice.setVisible(false);
        if (cachedDevice.getBondState() == 10) {
            return true;
        }
        return false;
    }

    public void onDeviceNameUpdated(BluetoothDevice device) {
        CachedBluetoothDevice cachedDevice = findDevice(device);
        if (cachedDevice != null) {
            cachedDevice.refreshName();
        }
    }

    CachedBluetoothDevice findDevice(BluetoothDevice device) {
        for (CachedBluetoothDevice cachedDevice : this.mCachedDevices) {
            if (cachedDevice.getDevice().equals(device)) {
                return cachedDevice;
            }
        }
        return null;
    }

    CachedBluetoothDevice addDevice(LocalBluetoothAdapter adapter, LocalBluetoothProfileManager profileManager, BluetoothDevice device) {
        CachedBluetoothDevice newDevice = new CachedBluetoothDevice(this.mContext, adapter, profileManager, device);
        synchronized (this.mCachedDevices) {
            this.mCachedDevices.add(newDevice);
        }
        return newDevice;
    }

    public String getName(BluetoothDevice device) {
        CachedBluetoothDevice cachedDevice = findDevice(device);
        if (cachedDevice != null) {
            return cachedDevice.getName();
        }
        String name = device.getAliasName();
        return name == null ? device.getAddress() : name;
    }

    public synchronized void clearNonBondedDevices() {
        for (int i = this.mCachedDevices.size() - 1; i >= 0; i--) {
            if (((CachedBluetoothDevice) this.mCachedDevices.get(i)).getBondState() != 12) {
                this.mCachedDevices.remove(i);
            }
        }
    }

    public synchronized void onScanningStateChanged(boolean started) {
        if (started) {
            for (int i = this.mCachedDevices.size() - 1; i >= 0; i--) {
                ((CachedBluetoothDevice) this.mCachedDevices.get(i)).setVisible(false);
            }
        }
    }

    public synchronized void onBtClassChanged(BluetoothDevice device) {
        CachedBluetoothDevice cachedDevice = findDevice(device);
        if (cachedDevice != null) {
            cachedDevice.refreshBtClass();
        }
    }

    public synchronized void onUuidChanged(BluetoothDevice device) {
        CachedBluetoothDevice cachedDevice = findDevice(device);
        if (cachedDevice != null) {
            cachedDevice.onUuidChanged();
        }
    }

    public synchronized void onBluetoothStateChanged(int bluetoothState) {
        if (bluetoothState == 13) {
            for (int i = this.mCachedDevices.size() - 1; i >= 0; i--) {
                CachedBluetoothDevice cachedDevice = (CachedBluetoothDevice) this.mCachedDevices.get(i);
                if (cachedDevice.getBondState() != 12) {
                    cachedDevice.setVisible(false);
                    this.mCachedDevices.remove(i);
                } else {
                    cachedDevice.clearProfileConnectionState();
                }
            }
        }
    }
}
