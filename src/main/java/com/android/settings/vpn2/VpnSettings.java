package com.android.settings.vpn2;

import android.app.AlertDialog.Builder;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.DialogInterface.OnDismissListener;
import android.content.res.Resources;
import android.net.ConnectivityManager;
import android.net.IConnectivityManager;
import android.net.IConnectivityManager.Stub;
import android.os.Bundle;
import android.os.Handler;
import android.os.Handler.Callback;
import android.os.Message;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.os.UserManager;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceGroup;
import android.preference.PreferenceScreen;
import android.security.Credentials;
import android.security.KeyStore;
import android.text.TextUtils;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import com.android.internal.net.LegacyVpnInfo;
import com.android.internal.net.VpnProfile;
import com.android.internal.util.ArrayUtils;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.google.android.collect.Lists;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class VpnSettings extends SettingsPreferenceFragment implements OnClickListener, OnDismissListener, Callback, OnPreferenceClickListener {
    private VpnDialog mDialog;
    private LegacyVpnInfo mInfo;
    private final KeyStore mKeyStore = KeyStore.getInstance();
    private HashMap<String, VpnPreference> mPreferences = new HashMap();
    private String mSelectedKey;
    private final IConnectivityManager mService = Stub.asInterface(ServiceManager.getService("connectivity"));
    private UserManager mUm;
    private boolean mUnavailable;
    private boolean mUnlocking = false;
    private Handler mUpdater;

    public static class LockdownConfigFragment extends DialogFragment {
        private int mCurrentIndex;
        private List<VpnProfile> mProfiles;
        private List<CharSequence> mTitles;

        private static class TitleAdapter extends ArrayAdapter<CharSequence> {
            public TitleAdapter(Context context, List<CharSequence> objects) {
                super(context, 17367241, 16908308, objects);
            }
        }

        public static void show(VpnSettings parent) {
            if (parent.isAdded()) {
                new LockdownConfigFragment().show(parent.getFragmentManager(), "lockdown");
            }
        }

        private static String getStringOrNull(KeyStore keyStore, String key) {
            byte[] value = keyStore.get("LOCKDOWN_VPN");
            return value == null ? null : new String(value);
        }

        private void initProfiles(KeyStore keyStore, Resources res) {
            String lockdownKey = getStringOrNull(keyStore, "LOCKDOWN_VPN");
            this.mProfiles = VpnSettings.loadVpnProfiles(keyStore, 0);
            this.mTitles = Lists.newArrayList();
            this.mTitles.add(res.getText(R.string.vpn_lockdown_none));
            this.mCurrentIndex = 0;
            for (VpnProfile profile : this.mProfiles) {
                if (TextUtils.equals(profile.key, lockdownKey)) {
                    this.mCurrentIndex = this.mTitles.size();
                }
                this.mTitles.add(profile.name);
            }
        }

        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final Context context = getActivity();
            final KeyStore keyStore = KeyStore.getInstance();
            initProfiles(keyStore, context.getResources());
            Builder builder = new Builder(context);
            LayoutInflater dialogInflater = LayoutInflater.from(builder.getContext());
            builder.setTitle(R.string.vpn_menu_lockdown);
            View view = dialogInflater.inflate(R.layout.vpn_lockdown_editor, null, false);
            final ListView listView = (ListView) view.findViewById(16908298);
            listView.setChoiceMode(1);
            listView.setAdapter(new TitleAdapter(context, this.mTitles));
            listView.setItemChecked(this.mCurrentIndex, true);
            builder.setView(view);
            builder.setPositiveButton(17039370, new OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    int newIndex = listView.getCheckedItemPosition();
                    if (LockdownConfigFragment.this.mCurrentIndex != newIndex) {
                        if (newIndex == 0) {
                            keyStore.delete("LOCKDOWN_VPN");
                        } else {
                            VpnProfile profile = (VpnProfile) LockdownConfigFragment.this.mProfiles.get(newIndex - 1);
                            if (profile.isValidLockdownProfile()) {
                                keyStore.put("LOCKDOWN_VPN", profile.key.getBytes(), -1, 1);
                            } else {
                                Toast.makeText(context, R.string.vpn_lockdown_config_error, 1).show();
                                return;
                            }
                        }
                        ConnectivityManager.from(LockdownConfigFragment.this.getActivity()).updateLockdownVpn();
                    }
                }
            });
            return builder.create();
        }
    }

    private static class VpnPreference extends Preference {
        private VpnProfile mProfile;
        private int mState = -1;

        VpnPreference(Context context, VpnProfile profile) {
            super(context);
            setPersistent(false);
            setOrder(0);
            this.mProfile = profile;
            update();
        }

        VpnProfile getProfile() {
            return this.mProfile;
        }

        void update(VpnProfile profile) {
            this.mProfile = profile;
            update();
        }

        void update(int state) {
            this.mState = state;
            update();
        }

        void update() {
            if (this.mState < 0) {
                setSummary(getContext().getResources().getStringArray(R.array.vpn_types_long)[this.mProfile.type]);
            } else {
                setSummary(getContext().getResources().getStringArray(R.array.vpn_states)[this.mState]);
            }
            setTitle(this.mProfile.name);
            notifyHierarchyChanged();
        }

        public int compareTo(Preference preference) {
            if (!(preference instanceof VpnPreference)) {
                return -1;
            }
            VpnPreference another = (VpnPreference) preference;
            int result = another.mState - this.mState;
            if (result != 0) {
                return result;
            }
            result = this.mProfile.name.compareTo(another.mProfile.name);
            if (result != 0) {
                return result;
            }
            result = this.mProfile.type - another.mProfile.type;
            if (result == 0) {
                return this.mProfile.key.compareTo(another.mProfile.key);
            }
            return result;
        }
    }

    public void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        this.mUm = (UserManager) getSystemService("user");
        if (this.mUm.hasUserRestriction("no_config_vpn")) {
            this.mUnavailable = true;
            setPreferenceScreen(new PreferenceScreen(getActivity(), null));
            return;
        }
        setHasOptionsMenu(true);
        addPreferencesFromResource(R.xml.vpn_settings2);
        if (savedState != null) {
            VpnProfile profile = VpnProfile.decode(savedState.getString("VpnKey"), savedState.getByteArray("VpnProfile"));
            if (profile != null) {
                this.mDialog = new VpnDialog(getActivity(), this, profile, savedState.getBoolean("VpnEditing"));
            }
        }
    }

    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.vpn, menu);
    }

    public void onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        if (SystemProperties.getBoolean("persist.radio.imsregrequired", false)) {
            menu.findItem(R.id.vpn_lockdown).setVisible(false);
        }
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.vpn_create:
                long millis = System.currentTimeMillis();
                while (this.mPreferences.containsKey(Long.toHexString(millis))) {
                    millis++;
                }
                this.mDialog = new VpnDialog(getActivity(), this, new VpnProfile(Long.toHexString(millis)), true);
                this.mDialog.setOnDismissListener(this);
                this.mDialog.show();
                return true;
            case R.id.vpn_lockdown:
                LockdownConfigFragment.show(this);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    public void onSaveInstanceState(Bundle savedState) {
        if (this.mDialog != null) {
            VpnProfile profile = this.mDialog.getProfile();
            savedState.putString("VpnKey", profile.key);
            savedState.putByteArray("VpnProfile", profile.encode());
            savedState.putBoolean("VpnEditing", this.mDialog.isEditing());
        }
    }

    public void onResume() {
        boolean z = false;
        super.onResume();
        if (this.mUnavailable) {
            TextView emptyView = (TextView) getView().findViewById(16908292);
            getListView().setEmptyView(emptyView);
            if (emptyView != null) {
                emptyView.setText(R.string.vpn_settings_not_available);
                return;
            }
            return;
        }
        if (getActivity().getIntent().getBooleanExtra("android.net.vpn.PICK_LOCKDOWN", false)) {
            LockdownConfigFragment.show(this);
        }
        if (this.mKeyStore.isUnlocked()) {
            this.mUnlocking = false;
            if (this.mPreferences.size() == 0) {
                PreferenceGroup group = getPreferenceScreen();
                Context context = getActivity();
                for (VpnProfile profile : loadVpnProfiles(this.mKeyStore, new int[0])) {
                    VpnPreference pref = new VpnPreference(context, profile);
                    pref.setOnPreferenceClickListener(this);
                    this.mPreferences.put(profile.key, pref);
                    group.addPreference(pref);
                }
            }
            if (this.mDialog != null) {
                this.mDialog.setOnDismissListener(this);
                this.mDialog.show();
            }
            if (this.mUpdater == null) {
                this.mUpdater = new Handler(this);
            }
            this.mUpdater.sendEmptyMessage(0);
            registerForContextMenu(getListView());
            return;
        }
        if (this.mUnlocking) {
            finishFragment();
        } else {
            Credentials.getInstance().unlock(getActivity());
        }
        if (!this.mUnlocking) {
            z = true;
        }
        this.mUnlocking = z;
    }

    public void onPause() {
        super.onPause();
        if (!this.mUnavailable) {
            if (this.mDialog != null) {
                this.mDialog.setOnDismissListener(null);
                this.mDialog.dismiss();
            }
            if (getView() != null) {
                unregisterForContextMenu(getListView());
            }
        }
    }

    public void onDismiss(DialogInterface dialog) {
        this.mDialog = null;
    }

    public void onClick(DialogInterface dialog, int button) {
        if (button == -1) {
            VpnProfile profile = this.mDialog.getProfile();
            this.mKeyStore.put("VPN_" + profile.key, profile.encode(), -1, 1);
            VpnPreference preference = (VpnPreference) this.mPreferences.get(profile.key);
            if (preference != null) {
                disconnect(profile.key);
                preference.update(profile);
            } else {
                preference = new VpnPreference(getActivity(), profile);
                preference.setOnPreferenceClickListener(this);
                this.mPreferences.put(profile.key, preference);
                getPreferenceScreen().addPreference(preference);
            }
            if (!this.mDialog.isEditing()) {
                try {
                    connect(profile);
                } catch (Exception e) {
                    Log.e("VpnSettings", "connect", e);
                }
            }
        }
    }

    public void onCreateContextMenu(ContextMenu menu, View view, ContextMenuInfo info) {
        if (this.mDialog != null) {
            Log.v("VpnSettings", "onCreateContextMenu() is called when mDialog != null");
        } else if (info instanceof AdapterContextMenuInfo) {
            Preference preference = (Preference) getListView().getItemAtPosition(((AdapterContextMenuInfo) info).position);
            if (preference instanceof VpnPreference) {
                VpnProfile profile = ((VpnPreference) preference).getProfile();
                this.mSelectedKey = profile.key;
                menu.setHeaderTitle(profile.name);
                menu.add(0, R.string.vpn_menu_edit, 0, R.string.vpn_menu_edit);
                menu.add(0, R.string.vpn_menu_delete, 0, R.string.vpn_menu_delete);
            }
        }
    }

    public boolean onContextItemSelected(MenuItem item) {
        if (this.mDialog != null) {
            Log.v("VpnSettings", "onContextItemSelected() is called when mDialog != null");
            return false;
        }
        VpnPreference preference = (VpnPreference) this.mPreferences.get(this.mSelectedKey);
        if (preference == null) {
            Log.v("VpnSettings", "onContextItemSelected() is called but no preference is found");
            return false;
        }
        switch (item.getItemId()) {
            case R.string.vpn_menu_edit:
                this.mDialog = new VpnDialog(getActivity(), this, preference.getProfile(), true);
                this.mDialog.setOnDismissListener(this);
                this.mDialog.show();
                return true;
            case R.string.vpn_menu_delete:
                disconnect(this.mSelectedKey);
                getPreferenceScreen().removePreference(preference);
                this.mPreferences.remove(this.mSelectedKey);
                this.mKeyStore.delete("VPN_" + this.mSelectedKey);
                return true;
            default:
                return false;
        }
    }

    public boolean onPreferenceClick(Preference preference) {
        if (this.mDialog != null) {
            Log.v("VpnSettings", "onPreferenceClick() is called when mDialog != null");
        } else {
            if (preference instanceof VpnPreference) {
                VpnProfile profile = ((VpnPreference) preference).getProfile();
                if (this.mInfo != null && profile.key.equals(this.mInfo.key) && this.mInfo.state == 3) {
                    try {
                        this.mInfo.intent.send();
                    } catch (Exception e) {
                    }
                }
                this.mDialog = new VpnDialog(getActivity(), this, profile, false);
            } else {
                long millis = System.currentTimeMillis();
                while (this.mPreferences.containsKey(Long.toHexString(millis))) {
                    millis++;
                }
                this.mDialog = new VpnDialog(getActivity(), this, new VpnProfile(Long.toHexString(millis)), true);
            }
            this.mDialog.setOnDismissListener(this);
            this.mDialog.show();
        }
        return true;
    }

    public boolean handleMessage(Message message) {
        this.mUpdater.removeMessages(0);
        if (isResumed()) {
            try {
                VpnPreference preference;
                LegacyVpnInfo info = this.mService.getLegacyVpnInfo();
                if (this.mInfo != null) {
                    preference = (VpnPreference) this.mPreferences.get(this.mInfo.key);
                    if (preference != null) {
                        preference.update(-1);
                    }
                    this.mInfo = null;
                }
                if (info != null) {
                    preference = (VpnPreference) this.mPreferences.get(info.key);
                    if (preference != null) {
                        preference.update(info.state);
                        this.mInfo = info;
                    }
                }
            } catch (Exception e) {
            }
            this.mUpdater.sendEmptyMessageDelayed(0, 1000);
        }
        return true;
    }

    private void connect(VpnProfile profile) throws Exception {
        try {
            this.mService.startLegacyVpn(profile);
        } catch (IllegalStateException e) {
            Toast.makeText(getActivity(), R.string.vpn_no_network, 1).show();
        }
    }

    private void disconnect(String key) {
        if (this.mInfo != null && key.equals(this.mInfo.key)) {
            try {
                this.mService.prepareVpn("[Legacy VPN]", "[Legacy VPN]");
            } catch (Exception e) {
            }
        }
    }

    protected int getHelpResource() {
        return R.string.help_url_vpn;
    }

    private static List<VpnProfile> loadVpnProfiles(KeyStore keyStore, int... excludeTypes) {
        ArrayList<VpnProfile> result = Lists.newArrayList();
        String[] keys = keyStore.saw("VPN_");
        if (keys != null) {
            for (String key : keys) {
                VpnProfile profile = VpnProfile.decode(key, keyStore.get("VPN_" + key));
                if (!(profile == null || ArrayUtils.contains(excludeTypes, profile.type))) {
                    result.add(profile);
                }
            }
        }
        return result;
    }
}
