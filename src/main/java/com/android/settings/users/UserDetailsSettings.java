package com.android.settings.users;

import android.app.Dialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.pm.UserInfo;
import android.os.Bundle;
import android.os.UserHandle;
import android.os.UserManager;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.SwitchPreference;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.Utils;

public class UserDetailsSettings extends SettingsPreferenceFragment implements OnPreferenceChangeListener, OnPreferenceClickListener {
    private static final String TAG = UserDetailsSettings.class.getSimpleName();
    private Bundle mDefaultGuestRestrictions;
    private boolean mGuestUser;
    private SwitchPreference mPhonePref;
    private Preference mRemoveUserPref;
    private UserInfo mUserInfo;
    private UserManager mUserManager;

    public void onCreate(Bundle icicle) {
        boolean z = true;
        super.onCreate(icicle);
        this.mUserManager = (UserManager) getActivity().getSystemService("user");
        addPreferencesFromResource(R.xml.user_details_settings);
        this.mPhonePref = (SwitchPreference) findPreference("enable_calling");
        this.mRemoveUserPref = findPreference("remove_user");
        this.mGuestUser = getArguments().getBoolean("guest_user", false);
        if (this.mGuestUser) {
            removePreference("remove_user");
            this.mPhonePref.setTitle(R.string.user_enable_calling);
            this.mDefaultGuestRestrictions = this.mUserManager.getDefaultGuestRestrictions();
            SwitchPreference switchPreference = this.mPhonePref;
            if (this.mDefaultGuestRestrictions.getBoolean("no_outgoing_calls")) {
                z = false;
            }
            switchPreference.setChecked(z);
        } else {
            int userId = getArguments().getInt("user_id", -1);
            if (userId == -1) {
                throw new RuntimeException("Arguments to this fragment must contain the user id");
            }
            boolean z2;
            this.mUserInfo = this.mUserManager.getUserInfo(userId);
            SwitchPreference switchPreference2 = this.mPhonePref;
            if (this.mUserManager.hasUserRestriction("no_outgoing_calls", new UserHandle(userId))) {
                z2 = false;
            } else {
                z2 = true;
            }
            switchPreference2.setChecked(z2);
            this.mRemoveUserPref.setOnPreferenceClickListener(this);
        }
        if (this.mUserManager.hasUserRestriction("no_remove_user")) {
            removePreference("remove_user");
        }
        this.mPhonePref.setOnPreferenceChangeListener(this);
    }

    public boolean onPreferenceClick(Preference preference) {
        if (preference != this.mRemoveUserPref) {
            return false;
        }
        if (UserHandle.myUserId() != 0) {
            throw new RuntimeException("Only the owner can remove a user");
        }
        showDialog(1);
        return true;
    }

    public boolean onPreferenceChange(Preference preference, Object newValue) {
        boolean z = false;
        String str;
        boolean z2;
        UserHandle userHandle;
        if (this.mGuestUser) {
            Bundle bundle = this.mDefaultGuestRestrictions;
            str = "no_outgoing_calls";
            if (((Boolean) newValue).booleanValue()) {
                z2 = false;
            } else {
                z2 = true;
            }
            bundle.putBoolean(str, z2);
            this.mDefaultGuestRestrictions.putBoolean("no_sms", true);
            this.mUserManager.setDefaultGuestRestrictions(this.mDefaultGuestRestrictions);
            for (UserInfo user : this.mUserManager.getUsers(true)) {
                if (user.isGuest()) {
                    userHandle = new UserHandle(user.id);
                    Bundle userRestrictions = this.mUserManager.getUserRestrictions(userHandle);
                    userRestrictions.putAll(this.mDefaultGuestRestrictions);
                    this.mUserManager.setUserRestrictions(userRestrictions, userHandle);
                }
            }
        } else {
            userHandle = new UserHandle(this.mUserInfo.id);
            UserManager userManager = this.mUserManager;
            str = "no_outgoing_calls";
            if (((Boolean) newValue).booleanValue()) {
                z2 = false;
            } else {
                z2 = true;
            }
            userManager.setUserRestriction(str, z2, userHandle);
            UserManager userManager2 = this.mUserManager;
            String str2 = "no_sms";
            if (!((Boolean) newValue).booleanValue()) {
                z = true;
            }
            userManager2.setUserRestriction(str2, z, userHandle);
        }
        return true;
    }

    public Dialog onCreateDialog(int dialogId) {
        if (getActivity() == null) {
            return null;
        }
        switch (dialogId) {
            case 1:
                return Utils.createRemoveConfirmationDialog(getActivity(), this.mUserInfo.id, new OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        UserDetailsSettings.this.removeUser();
                    }
                });
            default:
                return null;
        }
    }

    void removeUser() {
        this.mUserManager.removeUser(this.mUserInfo.id);
        finishFragment();
    }
}
