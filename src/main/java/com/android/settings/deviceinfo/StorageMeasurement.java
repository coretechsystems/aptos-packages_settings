package com.android.settings.deviceinfo;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.content.pm.ApplicationInfo;
import android.content.pm.IPackageStatsObserver;
import android.content.pm.PackageManager;
import android.content.pm.PackageStats;
import android.content.pm.UserInfo;
import android.os.Environment;
import android.os.Environment.UserEnvironment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.UserManager;
import android.os.storage.StorageVolume;
import android.util.Log;
import android.util.SparseLongArray;
import com.android.internal.app.IMediaContainerService;
import com.android.internal.app.IMediaContainerService.Stub;
import com.google.android.collect.Maps;
import com.google.android.collect.Sets;
import java.io.File;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

public class StorageMeasurement {
    public static final ComponentName DEFAULT_CONTAINER_COMPONENT = new ComponentName("com.android.defcontainer", "com.android.defcontainer.DefaultContainerService");
    static final boolean LOGV = Log.isLoggable("StorageMeasurement", 2);
    private static HashMap<StorageVolume, StorageMeasurement> sInstances = Maps.newHashMap();
    private static final Set<String> sMeasureMediaTypes = Sets.newHashSet(new String[]{Environment.DIRECTORY_DCIM, Environment.DIRECTORY_MOVIES, Environment.DIRECTORY_PICTURES, Environment.DIRECTORY_MUSIC, Environment.DIRECTORY_ALARMS, Environment.DIRECTORY_NOTIFICATIONS, Environment.DIRECTORY_RINGTONES, Environment.DIRECTORY_PODCASTS, Environment.DIRECTORY_DOWNLOADS, "Android"});
    private long mAvailSize;
    List<FileInfo> mFileInfoForMisc;
    private final MeasurementHandler mHandler;
    private final boolean mIsInternal;
    private final boolean mIsPrimary;
    private volatile WeakReference<MeasurementReceiver> mReceiver;
    private long mTotalSize;
    private final StorageVolume mVolume;

    static class FileInfo implements Comparable<FileInfo> {
        final String mFileName;
        final long mId;
        final long mSize;

        FileInfo(String fileName, long size, long id) {
            this.mFileName = fileName;
            this.mSize = size;
            this.mId = id;
        }

        public int compareTo(FileInfo that) {
            if (this == that || this.mSize == that.mSize) {
                return 0;
            }
            return this.mSize < that.mSize ? 1 : -1;
        }

        public String toString() {
            return this.mFileName + " : " + this.mSize + ", id:" + this.mId;
        }
    }

    public static class MeasurementDetails {
        public long appsSize;
        public long availSize;
        public long cacheSize;
        public HashMap<String, Long> mediaSize = Maps.newHashMap();
        public long miscSize;
        public long totalSize;
        public SparseLongArray usersSize = new SparseLongArray();
    }

    private class MeasurementHandler extends Handler {
        private volatile boolean mBound = false;
        private MeasurementDetails mCached;
        private final WeakReference<Context> mContext;
        private final ServiceConnection mDefContainerConn = new ServiceConnection() {
            public void onServiceConnected(ComponentName name, IBinder service) {
                IMediaContainerService imcs = Stub.asInterface(service);
                MeasurementHandler.this.mDefaultContainer = imcs;
                MeasurementHandler.this.mBound = true;
                MeasurementHandler.this.sendMessage(MeasurementHandler.this.obtainMessage(2, imcs));
            }

            public void onServiceDisconnected(ComponentName name) {
                MeasurementHandler.this.mBound = false;
                MeasurementHandler.this.removeMessages(2);
            }
        };
        private IMediaContainerService mDefaultContainer;
        private Object mLock = new Object();

        public MeasurementHandler(Context context, Looper looper) {
            super(looper);
            this.mContext = new WeakReference(context);
        }

