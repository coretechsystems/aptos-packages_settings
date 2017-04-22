package com.android.settings;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.os.Build.VERSION;
import android.os.Bundle;
import android.os.SELinux;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.os.UserManager;
import android.preference.Preference;
import android.preference.PreferenceGroup;
import android.preference.PreferenceScreen;
import android.provider.SearchIndexableResource;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;
import com.android.internal.app.PlatLogoActivity;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settings.search.Index;
import com.android.settings.search.Indexable;
import com.android.settings.search.Indexable.SearchIndexProvider;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DeviceInfoSettings extends SettingsPreferenceFragment implements Indexable {
    public static final SearchIndexProvider SEARCH_INDEX_DATA_PROVIDER = new BaseSearchIndexProvider() {
        public List<SearchIndexableResource> getXmlResourcesToIndex(Context context, boolean enabled) {
            new SearchIndexableResource(context).xmlResId = R.xml.device_info_settings;
            return Arrays.asList(new SearchIndexableResource[]{sir});
        }

        public List<String> getNonIndexableKeys(Context context) {
            List<String> keys = new ArrayList();
            if (isPropertyMissing("ro.build.selinux")) {
                keys.add("selinux_status");
            }
            if (isPropertyMissing("ro.url.safetylegal")) {
                keys.add("safetylegal");
            }
            if (isPropertyMissing("ro.ril.fccid")) {
                keys.add("fcc_equipment_id");
            }
            if (Utils.isWifiOnly(context)) {
                keys.add("baseband_version");
            }
            if (TextUtils.isEmpty(DeviceInfoSettings.getFeedbackReporterPackage(context))) {
                keys.add("device_feedback");
            }
            if (!checkIntentAction(context, "android.settings.TERMS")) {
                keys.add("terms");
            }
            if (!checkIntentAction(context, "android.settings.LICENSE")) {
                keys.add("license");
            }
            if (!checkIntentAction(context, "android.settings.COPYRIGHT")) {
                keys.add("copyright");
            }
            if (UserHandle.myUserId() != 0) {
                keys.add("system_update_settings");
            }
            if (!context.getResources().getBoolean(R.bool.config_additional_system_update_setting_enable)) {
                keys.add("additional_system_update_settings");
            }
            return keys;
        }

        private boolean isPropertyMissing(String property) {
            return SystemProperties.get(property).equals("");
        }

        private boolean checkIntentAction(Context context, String action) {
            List<ResolveInfo> list = context.getPackageManager().queryIntentActivities(new Intent(action), 0);
            int listSize = list.size();
            for (int i = 0; i < listSize; i++) {
                if ((((ResolveInfo) list.get(i)).activityInfo.applicationInfo.flags & 1) != 0) {
                    return true;
                }
            }
            return false;
        }
    };
    int mDevHitCountdown;
    Toast mDevHitToast;
    long[] mHits = new long[3];

    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        addPreferencesFromResource(R.xml.device_info_settings);
        setStringSummary("firmware_version", VERSION.RELEASE);
        findPreference("firmware_version").setEnabled(true);
        setValueSummary("baseband_version", "gsm.version.baseband");
        setStringSummary("device_model", Build.MODEL + getMsvSuffix());
        setValueSummary("fcc_equipment_id", "ro.ril.fccid");
        setStringSummary("device_model", Build.MODEL);
        setStringSummary("build_number", Build.DISPLAY);
        findPreference("build_number").setEnabled(true);
        findPreference("kernel_version").setSummary(getFormattedKernelVersion());
        if (!SELinux.isSELinuxEnabled()) {
            setStringSummary("selinux_status", getResources().getString(R.string.selinux_status_disabled));
        } else if (!SELinux.isSELinuxEnforced()) {
            setStringSummary("selinux_status", getResources().getString(R.string.selinux_status_permissive));
        }
        removePreferenceIfPropertyMissing(getPreferenceScreen(), "selinux_status", "ro.build.selinux");
        removePreferenceIfPropertyMissing(getPreferenceScreen(), "safetylegal", "ro.url.safetylegal");
        removePreferenceIfPropertyMissing(getPreferenceScreen(), "fcc_equipment_id", "ro.ril.fccid");
        if (Utils.isWifiOnly(getActivity())) {
            getPreferenceScreen().removePreference(findPreference("baseband_version"));
        }
        if (TextUtils.isEmpty(getFeedbackReporterPackage(getActivity()))) {
            getPreferenceScreen().removePreference(findPreference("device_feedback"));
        }
        Activity act = getActivity();
        PreferenceGroup parentPreference = (PreferenceGroup) findPreference("container");
        Utils.updatePreferenceToSpecificActivityOrRemove(act, parentPreference, "terms", 1);
        Utils.updatePreferenceToSpecificActivityOrRemove(act, parentPreference, "license", 1);
        Utils.updatePreferenceToSpecificActivityOrRemove(act, parentPreference, "copyright", 1);
        parentPreference = getPreferenceScreen();
        if (UserHandle.myUserId() == 0) {
            Utils.updatePreferenceToSpecificActivityOrRemove(act, parentPreference, "system_update_settings", 1);
        } else {
            removePreference("system_update_settings");
        }
        removePreferenceIfBoolFalse("additional_system_update_settings", R.bool.config_additional_system_update_setting_enable);
        if (getPackageManager().queryIntentActivities(new Intent("android.settings.SHOW_REGULATORY_INFO"), 0).isEmpty()) {
            Preference pref = findPreference("regulatory_info");
            if (pref != null) {
                getPreferenceScreen().removePreference(pref);
            }
        }
    }

    public void onResume() {
        super.onResume();
        this.mDevHitCountdown = getActivity().getSharedPreferences("development", 0).getBoolean("show", Build.TYPE.equals("eng")) ? -1 : 7;
        this.mDevHitToast = null;
    }

    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        if (preference.getKey().equals("firmware_version")) {
            System.arraycopy(this.mHits, 1, this.mHits, 0, this.mHits.length - 1);
            this.mHits[this.mHits.length - 1] = SystemClock.uptimeMillis();
            if (this.mHits[0] >= SystemClock.uptimeMillis() - 500) {
                Intent intent = new Intent("android.intent.action.MAIN");
                intent.setClassName("android", PlatLogoActivity.class.getName());
                try {
                    startActivity(intent);
                } catch (Exception e) {
                    Log.e("DeviceInfoSettings", "Unable to start activity " + intent.toString());
                }
            }
        } else if (preference.getKey().equals("build_number")) {
            if (UserHandle.myUserId() != 0 || ((UserManager) getSystemService("user")).hasUserRestriction("no_debugging_features")) {
                return true;
            }
            if (this.mDevHitCountdown > 0) {
                this.mDevHitCountdown--;
                if (this.mDevHitCountdown == 0) {
                    getActivity().getSharedPreferences("development", 0).edit().putBoolean("show", true).apply();
                    if (this.mDevHitToast != null) {
                        this.mDevHitToast.cancel();
                    }
                    this.mDevHitToast = Toast.makeText(getActivity(), R.string.show_dev_on, 1);
                    this.mDevHitToast.show();
                    Index.getInstance(getActivity().getApplicationContext()).updateFromClassNameResource(DevelopmentSettings.class.getName(), true, true);
                } else if (this.mDevHitCountdown > 0 && this.mDevHitCountdown < 5) {
                    if (this.mDevHitToast != null) {
                        this.mDevHitToast.cancel();
                    }
                    this.mDevHitToast = Toast.makeText(getActivity(), getResources().getQuantityString(R.plurals.show_dev_countdown, this.mDevHitCountdown, new Object[]{Integer.valueOf(this.mDevHitCountdown)}), 0);
                    this.mDevHitToast.show();
                }
            } else if (this.mDevHitCountdown < 0) {
                if (this.mDevHitToast != null) {
                    this.mDevHitToast.cancel();
                }
                this.mDevHitToast = Toast.makeText(getActivity(), R.string.show_dev_already, 1);
                this.mDevHitToast.show();
            }
        } else if (preference.getKey().equals("device_feedback")) {
            sendFeedback();
        }
        return super.onPreferenceTreeClick(preferenceScreen, preference);
    }

    private void removePreferenceIfPropertyMissing(PreferenceGroup preferenceGroup, String preference, String property) {
        if (SystemProperties.get(property).equals("")) {
            try {
                preferenceGroup.removePreference(findPreference(preference));
            } catch (RuntimeException e) {
                Log.d("DeviceInfoSettings", "Property '" + property + "' missing and no '" + preference + "' preference");
            }
        }
    }

    private void removePreferenceIfBoolFalse(String preference, int resId) {
        if (!getResources().getBoolean(resId)) {
            Preference pref = findPreference(preference);
            if (pref != null) {
                getPreferenceScreen().removePreference(pref);
            }
        }
    }

    private void setStringSummary(String preference, String value) {
        try {
            findPreference(preference).setSummary(value);
        } catch (RuntimeException e) {
            findPreference(preference).setSummary(getResources().getString(R.string.device_info_default));
        }
    }

    private void setValueSummary(String preference, String property) {
        try {
            findPreference(preference).setSummary(SystemProperties.get(property, getResources().getString(R.string.device_info_default)));
        } catch (RuntimeException e) {
        }
    }

    private void sendFeedback() {
        String reporterPackage = getFeedbackReporterPackage(getActivity());
        if (!TextUtils.isEmpty(reporterPackage)) {
            Intent intent = new Intent("android.intent.action.BUG_REPORT");
            intent.setPackage(reporterPackage);
            startActivityForResult(intent, 0);
        }
    }

    private static String readLine(String filename) throws IOException {
        BufferedReader reader = new BufferedReader(new FileReader(filename), 256);
        try {
            String readLine = reader.readLine();
            return readLine;
        } finally {
            reader.close();
        }
    }

    public static String getFormattedKernelVersion() {
        try {
            return formatKernelVersion(readLine("/proc/version"));
        } catch (IOException e) {
            Log.e("DeviceInfoSettings", "IO Exception when getting kernel version for Device Info screen", e);
            return "Unavailable";
        }
    }

    public static String formatKernelVersion(String rawKernelVersion) {
        String PROC_VERSION_REGEX = "Linux version (\\S+) \\((\\S+?)\\) (?:\\(gcc.+? \\)) (#\\d+) (?:.*?)?((Sun|Mon|Tue|Wed|Thu|Fri|Sat).+)";
        Matcher m = Pattern.compile("Linux version (\\S+) \\((\\S+?)\\) (?:\\(gcc.+? \\)) (#\\d+) (?:.*?)?((Sun|Mon|Tue|Wed|Thu|Fri|Sat).+)").matcher(rawKernelVersion);
        if (!m.matches()) {
            Log.e("DeviceInfoSettings", "Regex did not match on /proc/version: " + rawKernelVersion);
            return "Unavailable";
        } else if (m.groupCount() >= 4) {
            return m.group(1) + "\n" + m.group(2) + " " + m.group(3) + "\n" + m.group(4);
        } else {
            Log.e("DeviceInfoSettings", "Regex match on /proc/version only returned " + m.groupCount() + " groups");
            return "Unavailable";
        }
    }

    private String getMsvSuffix() {
        try {
            if (Long.parseLong(readLine("/sys/board_properties/soc/msv"), 16) == 0) {
                return " (ENGINEERING)";
            }
        } catch (IOException e) {
        } catch (NumberFormatException e2) {
        }
        return "";
    }

    private static String getFeedbackReporterPackage(Context context) {
        String feedbackReporter = context.getResources().getString(R.string.oem_preferred_feedback_reporter);
        if (TextUtils.isEmpty(feedbackReporter)) {
            return feedbackReporter;
        }
        Intent intent = new Intent("android.intent.action.BUG_REPORT");
        PackageManager pm = context.getPackageManager();
        for (ResolveInfo info : pm.queryIntentActivities(intent, 64)) {
            if (!(info.activityInfo == null || TextUtils.isEmpty(info.activityInfo.packageName))) {
                try {
                    if ((pm.getApplicationInfo(info.activityInfo.packageName, 0).flags & 1) != 0 && TextUtils.equals(info.activityInfo.packageName, feedbackReporter)) {
                        return feedbackReporter;
                    }
                } catch (NameNotFoundException e) {
                }
            }
        }
        return null;
    }
}
