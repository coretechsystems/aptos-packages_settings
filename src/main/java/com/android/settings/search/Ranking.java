package com.android.settings.search;

import com.android.settings.ChooseLockGeneric.ChooseLockGenericFragment;
import com.android.settings.DataUsageSummary;
import com.android.settings.DateTimeSettings;
import com.android.settings.DevelopmentSettings;
import com.android.settings.DeviceInfoSettings;
import com.android.settings.DisplaySettings;
import com.android.settings.HomeSettings;
import com.android.settings.PrivacySettings;
import com.android.settings.ScreenPinningSettings;
import com.android.settings.SecuritySettings;
import com.android.settings.WallpaperTypeSettings;
import com.android.settings.WirelessSettings;
import com.android.settings.accessibility.AccessibilitySettings;
import com.android.settings.bluetooth.BluetoothSettings;
import com.android.settings.deviceinfo.Memory;
import com.android.settings.fuelgauge.BatterySaverSettings;
import com.android.settings.fuelgauge.PowerUsageSummary;
import com.android.settings.inputmethod.InputMethodAndLanguageSettings;
import com.android.settings.location.LocationSettings;
import com.android.settings.net.DataUsageMeteredSettings;
import com.android.settings.notification.NotificationSettings;
import com.android.settings.notification.OtherSoundSettings;
import com.android.settings.notification.ZenModeSettings;
import com.android.settings.print.PrintSettingsFragment;
import com.android.settings.sim.SimSettings;
import com.android.settings.users.UserSettings;
import com.android.settings.voice.VoiceInputSettings;
import com.android.settings.wifi.AdvancedWifiSettings;
import com.android.settings.wifi.SavedAccessPointsWifiSettings;
import com.android.settings.wifi.WifiSettings;
import java.util.HashMap;

public final class Ranking {
    private static HashMap<String, Integer> sBaseRankMap = new HashMap();
    public static int sCurrentBaseRank = 2048;
    private static HashMap<String, Integer> sRankMap = new HashMap();

    static {
        sRankMap.put(WifiSettings.class.getName(), Integer.valueOf(1));
        sRankMap.put(AdvancedWifiSettings.class.getName(), Integer.valueOf(1));
        sRankMap.put(SavedAccessPointsWifiSettings.class.getName(), Integer.valueOf(1));
        sRankMap.put(BluetoothSettings.class.getName(), Integer.valueOf(2));
        sRankMap.put(SimSettings.class.getName(), Integer.valueOf(3));
        sRankMap.put(DataUsageSummary.class.getName(), Integer.valueOf(4));
        sRankMap.put(DataUsageMeteredSettings.class.getName(), Integer.valueOf(4));
        sRankMap.put(WirelessSettings.class.getName(), Integer.valueOf(5));
        sRankMap.put(HomeSettings.class.getName(), Integer.valueOf(6));
        sRankMap.put(DisplaySettings.class.getName(), Integer.valueOf(7));
        sRankMap.put(WallpaperTypeSettings.class.getName(), Integer.valueOf(8));
        sRankMap.put(NotificationSettings.class.getName(), Integer.valueOf(9));
        sRankMap.put(OtherSoundSettings.class.getName(), Integer.valueOf(9));
        sRankMap.put(ZenModeSettings.class.getName(), Integer.valueOf(9));
        sRankMap.put(Memory.class.getName(), Integer.valueOf(10));
        sRankMap.put(PowerUsageSummary.class.getName(), Integer.valueOf(11));
        sRankMap.put(BatterySaverSettings.class.getName(), Integer.valueOf(11));
        sRankMap.put(UserSettings.class.getName(), Integer.valueOf(12));
        sRankMap.put(LocationSettings.class.getName(), Integer.valueOf(13));
        sRankMap.put(SecuritySettings.class.getName(), Integer.valueOf(14));
        sRankMap.put(ChooseLockGenericFragment.class.getName(), Integer.valueOf(14));
        sRankMap.put(ScreenPinningSettings.class.getName(), Integer.valueOf(14));
        sRankMap.put(InputMethodAndLanguageSettings.class.getName(), Integer.valueOf(15));
        sRankMap.put(VoiceInputSettings.class.getName(), Integer.valueOf(15));
        sRankMap.put(PrivacySettings.class.getName(), Integer.valueOf(16));
        sRankMap.put(DateTimeSettings.class.getName(), Integer.valueOf(17));
        sRankMap.put(AccessibilitySettings.class.getName(), Integer.valueOf(18));
        sRankMap.put(PrintSettingsFragment.class.getName(), Integer.valueOf(19));
        sRankMap.put(DevelopmentSettings.class.getName(), Integer.valueOf(20));
        sRankMap.put(DeviceInfoSettings.class.getName(), Integer.valueOf(21));
        sBaseRankMap.put("com.android.settings", Integer.valueOf(0));
    }

    public static int getRankForClassName(String className) {
        Integer rank = (Integer) sRankMap.get(className);
        return rank != null ? rank.intValue() : 1024;
    }

    public static int getBaseRankForAuthority(String authority) {
        int intValue;
        synchronized (sBaseRankMap) {
            Integer base = (Integer) sBaseRankMap.get(authority);
            if (base != null) {
                intValue = base.intValue();
            } else {
                sCurrentBaseRank++;
                sBaseRankMap.put(authority, Integer.valueOf(sCurrentBaseRank));
                intValue = sCurrentBaseRank;
            }
        }
        return intValue;
    }
}
