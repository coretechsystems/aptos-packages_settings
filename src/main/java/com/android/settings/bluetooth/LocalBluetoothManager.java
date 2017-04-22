package com.android.settings.bluetooth;

import android.content.Context;
import android.util.Log;

public final class LocalBluetoothManager {
    private static LocalBluetoothManager sInstance;
    private final CachedBluetoothDeviceManager mCachedDeviceManager;
    private final Context mContext;
    private BluetoothDiscoverableEnabler mDiscoverableEnabler;
    private final BluetoothEventManager mEventManager;
    private Context mForegroundActivity;
    private final LocalBluetoothAdapter mLocalAdapter;
    private final LocalBluetoothProfileManager mProfileManager;

    public static synchronized LocalBluetoothManager getInstance(Context context) {
        LocalBluetoothManager localBluetoothManager;
        synchronized (LocalBluetoothManager.class) {
            if (sInstance == null) {
                LocalBluetoothAdapter adapter = LocalBluetoothAdapter.getInstance();
                if (adapter == null) {
                    localBluetoothManager = null;
                } else {
                    sInstance = new LocalBluetoothManager(adapter, context.getApplicationContext());
                }
            }
            localBluetoothManager = sInstance;
        }
        return localBluetoothManager;
    }

    public BluetoothDiscoverableEnabler getDiscoverableEnabler() {
        return this.mDiscoverableEnabler;
    }

    private LocalBluetoothManager(LocalBluetoothAdapter adapter, Context context) {
        this.mContext = context;
        this.mLocalAdapter = adapter;
        this.mCachedDeviceManager = new CachedBluetoothDeviceManager(context);
        this.mEventManager = new BluetoothEventManager(this.mLocalAdapter, this.mCachedDeviceManager, context);
        this.mProfileManager = new LocalBluetoothProfileManager(context, this.mLocalAdapter, this.mCachedDeviceManager, this.mEventManager);
    }

    public LocalBluetoothAdapter getBluetoothAdapter() {
        return this.mLocalAdapter;
    }

    public Context getForegroundActivity() {
        return this.mForegroundActivity;
    }

    boolean isForegroundActivity() {
        return this.mForegroundActivity != null;
    }

    synchronized void setForegroundActivity(Context context) {
        if (context != null) {
            Log.d("LocalBluetoothManager", "setting foreground activity to non-null context");
            this.mForegroundActivity = context;
        } else if (this.mForegroundActivity != null) {
            Log.d("LocalBluetoothManager", "setting foreground activity to null");
            this.mForegroundActivity = null;
        }
    }

    CachedBluetoothDeviceManager getCachedDeviceManager() {
        return this.mCachedDeviceManager;
    }

    BluetoothEventManager getEventManager() {
        return this.mEventManager;
    }

    LocalBluetoothProfileManager getProfileManager() {
        return this.mProfileManager;
    }
}
