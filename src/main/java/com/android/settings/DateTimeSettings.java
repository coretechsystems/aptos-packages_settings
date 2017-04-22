package com.android.settings;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.DatePickerDialog;
import android.app.DatePickerDialog.OnDateSetListener;
import android.app.Dialog;
import android.app.TimePickerDialog;
import android.app.TimePickerDialog.OnTimeSetListener;
import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceScreen;
import android.provider.Settings.Global;
import android.provider.Settings.SettingNotFoundException;
import android.provider.Settings.System;
import android.text.BidiFormatter;
import android.text.TextDirectionHeuristics;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.widget.DatePicker;
import android.widget.TimePicker;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class DateTimeSettings extends SettingsPreferenceFragment implements OnDateSetListener, OnTimeSetListener, OnSharedPreferenceChangeListener {
    private CheckBoxPreference mAutoTimePref;
    private CheckBoxPreference mAutoTimeZonePref;
    private ListPreference mDateFormat;
    private Preference mDatePref;
    private Calendar mDummyDate;
    private BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            Activity activity = DateTimeSettings.this.getActivity();
            if (activity != null) {
                DateTimeSettings.this.updateTimeAndDateDisplay(activity);
            }
        }
    };
    private Preference mTime24Pref;
    private Preference mTimePref;
    private Preference mTimeZone;

    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        addPreferencesFromResource(R.xml.date_time_prefs);
        initUI();
    }

    private void initUI() {
        boolean autoTimeEnabled = getAutoState("auto_time");
        boolean autoTimeZoneEnabled = getAutoState("auto_time_zone");
        this.mAutoTimePref = (CheckBoxPreference) findPreference("auto_time");
        if (((DevicePolicyManager) getSystemService("device_policy")).getAutoTimeRequired()) {
            this.mAutoTimePref.setEnabled(false);
        }
        boolean isFirstRun = getActivity().getIntent().getBooleanExtra("firstRun", false);
        this.mDummyDate = Calendar.getInstance();
        this.mAutoTimePref.setChecked(autoTimeEnabled);
        this.mAutoTimeZonePref = (CheckBoxPreference) findPreference("auto_zone");
        if (Utils.isWifiOnly(getActivity()) || isFirstRun) {
            getPreferenceScreen().removePreference(this.mAutoTimeZonePref);
            autoTimeZoneEnabled = false;
        }
        this.mAutoTimeZonePref.setChecked(autoTimeZoneEnabled);
        this.mTimePref = findPreference("time");
        this.mTime24Pref = findPreference("24 hour");
        this.mTimeZone = findPreference("timezone");
        this.mDatePref = findPreference("date");
        this.mDateFormat = (ListPreference) findPreference("date_format");
        if (isFirstRun) {
            getPreferenceScreen().removePreference(this.mTime24Pref);
            getPreferenceScreen().removePreference(this.mDateFormat);
        }
        String[] dateFormats = getResources().getStringArray(R.array.date_format_values);
        String[] formattedDates = new String[dateFormats.length];
        String currentFormat = getDateFormat();
        if (currentFormat == null) {
            currentFormat = "";
        }
        Calendar calendar = this.mDummyDate;
        int i = this.mDummyDate.get(1);
        Calendar calendar2 = this.mDummyDate;
        calendar.set(i, 11, 31, 13, 0, 0);
        for (int i2 = 0; i2 < formattedDates.length; i2++) {
            String formatted = DateFormat.getDateFormatForSetting(getActivity(), dateFormats[i2]).format(this.mDummyDate.getTime());
            if (dateFormats[i2].length() == 0) {
                formattedDates[i2] = getResources().getString(R.string.normal_date_format, new Object[]{formatted});
            } else {
                formattedDates[i2] = formatted;
            }
        }
        this.mDateFormat.setEntries(formattedDates);
        this.mDateFormat.setEntryValues(R.array.date_format_values);
        this.mDateFormat.setValue(currentFormat);
        this.mTimePref.setEnabled(!autoTimeEnabled);
        this.mDatePref.setEnabled(!autoTimeEnabled);
        this.mTimeZone.setEnabled(!autoTimeZoneEnabled);
    }

    public void onResume() {
        super.onResume();
        getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
        ((CheckBoxPreference) this.mTime24Pref).setChecked(is24Hour());
        IntentFilter filter = new IntentFilter();
        filter.addAction("android.intent.action.TIME_TICK");
        filter.addAction("android.intent.action.TIME_SET");
        filter.addAction("android.intent.action.TIMEZONE_CHANGED");
        getActivity().registerReceiver(this.mIntentReceiver, filter, null, null);
        updateTimeAndDateDisplay(getActivity());
    }

    public void onPause() {
        super.onPause();
        getActivity().unregisterReceiver(this.mIntentReceiver);
        getPreferenceScreen().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
    }

    public void updateTimeAndDateDisplay(Context context) {
        java.text.DateFormat shortDateFormat = DateFormat.getDateFormat(context);
        Calendar now = Calendar.getInstance();
        this.mDummyDate.setTimeZone(now.getTimeZone());
        this.mDummyDate.set(now.get(1), 11, 31, 13, 0, 0);
        Date dummyDate = this.mDummyDate.getTime();
        this.mTimePref.setSummary(DateFormat.getTimeFormat(getActivity()).format(now.getTime()));
        this.mTimeZone.setSummary(getTimeZoneText(now.getTimeZone(), true));
        this.mDatePref.setSummary(shortDateFormat.format(now.getTime()));
        this.mDateFormat.setSummary(shortDateFormat.format(dummyDate));
        this.mTime24Pref.setSummary(DateFormat.getTimeFormat(getActivity()).format(dummyDate));
    }

    public void onDateSet(DatePicker view, int year, int month, int day) {
        Activity activity = getActivity();
        if (activity != null) {
            setDate(activity, year, month, day);
            updateTimeAndDateDisplay(activity);
        }
    }

    public void onTimeSet(TimePicker view, int hourOfDay, int minute) {
        Activity activity = getActivity();
        if (activity != null) {
            setTime(activity, hourOfDay, minute);
            updateTimeAndDateDisplay(activity);
        }
    }

    public void onSharedPreferenceChanged(SharedPreferences preferences, String key) {
        boolean z = true;
        if (key.equals("date_format")) {
            System.putString(getContentResolver(), "date_format", preferences.getString(key, getResources().getString(R.string.default_date_format)));
            updateTimeAndDateDisplay(getActivity());
        } else if (key.equals("auto_time")) {
            boolean z2;
            boolean autoEnabled = preferences.getBoolean(key, true);
            r6 = getContentResolver();
            r7 = "auto_time";
            if (autoEnabled) {
                r3 = 1;
            } else {
                r3 = 0;
            }
            Global.putInt(r6, r7, r3);
            Preference preference = this.mTimePref;
            if (autoEnabled) {
                z2 = false;
            } else {
                z2 = true;
            }
            preference.setEnabled(z2);
            r3 = this.mDatePref;
            if (autoEnabled) {
                z = false;
            }
            r3.setEnabled(z);
        } else if (key.equals("auto_zone")) {
            boolean autoZoneEnabled = preferences.getBoolean(key, true);
            r6 = getContentResolver();
            r7 = "auto_time_zone";
            if (autoZoneEnabled) {
                r3 = 1;
            } else {
                r3 = 0;
            }
            Global.putInt(r6, r7, r3);
            r3 = this.mTimeZone;
            if (autoZoneEnabled) {
                z = false;
            }
            r3.setEnabled(z);
        }
    }

    public Dialog onCreateDialog(int id) {
        Calendar calendar = Calendar.getInstance();
        switch (id) {
            case 0:
                DatePickerDialog d = new DatePickerDialog(getActivity(), this, calendar.get(1), calendar.get(2), calendar.get(5));
                configureDatePicker(d.getDatePicker());
                return d;
            case 1:
                return new TimePickerDialog(getActivity(), this, calendar.get(11), calendar.get(12), DateFormat.is24HourFormat(getActivity()));
            default:
                throw new IllegalArgumentException();
        }
    }

    static void configureDatePicker(DatePicker datePicker) {
        Calendar t = Calendar.getInstance();
        t.clear();
        t.set(1970, 0, 1);
        datePicker.setMinDate(t.getTimeInMillis());
        t.clear();
        t.set(2037, 11, 31);
        datePicker.setMaxDate(t.getTimeInMillis());
    }

    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        if (preference == this.mDatePref) {
            showDialog(0);
        } else if (preference == this.mTimePref) {
            removeDialog(1);
            showDialog(1);
        } else if (preference == this.mTime24Pref) {
            boolean is24Hour = ((CheckBoxPreference) this.mTime24Pref).isChecked();
            set24Hour(is24Hour);
            updateTimeAndDateDisplay(getActivity());
            timeUpdated(is24Hour);
        }
        return super.onPreferenceTreeClick(preferenceScreen, preference);
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        updateTimeAndDateDisplay(getActivity());
    }

    private void timeUpdated(boolean is24Hour) {
        Intent timeChanged = new Intent("android.intent.action.TIME_SET");
        timeChanged.putExtra("android.intent.extra.TIME_PREF_24_HOUR_FORMAT", is24Hour);
        getActivity().sendBroadcast(timeChanged);
    }

    private boolean is24Hour() {
        return DateFormat.is24HourFormat(getActivity());
    }

    private void set24Hour(boolean is24Hour) {
        System.putString(getContentResolver(), "time_12_24", is24Hour ? "24" : "12");
    }

    private String getDateFormat() {
        return System.getString(getContentResolver(), "date_format");
    }

    private boolean getAutoState(String name) {
        try {
            return Global.getInt(getContentResolver(), name) > 0;
        } catch (SettingNotFoundException e) {
            return false;
        }
    }

    static void setDate(Context context, int year, int month, int day) {
        Calendar c = Calendar.getInstance();
        c.set(1, year);
        c.set(2, month);
        c.set(5, day);
        long when = c.getTimeInMillis();
        if (when / 1000 < 2147483647L) {
            ((AlarmManager) context.getSystemService("alarm")).setTime(when);
        }
    }

    static void setTime(Context context, int hourOfDay, int minute) {
        Calendar c = Calendar.getInstance();
        c.set(11, hourOfDay);
        c.set(12, minute);
        c.set(13, 0);
        c.set(14, 0);
        long when = c.getTimeInMillis();
        if (when / 1000 < 2147483647L) {
            ((AlarmManager) context.getSystemService("alarm")).setTime(when);
        }
    }

    public static String getTimeZoneText(TimeZone tz, boolean includeName) {
        boolean isRtl = true;
        Date now = new Date();
        SimpleDateFormat gmtFormatter = new SimpleDateFormat("ZZZZ");
        gmtFormatter.setTimeZone(tz);
        String gmtString = gmtFormatter.format(now);
        BidiFormatter bidiFormatter = BidiFormatter.getInstance();
        if (TextUtils.getLayoutDirectionFromLocale(Locale.getDefault()) != 1) {
            isRtl = false;
        }
        gmtString = bidiFormatter.unicodeWrap(gmtString, isRtl ? TextDirectionHeuristics.RTL : TextDirectionHeuristics.LTR);
        if (!includeName) {
            return gmtString;
        }
        SimpleDateFormat zoneNameFormatter = new SimpleDateFormat("zzzz");
        zoneNameFormatter.setTimeZone(tz);
        return gmtString + " " + zoneNameFormatter.format(now);
    }
}
