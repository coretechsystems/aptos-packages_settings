package com.android.settings.wifi;

import android.app.Activity;
import android.content.Intent;
import android.net.NetworkInfo.DetailedState;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.ActionListener;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import com.android.settings.R;
import java.util.EnumMap;

public class WifiSettingsForSetupWizardXL extends Activity implements OnClickListener {
    private static final EnumMap<DetailedState, DetailedState> sNetworkStateMap = new EnumMap(DetailedState.class);
    private Button mAddNetworkButton;
    private Button mBackButton;
    private View mBottomPadding;
    private Button mConnectButton;
    private View mConnectingStatusLayout;
    private TextView mConnectingStatusView;
    private View mContentPadding;
    private CharSequence mEditingTitle;
    private InputMethodManager mInputMethodManager;
    private CharSequence mNetworkName = "";
    private DetailedState mPreviousNetworkState = DetailedState.DISCONNECTED;
    private ProgressBar mProgressBar;
    private Button mRefreshButton;
    private int mScreenState = 0;
    private Button mSkipOrNextButton;
    private TextView mTitleView;
    private View mTopDividerNoProgress;
    private View mTopPadding;
    private WifiConfigUiForSetupWizardXL mWifiConfig;
    private WifiManager mWifiManager;
    private WifiSettings mWifiSettings;
    private View mWifiSettingsFragmentLayout;

