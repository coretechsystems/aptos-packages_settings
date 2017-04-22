package com.android.settings;

import android.appwidget.AppWidgetManager;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import com.android.settings.ActivityPicker.PickAdapter.Item;
import com.android.settings.AppWidgetLoader.ItemConstructor;
import java.util.List;

public class AppWidgetPickActivity extends ActivityPicker implements ItemConstructor<Item> {
    private int mAppWidgetId;
    private AppWidgetLoader<Item> mAppWidgetLoader;
    private AppWidgetManager mAppWidgetManager;
    List<Item> mItems;
    private PackageManager mPackageManager;

    public void onCreate(Bundle icicle) {
        this.mPackageManager = getPackageManager();
        this.mAppWidgetManager = AppWidgetManager.getInstance(this);
        this.mAppWidgetLoader = new AppWidgetLoader(this, this.mAppWidgetManager, this);
        super.onCreate(icicle);
        setResultData(0, null);
        Intent intent = getIntent();
        if (intent.hasExtra("appWidgetId")) {
            this.mAppWidgetId = intent.getIntExtra("appWidgetId", 0);
        } else {
            finish();
        }
    }

    protected List<Item> getItems() {
        this.mItems = this.mAppWidgetLoader.getItems(getIntent());
        return this.mItems;
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public com.android.settings.ActivityPicker.PickAdapter.Item createItem(android.content.Context r12, android.appwidget.AppWidgetProviderInfo r13, android.os.Bundle r14) {
        /*
        r11 = this;
        r5 = r13.label;
        r2 = 0;
        r8 = r13.icon;
        if (r8 == 0) goto L_0x005a;
    L_0x0007:
        r7 = r12.getResources();	 Catch:{ NameNotFoundException -> 0x007d }
        r8 = r7.getDisplayMetrics();	 Catch:{ NameNotFoundException -> 0x007d }
        r0 = r8.densityDpi;	 Catch:{ NameNotFoundException -> 0x007d }
        switch(r0) {
            case 160: goto L_0x0072;
            case 213: goto L_0x0074;
            case 240: goto L_0x0076;
            case 320: goto L_0x0078;
            case 480: goto L_0x007a;
            default: goto L_0x0014;
        };	 Catch:{ NameNotFoundException -> 0x007d }
    L_0x0014:
        r8 = (float) r0;	 Catch:{ NameNotFoundException -> 0x007d }
        r9 = 1061158912; // 0x3f400000 float:0.75 double:5.24282163E-315;
        r8 = r8 * r9;
        r9 = 1056964608; // 0x3f000000 float:0.5 double:5.222099017E-315;
        r8 = r8 + r9;
        r3 = (int) r8;	 Catch:{ NameNotFoundException -> 0x007d }
        r8 = r11.mPackageManager;	 Catch:{ NameNotFoundException -> 0x007d }
        r9 = r13.provider;	 Catch:{ NameNotFoundException -> 0x007d }
        r9 = r9.getPackageName();	 Catch:{ NameNotFoundException -> 0x007d }
        r6 = r8.getResourcesForApplication(r9);	 Catch:{ NameNotFoundException -> 0x007d }
        r8 = r13.icon;	 Catch:{ NameNotFoundException -> 0x007d }
        r2 = r6.getDrawableForDensity(r8, r3);	 Catch:{ NameNotFoundException -> 0x007d }
    L_0x002e:
        if (r2 != 0) goto L_0x005a;
    L_0x0030:
        r8 = "AppWidgetPickActivity";
        r9 = new java.lang.StringBuilder;
        r9.<init>();
        r10 = "Can't load icon drawable 0x";
        r9 = r9.append(r10);
        r10 = r13.icon;
        r10 = java.lang.Integer.toHexString(r10);
        r9 = r9.append(r10);
        r10 = " for provider: ";
        r9 = r9.append(r10);
        r10 = r13.provider;
        r9 = r9.append(r10);
        r9 = r9.toString();
        android.util.Log.w(r8, r9);
    L_0x005a:
        r4 = new com.android.settings.ActivityPicker$PickAdapter$Item;
        r4.<init>(r12, r5, r2);
        r8 = r13.provider;
        r8 = r8.getPackageName();
        r4.packageName = r8;
        r8 = r13.provider;
        r8 = r8.getClassName();
        r4.className = r8;
        r4.extras = r14;
        return r4;
    L_0x0072:
        r3 = 120; // 0x78 float:1.68E-43 double:5.93E-322;
    L_0x0074:
        r3 = 160; // 0xa0 float:2.24E-43 double:7.9E-322;
    L_0x0076:
        r3 = 160; // 0xa0 float:2.24E-43 double:7.9E-322;
    L_0x0078:
        r3 = 240; // 0xf0 float:3.36E-43 double:1.186E-321;
    L_0x007a:
        r3 = 320; // 0x140 float:4.48E-43 double:1.58E-321;
        goto L_0x0014;
    L_0x007d:
        r1 = move-exception;
        r8 = "AppWidgetPickActivity";
        r9 = new java.lang.StringBuilder;
        r9.<init>();
        r10 = "Can't load icon drawable 0x";
        r9 = r9.append(r10);
        r10 = r13.icon;
        r10 = java.lang.Integer.toHexString(r10);
        r9 = r9.append(r10);
        r10 = " for provider: ";
        r9 = r9.append(r10);
        r10 = r13.provider;
        r9 = r9.append(r10);
        r9 = r9.toString();
        android.util.Log.w(r8, r9);
        goto L_0x002e;
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.settings.AppWidgetPickActivity.createItem(android.content.Context, android.appwidget.AppWidgetProviderInfo, android.os.Bundle):com.android.settings.ActivityPicker$PickAdapter$Item");
    }

    public void onClick(DialogInterface dialog, int which) {
        Intent intent = getIntentForPosition(which);
        if (((Item) this.mItems.get(which)).extras != null) {
            setResultData(-1, intent);
        } else {
            int result;
            Bundle options = null;
            try {
                if (intent.getExtras() != null) {
                    options = intent.getExtras().getBundle("appWidgetOptions");
                }
                this.mAppWidgetManager.bindAppWidgetId(this.mAppWidgetId, intent.getComponent(), options);
                result = -1;
            } catch (IllegalArgumentException e) {
                result = 0;
            }
            setResultData(result, null);
        }
        finish();
    }

    void setResultData(int code, Intent intent) {
        Intent result = intent != null ? intent : new Intent();
        result.putExtra("appWidgetId", this.mAppWidgetId);
        setResult(code, result);
    }
}
