package com.android.settings.deviceinfo;

import android.app.ActivityManagerNative;
import android.app.ActivityThread;
import android.content.Context;
import android.content.Intent;
import android.content.pm.UserInfo;
import android.content.res.Resources;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.RemoteException;
import android.os.UserManager;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.provider.MediaStore.Images.Media;
import android.text.format.Formatter;
import com.android.settings.MediaFormat;
import com.android.settings.R;
import com.android.settings.Settings.ManageApplicationsActivity;
import com.android.settings.deviceinfo.StorageMeasurement.MeasurementDetails;
import com.android.settings.deviceinfo.StorageMeasurement.MeasurementReceiver;
import com.google.android.collect.Lists;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

public class StorageVolumePreferenceCategory extends PreferenceCategory {
    private Preference mFormatPreference;
    private StorageItemPreference mItemApps;
    private StorageItemPreference mItemAvailable;
    private StorageItemPreference mItemCache;
    private StorageItemPreference mItemDcim;
    private StorageItemPreference mItemDownloads;
    private StorageItemPreference mItemMisc;
    private StorageItemPreference mItemMusic;
    private StorageItemPreference mItemTotal;
    private List<StorageItemPreference> mItemUsers = Lists.newArrayList();
    private final StorageMeasurement mMeasure;
    private Preference mMountTogglePreference;
    private MeasurementReceiver mReceiver = new MeasurementReceiver() {
        public void updateApproximate(StorageMeasurement meas, long totalSize, long availSize) {
            StorageVolumePreferenceCategory.this.mUpdateHandler.obtainMessage(1, new long[]{totalSize, availSize}).sendToTarget();
        }

        public void updateDetails(StorageMeasurement meas, MeasurementDetails details) {
            StorageVolumePreferenceCategory.this.mUpdateHandler.obtainMessage(2, details).sendToTarget();
        }
    };
    private final Resources mResources;
    private Preference mStorageLow;
    private final StorageManager mStorageManager;
    private long mTotalSize;
    private Handler mUpdateHandler = new Handler() {
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case 1:
                    long[] size = (long[]) msg.obj;
                    StorageVolumePreferenceCategory.this.updateApproximate(size[0], size[1]);
                    return;
                case 2:
                    StorageVolumePreferenceCategory.this.updateDetails(msg.obj);
                    return;
                default:
                    return;
            }
        }
    };
    private UsageBarPreference mUsageBarPreference;
    private boolean mUsbConnected;
    private String mUsbFunction;
    private final UserManager mUserManager;
    private final StorageVolume mVolume;

    public static class PreferenceHeader extends Preference {
        public PreferenceHeader(Context context, int titleRes) {
            super(context, null, 16842892);
            setTitle(titleRes);
        }

        public PreferenceHeader(Context context, CharSequence title) {
            super(context, null, 16842892);
            setTitle(title);
        }

        public boolean isEnabled() {
            return false;
        }
    }

    public static StorageVolumePreferenceCategory buildForInternal(Context context) {
        return new StorageVolumePreferenceCategory(context, null);
    }

    public static StorageVolumePreferenceCategory buildForPhysical(Context context, StorageVolume volume) {
        return new StorageVolumePreferenceCategory(context, volume);
    }

    private StorageVolumePreferenceCategory(Context context, StorageVolume volume) {
        super(context);
        this.mVolume = volume;
        this.mMeasure = StorageMeasurement.getInstance(context, volume);
        this.mResources = context.getResources();
        this.mStorageManager = StorageManager.from(context);
        this.mUserManager = (UserManager) context.getSystemService("user");
        setTitle(volume != null ? volume.getDescription(context) : context.getText(R.string.internal_storage));
    }

    private StorageItemPreference buildItem(int titleRes, int colorRes) {
        return new StorageItemPreference(getContext(), titleRes, colorRes);
    }

    public void init() {
        Context context = getContext();
        removeAll();
        try {
            UserInfo currentUser = ActivityManagerNative.getDefault().getCurrentUser();
            List<UserInfo> otherUsers = getUsersExcluding(currentUser);
            boolean showUsers = this.mVolume == null && otherUsers.size() > 0;
            this.mUsageBarPreference = new UsageBarPreference(context);
            this.mUsageBarPreference.setOrder(-2);
            addPreference(this.mUsageBarPreference);
            this.mItemTotal = buildItem(R.string.memory_size, 0);
            this.mItemAvailable = buildItem(R.string.memory_available, R.color.memory_avail);
            addPreference(this.mItemTotal);
            addPreference(this.mItemAvailable);
            this.mItemApps = buildItem(R.string.memory_apps_usage, R.color.memory_apps_usage);
            this.mItemDcim = buildItem(R.string.memory_dcim_usage, R.color.memory_dcim);
            this.mItemMusic = buildItem(R.string.memory_music_usage, R.color.memory_music);
            this.mItemDownloads = buildItem(R.string.memory_downloads_usage, R.color.memory_downloads);
            this.mItemCache = buildItem(R.string.memory_media_cache_usage, R.color.memory_cache);
            this.mItemMisc = buildItem(R.string.memory_media_misc_usage, R.color.memory_misc);
            this.mItemCache.setKey("cache");
            boolean showDetails = this.mVolume == null || this.mVolume.isPrimary();
            if (showDetails) {
                if (showUsers) {
                    addPreference(new PreferenceHeader(context, (CharSequence) currentUser.name));
                }
                addPreference(this.mItemApps);
                addPreference(this.mItemDcim);
                addPreference(this.mItemMusic);
                addPreference(this.mItemDownloads);
                addPreference(this.mItemCache);
                addPreference(this.mItemMisc);
                if (showUsers) {
                    addPreference(new PreferenceHeader(context, R.string.storage_other_users));
                    int count = 0;
                    for (UserInfo info : otherUsers) {
                        int count2 = count + 1;
                        Preference storageItemPreference = new StorageItemPreference(getContext(), info.name, count % 2 == 0 ? R.color.memory_user_light : R.color.memory_user_dark, info.id);
                        this.mItemUsers.add(storageItemPreference);
                        addPreference(storageItemPreference);
                        count = count2;
                    }
                }
            }
            boolean isRemovable = this.mVolume != null ? this.mVolume.isRemovable() : false;
            this.mMountTogglePreference = new Preference(context);
            if (isRemovable) {
                this.mMountTogglePreference.setTitle(R.string.sd_eject);
                this.mMountTogglePreference.setSummary(R.string.sd_eject_summary);
                addPreference(this.mMountTogglePreference);
            }
            if (this.mVolume != null) {
                this.mFormatPreference = new Preference(context);
                this.mFormatPreference.setTitle(R.string.sd_format);
                this.mFormatPreference.setSummary(R.string.sd_format_summary);
                addPreference(this.mFormatPreference);
            }
            try {
                if (ActivityThread.getPackageManager().isStorageLow()) {
                    this.mStorageLow = new Preference(context);
                    this.mStorageLow.setOrder(-1);
                    this.mStorageLow.setTitle(R.string.storage_low_title);
                    this.mStorageLow.setSummary(R.string.storage_low_summary);
                    addPreference(this.mStorageLow);
                } else if (this.mStorageLow != null) {
                    removePreference(this.mStorageLow);
                    this.mStorageLow = null;
                }
            } catch (RemoteException e) {
            }
        } catch (RemoteException e2) {
            throw new RuntimeException("Failed to get current user");
        }
    }

    public StorageVolume getStorageVolume() {
        return this.mVolume;
    }

    private void updatePreferencesFromState() {
        if (this.mVolume != null) {
            this.mMountTogglePreference.setEnabled(true);
            String state = this.mStorageManager.getVolumeState(this.mVolume.getPath());
            if ("mounted_ro".equals(state)) {
                this.mItemAvailable.setTitle(R.string.memory_available_read_only);
            } else {
                this.mItemAvailable.setTitle(R.string.memory_available);
            }
            if ("mounted".equals(state) || "mounted_ro".equals(state)) {
                this.mMountTogglePreference.setEnabled(true);
                this.mMountTogglePreference.setTitle(this.mResources.getString(R.string.sd_eject));
                this.mMountTogglePreference.setSummary(this.mResources.getString(R.string.sd_eject_summary));
                addPreference(this.mUsageBarPreference);
                addPreference(this.mItemTotal);
                addPreference(this.mItemAvailable);
            } else {
                if ("unmounted".equals(state) || "nofs".equals(state) || "unmountable".equals(state)) {
                    this.mMountTogglePreference.setEnabled(true);
                    this.mMountTogglePreference.setTitle(this.mResources.getString(R.string.sd_mount));
                    this.mMountTogglePreference.setSummary(this.mResources.getString(R.string.sd_mount_summary));
                } else {
                    this.mMountTogglePreference.setEnabled(false);
                    this.mMountTogglePreference.setTitle(this.mResources.getString(R.string.sd_mount));
                    this.mMountTogglePreference.setSummary(this.mResources.getString(R.string.sd_insert_summary));
                }
                removePreference(this.mUsageBarPreference);
                removePreference(this.mItemTotal);
                removePreference(this.mItemAvailable);
            }
            if (this.mUsbConnected && ("mtp".equals(this.mUsbFunction) || "ptp".equals(this.mUsbFunction))) {
                this.mMountTogglePreference.setEnabled(false);
                if ("mounted".equals(state) || "mounted_ro".equals(state)) {
                    this.mMountTogglePreference.setSummary(this.mResources.getString(R.string.mtp_ptp_mode_summary));
                }
                if (this.mFormatPreference != null) {
                    this.mFormatPreference.setEnabled(false);
                    this.mFormatPreference.setSummary(this.mResources.getString(R.string.mtp_ptp_mode_summary));
                }
            } else if (this.mFormatPreference != null) {
                this.mFormatPreference.setEnabled(this.mMountTogglePreference.isEnabled());
                this.mFormatPreference.setSummary(this.mResources.getString(R.string.sd_format_summary));
            }
        }
    }

    public void updateApproximate(long totalSize, long availSize) {
        this.mItemTotal.setSummary(formatSize(totalSize));
        this.mItemAvailable.setSummary(formatSize(availSize));
        this.mTotalSize = totalSize;
        long usedSize = totalSize - availSize;
        this.mUsageBarPreference.clear();
        this.mUsageBarPreference.addEntry(0, ((float) usedSize) / ((float) totalSize), -7829368);
        this.mUsageBarPreference.commit();
        updatePreferencesFromState();
    }

    private static long totalValues(HashMap<String, Long> map, String... keys) {
        long total = 0;
        for (String key : keys) {
            if (map.containsKey(key)) {
                total += ((Long) map.get(key)).longValue();
            }
        }
        return total;
    }

    public void updateDetails(MeasurementDetails details) {
        boolean showDetails = this.mVolume == null || this.mVolume.isPrimary();
        if (showDetails) {
            this.mItemTotal.setSummary(formatSize(details.totalSize));
            this.mItemAvailable.setSummary(formatSize(details.availSize));
            this.mUsageBarPreference.clear();
            updatePreference(this.mItemApps, details.appsSize);
            updatePreference(this.mItemDcim, totalValues(details.mediaSize, Environment.DIRECTORY_DCIM, Environment.DIRECTORY_MOVIES, Environment.DIRECTORY_PICTURES));
            updatePreference(this.mItemMusic, totalValues(details.mediaSize, Environment.DIRECTORY_MUSIC, Environment.DIRECTORY_ALARMS, Environment.DIRECTORY_NOTIFICATIONS, Environment.DIRECTORY_RINGTONES, Environment.DIRECTORY_PODCASTS));
            updatePreference(this.mItemDownloads, totalValues(details.mediaSize, Environment.DIRECTORY_DOWNLOADS));
            updatePreference(this.mItemCache, details.cacheSize);
            updatePreference(this.mItemMisc, details.miscSize);
            for (StorageItemPreference userPref : this.mItemUsers) {
                updatePreference(userPref, details.usersSize.get(userPref.userHandle));
            }
            this.mUsageBarPreference.commit();
        }
    }

    private void updatePreference(StorageItemPreference pref, long size) {
        if (size > 0) {
            pref.setSummary(formatSize(size));
            this.mUsageBarPreference.addEntry(pref.getOrder(), ((float) size) / ((float) this.mTotalSize), pref.color);
            return;
        }
        removePreference(pref);
    }

    private void measure() {
        this.mMeasure.invalidate();
        this.mMeasure.measure();
    }

    public void onResume() {
        this.mMeasure.setReceiver(this.mReceiver);
        measure();
    }

    public void onStorageStateChanged() {
        init();
        measure();
    }

    public void onUsbStateChanged(boolean isUsbConnected, String usbFunction) {
        this.mUsbConnected = isUsbConnected;
        this.mUsbFunction = usbFunction;
        measure();
    }

    public void onMediaScannerFinished() {
        measure();
    }

    public void onCacheCleared() {
        measure();
    }

    public void onPause() {
        this.mMeasure.cleanUp();
    }

    private String formatSize(long size) {
        return Formatter.formatFileSize(getContext(), size);
    }

    public boolean mountToggleClicked(Preference preference) {
        return preference == this.mMountTogglePreference;
    }

    public Intent intentForClick(Preference pref) {
        String key = pref.getKey();
        Intent intent;
        if (pref == this.mFormatPreference) {
            intent = new Intent("android.intent.action.VIEW");
            intent.setClass(getContext(), MediaFormat.class);
            intent.putExtra("storage_volume", this.mVolume);
            return intent;
        } else if (pref == this.mItemApps) {
            intent = new Intent("android.intent.action.MANAGE_PACKAGE_STORAGE");
            intent.setClass(getContext(), ManageApplicationsActivity.class);
            return intent;
        } else if (pref == this.mItemDownloads) {
            return new Intent("android.intent.action.VIEW_DOWNLOADS").putExtra("android.app.DownloadManager.extra_sortBySize", true);
        } else {
            if (pref == this.mItemMusic) {
                intent = new Intent("android.intent.action.GET_CONTENT");
                intent.setType("audio/mp3");
                return intent;
            } else if (pref == this.mItemDcim) {
                intent = new Intent("android.intent.action.VIEW");
                intent.putExtra("android.intent.extra.LOCAL_ONLY", true);
                intent.setData(Media.EXTERNAL_CONTENT_URI);
                return intent;
            } else if (pref != this.mItemMisc) {
                return null;
            } else {
                intent = new Intent(getContext().getApplicationContext(), MiscFilesHandler.class);
                intent.putExtra("storage_volume", this.mVolume);
                return intent;
            }
        }
    }

    private List<UserInfo> getUsersExcluding(UserInfo excluding) {
        List<UserInfo> users = this.mUserManager.getUsers();
        Iterator<UserInfo> i = users.iterator();
        while (i.hasNext()) {
            if (((UserInfo) i.next()).id == excluding.id) {
                i.remove();
            }
        }
        return users;
    }
}
