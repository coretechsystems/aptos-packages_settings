package com.android.settings.accessibility;

import android.content.ComponentName;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.provider.Settings.Secure;
import android.text.TextUtils.SimpleStringSplitter;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

class AccessibilityUtils {
    static Set<ComponentName> getEnabledServicesFromSettings(Context context) {
        String enabledServicesSetting = Secure.getString(context.getContentResolver(), "enabled_accessibility_services");
        if (enabledServicesSetting == null) {
            return Collections.emptySet();
        }
        Set<ComponentName> enabledServices = new HashSet();
        SimpleStringSplitter colonSplitter = AccessibilitySettings.sStringColonSplitter;
        colonSplitter.setString(enabledServicesSetting);
        while (colonSplitter.hasNext()) {
            ComponentName enabledService = ComponentName.unflattenFromString(colonSplitter.next());
            if (enabledService != null) {
                enabledServices.add(enabledService);
            }
        }
        return enabledServices;
    }

    static CharSequence getTextForLocale(Context context, Locale locale, int resId) {
        Resources res = context.getResources();
        Configuration config = res.getConfiguration();
        Locale prevLocale = config.locale;
        try {
            config.locale = locale;
            res.updateConfiguration(config, null);
            CharSequence text = res.getText(resId);
            return text;
        } finally {
            config.locale = prevLocale;
            res.updateConfiguration(config, null);
        }
    }
}
