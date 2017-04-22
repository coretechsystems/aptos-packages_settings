package com.android.settings.wifi.p2p;

import android.content.Context;
import android.net.wifi.p2p.WifiP2pGroup;
import android.preference.Preference;
import android.view.View;

public class WifiP2pPersistentGroup extends Preference {
    public WifiP2pGroup mGroup;

    public WifiP2pPersistentGroup(Context context, WifiP2pGroup group) {
        super(context);
        this.mGroup = group;
    }

    protected void onBindView(View view) {
        setTitle(this.mGroup.getNetworkName());
        super.onBindView(view);
    }

    int getNetworkId() {
        return this.mGroup.getNetworkId();
    }

    String getGroupName() {
        return this.mGroup.getNetworkName();
    }
}
