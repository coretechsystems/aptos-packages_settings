package com.android.settings.notification;

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Resources;
import android.net.Uri;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.TwoStatePreference;
import android.provider.Settings.Global;
import android.provider.Settings.System;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.notification.DropDownPreference.Callback;

public class SettingPref {
    protected final int mDefault;
    protected DropDownPreference mDropDown;
    private final String mKey;
    protected final String mSetting;
    protected TwoStatePreference mTwoState;
    protected final int mType;
    private final Uri mUri = getUriFor(this.mType, this.mSetting);
    private final int[] mValues;

    public SettingPref(int type, String key, String setting, int def, int... values) {
        this.mType = type;
        this.mKey = key;
        this.mSetting = setting;
        this.mDefault = def;
        this.mValues = values;
    }

    public boolean isApplicable(Context context) {
        return true;
    }

    protected String getCaption(Resources res, int value) {
        throw new UnsupportedOperationException();
    }

    public Preference init(SettingsPreferenceFragment settings) {
        final Context context = settings.getActivity();
        Preference p = settings.getPreferenceScreen().findPreference(this.mKey);
        if (!(p == null || isApplicable(context))) {
            settings.getPreferenceScreen().removePreference(p);
            p = null;
        }
        if (p instanceof TwoStatePreference) {
            this.mTwoState = (TwoStatePreference) p;
        } else if (p instanceof DropDownPreference) {
            this.mDropDown = (DropDownPreference) p;
            for (int value : this.mValues) {
                this.mDropDown.addItem(getCaption(context.getResources(), value), Integer.valueOf(value));
            }
        }
        update(context);
        if (this.mTwoState != null) {
            p.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    SettingPref.this.setSetting(context, ((Boolean) newValue).booleanValue() ? 1 : 0);
                    return true;
                }
            });
            return this.mTwoState;
        } else if (this.mDropDown == null) {
            return null;
        } else {
            this.mDropDown.setCallback(new Callback() {
                public boolean onItemSelected(int pos, Object value) {
                    return SettingPref.this.setSetting(context, ((Integer) value).intValue());
                }
            });
            return this.mDropDown;
        }
    }

    protected boolean setSetting(Context context, int value) {
        return putInt(this.mType, context.getContentResolver(), this.mSetting, value);
    }

    public Uri getUri() {
        return this.mUri;
    }

    public String getKey() {
        return this.mKey;
    }

    public void update(Context context) {
        int val = getInt(this.mType, context.getContentResolver(), this.mSetting, this.mDefault);
        if (this.mTwoState != null) {
            this.mTwoState.setChecked(val != 0);
        } else if (this.mDropDown != null) {
            this.mDropDown.setSelectedValue(Integer.valueOf(val));
        }
    }

    private static Uri getUriFor(int type, String setting) {
        switch (type) {
            case 1:
                return Global.getUriFor(setting);
            case 2:
                return System.getUriFor(setting);
            default:
                throw new IllegalArgumentException();
        }
    }

    protected static boolean putInt(int type, ContentResolver cr, String setting, int value) {
        switch (type) {
            case 1:
                return Global.putInt(cr, setting, value);
            case 2:
                return System.putInt(cr, setting, value);
            default:
                throw new IllegalArgumentException();
        }
    }

    protected static int getInt(int type, ContentResolver cr, String setting, int def) {
        switch (type) {
            case 1:
                return Global.getInt(cr, setting, def);
            case 2:
                return System.getInt(cr, setting, def);
            default:
                throw new IllegalArgumentException();
        }
    }
}
