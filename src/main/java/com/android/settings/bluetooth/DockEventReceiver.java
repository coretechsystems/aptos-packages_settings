package com.android.settings.bluetooth;

import android.app.Service;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.util.Log;

public final class DockEventReceiver extends BroadcastReceiver {
    private static WakeLock sStartingService;
    private static final Object sStartingServiceSync = new Object();

    public void onReceive(Context context, Intent intent) {
        if (intent != null) {
            int state = intent.getIntExtra("android.intent.extra.DOCK_STATE", intent.getIntExtra("android.bluetooth.adapter.extra.STATE", -1234));
            BluetoothDevice device = (BluetoothDevice) intent.getParcelableExtra("android.bluetooth.device.extra.DEVICE");
            Intent i;
            if ("android.intent.action.DOCK_EVENT".equals(intent.getAction()) || "com.android.settings.bluetooth.action.DOCK_SHOW_UI".endsWith(intent.getAction())) {
                if (device == null) {
                    if (!"com.android.settings.bluetooth.action.DOCK_SHOW_UI".endsWith(intent.getAction())) {
                        if (!(state == 0 || state == 3)) {
                            return;
                        }
                    }
                    return;
                }
                switch (state) {
                    case 0:
                    case 1:
                    case 2:
                    case 3:
                    case 4:
                        i = new Intent(intent);
                        i.setClass(context, DockService.class);
                        beginStartingService(context, i);
                        return;
                    default:
                        Log.e("DockEventReceiver", "Unknown state: " + state);
                        return;
                }
            } else if ("android.bluetooth.headset.profile.action.CONNECTION_STATE_CHANGED".equals(intent.getAction()) || "android.bluetooth.a2dp.profile.action.CONNECTION_STATE_CHANGED".equals(intent.getAction())) {
                int newState = intent.getIntExtra("android.bluetooth.profile.extra.STATE", 2);
                int oldState = intent.getIntExtra("android.bluetooth.profile.extra.PREVIOUS_STATE", 0);
                if (device != null && newState == 0 && oldState != 3) {
                    i = new Intent(intent);
                    i.setClass(context, DockService.class);
                    beginStartingService(context, i);
                }
            } else if ("android.bluetooth.adapter.action.STATE_CHANGED".equals(intent.getAction()) && intent.getIntExtra("android.bluetooth.adapter.extra.STATE", Integer.MIN_VALUE) != 11) {
                i = new Intent(intent);
                i.setClass(context, DockService.class);
                beginStartingService(context, i);
            }
        }
    }

    private static void beginStartingService(Context context, Intent intent) {
        synchronized (sStartingServiceSync) {
            if (sStartingService == null) {
                sStartingService = ((PowerManager) context.getSystemService("power")).newWakeLock(1, "StartingDockService");
            }
            sStartingService.acquire();
            if (context.startService(intent) == null) {
                Log.e("DockEventReceiver", "Can't start DockService");
            }
        }
    }

    public static void finishStartingService(Service service, int startId) {
        synchronized (sStartingServiceSync) {
            if (sStartingService != null && service.stopSelfResult(startId)) {
                Log.d("DockEventReceiver", "finishStartingService: stopping service");
                sStartingService.release();
            }
        }
    }
}
