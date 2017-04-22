package com.android.settings.accessibility;

import android.content.Intent;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceScreen;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.widget.TextView;
import com.android.settings.R;
import com.android.settings.SettingsActivity;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.widget.SwitchBar;
import com.android.settings.widget.ToggleSwitch;

public abstract class ToggleFeaturePreferenceFragment extends SettingsPreferenceFragment {
    protected String mPreferenceKey;
    protected Intent mSettingsIntent;
    protected CharSequence mSettingsTitle;
    protected Preference mSummaryPreference;
    protected SwitchBar mSwitchBar;
    protected ToggleSwitch mToggleSwitch;

    protected abstract void onPreferenceToggled(String str, boolean z);

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        PreferenceScreen preferenceScreen = getPreferenceManager().createPreferenceScreen(getActivity());
        setPreferenceScreen(preferenceScreen);
        this.mSummaryPreference = new Preference(getActivity()) {
            protected void onBindView(View view) {
                super.onBindView(view);
                TextView summaryView = (TextView) view.findViewById(16908304);
                summaryView.setText(getSummary());
                sendAccessibilityEvent(summaryView);
            }

            private void sendAccessibilityEvent(View view) {
                AccessibilityManager accessibilityManager = AccessibilityManager.getInstance(ToggleFeaturePreferenceFragment.this.getActivity());
                if (accessibilityManager.isEnabled()) {
                    AccessibilityEvent event = AccessibilityEvent.obtain();
                    event.setEventType(8);
                    view.onInitializeAccessibilityEvent(event);
                    view.dispatchPopulateAccessibilityEvent(event);
                    accessibilityManager.sendAccessibilityEvent(event);
                }
            }
        };
        this.mSummaryPreference.setPersistent(false);
        this.mSummaryPreference.setLayoutResource(R.layout.text_description_preference);
        preferenceScreen.addPreference(this.mSummaryPreference);
    }

    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        this.mSwitchBar = ((SettingsActivity) getActivity()).getSwitchBar();
        this.mToggleSwitch = this.mSwitchBar.getSwitch();
        onProcessArguments(getArguments());
    }

    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        installActionBarToggleSwitch();
    }

    public void onDestroyView() {
        super.onDestroyView();
        removeActionBarToggleSwitch();
    }

    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        MenuItem menuItem = menu.add(this.mSettingsTitle);
        menuItem.setShowAsAction(1);
        menuItem.setIntent(this.mSettingsIntent);
    }

    protected void onInstallSwitchBarToggleSwitch() {
    }

    protected void onRemoveSwitchBarToggleSwitch() {
    }

    private void installActionBarToggleSwitch() {
        this.mSwitchBar.show();
        onInstallSwitchBarToggleSwitch();
    }

    private void removeActionBarToggleSwitch() {
        this.mToggleSwitch.setOnBeforeCheckedChangeListener(null);
        onRemoveSwitchBarToggleSwitch();
        this.mSwitchBar.hide();
    }

    public void setTitle(String title) {
        getActivity().setTitle(title);
    }

    protected void onProcessArguments(Bundle arguments) {
        if (arguments == null) {
            getPreferenceScreen().removePreference(this.mSummaryPreference);
            return;
        }
        this.mPreferenceKey = arguments.getString("preference_key");
        if (arguments.containsKey("checked")) {
            this.mSwitchBar.setCheckedInternal(arguments.getBoolean("checked"));
        }
        if (arguments.containsKey("title")) {
            setTitle(arguments.getString("title"));
        }
        if (arguments.containsKey("summary")) {
            this.mSummaryPreference.setSummary(arguments.getCharSequence("summary"));
            getListView().setSelector(new ColorDrawable(0));
            getListView().setDivider(null);
            return;
        }
        getPreferenceScreen().removePreference(this.mSummaryPreference);
    }
}
