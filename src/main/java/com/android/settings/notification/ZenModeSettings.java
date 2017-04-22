package com.android.settings.notification;

import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.FragmentManager;
import android.app.INotificationManager.Stub;
import android.app.TimePickerDialog;
import android.app.TimePickerDialog.OnTimeSetListener;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnClickListener;
import android.content.DialogInterface.OnDismissListener;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.ServiceManager;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceCategory;
import android.preference.PreferenceScreen;
import android.preference.SwitchPreference;
import android.provider.Settings.Global;
import android.service.notification.Condition;
import android.service.notification.ZenModeConfig;
import android.text.format.DateFormat;
import android.util.Log;
import android.util.SparseArray;
import android.widget.ScrollView;
import android.widget.TimePicker;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.Utils;
import com.android.settings.notification.DropDownPreference.Callback;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settings.search.Indexable;
import com.android.settings.search.Indexable.SearchIndexProvider;
import com.android.settings.search.SearchIndexableRaw;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Objects;

public class ZenModeSettings extends SettingsPreferenceFragment implements Indexable {
    private static final SimpleDateFormat DAY_FORMAT = new SimpleDateFormat("EEE");
    private static final SettingPrefWithCallback PREF_ZEN_MODE = new SettingPrefWithCallback(1, "zen_mode", "zen_mode", 0, 0, 1, 2) {
        protected String getCaption(Resources res, int value) {
            switch (value) {
                case 1:
                    return res.getString(R.string.zen_mode_option_important_interruptions);
                case 2:
                    return res.getString(R.string.zen_mode_option_no_interruptions);
                default:
                    return res.getString(R.string.zen_mode_option_off);
            }
        }
    };
    public static final SearchIndexProvider SEARCH_INDEX_DATA_PROVIDER = new BaseSearchIndexProvider() {
        public List<SearchIndexableRaw> getRawDataToIndex(Context context, boolean enabled) {
            SparseArray<String> keyTitles = ZenModeSettings.allKeyTitles(context);
            int N = keyTitles.size();
            List<SearchIndexableRaw> result = new ArrayList(N);
            Resources res = context.getResources();
            for (int i = 0; i < N; i++) {
                SearchIndexableRaw data = new SearchIndexableRaw(context);
                data.key = (String) keyTitles.valueAt(i);
                data.title = res.getString(keyTitles.keyAt(i));
                data.screenTitle = res.getString(R.string.zen_mode_settings_title);
                result.add(data);
            }
            return result;
        }

        public List<String> getNonIndexableKeys(Context context) {
            ArrayList<String> rt = new ArrayList();
            if (!Utils.isVoiceCapable(context)) {
                rt.add("phone_calls");
            }
            return rt;
        }
    };
    private PreferenceCategory mAutomationCategory;
    private SwitchPreference mCalls;
    private Preference mConditionProviders;
    private ZenModeConfig mConfig;
    private Context mContext;
    private Preference mDays;
    private AlertDialog mDialog;
    private boolean mDisableListeners;
    private TimePickerPreference mEnd;
    private Preference mEntry;
    private SwitchPreference mEvents;
    private final Handler mHandler = new Handler();
    private SwitchPreference mMessages;
    private PackageManager mPM;
    private final SettingsObserver mSettingsObserver = new SettingsObserver();
    private DropDownPreference mStarred;
    private TimePickerPreference mStart;

    private static class SettingPrefWithCallback extends SettingPref {
        private Callback mCallback;
        private int mValue;

        public interface Callback {
            void onSettingSelected(int i);
        }

        public SettingPrefWithCallback(int type, String key, String setting, int def, int... values) {
            super(type, key, setting, def, values);
        }

        public void setCallback(Callback callback) {
            this.mCallback = callback;
        }

        public void update(Context context) {
            this.mValue = getValue(context);
            super.update(context);
        }

        protected boolean setSetting(Context context, int value) {
            if (value == this.mValue) {
                return true;
            }
            this.mValue = value;
            if (this.mCallback != null) {
                this.mCallback.onSettingSelected(value);
            }
            return super.setSetting(context, value);
        }

