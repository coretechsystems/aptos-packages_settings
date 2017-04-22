package com.android.settings.bluetooth;

import android.app.Notification.Builder;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.os.PowerManager;
import android.text.TextUtils;
import com.android.settings.R;

public final class BluetoothPairingRequest extends BroadcastReceiver {
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action.equals("android.bluetooth.device.action.PAIRING_REQUEST")) {
            BluetoothDevice device = (BluetoothDevice) intent.getParcelableExtra("android.bluetooth.device.extra.DEVICE");
            int type = intent.getIntExtra("android.bluetooth.device.extra.PAIRING_VARIANT", Integer.MIN_VALUE);
            Intent pairingIntent = new Intent();
            pairingIntent.setClass(context, BluetoothPairingDialog.class);
            pairingIntent.putExtra("android.bluetooth.device.extra.DEVICE", device);
            pairingIntent.putExtra("android.bluetooth.device.extra.PAIRING_VARIANT", type);
            if (type == 2 || type == 4 || type == 5) {
                pairingIntent.putExtra("android.bluetooth.device.extra.PAIRING_KEY", intent.getIntExtra("android.bluetooth.device.extra.PAIRING_KEY", Integer.MIN_VALUE));
            }
            pairingIntent.setAction("android.bluetooth.device.action.PAIRING_REQUEST");
            pairingIntent.setFlags(268435456);
            PowerManager powerManager = (PowerManager) context.getSystemService("power");
            String deviceAddress = device != null ? device.getAddress() : null;
            if (powerManager.isScreenOn() && LocalBluetoothPreferences.shouldShowDialogInForeground(context, deviceAddress)) {
                context.startActivity(pairingIntent);
                return;
            }
            Resources res = context.getResources();
            Builder builder = new Builder(context).setSmallIcon(17301632).setTicker(res.getString(R.string.bluetooth_notif_ticker));
            PendingIntent pending = PendingIntent.getActivity(context, 0, pairingIntent, 1073741824);
            String name = intent.getStringExtra("android.bluetooth.device.extra.NAME");
            if (TextUtils.isEmpty(name)) {
                if (device != null) {
                    name = device.getAliasName();
                } else {
                    name = context.getString(17039374);
                }
            }
            builder.setContentTitle(res.getString(R.string.bluetooth_notif_title)).setContentText(res.getString(R.string.bluetooth_notif_message, new Object[]{name})).setContentIntent(pending).setAutoCancel(true).setDefaults(1).setColor(res.getColor(17170520));
            ((NotificationManager) context.getSystemService("notification")).notify(17301632, builder.getNotification());
        } else if (action.equals("android.bluetooth.device.action.PAIRING_CANCEL")) {
            ((NotificationManager) context.getSystemService("notification")).cancel(17301632);
        } else if ("android.bluetooth.device.action.BOND_STATE_CHANGED".equals(action)) {
            int bondState = intent.getIntExtra("android.bluetooth.device.extra.BOND_STATE", Integer.MIN_VALUE);
            if (intent.getIntExtra("android.bluetooth.device.extra.PREVIOUS_BOND_STATE", Integer.MIN_VALUE) == 11 && bondState == 10) {
                ((NotificationManager) context.getSystemService("notification")).cancel(17301632);
            }
        }
    }
}
