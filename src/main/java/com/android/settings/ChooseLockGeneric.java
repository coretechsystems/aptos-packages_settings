package com.android.settings;

import android.app.PendingIntent;
import android.app.admin.DevicePolicyManager;
import android.content.Intent;
import android.os.Bundle;
import android.os.Process;
import android.os.UserManager;
import android.preference.Preference;
import android.preference.PreferenceScreen;
import android.security.KeyStore;
import android.util.EventLog;
import android.util.MutableBoolean;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityManager;
import android.widget.ListView;
import com.android.internal.widget.LockPatternUtils;

public class ChooseLockGeneric extends SettingsActivity {

    public static class ChooseLockGenericFragment extends SettingsPreferenceFragment {
        private ChooseLockSettingsHelper mChooseLockSettingsHelper;
        private DevicePolicyManager mDPM;
        private boolean mEncryptionRequestDisabled;
        private int mEncryptionRequestQuality;
        private boolean mFinishPending = false;
        private KeyStore mKeyStore;
        private LockPatternUtils mLockPatternUtils;
        private boolean mPasswordConfirmed = false;
        private boolean mRequirePassword;
        private boolean mWaitingForConfirmation = false;

        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            this.mDPM = (DevicePolicyManager) getSystemService("device_policy");
            this.mKeyStore = KeyStore.getInstance();
            this.mChooseLockSettingsHelper = new ChooseLockSettingsHelper(getActivity());
            this.mLockPatternUtils = new LockPatternUtils(getActivity());
            boolean confirmCredentials = getActivity().getIntent().getBooleanExtra("confirm_credentials", true);
            if (getActivity() instanceof InternalActivity) {
                this.mPasswordConfirmed = !confirmCredentials;
            }
            if (savedInstanceState != null) {
                this.mPasswordConfirmed = savedInstanceState.getBoolean("password_confirmed");
                this.mWaitingForConfirmation = savedInstanceState.getBoolean("waiting_for_confirmation");
                this.mFinishPending = savedInstanceState.getBoolean("finish_pending");
                this.mEncryptionRequestQuality = savedInstanceState.getInt("encrypt_requested_quality");
                this.mEncryptionRequestDisabled = savedInstanceState.getBoolean("encrypt_requested_disabled");
            }
            if (this.mPasswordConfirmed) {
                updatePreferencesOrFinish();
            } else if (!this.mWaitingForConfirmation) {
                if (new ChooseLockSettingsHelper(getActivity(), this).launchConfirmationActivity(100, null, null)) {
                    this.mWaitingForConfirmation = true;
                    return;
                }
                this.mPasswordConfirmed = true;
                updatePreferencesOrFinish();
            }
        }

        public void onResume() {
            super.onResume();
            if (this.mFinishPending) {
                this.mFinishPending = false;
                finish();
            }
        }