        public Preference init(SettingsPreferenceFragment settings) {
            Preference ret = super.init(settings);
            this.mValue = getValue(settings.getActivity());
            return ret;
        }

        public boolean setValueWithoutCallback(Context context, int value) {
            this.mValue = value;
            return SettingPref.putInt(this.mType, context.getContentResolver(), this.mSetting, value);
        }

        public int getValue(Context context) {
            return SettingPref.getInt(this.mType, context.getContentResolver(), this.mSetting, this.mDefault);
        }
    }

    private final class SettingsObserver extends ContentObserver {
        private final Uri ZEN_MODE_CONFIG_ETAG_URI = Global.getUriFor("zen_mode_config_etag");
        private final Uri ZEN_MODE_URI = Global.getUriFor("zen_mode");

        public SettingsObserver() {
            super(ZenModeSettings.this.mHandler);
        }

        public void register() {
            ZenModeSettings.this.getContentResolver().registerContentObserver(this.ZEN_MODE_URI, false, this);
            ZenModeSettings.this.getContentResolver().registerContentObserver(this.ZEN_MODE_CONFIG_ETAG_URI, false, this);
        }

        public void unregister() {
            ZenModeSettings.this.getContentResolver().unregisterContentObserver(this);
        }

        public void onChange(boolean selfChange, Uri uri) {
            super.onChange(selfChange, uri);
            if (this.ZEN_MODE_URI.equals(uri)) {
                ZenModeSettings.PREF_ZEN_MODE.update(ZenModeSettings.this.mContext);
            }
            if (this.ZEN_MODE_CONFIG_ETAG_URI.equals(uri)) {
                ZenModeSettings.this.updateZenModeConfig();
            }
        }
    }

    private static class TimePickerPreference extends Preference {
        private Callback mCallback;
        private final Context mContext;
        private int mHourOfDay;
        private int mMinute;
        private int mSummaryFormat;

        public interface Callback {
            boolean onSetTime(int i, int i2);
        }

        public static class TimePickerFragment extends DialogFragment implements OnTimeSetListener {
            public TimePickerPreference pref;

            public Dialog onCreateDialog(Bundle savedInstanceState) {
                boolean usePref = this.pref != null && this.pref.mHourOfDay >= 0 && this.pref.mMinute >= 0;
                Calendar c = Calendar.getInstance();
                return new TimePickerDialog(getActivity(), this, usePref ? this.pref.mHourOfDay : c.get(11), usePref ? this.pref.mMinute : c.get(12), DateFormat.is24HourFormat(getActivity()));
            }

            public void onTimeSet(TimePicker view, int hourOfDay, int minute) {
                if (this.pref != null) {
                    this.pref.setTime(hourOfDay, minute);
                }
            }
        }

        public TimePickerPreference(Context context, final FragmentManager mgr) {
            super(context);
            this.mContext = context;
            setPersistent(false);
            setOnPreferenceClickListener(new OnPreferenceClickListener() {
                public boolean onPreferenceClick(Preference preference) {
                    TimePickerFragment frag = new TimePickerFragment();
                    frag.pref = TimePickerPreference.this;
                    frag.show(mgr, TimePickerPreference.class.getName());
                    return true;
                }
            });
        }

        public void setCallback(Callback callback) {
            this.mCallback = callback;
        }

        public void setSummaryFormat(int resId) {
            this.mSummaryFormat = resId;
            updateSummary();
        }

        public void setTime(int hourOfDay, int minute) {
            if (this.mCallback == null || this.mCallback.onSetTime(hourOfDay, minute)) {
                this.mHourOfDay = hourOfDay;
                this.mMinute = minute;
                updateSummary();
            }
        }

        private void updateSummary() {
            Calendar c = Calendar.getInstance();
            c.set(11, this.mHourOfDay);
            c.set(12, this.mMinute);
            String time = DateFormat.getTimeFormat(this.mContext).format(c.getTime());
            if (this.mSummaryFormat != 0) {
                time = this.mContext.getResources().getString(this.mSummaryFormat, new Object[]{time});
            }
            setSummary(time);
        }
    }

