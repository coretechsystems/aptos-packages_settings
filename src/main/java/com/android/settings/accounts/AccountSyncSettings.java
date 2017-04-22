package com.android.settings.accounts;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerCallback;
import android.accounts.AccountManagerFuture;
import android.accounts.AuthenticatorException;
import android.accounts.OperationCanceledException;
import android.app.AlertDialog.Builder;
import android.app.Dialog;
import android.content.ContentResolver;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.SyncAdapterType;
import android.content.SyncInfo;
import android.content.SyncStatusInfo;
import android.content.pm.ProviderInfo;
import android.net.ConnectivityManager;
import android.os.Bundle;
import android.os.UserHandle;
import android.os.UserManager;
import android.preference.Preference;
import android.preference.PreferenceScreen;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import com.android.settings.R;
import com.android.settings.Utils;
import com.google.android.collect.Lists;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

public class AccountSyncSettings extends AccountPreferenceBase {
    private Account mAccount;
    private ArrayList<SyncStateCheckBoxPreference> mCheckBoxes = new ArrayList();
    private TextView mErrorInfoView;
    private ArrayList<SyncAdapterType> mInvisibleAdapters = Lists.newArrayList();
    private ImageView mProviderIcon;
    private TextView mProviderId;
    private TextView mUserId;

    public /* bridge */ /* synthetic */ PreferenceScreen addPreferencesForType(String x0, PreferenceScreen x1) {
        return super.addPreferencesForType(x0, x1);
    }

    public /* bridge */ /* synthetic */ ArrayList getAuthoritiesForAccountType(String x0) {
        return super.getAuthoritiesForAccountType(x0);
    }

    public /* bridge */ /* synthetic */ void updateAuthDescriptions() {
        super.updateAuthDescriptions();
    }

