package com.android.settings;

import android.accounts.Account;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.preference.Preference;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import java.util.ArrayList;

public class AccountPreference extends Preference {
    private Account mAccount;
    private ArrayList<String> mAuthorities;
    private boolean mShowTypeIcon;
    private int mStatus;
    private ImageView mSyncStatusIcon;

    public AccountPreference(Context context, Account account, Drawable icon, ArrayList<String> authorities, boolean showTypeIcon) {
        super(context);
        this.mAccount = account;
        this.mAuthorities = authorities;
        this.mShowTypeIcon = showTypeIcon;
        if (showTypeIcon) {
            setIcon(icon);
        } else {
            setIcon(getSyncStatusIcon(1));
        }
        setTitle(this.mAccount.name);
        setSummary("");
        setPersistent(false);
        setSyncStatus(1, false);
    }

    public Account getAccount() {
        return this.mAccount;
    }

    public ArrayList<String> getAuthorities() {
        return this.mAuthorities;
    }

    protected void onBindView(View view) {
        super.onBindView(view);
        if (!this.mShowTypeIcon) {
            this.mSyncStatusIcon = (ImageView) view.findViewById(16908294);
            this.mSyncStatusIcon.setImageResource(getSyncStatusIcon(this.mStatus));
            this.mSyncStatusIcon.setContentDescription(getSyncContentDescription(this.mStatus));
        }
    }

    public void setSyncStatus(int status, boolean updateSummary) {
        this.mStatus = status;
        if (!(this.mShowTypeIcon || this.mSyncStatusIcon == null)) {
            this.mSyncStatusIcon.setImageResource(getSyncStatusIcon(status));
            this.mSyncStatusIcon.setContentDescription(getSyncContentDescription(this.mStatus));
        }
        if (updateSummary) {
            setSummary(getSyncStatusMessage(status));
        }
    }

    private int getSyncStatusMessage(int status) {
        switch (status) {
            case 0:
                return R.string.sync_enabled;
            case 1:
                return R.string.sync_disabled;
            case 2:
                return R.string.sync_error;
            case 3:
                return R.string.sync_in_progress;
            default:
                Log.e("AccountPreference", "Unknown sync status: " + status);
                return R.string.sync_error;
        }
    }

    private int getSyncStatusIcon(int status) {
        switch (status) {
            case 0:
                return R.drawable.ic_sync_green_holo;
            case 1:
                return R.drawable.ic_sync_grey_holo;
            case 2:
                return R.drawable.ic_sync_red_holo;
            case 3:
                return R.drawable.ic_sync_green_holo;
            default:
                Log.e("AccountPreference", "Unknown sync status: " + status);
                return R.drawable.ic_sync_red_holo;
        }
    }

    private String getSyncContentDescription(int status) {
        switch (status) {
            case 0:
                return getContext().getString(R.string.accessibility_sync_enabled);
            case 1:
                return getContext().getString(R.string.accessibility_sync_disabled);
            case 2:
                return getContext().getString(R.string.accessibility_sync_error);
            case 3:
                return getContext().getString(R.string.accessibility_sync_in_progress);
            default:
                Log.e("AccountPreference", "Unknown sync status: " + status);
                return getContext().getString(R.string.accessibility_sync_error);
        }
    }
}
