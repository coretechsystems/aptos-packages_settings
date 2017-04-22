package com.android.settings;

import android.app.AlertDialog.Builder;
import android.app.Dialog;
import android.app.backup.IBackupManager;
import android.app.backup.IBackupManager.Stub;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.os.Bundle;
import android.os.Process;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.UserHandle;
import android.os.UserManager;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceScreen;
import android.preference.SwitchPreference;
import android.provider.SearchIndexableResource;
import android.provider.Settings.Secure;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settings.search.Indexable.SearchIndexProvider;
import java.util.ArrayList;
import java.util.List;

public class PrivacySettings extends SettingsPreferenceFragment implements OnClickListener {
    public static final SearchIndexProvider SEARCH_INDEX_DATA_PROVIDER = new PrivacySearchIndexProvider();
    private SwitchPreference mAutoRestore;
    private SwitchPreference mBackup;
    private IBackupManager mBackupManager;
    private PreferenceScreen mConfigure;
    private Dialog mConfirmDialog;
    private int mDialogType;
    private boolean mEnabled;
    private OnPreferenceChangeListener preferenceChangeListener = new OnPreferenceChangeListener() {
        public boolean onPreferenceChange(Preference preference, Object newValue) {
            boolean z = true;
            if (!(preference instanceof SwitchPreference)) {
                return true;
            }
            boolean nextValue = ((Boolean) newValue).booleanValue();
            boolean result = false;
            if (preference == PrivacySettings.this.mBackup) {
                if (nextValue) {
                    PrivacySettings.this.setBackupEnabled(true);
                    result = true;
                } else {
                    PrivacySettings.this.showEraseBackupDialog();
                }
            } else if (preference == PrivacySettings.this.mAutoRestore) {
                try {
                    PrivacySettings.this.mBackupManager.setAutoRestore(nextValue);
                    result = true;
                } catch (RemoteException e) {
                    SwitchPreference access$300 = PrivacySettings.this.mAutoRestore;
                    if (nextValue) {
                        z = false;
                    }
                    access$300.setChecked(z);
                }
            }
            return result;
        }
    };

    private static class PrivacySearchIndexProvider extends BaseSearchIndexProvider {
        boolean mIsPrimary;

        public PrivacySearchIndexProvider() {
            this.mIsPrimary = UserHandle.myUserId() == 0;
        }

        public List<SearchIndexableResource> getXmlResourcesToIndex(Context context, boolean enabled) {
            List<SearchIndexableResource> result = new ArrayList();
            if (this.mIsPrimary) {
                SearchIndexableResource sir = new SearchIndexableResource(context);
                sir.xmlResId = R.xml.privacy_settings;
                result.add(sir);
            }
            return result;
        }
    }

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.mEnabled = Process.myUserHandle().isOwner();
        if (this.mEnabled) {
            addPreferencesFromResource(R.xml.privacy_settings);
            PreferenceScreen screen = getPreferenceScreen();
            this.mBackupManager = Stub.asInterface(ServiceManager.getService("backup"));
            this.mBackup = (SwitchPreference) screen.findPreference("backup_data");
            this.mBackup.setOnPreferenceChangeListener(this.preferenceChangeListener);
            this.mAutoRestore = (SwitchPreference) screen.findPreference("auto_restore");
            this.mAutoRestore.setOnPreferenceChangeListener(this.preferenceChangeListener);
            this.mConfigure = (PreferenceScreen) screen.findPreference("configure_account");
            if (UserManager.get(getActivity()).hasUserRestriction("no_factory_reset")) {
                screen.removePreference(findPreference("personal_data_category"));
            }
            if (getActivity().getPackageManager().resolveContentProvider("com.google.settings", 0) == null) {
                screen.removePreference(findPreference("backup_category"));
            }
            updateToggles();
        }
    }

    public void onResume() {
        super.onResume();
        if (this.mEnabled) {
            updateToggles();
        }
    }

    public void onStop() {
        if (this.mConfirmDialog != null && this.mConfirmDialog.isShowing()) {
            this.mConfirmDialog.dismiss();
        }
        this.mConfirmDialog = null;
        this.mDialogType = 0;
        super.onStop();
    }

    private void showEraseBackupDialog() {
        this.mDialogType = 2;
        this.mConfirmDialog = new Builder(getActivity()).setMessage(getResources().getText(R.string.backup_erase_dialog_message)).setTitle(R.string.backup_erase_dialog_title).setPositiveButton(17039370, this).setNegativeButton(17039360, this).show();
    }

    private void updateToggles() {
        boolean z;
        boolean configureEnabled;
        ContentResolver res = getContentResolver();
        boolean backupEnabled = false;
        Intent configIntent = null;
        String configSummary = null;
        try {
            backupEnabled = this.mBackupManager.isBackupEnabled();
            String transport = this.mBackupManager.getCurrentTransport();
            configIntent = this.mBackupManager.getConfigurationIntent(transport);
            configSummary = this.mBackupManager.getDestinationString(transport);
        } catch (RemoteException e) {
            this.mBackup.setEnabled(false);
        }
        this.mBackup.setChecked(backupEnabled);
        SwitchPreference switchPreference = this.mAutoRestore;
        if (Secure.getInt(res, "backup_auto_restore", 1) == 1) {
            z = true;
        } else {
            z = false;
        }
        switchPreference.setChecked(z);
        this.mAutoRestore.setEnabled(backupEnabled);
        if (configIntent == null || !backupEnabled) {
            configureEnabled = false;
        } else {
            configureEnabled = true;
        }
        this.mConfigure.setEnabled(configureEnabled);
        this.mConfigure.setIntent(configIntent);
        setConfigureSummary(configSummary);
    }

    private void setConfigureSummary(String summary) {
        if (summary != null) {
            this.mConfigure.setSummary(summary);
        } else {
            this.mConfigure.setSummary(R.string.backup_configure_account_default_summary);
        }
    }

    private void updateConfigureSummary() {
        try {
            setConfigureSummary(this.mBackupManager.getDestinationString(this.mBackupManager.getCurrentTransport()));
        } catch (RemoteException e) {
        }
    }

    public void onClick(DialogInterface dialog, int which) {
        if (this.mDialogType == 2) {
            if (which == -1) {
                setBackupEnabled(false);
            } else if (which == -2) {
                setBackupEnabled(true);
            }
            updateConfigureSummary();
        }
        this.mDialogType = 0;
    }

    private void setBackupEnabled(boolean enable) {
        boolean z = true;
        if (this.mBackupManager != null) {
            try {
                this.mBackupManager.setBackupEnabled(enable);
            } catch (RemoteException e) {
                boolean z2;
                SwitchPreference switchPreference = this.mBackup;
                if (enable) {
                    z2 = false;
                } else {
                    z2 = true;
                }
                switchPreference.setChecked(z2);
                SwitchPreference switchPreference2 = this.mAutoRestore;
                if (enable) {
                    z = false;
                }
                switchPreference2.setEnabled(z);
                return;
            }
        }
        this.mBackup.setChecked(enable);
        this.mAutoRestore.setEnabled(enable);
        this.mConfigure.setEnabled(enable);
    }

    protected int getHelpResource() {
        return R.string.help_url_backup_reset;
    }
}
