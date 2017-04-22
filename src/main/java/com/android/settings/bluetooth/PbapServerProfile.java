package com.android.settings.bluetooth;

import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothPbap;
import android.bluetooth.BluetoothPbap.ServiceListener;
import android.content.Context;
import android.util.Log;
import com.android.settings.R;

final class PbapServerProfile implements LocalBluetoothProfile {
    private static boolean V = true;
    private boolean mIsProfileReady;
    private BluetoothPbap mService;

    private final class PbapServiceListener implements ServiceListener {
        private PbapServiceListener() {
        }

        public void onServiceConnected(BluetoothPbap proxy) {
            if (PbapServerProfile.V) {
                Log.d("PbapServerProfile", "Bluetooth service connected");
            }
            PbapServerProfile.this.mService = proxy;
            PbapServerProfile.this.mIsProfileReady = true;
        }

        public void onServiceDisconnected() {
            if (PbapServerProfile.V) {
                Log.d("PbapServerProfile", "Bluetooth service disconnected");
            }
            PbapServerProfile.this.mIsProfileReady = false;
        }
    }

    public boolean isProfileReady() {
        return this.mIsProfileReady;
    }

    PbapServerProfile(Context context) {
        BluetoothPbap pbap = new BluetoothPbap(context, new PbapServiceListener());
    }

    public boolean isConnectable() {
        return true;
    }

    public boolean isAutoConnectable() {
        return false;
    }

    public boolean connect(BluetoothDevice device) {
        return false;
    }

    public boolean disconnect(BluetoothDevice device) {
        if (this.mService == null) {
            return false;
        }
        return this.mService.disconnect();
    }

    public int getConnectionStatus(BluetoothDevice device) {
        if (this.mService != null && this.mService.isConnected(device)) {
            return 2;
        }
        return 0;
    }

    public boolean isPreferred(BluetoothDevice device) {
        return false;
    }

    public int getPreferred(BluetoothDevice device) {
        return -1;
    }

    public void setPreferred(BluetoothDevice device, boolean preferred) {
    }

    public String toString() {
        return "PBAP Server";
    }

    public int getOrdinal() {
        return 6;
    }

    public int getNameResource(BluetoothDevice device) {
        return R.string.bluetooth_profile_pbap;
    }

    public int getDrawableResource(BluetoothClass btClass) {
        return R.drawable.ic_bt_cellphone;
    }

    protected void finalize() {
        if (V) {
            Log.d("PbapServerProfile", "finalize()");
        }
        if (this.mService != null) {
            try {
                this.mService.close();
                this.mService = null;
            } catch (Throwable t) {
                Log.w("PbapServerProfile", "Error cleaning up PBAP proxy", t);
            }
        }
    }
}
