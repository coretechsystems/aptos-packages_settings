package com.android.settings;

import android.app.ActivityManager;
import android.app.ActivityManagerNative;
import android.app.AlertDialog.Builder;
import android.app.Dialog;
import android.app.Fragment;
import android.app.IActivityManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ResolveInfo;
import android.content.pm.Signature;
import android.content.pm.UserInfo;
import android.content.res.Resources;
import android.content.res.Resources.NotFoundException;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.UserHandle;
import android.os.UserManager;
import android.preference.Preference;
import android.preference.PreferenceFrameLayout;
import android.preference.PreferenceFrameLayout.LayoutParams;
import android.preference.PreferenceGroup;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Profile;
import android.service.persistentdata.PersistentDataBlockManager;
import android.telephony.TelephonyManager;
import android.text.BidiFormatter;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import com.android.internal.util.UserIcons;
import com.android.settings.UserSpinnerAdapter.UserDetails;
import com.android.settings.dashboard.DashboardTile;
import com.android.settings.drawable.CircleFramedDrawable;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

public final class Utils {
    public static final int[] BADNESS_COLORS = new int[]{0, -3917784, -1750760, -754944, -344276, -9986505, -16089278};
    private static Signature[] sSystemSignature;

    public static boolean updatePreferenceToSpecificActivityOrRemove(Context context, PreferenceGroup parentPreferenceGroup, String preferenceKey, int flags) {
        Preference preference = parentPreferenceGroup.findPreference(preferenceKey);
        if (preference == null) {
            return false;
        }
        Intent intent = preference.getIntent();
        if (intent != null) {
            PackageManager pm = context.getPackageManager();
            List<ResolveInfo> list = pm.queryIntentActivities(intent, 0);
            int listSize = list.size();
            for (int i = 0; i < listSize; i++) {
                ResolveInfo resolveInfo = (ResolveInfo) list.get(i);
                if ((resolveInfo.activityInfo.applicationInfo.flags & 1) != 0) {
                    preference.setIntent(new Intent().setClassName(resolveInfo.activityInfo.packageName, resolveInfo.activityInfo.name));
                    if ((flags & 1) != 0) {
                        preference.setTitle(resolveInfo.loadLabel(pm));
                    }
                    return true;
                }
            }
        }
        parentPreferenceGroup.removePreference(preference);
        return false;
    }

