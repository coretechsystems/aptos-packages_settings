package com.android.settings.bluetooth;

import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;
import com.android.settings.R;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

final class BluetoothEventManager {
    private final IntentFilter mAdapterIntentFilter;
    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            BluetoothDevice device = (BluetoothDevice) intent.getParcelableExtra("android.bluetooth.device.extra.DEVICE");
            Handler handler = (Handler) BluetoothEventManager.this.mHandlerMap.get(intent.getAction());
            if (handler != null) {
                handler.onReceive(context, intent, device);
            }
        }
    };
    private final Collection<BluetoothCallback> mCallbacks = new ArrayList();
    private Context mContext;
    private final CachedBluetoothDeviceManager mDeviceManager;
    private final Map<String, Handler> mHandlerMap;
    private final LocalBluetoothAdapter mLocalAdapter;
    private final IntentFilter mProfileIntentFilter;
    private LocalBluetoothProfileManager mProfileManager;

    interface Handler {
        void onReceive(Context context, Intent intent, BluetoothDevice bluetoothDevice);
    }

    private class AdapterStateChangedHandler implements Handler {
        private AdapterStateChangedHandler() {
        }

        public void onReceive(Context context, Intent intent, BluetoothDevice device) {
            int state = intent.getIntExtra("android.bluetooth.adapter.extra.STATE", Integer.MIN_VALUE);
            BluetoothEventManager.this.mLocalAdapter.setBluetoothStateInt(state);
            synchronized (BluetoothEventManager.this.mCallbacks) {
                for (BluetoothCallback callback : BluetoothEventManager.this.mCallbacks) {
                    callback.onBluetoothStateChanged(state);
                }
            }
            BluetoothEventManager.this.mDeviceManager.onBluetoothStateChanged(state);
        }
    }

    private class BondStateChangedHandler implements Handler {
        private BondStateChangedHandler() {
        }

        public void onReceive(Context context, Intent intent, BluetoothDevice device) {
            if (device == null) {
                Log.e("BluetoothEventManager", "ACTION_BOND_STATE_CHANGED with no EXTRA_DEVICE");
                return;
            }
            int bondState = intent.getIntExtra("android.bluetooth.device.extra.BOND_STATE", Integer.MIN_VALUE);
            CachedBluetoothDevice cachedDevice = BluetoothEventManager.this.mDeviceManager.findDevice(device);
            if (cachedDevice == null) {
                Log.w("BluetoothEventManager", "CachedBluetoothDevice for device " + device + " not found, calling readPairedDevices().");
                if (BluetoothEventManager.this.readPairedDevices()) {
                    cachedDevice = BluetoothEventManager.this.mDeviceManager.findDevice(device);
                    if (cachedDevice == null) {
                        Log.e("BluetoothEventManager", "Got bonding state changed for " + device + ", but device not added in cache.");
                        return;
                    }
                }
                Log.e("BluetoothEventManager", "Got bonding state changed for " + device + ", but we have no record of that device.");
                return;
            }
            synchronized (BluetoothEventManager.this.mCallbacks) {
                for (BluetoothCallback callback : BluetoothEventManager.this.mCallbacks) {
                    callback.onDeviceBondStateChanged(cachedDevice, bondState);
                }
            }
            cachedDevice.onBondingStateChanged(bondState);
            if (bondState == 10) {
                if (device.isBluetoothDock()) {
                    LocalBluetoothPreferences.removeDockAutoConnectSetting(context, device.getAddress());
                    if (!device.getAddress().equals(BluetoothEventManager.getDockedDeviceAddress(context))) {
                        cachedDevice.setVisible(false);
                    }
                }
                showUnbondMessage(context, cachedDevice.getName(), intent.getIntExtra("android.bluetooth.device.extra.REASON", Integer.MIN_VALUE));
            }
        }

        private void showUnbondMessage(Context context, String name, int reason) {
            int errorMsg;
            switch (reason) {
                case 1:
                    errorMsg = R.string.bluetooth_pairing_pin_error_message;
                    break;
                case 2:
                    errorMsg = R.string.bluetooth_pairing_rejected_error_message;
                    break;
                case 4:
                    errorMsg = R.string.bluetooth_pairing_device_down_error_message;
                    break;
                case 5:
                case 6:
                case 7:
                case 8:
                    errorMsg = R.string.bluetooth_pairing_error_message;
                    break;
                default:
                    Log.w("BluetoothEventManager", "showUnbondMessage: Not displaying any message for reason: " + reason);
                    return;
            }
            Utils.showError(context, name, errorMsg);
        }
    }

    private class ClassChangedHandler implements Handler {
        private ClassChangedHandler() {
        }

        public void onReceive(Context context, Intent intent, BluetoothDevice device) {
            BluetoothEventManager.this.mDeviceManager.onBtClassChanged(device);
        }
    }

    private class DeviceDisappearedHandler implements Handler {
        private DeviceDisappearedHandler() {
        }

        public void onReceive(Context context, Intent intent, BluetoothDevice device) {
            CachedBluetoothDevice cachedDevice = BluetoothEventManager.this.mDeviceManager.findDevice(device);
            if (cachedDevice == null) {
                Log.w("BluetoothEventManager", "received ACTION_DISAPPEARED for an unknown device: " + device);
            } else if (CachedBluetoothDeviceManager.onDeviceDisappeared(cachedDevice)) {
                synchronized (BluetoothEventManager.this.mCallbacks) {
                    for (BluetoothCallback callback : BluetoothEventManager.this.mCallbacks) {
                        callback.onDeviceDeleted(cachedDevice);
                    }
                }
            }
        }
    }

    private class DeviceFoundHandler implements Handler {
        private DeviceFoundHandler() {
        }

        public void onReceive(Context context, Intent intent, BluetoothDevice device) {
            short rssi = intent.getShortExtra("android.bluetooth.device.extra.RSSI", Short.MIN_VALUE);
            BluetoothClass btClass = (BluetoothClass) intent.getParcelableExtra("android.bluetooth.device.extra.CLASS");
            String name = intent.getStringExtra("android.bluetooth.device.extra.NAME");
            CachedBluetoothDevice cachedDevice = BluetoothEventManager.this.mDeviceManager.findDevice(device);
            if (cachedDevice == null) {
                cachedDevice = BluetoothEventManager.this.mDeviceManager.addDevice(BluetoothEventManager.this.mLocalAdapter, BluetoothEventManager.this.mProfileManager, device);
                Log.d("BluetoothEventManager", "DeviceFoundHandler created new CachedBluetoothDevice: " + cachedDevice);
                BluetoothEventManager.this.dispatchDeviceAdded(cachedDevice);
            }
            cachedDevice.setRssi(rssi);
            cachedDevice.setBtClass(btClass);
            cachedDevice.setNewName(name);
            cachedDevice.setVisible(true);
        }
    }

    private class DockEventHandler implements Handler {
        private DockEventHandler() {
        }

        public void onReceive(Context context, Intent intent, BluetoothDevice device) {
            if (intent.getIntExtra("android.intent.extra.DOCK_STATE", 1) == 0 && device != null && device.getBondState() == 10) {
                CachedBluetoothDevice cachedDevice = BluetoothEventManager.this.mDeviceManager.findDevice(device);
                if (cachedDevice != null) {
                    cachedDevice.setVisible(false);
                }
            }
        }
    }

    private class NameChangedHandler implements Handler {
        private NameChangedHandler() {
        }

        public void onReceive(Context context, Intent intent, BluetoothDevice device) {
            BluetoothEventManager.this.mDeviceManager.onDeviceNameUpdated(device);
        }
    }

    private class PairingCancelHandler implements Handler {
        private PairingCancelHandler() {
        }

        public void onReceive(Context context, Intent intent, BluetoothDevice device) {
            if (device == null) {
                Log.e("BluetoothEventManager", "ACTION_PAIRING_CANCEL with no EXTRA_DEVICE");
            } else {
                Utils.showError(context, BluetoothEventManager.this.mDeviceManager.findDevice(device).getName(), R.string.bluetooth_pairing_error_message);
            }
        }
    }

    private class ScanningStateChangedHandler implements Handler {
        private final boolean mStarted;

        ScanningStateChangedHandler(boolean started) {
            this.mStarted = started;
        }

        public void onReceive(Context context, Intent intent, BluetoothDevice device) {
            synchronized (BluetoothEventManager.this.mCallbacks) {
                for (BluetoothCallback callback : BluetoothEventManager.this.mCallbacks) {
                    callback.onScanningStateChanged(this.mStarted);
                }
            }
            BluetoothEventManager.this.mDeviceManager.onScanningStateChanged(this.mStarted);
            LocalBluetoothPreferences.persistDiscoveringTimestamp(context);
        }
    }

    private class UuidChangedHandler implements Handler {
        private UuidChangedHandler() {
        }

        public void onReceive(Context context, Intent intent, BluetoothDevice device) {
            BluetoothEventManager.this.mDeviceManager.onUuidChanged(device);
        }
    }

    void addHandler(String action, Handler handler) {
        this.mHandlerMap.put(action, handler);
        this.mAdapterIntentFilter.addAction(action);
    }

    void addProfileHandler(String action, Handler handler) {
        this.mHandlerMap.put(action, handler);
        this.mProfileIntentFilter.addAction(action);
    }

    void setProfileManager(LocalBluetoothProfileManager manager) {
        this.mProfileManager = manager;
    }

    BluetoothEventManager(LocalBluetoothAdapter adapter, CachedBluetoothDeviceManager deviceManager, Context context) {
        this.mLocalAdapter = adapter;
        this.mDeviceManager = deviceManager;
        this.mAdapterIntentFilter = new IntentFilter();
        this.mProfileIntentFilter = new IntentFilter();
        this.mHandlerMap = new HashMap();
        this.mContext = context;
        addHandler("android.bluetooth.adapter.action.STATE_CHANGED", new AdapterStateChangedHandler());
        addHandler("android.bluetooth.adapter.action.DISCOVERY_STARTED", new ScanningStateChangedHandler(true));
        addHandler("android.bluetooth.adapter.action.DISCOVERY_FINISHED", new ScanningStateChangedHandler(false));
        addHandler("android.bluetooth.device.action.FOUND", new DeviceFoundHandler());
        addHandler("android.bluetooth.device.action.DISAPPEARED", new DeviceDisappearedHandler());
        addHandler("android.bluetooth.device.action.NAME_CHANGED", new NameChangedHandler());
        addHandler("android.bluetooth.device.action.BOND_STATE_CHANGED", new BondStateChangedHandler());
        addHandler("android.bluetooth.device.action.PAIRING_CANCEL", new PairingCancelHandler());
        addHandler("android.bluetooth.device.action.CLASS_CHANGED", new ClassChangedHandler());
        addHandler("android.bluetooth.device.action.UUID", new UuidChangedHandler());
        addHandler("android.intent.action.DOCK_EVENT", new DockEventHandler());
        this.mContext.registerReceiver(this.mBroadcastReceiver, this.mAdapterIntentFilter);
    }

    void registerProfileIntentReceiver() {
        this.mContext.registerReceiver(this.mBroadcastReceiver, this.mProfileIntentFilter);
    }

    void registerCallback(BluetoothCallback callback) {
        synchronized (this.mCallbacks) {
            this.mCallbacks.add(callback);
        }
    }

    void unregisterCallback(BluetoothCallback callback) {
        synchronized (this.mCallbacks) {
            this.mCallbacks.remove(callback);
        }
    }

    private static String getDockedDeviceAddress(Context context) {
        Intent i = context.registerReceiver(null, new IntentFilter("android.intent.action.DOCK_EVENT"));
        if (i == null || i.getIntExtra("android.intent.extra.DOCK_STATE", 0) == 0) {
            return null;
        }
        BluetoothDevice device = (BluetoothDevice) i.getParcelableExtra("android.bluetooth.device.extra.DEVICE");
        if (device != null) {
            return device.getAddress();
        }
        return null;
    }

    private void dispatchDeviceAdded(CachedBluetoothDevice cachedDevice) {
        synchronized (this.mCallbacks) {
            for (BluetoothCallback callback : this.mCallbacks) {
                callback.onDeviceAdded(cachedDevice);
            }
        }
    }

    boolean readPairedDevices() {
        Set<BluetoothDevice> bondedDevices = this.mLocalAdapter.getBondedDevices();
        if (bondedDevices == null) {
            return false;
        }
        boolean deviceAdded = false;
        for (BluetoothDevice device : bondedDevices) {
            if (this.mDeviceManager.findDevice(device) == null) {
                dispatchDeviceAdded(this.mDeviceManager.addDevice(this.mLocalAdapter, this.mProfileManager, device));
                deviceAdded = true;
            }
        }
        return deviceAdded;
    }
}
