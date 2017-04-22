package com.android.settings;

import android.content.Context;
import android.preference.ListPreference;
import android.util.AttributeSet;

public class WarnedListPreference extends ListPreference {
    public WarnedListPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    protected void onClick() {
    }

    public void click() {
        super.onClick();
    }
}
