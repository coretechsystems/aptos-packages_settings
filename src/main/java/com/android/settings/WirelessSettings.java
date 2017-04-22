package com.android.settings;

import android.app.Activity;
import android.app.AlertDialog.Builder;
import android.app.Dialog;
import android.app.admin.DevicePolicyManager;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.nfc.NfcAdapter;
import android.nfc.NfcManager;
import android.os.Bundle;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.os.UserManager;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceScreen;
import android.preference.SwitchPreference;
import android.provider.SearchIndexableResource;
import android.provider.Settings.Global;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import com.android.internal.telephony.SmsApplication;
import com.android.internal.telephony.SmsApplication.SmsApplicationData;
import com.android.settings.nfc.NfcEnabler;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settings.search.Indexable;
import com.android.settings.search.Indexable.SearchIndexProvider;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public class WirelessSettings extends SettingsPreferenceFragment implements OnPreferenceChangeListener, Indexable {
    public static final SearchIndexProvider SEARCH_INDEX_DATA_PROVIDER = new BaseSearchIndexProvider() {
        public List<SearchIndexableResource> getXmlResourcesToIndex(Context context, boolean enabled) {
            new SearchIndexableResource(context).xmlResId = R.xml.wireless_settings;
            return Arrays.asList(new SearchIndexableResource[]{sir});
        }

        public List<String> getNonIndexableKeys(Context context) {
            ArrayList<String> result = new ArrayList();
            result.add("toggle_nsd");
            UserManager um = (UserManager) context.getSystemService("user");
            boolean isSecondaryUser = UserHandle.myUserId() != 0;
            boolean isWimaxEnabled = !isSecondaryUser && context.getResources().getBoolean(17956946);
            if (!isWimaxEnabled || um.hasUserRestriction("no_config_mobile_networks")) {
                result.add("wimax_settings");
            }
            if (isSecondaryUser) {
                result.add("vpn_settings");
            }
            NfcManager manager = (NfcManager) context.getSystemService("nfc");
            if (manager != null && manager.getDefaultAdapter() == null) {
                result.add("toggle_nfc");
                result.add("android_beam_settings");
            }
            if (isSecondaryUser || Utils.isWifiOnly(context)) {
                result.add("mobile_network_settings");
                result.add("manage_mobile_plan");
            }
            if (!context.getResources().getBoolean(R.bool.config_show_mobile_plan)) {
                result.add("manage_mobile_plan");
            }
            if (!((TelephonyManager) context.getSystemService("phone")).isSmsCapable()) {
                result.add("sms_application");
            }
            PackageManager pm = context.getPackageManager();
            if (pm.hasSystemFeature("android.hardware.type.television")) {
                result.add("toggle_airplane");
            }
            result.add("proxy_settings");
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService("connectivity");
            if (isSecondaryUser || !cm.isTetheringSupported()) {
                result.add("tether_settings");
            }
            boolean isCellBroadcastAppLinkEnabled = context.getResources().getBoolean(17956955);
            if (isCellBroadcastAppLinkEnabled) {
                try {
                    if (pm.getApplicationEnabledSetting("com.android.cellbroadcastreceiver") == 2) {
                        isCellBroadcastAppLinkEnabled = false;
                    }
                } catch (IllegalArgumentException e) {
                    isCellBroadcastAppLinkEnabled = false;
                }
            }
            if (isSecondaryUser || !isCellBroadcastAppLinkEnabled) {
                result.add("cell_broadcast_settings");
            }
            return result;
        }
    };
    private AirplaneModeEnabler mAirplaneModeEnabler;
    private SwitchPreference mAirplaneModePreference;
    private ConnectivityManager mCm;
    private String mManageMobilePlanMessage;
    private NfcAdapter mNfcAdapter;
    private NfcEnabler mNfcEnabler;
    private NsdEnabler mNsdEnabler;
    private PackageManager mPm;
    private AppListPreference mSmsApplicationPreference;
    private TelephonyManager mTm;
    private UserManager mUm;

    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        log("onPreferenceTreeClick: preference=" + preference);
        if (preference == this.mAirplaneModePreference && Boolean.parseBoolean(SystemProperties.get("ril.cdma.inecmmode"))) {
            startActivityForResult(new Intent("android.intent.action.ACTION_SHOW_NOTICE_ECM_BLOCK_OTHERS", null), 1);
            return true;
        }
        if (preference == findPreference("manage_mobile_plan")) {
            onManageMobilePlanClick();
        }
        return super.onPreferenceTreeClick(preferenceScreen, preference);
    }

    public void onManageMobilePlanClick() {
        log("onManageMobilePlanClick:");
        this.mManageMobilePlanMessage = null;
        Resources resources = getActivity().getResources();
        NetworkInfo ni = this.mCm.getProvisioningOrActiveNetworkInfo();
        if (this.mTm.hasIccCard() && ni != null) {
            Intent provisioningIntent = new Intent("android.intent.action.ACTION_CARRIER_SETUP");
            List<String> carrierPackages = this.mTm.getCarrierPackageNamesForIntent(provisioningIntent);
            if (carrierPackages == null || carrierPackages.isEmpty()) {
                String url = this.mCm.getMobileProvisioningUrl();
                if (TextUtils.isEmpty(url)) {
                    if (TextUtils.isEmpty(this.mTm.getSimOperatorName())) {
                        if (TextUtils.isEmpty(this.mTm.getNetworkOperatorName())) {
                            this.mManageMobilePlanMessage = resources.getString(R.string.mobile_unknown_sim_operator);
                        } else {
                            this.mManageMobilePlanMessage = resources.getString(R.string.mobile_no_provisioning_url, new Object[]{this.mTm.getNetworkOperatorName()});
                        }
                    } else {
                        this.mManageMobilePlanMessage = resources.getString(R.string.mobile_no_provisioning_url, new Object[]{this.mTm.getSimOperatorName()});
                    }
                } else {
                    Intent intent = Intent.makeMainSelectorActivity("android.intent.action.MAIN", "android.intent.category.APP_BROWSER");
                    intent.setData(Uri.parse(url));
                    intent.setFlags(272629760);
                    try {
                        startActivity(intent);
                    } catch (ActivityNotFoundException e) {
                        Log.w("WirelessSettings", "onManageMobilePlanClick: startActivity failed" + e);
                    }
                }
            } else {
                if (carrierPackages.size() != 1) {
                    Log.w("WirelessSettings", "Multiple matching carrier apps found, launching the first.");
                }
                provisioningIntent.setPackage((String) carrierPackages.get(0));
                startActivity(provisioningIntent);
                return;
            }
        } else if (this.mTm.hasIccCard()) {
            this.mManageMobilePlanMessage = resources.getString(R.string.mobile_connect_to_internet);
        } else {
            this.mManageMobilePlanMessage = resources.getString(R.string.mobile_insert_sim_card);
        }
        if (!TextUtils.isEmpty(this.mManageMobilePlanMessage)) {
            log("onManageMobilePlanClick: message=" + this.mManageMobilePlanMessage);
            showDialog(1);
        }
    }

    private void initSmsApplicationSetting() {
        log("initSmsApplicationSetting:");
        Collection<SmsApplicationData> smsApplications = SmsApplication.getApplicationCollection(getActivity());
        String[] packageNames = new String[smsApplications.size()];
        int i = 0;
        for (SmsApplicationData smsApplicationData : smsApplications) {
            packageNames[i] = smsApplicationData.mPackageName;
            i++;
        }
        String defaultPackageName = null;
        ComponentName appName = SmsApplication.getDefaultSmsApplication(getActivity(), true);
        if (appName != null) {
            defaultPackageName = appName.getPackageName();
        }
        this.mSmsApplicationPreference.setPackageNames(packageNames, defaultPackageName);
    }

    public Dialog onCreateDialog(int dialogId) {
        log("onCreateDialog: dialogId=" + dialogId);
        switch (dialogId) {
            case 1:
                return new Builder(getActivity()).setMessage(this.mManageMobilePlanMessage).setCancelable(false).setPositiveButton(17039370, new OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        WirelessSettings.this.log("MANAGE_MOBILE_PLAN_DIALOG.onClickListener id=" + id);
                        WirelessSettings.this.mManageMobilePlanMessage = null;
                    }
                }).create();
            default:
                return super.onCreateDialog(dialogId);
        }
    }

    private void log(String s) {
        Log.d("WirelessSettings", s);
    }

    public static boolean isRadioAllowed(Context context, String type) {
        if (!AirplaneModeEnabler.isAirplaneModeOn(context)) {
            return true;
        }
        String toggleable = Global.getString(context.getContentResolver(), "airplane_mode_toggleable_radios");
        if (toggleable == null || !toggleable.contains(type)) {
            return false;
        }
        return true;
    }

    private boolean isSmsSupported() {
        return this.mTm.isSmsCapable();
    }

    public void onCreate(Bundle savedInstanceState) {
        PreferenceScreen root;
        Preference ps;
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            this.mManageMobilePlanMessage = savedInstanceState.getString("mManageMobilePlanMessage");
        }
        log("onCreate: mManageMobilePlanMessage=" + this.mManageMobilePlanMessage);
        this.mCm = (ConnectivityManager) getSystemService("connectivity");
        this.mTm = (TelephonyManager) getSystemService("phone");
        this.mPm = getPackageManager();
        this.mUm = (UserManager) getSystemService("user");
        addPreferencesFromResource(R.xml.wireless_settings);
        boolean isSecondaryUser = UserHandle.myUserId() != 0;
        Activity activity = getActivity();
        this.mAirplaneModePreference = (SwitchPreference) findPreference("toggle_airplane");
        SwitchPreference nfc = (SwitchPreference) findPreference("toggle_nfc");
        PreferenceScreen androidBeam = (PreferenceScreen) findPreference("android_beam_settings");
        CheckBoxPreference nsd = (CheckBoxPreference) findPreference("toggle_nsd");
        this.mAirplaneModeEnabler = new AirplaneModeEnabler(activity, this.mAirplaneModePreference);
        this.mNfcEnabler = new NfcEnabler(activity, nfc, androidBeam);
        this.mSmsApplicationPreference = (AppListPreference) findPreference("sms_application");
        this.mSmsApplicationPreference.setOnPreferenceChangeListener(this);
        initSmsApplicationSetting();
        getPreferenceScreen().removePreference(nsd);
        String toggleable = Global.getString(activity.getContentResolver(), "airplane_mode_toggleable_radios");
        boolean isWimaxEnabled = !isSecondaryUser && getResources().getBoolean(17956946);
        if (!isWimaxEnabled || this.mUm.hasUserRestriction("no_config_mobile_networks")) {
            root = getPreferenceScreen();
            ps = findPreference("wimax_settings");
            if (ps != null) {
                root.removePreference(ps);
            }
        } else if (toggleable == null || (!toggleable.contains("wimax") && isWimaxEnabled)) {
            findPreference("wimax_settings").setDependency("toggle_airplane");
        }
        if (toggleable == null || !toggleable.contains("wifi")) {
            findPreference("vpn_settings").setDependency("toggle_airplane");
        }
        if (isSecondaryUser || this.mUm.hasUserRestriction("no_config_vpn")) {
            removePreference("vpn_settings");
        }
        boolean isCellBroadcastAppLinkEnabled;
        if (toggleable == null || !toggleable.contains("bluetooth")) {
            if (toggleable == null || !toggleable.contains("nfc")) {
                findPreference("toggle_nfc").setDependency("toggle_airplane");
                findPreference("android_beam_settings").setDependency("toggle_airplane");
            }
            this.mNfcAdapter = NfcAdapter.getDefaultAdapter(activity);
            if (this.mNfcAdapter == null) {
                getPreferenceScreen().removePreference(nfc);
                getPreferenceScreen().removePreference(androidBeam);
                this.mNfcEnabler = null;
            }
            if (isSecondaryUser || Utils.isWifiOnly(getActivity()) || this.mUm.hasUserRestriction("no_config_mobile_networks")) {
                removePreference("mobile_network_settings");
                removePreference("manage_mobile_plan");
            }
            if (!(getResources().getBoolean(R.bool.config_show_mobile_plan) || findPreference("manage_mobile_plan") == null)) {
                removePreference("manage_mobile_plan");
            }
            if (!isSmsSupported()) {
                removePreference("sms_application");
            }
            if (this.mPm.hasSystemFeature("android.hardware.type.television")) {
                removePreference("toggle_airplane");
            }
            Preference mGlobalProxy = findPreference("proxy_settings");
            DevicePolicyManager mDPM = (DevicePolicyManager) activity.getSystemService("device_policy");
            getPreferenceScreen().removePreference(mGlobalProxy);
            mGlobalProxy.setEnabled(mDPM.getGlobalProxyAdmin() != null);
            ConnectivityManager cm = (ConnectivityManager) activity.getSystemService("connectivity");
            if (isSecondaryUser && cm.isTetheringSupported() && !this.mUm.hasUserRestriction("no_config_tethering")) {
                Preference p = findPreference("tether_settings");
                p.setTitle(Utils.getTetheringLabel(cm));
                p.setEnabled(!TetherSettings.isProvisioningNeededButUnavailable(getActivity()));
            } else {
                getPreferenceScreen().removePreference(findPreference("tether_settings"));
            }
            isCellBroadcastAppLinkEnabled = getResources().getBoolean(17956955);
            if (isCellBroadcastAppLinkEnabled) {
                try {
                    if (this.mPm.getApplicationEnabledSetting("com.android.cellbroadcastreceiver") == 2) {
                        isCellBroadcastAppLinkEnabled = false;
                    }
                } catch (IllegalArgumentException e) {
                    isCellBroadcastAppLinkEnabled = false;
                }
            }
            if (isSecondaryUser || !isCellBroadcastAppLinkEnabled || this.mUm.hasUserRestriction("no_config_cell_broadcasts")) {
                root = getPreferenceScreen();
                ps = findPreference("cell_broadcast_settings");
                if (ps != null) {
                    root.removePreference(ps);
                }
            }
            return;
        }
        findPreference("toggle_nfc").setDependency("toggle_airplane");
        findPreference("android_beam_settings").setDependency("toggle_airplane");
        this.mNfcAdapter = NfcAdapter.getDefaultAdapter(activity);
        if (this.mNfcAdapter == null) {
            getPreferenceScreen().removePreference(nfc);
            getPreferenceScreen().removePreference(androidBeam);
            this.mNfcEnabler = null;
        }
        removePreference("mobile_network_settings");
        removePreference("manage_mobile_plan");
        removePreference("manage_mobile_plan");
        if (isSmsSupported()) {
            removePreference("sms_application");
        }
        if (this.mPm.hasSystemFeature("android.hardware.type.television")) {
            removePreference("toggle_airplane");
        }
        Preference mGlobalProxy2 = findPreference("proxy_settings");
        DevicePolicyManager mDPM2 = (DevicePolicyManager) activity.getSystemService("device_policy");
        getPreferenceScreen().removePreference(mGlobalProxy2);
        if (mDPM2.getGlobalProxyAdmin() != null) {
        }
        mGlobalProxy2.setEnabled(mDPM2.getGlobalProxyAdmin() != null);
        ConnectivityManager cm2 = (ConnectivityManager) activity.getSystemService("connectivity");
        if (isSecondaryUser) {
        }
        getPreferenceScreen().removePreference(findPreference("tether_settings"));
        isCellBroadcastAppLinkEnabled = getResources().getBoolean(17956955);
        if (isCellBroadcastAppLinkEnabled) {
            if (this.mPm.getApplicationEnabledSetting("com.android.cellbroadcastreceiver") == 2) {
                isCellBroadcastAppLinkEnabled = false;
            }
        }
        if (!isSecondaryUser) {
        }
        root = getPreferenceScreen();
        ps = findPreference("cell_broadcast_settings");
        if (ps != null) {
            root.removePreference(ps);
        }
    }

    public void onStart() {
        super.onStart();
        initSmsApplicationSetting();
    }

    public void onResume() {
        super.onResume();
        this.mAirplaneModeEnabler.resume();
        if (this.mNfcEnabler != null) {
            this.mNfcEnabler.resume();
        }
        if (this.mNsdEnabler != null) {
            this.mNsdEnabler.resume();
        }
    }

    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (!TextUtils.isEmpty(this.mManageMobilePlanMessage)) {
            outState.putString("mManageMobilePlanMessage", this.mManageMobilePlanMessage);
        }
    }

    public void onPause() {
        super.onPause();
        this.mAirplaneModeEnabler.pause();
        if (this.mNfcEnabler != null) {
            this.mNfcEnabler.pause();
        }
        if (this.mNsdEnabler != null) {
            this.mNsdEnabler.pause();
        }
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == 1) {
            this.mAirplaneModeEnabler.setAirplaneModeInECM(Boolean.valueOf(data.getBooleanExtra("exit_ecm_result", false)).booleanValue(), this.mAirplaneModePreference.isChecked());
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    protected int getHelpResource() {
        return R.string.help_url_more_networks;
    }

    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (preference != this.mSmsApplicationPreference || newValue == null) {
            return false;
        }
        SmsApplication.setDefaultApplication(newValue.toString(), getActivity());
        return true;
    }
}
