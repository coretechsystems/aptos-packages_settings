package com.android.settings;

import android.app.ActivityManagerNative;
import android.app.AlertDialog.Builder;
import android.app.Dialog;
import android.app.admin.DevicePolicyManager;
import android.app.backup.IBackupManager;
import android.bluetooth.BluetoothAdapter;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.DialogInterface.OnDismissListener;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.hardware.usb.IUsbManager;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcel;
import android.os.Process;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.os.UserManager;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceGroup;
import android.preference.PreferenceScreen;
import android.provider.SearchIndexableResource;
import android.provider.Settings.Global;
import android.provider.Settings.Secure;
import android.provider.Settings.System;
import android.text.TextUtils;
import android.util.Log;
import android.view.IWindowManager;
import android.view.IWindowManager.Stub;
import android.widget.Switch;
import android.widget.TextView;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settings.search.Indexable;
import com.android.settings.search.Indexable.SearchIndexProvider;
import com.android.settings.widget.SwitchBar;
import com.android.settings.widget.SwitchBar.OnSwitchChangeListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

public class DevelopmentSettings extends SettingsPreferenceFragment implements OnClickListener, OnDismissListener, OnPreferenceChangeListener, Indexable, OnSwitchChangeListener {
    private static String DEFAULT_LOG_RING_BUFFER_SIZE_IN_BYTES = "262144";
    public static final SearchIndexProvider SEARCH_INDEX_DATA_PROVIDER = new BaseSearchIndexProvider() {
        private boolean isShowingDeveloperOptions(Context context) {
            return context.getSharedPreferences("development", 0).getBoolean("show", Build.TYPE.equals("eng"));
        }

        public List<SearchIndexableResource> getXmlResourcesToIndex(Context context, boolean enabled) {
            if (!isShowingDeveloperOptions(context)) {
                return null;
            }
            new SearchIndexableResource(context).xmlResId = R.xml.development_prefs;
            return Arrays.asList(new SearchIndexableResource[]{sir});
        }

        public List<String> getNonIndexableKeys(Context context) {
            if (!isShowingDeveloperOptions(context)) {
                return null;
            }
            List<String> keys = new ArrayList();
            if (DevelopmentSettings.showEnableOemUnlockPreference()) {
                return keys;
            }
            keys.add("oem_unlock_enable");
            return keys;
        }
    };
    private Dialog mAdbDialog;
    private Dialog mAdbKeysDialog;
    private final ArrayList<Preference> mAllPrefs = new ArrayList();
    private CheckBoxPreference mAllowMockLocation;
    private ListPreference mAnimatorDurationScale;
    private ListPreference mAppProcessLimit;
    private IBackupManager mBackupManager;
    private CheckBoxPreference mBtHciSnoopLog;
    private Preference mBugreport;
    private CheckBoxPreference mBugreportInPower;
    private Preference mClearAdbKeys;
    private String mDebugApp;
    private Preference mDebugAppPref;
    private ListPreference mDebugHwOverdraw;
    private CheckBoxPreference mDebugLayout;
    private CheckBoxPreference mDebugViewAttributes;
    private boolean mDialogClicked;
    private CheckBoxPreference mDisableOverlays;
    private final HashSet<Preference> mDisabledPrefs = new HashSet();
    private boolean mDontPokeProperties;
    private DevicePolicyManager mDpm;
    private CheckBoxPreference mEnableAdb;
    private Dialog mEnableDialog;
    private CheckBoxPreference mEnableOemUnlock;
    private CheckBoxPreference mEnableTerminal;
    private CheckBoxPreference mForceHardwareUi;
    private CheckBoxPreference mForceMsaa;
    private CheckBoxPreference mForceRtlLayout;
    private boolean mHaveDebugSettings;
    private CheckBoxPreference mImmediatelyDestroyActivities;
    private CheckBoxPreference mKeepScreenOn;
    private boolean mLastEnabledState;
    private ListPreference mLogdSize;
    private ListPreference mOpenGLTraces;
    private ListPreference mOverlayDisplayDevices;
    private PreferenceScreen mPassword;
    private CheckBoxPreference mPointerLocation;
    private PreferenceScreen mProcessStats;
    private final ArrayList<CheckBoxPreference> mResetCbPrefs = new ArrayList();
    private CheckBoxPreference mShowAllANRs;
    private CheckBoxPreference mShowCpuUsage;
    private CheckBoxPreference mShowHwLayersUpdates;
    private CheckBoxPreference mShowHwScreenUpdates;
    private ListPreference mShowNonRectClip;
    private CheckBoxPreference mShowScreenUpdates;
    private CheckBoxPreference mShowTouches;
    private ListPreference mSimulateColorSpace;
    private CheckBoxPreference mStrictMode;
    private SwitchBar mSwitchBar;
    private ListPreference mTrackFrameTime;
    private ListPreference mTransitionAnimationScale;
    private CheckBoxPreference mUSBAudio;
    private UserManager mUm;
    private boolean mUnavailable;
    private CheckBoxPreference mUseNuplayer;
    private CheckBoxPreference mVerifyAppsOverUsb;
    private CheckBoxPreference mWaitForDebugger;
    private CheckBoxPreference mWifiAggressiveHandover;
    private CheckBoxPreference mWifiAllowScansWithTraffic;
    private CheckBoxPreference mWifiDisplayCertification;
    private WifiManager mWifiManager;
    private CheckBoxPreference mWifiVerboseLogging;
    private ListPreference mWindowAnimationScale;
    private IWindowManager mWindowManager;

    static class SystemPropPoker extends AsyncTask<Void, Void, Void> {
        SystemPropPoker() {
        }

