package com.android.settings.voice;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceCategory;
import android.provider.Settings.Secure;
import android.service.voice.VoiceInteractionServiceInfo;
import android.widget.Checkable;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settings.search.Indexable;
import com.android.settings.search.Indexable.SearchIndexProvider;
import com.android.settings.search.SearchIndexableRaw;
import com.android.settings.voice.VoiceInputHelper.InteractionInfo;
import com.android.settings.voice.VoiceInputHelper.RecognizerInfo;
import com.android.settings.voice.VoiceInputPreference.RadioButtonGroupState;
import java.util.ArrayList;
import java.util.List;

public class VoiceInputSettings extends SettingsPreferenceFragment implements OnPreferenceClickListener, Indexable, RadioButtonGroupState {
    public static final SearchIndexProvider SEARCH_INDEX_DATA_PROVIDER = new BaseSearchIndexProvider() {
        public List<SearchIndexableRaw> getRawDataToIndex(Context context, boolean enabled) {
            int i;
            List<SearchIndexableRaw> indexables = new ArrayList();
            String screenTitle = context.getString(R.string.voice_input_settings_title);
            SearchIndexableRaw indexable = new SearchIndexableRaw(context);
            indexable.key = "voice_service_preference_section_title";
            indexable.title = context.getString(R.string.voice_service_preference_section_title);
            indexable.screenTitle = screenTitle;
            indexables.add(indexable);
            List<ResolveInfo> voiceInteractions = context.getPackageManager().queryIntentServices(new Intent("android.service.voice.VoiceInteractionService"), 128);
            int countInteractions = voiceInteractions.size();
            for (i = 0; i < countInteractions; i++) {
                ResolveInfo info = (ResolveInfo) voiceInteractions.get(i);
                if (new VoiceInteractionServiceInfo(context.getPackageManager(), info.serviceInfo).getParseError() == null) {
                    indexables.add(getSearchIndexableRaw(context, info, screenTitle));
                }
            }
            List<ResolveInfo> recognitions = context.getPackageManager().queryIntentServices(new Intent("android.speech.RecognitionService"), 128);
            int countRecognitions = recognitions.size();
            for (i = 0; i < countRecognitions; i++) {
                indexables.add(getSearchIndexableRaw(context, (ResolveInfo) recognitions.get(i), screenTitle));
            }
            return indexables;
        }

        private SearchIndexableRaw getSearchIndexableRaw(Context context, ResolveInfo info, String screenTitle) {
            ServiceInfo serviceInfo = info.serviceInfo;
            ComponentName componentName = new ComponentName(serviceInfo.packageName, serviceInfo.name);
            SearchIndexableRaw indexable = new SearchIndexableRaw(context);
            indexable.key = componentName.flattenToString();
            indexable.title = info.loadLabel(context.getPackageManager()).toString();
            indexable.screenTitle = screenTitle;
            return indexable;
        }
    };
    private Checkable mCurrentChecked;
    private String mCurrentKey;
    private VoiceInputHelper mHelper;
    private CharSequence mInteractorSummary;
    private CharSequence mInteractorWarning;
    private CharSequence mRecognizerSummary;
    private PreferenceCategory mServicePreferenceCategory;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.voice_input_settings);
        this.mServicePreferenceCategory = (PreferenceCategory) findPreference("voice_service_preference_section");
        this.mInteractorSummary = getActivity().getText(R.string.voice_interactor_preference_summary);
        this.mRecognizerSummary = getActivity().getText(R.string.voice_recognizer_preference_summary);
        this.mInteractorWarning = getActivity().getText(R.string.voice_interaction_security_warning);
    }

    public void onStart() {
        super.onStart();
        initSettings();
    }

    private void initSettings() {
        int i;
        this.mHelper = new VoiceInputHelper(getActivity());
        this.mHelper.buildUi();
        this.mServicePreferenceCategory.removeAll();
        if (this.mHelper.mCurrentVoiceInteraction != null) {
            this.mCurrentKey = this.mHelper.mCurrentVoiceInteraction.flattenToShortString();
        } else if (this.mHelper.mCurrentRecognizer != null) {
            this.mCurrentKey = this.mHelper.mCurrentRecognizer.flattenToShortString();
        } else {
            this.mCurrentKey = null;
        }
        for (i = 0; i < this.mHelper.mAvailableInteractionInfos.size(); i++) {
            this.mServicePreferenceCategory.addPreference(new VoiceInputPreference(getActivity(), (InteractionInfo) this.mHelper.mAvailableInteractionInfos.get(i), this.mInteractorSummary, this.mInteractorWarning, this));
        }
        for (i = 0; i < this.mHelper.mAvailableRecognizerInfos.size(); i++) {
            this.mServicePreferenceCategory.addPreference(new VoiceInputPreference(getActivity(), (RecognizerInfo) this.mHelper.mAvailableRecognizerInfos.get(i), this.mRecognizerSummary, null, this));
        }
    }

    public Checkable getCurrentChecked() {
        return this.mCurrentChecked;
    }

    public String getCurrentKey() {
        return this.mCurrentKey;
    }

    public void setCurrentChecked(Checkable current) {
        this.mCurrentChecked = current;
    }

    public void setCurrentKey(String key) {
        int i;
        this.mCurrentKey = key;
        for (i = 0; i < this.mHelper.mAvailableInteractionInfos.size(); i++) {
            InteractionInfo info = (InteractionInfo) this.mHelper.mAvailableInteractionInfos.get(i);
            if (info.key.equals(key)) {
                Secure.putString(getActivity().getContentResolver(), "voice_interaction_service", key);
                if (info.settings != null) {
                    Secure.putString(getActivity().getContentResolver(), "voice_recognition_service", info.settings.flattenToShortString());
                    return;
                }
                return;
            }
        }
        for (i = 0; i < this.mHelper.mAvailableRecognizerInfos.size(); i++) {
            if (((RecognizerInfo) this.mHelper.mAvailableRecognizerInfos.get(i)).key.equals(key)) {
                Secure.putString(getActivity().getContentResolver(), "voice_interaction_service", "");
                Secure.putString(getActivity().getContentResolver(), "voice_recognition_service", key);
                return;
            }
        }
    }

    public boolean onPreferenceClick(Preference preference) {
        if (preference instanceof VoiceInputPreference) {
            ((VoiceInputPreference) preference).doClick();
        }
        return true;
    }
}
