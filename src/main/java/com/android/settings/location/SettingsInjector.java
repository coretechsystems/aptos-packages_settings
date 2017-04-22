package com.android.settings.location;

import android.R;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.content.res.XmlResourceParser;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Messenger;
import android.os.Process;
import android.os.SystemClock;
import android.preference.Preference;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Xml;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import org.xmlpull.v1.XmlPullParserException;

class SettingsInjector {
    private final Context mContext;
    private final Handler mHandler = new StatusLoadingHandler();
    private final Set<Setting> mSettings = new HashSet();

    private final class Setting {
        public final Preference preference;
        public final InjectedSetting setting;
        public long startMillis;

        private Setting(InjectedSetting setting, Preference preference) {
            this.setting = setting;
            this.preference = preference;
        }

        public String toString() {
            return "Setting{setting=" + this.setting + ", preference=" + this.preference + '}';
        }

        public boolean equals(Object o) {
            return this == o || ((o instanceof Setting) && this.setting.equals(((Setting) o).setting));
        }

        public int hashCode() {
            return this.setting.hashCode();
        }

        public void startService() {
            Handler handler = new Handler() {
                public void handleMessage(Message msg) {
                    Bundle bundle = msg.getData();
                    boolean enabled = bundle.getBoolean("enabled", true);
                    if (Log.isLoggable("SettingsInjector", 3)) {
                        Log.d("SettingsInjector", Setting.this.setting + ": received " + msg + ", bundle: " + bundle);
                    }
                    Setting.this.preference.setSummary(null);
                    Setting.this.preference.setEnabled(enabled);
                    SettingsInjector.this.mHandler.sendMessage(SettingsInjector.this.mHandler.obtainMessage(2, Setting.this));
                }
            };
            Messenger messenger = new Messenger(handler);
            Intent intent = this.setting.getServiceIntent();
            intent.putExtra("messenger", messenger);
            if (Log.isLoggable("SettingsInjector", 3)) {
                Log.d("SettingsInjector", this.setting + ": sending update intent: " + intent + ", handler: " + handler);
                this.startMillis = SystemClock.elapsedRealtime();
            } else {
                this.startMillis = 0;
            }
            SettingsInjector.this.mContext.startServiceAsUser(intent, Process.myUserHandle());
        }

        public long getElapsedTime() {
            return SystemClock.elapsedRealtime() - this.startMillis;
        }

        public void maybeLogElapsedTime() {
            if (Log.isLoggable("SettingsInjector", 3) && this.startMillis != 0) {
                Log.d("SettingsInjector", this + " update took " + getElapsedTime() + " millis");
            }
        }
    }

    private final class StatusLoadingHandler extends Handler {
        private boolean mReloadRequested;
        private Set<Setting> mSettingsBeingLoaded;
        private Set<Setting> mSettingsToLoad;
        private Set<Setting> mTimedOutSettings;

        private StatusLoadingHandler() {
            this.mSettingsToLoad = new HashSet();
            this.mSettingsBeingLoaded = new HashSet();
            this.mTimedOutSettings = new HashSet();
        }

        public void handleMessage(Message msg) {
            if (Log.isLoggable("SettingsInjector", 3)) {
                Log.d("SettingsInjector", "handleMessage start: " + msg + ", " + this);
            }
            switch (msg.what) {
                case 1:
                    this.mReloadRequested = true;
                    break;
                case 2:
                    Setting receivedSetting = msg.obj;
                    receivedSetting.maybeLogElapsedTime();
                    this.mSettingsBeingLoaded.remove(receivedSetting);
                    this.mTimedOutSettings.remove(receivedSetting);
                    removeMessages(3, receivedSetting);
                    break;
                case 3:
                    Setting timedOutSetting = msg.obj;
                    this.mSettingsBeingLoaded.remove(timedOutSetting);
                    this.mTimedOutSettings.add(timedOutSetting);
                    if (Log.isLoggable("SettingsInjector", 5)) {
                        Log.w("SettingsInjector", "Timed out after " + timedOutSetting.getElapsedTime() + " millis trying to get status for: " + timedOutSetting);
                        break;
                    }
                    break;
                default:
                    Log.wtf("SettingsInjector", "Unexpected what: " + msg);
                    break;
            }
            if (this.mSettingsBeingLoaded.size() <= 0 && this.mTimedOutSettings.size() <= 1) {
                if (this.mReloadRequested && this.mSettingsToLoad.isEmpty() && this.mSettingsBeingLoaded.isEmpty() && this.mTimedOutSettings.isEmpty()) {
                    if (Log.isLoggable("SettingsInjector", 2)) {
                        Log.v("SettingsInjector", "reloading because idle and reload requesteed " + msg + ", " + this);
                    }
                    this.mSettingsToLoad.addAll(SettingsInjector.this.mSettings);
                    this.mReloadRequested = false;
                }
                Iterator<Setting> iter = this.mSettingsToLoad.iterator();
                if (iter.hasNext()) {
                    Setting setting = (Setting) iter.next();
                    iter.remove();
                    setting.startService();
                    this.mSettingsBeingLoaded.add(setting);
                    sendMessageDelayed(obtainMessage(3, setting), 1000);
                    if (Log.isLoggable("SettingsInjector", 3)) {
                        Log.d("SettingsInjector", "handleMessage end " + msg + ", " + this + ", started loading " + setting);
                    }
                } else if (Log.isLoggable("SettingsInjector", 2)) {
                    Log.v("SettingsInjector", "nothing left to do for " + msg + ", " + this);
                }
            } else if (Log.isLoggable("SettingsInjector", 2)) {
                Log.v("SettingsInjector", "too many services already live for " + msg + ", " + this);
            }
        }

