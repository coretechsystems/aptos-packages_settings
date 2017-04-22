package com.android.settings.wifi;

import android.app.AlertDialog.Builder;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources.Theme;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.preference.PreferenceFragment;
import android.util.Log;
import com.android.settings.ButtonBarHandler;
import com.android.settings.R;
import com.android.setupwizard.navigationbar.SetupWizardNavBar;
import com.android.setupwizard.navigationbar.SetupWizardNavBar.NavigationBarListener;

public class WifiSetupActivity extends WifiPickerActivity implements ButtonBarHandler, NavigationBarListener {
    private boolean mAllowSkip = true;
    private boolean mAutoFinishOnConnection;
    private final IntentFilter mFilter = new IntentFilter("android.net.wifi.STATE_CHANGE");
    private SetupWizardNavBar mNavigationBar;
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            WifiSetupActivity.this.refreshConnectionState();
        }
    };
    private boolean mUserSelectedNetwork;
    private boolean mWifiConnected;

    public static class WifiSkipDialog extends DialogFragment {
        public static WifiSkipDialog newInstance(int messageRes) {
            Bundle args = new Bundle();
            args.putInt("messageRes", messageRes);
            WifiSkipDialog dialog = new WifiSkipDialog();
            dialog.setArguments(args);
            return dialog;
        }

        public Dialog onCreateDialog(Bundle savedInstanceState) {
            return new Builder(getActivity()).setMessage(getArguments().getInt("messageRes")).setCancelable(false).setNegativeButton(R.string.wifi_skip_anyway, new OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    ((WifiSetupActivity) WifiSkipDialog.this.getActivity()).finishOrNext(1);
                }
            }).setPositiveButton(R.string.wifi_dont_skip, new OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                }
            }).create();
        }
    }

    protected void onCreate(Bundle savedInstanceState) {
        boolean z = true;
        super.onCreate(savedInstanceState);
        Intent intent = getIntent();
        this.mAutoFinishOnConnection = intent.getBooleanExtra("wifi_auto_finish_on_connect", false);
        this.mAllowSkip = intent.getBooleanExtra("allowSkip", true);
        if (intent.getBooleanExtra("wifi_require_user_network_selection", false)) {
            z = false;
        }
        this.mUserSelectedNetwork = z;
    }

    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean("userSelectedNetwork", this.mUserSelectedNetwork);
    }

    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        this.mUserSelectedNetwork = savedInstanceState.getBoolean("userSelectedNetwork", true);
    }

    private void refreshConnectionState() {
        boolean connected = true;
        ConnectivityManager connectivity = (ConnectivityManager) getSystemService("connectivity");
        if (connectivity == null || !connectivity.getNetworkInfo(1).isConnected()) {
            connected = false;
        }
        refreshConnectionState(connected);
    }

    private void refreshConnectionState(boolean connected) {
        this.mWifiConnected = connected;
        if (connected) {
            if (this.mAutoFinishOnConnection && this.mUserSelectedNetwork) {
                Log.d("WifiSetupActivity", "Auto-finishing with connection");
                finishOrNext(-1);
                this.mUserSelectedNetwork = false;
            }
            if (this.mNavigationBar != null) {
                this.mNavigationBar.getNextButton().setText(R.string.setup_wizard_next_button_label);
                this.mNavigationBar.getNextButton().setEnabled(true);
            }
        } else if (this.mNavigationBar != null) {
            this.mNavigationBar.getNextButton().setText(R.string.skip_label);
            this.mNavigationBar.getNextButton().setEnabled(this.mAllowSkip);
        }
    }

    void networkSelected() {
        Log.d("WifiSetupActivity", "Network selected by user");
        this.mUserSelectedNetwork = true;
    }

    public void onResume() {
        super.onResume();
        registerReceiver(this.mReceiver, this.mFilter);
        refreshConnectionState();
    }

    public void onPause() {
        unregisterReceiver(this.mReceiver);
        super.onPause();
    }

    protected void onApplyThemeResource(Theme theme, int resid, boolean first) {
        String themeName = getIntent().getStringExtra("theme");
        if ("holo_light".equalsIgnoreCase(themeName) || "material_light".equalsIgnoreCase(themeName)) {
            resid = R.style.SetupWizardWifiTheme.Light;
        } else if ("holo".equalsIgnoreCase(themeName) || "material".equalsIgnoreCase(themeName)) {
            resid = R.style.SetupWizardWifiTheme;
        }
        super.onApplyThemeResource(theme, resid, first);
    }

    protected boolean isValidFragment(String fragmentName) {
        return WifiSettingsForSetupWizard.class.getName().equals(fragmentName);
    }

    Class<? extends PreferenceFragment> getWifiSettingsClass() {
        return WifiSettingsForSetupWizard.class;
    }

    public void finishOrNext(int resultCode) {
        Log.d("WifiSetupActivity", "finishOrNext resultCode=" + resultCode + " isUsingWizardManager=" + isUsingWizardManager());
        if (isUsingWizardManager()) {
            sendResultsToSetupWizard(resultCode);
            return;
        }
        setResult(resultCode);
        finish();
    }

    private boolean isUsingWizardManager() {
        return getIntent().hasExtra("scriptUri");
    }

    private void sendResultsToSetupWizard(int resultCode) {
        Intent intent = getIntent();
        Intent nextIntent = new Intent("com.android.wizard.NEXT");
        nextIntent.putExtra("scriptUri", intent.getStringExtra("scriptUri"));
        nextIntent.putExtra("actionId", intent.getStringExtra("actionId"));
        nextIntent.putExtra("theme", intent.getStringExtra("theme"));
        nextIntent.putExtra("com.android.setupwizard.ResultCode", resultCode);
        startActivityForResult(nextIntent, 10000);
    }

    public void onNavigationBarCreated(SetupWizardNavBar bar) {
        this.mNavigationBar = bar;
        boolean useImmersiveMode = getIntent().getBooleanExtra("useImmersiveMode", false);
        bar.setUseImmersiveMode(useImmersiveMode);
        if (useImmersiveMode) {
            getWindow().setNavigationBarColor(0);
            getWindow().setStatusBarColor(0);
        }
    }

    public void onNavigateBack() {
        onBackPressed();
    }

    public void onNavigateNext() {
        if (this.mWifiConnected) {
            finishOrNext(-1);
        } else {
            WifiSkipDialog.newInstance(isNetworkConnected() ? R.string.wifi_skipped_message : R.string.wifi_and_mobile_skipped_message).show(getFragmentManager(), "dialog");
        }
    }

    private boolean isNetworkConnected() {
        ConnectivityManager connectivity = (ConnectivityManager) getSystemService("connectivity");
        if (connectivity == null) {
            return false;
        }
        NetworkInfo info = connectivity.getActiveNetworkInfo();
        if (info == null || !info.isConnected()) {
            return false;
        }
        return true;
    }
}
