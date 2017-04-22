package com.android.settings.notification;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings.Secure;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.RadioButton;
import com.android.settings.R;
import com.android.settings.SettingsActivity;
import com.android.settings.SettingsPreferenceFragment;

public class RedactionInterstitial extends SettingsActivity {

    public static class RedactionInterstitialFragment extends SettingsPreferenceFragment implements OnClickListener {
        private RadioButton mHideAllButton;
        private RadioButton mRedactSensitiveButton;
        private RadioButton mShowAllButton;

        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            View view = inflater.inflate(R.layout.redaction_interstitial, container, false);
            this.mShowAllButton = (RadioButton) view.findViewById(R.id.show_all);
            this.mRedactSensitiveButton = (RadioButton) view.findViewById(R.id.redact_sensitive);
            this.mHideAllButton = (RadioButton) view.findViewById(R.id.hide_all);
            this.mShowAllButton.setOnClickListener(this);
            this.mRedactSensitiveButton.setOnClickListener(this);
            this.mHideAllButton.setOnClickListener(this);
            return view;
        }

        public void onResume() {
            super.onResume();
            loadFromSettings();
        }

        private void loadFromSettings() {
            boolean enabled;
            boolean z;
            boolean z2 = true;
            if (Secure.getInt(getContentResolver(), "lock_screen_show_notifications", 0) != 0) {
                enabled = true;
            } else {
                enabled = false;
            }
            boolean show;
            if (Secure.getInt(getContentResolver(), "lock_screen_allow_private_notifications", 1) != 0) {
                show = true;
            } else {
                show = false;
            }
            RadioButton radioButton = this.mShowAllButton;
            if (enabled && show) {
                z = true;
            } else {
                z = false;
            }
            radioButton.setChecked(z);
            radioButton = this.mRedactSensitiveButton;
            if (!enabled || show) {
                z = false;
            } else {
                z = true;
            }
            radioButton.setChecked(z);
            RadioButton radioButton2 = this.mHideAllButton;
            if (enabled) {
                z2 = false;
            }
            radioButton2.setChecked(z2);
        }

        public void onClick(View v) {
            boolean show;
            boolean enabled;
            int i;
            int i2 = 1;
            if (v == this.mShowAllButton) {
                show = true;
            } else {
                show = false;
            }
            if (v != this.mHideAllButton) {
                enabled = true;
            } else {
                enabled = false;
            }
            ContentResolver contentResolver = getContentResolver();
            String str = "lock_screen_allow_private_notifications";
            if (show) {
                i = 1;
            } else {
                i = 0;
            }
            Secure.putInt(contentResolver, str, i);
            ContentResolver contentResolver2 = getContentResolver();
            String str2 = "lock_screen_show_notifications";
            if (!enabled) {
                i2 = 0;
            }
            Secure.putInt(contentResolver2, str2, i2);
        }
    }

    public Intent getIntent() {
        Intent modIntent = new Intent(super.getIntent());
        modIntent.putExtra(":settings:show_fragment", RedactionInterstitialFragment.class.getName());
        return modIntent;
    }

    protected boolean isValidFragment(String fragmentName) {
        return RedactionInterstitialFragment.class.getName().equals(fragmentName);
    }

    public static Intent createStartIntent(Context ctx) {
        return new Intent(ctx, RedactionInterstitial.class).putExtra("extra_prefs_show_button_bar", true).putExtra("extra_prefs_set_back_text", (String) null).putExtra("extra_prefs_set_next_text", ctx.getString(R.string.app_notifications_dialog_done));
    }
}
