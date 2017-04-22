package com.android.settings;

import android.app.ActivityThread;
import android.app.AlertDialog.Builder;
import android.app.AppOpsManager;
import android.app.AppOpsManager.OpEntry;
import android.app.AppOpsManager.PackageOps;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.pm.IPackageManager;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Looper;
import android.os.RemoteException;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceScreen;
import android.preference.SwitchPreference;
import android.util.ArrayMap;
import android.util.Log;
import com.android.internal.content.PackageMonitor;
import java.util.List;

public class UsageAccessSettings extends SettingsPreferenceFragment implements OnPreferenceChangeListener {
    private static final int[] APP_OPS_OP_CODES = new int[]{43};
    private static final String[] PM_USAGE_STATS_PERMISSION = new String[]{"android.permission.PACKAGE_USAGE_STATS"};
    AppOpsManager mAppOpsManager;
    private AppsRequestingAccessFetcher mLastFetcherTask;
    ArrayMap<String, PackageEntry> mPackageEntryMap = new ArrayMap();
    private final PackageMonitor mPackageMonitor = new PackageMonitor() {
        public void onPackageAdded(String packageName, int uid) {
            UsageAccessSettings.this.updateInterestedApps();
        }

        public void onPackageRemoved(String packageName, int uid) {
            UsageAccessSettings.this.updateInterestedApps();
        }
    };
    PreferenceScreen mPreferenceScreen;

    private class AppsRequestingAccessFetcher extends AsyncTask<Void, Void, ArrayMap<String, PackageEntry>> {
        private final Context mContext;
        private final IPackageManager mIPackageManager = ActivityThread.getPackageManager();
        private final PackageManager mPackageManager;

        public AppsRequestingAccessFetcher(Context context) {
            this.mContext = context;
            this.mPackageManager = context.getPackageManager();
        }

        protected ArrayMap<String, PackageEntry> doInBackground(Void... params) {
            try {
                String[] packages = this.mIPackageManager.getAppOpPermissionPackages("android.permission.PACKAGE_USAGE_STATS");
                if (packages == null) {
                    return null;
                }
                int i;
                PackageEntry pe;
                ArrayMap<String, PackageEntry> entries = new ArrayMap();
                for (String packageName : packages) {
                    if (!UsageAccessSettings.shouldIgnorePackage(packageName)) {
                        entries.put(packageName, new PackageEntry(packageName));
                    }
                }
                List<PackageInfo> packageInfos = this.mPackageManager.getPackagesHoldingPermissions(UsageAccessSettings.PM_USAGE_STATS_PERMISSION, 0);
                int packageInfoCount = packageInfos != null ? packageInfos.size() : 0;
                for (i = 0; i < packageInfoCount; i++) {
                    PackageInfo packageInfo = (PackageInfo) packageInfos.get(i);
                    pe = (PackageEntry) entries.get(packageInfo.packageName);
                    if (pe != null) {
                        pe.packageInfo = packageInfo;
                        pe.permissionGranted = true;
                    }
                }
                int packageCount = entries.size();
                i = 0;
                while (i < packageCount) {
                    pe = (PackageEntry) entries.valueAt(i);
                    if (pe.packageInfo == null) {
                        try {
                            pe.packageInfo = this.mPackageManager.getPackageInfo(pe.packageName, 0);
                        } catch (NameNotFoundException e) {
                            entries.removeAt(i);
                            i--;
                            packageCount--;
                        }
                    }
                    i++;
                }
                List<PackageOps> packageOps = UsageAccessSettings.this.mAppOpsManager.getPackagesForOps(UsageAccessSettings.APP_OPS_OP_CODES);
                int packageOpsCount = packageOps != null ? packageOps.size() : 0;
                for (i = 0; i < packageOpsCount; i++) {
                    PackageOps packageOp = (PackageOps) packageOps.get(i);
                    pe = (PackageEntry) entries.get(packageOp.getPackageName());
                    if (pe == null) {
                        Log.w("UsageAccessSettings", "AppOp permission exists for package " + packageOp.getPackageName() + " but package doesn't exist or did not request UsageStats access");
                    } else {
                        if (packageOp.getUid() == pe.packageInfo.applicationInfo.uid) {
                            if (packageOp.getOps().size() < 1) {
                                Log.w("UsageAccessSettings", "No AppOps permission exists for package " + packageOp.getPackageName());
                            } else {
                                pe.appOpMode = ((OpEntry) packageOp.getOps().get(0)).getMode();
                            }
                        }
                    }
                }
                return entries;
            } catch (RemoteException e2) {
                Log.w("UsageAccessSettings", "PackageManager is dead. Can't get list of packages requesting android.permission.PACKAGE_USAGE_STATS");
                return null;
            }
        }

