package com.android.settings;

import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.SystemProperties;
import android.os.UserManager;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceGroup;
import android.preference.PreferenceScreen;
import android.provider.Telephony.Carriers;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;
import com.android.internal.telephony.PhoneConstants.DataState;
import java.util.ArrayList;
import java.util.Iterator;

public class ApnSettings extends SettingsPreferenceFragment implements OnPreferenceChangeListener {
    private static final Uri DEFAULTAPN_URI = Uri.parse("content://telephony/carriers/restore");
    private static final Uri PREFERAPN_URI = Uri.parse("content://telephony/carriers/preferapn");
    private static boolean mRestoreDefaultApnMode;
    private IntentFilter mMobileStateFilter;
    private final BroadcastReceiver mMobileStateReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals("android.intent.action.ANY_DATA_STATE")) {
                switch (AnonymousClass2.$SwitchMap$com$android$internal$telephony$PhoneConstants$DataState[ApnSettings.getMobileDataState(intent).ordinal()]) {
                    case 1:
                        if (ApnSettings.mRestoreDefaultApnMode) {
                            ApnSettings.this.showDialog(1001);
                            return;
                        } else {
                            ApnSettings.this.fillList();
                            return;
                        }
                    default:
                        return;
                }
            }
        }
    };
    private RestoreApnProcessHandler mRestoreApnProcessHandler;
    private RestoreApnUiHandler mRestoreApnUiHandler;
    private HandlerThread mRestoreDefaultApnThread;
    private String mSelectedKey;
    private UserManager mUm;
    private boolean mUnavailable;

    static /* synthetic */ class AnonymousClass2 {
        static final /* synthetic */ int[] $SwitchMap$com$android$internal$telephony$PhoneConstants$DataState = new int[DataState.values().length];

        static {
            try {
                $SwitchMap$com$android$internal$telephony$PhoneConstants$DataState[DataState.CONNECTED.ordinal()] = 1;
            } catch (NoSuchFieldError e) {
            }
        }
    }

    private class RestoreApnProcessHandler extends Handler {
        private Handler mRestoreApnUiHandler;

        public RestoreApnProcessHandler(Looper looper, Handler restoreApnUiHandler) {
            super(looper);
            this.mRestoreApnUiHandler = restoreApnUiHandler;
        }

        public void handleMessage(Message msg) {
            switch (msg.what) {
                case 1:
                    ApnSettings.this.getContentResolver().delete(ApnSettings.DEFAULTAPN_URI, null, null);
                    this.mRestoreApnUiHandler.sendEmptyMessage(2);
                    return;
                default:
                    return;
            }
        }
    }

    private class RestoreApnUiHandler extends Handler {
        private RestoreApnUiHandler() {
        }

        public void handleMessage(Message msg) {
            switch (msg.what) {
                case 2:
                    Activity activity = ApnSettings.this.getActivity();
                    if (activity == null) {
                        ApnSettings.mRestoreDefaultApnMode = false;
                        return;
                    }
                    ApnSettings.this.fillList();
                    ApnSettings.this.getPreferenceScreen().setEnabled(true);
                    ApnSettings.mRestoreDefaultApnMode = false;
                    ApnSettings.this.removeDialog(1001);
                    Toast.makeText(activity, ApnSettings.this.getResources().getString(R.string.restore_default_apn_completed), 1).show();
                    return;
                default:
                    return;
            }
        }
    }

    private static DataState getMobileDataState(Intent intent) {
        String str = intent.getStringExtra("state");
        if (str != null) {
            return (DataState) Enum.valueOf(DataState.class, str);
        }
        return DataState.DISCONNECTED;
    }

    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        this.mUm = (UserManager) getSystemService("user");
        this.mMobileStateFilter = new IntentFilter("android.intent.action.ANY_DATA_STATE");
        if (!this.mUm.hasUserRestriction("no_config_mobile_networks")) {
            setHasOptionsMenu(true);
        }
    }

    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        TextView empty = (TextView) getView().findViewById(16908292);
        if (empty != null) {
            empty.setText(R.string.apn_settings_not_available);
            getListView().setEmptyView(empty);
        }
        if (this.mUm.hasUserRestriction("no_config_mobile_networks")) {
            this.mUnavailable = true;
            setPreferenceScreen(new PreferenceScreen(getActivity(), null));
            return;
        }
        addPreferencesFromResource(R.xml.apn_settings);
        getListView().setItemsCanFocus(true);
    }

    public void onResume() {
        super.onResume();
        if (!this.mUnavailable) {
            getActivity().registerReceiver(this.mMobileStateReceiver, this.mMobileStateFilter);
            if (!mRestoreDefaultApnMode) {
                fillList();
            }
        }
    }

    public void onPause() {
        super.onPause();
        if (!this.mUnavailable) {
            getActivity().unregisterReceiver(this.mMobileStateReceiver);
        }
    }

    public void onDestroy() {
        super.onDestroy();
        if (this.mRestoreDefaultApnThread != null) {
            this.mRestoreDefaultApnThread.quit();
        }
    }

    private void fillList() {
        String where = "numeric=\"" + SystemProperties.get("gsm.sim.operator.numeric", "") + "\"";
        Cursor cursor = getContentResolver().query(Carriers.CONTENT_URI, new String[]{"_id", "name", "apn", "type"}, where, null, "name ASC");
        if (cursor != null) {
            PreferenceGroup apnList = (PreferenceGroup) findPreference("apn_list");
            apnList.removeAll();
            ArrayList<Preference> mmsApnList = new ArrayList();
            this.mSelectedKey = getSelectedApnKey();
            cursor.moveToFirst();
            while (!cursor.isAfterLast()) {
                boolean selectable;
                String name = cursor.getString(1);
                String apn = cursor.getString(2);
                String key = cursor.getString(0);
                String type = cursor.getString(3);
                ApnPreference pref = new ApnPreference(getActivity());
                pref.setKey(key);
                pref.setTitle(name);
                pref.setSummary(apn);
                pref.setPersistent(false);
                pref.setOnPreferenceChangeListener(this);
                if (type != null) {
                    if (type.equals("mms")) {
                        selectable = false;
                        pref.setSelectable(selectable);
                        if (selectable) {
                            mmsApnList.add(pref);
                        } else {
                            if (this.mSelectedKey != null && this.mSelectedKey.equals(key)) {
                                pref.setChecked();
                            }
                            apnList.addPreference(pref);
                        }
                        cursor.moveToNext();
                    }
                }
                selectable = true;
                pref.setSelectable(selectable);
                if (selectable) {
                    mmsApnList.add(pref);
                } else {
                    pref.setChecked();
                    apnList.addPreference(pref);
                }
                cursor.moveToNext();
            }
            cursor.close();
            Iterator i$ = mmsApnList.iterator();
            while (i$.hasNext()) {
                apnList.addPreference((Preference) i$.next());
            }
        }
    }

    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        if (!this.mUnavailable) {
            menu.add(0, 1, 0, getResources().getString(R.string.menu_new)).setIcon(17301555).setShowAsAction(1);
            menu.add(0, 2, 0, getResources().getString(R.string.menu_restore)).setIcon(17301589);
        }
        super.onCreateOptionsMenu(menu, inflater);
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case 1:
                addNewApn();
                return true;
            case 2:
                restoreDefaultApn();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void addNewApn() {
        startActivity(new Intent("android.intent.action.INSERT", Carriers.CONTENT_URI));
    }

    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        String str = "android.intent.action.EDIT";
        startActivity(new Intent(str, ContentUris.withAppendedId(Carriers.CONTENT_URI, (long) Integer.parseInt(preference.getKey()))));
        return true;
    }

    public boolean onPreferenceChange(Preference preference, Object newValue) {
        Log.d("ApnSettings", "onPreferenceChange(): Preference - " + preference + ", newValue - " + newValue + ", newValue type - " + newValue.getClass());
        if (newValue instanceof String) {
            setSelectedApnKey((String) newValue);
        }
        return true;
    }

    private void setSelectedApnKey(String key) {
        this.mSelectedKey = key;
        ContentResolver resolver = getContentResolver();
        ContentValues values = new ContentValues();
        values.put("apn_id", this.mSelectedKey);
        resolver.update(PREFERAPN_URI, values, null, null);
    }

    private String getSelectedApnKey() {
        String key = null;
        Cursor cursor = getContentResolver().query(PREFERAPN_URI, new String[]{"_id"}, null, null, "name ASC");
        if (cursor.getCount() > 0) {
            cursor.moveToFirst();
            key = cursor.getString(0);
        }
        cursor.close();
        return key;
    }

    private boolean restoreDefaultApn() {
        showDialog(1001);
        mRestoreDefaultApnMode = true;
        if (this.mRestoreApnUiHandler == null) {
            this.mRestoreApnUiHandler = new RestoreApnUiHandler();
        }
        if (this.mRestoreApnProcessHandler == null || this.mRestoreDefaultApnThread == null) {
            this.mRestoreDefaultApnThread = new HandlerThread("Restore default APN Handler: Process Thread");
            this.mRestoreDefaultApnThread.start();
            this.mRestoreApnProcessHandler = new RestoreApnProcessHandler(this.mRestoreDefaultApnThread.getLooper(), this.mRestoreApnUiHandler);
        }
        this.mRestoreApnProcessHandler.sendEmptyMessage(1);
        return true;
    }

    public Dialog onCreateDialog(int id) {
        if (id != 1001) {
            return null;
        }
        ProgressDialog dialog = new ProgressDialog(getActivity());
        dialog.setMessage(getResources().getString(R.string.restore_default_apn));
        dialog.setCancelable(false);
        return dialog;
    }
}
