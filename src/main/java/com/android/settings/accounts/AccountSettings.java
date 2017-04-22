package com.android.settings.accounts;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.ActivityManager;
import android.app.AlertDialog.Builder;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.UserInfo;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Process;
import android.os.UserHandle;
import android.os.UserManager;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceCategory;
import android.preference.PreferenceGroup;
import android.preference.PreferenceScreen;
import android.util.Log;
import android.util.SparseArray;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MenuItem.OnMenuItemClickListener;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.Utils;
import com.android.settings.accounts.AuthenticatorHelper.OnAccountsUpdateListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class AccountSettings extends SettingsPreferenceFragment implements OnPreferenceClickListener, OnAccountsUpdateListener {
    private String[] mAuthorities;
    private int mAuthoritiesCount = 0;
    private ManagedProfileBroadcastReceiver mManagedProfileBroadcastReceiver = new ManagedProfileBroadcastReceiver();
    private Preference mProfileNotAvailablePreference;
    private SparseArray<ProfileData> mProfiles = new SparseArray();
    private UserManager mUm;

    private class AccountPreference extends Preference implements OnPreferenceClickListener {
        private final String mFragment;
        private final Bundle mFragmentArguments;
        private final CharSequence mTitle;

        public AccountPreference(Context context, CharSequence title, String fragment, Bundle fragmentArguments, Drawable icon) {
            super(context);
            this.mTitle = title;
            this.mFragment = fragment;
            this.mFragmentArguments = fragmentArguments;
            setWidgetLayoutResource(R.layout.account_type_preference);
            setTitle(title);
            setIcon(icon);
            setOnPreferenceClickListener(this);
        }

        public boolean onPreferenceClick(Preference preference) {
            if (this.mFragment == null) {
                return false;
            }
            Utils.startWithFragment(getContext(), this.mFragment, this.mFragmentArguments, null, 0, 0, this.mTitle);
            return true;
        }
    }

    public static class ConfirmAutoSyncChangeFragment extends DialogFragment {
        private boolean mEnabling;
        private UserHandle mUserHandle;

        public static void show(AccountSettings parent, boolean enabling, UserHandle userHandle) {
            if (parent.isAdded()) {
                ConfirmAutoSyncChangeFragment dialog = new ConfirmAutoSyncChangeFragment();
                dialog.mEnabling = enabling;
                dialog.mUserHandle = userHandle;
                dialog.setTargetFragment(parent, 0);
                dialog.show(parent.getFragmentManager(), "confirmAutoSyncChange");
            }
        }

        public Dialog onCreateDialog(Bundle savedInstanceState) {
            Context context = getActivity();
            if (savedInstanceState != null) {
                this.mEnabling = savedInstanceState.getBoolean("enabling");
            }
            Builder builder = new Builder(context);
            if (this.mEnabling) {
                builder.setTitle(R.string.data_usage_auto_sync_on_dialog_title);
                builder.setMessage(R.string.data_usage_auto_sync_on_dialog);
            } else {
                builder.setTitle(R.string.data_usage_auto_sync_off_dialog_title);
                builder.setMessage(R.string.data_usage_auto_sync_off_dialog);
            }
            builder.setPositiveButton(17039370, new OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    ContentResolver.setMasterSyncAutomaticallyAsUser(ConfirmAutoSyncChangeFragment.this.mEnabling, ConfirmAutoSyncChangeFragment.this.mUserHandle.getIdentifier());
                }
            });
            builder.setNegativeButton(17039360, null);
            return builder.create();
        }

        public void onSaveInstanceState(Bundle outState) {
            super.onSaveInstanceState(outState);
            outState.putBoolean("enabling", this.mEnabling);
        }
    }

    private class ManagedProfileBroadcastReceiver extends BroadcastReceiver {
        private boolean listeningToManagedProfileEvents;

        private ManagedProfileBroadcastReceiver() {
        }

        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals("android.intent.action.MANAGED_PROFILE_REMOVED") || intent.getAction().equals("android.intent.action.MANAGED_PROFILE_ADDED")) {
                Log.v("AccountSettings", "Received broadcast: " + intent.getAction());
                AccountSettings.this.stopListeningToAccountUpdates();
                AccountSettings.this.cleanUpPreferences();
                AccountSettings.this.updateUi();
                AccountSettings.this.listenToAccountUpdates();
                AccountSettings.this.getActivity().invalidateOptionsMenu();
                return;
            }
            Log.w("AccountSettings", "Cannot handle received broadcast: " + intent.getAction());
        }

        public void register(Context context) {
            if (!this.listeningToManagedProfileEvents) {
                IntentFilter intentFilter = new IntentFilter();
                intentFilter.addAction("android.intent.action.MANAGED_PROFILE_REMOVED");
                intentFilter.addAction("android.intent.action.MANAGED_PROFILE_ADDED");
                context.registerReceiver(this, intentFilter);
                this.listeningToManagedProfileEvents = true;
            }
        }

        public void unregister(Context context) {
            if (this.listeningToManagedProfileEvents) {
                context.unregisterReceiver(this);
                this.listeningToManagedProfileEvents = false;
            }
        }
    }

    private class MasterSyncStateClickListener implements OnMenuItemClickListener {
        private final UserHandle mUserHandle;

        public MasterSyncStateClickListener(UserHandle userHandle) {
            this.mUserHandle = userHandle;
        }

        public boolean onMenuItemClick(MenuItem item) {
            if (ActivityManager.isUserAMonkey()) {
                Log.d("AccountSettings", "ignoring monkey's attempt to flip sync state");
            } else {
                ConfirmAutoSyncChangeFragment.show(AccountSettings.this, !item.isChecked(), this.mUserHandle);
            }
            return true;
        }
    }

    private static class ProfileData {
        public Preference addAccountPreference;
        public AuthenticatorHelper authenticatorHelper;
        public PreferenceGroup preferenceGroup;
        public Preference removeWorkProfilePreference;
        public UserInfo userInfo;

        private ProfileData() {
        }
    }

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.mUm = (UserManager) getSystemService("user");
        this.mProfileNotAvailablePreference = new Preference(getActivity());
        this.mAuthorities = getActivity().getIntent().getStringArrayExtra("authorities");
        if (this.mAuthorities != null) {
            this.mAuthoritiesCount = this.mAuthorities.length;
        }
        setHasOptionsMenu(true);
    }

    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.account_settings, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    public void onPrepareOptionsMenu(Menu menu) {
        UserHandle currentProfile = Process.myUserHandle();
        if (this.mProfiles.size() == 1) {
            menu.findItem(R.id.account_settings_menu_auto_sync).setVisible(true).setOnMenuItemClickListener(new MasterSyncStateClickListener(currentProfile)).setChecked(ContentResolver.getMasterSyncAutomaticallyAsUser(currentProfile.getIdentifier()));
            menu.findItem(R.id.account_settings_menu_auto_sync_personal).setVisible(false);
            menu.findItem(R.id.account_settings_menu_auto_sync_work).setVisible(false);
        } else if (this.mProfiles.size() > 1) {
            UserHandle managedProfile = ((ProfileData) this.mProfiles.valueAt(1)).userInfo.getUserHandle();
            menu.findItem(R.id.account_settings_menu_auto_sync_personal).setVisible(true).setOnMenuItemClickListener(new MasterSyncStateClickListener(currentProfile)).setChecked(ContentResolver.getMasterSyncAutomaticallyAsUser(currentProfile.getIdentifier()));
            menu.findItem(R.id.account_settings_menu_auto_sync_work).setVisible(true).setOnMenuItemClickListener(new MasterSyncStateClickListener(managedProfile)).setChecked(ContentResolver.getMasterSyncAutomaticallyAsUser(managedProfile.getIdentifier()));
            menu.findItem(R.id.account_settings_menu_auto_sync).setVisible(false);
        } else {
            Log.w("AccountSettings", "Method onPrepareOptionsMenu called before mProfiles was initialized");
        }
    }

    public void onResume() {
        super.onResume();
        updateUi();
        this.mManagedProfileBroadcastReceiver.register(getActivity());
        listenToAccountUpdates();
    }

    public void onPause() {
        super.onPause();
        stopListeningToAccountUpdates();
        this.mManagedProfileBroadcastReceiver.unregister(getActivity());
        cleanUpPreferences();
    }

    public void onAccountsUpdate(UserHandle userHandle) {
        ProfileData profileData = (ProfileData) this.mProfiles.get(userHandle.getIdentifier());
        if (profileData != null) {
            updateAccountTypes(profileData);
        } else {
            Log.w("AccountSettings", "Missing Settings screen for: " + userHandle.getIdentifier());
        }
    }

    public boolean onPreferenceClick(Preference preference) {
        int count = this.mProfiles.size();
        int i = 0;
        while (i < count) {
            ProfileData profileData = (ProfileData) this.mProfiles.valueAt(i);
            if (preference == profileData.addAccountPreference) {
                Intent intent = new Intent("android.settings.ADD_ACCOUNT_SETTINGS");
                intent.putExtra("android.intent.extra.USER", profileData.userInfo.getUserHandle());
                intent.putExtra("authorities", this.mAuthorities);
                startActivity(intent);
                return true;
            } else if (preference == profileData.removeWorkProfilePreference) {
                final int userId = profileData.userInfo.id;
                Utils.createRemoveConfirmationDialog(getActivity(), userId, new OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        AccountSettings.this.mUm.removeUser(userId);
                    }
                }).show();
                return true;
            } else {
                i++;
            }
        }
        return false;
    }

    void updateUi() {
        boolean addCategory = true;
        addPreferencesFromResource(R.xml.account_settings);
        if (Utils.isManagedProfile(this.mUm)) {
            Log.e("AccountSettings", "We should not be showing settings for a managed profile");
            finish();
            return;
        }
        int profilesCount;
        int i;
        PreferenceScreen preferenceScreen = (PreferenceScreen) findPreference("account");
        if (this.mUm.isLinkedUser()) {
            updateProfileUi(this.mUm.getUserInfo(UserHandle.myUserId()), false, preferenceScreen);
        } else {
            List<UserInfo> profiles = this.mUm.getProfiles(UserHandle.myUserId());
            profilesCount = profiles.size();
            if (profilesCount <= 1) {
                addCategory = false;
            }
            for (i = 0; i < profilesCount; i++) {
                updateProfileUi((UserInfo) profiles.get(i), addCategory, preferenceScreen);
            }
        }
        profilesCount = this.mProfiles.size();
        for (i = 0; i < profilesCount; i++) {
            ProfileData profileData = (ProfileData) this.mProfiles.valueAt(i);
            if (!profileData.preferenceGroup.equals(preferenceScreen)) {
                preferenceScreen.addPreference(profileData.preferenceGroup);
            }
            updateAccountTypes(profileData);
        }
    }

    private void updateProfileUi(UserInfo userInfo, boolean addCategory, PreferenceScreen parent) {
        Context context = getActivity();
        ProfileData profileData = new ProfileData();
        profileData.userInfo = userInfo;
        if (addCategory) {
            profileData.preferenceGroup = new PreferenceCategory(context);
            profileData.preferenceGroup.setTitle(userInfo.isManagedProfile() ? R.string.category_work : R.string.category_personal);
            parent.addPreference(profileData.preferenceGroup);
        } else {
            profileData.preferenceGroup = parent;
        }
        if (userInfo.isEnabled()) {
            profileData.authenticatorHelper = new AuthenticatorHelper(context, userInfo.getUserHandle(), this.mUm, this);
            if (!this.mUm.hasUserRestriction("no_modify_accounts", userInfo.getUserHandle())) {
                profileData.addAccountPreference = newAddAccountPreference(context);
            }
        }
        if (userInfo.isManagedProfile()) {
            profileData.removeWorkProfilePreference = newRemoveWorkProfilePreference(context);
        }
        this.mProfiles.put(userInfo.id, profileData);
    }

    private Preference newAddAccountPreference(Context context) {
        Preference preference = new Preference(context);
        preference.setTitle(R.string.add_account_label);
        preference.setIcon(R.drawable.ic_menu_add_dark);
        preference.setOnPreferenceClickListener(this);
        preference.setOrder(1000);
        return preference;
    }

    private Preference newRemoveWorkProfilePreference(Context context) {
        Preference preference = new Preference(context);
        preference.setTitle(R.string.remove_managed_profile_label);
        preference.setIcon(R.drawable.ic_menu_delete);
        preference.setOnPreferenceClickListener(this);
        preference.setOrder(1001);
        return preference;
    }

    private void cleanUpPreferences() {
        PreferenceScreen preferenceScreen = getPreferenceScreen();
        if (preferenceScreen != null) {
            preferenceScreen.removeAll();
        }
        this.mProfiles.clear();
    }

    private void listenToAccountUpdates() {
        int count = this.mProfiles.size();
        for (int i = 0; i < count; i++) {
            AuthenticatorHelper authenticatorHelper = ((ProfileData) this.mProfiles.valueAt(i)).authenticatorHelper;
            if (authenticatorHelper != null) {
                authenticatorHelper.listenToAccountUpdates();
            }
        }
    }

    private void stopListeningToAccountUpdates() {
        int count = this.mProfiles.size();
        for (int i = 0; i < count; i++) {
            AuthenticatorHelper authenticatorHelper = ((ProfileData) this.mProfiles.valueAt(i)).authenticatorHelper;
            if (authenticatorHelper != null) {
                authenticatorHelper.stopListeningToAccountUpdates();
            }
        }
    }

    private void updateAccountTypes(ProfileData profileData) {
        profileData.preferenceGroup.removeAll();
        if (profileData.userInfo.isEnabled()) {
            ArrayList<AccountPreference> preferences = getAccountTypePreferences(profileData.authenticatorHelper, profileData.userInfo.getUserHandle());
            int count = preferences.size();
            for (int i = 0; i < count; i++) {
                profileData.preferenceGroup.addPreference((Preference) preferences.get(i));
            }
            if (profileData.addAccountPreference != null) {
                profileData.preferenceGroup.addPreference(profileData.addAccountPreference);
            }
        } else {
            this.mProfileNotAvailablePreference.setEnabled(false);
            this.mProfileNotAvailablePreference.setIcon(R.drawable.empty_icon);
            this.mProfileNotAvailablePreference.setTitle(null);
            this.mProfileNotAvailablePreference.setSummary(R.string.managed_profile_not_available_label);
            profileData.preferenceGroup.addPreference(this.mProfileNotAvailablePreference);
        }
        if (profileData.removeWorkProfilePreference != null) {
            profileData.preferenceGroup.addPreference(profileData.removeWorkProfilePreference);
        }
    }

    private ArrayList<AccountPreference> getAccountTypePreferences(AuthenticatorHelper helper, UserHandle userHandle) {
        String[] accountTypes = helper.getEnabledAccountTypes();
        ArrayList<AccountPreference> accountTypePreferences = new ArrayList(accountTypes.length);
        for (String accountType : accountTypes) {
            if (accountTypeHasAnyRequestedAuthorities(helper, accountType)) {
                CharSequence label = helper.getLabelForType(getActivity(), accountType);
                if (label != null) {
                    Account[] accounts = AccountManager.get(getActivity()).getAccountsByTypeAsUser(accountType, userHandle);
                    boolean skipToAccount = accounts.length == 1 && !helper.hasAccountPreferences(accountType);
                    Bundle fragmentArguments;
                    if (skipToAccount) {
                        fragmentArguments = new Bundle();
                        fragmentArguments.putParcelable("account", accounts[0]);
                        fragmentArguments.putParcelable("android.intent.extra.USER", userHandle);
                        accountTypePreferences.add(new AccountPreference(getActivity(), label, AccountSyncSettings.class.getName(), fragmentArguments, helper.getDrawableForType(getActivity(), accountType)));
                    } else {
                        fragmentArguments = new Bundle();
                        fragmentArguments.putString("account_type", accountType);
                        fragmentArguments.putString("account_label", label.toString());
                        fragmentArguments.putParcelable("android.intent.extra.USER", userHandle);
                        accountTypePreferences.add(new AccountPreference(getActivity(), label, ManageAccountsSettings.class.getName(), fragmentArguments, helper.getDrawableForType(getActivity(), accountType)));
                    }
                    helper.preloadDrawableForType(getActivity(), accountType);
                }
            }
        }
        Collections.sort(accountTypePreferences, new Comparator<AccountPreference>() {
            public int compare(AccountPreference t1, AccountPreference t2) {
                return t1.mTitle.toString().compareTo(t2.mTitle.toString());
            }
        });
        return accountTypePreferences;
    }

    private boolean accountTypeHasAnyRequestedAuthorities(AuthenticatorHelper helper, String accountType) {
        if (this.mAuthoritiesCount == 0) {
            return true;
        }
        ArrayList<String> authoritiesForType = helper.getAuthoritiesForAccountType(accountType);
        if (authoritiesForType == null) {
            Log.d("AccountSettings", "No sync authorities for account type: " + accountType);
            return false;
        }
        for (int j = 0; j < this.mAuthoritiesCount; j++) {
            if (authoritiesForType.contains(this.mAuthorities[j])) {
                return true;
            }
        }
        return false;
    }
}
