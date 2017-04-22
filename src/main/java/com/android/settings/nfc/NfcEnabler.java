package com.android.settings.nfc;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.nfc.NfcAdapter;
import android.os.UserManager;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceScreen;
import android.preference.SwitchPreference;
import com.android.settings.R;

public class NfcEnabler implements OnPreferenceChangeListener {
    private final PreferenceScreen mAndroidBeam;
    private boolean mBeamDisallowed;
    private final Context mContext;
    private final IntentFilter mIntentFilter;
    private final NfcAdapter mNfcAdapter;
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            if ("android.nfc.action.ADAPTER_STATE_CHANGED".equals(intent.getAction())) {
                NfcEnabler.this.handleNfcStateChanged(intent.getIntExtra("android.nfc.extra.ADAPTER_STATE", 1));
            }
        }
    };
    private final SwitchPreference mSwitch;

    public NfcEnabler(Context context, SwitchPreference switchPreference, PreferenceScreen androidBeam) {
        this.mContext = context;
        this.mSwitch = switchPreference;
        this.mAndroidBeam = androidBeam;
        this.mNfcAdapter = NfcAdapter.getDefaultAdapter(context);
        this.mBeamDisallowed = ((UserManager) this.mContext.getSystemService("user")).hasUserRestriction("no_outgoing_beam");
        if (this.mNfcAdapter == null) {
            this.mSwitch.setEnabled(false);
            this.mAndroidBeam.setEnabled(false);
            this.mIntentFilter = null;
            return;
        }
        if (this.mBeamDisallowed) {
            this.mAndroidBeam.setEnabled(false);
        }
        this.mIntentFilter = new IntentFilter("android.nfc.action.ADAPTER_STATE_CHANGED");
    }

    public void resume() {
        if (this.mNfcAdapter != null) {
            handleNfcStateChanged(this.mNfcAdapter.getAdapterState());
            this.mContext.registerReceiver(this.mReceiver, this.mIntentFilter);
            this.mSwitch.setOnPreferenceChangeListener(this);
        }
    }

    public void pause() {
        if (this.mNfcAdapter != null) {
            this.mContext.unregisterReceiver(this.mReceiver);
            this.mSwitch.setOnPreferenceChangeListener(null);
        }
    }

    public boolean onPreferenceChange(Preference preference, Object value) {
        boolean desiredState = ((Boolean) value).booleanValue();
        this.mSwitch.setEnabled(false);
        if (desiredState) {
            this.mNfcAdapter.enable();
        } else {
            this.mNfcAdapter.disable();
        }
        return false;
    }

    private void handleNfcStateChanged(int newState) {
        boolean z = true;
        switch (newState) {
            case 1:
                this.mSwitch.setChecked(false);
                this.mSwitch.setEnabled(true);
                this.mAndroidBeam.setEnabled(false);
                this.mAndroidBeam.setSummary(R.string.android_beam_disabled_summary);
                return;
            case 2:
                this.mSwitch.setChecked(true);
                this.mSwitch.setEnabled(false);
                this.mAndroidBeam.setEnabled(false);
                return;
            case 3:
                this.mSwitch.setChecked(true);
                this.mSwitch.setEnabled(true);
                PreferenceScreen preferenceScreen = this.mAndroidBeam;
                if (this.mBeamDisallowed) {
                    z = false;
                }
                preferenceScreen.setEnabled(z);
                if (!this.mNfcAdapter.isNdefPushEnabled() || this.mBeamDisallowed) {
                    this.mAndroidBeam.setSummary(R.string.android_beam_off_summary);
                    return;
                } else {
                    this.mAndroidBeam.setSummary(R.string.android_beam_on_summary);
                    return;
                }
            case 4:
                this.mSwitch.setChecked(false);
                this.mSwitch.setEnabled(false);
                this.mAndroidBeam.setEnabled(false);
                return;
            default:
                return;
        }
    }
}
