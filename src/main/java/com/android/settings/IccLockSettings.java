package com.android.settings;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.os.AsyncResult;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;
import android.util.Log;
import android.widget.Toast;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneFactory;

public class IccLockSettings extends PreferenceActivity implements OnPinEnteredListener {
    private int mDialogState = 0;
    private String mError;
    private Handler mHandler = new Handler() {
        public void handleMessage(Message msg) {
            boolean z = true;
            AsyncResult ar = msg.obj;
            IccLockSettings iccLockSettings;
            switch (msg.what) {
                case 100:
                    iccLockSettings = IccLockSettings.this;
                    if (ar.exception != null) {
                        z = false;
                    }
                    iccLockSettings.iccLockChanged(z, msg.arg1);
                    return;
                case 101:
                    iccLockSettings = IccLockSettings.this;
                    if (ar.exception != null) {
                        z = false;
                    }
                    iccLockSettings.iccPinChanged(z, msg.arg1);
                    return;
                case 102:
                    IccLockSettings.this.updatePreferences();
                    return;
                default:
                    return;
            }
        }
    };
    private String mNewPin;
    private String mOldPin;
    private Phone mPhone;
    private String mPin;
    private EditPinPreference mPinDialog;
    private CheckBoxPreference mPinToggle;
    private Resources mRes;
    private final BroadcastReceiver mSimStateReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            if ("android.intent.action.SIM_STATE_CHANGED".equals(intent.getAction())) {
                IccLockSettings.this.mHandler.sendMessage(IccLockSettings.this.mHandler.obtainMessage(102));
            }
        }
    };
    private boolean mToState;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Utils.isMonkeyRunning()) {
            finish();
            return;
        }
        addPreferencesFromResource(R.xml.sim_lock_settings);
        this.mPinDialog = (EditPinPreference) findPreference("sim_pin");
        this.mPinToggle = (CheckBoxPreference) findPreference("sim_toggle");
        if (savedInstanceState != null && savedInstanceState.containsKey("dialogState")) {
            this.mDialogState = savedInstanceState.getInt("dialogState");
            this.mPin = savedInstanceState.getString("dialogPin");
            this.mError = savedInstanceState.getString("dialogError");
            this.mToState = savedInstanceState.getBoolean("enableState");
            switch (this.mDialogState) {
                case 3:
                    this.mOldPin = savedInstanceState.getString("oldPinCode");
                    break;
                case 4:
                    this.mOldPin = savedInstanceState.getString("oldPinCode");
                    this.mNewPin = savedInstanceState.getString("newPinCode");
                    break;
            }
        }
        this.mPinDialog.setOnPinEnteredListener(this);
        getPreferenceScreen().setPersistent(false);
        this.mPhone = PhoneFactory.getDefaultPhone();
        this.mRes = getResources();
        updatePreferences();
    }

    private void updatePreferences() {
        this.mPinToggle.setChecked(this.mPhone.getIccCard().getIccLockEnabled());
    }

    protected void onResume() {
        super.onResume();
        registerReceiver(this.mSimStateReceiver, new IntentFilter("android.intent.action.SIM_STATE_CHANGED"));
        if (this.mDialogState != 0) {
            showPinDialog();
        } else {
            resetDialogState();
        }
    }

    protected void onPause() {
        super.onPause();
        unregisterReceiver(this.mSimStateReceiver);
    }

    protected void onSaveInstanceState(Bundle out) {
        if (this.mPinDialog.isDialogOpen()) {
            out.putInt("dialogState", this.mDialogState);
            out.putString("dialogPin", this.mPinDialog.getEditText().getText().toString());
            out.putString("dialogError", this.mError);
            out.putBoolean("enableState", this.mToState);
            switch (this.mDialogState) {
                case 3:
                    out.putString("oldPinCode", this.mOldPin);
                    return;
                case 4:
                    out.putString("oldPinCode", this.mOldPin);
                    out.putString("newPinCode", this.mNewPin);
                    return;
                default:
                    return;
            }
        }
        super.onSaveInstanceState(out);
    }

    private void showPinDialog() {
        if (this.mDialogState != 0) {
            setDialogValues();
            this.mPinDialog.showPinDialog();
        }
    }

    private void setDialogValues() {
        this.mPinDialog.setText(this.mPin);
        String message = "";
        switch (this.mDialogState) {
            case 1:
                message = this.mRes.getString(R.string.sim_enter_pin);
                this.mPinDialog.setDialogTitle(this.mToState ? this.mRes.getString(R.string.sim_enable_sim_lock) : this.mRes.getString(R.string.sim_disable_sim_lock));
                break;
            case 2:
                message = this.mRes.getString(R.string.sim_enter_old);
                this.mPinDialog.setDialogTitle(this.mRes.getString(R.string.sim_change_pin));
                break;
            case 3:
                message = this.mRes.getString(R.string.sim_enter_new);
                this.mPinDialog.setDialogTitle(this.mRes.getString(R.string.sim_change_pin));
                break;
            case 4:
                message = this.mRes.getString(R.string.sim_reenter_new);
                this.mPinDialog.setDialogTitle(this.mRes.getString(R.string.sim_change_pin));
                break;
        }
        if (this.mError != null) {
            message = this.mError + "\n" + message;
            this.mError = null;
        }
        this.mPinDialog.setDialogMessage(message);
    }

    public void onPinEntered(EditPinPreference preference, boolean positiveResult) {
        if (positiveResult) {
            this.mPin = preference.getText();
            if (reasonablePin(this.mPin)) {
                switch (this.mDialogState) {
                    case 1:
                        tryChangeIccLockState();
                        return;
                    case 2:
                        this.mOldPin = this.mPin;
                        this.mDialogState = 3;
                        this.mError = null;
                        this.mPin = null;
                        showPinDialog();
                        return;
                    case 3:
                        this.mNewPin = this.mPin;
                        this.mDialogState = 4;
                        this.mPin = null;
                        showPinDialog();
                        return;
                    case 4:
                        if (this.mPin.equals(this.mNewPin)) {
                            this.mError = null;
                            tryChangePin();
                            return;
                        }
                        this.mError = this.mRes.getString(R.string.sim_pins_dont_match);
                        this.mDialogState = 3;
                        this.mPin = null;
                        showPinDialog();
                        return;
                    default:
                        return;
                }
            }
            this.mError = this.mRes.getString(R.string.sim_bad_pin);
            showPinDialog();
            return;
        }
        resetDialogState();
    }

    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        boolean z = false;
        if (preference == this.mPinToggle) {
            this.mToState = this.mPinToggle.isChecked();
            CheckBoxPreference checkBoxPreference = this.mPinToggle;
            if (!this.mToState) {
                z = true;
            }
            checkBoxPreference.setChecked(z);
            this.mDialogState = 1;
            showPinDialog();
        } else if (preference == this.mPinDialog) {
            this.mDialogState = 2;
            return false;
        }
        return true;
    }

    private void tryChangeIccLockState() {
        this.mPhone.getIccCard().setIccLockEnabled(this.mToState, this.mPin, Message.obtain(this.mHandler, 100));
        this.mPinToggle.setEnabled(false);
    }

    private void iccLockChanged(boolean success, int attemptsRemaining) {
        if (success) {
            this.mPinToggle.setChecked(this.mToState);
        } else {
            Toast.makeText(this, getPinPasswordErrorMessage(attemptsRemaining), 1).show();
        }
        this.mPinToggle.setEnabled(true);
        resetDialogState();
    }

    private void iccPinChanged(boolean success, int attemptsRemaining) {
        if (success) {
            Toast.makeText(this, this.mRes.getString(R.string.sim_change_succeeded), 0).show();
        } else {
            Toast.makeText(this, getPinPasswordErrorMessage(attemptsRemaining), 1).show();
        }
        resetDialogState();
    }

    private void tryChangePin() {
        this.mPhone.getIccCard().changeIccLockPassword(this.mOldPin, this.mNewPin, Message.obtain(this.mHandler, 101));
    }

    private String getPinPasswordErrorMessage(int attemptsRemaining) {
        String displayMessage;
        if (attemptsRemaining == 0) {
            displayMessage = this.mRes.getString(R.string.wrong_pin_code_pukked);
        } else if (attemptsRemaining > 0) {
            displayMessage = this.mRes.getQuantityString(R.plurals.wrong_pin_code, attemptsRemaining, new Object[]{Integer.valueOf(attemptsRemaining)});
        } else {
            displayMessage = this.mRes.getString(R.string.pin_failed);
        }
        Log.d("IccLockSettings", "getPinPasswordErrorMessage: attemptsRemaining=" + attemptsRemaining + " displayMessage=" + displayMessage);
        return displayMessage;
    }

    private boolean reasonablePin(String pin) {
        if (pin == null || pin.length() < 4 || pin.length() > 8) {
            return false;
        }
        return true;
    }

    private void resetDialogState() {
        this.mError = null;
        this.mDialogState = 2;
        this.mPin = "";
        setDialogValues();
        this.mDialogState = 0;
    }
}
