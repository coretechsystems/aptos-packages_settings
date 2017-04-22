package com.android.settings.location;

import android.app.AppGlobals;
import android.app.AppOpsManager;
import android.app.AppOpsManager.OpEntry;
import android.app.AppOpsManager.PackageOps;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.UserHandle;
import android.os.UserManager;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import com.android.settings.R;
import com.android.settings.SettingsActivity;
import com.android.settings.applications.InstalledAppDetails;
import java.util.ArrayList;
import java.util.List;

public class RecentLocationApps {
    private static final String TAG = RecentLocationApps.class.getSimpleName();
    private final SettingsActivity mActivity;
    private final PackageManager mPackageManager;

    private static class AccessiblePreference extends DimmableIconPreference {
        public CharSequence mContentDescription;

        public AccessiblePreference(Context context, CharSequence contentDescription) {
            super(context);
            this.mContentDescription = contentDescription;
        }

        protected void onBindView(View view) {
            super.onBindView(view);
            if (this.mContentDescription != null) {
                ((TextView) view.findViewById(16908310)).setContentDescription(this.mContentDescription);
            }
        }
    }

    private class PackageEntryClickedListener implements OnPreferenceClickListener {
        private String mPackage;

        public PackageEntryClickedListener(String packageName) {
            this.mPackage = packageName;
        }

        public boolean onPreferenceClick(Preference preference) {
            Bundle args = new Bundle();
            args.putString("package", this.mPackage);
            RecentLocationApps.this.mActivity.startPreferencePanel(InstalledAppDetails.class.getName(), args, R.string.application_info_label, null, null, 0);
            return true;
        }
    }

    public RecentLocationApps(SettingsActivity activity) {
        this.mActivity = activity;
        this.mPackageManager = activity.getPackageManager();
    }

    private AccessiblePreference createRecentLocationEntry(Drawable icon, CharSequence label, boolean isHighBattery, CharSequence contentDescription, OnPreferenceClickListener listener) {
        AccessiblePreference pref = new AccessiblePreference(this.mActivity, contentDescription);
        pref.setIcon(icon);
        pref.setTitle(label);
        if (isHighBattery) {
            pref.setSummary(R.string.location_high_battery_use);
        } else {
            pref.setSummary(R.string.location_low_battery_use);
        }
        pref.setOnPreferenceClickListener(listener);
        return pref;
    }

    public List<Preference> getAppList() {
        int[] iArr = new int[2];
        List<PackageOps> appOps = ((AppOpsManager) this.mActivity.getSystemService("appops")).getPackagesForOps(new int[]{41, 42});
        ArrayList<Preference> prefs = new ArrayList();
        long now = System.currentTimeMillis();
        UserManager um = (UserManager) this.mActivity.getSystemService("user");
        List<UserHandle> profiles = um.getUserProfiles();
        int appOpsN = appOps.size();
        for (int i = 0; i < appOpsN; i++) {
            PackageOps ops = (PackageOps) appOps.get(i);
            String packageName = ops.getPackageName();
            int uid = ops.getUid();
            int userId = UserHandle.getUserId(uid);
            boolean isAndroidOs = uid == 1000 && "android".equals(packageName);
            if (!isAndroidOs && profiles.contains(new UserHandle(userId))) {
                Preference preference = getPreferenceFromOps(um, now, ops);
                if (preference != null) {
                    prefs.add(preference);
                }
            }
        }
        return prefs;
    }

    private Preference getPreferenceFromOps(UserManager um, long now, PackageOps ops) {
        String packageName = ops.getPackageName();
        boolean highBattery = false;
        boolean normalBattery = false;
        long recentLocationCutoffTime = now - 900000;
        for (OpEntry entry : ops.getOps()) {
            if (entry.isRunning() || entry.getTime() >= recentLocationCutoffTime) {
                switch (entry.getOp()) {
                    case 41:
                        normalBattery = true;
                        break;
                    case 42:
                        highBattery = true;
                        break;
                    default:
                        break;
                }
            }
        }
        if (highBattery || normalBattery) {
            int userId = UserHandle.getUserId(ops.getUid());
            try {
                ApplicationInfo appInfo = AppGlobals.getPackageManager().getApplicationInfo(packageName, 128, userId);
                if (appInfo == null) {
                    Log.w(TAG, "Null application info retrieved for package " + packageName + ", userId " + userId);
                    return null;
                }
                Resources res = this.mActivity.getResources();
                UserHandle userHandle = new UserHandle(userId);
                Drawable icon = this.mPackageManager.getUserBadgedIcon(this.mPackageManager.getApplicationIcon(appInfo), userHandle);
                CharSequence appLabel = this.mPackageManager.getApplicationLabel(appInfo);
                return createRecentLocationEntry(icon, appLabel, highBattery, this.mPackageManager.getUserBadgedLabel(appLabel, userHandle), new PackageEntryClickedListener(packageName));
            } catch (RemoteException e) {
                Log.w(TAG, "Error while retrieving application info for package " + packageName + ", userId " + userId, e);
                return null;
            }
        }
        if (Log.isLoggable(TAG, 2)) {
            Log.v(TAG, packageName + " hadn't used location within the time interval.");
        }
        return null;
    }
}
