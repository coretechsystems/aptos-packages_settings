package com.android.settings.notification;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.SwitchPreference;
import android.provider.Settings.Secure;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import com.android.internal.widget.LockPatternUtils;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.Utils;
import com.android.settings.notification.NotificationAppList.AppRow;
import com.android.settings.notification.NotificationAppList.Backend;

public class AppNotificationSettings extends SettingsPreferenceFragment {
    private static final boolean DEBUG = Log.isLoggable("AppNotificationSettings", 3);
    private AppRow mAppRow;
    private final Backend mBackend = new Backend();
    private SwitchPreference mBlock;
    private Context mContext;
    private boolean mCreated;
    private SwitchPreference mPriority;
    private SwitchPreference mSensitive;

    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        if (DEBUG) {
            Log.d("AppNotificationSettings", "onActivityCreated mCreated=" + this.mCreated);
        }
        if (this.mCreated) {
            Log.w("AppNotificationSettings", "onActivityCreated: ignoring duplicate call");
            return;
        }
        this.mCreated = true;
        if (this.mAppRow != null) {
            ViewGroup contentParent = (ViewGroup) getActivity().findViewById(R.id.main_content).getParent();
            View bar = getActivity().getLayoutInflater().inflate(R.layout.app_notification_header, contentParent, false);
            ((ImageView) bar.findViewById(R.id.app_icon)).setImageDrawable(this.mAppRow.icon);
            ((TextView) bar.findViewById(R.id.app_name)).setText(this.mAppRow.label);
            View appSettings = bar.findViewById(R.id.app_settings);
            if (this.mAppRow.settingsIntent == null) {
                appSettings.setVisibility(8);
            } else {
                appSettings.setClickable(true);
                appSettings.setOnClickListener(new OnClickListener() {
                    public void onClick(View v) {
                        AppNotificationSettings.this.mContext.startActivity(AppNotificationSettings.this.mAppRow.settingsIntent);
                    }
                });
            }
            contentParent.addView(bar, 0);
        }
    }

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.mContext = getActivity();
        Intent intent = getActivity().getIntent();
        if (DEBUG) {
            Log.d("AppNotificationSettings", "onCreate getIntent()=" + intent);
        }
        if (intent == null) {
            Log.w("AppNotificationSettings", "No intent");
            toastAndFinish();
            return;
        }
        final int uid = intent.getIntExtra("app_uid", -1);
        final String pkg = intent.getStringExtra("app_package");
        if (uid == -1 || TextUtils.isEmpty(pkg)) {
            Log.w("AppNotificationSettings", "Missing extras: app_package was " + pkg + ", " + "app_uid" + " was " + uid);
            toastAndFinish();
            return;
        }
        if (DEBUG) {
            Log.d("AppNotificationSettings", "Load details for pkg=" + pkg + " uid=" + uid);
        }
        PackageManager pm = getPackageManager();
        PackageInfo info = findPackageInfo(pm, pkg, uid);
        if (info == null) {
            Log.w("AppNotificationSettings", "Failed to find package info: app_package was " + pkg + ", " + "app_uid" + " was " + uid);
            toastAndFinish();
            return;
        }
        addPreferencesFromResource(R.xml.app_notification_settings);
        this.mBlock = (SwitchPreference) findPreference("block");
        this.mPriority = (SwitchPreference) findPreference("priority");
        this.mSensitive = (SwitchPreference) findPreference("sensitive");
        boolean secure = new LockPatternUtils(getActivity()).isSecure();
        boolean enabled = getLockscreenNotificationsEnabled();
        boolean allowPrivate = getLockscreenAllowPrivateNotifications();
        if (!(secure && enabled && allowPrivate)) {
            getPreferenceScreen().removePreference(this.mSensitive);
        }
        this.mAppRow = NotificationAppList.loadAppRow(pm, info.applicationInfo, this.mBackend);
        if (!intent.hasExtra("has_settings_intent")) {
            ArrayMap<String, AppRow> rows = new ArrayMap();
            rows.put(this.mAppRow.pkg, this.mAppRow);
            NotificationAppList.collectConfigActivities(getPackageManager(), rows);
        } else if (intent.getBooleanExtra("has_settings_intent", false)) {
            this.mAppRow.settingsIntent = (Intent) intent.getParcelableExtra("settings_intent");
        }
        this.mBlock.setChecked(this.mAppRow.banned);
        this.mPriority.setChecked(this.mAppRow.priority);
        if (this.mSensitive != null) {
            this.mSensitive.setChecked(this.mAppRow.sensitive);
        }
        this.mBlock.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                return AppNotificationSettings.this.mBackend.setNotificationsBanned(pkg, uid, ((Boolean) newValue).booleanValue());
            }
        });
        this.mPriority.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                return AppNotificationSettings.this.mBackend.setHighPriority(pkg, uid, ((Boolean) newValue).booleanValue());
            }
        });
        if (this.mSensitive != null) {
            this.mSensitive.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    return AppNotificationSettings.this.mBackend.setSensitive(pkg, uid, ((Boolean) newValue).booleanValue());
                }
            });
        }
        if (Utils.isSystemPackage(pm, info)) {
            getPreferenceScreen().removePreference(this.mBlock);
            this.mPriority.setDependency(null);
        }
    }

    private boolean getLockscreenNotificationsEnabled() {
        return Secure.getInt(getContentResolver(), "lock_screen_show_notifications", 0) != 0;
    }

    private boolean getLockscreenAllowPrivateNotifications() {
        return Secure.getInt(getContentResolver(), "lock_screen_allow_private_notifications", 0) != 0;
    }

    private void toastAndFinish() {
        Toast.makeText(this.mContext, R.string.app_not_found_dlg_text, 0).show();
        getActivity().finish();
    }

    private static PackageInfo findPackageInfo(PackageManager pm, String pkg, int uid) {
        String[] packages = pm.getPackagesForUid(uid);
        if (!(packages == null || pkg == null)) {
            int N = packages.length;
            int i = 0;
            while (i < N) {
                if (pkg.equals(packages[i])) {
                    try {
                        return pm.getPackageInfo(pkg, 64);
                    } catch (NameNotFoundException e) {
                        Log.w("AppNotificationSettings", "Failed to load package " + pkg, e);
                    }
                } else {
                    i++;
                }
            }
        }
        return null;
    }
}
