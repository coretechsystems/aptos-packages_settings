package com.android.settings;

import android.app.Activity;
import android.app.AlertDialog.Builder;
import android.app.Dialog;
import android.app.admin.DevicePolicyManager;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.os.Bundle;
import android.os.RemoteException;
import android.view.WindowManagerGlobal;

public class MonitoringCertInfoActivity extends Activity implements OnClickListener {
    private boolean hasDeviceOwner = false;

    protected void onCreate(Bundle savedStates) {
        boolean z;
        int buttonLabel;
        super.onCreate(savedStates);
        DevicePolicyManager dpm = (DevicePolicyManager) getSystemService("device_policy");
        Builder builder = new Builder(this);
        builder.setTitle(R.string.ssl_ca_cert_dialog_title);
        builder.setCancelable(true);
        if (dpm.getDeviceOwner() != null) {
            z = true;
        } else {
            z = false;
        }
        this.hasDeviceOwner = z;
        if (this.hasDeviceOwner) {
            builder.setMessage(getResources().getString(R.string.ssl_ca_cert_info_message, new Object[]{dpm.getDeviceOwnerName()}));
            buttonLabel = R.string.done_button;
        } else {
            builder.setIcon(17301624);
            builder.setMessage(R.string.ssl_ca_cert_warning_message);
            buttonLabel = R.string.ssl_ca_cert_settings_button;
        }
        builder.setPositiveButton(buttonLabel, this);
        Dialog dialog = builder.create();
        dialog.getWindow().setType(2003);
        try {
            WindowManagerGlobal.getWindowManagerService().dismissKeyguard();
        } catch (RemoteException e) {
        }
        dialog.setOnCancelListener(new OnCancelListener() {
            public void onCancel(DialogInterface dialog) {
                MonitoringCertInfoActivity.this.finish();
            }
        });
        dialog.show();
    }

    public void onClick(DialogInterface dialog, int which) {
        if (this.hasDeviceOwner) {
            finish();
            return;
        }
        Intent intent = new Intent("com.android.settings.TRUSTED_CREDENTIALS_USER");
        intent.setFlags(335544320);
        startActivity(intent);
        finish();
    }
}
