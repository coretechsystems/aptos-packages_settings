package com.android.settings.notification;

import android.content.Context;
import android.content.pm.PackageManager;
import com.android.settings.R;

public class ConditionProviderSettings extends ManagedServiceSettings {
    private static final Config CONFIG = getConditionProviderConfig();
    private static final String TAG = ConditionProviderSettings.class.getSimpleName();

    private static Config getConditionProviderConfig() {
        Config c = new Config();
        c.tag = TAG;
        c.setting = "enabled_condition_providers";
        c.intentAction = "android.service.notification.ConditionProviderService";
        c.permission = "android.permission.BIND_CONDITION_PROVIDER_SERVICE";
        c.noun = "condition provider";
        c.warningDialogTitle = R.string.condition_provider_security_warning_title;
        c.warningDialogSummary = R.string.condition_provider_security_warning_summary;
        c.emptyText = R.string.no_condition_providers;
        return c;
    }

    protected Config getConfig() {
        return CONFIG;
    }

    public static int getProviderCount(PackageManager pm) {
        return ManagedServiceSettings.getServicesCount(CONFIG, pm);
    }

    public static int getEnabledProviderCount(Context context) {
        return ManagedServiceSettings.getEnabledServicesCount(CONFIG, context);
    }
}
