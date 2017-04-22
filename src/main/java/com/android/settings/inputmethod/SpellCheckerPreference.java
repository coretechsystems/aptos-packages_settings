package com.android.settings.inputmethod;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.preference.Preference;
import android.text.TextUtils;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.textservice.SpellCheckerInfo;
import android.widget.RadioButton;
import android.widget.Toast;
import com.android.settings.R;

class SpellCheckerPreference extends Preference implements OnClickListener {
    private final OnRadioButtonPreferenceListener mOnRadioButtonListener;
    private View mPrefLeftButton;
    private RadioButton mRadioButton;
    private final SpellCheckerInfo mSci;
    private boolean mSelected;
    private View mSettingsButton;

    interface OnRadioButtonPreferenceListener {
        void onRadioButtonClicked(SpellCheckerPreference spellCheckerPreference);
    }

    public SpellCheckerPreference(Context context, SpellCheckerInfo sci, OnRadioButtonPreferenceListener onRadioButtonListener) {
        super(context, null, 0);
        setPersistent(false);
        setLayoutResource(R.layout.preference_spellchecker);
        setWidgetLayoutResource(R.layout.preference_spellchecker_widget);
        this.mSci = sci;
        this.mOnRadioButtonListener = onRadioButtonListener;
        setKey(sci.getId());
        setTitle(sci.loadLabel(context.getPackageManager()));
        String settingsActivity = this.mSci.getSettingsActivity();
        if (TextUtils.isEmpty(settingsActivity)) {
            setIntent(null);
            return;
        }
        Intent intent = new Intent("android.intent.action.MAIN");
        intent.setClassName(this.mSci.getPackageName(), settingsActivity);
        setIntent(intent);
    }

    protected void onBindView(View view) {
        super.onBindView(view);
        this.mRadioButton = (RadioButton) view.findViewById(R.id.pref_radio);
        this.mPrefLeftButton = view.findViewById(R.id.pref_left_button);
        this.mPrefLeftButton.setOnClickListener(this);
        this.mSettingsButton = view.findViewById(R.id.pref_right_button);
        this.mSettingsButton.setOnClickListener(this);
        updateSelectedState(this.mSelected);
    }

    public void onClick(View v) {
        if (v == this.mPrefLeftButton) {
            this.mOnRadioButtonListener.onRadioButtonClicked(this);
        } else if (v == this.mSettingsButton) {
            onSettingsButtonClicked();
        }
    }

    private void onSettingsButtonClicked() {
        Context context = getContext();
        try {
            Intent intent = getIntent();
            if (intent != null) {
                context.startActivity(intent);
            }
        } catch (ActivityNotFoundException e) {
            Toast.makeText(context, context.getString(R.string.failed_to_open_app_settings_toast, new Object[]{this.mSci.loadLabel(context.getPackageManager())}), 1).show();
        }
    }

    public SpellCheckerInfo getSpellCheckerInfo() {
        return this.mSci;
    }

    public void setSelected(boolean selected) {
        this.mSelected = selected;
        updateSelectedState(selected);
    }

    private void updateSelectedState(boolean selected) {
        if (this.mRadioButton != null) {
            this.mRadioButton.setChecked(selected);
            boolean z = isEnabled() && selected;
            enableSettingsButton(z);
        }
    }

    private void enableSettingsButton(boolean enabled) {
        if (this.mSettingsButton != null) {
            if (getIntent() == null) {
                this.mSettingsButton.setVisibility(8);
                return;
            }
            this.mSettingsButton.setEnabled(enabled);
            this.mSettingsButton.setClickable(enabled);
            this.mSettingsButton.setFocusable(enabled);
            if (!enabled) {
                this.mSettingsButton.setAlpha(0.4f);
            }
        }
    }
}
