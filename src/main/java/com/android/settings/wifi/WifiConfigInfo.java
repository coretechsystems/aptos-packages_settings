package com.android.settings.wifi;

import android.app.Activity;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.widget.TextView;
import com.android.settings.R;
import java.util.List;

public class WifiConfigInfo extends Activity {
    private TextView mConfigList;
    private WifiManager mWifiManager;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.mWifiManager = (WifiManager) getSystemService("wifi");
        setContentView(R.layout.wifi_config_info);
        this.mConfigList = (TextView) findViewById(R.id.config_list);
    }

    protected void onResume() {
        super.onResume();
        if (this.mWifiManager.isWifiEnabled()) {
            List<WifiConfiguration> wifiConfigs = this.mWifiManager.getConfiguredNetworks();
            StringBuffer configList = new StringBuffer();
            for (int i = wifiConfigs.size() - 1; i >= 0; i--) {
                configList.append(wifiConfigs.get(i));
            }
            this.mConfigList.setText(configList);
            return;
        }
        this.mConfigList.setText(R.string.wifi_state_disabled);
    }
}