        protected Void doInBackground(Void... params) {
            try {
                for (String service : ServiceManager.listServices()) {
                    IBinder obj = ServiceManager.checkService(service);
                    if (obj != null) {
                        Parcel data = Parcel.obtain();
                        try {
                            obj.transact(1599295570, data, null, 0);
                        } catch (RemoteException e) {
                        } catch (Exception e2) {
                            Log.i("DevelopmentSettings", "Someone wrote a bad service '" + service + "' that doesn't like to be poked: " + e2);
                        }
                        data.recycle();
                    }
                }
            } catch (RemoteException e3) {
            }
            return null;
        }
    }

    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        this.mWindowManager = Stub.asInterface(ServiceManager.getService("window"));
        this.mBackupManager = IBackupManager.Stub.asInterface(ServiceManager.getService("backup"));
        this.mDpm = (DevicePolicyManager) getActivity().getSystemService("device_policy");
        this.mUm = (UserManager) getSystemService("user");
        this.mWifiManager = (WifiManager) getSystemService("wifi");
        if (Process.myUserHandle().getIdentifier() != 0 || this.mUm.hasUserRestriction("no_debugging_features")) {
            this.mUnavailable = true;
            setPreferenceScreen(new PreferenceScreen(getActivity(), null));
            return;
        }
        addPreferencesFromResource(R.xml.development_prefs);
        PreferenceGroup debugDebuggingCategory = (PreferenceGroup) findPreference("debug_debugging_category");
        this.mEnableAdb = findAndInitCheckboxPref("enable_adb");
        this.mClearAdbKeys = findPreference("clear_adb_keys");
        if (!(SystemProperties.getBoolean("ro.adb.secure", false) || debugDebuggingCategory == null)) {
            debugDebuggingCategory.removePreference(this.mClearAdbKeys);
        }
        this.mAllPrefs.add(this.mClearAdbKeys);
        this.mEnableTerminal = findAndInitCheckboxPref("enable_terminal");
        if (!isPackageInstalled(getActivity(), "com.android.terminal")) {
            debugDebuggingCategory.removePreference(this.mEnableTerminal);
            this.mEnableTerminal = null;
        }
        this.mBugreport = findPreference("bugreport");
        this.mBugreportInPower = findAndInitCheckboxPref("bugreport_in_power");
        this.mKeepScreenOn = findAndInitCheckboxPref("keep_screen_on");
        this.mBtHciSnoopLog = findAndInitCheckboxPref("bt_hci_snoop_log");
        this.mEnableOemUnlock = findAndInitCheckboxPref("oem_unlock_enable");
        if (!showEnableOemUnlockPreference()) {
            removePreference(this.mEnableOemUnlock);
            this.mEnableOemUnlock = null;
        }
        this.mAllowMockLocation = findAndInitCheckboxPref("allow_mock_location");
        this.mDebugViewAttributes = findAndInitCheckboxPref("debug_view_attributes");
        this.mPassword = (PreferenceScreen) findPreference("local_backup_password");
        this.mAllPrefs.add(this.mPassword);
        if (!Process.myUserHandle().equals(UserHandle.OWNER)) {
            disableForUser(this.mEnableAdb);
            disableForUser(this.mClearAdbKeys);
            disableForUser(this.mEnableTerminal);
            disableForUser(this.mPassword);
        }
        this.mDebugAppPref = findPreference("debug_app");
        this.mAllPrefs.add(this.mDebugAppPref);
        this.mWaitForDebugger = findAndInitCheckboxPref("wait_for_debugger");
        this.mVerifyAppsOverUsb = findAndInitCheckboxPref("verify_apps_over_usb");
        if (!showVerifierSetting()) {
            if (debugDebuggingCategory != null) {
                debugDebuggingCategory.removePreference(this.mVerifyAppsOverUsb);
            } else {
                this.mVerifyAppsOverUsb.setEnabled(false);
            }
        }
        this.mStrictMode = findAndInitCheckboxPref("strict_mode");
        this.mPointerLocation = findAndInitCheckboxPref("pointer_location");
        this.mShowTouches = findAndInitCheckboxPref("show_touches");
        this.mShowScreenUpdates = findAndInitCheckboxPref("show_screen_updates");
        this.mDisableOverlays = findAndInitCheckboxPref("disable_overlays");
        this.mShowCpuUsage = findAndInitCheckboxPref("show_cpu_usage");
        this.mForceHardwareUi = findAndInitCheckboxPref("force_hw_ui");
        this.mForceMsaa = findAndInitCheckboxPref("force_msaa");
        this.mTrackFrameTime = addListPreference("track_frame_time");
        this.mShowNonRectClip = addListPreference("show_non_rect_clip");
        this.mShowHwScreenUpdates = findAndInitCheckboxPref("show_hw_screen_udpates");
        this.mShowHwLayersUpdates = findAndInitCheckboxPref("show_hw_layers_udpates");
        this.mDebugLayout = findAndInitCheckboxPref("debug_layout");
        this.mForceRtlLayout = findAndInitCheckboxPref("force_rtl_layout_all_locales");
        this.mDebugHwOverdraw = addListPreference("debug_hw_overdraw");
        this.mWifiDisplayCertification = findAndInitCheckboxPref("wifi_display_certification");
        this.mWifiVerboseLogging = findAndInitCheckboxPref("wifi_verbose_logging");
        this.mWifiAggressiveHandover = findAndInitCheckboxPref("wifi_aggressive_handover");
        this.mWifiAllowScansWithTraffic = findAndInitCheckboxPref("wifi_allow_scan_with_traffic");
        this.mLogdSize = addListPreference("select_logd_size");
        this.mWindowAnimationScale = addListPreference("window_animation_scale");
        this.mTransitionAnimationScale = addListPreference("transition_animation_scale");
        this.mAnimatorDurationScale = addListPreference("animator_duration_scale");
        this.mOverlayDisplayDevices = addListPreference("overlay_display_devices");
        this.mOpenGLTraces = addListPreference("enable_opengl_traces");
        this.mSimulateColorSpace = addListPreference("simulate_color_space");
        this.mUseNuplayer = findAndInitCheckboxPref("use_nuplayer");
        this.mUSBAudio = findAndInitCheckboxPref("usb_audio");
        this.mImmediatelyDestroyActivities = (CheckBoxPreference) findPreference("immediately_destroy_activities");
        this.mAllPrefs.add(this.mImmediatelyDestroyActivities);
        this.mResetCbPrefs.add(this.mImmediatelyDestroyActivities);
        this.mAppProcessLimit = addListPreference("app_process_limit");
        this.mShowAllANRs = (CheckBoxPreference) findPreference("show_all_anrs");
        this.mAllPrefs.add(this.mShowAllANRs);
        this.mResetCbPrefs.add(this.mShowAllANRs);
        Preference hdcpChecking = findPreference("hdcp_checking");
        if (hdcpChecking != null) {
            this.mAllPrefs.add(hdcpChecking);
            removePreferenceForProduction(hdcpChecking);
        }
        this.mProcessStats = (PreferenceScreen) findPreference("proc_stats");
        this.mAllPrefs.add(this.mProcessStats);
    }

    private ListPreference addListPreference(String prefKey) {
        ListPreference pref = (ListPreference) findPreference(prefKey);
        this.mAllPrefs.add(pref);
        pref.setOnPreferenceChangeListener(this);
        return pref;
    }

    private void disableForUser(Preference pref) {
        if (pref != null) {
            pref.setEnabled(false);
            this.mDisabledPrefs.add(pref);
        }
    }

    private CheckBoxPreference findAndInitCheckboxPref(String key) {
        CheckBoxPreference pref = (CheckBoxPreference) findPreference(key);
        if (pref == null) {
            throw new IllegalArgumentException("Cannot find preference with key = " + key);
        }
        this.mAllPrefs.add(pref);
        this.mResetCbPrefs.add(pref);
        return pref;
    }

    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        this.mSwitchBar = ((SettingsActivity) getActivity()).getSwitchBar();
        if (this.mUnavailable) {
            this.mSwitchBar.setEnabled(false);
        } else {
            this.mSwitchBar.addOnSwitchChangeListener(this);
        }
    }

    private boolean removePreferenceForProduction(Preference preference) {
        if (!"user".equals(Build.TYPE)) {
            return false;
        }
        removePreference(preference);
        return true;
    }

    private void removePreference(Preference preference) {
        getPreferenceScreen().removePreference(preference);
        this.mAllPrefs.remove(preference);
    }

    private void setPrefsEnabledState(boolean enabled) {
        for (int i = 0; i < this.mAllPrefs.size(); i++) {
            Preference pref = (Preference) this.mAllPrefs.get(i);
            boolean z = enabled && !this.mDisabledPrefs.contains(pref);
            pref.setEnabled(z);
        }
        updateAllOptions();
    }

    public void onResume() {
        boolean z = false;
        super.onResume();
        if (this.mUnavailable) {
            TextView emptyView = (TextView) getView().findViewById(16908292);
            getListView().setEmptyView(emptyView);
            if (emptyView != null) {
                emptyView.setText(R.string.development_settings_not_available);
                return;
            }
            return;
        }
        if (this.mDpm.getMaximumTimeToLock(null) > 0) {
            this.mDisabledPrefs.add(this.mKeepScreenOn);
        } else {
            this.mDisabledPrefs.remove(this.mKeepScreenOn);
        }
        if (Global.getInt(getActivity().getContentResolver(), "development_settings_enabled", 0) != 0) {
            z = true;
        }
        this.mLastEnabledState = z;
        this.mSwitchBar.setChecked(this.mLastEnabledState);
        setPrefsEnabledState(this.mLastEnabledState);
        if (this.mHaveDebugSettings && !this.mLastEnabledState) {
            Global.putInt(getActivity().getContentResolver(), "development_settings_enabled", 1);
            this.mLastEnabledState = true;
            this.mSwitchBar.setChecked(this.mLastEnabledState);
            setPrefsEnabledState(this.mLastEnabledState);
        }
        this.mSwitchBar.show();
    }

    public void onDestroyView() {
        super.onDestroyView();
        if (!this.mUnavailable) {
            this.mSwitchBar.removeOnSwitchChangeListener(this);
            this.mSwitchBar.hide();
        }
    }

    void updateCheckBox(CheckBoxPreference checkBox, boolean value) {
        checkBox.setChecked(value);
        this.mHaveDebugSettings |= value;
    }

    private void updateAllOptions() {
        CheckBoxPreference checkBoxPreference;
        boolean z;
        boolean z2 = true;
        Context context = getActivity();
        ContentResolver cr = context.getContentResolver();
        this.mHaveDebugSettings = false;
        updateCheckBox(this.mEnableAdb, Global.getInt(cr, "adb_enabled", 0) != 0);
        if (this.mEnableTerminal != null) {
            checkBoxPreference = this.mEnableTerminal;
            if (context.getPackageManager().getApplicationEnabledSetting("com.android.terminal") == 1) {
                z = true;
            } else {
                z = false;
            }
            updateCheckBox(checkBoxPreference, z);
        }
        checkBoxPreference = this.mBugreportInPower;
        if (Secure.getInt(cr, "bugreport_in_power_menu", 0) != 0) {
            z = true;
        } else {
            z = false;
        }
        updateCheckBox(checkBoxPreference, z);
        checkBoxPreference = this.mKeepScreenOn;
        if (Global.getInt(cr, "stay_on_while_plugged_in", 0) != 0) {
            z = true;
        } else {
            z = false;
        }
        updateCheckBox(checkBoxPreference, z);
        checkBoxPreference = this.mBtHciSnoopLog;
        if (Secure.getInt(cr, "bluetooth_hci_log", 0) != 0) {
            z = true;
        } else {
            z = false;
        }
        updateCheckBox(checkBoxPreference, z);
        if (this.mEnableOemUnlock != null) {
            updateCheckBox(this.mEnableOemUnlock, Utils.isOemUnlockEnabled(getActivity()));
        }
        checkBoxPreference = this.mAllowMockLocation;
        if (Secure.getInt(cr, "mock_location", 0) != 0) {
            z = true;
        } else {
            z = false;
        }
        updateCheckBox(checkBoxPreference, z);
        CheckBoxPreference checkBoxPreference2 = this.mDebugViewAttributes;
        if (Global.getInt(cr, "debug_view_attributes", 0) == 0) {
            z2 = false;
        }
        updateCheckBox(checkBoxPreference2, z2);
        updateHdcpValues();
        updatePasswordSummary();
        updateDebuggerOptions();
        updateStrictModeVisualOptions();
        updatePointerLocationOptions();
        updateShowTouchesOptions();
        updateFlingerOptions();
        updateCpuUsageOptions();
        updateHardwareUiOptions();
        updateMsaaOptions();
        updateTrackFrameTimeOptions();
        updateShowNonRectClipOptions();
        updateShowHwScreenUpdatesOptions();
        updateShowHwLayersUpdatesOptions();
        updateDebugHwOverdrawOptions();
        updateDebugLayoutOptions();
        updateAnimationScaleOptions();
        updateOverlayDisplayDevicesOptions();
        updateOpenGLTracesOptions();
        updateImmediatelyDestroyActivitiesOptions();
        updateAppProcessLimitOptions();
        updateShowAllANRsOptions();
        updateVerifyAppsOverUsbOptions();
        updateBugreportOptions();
        updateForceRtlOptions();
        updateLogdSizeValues();
        updateWifiDisplayCertificationOptions();
        updateWifiVerboseLoggingOptions();
        updateWifiAggressiveHandoverOptions();
        updateWifiAllowScansWithTrafficOptions();
        updateSimulateColorSpace();
        updateUseNuplayerOptions();
        updateUSBAudioOptions();
    }

    private void resetDangerousOptions() {
        this.mDontPokeProperties = true;
        for (int i = 0; i < this.mResetCbPrefs.size(); i++) {
            CheckBoxPreference cb = (CheckBoxPreference) this.mResetCbPrefs.get(i);
            if (cb.isChecked()) {
                cb.setChecked(false);
                onPreferenceTreeClick(null, cb);
            }
        }
        resetDebuggerOptions();
        writeLogdSizeOption(null);
        writeAnimationScaleOption(0, this.mWindowAnimationScale, null);
        writeAnimationScaleOption(1, this.mTransitionAnimationScale, null);
        writeAnimationScaleOption(2, this.mAnimatorDurationScale, null);
        if (usingDevelopmentColorSpace()) {
            writeSimulateColorSpace(Integer.valueOf(-1));
        }
        writeOverlayDisplayDevicesOptions(null);
        writeAppProcessLimitOptions(null);
        this.mHaveDebugSettings = false;
        updateAllOptions();
        this.mDontPokeProperties = false;
        pokeSystemProperties();
    }

    private void updateHdcpValues() {
        ListPreference hdcpChecking = (ListPreference) findPreference("hdcp_checking");
        if (hdcpChecking != null) {
            String currentValue = SystemProperties.get("persist.sys.hdcp_checking");
            String[] values = getResources().getStringArray(R.array.hdcp_checking_values);
            String[] summaries = getResources().getStringArray(R.array.hdcp_checking_summaries);
            int index = 1;
            for (int i = 0; i < values.length; i++) {
                if (currentValue.equals(values[i])) {
                    index = i;
                    break;
                }
            }
            hdcpChecking.setValue(values[index]);
            hdcpChecking.setSummary(summaries[index]);
            hdcpChecking.setOnPreferenceChangeListener(this);
        }
    }

    private void updatePasswordSummary() {
        try {
            if (this.mBackupManager.hasBackupPassword()) {
                this.mPassword.setSummary(R.string.local_backup_password_summary_change);
            } else {
                this.mPassword.setSummary(R.string.local_backup_password_summary_none);
            }
        } catch (RemoteException e) {
        }
    }

    private void writeBtHciSnoopLogOptions() {
        BluetoothAdapter.getDefaultAdapter().configHciSnoopLog(this.mBtHciSnoopLog.isChecked());
        Secure.putInt(getActivity().getContentResolver(), "bluetooth_hci_log", this.mBtHciSnoopLog.isChecked() ? 1 : 0);
    }

    private void writeDebuggerOptions() {
        try {
            ActivityManagerNative.getDefault().setDebugApp(this.mDebugApp, this.mWaitForDebugger.isChecked(), true);
        } catch (RemoteException e) {
        }
    }

    private static void resetDebuggerOptions() {
        try {
            ActivityManagerNative.getDefault().setDebugApp(null, false, true);
        } catch (RemoteException e) {
        }
    }

    private void updateDebuggerOptions() {
        this.mDebugApp = Global.getString(getActivity().getContentResolver(), "debug_app");
        updateCheckBox(this.mWaitForDebugger, Global.getInt(getActivity().getContentResolver(), "wait_for_debugger", 0) != 0);
        if (this.mDebugApp == null || this.mDebugApp.length() <= 0) {
            this.mDebugAppPref.setSummary(getResources().getString(R.string.debug_app_not_set));
            this.mWaitForDebugger.setEnabled(false);
            return;
        }
        String label;
        try {
            CharSequence lab = getActivity().getPackageManager().getApplicationLabel(getActivity().getPackageManager().getApplicationInfo(this.mDebugApp, 512));
            label = lab != null ? lab.toString() : this.mDebugApp;
        } catch (NameNotFoundException e) {
            label = this.mDebugApp;
        }
        this.mDebugAppPref.setSummary(getResources().getString(R.string.debug_app_set, new Object[]{label}));
        this.mWaitForDebugger.setEnabled(true);
        this.mHaveDebugSettings = true;
    }

    private void updateVerifyAppsOverUsbOptions() {
        boolean z = true;
        CheckBoxPreference checkBoxPreference = this.mVerifyAppsOverUsb;
        if (Global.getInt(getActivity().getContentResolver(), "verifier_verify_adb_installs", 1) == 0) {
            z = false;
        }
        updateCheckBox(checkBoxPreference, z);
        this.mVerifyAppsOverUsb.setEnabled(enableVerifierSetting());
    }

    private void writeVerifyAppsOverUsbOptions() {
        Global.putInt(getActivity().getContentResolver(), "verifier_verify_adb_installs", this.mVerifyAppsOverUsb.isChecked() ? 1 : 0);
    }

    private boolean enableVerifierSetting() {
        ContentResolver cr = getActivity().getContentResolver();
        if (Global.getInt(cr, "adb_enabled", 0) == 0 || Global.getInt(cr, "package_verifier_enable", 1) == 0) {
            return false;
        }
        PackageManager pm = getActivity().getPackageManager();
        Intent verification = new Intent("android.intent.action.PACKAGE_NEEDS_VERIFICATION");
        verification.setType("application/vnd.android.package-archive");
        verification.addFlags(1);
        if (pm.queryBroadcastReceivers(verification, 0).size() != 0) {
            return true;
        }
        return false;
    }

    private boolean showVerifierSetting() {
        return Global.getInt(getActivity().getContentResolver(), "verifier_setting_visible", 1) > 0;
    }

    private static boolean showEnableOemUnlockPreference() {
        return !SystemProperties.get("ro.frp.pst").equals("");
    }

    private void updateBugreportOptions() {
        if ("user".equals(Build.TYPE)) {
            boolean adbEnabled;
            ContentResolver resolver = getActivity().getContentResolver();
            if (Global.getInt(resolver, "adb_enabled", 0) != 0) {
                adbEnabled = true;
            } else {
                adbEnabled = false;
            }
            if (adbEnabled) {
                this.mBugreport.setEnabled(true);
                this.mBugreportInPower.setEnabled(true);
                return;
            }
            this.mBugreport.setEnabled(false);
            this.mBugreportInPower.setEnabled(false);
            this.mBugreportInPower.setChecked(false);
            Secure.putInt(resolver, "bugreport_in_power_menu", 0);
            return;
        }
        this.mBugreportInPower.setEnabled(true);
    }

    private static int currentStrictModeActiveIndex() {
        if (TextUtils.isEmpty(SystemProperties.get("persist.sys.strictmode.visual"))) {
            return 0;
        }
        return SystemProperties.getBoolean("persist.sys.strictmode.visual", false) ? 1 : 2;
    }

    private void writeStrictModeVisualOptions() {
        try {
            this.mWindowManager.setStrictModeVisualIndicatorPreference(this.mStrictMode.isChecked() ? "1" : "");
        } catch (RemoteException e) {
        }
    }

    private void updateStrictModeVisualOptions() {
        boolean z = true;
        CheckBoxPreference checkBoxPreference = this.mStrictMode;
        if (currentStrictModeActiveIndex() != 1) {
            z = false;
        }
        updateCheckBox(checkBoxPreference, z);
    }

    private void writePointerLocationOptions() {
        System.putInt(getActivity().getContentResolver(), "pointer_location", this.mPointerLocation.isChecked() ? 1 : 0);
    }

    private void updatePointerLocationOptions() {
        boolean z = false;
        CheckBoxPreference checkBoxPreference = this.mPointerLocation;
        if (System.getInt(getActivity().getContentResolver(), "pointer_location", 0) != 0) {
            z = true;
        }
        updateCheckBox(checkBoxPreference, z);
    }

    private void writeShowTouchesOptions() {
        System.putInt(getActivity().getContentResolver(), "show_touches", this.mShowTouches.isChecked() ? 1 : 0);
    }

    private void updateShowTouchesOptions() {
        boolean z = false;
        CheckBoxPreference checkBoxPreference = this.mShowTouches;
        if (System.getInt(getActivity().getContentResolver(), "show_touches", 0) != 0) {
            z = true;
        }
        updateCheckBox(checkBoxPreference, z);
    }

    private void updateFlingerOptions() {
        boolean z = true;
        try {
            IBinder flinger = ServiceManager.getService("SurfaceFlinger");
            if (flinger != null) {
                boolean z2;
                Parcel data = Parcel.obtain();
                Parcel reply = Parcel.obtain();
                data.writeInterfaceToken("android.ui.ISurfaceComposer");
                flinger.transact(1010, data, reply, 0);
                int showCpu = reply.readInt();
                int enableGL = reply.readInt();
                int showUpdates = reply.readInt();
                CheckBoxPreference checkBoxPreference = this.mShowScreenUpdates;
                if (showUpdates != 0) {
                    z2 = true;
                } else {
                    z2 = false;
                }
                updateCheckBox(checkBoxPreference, z2);
                int showBackground = reply.readInt();
                int disableOverlays = reply.readInt();
                CheckBoxPreference checkBoxPreference2 = this.mDisableOverlays;
                if (disableOverlays == 0) {
                    z = false;
                }
                updateCheckBox(checkBoxPreference2, z);
                reply.recycle();
                data.recycle();
            }
        } catch (RemoteException e) {
        }
    }

    private void writeShowUpdatesOption() {
        int showUpdates = 0;
        try {
            IBinder flinger = ServiceManager.getService("SurfaceFlinger");
            if (flinger != null) {
                Parcel data = Parcel.obtain();
                data.writeInterfaceToken("android.ui.ISurfaceComposer");
                if (this.mShowScreenUpdates.isChecked()) {
                    showUpdates = 1;
                }
                data.writeInt(showUpdates);
                flinger.transact(1002, data, null, 0);
                data.recycle();
                updateFlingerOptions();
            }
        } catch (RemoteException e) {
        }
    }

    private void writeDisableOverlaysOption() {
        int disableOverlays = 0;
        try {
            IBinder flinger = ServiceManager.getService("SurfaceFlinger");
            if (flinger != null) {
                Parcel data = Parcel.obtain();
                data.writeInterfaceToken("android.ui.ISurfaceComposer");
                if (this.mDisableOverlays.isChecked()) {
                    disableOverlays = 1;
                }
                data.writeInt(disableOverlays);
                flinger.transact(1008, data, null, 0);
                data.recycle();
                updateFlingerOptions();
            }
        } catch (RemoteException e) {
        }
    }

    private void updateHardwareUiOptions() {
        updateCheckBox(this.mForceHardwareUi, SystemProperties.getBoolean("persist.sys.ui.hw", false));
    }

    private void writeHardwareUiOptions() {
        SystemProperties.set("persist.sys.ui.hw", this.mForceHardwareUi.isChecked() ? "true" : "false");
        pokeSystemProperties();
    }

    private void updateMsaaOptions() {
        updateCheckBox(this.mForceMsaa, SystemProperties.getBoolean("debug.egl.force_msaa", false));
    }

    private void writeMsaaOptions() {
        SystemProperties.set("debug.egl.force_msaa", this.mForceMsaa.isChecked() ? "true" : "false");
        pokeSystemProperties();
    }

    private void updateTrackFrameTimeOptions() {
        String value = SystemProperties.get("debug.hwui.profile");
        if (value == null) {
            value = "";
        }
        CharSequence[] values = this.mTrackFrameTime.getEntryValues();
        for (int i = 0; i < values.length; i++) {
            if (value.contentEquals(values[i])) {
                this.mTrackFrameTime.setValueIndex(i);
                this.mTrackFrameTime.setSummary(this.mTrackFrameTime.getEntries()[i]);
                return;
            }
        }
        this.mTrackFrameTime.setValueIndex(0);
        this.mTrackFrameTime.setSummary(this.mTrackFrameTime.getEntries()[0]);
    }

    private void writeTrackFrameTimeOptions(Object newValue) {
        SystemProperties.set("debug.hwui.profile", newValue == null ? "" : newValue.toString());
        pokeSystemProperties();
        updateTrackFrameTimeOptions();
    }

    private void updateShowNonRectClipOptions() {
        String value = SystemProperties.get("debug.hwui.show_non_rect_clip");
        if (value == null) {
            value = "hide";
        }
        CharSequence[] values = this.mShowNonRectClip.getEntryValues();
        for (int i = 0; i < values.length; i++) {
            if (value.contentEquals(values[i])) {
                this.mShowNonRectClip.setValueIndex(i);
                this.mShowNonRectClip.setSummary(this.mShowNonRectClip.getEntries()[i]);
                return;
            }
        }
        this.mShowNonRectClip.setValueIndex(0);
        this.mShowNonRectClip.setSummary(this.mShowNonRectClip.getEntries()[0]);
    }

    private void writeShowNonRectClipOptions(Object newValue) {
        SystemProperties.set("debug.hwui.show_non_rect_clip", newValue == null ? "" : newValue.toString());
        pokeSystemProperties();
        updateShowNonRectClipOptions();
    }

    private void updateShowHwScreenUpdatesOptions() {
        updateCheckBox(this.mShowHwScreenUpdates, SystemProperties.getBoolean("debug.hwui.show_dirty_regions", false));
    }

    private void writeShowHwScreenUpdatesOptions() {
        SystemProperties.set("debug.hwui.show_dirty_regions", this.mShowHwScreenUpdates.isChecked() ? "true" : null);
        pokeSystemProperties();
    }

    private void updateShowHwLayersUpdatesOptions() {
        updateCheckBox(this.mShowHwLayersUpdates, SystemProperties.getBoolean("debug.hwui.show_layers_updates", false));
    }

    private void writeShowHwLayersUpdatesOptions() {
        SystemProperties.set("debug.hwui.show_layers_updates", this.mShowHwLayersUpdates.isChecked() ? "true" : null);
        pokeSystemProperties();
    }

    private void updateDebugHwOverdrawOptions() {
        String value = SystemProperties.get("debug.hwui.overdraw");
        if (value == null) {
            value = "";
        }
        CharSequence[] values = this.mDebugHwOverdraw.getEntryValues();
        for (int i = 0; i < values.length; i++) {
            if (value.contentEquals(values[i])) {
                this.mDebugHwOverdraw.setValueIndex(i);
                this.mDebugHwOverdraw.setSummary(this.mDebugHwOverdraw.getEntries()[i]);
                return;
            }
        }
        this.mDebugHwOverdraw.setValueIndex(0);
        this.mDebugHwOverdraw.setSummary(this.mDebugHwOverdraw.getEntries()[0]);
    }

    private void writeDebugHwOverdrawOptions(Object newValue) {
        SystemProperties.set("debug.hwui.overdraw", newValue == null ? "" : newValue.toString());
        pokeSystemProperties();
        updateDebugHwOverdrawOptions();
    }

    private void updateDebugLayoutOptions() {
        updateCheckBox(this.mDebugLayout, SystemProperties.getBoolean("debug.layout", false));
    }

    private void writeDebugLayoutOptions() {
        SystemProperties.set("debug.layout", this.mDebugLayout.isChecked() ? "true" : "false");
        pokeSystemProperties();
    }

    private void updateSimulateColorSpace() {
        boolean enabled;
        ContentResolver cr = getContentResolver();
        if (Secure.getInt(cr, "accessibility_display_daltonizer_enabled", 0) != 0) {
            enabled = true;
        } else {
            enabled = false;
        }
        if (enabled) {
            String mode = Integer.toString(Secure.getInt(cr, "accessibility_display_daltonizer", -1));
            this.mSimulateColorSpace.setValue(mode);
            if (this.mSimulateColorSpace.findIndexOfValue(mode) < 0) {
                this.mSimulateColorSpace.setSummary(getString(R.string.daltonizer_type_overridden, new Object[]{getString(R.string.accessibility_display_daltonizer_preference_title)}));
                return;
            }
            this.mSimulateColorSpace.setSummary("%s");
            return;
        }
        this.mSimulateColorSpace.setValue(Integer.toString(-1));
    }

    private boolean usingDevelopmentColorSpace() {
        boolean enabled;
        ContentResolver cr = getContentResolver();
        if (Secure.getInt(cr, "accessibility_display_daltonizer_enabled", 0) != 0) {
            enabled = true;
        } else {
            enabled = false;
        }
        if (enabled) {
            if (this.mSimulateColorSpace.findIndexOfValue(Integer.toString(Secure.getInt(cr, "accessibility_display_daltonizer", -1))) >= 0) {
                return true;
            }
        }
        return false;
    }

    private void writeSimulateColorSpace(Object value) {
        ContentResolver cr = getContentResolver();
        int newMode = Integer.parseInt(value.toString());
        if (newMode < 0) {
            Secure.putInt(cr, "accessibility_display_daltonizer_enabled", 0);
            return;
        }
        Secure.putInt(cr, "accessibility_display_daltonizer_enabled", 1);
        Secure.putInt(cr, "accessibility_display_daltonizer", newMode);
    }

    private void updateUseNuplayerOptions() {
        boolean z = false;
        CheckBoxPreference checkBoxPreference = this.mUseNuplayer;
        if (!SystemProperties.getBoolean("persist.sys.media.use-awesome", false)) {
            z = true;
        }
        updateCheckBox(checkBoxPreference, z);
    }

    private void writeUseNuplayerOptions() {
        SystemProperties.set("persist.sys.media.use-awesome", this.mUseNuplayer.isChecked() ? "false" : "true");
        pokeSystemProperties();
    }

    private void updateUSBAudioOptions() {
        boolean z = false;
        CheckBoxPreference checkBoxPreference = this.mUSBAudio;
        if (Secure.getInt(getContentResolver(), "usb_audio_automatic_routing_disabled", 0) != 0) {
            z = true;
        }
        updateCheckBox(checkBoxPreference, z);
    }

    private void writeUSBAudioOptions() {
        Secure.putInt(getContentResolver(), "usb_audio_automatic_routing_disabled", this.mUSBAudio.isChecked() ? 1 : 0);
    }

    private void updateForceRtlOptions() {
        boolean z = false;
        CheckBoxPreference checkBoxPreference = this.mForceRtlLayout;
        if (Global.getInt(getActivity().getContentResolver(), "debug.force_rtl", 0) != 0) {
            z = true;
        }
        updateCheckBox(checkBoxPreference, z);
    }

    private void writeForceRtlOptions() {
        boolean value = this.mForceRtlLayout.isChecked();
        Global.putInt(getActivity().getContentResolver(), "debug.force_rtl", value ? 1 : 0);
        SystemProperties.set("debug.force_rtl", value ? "1" : "0");
        LocalePicker.updateLocale(getActivity().getResources().getConfiguration().locale);
    }

    private void updateWifiDisplayCertificationOptions() {
        boolean z = false;
        CheckBoxPreference checkBoxPreference = this.mWifiDisplayCertification;
        if (Global.getInt(getActivity().getContentResolver(), "wifi_display_certification_on", 0) != 0) {
            z = true;
        }
        updateCheckBox(checkBoxPreference, z);
    }

    private void writeWifiDisplayCertificationOptions() {
        Global.putInt(getActivity().getContentResolver(), "wifi_display_certification_on", this.mWifiDisplayCertification.isChecked() ? 1 : 0);
    }

    private void updateWifiVerboseLoggingOptions() {
        updateCheckBox(this.mWifiVerboseLogging, this.mWifiManager.getVerboseLoggingLevel() > 0);
    }

    private void writeWifiVerboseLoggingOptions() {
        this.mWifiManager.enableVerboseLogging(this.mWifiVerboseLogging.isChecked() ? 1 : 0);
    }

    private void updateWifiAggressiveHandoverOptions() {
        updateCheckBox(this.mWifiAggressiveHandover, this.mWifiManager.getAggressiveHandover() > 0);
    }

    private void writeWifiAggressiveHandoverOptions() {
        this.mWifiManager.enableAggressiveHandover(this.mWifiAggressiveHandover.isChecked() ? 1 : 0);
    }

    private void updateWifiAllowScansWithTrafficOptions() {
        updateCheckBox(this.mWifiAllowScansWithTraffic, this.mWifiManager.getAllowScansWithTraffic() > 0);
    }

    private void writeWifiAllowScansWithTrafficOptions() {
        this.mWifiManager.setAllowScansWithTraffic(this.mWifiAllowScansWithTraffic.isChecked() ? 1 : 0);
    }

    private void updateLogdSizeValues() {
        if (this.mLogdSize != null) {
            String currentValue = SystemProperties.get("persist.logd.size");
            if (currentValue == null) {
                currentValue = SystemProperties.get("ro.logd.size");
                if (currentValue == null) {
                    currentValue = "256K";
                }
            }
            String[] values = getResources().getStringArray(R.array.select_logd_size_values);
            String[] titles = getResources().getStringArray(R.array.select_logd_size_titles);
            if (SystemProperties.get("ro.config.low_ram").equals("true")) {
                this.mLogdSize.setEntries(R.array.select_logd_size_lowram_titles);
                titles = getResources().getStringArray(R.array.select_logd_size_lowram_titles);
            }
            String[] summaries = getResources().getStringArray(R.array.select_logd_size_summaries);
            int index = 1;
            int i = 0;
            while (i < titles.length) {
                if (currentValue.equals(values[i]) || currentValue.equals(titles[i])) {
                    index = i;
                    break;
                }
                i++;
            }
            this.mLogdSize.setValue(values[index]);
            this.mLogdSize.setSummary(summaries[index]);
            this.mLogdSize.setOnPreferenceChangeListener(this);
        }
    }

    private void writeLogdSizeOption(Object newValue) {
        String currentValue = SystemProperties.get("ro.logd.size");
        if (currentValue != null) {
            DEFAULT_LOG_RING_BUFFER_SIZE_IN_BYTES = currentValue;
        }
        String size = newValue != null ? newValue.toString() : DEFAULT_LOG_RING_BUFFER_SIZE_IN_BYTES;
        SystemProperties.set("persist.logd.size", size);
        pokeSystemProperties();
        try {
            Runtime.getRuntime().exec("logcat -b all -G " + size).waitFor();
            Log.i("DevelopmentSettings", "Logcat ring buffer sizes set to: " + size);
        } catch (Exception e) {
            Log.w("DevelopmentSettings", "Cannot set logcat ring buffer sizes", e);
        }
        updateLogdSizeValues();
    }

    private void updateCpuUsageOptions() {
        boolean z = false;
        CheckBoxPreference checkBoxPreference = this.mShowCpuUsage;
        if (Global.getInt(getActivity().getContentResolver(), "show_processes", 0) != 0) {
            z = true;
        }
        updateCheckBox(checkBoxPreference, z);
    }

    private void writeCpuUsageOptions() {
        boolean value = this.mShowCpuUsage.isChecked();
        Global.putInt(getActivity().getContentResolver(), "show_processes", value ? 1 : 0);
        Intent service = new Intent().setClassName("com.android.systemui", "com.android.systemui.LoadAverageService");
        if (value) {
            getActivity().startService(service);
        } else {
            getActivity().stopService(service);
        }
    }

    private void writeImmediatelyDestroyActivitiesOptions() {
        try {
            ActivityManagerNative.getDefault().setAlwaysFinish(this.mImmediatelyDestroyActivities.isChecked());
        } catch (RemoteException e) {
        }
    }

    private void updateImmediatelyDestroyActivitiesOptions() {
        boolean z = false;
        CheckBoxPreference checkBoxPreference = this.mImmediatelyDestroyActivities;
        if (Global.getInt(getActivity().getContentResolver(), "always_finish_activities", 0) != 0) {
            z = true;
        }
        updateCheckBox(checkBoxPreference, z);
    }

    private void updateAnimationScaleValue(int which, ListPreference pref) {
        try {
            float scale = this.mWindowManager.getAnimationScale(which);
            if (scale != 1.0f) {
                this.mHaveDebugSettings = true;
            }
            CharSequence[] values = pref.getEntryValues();
            for (int i = 0; i < values.length; i++) {
                if (scale <= Float.parseFloat(values[i].toString())) {
                    pref.setValueIndex(i);
                    pref.setSummary(pref.getEntries()[i]);
                    return;
                }
            }
            pref.setValueIndex(values.length - 1);
            pref.setSummary(pref.getEntries()[0]);
        } catch (RemoteException e) {
        }
    }

    private void updateAnimationScaleOptions() {
        updateAnimationScaleValue(0, this.mWindowAnimationScale);
        updateAnimationScaleValue(1, this.mTransitionAnimationScale);
        updateAnimationScaleValue(2, this.mAnimatorDurationScale);
    }

    private void writeAnimationScaleOption(int which, ListPreference pref, Object newValue) {
        float scale;
        if (newValue != null) {
            try {
                scale = Float.parseFloat(newValue.toString());
            } catch (RemoteException e) {
                return;
            }
        }
        scale = 1.0f;
        this.mWindowManager.setAnimationScale(which, scale);
        updateAnimationScaleValue(which, pref);
    }

    private void updateOverlayDisplayDevicesOptions() {
        String value = Global.getString(getActivity().getContentResolver(), "overlay_display_devices");
        if (value == null) {
            value = "";
        }
        CharSequence[] values = this.mOverlayDisplayDevices.getEntryValues();
        for (int i = 0; i < values.length; i++) {
            if (value.contentEquals(values[i])) {
                this.mOverlayDisplayDevices.setValueIndex(i);
                this.mOverlayDisplayDevices.setSummary(this.mOverlayDisplayDevices.getEntries()[i]);
                return;
            }
        }
        this.mOverlayDisplayDevices.setValueIndex(0);
        this.mOverlayDisplayDevices.setSummary(this.mOverlayDisplayDevices.getEntries()[0]);
    }

    private void writeOverlayDisplayDevicesOptions(Object newValue) {
        Global.putString(getActivity().getContentResolver(), "overlay_display_devices", (String) newValue);
        updateOverlayDisplayDevicesOptions();
    }

    private void updateOpenGLTracesOptions() {
        String value = SystemProperties.get("debug.egl.trace");
        if (value == null) {
            value = "";
        }
        CharSequence[] values = this.mOpenGLTraces.getEntryValues();
        for (int i = 0; i < values.length; i++) {
            if (value.contentEquals(values[i])) {
                this.mOpenGLTraces.setValueIndex(i);
                this.mOpenGLTraces.setSummary(this.mOpenGLTraces.getEntries()[i]);
                return;
            }
        }
        this.mOpenGLTraces.setValueIndex(0);
        this.mOpenGLTraces.setSummary(this.mOpenGLTraces.getEntries()[0]);
    }

    private void writeOpenGLTracesOptions(Object newValue) {
        SystemProperties.set("debug.egl.trace", newValue == null ? "" : newValue.toString());
        pokeSystemProperties();
        updateOpenGLTracesOptions();
    }

    private void updateAppProcessLimitOptions() {
        try {
            int limit = ActivityManagerNative.getDefault().getProcessLimit();
            CharSequence[] values = this.mAppProcessLimit.getEntryValues();
            for (int i = 0; i < values.length; i++) {
                if (Integer.parseInt(values[i].toString()) >= limit) {
                    if (i != 0) {
                        this.mHaveDebugSettings = true;
                    }
                    this.mAppProcessLimit.setValueIndex(i);
                    this.mAppProcessLimit.setSummary(this.mAppProcessLimit.getEntries()[i]);
                    return;
                }
            }
            this.mAppProcessLimit.setValueIndex(0);
            this.mAppProcessLimit.setSummary(this.mAppProcessLimit.getEntries()[0]);
        } catch (RemoteException e) {
        }
    }

    private void writeAppProcessLimitOptions(Object newValue) {
        int limit;
        if (newValue != null) {
            try {
                limit = Integer.parseInt(newValue.toString());
            } catch (RemoteException e) {
                return;
            }
        }
        limit = -1;
        ActivityManagerNative.getDefault().setProcessLimit(limit);
        updateAppProcessLimitOptions();
    }

    private void writeShowAllANRsOptions() {
        Secure.putInt(getActivity().getContentResolver(), "anr_show_background", this.mShowAllANRs.isChecked() ? 1 : 0);
    }

    private void updateShowAllANRsOptions() {
        boolean z = false;
        CheckBoxPreference checkBoxPreference = this.mShowAllANRs;
        if (Secure.getInt(getActivity().getContentResolver(), "anr_show_background", 0) != 0) {
            z = true;
        }
        updateCheckBox(checkBoxPreference, z);
    }

    public void onSwitchChanged(Switch switchView, boolean isChecked) {
        if (switchView != this.mSwitchBar.getSwitch() || isChecked == this.mLastEnabledState) {
            return;
        }
        if (isChecked) {
            this.mDialogClicked = false;
            if (this.mEnableDialog != null) {
                dismissDialogs();
            }
            this.mEnableDialog = new Builder(getActivity()).setMessage(getActivity().getResources().getString(R.string.dev_settings_warning_message)).setTitle(R.string.dev_settings_warning_title).setPositiveButton(17039379, this).setNegativeButton(17039369, this).show();
            this.mEnableDialog.setOnDismissListener(this);
            return;
        }
        resetDangerousOptions();
        Global.putInt(getActivity().getContentResolver(), "development_settings_enabled", 0);
        this.mLastEnabledState = isChecked;
        setPrefsEnabledState(this.mLastEnabledState);
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode != 1000) {
            super.onActivityResult(requestCode, resultCode, data);
        } else if (resultCode == -1) {
            this.mDebugApp = data.getAction();
            writeDebuggerOptions();
            updateDebuggerOptions();
        }
    }

    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        int i = 1;
        if (Utils.isMonkeyRunning()) {
            return false;
        }
        if (preference == this.mEnableAdb) {
            if (this.mEnableAdb.isChecked()) {
                this.mDialogClicked = false;
                if (this.mAdbDialog != null) {
                    dismissDialogs();
                }
                this.mAdbDialog = new Builder(getActivity()).setMessage(getActivity().getResources().getString(R.string.adb_warning_message)).setTitle(R.string.adb_warning_title).setPositiveButton(17039379, this).setNegativeButton(17039369, this).show();
                this.mAdbDialog.setOnDismissListener(this);
                return false;
            }
            Global.putInt(getActivity().getContentResolver(), "adb_enabled", 0);
            this.mVerifyAppsOverUsb.setEnabled(false);
            this.mVerifyAppsOverUsb.setChecked(false);
            updateBugreportOptions();
            return false;
        } else if (preference == this.mClearAdbKeys) {
            if (this.mAdbKeysDialog != null) {
                dismissDialogs();
            }
            this.mAdbKeysDialog = new Builder(getActivity()).setMessage(R.string.adb_keys_warning_message).setPositiveButton(17039370, this).setNegativeButton(17039360, null).show();
            return false;
        } else if (preference == this.mEnableTerminal) {
            PackageManager pm = getActivity().getPackageManager();
            String str = "com.android.terminal";
            if (!this.mEnableTerminal.isChecked()) {
                i = 0;
            }
            pm.setApplicationEnabledSetting(str, i, 0);
            return false;
        } else if (preference == this.mBugreportInPower) {
            r3 = getActivity().getContentResolver();
            r4 = "bugreport_in_power_menu";
            if (!this.mBugreportInPower.isChecked()) {
                i = 0;
            }
            Secure.putInt(r3, r4, i);
            return false;
        } else if (preference == this.mKeepScreenOn) {
            Global.putInt(getActivity().getContentResolver(), "stay_on_while_plugged_in", this.mKeepScreenOn.isChecked() ? 3 : 0);
            return false;
        } else if (preference == this.mBtHciSnoopLog) {
            writeBtHciSnoopLogOptions();
            return false;
        } else if (preference == this.mEnableOemUnlock) {
            Utils.setOemUnlockEnabled(getActivity(), this.mEnableOemUnlock.isChecked());
            return false;
        } else if (preference == this.mAllowMockLocation) {
            r3 = getActivity().getContentResolver();
            r4 = "mock_location";
            if (!this.mAllowMockLocation.isChecked()) {
                i = 0;
            }
            Secure.putInt(r3, r4, i);
            return false;
        } else if (preference == this.mDebugViewAttributes) {
            r3 = getActivity().getContentResolver();
            r4 = "debug_view_attributes";
            if (!this.mDebugViewAttributes.isChecked()) {
                i = 0;
            }
            Global.putInt(r3, r4, i);
            return false;
        } else if (preference == this.mDebugAppPref) {
            startActivityForResult(new Intent(getActivity(), AppPicker.class), 1000);
            return false;
        } else if (preference == this.mWaitForDebugger) {
            writeDebuggerOptions();
            return false;
        } else if (preference == this.mVerifyAppsOverUsb) {
            writeVerifyAppsOverUsbOptions();
            return false;
        } else if (preference == this.mStrictMode) {
            writeStrictModeVisualOptions();
            return false;
        } else if (preference == this.mPointerLocation) {
            writePointerLocationOptions();
            return false;
        } else if (preference == this.mShowTouches) {
            writeShowTouchesOptions();
            return false;
        } else if (preference == this.mShowScreenUpdates) {
            writeShowUpdatesOption();
            return false;
        } else if (preference == this.mDisableOverlays) {
            writeDisableOverlaysOption();
            return false;
        } else if (preference == this.mShowCpuUsage) {
            writeCpuUsageOptions();
            return false;
        } else if (preference == this.mImmediatelyDestroyActivities) {
            writeImmediatelyDestroyActivitiesOptions();
            return false;
        } else if (preference == this.mShowAllANRs) {
            writeShowAllANRsOptions();
            return false;
        } else if (preference == this.mForceHardwareUi) {
            writeHardwareUiOptions();
            return false;
        } else if (preference == this.mForceMsaa) {
            writeMsaaOptions();
            return false;
        } else if (preference == this.mShowHwScreenUpdates) {
            writeShowHwScreenUpdatesOptions();
            return false;
        } else if (preference == this.mShowHwLayersUpdates) {
            writeShowHwLayersUpdatesOptions();
            return false;
        } else if (preference == this.mDebugLayout) {
            writeDebugLayoutOptions();
            return false;
        } else if (preference == this.mForceRtlLayout) {
            writeForceRtlOptions();
            return false;
        } else if (preference == this.mWifiDisplayCertification) {
            writeWifiDisplayCertificationOptions();
            return false;
        } else if (preference == this.mWifiVerboseLogging) {
            writeWifiVerboseLoggingOptions();
            return false;
        } else if (preference == this.mWifiAggressiveHandover) {
            writeWifiAggressiveHandoverOptions();
            return false;
        } else if (preference == this.mWifiAllowScansWithTraffic) {
            writeWifiAllowScansWithTrafficOptions();
            return false;
        } else if (preference == this.mUseNuplayer) {
            writeUseNuplayerOptions();
            return false;
        } else if (preference != this.mUSBAudio) {
            return super.onPreferenceTreeClick(preferenceScreen, preference);
        } else {
            writeUSBAudioOptions();
            return false;
        }
    }

    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if ("hdcp_checking".equals(preference.getKey())) {
            SystemProperties.set("persist.sys.hdcp_checking", newValue.toString());
            updateHdcpValues();
            pokeSystemProperties();
            return true;
        } else if (preference == this.mLogdSize) {
            writeLogdSizeOption(newValue);
            return true;
        } else if (preference == this.mWindowAnimationScale) {
            writeAnimationScaleOption(0, this.mWindowAnimationScale, newValue);
            return true;
        } else if (preference == this.mTransitionAnimationScale) {
            writeAnimationScaleOption(1, this.mTransitionAnimationScale, newValue);
            return true;
        } else if (preference == this.mAnimatorDurationScale) {
            writeAnimationScaleOption(2, this.mAnimatorDurationScale, newValue);
            return true;
        } else if (preference == this.mOverlayDisplayDevices) {
            writeOverlayDisplayDevicesOptions(newValue);
            return true;
        } else if (preference == this.mOpenGLTraces) {
            writeOpenGLTracesOptions(newValue);
            return true;
        } else if (preference == this.mTrackFrameTime) {
            writeTrackFrameTimeOptions(newValue);
            return true;
        } else if (preference == this.mDebugHwOverdraw) {
            writeDebugHwOverdrawOptions(newValue);
            return true;
        } else if (preference == this.mShowNonRectClip) {
            writeShowNonRectClipOptions(newValue);
            return true;
        } else if (preference == this.mAppProcessLimit) {
            writeAppProcessLimitOptions(newValue);
            return true;
        } else if (preference != this.mSimulateColorSpace) {
            return false;
        } else {
            writeSimulateColorSpace(newValue);
            return true;
        }
    }

    private void dismissDialogs() {
        if (this.mAdbDialog != null) {
            this.mAdbDialog.dismiss();
            this.mAdbDialog = null;
        }
        if (this.mAdbKeysDialog != null) {
            this.mAdbKeysDialog.dismiss();
            this.mAdbKeysDialog = null;
        }
        if (this.mEnableDialog != null) {
            this.mEnableDialog.dismiss();
            this.mEnableDialog = null;
        }
    }

    public void onClick(DialogInterface dialog, int which) {
        if (dialog == this.mAdbDialog) {
            if (which == -1) {
                this.mDialogClicked = true;
                Global.putInt(getActivity().getContentResolver(), "adb_enabled", 1);
                this.mVerifyAppsOverUsb.setEnabled(true);
                updateVerifyAppsOverUsbOptions();
                updateBugreportOptions();
                return;
            }
            this.mEnableAdb.setChecked(false);
        } else if (dialog == this.mAdbKeysDialog) {
            if (which == -1) {
                try {
                    IUsbManager.Stub.asInterface(ServiceManager.getService("usb")).clearUsbDebuggingKeys();
                } catch (RemoteException e) {
                    Log.e("DevelopmentSettings", "Unable to clear adb keys", e);
                }
            }
        } else if (dialog != this.mEnableDialog) {
        } else {
            if (which == -1) {
                this.mDialogClicked = true;
                Global.putInt(getActivity().getContentResolver(), "development_settings_enabled", 1);
                this.mLastEnabledState = true;
                setPrefsEnabledState(this.mLastEnabledState);
                return;
            }
            this.mSwitchBar.setChecked(false);
        }
    }

    public void onDismiss(DialogInterface dialog) {
        if (dialog == this.mAdbDialog) {
            if (!this.mDialogClicked) {
                this.mEnableAdb.setChecked(false);
            }
            this.mAdbDialog = null;
        } else if (dialog == this.mEnableDialog) {
            if (!this.mDialogClicked) {
                this.mSwitchBar.setChecked(false);
            }
            this.mEnableDialog = null;
        }
    }

    public void onDestroy() {
        dismissDialogs();
        super.onDestroy();
    }

    void pokeSystemProperties() {
        if (!this.mDontPokeProperties) {
            new SystemPropPoker().execute(new Void[0]);
        }
    }

    private static boolean isPackageInstalled(Context context, String packageName) {
        try {
            return context.getPackageManager().getPackageInfo(packageName, 0) != null;
        } catch (NameNotFoundException e) {
            return false;
        }
    }
}
