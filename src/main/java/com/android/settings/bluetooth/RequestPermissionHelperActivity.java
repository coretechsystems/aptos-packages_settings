package com.android.settings.bluetooth;

import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import com.android.internal.app.AlertActivity;
import com.android.internal.app.AlertController.AlertParams;
import com.android.settings.R;

public class RequestPermissionHelperActivity extends AlertActivity implements OnClickListener {
    private boolean mEnableOnly;
    private LocalBluetoothAdapter mLocalAdapter;
    private int mTimeout;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (parseIntent()) {
            finish();
            return;
        }
        createDialog();
        if (getResources().getBoolean(R.bool.auto_confirm_bluetooth_activation_dialog)) {
            onClick(null, -1);
            dismiss();
        }
    }

    void createDialog() {
        AlertParams p = this.mAlertParams;
        if (this.mEnableOnly) {
            p.mMessage = getString(R.string.bluetooth_ask_enablement);
        } else if (this.mTimeout == 0) {
            p.mMessage = getString(R.string.bluetooth_ask_enablement_and_lasting_discovery);
        } else {
            p.mMessage = getString(R.string.bluetooth_ask_enablement_and_discovery, new Object[]{Integer.valueOf(this.mTimeout)});
        }
        p.mPositiveButtonText = getString(R.string.allow);
        p.mPositiveButtonListener = this;
        p.mNegativeButtonText = getString(R.string.deny);
        p.mNegativeButtonListener = this;
        setupAlert();
    }

    public void onClick(DialogInterface dialog, int which) {
        int returnCode;
        switch (which) {
            case -2:
                returnCode = 0;
                break;
            case -1:
                int btState = 0;
                int retryCount = 30;
                do {
                    try {
                        btState = this.mLocalAdapter.getBluetoothState();
                        Thread.sleep(100);
                        if (btState == 13) {
                            retryCount--;
                        }
                    } catch (InterruptedException e) {
                    }
                    if (btState == 11 && btState != 12 && !this.mLocalAdapter.enable()) {
                        returnCode = 0;
                        break;
                    } else {
                        returnCode = -1000;
                        break;
                    }
                } while (retryCount > 0);
                if (btState == 11) {
                    break;
                }
                returnCode = -1000;
            default:
                return;
        }
        setResult(returnCode);
    }

    private boolean parseIntent() {
        Intent intent = getIntent();
        if (intent != null && intent.getAction().equals("com.android.settings.bluetooth.ACTION_INTERNAL_REQUEST_BT_ON")) {
            this.mEnableOnly = true;
        } else if (intent == null || !intent.getAction().equals("com.android.settings.bluetooth.ACTION_INTERNAL_REQUEST_BT_ON_AND_DISCOVERABLE")) {
            setResult(0);
            return true;
        } else {
            this.mEnableOnly = false;
            this.mTimeout = intent.getIntExtra("android.bluetooth.adapter.extra.DISCOVERABLE_DURATION", 120);
        }
        LocalBluetoothManager manager = LocalBluetoothManager.getInstance(this);
        if (manager == null) {
            Log.e("RequestPermissionHelperActivity", "Error: there's a problem starting Bluetooth");
            setResult(0);
            return true;
        }
        this.mLocalAdapter = manager.getBluetoothAdapter();
        return false;
    }

    public void onBackPressed() {
        setResult(0);
        super.onBackPressed();
    }
}
