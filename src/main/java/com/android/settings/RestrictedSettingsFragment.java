package com.android.settings;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.RestrictionsManager;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.os.UserManager;

public class RestrictedSettingsFragment extends SettingsPreferenceFragment {
    private boolean mChallengeRequested;
    private boolean mChallengeSucceeded;
    private final String mRestrictionKey;
    private RestrictionsManager mRestrictionsManager;
    private BroadcastReceiver mScreenOffReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            if (!RestrictedSettingsFragment.this.mChallengeRequested) {
                RestrictedSettingsFragment.this.mChallengeSucceeded = false;
                RestrictedSettingsFragment.this.mChallengeRequested = false;
            }
        }
    };
    private UserManager mUserManager;

    public RestrictedSettingsFragment(String restrictionKey) {
        this.mRestrictionKey = restrictionKey;
    }

    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        this.mRestrictionsManager = (RestrictionsManager) getSystemService("restrictions");
        this.mUserManager = (UserManager) getSystemService("user");
        if (icicle != null) {
            this.mChallengeSucceeded = icicle.getBoolean("chsc", false);
            this.mChallengeRequested = icicle.getBoolean("chrq", false);
        }
        IntentFilter offFilter = new IntentFilter("android.intent.action.SCREEN_OFF");
        offFilter.addAction("android.intent.action.USER_PRESENT");
        getActivity().registerReceiver(this.mScreenOffReceiver, offFilter);
    }

    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (getActivity().isChangingConfigurations()) {
            outState.putBoolean("chrq", this.mChallengeRequested);
            outState.putBoolean("chsc", this.mChallengeSucceeded);
        }
    }

    public void onResume() {
        super.onResume();
        if (shouldBeProviderProtected(this.mRestrictionKey)) {
            ensurePin();
        }
    }

    public void onDestroy() {
        getActivity().unregisterReceiver(this.mScreenOffReceiver);
        super.onDestroy();
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode != 12309) {
            super.onActivityResult(requestCode, resultCode, data);
        } else if (resultCode == -1) {
            this.mChallengeSucceeded = true;
            this.mChallengeRequested = false;
        } else {
            this.mChallengeSucceeded = false;
        }
    }

    private void ensurePin() {
        if (!this.mChallengeSucceeded && !this.mChallengeRequested && this.mRestrictionsManager.hasRestrictionsProvider()) {
            Intent intent = this.mRestrictionsManager.createLocalApprovalIntent();
            if (intent != null) {
                this.mChallengeRequested = true;
                this.mChallengeSucceeded = false;
                PersistableBundle request = new PersistableBundle();
                request.putString("android.request.mesg", getResources().getString(R.string.restr_pin_enter_admin_pin));
                intent.putExtra("android.content.extra.REQUEST_BUNDLE", request);
                startActivityForResult(intent, 12309);
            }
        }
    }

    protected boolean isRestrictedAndNotProviderProtected() {
        if (this.mRestrictionKey == null || "restrict_if_overridable".equals(this.mRestrictionKey) || !this.mUserManager.hasUserRestriction(this.mRestrictionKey) || this.mRestrictionsManager.hasRestrictionsProvider()) {
            return false;
        }
        return true;
    }

    protected boolean hasChallengeSucceeded() {
        return (this.mChallengeRequested && this.mChallengeSucceeded) || !this.mChallengeRequested;
    }

    protected boolean shouldBeProviderProtected(String restrictionKey) {
        boolean z = true;
        if (restrictionKey == null) {
            return false;
        }
        boolean restricted;
        if ("restrict_if_overridable".equals(restrictionKey) || this.mUserManager.hasUserRestriction(this.mRestrictionKey)) {
            restricted = true;
        } else {
            restricted = false;
        }
        if (!(restricted && this.mRestrictionsManager.hasRestrictionsProvider())) {
            z = false;
        }
        return z;
    }

    protected boolean isUiRestricted() {
        return isRestrictedAndNotProviderProtected() || !hasChallengeSucceeded();
    }
}