        /* JADX WARNING: inconsistent code. */
        /* Code decompiled incorrectly, please refer to instructions dump. */
        public void handleMessage(android.os.Message r8) {
            /*
            r7 = this;
            r0 = 0;
            r3 = r8.what;
            switch(r3) {
                case 1: goto L_0x0007;
                case 2: goto L_0x0050;
                case 3: goto L_0x005b;
                case 4: goto L_0x0080;
                case 5: goto L_0x008f;
                default: goto L_0x0006;
            };
        L_0x0006:
            return;
        L_0x0007:
            r3 = r7.mCached;
            if (r3 == 0) goto L_0x0013;
        L_0x000b:
            r3 = com.android.settings.deviceinfo.StorageMeasurement.this;
            r4 = r7.mCached;
            r3.sendExactUpdate(r4);
            goto L_0x0006;
        L_0x0013:
            r3 = r7.mContext;
            if (r3 == 0) goto L_0x0020;
        L_0x0017:
            r3 = r7.mContext;
            r3 = r3.get();
            r3 = (android.content.Context) r3;
            r0 = r3;
        L_0x0020:
            if (r0 == 0) goto L_0x0006;
        L_0x0022:
            r4 = r7.mLock;
            monitor-enter(r4);
            r3 = r7.mBound;	 Catch:{ all -> 0x0039 }
            if (r3 == 0) goto L_0x003c;
        L_0x0029:
            r3 = 3;
            r7.removeMessages(r3);	 Catch:{ all -> 0x0039 }
            r3 = 2;
            r5 = r7.mDefaultContainer;	 Catch:{ all -> 0x0039 }
            r3 = r7.obtainMessage(r3, r5);	 Catch:{ all -> 0x0039 }
            r7.sendMessage(r3);	 Catch:{ all -> 0x0039 }
        L_0x0037:
            monitor-exit(r4);	 Catch:{ all -> 0x0039 }
            goto L_0x0006;
        L_0x0039:
            r3 = move-exception;
            monitor-exit(r4);	 Catch:{ all -> 0x0039 }
            throw r3;
        L_0x003c:
            r3 = new android.content.Intent;	 Catch:{ all -> 0x0039 }
            r3.<init>();	 Catch:{ all -> 0x0039 }
            r5 = com.android.settings.deviceinfo.StorageMeasurement.DEFAULT_CONTAINER_COMPONENT;	 Catch:{ all -> 0x0039 }
            r2 = r3.setComponent(r5);	 Catch:{ all -> 0x0039 }
            r3 = r7.mDefContainerConn;	 Catch:{ all -> 0x0039 }
            r5 = 1;
            r6 = android.os.UserHandle.OWNER;	 Catch:{ all -> 0x0039 }
            r0.bindServiceAsUser(r2, r3, r5, r6);	 Catch:{ all -> 0x0039 }
            goto L_0x0037;
        L_0x0050:
            r1 = r8.obj;
            r1 = (com.android.internal.app.IMediaContainerService) r1;
            r7.measureApproximateStorage(r1);
            r7.measureExactStorage(r1);
            goto L_0x0006;
        L_0x005b:
            r4 = r7.mLock;
            monitor-enter(r4);
            r3 = r7.mBound;	 Catch:{ all -> 0x0073 }
            if (r3 == 0) goto L_0x007e;
        L_0x0062:
            r3 = r7.mContext;	 Catch:{ all -> 0x0073 }
            if (r3 == 0) goto L_0x006f;
        L_0x0066:
            r3 = r7.mContext;	 Catch:{ all -> 0x0073 }
            r3 = r3.get();	 Catch:{ all -> 0x0073 }
            r3 = (android.content.Context) r3;	 Catch:{ all -> 0x0073 }
            r0 = r3;
        L_0x006f:
            if (r0 != 0) goto L_0x0076;
        L_0x0071:
            monitor-exit(r4);	 Catch:{ all -> 0x0073 }
            goto L_0x0006;
        L_0x0073:
            r3 = move-exception;
            monitor-exit(r4);	 Catch:{ all -> 0x0073 }
            throw r3;
        L_0x0076:
            r3 = 0;
            r7.mBound = r3;	 Catch:{ all -> 0x0073 }
            r3 = r7.mDefContainerConn;	 Catch:{ all -> 0x0073 }
            r0.unbindService(r3);	 Catch:{ all -> 0x0073 }
        L_0x007e:
            monitor-exit(r4);	 Catch:{ all -> 0x0073 }
            goto L_0x0006;
        L_0x0080:
            r3 = r8.obj;
            r3 = (com.android.settings.deviceinfo.StorageMeasurement.MeasurementDetails) r3;
            r7.mCached = r3;
            r3 = com.android.settings.deviceinfo.StorageMeasurement.this;
            r4 = r7.mCached;
            r3.sendExactUpdate(r4);
            goto L_0x0006;
        L_0x008f:
            r7.mCached = r0;
            goto L_0x0006;
            */
            throw new UnsupportedOperationException("Method not decompiled: com.android.settings.deviceinfo.StorageMeasurement.MeasurementHandler.handleMessage(android.os.Message):void");
        }

