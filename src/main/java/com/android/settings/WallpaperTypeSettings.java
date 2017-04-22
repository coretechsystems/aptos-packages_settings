package com.android.settings;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceScreen;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settings.search.Indexable;
import com.android.settings.search.Indexable.SearchIndexProvider;
import com.android.settings.search.SearchIndexableRaw;
import java.util.ArrayList;
import java.util.List;

public class WallpaperTypeSettings extends SettingsPreferenceFragment implements Indexable {
    public static final SearchIndexProvider SEARCH_INDEX_DATA_PROVIDER = new BaseSearchIndexProvider() {
        public List<SearchIndexableRaw> getRawDataToIndex(Context context, boolean enabled) {
            List<SearchIndexableRaw> result = new ArrayList();
            Intent intent = new Intent("android.intent.action.SET_WALLPAPER");
            PackageManager pm = context.getPackageManager();
            for (ResolveInfo info : pm.queryIntentActivities(intent, 65536)) {
                CharSequence label = info.loadLabel(pm);
                if (label == null) {
                    label = info.activityInfo.packageName;
                }
                SearchIndexableRaw data = new SearchIndexableRaw(context);
                data.title = label.toString();
                data.screenTitle = context.getResources().getString(R.string.wallpaper_settings_fragment_title);
                data.intentAction = "android.intent.action.SET_WALLPAPER";
                data.intentTargetPackage = info.activityInfo.packageName;
                data.intentTargetClass = info.activityInfo.name;
                result.add(data);
            }
            return result;
        }
    };

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.wallpaper_settings);
        populateWallpaperTypes();
    }

    private void populateWallpaperTypes() {
        Intent intent = new Intent("android.intent.action.SET_WALLPAPER");
        PackageManager pm = getPackageManager();
        List<ResolveInfo> rList = pm.queryIntentActivities(intent, 65536);
        PreferenceScreen parent = getPreferenceScreen();
        parent.setOrderingAsAdded(false);
        for (ResolveInfo info : rList) {
            Preference pref = new Preference(getActivity());
            Intent prefIntent = new Intent(intent);
            prefIntent.setComponent(new ComponentName(info.activityInfo.packageName, info.activityInfo.name));
            pref.setIntent(prefIntent);
            CharSequence label = info.loadLabel(pm);
            if (label == null) {
                label = info.activityInfo.packageName;
            }
            pref.setTitle(label);
            parent.addPreference(pref);
        }
    }
}
