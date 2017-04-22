package com.android.settings.bluetooth;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.SystemProperties;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.util.Log;
import com.android.settings.R;

final class BluetoothDiscoverableEnabler implements OnPreferenceClickListener {
    private Context mContext;
    private boolean mDiscoverable;
    private final Preference mDiscoveryPreference;
    private final LocalBluetoothAdapter mLocalAdapter;
    private int mNumberOfPairedDevices;
    private final SharedPreferences mSharedPreferences;
    private int mTimeoutSecs;
    private final Handler mUiHandler;
    private final Runnable mUpdateCountdownSummaryRunnable;

    class AnonymousClass1 extends BroadcastReceiver {
        final /* synthetic */ BluetoothDiscoverableEnabler this$0;

        public void onReceive(Context context, Intent intent) {
            if ("android.bluetooth.adapter.action.SCAN_MODE_CHANGED".equals(intent.getAction())) {
                int mode = intent.getIntExtra("android.bluetooth.adapter.extra.SCAN_MODE", Integer.MIN_VALUE);
                if (mode != Integer.MIN_VALUE) {
                    this.this$0.handleModeChanged(mode);
                }
            }
        }
    }

    class AnonymousClass2 implements Runnable {
        final /* synthetic */ BluetoothDiscoverableEnabler this$0;

        public void run() {
            this.this$0.updateCountdownSummary();
        }
    }

    public boolean onPreferenceClick(Preference preference) {
        this.mDiscoverable = !this.mDiscoverable;
        setEnabled(this.mDiscoverable);
        return true;
    }

    private void setEnabled(boolean enable) {
        if (enable) {
            int timeout = getDiscoverableTimeout();
            long endTimestamp = System.currentTimeMillis() + (((long) timeout) * 1000);
            LocalBluetoothPreferences.persistDiscoverableEndTimestamp(this.mContext, endTimestamp);
            this.mLocalAdapter.setScanMode(23, timeout);
            updateCountdownSummary();
            Log.d("BluetoothDiscoverableEnabler", "setEnabled(): enabled = " + enable + "timeout = " + timeout);
            if (timeout > 0) {
                BluetoothDiscoverableTimeoutReceiver.setDiscoverableAlarm(this.mContext, endTimestamp);
                return;
            } else {
                BluetoothDiscoverableTimeoutReceiver.cancelDiscoverableAlarm(this.mContext);
                return;
            }
        }
        this.mLocalAdapter.setScanMode(21);
        BluetoothDiscoverableTimeoutReceiver.cancelDiscoverableAlarm(this.mContext);
    }

    private void updateTimerDisplay(int timeout) {
        if (getDiscoverableTimeout() == 0) {
            this.mDiscoveryPreference.setSummary(R.string.bluetooth_is_discoverable_always);
            return;
        }
        String textTimeout = formatTimeRemaining(timeout);
        this.mDiscoveryPreference.setSummary(this.mContext.getString(R.string.bluetooth_is_discoverable, new Object[]{textTimeout}));
    }

    private static String formatTimeRemaining(int timeout) {
        StringBuilder sb = new StringBuilder(6);
        int min = timeout / 60;
        sb.append(min).append(':');
        int sec = timeout - (min * 60);
        if (sec < 10) {
            sb.append('0');
        }
        sb.append(sec);
        return sb.toString();
    }

    void setDiscoverableTimeout(int index) {
        String timeoutValue;
        switch (index) {
            case 1:
                this.mTimeoutSecs = 300;
                timeoutValue = "fivemin";
                break;
            case 2:
                this.mTimeoutSecs = 3600;
                timeoutValue = "onehour";
                break;
            case 3:
                this.mTimeoutSecs = 0;
                timeoutValue = "never";
                break;
            default:
                this.mTimeoutSecs = 120;
                timeoutValue = "twomin";
                break;
        }
        this.mSharedPreferences.edit().putString("bt_discoverable_timeout", timeoutValue).apply();
        setEnabled(true);
    }

    private int getDiscoverableTimeout() {
        if (this.mTimeoutSecs != -1) {
            return this.mTimeoutSecs;
        }
        int timeout = SystemProperties.getInt("debug.bt.discoverable_time", -1);
        if (timeout < 0) {
            String timeoutValue = this.mSharedPreferences.getString("bt_discoverable_timeout", "twomin");
            if (timeoutValue.equals("never")) {
                timeout = 0;
            } else if (timeoutValue.equals("onehour")) {
                timeout = 3600;
            } else if (timeoutValue.equals("fivemin")) {
                timeout = 300;
            } else {
                timeout = 120;
            }
        }
        this.mTimeoutSecs = timeout;
        return timeout;
    }

    int getDiscoverableTimeoutIndex() {
        switch (getDiscoverableTimeout()) {
            case 0:
                return 3;
            case 300:
                return 1;
            case 3600:
                return 2;
            default:
                return 0;
        }
    }

    void handleModeChanged(int mode) {
        Log.d("BluetoothDiscoverableEnabler", "handleModeChanged(): mode = " + mode);
        if (mode == 23) {
            this.mDiscoverable = true;
            updateCountdownSummary();
            return;
        }
        this.mDiscoverable = false;
        setSummaryNotDiscoverable();
    }

    private void setSummaryNotDiscoverable() {
        if (this.mNumberOfPairedDevices != 0) {
            this.mDiscoveryPreference.setSummary(R.string.bluetooth_only_visible_to_paired_devices);
        } else {
            this.mDiscoveryPreference.setSummary(R.string.bluetooth_not_visible_to_other_devices);
        }
    }

    private void updateCountdownSummary() {
        if (this.mLocalAdapter.getScanMode() == 23) {
            long currentTimestamp = System.currentTimeMillis();
            long endTimestamp = LocalBluetoothPreferences.getDiscoverableEndTimestamp(this.mContext);
            if (currentTimestamp > endTimestamp) {
                updateTimerDisplay(0);
                return;
            }
            updateTimerDisplay((int) ((endTimestamp - currentTimestamp) / 1000));
            synchronized (this) {
                this.mUiHandler.removeCallbacks(this.mUpdateCountdownSummaryRunnable);
                this.mUiHandler.postDelayed(this.mUpdateCountdownSummaryRunnable, 1000);
            }
        }
    }
}
