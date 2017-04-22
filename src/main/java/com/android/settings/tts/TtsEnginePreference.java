package com.android.settings.tts;

import android.app.AlertDialog.Builder;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.preference.Preference;
import android.speech.tts.TextToSpeech.EngineInfo;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Checkable;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.RadioButton;
import com.android.settings.R;
import com.android.settings.SettingsActivity;

public class TtsEnginePreference extends Preference {
    private final EngineInfo mEngineInfo;
    private volatile boolean mPreventRadioButtonCallbacks;
    private RadioButton mRadioButton;
    private final OnCheckedChangeListener mRadioChangeListener = new OnCheckedChangeListener() {
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            TtsEnginePreference.this.onRadioButtonClicked(buttonView, isChecked);
        }
    };
    private final SettingsActivity mSettingsActivity;
    private View mSettingsIcon;
    private final RadioButtonGroupState mSharedState;
    private Intent mVoiceCheckData;

    public interface RadioButtonGroupState {
        Checkable getCurrentChecked();

        String getCurrentKey();

        void setCurrentChecked(Checkable checkable);

        void setCurrentKey(String str);
    }

    public TtsEnginePreference(Context context, EngineInfo info, RadioButtonGroupState state, SettingsActivity prefActivity) {
        super(context);
        setLayoutResource(R.layout.preference_tts_engine);
        this.mSharedState = state;
        this.mSettingsActivity = prefActivity;
        this.mEngineInfo = info;
        this.mPreventRadioButtonCallbacks = false;
        setKey(this.mEngineInfo.name);
        setTitle(this.mEngineInfo.label);
    }

    public View getView(View convertView, ViewGroup parent) {
        boolean z = true;
        if (this.mSharedState == null) {
            throw new IllegalStateException("Call to getView() before a call tosetSharedState()");
        }
        View view = super.getView(convertView, parent);
        final RadioButton rb = (RadioButton) view.findViewById(R.id.tts_engine_radiobutton);
        rb.setOnCheckedChangeListener(this.mRadioChangeListener);
        boolean isChecked = getKey().equals(this.mSharedState.getCurrentKey());
        if (isChecked) {
            this.mSharedState.setCurrentChecked(rb);
        }
        this.mPreventRadioButtonCallbacks = true;
        rb.setChecked(isChecked);
        this.mPreventRadioButtonCallbacks = false;
        this.mRadioButton = rb;
        view.findViewById(R.id.tts_engine_pref_text).setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                TtsEnginePreference.this.onRadioButtonClicked(rb, !rb.isChecked());
            }
        });
        this.mSettingsIcon = view.findViewById(R.id.tts_engine_settings);
        View view2 = this.mSettingsIcon;
        if (!isChecked || this.mVoiceCheckData == null) {
            z = false;
        }
        view2.setEnabled(z);
        if (!isChecked) {
            this.mSettingsIcon.setAlpha(0.4f);
        }
        this.mSettingsIcon.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                Bundle args = new Bundle();
                args.putString("name", TtsEnginePreference.this.mEngineInfo.name);
                args.putString("label", TtsEnginePreference.this.mEngineInfo.label);
                if (TtsEnginePreference.this.mVoiceCheckData != null) {
                    args.putParcelable("voices", TtsEnginePreference.this.mVoiceCheckData);
                }
                TtsEnginePreference.this.mSettingsActivity.startPreferencePanel(TtsEngineSettingsFragment.class.getName(), args, 0, TtsEnginePreference.this.mEngineInfo.label, null, 0);
            }
        });
        if (this.mVoiceCheckData != null) {
            this.mSettingsIcon.setEnabled(this.mRadioButton.isChecked());
        }
        return view;
    }

    public void setVoiceDataDetails(Intent data) {
        this.mVoiceCheckData = data;
        if (this.mSettingsIcon != null && this.mRadioButton != null) {
            if (this.mRadioButton.isChecked()) {
                this.mSettingsIcon.setEnabled(true);
                return;
            }
            this.mSettingsIcon.setEnabled(false);
            this.mSettingsIcon.setAlpha(0.4f);
        }
    }

    private boolean shouldDisplayDataAlert() {
        return !this.mEngineInfo.system;
    }

    private void displayDataAlert(DialogInterface.OnClickListener positiveOnClickListener, DialogInterface.OnClickListener negativeOnClickListener) {
        Log.i("TtsEnginePreference", "Displaying data alert for :" + this.mEngineInfo.name);
        Builder builder = new Builder(getContext());
        builder.setTitle(17039380).setMessage(getContext().getString(R.string.tts_engine_security_warning, new Object[]{this.mEngineInfo.label})).setCancelable(true).setPositiveButton(17039370, positiveOnClickListener).setNegativeButton(17039360, negativeOnClickListener);
        builder.create().show();
    }

    private void onRadioButtonClicked(final CompoundButton buttonView, boolean isChecked) {
        if (!this.mPreventRadioButtonCallbacks && this.mSharedState.getCurrentChecked() != buttonView) {
            if (!isChecked) {
                this.mSettingsIcon.setEnabled(false);
            } else if (shouldDisplayDataAlert()) {
                displayDataAlert(new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        TtsEnginePreference.this.makeCurrentEngine(buttonView);
                    }
                }, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        buttonView.setChecked(false);
                    }
                });
            } else {
                makeCurrentEngine(buttonView);
            }
        }
    }

    private void makeCurrentEngine(Checkable current) {
        if (this.mSharedState.getCurrentChecked() != null) {
            this.mSharedState.getCurrentChecked().setChecked(false);
        }
        this.mSharedState.setCurrentChecked(current);
        this.mSharedState.setCurrentKey(getKey());
        callChangeListener(this.mSharedState.getCurrentKey());
        this.mSettingsIcon.setEnabled(true);
    }
}
