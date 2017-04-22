package com.android.settings.location;

import android.content.Intent;
import android.text.TextUtils;
import android.util.Log;
import com.android.internal.annotations.Immutable;
import com.android.internal.util.Preconditions;

@Immutable
class InjectedSetting {
    public final String className;
    public final int iconId;
    public final String packageName;
    public final String settingsActivity;
    public final String title;

    private InjectedSetting(String packageName, String className, String title, int iconId, String settingsActivity) {
        this.packageName = (String) Preconditions.checkNotNull(packageName, "packageName");
        this.className = (String) Preconditions.checkNotNull(className, "className");
        this.title = (String) Preconditions.checkNotNull(title, "title");
        this.iconId = iconId;
        this.settingsActivity = (String) Preconditions.checkNotNull(settingsActivity);
    }

    public static InjectedSetting newInstance(String packageName, String className, String title, int iconId, String settingsActivity) {
        if (packageName != null && className != null && !TextUtils.isEmpty(title) && !TextUtils.isEmpty(settingsActivity)) {
            return new InjectedSetting(packageName, className, title, iconId, settingsActivity);
        }
        if (Log.isLoggable("SettingsInjector", 5)) {
            Log.w("SettingsInjector", "Illegal setting specification: package=" + packageName + ", class=" + className + ", title=" + title + ", settingsActivity=" + settingsActivity);
        }
        return null;
    }

    public String toString() {
        return "InjectedSetting{mPackageName='" + this.packageName + '\'' + ", mClassName='" + this.className + '\'' + ", label=" + this.title + ", iconId=" + this.iconId + ", settingsActivity='" + this.settingsActivity + '\'' + '}';
    }

    public Intent getServiceIntent() {
        Intent intent = new Intent();
        intent.setClassName(this.packageName, this.className);
        return intent;
    }

    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof InjectedSetting)) {
            return false;
        }
        InjectedSetting that = (InjectedSetting) o;
        if (this.packageName.equals(that.packageName) && this.className.equals(that.className) && this.title.equals(that.title) && this.iconId == that.iconId && this.settingsActivity.equals(that.settingsActivity)) {
            return true;
        }
        return false;
    }

    public int hashCode() {
        return (((((((this.packageName.hashCode() * 31) + this.className.hashCode()) * 31) + this.title.hashCode()) * 31) + this.iconId) * 31) + this.settingsActivity.hashCode();
    }
}
