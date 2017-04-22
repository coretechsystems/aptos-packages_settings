package com.android.settings;

import android.app.Activity;
import android.app.Fragment;
import android.content.Intent;
import com.android.internal.widget.LockPatternUtils;
import com.android.settings.ConfirmLockPattern.InternalActivity;

public final class ChooseLockSettingsHelper {
    private Activity mActivity;
    private Fragment mFragment;
    private LockPatternUtils mLockPatternUtils;

    public ChooseLockSettingsHelper(Activity activity) {
        this.mActivity = activity;
        this.mLockPatternUtils = new LockPatternUtils(activity);
    }

    public ChooseLockSettingsHelper(Activity activity, Fragment fragment) {
        this(activity);
        this.mFragment = fragment;
    }

    public LockPatternUtils utils() {
        return this.mLockPatternUtils;
    }

    boolean launchConfirmationActivity(int request, CharSequence message, CharSequence details) {
        return launchConfirmationActivity(request, message, details, false);
    }

    boolean launchConfirmationActivity(int request, CharSequence message, CharSequence details, boolean returnCredentials) {
        switch (this.mLockPatternUtils.getKeyguardStoredPasswordQuality()) {
            case 65536:
                return confirmPattern(request, message, details, returnCredentials);
            case 131072:
            case 196608:
            case 262144:
            case 327680:
            case 393216:
                return confirmPassword(request, message, returnCredentials);
            default:
                return false;
        }
    }

    private boolean confirmPattern(int request, CharSequence message, CharSequence details, boolean returnCredentials) {
        if (!this.mLockPatternUtils.isLockPatternEnabled() || !this.mLockPatternUtils.savedPatternExists()) {
            return false;
        }
        Intent intent = new Intent();
        intent.putExtra("com.android.settings.ConfirmLockPattern.header", message);
        intent.putExtra("com.android.settings.ConfirmLockPattern.footer", details);
        intent.setClassName("com.android.settings", returnCredentials ? InternalActivity.class.getName() : ConfirmLockPattern.class.getName());
        if (this.mFragment != null) {
            this.mFragment.startActivityForResult(intent, request);
        } else {
            this.mActivity.startActivityForResult(intent, request);
        }
        return true;
    }

    private boolean confirmPassword(int request, CharSequence message, boolean returnCredentials) {
        if (!this.mLockPatternUtils.isLockPasswordEnabled()) {
            return false;
        }
        Intent intent = new Intent();
        intent.putExtra("com.android.settings.ConfirmLockPattern.header", message);
        intent.setClassName("com.android.settings", returnCredentials ? ConfirmLockPassword.InternalActivity.class.getName() : ConfirmLockPassword.class.getName());
        if (this.mFragment != null) {
            this.mFragment.startActivityForResult(intent, request);
        } else {
            this.mActivity.startActivityForResult(intent, request);
        }
        return true;
    }
}
