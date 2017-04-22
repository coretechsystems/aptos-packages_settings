package com.android.settings.wifi;

import android.content.Intent;
import android.preference.PreferenceFragment;
import com.android.settings.ButtonBarHandler;
import com.android.settings.R;
import com.android.settings.SettingsActivity;
import com.android.settings.wifi.p2p.WifiP2pSettings;

public class WifiPickerActivity extends SettingsActivity implements ButtonBarHandler {
    public Intent getIntent() {
        Intent modIntent = new Intent(super.getIntent());
        if (!modIntent.hasExtra(":settings:show_fragment")) {
            modIntent.putExtra(":settings:show_fragment", getWifiSettingsClass().getName());
            modIntent.putExtra(":settings:show_fragment_title_resid", R.string.wifi_select_network);
        }
        return modIntent;
    }

    protected boolean isValidFragment(String fragmentName) {
        if (WifiSettings.class.getName().equals(fragmentName) || WifiP2pSettings.class.getName().equals(fragmentName) || SavedAccessPointsWifiSettings.class.getName().equals(fragmentName) || AdvancedWifiSettings.class.getName().equals(fragmentName)) {
            return true;
        }
        return false;
    }

    Class<? extends PreferenceFragment> getWifiSettingsClass() {
        return WifiSettings.class;
    }
}
