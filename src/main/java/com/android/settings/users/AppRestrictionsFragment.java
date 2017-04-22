package com.android.settings.users;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.RestrictionEntry;
import android.content.pm.ApplicationInfo;
import android.content.pm.IPackageManager;
import android.content.pm.IPackageManager.Stub;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ResolveInfo;
import android.content.res.Resources.NotFoundException;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.AsyncTask.Status;
import android.os.Bundle;
import android.os.Process;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.UserHandle;
import android.os.UserManager;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.MultiSelectListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceGroup;
import android.preference.SwitchPreference;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.Switch;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.drawable.CircleFramedDrawable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.StringTokenizer;

public class AppRestrictionsFragment extends SettingsPreferenceFragment implements OnPreferenceChangeListener, OnPreferenceClickListener, OnClickListener {
    private static final String TAG = AppRestrictionsFragment.class.getSimpleName();
    private PreferenceGroup mAppList;
    private boolean mAppListChanged;
    private AsyncTask mAppLoadingTask;
    private int mCustomRequestCode = 1000;
    private HashMap<Integer, AppRestrictionsPreference> mCustomRequestMap = new HashMap();
    private boolean mFirstTime = true;
    protected IPackageManager mIPm;
    private boolean mNewUser;
    protected PackageManager mPackageManager;
    private BroadcastReceiver mPackageObserver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            AppRestrictionsFragment.this.onPackageChanged(intent);
        }
    };
    protected boolean mRestrictedProfile;
    HashMap<String, Boolean> mSelectedPackages = new HashMap();
    private PackageInfo mSysPackageInfo;
    protected UserHandle mUser;
    private List<ApplicationInfo> mUserApps;
    private BroadcastReceiver mUserBackgrounding = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            if (AppRestrictionsFragment.this.mAppListChanged) {
                AppRestrictionsFragment.this.applyUserAppsStates();
            }
        }
    };
    protected UserManager mUserManager;
    private List<SelectableAppInfo> mVisibleApps;

    private class AppLabelComparator implements Comparator<SelectableAppInfo> {
        private AppLabelComparator() {
        }

        public int compare(SelectableAppInfo lhs, SelectableAppInfo rhs) {
            return lhs.activityName.toString().toLowerCase().compareTo(rhs.activityName.toString().toLowerCase());
        }
    }

    private class AppLoadingTask extends AsyncTask<Void, Void, Void> {
        private AppLoadingTask() {
        }

        protected Void doInBackground(Void... params) {
            AppRestrictionsFragment.this.fetchAndMergeApps();
            return null;
        }

        protected void onPostExecute(Void result) {
            AppRestrictionsFragment.this.populateApps();
        }

        protected void onPreExecute() {
        }
    }

    static class AppRestrictionsPreference extends SwitchPreference {
        private boolean hasSettings;
        private boolean immutable;
        private OnClickListener listener;
        private List<Preference> mChildren = new ArrayList();
        private boolean panelOpen;
        private ArrayList<RestrictionEntry> restrictions;

        AppRestrictionsPreference(Context context, OnClickListener listener) {
            super(context);
            setLayoutResource(R.layout.preference_app_restrictions);
            this.listener = listener;
        }

        private void setSettingsEnabled(boolean enable) {
            this.hasSettings = enable;
        }

        void setRestrictions(ArrayList<RestrictionEntry> restrictions) {
            this.restrictions = restrictions;
        }

        void setImmutable(boolean immutable) {
            this.immutable = immutable;
        }

        boolean isImmutable() {
            return this.immutable;
        }

        ArrayList<RestrictionEntry> getRestrictions() {
            return this.restrictions;
        }

        boolean isPanelOpen() {
            return this.panelOpen;
        }

        void setPanelOpen(boolean open) {
            this.panelOpen = open;
        }

        protected void onBindView(View view) {
            int i;
            boolean z;
            int i2 = 8;
            boolean z2 = false;
            super.onBindView(view);
            View appRestrictionsSettings = view.findViewById(R.id.app_restrictions_settings);
            if (this.hasSettings) {
                i = 0;
            } else {
                i = 8;
            }
            appRestrictionsSettings.setVisibility(i);
            View findViewById = view.findViewById(R.id.settings_divider);
            if (this.hasSettings) {
                i2 = 0;
            }
            findViewById.setVisibility(i2);
            appRestrictionsSettings.setOnClickListener(this.listener);
            appRestrictionsSettings.setTag(this);
            View appRestrictionsPref = view.findViewById(R.id.app_restrictions_pref);
            appRestrictionsPref.setOnClickListener(this.listener);
            appRestrictionsPref.setTag(this);
            ViewGroup widget = (ViewGroup) view.findViewById(16908312);
            if (isImmutable()) {
                z = false;
            } else {
                z = true;
            }
            widget.setEnabled(z);
            if (widget.getChildCount() > 0) {
                final Switch toggle = (Switch) widget.getChildAt(0);
                if (!isImmutable()) {
                    z2 = true;
                }
                toggle.setEnabled(z2);
                toggle.setTag(this);
                toggle.setClickable(true);
                toggle.setFocusable(true);
                toggle.setOnCheckedChangeListener(new OnCheckedChangeListener() {
                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                        AppRestrictionsPreference.this.listener.onClick(toggle);
                    }
                });
            }
        }
    }

    class RestrictionsResultReceiver extends BroadcastReceiver {
        boolean invokeIfCustom;
        String packageName;
        AppRestrictionsPreference preference;

        RestrictionsResultReceiver(String packageName, AppRestrictionsPreference preference, boolean invokeIfCustom) {
            this.packageName = packageName;
            this.preference = preference;
            this.invokeIfCustom = invokeIfCustom;
        }

        public void onReceive(Context context, Intent intent) {
            Bundle results = getResultExtras(true);
            ArrayList<RestrictionEntry> restrictions = results.getParcelableArrayList("android.intent.extra.restrictions_list");
            Intent restrictionsIntent = (Intent) results.getParcelable("android.intent.extra.restrictions_intent");
            if (restrictions != null && restrictionsIntent == null) {
                AppRestrictionsFragment.this.onRestrictionsReceived(this.preference, this.packageName, restrictions);
                if (AppRestrictionsFragment.this.mRestrictedProfile) {
                    AppRestrictionsFragment.this.mUserManager.setApplicationRestrictions(this.packageName, RestrictionUtils.restrictionsToBundle(restrictions), AppRestrictionsFragment.this.mUser);
                }
            } else if (restrictionsIntent != null) {
                this.preference.setRestrictions(restrictions);
                if (this.invokeIfCustom && AppRestrictionsFragment.this.isResumed()) {
                    AppRestrictionsFragment.this.startActivityForResult(restrictionsIntent, AppRestrictionsFragment.this.generateCustomActivityRequestCode(this.preference));
                }
            }
        }
    }

    static class SelectableAppInfo {
        CharSequence activityName;
        CharSequence appName;
        Drawable icon;
        SelectableAppInfo masterEntry;
        String packageName;

        SelectableAppInfo() {
        }

        public String toString() {
            return this.packageName + ": appName=" + this.appName + "; activityName=" + this.activityName + "; icon=" + this.icon + "; masterEntry=" + this.masterEntry;
        }
    }

    protected void init(Bundle icicle) {
        if (icicle != null) {
            this.mUser = new UserHandle(icicle.getInt("user_id"));
        } else {
            Bundle args = getArguments();
            if (args != null) {
                if (args.containsKey("user_id")) {
                    this.mUser = new UserHandle(args.getInt("user_id"));
                }
                this.mNewUser = args.getBoolean("new_user", false);
            }
        }
        if (this.mUser == null) {
            this.mUser = Process.myUserHandle();
        }
        this.mPackageManager = getActivity().getPackageManager();
        this.mIPm = Stub.asInterface(ServiceManager.getService("package"));
        this.mUserManager = (UserManager) getActivity().getSystemService("user");
        this.mRestrictedProfile = this.mUserManager.getUserInfo(this.mUser.getIdentifier()).isRestricted();
        try {
            this.mSysPackageInfo = this.mPackageManager.getPackageInfo("android", 64);
        } catch (NameNotFoundException e) {
        }
        addPreferencesFromResource(R.xml.app_restrictions);
        this.mAppList = getAppPreferenceGroup();
    }

    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("user_id", this.mUser.getIdentifier());
    }

    public void onResume() {
        super.onResume();
        getActivity().registerReceiver(this.mUserBackgrounding, new IntentFilter("android.intent.action.USER_BACKGROUND"));
        IntentFilter packageFilter = new IntentFilter();
        packageFilter.addAction("android.intent.action.PACKAGE_ADDED");
        packageFilter.addAction("android.intent.action.PACKAGE_REMOVED");
        packageFilter.addDataScheme("package");
        getActivity().registerReceiver(this.mPackageObserver, packageFilter);
        this.mAppListChanged = false;
        if (this.mAppLoadingTask == null || this.mAppLoadingTask.getStatus() == Status.FINISHED) {
            this.mAppLoadingTask = new AppLoadingTask().execute((Void[]) null);
        }
    }

    public void onPause() {
        super.onPause();
        this.mNewUser = false;
        getActivity().unregisterReceiver(this.mUserBackgrounding);
        getActivity().unregisterReceiver(this.mPackageObserver);
        if (this.mAppListChanged) {
            new Thread() {
                public void run() {
                    AppRestrictionsFragment.this.applyUserAppsStates();
                }
            }.start();
        }
    }

    private void onPackageChanged(Intent intent) {
        String action = intent.getAction();
        AppRestrictionsPreference pref = (AppRestrictionsPreference) findPreference(getKeyForPackage(intent.getData().getSchemeSpecificPart()));
        if (pref != null) {
            if (("android.intent.action.PACKAGE_ADDED".equals(action) && pref.isChecked()) || ("android.intent.action.PACKAGE_REMOVED".equals(action) && !pref.isChecked())) {
                pref.setEnabled(true);
            }
        }
    }

    protected PreferenceGroup getAppPreferenceGroup() {
        return getPreferenceScreen();
    }

    Drawable getCircularUserIcon() {
        Bitmap userIcon = this.mUserManager.getUserIcon(this.mUser.getIdentifier());
        if (userIcon == null) {
            return null;
        }
        return CircleFramedDrawable.getInstance(getActivity(), userIcon);
    }

    private void applyUserAppsStates() {
        int userId = this.mUser.getIdentifier();
        if (this.mUserManager.getUserInfo(userId).isRestricted() || userId == UserHandle.myUserId()) {
            for (Entry<String, Boolean> entry : this.mSelectedPackages.entrySet()) {
                applyUserAppState((String) entry.getKey(), ((Boolean) entry.getValue()).booleanValue());
            }
            return;
        }
        Log.e(TAG, "Cannot apply application restrictions on another user!");
    }

    private void applyUserAppState(String packageName, boolean enabled) {
        int userId = this.mUser.getIdentifier();
        if (enabled) {
            try {
                ApplicationInfo info = this.mIPm.getApplicationInfo(packageName, 8192, userId);
                if (info == null || !info.enabled || (info.flags & 8388608) == 0) {
                    this.mIPm.installExistingPackageAsUser(packageName, this.mUser.getIdentifier());
                }
                if (info != null && (info.flags & 134217728) != 0 && (info.flags & 8388608) != 0) {
                    disableUiForPackage(packageName);
                    this.mIPm.setApplicationHiddenSettingAsUser(packageName, false, userId);
                    return;
                }
                return;
            } catch (RemoteException e) {
                return;
            }
        }
        try {
            if (this.mIPm.getApplicationInfo(packageName, 0, userId) == null) {
                return;
            }
            if (this.mRestrictedProfile) {
                this.mIPm.deletePackageAsUser(packageName, null, this.mUser.getIdentifier(), 4);
                return;
            }
            disableUiForPackage(packageName);
            this.mIPm.setApplicationHiddenSettingAsUser(packageName, true, userId);
        } catch (RemoteException e2) {
        }
    }

    private void disableUiForPackage(String packageName) {
        AppRestrictionsPreference pref = (AppRestrictionsPreference) findPreference(getKeyForPackage(packageName));
        if (pref != null) {
            pref.setEnabled(false);
        }
    }

    private boolean isSystemPackage(String packageName) {
        try {
            PackageInfo pi = this.mPackageManager.getPackageInfo(packageName, 0);
            if (pi.applicationInfo == null) {
                return false;
            }
            int flags = pi.applicationInfo.flags;
            if ((flags & 1) == 0 && (flags & 128) == 0) {
                return false;
            }
            return true;
        } catch (NameNotFoundException e) {
            return false;
        }
    }

    private void addSystemImes(Set<String> excludePackages) {
        Context context = getActivity();
        if (context != null) {
            for (InputMethodInfo imi : ((InputMethodManager) context.getSystemService("input_method")).getInputMethodList()) {
                try {
                    if (imi.isDefault(context) && isSystemPackage(imi.getPackageName())) {
                        excludePackages.add(imi.getPackageName());
                    }
                } catch (NotFoundException e) {
                }
            }
        }
    }

    private void addSystemApps(List<SelectableAppInfo> visibleApps, Intent intent, Set<String> excludePackages) {
        if (getActivity() != null) {
            PackageManager pm = this.mPackageManager;
            for (ResolveInfo app : pm.queryIntentActivities(intent, 8704)) {
                if (!(app.activityInfo == null || app.activityInfo.applicationInfo == null)) {
                    String packageName = app.activityInfo.packageName;
                    int flags = app.activityInfo.applicationInfo.flags;
                    if (!(((flags & 1) == 0 && (flags & 128) == 0) || excludePackages.contains(packageName))) {
                        int enabled = pm.getApplicationEnabledSetting(packageName);
                        if (enabled == 4 || enabled == 2) {
                            ApplicationInfo targetUserAppInfo = getAppInfoForUser(packageName, 0, this.mUser);
                            if (targetUserAppInfo != null) {
                                if ((targetUserAppInfo.flags & 8388608) == 0) {
                                }
                            }
                        }
                        SelectableAppInfo info = new SelectableAppInfo();
                        info.packageName = app.activityInfo.packageName;
                        info.appName = app.activityInfo.applicationInfo.loadLabel(pm);
                        info.icon = app.activityInfo.loadIcon(pm);
                        info.activityName = app.activityInfo.loadLabel(pm);
                        if (info.activityName == null) {
                            info.activityName = info.appName;
                        }
                        visibleApps.add(info);
                    }
                }
            }
        }
    }

    private ApplicationInfo getAppInfoForUser(String packageName, int flags, UserHandle user) {
        try {
            return this.mIPm.getApplicationInfo(packageName, flags, user.getIdentifier());
        } catch (RemoteException e) {
            return null;
        }
    }

    private void fetchAndMergeApps() {
        this.mAppList.setOrderingAsAdded(false);
        this.mVisibleApps = new ArrayList();
        if (getActivity() != null) {
            SelectableAppInfo info;
            PackageManager pm = this.mPackageManager;
            IPackageManager ipm = this.mIPm;
            HashSet<String> excludePackages = new HashSet();
            addSystemImes(excludePackages);
            Intent launcherIntent = new Intent("android.intent.action.MAIN");
            launcherIntent.addCategory("android.intent.category.LAUNCHER");
            addSystemApps(this.mVisibleApps, launcherIntent, excludePackages);
            Intent widgetIntent = new Intent("android.appwidget.action.APPWIDGET_UPDATE");
            addSystemApps(this.mVisibleApps, widgetIntent, excludePackages);
            for (ApplicationInfo app : pm.getInstalledApplications(8192)) {
                if ((app.flags & 8388608) != 0) {
                    if ((app.flags & 1) == 0 && (app.flags & 128) == 0) {
                        info = new SelectableAppInfo();
                        info.packageName = app.packageName;
                        info.appName = app.loadLabel(pm);
                        info.activityName = info.appName;
                        info.icon = app.loadIcon(pm);
                        this.mVisibleApps.add(info);
                    } else {
                        try {
                            PackageInfo pi = pm.getPackageInfo(app.packageName, 0);
                            if (this.mRestrictedProfile && pi.requiredAccountType != null && pi.restrictedAccountType == null) {
                                this.mSelectedPackages.put(app.packageName, Boolean.valueOf(false));
                            }
                        } catch (NameNotFoundException e) {
                        }
                    }
                }
            }
            this.mUserApps = null;
            try {
                this.mUserApps = ipm.getInstalledApplications(8192, this.mUser.getIdentifier()).getList();
            } catch (RemoteException e2) {
            }
            if (this.mUserApps != null) {
                for (ApplicationInfo app2 : this.mUserApps) {
                    if ((app2.flags & 8388608) != 0 && (app2.flags & 1) == 0 && (app2.flags & 128) == 0) {
                        info = new SelectableAppInfo();
                        info.packageName = app2.packageName;
                        info.appName = app2.loadLabel(pm);
                        info.activityName = info.appName;
                        info.icon = app2.loadIcon(pm);
                        this.mVisibleApps.add(info);
                    }
                }
            }
            Collections.sort(this.mVisibleApps, new AppLabelComparator());
            Set<String> dedupPackageSet = new HashSet();
            for (int i = this.mVisibleApps.size() - 1; i >= 0; i--) {
                info = (SelectableAppInfo) this.mVisibleApps.get(i);
                String both = info.packageName + "+" + info.activityName;
                if (TextUtils.isEmpty(info.packageName) || TextUtils.isEmpty(info.activityName) || !dedupPackageSet.contains(both)) {
                    dedupPackageSet.add(both);
                } else {
                    this.mVisibleApps.remove(i);
                }
            }
            HashMap<String, SelectableAppInfo> packageMap = new HashMap();
            for (SelectableAppInfo info2 : this.mVisibleApps) {
                if (packageMap.containsKey(info2.packageName)) {
                    info2.masterEntry = (SelectableAppInfo) packageMap.get(info2.packageName);
                } else {
                    packageMap.put(info2.packageName, info2);
                }
            }
        }
    }

    private boolean isPlatformSigned(PackageInfo pi) {
        return (pi == null || pi.signatures == null || !this.mSysPackageInfo.signatures[0].equals(pi.signatures[0])) ? false : true;
    }

    private boolean isAppEnabledForUser(PackageInfo pi) {
        if (pi == null) {
            return false;
        }
        int flags = pi.applicationInfo.flags;
        if ((8388608 & flags) == 0 || (134217728 & flags) != 0) {
            return false;
        }
        return true;
    }

    private void populateApps() {
        Context context = getActivity();
        if (context != null) {
            PackageManager pm = this.mPackageManager;
            IPackageManager ipm = this.mIPm;
            this.mAppList.removeAll();
            List<ResolveInfo> receivers = pm.queryBroadcastReceivers(new Intent("android.intent.action.GET_RESTRICTION_ENTRIES"), 0);
            int i = 0;
            if (this.mVisibleApps.size() > 0) {
                for (SelectableAppInfo app : this.mVisibleApps) {
                    String packageName = app.packageName;
                    if (packageName != null) {
                        boolean isSettingsApp = packageName.equals(context.getPackageName());
                        AppRestrictionsPreference p = new AppRestrictionsPreference(context, this);
                        boolean hasSettings = resolveInfoListHasPackage(receivers, packageName);
                        p.setIcon(app.icon != null ? app.icon.mutate() : null);
                        p.setChecked(false);
                        p.setTitle(app.activityName);
                        if (app.masterEntry != null) {
                            p.setSummary(context.getString(R.string.user_restrictions_controlled_by, new Object[]{app.masterEntry.activityName}));
                        }
                        p.setKey(getKeyForPackage(packageName));
                        boolean z = (hasSettings || isSettingsApp) && app.masterEntry == null;
                        p.setSettingsEnabled(z);
                        p.setPersistent(false);
                        p.setOnPreferenceChangeListener(this);
                        p.setOnPreferenceClickListener(this);
                        PackageInfo pi = null;
                        try {
                            pi = ipm.getPackageInfo(packageName, 8256, this.mUser.getIdentifier());
                        } catch (RemoteException e) {
                        }
                        if (pi != null && (pi.requiredForAllUsers || isPlatformSigned(pi))) {
                            p.setChecked(true);
                            p.setImmutable(true);
                            if (hasSettings || isSettingsApp) {
                                if (hasSettings && app.masterEntry == null) {
                                    requestRestrictionsForApp(packageName, p, false);
                                }
                            }
                        } else if (!this.mNewUser && isAppEnabledForUser(pi)) {
                            p.setChecked(true);
                        }
                        if (this.mRestrictedProfile && pi.requiredAccountType != null && pi.restrictedAccountType == null) {
                            p.setChecked(false);
                            p.setImmutable(true);
                            p.setSummary(R.string.app_not_supported_in_limited);
                        }
                        if (this.mRestrictedProfile && pi.restrictedAccountType != null) {
                            p.setSummary(R.string.app_sees_restricted_accounts);
                        }
                        if (app.masterEntry != null) {
                            p.setImmutable(true);
                            p.setChecked(((Boolean) this.mSelectedPackages.get(packageName)).booleanValue());
                        }
                        this.mAppList.addPreference(p);
                        if (isSettingsApp) {
                            p.setOrder(100);
                        } else {
                            p.setOrder((i + 2) * 100);
                        }
                        this.mSelectedPackages.put(packageName, Boolean.valueOf(p.isChecked()));
                        this.mAppListChanged = true;
                        i++;
                    }
                }
            }
            if (this.mNewUser && this.mFirstTime) {
                this.mFirstTime = false;
                applyUserAppsStates();
            }
        }
    }

    private String getKeyForPackage(String packageName) {
        return "pkg_" + packageName;
    }

    private boolean resolveInfoListHasPackage(List<ResolveInfo> receivers, String packageName) {
        for (ResolveInfo info : receivers) {
            if (info.activityInfo.packageName.equals(packageName)) {
                return true;
            }
        }
        return false;
    }

    private void updateAllEntries(String prefKey, boolean checked) {
        for (int i = 0; i < this.mAppList.getPreferenceCount(); i++) {
            Preference pref = this.mAppList.getPreference(i);
            if ((pref instanceof AppRestrictionsPreference) && prefKey.equals(pref.getKey())) {
                ((AppRestrictionsPreference) pref).setChecked(checked);
            }
        }
    }

    public void onClick(View v) {
        if (v.getTag() instanceof AppRestrictionsPreference) {
            AppRestrictionsPreference pref = (AppRestrictionsPreference) v.getTag();
            if (v.getId() == R.id.app_restrictions_settings) {
                onAppSettingsIconClicked(pref);
            } else if (!pref.isImmutable()) {
                boolean z;
                if (pref.isChecked()) {
                    z = false;
                } else {
                    z = true;
                }
                pref.setChecked(z);
                String packageName = pref.getKey().substring("pkg_".length());
                this.mSelectedPackages.put(packageName, Boolean.valueOf(pref.isChecked()));
                if (pref.isChecked() && pref.hasSettings && pref.restrictions == null) {
                    requestRestrictionsForApp(packageName, pref, false);
                }
                this.mAppListChanged = true;
                if (!this.mRestrictedProfile) {
                    applyUserAppState(packageName, pref.isChecked());
                }
                updateAllEntries(pref.getKey(), pref.isChecked());
            }
        }
    }

    public boolean onPreferenceChange(Preference preference, Object newValue) {
        String key = preference.getKey();
        if (key != null && key.contains(";")) {
            StringTokenizer st = new StringTokenizer(key, ";");
            String packageName = st.nextToken();
            String restrictionKey = st.nextToken();
            ArrayList<RestrictionEntry> restrictions = ((AppRestrictionsPreference) this.mAppList.findPreference("pkg_" + packageName)).getRestrictions();
            if (restrictions != null) {
                Iterator i$ = restrictions.iterator();
                while (i$.hasNext()) {
                    RestrictionEntry entry = (RestrictionEntry) i$.next();
                    if (entry.getKey().equals(restrictionKey)) {
                        switch (entry.getType()) {
                            case 1:
                                entry.setSelectedState(((Boolean) newValue).booleanValue());
                                break;
                            case 2:
                            case 3:
                                ListPreference listPref = (ListPreference) preference;
                                entry.setSelectedString((String) newValue);
                                listPref.setSummary(findInArray(entry.getChoiceEntries(), entry.getChoiceValues(), (String) newValue));
                                break;
                            case 4:
                                Set<String> set = (Set) newValue;
                                String[] selectedValues = new String[set.size()];
                                set.toArray(selectedValues);
                                entry.setAllSelectedStrings(selectedValues);
                                break;
                            default:
                                continue;
                        }
                        if (packageName.equals(getActivity().getPackageName())) {
                            RestrictionUtils.setRestrictions(getActivity(), restrictions, this.mUser);
                        } else {
                            this.mUserManager.setApplicationRestrictions(packageName, RestrictionUtils.restrictionsToBundle(restrictions), this.mUser);
                        }
                    }
                }
            }
        }
        return true;
    }

    private void removeRestrictionsForApp(AppRestrictionsPreference preference) {
        for (Preference p : preference.mChildren) {
            this.mAppList.removePreference(p);
        }
        preference.mChildren.clear();
    }

    private void onAppSettingsIconClicked(AppRestrictionsPreference preference) {
        boolean z = true;
        if (preference.getKey().startsWith("pkg_")) {
            if (preference.isPanelOpen()) {
                removeRestrictionsForApp(preference);
            } else {
                String packageName = preference.getKey().substring("pkg_".length());
                if (packageName.equals(getActivity().getPackageName())) {
                    onRestrictionsReceived(preference, packageName, RestrictionUtils.getRestrictions(getActivity(), this.mUser));
                } else {
                    requestRestrictionsForApp(packageName, preference, true);
                }
            }
            if (preference.isPanelOpen()) {
                z = false;
            }
            preference.setPanelOpen(z);
        }
    }

    private void requestRestrictionsForApp(String packageName, AppRestrictionsPreference preference, boolean invokeIfCustom) {
        Bundle oldEntries = this.mUserManager.getApplicationRestrictions(packageName, this.mUser);
        Intent intent = new Intent("android.intent.action.GET_RESTRICTION_ENTRIES");
        intent.setPackage(packageName);
        intent.putExtra("android.intent.extra.restrictions_bundle", oldEntries);
        intent.addFlags(32);
        getActivity().sendOrderedBroadcast(intent, null, new RestrictionsResultReceiver(packageName, preference, invokeIfCustom), null, -1, null, null);
    }

    private void onRestrictionsReceived(AppRestrictionsPreference preference, String packageName, ArrayList<RestrictionEntry> restrictions) {
        removeRestrictionsForApp(preference);
        Context context = preference.getContext();
        int count = 1;
        Iterator it = restrictions.iterator();
        while (it.hasNext()) {
            RestrictionEntry entry = (RestrictionEntry) it.next();
            Preference p = null;
            switch (entry.getType()) {
                case 1:
                    p = new CheckBoxPreference(context);
                    p.setTitle(entry.getTitle());
                    p.setSummary(entry.getDescription());
                    ((CheckBoxPreference) p).setChecked(entry.getSelectedState());
                    break;
                case 2:
                case 3:
                    p = new ListPreference(context);
                    p.setTitle(entry.getTitle());
                    String value = entry.getSelectedString();
                    if (value == null) {
                        value = entry.getDescription();
                    }
                    p.setSummary(findInArray(entry.getChoiceEntries(), entry.getChoiceValues(), value));
                    ((ListPreference) p).setEntryValues(entry.getChoiceValues());
                    ((ListPreference) p).setEntries(entry.getChoiceEntries());
                    ((ListPreference) p).setValue(value);
                    ((ListPreference) p).setDialogTitle(entry.getTitle());
                    break;
                case 4:
                    p = new MultiSelectListPreference(context);
                    p.setTitle(entry.getTitle());
                    ((MultiSelectListPreference) p).setEntryValues(entry.getChoiceValues());
                    ((MultiSelectListPreference) p).setEntries(entry.getChoiceEntries());
                    HashSet<String> set = new HashSet();
                    for (String s : entry.getAllSelectedStrings()) {
                        set.add(s);
                    }
                    ((MultiSelectListPreference) p).setValues(set);
                    ((MultiSelectListPreference) p).setDialogTitle(entry.getTitle());
                    break;
            }
            if (p != null) {
                p.setPersistent(false);
                p.setOrder(preference.getOrder() + count);
                p.setKey(preference.getKey().substring("pkg_".length()) + ";" + entry.getKey());
                this.mAppList.addPreference(p);
                p.setOnPreferenceChangeListener(this);
                p.setIcon(R.drawable.empty_icon);
                preference.mChildren.add(p);
                count++;
            }
        }
        preference.setRestrictions(restrictions);
        if (count == 1 && preference.isImmutable() && preference.isChecked()) {
            this.mAppList.removePreference(preference);
        }
    }

    private int generateCustomActivityRequestCode(AppRestrictionsPreference preference) {
        this.mCustomRequestCode++;
        this.mCustomRequestMap.put(Integer.valueOf(this.mCustomRequestCode), preference);
        return this.mCustomRequestCode;
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        AppRestrictionsPreference pref = (AppRestrictionsPreference) this.mCustomRequestMap.get(Integer.valueOf(requestCode));
        if (pref == null) {
            Log.w(TAG, "Unknown requestCode " + requestCode);
            return;
        }
        if (resultCode == -1) {
            String packageName = pref.getKey().substring("pkg_".length());
            ArrayList<RestrictionEntry> list = data.getParcelableArrayListExtra("android.intent.extra.restrictions_list");
            Bundle bundle = data.getBundleExtra("android.intent.extra.restrictions_bundle");
            if (list != null) {
                pref.setRestrictions(list);
                this.mUserManager.setApplicationRestrictions(packageName, RestrictionUtils.restrictionsToBundle(list), this.mUser);
            } else if (bundle != null) {
                this.mUserManager.setApplicationRestrictions(packageName, bundle, this.mUser);
            }
        }
        this.mCustomRequestMap.remove(Integer.valueOf(requestCode));
    }

    private String findInArray(String[] choiceEntries, String[] choiceValues, String selectedString) {
        for (int i = 0; i < choiceValues.length; i++) {
            if (choiceValues[i].equals(selectedString)) {
                return choiceEntries[i];
            }
        }
        return selectedString;
    }

    public boolean onPreferenceClick(Preference preference) {
        boolean newEnabledState = false;
        if (!preference.getKey().startsWith("pkg_")) {
            return false;
        }
        AppRestrictionsPreference arp = (AppRestrictionsPreference) preference;
        if (arp.isImmutable()) {
            return true;
        }
        String packageName = arp.getKey().substring("pkg_".length());
        if (!arp.isChecked()) {
            newEnabledState = true;
        }
        arp.setChecked(newEnabledState);
        this.mSelectedPackages.put(packageName, Boolean.valueOf(newEnabledState));
        updateAllEntries(arp.getKey(), newEnabledState);
        this.mAppListChanged = true;
        applyUserAppState(packageName, newEnabledState);
        return true;
    }
}
