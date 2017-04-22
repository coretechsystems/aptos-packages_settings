package com.android.settings;

import android.app.AlertDialog.Builder;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.SystemProperties;
import android.preference.DialogPreference;
import android.util.AttributeSet;
import android.view.View;

public class BugreportPreference extends DialogPreference {
    public BugreportPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    protected void onPrepareDialogBuilder(Builder builder) {
        super.onPrepareDialogBuilder(builder);
        builder.setPositiveButton(17040505, this);
        builder.setMessage(17039609);
    }

    protected void showDialog(Bundle state) {
        super.showDialog(state);
    }

    protected void onBindDialogView(View view) {
        super.onBindDialogView(view);
    }

    protected void onDialogClosed(boolean positiveResult) {
        super.onDialogClosed(positiveResult);
    }

    public void onClick(DialogInterface dialog, int which) {
        if (which == -1) {
            SystemProperties.set("ctl.start", "bugreport");
        }
    }
}
