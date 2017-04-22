package com.android.settings;

import android.app.AlertDialog.Builder;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.UserHandle;
import android.os.UserManager;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceGroup;
import android.preference.PreferenceScreen;
import android.preference.SwitchPreference;
import android.provider.SearchIndexableResource;
import android.provider.Settings.Global;
import android.provider.Settings.Secure;
import android.provider.Settings.System;
import android.security.KeyStore;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import com.android.internal.widget.LockPatternUtils;
import com.android.settings.TrustAgentUtils.TrustAgentComponentInfo;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settings.search.Index;
import com.android.settings.search.Indexable;
import com.android.settings.search.Indexable.SearchIndexProvider;
import com.android.settings.search.SearchIndexableRaw;
import java.util.ArrayList;
import java.util.List;

public class SecuritySettings extends SettingsPreferenceFragment implements OnClickListener, OnPreferenceChangeListener, Indexable {
    public static final SearchIndexProvider SEARCH_INDEX_DATA_PROVIDER = new SecuritySearchIndexProvider();
    private static final String[] SWITCH_PREFERENCE_KEYS = new String[]{"lock_after_timeout", "lockenabled", "visiblepattern", "biometric_weak_liveliness", "power_button_instantly_locks", "show_password", "toggle_install_applications"};
    private static final Intent TRUST_AGENT_INTENT = new Intent("android.service.trust.TrustAgentService");
    private SwitchPreference mBiometricWeakLiveliness;
    private ChooseLockSettingsHelper mChooseLockSettingsHelper;
    private DevicePolicyManager mDPM;
    private boolean mIsPrimary;
    private KeyStore mKeyStore;
    private ListPreference mLockAfter;
    private LockPatternUtils mLockPatternUtils;
    private SwitchPreference mPowerButtonInstantlyLocks;
    private Preference mResetCredentials;
    private SwitchPreference mShowPassword;
    private SwitchPreference mToggleAppInstallation;
    private Intent mTrustAgentClickIntent;
    private SwitchPreference mVisiblePattern;
    private DialogInterface mWarnInstallApps;

    private static class SecuritySearchIndexProvider extends BaseSearchIndexProvider {
        boolean mIsPrimary;

        public SecuritySearchIndexProvider() {
            this.mIsPrimary = UserHandle.myUserId() == 0;
        }

        public List<SearchIndexableResource> getXmlResourcesToIndex(Context context, boolean enabled) {
            List<SearchIndexableResource> result = new ArrayList();
            int resId = SecuritySettings.getResIdForLockUnlockScreen(context, new LockPatternUtils(context));
            SearchIndexableResource sir = new SearchIndexableResource(context);
            sir.xmlResId = resId;
            result.add(sir);
            if (this.mIsPrimary) {
                switch (((DevicePolicyManager) context.getSystemService("device_policy")).getStorageEncryptionStatus()) {
                    case 1:
                        resId = R.xml.security_settings_unencrypted;
                        break;
                    case 3:
                        resId = R.xml.security_settings_encrypted;
                        break;
                }
                sir = new SearchIndexableResource(context);
                sir.xmlResId = resId;
                result.add(sir);
            }
            sir = new SearchIndexableResource(context);
            sir.xmlResId = R.xml.security_settings_misc;
            result.add(sir);
            return result;
        }

