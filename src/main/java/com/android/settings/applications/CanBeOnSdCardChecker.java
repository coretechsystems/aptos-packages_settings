package com.android.settings.applications;

import android.content.pm.ApplicationInfo;
import android.content.pm.IPackageManager;
import android.content.pm.IPackageManager.Stub;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.Log;

/* compiled from: ManageApplications */
final class CanBeOnSdCardChecker {
    int mInstallLocation;
    final IPackageManager mPm = Stub.asInterface(ServiceManager.getService("package"));

    CanBeOnSdCardChecker() {
    }

    void init() {
        try {
            this.mInstallLocation = this.mPm.getInstallLocation();
        } catch (RemoteException e) {
            Log.e("CanBeOnSdCardChecker", "Is Package Manager running?");
        }
    }

    boolean check(ApplicationInfo info) {
        if ((info.flags & 262144) != 0) {
            return true;
        }
        if ((info.flags & 1) != 0) {
            return false;
        }
        if (info.installLocation == 2 || info.installLocation == 0) {
            return true;
        }
        if (info.installLocation == -1 && this.mInstallLocation == 2) {
            return true;
        }
        return false;
    }
}
