package com.android.settings.inputmethod;

import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.SwitchPreference;
import android.text.TextUtils;
import android.util.Log;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.InputMethodSubtype;
import android.widget.Toast;
import com.android.internal.inputmethod.InputMethodUtils;
import com.android.settings.R;
import java.text.Collator;
import java.util.ArrayList;
import java.util.List;

class InputMethodPreference extends SwitchPreference implements OnPreferenceChangeListener, OnPreferenceClickListener {
    private static final String TAG = InputMethodPreference.class.getSimpleName();
    private AlertDialog mDialog = null;
    private final boolean mHasPriorityInSorting;
    private final InputMethodInfo mImi;
    private final InputMethodSettingValuesWrapper mInputMethodSettingValues;
    private final boolean mIsAllowedByOrganization;
    private final OnSavePreferenceListener mOnSaveListener;

    interface OnSavePreferenceListener {
        void onSaveInputMethodPreference(InputMethodPreference inputMethodPreference);
    }

    InputMethodPreference(Context context, InputMethodInfo imi, boolean isImeEnabler, boolean isAllowedByOrganization, OnSavePreferenceListener onSaveListener) {
        boolean z = false;
        super(context);
        setPersistent(false);
        this.mImi = imi;
        this.mIsAllowedByOrganization = isAllowedByOrganization;
        this.mOnSaveListener = onSaveListener;
        if (!isImeEnabler) {
            setWidgetLayoutResource(0);
        }
        setSwitchTextOn("");
        setSwitchTextOff("");
        setKey(imi.getId());
        setTitle(imi.loadLabel(context.getPackageManager()));
        String settingsActivity = imi.getSettingsActivity();
        if (TextUtils.isEmpty(settingsActivity)) {
            setIntent(null);
        } else {
            Intent intent = new Intent("android.intent.action.MAIN");
            intent.setClassName(imi.getPackageName(), settingsActivity);
            setIntent(intent);
        }
        this.mInputMethodSettingValues = InputMethodSettingValuesWrapper.getInstance(context);
        if (InputMethodUtils.isSystemIme(imi) && this.mInputMethodSettingValues.isValidSystemNonAuxAsciiCapableIme(imi, context)) {
            z = true;
        }
        this.mHasPriorityInSorting = z;
        setOnPreferenceClickListener(this);
        setOnPreferenceChangeListener(this);
    }

    public InputMethodInfo getInputMethodInfo() {
        return this.mImi;
    }

    private boolean isImeEnabler() {
        return getWidgetLayoutResource() != 0;
    }

    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (isImeEnabler()) {
            if (isChecked()) {
                setChecked(false);
                this.mOnSaveListener.onSaveInputMethodPreference(this);
            } else if (InputMethodUtils.isSystemIme(this.mImi)) {
                setChecked(true);
                this.mOnSaveListener.onSaveInputMethodPreference(this);
            } else {
                showSecurityWarnDialog(this.mImi);
            }
        }
        return false;
    }

    public boolean onPreferenceClick(Preference preference) {
        if (!isImeEnabler()) {
            Context context = getContext();
            try {
                Intent intent = getIntent();
                if (intent != null) {
                    context.startActivity(intent);
                }
            } catch (ActivityNotFoundException e) {
                Log.d(TAG, "IME's Settings Activity Not Found", e);
                Toast.makeText(context, context.getString(R.string.failed_to_open_app_settings_toast, new Object[]{this.mImi.loadLabel(context.getPackageManager())}), 1).show();
            }
        }
        return true;
    }

    void updatePreferenceViews() {
        boolean z = !(this.mInputMethodSettingValues.isAlwaysCheckedIme(this.mImi, getContext()) && isImeEnabler()) && this.mIsAllowedByOrganization;
        setEnabled(z);
        setChecked(this.mInputMethodSettingValues.isEnabledImi(this.mImi));
        setSummary(getSummaryString());
    }

    private InputMethodManager getInputMethodManager() {
        return (InputMethodManager) getContext().getSystemService("input_method");
    }

    private String getSummaryString() {
        Context context = getContext();
        if (!this.mIsAllowedByOrganization) {
            return context.getString(R.string.accessibility_feature_or_input_method_not_allowed);
        }
        List<InputMethodSubtype> subtypes = getInputMethodManager().getEnabledInputMethodSubtypeList(this.mImi, true);
        ArrayList<CharSequence> subtypeLabels = new ArrayList();
        for (InputMethodSubtype subtype : subtypes) {
            subtypeLabels.add(subtype.getDisplayName(context, this.mImi.getPackageName(), this.mImi.getServiceInfo().applicationInfo));
        }
        return TextUtils.join(", ", subtypeLabels);
    }

    private void showSecurityWarnDialog(InputMethodInfo imi) {
        if (this.mDialog != null && this.mDialog.isShowing()) {
            this.mDialog.dismiss();
        }
        Context context = getContext();
        Builder builder = new Builder(context);
        builder.setCancelable(true);
        builder.setTitle(17039380);
        CharSequence label = imi.getServiceInfo().applicationInfo.loadLabel(context.getPackageManager());
        builder.setMessage(context.getString(R.string.ime_security_warning, new Object[]{label}));
        builder.setPositiveButton(17039370, new OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                InputMethodPreference.this.setChecked(true);
                InputMethodPreference.this.mOnSaveListener.onSaveInputMethodPreference(InputMethodPreference.this);
                InputMethodPreference.this.notifyChanged();
            }
        });
        builder.setNegativeButton(17039360, new OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                InputMethodPreference.this.setChecked(false);
                InputMethodPreference.this.mOnSaveListener.onSaveInputMethodPreference(InputMethodPreference.this);
                InputMethodPreference.this.notifyChanged();
            }
        });
        this.mDialog = builder.create();
        this.mDialog.show();
    }

    int compareTo(InputMethodPreference rhs, Collator collator) {
        if (this == rhs) {
            return 0;
        }
        if (this.mHasPriorityInSorting == rhs.mHasPriorityInSorting) {
            CharSequence t0 = getTitle();
            CharSequence t1 = rhs.getTitle();
            if (TextUtils.isEmpty(t0)) {
                return 1;
            }
            if (TextUtils.isEmpty(t1)) {
                return -1;
            }
            return collator.compare(t0.toString(), t1.toString());
        } else if (this.mHasPriorityInSorting) {
            return -1;
        } else {
            return 1;
        }
    }
}
