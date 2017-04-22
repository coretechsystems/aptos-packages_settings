package com.android.settings.inputmethod;

import android.content.ContentResolver;
import android.content.SharedPreferences;
import android.preference.Preference;
import android.preference.PreferenceScreen;
import android.preference.TwoStatePreference;
import android.provider.Settings.Secure;
import android.provider.Settings.SettingNotFoundException;
import android.text.TextUtils;
import android.text.TextUtils.SimpleStringSplitter;
import android.view.inputmethod.InputMethodInfo;
import com.android.settings.SettingsPreferenceFragment;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

class InputMethodAndSubtypeUtil {
    private static final SimpleStringSplitter sStringInputMethodSplitter = new SimpleStringSplitter(':');
    private static final SimpleStringSplitter sStringInputMethodSubtypeSplitter = new SimpleStringSplitter(';');

    InputMethodAndSubtypeUtil() {
    }

    static String buildInputMethodsAndSubtypesString(HashMap<String, HashSet<String>> imeToSubtypesMap) {
        StringBuilder builder = new StringBuilder();
        for (String imi : imeToSubtypesMap.keySet()) {
            if (builder.length() > 0) {
                builder.append(':');
            }
            HashSet<String> subtypeIdSet = (HashSet) imeToSubtypesMap.get(imi);
            builder.append(imi);
            Iterator i$ = subtypeIdSet.iterator();
            while (i$.hasNext()) {
                builder.append(';').append((String) i$.next());
            }
        }
        return builder.toString();
    }

    private static String buildInputMethodsString(HashSet<String> imiList) {
        StringBuilder builder = new StringBuilder();
        Iterator i$ = imiList.iterator();
        while (i$.hasNext()) {
            String imi = (String) i$.next();
            if (builder.length() > 0) {
                builder.append(':');
            }
            builder.append(imi);
        }
        return builder.toString();
    }

    private static int getInputMethodSubtypeSelected(ContentResolver resolver) {
        try {
            return Secure.getInt(resolver, "selected_input_method_subtype");
        } catch (SettingNotFoundException e) {
            return -1;
        }
    }

    private static boolean isInputMethodSubtypeSelected(ContentResolver resolver) {
        return getInputMethodSubtypeSelected(resolver) != -1;
    }

    private static void putSelectedInputMethodSubtype(ContentResolver resolver, int hashCode) {
        Secure.putInt(resolver, "selected_input_method_subtype", hashCode);
    }

    private static HashMap<String, HashSet<String>> getEnabledInputMethodsAndSubtypeList(ContentResolver resolver) {
        return parseInputMethodsAndSubtypesString(Secure.getString(resolver, "enabled_input_methods"));
    }

    static HashMap<String, HashSet<String>> parseInputMethodsAndSubtypesString(String inputMethodsAndSubtypesString) {
        HashMap<String, HashSet<String>> subtypesMap = new HashMap();
        if (!TextUtils.isEmpty(inputMethodsAndSubtypesString)) {
            sStringInputMethodSplitter.setString(inputMethodsAndSubtypesString);
            while (sStringInputMethodSplitter.hasNext()) {
                sStringInputMethodSubtypeSplitter.setString(sStringInputMethodSplitter.next());
                if (sStringInputMethodSubtypeSplitter.hasNext()) {
                    HashSet<String> subtypeIdSet = new HashSet();
                    String imiId = sStringInputMethodSubtypeSplitter.next();
                    while (sStringInputMethodSubtypeSplitter.hasNext()) {
                        subtypeIdSet.add(sStringInputMethodSubtypeSplitter.next());
                    }
                    subtypesMap.put(imiId, subtypeIdSet);
                }
            }
        }
        return subtypesMap;
    }

    static void enableInputMethodSubtypesOf(ContentResolver resolver, String imiId, HashSet<String> enabledSubtypeIdSet) {
        HashMap<String, HashSet<String>> enabledImeAndSubtypeIdsMap = getEnabledInputMethodsAndSubtypeList(resolver);
        enabledImeAndSubtypeIdsMap.put(imiId, enabledSubtypeIdSet);
        Secure.putString(resolver, "enabled_input_methods", buildInputMethodsAndSubtypesString(enabledImeAndSubtypeIdsMap));
    }