        public List<SearchIndexableRaw> getRawDataToIndex(Context context, boolean enabled) {
            List<SearchIndexableRaw> result = new ArrayList();
            Resources res = context.getResources();
            String screenTitle = res.getString(R.string.security_settings_title);
            SearchIndexableRaw data = new SearchIndexableRaw(context);
            data.title = screenTitle;
            data.screenTitle = screenTitle;
            result.add(data);
            if (!this.mIsPrimary) {
                int resId = UserManager.get(context).isLinkedUser() ? R.string.profile_info_settings_title : R.string.user_info_settings_title;
                data = new SearchIndexableRaw(context);
                data.title = res.getString(resId);
                data.screenTitle = screenTitle;
                result.add(data);
            }
            if (!((UserManager) context.getSystemService("user")).hasUserRestriction("no_config_credentials")) {
                int storageSummaryRes = KeyStore.getInstance().isHardwareBacked() ? R.string.credential_storage_type_hardware : R.string.credential_storage_type_software;
                data = new SearchIndexableRaw(context);
                data.title = res.getString(storageSummaryRes);
                data.screenTitle = screenTitle;
                result.add(data);
            }
            LockPatternUtils lockPatternUtils = new LockPatternUtils(context);
            if (lockPatternUtils.isSecure()) {
                ArrayList<TrustAgentComponentInfo> agents = SecuritySettings.getActiveTrustAgents(context.getPackageManager(), lockPatternUtils);
                for (int i = 0; i < agents.size(); i++) {
                    TrustAgentComponentInfo agent = (TrustAgentComponentInfo) agents.get(i);
                    data = new SearchIndexableRaw(context);
                    data.title = agent.title;
                    data.screenTitle = screenTitle;
                    result.add(data);
                }
            }
            return result;
        }

