package com.android.settings;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class TestingSettingsBroadcastReceiver extends BroadcastReceiver {
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction().equals("android.provider.Telephony.SECRET_CODE")) {
            Intent i = new Intent("android.intent.action.MAIN");
            i.setClass(context, TestingSettings.class);
            i.setFlags(268435456);
            context.startActivity(i);
        }
    }
}
