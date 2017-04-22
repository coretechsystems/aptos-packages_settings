package com.android.settings.accounts;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AuthenticatorDescription;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SyncAdapterType;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources.NotFoundException;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.Log;
import com.google.android.collect.Maps;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public final class AuthenticatorHelper extends BroadcastReceiver {
    private Map<String, Drawable> mAccTypeIconCache = new HashMap();
    private HashMap<String, ArrayList<String>> mAccountTypeToAuthorities = Maps.newHashMap();
    private AuthenticatorDescription[] mAuthDescs;
    private final Context mContext;
    private ArrayList<String> mEnabledAccountTypes = new ArrayList();
    private final OnAccountsUpdateListener mListener;
    private boolean mListeningToAccountUpdates;
    private Map<String, AuthenticatorDescription> mTypeToAuthDescription = new HashMap();
    private final UserManager mUm;
    private final UserHandle mUserHandle;

    public interface OnAccountsUpdateListener {
        void onAccountsUpdate(UserHandle userHandle);
    }

    public AuthenticatorHelper(Context context, UserHandle userHandle, UserManager userManager, OnAccountsUpdateListener listener) {
        this.mContext = context;
        this.mUm = userManager;
        this.mUserHandle = userHandle;
        this.mListener = listener;
        onAccountsUpdated(null);
    }

    public String[] getEnabledAccountTypes() {
        return (String[]) this.mEnabledAccountTypes.toArray(new String[this.mEnabledAccountTypes.size()]);
    }

    public void preloadDrawableForType(final Context context, final String accountType) {
        new AsyncTask<Void, Void, Void>() {
            protected Void doInBackground(Void... params) {
                AuthenticatorHelper.this.getDrawableForType(context, accountType);
                return null;
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, (Void[]) null);
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public android.graphics.drawable.Drawable getDrawableForType(android.content.Context r7, java.lang.String r8) {
        /*
        r6 = this;
        r2 = 0;
        r4 = r6.mAccTypeIconCache;
        monitor-enter(r4);
        r3 = r6.mAccTypeIconCache;	 Catch:{ all -> 0x005b }
        r3 = r3.containsKey(r8);	 Catch:{ all -> 0x005b }
        if (r3 == 0) goto L_0x0016;
    L_0x000c:
        r3 = r6.mAccTypeIconCache;	 Catch:{ all -> 0x005b }
        r3 = r3.get(r8);	 Catch:{ all -> 0x005b }
        r3 = (android.graphics.drawable.Drawable) r3;	 Catch:{ all -> 0x005b }
        monitor-exit(r4);	 Catch:{ all -> 0x005b }
    L_0x0015:
        return r3;
    L_0x0016:
        monitor-exit(r4);	 Catch:{ all -> 0x005b }
        r3 = r6.mTypeToAuthDescription;
        r3 = r3.containsKey(r8);
        if (r3 == 0) goto L_0x004f;
    L_0x001f:
        r3 = r6.mTypeToAuthDescription;	 Catch:{ NameNotFoundException -> 0x0061, NotFoundException -> 0x0063 }
        r1 = r3.get(r8);	 Catch:{ NameNotFoundException -> 0x0061, NotFoundException -> 0x0063 }
        r1 = (android.accounts.AuthenticatorDescription) r1;	 Catch:{ NameNotFoundException -> 0x0061, NotFoundException -> 0x0063 }
        r3 = r1.packageName;	 Catch:{ NameNotFoundException -> 0x0061, NotFoundException -> 0x0063 }
        r4 = 0;
        r5 = r6.mUserHandle;	 Catch:{ NameNotFoundException -> 0x0061, NotFoundException -> 0x0063 }
        r0 = r7.createPackageContextAsUser(r3, r4, r5);	 Catch:{ NameNotFoundException -> 0x0061, NotFoundException -> 0x0063 }
        r3 = r6.mContext;	 Catch:{ NameNotFoundException -> 0x0061, NotFoundException -> 0x0063 }
        r3 = r3.getPackageManager();	 Catch:{ NameNotFoundException -> 0x0061, NotFoundException -> 0x0063 }
        r4 = r0.getResources();	 Catch:{ NameNotFoundException -> 0x0061, NotFoundException -> 0x0063 }
        r5 = r1.iconId;	 Catch:{ NameNotFoundException -> 0x0061, NotFoundException -> 0x0063 }
        r4 = r4.getDrawable(r5);	 Catch:{ NameNotFoundException -> 0x0061, NotFoundException -> 0x0063 }
        r5 = r6.mUserHandle;	 Catch:{ NameNotFoundException -> 0x0061, NotFoundException -> 0x0063 }
        r2 = r3.getUserBadgedIcon(r4, r5);	 Catch:{ NameNotFoundException -> 0x0061, NotFoundException -> 0x0063 }
        r4 = r6.mAccTypeIconCache;	 Catch:{ NameNotFoundException -> 0x0061, NotFoundException -> 0x0063 }
        monitor-enter(r4);	 Catch:{ NameNotFoundException -> 0x0061, NotFoundException -> 0x0063 }
        r3 = r6.mAccTypeIconCache;	 Catch:{ all -> 0x005e }
        r3.put(r8, r2);	 Catch:{ all -> 0x005e }
        monitor-exit(r4);	 Catch:{ all -> 0x005e }
    L_0x004f:
        if (r2 != 0) goto L_0x0059;
    L_0x0051:
        r3 = r7.getPackageManager();
        r2 = r3.getDefaultActivityIcon();
    L_0x0059:
        r3 = r2;
        goto L_0x0015;
    L_0x005b:
        r3 = move-exception;
        monitor-exit(r4);	 Catch:{ all -> 0x005b }
        throw r3;
    L_0x005e:
        r3 = move-exception;
        monitor-exit(r4);	 Catch:{ all -> 0x005e }
        throw r3;	 Catch:{ NameNotFoundException -> 0x0061, NotFoundException -> 0x0063 }
    L_0x0061:
        r3 = move-exception;
        goto L_0x004f;
    L_0x0063:
        r3 = move-exception;
        goto L_0x004f;
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.settings.accounts.AuthenticatorHelper.getDrawableForType(android.content.Context, java.lang.String):android.graphics.drawable.Drawable");
    }

    public CharSequence getLabelForType(Context context, String accountType) {
        CharSequence label = null;
        if (this.mTypeToAuthDescription.containsKey(accountType)) {
            try {
                AuthenticatorDescription desc = (AuthenticatorDescription) this.mTypeToAuthDescription.get(accountType);
                label = context.createPackageContextAsUser(desc.packageName, 0, this.mUserHandle).getResources().getText(desc.labelId);
            } catch (NameNotFoundException e) {
                Log.w("AuthenticatorHelper", "No label name for account type " + accountType);
            } catch (NotFoundException e2) {
                Log.w("AuthenticatorHelper", "No label icon for account type " + accountType);
            }
        }
        return label;
    }

    public void updateAuthDescriptions(Context context) {
        this.mAuthDescs = AccountManager.get(context).getAuthenticatorTypesAsUser(this.mUserHandle.getIdentifier());
        for (int i = 0; i < this.mAuthDescs.length; i++) {
            this.mTypeToAuthDescription.put(this.mAuthDescs[i].type, this.mAuthDescs[i]);
        }
    }

    public boolean containsAccountType(String accountType) {
        return this.mTypeToAuthDescription.containsKey(accountType);
    }

    public AuthenticatorDescription getAccountTypeDescription(String accountType) {
        return (AuthenticatorDescription) this.mTypeToAuthDescription.get(accountType);
    }

    public boolean hasAccountPreferences(String accountType) {
        if (containsAccountType(accountType)) {
            AuthenticatorDescription desc = getAccountTypeDescription(accountType);
            if (!(desc == null || desc.accountPreferencesId == 0)) {
                return true;
            }
        }
        return false;
    }

    void onAccountsUpdated(Account[] accounts) {
        updateAuthDescriptions(this.mContext);
        if (accounts == null) {
            accounts = AccountManager.get(this.mContext).getAccountsAsUser(this.mUserHandle.getIdentifier());
        }
        this.mEnabledAccountTypes.clear();
        this.mAccTypeIconCache.clear();
        for (Account account : accounts) {
            if (!this.mEnabledAccountTypes.contains(account.type)) {
                this.mEnabledAccountTypes.add(account.type);
            }
        }
        buildAccountTypeToAuthoritiesMap();
        if (this.mListeningToAccountUpdates) {
            this.mListener.onAccountsUpdate(this.mUserHandle);
        }
    }

    public void onReceive(Context context, Intent intent) {
        onAccountsUpdated(AccountManager.get(this.mContext).getAccountsAsUser(this.mUserHandle.getIdentifier()));
    }

    public void listenToAccountUpdates() {
        if (!this.mListeningToAccountUpdates) {
            IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction("android.accounts.LOGIN_ACCOUNTS_CHANGED");
            intentFilter.addAction("android.intent.action.DEVICE_STORAGE_OK");
            this.mContext.registerReceiverAsUser(this, this.mUserHandle, intentFilter, null, null);
            this.mListeningToAccountUpdates = true;
        }
    }

    public void stopListeningToAccountUpdates() {
        if (this.mListeningToAccountUpdates) {
            this.mContext.unregisterReceiver(this);
            this.mListeningToAccountUpdates = false;
        }
    }

    public ArrayList<String> getAuthoritiesForAccountType(String type) {
        return (ArrayList) this.mAccountTypeToAuthorities.get(type);
    }

    private void buildAccountTypeToAuthoritiesMap() {
        this.mAccountTypeToAuthorities.clear();
        for (SyncAdapterType sa : ContentResolver.getSyncAdapterTypesAsUser(this.mUserHandle.getIdentifier())) {
            ArrayList<String> authorities = (ArrayList) this.mAccountTypeToAuthorities.get(sa.accountType);
            if (authorities == null) {
                authorities = new ArrayList();
                this.mAccountTypeToAuthorities.put(sa.accountType, authorities);
            }
            if (Log.isLoggable("AuthenticatorHelper", 2)) {
                Log.d("AuthenticatorHelper", "Added authority " + sa.authority + " to accountType " + sa.accountType);
            }
            authorities.add(sa.authority);
        }
    }
}