        public List<String> getNonIndexableKeys(Context context) {
            List<String> keys = new ArrayList();
            LockPatternUtils lockPatternUtils = new LockPatternUtils(context);
            if (SecuritySettings.getResIdForLockUnlockScreen(context, lockPatternUtils) == R.xml.security_settings_biometric_weak && lockPatternUtils.getKeyguardStoredPasswordQuality() != 65536) {
                keys.add("visiblepattern");
            }
            TelephonyManager tm = TelephonyManager.getDefault();
            if (!(this.mIsPrimary && tm.hasIccCard())) {
                keys.add("sim_lock");
            }
            if (((UserManager) context.getSystemService("user")).hasUserRestriction("no_config_credentials")) {
                keys.add("credentials_management");
            }
            if (!lockPatternUtils.isSecure()) {
                keys.add("trust_agent");
                keys.add("manage_trust_agents");
            }
            return keys;
        }
    }

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.mLockPatternUtils = new LockPatternUtils(getActivity());
        this.mDPM = (DevicePolicyManager) getSystemService("device_policy");
        this.mChooseLockSettingsHelper = new ChooseLockSettingsHelper(getActivity());
        if (savedInstanceState != null && savedInstanceState.containsKey("trust_agent_click_intent")) {
            this.mTrustAgentClickIntent = (Intent) savedInstanceState.getParcelable("trust_agent_click_intent");
        }
    }

    private static int getResIdForLockUnlockScreen(Context context, LockPatternUtils lockPatternUtils) {
        boolean singleUser = true;
        if (!lockPatternUtils.isSecure()) {
            if (((UserManager) context.getSystemService("user")).getUsers(true).size() != 1) {
                singleUser = false;
            }
            if (singleUser && lockPatternUtils.isLockScreenDisabled()) {
                return R.xml.security_settings_lockscreen;
            }
            return R.xml.security_settings_chooser;
        } else if (lockPatternUtils.usingBiometricWeak() && lockPatternUtils.isBiometricWeakInstalled()) {
            return R.xml.security_settings_biometric_weak;
        } else {
            switch (lockPatternUtils.getKeyguardStoredPasswordQuality()) {
                case 65536:
                    return R.xml.security_settings_pattern;
                case 131072:
                case 196608:
                    return R.xml.security_settings_pin;
                case 262144:
                case 327680:
                case 393216:
                    return R.xml.security_settings_password;
                default:
                    return 0;
            }
        }
    }

    private PreferenceScreen createPreferenceHierarchy() {
        PreferenceScreen root = getPreferenceScreen();
        if (root != null) {
            root.removeAll();
        }
        addPreferencesFromResource(R.xml.security_settings);
        root = getPreferenceScreen();
        int resid = getResIdForLockUnlockScreen(getActivity(), this.mLockPatternUtils);
        addPreferencesFromResource(resid);
        this.mIsPrimary = UserHandle.myUserId() == 0;
        if (!this.mIsPrimary) {
            Preference ownerInfoPref = findPreference("owner_info_settings");
            if (ownerInfoPref != null) {
                if (UserManager.get(getActivity()).isLinkedUser()) {
                    ownerInfoPref.setTitle(R.string.profile_info_settings_title);
                } else {
                    ownerInfoPref.setTitle(R.string.user_info_settings_title);
                }
            }
        }
        if (this.mIsPrimary) {
            if (LockPatternUtils.isDeviceEncryptionEnabled()) {
                addPreferencesFromResource(R.xml.security_settings_encrypted);
            } else {
                addPreferencesFromResource(R.xml.security_settings_unencrypted);
            }
        }
        PreferenceGroup securityCategory = (PreferenceGroup) root.findPreference("security_category");
        if (securityCategory != null) {
            boolean hasSecurity = this.mLockPatternUtils.isSecure();
            ArrayList<TrustAgentComponentInfo> agents = getActiveTrustAgents(getPackageManager(), this.mLockPatternUtils);
            int i;
            for (i = 0; i < agents.size(); i++) {
                TrustAgentComponentInfo agent = (TrustAgentComponentInfo) agents.get(i);
                Preference preference = new Preference(securityCategory.getContext());
                preference.setKey("trust_agent");
                preference.setTitle(agent.title);
                preference.setSummary(agent.summary);
                Intent intent = new Intent();
                intent.setComponent(agent.componentName);
                intent.setAction("android.intent.action.MAIN");
                preference.setIntent(intent);
                securityCategory.addPreference(preference);
                if (!hasSecurity) {
                    preference.setEnabled(false);
                    preference.setSummary(R.string.disabled_because_no_backup_security);
                }
            }
        }
        this.mLockAfter = (ListPreference) root.findPreference("lock_after_timeout");
        if (this.mLockAfter != null) {
            setupLockAfterPreference();
            updateLockAfterPreferenceSummary();
        }
        this.mBiometricWeakLiveliness = (SwitchPreference) root.findPreference("biometric_weak_liveliness");
        this.mVisiblePattern = (SwitchPreference) root.findPreference("visiblepattern");
        this.mPowerButtonInstantlyLocks = (SwitchPreference) root.findPreference("power_button_instantly_locks");
        Preference trustAgentPreference = root.findPreference("trust_agent");
        if (!(this.mPowerButtonInstantlyLocks == null || trustAgentPreference == null || trustAgentPreference.getTitle().length() <= 0)) {
            this.mPowerButtonInstantlyLocks.setSummary(getString(R.string.lockpattern_settings_power_button_instantly_locks_summary, new Object[]{trustAgentPreference.getTitle()}));
        }
        if (!(resid != R.xml.security_settings_biometric_weak || this.mLockPatternUtils.getKeyguardStoredPasswordQuality() == 65536 || securityCategory == null || this.mVisiblePattern == null)) {
            securityCategory.removePreference(root.findPreference("visiblepattern"));
        }
        addPreferencesFromResource(R.xml.security_settings_misc);
        TelephonyManager tm = TelephonyManager.getDefault();
        if (!this.mIsPrimary || !tm.hasIccCard()) {
            root.removePreference(root.findPreference("sim_lock"));
        } else if (TelephonyManager.getDefault().getSimState() == 1 || TelephonyManager.getDefault().getSimState() == 0) {
            root.findPreference("sim_lock").setEnabled(false);
        }
        if (System.getInt(getContentResolver(), "lock_to_app_enabled", 0) != 0) {
            root.findPreference("screen_pinning_settings").setSummary(getResources().getString(R.string.switch_on_text));
        }
        this.mShowPassword = (SwitchPreference) root.findPreference("show_password");
        this.mResetCredentials = root.findPreference("credentials_reset");
        UserManager um = (UserManager) getActivity().getSystemService("user");
        this.mKeyStore = KeyStore.getInstance();
        if (um.hasUserRestriction("no_config_credentials")) {
            PreferenceGroup credentialsManager = (PreferenceGroup) root.findPreference("credentials_management");
            credentialsManager.removePreference(root.findPreference("credentials_reset"));
            credentialsManager.removePreference(root.findPreference("credentials_install"));
            credentialsManager.removePreference(root.findPreference("credential_storage_type"));
        } else {
            root.findPreference("credential_storage_type").setSummary(this.mKeyStore.isHardwareBacked() ? R.string.credential_storage_type_hardware : R.string.credential_storage_type_software);
        }
        PreferenceGroup deviceAdminCategory = (PreferenceGroup) root.findPreference("device_admin_category");
        this.mToggleAppInstallation = (SwitchPreference) findPreference("toggle_install_applications");
        this.mToggleAppInstallation.setChecked(isNonMarketAppsAllowed());
        this.mToggleAppInstallation.setEnabled(this.mIsPrimary);
        if (um.hasUserRestriction("no_install_unknown_sources") || um.hasUserRestriction("no_install_apps")) {
            this.mToggleAppInstallation.setEnabled(false);
        }
        PreferenceGroup advancedCategory = (PreferenceGroup) root.findPreference("advanced_security");
        if (advancedCategory != null) {
            Preference manageAgents = advancedCategory.findPreference("manage_trust_agents");
            if (!(manageAgents == null || this.mLockPatternUtils.isSecure())) {
                manageAgents.setEnabled(false);
                manageAgents.setSummary(R.string.disabled_because_no_backup_security);
            }
        }
        Index.getInstance(getActivity()).updateFromClassNameResource(SecuritySettings.class.getName(), true, true);
        for (String findPreference : SWITCH_PREFERENCE_KEYS) {
            Preference pref = findPreference(findPreference);
            if (pref != null) {
                pref.setOnPreferenceChangeListener(this);
            }
        }
        return root;
    }

    private static ArrayList<TrustAgentComponentInfo> getActiveTrustAgents(PackageManager pm, LockPatternUtils utils) {
        ArrayList<TrustAgentComponentInfo> result = new ArrayList();
        List<ResolveInfo> resolveInfos = pm.queryIntentServices(TRUST_AGENT_INTENT, 128);
        List<ComponentName> enabledTrustAgents = utils.getEnabledTrustAgents();
        if (enabledTrustAgents != null && !enabledTrustAgents.isEmpty()) {
            for (int i = 0; i < resolveInfos.size(); i++) {
                ResolveInfo resolveInfo = (ResolveInfo) resolveInfos.get(i);
                if (resolveInfo.serviceInfo != null && TrustAgentUtils.checkProvidePermission(resolveInfo, pm)) {
                    TrustAgentComponentInfo trustAgentComponentInfo = TrustAgentUtils.getSettingsComponent(pm, resolveInfo);
                    if (!(trustAgentComponentInfo.componentName == null || !enabledTrustAgents.contains(TrustAgentUtils.getComponentName(resolveInfo)) || TextUtils.isEmpty(trustAgentComponentInfo.title))) {
                        result.add(trustAgentComponentInfo);
                        break;
                    }
                }
            }
        }
        return result;
    }

    private boolean isNonMarketAppsAllowed() {
        return Global.getInt(getContentResolver(), "install_non_market_apps", 0) > 0;
    }

    private void setNonMarketAppsAllowed(boolean enabled) {
        if (!((UserManager) getActivity().getSystemService("user")).hasUserRestriction("no_install_unknown_sources")) {
            Global.putInt(getContentResolver(), "install_non_market_apps", enabled ? 1 : 0);
        }
    }

    private void warnAppInstallation() {
        this.mWarnInstallApps = new Builder(getActivity()).setTitle(getResources().getString(R.string.error_title)).setIcon(17301543).setMessage(getResources().getString(R.string.install_all_warning)).setPositiveButton(17039379, this).setNegativeButton(17039369, this).show();
    }

    public void onClick(DialogInterface dialog, int which) {
        if (dialog == this.mWarnInstallApps) {
            boolean turnOn = which == -1;
            setNonMarketAppsAllowed(turnOn);
            if (this.mToggleAppInstallation != null) {
                this.mToggleAppInstallation.setChecked(turnOn);
            }
        }
    }

    public void onDestroy() {
        super.onDestroy();
        if (this.mWarnInstallApps != null) {
            this.mWarnInstallApps.dismiss();
        }
    }

    private void setupLockAfterPreference() {
        long adminTimeout;
        this.mLockAfter.setValue(String.valueOf(Secure.getLong(getContentResolver(), "lock_screen_lock_after_timeout", 5000)));
        this.mLockAfter.setOnPreferenceChangeListener(this);
        if (this.mDPM != null) {
            adminTimeout = this.mDPM.getMaximumTimeToLock(null);
        } else {
            adminTimeout = 0;
        }
        long displayTimeout = (long) Math.max(0, System.getInt(getContentResolver(), "screen_off_timeout", 0));
        if (adminTimeout > 0) {
            disableUnusableTimeouts(Math.max(0, adminTimeout - displayTimeout));
        }
    }

    private void updateLockAfterPreferenceSummary() {
        long currentTimeout = Secure.getLong(getContentResolver(), "lock_screen_lock_after_timeout", 5000);
        CharSequence[] entries = this.mLockAfter.getEntries();
        CharSequence[] values = this.mLockAfter.getEntryValues();
        int best = 0;
        for (int i = 0; i < values.length; i++) {
            if (currentTimeout >= Long.valueOf(values[i].toString()).longValue()) {
                best = i;
            }
        }
        Preference preference = getPreferenceScreen().findPreference("trust_agent");
        if (preference == null || preference.getTitle().length() <= 0) {
            this.mLockAfter.setSummary(getString(R.string.lock_after_timeout_summary, new Object[]{entries[best]}));
            return;
        }
        this.mLockAfter.setSummary(getString(R.string.lock_after_timeout_summary_with_exception, new Object[]{entries[best], preference.getTitle()}));
    }

    private void disableUnusableTimeouts(long maxTimeout) {
        CharSequence[] entries = this.mLockAfter.getEntries();
        CharSequence[] values = this.mLockAfter.getEntryValues();
        ArrayList<CharSequence> revisedEntries = new ArrayList();
        ArrayList<CharSequence> revisedValues = new ArrayList();
        for (int i = 0; i < values.length; i++) {
            if (Long.valueOf(values[i].toString()).longValue() <= maxTimeout) {
                revisedEntries.add(entries[i]);
                revisedValues.add(values[i]);
            }
        }
        if (!(revisedEntries.size() == entries.length && revisedValues.size() == values.length)) {
            this.mLockAfter.setEntries((CharSequence[]) revisedEntries.toArray(new CharSequence[revisedEntries.size()]));
            this.mLockAfter.setEntryValues((CharSequence[]) revisedValues.toArray(new CharSequence[revisedValues.size()]));
            int userPreference = Integer.valueOf(this.mLockAfter.getValue()).intValue();
            if (((long) userPreference) <= maxTimeout) {
                this.mLockAfter.setValue(String.valueOf(userPreference));
            }
        }
        this.mLockAfter.setEnabled(revisedEntries.size() > 0);
    }

    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (this.mTrustAgentClickIntent != null) {
            outState.putParcelable("trust_agent_click_intent", this.mTrustAgentClickIntent);
        }
    }

    public void onResume() {
        boolean z = true;
        super.onResume();
        createPreferenceHierarchy();
        LockPatternUtils lockPatternUtils = this.mChooseLockSettingsHelper.utils();
        if (this.mBiometricWeakLiveliness != null) {
            this.mBiometricWeakLiveliness.setChecked(lockPatternUtils.isBiometricWeakLivelinessEnabled());
        }
        if (this.mVisiblePattern != null) {
            this.mVisiblePattern.setChecked(lockPatternUtils.isVisiblePatternEnabled());
        }
        if (this.mPowerButtonInstantlyLocks != null) {
            this.mPowerButtonInstantlyLocks.setChecked(lockPatternUtils.getPowerButtonInstantlyLocks());
        }
        if (this.mShowPassword != null) {
            this.mShowPassword.setChecked(System.getInt(getContentResolver(), "show_password", 1) != 0);
        }
        if (this.mResetCredentials != null) {
            Preference preference = this.mResetCredentials;
            if (this.mKeyStore.isEmpty()) {
                z = false;
            }
            preference.setEnabled(z);
        }
    }

    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        String key = preference.getKey();
        if ("unlock_set_or_change".equals(key)) {
            startFragment(this, "com.android.settings.ChooseLockGeneric$ChooseLockGenericFragment", R.string.lock_settings_picker_title, 123, null);
        } else if ("biometric_weak_improve_matching".equals(key)) {
            if (!new ChooseLockSettingsHelper(getActivity(), this).launchConfirmationActivity(124, null, null)) {
                startBiometricWeakImprove();
            }
        } else if (!"trust_agent".equals(key)) {
            return super.onPreferenceTreeClick(preferenceScreen, preference);
        } else {
            ChooseLockSettingsHelper helper = new ChooseLockSettingsHelper(getActivity(), this);
            this.mTrustAgentClickIntent = preference.getIntent();
            if (!(helper.launchConfirmationActivity(126, null, null) || this.mTrustAgentClickIntent == null)) {
                startActivity(this.mTrustAgentClickIntent);
                this.mTrustAgentClickIntent = null;
            }
        }
        return true;
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 124 && resultCode == -1) {
            startBiometricWeakImprove();
        } else if (requestCode == 125 && resultCode == -1) {
            this.mChooseLockSettingsHelper.utils().setBiometricWeakLivelinessEnabled(false);
        } else if (requestCode != 126 || resultCode != -1) {
            createPreferenceHierarchy();
        } else if (this.mTrustAgentClickIntent != null) {
            startActivity(this.mTrustAgentClickIntent);
            this.mTrustAgentClickIntent = null;
        }
    }

    public boolean onPreferenceChange(Preference preference, Object value) {
        int i = 1;
        String key = preference.getKey();
        LockPatternUtils lockPatternUtils = this.mChooseLockSettingsHelper.utils();
        if ("lock_after_timeout".equals(key)) {
            try {
                Secure.putInt(getContentResolver(), "lock_screen_lock_after_timeout", Integer.parseInt((String) value));
            } catch (NumberFormatException e) {
                Log.e("SecuritySettings", "could not persist lockAfter timeout setting", e);
            }
            updateLockAfterPreferenceSummary();
            return true;
        } else if ("lockenabled".equals(key)) {
            lockPatternUtils.setLockPatternEnabled(((Boolean) value).booleanValue());
            return true;
        } else if ("visiblepattern".equals(key)) {
            lockPatternUtils.setVisiblePatternEnabled(((Boolean) value).booleanValue());
            return true;
        } else if ("biometric_weak_liveliness".equals(key)) {
            if (((Boolean) value).booleanValue()) {
                lockPatternUtils.setBiometricWeakLivelinessEnabled(true);
                return true;
            }
            this.mBiometricWeakLiveliness.setChecked(true);
            if (new ChooseLockSettingsHelper(getActivity(), this).launchConfirmationActivity(125, null, null)) {
                return true;
            }
            lockPatternUtils.setBiometricWeakLivelinessEnabled(false);
            this.mBiometricWeakLiveliness.setChecked(false);
            return true;
        } else if ("power_button_instantly_locks".equals(key)) {
            this.mLockPatternUtils.setPowerButtonInstantlyLocks(((Boolean) value).booleanValue());
            return true;
        } else if ("show_password".equals(key)) {
            ContentResolver contentResolver = getContentResolver();
            String str = "show_password";
            if (!((Boolean) value).booleanValue()) {
                i = 0;
            }
            System.putInt(contentResolver, str, i);
            return true;
        } else if (!"toggle_install_applications".equals(key)) {
            return true;
        } else {
            if (((Boolean) value).booleanValue()) {
                this.mToggleAppInstallation.setChecked(false);
                warnAppInstallation();
                return false;
            }
            setNonMarketAppsAllowed(false);
            return true;
        }
    }

    protected int getHelpResource() {
        return R.string.help_url_security;
    }

    public void startBiometricWeakImprove() {
        Intent intent = new Intent();
        intent.setClassName("com.android.facelock", "com.android.facelock.AddToSetup");
        startActivity(intent);
    }
}
