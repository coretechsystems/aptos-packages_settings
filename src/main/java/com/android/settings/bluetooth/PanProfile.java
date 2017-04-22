package com.android.settings.bluetooth;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothPan;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothProfile.ServiceListener;
import android.content.Context;
import android.util.Log;
import com.android.settings.R;
import java.util.HashMap;
import java.util.List;

final class PanProfile implements LocalBluetoothProfile {
    private static boolean V = true;
    private final HashMap<BluetoothDevice, Integer> mDeviceRoleMap = new HashMap();
    private boolean mIsProfileReady;
    private BluetoothPan mService;

    private final class PanServiceListener implements ServiceListener {
        private PanServiceListener() {
        }

        public void onServiceConnected(int profile, BluetoothProfile proxy) {
            if (PanProfile.V) {
                Log.d("PanProfile", "Bluetooth service connected");
            }
            PanProfile.this.mService = (BluetoothPan) proxy;
            PanProfile.this.mIsProfileReady = true;
        }

        public void onServiceDisconnected(int profile) {
            if (PanProfile.V) {
                Log.d("PanProfile", "Bluetooth service disconnected");
            }
            PanProfile.this.mIsProfileReady = false;
        }
    }

    public boolean isProfileReady() {
        return this.mIsProfileReady;
    }

    PanProfile(Context context) {
        BluetoothAdapter.getDefaultAdapter().getProfileProxy(context, new PanServiceListener(), 5);
    }

    public boolean isConnectable() {
        return true;
    }

    public boolean isAutoConnectable() {
        return false;
    }

    public boolean connect(BluetoothDevice device) {
        if (this.mService == null) {
            return false;
        }
        List<BluetoothDevice> sinks = this.mService.getConnectedDevices();
        if (sinks != null) {
            for (BluetoothDevice sink : sinks) {
                this.mService.disconnect(sink);
            }
        }
        return this.mService.connect(device);
    }

    public boolean disconnect(BluetoothDevice device) {
        if (this.mService == null) {
            return false;
        }
        return this.mService.disconnect(device);
    }

    public int getConnectionStatus(BluetoothDevice device) {
        if (this.mService == null) {
            return 0;
        }
        return this.mService.getConnectionState(device);
    }

    public boolean isPreferred(BluetoothDevice device) {
        return getConnectionStatus(device) == 2;
    }

    public int getPreferred(BluetoothDevice device) {
        return -1;
    }

    public void setPreferred(BluetoothDevice device, boolean preferred) {
    }

    public String toString() {
        return "PAN";
    }

    public int getOrdinal() {
        return 4;
    }

    public int getNameResource(BluetoothDevice device) {
        if (isLocalRoleNap(device)) {
            return R.string.bluetooth_profile_pan_nap;
        }
        return R.string.bluetooth_profile_pan;
    }

    public int getDrawableResource(BluetoothClass btClass) {
        return R.drawable.ic_bt_network_pan;
    }

    void setLocalRole(BluetoothDevice device, int role) {
        this.mDeviceRoleMap.put(device, Integer.valueOf(role));
    }

    boolean isLocalRoleNap(BluetoothDevice device) {
        if (!this.mDeviceRoleMap.containsKey(device)) {
            return false;
        }
        if (((Integer) this.mDeviceRoleMap.get(device)).intValue() == 1) {
            return true;
        }
        return false;
    }

    protected void finalize() {
        if (V) {
            Log.d("PanProfile", "finalize()");
        }
        if (this.mService != null) {
            try {
                BluetoothAdapter.getDefaultAdapter().closeProfileProxy(5, this.mService);
                this.mService = null;
            } catch (Throwable t) {
                Log.w("PanProfile", "Error cleaning up PAN proxy", t);
            }
        }
    }
}
