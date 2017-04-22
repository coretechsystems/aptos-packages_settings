package com.android.settings.wifi.p2p;

import android.app.Activity;
import android.app.AlertDialog.Builder;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.NetworkInfo;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pGroup;
import android.net.wifi.p2p.WifiP2pGroupList;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.WifiP2pManager.ActionListener;
import android.net.wifi.p2p.WifiP2pManager.Channel;
import android.net.wifi.p2p.WifiP2pManager.PeerListListener;
import android.net.wifi.p2p.WifiP2pManager.PersistentGroupInfoListener;
import android.os.Bundle;
import android.os.SystemProperties;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceGroup;
import android.preference.PreferenceScreen;
import android.text.InputFilter;
import android.text.InputFilter.LengthFilter;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.Toast;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;

public class WifiP2pSettings extends SettingsPreferenceFragment implements PeerListListener, PersistentGroupInfoListener {
    private OnClickListener mCancelConnectListener;
    private Channel mChannel;
    private int mConnectedDevices;
    private OnClickListener mDeleteGroupListener;
    private EditText mDeviceNameText;
    private OnClickListener mDisconnectListener;
    private final IntentFilter mIntentFilter = new IntentFilter();
    private boolean mLastGroupFormed = false;
    private WifiP2pDeviceList mPeers = new WifiP2pDeviceList();
    private PreferenceGroup mPeersGroup;
    private PreferenceGroup mPersistentGroup;
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            boolean z = true;
            String action = intent.getAction();
            if ("android.net.wifi.p2p.STATE_CHANGED".equals(action)) {
                WifiP2pSettings wifiP2pSettings = WifiP2pSettings.this;
                if (intent.getIntExtra("wifi_p2p_state", 1) != 2) {
                    z = false;
                }
                wifiP2pSettings.mWifiP2pEnabled = z;
                WifiP2pSettings.this.handleP2pStateChanged();
            } else if ("android.net.wifi.p2p.PEERS_CHANGED".equals(action)) {
                WifiP2pSettings.this.mPeers = (WifiP2pDeviceList) intent.getParcelableExtra("wifiP2pDeviceList");
                WifiP2pSettings.this.handlePeersChanged();
            } else if ("android.net.wifi.p2p.CONNECTION_STATE_CHANGE".equals(action)) {
                if (WifiP2pSettings.this.mWifiP2pManager != null) {
                    WifiP2pInfo wifip2pinfo = (WifiP2pInfo) intent.getParcelableExtra("wifiP2pInfo");
                    if (!(((NetworkInfo) intent.getParcelableExtra("networkInfo")).isConnected() || WifiP2pSettings.this.mLastGroupFormed)) {
                        WifiP2pSettings.this.startSearch();
                    }
                    WifiP2pSettings.this.mLastGroupFormed = wifip2pinfo.groupFormed;
                }
            } else if ("android.net.wifi.p2p.THIS_DEVICE_CHANGED".equals(action)) {
                WifiP2pSettings.this.mThisDevice = (WifiP2pDevice) intent.getParcelableExtra("wifiP2pDevice");
                WifiP2pSettings.this.updateDevicePref();
            } else if ("android.net.wifi.p2p.DISCOVERY_STATE_CHANGE".equals(action)) {
                if (intent.getIntExtra("discoveryState", 1) == 2) {
                    WifiP2pSettings.this.updateSearchMenu(true);
                } else {
                    WifiP2pSettings.this.updateSearchMenu(false);
                }
            } else if ("android.net.wifi.p2p.PERSISTENT_GROUPS_CHANGED".equals(action) && WifiP2pSettings.this.mWifiP2pManager != null) {
                WifiP2pSettings.this.mWifiP2pManager.requestPersistentGroupInfo(WifiP2pSettings.this.mChannel, WifiP2pSettings.this);
            }
        }
    };
    private OnClickListener mRenameListener;
    private String mSavedDeviceName;
    private WifiP2pPersistentGroup mSelectedGroup;
    private String mSelectedGroupName;
    private WifiP2pPeer mSelectedWifiPeer;
    private WifiP2pDevice mThisDevice;
    private Preference mThisDevicePref;
    private boolean mWifiP2pEnabled;
    private WifiP2pManager mWifiP2pManager;
    private boolean mWifiP2pSearching;

    public void onActivityCreated(Bundle savedInstanceState) {
        addPreferencesFromResource(R.xml.wifi_p2p_settings);
        this.mIntentFilter.addAction("android.net.wifi.p2p.STATE_CHANGED");
        this.mIntentFilter.addAction("android.net.wifi.p2p.PEERS_CHANGED");
        this.mIntentFilter.addAction("android.net.wifi.p2p.CONNECTION_STATE_CHANGE");
        this.mIntentFilter.addAction("android.net.wifi.p2p.THIS_DEVICE_CHANGED");
        this.mIntentFilter.addAction("android.net.wifi.p2p.DISCOVERY_STATE_CHANGE");
        this.mIntentFilter.addAction("android.net.wifi.p2p.PERSISTENT_GROUPS_CHANGED");
        Activity activity = getActivity();
        this.mWifiP2pManager = (WifiP2pManager) getSystemService("wifip2p");
        if (this.mWifiP2pManager != null) {
            this.mChannel = this.mWifiP2pManager.initialize(activity, getActivity().getMainLooper(), null);
            if (this.mChannel == null) {
                Log.e("WifiP2pSettings", "Failed to set up connection with wifi p2p service");
                this.mWifiP2pManager = null;
            }
        } else {
            Log.e("WifiP2pSettings", "mWifiP2pManager is null !");
        }
        if (savedInstanceState != null && savedInstanceState.containsKey("PEER_STATE")) {
            this.mSelectedWifiPeer = new WifiP2pPeer(getActivity(), (WifiP2pDevice) savedInstanceState.getParcelable("PEER_STATE"));
        }
        if (savedInstanceState != null && savedInstanceState.containsKey("DEV_NAME")) {
            this.mSavedDeviceName = savedInstanceState.getString("DEV_NAME");
        }
        if (savedInstanceState != null && savedInstanceState.containsKey("GROUP_NAME")) {
            this.mSelectedGroupName = savedInstanceState.getString("GROUP_NAME");
        }
        this.mRenameListener = new OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                if (which == -1 && WifiP2pSettings.this.mWifiP2pManager != null) {
                    WifiP2pSettings.this.mWifiP2pManager.setDeviceName(WifiP2pSettings.this.mChannel, WifiP2pSettings.this.mDeviceNameText.getText().toString(), new ActionListener() {
                        public void onSuccess() {
                        }

                        public void onFailure(int reason) {
                            Toast.makeText(WifiP2pSettings.this.getActivity(), R.string.wifi_p2p_failed_rename_message, 1).show();
                        }
                    });
                }
            }
        };
        this.mDisconnectListener = new OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                if (which == -1 && WifiP2pSettings.this.mWifiP2pManager != null) {
                    WifiP2pSettings.this.mWifiP2pManager.removeGroup(WifiP2pSettings.this.mChannel, new ActionListener() {
                        public void onSuccess() {
                        }

                        public void onFailure(int reason) {
                        }
                    });
                }
            }
        };
        this.mCancelConnectListener = new OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                if (which == -1 && WifiP2pSettings.this.mWifiP2pManager != null) {
                    WifiP2pSettings.this.mWifiP2pManager.cancelConnect(WifiP2pSettings.this.mChannel, new ActionListener() {
                        public void onSuccess() {
                        }

                        public void onFailure(int reason) {
                        }
                    });
                }
            }
        };
        this.mDeleteGroupListener = new OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                if (which == -1) {
                    if (WifiP2pSettings.this.mWifiP2pManager != null && WifiP2pSettings.this.mSelectedGroup != null) {
                        WifiP2pSettings.this.mWifiP2pManager.deletePersistentGroup(WifiP2pSettings.this.mChannel, WifiP2pSettings.this.mSelectedGroup.getNetworkId(), new ActionListener() {
                            public void onSuccess() {
                            }

                            public void onFailure(int reason) {
                            }
                        });
                        WifiP2pSettings.this.mSelectedGroup = null;
                    }
                } else if (which == -2) {
                    WifiP2pSettings.this.mSelectedGroup = null;
                }
            }
        };
        setHasOptionsMenu(true);
        PreferenceScreen preferenceScreen = getPreferenceScreen();
        preferenceScreen.removeAll();
        preferenceScreen.setOrderingAsAdded(true);
        this.mThisDevicePref = new Preference(getActivity());
        this.mThisDevicePref.setPersistent(false);
        this.mThisDevicePref.setSelectable(false);
        preferenceScreen.addPreference(this.mThisDevicePref);
        this.mPeersGroup = new PreferenceCategory(getActivity());
        this.mPeersGroup.setTitle(R.string.wifi_p2p_peer_devices);
        preferenceScreen.addPreference(this.mPeersGroup);
        this.mPersistentGroup = new PreferenceCategory(getActivity());
        this.mPersistentGroup.setTitle(R.string.wifi_p2p_remembered_groups);
        preferenceScreen.addPreference(this.mPersistentGroup);
        super.onActivityCreated(savedInstanceState);
    }

    public void onResume() {
        super.onResume();
        getActivity().registerReceiver(this.mReceiver, this.mIntentFilter);
        if (this.mWifiP2pManager != null) {
            this.mWifiP2pManager.requestPeers(this.mChannel, this);
        }
    }

    public void onPause() {
        super.onPause();
        if (this.mWifiP2pManager != null) {
            this.mWifiP2pManager.stopPeerDiscovery(this.mChannel, null);
        }
        getActivity().unregisterReceiver(this.mReceiver);
    }

    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        menu.add(0, 1, 0, this.mWifiP2pSearching ? R.string.wifi_p2p_menu_searching : R.string.wifi_p2p_menu_search).setEnabled(this.mWifiP2pEnabled).setShowAsAction(1);
        menu.add(0, 2, 0, R.string.wifi_p2p_menu_rename).setEnabled(this.mWifiP2pEnabled).setShowAsAction(1);
        super.onCreateOptionsMenu(menu, inflater);
    }

    public void onPrepareOptionsMenu(Menu menu) {
        MenuItem searchMenu = menu.findItem(1);
        MenuItem renameMenu = menu.findItem(2);
        if (this.mWifiP2pEnabled) {
            searchMenu.setEnabled(true);
            renameMenu.setEnabled(true);
        } else {
            searchMenu.setEnabled(false);
            renameMenu.setEnabled(false);
        }
        if (this.mWifiP2pSearching) {
            searchMenu.setTitle(R.string.wifi_p2p_menu_searching);
        } else {
            searchMenu.setTitle(R.string.wifi_p2p_menu_search);
        }
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case 1:
                startSearch();
                return true;
            case 2:
                showDialog(3);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    public boolean onPreferenceTreeClick(PreferenceScreen screen, Preference preference) {
        if (preference instanceof WifiP2pPeer) {
            this.mSelectedWifiPeer = (WifiP2pPeer) preference;
            if (this.mSelectedWifiPeer.device.status == 0) {
                showDialog(1);
            } else if (this.mSelectedWifiPeer.device.status == 1) {
                showDialog(2);
            } else {
                WifiP2pConfig config = new WifiP2pConfig();
                config.deviceAddress = this.mSelectedWifiPeer.device.deviceAddress;
                int forceWps = SystemProperties.getInt("wifidirect.wps", -1);
                if (forceWps != -1) {
                    config.wps.setup = forceWps;
                } else if (this.mSelectedWifiPeer.device.wpsPbcSupported()) {
                    config.wps.setup = 0;
                } else if (this.mSelectedWifiPeer.device.wpsKeypadSupported()) {
                    config.wps.setup = 2;
                } else {
                    config.wps.setup = 1;
                }
                this.mWifiP2pManager.connect(this.mChannel, config, new ActionListener() {
                    public void onSuccess() {
                    }

                    public void onFailure(int reason) {
                        Log.e("WifiP2pSettings", " connect fail " + reason);
                        Toast.makeText(WifiP2pSettings.this.getActivity(), R.string.wifi_p2p_failed_connect_message, 0).show();
                    }
                });
            }
        } else if (preference instanceof WifiP2pPersistentGroup) {
            this.mSelectedGroup = (WifiP2pPersistentGroup) preference;
            showDialog(4);
        }
        return super.onPreferenceTreeClick(screen, preference);
    }

    public Dialog onCreateDialog(int id) {
        String deviceName;
        if (id == 1) {
            String msg;
            deviceName = TextUtils.isEmpty(this.mSelectedWifiPeer.device.deviceName) ? this.mSelectedWifiPeer.device.deviceAddress : this.mSelectedWifiPeer.device.deviceName;
            if (this.mConnectedDevices > 1) {
                msg = getActivity().getString(R.string.wifi_p2p_disconnect_multiple_message, new Object[]{deviceName, Integer.valueOf(this.mConnectedDevices - 1)});
            } else {
                msg = getActivity().getString(R.string.wifi_p2p_disconnect_message, new Object[]{deviceName});
            }
            return new Builder(getActivity()).setTitle(R.string.wifi_p2p_disconnect_title).setMessage(msg).setPositiveButton(getActivity().getString(R.string.dlg_ok), this.mDisconnectListener).setNegativeButton(getActivity().getString(R.string.dlg_cancel), null).create();
        } else if (id == 2) {
            if (TextUtils.isEmpty(this.mSelectedWifiPeer.device.deviceName)) {
                deviceName = this.mSelectedWifiPeer.device.deviceAddress;
            } else {
                deviceName = this.mSelectedWifiPeer.device.deviceName;
            }
            return new Builder(getActivity()).setTitle(R.string.wifi_p2p_cancel_connect_title).setMessage(getActivity().getString(R.string.wifi_p2p_cancel_connect_message, new Object[]{deviceName})).setPositiveButton(getActivity().getString(R.string.dlg_ok), this.mCancelConnectListener).setNegativeButton(getActivity().getString(R.string.dlg_cancel), null).create();
        } else if (id == 3) {
            this.mDeviceNameText = new EditText(getActivity());
            this.mDeviceNameText.setFilters(new InputFilter[]{new LengthFilter(30)});
            if (this.mSavedDeviceName != null) {
                this.mDeviceNameText.setText(this.mSavedDeviceName);
                this.mDeviceNameText.setSelection(this.mSavedDeviceName.length());
            } else if (!(this.mThisDevice == null || TextUtils.isEmpty(this.mThisDevice.deviceName))) {
                this.mDeviceNameText.setText(this.mThisDevice.deviceName);
                this.mDeviceNameText.setSelection(0, this.mThisDevice.deviceName.length());
            }
            this.mSavedDeviceName = null;
            return new Builder(getActivity()).setTitle(R.string.wifi_p2p_menu_rename).setView(this.mDeviceNameText).setPositiveButton(getActivity().getString(R.string.dlg_ok), this.mRenameListener).setNegativeButton(getActivity().getString(R.string.dlg_cancel), null).create();
        } else if (id == 4) {
            return new Builder(getActivity()).setMessage(getActivity().getString(R.string.wifi_p2p_delete_group_message)).setPositiveButton(getActivity().getString(R.string.dlg_ok), this.mDeleteGroupListener).setNegativeButton(getActivity().getString(R.string.dlg_cancel), this.mDeleteGroupListener).create();
        } else {
            return null;
        }
    }

    public void onSaveInstanceState(Bundle outState) {
        if (this.mSelectedWifiPeer != null) {
            outState.putParcelable("PEER_STATE", this.mSelectedWifiPeer.device);
        }
        if (this.mDeviceNameText != null) {
            outState.putString("DEV_NAME", this.mDeviceNameText.getText().toString());
        }
        if (this.mSelectedGroup != null) {
            outState.putString("GROUP_NAME", this.mSelectedGroup.getGroupName());
        }
    }

    private void handlePeersChanged() {
        this.mPeersGroup.removeAll();
        this.mConnectedDevices = 0;
        for (WifiP2pDevice peer : this.mPeers.getDeviceList()) {
            this.mPeersGroup.addPreference(new WifiP2pPeer(getActivity(), peer));
            if (peer.status == 0) {
                this.mConnectedDevices++;
            }
        }
    }

    public void onPersistentGroupInfoAvailable(WifiP2pGroupList groups) {
        this.mPersistentGroup.removeAll();
        for (WifiP2pGroup group : groups.getGroupList()) {
            WifiP2pPersistentGroup wppg = new WifiP2pPersistentGroup(getActivity(), group);
            this.mPersistentGroup.addPreference(wppg);
            if (wppg.getGroupName().equals(this.mSelectedGroupName)) {
                this.mSelectedGroup = wppg;
                this.mSelectedGroupName = null;
            }
        }
        if (this.mSelectedGroupName != null) {
            Log.w("WifiP2pSettings", " Selected group " + this.mSelectedGroupName + " disappered on next query ");
        }
    }

    public void onPeersAvailable(WifiP2pDeviceList peers) {
        this.mPeers = peers;
        handlePeersChanged();
    }

    private void handleP2pStateChanged() {
        updateSearchMenu(false);
        this.mThisDevicePref.setEnabled(this.mWifiP2pEnabled);
        this.mPeersGroup.setEnabled(this.mWifiP2pEnabled);
        this.mPersistentGroup.setEnabled(this.mWifiP2pEnabled);
    }

    private void updateSearchMenu(boolean searching) {
        this.mWifiP2pSearching = searching;
        Activity activity = getActivity();
        if (activity != null) {
            activity.invalidateOptionsMenu();
        }
    }

    private void startSearch() {
        if (this.mWifiP2pManager != null && !this.mWifiP2pSearching) {
            this.mWifiP2pManager.discoverPeers(this.mChannel, new ActionListener() {
                public void onSuccess() {
                }

                public void onFailure(int reason) {
                }
            });
        }
    }

    private void updateDevicePref() {
        if (this.mThisDevice == null) {
            return;
        }
        if (TextUtils.isEmpty(this.mThisDevice.deviceName)) {
            this.mThisDevicePref.setTitle(this.mThisDevice.deviceAddress);
        } else {
            this.mThisDevicePref.setTitle(this.mThisDevice.deviceName);
        }
    }
}
