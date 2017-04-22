package com.android.settings.inputmethod;

import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceScreen;
import android.view.textservice.SpellCheckerInfo;
import android.view.textservice.SpellCheckerSubtype;
import android.view.textservice.TextServicesManager;
import android.widget.Switch;
import com.android.settings.R;
import com.android.settings.SettingsActivity;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.widget.SwitchBar;
import com.android.settings.widget.SwitchBar.OnSwitchChangeListener;

public class SpellCheckersSettings extends SettingsPreferenceFragment implements OnPreferenceClickListener, OnRadioButtonPreferenceListener, OnSwitchChangeListener {
    private static final String TAG = SpellCheckersSettings.class.getSimpleName();
    private SpellCheckerInfo mCurrentSci;
    private AlertDialog mDialog = null;
    private SpellCheckerInfo[] mEnabledScis;
    private Preference mSpellCheckerLanaguagePref;
    private SwitchBar mSwitchBar;
    private TextServicesManager mTsm;

    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        addPreferencesFromResource(R.xml.spellchecker_prefs);
        this.mSpellCheckerLanaguagePref = findPreference("spellchecker_language");
        this.mSpellCheckerLanaguagePref.setOnPreferenceClickListener(this);
        this.mTsm = (TextServicesManager) getSystemService("textservices");
        this.mCurrentSci = this.mTsm.getCurrentSpellChecker();
        this.mEnabledScis = this.mTsm.getEnabledSpellCheckers();
        populatePreferenceScreen();
    }

    private void populatePreferenceScreen() {
        PreferenceScreen screen = getPreferenceScreen();
        Context context = getActivity();
        int count = this.mEnabledScis == null ? 0 : this.mEnabledScis.length;
        for (int index = 0; index < count; index++) {
            SpellCheckerPreference pref = new SpellCheckerPreference(context, this.mEnabledScis[index], this);
            screen.addPreference(pref);
            InputMethodAndSubtypeUtil.removeUnnecessaryNonPersistentPreference(pref);
        }
    }

    public void onResume() {
        super.onResume();
        this.mSwitchBar = ((SettingsActivity) getActivity()).getSwitchBar();
        this.mSwitchBar.show();
        this.mSwitchBar.addOnSwitchChangeListener(this);
        updatePreferenceScreen();
    }

    public void onPause() {
        super.onPause();
        this.mSwitchBar.removeOnSwitchChangeListener(this);
    }

    public void onSwitchChanged(Switch switchView, boolean isChecked) {
        this.mTsm.setSpellCheckerEnabled(isChecked);
        updatePreferenceScreen();
    }

    private void updatePreferenceScreen() {
        this.mCurrentSci = this.mTsm.getCurrentSpellChecker();
        boolean isSpellCheckerEnabled = this.mTsm.isSpellCheckerEnabled();
        this.mSwitchBar.setChecked(isSpellCheckerEnabled);
        this.mSpellCheckerLanaguagePref.setSummary(getSpellCheckerSubtypeLabel(this.mCurrentSci, this.mTsm.getCurrentSpellCheckerSubtype(false)));
        PreferenceScreen screen = getPreferenceScreen();
        int count = screen.getPreferenceCount();
        for (int index = 0; index < count; index++) {
            Preference preference = screen.getPreference(index);
            preference.setEnabled(isSpellCheckerEnabled);
            if (preference instanceof SpellCheckerPreference) {
                SpellCheckerPreference pref = (SpellCheckerPreference) preference;
                boolean z = this.mCurrentSci != null && this.mCurrentSci.getId().equals(pref.getSpellCheckerInfo().getId());
                pref.setSelected(z);
            }
        }
    }

    private CharSequence getSpellCheckerSubtypeLabel(SpellCheckerInfo sci, SpellCheckerSubtype subtype) {
        if (sci == null) {
            return null;
        }
        if (subtype == null) {
            return getString(R.string.use_system_language_to_select_input_method_subtypes);
        }
        return subtype.getDisplayName(getActivity(), sci.getPackageName(), sci.getServiceInfo().applicationInfo);
    }

    public boolean onPreferenceClick(Preference pref) {
        if (pref != this.mSpellCheckerLanaguagePref) {
            return false;
        }
        showChooseLanguageDialog();
        return true;
    }

    public void onRadioButtonClicked(SpellCheckerPreference pref) {
        SpellCheckerInfo sci = pref.getSpellCheckerInfo();
        if ((sci.getServiceInfo().applicationInfo.flags & 1) != 0) {
            changeCurrentSpellChecker(sci);
        } else {
            showSecurityWarnDialog(pref);
        }
    }

    private static int convertSubtypeIndexToDialogItemId(int index) {
        return index + 1;
    }

    private static int convertDialogItemIdToSubtypeIndex(int item) {
        return item - 1;
    }

    private void showChooseLanguageDialog() {
        if (this.mDialog != null && this.mDialog.isShowing()) {
            this.mDialog.dismiss();
        }
        final SpellCheckerInfo currentSci = this.mTsm.getCurrentSpellChecker();
        SpellCheckerSubtype currentScs = this.mTsm.getCurrentSpellCheckerSubtype(false);
        Builder builder = new Builder(getActivity());
        builder.setTitle(R.string.phone_language);
        int subtypeCount = currentSci.getSubtypeCount();
        CharSequence[] items = new CharSequence[(subtypeCount + 1)];
        items[0] = getSpellCheckerSubtypeLabel(currentSci, null);
        int checkedItemId = 0;
        for (int index = 0; index < subtypeCount; index++) {
            SpellCheckerSubtype subtype = currentSci.getSubtypeAt(index);
            int itemId = convertSubtypeIndexToDialogItemId(index);
            items[itemId] = getSpellCheckerSubtypeLabel(currentSci, subtype);
            if (subtype.equals(currentScs)) {
                checkedItemId = itemId;
            }
        }
        builder.setSingleChoiceItems(items, checkedItemId, new OnClickListener() {
            public void onClick(DialogInterface dialog, int item) {
                if (item == 0) {
                    SpellCheckersSettings.this.mTsm.setSpellCheckerSubtype(null);
                } else {
                    SpellCheckersSettings.this.mTsm.setSpellCheckerSubtype(currentSci.getSubtypeAt(SpellCheckersSettings.convertDialogItemIdToSubtypeIndex(item)));
                }
                dialog.dismiss();
                SpellCheckersSettings.this.updatePreferenceScreen();
            }
        });
        this.mDialog = builder.create();
        this.mDialog.show();
    }

    private void showSecurityWarnDialog(SpellCheckerPreference pref) {
        if (this.mDialog != null && this.mDialog.isShowing()) {
            this.mDialog.dismiss();
        }
        final SpellCheckerInfo sci = pref.getSpellCheckerInfo();
        Builder builder = new Builder(getActivity());
        builder.setTitle(17039380);
        builder.setMessage(getString(R.string.spellchecker_security_warning, new Object[]{pref.getTitle()}));
        builder.setCancelable(true);
        builder.setPositiveButton(17039370, new OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                SpellCheckersSettings.this.changeCurrentSpellChecker(sci);
            }
        });
        builder.setNegativeButton(17039360, new OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
            }
        });
        this.mDialog = builder.create();
        this.mDialog.show();
    }

    private void changeCurrentSpellChecker(SpellCheckerInfo sci) {
        this.mTsm.setCurrentSpellChecker(sci);
        updatePreferenceScreen();
    }
}
