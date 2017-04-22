package com.android.settings.notification;

import android.content.Context;
import android.content.pm.PackageManager;
import com.android.settings.R;

public class NotificationAccessSettings extends ManagedServiceSettings {
    private static final Config CONFIG = getNotificationListenerConfig();
    private static final String TAG = NotificationAccessSettings.class.getSimpleName();

    private static Config getNotificationListenerConfig() {
        Config c = new Config();
        c.tag = TAG;
        c.setting = "enabled_notification_listeners";
        c.intentAction = "android.service.notification.NotificationListenerService";
        c.permission = "android.permission.BIND_NOTIFICATION_LISTENER_SERVICE";
        c.noun = "notification listener";
        c.warningDialogTitle = R.string.notification_listener_security_warning_title;
        c.warningDialogSummary = R.string.notification_listener_security_warning_summary;
        c.emptyText = R.string.no_notification_listeners;
        return c;
    }

    protected Config getConfig() {
        return CONFIG;
    }

    public static int getListenersCount(PackageManager pm) {
        return ManagedServiceSettings.getServicesCount(CONFIG, pm);
    }

    public static int getEnabledListenersCount(Context context) {
        return ManagedServiceSettings.getEnabledServicesCount(CONFIG, context);
    }
}
