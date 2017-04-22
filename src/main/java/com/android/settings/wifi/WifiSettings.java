package com.android.settings.wifi;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences.Editor;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.NetworkInfo.DetailedState;
import android.net.NetworkScorerAppManager;
import android.net.NetworkScorerAppManager.NetworkScorerAppData;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.ActionListener;
import android.os.Build.VERSION;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.Preference;
import android.preference.PreferenceScreen;
import android.provider.Settings.Global;
import android.provider.Settings.Secure;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import com.android.settings.R;
import com.android.settings.RestrictedSettingsFragment;
import com.android.settings.SettingsActivity;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settings.search.Indexable;
import com.android.settings.search.Indexable.SearchIndexProvider;
import com.android.settings.search.SearchIndexableRaw;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class WifiSettings extends RestrictedSettingsFragment implements OnClickListener, Indexable {
    public static final SearchIndexProvider SEARCH_INDEX_DATA_PROVIDER = new BaseSearchIndexProvider() {
        public List<SearchIndexableRaw> getRawDataToIndex(Context context, boolean enabled) {
            List<SearchIndexableRaw> result = new ArrayList();
            Resources res = context.getResources();
            SearchIndexableRaw data = new SearchIndexableRaw(context);
            data.title = res.getString(R.string.wifi_settings);
            data.screenTitle = res.getString(R.string.wifi_settings);
            data.keywords = res.getString(R.string.keywords_wifi);
            result.add(data);
            for (AccessPoint accessPoint : WifiSettings.constructAccessPoints(context, (WifiManager) context.getSystemService("wifi"), null, null)) {
                if (accessPoint.getConfig() != null) {
                    data = new SearchIndexableRaw(context);
                    data.title = accessPoint.getTitle().toString();
                    data.screenTitle = res.getString(R.string.wifi_settings);
                    data.enabled = enabled;
                    result.add(data);
                }
            }
            return result;
        }
    };
    public static int mVerboseLogging = 0;
    private static boolean savedNetworksExist;
    private Bundle mAccessPointSavedState;
    private ActionListener mConnectListener;
    private final AtomicBoolean mConnected = new AtomicBoolean(false);
    private WifiDialog mDialog;
    private AccessPoint mDlgAccessPoint;
    private boolean mDlgEdit;
    private TextView mEmptyView;
    private boolean mEnableNextOnConnection;
    private final IntentFilter mFilter = new IntentFilter();
    private ActionListener mForgetListener;
    private WifiInfo mLastInfo;
    private DetailedState mLastState;
    private final BroadcastReceiver mReceiver;
    private ActionListener mSaveListener;
    private final Scanner mScanner;
    private AccessPoint mSelectedAccessPoint;
    private NetworkScorerAppData mWifiAssistantApp;
    private View mWifiAssistantCard;
    private WifiEnabler mWifiEnabler;
    WifiManager mWifiManager;
    private WriteWifiConfigToNfcDialog mWifiToNfcDialog;

    private static class Multimap<K, V> {
        private final HashMap<K, List<V>> store;

        private Multimap() {
            this.store = new HashMap();
        }

        List<V> getAll(K key) {
            List<V> values = (List) this.store.get(key);
            return values != null ? values : Collections.emptyList();
        }

        void put(K key, V val) {
            List<V> curVals = (List) this.store.get(key);
            if (curVals == null) {
                curVals = new ArrayList(3);
                this.store.put(key, curVals);
            }
            curVals.add(val);
        }
    }

    private static class Scanner extends Handler {
        private int mRetry = 0;
        private WifiSettings mWifiSettings = null;

        Scanner(WifiSettings wifiSettings) {
            this.mWifiSettings = wifiSettings;
        }

        void resume() {
            if (!hasMessages(0)) {
                sendEmptyMessage(0);
            }
        }

        void forceScan() {
            removeMessages(0);
            sendEmptyMessage(0);
        }

        void pause() {
            this.mRetry = 0;
            removeMessages(0);
        }

        public void handleMessage(Message message) {
            if (this.mWifiSettings.mWifiManager.startScan()) {
                this.mRetry = 0;
            } else {
                int i = this.mRetry + 1;
                this.mRetry = i;
                if (i >= 3) {
                    this.mRetry = 0;
                    Activity activity = this.mWifiSettings.getActivity();
                    if (activity != null) {
                        Toast.makeText(activity, R.string.wifi_fail_to_scan, 1).show();
                        return;
                    }
                    return;
                }
            }
            sendEmptyMessageDelayed(0, 10000);
        }
    }

    public WifiSettings() {
        super("no_config_wifi");
        this.mFilter.addAction("android.net.wifi.WIFI_STATE_CHANGED");
        this.mFilter.addAction("android.net.wifi.SCAN_RESULTS");
        this.mFilter.addAction("android.net.wifi.NETWORK_IDS_CHANGED");
        this.mFilter.addAction("android.net.wifi.supplicant.STATE_CHANGE");
        this.mFilter.addAction("android.net.wifi.CONFIGURED_NETWORKS_CHANGE");
        this.mFilter.addAction("android.net.wifi.LINK_CONFIGURATION_CHANGED");
        this.mFilter.addAction("android.net.wifi.STATE_CHANGE");
        this.mFilter.addAction("android.net.wifi.RSSI_CHANGED");
        this.mReceiver = new BroadcastReceiver() {
            public void onReceive(Context context, Intent intent) {
                WifiSettings.this.handleEvent(intent);
            }
        };
        this.mScanner = new Scanner(this);
    }

    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        this.mWifiManager = (WifiManager) getSystemService("wifi");
        this.mConnectListener = new ActionListener() {
            public void onSuccess() {
            }

            public void onFailure(int reason) {
                Activity activity = WifiSettings.this.getActivity();
                if (activity != null) {
                    Toast.makeText(activity, R.string.wifi_failed_connect_message, 0).show();
                }
            }
        };
        this.mSaveListener = new ActionListener() {
            public void onSuccess() {
            }

            public void onFailure(int reason) {
                Activity activity = WifiSettings.this.getActivity();
                if (activity != null) {
                    Toast.makeText(activity, R.string.wifi_failed_save_message, 0).show();
                }
            }
        };
        this.mForgetListener = new ActionListener() {
            public void onSuccess() {
            }

            public void onFailure(int reason) {
                Activity activity = WifiSettings.this.getActivity();
                if (activity != null) {
                    Toast.makeText(activity, R.string.wifi_failed_forget_message, 0).show();
                }
            }
        };
        if (savedInstanceState != null) {
            this.mDlgEdit = savedInstanceState.getBoolean("edit_mode");
            if (savedInstanceState.containsKey("wifi_ap_state")) {
                this.mAccessPointSavedState = savedInstanceState.getBundle("wifi_ap_state");
            }
        }
        this.mEnableNextOnConnection = getActivity().getIntent().getBooleanExtra("wifi_enable_next_on_connect", false);
        if (this.mEnableNextOnConnection && hasNextButton()) {
            ConnectivityManager connectivity = (ConnectivityManager) getActivity().getSystemService("connectivity");
            if (connectivity != null) {
                changeNextButtonState(connectivity.getNetworkInfo(1).isConnected());
            }
        }
        addPreferencesFromResource(R.xml.wifi_settings);
        prepareWifiAssistantCard();
        this.mEmptyView = initEmptyView();
        registerForContextMenu(getListView());
        setHasOptionsMenu(true);
    }

    public void onActivityResult(int requestCode, int resultCode, Intent resultData) {
        if (requestCode != 1) {
            super.onActivityResult(requestCode, resultCode, resultData);
        } else if (resultCode == -1) {
            disableWifiAssistantCardUntilPlatformUpgrade();
            getListView().removeHeaderView(this.mWifiAssistantCard);
            this.mWifiAssistantApp = null;
        }
    }

    public void onDestroyView() {
        super.onDestroyView();
        if (this.mWifiEnabler != null) {
            this.mWifiEnabler.teardownSwitchBar();
        }
    }

    public void onStart() {
        super.onStart();
        this.mWifiEnabler = createWifiEnabler();
    }

    WifiEnabler createWifiEnabler() {
        SettingsActivity activity = (SettingsActivity) getActivity();
        return new WifiEnabler(activity, activity.getSwitchBar());
    }

    public void onResume() {
        Activity activity = getActivity();
        super.onResume();
        if (this.mWifiEnabler != null) {
            this.mWifiEnabler.resume(activity);
        }
        activity.registerReceiver(this.mReceiver, this.mFilter);
        updateAccessPoints();
    }

    public void onPause() {
        super.onPause();
        if (this.mWifiEnabler != null) {
            this.mWifiEnabler.pause();
        }
        getActivity().unregisterReceiver(this.mReceiver);
        this.mScanner.pause();
    }

    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        if (!isUiRestricted()) {
            addOptionsMenuItems(menu);
            super.onCreateOptionsMenu(menu, inflater);
        }
    }

    void addOptionsMenuItems(Menu menu) {
        boolean wifiIsEnabled = this.mWifiManager.isWifiEnabled();
        TypedArray ta = getActivity().getTheme().obtainStyledAttributes(new int[]{R.attr.ic_menu_add, R.attr.ic_wps});
        menu.add(0, 4, 0, R.string.wifi_add_network).setIcon(ta.getDrawable(0)).setEnabled(wifiIsEnabled).setShowAsAction(0);
        if (savedNetworksExist) {
            menu.add(0, 3, 0, R.string.wifi_saved_access_points_label).setIcon(ta.getDrawable(0)).setEnabled(wifiIsEnabled).setShowAsAction(0);
        }
        menu.add(0, 6, 0, R.string.menu_stats_refresh).setEnabled(wifiIsEnabled).setShowAsAction(0);
        menu.add(0, 5, 0, R.string.wifi_menu_advanced).setShowAsAction(0);
        ta.recycle();
    }

    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (this.mDialog != null && this.mDialog.isShowing()) {
            outState.putBoolean("edit_mode", this.mDlgEdit);
            if (this.mDlgAccessPoint != null) {
                this.mAccessPointSavedState = new Bundle();
                this.mDlgAccessPoint.saveWifiState(this.mAccessPointSavedState);
                outState.putBundle("wifi_ap_state", this.mAccessPointSavedState);
            }
        }
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        if (isUiRestricted()) {
            return false;
        }
        switch (item.getItemId()) {
            case 1:
                showDialog(2);
                return true;
            case 2:
                showDialog(3);
                return true;
            case 3:
                if (getActivity() instanceof SettingsActivity) {
                    ((SettingsActivity) getActivity()).startPreferencePanel(SavedAccessPointsWifiSettings.class.getCanonicalName(), null, R.string.wifi_saved_access_points_titlebar, null, this, 0);
                } else {
                    startFragment(this, SavedAccessPointsWifiSettings.class.getCanonicalName(), R.string.wifi_saved_access_points_titlebar, -1, null);
                }
                return true;
            case 4:
                if (this.mWifiManager.isWifiEnabled()) {
                    onAddNetworkPressed();
                }
                return true;
            case 5:
                if (getActivity() instanceof SettingsActivity) {
                    ((SettingsActivity) getActivity()).startPreferencePanel(AdvancedWifiSettings.class.getCanonicalName(), null, R.string.wifi_advanced_titlebar, null, this, 0);
                } else {
                    startFragment(this, AdvancedWifiSettings.class.getCanonicalName(), R.string.wifi_advanced_titlebar, -1, null);
                }
                return true;
            case 6:
                if (this.mWifiManager.isWifiEnabled()) {
                    this.mScanner.forceScan();
                }
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    public void onCreateContextMenu(ContextMenu menu, View view, ContextMenuInfo info) {
        if (info instanceof AdapterContextMenuInfo) {
            Preference preference = (Preference) getListView().getItemAtPosition(((AdapterContextMenuInfo) info).position);
            if (preference instanceof AccessPoint) {
                this.mSelectedAccessPoint = (AccessPoint) preference;
                menu.setHeaderTitle(this.mSelectedAccessPoint.ssid);
                if (this.mSelectedAccessPoint.getLevel() != -1 && this.mSelectedAccessPoint.getState() == null) {
                    menu.add(0, 7, 0, R.string.wifi_menu_connect);
                }
                if (this.mSelectedAccessPoint.networkId != -1) {
                    if (ActivityManager.getCurrentUser() == 0) {
                        menu.add(0, 8, 0, R.string.wifi_menu_forget);
                    }
                    menu.add(0, 9, 0, R.string.wifi_menu_modify);
                    if (this.mSelectedAccessPoint.security != 0) {
                        menu.add(0, 10, 0, R.string.wifi_menu_write_to_nfc);
                    }
                }
            }
        }
    }

    public boolean onContextItemSelected(MenuItem item) {
        if (this.mSelectedAccessPoint == null) {
            return super.onContextItemSelected(item);
        }
        switch (item.getItemId()) {
            case 7:
                if (this.mSelectedAccessPoint.networkId != -1) {
                    connect(this.mSelectedAccessPoint.networkId);
                    return true;
                } else if (this.mSelectedAccessPoint.security == 0) {
                    this.mSelectedAccessPoint.generateOpenNetworkConfig();
                    connect(this.mSelectedAccessPoint.getConfig());
                    return true;
                } else {
                    showDialog(this.mSelectedAccessPoint, true);
                    return true;
                }
            case 8:
                this.mWifiManager.forget(this.mSelectedAccessPoint.networkId, this.mForgetListener);
                return true;
            case 9:
                showDialog(this.mSelectedAccessPoint, true);
                return true;
            case 10:
                showDialog(6);
                return true;
            default:
                return super.onContextItemSelected(item);
        }
    }

    public boolean onPreferenceTreeClick(PreferenceScreen screen, Preference preference) {
        if (!(preference instanceof AccessPoint)) {
            return super.onPreferenceTreeClick(screen, preference);
        }
        this.mSelectedAccessPoint = (AccessPoint) preference;
        if (this.mSelectedAccessPoint.security == 0 && this.mSelectedAccessPoint.networkId == -1) {
            this.mSelectedAccessPoint.generateOpenNetworkConfig();
            if (!savedNetworksExist) {
                savedNetworksExist = true;
                getActivity().invalidateOptionsMenu();
            }
            connect(this.mSelectedAccessPoint.getConfig());
            return true;
        }
        showDialog(this.mSelectedAccessPoint, false);
        return true;
    }

    private void showDialog(AccessPoint accessPoint, boolean edit) {
        if (this.mDialog != null) {
            removeDialog(1);
            this.mDialog = null;
        }
        this.mDlgAccessPoint = accessPoint;
        this.mDlgEdit = edit;
        showDialog(1);
    }

    public Dialog onCreateDialog(int dialogId) {
        switch (dialogId) {
            case 1:
                AccessPoint ap = this.mDlgAccessPoint;
                if (ap == null && this.mAccessPointSavedState != null) {
                    ap = new AccessPoint(getActivity(), this.mAccessPointSavedState);
                    this.mDlgAccessPoint = ap;
                    this.mAccessPointSavedState = null;
                }
                this.mSelectedAccessPoint = ap;
                this.mDialog = new WifiDialog(getActivity(), this, ap, this.mDlgEdit);
                return this.mDialog;
            case 2:
                return new WpsDialog(getActivity(), 0);
            case 3:
                return new WpsDialog(getActivity(), 1);
            case 6:
                if (this.mSelectedAccessPoint != null) {
                    this.mWifiToNfcDialog = new WriteWifiConfigToNfcDialog(getActivity(), this.mSelectedAccessPoint, this.mWifiManager);
                    return this.mWifiToNfcDialog;
                }
                break;
        }
        return super.onCreateDialog(dialogId);
    }

    private void updateAccessPoints() {
        if (getActivity() != null) {
            if (isUiRestricted()) {
                addMessagePreference(R.string.wifi_empty_list_user_restricted);
                return;
            }
            int wifiState = this.mWifiManager.getWifiState();
            mVerboseLogging = this.mWifiManager.getVerboseLoggingLevel();
            switch (wifiState) {
                case 0:
                    addMessagePreference(R.string.wifi_stopping);
                    return;
                case 1:
                    setOffMessage();
                    return;
                case 2:
                    getPreferenceScreen().removeAll();
                    return;
                case 3:
                    Collection<AccessPoint> accessPoints = constructAccessPoints(getActivity(), this.mWifiManager, this.mLastInfo, this.mLastState);
                    getPreferenceScreen().removeAll();
                    if (accessPoints.size() == 0) {
                        addMessagePreference(R.string.wifi_empty_list_wifi_on);
                    }
                    getListView().removeHeaderView(this.mWifiAssistantCard);
                    if (this.mWifiAssistantApp != null) {
                        getListView().addHeaderView(this.mWifiAssistantCard);
                    }
                    for (AccessPoint accessPoint : accessPoints) {
                        if (accessPoint.getLevel() != -1) {
                            getPreferenceScreen().addPreference(accessPoint);
                        }
                    }
                    return;
                default:
                    return;
            }
        }
    }

    public static NetworkScorerAppData getWifiAssistantApp(Context context) {
        Collection<NetworkScorerAppData> scorers = NetworkScorerAppManager.getAllValidScorers(context);
        if (scorers.isEmpty()) {
            return null;
        }
        return (NetworkScorerAppData) scorers.iterator().next();
    }

    private void prepareWifiAssistantCard() {
        if (!(getActivity() instanceof WifiPickerActivity) && NetworkScorerAppManager.getActiveScorer(getActivity()) == null) {
            Collection<NetworkScorerAppData> scorers = NetworkScorerAppManager.getAllValidScorers(getActivity());
            if (!scorers.isEmpty() && VERSION.SDK_INT > getPreferenceScreen().getSharedPreferences().getInt("assistant_dismiss_platform", 0)) {
                this.mWifiAssistantApp = (NetworkScorerAppData) scorers.iterator().next();
                if (this.mWifiAssistantCard == null) {
                    this.mWifiAssistantCard = LayoutInflater.from(getActivity()).inflate(R.layout.wifi_assistant_card, getListView(), false);
                    Button setup = (Button) this.mWifiAssistantCard.findViewById(R.id.setup);
                    Button noThanks = (Button) this.mWifiAssistantCard.findViewById(R.id.no_thanks_button);
                    ((TextView) this.mWifiAssistantCard.findViewById(R.id.wifi_assistant_text)).setText(getResources().getString(R.string.wifi_assistant_title_message, new Object[]{this.mWifiAssistantApp.mScorerName}));
                    if (setup != null && noThanks != null) {
                        setup.setOnClickListener(new View.OnClickListener() {
                            public void onClick(View v) {
                                Intent intent = new Intent();
                                if (WifiSettings.this.mWifiAssistantApp.mConfigurationActivityClassName != null) {
                                    intent.setClassName(WifiSettings.this.mWifiAssistantApp.mPackageName, WifiSettings.this.mWifiAssistantApp.mConfigurationActivityClassName);
                                } else {
                                    intent.setAction("android.net.scoring.CHANGE_ACTIVE");
                                    intent.putExtra("packageName", WifiSettings.this.mWifiAssistantApp.mPackageName);
                                }
                                WifiSettings.this.startActivityForResult(intent, 1);
                            }
                        });
                        noThanks.setOnClickListener(new View.OnClickListener() {
                            public void onClick(View v) {
                                WifiSettings.this.disableWifiAssistantCardUntilPlatformUpgrade();
                                WifiSettings.this.getListView().removeHeaderView(WifiSettings.this.mWifiAssistantCard);
                                WifiSettings.this.mWifiAssistantApp = null;
                            }
                        });
                    }
                }
            }
        }
    }

    private void disableWifiAssistantCardUntilPlatformUpgrade() {
        Editor editor = getPreferenceScreen().getSharedPreferences().edit();
        editor.putInt("assistant_dismiss_platform", VERSION.SDK_INT);
        editor.apply();
    }

    protected TextView initEmptyView() {
        TextView emptyView = (TextView) getActivity().findViewById(16908292);
        getListView().setEmptyView(emptyView);
        return emptyView;
    }

    private void setOffMessage() {
        if (this.mEmptyView != null) {
            this.mEmptyView.setText(R.string.wifi_empty_list_wifi_off);
            if (Global.getInt(getActivity().getContentResolver(), "wifi_scan_always_enabled", 0) == 1) {
                int resId;
                this.mEmptyView.append("\n\n");
                if (Secure.isLocationProviderEnabled(getActivity().getContentResolver(), "network")) {
                    resId = R.string.wifi_scan_notify_text_location_on;
                } else {
                    resId = R.string.wifi_scan_notify_text_location_off;
                }
                this.mEmptyView.append(getText(resId));
            }
        }
        getPreferenceScreen().removeAll();
    }

    private void addMessagePreference(int messageId) {
        if (this.mEmptyView != null) {
            this.mEmptyView.setText(messageId);
        }
        getPreferenceScreen().removeAll();
    }

    private static List<AccessPoint> constructAccessPoints(Context context, WifiManager wifiManager, WifiInfo lastInfo, DetailedState lastState) {
        AccessPoint accessPoint;
        ArrayList<AccessPoint> accessPoints = new ArrayList();
        Multimap<String, AccessPoint> apMap = new Multimap();
        List<WifiConfiguration> configs = wifiManager.getConfiguredNetworks();
        if (configs != null) {
            if (savedNetworksExist != (configs.size() > 0)) {
                savedNetworksExist = !savedNetworksExist;
                if (context instanceof Activity) {
                    ((Activity) context).invalidateOptionsMenu();
                }
            }
            for (WifiConfiguration config : configs) {
                if (!config.selfAdded || config.numAssociation != 0) {
                    accessPoint = new AccessPoint(context, config);
                    if (!(lastInfo == null || lastState == null)) {
                        accessPoint.update(lastInfo, lastState);
                    }
                    accessPoints.add(accessPoint);
                    apMap.put(accessPoint.ssid, accessPoint);
                }
            }
        }
        List<ScanResult> results = wifiManager.getScanResults();
        if (results != null) {
            for (ScanResult result : results) {
                if (!(result.SSID == null || result.SSID.length() == 0 || result.capabilities.contains("[IBSS]"))) {
                    boolean found = false;
                    for (AccessPoint accessPoint2 : apMap.getAll(result.SSID)) {
                        if (accessPoint2.update(result)) {
                            found = true;
                        }
                    }
                    if (!found) {
                        accessPoint2 = new AccessPoint(context, result);
                        accessPoints.add(accessPoint2);
                        apMap.put(accessPoint2.ssid, accessPoint2);
                    }
                }
            }
        }
        Collections.sort(accessPoints);
        return accessPoints;
    }

    private void handleEvent(Intent intent) {
        String action = intent.getAction();
        if ("android.net.wifi.WIFI_STATE_CHANGED".equals(action)) {
            updateWifiState(intent.getIntExtra("wifi_state", 4));
        } else if ("android.net.wifi.SCAN_RESULTS".equals(action) || "android.net.wifi.CONFIGURED_NETWORKS_CHANGE".equals(action) || "android.net.wifi.LINK_CONFIGURATION_CHANGED".equals(action)) {
            updateAccessPoints();
        } else if ("android.net.wifi.STATE_CHANGE".equals(action)) {
            NetworkInfo info = (NetworkInfo) intent.getParcelableExtra("networkInfo");
            this.mConnected.set(info.isConnected());
            changeNextButtonState(info.isConnected());
            updateAccessPoints();
            updateConnectionState(info.getDetailedState());
        } else if ("android.net.wifi.RSSI_CHANGED".equals(action)) {
            updateConnectionState(null);
        }
    }

    private void updateConnectionState(DetailedState state) {
        if (this.mWifiManager.isWifiEnabled()) {
            if (state == DetailedState.OBTAINING_IPADDR) {
                this.mScanner.pause();
            } else {
                this.mScanner.resume();
            }
            this.mLastInfo = this.mWifiManager.getConnectionInfo();
            if (state != null) {
                this.mLastState = state;
            }
            for (int i = getPreferenceScreen().getPreferenceCount() - 1; i >= 0; i--) {
                Preference preference = getPreferenceScreen().getPreference(i);
                if (preference instanceof AccessPoint) {
                    ((AccessPoint) preference).update(this.mLastInfo, this.mLastState);
                }
            }
            return;
        }
        this.mScanner.pause();
    }

    private void updateWifiState(int state) {
        Activity activity = getActivity();
        if (activity != null) {
            activity.invalidateOptionsMenu();
        }
        switch (state) {
            case 1:
                setOffMessage();
                break;
            case 2:
                addMessagePreference(R.string.wifi_starting);
                break;
            case 3:
                this.mScanner.resume();
                return;
        }
        this.mLastInfo = null;
        this.mLastState = null;
        this.mScanner.pause();
    }

    private void changeNextButtonState(boolean enabled) {
        if (this.mEnableNextOnConnection && hasNextButton()) {
            getNextButton().setEnabled(enabled);
        }
    }

    public void onClick(DialogInterface dialogInterface, int button) {
        if (button == -3 && this.mSelectedAccessPoint != null) {
            forget();
        } else if (button == -1 && this.mDialog != null) {
            submit(this.mDialog.getController());
        }
    }

    void submit(WifiConfigController configController) {
        WifiConfiguration config = configController.getConfig();
        if (config == null) {
            if (!(this.mSelectedAccessPoint == null || this.mSelectedAccessPoint.networkId == -1)) {
                connect(this.mSelectedAccessPoint.networkId);
            }
        } else if (config.networkId != -1) {
            if (this.mSelectedAccessPoint != null) {
                this.mWifiManager.save(config, this.mSaveListener);
            }
        } else if (configController.isEdit()) {
            this.mWifiManager.save(config, this.mSaveListener);
        } else {
            connect(config);
        }
        if (this.mWifiManager.isWifiEnabled()) {
            this.mScanner.resume();
        }
        updateAccessPoints();
    }

    void forget() {
        if (this.mSelectedAccessPoint.networkId == -1) {
            Log.e("WifiSettings", "Failed to forget invalid network " + this.mSelectedAccessPoint.getConfig());
            return;
        }
        this.mWifiManager.forget(this.mSelectedAccessPoint.networkId, this.mForgetListener);
        if (this.mWifiManager.isWifiEnabled()) {
            this.mScanner.resume();
        }
        updateAccessPoints();
        changeNextButtonState(false);
    }

    protected void connect(WifiConfiguration config) {
        this.mWifiManager.connect(config, this.mConnectListener);
    }

    protected void connect(int networkId) {
        this.mWifiManager.connect(networkId, this.mConnectListener);
    }

    void refreshAccessPoints() {
        if (this.mWifiManager.isWifiEnabled()) {
            this.mScanner.resume();
        }
        getPreferenceScreen().removeAll();
    }

    void onAddNetworkPressed() {
        this.mSelectedAccessPoint = null;
        showDialog(null, true);
    }

    void resumeWifiScan() {
        if (this.mWifiManager.isWifiEnabled()) {
            this.mScanner.resume();
        }
    }

    protected int getHelpResource() {
        return R.string.help_url_wifi;
    }
}
