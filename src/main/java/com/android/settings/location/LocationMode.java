package com.android.settings.location;

import android.preference.PreferenceScreen;
import com.android.settings.R;
import com.android.settings.location.RadioButtonPreference.OnClickListener;

public class LocationMode extends LocationSettingsBase implements OnClickListener {
    private RadioButtonPreference mBatterySaving;
    private RadioButtonPreference mHighAccuracy;
    private RadioButtonPreference mSensorsOnly;

    public void onResume() {
        super.onResume();
        createPreferenceHierarchy();
    }

    public void onPause() {
        super.onPause();
    }

    private PreferenceScreen createPreferenceHierarchy() {
        PreferenceScreen root = getPreferenceScreen();
        if (root != null) {
            root.removeAll();
        }
        addPreferencesFromResource(R.xml.location_mode);
        root = getPreferenceScreen();
        this.mHighAccuracy = (RadioButtonPreference) root.findPreference("high_accuracy");
        this.mBatterySaving = (RadioButtonPreference) root.findPreference("battery_saving");
        this.mSensorsOnly = (RadioButtonPreference) root.findPreference("sensors_only");
        this.mHighAccuracy.setOnClickListener(this);
        this.mBatterySaving.setOnClickListener(this);
        this.mSensorsOnly.setOnClickListener(this);
        refreshLocationMode();
        return root;
    }

    private void updateRadioButtons(RadioButtonPreference activated) {
        if (activated == null) {
            this.mHighAccuracy.setChecked(false);
            this.mBatterySaving.setChecked(false);
            this.mSensorsOnly.setChecked(false);
        } else if (activated == this.mHighAccuracy) {
            this.mHighAccuracy.setChecked(true);
            this.mBatterySaving.setChecked(false);
            this.mSensorsOnly.setChecked(false);
        } else if (activated == this.mBatterySaving) {
            this.mHighAccuracy.setChecked(false);
            this.mBatterySaving.setChecked(true);
            this.mSensorsOnly.setChecked(false);
        } else if (activated == this.mSensorsOnly) {
            this.mHighAccuracy.setChecked(false);
            this.mBatterySaving.setChecked(false);
            this.mSensorsOnly.setChecked(true);
        }
    }

    public void onRadioButtonClicked(RadioButtonPreference emiter) {
        int mode = 0;
        if (emiter == this.mHighAccuracy) {
            mode = 3;
        } else if (emiter == this.mBatterySaving) {
            mode = 2;
        } else if (emiter == this.mSensorsOnly) {
            mode = 1;
        }
        setLocationMode(mode);
    }

    public void onModeChanged(int mode, boolean restricted) {
        switch (mode) {
            case 0:
                updateRadioButtons(null);
                break;
            case 1:
                updateRadioButtons(this.mSensorsOnly);
                break;
            case 2:
                updateRadioButtons(this.mBatterySaving);
                break;
            case 3:
                updateRadioButtons(this.mHighAccuracy);
                break;
        }
        boolean enabled = (mode == 0 || restricted) ? false : true;
        this.mHighAccuracy.setEnabled(enabled);
        this.mBatterySaving.setEnabled(enabled);
        this.mSensorsOnly.setEnabled(enabled);
    }

    public int getHelpResource() {
        return R.string.help_url_location_access;
    }
}
