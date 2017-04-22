package com.android.settings.wifi;

import android.content.res.TypedArray;
import android.database.DataSetObserver;
import android.net.wifi.WifiConfiguration;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AbsListView.LayoutParams;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;
import com.android.settings.R;

public class WifiSettingsForSetupWizard extends WifiSettings {
    private ListAdapter mAdapter;
    private View mAddOtherNetworkItem;
    private TextView mEmptyFooter;
    private boolean mListLastEmpty = false;

    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.setup_preference, container, false);
        ListView list = (ListView) view.findViewById(16908298);
        if (view.findViewById(R.id.title) == null) {
            list.addHeaderView(inflater.inflate(R.layout.setup_wizard_header, list, false), null, false);
        }
        this.mAddOtherNetworkItem = inflater.inflate(R.layout.setup_wifi_add_network, list, false);
        list.addFooterView(this.mAddOtherNetworkItem, null, true);
        this.mAddOtherNetworkItem.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                if (WifiSettingsForSetupWizard.this.mWifiManager.isWifiEnabled()) {
                    WifiSettingsForSetupWizard.this.onAddNetworkPressed();
                }
            }
        });
        if (getActivity().getIntent().getBooleanExtra("wifi_show_wifi_required_info", false)) {
            view.findViewById(R.id.wifi_required_info).setVisibility(0);
        }
        return view;
    }

    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        getView().setSystemUiVisibility(27525120);
        if (hasNextButton()) {
            getNextButton().setVisibility(8);
        }
        this.mAdapter = getPreferenceScreen().getRootAdapter();
        this.mAdapter.registerDataSetObserver(new DataSetObserver() {
            public void onChanged() {
                super.onChanged();
                WifiSettingsForSetupWizard.this.updateFooter();
            }
        });
    }

    public void registerForContextMenu(View view) {
    }

    WifiEnabler createWifiEnabler() {
        return null;
    }

    void addOptionsMenuItems(Menu menu) {
        boolean wifiIsEnabled = this.mWifiManager.isWifiEnabled();
        TypedArray ta = getActivity().getTheme().obtainStyledAttributes(new int[]{R.attr.ic_wps});
        menu.add(0, 1, 0, R.string.wifi_menu_wps_pbc).setIcon(ta.getDrawable(0)).setEnabled(wifiIsEnabled).setShowAsAction(2);
        menu.add(0, 4, 0, R.string.wifi_add_network).setEnabled(wifiIsEnabled).setShowAsAction(2);
        ta.recycle();
    }

    protected void connect(WifiConfiguration config) {
        ((WifiSetupActivity) getActivity()).networkSelected();
        super.connect(config);
    }

    protected void connect(int networkId) {
        ((WifiSetupActivity) getActivity()).networkSelected();
        super.connect(networkId);
    }

    protected TextView initEmptyView() {
        this.mEmptyFooter = new TextView(getActivity());
        this.mEmptyFooter.setLayoutParams(new LayoutParams(-1, -1));
        this.mEmptyFooter.setGravity(17);
        this.mEmptyFooter.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.ic_wifi_emptystate, 0, 0);
        return this.mEmptyFooter;
    }

    protected void updateFooter() {
        boolean isEmpty = this.mAdapter.isEmpty();
        if (isEmpty != this.mListLastEmpty) {
            ListView list = getListView();
            if (isEmpty) {
                list.removeFooterView(this.mAddOtherNetworkItem);
                list.addFooterView(this.mEmptyFooter, null, false);
            } else {
                list.removeFooterView(this.mEmptyFooter);
                list.addFooterView(this.mAddOtherNetworkItem, null, true);
            }
            this.mListLastEmpty = isEmpty;
        }
    }
}