        private void measureApproximateStorage(IMediaContainerService imcs) {
            try {
                long[] stats = imcs.getFileSystemStats(StorageMeasurement.this.mVolume != null ? StorageMeasurement.this.mVolume.getPath() : Environment.getDataDirectory().getPath());
                StorageMeasurement.this.mTotalSize = stats[0];
                StorageMeasurement.this.mAvailSize = stats[1];
            } catch (Exception e) {
                Log.w("StorageMeasurement", "Problem in container service", e);
            }
            StorageMeasurement.this.sendInternalApproximateUpdate();
        }

        private void measureExactStorage(IMediaContainerService imcs) {
            Context context = this.mContext != null ? (Context) this.mContext.get() : null;
            if (context != null) {
                MeasurementDetails details = new MeasurementDetails();
                Message finished = obtainMessage(4, details);
                details.totalSize = StorageMeasurement.this.mTotalSize;
                details.availSize = StorageMeasurement.this.mAvailSize;
                List<UserInfo> users = ((UserManager) context.getSystemService("user")).getUsers();
                int currentUser = ActivityManager.getCurrentUser();
                UserEnvironment currentEnv = new UserEnvironment(currentUser);
                boolean measureMedia = (StorageMeasurement.this.mIsInternal && Environment.isExternalStorageEmulated()) || StorageMeasurement.this.mIsPrimary;
                if (measureMedia) {
                    for (String type : StorageMeasurement.sMeasureMediaTypes) {
                        details.mediaSize.put(type, Long.valueOf(StorageMeasurement.getDirectorySize(imcs, currentEnv.getExternalStoragePublicDirectory(type))));
                    }
                }
                if (measureMedia) {
                    File path;
                    if (StorageMeasurement.this.mIsInternal) {
                        path = currentEnv.getExternalStorageDirectory();
                    } else {
                        path = StorageMeasurement.this.mVolume.getPathFile();
                    }
                    details.miscSize = StorageMeasurement.this.measureMisc(imcs, path);
                }
                for (UserInfo user : users) {
                    StorageMeasurement.addValue(details.usersSize, user.id, StorageMeasurement.getDirectorySize(imcs, new UserEnvironment(user.id).getExternalStorageDirectory()));
                }
                PackageManager pm = context.getPackageManager();
                if (StorageMeasurement.this.mIsInternal || StorageMeasurement.this.mIsPrimary) {
                    List<ApplicationInfo> apps = pm.getInstalledApplications(8704);
                    StatsObserver observer = new StatsObserver(StorageMeasurement.this.mIsInternal, details, currentUser, finished, users.size() * apps.size());
                    for (UserInfo user2 : users) {
                        for (ApplicationInfo app : apps) {
                            pm.getPackageSizeInfo(app.packageName, user2.id, observer);
                        }
                    }
                    return;
                }
                finished.sendToTarget();
            }
        }
    }

    public interface MeasurementReceiver {
        void updateApproximate(StorageMeasurement storageMeasurement, long j, long j2);

        void updateDetails(StorageMeasurement storageMeasurement, MeasurementDetails measurementDetails);
    }

    private static class StatsObserver extends IPackageStatsObserver.Stub {
        private final int mCurrentUser;
        private final MeasurementDetails mDetails;
        private final Message mFinished;
        private final boolean mIsInternal;
        private int mRemaining;

        public StatsObserver(boolean isInternal, MeasurementDetails details, int currentUser, Message finished, int remaining) {
            this.mIsInternal = isInternal;
            this.mDetails = details;
            this.mCurrentUser = currentUser;
            this.mFinished = finished;
            this.mRemaining = remaining;
        }

        public void onGetStatsCompleted(PackageStats stats, boolean succeeded) {
            synchronized (this.mDetails) {
                if (succeeded) {
                    addStatsLocked(stats);
                }
                int i = this.mRemaining - 1;
                this.mRemaining = i;
                if (i == 0) {
                    this.mFinished.sendToTarget();
                }
            }
        }

