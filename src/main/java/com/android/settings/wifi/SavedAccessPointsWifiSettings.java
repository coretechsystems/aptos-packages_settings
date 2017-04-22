package com.android.settings.wifi;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceScreen;
import android.util.Log;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settings.search.Indexable;
import com.android.settings.search.Indexable.SearchIndexProvider;
import com.android.settings.search.SearchIndexableRaw;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SavedAccessPointsWifiSettings extends SettingsPreferenceFragment implements OnClickListener, Indexable {
    public static final SearchIndexProvider SEARCH_INDEX_DATA_PROVIDER = new BaseSearchIndexProvider() {
        public List<SearchIndexableRaw> getRawDataToIndex(Context context, boolean enabled) {
            List<SearchIndexableRaw> result = new ArrayList();
            String title = context.getResources().getString(R.string.wifi_saved_access_points_titlebar);
            SearchIndexableRaw data = new SearchIndexableRaw(context);
            data.title = title;
            data.screenTitle = title;
            data.enabled = enabled;
            result.add(data);
            List<AccessPoint> accessPoints = SavedAccessPointsWifiSettings.constructSavedAccessPoints(context, (WifiManager) context.getSystemService("wifi"));
            int accessPointsSize = accessPoints.size();
            for (int i = 0; i < accessPointsSize; i++) {
                data = new SearchIndexableRaw(context);
                data.title = ((AccessPoint) accessPoints.get(i)).getTitle().toString();
                data.screenTitle = title;
                data.enabled = enabled;
                result.add(data);
            }
            return result;
        }
    };
    private Bundle mAccessPointSavedState;
    private WifiDialog mDialog;
    private AccessPoint mDlgAccessPoint;
    private AccessPoint mSelectedAccessPoint;
    private WifiManager mWifiManager;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.wifi_display_saved_access_points);
    }

    public void onResume() {
        super.onResume();
        initPreferences();
    }

    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        this.mWifiManager = (WifiManager) getSystemService("wifi");
        if (savedInstanceState != null && savedInstanceState.containsKey("wifi_ap_state")) {
            this.mAccessPointSavedState = savedInstanceState.getBundle("wifi_ap_state");
        }
    }

    private void initPreferences() {
        PreferenceScreen preferenceScreen = getPreferenceScreen();
        Context context = getActivity();
        this.mWifiManager = (WifiManager) context.getSystemService("wifi");
        List<AccessPoint> accessPoints = constructSavedAccessPoints(context, this.mWifiManager);
        preferenceScreen.removeAll();
        int accessPointsSize = accessPoints.size();
        for (int i = 0; i < accessPointsSize; i++) {
            preferenceScreen.addPreference((Preference) accessPoints.get(i));
        }
        if (getPreferenceScreen().getPreferenceCount() < 1) {
            Log.w("SavedAccessPointsWifiSettings", "Saved networks activity loaded, but there are no saved networks!");
        }
    }

    private static List<AccessPoint> constructSavedAccessPoints(Context context, WifiManager wifiManager) {
        List<AccessPoint> accessPoints = new ArrayList();
        Map<String, List<ScanResult>> resultsMap = new HashMap();
        List<WifiConfiguration> configs = wifiManager.getConfiguredNetworks();
        List<ScanResult> scanResults = wifiManager.getScanResults();
        if (configs != null) {
            int i;
            int scanResultsSize = scanResults.size();
            for (i = 0; i < scanResultsSize; i++) {
                ScanResult result = (ScanResult) scanResults.get(i);
                List<ScanResult> res = (List) resultsMap.get(result.SSID);
                if (res == null) {
                    res = new ArrayList();
                    resultsMap.put(result.SSID, res);
                }
                res.add(result);
            }
            int configsSize = configs.size();
            for (i = 0; i < configsSize; i++) {
                WifiConfiguration config = (WifiConfiguration) configs.get(i);
                if (!config.selfAdded || config.numAssociation != 0) {
                    AccessPoint accessPoint = new AccessPoint(context, config);
                    List<ScanResult> results = (List) resultsMap.get(accessPoint.ssid);
                    accessPoint.setShowSummary(false);
                    if (results != null) {
                        int resultsSize = results.size();
                        for (int j = 0; j < resultsSize; j++) {
                            accessPoint.update((ScanResult) results.get(j));
                            accessPoint.setIcon(null);
                        }
                    }
                    accessPoints.add(accessPoint);
                }
            }
        }
        return accessPoints;
    }

    private void showDialog(AccessPoint accessPoint, boolean edit) {
        if (this.mDialog != null) {
            removeDialog(1);
            this.mDialog = null;
        }
        this.mDlgAccessPoint = accessPoint;
        showDialog(1);
    }

    public Dialog onCreateDialog(int dialogId) {
        switch (dialogId) {
            case 1:
                if (this.mDlgAccessPoint == null) {
                    this.mDlgAccessPoint = new AccessPoint(getActivity(), this.mAccessPointSavedState);
                    this.mAccessPointSavedState = null;
                }
                this.mSelectedAccessPoint = this.mDlgAccessPoint;
                this.mDialog = new WifiDialog(getActivity(), this, this.mDlgAccessPoint, false, true);
                return this.mDialog;
            default:
                return super.onCreateDialog(dialogId);
        }
    }

    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (this.mDialog != null && this.mDialog.isShowing() && this.mDlgAccessPoint != null) {
            this.mAccessPointSavedState = new Bundle();
            this.mDlgAccessPoint.saveWifiState(this.mAccessPointSavedState);
            outState.putBundle("wifi_ap_state", this.mAccessPointSavedState);
        }
    }

    public void onClick(DialogInterface dialogInterface, int button) {
        if (button == -3 && this.mSelectedAccessPoint != null) {
            this.mWifiManager.forget(this.mSelectedAccessPoint.networkId, null);
            getPreferenceScreen().removePreference(this.mSelectedAccessPoint);
            this.mSelectedAccessPoint = null;
        }
    }

    public boolean onPreferenceTreeClick(PreferenceScreen screen, Preference preference) {
        if (!(preference instanceof AccessPoint)) {
            return super.onPreferenceTreeClick(screen, preference);
        }
        showDialog((AccessPoint) preference, false);
        return true;
    }
}
