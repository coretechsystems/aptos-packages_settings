package com.android.settings.applications;

import android.content.Intent;
import com.android.settings.SettingsActivity;

public class InstalledAppDetailsTop extends SettingsActivity {
    public Intent getIntent() {
        Intent modIntent = new Intent(super.getIntent());
        modIntent.putExtra(":settings:show_fragment", InstalledAppDetails.class.getName());
        return modIntent;
    }

    protected boolean isValidFragment(String fragmentName) {
        if (InstalledAppDetails.class.getName().equals(fragmentName)) {
            return true;
        }
        return false;
    }
}
