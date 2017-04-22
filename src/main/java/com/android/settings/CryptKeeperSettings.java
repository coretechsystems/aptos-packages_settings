package com.android.settings;

import android.app.Activity;
import android.app.AlertDialog.Builder;
import android.app.Fragment;
import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.os.Bundle;
import android.preference.Preference;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;

public class CryptKeeperSettings extends Fragment {
    private View mBatteryWarning;
    private View mContentView;
    private Button mInitiateButton;
    private OnClickListener mInitiateListener = new OnClickListener() {
        public void onClick(View v) {
            if (!CryptKeeperSettings.this.runKeyguardConfirmation(55)) {
                new Builder(CryptKeeperSettings.this.getActivity()).setTitle(R.string.crypt_keeper_dialog_need_password_title).setMessage(R.string.crypt_keeper_dialog_need_password_message).setPositiveButton(17039370, null).create().show();
            }
        }
    };
    private IntentFilter mIntentFilter;
    private BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            int i = 8;
            boolean z = true;
            if (intent.getAction().equals("android.intent.action.BATTERY_CHANGED")) {
                boolean levelOk;
                boolean pluggedOk;
                int i2;
                int level = intent.getIntExtra("level", 0);
                int plugged = intent.getIntExtra("plugged", 0);
                int invalidCharger = intent.getIntExtra("invalid_charger", 0);
                if (level >= 80) {
                    levelOk = true;
                } else {
                    levelOk = false;
                }
                if ((plugged & 7) == 0 || invalidCharger != 0) {
                    pluggedOk = false;
                } else {
                    pluggedOk = true;
                }
                Button access$000 = CryptKeeperSettings.this.mInitiateButton;
                if (!(levelOk && pluggedOk)) {
                    z = false;
                }
                access$000.setEnabled(z);
                View access$100 = CryptKeeperSettings.this.mPowerWarning;
                if (pluggedOk) {
                    i2 = 8;
                } else {
                    i2 = 0;
                }
                access$100.setVisibility(i2);
                View access$200 = CryptKeeperSettings.this.mBatteryWarning;
                if (!levelOk) {
                    i = 0;
                }
                access$200.setVisibility(i);
            }
        }
    };
    private View mPowerWarning;

    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedState) {
        this.mContentView = inflater.inflate(R.layout.crypt_keeper_settings, null);
        this.mIntentFilter = new IntentFilter();
        this.mIntentFilter.addAction("android.intent.action.BATTERY_CHANGED");
        this.mInitiateButton = (Button) this.mContentView.findViewById(R.id.initiate_encrypt);
        this.mInitiateButton.setOnClickListener(this.mInitiateListener);
        this.mInitiateButton.setEnabled(false);
        this.mPowerWarning = this.mContentView.findViewById(R.id.warning_unplugged);
        this.mBatteryWarning = this.mContentView.findViewById(R.id.warning_low_charge);
        return this.mContentView;
    }

    public void onResume() {
        super.onResume();
        getActivity().registerReceiver(this.mIntentReceiver, this.mIntentFilter);
    }

    public void onPause() {
        super.onPause();
        getActivity().unregisterReceiver(this.mIntentReceiver);
    }

    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        Activity activity = getActivity();
        if ("android.app.action.START_ENCRYPTION".equals(activity.getIntent().getAction())) {
            DevicePolicyManager dpm = (DevicePolicyManager) activity.getSystemService("device_policy");
            if (dpm != null && dpm.getStorageEncryptionStatus() != 1) {
                activity.finish();
            }
        }
    }

    private boolean runKeyguardConfirmation(int request) {
        Resources res = getActivity().getResources();
        ChooseLockSettingsHelper helper = new ChooseLockSettingsHelper(getActivity(), this);
        if (helper.utils().getKeyguardStoredPasswordQuality() != 0) {
            return helper.launchConfirmationActivity(request, res.getText(R.string.master_clear_gesture_prompt), res.getText(R.string.crypt_keeper_confirm_encrypt), true);
        }
        showFinalConfirmation(1, "");
        return true;
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 55 && resultCode == -1 && data != null) {
            int type = data.getIntExtra("type", -1);
            String password = data.getStringExtra("password");
            if (!TextUtils.isEmpty(password)) {
                showFinalConfirmation(type, password);
            }
        }
    }

    private void showFinalConfirmation(int type, String password) {
        Preference preference = new Preference(getActivity());
        preference.setFragment(CryptKeeperConfirm.class.getName());
        preference.setTitle(R.string.crypt_keeper_confirm_title);
        preference.getExtras().putInt("type", type);
        preference.getExtras().putString("password", password);
        ((SettingsActivity) getActivity()).onPreferenceStartFragment(null, preference);
    }
}