        public String toString() {
            return "StatusLoadingHandler{mSettingsToLoad=" + this.mSettingsToLoad + ", mSettingsBeingLoaded=" + this.mSettingsBeingLoaded + ", mTimedOutSettings=" + this.mTimedOutSettings + ", mReloadRequested=" + this.mReloadRequested + '}';
        }
    }

    public SettingsInjector(Context context) {
        this.mContext = context;
    }

    private List<InjectedSetting> getSettings() {
        PackageManager pm = this.mContext.getPackageManager();
        List<ResolveInfo> resolveInfos = pm.queryIntentServices(new Intent("android.location.SettingInjectorService"), 128);
        if (Log.isLoggable("SettingsInjector", 3)) {
            Log.d("SettingsInjector", "Found services: " + resolveInfos);
        }
        List<InjectedSetting> settings = new ArrayList(resolveInfos.size());
        for (ResolveInfo resolveInfo : resolveInfos) {
            try {
                InjectedSetting setting = parseServiceInfo(resolveInfo, pm);
                if (setting == null) {
                    Log.w("SettingsInjector", "Unable to load service info " + resolveInfo);
                } else {
                    settings.add(setting);
                }
            } catch (XmlPullParserException e) {
                Log.w("SettingsInjector", "Unable to load service info " + resolveInfo, e);
            } catch (IOException e2) {
                Log.w("SettingsInjector", "Unable to load service info " + resolveInfo, e2);
            }
        }
        if (Log.isLoggable("SettingsInjector", 3)) {
            Log.d("SettingsInjector", "Loaded settings: " + settings);
        }
        return settings;
    }

    private static InjectedSetting parseServiceInfo(ResolveInfo service, PackageManager pm) throws XmlPullParserException, IOException {
        ServiceInfo si = service.serviceInfo;
        ApplicationInfo ai = si.applicationInfo;
        if ((ai.flags & 1) == 0 && Log.isLoggable("SettingsInjector", 5)) {
            Log.w("SettingsInjector", "Ignoring attempt to inject setting from app not in system image: " + service);
            return null;
        }
        XmlResourceParser parser = null;
        try {
            parser = si.loadXmlMetaData(pm, "android.location.SettingInjectorService");
            if (parser == null) {
                throw new XmlPullParserException("No android.location.SettingInjectorService meta-data for " + service + ": " + si);
            }
            AttributeSet attrs = Xml.asAttributeSet(parser);
            int type;
            do {
                type = parser.next();
                if (type == 1) {
                    break;
                }
            } while (type != 2);
            if ("injected-location-setting".equals(parser.getName())) {
                InjectedSetting parseAttributes = parseAttributes(si.packageName, si.name, pm.getResourcesForApplication(ai), attrs);
                if (parser == null) {
                    return parseAttributes;
                }
                parser.close();
                return parseAttributes;
            }
            throw new XmlPullParserException("Meta-data does not start with injected-location-setting tag");
        } catch (NameNotFoundException e) {
            throw new XmlPullParserException("Unable to load resources for package " + si.packageName);
        } catch (Throwable th) {
            if (parser != null) {
                parser.close();
            }
        }
    }

    private static InjectedSetting parseAttributes(String packageName, String className, Resources res, AttributeSet attrs) {
        TypedArray sa = res.obtainAttributes(attrs, R.styleable.SettingInjectorService);
        try {
            String title = sa.getString(1);
            int iconId = sa.getResourceId(0, 0);
            String settingsActivity = sa.getString(2);
            if (Log.isLoggable("SettingsInjector", 3)) {
                Log.d("SettingsInjector", "parsed title: " + title + ", iconId: " + iconId + ", settingsActivity: " + settingsActivity);
            }
            InjectedSetting newInstance = InjectedSetting.newInstance(packageName, className, title, iconId, settingsActivity);
            return newInstance;
        } finally {
            sa.recycle();
        }
    }

    public List<Preference> getInjectedSettings() {
        Iterable<InjectedSetting> settings = getSettings();
        ArrayList<Preference> prefs = new ArrayList();
        for (InjectedSetting setting : settings) {
            this.mSettings.add(new Setting(setting, addServiceSetting(prefs, setting)));
        }
        reloadStatusMessages();
        return prefs;
    }

    public void reloadStatusMessages() {
        if (Log.isLoggable("SettingsInjector", 3)) {
            Log.d("SettingsInjector", "reloadingStatusMessages: " + this.mSettings);
        }
        this.mHandler.sendMessage(this.mHandler.obtainMessage(1));
    }

    private Preference addServiceSetting(List<Preference> prefs, InjectedSetting info) {
        Preference pref = new DimmableIconPreference(this.mContext);
        pref.setTitle(info.title);
        pref.setSummary(null);
        pref.setIcon(this.mContext.getPackageManager().getDrawable(info.packageName, info.iconId, null));
        Intent settingIntent = new Intent();
        settingIntent.setClassName(info.packageName, info.settingsActivity);
        settingIntent.setFlags(268435456);
        pref.setIntent(settingIntent);
        prefs.add(pref);
        return pref;
    }
}
