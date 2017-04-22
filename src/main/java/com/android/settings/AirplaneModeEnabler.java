package com.android.settings;

import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.Message;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.SwitchPreference;
import android.provider.Settings.Global;
import com.android.internal.telephony.PhoneStateIntentReceiver;

public class AirplaneModeEnabler implements OnPreferenceChangeListener {
    private ContentObserver mAirplaneModeObserver = new ContentObserver(new Handler()) {
        public void onChange(boolean selfChange) {
            AirplaneModeEnabler.this.onAirplaneModeChanged();
        }
    };
    private final Context mContext;
    private Handler mHandler = new Handler() {
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case 3:
                    AirplaneModeEnabler.this.onAirplaneModeChanged();
                    return;
                default:
                    return;
            }
        }
    };
    private PhoneStateIntentReceiver mPhoneStateReceiver;
    private final SwitchPreference mSwitchPref;

    public AirplaneModeEnabler(Context context, SwitchPreference airplaneModeCheckBoxPreference) {
        this.mContext = context;
        this.mSwitchPref = airplaneModeCheckBoxPreference;
        airplaneModeCheckBoxPreference.setPersistent(false);
        this.mPhoneStateReceiver = new PhoneStateIntentReceiver(this.mContext, this.mHandler);
        this.mPhoneStateReceiver.notifyServiceState(3);
    }

    public void resume() {
        this.mSwitchPref.setChecked(isAirplaneModeOn(this.mContext));
        this.mPhoneStateReceiver.registerIntent();
        this.mSwitchPref.setOnPreferenceChangeListener(this);
        this.mContext.getContentResolver().registerContentObserver(Global.getUriFor("airplane_mode_on"), true, this.mAirplaneModeObserver);
    }

    public void pause() {
        this.mPhoneStateReceiver.unregisterIntent();
        this.mSwitchPref.setOnPreferenceChangeListener(null);
        this.mContext.getContentResolver().unregisterContentObserver(this.mAirplaneModeObserver);
    }

    public static boolean isAirplaneModeOn(Context context) {
        return Global.getInt(context.getContentResolver(), "airplane_mode_on", 0) != 0;
    }

    private void setAirplaneModeOn(boolean enabling) {
        Global.putInt(this.mContext.getContentResolver(), "airplane_mode_on", enabling ? 1 : 0);
        this.mSwitchPref.setChecked(enabling);
        Intent intent = new Intent("android.intent.action.AIRPLANE_MODE");
        intent.putExtra("state", enabling);
        this.mContext.sendBroadcastAsUser(intent, UserHandle.ALL);
    }

    private void onAirplaneModeChanged() {
        this.mSwitchPref.setChecked(isAirplaneModeOn(this.mContext));
    }

    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (!Boolean.parseBoolean(SystemProperties.get("ril.cdma.inecmmode"))) {
            setAirplaneModeOn(((Boolean) newValue).booleanValue());
        }
        return true;
    }

    public void setAirplaneModeInECM(boolean isECMExit, boolean isAirplaneModeOn) {
        if (isECMExit) {
            setAirplaneModeOn(isAirplaneModeOn);
        } else {
            onAirplaneModeChanged();
        }
    }
}
