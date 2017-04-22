package com.android.settings.net;

import android.content.Context;
import android.content.res.Resources;
import android.net.NetworkPolicy;
import android.net.NetworkPolicyManager;
import android.net.NetworkTemplate;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.SwitchPreference;
import com.android.settings.DataUsageSummary;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settings.search.Indexable;
import com.android.settings.search.Indexable.SearchIndexProvider;
import com.android.settings.search.SearchIndexableRaw;
import java.util.ArrayList;
import java.util.List;

public class DataUsageMeteredSettings extends SettingsPreferenceFragment implements Indexable {
    public static final SearchIndexProvider SEARCH_INDEX_DATA_PROVIDER = new BaseSearchIndexProvider() {
        public List<SearchIndexableRaw> getRawDataToIndex(Context context, boolean enabled) {
            List<SearchIndexableRaw> result = new ArrayList();
            Resources res = context.getResources();
            SearchIndexableRaw data = new SearchIndexableRaw(context);
            data.title = res.getString(R.string.data_usage_menu_metered);
            data.screenTitle = res.getString(R.string.data_usage_menu_metered);
            result.add(data);
            data = new SearchIndexableRaw(context);
            data.title = res.getString(R.string.data_usage_metered_body);
            data.screenTitle = res.getString(R.string.data_usage_menu_metered);
            result.add(data);
            data = new SearchIndexableRaw(context);
            data.title = res.getString(R.string.data_usage_metered_wifi);
            data.screenTitle = res.getString(R.string.data_usage_menu_metered);
            result.add(data);
            WifiManager wifiManager = (WifiManager) context.getSystemService("wifi");
            if (DataUsageSummary.hasWifiRadio(context) && wifiManager.isWifiEnabled()) {
                for (WifiConfiguration config : wifiManager.getConfiguredNetworks()) {
                    if (config.SSID != null) {
                        String networkId = config.SSID;
                        data = new SearchIndexableRaw(context);
                        data.title = WifiInfo.removeDoubleQuotes(networkId);
                        data.screenTitle = res.getString(R.string.data_usage_menu_metered);
                        result.add(data);
                    }
                }
            } else {
                data = new SearchIndexableRaw(context);
                data.title = res.getString(R.string.data_usage_metered_wifi_disabled);
                data.screenTitle = res.getString(R.string.data_usage_menu_metered);
                result.add(data);
            }
            return result;
        }

        public List<String> getNonIndexableKeys(Context context) {
            ArrayList<String> result = new ArrayList();
            result.add("mobile");
            return result;
        }
    };
    private PreferenceCategory mMobileCategory;
    private NetworkPolicyEditor mPolicyEditor;
    private NetworkPolicyManager mPolicyManager;
    private PreferenceCategory mWifiCategory;
    private Preference mWifiDisabled;
    private WifiManager mWifiManager;

    private class MeteredPreference extends SwitchPreference {
        private boolean mBinding = true;
        private final NetworkTemplate mTemplate;

        public MeteredPreference(Context context, NetworkTemplate template) {
            super(context);
            this.mTemplate = template;
            setPersistent(false);
            NetworkPolicy policy = DataUsageMeteredSettings.this.mPolicyEditor.getPolicyMaybeUnquoted(template);
            if (policy == null) {
                setChecked(false);
            } else if (policy.limitBytes != -1) {
                setChecked(true);
                setEnabled(false);
            } else {
                setChecked(policy.metered);
            }
            this.mBinding = false;
        }

        protected void notifyChanged() {
            super.notifyChanged();
            if (!this.mBinding) {
                DataUsageMeteredSettings.this.mPolicyEditor.setPolicyMetered(this.mTemplate, isChecked());
            }
        }
    }

    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        Context context = getActivity();
        this.mPolicyManager = NetworkPolicyManager.from(context);
        this.mWifiManager = (WifiManager) context.getSystemService("wifi");
        this.mPolicyEditor = new NetworkPolicyEditor(this.mPolicyManager);
        this.mPolicyEditor.read();
        addPreferencesFromResource(R.xml.data_usage_metered_prefs);
        this.mMobileCategory = (PreferenceCategory) findPreference("mobile");
        this.mWifiCategory = (PreferenceCategory) findPreference("wifi");
        this.mWifiDisabled = findPreference("wifi_disabled");
        updateNetworks(context);
    }

    private void updateNetworks(Context context) {
        getPreferenceScreen().removePreference(this.mMobileCategory);
        this.mWifiCategory.removeAll();
        if (DataUsageSummary.hasWifiRadio(context) && this.mWifiManager.isWifiEnabled()) {
            for (WifiConfiguration config : this.mWifiManager.getConfiguredNetworks()) {
                if (config.SSID != null) {
                    this.mWifiCategory.addPreference(buildWifiPref(context, config));
                }
            }
            return;
        }
        this.mWifiCategory.addPreference(this.mWifiDisabled);
    }

    private Preference buildWifiPref(Context context, WifiConfiguration config) {
        String networkId = config.SSID;
        MeteredPreference pref = new MeteredPreference(context, NetworkTemplate.buildTemplateWifi(networkId));
        pref.setTitle(WifiInfo.removeDoubleQuotes(networkId));
        return pref;
    }
}