        public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
            String key = preference.getKey();
            EventLog.writeEvent(90200, key);
            if ("unlock_set_off".equals(key)) {
                updateUnlockMethodAndFinish(0, true);
                return true;
            } else if ("unlock_set_none".equals(key)) {
                updateUnlockMethodAndFinish(0, false);
                return true;
            } else if ("unlock_set_biometric_weak".equals(key)) {
                maybeEnableEncryption(32768, false);
                return true;
            } else if ("unlock_set_pattern".equals(key)) {
                maybeEnableEncryption(65536, false);
                return true;
            } else if ("unlock_set_pin".equals(key)) {
                maybeEnableEncryption(131072, false);
                return true;
            } else if (!"unlock_set_password".equals(key)) {
                return false;
            } else {
                maybeEnableEncryption(262144, false);
                return true;
            }
        }

        private void maybeEnableEncryption(int quality, boolean disabled) {
            boolean z = false;
            if (Process.myUserHandle().isOwner() && LockPatternUtils.isDeviceEncryptionEnabled()) {
                this.mEncryptionRequestQuality = quality;
                this.mEncryptionRequestDisabled = disabled;
                boolean accEn = AccessibilityManager.getInstance(getActivity()).isEnabled();
                LockPatternUtils lockPatternUtils = this.mLockPatternUtils;
                if (!accEn) {
                    z = true;
                }
                startActivityForResult(EncryptionInterstitial.createStartIntent(getActivity(), quality, lockPatternUtils.isCredentialRequiredToDecrypt(z)), 102);
                return;
            }
            this.mRequirePassword = false;
            updateUnlockMethodAndFinish(quality, disabled);
        }

        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            View v = super.onCreateView(inflater, container, savedInstanceState);
            if (getActivity().getIntent().getBooleanExtra("lockscreen.biometric_weak_fallback", false)) {
                ((ListView) v.findViewById(16908298)).addHeaderView(View.inflate(getActivity(), R.layout.weak_biometric_fallback_header, null), null, false);
            }
            return v;
        }

        public void onActivityResult(int requestCode, int resultCode, Intent data) {
            super.onActivityResult(requestCode, resultCode, data);
            this.mWaitingForConfirmation = false;
            if (requestCode == 100 && resultCode == -1) {
                this.mPasswordConfirmed = true;
                updatePreferencesOrFinish();
            } else if (requestCode == 101) {
                this.mChooseLockSettingsHelper.utils().deleteTempGallery();
                getActivity().setResult(resultCode);
                finish();
            } else if (requestCode == 102 && resultCode == -1) {
                this.mRequirePassword = data.getBooleanExtra("extra_require_password", true);
                updateUnlockMethodAndFinish(this.mEncryptionRequestQuality, this.mEncryptionRequestDisabled);
            } else {
                getActivity().setResult(0);
                finish();
            }
        }

        public void onSaveInstanceState(Bundle outState) {
            super.onSaveInstanceState(outState);
            outState.putBoolean("password_confirmed", this.mPasswordConfirmed);
            outState.putBoolean("waiting_for_confirmation", this.mWaitingForConfirmation);
            outState.putBoolean("finish_pending", this.mFinishPending);
            outState.putInt("encrypt_requested_quality", this.mEncryptionRequestQuality);
            outState.putBoolean("encrypt_requested_disabled", this.mEncryptionRequestDisabled);
        }

        private void updatePreferencesOrFinish() {
            Intent intent = getActivity().getIntent();
            int quality = intent.getIntExtra("lockscreen.password_type", -1);
            if (quality == -1) {
                quality = intent.getIntExtra("minimum_quality", -1);
                MutableBoolean allowBiometric = new MutableBoolean(false);
                quality = upgradeQuality(quality, allowBiometric);
                PreferenceScreen prefScreen = getPreferenceScreen();
                if (prefScreen != null) {
                    prefScreen.removeAll();
                }
                addPreferencesFromResource(R.xml.security_settings_picker);
                disableUnusablePreferences(quality, allowBiometric);
                updatePreferenceSummaryIfNeeded();
                return;
            }
            updateUnlockMethodAndFinish(quality, false);
        }

        private int upgradeQuality(int quality, MutableBoolean allowBiometric) {
            return upgradeQualityForKeyStore(upgradeQualityForDPM(quality));
        }

        private int upgradeQualityForDPM(int quality) {
            int minQuality = this.mDPM.getPasswordQuality(null);
            if (quality < minQuality) {
                return minQuality;
            }
            return quality;
        }

        private int upgradeQualityForKeyStore(int quality) {
            if (this.mKeyStore.isEmpty() || quality >= 65536) {
                return quality;
            }
            return 65536;
        }

        private void disableUnusablePreferences(int quality, MutableBoolean allowBiometric) {
            PreferenceScreen entries = getPreferenceScreen();
            boolean onlyShowFallback = getActivity().getIntent().getBooleanExtra("lockscreen.biometric_weak_fallback", false);
            boolean weakBiometricAvailable = this.mChooseLockSettingsHelper.utils().isBiometricWeakInstalled();
            boolean singleUser = ((UserManager) getSystemService("user")).getUsers(true).size() == 1;
            for (int i = entries.getPreferenceCount() - 1; i >= 0; i--) {
                Preference pref = entries.getPreference(i);
                if (pref instanceof PreferenceScreen) {
                    String key = ((PreferenceScreen) pref).getKey();
                    boolean enabled = true;
                    boolean visible = true;
                    if ("unlock_set_off".equals(key)) {
                        enabled = quality <= 0;
                        visible = singleUser;
                    } else if ("unlock_set_none".equals(key)) {
                        enabled = quality <= 0;
                    } else if ("unlock_set_biometric_weak".equals(key)) {
                        enabled = quality <= 32768 || allowBiometric.value;
                        visible = weakBiometricAvailable;
                    } else if ("unlock_set_pattern".equals(key)) {
                        enabled = quality <= 65536;
                    } else if ("unlock_set_pin".equals(key)) {
                        enabled = quality <= 196608;
                    } else if ("unlock_set_password".equals(key)) {
                        enabled = quality <= 393216;
                    }
                    if (!visible || (onlyShowFallback && !allowedForFallback(key))) {
                        entries.removePreference(pref);
                    } else if (!enabled) {
                        pref.setSummary(R.string.unlock_set_unlock_disabled_summary);
                        pref.setEnabled(false);
                    }
                }
            }
        }

        /* JADX WARNING: inconsistent code. */
        /* Code decompiled incorrectly, please refer to instructions dump. */
        private void updatePreferenceSummaryIfNeeded() {
            /*
            r8 = this;
            r6 = -1;
            r5 = com.android.internal.widget.LockPatternUtils.isDeviceEncrypted();
            if (r5 == 0) goto L_0x0008;
        L_0x0007:
            return;
        L_0x0008:
            r5 = r8.getActivity();
            r5 = android.view.accessibility.AccessibilityManager.getInstance(r5);
            r5 = r5.getEnabledAccessibilityServiceList(r6);
            r5 = r5.isEmpty();
            if (r5 != 0) goto L_0x0007;
        L_0x001a:
            r5 = 2131232208; // 0x7f0805d0 float:1.8080519E38 double:1.0529686173E-314;
            r4 = r8.getString(r5);
            r3 = r8.getPreferenceScreen();
            r2 = r3.getPreferenceCount();
            r0 = 0;
        L_0x002a:
            if (r0 >= r2) goto L_0x0007;
        L_0x002c:
            r1 = r3.getPreference(r0);
            r5 = r1.getKey();
            r7 = r5.hashCode();
            switch(r7) {
                case -122970563: goto L_0x004c;
                case 669087475: goto L_0x0056;
                case 1407992888: goto L_0x0042;
                default: goto L_0x003b;
            };
        L_0x003b:
            r5 = r6;
        L_0x003c:
            switch(r5) {
                case 0: goto L_0x0060;
                case 1: goto L_0x0060;
                case 2: goto L_0x0060;
                default: goto L_0x003f;
            };
        L_0x003f:
            r0 = r0 + 1;
            goto L_0x002a;
        L_0x0042:
            r7 = "unlock_set_pattern";
            r5 = r5.equals(r7);
            if (r5 == 0) goto L_0x003b;
        L_0x004a:
            r5 = 0;
            goto L_0x003c;
        L_0x004c:
            r7 = "unlock_set_pin";
            r5 = r5.equals(r7);
            if (r5 == 0) goto L_0x003b;
        L_0x0054:
            r5 = 1;
            goto L_0x003c;
        L_0x0056:
            r7 = "unlock_set_password";
            r5 = r5.equals(r7);
            if (r5 == 0) goto L_0x003b;
        L_0x005e:
            r5 = 2;
            goto L_0x003c;
        L_0x0060:
            r1.setSummary(r4);
            goto L_0x003f;
            */
            throw new UnsupportedOperationException("Method not decompiled: com.android.settings.ChooseLockGeneric.ChooseLockGenericFragment.updatePreferenceSummaryIfNeeded():void");
        }

        private boolean allowedForFallback(String key) {
            return "unlock_backup_info".equals(key) || "unlock_set_pattern".equals(key) || "unlock_set_pin".equals(key);
        }

        private Intent getBiometricSensorIntent() {
            Intent fallBackIntent = new Intent().setClass(getActivity(), InternalActivity.class);
            fallBackIntent.putExtra("lockscreen.biometric_weak_fallback", true);
            fallBackIntent.putExtra("confirm_credentials", false);
            fallBackIntent.putExtra(":settings:show_fragment_title", R.string.backup_lock_settings_picker_title);
            Intent intent = new Intent();
            intent.setClassName("com.android.facelock", "com.android.facelock.SetupIntro");
            intent.putExtra("showTutorial", true);
            intent.putExtra("PendingIntent", PendingIntent.getActivity(getActivity(), 0, fallBackIntent, 0));
            return intent;
        }

        void updateUnlockMethodAndFinish(int quality, boolean disabled) {
            if (this.mPasswordConfirmed) {
                boolean isFallback = getActivity().getIntent().getBooleanExtra("lockscreen.biometric_weak_fallback", false);
                quality = upgradeQuality(quality, null);
                Intent intent;
                if (quality >= 131072) {
                    int minLength = this.mDPM.getPasswordMinimumLength(null);
                    if (minLength < 4) {
                        minLength = 4;
                    }
                    int i = quality;
                    intent = ChooseLockPassword.createIntent(getActivity(), i, isFallback, minLength, this.mDPM.getPasswordMaximumLength(quality), this.mRequirePassword, false);
                    if (isFallback) {
                        startActivityForResult(intent, 101);
                        return;
                    }
                    this.mFinishPending = true;
                    intent.addFlags(33554432);
                    startActivity(intent);
                    return;
                } else if (quality == 65536) {
                    intent = ChooseLockPattern.createIntent(getActivity(), isFallback, this.mRequirePassword, false);
                    if (isFallback) {
                        startActivityForResult(intent, 101);
                        return;
                    }
                    this.mFinishPending = true;
                    intent.addFlags(33554432);
                    startActivity(intent);
                    return;
                } else if (quality == 32768) {
                    intent = getBiometricSensorIntent();
                    this.mFinishPending = true;
                    startActivity(intent);
                    return;
                } else if (quality == 0) {
                    this.mChooseLockSettingsHelper.utils().clearLock(false);
                    this.mChooseLockSettingsHelper.utils().setLockScreenDisabled(disabled);
                    getActivity().setResult(-1);
                    finish();
                    return;
                } else {
                    finish();
                    return;
                }
            }
            throw new IllegalStateException("Tried to update password without confirming it");
        }

        protected int getHelpResource() {
            return R.string.help_url_choose_lockscreen;
        }
    }

    public static class InternalActivity extends ChooseLockGeneric {
    }

    public Intent getIntent() {
        Intent modIntent = new Intent(super.getIntent());
        modIntent.putExtra(":settings:show_fragment", ChooseLockGenericFragment.class.getName());
        return modIntent;
    }

    protected boolean isValidFragment(String fragmentName) {
        if (ChooseLockGenericFragment.class.getName().equals(fragmentName)) {
            return true;
        }
        return false;
    }
}
