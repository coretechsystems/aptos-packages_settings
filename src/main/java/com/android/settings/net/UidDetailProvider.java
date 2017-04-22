package com.android.settings.net;

import android.app.AppGlobals;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.IPackageManager;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.UserInfo;
import android.content.res.Resources;
import android.net.ConnectivityManager;
import android.os.RemoteException;
import android.os.UserHandle;
import android.os.UserManager;
import android.text.TextUtils;
import android.util.Log;
import android.util.SparseArray;
import com.android.settings.R;
import com.android.settings.Utils;

public class UidDetailProvider {
    private final Context mContext;
    private final SparseArray<UidDetail> mUidDetailCache = new SparseArray();

    public static int buildKeyForUser(int userHandle) {
        return -2000 - userHandle;
    }

    public static boolean isKeyForUser(int key) {
        return key <= -2000;
    }

    public static int getUserIdForKey(int key) {
        return -2000 - key;
    }

    public UidDetailProvider(Context context) {
        this.mContext = context.getApplicationContext();
    }

    public void clearCache() {
        synchronized (this.mUidDetailCache) {
            this.mUidDetailCache.clear();
        }
    }

    public UidDetail getUidDetail(int uid, boolean blocking) {
        synchronized (this.mUidDetailCache) {
            UidDetail detail = (UidDetail) this.mUidDetailCache.get(uid);
        }
        if (detail != null) {
            return detail;
        }
        if (!blocking) {
            return null;
        }
        detail = buildUidDetail(uid);
        synchronized (this.mUidDetailCache) {
            this.mUidDetailCache.put(uid, detail);
        }
        return detail;
    }

    private UidDetail buildUidDetail(int uid) {
        Resources res = this.mContext.getResources();
        PackageManager pm = this.mContext.getPackageManager();
        UidDetail detail = new UidDetail();
        detail.label = pm.getNameForUid(uid);
        detail.icon = pm.getDefaultActivityIcon();
        switch (uid) {
            case -5:
                detail.label = res.getString(Utils.getTetheringLabel((ConnectivityManager) this.mContext.getSystemService("connectivity")));
                detail.icon = pm.getDefaultActivityIcon();
                break;
            case -4:
                detail.label = res.getString(UserManager.supportsMultipleUsers() ? R.string.data_usage_uninstalled_apps_users : R.string.data_usage_uninstalled_apps);
                detail.icon = pm.getDefaultActivityIcon();
                break;
            case 1000:
                detail.label = res.getString(R.string.process_kernel_label);
                detail.icon = pm.getDefaultActivityIcon();
                break;
            default:
                UserManager um = (UserManager) this.mContext.getSystemService("user");
                if (isKeyForUser(uid)) {
                    UserInfo info = um.getUserInfo(getUserIdForKey(uid));
                    if (info != null) {
                        if (!info.isManagedProfile()) {
                            detail.label = res.getString(R.string.running_process_item_user_label, new Object[]{info.name});
                            detail.icon = Utils.getUserIcon(this.mContext, um, info);
                            break;
                        }
                        detail.label = res.getString(R.string.managed_user_title);
                        detail.icon = Resources.getSystem().getDrawable(17302357);
                        break;
                    }
                }
                String[] packageNames = pm.getPackagesForUid(uid);
                int length = packageNames != null ? packageNames.length : 0;
                try {
                    int userId = UserHandle.getUserId(uid);
                    UserHandle userHandle = new UserHandle(userId);
                    IPackageManager ipm = AppGlobals.getPackageManager();
                    if (length == 1) {
                        ApplicationInfo info2 = ipm.getApplicationInfo(packageNames[0], 0, userId);
                        if (info2 != null) {
                            detail.label = info2.loadLabel(pm).toString();
                            detail.icon = um.getBadgedIconForUser(info2.loadIcon(pm), new UserHandle(userId));
                        }
                    } else if (length > 1) {
                        detail.detailLabels = new CharSequence[length];
                        detail.detailContentDescriptions = new CharSequence[length];
                        for (int i = 0; i < length; i++) {
                            String packageName = packageNames[i];
                            PackageInfo packageInfo = pm.getPackageInfo(packageName, 0);
                            ApplicationInfo appInfo = ipm.getApplicationInfo(packageName, 0, userId);
                            if (appInfo != null) {
                                detail.detailLabels[i] = appInfo.loadLabel(pm).toString();
                                detail.detailContentDescriptions[i] = um.getBadgedLabelForUser(detail.detailLabels[i], userHandle);
                                if (packageInfo.sharedUserLabel != 0) {
                                    detail.label = pm.getText(packageName, packageInfo.sharedUserLabel, packageInfo.applicationInfo).toString();
                                    detail.icon = um.getBadgedIconForUser(appInfo.loadIcon(pm), userHandle);
                                }
                            }
                        }
                    }
                    detail.contentDescription = um.getBadgedLabelForUser(detail.label, userHandle);
                } catch (NameNotFoundException e) {
                    Log.w("DataUsage", "Error while building UI detail for uid " + uid, e);
                } catch (RemoteException e2) {
                    Log.w("DataUsage", "Error while building UI detail for uid " + uid, e2);
                }
                if (TextUtils.isEmpty(detail.label)) {
                    detail.label = Integer.toString(uid);
                    break;
                }
                break;
        }
        return detail;
    }
}
