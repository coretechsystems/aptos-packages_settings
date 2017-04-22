package com.android.settings.inputmethod;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceGroup;
import android.provider.UserDictionary.Words;
import android.text.TextUtils;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.InputMethodSubtype;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.UserDictionarySettings;
import com.android.settings.Utils;
import java.util.Iterator;
import java.util.Locale;
import java.util.TreeSet;

public class UserDictionaryList extends SettingsPreferenceFragment {
    private String mLocale;

    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        setPreferenceScreen(getPreferenceManager().createPreferenceScreen(getActivity()));
        getActivity().getActionBar().setTitle(R.string.user_dict_settings_title);
    }

    public void onActivityCreated(Bundle savedInstanceState) {
        String locale;
        super.onActivityCreated(savedInstanceState);
        Intent intent = getActivity().getIntent();
        String localeFromIntent = intent == null ? null : intent.getStringExtra("locale");
        Bundle arguments = getArguments();
        String localeFromArguments = arguments == null ? null : arguments.getString("locale");
        if (localeFromArguments != null) {
            locale = localeFromArguments;
        } else if (localeFromIntent != null) {
            locale = localeFromIntent;
        } else {
            locale = null;
        }
        this.mLocale = locale;
    }

    public static TreeSet<String> getUserDictionaryLocalesSet(Context context) {
        Cursor cursor = context.getContentResolver().query(Words.CONTENT_URI, new String[]{"locale"}, null, null, null);
        TreeSet<String> localeSet = new TreeSet();
        if (cursor == null) {
            return null;
        }
        try {
            String locale;
            if (cursor.moveToFirst()) {
                int columnIndex = cursor.getColumnIndex("locale");
                do {
                    locale = cursor.getString(columnIndex);
                    if (locale == null) {
                        locale = "";
                    }
                    localeSet.add(locale);
                } while (cursor.moveToNext());
            }
            cursor.close();
            InputMethodManager imm = (InputMethodManager) context.getSystemService("input_method");
            for (InputMethodInfo imi : imm.getEnabledInputMethodList()) {
                for (InputMethodSubtype subtype : imm.getEnabledInputMethodSubtypeList(imi, true)) {
                    locale = subtype.getLocale();
                    if (!TextUtils.isEmpty(locale)) {
                        localeSet.add(locale);
                    }
                }
            }
            if (localeSet.contains(Locale.getDefault().getLanguage().toString())) {
                return localeSet;
            }
            localeSet.add(Locale.getDefault().toString());
            return localeSet;
        } catch (Throwable th) {
            cursor.close();
        }
    }

    protected void createUserDictSettings(PreferenceGroup userDictGroup) {
        Activity activity = getActivity();
        userDictGroup.removeAll();
        TreeSet<String> localeSet = getUserDictionaryLocalesSet(activity);
        if (this.mLocale != null) {
            localeSet.add(this.mLocale);
        }
        if (localeSet.size() > 1) {
            localeSet.add("");
        }
        if (localeSet.isEmpty()) {
            userDictGroup.addPreference(createUserDictionaryPreference(null, activity));
            return;
        }
        Iterator i$ = localeSet.iterator();
        while (i$.hasNext()) {
            userDictGroup.addPreference(createUserDictionaryPreference((String) i$.next(), activity));
        }
    }

    protected Preference createUserDictionaryPreference(String locale, Activity activity) {
        Preference newPref = new Preference(getActivity());
        Intent intent = new Intent("android.settings.USER_DICTIONARY_SETTINGS");
        if (locale == null) {
            newPref.setTitle(Locale.getDefault().getDisplayName());
        } else {
            if ("".equals(locale)) {
                newPref.setTitle(getString(R.string.user_dict_settings_all_languages));
            } else {
                newPref.setTitle(Utils.createLocaleFromString(locale).getDisplayName());
            }
            intent.putExtra("locale", locale);
            newPref.getExtras().putString("locale", locale);
        }
        newPref.setIntent(intent);
        newPref.setFragment(UserDictionarySettings.class.getName());
        return newPref;
    }

    public void onResume() {
        super.onResume();
        createUserDictSettings(getPreferenceScreen());
    }
}
