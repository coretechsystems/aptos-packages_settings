package com.android.settings.accounts;

import android.accounts.AccountManager;
import android.accounts.AccountManagerCallback;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.Log;
import android.widget.Toast;
import com.android.settings.R;
import com.android.settings.Utils;

public class AddAccountSettings extends Activity {
    private boolean mAddAccountCalled = false;
    private final AccountManagerCallback<Bundle> mCallback = new AccountManagerCallback<Bundle>() {
        public void run(android.accounts.AccountManagerFuture<android.os.Bundle> r9) {
            /* JADX: method processing error */
/*
Error: jadx.core.utils.exceptions.JadxRuntimeException: Can't find block by offset: 0x006f in list [B:9:0x006a]
	at jadx.core.utils.BlockUtils.getBlockByOffset(BlockUtils.java:42)
	at jadx.core.dex.instructions.IfNode.initBlocks(IfNode.java:60)
	at jadx.core.dex.visitors.blocksmaker.BlockFinish.initBlocksInIfNodes(BlockFinish.java:48)
	at jadx.core.dex.visitors.blocksmaker.BlockFinish.visit(BlockFinish.java:33)
	at jadx.core.dex.visitors.DepthTraversal.visit(DepthTraversal.java:31)
	at jadx.core.dex.visitors.DepthTraversal.visit(DepthTraversal.java:17)
	at jadx.core.dex.visitors.DepthTraversal.visit(DepthTraversal.java:14)
	at jadx.core.ProcessClass.process(ProcessClass.java:37)
	at jadx.api.JadxDecompiler.processClass(JadxDecompiler.java:306)
	at jadx.api.JavaClass.decompile(JavaClass.java:62)
*/
            /*
            r8 = this;
            r2 = 1;
            r1 = r9.getResult();	 Catch:{ OperationCanceledException -> 0x008e, IOException -> 0x00a7, AuthenticatorException -> 0x00d1, all -> 0x00fc }
            r1 = (android.os.Bundle) r1;	 Catch:{ OperationCanceledException -> 0x008e, IOException -> 0x00a7, AuthenticatorException -> 0x00d1, all -> 0x00fc }
            r5 = "intent";	 Catch:{ OperationCanceledException -> 0x008e, IOException -> 0x00a7, AuthenticatorException -> 0x00d1, all -> 0x00fc }
            r4 = r1.get(r5);	 Catch:{ OperationCanceledException -> 0x008e, IOException -> 0x00a7, AuthenticatorException -> 0x00d1, all -> 0x00fc }
            r4 = (android.content.Intent) r4;	 Catch:{ OperationCanceledException -> 0x008e, IOException -> 0x00a7, AuthenticatorException -> 0x00d1, all -> 0x00fc }
            if (r4 == 0) goto L_0x0070;	 Catch:{ OperationCanceledException -> 0x008e, IOException -> 0x00a7, AuthenticatorException -> 0x00d1, all -> 0x00fc }
        L_0x0011:
            r2 = 0;	 Catch:{ OperationCanceledException -> 0x008e, IOException -> 0x00a7, AuthenticatorException -> 0x00d1, all -> 0x00fc }
            r0 = new android.os.Bundle;	 Catch:{ OperationCanceledException -> 0x008e, IOException -> 0x00a7, AuthenticatorException -> 0x00d1, all -> 0x00fc }
            r0.<init>();	 Catch:{ OperationCanceledException -> 0x008e, IOException -> 0x00a7, AuthenticatorException -> 0x00d1, all -> 0x00fc }
            r5 = "pendingIntent";	 Catch:{ OperationCanceledException -> 0x008e, IOException -> 0x00a7, AuthenticatorException -> 0x00d1, all -> 0x00fc }
            r6 = com.android.settings.accounts.AddAccountSettings.this;	 Catch:{ OperationCanceledException -> 0x008e, IOException -> 0x00a7, AuthenticatorException -> 0x00d1, all -> 0x00fc }
            r6 = r6.mPendingIntent;	 Catch:{ OperationCanceledException -> 0x008e, IOException -> 0x00a7, AuthenticatorException -> 0x00d1, all -> 0x00fc }
            r0.putParcelable(r5, r6);	 Catch:{ OperationCanceledException -> 0x008e, IOException -> 0x00a7, AuthenticatorException -> 0x00d1, all -> 0x00fc }
            r5 = "hasMultipleUsers";	 Catch:{ OperationCanceledException -> 0x008e, IOException -> 0x00a7, AuthenticatorException -> 0x00d1, all -> 0x00fc }
            r6 = com.android.settings.accounts.AddAccountSettings.this;	 Catch:{ OperationCanceledException -> 0x008e, IOException -> 0x00a7, AuthenticatorException -> 0x00d1, all -> 0x00fc }
            r6 = com.android.settings.Utils.hasMultipleUsers(r6);	 Catch:{ OperationCanceledException -> 0x008e, IOException -> 0x00a7, AuthenticatorException -> 0x00d1, all -> 0x00fc }
            r0.putBoolean(r5, r6);	 Catch:{ OperationCanceledException -> 0x008e, IOException -> 0x00a7, AuthenticatorException -> 0x00d1, all -> 0x00fc }
            r5 = "android.intent.extra.USER";	 Catch:{ OperationCanceledException -> 0x008e, IOException -> 0x00a7, AuthenticatorException -> 0x00d1, all -> 0x00fc }
            r6 = com.android.settings.accounts.AddAccountSettings.this;	 Catch:{ OperationCanceledException -> 0x008e, IOException -> 0x00a7, AuthenticatorException -> 0x00d1, all -> 0x00fc }
            r6 = r6.mUserHandle;	 Catch:{ OperationCanceledException -> 0x008e, IOException -> 0x00a7, AuthenticatorException -> 0x00d1, all -> 0x00fc }
            r0.putParcelable(r5, r6);	 Catch:{ OperationCanceledException -> 0x008e, IOException -> 0x00a7, AuthenticatorException -> 0x00d1, all -> 0x00fc }
            r4.putExtras(r0);	 Catch:{ OperationCanceledException -> 0x008e, IOException -> 0x00a7, AuthenticatorException -> 0x00d1, all -> 0x00fc }
            r5 = com.android.settings.accounts.AddAccountSettings.this;	 Catch:{ OperationCanceledException -> 0x008e, IOException -> 0x00a7, AuthenticatorException -> 0x00d1, all -> 0x00fc }
            r6 = 2;	 Catch:{ OperationCanceledException -> 0x008e, IOException -> 0x00a7, AuthenticatorException -> 0x00d1, all -> 0x00fc }
            r7 = com.android.settings.accounts.AddAccountSettings.this;	 Catch:{ OperationCanceledException -> 0x008e, IOException -> 0x00a7, AuthenticatorException -> 0x00d1, all -> 0x00fc }
            r7 = r7.mUserHandle;	 Catch:{ OperationCanceledException -> 0x008e, IOException -> 0x00a7, AuthenticatorException -> 0x00d1, all -> 0x00fc }
            r5.startActivityForResultAsUser(r4, r6, r7);	 Catch:{ OperationCanceledException -> 0x008e, IOException -> 0x00a7, AuthenticatorException -> 0x00d1, all -> 0x00fc }
        L_0x0047:
            r5 = "AccountSettings";	 Catch:{ OperationCanceledException -> 0x008e, IOException -> 0x00a7, AuthenticatorException -> 0x00d1, all -> 0x00fc }
            r6 = 2;	 Catch:{ OperationCanceledException -> 0x008e, IOException -> 0x00a7, AuthenticatorException -> 0x00d1, all -> 0x00fc }
            r5 = android.util.Log.isLoggable(r5, r6);	 Catch:{ OperationCanceledException -> 0x008e, IOException -> 0x00a7, AuthenticatorException -> 0x00d1, all -> 0x00fc }
            if (r5 == 0) goto L_0x0068;	 Catch:{ OperationCanceledException -> 0x008e, IOException -> 0x00a7, AuthenticatorException -> 0x00d1, all -> 0x00fc }
        L_0x0050:
            r5 = "AccountSettings";	 Catch:{ OperationCanceledException -> 0x008e, IOException -> 0x00a7, AuthenticatorException -> 0x00d1, all -> 0x00fc }
            r6 = new java.lang.StringBuilder;	 Catch:{ OperationCanceledException -> 0x008e, IOException -> 0x00a7, AuthenticatorException -> 0x00d1, all -> 0x00fc }
            r6.<init>();	 Catch:{ OperationCanceledException -> 0x008e, IOException -> 0x00a7, AuthenticatorException -> 0x00d1, all -> 0x00fc }
            r7 = "account added: ";	 Catch:{ OperationCanceledException -> 0x008e, IOException -> 0x00a7, AuthenticatorException -> 0x00d1, all -> 0x00fc }
            r6 = r6.append(r7);	 Catch:{ OperationCanceledException -> 0x008e, IOException -> 0x00a7, AuthenticatorException -> 0x00d1, all -> 0x00fc }
            r6 = r6.append(r1);	 Catch:{ OperationCanceledException -> 0x008e, IOException -> 0x00a7, AuthenticatorException -> 0x00d1, all -> 0x00fc }
            r6 = r6.toString();	 Catch:{ OperationCanceledException -> 0x008e, IOException -> 0x00a7, AuthenticatorException -> 0x00d1, all -> 0x00fc }
            android.util.Log.v(r5, r6);	 Catch:{ OperationCanceledException -> 0x008e, IOException -> 0x00a7, AuthenticatorException -> 0x00d1, all -> 0x00fc }
        L_0x0068:
            if (r2 == 0) goto L_0x006f;
        L_0x006a:
            r5 = com.android.settings.accounts.AddAccountSettings.this;
            r5.finish();
        L_0x006f:
            return;
        L_0x0070:
            r5 = com.android.settings.accounts.AddAccountSettings.this;	 Catch:{ OperationCanceledException -> 0x008e, IOException -> 0x00a7, AuthenticatorException -> 0x00d1, all -> 0x00fc }
            r6 = -1;	 Catch:{ OperationCanceledException -> 0x008e, IOException -> 0x00a7, AuthenticatorException -> 0x00d1, all -> 0x00fc }
            r5.setResult(r6);	 Catch:{ OperationCanceledException -> 0x008e, IOException -> 0x00a7, AuthenticatorException -> 0x00d1, all -> 0x00fc }
            r5 = com.android.settings.accounts.AddAccountSettings.this;	 Catch:{ OperationCanceledException -> 0x008e, IOException -> 0x00a7, AuthenticatorException -> 0x00d1, all -> 0x00fc }
            r5 = r5.mPendingIntent;	 Catch:{ OperationCanceledException -> 0x008e, IOException -> 0x00a7, AuthenticatorException -> 0x00d1, all -> 0x00fc }
            if (r5 == 0) goto L_0x0047;	 Catch:{ OperationCanceledException -> 0x008e, IOException -> 0x00a7, AuthenticatorException -> 0x00d1, all -> 0x00fc }
        L_0x007e:
            r5 = com.android.settings.accounts.AddAccountSettings.this;	 Catch:{ OperationCanceledException -> 0x008e, IOException -> 0x00a7, AuthenticatorException -> 0x00d1, all -> 0x00fc }
            r5 = r5.mPendingIntent;	 Catch:{ OperationCanceledException -> 0x008e, IOException -> 0x00a7, AuthenticatorException -> 0x00d1, all -> 0x00fc }
            r5.cancel();	 Catch:{ OperationCanceledException -> 0x008e, IOException -> 0x00a7, AuthenticatorException -> 0x00d1, all -> 0x00fc }
            r5 = com.android.settings.accounts.AddAccountSettings.this;	 Catch:{ OperationCanceledException -> 0x008e, IOException -> 0x00a7, AuthenticatorException -> 0x00d1, all -> 0x00fc }
            r6 = 0;	 Catch:{ OperationCanceledException -> 0x008e, IOException -> 0x00a7, AuthenticatorException -> 0x00d1, all -> 0x00fc }
            r5.mPendingIntent = r6;	 Catch:{ OperationCanceledException -> 0x008e, IOException -> 0x00a7, AuthenticatorException -> 0x00d1, all -> 0x00fc }
            goto L_0x0047;
        L_0x008e:
            r3 = move-exception;
            r5 = "AccountSettings";	 Catch:{ OperationCanceledException -> 0x008e, IOException -> 0x00a7, AuthenticatorException -> 0x00d1, all -> 0x00fc }
            r6 = 2;	 Catch:{ OperationCanceledException -> 0x008e, IOException -> 0x00a7, AuthenticatorException -> 0x00d1, all -> 0x00fc }
            r5 = android.util.Log.isLoggable(r5, r6);	 Catch:{ OperationCanceledException -> 0x008e, IOException -> 0x00a7, AuthenticatorException -> 0x00d1, all -> 0x00fc }
            if (r5 == 0) goto L_0x009f;	 Catch:{ OperationCanceledException -> 0x008e, IOException -> 0x00a7, AuthenticatorException -> 0x00d1, all -> 0x00fc }
        L_0x0098:
            r5 = "AccountSettings";	 Catch:{ OperationCanceledException -> 0x008e, IOException -> 0x00a7, AuthenticatorException -> 0x00d1, all -> 0x00fc }
            r6 = "addAccount was canceled";	 Catch:{ OperationCanceledException -> 0x008e, IOException -> 0x00a7, AuthenticatorException -> 0x00d1, all -> 0x00fc }
            android.util.Log.v(r5, r6);	 Catch:{ OperationCanceledException -> 0x008e, IOException -> 0x00a7, AuthenticatorException -> 0x00d1, all -> 0x00fc }
        L_0x009f:
            if (r2 == 0) goto L_0x006f;
        L_0x00a1:
            r5 = com.android.settings.accounts.AddAccountSettings.this;
            r5.finish();
            goto L_0x006f;
        L_0x00a7:
            r3 = move-exception;
            r5 = "AccountSettings";	 Catch:{ OperationCanceledException -> 0x008e, IOException -> 0x00a7, AuthenticatorException -> 0x00d1, all -> 0x00fc }
            r6 = 2;	 Catch:{ OperationCanceledException -> 0x008e, IOException -> 0x00a7, AuthenticatorException -> 0x00d1, all -> 0x00fc }
            r5 = android.util.Log.isLoggable(r5, r6);	 Catch:{ OperationCanceledException -> 0x008e, IOException -> 0x00a7, AuthenticatorException -> 0x00d1, all -> 0x00fc }
            if (r5 == 0) goto L_0x00c9;	 Catch:{ OperationCanceledException -> 0x008e, IOException -> 0x00a7, AuthenticatorException -> 0x00d1, all -> 0x00fc }
        L_0x00b1:
            r5 = "AccountSettings";	 Catch:{ OperationCanceledException -> 0x008e, IOException -> 0x00a7, AuthenticatorException -> 0x00d1, all -> 0x00fc }
            r6 = new java.lang.StringBuilder;	 Catch:{ OperationCanceledException -> 0x008e, IOException -> 0x00a7, AuthenticatorException -> 0x00d1, all -> 0x00fc }
            r6.<init>();	 Catch:{ OperationCanceledException -> 0x008e, IOException -> 0x00a7, AuthenticatorException -> 0x00d1, all -> 0x00fc }
            r7 = "addAccount failed: ";	 Catch:{ OperationCanceledException -> 0x008e, IOException -> 0x00a7, AuthenticatorException -> 0x00d1, all -> 0x00fc }
            r6 = r6.append(r7);	 Catch:{ OperationCanceledException -> 0x008e, IOException -> 0x00a7, AuthenticatorException -> 0x00d1, all -> 0x00fc }
            r6 = r6.append(r3);	 Catch:{ OperationCanceledException -> 0x008e, IOException -> 0x00a7, AuthenticatorException -> 0x00d1, all -> 0x00fc }
            r6 = r6.toString();	 Catch:{ OperationCanceledException -> 0x008e, IOException -> 0x00a7, AuthenticatorException -> 0x00d1, all -> 0x00fc }
            android.util.Log.v(r5, r6);	 Catch:{ OperationCanceledException -> 0x008e, IOException -> 0x00a7, AuthenticatorException -> 0x00d1, all -> 0x00fc }
        L_0x00c9:
            if (r2 == 0) goto L_0x006f;
        L_0x00cb:
            r5 = com.android.settings.accounts.AddAccountSettings.this;
            r5.finish();
            goto L_0x006f;
        L_0x00d1:
            r3 = move-exception;
            r5 = "AccountSettings";	 Catch:{ OperationCanceledException -> 0x008e, IOException -> 0x00a7, AuthenticatorException -> 0x00d1, all -> 0x00fc }
            r6 = 2;	 Catch:{ OperationCanceledException -> 0x008e, IOException -> 0x00a7, AuthenticatorException -> 0x00d1, all -> 0x00fc }
            r5 = android.util.Log.isLoggable(r5, r6);	 Catch:{ OperationCanceledException -> 0x008e, IOException -> 0x00a7, AuthenticatorException -> 0x00d1, all -> 0x00fc }
            if (r5 == 0) goto L_0x00f3;	 Catch:{ OperationCanceledException -> 0x008e, IOException -> 0x00a7, AuthenticatorException -> 0x00d1, all -> 0x00fc }
        L_0x00db:
            r5 = "AccountSettings";	 Catch:{ OperationCanceledException -> 0x008e, IOException -> 0x00a7, AuthenticatorException -> 0x00d1, all -> 0x00fc }
            r6 = new java.lang.StringBuilder;	 Catch:{ OperationCanceledException -> 0x008e, IOException -> 0x00a7, AuthenticatorException -> 0x00d1, all -> 0x00fc }
            r6.<init>();	 Catch:{ OperationCanceledException -> 0x008e, IOException -> 0x00a7, AuthenticatorException -> 0x00d1, all -> 0x00fc }
            r7 = "addAccount failed: ";	 Catch:{ OperationCanceledException -> 0x008e, IOException -> 0x00a7, AuthenticatorException -> 0x00d1, all -> 0x00fc }
            r6 = r6.append(r7);	 Catch:{ OperationCanceledException -> 0x008e, IOException -> 0x00a7, AuthenticatorException -> 0x00d1, all -> 0x00fc }
            r6 = r6.append(r3);	 Catch:{ OperationCanceledException -> 0x008e, IOException -> 0x00a7, AuthenticatorException -> 0x00d1, all -> 0x00fc }
            r6 = r6.toString();	 Catch:{ OperationCanceledException -> 0x008e, IOException -> 0x00a7, AuthenticatorException -> 0x00d1, all -> 0x00fc }
            android.util.Log.v(r5, r6);	 Catch:{ OperationCanceledException -> 0x008e, IOException -> 0x00a7, AuthenticatorException -> 0x00d1, all -> 0x00fc }
        L_0x00f3:
            if (r2 == 0) goto L_0x006f;
        L_0x00f5:
            r5 = com.android.settings.accounts.AddAccountSettings.this;
            r5.finish();
            goto L_0x006f;
        L_0x00fc:
            r5 = move-exception;
            if (r2 == 0) goto L_0x0104;
        L_0x00ff:
            r6 = com.android.settings.accounts.AddAccountSettings.this;
            r6.finish();
        L_0x0104:
            throw r5;
            */
            throw new UnsupportedOperationException("Method not decompiled: com.android.settings.accounts.AddAccountSettings.1.run(android.accounts.AccountManagerFuture):void");
        }
    };
    private PendingIntent mPendingIntent;
    private UserHandle mUserHandle;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            this.mAddAccountCalled = savedInstanceState.getBoolean("AddAccountCalled");
            if (Log.isLoggable("AccountSettings", 2)) {
                Log.v("AccountSettings", "restored");
            }
        }
        UserManager um = (UserManager) getSystemService("user");
        this.mUserHandle = Utils.getSecureTargetUser(getActivityToken(), um, null, getIntent().getExtras());
        if (um.hasUserRestriction("no_modify_accounts", this.mUserHandle)) {
            Toast.makeText(this, R.string.user_cannot_add_accounts_message, 1).show();
            finish();
        } else if (this.mAddAccountCalled) {
            finish();
        } else {
            String[] authorities = getIntent().getStringArrayExtra("authorities");
            String[] accountTypes = getIntent().getStringArrayExtra("account_types");
            Intent intent = new Intent(this, ChooseAccountActivity.class);
            if (authorities != null) {
                intent.putExtra("authorities", authorities);
            }
            if (accountTypes != null) {
                intent.putExtra("account_types", accountTypes);
            }
            intent.putExtra("android.intent.extra.USER", this.mUserHandle);
            startActivityForResult(intent, 1);
        }
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case 1:
                if (resultCode == 0) {
                    setResult(resultCode);
                    finish();
                    return;
                }
                addAccount(data.getStringExtra("selected_account"));
                return;
            case 2:
                setResult(resultCode);
                if (this.mPendingIntent != null) {
                    this.mPendingIntent.cancel();
                    this.mPendingIntent = null;
                }
                finish();
                return;
            default:
                return;
        }
    }

    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean("AddAccountCalled", this.mAddAccountCalled);
        if (Log.isLoggable("AccountSettings", 2)) {
            Log.v("AccountSettings", "saved");
        }
    }

    private void addAccount(String accountType) {
        Bundle addAccountOptions = new Bundle();
        Intent identityIntent = new Intent();
        identityIntent.setComponent(new ComponentName("SHOULDN'T RESOLVE!", "SHOULDN'T RESOLVE!"));
        identityIntent.setAction("SHOULDN'T RESOLVE!");
        identityIntent.addCategory("SHOULDN'T RESOLVE!");
        this.mPendingIntent = PendingIntent.getBroadcast(this, 0, identityIntent, 0);
        addAccountOptions.putParcelable("pendingIntent", this.mPendingIntent);
        addAccountOptions.putBoolean("hasMultipleUsers", Utils.hasMultipleUsers(this));
        AccountManager.get(this).addAccountAsUser(accountType, null, null, addAccountOptions, null, this.mCallback, null, this.mUserHandle);
        this.mAddAccountCalled = true;
    }
}
