package com.android.settings.inputmethod;

import android.content.Context;
import android.preference.Preference;
import android.text.TextUtils;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodSubtype;
import com.android.internal.inputmethod.InputMethodUtils;
import java.text.Collator;
import java.util.Locale;

class InputMethodSubtypePreference extends SwitchWithNoTextPreference {
    private final boolean mIsSystemLanguage;
    private final boolean mIsSystemLocale;

    InputMethodSubtypePreference(Context context, InputMethodSubtype subtype, InputMethodInfo imi) {
        boolean z = false;
        super(context);
        setPersistent(false);
        setKey(imi.getId() + subtype.hashCode());
        setTitle(subtype.getDisplayName(context, imi.getPackageName(), imi.getServiceInfo().applicationInfo));
        String subtypeLocaleString = subtype.getLocale();
        if (TextUtils.isEmpty(subtypeLocaleString)) {
            this.mIsSystemLocale = false;
            this.mIsSystemLanguage = false;
            return;
        }
        Locale systemLocale = context.getResources().getConfiguration().locale;
        this.mIsSystemLocale = subtypeLocaleString.equals(systemLocale.toString());
        if (this.mIsSystemLocale || InputMethodUtils.getLanguageFromLocaleString(subtypeLocaleString).equals(systemLocale.getLanguage())) {
            z = true;
        }
        this.mIsSystemLanguage = z;
    }

    int compareTo(Preference rhs, Collator collator) {
        if (this == rhs) {
            return 0;
        }
        if (!(rhs instanceof InputMethodSubtypePreference)) {
            return super.compareTo(rhs);
        }
        InputMethodSubtypePreference pref = (InputMethodSubtypePreference) rhs;
        CharSequence t0 = getTitle();
        CharSequence t1 = rhs.getTitle();
        if (TextUtils.equals(t0, t1)) {
            return 0;
        }
        if (this.mIsSystemLocale) {
            return -1;
        }
        if (pref.mIsSystemLocale) {
            return 1;
        }
        if (this.mIsSystemLanguage) {
            return -1;
        }
        if (pref.mIsSystemLanguage) {
            return 1;
        }
        if (TextUtils.isEmpty(t0)) {
            return 1;
        }
        if (TextUtils.isEmpty(t1)) {
            return -1;
        }
        return collator.compare(t0.toString(), t1.toString());
    }
}