    private static SparseArray<String> allKeyTitles(Context context) {
        SparseArray<String> rt = new SparseArray();
        rt.put(R.string.zen_mode_important_category, "important");
        if (Utils.isVoiceCapable(context)) {
            rt.put(R.string.zen_mode_phone_calls, "phone_calls");
            rt.put(R.string.zen_mode_option_title, "zen_mode");
        } else {
            rt.put(R.string.zen_mode_option_title_novoice, "zen_mode");
        }
        rt.put(R.string.zen_mode_messages, "messages");
        rt.put(R.string.zen_mode_from_starred, "starred");
        rt.put(R.string.zen_mode_events, "events");
        rt.put(R.string.zen_mode_alarm_info, "alarm_info");
        rt.put(R.string.zen_mode_downtime_category, "downtime");
        rt.put(R.string.zen_mode_downtime_days, "days");
        rt.put(R.string.zen_mode_start_time, "start_time");
        rt.put(R.string.zen_mode_end_time, "end_time");
        rt.put(R.string.zen_mode_automation_category, "automation");
        rt.put(R.string.manage_condition_providers, "manage_condition_providers");
        return rt;
    }

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.mContext = getActivity();
        this.mPM = this.mContext.getPackageManager();
        addPreferencesFromResource(R.xml.zen_mode_settings);
        PreferenceScreen root = getPreferenceScreen();
        this.mConfig = getZenModeConfig();
        Log.d("ZenModeSettings", "Loaded mConfig=" + this.mConfig);
        Preference zenMode = PREF_ZEN_MODE.init(this);
        PREF_ZEN_MODE.setCallback(new Callback() {
            public void onSettingSelected(int value) {
                if (value != 0) {
                    ZenModeSettings.this.showConditionSelection(value);
                }
            }
        });
        if (!Utils.isVoiceCapable(this.mContext)) {
            zenMode.setTitle(R.string.zen_mode_option_title_novoice);
        }
        PreferenceCategory important = (PreferenceCategory) root.findPreference("important");
        this.mCalls = (SwitchPreference) important.findPreference("phone_calls");
        if (Utils.isVoiceCapable(this.mContext)) {
            this.mCalls.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    if (ZenModeSettings.this.mDisableListeners) {
                        return true;
                    }
                    boolean val = ((Boolean) newValue).booleanValue();
                    if (val == ZenModeSettings.this.mConfig.allowCalls) {
                        return true;
                    }
                    Log.d("ZenModeSettings", "onPrefChange allowCalls=" + val);
                    ZenModeConfig newConfig = ZenModeSettings.this.mConfig.copy();
                    newConfig.allowCalls = val;
                    return ZenModeSettings.this.setZenModeConfig(newConfig);
                }
            });
        } else {
            important.removePreference(this.mCalls);
            this.mCalls = null;
        }
        this.mMessages = (SwitchPreference) important.findPreference("messages");
        this.mMessages.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                if (ZenModeSettings.this.mDisableListeners) {
                    return true;
                }
                boolean val = ((Boolean) newValue).booleanValue();
                if (val == ZenModeSettings.this.mConfig.allowMessages) {
                    return true;
                }
                Log.d("ZenModeSettings", "onPrefChange allowMessages=" + val);
                ZenModeConfig newConfig = ZenModeSettings.this.mConfig.copy();
                newConfig.allowMessages = val;
                return ZenModeSettings.this.setZenModeConfig(newConfig);
            }
        });
        this.mStarred = (DropDownPreference) important.findPreference("starred");
        this.mStarred.addItem((int) R.string.zen_mode_from_anyone, Integer.valueOf(0));
        this.mStarred.addItem((int) R.string.zen_mode_from_starred, Integer.valueOf(2));
        this.mStarred.addItem((int) R.string.zen_mode_from_contacts, Integer.valueOf(1));
        this.mStarred.setCallback(new Callback() {
            public boolean onItemSelected(int pos, Object newValue) {
                if (ZenModeSettings.this.mDisableListeners) {
                    return true;
                }
                int val = ((Integer) newValue).intValue();
                if (val == ZenModeSettings.this.mConfig.allowFrom) {
                    return true;
                }
                Log.d("ZenModeSettings", "onPrefChange allowFrom=" + ZenModeConfig.sourceToString(val));
                ZenModeConfig newConfig = ZenModeSettings.this.mConfig.copy();
                newConfig.allowFrom = val;
                return ZenModeSettings.this.setZenModeConfig(newConfig);
            }
        });
        important.addPreference(this.mStarred);
        this.mEvents = (SwitchPreference) important.findPreference("events");
        this.mEvents.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                if (ZenModeSettings.this.mDisableListeners) {
                    return true;
                }
                boolean val = ((Boolean) newValue).booleanValue();
                if (val == ZenModeSettings.this.mConfig.allowEvents) {
                    return true;
                }
                Log.d("ZenModeSettings", "onPrefChange allowEvents=" + val);
                ZenModeConfig newConfig = ZenModeSettings.this.mConfig.copy();
                newConfig.allowEvents = val;
                return ZenModeSettings.this.setZenModeConfig(newConfig);
            }
        });
        PreferenceCategory downtime = (PreferenceCategory) root.findPreference("downtime");
        this.mDays = downtime.findPreference("days");
        this.mDays.setOnPreferenceClickListener(new OnPreferenceClickListener() {
            public boolean onPreferenceClick(Preference preference) {
                new Builder(ZenModeSettings.this.mContext).setTitle(R.string.zen_mode_downtime_days).setView(new ZenModeDowntimeDaysSelection(ZenModeSettings.this.mContext, ZenModeSettings.this.mConfig.sleepMode) {
                    protected void onChanged(String mode) {
                        if (!ZenModeSettings.this.mDisableListeners && !Objects.equals(mode, ZenModeSettings.this.mConfig.sleepMode)) {
                            Log.d("ZenModeSettings", "days.onChanged sleepMode=" + mode);
                            ZenModeConfig newConfig = ZenModeSettings.this.mConfig.copy();
                            newConfig.sleepMode = mode;
                            ZenModeSettings.this.setZenModeConfig(newConfig);
                        }
                    }
                }).setOnDismissListener(new OnDismissListener() {
                    public void onDismiss(DialogInterface dialog) {
                        ZenModeSettings.this.updateDays();
                    }
                }).setPositiveButton(R.string.done_button, null).show();
                return true;
            }
        });
        FragmentManager mgr = getFragmentManager();
        this.mStart = new TimePickerPreference(this.mContext, mgr);
        this.mStart.setKey("start_time");
        this.mStart.setTitle(R.string.zen_mode_start_time);
        this.mStart.setCallback(new Callback() {
            public boolean onSetTime(int hour, int minute) {
                if (ZenModeSettings.this.mDisableListeners) {
                    return true;
                }
                if (!ZenModeConfig.isValidHour(hour)) {
                    return false;
                }
                if (!ZenModeConfig.isValidMinute(minute)) {
                    return false;
                }
                if (hour == ZenModeSettings.this.mConfig.sleepStartHour && minute == ZenModeSettings.this.mConfig.sleepStartMinute) {
                    return true;
                }
                Log.d("ZenModeSettings", "onPrefChange sleepStart h=" + hour + " m=" + minute);
                ZenModeConfig newConfig = ZenModeSettings.this.mConfig.copy();
                newConfig.sleepStartHour = hour;
                newConfig.sleepStartMinute = minute;
                return ZenModeSettings.this.setZenModeConfig(newConfig);
            }
        });
        downtime.addPreference(this.mStart);
        this.mStart.setDependency(this.mDays.getKey());
        this.mEnd = new TimePickerPreference(this.mContext, mgr);
        this.mEnd.setKey("end_time");
        this.mEnd.setTitle(R.string.zen_mode_end_time);
        this.mEnd.setCallback(new Callback() {
            public boolean onSetTime(int hour, int minute) {
                if (ZenModeSettings.this.mDisableListeners) {
                    return true;
                }
                if (!ZenModeConfig.isValidHour(hour)) {
                    return false;
                }
                if (!ZenModeConfig.isValidMinute(minute)) {
                    return false;
                }
                if (hour == ZenModeSettings.this.mConfig.sleepEndHour && minute == ZenModeSettings.this.mConfig.sleepEndMinute) {
                    return true;
                }
                Log.d("ZenModeSettings", "onPrefChange sleepEnd h=" + hour + " m=" + minute);
                ZenModeConfig newConfig = ZenModeSettings.this.mConfig.copy();
                newConfig.sleepEndHour = hour;
                newConfig.sleepEndMinute = minute;
                return ZenModeSettings.this.setZenModeConfig(newConfig);
            }
        });
        downtime.addPreference(this.mEnd);
        this.mEnd.setDependency(this.mDays.getKey());
        this.mAutomationCategory = (PreferenceCategory) findPreference("automation");
        this.mEntry = findPreference("entry");
        this.mEntry.setOnPreferenceClickListener(new OnPreferenceClickListener() {
            public boolean onPreferenceClick(Preference preference) {
                new Builder(ZenModeSettings.this.mContext).setTitle(R.string.zen_mode_entry_conditions_title).setView(new ZenModeAutomaticConditionSelection(ZenModeSettings.this.mContext)).setOnDismissListener(new OnDismissListener() {
                    public void onDismiss(DialogInterface dialog) {
                        ZenModeSettings.this.refreshAutomationSection();
                    }
                }).setPositiveButton(R.string.dlg_ok, null).show();
                return true;
            }
        });
        this.mConditionProviders = findPreference("manage_condition_providers");
        updateControls();
    }

    private void updateDays() {
        if (this.mConfig != null) {
            int[] days = ZenModeConfig.tryParseDays(this.mConfig.sleepMode);
            if (!(days == null || days.length == 0)) {
                StringBuilder sb = new StringBuilder();
                Calendar c = Calendar.getInstance();
                for (int day : ZenModeDowntimeDaysSelection.DAYS) {
                    int j = 0;
                    while (j < days.length) {
                        if (day == days[j]) {
                            c.set(7, day);
                            if (sb.length() > 0) {
                                sb.append(this.mContext.getString(R.string.summary_divider_text));
                            }
                            sb.append(DAY_FORMAT.format(c.getTime()));
                        } else {
                            j++;
                        }
                    }
                }
                if (sb.length() > 0) {
                    this.mDays.setSummary(sb);
                    this.mDays.notifyDependencyChange(false);
                    return;
                }
            }
        }
        this.mDays.setSummary(R.string.zen_mode_downtime_days_none);
        this.mDays.notifyDependencyChange(true);
    }

    private void updateEndSummary() {
        boolean nextDay;
        int i = 0;
        if ((this.mConfig.sleepStartHour * 60) + this.mConfig.sleepStartMinute >= (this.mConfig.sleepEndHour * 60) + this.mConfig.sleepEndMinute) {
            nextDay = true;
        } else {
            nextDay = false;
        }
        TimePickerPreference timePickerPreference = this.mEnd;
        if (nextDay) {
            i = R.string.zen_mode_end_time_summary_format;
        }
        timePickerPreference.setSummaryFormat(i);
    }

    private void updateControls() {
        this.mDisableListeners = true;
        if (this.mCalls != null) {
            this.mCalls.setChecked(this.mConfig.allowCalls);
        }
        this.mMessages.setChecked(this.mConfig.allowMessages);
        this.mStarred.setSelectedValue(Integer.valueOf(this.mConfig.allowFrom));
        this.mEvents.setChecked(this.mConfig.allowEvents);
        updateStarredEnabled();
        updateDays();
        this.mStart.setTime(this.mConfig.sleepStartHour, this.mConfig.sleepStartMinute);
        this.mEnd.setTime(this.mConfig.sleepEndHour, this.mConfig.sleepEndMinute);
        this.mDisableListeners = false;
        refreshAutomationSection();
        updateEndSummary();
    }

    private void updateStarredEnabled() {
        DropDownPreference dropDownPreference = this.mStarred;
        boolean z = this.mConfig.allowCalls || this.mConfig.allowMessages;
        dropDownPreference.setEnabled(z);
    }

    private void refreshAutomationSection() {
        if (this.mConditionProviders == null) {
            return;
        }
        if (ConditionProviderSettings.getProviderCount(this.mPM) == 0) {
            getPreferenceScreen().removePreference(this.mAutomationCategory);
            return;
        }
        int n = ConditionProviderSettings.getEnabledProviderCount(this.mContext);
        if (n == 0) {
            this.mConditionProviders.setSummary(getResources().getString(R.string.manage_condition_providers_summary_zero));
        } else {
            this.mConditionProviders.setSummary(String.format(getResources().getQuantityString(R.plurals.manage_condition_providers_summary_nonzero, n, new Object[]{Integer.valueOf(n)}), new Object[0]));
        }
        String entrySummary = getEntryConditionSummary();
        if (n == 0 || entrySummary == null) {
            this.mEntry.setSummary(R.string.zen_mode_entry_conditions_summary_none);
        } else {
            this.mEntry.setSummary(entrySummary);
        }
    }

    private String getEntryConditionSummary() {
        String str = null;
        try {
            Condition[] automatic = Stub.asInterface(ServiceManager.getService("notification")).getAutomaticZenModeConditions();
            if (!(automatic == null || automatic.length == 0)) {
                String divider = getString(R.string.summary_divider_text);
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < automatic.length; i++) {
                    if (i > 0) {
                        sb.append(divider);
                    }
                    sb.append(automatic[i].summary);
                }
                str = sb.toString();
            }
        } catch (Exception e) {
            Log.w("ZenModeSettings", "Error calling getAutomaticZenModeConditions", e);
        }
        return str;
    }

    public void onResume() {
        super.onResume();
        updateControls();
        this.mSettingsObserver.register();
    }

    public void onPause() {
        super.onPause();
        this.mSettingsObserver.unregister();
    }

    private void updateZenModeConfig() {
        ZenModeConfig config = getZenModeConfig();
        if (!Objects.equals(config, this.mConfig)) {
            this.mConfig = config;
            Log.d("ZenModeSettings", "updateZenModeConfig mConfig=" + this.mConfig);
            updateControls();
        }
    }

    private ZenModeConfig getZenModeConfig() {
        try {
            return Stub.asInterface(ServiceManager.getService("notification")).getZenModeConfig();
        } catch (Exception e) {
            Log.w("ZenModeSettings", "Error calling NoMan", e);
            return new ZenModeConfig();
        }
    }

    private boolean setZenModeConfig(ZenModeConfig config) {
        try {
            boolean success = Stub.asInterface(ServiceManager.getService("notification")).setZenModeConfig(config);
            if (!success) {
                return success;
            }
            this.mConfig = config;
            Log.d("ZenModeSettings", "Saved mConfig=" + this.mConfig);
            updateEndSummary();
            updateStarredEnabled();
            return success;
        } catch (Exception e) {
            Log.w("ZenModeSettings", "Error calling NoMan", e);
            return false;
        }
    }

    protected void showConditionSelection(int newSettingsValue) {
        if (this.mDialog == null) {
            final ZenModeConditionSelection zenModeConditionSelection = new ZenModeConditionSelection(this.mContext);
            OnClickListener positiveListener = new OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    zenModeConditionSelection.confirmCondition();
                    ZenModeSettings.this.mDialog = null;
                }
            };
            final int oldSettingsValue = PREF_ZEN_MODE.getValue(this.mContext);
            ScrollView scrollView = new ScrollView(this.mContext);
            scrollView.addView(zenModeConditionSelection);
            this.mDialog = new Builder(getActivity()).setTitle(PREF_ZEN_MODE.getCaption(getResources(), newSettingsValue)).setView(scrollView).setPositiveButton(R.string.okay, positiveListener).setNegativeButton(R.string.cancel_all_caps, new OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    ZenModeSettings.this.cancelDialog(oldSettingsValue);
                }
            }).setOnCancelListener(new OnCancelListener() {
                public void onCancel(DialogInterface dialog) {
                    ZenModeSettings.this.cancelDialog(oldSettingsValue);
                }
            }).create();
            this.mDialog.show();
        }
    }

    protected void cancelDialog(int oldSettingsValue) {
        PREF_ZEN_MODE.setValueWithoutCallback(this.mContext, oldSettingsValue);
        this.mDialog = null;
    }
}
