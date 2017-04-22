package com.android.settings.inputmethod;

import android.app.Activity;
import android.app.Fragment;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.database.ContentObserver;
import android.hardware.input.InputDeviceIdentifier;
import android.hardware.input.InputManager;
import android.hardware.input.InputManager.InputDeviceListener;
import android.hardware.input.KeyboardLayout;
import android.os.Bundle;
import android.os.Handler;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceCategory;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.provider.Settings.Secure;
import android.provider.Settings.System;
import android.speech.tts.TtsEngines;
import android.text.TextUtils;
import android.view.InputDevice;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.InputMethodSubtype;
import android.view.textservice.TextServicesManager;
import com.android.internal.app.LocalePicker;
import com.android.internal.app.LocalePicker.LocaleInfo;
import com.android.settings.R;
import com.android.settings.Settings.KeyboardLayoutPickerActivity;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.SubSettings;
import com.android.settings.UserDictionarySettings;
import com.android.settings.Utils;
import com.android.settings.VoiceInputOutputSettings;
import com.android.settings.inputmethod.KeyboardLayoutDialogFragment.OnSetupKeyboardLayoutsListener;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settings.search.Indexable;
import com.android.settings.search.Indexable.SearchIndexProvider;
import com.android.settings.search.SearchIndexableRaw;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;

