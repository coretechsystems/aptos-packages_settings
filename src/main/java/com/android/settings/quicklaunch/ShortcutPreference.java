package com.android.settings.quicklaunch;

import android.content.Context;
import android.content.res.ColorStateList;
import android.preference.Preference;
import android.util.TypedValue;
import android.view.View;
import android.widget.TextView;
import com.android.settings.R;

public class ShortcutPreference extends Preference implements Comparable<Preference> {
    private static String STRING_ASSIGN_APPLICATION;
    private static String STRING_NO_SHORTCUT;
    private static int sDimAlpha;
    private static ColorStateList sDimSummaryColor;
    private static ColorStateList sDimTitleColor;
    private static ColorStateList sRegularSummaryColor;
    private static ColorStateList sRegularTitleColor;
    private static Object sStaticVarsLock = new Object();
    private boolean mHasBookmark;
    private char mShortcut;

    public ShortcutPreference(Context context, char shortcut) {
        super(context);
        synchronized (sStaticVarsLock) {
            if (STRING_ASSIGN_APPLICATION == null) {
                STRING_ASSIGN_APPLICATION = context.getString(R.string.quick_launch_assign_application);
                STRING_NO_SHORTCUT = context.getString(R.string.quick_launch_no_shortcut);
                TypedValue outValue = new TypedValue();
                context.getTheme().resolveAttribute(16842803, outValue, true);
                sDimAlpha = (int) (outValue.getFloat() * 255.0f);
            }
        }
        this.mShortcut = shortcut;
        setWidgetLayoutResource(R.layout.preference_widget_shortcut);
    }

    public char getShortcut() {
        return this.mShortcut;
    }

    public boolean hasBookmark() {
        return this.mHasBookmark;
    }

    public void setHasBookmark(boolean hasBookmark) {
        if (hasBookmark != this.mHasBookmark) {
            this.mHasBookmark = hasBookmark;
            notifyChanged();
        }
    }

    public CharSequence getTitle() {
        return this.mHasBookmark ? super.getTitle() : STRING_ASSIGN_APPLICATION;
    }

    public CharSequence getSummary() {
        return this.mHasBookmark ? super.getSummary() : STRING_NO_SHORTCUT;
    }

    protected void onBindView(View view) {
        super.onBindView(view);
        TextView shortcutView = (TextView) view.findViewById(R.id.shortcut);
        if (shortcutView != null) {
            shortcutView.setText(String.valueOf(this.mShortcut));
        }
        TextView titleView = (TextView) view.findViewById(16908310);
        synchronized (sStaticVarsLock) {
            if (sRegularTitleColor == null) {
                sRegularTitleColor = titleView.getTextColors();
                sDimTitleColor = sRegularTitleColor.withAlpha(sDimAlpha);
            }
        }
        ColorStateList color = this.mHasBookmark ? sRegularTitleColor : sDimTitleColor;
        if (color != null) {
            titleView.setTextColor(color);
        }
        TextView summaryView = (TextView) view.findViewById(16908304);
        synchronized (sStaticVarsLock) {
            if (sRegularSummaryColor == null) {
                sRegularSummaryColor = summaryView.getTextColors();
                sDimSummaryColor = sRegularSummaryColor.withAlpha(sDimAlpha);
            }
        }
        color = this.mHasBookmark ? sRegularSummaryColor : sDimSummaryColor;
        if (color != null) {
            summaryView.setTextColor(color);
        }
    }

    public int compareTo(Preference another) {
        if (!(another instanceof ShortcutPreference)) {
            return super.compareTo(another);
        }
        char other = ((ShortcutPreference) another).mShortcut;
        if (Character.isDigit(this.mShortcut) && Character.isLetter(other)) {
            return 1;
        }
        if (Character.isDigit(other) && Character.isLetter(this.mShortcut)) {
            return -1;
        }
        return this.mShortcut - other;
    }
}