    static {
        sNetworkStateMap.put(DetailedState.IDLE, DetailedState.DISCONNECTED);
        sNetworkStateMap.put(DetailedState.SCANNING, DetailedState.SCANNING);
        sNetworkStateMap.put(DetailedState.CONNECTING, DetailedState.CONNECTING);
        sNetworkStateMap.put(DetailedState.AUTHENTICATING, DetailedState.CONNECTING);
        sNetworkStateMap.put(DetailedState.OBTAINING_IPADDR, DetailedState.CONNECTING);
        sNetworkStateMap.put(DetailedState.CONNECTED, DetailedState.CONNECTED);
        sNetworkStateMap.put(DetailedState.SUSPENDED, DetailedState.SUSPENDED);
        sNetworkStateMap.put(DetailedState.DISCONNECTING, DetailedState.DISCONNECTED);
        sNetworkStateMap.put(DetailedState.DISCONNECTED, DetailedState.DISCONNECTED);
        sNetworkStateMap.put(DetailedState.FAILED, DetailedState.FAILED);
    }

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(1);
        setContentView(2130968787);
        this.mWifiManager = (WifiManager) getSystemService("wifi");
        this.mWifiManager.setWifiEnabled(true);
        this.mWifiSettings = (WifiSettings) getFragmentManager().findFragmentById(R.id.wifi_setup_fragment);
        this.mInputMethodManager = (InputMethodManager) getSystemService("input_method");
        initViews();
        showScanningState();
    }

    private void initViews() {
        Intent intent = getIntent();
        if (intent.getBooleanExtra("firstRun", false)) {
            findViewById(R.id.layout_root).setSystemUiVisibility(4194304);
        }
        if (intent.getBooleanExtra("extra_prefs_landscape_lock", false)) {
            setRequestedOrientation(6);
        }
        if (intent.getBooleanExtra("extra_prefs_portrait_lock", false)) {
            setRequestedOrientation(7);
        }
        this.mTitleView = (TextView) findViewById(R.id.wifi_setup_title);
        this.mProgressBar = (ProgressBar) findViewById(R.id.scanning_progress_bar);
        this.mProgressBar.setMax(2);
        this.mTopDividerNoProgress = findViewById(R.id.top_divider_no_progress);
        this.mBottomPadding = findViewById(R.id.bottom_padding);
        this.mProgressBar.setVisibility(0);
        this.mProgressBar.setIndeterminate(true);
        this.mTopDividerNoProgress.setVisibility(8);
        this.mAddNetworkButton = (Button) findViewById(R.id.wifi_setup_add_network);
        this.mAddNetworkButton.setOnClickListener(this);
        this.mRefreshButton = (Button) findViewById(R.id.wifi_setup_refresh_list);
        this.mRefreshButton.setOnClickListener(this);
        this.mSkipOrNextButton = (Button) findViewById(R.id.wifi_setup_skip_or_next);
        this.mSkipOrNextButton.setOnClickListener(this);
        this.mConnectButton = (Button) findViewById(R.id.wifi_setup_connect);
        this.mConnectButton.setOnClickListener(this);
        this.mBackButton = (Button) findViewById(R.id.wifi_setup_cancel);
        this.mBackButton.setOnClickListener(this);
        this.mTopPadding = findViewById(R.id.top_padding);
        this.mContentPadding = findViewById(R.id.content_padding);
        this.mWifiSettingsFragmentLayout = findViewById(R.id.wifi_settings_fragment_layout);
        this.mConnectingStatusLayout = findViewById(R.id.connecting_status_layout);
        this.mConnectingStatusView = (TextView) findViewById(R.id.connecting_status);
    }

    private void restoreFirstVisibilityState() {
        showDefaultTitle();
        this.mAddNetworkButton.setVisibility(0);
        this.mRefreshButton.setVisibility(0);
        this.mSkipOrNextButton.setVisibility(0);
        this.mConnectButton.setVisibility(8);
        this.mBackButton.setVisibility(8);
        setPaddingVisibility(0);
    }

    public void onClick(View view) {
        hideSoftwareKeyboard();
        if (view == this.mAddNetworkButton) {
            Log.d("SetupWizard", "AddNetwork button pressed");
            onAddNetworkButtonPressed();
        } else if (view == this.mRefreshButton) {
            Log.d("SetupWizard", "Refresh button pressed");
            refreshAccessPoints(true);
        } else if (view == this.mSkipOrNextButton) {
            Log.d("SetupWizard", "Skip/Next button pressed");
            if (TextUtils.equals(getString(R.string.wifi_setup_skip), ((Button) view).getText())) {
                this.mWifiManager.setWifiEnabled(false);
                setResult(1);
            } else {
                setResult(-1);
            }
            finish();
        } else if (view == this.mConnectButton) {
            Log.d("SetupWizard", "Connect button pressed");
            onConnectButtonPressed();
        } else if (view == this.mBackButton) {
            Log.d("SetupWizard", "Back button pressed");
            onBackButtonPressed();
        }
    }

    private void hideSoftwareKeyboard() {
        Log.i("SetupWizard", "Hiding software keyboard.");
        View focusedView = getCurrentFocus();
        if (focusedView != null) {
            this.mInputMethodManager.hideSoftInputFromWindow(focusedView.getWindowToken(), 0);
        }
    }

    private void showConnectingState() {
        this.mScreenState = 2;
        this.mBackButton.setVisibility(0);
        this.mEditingTitle = this.mTitleView.getText();
        showConnectingTitle();
        showConnectingProgressBar();
        setPaddingVisibility(0);
    }

    private void showDefaultTitle() {
        this.mTitleView.setText(getString(R.string.wifi_setup_title));
    }

    private void showConnectingTitle() {
        if (TextUtils.isEmpty(this.mNetworkName) && this.mWifiConfig != null) {
            if (this.mWifiConfig.getController() == null || this.mWifiConfig.getController().getConfig() == null) {
                Log.w("SetupWizard", "Unexpected null found (WifiController or WifiConfig is null). Ignore them.");
            } else {
                this.mNetworkName = this.mWifiConfig.getController().getConfig().SSID;
            }
        }
        this.mTitleView.setText(getString(R.string.wifi_setup_title_connecting_network, new Object[]{this.mNetworkName}));
    }

    private void showTopDividerWithProgressBar() {
        this.mProgressBar.setVisibility(0);
        this.mTopDividerNoProgress.setVisibility(8);
        this.mBottomPadding.setVisibility(8);
    }

    private void showScanningState() {
        setPaddingVisibility(0);
        this.mWifiSettingsFragmentLayout.setVisibility(8);
        showScanningProgressBar();
    }

    private void onAddNetworkButtonPressed() {
        this.mWifiSettings.onAddNetworkPressed();
    }

    boolean initSecurityFields(View view, int accessPointSecurity) {
        view.findViewById(R.id.eap_not_supported).setVisibility(8);
        view.findViewById(R.id.eap_not_supported_for_add_network).setVisibility(8);
        view.findViewById(R.id.ssid_text).setVisibility(0);
        view.findViewById(R.id.ssid_layout).setVisibility(0);
        if (accessPointSecurity == 3) {
            setPaddingVisibility(0);
            hideSoftwareKeyboard();
            if (view.findViewById(R.id.type_ssid).getVisibility() == 0) {
                view.findViewById(R.id.eap_not_supported_for_add_network).setVisibility(0);
            } else {
                view.findViewById(R.id.eap_not_supported).setVisibility(0);
            }
            view.findViewById(R.id.security_fields).setVisibility(8);
            view.findViewById(R.id.ssid_text).setVisibility(8);
            view.findViewById(R.id.ssid_layout).setVisibility(8);
            onEapNetworkSelected();
            return false;
        }
        this.mConnectButton.setVisibility(0);
        setPaddingVisibility(8);
        if (this.mWifiConfig != null) {
            if (accessPointSecurity == 2 || accessPointSecurity == 1) {
                this.mWifiConfig.requestFocusAndShowKeyboard(R.id.password);
            } else {
                this.mWifiConfig.requestFocusAndShowKeyboard(R.id.ssid);
            }
        }
        return true;
    }

    private void onEapNetworkSelected() {
        this.mConnectButton.setVisibility(8);
        this.mBackButton.setText(R.string.wifi_setup_back);
    }

    void onConnectButtonPressed() {
        this.mScreenState = 2;
        this.mWifiSettings.submit(this.mWifiConfig.getController());
        showConnectingState();
        this.mBackButton.setVisibility(0);
        this.mBackButton.setText(R.string.wifi_setup_back);
        ((ViewGroup) findViewById(R.id.wifi_config_ui)).setVisibility(8);
        this.mConnectingStatusLayout.setVisibility(0);
        this.mConnectingStatusView.setText(R.string.wifi_setup_description_connecting);
        this.mSkipOrNextButton.setVisibility(0);
        this.mSkipOrNextButton.setEnabled(false);
        this.mConnectButton.setVisibility(8);
        this.mAddNetworkButton.setVisibility(8);
        this.mRefreshButton.setVisibility(8);
    }

    private void onBackButtonPressed() {
        if (this.mScreenState == 2 || this.mScreenState == 3) {
            Log.d("SetupWizard", "Back button pressed after connect action.");
            this.mScreenState = 0;
            restoreFirstVisibilityState();
            this.mSkipOrNextButton.setEnabled(true);
            changeNextButtonState(false);
            showScanningState();
            for (WifiConfiguration config : this.mWifiManager.getConfiguredNetworks()) {
                Log.d("SetupWizard", String.format("forgeting Wi-Fi network \"%s\" (id: %d)", new Object[]{config.SSID, Integer.valueOf(config.networkId)}));
                this.mWifiManager.forget(config.networkId, new ActionListener() {
                    public void onSuccess() {
                    }

                    public void onFailure(int reason) {
                    }
                });
            }
            this.mWifiSettingsFragmentLayout.setVisibility(8);
            refreshAccessPoints(true);
        } else {
            this.mScreenState = 0;
            this.mWifiSettings.resumeWifiScan();
            restoreFirstVisibilityState();
            this.mAddNetworkButton.setEnabled(true);
            this.mRefreshButton.setEnabled(true);
            this.mSkipOrNextButton.setEnabled(true);
            showDisconnectedProgressBar();
            this.mWifiSettingsFragmentLayout.setVisibility(0);
            this.mBottomPadding.setVisibility(8);
        }
        setPaddingVisibility(0);
        this.mConnectingStatusLayout.setVisibility(8);
        ViewGroup parent = (ViewGroup) findViewById(R.id.wifi_config_ui);
        parent.removeAllViews();
        parent.setVisibility(8);
        this.mWifiConfig = null;
    }

    void changeNextButtonState(boolean connected) {
        if (connected) {
            this.mSkipOrNextButton.setText(R.string.wifi_setup_next);
        } else {
            this.mSkipOrNextButton.setText(R.string.wifi_setup_skip);
        }
    }

    private void refreshAccessPoints(boolean disconnectNetwork) {
        showScanningState();
        if (disconnectNetwork) {
            this.mWifiManager.disconnect();
        }
        this.mWifiSettings.refreshAccessPoints();
    }

    void setPaddingVisibility(int visibility) {
        this.mTopPadding.setVisibility(visibility);
        this.mContentPadding.setVisibility(visibility);
    }

    private void showDisconnectedProgressBar() {
        if (this.mScreenState == 0) {
            this.mProgressBar.setVisibility(8);
            this.mProgressBar.setIndeterminate(false);
            this.mTopDividerNoProgress.setVisibility(0);
            return;
        }
        this.mProgressBar.setVisibility(0);
        this.mProgressBar.setIndeterminate(false);
        this.mProgressBar.setProgress(0);
        this.mTopDividerNoProgress.setVisibility(8);
    }

    private void showScanningProgressBar() {
        showTopDividerWithProgressBar();
        this.mProgressBar.setIndeterminate(true);
    }

    private void showConnectingProgressBar() {
        showTopDividerWithProgressBar();
        this.mProgressBar.setIndeterminate(false);
        this.mProgressBar.setProgress(1);
    }
}