public class InputMethodAndLanguageSettings extends SettingsPreferenceFragment implements InputDeviceListener, OnPreferenceChangeListener, OnSavePreferenceListener, OnSetupKeyboardLayoutsListener, Indexable {
    public static final SearchIndexProvider SEARCH_INDEX_DATA_PROVIDER = new BaseSearchIndexProvider() {
        public List<SearchIndexableRaw> getRawDataToIndex(Context context, boolean enabled) {
            SearchIndexableRaw indexable;
            List<SearchIndexableRaw> indexables = new ArrayList();
            String screenTitle = context.getString(R.string.language_keyboard_settings_title);
            if (context.getAssets().getLocales().length > 1) {
                String localeName = InputMethodAndLanguageSettings.getLocaleName(context);
                indexable = new SearchIndexableRaw(context);
                indexable.key = "phone_language";
                indexable.title = context.getString(R.string.phone_language);
                indexable.summaryOn = localeName;
                indexable.summaryOff = localeName;
                indexable.screenTitle = screenTitle;
                indexables.add(indexable);
            }
            indexable = new SearchIndexableRaw(context);
            indexable.key = "spellcheckers_settings";
            indexable.title = context.getString(R.string.spellcheckers_settings_title);
            indexable.screenTitle = screenTitle;
            indexables.add(indexable);
            if (UserDictionaryList.getUserDictionaryLocalesSet(context) != null) {
                indexable = new SearchIndexableRaw(context);
                indexable.key = "user_dict_settings";
                indexable.title = context.getString(R.string.user_dict_settings_title);
                indexable.screenTitle = screenTitle;
                indexables.add(indexable);
            }
            indexable = new SearchIndexableRaw(context);
            indexable.key = "keyboard_settings";
            indexable.title = context.getString(R.string.keyboard_settings_category);
            indexable.screenTitle = screenTitle;
            indexables.add(indexable);
            InputMethodSettingValuesWrapper immValues = InputMethodSettingValuesWrapper.getInstance(context);
            immValues.refreshAllInputMethodAndSubtypes();
            String currImeName = immValues.getCurrentInputMethodName(context).toString();
            indexable = new SearchIndexableRaw(context);
            indexable.key = "current_input_method";
            indexable.title = context.getString(R.string.current_input_method);
            indexable.summaryOn = currImeName;
            indexable.summaryOff = currImeName;
            indexable.screenTitle = screenTitle;
            indexables.add(indexable);
            InputMethodManager inputMethodManager = (InputMethodManager) context.getSystemService("input_method");
            List<InputMethodInfo> inputMethods = immValues.getInputMethodList();
            int inputMethodCount = inputMethods == null ? 0 : inputMethods.size();
            for (int i = 0; i < inputMethodCount; i++) {
                InputMethodInfo inputMethod = (InputMethodInfo) inputMethods.get(i);
                StringBuilder builder = new StringBuilder();
                List<InputMethodSubtype> subtypes = inputMethodManager.getEnabledInputMethodSubtypeList(inputMethod, true);
                int subtypeCount = subtypes.size();
                for (int j = 0; j < subtypeCount; j++) {
                    InputMethodSubtype subtype = (InputMethodSubtype) subtypes.get(j);
                    if (builder.length() > 0) {
                        builder.append(',');
                    }
                    builder.append(subtype.getDisplayName(context, inputMethod.getPackageName(), inputMethod.getServiceInfo().applicationInfo));
                }
                String summary = builder.toString();
                ServiceInfo serviceInfo = inputMethod.getServiceInfo();
                ComponentName componentName = new ComponentName(serviceInfo.packageName, serviceInfo.name);
                indexable = new SearchIndexableRaw(context);
                indexable.key = componentName.flattenToString();
                indexable.title = inputMethod.loadLabel(context.getPackageManager()).toString();
                indexable.summaryOn = summary;
                indexable.summaryOff = summary;
                indexable.screenTitle = screenTitle;
                indexables.add(indexable);
            }
            InputManager inputManager = (InputManager) context.getSystemService("input");
            boolean hasHardKeyboards = false;
            int[] devices = InputDevice.getDeviceIds();
            for (int device : devices) {
                InputDevice device2 = InputDevice.getDevice(device);
                if (!(device2 == null || device2.isVirtual() || !device2.isFullKeyboard())) {
                    hasHardKeyboards = true;
                    String keyboardLayoutDescriptor = inputManager.getCurrentKeyboardLayoutForInputDevice(device2.getIdentifier());
                    KeyboardLayout keyboardLayout = keyboardLayoutDescriptor != null ? inputManager.getKeyboardLayout(keyboardLayoutDescriptor) : null;
                    if (keyboardLayout != null) {
                        summary = keyboardLayout.toString();
                    } else {
                        summary = context.getString(R.string.keyboard_layout_default_label);
                    }
                    indexable = new SearchIndexableRaw(context);
                    indexable.key = device2.getName();
                    indexable.title = device2.getName();
                    indexable.summaryOn = summary;
                    indexable.summaryOff = summary;
                    indexable.screenTitle = screenTitle;
                    indexables.add(indexable);
                }
            }
            if (hasHardKeyboards) {
                indexable = new SearchIndexableRaw(context);
                indexable.key = "builtin_keyboard_settings";
                indexable.title = context.getString(R.string.builtin_keyboard_settings_title);
                indexable.screenTitle = screenTitle;
                indexables.add(indexable);
            }
            indexable = new SearchIndexableRaw(context);
            indexable.key = "voice_input_settings";
            indexable.title = context.getString(R.string.voice_input_settings);
            indexable.screenTitle = screenTitle;
            indexables.add(indexable);
            if (!new TtsEngines(context).getEngines().isEmpty()) {
                indexable = new SearchIndexableRaw(context);
                indexable.key = "tts_settings";
                indexable.title = context.getString(R.string.tts_settings_title);
                indexable.screenTitle = screenTitle;
                indexables.add(indexable);
            }
            indexable = new SearchIndexableRaw(context);
            indexable.key = "pointer_settings_category";
            indexable.title = context.getString(R.string.pointer_settings_category);
            indexable.screenTitle = screenTitle;
            indexables.add(indexable);
            indexable = new SearchIndexableRaw(context);
            indexable.key = "pointer_speed";
            indexable.title = context.getString(R.string.pointer_speed);
            indexable.screenTitle = screenTitle;
            indexables.add(indexable);
            if (InputMethodAndLanguageSettings.haveInputDeviceWithVibrator()) {
                indexable = new SearchIndexableRaw(context);
                indexable.key = "vibrate_input_devices";
                indexable.title = context.getString(R.string.vibrate_input_devices);
                indexable.summaryOn = context.getString(R.string.vibrate_input_devices_summary);
                indexable.summaryOff = context.getString(R.string.vibrate_input_devices_summary);
                indexable.screenTitle = screenTitle;
                indexables.add(indexable);
            }
            return indexables;
        }
    };
    private int mDefaultInputMethodSelectorVisibility = 0;
    private DevicePolicyManager mDpm;
    private PreferenceCategory mGameControllerCategory;
    private Handler mHandler;
    private PreferenceCategory mHardKeyboardCategory;
    private final ArrayList<PreferenceScreen> mHardKeyboardPreferenceList = new ArrayList();
    private InputManager mIm;
    private InputMethodManager mImm;
    private final ArrayList<InputMethodPreference> mInputMethodPreferenceList = new ArrayList();
    private InputMethodSettingValuesWrapper mInputMethodSettingValues;
    private Intent mIntentWaitingForResult;
    private PreferenceCategory mKeyboardSettingsCategory;
    private Preference mLanguagePref;
    private SettingsObserver mSettingsObserver;
    private boolean mShowsOnlyFullImeAndKeyboardList;

