package com.android.settings.location;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.preference.Preference;

public class DimmableIconPreference extends Preference {
    public DimmableIconPreference(Context context) {
        super(context);
    }

    private void dimIcon(boolean dimmed) {
        Drawable icon = getIcon();
        if (icon != null) {
            icon.mutate().setAlpha(dimmed ? 102 : 255);
            setIcon(icon);
        }
    }

    public void onParentChanged(Preference parent, boolean disableChild) {
        dimIcon(disableChild);
        super.onParentChanged(parent, disableChild);
    }

    public void setEnabled(boolean enabled) {
        dimIcon(!enabled);
        super.setEnabled(enabled);
    }
}
