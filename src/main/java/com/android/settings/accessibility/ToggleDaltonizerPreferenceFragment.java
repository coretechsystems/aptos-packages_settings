package com.android.settings.accessibility;

import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.provider.Settings.Secure;
import android.view.View;
import android.widget.Switch;
import com.android.settings.R;
import com.android.settings.widget.SwitchBar;
import com.android.settings.widget.SwitchBar.OnSwitchChangeListener;

public class ToggleDaltonizerPreferenceFragment extends ToggleFeaturePreferenceFragment implements OnPreferenceChangeListener, OnSwitchChangeListener {
    private ListPreference mType;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.accessibility_daltonizer_settings);
        this.mType = (ListPreference) findPreference("type");
        initPreferences();
    }

    protected void onPreferenceToggled(String preferenceKey, boolean enabled) {
        Secure.putInt(getContentResolver(), "accessibility_display_daltonizer_enabled", enabled ? 1 : 0);
    }

    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (preference == this.mType) {
            Secure.putInt(getContentResolver(), "accessibility_display_daltonizer", Integer.parseInt((String) newValue));
            preference.setSummary("%s");
        }
        return true;
    }

    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setTitle(getString(R.string.accessibility_display_daltonizer_preference_title));
    }

    protected void onInstallSwitchBarToggleSwitch() {
        boolean z = true;
        super.onInstallSwitchBarToggleSwitch();
        SwitchBar switchBar = this.mSwitchBar;
        if (Secure.getInt(getContentResolver(), "accessibility_display_daltonizer_enabled", 0) != 1) {
            z = false;
        }
        switchBar.setCheckedInternal(z);
        this.mSwitchBar.addOnSwitchChangeListener(this);
    }

    protected void onRemoveSwitchBarToggleSwitch() {
        super.onRemoveSwitchBarToggleSwitch();
        this.mSwitchBar.removeOnSwitchChangeListener(this);
    }

    private void initPreferences() {
        String value = Integer.toString(Secure.getInt(getContentResolver(), "accessibility_display_daltonizer", 12));
        this.mType.setValue(value);
        this.mType.setOnPreferenceChangeListener(this);
        if (this.mType.findIndexOfValue(value) < 0) {
            this.mType.setSummary(getString(R.string.daltonizer_type_overridden, new Object[]{getString(R.string.simulate_color_space)}));
        }
    }

    public void onSwitchChanged(Switch switchView, boolean isChecked) {
        onPreferenceToggled(this.mPreferenceKey, isChecked);
    }
}