    private class SettingsObserver extends ContentObserver {
        private Context mContext;

        public SettingsObserver(Handler handler, Context context) {
            super(handler);
            this.mContext = context;
        }

        public void onChange(boolean selfChange) {
            InputMethodAndLanguageSettings.this.updateCurrentImeName();
        }

        public void resume() {
            ContentResolver cr = this.mContext.getContentResolver();
            cr.registerContentObserver(Secure.getUriFor("default_input_method"), false, this);
            cr.registerContentObserver(Secure.getUriFor("selected_input_method_subtype"), false, this);
        }

        public void pause() {
            this.mContext.getContentResolver().unregisterContentObserver(this);
        }
    }

    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        addPreferencesFromResource(R.xml.language_settings);
        Activity activity = getActivity();
        this.mImm = (InputMethodManager) getSystemService("input_method");
        this.mInputMethodSettingValues = InputMethodSettingValuesWrapper.getInstance(activity);
        try {
            this.mDefaultInputMethodSelectorVisibility = Integer.valueOf(getString(R.string.input_method_selector_visibility_default_value)).intValue();
        } catch (NumberFormatException e) {
        }
        if (activity.getAssets().getLocales().length == 1) {
            getPreferenceScreen().removePreference(findPreference("phone_language"));
        } else {
            this.mLanguagePref = findPreference("phone_language");
        }
        new VoiceInputOutputSettings(this).onCreate();
        this.mHardKeyboardCategory = (PreferenceCategory) findPreference("hard_keyboard");
        this.mKeyboardSettingsCategory = (PreferenceCategory) findPreference("keyboard_settings_category");
        this.mGameControllerCategory = (PreferenceCategory) findPreference("game_controller_settings_category");
        Intent startingIntent = activity.getIntent();
        this.mShowsOnlyFullImeAndKeyboardList = "android.settings.INPUT_METHOD_SETTINGS".equals(startingIntent.getAction());
        if (this.mShowsOnlyFullImeAndKeyboardList) {
            getPreferenceScreen().removeAll();
            getPreferenceScreen().addPreference(this.mHardKeyboardCategory);
            this.mKeyboardSettingsCategory.removeAll();
            getPreferenceScreen().addPreference(this.mKeyboardSettingsCategory);
        }
        this.mIm = (InputManager) activity.getSystemService("input");
        updateInputDevices();
        Preference spellChecker = findPreference("spellcheckers_settings");
        if (spellChecker != null) {
            InputMethodAndSubtypeUtil.removeUnnecessaryNonPersistentPreference(spellChecker);
            Intent intent = new Intent("android.intent.action.MAIN");
            intent.setClass(activity, SubSettings.class);
            intent.putExtra(":settings:show_fragment", SpellCheckersSettings.class.getName());
            intent.putExtra(":settings:show_fragment_title_resid", R.string.spellcheckers_settings_title);
            spellChecker.setIntent(intent);
        }
        this.mHandler = new Handler();
        this.mSettingsObserver = new SettingsObserver(this.mHandler, activity);
        this.mDpm = (DevicePolicyManager) getActivity().getSystemService("device_policy");
        InputDeviceIdentifier identifier = (InputDeviceIdentifier) startingIntent.getParcelableExtra("input_device_identifier");
        if (this.mShowsOnlyFullImeAndKeyboardList && identifier != null) {
            showKeyboardLayoutDialog(identifier);
        }
    }

    private void updateUserDictionaryPreference(Preference userDictionaryPreference) {
        final TreeSet<String> localeSet = UserDictionaryList.getUserDictionaryLocalesSet(getActivity());
        if (localeSet == null) {
            getPreferenceScreen().removePreference(userDictionaryPreference);
        } else {
            userDictionaryPreference.setOnPreferenceClickListener(new OnPreferenceClickListener() {
                public boolean onPreferenceClick(Preference arg0) {
                    Class<? extends Fragment> targetFragment;
                    Bundle extras = new Bundle();
                    if (localeSet.size() <= 1) {
                        if (!localeSet.isEmpty()) {
                            extras.putString("locale", (String) localeSet.first());
                        }
                        targetFragment = UserDictionarySettings.class;
                    } else {
                        targetFragment = UserDictionaryList.class;
                    }
                    InputMethodAndLanguageSettings.this.startFragment(InputMethodAndLanguageSettings.this, targetFragment.getCanonicalName(), -1, -1, extras);
                    return true;
                }
            });
        }
    }

    public void onResume() {
        super.onResume();
        this.mSettingsObserver.resume();
        this.mIm.registerInputDeviceListener(this, null);
        Preference spellChecker = findPreference("spellcheckers_settings");
        if (spellChecker != null) {
            TextServicesManager tsm = (TextServicesManager) getSystemService("textservices");
            if (tsm.isSpellCheckerEnabled()) {
                spellChecker.setSummary(tsm.getCurrentSpellChecker().loadLabel(getPackageManager()));
            } else {
                spellChecker.setSummary(R.string.switch_off_text);
            }
        }
        if (!this.mShowsOnlyFullImeAndKeyboardList) {
            if (this.mLanguagePref != null) {
                this.mLanguagePref.setSummary(getLocaleName(getActivity()));
            }
            updateUserDictionaryPreference(findPreference("key_user_dictionary_settings"));
        }
        updateInputDevices();
        this.mInputMethodSettingValues.refreshAllInputMethodAndSubtypes();
        updateInputMethodPreferenceViews();
    }

    public void onPause() {
        super.onPause();
        this.mIm.unregisterInputDeviceListener(this);
        this.mSettingsObserver.pause();
        InputMethodAndSubtypeUtil.saveInputMethodSubtypeList(this, getContentResolver(), this.mInputMethodSettingValues.getInputMethodList(), !this.mHardKeyboardPreferenceList.isEmpty());
    }

    public void onInputDeviceAdded(int deviceId) {
        updateInputDevices();
    }

    public void onInputDeviceChanged(int deviceId) {
        updateInputDevices();
    }

    public void onInputDeviceRemoved(int deviceId) {
        updateInputDevices();
    }

    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        int i = 0;
        if (Utils.isMonkeyRunning()) {
            return false;
        }
        if (preference instanceof PreferenceScreen) {
            if (preference.getFragment() == null && "current_input_method".equals(preference.getKey())) {
                ((InputMethodManager) getSystemService("input_method")).showInputMethodPicker();
            }
        } else if (preference instanceof CheckBoxPreference) {
            Preference chkPref = (CheckBoxPreference) preference;
            if (chkPref == this.mGameControllerCategory.findPreference("vibrate_input_devices")) {
                ContentResolver contentResolver = getContentResolver();
                String str = "vibrate_input_devices";
                if (chkPref.isChecked()) {
                    i = 1;
                }
                System.putInt(contentResolver, str, i);
                return true;
            }
        }
        return super.onPreferenceTreeClick(preferenceScreen, preference);
    }

    private static String getLocaleName(Context context) {
        Locale currentLocale = context.getResources().getConfiguration().locale;
        for (LocaleInfo locale : LocalePicker.getAllAssetLocales(context, true)) {
            if (locale.getLocale().equals(currentLocale)) {
                return locale.getLabel();
            }
        }
        return currentLocale.getDisplayName(currentLocale);
    }

    public boolean onPreferenceChange(Preference preference, Object value) {
        return false;
    }

    private void updateInputMethodPreferenceViews() {
        synchronized (this.mInputMethodPreferenceList) {
            int i;
            Iterator i$ = this.mInputMethodPreferenceList.iterator();
            while (i$.hasNext()) {
                this.mKeyboardSettingsCategory.removePreference((InputMethodPreference) i$.next());
            }
            this.mInputMethodPreferenceList.clear();
            List<String> permittedList = this.mDpm.getPermittedInputMethodsForCurrentUser();
            Context context = getActivity();
            List<InputMethodInfo> imis = this.mShowsOnlyFullImeAndKeyboardList ? this.mInputMethodSettingValues.getInputMethodList() : this.mImm.getEnabledInputMethodList();
            int N = imis == null ? 0 : imis.size();
            for (i = 0; i < N; i++) {
                boolean isAllowedByOrganization;
                InputMethodInfo imi = (InputMethodInfo) imis.get(i);
                if (permittedList == null || permittedList.contains(imi.getPackageName())) {
                    isAllowedByOrganization = true;
                } else {
                    isAllowedByOrganization = false;
                }
                this.mInputMethodPreferenceList.add(new InputMethodPreference(context, imi, this.mShowsOnlyFullImeAndKeyboardList, isAllowedByOrganization, this));
            }
            final Collator collator = Collator.getInstance();
            Collections.sort(this.mInputMethodPreferenceList, new Comparator<InputMethodPreference>() {
                public int compare(InputMethodPreference lhs, InputMethodPreference rhs) {
                    return lhs.compareTo(rhs, collator);
                }
            });
            for (i = 0; i < N; i++) {
                InputMethodPreference pref = (InputMethodPreference) this.mInputMethodPreferenceList.get(i);
                this.mKeyboardSettingsCategory.addPreference(pref);
                InputMethodAndSubtypeUtil.removeUnnecessaryNonPersistentPreference(pref);
                pref.updatePreferenceViews();
            }
        }
        updateCurrentImeName();
        InputMethodAndSubtypeUtil.loadInputMethodSubtypeList(this, getContentResolver(), this.mInputMethodSettingValues.getInputMethodList(), null);
    }

    public void onSaveInputMethodPreference(InputMethodPreference pref) {
        InputMethodInfo imi = pref.getInputMethodInfo();
        if (!pref.isChecked()) {
            saveEnabledSubtypesOf(imi);
        }
        InputMethodAndSubtypeUtil.saveInputMethodSubtypeList(this, getContentResolver(), this.mImm.getInputMethodList(), getResources().getConfiguration().keyboard == 2);
        this.mInputMethodSettingValues.refreshAllInputMethodAndSubtypes();
        if (pref.isChecked()) {
            restorePreviouslyEnabledSubtypesOf(imi);
        }
        Iterator i$ = this.mInputMethodPreferenceList.iterator();
        while (i$.hasNext()) {
            ((InputMethodPreference) i$.next()).updatePreferenceViews();
        }
    }

    private void saveEnabledSubtypesOf(InputMethodInfo imi) {
        HashSet<String> enabledSubtypeIdSet = new HashSet();
        for (InputMethodSubtype subtype : this.mImm.getEnabledInputMethodSubtypeList(imi, true)) {
            enabledSubtypeIdSet.add(Integer.toString(subtype.hashCode()));
        }
        HashMap<String, HashSet<String>> imeToEnabledSubtypeIdsMap = loadPreviouslyEnabledSubtypeIdsMap();
        imeToEnabledSubtypeIdsMap.put(imi.getId(), enabledSubtypeIdSet);
        savePreviouslyEnabledSubtypeIdsMap(imeToEnabledSubtypeIdsMap);
    }

    private void restorePreviouslyEnabledSubtypesOf(InputMethodInfo imi) {
        HashMap<String, HashSet<String>> imeToEnabledSubtypeIdsMap = loadPreviouslyEnabledSubtypeIdsMap();
        String imiId = imi.getId();
        HashSet<String> enabledSubtypeIdSet = (HashSet) imeToEnabledSubtypeIdsMap.remove(imiId);
        if (enabledSubtypeIdSet != null) {
            savePreviouslyEnabledSubtypeIdsMap(imeToEnabledSubtypeIdsMap);
            InputMethodAndSubtypeUtil.enableInputMethodSubtypesOf(getContentResolver(), imiId, enabledSubtypeIdSet);
        }
    }

    private HashMap<String, HashSet<String>> loadPreviouslyEnabledSubtypeIdsMap() {
        return InputMethodAndSubtypeUtil.parseInputMethodsAndSubtypesString(PreferenceManager.getDefaultSharedPreferences(getActivity()).getString("previously_enabled_subtypes", null));
    }

    private void savePreviouslyEnabledSubtypeIdsMap(HashMap<String, HashSet<String>> subtypesMap) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
        prefs.edit().putString("previously_enabled_subtypes", InputMethodAndSubtypeUtil.buildInputMethodsAndSubtypesString(subtypesMap)).apply();
    }

    private void updateCurrentImeName() {
        Context context = getActivity();
        if (context != null && this.mImm != null) {
            Preference curPref = getPreferenceScreen().findPreference("current_input_method");
            if (curPref != null) {
                CharSequence curIme = this.mInputMethodSettingValues.getCurrentInputMethodName(context);
                if (!TextUtils.isEmpty(curIme)) {
                    synchronized (this) {
                        curPref.setSummary(curIme);
                    }
                }
            }
        }
    }

    private void updateInputDevices() {
        updateHardKeyboards();
        updateGameControllers();
    }

    private void updateHardKeyboards() {
        int i;
        this.mHardKeyboardPreferenceList.clear();
        int[] devices = InputDevice.getDeviceIds();
        for (int device : devices) {
            InputDevice device2 = InputDevice.getDevice(device);
            if (!(device2 == null || device2.isVirtual() || !device2.isFullKeyboard())) {
                KeyboardLayout keyboardLayout;
                final InputDeviceIdentifier identifier = device2.getIdentifier();
                String keyboardLayoutDescriptor = this.mIm.getCurrentKeyboardLayoutForInputDevice(identifier);
                if (keyboardLayoutDescriptor != null) {
                    keyboardLayout = this.mIm.getKeyboardLayout(keyboardLayoutDescriptor);
                } else {
                    keyboardLayout = null;
                }
                PreferenceScreen pref = new PreferenceScreen(getActivity(), null);
                pref.setTitle(device2.getName());
                if (keyboardLayout != null) {
                    pref.setSummary(keyboardLayout.toString());
                } else {
                    pref.setSummary(R.string.keyboard_layout_default_label);
                }
                pref.setOnPreferenceClickListener(new OnPreferenceClickListener() {
                    public boolean onPreferenceClick(Preference preference) {
                        InputMethodAndLanguageSettings.this.showKeyboardLayoutDialog(identifier);
                        return true;
                    }
                });
                this.mHardKeyboardPreferenceList.add(pref);
            }
        }
        if (this.mHardKeyboardPreferenceList.isEmpty()) {
            getPreferenceScreen().removePreference(this.mHardKeyboardCategory);
            return;
        }
        int i2 = this.mHardKeyboardCategory.getPreferenceCount();
        while (true) {
            i = i2 - 1;
            if (i2 <= 0) {
                break;
            }
            Preference pref2 = this.mHardKeyboardCategory.getPreference(i);
            if (pref2.getOrder() < 1000) {
                this.mHardKeyboardCategory.removePreference(pref2);
            }
            i2 = i;
        }
        Collections.sort(this.mHardKeyboardPreferenceList);
        int count = this.mHardKeyboardPreferenceList.size();
        for (i = 0; i < count; i++) {
            pref2 = (Preference) this.mHardKeyboardPreferenceList.get(i);
            pref2.setOrder(i);
            this.mHardKeyboardCategory.addPreference(pref2);
        }
        getPreferenceScreen().addPreference(this.mHardKeyboardCategory);
    }

    private void showKeyboardLayoutDialog(InputDeviceIdentifier inputDeviceIdentifier) {
        KeyboardLayoutDialogFragment fragment = new KeyboardLayoutDialogFragment(inputDeviceIdentifier);
        fragment.setTargetFragment(this, 0);
        fragment.show(getActivity().getFragmentManager(), "keyboardLayout");
    }

    public void onSetupKeyboardLayouts(InputDeviceIdentifier inputDeviceIdentifier) {
        Intent intent = new Intent("android.intent.action.MAIN");
        intent.setClass(getActivity(), KeyboardLayoutPickerActivity.class);
        intent.putExtra("input_device_identifier", inputDeviceIdentifier);
        this.mIntentWaitingForResult = intent;
        startActivityForResult(intent, 0);
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (this.mIntentWaitingForResult != null) {
            InputDeviceIdentifier inputDeviceIdentifier = (InputDeviceIdentifier) this.mIntentWaitingForResult.getParcelableExtra("input_device_identifier");
            this.mIntentWaitingForResult = null;
            showKeyboardLayoutDialog(inputDeviceIdentifier);
        }
    }

    private void updateGameControllers() {
        boolean z = true;
        if (haveInputDeviceWithVibrator()) {
            getPreferenceScreen().addPreference(this.mGameControllerCategory);
            CheckBoxPreference chkPref = (CheckBoxPreference) this.mGameControllerCategory.findPreference("vibrate_input_devices");
            if (System.getInt(getContentResolver(), "vibrate_input_devices", 1) <= 0) {
                z = false;
            }
            chkPref.setChecked(z);
            return;
        }
        getPreferenceScreen().removePreference(this.mGameControllerCategory);
    }

    private static boolean haveInputDeviceWithVibrator() {
        int[] devices = InputDevice.getDeviceIds();
        for (int device : devices) {
            InputDevice device2 = InputDevice.getDevice(device);
            if (device2 != null && !device2.isVirtual() && device2.getVibrator().hasVibrator()) {
                return true;
            }
        }
        return false;
    }
}
