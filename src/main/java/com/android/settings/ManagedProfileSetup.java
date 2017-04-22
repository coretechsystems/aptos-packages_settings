package com.android.settings;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.Log;
import java.util.List;

public class ManagedProfileSetup extends BroadcastReceiver {
    public void onReceive(Context context, Intent broadcast) {
        if (Utils.isManagedProfile((UserManager) context.getSystemService("user"))) {
            Log.i("Settings", "Received broadcast: " + broadcast.getAction() + ". Setting up intent forwarding for managed profile.");
            PackageManager pm = context.getPackageManager();
            pm.clearCrossProfileIntentFilters(UserHandle.myUserId());
            Intent intent = new Intent();
            intent.addCategory("android.intent.category.DEFAULT");
            intent.setPackage(context.getPackageName());
            List<ResolveInfo> resolvedIntents = pm.queryIntentActivities(intent, 193);
            int count = resolvedIntents.size();
            for (int i = 0; i < count; i++) {
                ResolveInfo info = (ResolveInfo) resolvedIntents.get(i);
                if (!(info.filter == null || info.activityInfo == null || info.activityInfo.metaData == null || !info.activityInfo.metaData.getBoolean("com.android.settings.PRIMARY_PROFILE_CONTROLLED"))) {
                    pm.addCrossProfileIntentFilter(info.filter, UserHandle.myUserId(), 0, 2);
                }
            }
            pm.setComponentEnabledSetting(new ComponentName(context, Settings.class), 2, 1);
        }
    }
}
