package com.android.settings;

import android.util.Log;

public class SubSettings extends SettingsActivity {
    public boolean onNavigateUp() {
        finish();
        return true;
    }

    protected boolean isValidFragment(String fragmentName) {
        Log.d("SubSettings", "Launching fragment " + fragmentName);
        return true;
    }
}
