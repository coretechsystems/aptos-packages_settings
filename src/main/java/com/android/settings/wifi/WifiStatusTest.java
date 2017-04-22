package com.android.settings.wifi;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.NetworkInfo;
import android.net.wifi.ScanResult;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;
import com.android.settings.R;
import java.io.IOException;
import java.net.UnknownHostException;
import java.util.List;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

public class WifiStatusTest extends Activity {
    private TextView mBSSID;
    private TextView mHiddenSSID;
    private TextView mHttpClientTest;
    private String mHttpClientTestResult;
    private TextView mIPAddr;
    private TextView mLinkSpeed;
    private TextView mMACAddr;
    private TextView mNetworkId;
    private TextView mNetworkState;
    OnClickListener mPingButtonHandler = new OnClickListener() {
        public void onClick(View v) {
            WifiStatusTest.this.updatePingState();
        }
    };
    private TextView mPingHostname;
    private String mPingHostnameResult;
    private TextView mPingIpAddr;
    private String mPingIpAddrResult;
    private TextView mRSSI;
    private TextView mSSID;
    private TextView mScanList;
    private TextView mSupplicantState;
    private WifiManager mWifiManager;
    private TextView mWifiState;
    private IntentFilter mWifiStateFilter;
    private final BroadcastReceiver mWifiStateReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals("android.net.wifi.WIFI_STATE_CHANGED")) {
                WifiStatusTest.this.handleWifiStateChanged(intent.getIntExtra("wifi_state", 4));
            } else if (intent.getAction().equals("android.net.wifi.STATE_CHANGE")) {
                WifiStatusTest.this.handleNetworkStateChanged((NetworkInfo) intent.getParcelableExtra("networkInfo"));
            } else if (intent.getAction().equals("android.net.wifi.SCAN_RESULTS")) {
                WifiStatusTest.this.handleScanResultsAvailable();
            } else if (!intent.getAction().equals("android.net.wifi.supplicant.CONNECTION_CHANGE")) {
                if (intent.getAction().equals("android.net.wifi.supplicant.STATE_CHANGE")) {
                    WifiStatusTest.this.handleSupplicantStateChanged((SupplicantState) intent.getParcelableExtra("newState"), intent.hasExtra("supplicantError"), intent.getIntExtra("supplicantError", 0));
                } else if (intent.getAction().equals("android.net.wifi.RSSI_CHANGED")) {
                    WifiStatusTest.this.handleSignalChanged(intent.getIntExtra("newRssi", 0));
                } else if (!intent.getAction().equals("android.net.wifi.NETWORK_IDS_CHANGED")) {
                    Log.e("WifiStatusTest", "Received an unknown Wifi Intent");
                }
            }
        }
    };
    private Button pingTestButton;
    private Button updateButton;
    OnClickListener updateButtonHandler = new OnClickListener() {
        public void onClick(View v) {
            WifiInfo wifiInfo = WifiStatusTest.this.mWifiManager.getConnectionInfo();
            WifiStatusTest.this.setWifiStateText(WifiStatusTest.this.mWifiManager.getWifiState());
            WifiStatusTest.this.mBSSID.setText(wifiInfo.getBSSID());
            WifiStatusTest.this.mHiddenSSID.setText(String.valueOf(wifiInfo.getHiddenSSID()));
            int ipAddr = wifiInfo.getIpAddress();
            StringBuffer ipBuf = new StringBuffer();
            ipAddr >>>= 8;
            ipAddr >>>= 8;
            ipBuf.append(ipAddr & 255).append('.').append(ipAddr & 255).append('.').append(ipAddr & 255).append('.').append((ipAddr >>> 8) & 255);
            WifiStatusTest.this.mIPAddr.setText(ipBuf);
            WifiStatusTest.this.mLinkSpeed.setText(String.valueOf(wifiInfo.getLinkSpeed()) + " Mbps");
            WifiStatusTest.this.mMACAddr.setText(wifiInfo.getMacAddress());
            WifiStatusTest.this.mNetworkId.setText(String.valueOf(wifiInfo.getNetworkId()));
            WifiStatusTest.this.mRSSI.setText(String.valueOf(wifiInfo.getRssi()));
            WifiStatusTest.this.mSSID.setText(wifiInfo.getSSID());
            WifiStatusTest.this.setSupplicantStateText(wifiInfo.getSupplicantState());
        }
    };

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.mWifiManager = (WifiManager) getSystemService("wifi");
        this.mWifiStateFilter = new IntentFilter("android.net.wifi.WIFI_STATE_CHANGED");
        this.mWifiStateFilter.addAction("android.net.wifi.STATE_CHANGE");
        this.mWifiStateFilter.addAction("android.net.wifi.SCAN_RESULTS");
        this.mWifiStateFilter.addAction("android.net.wifi.supplicant.STATE_CHANGE");
        this.mWifiStateFilter.addAction("android.net.wifi.RSSI_CHANGED");
        this.mWifiStateFilter.addAction("android.net.wifi.WIFI_STATE_CHANGED");
        registerReceiver(this.mWifiStateReceiver, this.mWifiStateFilter);
        setContentView(R.layout.wifi_status_test);
        this.updateButton = (Button) findViewById(R.id.update);
        this.updateButton.setOnClickListener(this.updateButtonHandler);
        this.mWifiState = (TextView) findViewById(R.id.wifi_state);
        this.mNetworkState = (TextView) findViewById(R.id.network_state);
        this.mSupplicantState = (TextView) findViewById(R.id.supplicant_state);
        this.mRSSI = (TextView) findViewById(R.id.rssi);
        this.mBSSID = (TextView) findViewById(R.id.bssid);
        this.mSSID = (TextView) findViewById(R.id.ssid);
        this.mHiddenSSID = (TextView) findViewById(R.id.hidden_ssid);
        this.mIPAddr = (TextView) findViewById(R.id.ipaddr);
        this.mMACAddr = (TextView) findViewById(R.id.macaddr);
        this.mNetworkId = (TextView) findViewById(R.id.networkid);
        this.mLinkSpeed = (TextView) findViewById(R.id.link_speed);
        this.mScanList = (TextView) findViewById(R.id.scan_list);
        this.mPingIpAddr = (TextView) findViewById(R.id.pingIpAddr);
        this.mPingHostname = (TextView) findViewById(R.id.pingHostname);
        this.mHttpClientTest = (TextView) findViewById(R.id.httpClientTest);
        this.pingTestButton = (Button) findViewById(R.id.ping_test);
        this.pingTestButton.setOnClickListener(this.mPingButtonHandler);
    }

    protected void onResume() {
        super.onResume();
        registerReceiver(this.mWifiStateReceiver, this.mWifiStateFilter);
    }

    protected void onPause() {
        super.onPause();
        unregisterReceiver(this.mWifiStateReceiver);
    }

    private void setSupplicantStateText(SupplicantState supplicantState) {
        if (SupplicantState.FOUR_WAY_HANDSHAKE.equals(supplicantState)) {
            this.mSupplicantState.setText("FOUR WAY HANDSHAKE");
        } else if (SupplicantState.ASSOCIATED.equals(supplicantState)) {
            this.mSupplicantState.setText("ASSOCIATED");
        } else if (SupplicantState.ASSOCIATING.equals(supplicantState)) {
            this.mSupplicantState.setText("ASSOCIATING");
        } else if (SupplicantState.COMPLETED.equals(supplicantState)) {
            this.mSupplicantState.setText("COMPLETED");
        } else if (SupplicantState.DISCONNECTED.equals(supplicantState)) {
            this.mSupplicantState.setText("DISCONNECTED");
        } else if (SupplicantState.DORMANT.equals(supplicantState)) {
            this.mSupplicantState.setText("DORMANT");
        } else if (SupplicantState.GROUP_HANDSHAKE.equals(supplicantState)) {
            this.mSupplicantState.setText("GROUP HANDSHAKE");
        } else if (SupplicantState.INACTIVE.equals(supplicantState)) {
            this.mSupplicantState.setText("INACTIVE");
        } else if (SupplicantState.INVALID.equals(supplicantState)) {
            this.mSupplicantState.setText("INVALID");
        } else if (SupplicantState.SCANNING.equals(supplicantState)) {
            this.mSupplicantState.setText("SCANNING");
        } else if (SupplicantState.UNINITIALIZED.equals(supplicantState)) {
            this.mSupplicantState.setText("UNINITIALIZED");
        } else {
            this.mSupplicantState.setText("BAD");
            Log.e("WifiStatusTest", "supplicant state is bad");
        }
    }

    private void setWifiStateText(int wifiState) {
        String wifiStateString;
        switch (wifiState) {
            case 0:
                wifiStateString = getString(R.string.wifi_state_disabling);
                break;
            case 1:
                wifiStateString = getString(R.string.wifi_state_disabled);
                break;
            case 2:
                wifiStateString = getString(R.string.wifi_state_enabling);
                break;
            case 3:
                wifiStateString = getString(R.string.wifi_state_enabled);
                break;
            case 4:
                wifiStateString = getString(R.string.wifi_state_unknown);
                break;
            default:
                wifiStateString = "BAD";
                Log.e("WifiStatusTest", "wifi state is bad");
                break;
        }
        this.mWifiState.setText(wifiStateString);
    }

    private void handleSignalChanged(int rssi) {
        this.mRSSI.setText(String.valueOf(rssi));
    }

    private void handleWifiStateChanged(int wifiState) {
        setWifiStateText(wifiState);
    }

    private void handleScanResultsAvailable() {
        List<ScanResult> list = this.mWifiManager.getScanResults();
        StringBuffer scanList = new StringBuffer();
        if (list != null) {
            for (int i = list.size() - 1; i >= 0; i--) {
                ScanResult scanResult = (ScanResult) list.get(i);
                if (!(scanResult == null || TextUtils.isEmpty(scanResult.SSID))) {
                    scanList.append(scanResult.SSID + " ");
                }
            }
        }
        this.mScanList.setText(scanList);
    }

    private void handleSupplicantStateChanged(SupplicantState state, boolean hasError, int error) {
        if (hasError) {
            this.mSupplicantState.setText("ERROR AUTHENTICATING");
        } else {
            setSupplicantStateText(state);
        }
    }

    private void handleNetworkStateChanged(NetworkInfo networkInfo) {
        if (this.mWifiManager.isWifiEnabled()) {
            this.mNetworkState.setText(Summary.get(this, this.mWifiManager.getConnectionInfo().getSSID(), networkInfo.getDetailedState()));
        }
    }

    private final void updatePingState() {
        final Handler handler = new Handler();
        this.mPingIpAddrResult = getResources().getString(R.string.radioInfo_unknown);
        this.mPingHostnameResult = getResources().getString(R.string.radioInfo_unknown);
        this.mHttpClientTestResult = getResources().getString(R.string.radioInfo_unknown);
        this.mPingIpAddr.setText(this.mPingIpAddrResult);
        this.mPingHostname.setText(this.mPingHostnameResult);
        this.mHttpClientTest.setText(this.mHttpClientTestResult);
        final Runnable updatePingResults = new Runnable() {
            public void run() {
                WifiStatusTest.this.mPingIpAddr.setText(WifiStatusTest.this.mPingIpAddrResult);
                WifiStatusTest.this.mPingHostname.setText(WifiStatusTest.this.mPingHostnameResult);
                WifiStatusTest.this.mHttpClientTest.setText(WifiStatusTest.this.mHttpClientTestResult);
            }
        };
        new Thread() {
            public void run() {
                WifiStatusTest.this.pingIpAddr();
                handler.post(updatePingResults);
            }
        }.start();
        new Thread() {
            public void run() {
                WifiStatusTest.this.pingHostname();
                handler.post(updatePingResults);
            }
        }.start();
        new Thread() {
            public void run() {
                WifiStatusTest.this.httpClientTest();
                handler.post(updatePingResults);
            }
        }.start();
    }

    private final void pingIpAddr() {
        try {
            if (Runtime.getRuntime().exec("ping -c 1 -w 100 " + "74.125.47.104").waitFor() == 0) {
                this.mPingIpAddrResult = "Pass";
            } else {
                this.mPingIpAddrResult = "Fail: IP addr not reachable";
            }
        } catch (IOException e) {
            this.mPingIpAddrResult = "Fail: IOException";
        } catch (InterruptedException e2) {
            this.mPingIpAddrResult = "Fail: InterruptedException";
        }
    }

    private final void pingHostname() {
        try {
            if (Runtime.getRuntime().exec("ping -c 1 -w 100 www.google.com").waitFor() == 0) {
                this.mPingHostnameResult = "Pass";
            } else {
                this.mPingHostnameResult = "Fail: Host unreachable";
            }
        } catch (UnknownHostException e) {
            this.mPingHostnameResult = "Fail: Unknown Host";
        } catch (IOException e2) {
            this.mPingHostnameResult = "Fail: IOException";
        } catch (InterruptedException e3) {
            this.mPingHostnameResult = "Fail: InterruptedException";
        }
    }

    private void httpClientTest() {
        HttpClient client = new DefaultHttpClient();
        try {
            HttpGet request = new HttpGet("http://www.google.com");
            HttpResponse response = client.execute(request);
            if (response.getStatusLine().getStatusCode() == 200) {
                this.mHttpClientTestResult = "Pass";
            } else {
                this.mHttpClientTestResult = "Fail: Code: " + String.valueOf(response);
            }
            request.abort();
        } catch (IOException e) {
            this.mHttpClientTestResult = "Fail: IOException";
        }
    }
}
