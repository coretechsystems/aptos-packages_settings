package com.android.settings.bluetooth;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHeadset;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothProfile.ServiceListener;
import android.bluetooth.BluetoothUuid;
import android.content.Context;
import android.os.ParcelUuid;
import android.util.Log;
import com.android.settings.R;
import java.util.List;

final class HeadsetProfile implements LocalBluetoothProfile {
    static final ParcelUuid[] UUIDS = new ParcelUuid[]{BluetoothUuid.HSP, BluetoothUuid.Handsfree};
    private static boolean V = true;
    private final CachedBluetoothDeviceManager mDeviceManager;
    private boolean mIsProfileReady;
    private final LocalBluetoothAdapter mLocalAdapter;
    private final LocalBluetoothProfileManager mProfileManager;
    private BluetoothHeadset mService;

    private final class HeadsetServiceListener implements ServiceListener {
        private HeadsetServiceListener() {
        }

        public void onServiceConnected(int profile, BluetoothProfile proxy) {
            if (HeadsetProfile.V) {
                Log.d("HeadsetProfile", "Bluetooth service connected");
            }
            HeadsetProfile.this.mService = (BluetoothHeadset) proxy;
            List<BluetoothDevice> deviceList = HeadsetProfile.this.mService.getConnectedDevices();
            while (!deviceList.isEmpty()) {
                BluetoothDevice nextDevice = (BluetoothDevice) deviceList.remove(0);
                CachedBluetoothDevice device = HeadsetProfile.this.mDeviceManager.findDevice(nextDevice);
                if (device == null) {
                    Log.w("HeadsetProfile", "HeadsetProfile found new device: " + nextDevice);
                    device = HeadsetProfile.this.mDeviceManager.addDevice(HeadsetProfile.this.mLocalAdapter, HeadsetProfile.this.mProfileManager, nextDevice);
                }
                device.onProfileStateChanged(HeadsetProfile.this, 2);
                device.refresh();
            }
            HeadsetProfile.this.mProfileManager.callServiceConnectedListeners();
            HeadsetProfile.this.mIsProfileReady = true;
        }

        public void onServiceDisconnected(int profile) {
            if (HeadsetProfile.V) {
                Log.d("HeadsetProfile", "Bluetooth service disconnected");
            }
            HeadsetProfile.this.mProfileManager.callServiceDisconnectedListeners();
            HeadsetProfile.this.mIsProfileReady = false;
        }
    }

    public boolean isProfileReady() {
        return this.mIsProfileReady;
    }

    HeadsetProfile(Context context, LocalBluetoothAdapter adapter, CachedBluetoothDeviceManager deviceManager, LocalBluetoothProfileManager profileManager) {
        this.mLocalAdapter = adapter;
        this.mDeviceManager = deviceManager;
        this.mProfileManager = profileManager;
        this.mLocalAdapter.getProfileProxy(context, new HeadsetServiceListener(), 1);
    }

    public boolean isConnectable() {
        return true;
    }

    public boolean isAutoConnectable() {
        return true;
    }

    public boolean connect(BluetoothDevice device) {
        if (this.mService == null) {
            return false;
        }
        List<BluetoothDevice> sinks = this.mService.getConnectedDevices();
        if (sinks != null) {
            for (BluetoothDevice sink : sinks) {
                Log.d("HeadsetProfile", "Not disconnecting device = " + sink);
            }
        }
        return this.mService.connect(device);
    }

    public boolean disconnect(BluetoothDevice device) {
        if (this.mService == null) {
            return false;
        }
        List<BluetoothDevice> deviceList = this.mService.getConnectedDevices();
        if (deviceList.isEmpty()) {
            return false;
        }
        for (BluetoothDevice dev : deviceList) {
            if (dev.equals(device)) {
                if (V) {
                    Log.d("HeadsetProfile", "Downgrade priority as useris disconnecting the headset");
                }
                if (this.mService.getPriority(device) > 100) {
                    this.mService.setPriority(device, 100);
                }
                return this.mService.disconnect(device);
            }
        }
        return false;
    }

    public int getConnectionStatus(BluetoothDevice device) {
        if (this.mService == null) {
            return 0;
        }
        List<BluetoothDevice> deviceList = this.mService.getConnectedDevices();
        if (deviceList.isEmpty()) {
            return 0;
        }
        for (BluetoothDevice dev : deviceList) {
            if (dev.equals(device)) {
                return this.mService.getConnectionState(device);
            }
        }
        return 0;
    }

    public boolean isPreferred(BluetoothDevice device) {
        if (this.mService != null && this.mService.getPriority(device) > 0) {
            return true;
        }
        return false;
    }

    public int getPreferred(BluetoothDevice device) {
        if (this.mService == null) {
            return 0;
        }
        return this.mService.getPriority(device);
    }

    public void setPreferred(BluetoothDevice device, boolean preferred) {
        if (this.mService != null) {
            if (!preferred) {
                this.mService.setPriority(device, 0);
            } else if (this.mService.getPriority(device) < 100) {
                this.mService.setPriority(device, 100);
            }
        }
    }

    public String toString() {
        return "HEADSET";
    }

    public int getOrdinal() {
        return 0;
    }

    public int getNameResource(BluetoothDevice device) {
        return R.string.bluetooth_profile_headset;
    }

    public int getDrawableResource(BluetoothClass btClass) {
        return R.drawable.ic_bt_headset_hfp;
    }

    protected void finalize() {
        if (V) {
            Log.d("HeadsetProfile", "finalize()");
        }
        if (this.mService != null) {
            try {
                BluetoothAdapter.getDefaultAdapter().closeProfileProxy(1, this.mService);
                this.mService = null;
            } catch (Throwable t) {
                Log.w("HeadsetProfile", "Error cleaning up HID proxy", t);
            }
        }
    }
}
