package com.android.settings.wifi;

import android.os.Bundle;
import android.preference.PreferenceActivity;
import com.android.settings.R;

public class WifiInfo extends PreferenceActivity {
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.testing_wifi_settings);
    }
}