    private static HashSet<String> getDisabledSystemIMEs(ContentResolver resolver) {
        HashSet<String> set = new HashSet();
        String disabledIMEsStr = Secure.getString(resolver, "disabled_system_input_methods");
        if (!TextUtils.isEmpty(disabledIMEsStr)) {
            sStringInputMethodSplitter.setString(disabledIMEsStr);
            while (sStringInputMethodSplitter.hasNext()) {
                set.add(sStringInputMethodSplitter.next());
            }
        }
        return set;
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    static void saveInputMethodSubtypeList(com.android.settings.SettingsPreferenceFragment r25, android.content.ContentResolver r26, java.util.List<android.view.inputmethod.InputMethodInfo> r27, boolean r28) {
        /*
        r23 = "default_input_method";
        r0 = r26;
        r1 = r23;
        r2 = android.provider.Settings.Secure.getString(r0, r1);
        r15 = getInputMethodSubtypeSelected(r26);
        r5 = getEnabledInputMethodsAndSubtypeList(r26);
        r3 = getDisabledSystemIMEs(r26);
        r13 = 0;
        r8 = r27.iterator();
    L_0x001b:
        r23 = r8.hasNext();
        if (r23 == 0) goto L_0x0100;
    L_0x0021:
        r9 = r8.next();
        r9 = (android.view.inputmethod.InputMethodInfo) r9;
        r10 = r9.getId();
        r0 = r25;
        r14 = r0.findPreference(r10);
        if (r14 == 0) goto L_0x001b;
    L_0x0033:
        r0 = r14 instanceof android.preference.TwoStatePreference;
        r23 = r0;
        if (r23 == 0) goto L_0x00b4;
    L_0x0039:
        r14 = (android.preference.TwoStatePreference) r14;
        r12 = r14.isChecked();
    L_0x003f:
        r11 = r10.equals(r2);
        r22 = com.android.internal.inputmethod.InputMethodUtils.isSystemIme(r9);
        if (r28 != 0) goto L_0x005f;
    L_0x0049:
        r23 = r25.getActivity();
        r23 = com.android.settings.inputmethod.InputMethodSettingValuesWrapper.getInstance(r23);
        r24 = r25.getActivity();
        r0 = r23;
        r1 = r24;
        r23 = r0.isAlwaysCheckedIme(r9, r1);
        if (r23 != 0) goto L_0x0061;
    L_0x005f:
        if (r12 == 0) goto L_0x00e2;
    L_0x0061:
        r23 = r5.containsKey(r10);
        if (r23 != 0) goto L_0x0071;
    L_0x0067:
        r23 = new java.util.HashSet;
        r23.<init>();
        r0 = r23;
        r5.put(r10, r0);
    L_0x0071:
        r21 = r5.get(r10);
        r21 = (java.util.HashSet) r21;
        r20 = 0;
        r17 = r9.getSubtypeCount();
        r7 = 0;
    L_0x007e:
        r0 = r17;
        if (r7 >= r0) goto L_0x00e8;
    L_0x0082:
        r16 = r9.getSubtypeAt(r7);
        r23 = r16.hashCode();
        r18 = java.lang.String.valueOf(r23);
        r23 = new java.lang.StringBuilder;
        r23.<init>();
        r0 = r23;
        r23 = r0.append(r10);
        r0 = r23;
        r1 = r18;
        r23 = r0.append(r1);
        r23 = r23.toString();
        r0 = r25;
        r1 = r23;
        r19 = r0.findPreference(r1);
        r19 = (android.preference.TwoStatePreference) r19;
        if (r19 != 0) goto L_0x00b9;
    L_0x00b1:
        r7 = r7 + 1;
        goto L_0x007e;
    L_0x00b4:
        r12 = r5.containsKey(r10);
        goto L_0x003f;
    L_0x00b9:
        if (r20 != 0) goto L_0x00c1;
    L_0x00bb:
        r21.clear();
        r13 = 1;
        r20 = 1;
    L_0x00c1:
        r23 = r19.isChecked();
        if (r23 == 0) goto L_0x00da;
    L_0x00c7:
        r0 = r21;
        r1 = r18;
        r0.add(r1);
        if (r11 == 0) goto L_0x00b1;
    L_0x00d0:
        r23 = r16.hashCode();
        r0 = r23;
        if (r15 != r0) goto L_0x00b1;
    L_0x00d8:
        r13 = 0;
        goto L_0x00b1;
    L_0x00da:
        r0 = r21;
        r1 = r18;
        r0.remove(r1);
        goto L_0x00b1;
    L_0x00e2:
        r5.remove(r10);
        if (r11 == 0) goto L_0x00e8;
    L_0x00e7:
        r2 = 0;
    L_0x00e8:
        if (r22 == 0) goto L_0x001b;
    L_0x00ea:
        if (r28 == 0) goto L_0x001b;
    L_0x00ec:
        r23 = r3.contains(r10);
        if (r23 == 0) goto L_0x00f9;
    L_0x00f2:
        if (r12 == 0) goto L_0x001b;
    L_0x00f4:
        r3.remove(r10);
        goto L_0x001b;
    L_0x00f9:
        if (r12 != 0) goto L_0x001b;
    L_0x00fb:
        r3.add(r10);
        goto L_0x001b;
    L_0x0100:
        r6 = buildInputMethodsAndSubtypesString(r5);
        r4 = buildInputMethodsString(r3);
        if (r13 != 0) goto L_0x0110;
    L_0x010a:
        r23 = isInputMethodSubtypeSelected(r26);
        if (r23 != 0) goto L_0x0119;
    L_0x0110:
        r23 = -1;
        r0 = r26;
        r1 = r23;
        putSelectedInputMethodSubtype(r0, r1);
    L_0x0119:
        r23 = "enabled_input_methods";
        r0 = r26;
        r1 = r23;
        android.provider.Settings.Secure.putString(r0, r1, r6);
        r23 = r4.length();
        if (r23 <= 0) goto L_0x0131;
    L_0x0128:
        r23 = "disabled_system_input_methods";
        r0 = r26;
        r1 = r23;
        android.provider.Settings.Secure.putString(r0, r1, r4);
    L_0x0131:
        r23 = "default_input_method";
        if (r2 == 0) goto L_0x013d;
    L_0x0135:
        r0 = r26;
        r1 = r23;
        android.provider.Settings.Secure.putString(r0, r1, r2);
        return;
    L_0x013d:
        r2 = "";
        goto L_0x0135;
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.settings.inputmethod.InputMethodAndSubtypeUtil.saveInputMethodSubtypeList(com.android.settings.SettingsPreferenceFragment, android.content.ContentResolver, java.util.List, boolean):void");
    }

    static void loadInputMethodSubtypeList(SettingsPreferenceFragment context, ContentResolver resolver, List<InputMethodInfo> inputMethodInfos, Map<String, List<Preference>> inputMethodPrefsMap) {
        HashMap<String, HashSet<String>> enabledSubtypes = getEnabledInputMethodsAndSubtypeList(resolver);
        for (InputMethodInfo imi : inputMethodInfos) {
            String imiId = imi.getId();
            Preference pref = context.findPreference(imiId);
            if (pref instanceof TwoStatePreference) {
                TwoStatePreference subtypePref = (TwoStatePreference) pref;
                boolean isEnabled = enabledSubtypes.containsKey(imiId);
                subtypePref.setChecked(isEnabled);
                if (inputMethodPrefsMap != null) {
                    for (Preference childPref : (List) inputMethodPrefsMap.get(imiId)) {
                        childPref.setEnabled(isEnabled);
                    }
                }
                setSubtypesPreferenceEnabled(context, inputMethodInfos, imiId, isEnabled);
            }
        }
        updateSubtypesPreferenceChecked(context, inputMethodInfos, enabledSubtypes);
    }

    static void setSubtypesPreferenceEnabled(SettingsPreferenceFragment context, List<InputMethodInfo> inputMethodProperties, String id, boolean enabled) {
        PreferenceScreen preferenceScreen = context.getPreferenceScreen();
        for (InputMethodInfo imi : inputMethodProperties) {
            if (id.equals(imi.getId())) {
                int subtypeCount = imi.getSubtypeCount();
                for (int i = 0; i < subtypeCount; i++) {
                    TwoStatePreference pref = (TwoStatePreference) preferenceScreen.findPreference(id + imi.getSubtypeAt(i).hashCode());
                    if (pref != null) {
                        pref.setEnabled(enabled);
                    }
                }
            }
        }
    }

    private static void updateSubtypesPreferenceChecked(SettingsPreferenceFragment context, List<InputMethodInfo> inputMethodProperties, HashMap<String, HashSet<String>> enabledSubtypes) {
        PreferenceScreen preferenceScreen = context.getPreferenceScreen();
        for (InputMethodInfo imi : inputMethodProperties) {
            String id = imi.getId();
            if (enabledSubtypes.containsKey(id)) {
                HashSet<String> enabledSubtypesSet = (HashSet) enabledSubtypes.get(id);
                int subtypeCount = imi.getSubtypeCount();
                for (int i = 0; i < subtypeCount; i++) {
                    String hashCode = String.valueOf(imi.getSubtypeAt(i).hashCode());
                    TwoStatePreference pref = (TwoStatePreference) preferenceScreen.findPreference(id + hashCode);
                    if (pref != null) {
                        pref.setChecked(enabledSubtypesSet.contains(hashCode));
                    }
                }
            }
        }
    }

    static void removeUnnecessaryNonPersistentPreference(Preference pref) {
        String key = pref.getKey();
        if (!pref.isPersistent() && key != null) {
            SharedPreferences prefs = pref.getSharedPreferences();
            if (prefs != null && prefs.contains(key)) {
                prefs.edit().remove(key).apply();
            }
        }
    }
}
