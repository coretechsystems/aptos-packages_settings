package com.android.settings.notification;

import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.Vibrator;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceCategory;
import android.preference.SeekBarVolumizer;
import android.preference.TwoStatePreference;
import android.provider.SearchIndexableResource;
import android.provider.Settings.Secure;
import android.provider.Settings.SettingNotFoundException;
import android.provider.Settings.System;
import android.util.Log;
import com.android.internal.widget.LockPatternUtils;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.Utils;
import com.android.settings.notification.VolumeSeekBarPreference.Callback;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settings.search.Indexable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class NotificationSettings extends SettingsPreferenceFragment implements Indexable {
    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER = new BaseSearchIndexProvider() {
        public List<SearchIndexableResource> getXmlResourcesToIndex(Context context, boolean enabled) {
            new SearchIndexableResource(context).xmlResId = R.xml.notification_settings;
            return Arrays.asList(new SearchIndexableResource[]{sir});
        }

        public List<String> getNonIndexableKeys(Context context) {
            ArrayList<String> rt = new ArrayList();
            if (Utils.isVoiceCapable(context)) {
                rt.add("notification_volume");
            } else {
                rt.add("ring_volume");
                rt.add("ringtone");
                rt.add("vibrate_when_ringing");
            }
            return rt;
        }
    };
    private Context mContext;
    private final H mHandler = new H();
    private DropDownPreference mLockscreen;
    private int mLockscreenSelectedValue;
    private final Runnable mLookupRingtoneNames = new Runnable() {
        public void run() {
            CharSequence summary;
            if (NotificationSettings.this.mPhoneRingtonePreference != null) {
                summary = NotificationSettings.updateRingtoneName(NotificationSettings.this.mContext, 1);
                if (summary != null) {
                    NotificationSettings.this.mHandler.obtainMessage(1, summary).sendToTarget();
                }
            }
            if (NotificationSettings.this.mNotificationRingtonePreference != null) {
                summary = NotificationSettings.updateRingtoneName(NotificationSettings.this.mContext, 2);
                if (summary != null) {
                    NotificationSettings.this.mHandler.obtainMessage(2, summary).sendToTarget();
                }
            }
        }
    };
    private Preference mNotificationAccess;
    private TwoStatePreference mNotificationPulse;
    private Preference mNotificationRingtonePreference;
    private PackageManager mPM;
    private Preference mPhoneRingtonePreference;
    private VolumeSeekBarPreference mRingOrNotificationPreference;
    private boolean mSecure;
    private final SettingsObserver mSettingsObserver = new SettingsObserver();
    private TwoStatePreference mVibrateWhenRinging;
    private Vibrator mVibrator;
    private boolean mVoiceCapable;
    private final VolumePreferenceCallback mVolumeCallback = new VolumePreferenceCallback();

    private final class H extends Handler {
        private H() {
            super(Looper.getMainLooper());
        }

        public void handleMessage(Message msg) {
            switch (msg.what) {
                case 1:
                    NotificationSettings.this.mPhoneRingtonePreference.setSummary((CharSequence) msg.obj);
                    return;
                case 2:
                    NotificationSettings.this.mNotificationRingtonePreference.setSummary((CharSequence) msg.obj);
                    return;
                case 3:
                    NotificationSettings.this.mVolumeCallback.stopSample();
                    return;
                case 4:
                    NotificationSettings.this.updateRingOrNotificationIcon(msg.arg1);
                    return;
                default:
                    return;
            }
        }
    }

    private final class SettingsObserver extends ContentObserver {
        private final Uri LOCK_SCREEN_PRIVATE_URI = Secure.getUriFor("lock_screen_allow_private_notifications");
        private final Uri LOCK_SCREEN_SHOW_URI = Secure.getUriFor("lock_screen_show_notifications");
        private final Uri NOTIFICATION_LIGHT_PULSE_URI = System.getUriFor("notification_light_pulse");
        private final Uri VIBRATE_WHEN_RINGING_URI = System.getUriFor("vibrate_when_ringing");

        public SettingsObserver() {
            super(NotificationSettings.this.mHandler);
        }

        public void register(boolean register) {
            ContentResolver cr = NotificationSettings.this.getContentResolver();
            if (register) {
                cr.registerContentObserver(this.VIBRATE_WHEN_RINGING_URI, false, this);
                cr.registerContentObserver(this.NOTIFICATION_LIGHT_PULSE_URI, false, this);
                cr.registerContentObserver(this.LOCK_SCREEN_PRIVATE_URI, false, this);
                cr.registerContentObserver(this.LOCK_SCREEN_SHOW_URI, false, this);
                return;
            }
            cr.unregisterContentObserver(this);
        }

        public void onChange(boolean selfChange, Uri uri) {
            super.onChange(selfChange, uri);
            if (this.VIBRATE_WHEN_RINGING_URI.equals(uri)) {
                NotificationSettings.this.updateVibrateWhenRinging();
            }
            if (this.NOTIFICATION_LIGHT_PULSE_URI.equals(uri)) {
                NotificationSettings.this.updatePulse();
            }
            if (this.LOCK_SCREEN_PRIVATE_URI.equals(uri) || this.LOCK_SCREEN_SHOW_URI.equals(uri)) {
                NotificationSettings.this.updateLockscreenNotifications();
            }
        }
    }

    private final class VolumePreferenceCallback implements Callback {
        private SeekBarVolumizer mCurrent;

        private VolumePreferenceCallback() {
        }

        public void onSampleStarting(SeekBarVolumizer sbv) {
            if (!(this.mCurrent == null || this.mCurrent == sbv)) {
                this.mCurrent.stopSample();
            }
            this.mCurrent = sbv;
            if (this.mCurrent != null) {
                NotificationSettings.this.mHandler.removeMessages(3);
                NotificationSettings.this.mHandler.sendEmptyMessageDelayed(3, 2000);
            }
        }

        public void onStreamValueChanged(int stream, int progress) {
            if (stream == 2) {
                NotificationSettings.this.mHandler.removeMessages(4);
                NotificationSettings.this.mHandler.obtainMessage(4, progress, 0).sendToTarget();
            }
        }

        public void stopSample() {
            if (this.mCurrent != null) {
                this.mCurrent.stopSample();
            }
        }
    }

    private static java.lang.CharSequence updateRingtoneName(android.content.Context r8, int r9) {
        /* JADX: method processing error */
/*
Error: java.util.NoSuchElementException
	at java.util.HashMap$HashIterator.nextNode(Unknown Source)
	at java.util.HashMap$KeyIterator.next(Unknown Source)
	at jadx.core.dex.visitors.blocksmaker.BlockFinallyExtract.applyRemove(BlockFinallyExtract.java:535)
	at jadx.core.dex.visitors.blocksmaker.BlockFinallyExtract.extractFinally(BlockFinallyExtract.java:175)
	at jadx.core.dex.visitors.blocksmaker.BlockFinallyExtract.processExceptionHandler(BlockFinallyExtract.java:79)
	at jadx.core.dex.visitors.blocksmaker.BlockFinallyExtract.visit(BlockFinallyExtract.java:51)
	at jadx.core.dex.visitors.DepthTraversal.visit(DepthTraversal.java:31)
	at jadx.core.dex.visitors.DepthTraversal.visit(DepthTraversal.java:17)
	at jadx.core.ProcessClass.process(ProcessClass.java:37)
	at jadx.api.JadxDecompiler.processClass(JadxDecompiler.java:306)
	at jadx.api.JavaClass.decompile(JavaClass.java:62)
*/
        /*
        r7 = 0;
        if (r8 != 0) goto L_0x000b;
    L_0x0003:
        r0 = "NotificationSettings";
        r2 = "Unable to update ringtone name, no context provided";
        android.util.Log.e(r0, r2);
    L_0x000a:
        return r7;
    L_0x000b:
        r1 = android.media.RingtoneManager.getActualDefaultRingtoneUri(r8, r9);
        r0 = 17040547; // 0x10404a3 float:2.4247898E-38 double:8.419149E-317;
        r7 = r8.getString(r0);
        if (r1 != 0) goto L_0x0020;
    L_0x0018:
        r0 = 17040545; // 0x10404a1 float:2.4247892E-38 double:8.419148E-317;
        r7 = r8.getString(r0);
        goto L_0x000a;
    L_0x0020:
        r6 = 0;
        r0 = "media";	 Catch:{ SQLiteException -> 0x0073, IllegalArgumentException -> 0x007a, all -> 0x0081 }
        r2 = r1.getAuthority();	 Catch:{ SQLiteException -> 0x0073, IllegalArgumentException -> 0x007a, all -> 0x0081 }
        r0 = r0.equals(r2);	 Catch:{ SQLiteException -> 0x0073, IllegalArgumentException -> 0x007a, all -> 0x0081 }
        if (r0 == 0) goto L_0x0053;	 Catch:{ SQLiteException -> 0x0073, IllegalArgumentException -> 0x007a, all -> 0x0081 }
    L_0x002d:
        r0 = r8.getContentResolver();	 Catch:{ SQLiteException -> 0x0073, IllegalArgumentException -> 0x007a, all -> 0x0081 }
        r2 = 1;	 Catch:{ SQLiteException -> 0x0073, IllegalArgumentException -> 0x007a, all -> 0x0081 }
        r2 = new java.lang.String[r2];	 Catch:{ SQLiteException -> 0x0073, IllegalArgumentException -> 0x007a, all -> 0x0081 }
        r3 = 0;	 Catch:{ SQLiteException -> 0x0073, IllegalArgumentException -> 0x007a, all -> 0x0081 }
        r4 = "title";	 Catch:{ SQLiteException -> 0x0073, IllegalArgumentException -> 0x007a, all -> 0x0081 }
        r2[r3] = r4;	 Catch:{ SQLiteException -> 0x0073, IllegalArgumentException -> 0x007a, all -> 0x0081 }
        r3 = 0;	 Catch:{ SQLiteException -> 0x0073, IllegalArgumentException -> 0x007a, all -> 0x0081 }
        r4 = 0;	 Catch:{ SQLiteException -> 0x0073, IllegalArgumentException -> 0x007a, all -> 0x0081 }
        r5 = 0;	 Catch:{ SQLiteException -> 0x0073, IllegalArgumentException -> 0x007a, all -> 0x0081 }
        r6 = r0.query(r1, r2, r3, r4, r5);	 Catch:{ SQLiteException -> 0x0073, IllegalArgumentException -> 0x007a, all -> 0x0081 }
    L_0x0040:
        if (r6 == 0) goto L_0x004d;	 Catch:{ SQLiteException -> 0x0073, IllegalArgumentException -> 0x007a, all -> 0x0081 }
    L_0x0042:
        r0 = r6.moveToFirst();	 Catch:{ SQLiteException -> 0x0073, IllegalArgumentException -> 0x007a, all -> 0x0081 }
        if (r0 == 0) goto L_0x004d;	 Catch:{ SQLiteException -> 0x0073, IllegalArgumentException -> 0x007a, all -> 0x0081 }
    L_0x0048:
        r0 = 0;	 Catch:{ SQLiteException -> 0x0073, IllegalArgumentException -> 0x007a, all -> 0x0081 }
        r7 = r6.getString(r0);	 Catch:{ SQLiteException -> 0x0073, IllegalArgumentException -> 0x007a, all -> 0x0081 }
    L_0x004d:
        if (r6 == 0) goto L_0x000a;
    L_0x004f:
        r6.close();
        goto L_0x000a;
    L_0x0053:
        r0 = "content";	 Catch:{ SQLiteException -> 0x0073, IllegalArgumentException -> 0x007a, all -> 0x0081 }
        r2 = r1.getScheme();	 Catch:{ SQLiteException -> 0x0073, IllegalArgumentException -> 0x007a, all -> 0x0081 }
        r0 = r0.equals(r2);	 Catch:{ SQLiteException -> 0x0073, IllegalArgumentException -> 0x007a, all -> 0x0081 }
        if (r0 == 0) goto L_0x0040;	 Catch:{ SQLiteException -> 0x0073, IllegalArgumentException -> 0x007a, all -> 0x0081 }
    L_0x005f:
        r0 = r8.getContentResolver();	 Catch:{ SQLiteException -> 0x0073, IllegalArgumentException -> 0x007a, all -> 0x0081 }
        r2 = 1;	 Catch:{ SQLiteException -> 0x0073, IllegalArgumentException -> 0x007a, all -> 0x0081 }
        r2 = new java.lang.String[r2];	 Catch:{ SQLiteException -> 0x0073, IllegalArgumentException -> 0x007a, all -> 0x0081 }
        r3 = 0;	 Catch:{ SQLiteException -> 0x0073, IllegalArgumentException -> 0x007a, all -> 0x0081 }
        r4 = "_display_name";	 Catch:{ SQLiteException -> 0x0073, IllegalArgumentException -> 0x007a, all -> 0x0081 }
        r2[r3] = r4;	 Catch:{ SQLiteException -> 0x0073, IllegalArgumentException -> 0x007a, all -> 0x0081 }
        r3 = 0;	 Catch:{ SQLiteException -> 0x0073, IllegalArgumentException -> 0x007a, all -> 0x0081 }
        r4 = 0;	 Catch:{ SQLiteException -> 0x0073, IllegalArgumentException -> 0x007a, all -> 0x0081 }
        r5 = 0;	 Catch:{ SQLiteException -> 0x0073, IllegalArgumentException -> 0x007a, all -> 0x0081 }
        r6 = r0.query(r1, r2, r3, r4, r5);	 Catch:{ SQLiteException -> 0x0073, IllegalArgumentException -> 0x007a, all -> 0x0081 }
        goto L_0x0040;
    L_0x0073:
        r0 = move-exception;
        if (r6 == 0) goto L_0x000a;
    L_0x0076:
        r6.close();
        goto L_0x000a;
    L_0x007a:
        r0 = move-exception;
        if (r6 == 0) goto L_0x000a;
    L_0x007d:
        r6.close();
        goto L_0x000a;
    L_0x0081:
        r0 = move-exception;
        if (r6 == 0) goto L_0x0087;
    L_0x0084:
        r6.close();
    L_0x0087:
        throw r0;
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.settings.notification.NotificationSettings.updateRingtoneName(android.content.Context, int):java.lang.CharSequence");
    }

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.mContext = getActivity();
        this.mPM = this.mContext.getPackageManager();
        this.mVoiceCapable = Utils.isVoiceCapable(this.mContext);
        this.mSecure = new LockPatternUtils(getActivity()).isSecure();
        this.mVibrator = (Vibrator) getActivity().getSystemService("vibrator");
        if (!(this.mVibrator == null || this.mVibrator.hasVibrator())) {
            this.mVibrator = null;
        }
        addPreferencesFromResource(R.xml.notification_settings);
        PreferenceCategory sound = (PreferenceCategory) findPreference("sound");
        initVolumePreference("media_volume", 3);
        initVolumePreference("alarm_volume", 4);
        if (this.mVoiceCapable) {
            this.mRingOrNotificationPreference = initVolumePreference("ring_volume", 2);
            sound.removePreference(sound.findPreference("notification_volume"));
        } else {
            this.mRingOrNotificationPreference = initVolumePreference("notification_volume", 5);
            sound.removePreference(sound.findPreference("ring_volume"));
        }
        initRingtones(sound);
        initVibrateWhenRinging(sound);
        PreferenceCategory notification = (PreferenceCategory) findPreference("notification");
        initPulse(notification);
        initLockscreenNotifications(notification);
        this.mNotificationAccess = findPreference("manage_notification_access");
        refreshNotificationListeners();
    }

    public void onResume() {
        super.onResume();
        refreshNotificationListeners();
        lookupRingtoneNames();
        this.mSettingsObserver.register(true);
    }

    public void onPause() {
        super.onPause();
        this.mVolumeCallback.stopSample();
        this.mSettingsObserver.register(false);
    }

    private VolumeSeekBarPreference initVolumePreference(String key, int stream) {
        VolumeSeekBarPreference volumePref = (VolumeSeekBarPreference) findPreference(key);
        volumePref.setCallback(this.mVolumeCallback);
        volumePref.setStream(stream);
        return volumePref;
    }

    private void updateRingOrNotificationIcon(int progress) {
        VolumeSeekBarPreference volumeSeekBarPreference = this.mRingOrNotificationPreference;
        int i = progress > 0 ? R.drawable.ring_notif : this.mVibrator == null ? R.drawable.ring_notif_mute : R.drawable.ring_notif_vibrate;
        volumeSeekBarPreference.showIcon(i);
    }

    private void initRingtones(PreferenceCategory root) {
        this.mPhoneRingtonePreference = root.findPreference("ringtone");
        if (!(this.mPhoneRingtonePreference == null || this.mVoiceCapable)) {
            root.removePreference(this.mPhoneRingtonePreference);
            this.mPhoneRingtonePreference = null;
        }
        this.mNotificationRingtonePreference = root.findPreference("notification_ringtone");
    }

    private void lookupRingtoneNames() {
        AsyncTask.execute(this.mLookupRingtoneNames);
    }

    private void initVibrateWhenRinging(PreferenceCategory root) {
        this.mVibrateWhenRinging = (TwoStatePreference) root.findPreference("vibrate_when_ringing");
        if (this.mVibrateWhenRinging == null) {
            Log.i("NotificationSettings", "Preference not found: vibrate_when_ringing");
        } else if (this.mVoiceCapable) {
            this.mVibrateWhenRinging.setPersistent(false);
            updateVibrateWhenRinging();
            this.mVibrateWhenRinging.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    return System.putInt(NotificationSettings.this.getContentResolver(), "vibrate_when_ringing", ((Boolean) newValue).booleanValue() ? 1 : 0);
                }
            });
        } else {
            root.removePreference(this.mVibrateWhenRinging);
            this.mVibrateWhenRinging = null;
        }
    }

    private void updateVibrateWhenRinging() {
        boolean z = false;
        if (this.mVibrateWhenRinging != null) {
            TwoStatePreference twoStatePreference = this.mVibrateWhenRinging;
            if (System.getInt(getContentResolver(), "vibrate_when_ringing", 0) != 0) {
                z = true;
            }
            twoStatePreference.setChecked(z);
        }
    }

    private void initPulse(PreferenceCategory parent) {
        this.mNotificationPulse = (TwoStatePreference) parent.findPreference("notification_pulse");
        if (this.mNotificationPulse == null) {
            Log.i("NotificationSettings", "Preference not found: notification_pulse");
        } else if (getResources().getBoolean(17956910)) {
            updatePulse();
            this.mNotificationPulse.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    return System.putInt(NotificationSettings.this.getContentResolver(), "notification_light_pulse", ((Boolean) newValue).booleanValue() ? 1 : 0);
                }
            });
        } else {
            parent.removePreference(this.mNotificationPulse);
        }
    }

    private void updatePulse() {
        boolean z = true;
        if (this.mNotificationPulse != null) {
            try {
                TwoStatePreference twoStatePreference = this.mNotificationPulse;
                if (System.getInt(getContentResolver(), "notification_light_pulse") != 1) {
                    z = false;
                }
                twoStatePreference.setChecked(z);
            } catch (SettingNotFoundException e) {
                Log.e("NotificationSettings", "notification_light_pulse not found");
            }
        }
    }

    private void initLockscreenNotifications(PreferenceCategory parent) {
        this.mLockscreen = (DropDownPreference) parent.findPreference("lock_screen_notifications");
        if (this.mLockscreen == null) {
            Log.i("NotificationSettings", "Preference not found: lock_screen_notifications");
            return;
        }
        this.mLockscreen.addItem((int) R.string.lock_screen_notifications_summary_show, Integer.valueOf(R.string.lock_screen_notifications_summary_show));
        if (this.mSecure) {
            this.mLockscreen.addItem((int) R.string.lock_screen_notifications_summary_hide, Integer.valueOf(R.string.lock_screen_notifications_summary_hide));
        }
        this.mLockscreen.addItem((int) R.string.lock_screen_notifications_summary_disable, Integer.valueOf(R.string.lock_screen_notifications_summary_disable));
        updateLockscreenNotifications();
        this.mLockscreen.setCallback(new DropDownPreference.Callback() {
            public boolean onItemSelected(int pos, Object value) {
                int i = 0;
                int val = ((Integer) value).intValue();
                if (val != NotificationSettings.this.mLockscreenSelectedValue) {
                    boolean enabled;
                    boolean show;
                    int i2;
                    if (val != R.string.lock_screen_notifications_summary_disable) {
                        enabled = true;
                    } else {
                        enabled = false;
                    }
                    if (val == R.string.lock_screen_notifications_summary_show) {
                        show = true;
                    } else {
                        show = false;
                    }
                    ContentResolver access$1000 = NotificationSettings.this.getContentResolver();
                    String str = "lock_screen_allow_private_notifications";
                    if (show) {
                        i2 = 1;
                    } else {
                        i2 = 0;
                    }
                    Secure.putInt(access$1000, str, i2);
                    ContentResolver access$1100 = NotificationSettings.this.getContentResolver();
                    String str2 = "lock_screen_show_notifications";
                    if (enabled) {
                        i = 1;
                    }
                    Secure.putInt(access$1100, str2, i);
                    NotificationSettings.this.mLockscreenSelectedValue = val;
                }
                return true;
            }
        });
    }

    private void updateLockscreenNotifications() {
        if (this.mLockscreen != null) {
            boolean enabled = getLockscreenNotificationsEnabled();
            boolean allowPrivate = !this.mSecure || getLockscreenAllowPrivateNotifications();
            int i = !enabled ? R.string.lock_screen_notifications_summary_disable : allowPrivate ? R.string.lock_screen_notifications_summary_show : R.string.lock_screen_notifications_summary_hide;
            this.mLockscreenSelectedValue = i;
            this.mLockscreen.setSelectedValue(Integer.valueOf(this.mLockscreenSelectedValue));
        }
    }

    private boolean getLockscreenNotificationsEnabled() {
        return Secure.getInt(getContentResolver(), "lock_screen_show_notifications", 0) != 0;
    }

    private boolean getLockscreenAllowPrivateNotifications() {
        return Secure.getInt(getContentResolver(), "lock_screen_allow_private_notifications", 0) != 0;
    }

    private void refreshNotificationListeners() {
        if (this.mNotificationAccess == null) {
            return;
        }
        if (NotificationAccessSettings.getListenersCount(this.mPM) == 0) {
            getPreferenceScreen().removePreference(this.mNotificationAccess);
            return;
        }
        int n = NotificationAccessSettings.getEnabledListenersCount(this.mContext);
        if (n == 0) {
            this.mNotificationAccess.setSummary(getResources().getString(R.string.manage_notification_access_summary_zero));
            return;
        }
        this.mNotificationAccess.setSummary(String.format(getResources().getQuantityString(R.plurals.manage_notification_access_summary_nonzero, n, new Object[]{Integer.valueOf(n)}), new Object[0]));
    }
}
