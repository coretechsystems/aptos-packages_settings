package com.android.settings.inputmethod;

import android.content.Context;
import android.preference.SwitchPreference;

class SwitchWithNoTextPreference extends SwitchPreference {
    SwitchWithNoTextPreference(Context context) {
        super(context);
        setSwitchTextOn("");
        setSwitchTextOff("");
    }
}
