package com.android.settings.tts;

import android.app.AlertDialog.Builder;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceCategory;
import android.provider.Settings.Secure;
import android.provider.Settings.SettingNotFoundException;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TextToSpeech.EngineInfo;
import android.speech.tts.TextToSpeech.OnInitListener;
import android.speech.tts.TtsEngines;
import android.speech.tts.UtteranceProgressListener;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Checkable;
import com.android.settings.R;
import com.android.settings.SettingsActivity;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.tts.TtsEnginePreference.RadioButtonGroupState;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.Objects;
import java.util.Set;

public class TextToSpeechSettings extends SettingsPreferenceFragment implements OnPreferenceChangeListener, OnPreferenceClickListener, RadioButtonGroupState {
    private List<String> mAvailableStrLocals;
    private Checkable mCurrentChecked;
    private Locale mCurrentDefaultLocale;
    private String mCurrentEngine;
    private int mDefaultRate = 100;
    private ListPreference mDefaultRatePref;
    private PreferenceCategory mEnginePreferenceCategory;
    private Preference mEngineStatus;
    private TtsEngines mEnginesHelper = null;
    private final OnInitListener mInitListener = new OnInitListener() {
        public void onInit(int status) {
            TextToSpeechSettings.this.onInitEngine(status);
        }
    };
    private Preference mPlayExample;
    private String mPreviousEngine;
    private String mSampleText = null;
    private TextToSpeech mTts = null;
    private final OnInitListener mUpdateListener = new OnInitListener() {
        public void onInit(int status) {
            TextToSpeechSettings.this.onUpdateEngine(status);
        }
    };

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.tts_settings);
        getActivity().setVolumeControlStream(3);
        this.mPlayExample = findPreference("tts_play_example");
        this.mPlayExample.setOnPreferenceClickListener(this);
        this.mPlayExample.setEnabled(false);
        this.mEnginePreferenceCategory = (PreferenceCategory) findPreference("tts_engine_preference_section");
        this.mDefaultRatePref = (ListPreference) findPreference("tts_default_rate");
        this.mEngineStatus = findPreference("tts_status");
        updateEngineStatus(R.string.tts_status_checking);
        this.mTts = new TextToSpeech(getActivity().getApplicationContext(), this.mInitListener);
        this.mEnginesHelper = new TtsEngines(getActivity().getApplicationContext());
        setTtsUtteranceProgressListener();
        initSettings();
        setRetainInstance(true);
    }

    public void onResume() {
        super.onResume();
        if (this.mTts != null && this.mCurrentDefaultLocale != null) {
            Locale ttsDefaultLocale = this.mTts.getDefaultLanguage();
            if (this.mCurrentDefaultLocale != null && !this.mCurrentDefaultLocale.equals(ttsDefaultLocale)) {
                updateWidgetState(false);
                checkDefaultLocale();
            }
        }
    }

    private void setTtsUtteranceProgressListener() {
        if (this.mTts != null) {
            this.mTts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                public void onStart(String utteranceId) {
                }

                public void onDone(String utteranceId) {
                }

                public void onError(String utteranceId) {
                    Log.e("TextToSpeechSettings", "Error while trying to synthesize sample text");
                }
            });
        }
    }

    public void onDestroy() {
        super.onDestroy();
        if (this.mTts != null) {
            this.mTts.shutdown();
            this.mTts = null;
        }
    }

    private void initSettings() {
        try {
            this.mDefaultRate = Secure.getInt(getContentResolver(), "tts_default_rate");
        } catch (SettingNotFoundException e) {
            this.mDefaultRate = 100;
        }
        this.mDefaultRatePref.setValue(String.valueOf(this.mDefaultRate));
        this.mDefaultRatePref.setOnPreferenceChangeListener(this);
        this.mCurrentEngine = this.mTts.getCurrentEngine();
        if (getActivity() instanceof SettingsActivity) {
            SettingsActivity activity = (SettingsActivity) getActivity();
            this.mEnginePreferenceCategory.removeAll();
            for (EngineInfo engine : this.mEnginesHelper.getEngines()) {
                this.mEnginePreferenceCategory.addPreference(new TtsEnginePreference(getActivity(), engine, this, activity));
            }
            checkVoiceData(this.mCurrentEngine);
            return;
        }
        throw new IllegalStateException("TextToSpeechSettings used outside a Settings");
    }

    public void onInitEngine(int status) {
        if (status == 0) {
            checkDefaultLocale();
        } else {
            updateWidgetState(false);
        }
    }

    private void checkDefaultLocale() {
        Locale defaultLocale = this.mTts.getDefaultLanguage();
        if (defaultLocale == null) {
            Log.e("TextToSpeechSettings", "Failed to get default language from engine " + this.mCurrentEngine);
            updateWidgetState(false);
            updateEngineStatus(R.string.tts_status_not_supported);
            return;
        }
        Locale oldDefaultLocale = this.mCurrentDefaultLocale;
        this.mCurrentDefaultLocale = this.mEnginesHelper.parseLocaleString(defaultLocale.toString());
        if (!Objects.equals(oldDefaultLocale, this.mCurrentDefaultLocale)) {
            this.mSampleText = null;
        }
        int defaultAvailable = this.mTts.setLanguage(defaultLocale);
        if (evaluateDefaultLocale() && this.mSampleText == null) {
            getSampleText();
        }
    }

    private boolean evaluateDefaultLocale() {
        if (this.mCurrentDefaultLocale == null || this.mAvailableStrLocals == null) {
            return false;
        }
        boolean notInAvailableLangauges = true;
        try {
            String defaultLocaleStr = this.mCurrentDefaultLocale.getISO3Language();
            if (!TextUtils.isEmpty(this.mCurrentDefaultLocale.getISO3Country())) {
                defaultLocaleStr = defaultLocaleStr + "-" + this.mCurrentDefaultLocale.getISO3Country();
            }
            if (!TextUtils.isEmpty(this.mCurrentDefaultLocale.getVariant())) {
                defaultLocaleStr = defaultLocaleStr + "-" + this.mCurrentDefaultLocale.getVariant();
            }
            for (String loc : this.mAvailableStrLocals) {
                if (loc.equalsIgnoreCase(defaultLocaleStr)) {
                    notInAvailableLangauges = false;
                    break;
                }
            }
            int defaultAvailable = this.mTts.setLanguage(this.mCurrentDefaultLocale);
            if (defaultAvailable == -2 || defaultAvailable == -1 || notInAvailableLangauges) {
                updateEngineStatus(R.string.tts_status_not_supported);
                updateWidgetState(false);
                return false;
            }
            if (isNetworkRequiredForSynthesis()) {
                updateEngineStatus(R.string.tts_status_requires_network);
            } else {
                updateEngineStatus(R.string.tts_status_ok);
            }
            updateWidgetState(true);
            return true;
        } catch (MissingResourceException e) {
            updateEngineStatus(R.string.tts_status_not_supported);
            updateWidgetState(false);
            return false;
        }
    }

    private void getSampleText() {
        String currentEngine = this.mTts.getCurrentEngine();
        if (TextUtils.isEmpty(currentEngine)) {
            currentEngine = this.mTts.getDefaultEngine();
        }
        Intent intent = new Intent("android.speech.tts.engine.GET_SAMPLE_TEXT");
        intent.putExtra("language", this.mCurrentDefaultLocale.getLanguage());
        intent.putExtra("country", this.mCurrentDefaultLocale.getCountry());
        intent.putExtra("variant", this.mCurrentDefaultLocale.getVariant());
        intent.setPackage(currentEngine);
        try {
            startActivityForResult(intent, 1983);
        } catch (ActivityNotFoundException e) {
            Log.e("TextToSpeechSettings", "Failed to get sample text, no activity found for " + intent + ")");
        }
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == 1983) {
            onSampleTextReceived(resultCode, data);
        } else if (requestCode == 1977) {
            onVoiceDataIntegrityCheckDone(data);
        }
    }

    private String getDefaultSampleString() {
        if (!(this.mTts == null || this.mTts.getLanguage() == null)) {
            try {
                String currentLang = this.mTts.getLanguage().getISO3Language();
                String[] strings = getActivity().getResources().getStringArray(R.array.tts_demo_strings);
                String[] langs = getActivity().getResources().getStringArray(R.array.tts_demo_string_langs);
                for (int i = 0; i < strings.length; i++) {
                    if (langs[i].equals(currentLang)) {
                        return strings[i];
                    }
                }
            } catch (MissingResourceException e) {
            }
        }
        return getString(R.string.tts_default_sample_string);
    }

    private boolean isNetworkRequiredForSynthesis() {
        Set<String> features = this.mTts.getFeatures(this.mCurrentDefaultLocale);
        if (features == null || !features.contains("networkTts") || features.contains("embeddedTts")) {
            return false;
        }
        return true;
    }

    private void onSampleTextReceived(int resultCode, Intent data) {
        String sample = getDefaultSampleString();
        if (!(resultCode != 0 || data == null || data == null || data.getStringExtra("sampleText") == null)) {
            sample = data.getStringExtra("sampleText");
        }
        this.mSampleText = sample;
        if (this.mSampleText != null) {
            updateWidgetState(true);
        } else {
            Log.e("TextToSpeechSettings", "Did not have a sample string for the requested language. Using default");
        }
    }

    private void speakSampleText() {
        boolean networkRequired = isNetworkRequiredForSynthesis();
        if (!networkRequired || (networkRequired && this.mTts.isLanguageAvailable(this.mCurrentDefaultLocale) >= 0)) {
            HashMap<String, String> params = new HashMap();
            params.put("utteranceId", "Sample");
            this.mTts.speak(this.mSampleText, 0, params);
            return;
        }
        Log.w("TextToSpeechSettings", "Network required for sample synthesis for requested language");
        displayNetworkAlert();
    }

    public boolean onPreferenceChange(Preference preference, Object objValue) {
        if ("tts_default_rate".equals(preference.getKey())) {
            this.mDefaultRate = Integer.parseInt((String) objValue);
            try {
                Secure.putInt(getContentResolver(), "tts_default_rate", this.mDefaultRate);
                if (this.mTts != null) {
                    this.mTts.setSpeechRate(((float) this.mDefaultRate) / 100.0f);
                }
            } catch (NumberFormatException e) {
                Log.e("TextToSpeechSettings", "could not persist default TTS rate setting", e);
            }
        }
        return true;
    }

    public boolean onPreferenceClick(Preference preference) {
        if (preference != this.mPlayExample) {
            return false;
        }
        speakSampleText();
        return true;
    }

    private void updateWidgetState(boolean enable) {
        this.mPlayExample.setEnabled(enable);
        this.mDefaultRatePref.setEnabled(enable);
        this.mEngineStatus.setEnabled(enable);
    }

    private void updateEngineStatus(int resourceId) {
        Locale locale = this.mCurrentDefaultLocale;
        if (locale == null) {
            locale = Locale.getDefault();
        }
        this.mEngineStatus.setSummary(getString(resourceId, new Object[]{locale.getDisplayName()}));
    }

    private void displayNetworkAlert() {
        Builder builder = new Builder(getActivity());
        builder.setTitle(17039380).setMessage(getActivity().getString(R.string.tts_engine_network_required)).setCancelable(false).setPositiveButton(17039370, null);
        builder.create().show();
    }

    private void updateDefaultEngine(String engine) {
        updateWidgetState(false);
        updateEngineStatus(R.string.tts_status_checking);
        this.mPreviousEngine = this.mTts.getCurrentEngine();
        if (this.mTts != null) {
            try {
                this.mTts.shutdown();
                this.mTts = null;
            } catch (Exception e) {
                Log.e("TextToSpeechSettings", "Error shutting down TTS engine" + e);
            }
        }
        this.mTts = new TextToSpeech(getActivity().getApplicationContext(), this.mUpdateListener, engine);
        setTtsUtteranceProgressListener();
    }

    public void onUpdateEngine(int status) {
        if (status == 0) {
            checkVoiceData(this.mTts.getCurrentEngine());
            return;
        }
        if (this.mPreviousEngine != null) {
            this.mTts = new TextToSpeech(getActivity().getApplicationContext(), this.mInitListener, this.mPreviousEngine);
            setTtsUtteranceProgressListener();
        }
        this.mPreviousEngine = null;
    }

    private void checkVoiceData(String engine) {
        Intent intent = new Intent("android.speech.tts.engine.CHECK_TTS_DATA");
        intent.setPackage(engine);
        try {
            startActivityForResult(intent, 1977);
        } catch (ActivityNotFoundException e) {
            Log.e("TextToSpeechSettings", "Failed to check TTS data, no activity found for " + intent + ")");
        }
    }

    private void onVoiceDataIntegrityCheckDone(Intent data) {
        String engine = this.mTts.getCurrentEngine();
        if (engine == null) {
            Log.e("TextToSpeechSettings", "Voice data check complete, but no engine bound");
        } else if (data == null) {
            Log.e("TextToSpeechSettings", "Engine failed voice data integrity check (null return)" + this.mTts.getCurrentEngine());
        } else {
            Secure.putString(getContentResolver(), "tts_default_synth", engine);
            this.mAvailableStrLocals = data.getStringArrayListExtra("availableVoices");
            if (this.mAvailableStrLocals == null) {
                Log.e("TextToSpeechSettings", "Voice data check complete, but no available voices found");
                this.mAvailableStrLocals = new ArrayList();
            }
            if (evaluateDefaultLocale()) {
                getSampleText();
            }
            int engineCount = this.mEnginePreferenceCategory.getPreferenceCount();
            for (int i = 0; i < engineCount; i++) {
                Preference p = this.mEnginePreferenceCategory.getPreference(i);
                if (p instanceof TtsEnginePreference) {
                    TtsEnginePreference enginePref = (TtsEnginePreference) p;
                    if (enginePref.getKey().equals(engine)) {
                        enginePref.setVoiceDataDetails(data);
                        return;
                    }
                }
            }
        }
    }

    public Checkable getCurrentChecked() {
        return this.mCurrentChecked;
    }

    public String getCurrentKey() {
        return this.mCurrentEngine;
    }

    public void setCurrentChecked(Checkable current) {
        this.mCurrentChecked = current;
    }

    public void setCurrentKey(String key) {
        this.mCurrentEngine = key;
        updateDefaultEngine(this.mCurrentEngine);
    }
}