    public Dialog onCreateDialog(int id) {
        if (id == 100) {
            return new Builder(getActivity()).setTitle(R.string.really_remove_account_title).setMessage(R.string.really_remove_account_message).setNegativeButton(17039360, null).setPositiveButton(R.string.remove_account_label, new OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    AccountManager.get(AccountSyncSettings.this.getActivity()).removeAccountAsUser(AccountSyncSettings.this.mAccount, new AccountManagerCallback<Boolean>() {
                        public void run(AccountManagerFuture<Boolean> future) {
                            if (AccountSyncSettings.this.isResumed()) {
                                boolean failed = true;
                                try {
                                    if (((Boolean) future.getResult()).booleanValue()) {
                                        failed = false;
                                    }
                                } catch (OperationCanceledException e) {
                                } catch (IOException e2) {
                                } catch (AuthenticatorException e3) {
                                }
                                if (!failed || AccountSyncSettings.this.getActivity() == null || AccountSyncSettings.this.getActivity().isFinishing()) {
                                    AccountSyncSettings.this.finish();
                                } else {
                                    AccountSyncSettings.this.showDialog(101);
                                }
                            }
                        }
                    }, null, AccountSyncSettings.this.mUserHandle);
                }
            }).create();
        }
        if (id == 101) {
            return new Builder(getActivity()).setTitle(R.string.really_remove_account_title).setPositiveButton(17039370, null).setMessage(R.string.remove_account_failed).create();
        }
        if (id == 102) {
            return new Builder(getActivity()).setTitle(R.string.cant_sync_dialog_title).setMessage(R.string.cant_sync_dialog_message).setPositiveButton(17039370, null).create();
        }
        return null;
    }

    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        setHasOptionsMenu(true);
    }

    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.account_sync_screen, container, false);
        Utils.prepareCustomPreferencesList(container, view, (ListView) view.findViewById(16908298), false);
        initializeUi(view);
        return view;
    }

    protected void initializeUi(View rootView) {
        addPreferencesFromResource(R.xml.account_sync_settings);
        this.mErrorInfoView = (TextView) rootView.findViewById(R.id.sync_settings_error_info);
        this.mErrorInfoView.setVisibility(8);
        this.mUserId = (TextView) rootView.findViewById(R.id.user_id);
        this.mProviderId = (TextView) rootView.findViewById(R.id.provider_id);
        this.mProviderIcon = (ImageView) rootView.findViewById(R.id.provider_icon);
    }

    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        Bundle arguments = getArguments();
        if (arguments == null) {
            Log.e("AccountSettings", "No arguments provided when starting intent. ACCOUNT_KEY needed.");
            finish();
            return;
        }
        this.mAccount = (Account) arguments.getParcelable("account");
        if (accountExists(this.mAccount)) {
            if (Log.isLoggable("AccountSettings", 2)) {
                Log.v("AccountSettings", "Got account: " + this.mAccount);
            }
            this.mUserId.setText(this.mAccount.name);
            this.mProviderId.setText(this.mAccount.type);
            return;
        }
        Log.e("AccountSettings", "Account provided does not exist: " + this.mAccount);
        finish();
    }

    public void onResume() {
        this.mAuthenticatorHelper.listenToAccountUpdates();
        updateAuthDescriptions();
        onAccountsUpdate(UserHandle.getCallingUserHandle());
        super.onResume();
    }

    public void onPause() {
        super.onPause();
        this.mAuthenticatorHelper.stopListeningToAccountUpdates();
    }

    private void addSyncStateCheckBox(Account account, String authority) {
        SyncStateCheckBoxPreference item = new SyncStateCheckBoxPreference(getActivity(), account, authority);
        item.setPersistent(false);
        ProviderInfo providerInfo = getPackageManager().resolveContentProviderAsUser(authority, 0, this.mUserHandle.getIdentifier());
        if (providerInfo != null) {
            if (TextUtils.isEmpty(providerInfo.loadLabel(getPackageManager()))) {
                Log.e("AccountSettings", "Provider needs a label for authority '" + authority + "'");
                return;
            }
            item.setTitle(getString(R.string.sync_item_title, new Object[]{providerInfo.loadLabel(getPackageManager())}));
            item.setKey(authority);
            this.mCheckBoxes.add(item);
        }
    }

    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        MenuItem syncNow = menu.add(0, 1, 0, getString(R.string.sync_menu_sync_now)).setIcon(R.drawable.ic_menu_refresh_holo_dark);
        MenuItem syncCancel = menu.add(0, 2, 0, getString(R.string.sync_menu_sync_cancel)).setIcon(17301560);
        if (!((UserManager) getSystemService("user")).hasUserRestriction("no_modify_accounts", this.mUserHandle)) {
            menu.add(0, 3, 0, getString(R.string.remove_account_label)).setIcon(R.drawable.ic_menu_delete).setShowAsAction(4);
        }
        syncNow.setShowAsAction(4);
        syncCancel.setShowAsAction(4);
        super.onCreateOptionsMenu(menu, inflater);
    }

    public void onPrepareOptionsMenu(Menu menu) {
        boolean z = true;
        super.onPrepareOptionsMenu(menu);
        boolean syncActive = ContentResolver.getCurrentSyncsAsUser(this.mUserHandle.getIdentifier()).isEmpty();
        MenuItem findItem = menu.findItem(1);
        if (syncActive) {
            z = false;
        }
        findItem.setVisible(z);
        menu.findItem(2).setVisible(syncActive);
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case 1:
                startSyncForEnabledProviders();
                return true;
            case 2:
                cancelSyncForEnabledProviders();
                return true;
            case 3:
                showDialog(100);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    public boolean onPreferenceTreeClick(PreferenceScreen preferences, Preference preference) {
        if (!(preference instanceof SyncStateCheckBoxPreference)) {
            return super.onPreferenceTreeClick(preferences, preference);
        }
        SyncStateCheckBoxPreference syncPref = (SyncStateCheckBoxPreference) preference;
        String authority = syncPref.getAuthority();
        Account account = syncPref.getAccount();
        int userId = this.mUserHandle.getIdentifier();
        boolean syncAutomatically = ContentResolver.getSyncAutomaticallyAsUser(account, authority, userId);
        if (syncPref.isOneTimeSyncMode()) {
            requestOrCancelSync(account, authority, true);
            return true;
        }
        boolean syncOn = syncPref.isChecked();
        if (syncOn == syncAutomatically) {
            return true;
        }
        ContentResolver.setSyncAutomaticallyAsUser(account, authority, syncOn, userId);
        if (ContentResolver.getMasterSyncAutomaticallyAsUser(userId) && syncOn) {
            return true;
        }
        requestOrCancelSync(account, authority, syncOn);
        return true;
    }

    private void startSyncForEnabledProviders() {
        requestOrCancelSyncForEnabledProviders(true);
        getActivity().invalidateOptionsMenu();
    }

    private void cancelSyncForEnabledProviders() {
        requestOrCancelSyncForEnabledProviders(false);
        getActivity().invalidateOptionsMenu();
    }

    private void requestOrCancelSyncForEnabledProviders(boolean startSync) {
        int count = getPreferenceScreen().getPreferenceCount();
        for (int i = 0; i < count; i++) {
            Preference pref = getPreferenceScreen().getPreference(i);
            if (pref instanceof SyncStateCheckBoxPreference) {
                SyncStateCheckBoxPreference syncPref = (SyncStateCheckBoxPreference) pref;
                if (syncPref.isChecked()) {
                    requestOrCancelSync(syncPref.getAccount(), syncPref.getAuthority(), startSync);
                }
            }
        }
        if (this.mAccount != null) {
            Iterator i$ = this.mInvisibleAdapters.iterator();
            while (i$.hasNext()) {
                requestOrCancelSync(this.mAccount, ((SyncAdapterType) i$.next()).authority, startSync);
            }
        }
    }

    private void requestOrCancelSync(Account account, String authority, boolean flag) {
        if (flag) {
            Bundle extras = new Bundle();
            extras.putBoolean("force", true);
            ContentResolver.requestSyncAsUser(account, authority, this.mUserHandle.getIdentifier(), extras);
            return;
        }
        ContentResolver.cancelSyncAsUser(account, authority, this.mUserHandle.getIdentifier());
    }

    private boolean isSyncing(List<SyncInfo> currentSyncs, Account account, String authority) {
        for (SyncInfo syncInfo : currentSyncs) {
            if (syncInfo.account.equals(account) && syncInfo.authority.equals(authority)) {
                return true;
            }
        }
        return false;
    }

    protected void onSyncStateUpdated() {
        if (isResumed()) {
            setFeedsState();
        }
    }

    private void setFeedsState() {
        Date date = new Date();
        int userId = this.mUserHandle.getIdentifier();
        List<SyncInfo> currentSyncs = ContentResolver.getCurrentSyncsAsUser(userId);
        boolean syncIsFailing = false;
        updateAccountCheckboxes();
        int count = getPreferenceScreen().getPreferenceCount();
        for (int i = 0; i < count; i++) {
            Preference pref = getPreferenceScreen().getPreference(i);
            if (pref instanceof SyncStateCheckBoxPreference) {
                SyncStateCheckBoxPreference syncPref = (SyncStateCheckBoxPreference) pref;
                String authority = syncPref.getAuthority();
                Account account = syncPref.getAccount();
                SyncStatusInfo status = ContentResolver.getSyncStatusAsUser(account, authority, userId);
                boolean syncEnabled = ContentResolver.getSyncAutomaticallyAsUser(account, authority, userId);
                boolean authorityIsPending = status == null ? false : status.pending;
                boolean initialSync = status == null ? false : status.initialize;
                boolean activelySyncing = isSyncing(currentSyncs, account, authority);
                boolean lastSyncFailed = (status == null || status.lastFailureTime == 0 || status.getLastFailureMesgAsInt(0) == 1) ? false : true;
                if (!syncEnabled) {
                    lastSyncFailed = false;
                }
                if (!(!lastSyncFailed || activelySyncing || authorityIsPending)) {
                    syncIsFailing = true;
                }
                if (Log.isLoggable("AccountSettings", 2)) {
                    Log.d("AccountSettings", "Update sync status: " + account + " " + authority + " active = " + activelySyncing + " pend =" + authorityIsPending);
                }
                long successEndTime = status == null ? 0 : status.lastSuccessTime;
                if (!syncEnabled) {
                    syncPref.setSummary(R.string.sync_disabled);
                } else if (activelySyncing) {
                    syncPref.setSummary(R.string.sync_in_progress);
                } else if (successEndTime != 0) {
                    date.setTime(successEndTime);
                    String timeString = formatSyncDate(date);
                    syncPref.setSummary(getResources().getString(R.string.last_synced, new Object[]{timeString}));
                } else {
                    syncPref.setSummary("");
                }
                int syncState = ContentResolver.getIsSyncableAsUser(account, authority, userId);
                boolean z = activelySyncing && syncState >= 0 && !initialSync;
                syncPref.setActive(z);
                z = authorityIsPending && syncState >= 0 && !initialSync;
                syncPref.setPending(z);
                syncPref.setFailed(lastSyncFailed);
                boolean oneTimeSyncMode = (ContentResolver.getMasterSyncAutomaticallyAsUser(userId) && ((ConnectivityManager) getSystemService("connectivity")).getBackgroundDataSetting()) ? false : true;
                syncPref.setOneTimeSyncMode(oneTimeSyncMode);
                if (oneTimeSyncMode || syncEnabled) {
                    z = true;
                } else {
                    z = false;
                }
                syncPref.setChecked(z);
            }
        }
        this.mErrorInfoView.setVisibility(syncIsFailing ? 0 : 8);
        getActivity().invalidateOptionsMenu();
    }

    public void onAccountsUpdate(UserHandle userHandle) {
        super.onAccountsUpdate(userHandle);
        if (accountExists(this.mAccount)) {
            updateAccountCheckboxes();
            onSyncStateUpdated();
            return;
        }
        finish();
    }

    private boolean accountExists(Account account) {
        if (account == null) {
            return false;
        }
        for (Account equals : AccountManager.get(getActivity()).getAccountsByTypeAsUser(account.type, this.mUserHandle)) {
            if (equals.equals(account)) {
                return true;
            }
        }
        return false;
    }

    private void updateAccountCheckboxes() {
        int i;
        this.mInvisibleAdapters.clear();
        SyncAdapterType[] syncAdapters = ContentResolver.getSyncAdapterTypesAsUser(this.mUserHandle.getIdentifier());
        ArrayList<String> authorities = new ArrayList();
        for (SyncAdapterType sa : syncAdapters) {
            if (sa.accountType.equals(this.mAccount.type)) {
                if (sa.isUserVisible()) {
                    if (Log.isLoggable("AccountSettings", 2)) {
                        Log.d("AccountSettings", "updateAccountCheckboxes: added authority " + sa.authority + " to accountType " + sa.accountType);
                    }
                    authorities.add(sa.authority);
                } else {
                    this.mInvisibleAdapters.add(sa);
                }
            }
        }
        int n = this.mCheckBoxes.size();
        for (i = 0; i < n; i++) {
            getPreferenceScreen().removePreference((Preference) this.mCheckBoxes.get(i));
        }
        this.mCheckBoxes.clear();
        if (Log.isLoggable("AccountSettings", 2)) {
            Log.d("AccountSettings", "looking for sync adapters that match account " + this.mAccount);
        }
        int m = authorities.size();
        for (int j = 0; j < m; j++) {
            String authority = (String) authorities.get(j);
            int syncState = ContentResolver.getIsSyncableAsUser(this.mAccount, authority, this.mUserHandle.getIdentifier());
            if (Log.isLoggable("AccountSettings", 2)) {
                Log.d("AccountSettings", "  found authority " + authority + " " + syncState);
            }
            if (syncState > 0) {
                addSyncStateCheckBox(this.mAccount, authority);
            }
        }
        Collections.sort(this.mCheckBoxes);
        n = this.mCheckBoxes.size();
        for (i = 0; i < n; i++) {
            getPreferenceScreen().addPreference((Preference) this.mCheckBoxes.get(i));
        }
    }

    protected void onAuthDescriptionsUpdated() {
        super.onAuthDescriptionsUpdated();
        getPreferenceScreen().removeAll();
        if (this.mAccount != null) {
            this.mProviderIcon.setImageDrawable(getDrawableForType(this.mAccount.type));
            this.mProviderId.setText(getLabelForType(this.mAccount.type));
        }
        addPreferencesFromResource(R.xml.account_sync_settings);
    }

    protected int getHelpResource() {
        return R.string.help_url_accounts;
    }
}