        protected void onPostExecute(ArrayMap<String, PackageEntry> newEntries) {
            UsageAccessSettings.this.mLastFetcherTask = null;
            if (UsageAccessSettings.this.getActivity() != null) {
                if (newEntries == null) {
                    UsageAccessSettings.this.mPackageEntryMap.clear();
                    UsageAccessSettings.this.mPreferenceScreen.removeAll();
                    return;
                }
                int i;
                int oldPackageCount = UsageAccessSettings.this.mPackageEntryMap.size();
                for (i = 0; i < oldPackageCount; i++) {
                    PackageEntry oldPackageEntry = (PackageEntry) UsageAccessSettings.this.mPackageEntryMap.valueAt(i);
                    PackageEntry newPackageEntry = (PackageEntry) newEntries.get(oldPackageEntry.packageName);
                    if (newPackageEntry == null) {
                        UsageAccessSettings.this.mPreferenceScreen.removePreference(oldPackageEntry.preference);
                    } else {
                        newPackageEntry.preference = oldPackageEntry.preference;
                    }
                }
                int packageCount = newEntries.size();
                for (i = 0; i < packageCount; i++) {
                    PackageEntry packageEntry = (PackageEntry) newEntries.valueAt(i);
                    if (packageEntry.preference == null) {
                        packageEntry.preference = new SwitchPreference(this.mContext);
                        packageEntry.preference.setPersistent(false);
                        packageEntry.preference.setOnPreferenceChangeListener(UsageAccessSettings.this);
                        UsageAccessSettings.this.mPreferenceScreen.addPreference(packageEntry.preference);
                    }
                    updatePreference(packageEntry);
                }
                UsageAccessSettings.this.mPackageEntryMap.clear();
                UsageAccessSettings.this.mPackageEntryMap = newEntries;
            }
        }

        private void updatePreference(PackageEntry pe) {
            pe.preference.setIcon(pe.packageInfo.applicationInfo.loadIcon(this.mPackageManager));
            pe.preference.setTitle(pe.packageInfo.applicationInfo.loadLabel(this.mPackageManager));
            pe.preference.setKey(pe.packageName);
            boolean check = false;
            if (pe.appOpMode == 0) {
                check = true;
            } else if (pe.appOpMode == 3) {
                check = pe.permissionGranted;
            }
            if (check != pe.preference.isChecked()) {
                pe.preference.setChecked(check);
            }
        }
    }

    private static class PackageEntry {
        int appOpMode = 3;
        PackageInfo packageInfo;
        final String packageName;
        boolean permissionGranted;
        SwitchPreference preference;

        public PackageEntry(String packageName) {
            this.packageName = packageName;
        }
    }

    public static class WarningDialogFragment extends DialogFragment implements OnClickListener {
        public static WarningDialogFragment newInstance(String packageName) {
            WarningDialogFragment dialog = new WarningDialogFragment();
            Bundle args = new Bundle();
            args.putString("package", packageName);
            dialog.setArguments(args);
            return dialog;
        }

        public Dialog onCreateDialog(Bundle savedInstanceState) {
            return new Builder(getActivity()).setTitle(R.string.allow_usage_access_title).setMessage(R.string.allow_usage_access_message).setIconAttribute(16843605).setNegativeButton(R.string.cancel, this).setPositiveButton(17039370, this).create();
        }

        public void onClick(DialogInterface dialog, int which) {
            if (which == -1) {
                ((UsageAccessSettings) getParentFragment()).allowAccess(getArguments().getString("package"));
            } else {
                dialog.cancel();
            }
        }
    }

    static boolean shouldIgnorePackage(String packageName) {
        return packageName.equals("android") || packageName.equals("com.android.settings");
    }

    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        addPreferencesFromResource(R.xml.usage_access_settings);
        this.mPreferenceScreen = getPreferenceScreen();
        this.mPreferenceScreen.setOrderingAsAdded(false);
        this.mAppOpsManager = (AppOpsManager) getSystemService("appops");
    }

    public void onResume() {
        super.onResume();
        updateInterestedApps();
        this.mPackageMonitor.register(getActivity(), Looper.getMainLooper(), false);
    }

    public void onPause() {
        super.onPause();
        this.mPackageMonitor.unregister();
        if (this.mLastFetcherTask != null) {
            this.mLastFetcherTask.cancel(true);
            this.mLastFetcherTask = null;
        }
    }

    private void updateInterestedApps() {
        if (this.mLastFetcherTask != null) {
            this.mLastFetcherTask.cancel(true);
        }
        this.mLastFetcherTask = new AppsRequestingAccessFetcher(getActivity());
        this.mLastFetcherTask.execute(new Void[0]);
    }

    public boolean onPreferenceChange(Preference preference, Object newValue) {
        String packageName = preference.getKey();
        PackageEntry pe = (PackageEntry) this.mPackageEntryMap.get(packageName);
        if (pe == null) {
            Log.w("UsageAccessSettings", "Preference change event for package " + packageName + " but that package is no longer valid.");
            return false;
        } else if (newValue instanceof Boolean) {
            int newMode;
            if (((Boolean) newValue).booleanValue()) {
                newMode = 0;
            } else {
                newMode = 1;
            }
            if (pe.appOpMode == newMode) {
                return true;
            }
            if (newMode != 0) {
                setNewMode(pe, newMode);
                return true;
            }
            FragmentTransaction ft = getChildFragmentManager().beginTransaction();
            Fragment prev = getChildFragmentManager().findFragmentByTag("warning");
            if (prev != null) {
                ft.remove(prev);
            }
            WarningDialogFragment.newInstance(pe.packageName).show(ft, "warning");
            return false;
        } else {
            Log.w("UsageAccessSettings", "Preference change event for package " + packageName + " had non boolean value of type " + newValue.getClass().getName());
            return false;
        }
    }

    void setNewMode(PackageEntry pe, int newMode) {
        this.mAppOpsManager.setMode(43, pe.packageInfo.applicationInfo.uid, pe.packageName, newMode);
        pe.appOpMode = newMode;
    }

    void allowAccess(String packageName) {
        PackageEntry entry = (PackageEntry) this.mPackageEntryMap.get(packageName);
        if (entry == null) {
            Log.w("UsageAccessSettings", "Unable to give access to package " + packageName + ": it does not exist.");
            return;
        }
        setNewMode(entry, 0);
        entry.preference.setChecked(true);
    }
}