        private void addStatsLocked(PackageStats stats) {
            MeasurementDetails measurementDetails;
            if (this.mIsInternal) {
                long codeSize = stats.codeSize;
                long dataSize = stats.dataSize;
                long cacheSize = stats.cacheSize;
                if (Environment.isExternalStorageEmulated()) {
                    codeSize += stats.externalCodeSize + stats.externalObbSize;
                    dataSize += stats.externalDataSize + stats.externalMediaSize;
                    cacheSize += stats.externalCacheSize;
                }
                if (stats.userHandle == this.mCurrentUser) {
                    measurementDetails = this.mDetails;
                    measurementDetails.appsSize += codeSize;
                    measurementDetails = this.mDetails;
                    measurementDetails.appsSize += dataSize;
                }
                StorageMeasurement.addValue(this.mDetails.usersSize, stats.userHandle, dataSize);
                measurementDetails = this.mDetails;
                measurementDetails.cacheSize += cacheSize;
                return;
            }
            measurementDetails = this.mDetails;
            measurementDetails.appsSize += ((stats.externalCodeSize + stats.externalDataSize) + stats.externalMediaSize) + stats.externalObbSize;
            measurementDetails = this.mDetails;
            measurementDetails.cacheSize += stats.externalCacheSize;
        }
    }

    public static StorageMeasurement getInstance(Context context, StorageVolume volume) {
        StorageMeasurement value;
        synchronized (sInstances) {
            value = (StorageMeasurement) sInstances.get(volume);
            if (value == null) {
                value = new StorageMeasurement(context.getApplicationContext(), volume);
                sInstances.put(volume, value);
            }
        }
        return value;
    }

    private StorageMeasurement(Context context, StorageVolume volume) {
        boolean z;
        boolean z2 = false;
        this.mVolume = volume;
        if (volume == null) {
            z = true;
        } else {
            z = false;
        }
        this.mIsInternal = z;
        if (volume != null) {
            z2 = volume.isPrimary();
        }
        this.mIsPrimary = z2;
        HandlerThread handlerThread = new HandlerThread("MemoryMeasurement");
        handlerThread.start();
        this.mHandler = new MeasurementHandler(context, handlerThread.getLooper());
    }

    public void setReceiver(MeasurementReceiver receiver) {
        if (this.mReceiver == null || this.mReceiver.get() == null) {
            this.mReceiver = new WeakReference(receiver);
        }
    }

    public void measure() {
        if (!this.mHandler.hasMessages(1)) {
            this.mHandler.sendEmptyMessage(1);
        }
    }

    public void cleanUp() {
        this.mReceiver = null;
        this.mHandler.removeMessages(1);
        this.mHandler.sendEmptyMessage(3);
    }

    public void invalidate() {
        this.mHandler.sendEmptyMessage(5);
    }

    private void sendInternalApproximateUpdate() {
        MeasurementReceiver receiver = this.mReceiver != null ? (MeasurementReceiver) this.mReceiver.get() : null;
        if (receiver != null) {
            receiver.updateApproximate(this, this.mTotalSize, this.mAvailSize);
        }
    }

    private void sendExactUpdate(MeasurementDetails details) {
        MeasurementReceiver receiver = this.mReceiver != null ? (MeasurementReceiver) this.mReceiver.get() : null;
        if (receiver != null) {
            receiver.updateDetails(this, details);
        } else if (LOGV) {
            Log.i("StorageMeasurement", "measurements dropped because receiver is null! wasted effort");
        }
    }

    private static long getDirectorySize(IMediaContainerService imcs, File path) {
        try {
            long size = imcs.calculateDirectorySize(path.toString());
            Log.d("StorageMeasurement", "getDirectorySize(" + path + ") returned " + size);
            return size;
        } catch (Exception e) {
            Log.w("StorageMeasurement", "Could not read memory from default container service for " + path, e);
            return 0;
        }
    }

    private long measureMisc(IMediaContainerService imcs, File dir) {
        this.mFileInfoForMisc = new ArrayList();
        File[] files = dir.listFiles();
        if (files == null) {
            return 0;
        }
        long counter = 0;
        long miscSize = 0;
        for (File file : files) {
            String path = file.getAbsolutePath();
            if (!sMeasureMediaTypes.contains(file.getName())) {
                long counter2;
                if (file.isFile()) {
                    long fileSize = file.length();
                    counter2 = counter + 1;
                    this.mFileInfoForMisc.add(new FileInfo(path, fileSize, counter));
                    miscSize += fileSize;
                    counter = counter2;
                } else if (file.isDirectory()) {
                    long dirSize = getDirectorySize(imcs, file);
                    counter2 = counter + 1;
                    this.mFileInfoForMisc.add(new FileInfo(path, dirSize, counter));
                    miscSize += dirSize;
                    counter = counter2;
                }
            }
        }
        Collections.sort(this.mFileInfoForMisc);
        return miscSize;
    }

    private static void addValue(SparseLongArray array, int key, long value) {
        array.put(key, array.get(key) + value);
    }
}