    public static boolean updateTileToSpecificActivityFromMetaDataOrRemove(Context context, DashboardTile tile) {
        Intent intent = tile.intent;
        if (intent != null) {
            PackageManager pm = context.getPackageManager();
            List<ResolveInfo> list = pm.queryIntentActivities(intent, 128);
            int listSize = list.size();
            for (int i = 0; i < listSize; i++) {
                ResolveInfo resolveInfo = (ResolveInfo) list.get(i);
                if ((resolveInfo.activityInfo.applicationInfo.flags & 1) != 0) {
                    String title = null;
                    String summary = null;
                    try {
                        Resources res = pm.getResourcesForApplication(resolveInfo.activityInfo.packageName);
                        Bundle metaData = resolveInfo.activityInfo.metaData;
                        if (!(res == null || metaData == null)) {
                            Drawable icon = res.getDrawable(metaData.getInt("com.android.settings.icon"));
                            title = res.getString(metaData.getInt("com.android.settings.title"));
                            summary = res.getString(metaData.getInt("com.android.settings.summary"));
                        }
                    } catch (NameNotFoundException e) {
                    } catch (NotFoundException e2) {
                    }
                    if (TextUtils.isEmpty(title)) {
                        title = resolveInfo.loadLabel(pm).toString();
                    }
                    tile.title = title;
                    tile.summary = summary;
                    tile.intent = new Intent().setClassName(resolveInfo.activityInfo.packageName, resolveInfo.activityInfo.name);
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean isMonkeyRunning() {
        return ActivityManager.isUserAMonkey();
    }

    public static boolean isVoiceCapable(Context context) {
        TelephonyManager telephony = (TelephonyManager) context.getSystemService("phone");
        return telephony != null && telephony.isVoiceCapable();
    }

    public static boolean isWifiOnly(Context context) {
        if (((ConnectivityManager) context.getSystemService("connectivity")).isNetworkSupported(0)) {
            return false;
        }
        return true;
    }

    public static String getWifiIpAddresses(Context context) {
        return formatIpAddresses(((ConnectivityManager) context.getSystemService("connectivity")).getLinkProperties(1));
    }

    public static String getDefaultIpAddresses(ConnectivityManager cm) {
        return formatIpAddresses(cm.getActiveLinkProperties());
    }

    private static String formatIpAddresses(LinkProperties prop) {
        String str = null;
        if (prop != null) {
            Iterator<InetAddress> iter = prop.getAllAddresses().iterator();
            if (iter.hasNext()) {
                str = "";
                while (iter.hasNext()) {
                    str = str + ((InetAddress) iter.next()).getHostAddress();
                    if (iter.hasNext()) {
                        str = str + "\n";
                    }
                }
            }
        }
        return str;
    }

    public static Locale createLocaleFromString(String localeStr) {
        if (localeStr == null) {
            return Locale.getDefault();
        }
        String[] brokenDownLocale = localeStr.split("_", 3);
        if (1 == brokenDownLocale.length) {
            return new Locale(brokenDownLocale[0]);
        }
        if (2 == brokenDownLocale.length) {
            return new Locale(brokenDownLocale[0], brokenDownLocale[1]);
        }
        return new Locale(brokenDownLocale[0], brokenDownLocale[1], brokenDownLocale[2]);
    }

    public static String formatPercentage(long amount, long total) {
        return formatPercentage(((double) amount) / ((double) total));
    }

    public static String formatPercentage(int percentage) {
        return formatPercentage(((double) percentage) / 100.0d);
    }

    private static String formatPercentage(double percentage) {
        return BidiFormatter.getInstance().unicodeWrap(NumberFormat.getPercentInstance().format(percentage));
    }

    public static boolean isBatteryPresent(Intent batteryChangedIntent) {
        return batteryChangedIntent.getBooleanExtra("present", true);
    }

    public static String getBatteryPercentage(Intent batteryChangedIntent) {
        return formatPercentage(getBatteryLevel(batteryChangedIntent));
    }

    public static int getBatteryLevel(Intent batteryChangedIntent) {
        int level = batteryChangedIntent.getIntExtra("level", 0);
        return (level * 100) / batteryChangedIntent.getIntExtra("scale", 100);
    }

    public static String getBatteryStatus(Resources res, Intent batteryChangedIntent) {
        Intent intent = batteryChangedIntent;
        int plugType = intent.getIntExtra("plugged", 0);
        int status = intent.getIntExtra("status", 1);
        if (status == 2) {
            int resId;
            if (plugType == 1) {
                resId = R.string.battery_info_status_charging_ac;
            } else if (plugType == 2) {
                resId = R.string.battery_info_status_charging_usb;
            } else if (plugType == 4) {
                resId = R.string.battery_info_status_charging_wireless;
            } else {
                resId = R.string.battery_info_status_charging;
            }
            return res.getString(resId);
        } else if (status == 3) {
            return res.getString(R.string.battery_info_status_discharging);
        } else {
            if (status == 4) {
                return res.getString(R.string.battery_info_status_not_charging);
            }
            if (status == 5) {
                return res.getString(R.string.battery_info_status_full);
            }
            return res.getString(R.string.battery_info_status_unknown);
        }
    }

    public static void forcePrepareCustomPreferencesList(ViewGroup parent, View child, ListView list, boolean ignoreSidePadding) {
        list.setScrollBarStyle(33554432);
        list.setClipToPadding(false);
        prepareCustomPreferencesList(parent, child, list, ignoreSidePadding);
    }

    public static void prepareCustomPreferencesList(ViewGroup parent, View child, View list, boolean ignoreSidePadding) {
        boolean movePadding;
        if (list.getScrollBarStyle() == 33554432) {
            movePadding = true;
        } else {
            movePadding = false;
        }
        if (movePadding) {
            Resources res = list.getResources();
            int paddingSide = res.getDimensionPixelSize(R.dimen.settings_side_margin);
            int paddingBottom = res.getDimensionPixelSize(17104931);
            if (parent instanceof PreferenceFrameLayout) {
                int effectivePaddingSide;
                ((LayoutParams) child.getLayoutParams()).removeBorders = true;
                if (ignoreSidePadding) {
                    effectivePaddingSide = 0;
                } else {
                    effectivePaddingSide = paddingSide;
                }
                list.setPaddingRelative(effectivePaddingSide, 0, effectivePaddingSide, paddingBottom);
                return;
            }
            list.setPaddingRelative(paddingSide, 0, paddingSide, paddingBottom);
        }
    }

    public static void forceCustomPadding(View view, boolean additive) {
        int paddingStart;
        Resources res = view.getResources();
        int paddingSide = res.getDimensionPixelSize(R.dimen.settings_side_margin);
        if (additive) {
            paddingStart = view.getPaddingStart();
        } else {
            paddingStart = 0;
        }
        int paddingStart2 = paddingSide + paddingStart;
        if (additive) {
            paddingStart = view.getPaddingEnd();
        } else {
            paddingStart = 0;
        }
        view.setPaddingRelative(paddingStart2, 0, paddingSide + paddingStart, res.getDimensionPixelSize(17104931));
    }

    public static int getTetheringLabel(ConnectivityManager cm) {
        boolean usbAvailable;
        boolean wifiAvailable;
        String[] usbRegexs = cm.getTetherableUsbRegexs();
        String[] wifiRegexs = cm.getTetherableWifiRegexs();
        String[] bluetoothRegexs = cm.getTetherableBluetoothRegexs();
        if (usbRegexs.length != 0) {
            usbAvailable = true;
        } else {
            usbAvailable = false;
        }
        if (wifiRegexs.length != 0) {
            wifiAvailable = true;
        } else {
            wifiAvailable = false;
        }
        boolean bluetoothAvailable;
        if (bluetoothRegexs.length != 0) {
            bluetoothAvailable = true;
        } else {
            bluetoothAvailable = false;
        }
        if (wifiAvailable && usbAvailable && bluetoothAvailable) {
            return R.string.tether_settings_title_all;
        }
        if (wifiAvailable && usbAvailable) {
            return R.string.tether_settings_title_all;
        }
        if (wifiAvailable && bluetoothAvailable) {
            return R.string.tether_settings_title_all;
        }
        if (wifiAvailable) {
            return R.string.tether_settings_title_wifi;
        }
        if (usbAvailable && bluetoothAvailable) {
            return R.string.tether_settings_title_usb_bluetooth;
        }
        if (usbAvailable) {
            return R.string.tether_settings_title_usb;
        }
        return R.string.tether_settings_title_bluetooth;
    }

    public static boolean copyMeProfilePhoto(Context context, UserInfo user) {
        InputStream avatarDataStream = Contacts.openContactPhotoInputStream(context.getContentResolver(), Profile.CONTENT_URI, true);
        if (avatarDataStream == null) {
            return false;
        }
        ((UserManager) context.getSystemService("user")).setUserIcon(user != null ? user.id : UserHandle.myUserId(), BitmapFactory.decodeStream(avatarDataStream));
        try {
            avatarDataStream.close();
            return true;
        } catch (IOException e) {
            return true;
        }
    }

    public static String getMeProfileName(Context context, boolean full) {
        if (full) {
            return getProfileDisplayName(context);
        }
        return getShorterNameIfPossible(context);
    }

    private static String getShorterNameIfPossible(Context context) {
        String given = getLocalProfileGivenName(context);
        return !TextUtils.isEmpty(given) ? given : getProfileDisplayName(context);
    }

    private static String getLocalProfileGivenName(Context context) {
        ContentResolver cr = context.getContentResolver();
        Cursor localRawProfile = cr.query(Profile.CONTENT_RAW_CONTACTS_URI, new String[]{"_id"}, "account_type IS NULL AND account_name IS NULL", null, null);
        if (localRawProfile == null) {
            return null;
        }
        try {
            if (!localRawProfile.moveToFirst()) {
                return null;
            }
            long localRowProfileId = localRawProfile.getLong(0);
            localRawProfile.close();
            Cursor structuredName = cr.query(Profile.CONTENT_URI.buildUpon().appendPath("data").build(), new String[]{"data2", "data3"}, "raw_contact_id=" + localRowProfileId, null, null);
            if (structuredName == null) {
                return null;
            }
            try {
                if (!structuredName.moveToFirst()) {
                    return null;
                }
                String partialName = structuredName.getString(0);
                if (TextUtils.isEmpty(partialName)) {
                    partialName = structuredName.getString(1);
                }
                structuredName.close();
                return partialName;
            } finally {
                structuredName.close();
            }
        } finally {
            localRawProfile.close();
        }
    }

    private static final String getProfileDisplayName(Context context) {
        String str = null;
        Cursor profile = context.getContentResolver().query(Profile.CONTENT_URI, new String[]{"display_name"}, str, str, str);
        if (profile != null) {
            try {
                if (profile.moveToFirst()) {
                    str = profile.getString(0);
                    profile.close();
                }
            } finally {
                profile.close();
            }
        }
        return str;
    }

    public static Dialog buildGlobalChangeWarningDialog(Context context, int titleResId, final Runnable positiveAction) {
        Builder builder = new Builder(context);
        builder.setTitle(titleResId);
        builder.setMessage(R.string.global_change_warning);
        builder.setPositiveButton(17039370, new OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                positiveAction.run();
            }
        });
        builder.setNegativeButton(17039360, null);
        return builder.create();
    }

    public static boolean hasMultipleUsers(Context context) {
        return ((UserManager) context.getSystemService("user")).getUsers().size() > 1;
    }

    public static void startWithFragment(Context context, String fragmentName, Bundle args, Fragment resultTo, int resultRequestCode, int titleResId, CharSequence title) {
        startWithFragment(context, fragmentName, args, resultTo, resultRequestCode, titleResId, title, false);
    }

    public static void startWithFragment(Context context, String fragmentName, Bundle args, Fragment resultTo, int resultRequestCode, int titleResId, CharSequence title, boolean isShortcut) {
        Intent intent = onBuildStartFragmentIntent(context, fragmentName, args, titleResId, title, isShortcut);
        if (resultTo == null) {
            context.startActivity(intent);
        } else {
            resultTo.startActivityForResult(intent, resultRequestCode);
        }
    }

    public static void startWithFragmentAsUser(Context context, String fragmentName, Bundle args, int titleResId, CharSequence title, boolean isShortcut, UserHandle userHandle) {
        Intent intent = onBuildStartFragmentIntent(context, fragmentName, args, titleResId, title, isShortcut);
        intent.addFlags(268435456);
        intent.addFlags(32768);
        context.startActivityAsUser(intent, userHandle);
    }

    public static Intent onBuildStartFragmentIntent(Context context, String fragmentName, Bundle args, int titleResId, CharSequence title, boolean isShortcut) {
        Intent intent = new Intent("android.intent.action.MAIN");
        intent.setClass(context, SubSettings.class);
        intent.putExtra(":settings:show_fragment", fragmentName);
        intent.putExtra(":settings:show_fragment_args", args);
        intent.putExtra(":settings:show_fragment_title_resid", titleResId);
        intent.putExtra(":settings:show_fragment_title", title);
        intent.putExtra(":settings:show_fragment_as_shortcut", isShortcut);
        return intent;
    }

    public static boolean isManagedProfile(UserManager userManager) {
        return userManager.getUserInfo(userManager.getUserHandle()).isManagedProfile();
    }

    public static UserSpinnerAdapter createUserSpinnerAdapter(UserManager userManager, Context context) {
        List<UserHandle> userProfiles = userManager.getUserProfiles();
        if (userProfiles.size() < 2) {
            return null;
        }
        UserHandle myUserHandle = new UserHandle(UserHandle.myUserId());
        userProfiles.remove(myUserHandle);
        userProfiles.add(0, myUserHandle);
        ArrayList<UserDetails> userDetails = new ArrayList(userProfiles.size());
        int count = userProfiles.size();
        for (int i = 0; i < count; i++) {
            userDetails.add(new UserDetails((UserHandle) userProfiles.get(i), userManager, context));
        }
        return new UserSpinnerAdapter(context, userDetails);
    }

    public static UserHandle getSecureTargetUser(IBinder activityToken, UserManager um, Bundle arguments, Bundle intentExtras) {
        UserHandle argumentsUser = null;
        UserHandle currentUser = new UserHandle(UserHandle.myUserId());
        IActivityManager am = ActivityManagerNative.getDefault();
        try {
            boolean launchedFromSettingsApp = "com.android.settings".equals(am.getLaunchedFromPackage(activityToken));
            UserHandle launchedFromUser = new UserHandle(UserHandle.getUserId(am.getLaunchedFromUid(activityToken)));
            if (launchedFromUser != null && !launchedFromUser.equals(currentUser) && isProfileOf(um, launchedFromUser)) {
                return launchedFromUser;
            }
            UserHandle extrasUser;
            if (intentExtras != null) {
                extrasUser = (UserHandle) intentExtras.getParcelable("android.intent.extra.USER");
            } else {
                extrasUser = null;
            }
            if (extrasUser != null && !extrasUser.equals(currentUser) && launchedFromSettingsApp && isProfileOf(um, extrasUser)) {
                return extrasUser;
            }
            if (arguments != null) {
                argumentsUser = (UserHandle) arguments.getParcelable("android.intent.extra.USER");
            }
            if (argumentsUser != null && !argumentsUser.equals(currentUser) && launchedFromSettingsApp && isProfileOf(um, argumentsUser)) {
                return argumentsUser;
            }
            return currentUser;
        } catch (RemoteException e) {
            Log.v("Settings", "Could not talk to activity manager.", e);
        }
    }

    private static boolean isProfileOf(UserManager um, UserHandle otherUser) {
        if (um == null || otherUser == null) {
            return false;
        }
        if (UserHandle.myUserId() == otherUser.getIdentifier() || um.getUserProfiles().contains(otherUser)) {
            return true;
        }
        return false;
    }

    public static Dialog createRemoveConfirmationDialog(Context context, int removingUserId, OnClickListener onConfirmListener) {
        int titleResId;
        int messageResId;
        UserInfo userInfo = ((UserManager) context.getSystemService("user")).getUserInfo(removingUserId);
        if (UserHandle.myUserId() == removingUserId) {
            titleResId = R.string.user_confirm_remove_self_title;
            messageResId = R.string.user_confirm_remove_self_message;
        } else if (userInfo.isRestricted()) {
            titleResId = R.string.user_profile_confirm_remove_title;
            messageResId = R.string.user_profile_confirm_remove_message;
        } else if (userInfo.isManagedProfile()) {
            titleResId = R.string.work_profile_confirm_remove_title;
            messageResId = R.string.work_profile_confirm_remove_message;
        } else {
            titleResId = R.string.user_confirm_remove_title;
            messageResId = R.string.user_confirm_remove_message;
        }
        return new Builder(context).setTitle(titleResId).setMessage(messageResId).setPositiveButton(R.string.user_delete_button, onConfirmListener).setNegativeButton(17039360, null).create();
    }

    static boolean isOemUnlockEnabled(Context context) {
        return ((PersistentDataBlockManager) context.getSystemService("persistent_data_block")).getOemUnlockEnabled();
    }

    static void setOemUnlockEnabled(Context context, boolean enabled) {
        ((PersistentDataBlockManager) context.getSystemService("persistent_data_block")).setOemUnlockEnabled(enabled);
    }

    public static Drawable getUserIcon(Context context, UserManager um, UserInfo user) {
        if (user.iconPath != null) {
            Bitmap icon = um.getUserIcon(user.id);
            if (icon != null) {
                return CircleFramedDrawable.getInstance(context, icon);
            }
        }
        return UserIcons.getDefaultUserIcon(user.id, false);
    }

    public static boolean showSimCardTile(Context context) {
        TelephonyManager tm = (TelephonyManager) context.getSystemService("phone");
        return false;
    }

    public static boolean isSystemPackage(PackageManager pm, PackageInfo pkg) {
        if (sSystemSignature == null) {
            sSystemSignature = new Signature[]{getSystemSignature(pm)};
        }
        if (sSystemSignature[0] == null || !sSystemSignature[0].equals(getFirstSignature(pkg))) {
            return false;
        }
        return true;
    }

    private static Signature getFirstSignature(PackageInfo pkg) {
        if (pkg == null || pkg.signatures == null || pkg.signatures.length <= 0) {
            return null;
        }
        return pkg.signatures[0];
    }

    private static Signature getSystemSignature(PackageManager pm) {
        try {
            return getFirstSignature(pm.getPackageInfo("android", 64));
        } catch (NameNotFoundException e) {
            return null;
        }
    }

    public static String formatElapsedTime(Context context, double millis, boolean withSeconds) {
        StringBuilder sb = new StringBuilder();
        int seconds = (int) Math.floor(millis / 1000.0d);
        if (!withSeconds) {
            seconds += 30;
        }
        int days = 0;
        int hours = 0;
        int minutes = 0;
        if (seconds >= 86400) {
            days = seconds / 86400;
            seconds -= 86400 * days;
        }
        if (seconds >= 3600) {
            hours = seconds / 3600;
            seconds -= hours * 3600;
        }
        if (seconds >= 60) {
            minutes = seconds / 60;
            seconds -= minutes * 60;
        }
        if (withSeconds) {
            if (days > 0) {
                sb.append(context.getString(R.string.battery_history_days, new Object[]{Integer.valueOf(days), Integer.valueOf(hours), Integer.valueOf(minutes), Integer.valueOf(seconds)}));
            } else if (hours > 0) {
                sb.append(context.getString(R.string.battery_history_hours, new Object[]{Integer.valueOf(hours), Integer.valueOf(minutes), Integer.valueOf(seconds)}));
            } else if (minutes > 0) {
                sb.append(context.getString(R.string.battery_history_minutes, new Object[]{Integer.valueOf(minutes), Integer.valueOf(seconds)}));
            } else {
                sb.append(context.getString(R.string.battery_history_seconds, new Object[]{Integer.valueOf(seconds)}));
            }
        } else if (days > 0) {
            sb.append(context.getString(R.string.battery_history_days_no_seconds, new Object[]{Integer.valueOf(days), Integer.valueOf(hours), Integer.valueOf(minutes)}));
        } else if (hours > 0) {
            sb.append(context.getString(R.string.battery_history_hours_no_seconds, new Object[]{Integer.valueOf(hours), Integer.valueOf(minutes)}));
        } else {
            sb.append(context.getString(R.string.battery_history_minutes_no_seconds, new Object[]{Integer.valueOf(minutes)}));
        }
        return sb.toString();
    }
}
