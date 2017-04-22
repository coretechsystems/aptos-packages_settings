package com.android.settings.bluetooth;

import android.app.Notification;
import android.app.Notification.Builder;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;
import android.util.Log;
import com.android.settings.R;

public final class BluetoothPermissionRequest extends BroadcastReceiver {
    Context mContext;
    BluetoothDevice mDevice;
    int mRequestType;
    String mReturnClass = null;
    String mReturnPackage = null;

    public void onReceive(Context context, Intent intent) {
        this.mContext = context;
        String action = intent.getAction();
        if (action.equals("android.bluetooth.device.action.CONNECTION_ACCESS_REQUEST")) {
            this.mDevice = (BluetoothDevice) intent.getParcelableExtra("android.bluetooth.device.extra.DEVICE");
            this.mRequestType = intent.getIntExtra("android.bluetooth.device.extra.ACCESS_REQUEST_TYPE", 1);
            this.mReturnPackage = intent.getStringExtra("android.bluetooth.device.extra.PACKAGE_NAME");
            this.mReturnClass = intent.getStringExtra("android.bluetooth.device.extra.CLASS_NAME");
            if (!checkUserChoice()) {
                Intent connectionAccessIntent = new Intent(action);
                connectionAccessIntent.setClass(context, BluetoothPermissionActivity.class);
                connectionAccessIntent.setFlags(402653184);
                connectionAccessIntent.setType(Integer.toString(this.mRequestType));
                connectionAccessIntent.putExtra("android.bluetooth.device.extra.ACCESS_REQUEST_TYPE", this.mRequestType);
                connectionAccessIntent.putExtra("android.bluetooth.device.extra.DEVICE", this.mDevice);
                connectionAccessIntent.putExtra("android.bluetooth.device.extra.PACKAGE_NAME", this.mReturnPackage);
                connectionAccessIntent.putExtra("android.bluetooth.device.extra.CLASS_NAME", this.mReturnClass);
                String deviceAddress = this.mDevice != null ? this.mDevice.getAddress() : null;
                if (((PowerManager) context.getSystemService("power")).isScreenOn() && LocalBluetoothPreferences.shouldShowDialogInForeground(context, deviceAddress)) {
                    context.startActivity(connectionAccessIntent);
                    return;
                }
                String title;
                String message;
                Intent deleteIntent = new Intent("android.bluetooth.device.action.CONNECTION_ACCESS_REPLY");
                deleteIntent.putExtra("android.bluetooth.device.extra.DEVICE", this.mDevice);
                deleteIntent.putExtra("android.bluetooth.device.extra.CONNECTION_ACCESS_RESULT", 2);
                deleteIntent.putExtra("android.bluetooth.device.extra.ACCESS_REQUEST_TYPE", this.mRequestType);
                String deviceName = this.mDevice != null ? this.mDevice.getAliasName() : null;
                switch (this.mRequestType) {
                    case 2:
                        title = context.getString(R.string.bluetooth_phonebook_request);
                        message = context.getString(R.string.bluetooth_pb_acceptance_dialog_text, new Object[]{deviceName, deviceName});
                        break;
                    case 3:
                        title = context.getString(R.string.bluetooth_map_request);
                        message = context.getString(R.string.bluetooth_map_acceptance_dialog_text, new Object[]{deviceName, deviceName});
                        break;
                    default:
                        title = context.getString(R.string.bluetooth_connection_permission_request);
                        message = context.getString(R.string.bluetooth_connection_dialog_text, new Object[]{deviceName, deviceName});
                        break;
                }
                Notification notification = new Builder(context).setContentTitle(title).setTicker(message).setContentText(message).setSmallIcon(17301632).setAutoCancel(true).setPriority(2).setOnlyAlertOnce(false).setDefaults(-1).setContentIntent(PendingIntent.getActivity(context, 0, connectionAccessIntent, 0)).setDeleteIntent(PendingIntent.getBroadcast(context, 0, deleteIntent, 0)).setColor(context.getResources().getColor(17170520)).build();
                notification.flags |= 32;
                ((NotificationManager) context.getSystemService("notification")).notify(getNotificationTag(this.mRequestType), 17301632, notification);
            }
        } else if (action.equals("android.bluetooth.device.action.CONNECTION_ACCESS_CANCEL")) {
            NotificationManager manager = (NotificationManager) context.getSystemService("notification");
            this.mRequestType = intent.getIntExtra("android.bluetooth.device.extra.ACCESS_REQUEST_TYPE", 2);
            manager.cancel(getNotificationTag(this.mRequestType), 17301632);
        }
    }

    private String getNotificationTag(int requestType) {
        if (requestType == 2) {
            return "Phonebook Access";
        }
        if (this.mRequestType == 3) {
            return "Message Access";
        }
        return null;
    }

    private boolean checkUserChoice() {
        boolean processed = false;
        if (this.mRequestType != 2 && this.mRequestType != 3) {
            return 0;
        }
        LocalBluetoothManager bluetoothManager = LocalBluetoothManager.getInstance(this.mContext);
        CachedBluetoothDeviceManager cachedDeviceManager = bluetoothManager.getCachedDeviceManager();
        CachedBluetoothDevice cachedDevice = cachedDeviceManager.findDevice(this.mDevice);
        if (cachedDevice == null) {
            cachedDevice = cachedDeviceManager.addDevice(bluetoothManager.getBluetoothAdapter(), bluetoothManager.getProfileManager(), this.mDevice);
        }
        String intentName = "android.bluetooth.device.action.CONNECTION_ACCESS_REPLY";
        if (this.mRequestType == 2) {
            int phonebookPermission = cachedDevice.getPhonebookPermissionChoice();
            if (phonebookPermission != 0) {
                if (phonebookPermission == 1) {
                    sendReplyIntentToReceiver(true);
                    processed = true;
                } else if (phonebookPermission == 2) {
                    sendReplyIntentToReceiver(false);
                    processed = true;
                } else {
                    Log.e("BluetoothPermissionRequest", "Bad phonebookPermission: " + phonebookPermission);
                }
            }
        } else if (this.mRequestType == 3) {
            int messagePermission = cachedDevice.getMessagePermissionChoice();
            if (messagePermission != 0) {
                if (messagePermission == 1) {
                    sendReplyIntentToReceiver(true);
                    processed = true;
                } else if (messagePermission == 2) {
                    sendReplyIntentToReceiver(false);
                    processed = true;
                } else {
                    Log.e("BluetoothPermissionRequest", "Bad messagePermission: " + messagePermission);
                }
            }
        }
        return processed;
    }

    private void sendReplyIntentToReceiver(boolean allowed) {
        Intent intent = new Intent("android.bluetooth.device.action.CONNECTION_ACCESS_REPLY");
        if (!(this.mReturnPackage == null || this.mReturnClass == null)) {
            intent.setClassName(this.mReturnPackage, this.mReturnClass);
        }
        intent.putExtra("android.bluetooth.device.extra.CONNECTION_ACCESS_RESULT", allowed ? 1 : 2);
        intent.putExtra("android.bluetooth.device.extra.DEVICE", this.mDevice);
        intent.putExtra("android.bluetooth.device.extra.ACCESS_REQUEST_TYPE", this.mRequestType);
        this.mContext.sendBroadcast(intent, "android.permission.BLUETOOTH_ADMIN");
    }
}
