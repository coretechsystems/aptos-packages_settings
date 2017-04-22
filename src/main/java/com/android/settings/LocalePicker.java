package com.android.settings;

import android.app.Dialog;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import com.android.internal.app.LocalePicker.LocaleSelectionListener;
import com.android.settings.SettingsPreferenceFragment.SettingsDialogFragment;
import java.util.Locale;

public class LocalePicker extends com.android.internal.app.LocalePicker implements LocaleSelectionListener, DialogCreatable {
    private SettingsDialogFragment mDialogFragment;
    private Locale mTargetLocale;

    public LocalePicker() {
        setLocaleSelectionListener(this);
    }

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null && savedInstanceState.containsKey("locale")) {
            this.mTargetLocale = new Locale(savedInstanceState.getString("locale"));
        }
    }

    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = super.onCreateView(inflater, container, savedInstanceState);
        Utils.forcePrepareCustomPreferencesList(container, view, (ListView) view.findViewById(16908298), false);
        return view;
    }

    public void onLocaleSelected(Locale locale) {
        if (Utils.hasMultipleUsers(getActivity())) {
            this.mTargetLocale = locale;
            showDialog(1);
            return;
        }
        getActivity().onBackPressed();
        updateLocale(locale);
    }

    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (this.mTargetLocale != null) {
            outState.putString("locale", this.mTargetLocale.toString());
        }
    }

    protected void showDialog(int dialogId) {
        if (this.mDialogFragment != null) {
            Log.e("LocalePicker", "Old dialog fragment not null!");
        }
        this.mDialogFragment = new SettingsDialogFragment(this, dialogId);
        this.mDialogFragment.show(getActivity().getFragmentManager(), Integer.toString(dialogId));
    }

    public Dialog onCreateDialog(final int dialogId) {
        return Utils.buildGlobalChangeWarningDialog(getActivity(), R.string.global_locale_change_title, new Runnable() {
            public void run() {
                LocalePicker.this.removeDialog(dialogId);
                LocalePicker.this.getActivity().onBackPressed();
                LocalePicker.updateLocale(LocalePicker.this.mTargetLocale);
            }
        });
    }

    protected void removeDialog(int dialogId) {
        if (this.mDialogFragment != null && this.mDialogFragment.getDialogId() == dialogId) {
            this.mDialogFragment.dismiss();
        }
        this.mDialogFragment = null;
    }
}
