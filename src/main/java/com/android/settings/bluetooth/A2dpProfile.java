package com.android.settings.bluetooth;

import android.bluetooth.BluetoothA2dp;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothProfile.ServiceListener;
import android.bluetooth.BluetoothUuid;
import android.content.Context;
import android.os.ParcelUuid;
import android.util.Log;
import com.android.settings.R;
import java.util.ArrayList;
import java.util.List;

final class A2dpProfile implements LocalBluetoothProfile {
    static final ParcelUuid[] SINK_UUIDS = new ParcelUuid[]{BluetoothUuid.AudioSink, BluetoothUuid.AdvAudioDist};
    private static boolean V = true;
    private final CachedBluetoothDeviceManager mDeviceManager;
    private boolean mIsProfileReady;
    private final LocalBluetoothAdapter mLocalAdapter;
    private final LocalBluetoothProfileManager mProfileManager;
    private BluetoothA2dp mService;

    private final class A2dpServiceListener implements ServiceListener {
        private A2dpServiceListener() {
        }

        public void onServiceConnected(int profile, BluetoothProfile proxy) {
            if (A2dpProfile.V) {
                Log.d("A2dpProfile", "Bluetooth service connected");
            }
            A2dpProfile.this.mService = (BluetoothA2dp) proxy;
            List<BluetoothDevice> deviceList = A2dpProfile.this.mService.getConnectedDevices();
            while (!deviceList.isEmpty()) {
                BluetoothDevice nextDevice = (BluetoothDevice) deviceList.remove(0);
                CachedBluetoothDevice device = A2dpProfile.this.mDeviceManager.findDevice(nextDevice);
                if (device == null) {
                    Log.w("A2dpProfile", "A2dpProfile found new device: " + nextDevice);
                    device = A2dpProfile.this.mDeviceManager.addDevice(A2dpProfile.this.mLocalAdapter, A2dpProfile.this.mProfileManager, nextDevice);
                }
                device.onProfileStateChanged(A2dpProfile.this, 2);
                device.refresh();
            }
            A2dpProfile.this.mIsProfileReady = true;
        }

        public void onServiceDisconnected(int profile) {
            if (A2dpProfile.V) {
                Log.d("A2dpProfile", "Bluetooth service disconnected");
            }
            A2dpProfile.this.mIsProfileReady = false;
        }
    }

    public boolean isProfileReady() {
        return this.mIsProfileReady;
    }

    A2dpProfile(Context context, LocalBluetoothAdapter adapter, CachedBluetoothDeviceManager deviceManager, LocalBluetoothProfileManager profileManager) {
        this.mLocalAdapter = adapter;
        this.mDeviceManager = deviceManager;
        this.mProfileManager = profileManager;
        this.mLocalAdapter.getProfileProxy(context, new A2dpServiceListener(), 2);
    }

    public boolean isConnectable() {
        return true;
    }

    public boolean isAutoConnectable() {
        return true;
    }

    public List<BluetoothDevice> getConnectedDevices() {
        if (this.mService == null) {
            return new ArrayList(0);
        }
        return this.mService.getDevicesMatchingConnectionStates(new int[]{2, 1, 3});
    }

    public boolean connect(BluetoothDevice device) {
        if (this.mService == null) {
            return false;
        }
        List<BluetoothDevice> sinks = getConnectedDevices();
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
        if (this.mService.getPriority(device) > 100) {
            this.mService.setPriority(device, 100);
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

    boolean isA2dpPlaying() {
        if (this.mService == null) {
            return false;
        }
        List<BluetoothDevice> sinks = this.mService.getConnectedDevices();
        return !sinks.isEmpty() && this.mService.isA2dpPlaying((BluetoothDevice) sinks.get(0));
    }

    public String toString() {
        return "A2DP";
    }

    public int getOrdinal() {
        return 1;
    }

    public int getNameResource(BluetoothDevice device) {
        return R.string.bluetooth_profile_a2dp;
    }

    public int getDrawableResource(BluetoothClass btClass) {
        return R.drawable.ic_bt_headphones_a2dp;
    }

    protected void finalize() {
        if (V) {
            Log.d("A2dpProfile", "finalize()");
        }
        if (this.mService != null) {
            try {
                BluetoothAdapter.getDefaultAdapter().closeProfileProxy(2, this.mService);
                this.mService = null;
            } catch (Throwable t) {
                Log.w("A2dpProfile", "Error cleaning up A2DP proxy", t);
            }
        }
    }
}
