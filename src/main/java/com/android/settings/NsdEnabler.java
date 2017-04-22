package com.android.settings;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.net.nsd.NsdManager;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;

public class NsdEnabler implements OnPreferenceChangeListener {
    private final CheckBoxPreference mCheckbox;
    private final Context mContext;
    private final IntentFilter mIntentFilter;
    private NsdManager mNsdManager;
    private final BroadcastReceiver mReceiver;

    public void resume() {
        this.mContext.registerReceiver(this.mReceiver, this.mIntentFilter);
        this.mCheckbox.setOnPreferenceChangeListener(this);
    }

    public void pause() {
        this.mContext.unregisterReceiver(this.mReceiver);
        this.mCheckbox.setOnPreferenceChangeListener(null);
    }

    public boolean onPreferenceChange(Preference preference, Object value) {
        boolean desiredState = ((Boolean) value).booleanValue();
        this.mCheckbox.setEnabled(false);
        this.mNsdManager.setEnabled(desiredState);
        return false;
    }
}
