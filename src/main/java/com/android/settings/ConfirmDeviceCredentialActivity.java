package com.android.settings;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

public class ConfirmDeviceCredentialActivity extends Activity {
    public static final String TAG = ConfirmDeviceCredentialActivity.class.getSimpleName();

    public static Intent createIntent(CharSequence title, CharSequence details) {
        Intent intent = new Intent();
        intent.setClassName("com.android.settings", ConfirmDeviceCredentialActivity.class.getName());
        intent.putExtra("android.app.extra.TITLE", title);
        intent.putExtra("android.app.extra.DESCRIPTION", details);
        return intent;
    }

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = getIntent();
        if (!new ChooseLockSettingsHelper(this).launchConfirmationActivity(0, intent.getStringExtra("android.app.extra.TITLE"), intent.getStringExtra("android.app.extra.DESCRIPTION"))) {
            Log.d(TAG, "No pattern, password or PIN set.");
            setResult(-1);
            finish();
        }
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        boolean credentialsConfirmed;
        int i = 0;
        if (resultCode == -1) {
            credentialsConfirmed = true;
        } else {
            credentialsConfirmed = false;
        }
        Log.d(TAG, "Device credentials confirmed: " + credentialsConfirmed);
        if (credentialsConfirmed) {
            i = -1;
        }
        setResult(i);
        finish();
    }
}
