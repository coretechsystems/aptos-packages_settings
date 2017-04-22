package com.android.settings;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.AlertDialog.Builder;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityManager;
import android.widget.RadioButton;
import android.widget.TextView;
import com.android.internal.widget.LockPatternUtils;
import java.util.List;

public class EncryptionInterstitial extends SettingsActivity {

    public static class EncryptionInterstitialFragment extends SettingsPreferenceFragment implements OnClickListener, View.OnClickListener {
        private RadioButton mDontRequirePasswordToDecryptButton;
        private TextView mEncryptionMessage;
        private boolean mPasswordRequired;
        private RadioButton mRequirePasswordToDecryptButton;

        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            int msgId;
            int enableId;
            int disableId;
            View view = inflater.inflate(R.layout.encryption_interstitial, container, false);
            this.mRequirePasswordToDecryptButton = (RadioButton) view.findViewById(R.id.encrypt_require_password);
            this.mDontRequirePasswordToDecryptButton = (RadioButton) view.findViewById(R.id.encrypt_dont_require_password);
            this.mEncryptionMessage = (TextView) view.findViewById(R.id.encryption_message);
            switch (getActivity().getIntent().getIntExtra("extra_password_quality", 0)) {
                case 65536:
                    msgId = R.string.encryption_interstitial_message_pattern;
                    enableId = R.string.encrypt_require_pattern;
                    disableId = R.string.encrypt_dont_require_pattern;
                    break;
                case 131072:
                case 196608:
                    msgId = R.string.encryption_interstitial_message_pin;
                    enableId = R.string.encrypt_require_pin;
                    disableId = R.string.encrypt_dont_require_pin;
                    break;
                default:
                    msgId = R.string.encryption_interstitial_message_password;
                    enableId = R.string.encrypt_require_password;
                    disableId = R.string.encrypt_dont_require_password;
                    break;
            }
            this.mEncryptionMessage.setText(msgId);
            this.mRequirePasswordToDecryptButton.setOnClickListener(this);
            this.mRequirePasswordToDecryptButton.setText(enableId);
            this.mDontRequirePasswordToDecryptButton.setOnClickListener(this);
            this.mDontRequirePasswordToDecryptButton.setText(disableId);
            setRequirePasswordState(getActivity().getIntent().getBooleanExtra("extra_require_password", true));
            return view;
        }

        public void onClick(View v) {
            if (v != this.mRequirePasswordToDecryptButton) {
                setRequirePasswordState(false);
            } else if (!AccessibilityManager.getInstance(getActivity()).isEnabled() || this.mPasswordRequired) {
                setRequirePasswordState(true);
            } else {
                setRequirePasswordState(false);
                showDialog(1);
            }
        }

        public Dialog onCreateDialog(int dialogId) {
            switch (dialogId) {
                case 1:
                    int titleId;
                    int messageId;
                    CharSequence exampleAccessibility;
                    switch (new LockPatternUtils(getActivity()).getKeyguardStoredPasswordQuality()) {
                        case 65536:
                            titleId = R.string.encrypt_talkback_dialog_require_pattern;
                            messageId = R.string.encrypt_talkback_dialog_message_pattern;
                            break;
                        case 131072:
                        case 196608:
                            titleId = R.string.encrypt_talkback_dialog_require_pin;
                            messageId = R.string.encrypt_talkback_dialog_message_pin;
                            break;
                        default:
                            titleId = R.string.encrypt_talkback_dialog_require_password;
                            messageId = R.string.encrypt_talkback_dialog_message_password;
                            break;
                    }
                    List<AccessibilityServiceInfo> list = AccessibilityManager.getInstance(getActivity()).getEnabledAccessibilityServiceList(-1);
                    if (list.isEmpty()) {
                        exampleAccessibility = "";
                    } else {
                        exampleAccessibility = ((AccessibilityServiceInfo) list.get(0)).getResolveInfo().loadLabel(getPackageManager());
                    }
                    return new Builder(getActivity()).setTitle(titleId).setMessage(getString(messageId, new Object[]{exampleAccessibility})).setCancelable(true).setPositiveButton(17039370, this).setNegativeButton(17039360, this).create();
                default:
                    throw new IllegalArgumentException();
            }
        }

        private void setRequirePasswordState(boolean required) {
            this.mPasswordRequired = required;
            this.mRequirePasswordToDecryptButton.setChecked(required);
            this.mDontRequirePasswordToDecryptButton.setChecked(!required);
            SettingsActivity sa = (SettingsActivity) getActivity();
            Intent resultIntentData = sa.getResultIntentData();
            if (resultIntentData == null) {
                resultIntentData = new Intent();
            }
            resultIntentData.putExtra("extra_require_password", this.mPasswordRequired);
            sa.setResultIntentData(resultIntentData);
        }

        public void onClick(DialogInterface dialog, int which) {
            if (which == -1) {
                setRequirePasswordState(true);
            } else if (which == -2) {
                setRequirePasswordState(false);
            }
        }
    }

    public Intent getIntent() {
        Intent modIntent = new Intent(super.getIntent());
        modIntent.putExtra(":settings:show_fragment", EncryptionInterstitialFragment.class.getName());
        return modIntent;
    }

    protected boolean isValidFragment(String fragmentName) {
        return EncryptionInterstitialFragment.class.getName().equals(fragmentName);
    }

    public static Intent createStartIntent(Context ctx, int quality, boolean requirePasswordDefault) {
        return new Intent(ctx, EncryptionInterstitial.class).putExtra("extra_prefs_show_button_bar", true).putExtra("extra_prefs_set_back_text", (String) null).putExtra("extra_prefs_set_next_text", ctx.getString(R.string.encryption_continue_button)).putExtra("extra_password_quality", quality).putExtra(":settings:show_fragment_title_resid", R.string.encryption_interstitial_header).putExtra("extra_require_password", requirePasswordDefault);
    }
}
