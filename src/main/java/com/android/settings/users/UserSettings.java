package com.android.settings.users;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.ActivityManagerNative;
import android.app.AlertDialog.Builder;
import android.app.Dialog;
import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnDismissListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.UserInfo;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Process;
import android.os.RemoteException;
import android.os.UserHandle;
import android.os.UserManager;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceGroup;
import android.provider.Settings.Global;
import android.provider.Settings.Secure;
import android.util.Log;
import android.util.SparseArray;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.SimpleAdapter;
import com.android.internal.util.UserIcons;
import com.android.internal.widget.LockPatternUtils;
import com.android.settings.OwnerInfoSettings;
import com.android.settings.R;
import com.android.settings.SelectableEditTextPreference;
import com.android.settings.SettingsActivity;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.Utils;
import com.android.settings.drawable.CircleFramedDrawable;
import com.android.settings.users.EditUserInfoController.OnContentChangedCallback;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class UserSettings extends SettingsPreferenceFragment implements OnDismissListener, OnPreferenceChangeListener, OnPreferenceClickListener, OnClickListener, OnContentChangedCallback {
    private Preference mAddUser;
    private int mAddedUserId = 0;
    private boolean mAddingUser;
    private boolean mCanAddRestrictedProfile = true;
    private Drawable mDefaultIconDrawable;
    private EditUserInfoController mEditUserInfoController;
    private boolean mEnabled = true;
    private Handler mHandler;
    private boolean mIsGuest;
    private boolean mIsOwner;
    private Preference mMePreference;
    private SelectableEditTextPreference mNicknamePreference;
    private int mRemovingUserId = -1;
    private BroadcastReceiver mUserChangeReceiver;
    private SparseArray<Bitmap> mUserIcons = new SparseArray();
    private PreferenceGroup mUserListCategory;
    private final Object mUserLock = new Object();
    private UserManager mUserManager;

    public UserSettings() {
        boolean z = true;
        if (UserHandle.myUserId() != 0) {
            z = false;
        }
        this.mIsOwner = z;
        this.mEditUserInfoController = new EditUserInfoController();
        this.mHandler = new Handler() {
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case 1:
                        UserSettings.this.updateUserList();
                        return;
                    case 2:
                        UserSettings.this.onUserCreated(msg.arg1);
                        return;
                    case 3:
                        UserSettings.this.onManageUserClicked(msg.arg1, true);
                        return;
                    default:
                        return;
                }
            }
        };
        this.mUserChangeReceiver = new BroadcastReceiver() {
            public void onReceive(Context context, Intent intent) {
                if (intent.getAction().equals("android.intent.action.USER_REMOVED")) {
                    UserSettings.this.mRemovingUserId = -1;
                } else if (intent.getAction().equals("android.intent.action.USER_INFO_CHANGED")) {
                    int userHandle = intent.getIntExtra("android.intent.extra.user_handle", -1);
                    if (userHandle != -1) {
                        UserSettings.this.mUserIcons.remove(userHandle);
                    }
                }
                UserSettings.this.mHandler.sendEmptyMessage(1);
            }
        };
    }

    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        if (icicle != null) {
            if (icicle.containsKey("adding_user")) {
                this.mAddedUserId = icicle.getInt("adding_user");
            }
            if (icicle.containsKey("removing_user")) {
                this.mRemovingUserId = icicle.getInt("removing_user");
            }
            this.mEditUserInfoController.onRestoreInstanceState(icicle);
        }
        Context context = getActivity();
        this.mUserManager = (UserManager) context.getSystemService("user");
        boolean hasMultipleUsers;
        if (this.mUserManager.getUserCount() > 1) {
            hasMultipleUsers = true;
        } else {
            hasMultipleUsers = false;
        }
        if ((UserManager.supportsMultipleUsers() || hasMultipleUsers) && !Utils.isMonkeyRunning()) {
            int myUserId = UserHandle.myUserId();
            this.mIsGuest = this.mUserManager.getUserInfo(myUserId).isGuest();
            addPreferencesFromResource(R.xml.user_settings);
            this.mUserListCategory = (PreferenceGroup) findPreference("user_list");
            this.mMePreference = new UserPreference(context, null, myUserId, null, null);
            this.mMePreference.setKey("user_me");
            this.mMePreference.setOnPreferenceClickListener(this);
            if (this.mIsOwner) {
                this.mMePreference.setSummary(R.string.user_owner);
            }
            this.mAddUser = findPreference("user_add");
            if (!this.mIsOwner || UserManager.getMaxSupportedUsers() < 2 || !UserManager.supportsMultipleUsers() || this.mUserManager.hasUserRestriction("no_add_user")) {
                removePreference("user_add");
            } else {
                this.mAddUser.setOnPreferenceClickListener(this);
                if (((DevicePolicyManager) context.getSystemService("device_policy")).getDeviceOwner() != null || Utils.isVoiceCapable(context)) {
                    this.mCanAddRestrictedProfile = false;
                    this.mAddUser.setTitle(R.string.user_add_user_menu);
                }
            }
            loadProfile();
            setHasOptionsMenu(true);
            IntentFilter filter = new IntentFilter("android.intent.action.USER_REMOVED");
            filter.addAction("android.intent.action.USER_INFO_CHANGED");
            context.registerReceiverAsUser(this.mUserChangeReceiver, UserHandle.ALL, filter, null, this.mHandler);
            return;
        }
        this.mEnabled = false;
    }

    public void onResume() {
        super.onResume();
        if (this.mEnabled) {
            loadProfile();
            updateUserList();
        }
    }

    public void onDestroy() {
        super.onDestroy();
        if (this.mEnabled) {
            getActivity().unregisterReceiver(this.mUserChangeReceiver);
        }
    }

    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        this.mEditUserInfoController.onSaveInstanceState(outState);
        outState.putInt("adding_user", this.mAddedUserId);
        outState.putInt("removing_user", this.mRemovingUserId);
    }

    public void startActivityForResult(Intent intent, int requestCode) {
        this.mEditUserInfoController.startingActivityForResult();
        super.startActivityForResult(intent, requestCode);
    }

    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        boolean z = true;
        int i = 0;
        UserManager um = (UserManager) getActivity().getSystemService("user");
        if (!(this.mIsOwner || um.hasUserRestriction("no_remove_user"))) {
            String nickname = this.mUserManager.getUserName();
            int pos = 0 + 1;
            menu.add(0, 1, 0, getResources().getString(R.string.user_remove_user_menu, new Object[]{nickname})).setShowAsAction(0);
            i = pos;
        }
        if (this.mIsOwner && !um.hasUserRestriction("no_add_user")) {
            pos = i + 1;
            MenuItem allowAddOnLockscreen = menu.add(0, 2, i, R.string.user_add_on_lockscreen_menu);
            allowAddOnLockscreen.setCheckable(true);
            if (Global.getInt(getContentResolver(), "add_users_when_locked", 0) != 1) {
                z = false;
            }
            allowAddOnLockscreen.setChecked(z);
            i = pos;
        }
        super.onCreateOptionsMenu(menu, inflater);
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        boolean z = false;
        int itemId = item.getItemId();
        if (itemId == 1) {
            onRemoveUserClicked(UserHandle.myUserId());
            return true;
        } else if (itemId != 2) {
            return super.onOptionsItemSelected(item);
        } else {
            int i;
            boolean isChecked = item.isChecked();
            ContentResolver contentResolver = getContentResolver();
            String str = "add_users_when_locked";
            if (isChecked) {
                i = 0;
            } else {
                i = 1;
            }
            Global.putInt(contentResolver, str, i);
            if (!isChecked) {
                z = true;
            }
            item.setChecked(z);
            return true;
        }
    }

    private void loadProfile() {
        if (this.mIsGuest) {
            this.mMePreference.setIcon(getEncircledDefaultIcon());
            this.mMePreference.setTitle(R.string.user_exit_guest_title);
            return;
        }
        new AsyncTask<Void, Void, String>() {
            protected void onPostExecute(String result) {
                UserSettings.this.finishLoadProfile(result);
            }

            protected String doInBackground(Void... values) {
                UserInfo user = UserSettings.this.mUserManager.getUserInfo(UserHandle.myUserId());
                if (user.iconPath == null || user.iconPath.equals("")) {
                    UserSettings.this.assignProfilePhoto(user);
                }
                return user.name;
            }
        }.execute(new Void[0]);
    }

    private void finishLoadProfile(String profileName) {
        if (getActivity() != null) {
            this.mMePreference.setTitle(getString(R.string.user_you, new Object[]{profileName}));
            int myUserId = UserHandle.myUserId();
            Bitmap b = this.mUserManager.getUserIcon(myUserId);
            if (b != null) {
                this.mMePreference.setIcon(encircle(b));
                this.mUserIcons.put(myUserId, b);
            }
        }
    }

    private boolean hasLockscreenSecurity() {
        LockPatternUtils lpu = new LockPatternUtils(getActivity());
        return lpu.isLockPasswordEnabled() || lpu.isLockPatternEnabled();
    }

    private void launchChooseLockscreen() {
        Intent chooseLockIntent = new Intent("android.app.action.SET_NEW_PASSWORD");
        chooseLockIntent.putExtra("minimum_quality", 65536);
        startActivityForResult(chooseLockIntent, 10);
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != 10) {
            this.mEditUserInfoController.onActivityResult(requestCode, resultCode, data);
        } else if (resultCode != 0 && hasLockscreenSecurity()) {
            addUserNow(2);
        }
    }

    private void onAddUserClicked(int userType) {
        synchronized (this.mUserLock) {
            if (this.mRemovingUserId == -1 && !this.mAddingUser) {
                switch (userType) {
                    case 1:
                        showDialog(2);
                        break;
                    case 2:
                        if (!hasLockscreenSecurity()) {
                            showDialog(7);
                            break;
                        } else {
                            addUserNow(2);
                            break;
                        }
                }
            }
        }
    }

    private void onRemoveUserClicked(int userId) {
        synchronized (this.mUserLock) {
            if (this.mRemovingUserId == -1 && !this.mAddingUser) {
                this.mRemovingUserId = userId;
                showDialog(1);
            }
        }
    }

    private UserInfo createLimitedUser() {
        UserInfo newUserInfo = this.mUserManager.createSecondaryUser(getResources().getString(R.string.user_new_profile_name), 8);
        int userId = newUserInfo.id;
        UserHandle user = new UserHandle(userId);
        this.mUserManager.setUserRestriction("no_modify_accounts", true, user);
        Secure.putIntForUser(getContentResolver(), "location_mode", 0, userId);
        this.mUserManager.setUserRestriction("no_share_location", true, user);
        assignDefaultPhoto(newUserInfo);
        AccountManager am = AccountManager.get(getActivity());
        Account[] accounts = am.getAccounts();
        if (accounts != null) {
            for (Account account : accounts) {
                am.addSharedAccount(account, user);
            }
        }
        return newUserInfo;
    }

    private UserInfo createTrustedUser() {
        UserInfo newUserInfo = this.mUserManager.createSecondaryUser(getResources().getString(R.string.user_new_user_name), 0);
        if (newUserInfo != null) {
            assignDefaultPhoto(newUserInfo);
        }
        return newUserInfo;
    }

    private void onManageUserClicked(int userId, boolean newUser) {
        if (userId == -11) {
            Bundle extras = new Bundle();
            extras.putBoolean("guest_user", true);
            ((SettingsActivity) getActivity()).startPreferencePanel(UserDetailsSettings.class.getName(), extras, R.string.user_guest, null, null, 0);
            return;
        }
        UserInfo info = this.mUserManager.getUserInfo(userId);
        if (info.isRestricted() && this.mIsOwner) {
            extras = new Bundle();
            extras.putInt("user_id", userId);
            extras.putBoolean("new_user", newUser);
            ((SettingsActivity) getActivity()).startPreferencePanel(RestrictedProfileSettings.class.getName(), extras, R.string.user_restrictions_title, null, null, 0);
        } else if (info.id == UserHandle.myUserId()) {
            extras = new Bundle();
            if (!info.isRestricted()) {
                extras.putBoolean("show_nickname", true);
            }
            int titleResId = info.id == 0 ? R.string.owner_info_settings_title : info.isRestricted() ? R.string.profile_info_settings_title : R.string.user_info_settings_title;
            ((SettingsActivity) getActivity()).startPreferencePanel(OwnerInfoSettings.class.getName(), extras, titleResId, null, null, 0);
        } else if (this.mIsOwner) {
            extras = new Bundle();
            extras.putInt("user_id", userId);
            ((SettingsActivity) getActivity()).startPreferencePanel(UserDetailsSettings.class.getName(), extras, -1, info.name, null, 0);
        }
    }

    private void onUserCreated(int userId) {
        this.mAddedUserId = userId;
        if (this.mUserManager.getUserInfo(userId).isRestricted()) {
            showDialog(4);
        } else {
            showDialog(3);
        }
    }

    public void onDialogShowing() {
        super.onDialogShowing();
        setOnDismissListener(this);
    }

    public Dialog onCreateDialog(int dialogId) {
        Context context = getActivity();
        if (context == null) {
            return null;
        }
        switch (dialogId) {
            case 1:
                return Utils.createRemoveConfirmationDialog(getActivity(), this.mRemovingUserId, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        UserSettings.this.removeUserNow();
                    }
                });
            case 2:
                SharedPreferences preferences = getActivity().getPreferences(0);
                boolean longMessageDisplayed = preferences.getBoolean("key_add_user_long_message_displayed", false);
                int messageResId = longMessageDisplayed ? R.string.user_add_user_message_short : R.string.user_add_user_message_long;
                final int i = dialogId == 2 ? 1 : 2;
                final boolean z = longMessageDisplayed;
                final SharedPreferences sharedPreferences = preferences;
                return new Builder(context).setTitle(R.string.user_add_user_title).setMessage(messageResId).setPositiveButton(17039370, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        UserSettings.this.addUserNow(i);
                        if (!z) {
                            sharedPreferences.edit().putBoolean("key_add_user_long_message_displayed", true).apply();
                        }
                    }
                }).setNegativeButton(17039360, null).create();
            case 3:
                return new Builder(context).setTitle(R.string.user_setup_dialog_title).setMessage(R.string.user_setup_dialog_message).setPositiveButton(R.string.user_setup_button_setup_now, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        UserSettings.this.switchUserNow(UserSettings.this.mAddedUserId);
                    }
                }).setNegativeButton(R.string.user_setup_button_setup_later, null).create();
            case 4:
                return new Builder(context).setMessage(R.string.user_setup_profile_dialog_message).setPositiveButton(17039370, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        UserSettings.this.switchUserNow(UserSettings.this.mAddedUserId);
                    }
                }).setNegativeButton(17039360, null).create();
            case 5:
                return new Builder(context).setMessage(R.string.user_cannot_manage_message).setPositiveButton(17039370, null).create();
            case 6:
                List<HashMap<String, String>> data = new ArrayList();
                HashMap<String, String> addUserItem = new HashMap();
                addUserItem.put("title", getString(R.string.user_add_user_item_title));
                addUserItem.put("summary", getString(R.string.user_add_user_item_summary));
                HashMap<String, String> addProfileItem = new HashMap();
                addProfileItem.put("title", getString(R.string.user_add_profile_item_title));
                addProfileItem.put("summary", getString(R.string.user_add_profile_item_summary));
                data.add(addUserItem);
                data.add(addProfileItem);
                return new Builder(context).setTitle(R.string.user_add_user_type_title).setAdapter(new SimpleAdapter(context, data, R.layout.two_line_list_item, new String[]{"title", "summary"}, new int[]{R.id.title, R.id.summary}), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        UserSettings.this.onAddUserClicked(which == 0 ? 1 : 2);
                    }
                }).create();
            case 7:
                return new Builder(context).setMessage(R.string.user_need_lock_message).setPositiveButton(R.string.user_set_lock_button, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        UserSettings.this.launchChooseLockscreen();
                    }
                }).setNegativeButton(17039360, null).create();
            case 8:
                return new Builder(context).setTitle(R.string.user_exit_guest_confirm_title).setMessage(R.string.user_exit_guest_confirm_message).setPositiveButton(R.string.user_exit_guest_dialog_remove, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        UserSettings.this.exitGuest();
                    }
                }).setNegativeButton(17039360, null).create();
            case 9:
                return this.mEditUserInfoController.createDialog(this, this.mMePreference.getIcon(), this.mMePreference.getTitle(), R.string.profile_info_settings_title, this, Process.myUserHandle());
            default:
                return null;
        }
    }

    private void removeUserNow() {
        if (this.mRemovingUserId == UserHandle.myUserId()) {
            removeThisUser();
        } else {
            new Thread() {
                public void run() {
                    synchronized (UserSettings.this.mUserLock) {
                        UserSettings.this.mUserManager.removeUser(UserSettings.this.mRemovingUserId);
                        UserSettings.this.mHandler.sendEmptyMessage(1);
                    }
                }
            }.start();
        }
    }

    private void removeThisUser() {
        try {
            ActivityManagerNative.getDefault().switchUser(0);
            ((UserManager) getActivity().getSystemService("user")).removeUser(UserHandle.myUserId());
        } catch (RemoteException e) {
            Log.e("UserSettings", "Unable to remove self user");
        }
    }

    private void addUserNow(final int userType) {
        synchronized (this.mUserLock) {
            this.mAddingUser = true;
            new Thread() {
                public void run() {
                    UserInfo user;
                    if (userType == 1) {
                        user = UserSettings.this.createTrustedUser();
                    } else {
                        user = UserSettings.this.createLimitedUser();
                    }
                    synchronized (UserSettings.this.mUserLock) {
                        UserSettings.this.mAddingUser = false;
                        if (userType == 1) {
                            UserSettings.this.mHandler.sendEmptyMessage(1);
                            UserSettings.this.mHandler.sendMessage(UserSettings.this.mHandler.obtainMessage(2, user.id, user.serialNumber));
                        } else {
                            UserSettings.this.mHandler.sendMessage(UserSettings.this.mHandler.obtainMessage(3, user.id, user.serialNumber));
                        }
                    }
                }
            }.start();
        }
    }

    private void switchUserNow(int userId) {
        try {
            ActivityManagerNative.getDefault().switchUser(userId);
        } catch (RemoteException e) {
        }
    }

    private void exitGuest() {
        if (this.mIsGuest) {
            removeThisUser();
        }
    }

    private void updateUserList() {
        if (getActivity() != null) {
            Preference userPreference;
            List<UserInfo> users = this.mUserManager.getUsers(true);
            Context context = getActivity();
            this.mUserListCategory.removeAll();
            this.mUserListCategory.setOrderingAsAdded(false);
            this.mUserListCategory.addPreference(this.mMePreference);
            boolean voiceCapable = Utils.isVoiceCapable(context);
            ArrayList<Integer> missingIcons = new ArrayList();
            for (UserInfo user : users) {
                if (!user.isManagedProfile()) {
                    Preference pref;
                    if (user.id == UserHandle.myUserId()) {
                        pref = this.mMePreference;
                    } else if (!user.isGuest()) {
                        OnClickListener onClickListener;
                        boolean showSettings = this.mIsOwner && (voiceCapable || user.isRestricted());
                        boolean showDelete = (!this.mIsOwner || voiceCapable || user.isRestricted() || user.isGuest()) ? false : true;
                        int i = user.id;
                        OnClickListener onClickListener2 = showSettings ? this : null;
                        if (showDelete) {
                            onClickListener = this;
                        } else {
                            onClickListener = null;
                        }
                        pref = new UserPreference(context, null, i, onClickListener2, onClickListener);
                        pref.setOnPreferenceClickListener(this);
                        pref.setKey("id=" + user.id);
                        this.mUserListCategory.addPreference(pref);
                        if (user.id == 0) {
                            pref.setSummary(R.string.user_owner);
                        }
                        pref.setTitle(user.name);
                    }
                    if (isInitialized(user)) {
                        if (user.isRestricted()) {
                            pref.setSummary(R.string.user_summary_restricted_profile);
                        }
                    } else if (user.isRestricted()) {
                        pref.setSummary(R.string.user_summary_restricted_not_set_up);
                    } else {
                        pref.setSummary(R.string.user_summary_not_set_up);
                    }
                    if (user.iconPath == null) {
                        pref.setIcon(getEncircledDefaultIcon());
                    } else if (this.mUserIcons.get(user.id) == null) {
                        missingIcons.add(Integer.valueOf(user.id));
                        pref.setIcon(getEncircledDefaultIcon());
                    } else {
                        setPhotoId(pref, user);
                    }
                }
            }
            if (this.mAddingUser) {
                userPreference = new UserPreference(getActivity(), null, -10, null, null);
                userPreference.setEnabled(false);
                userPreference.setTitle(R.string.user_new_user_name);
                userPreference.setIcon(getEncircledDefaultIcon());
                this.mUserListCategory.addPreference(userPreference);
            }
            boolean showGuestPreference = !this.mIsGuest;
            if (showGuestPreference && this.mUserManager.hasUserRestriction("no_add_user")) {
                showGuestPreference = false;
                for (UserInfo user2 : users) {
                    if (user2.isGuest()) {
                        showGuestPreference = true;
                        break;
                    }
                }
            }
            if (showGuestPreference) {
                Context activity = getActivity();
                OnClickListener onClickListener3 = (this.mIsOwner && voiceCapable) ? this : null;
                userPreference = new UserPreference(activity, null, -11, onClickListener3, null);
                userPreference.setTitle(R.string.user_guest);
                userPreference.setIcon(getEncircledDefaultIcon());
                userPreference.setOnPreferenceClickListener(this);
                this.mUserListCategory.addPreference(userPreference);
            }
            getActivity().invalidateOptionsMenu();
            if (missingIcons.size() > 0) {
                loadIconsAsync(missingIcons);
            }
            this.mAddUser.setEnabled(this.mUserManager.canAddMoreUsers());
        }
    }

    private void loadIconsAsync(List<Integer> missingIcons) {
        Resources resources = getResources();
        new AsyncTask<List<Integer>, Void, Void>() {
            protected void onPostExecute(Void result) {
                UserSettings.this.updateUserList();
            }

            protected Void doInBackground(List<Integer>... values) {
                for (Integer intValue : values[0]) {
                    int userId = intValue.intValue();
                    Bitmap bitmap = UserSettings.this.mUserManager.getUserIcon(userId);
                    if (bitmap == null) {
                        bitmap = UserIcons.convertToBitmap(UserIcons.getDefaultUserIcon(userId, false));
                    }
                    UserSettings.this.mUserIcons.append(userId, bitmap);
                }
                return null;
            }
        }.execute(new List[]{missingIcons});
    }

    private void assignProfilePhoto(UserInfo user) {
        if (!Utils.copyMeProfilePhoto(getActivity(), user)) {
            assignDefaultPhoto(user);
        }
    }

    private void assignDefaultPhoto(UserInfo user) {
        this.mUserManager.setUserIcon(user.id, UserIcons.convertToBitmap(UserIcons.getDefaultUserIcon(user.id, false)));
    }

    private Drawable getEncircledDefaultIcon() {
        if (this.mDefaultIconDrawable == null) {
            this.mDefaultIconDrawable = encircle(UserIcons.convertToBitmap(UserIcons.getDefaultUserIcon(-10000, false)));
        }
        return this.mDefaultIconDrawable;
    }

    private void setPhotoId(Preference pref, UserInfo user) {
        Bitmap bitmap = (Bitmap) this.mUserIcons.get(user.id);
        if (bitmap != null) {
            pref.setIcon(encircle(bitmap));
        }
    }

    private void setUserName(String name) {
        this.mUserManager.setUserName(UserHandle.myUserId(), name);
        this.mNicknamePreference.setSummary(name);
        getActivity().invalidateOptionsMenu();
    }

    public boolean onPreferenceClick(Preference pref) {
        if (pref == this.mMePreference) {
            if (this.mIsGuest) {
                showDialog(8);
                return true;
            } else if (this.mUserManager.isLinkedUser()) {
                onManageUserClicked(UserHandle.myUserId(), false);
            } else {
                showDialog(9);
            }
        } else if (pref instanceof UserPreference) {
            int userId = ((UserPreference) pref).getUserId();
            if (userId == -11) {
                createAndSwitchToGuestUser();
            } else {
                UserInfo user = this.mUserManager.getUserInfo(userId);
                if (isInitialized(user)) {
                    switchUserNow(userId);
                } else {
                    this.mHandler.sendMessage(this.mHandler.obtainMessage(2, user.id, user.serialNumber));
                }
            }
        } else if (pref == this.mAddUser) {
            if (this.mCanAddRestrictedProfile) {
                showDialog(6);
            } else {
                onAddUserClicked(1);
            }
        }
        return false;
    }

    private void createAndSwitchToGuestUser() {
        for (UserInfo user : this.mUserManager.getUsers()) {
            if (user.isGuest()) {
                switchUserNow(user.id);
                return;
            }
        }
        if (this.mUserManager.hasUserRestriction("no_add_user") || !(this.mIsOwner || Global.getInt(getContentResolver(), "add_users_when_locked", 0) == 1)) {
            Log.i("UserSettings", "Blocking guest creation because it is restricted");
            return;
        }
        UserInfo guestUser = this.mUserManager.createGuest(getActivity(), getResources().getString(R.string.user_guest));
        if (guestUser != null) {
            switchUserNow(guestUser.id);
        }
    }

    private boolean isInitialized(UserInfo user) {
        return (user.flags & 16) != 0;
    }

    private Drawable encircle(Bitmap icon) {
        return CircleFramedDrawable.getInstance(getActivity(), icon);
    }

    public void onClick(View v) {
        if (v.getTag() instanceof UserPreference) {
            int userId = ((UserPreference) v.getTag()).getUserId();
            switch (v.getId()) {
                case R.id.manage_user:
                    onManageUserClicked(userId, false);
                    return;
                case R.id.trash_user:
                    onRemoveUserClicked(userId);
                    return;
                default:
                    return;
            }
        }
    }

    public void onDismiss(DialogInterface dialog) {
        synchronized (this.mUserLock) {
            this.mAddingUser = false;
            this.mRemovingUserId = -1;
            updateUserList();
        }
    }

    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (preference != this.mNicknamePreference) {
            return false;
        }
        String value = (String) newValue;
        if (preference == this.mNicknamePreference && value != null && value.length() > 0) {
            setUserName(value);
        }
        return true;
    }

    public int getHelpResource() {
        return R.string.help_url_users;
    }

    public void onPhotoChanged(Drawable photo) {
        this.mMePreference.setIcon(photo);
    }

    public void onLabelChanged(CharSequence label) {
        this.mMePreference.setTitle(label);
    }
}
