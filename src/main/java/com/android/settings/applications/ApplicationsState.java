package com.android.settings.applications;

import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.IPackageStatsObserver.Stub;
import android.content.pm.PackageManager;
import android.content.pm.PackageStats;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.os.UserHandle;
import android.text.format.Formatter;
import java.io.File;
import java.text.Collator;
import java.text.Normalizer;
import java.text.Normalizer.Form;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Pattern;

public class ApplicationsState {
    public static final AppFilter ALL_ENABLED_FILTER = new AppFilter() {
        public void init() {
        }

        public boolean filterApp(ApplicationInfo info) {
            if (info.enabled) {
                return true;
            }
            return false;
        }
    };
    public static final Comparator<AppEntry> ALPHA_COMPARATOR = new Comparator<AppEntry>() {
        private final Collator sCollator = Collator.getInstance();

        public int compare(AppEntry object1, AppEntry object2) {
            boolean normal1;
            boolean normal2;
            if (!object1.info.enabled || (object1.info.flags & 8388608) == 0) {
                normal1 = false;
            } else {
                normal1 = true;
            }
            if (!object2.info.enabled || (object2.info.flags & 8388608) == 0) {
                normal2 = false;
            } else {
                normal2 = true;
            }
            if (normal1 == normal2) {
                return this.sCollator.compare(object1.label, object2.label);
            }
            if (normal1) {
                return -1;
            }
            return 1;
        }
    };
    public static final AppFilter DISABLED_FILTER = new AppFilter() {
        public void init() {
        }

        public boolean filterApp(ApplicationInfo info) {
            if (info.enabled) {
                return false;
            }
            return true;
        }
    };
    public static final Comparator<AppEntry> EXTERNAL_SIZE_COMPARATOR = new Comparator<AppEntry>() {
        private final Collator sCollator = Collator.getInstance();

        public int compare(AppEntry object1, AppEntry object2) {
            if (object1.externalSize < object2.externalSize) {
                return 1;
            }
            if (object1.externalSize > object2.externalSize) {
                return -1;
            }
            return this.sCollator.compare(object1.label, object2.label);
        }
    };
    public static final Comparator<AppEntry> INTERNAL_SIZE_COMPARATOR = new Comparator<AppEntry>() {
        private final Collator sCollator = Collator.getInstance();

        public int compare(AppEntry object1, AppEntry object2) {
            if (object1.internalSize < object2.internalSize) {
                return 1;
            }
            if (object1.internalSize > object2.internalSize) {
                return -1;
            }
            return this.sCollator.compare(object1.label, object2.label);
        }
    };
    public static final AppFilter ON_SD_CARD_FILTER = new AppFilter() {
        final CanBeOnSdCardChecker mCanBeOnSdCardChecker = new CanBeOnSdCardChecker();

        public void init() {
            this.mCanBeOnSdCardChecker.init();
        }

        public boolean filterApp(ApplicationInfo info) {
            return this.mCanBeOnSdCardChecker.check(info);
        }
    };
    static final Pattern REMOVE_DIACRITICALS_PATTERN = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
    public static final Comparator<AppEntry> SIZE_COMPARATOR = new Comparator<AppEntry>() {
        private final Collator sCollator = Collator.getInstance();

        public int compare(AppEntry object1, AppEntry object2) {
            if (object1.size < object2.size) {
                return 1;
            }
            if (object1.size > object2.size) {
                return -1;
            }
            return this.sCollator.compare(object1.label, object2.label);
        }
    };
    public static final AppFilter THIRD_PARTY_FILTER = new AppFilter() {
        public void init() {
        }

        public boolean filterApp(ApplicationInfo info) {
            if ((info.flags & 128) == 0 && (info.flags & 1) != 0) {
                return false;
            }
            return true;
        }
    };
    static ApplicationsState sInstance;
    static final Object sLock = new Object();
    final ArrayList<Session> mActiveSessions = new ArrayList();
    final ArrayList<AppEntry> mAppEntries = new ArrayList();
    List<ApplicationInfo> mApplications = new ArrayList();
    final BackgroundHandler mBackgroundHandler;
    final Context mContext;
    String mCurComputingSizePkg;
    long mCurId = 1;
    final HashMap<String, AppEntry> mEntriesMap = new HashMap();
    boolean mHaveDisabledApps;
    final InterestingConfigChanges mInterestingConfigChanges = new InterestingConfigChanges();
    final MainHandler mMainHandler = new MainHandler();
    PackageIntentReceiver mPackageIntentReceiver;
    final PackageManager mPm;
    final ArrayList<Session> mRebuildingSessions = new ArrayList();
    boolean mResumed;
    final int mRetrieveFlags;
    final ArrayList<Session> mSessions = new ArrayList();
    boolean mSessionsChanged;
    final HandlerThread mThread;

    public interface AppFilter {
        boolean filterApp(ApplicationInfo applicationInfo);

        void init();
    }

    public static class SizeInfo {
        long cacheSize;
        long codeSize;
        long dataSize;
        long externalCacheSize;
        long externalCodeSize;
        long externalDataSize;
    }

    public static class AppEntry extends SizeInfo {
        final File apkFile;
        long externalSize;
        String externalSizeStr;
        Drawable icon;
        final long id;
        ApplicationInfo info;
        long internalSize;
        String internalSizeStr;
        String label;
        boolean mounted;
        String normalizedLabel;
        long size = -1;
        long sizeLoadStart;
        boolean sizeStale = true;
        String sizeStr;

        String getNormalizedLabel() {
            if (this.normalizedLabel != null) {
                return this.normalizedLabel;
            }
            this.normalizedLabel = ApplicationsState.normalize(this.label);
            return this.normalizedLabel;
        }

        AppEntry(Context context, ApplicationInfo info, long id) {
            this.apkFile = new File(info.sourceDir);
            this.id = id;
            this.info = info;
            ensureLabel(context);
        }

        void ensureLabel(Context context) {
            if (this.label != null && this.mounted) {
                return;
            }
            if (this.apkFile.exists()) {
                this.mounted = true;
                CharSequence label = this.info.loadLabel(context.getPackageManager());
                this.label = label != null ? label.toString() : this.info.packageName;
                return;
            }
            this.mounted = false;
            this.label = this.info.packageName;
        }

        boolean ensureIconLocked(Context context, PackageManager pm) {
            if (this.icon == null) {
                if (this.apkFile.exists()) {
                    this.icon = this.info.loadIcon(pm);
                    return true;
                }
                this.mounted = false;
                this.icon = context.getResources().getDrawable(17303234);
            } else if (!this.mounted && this.apkFile.exists()) {
                this.mounted = true;
                this.icon = this.info.loadIcon(pm);
                return true;
            }
            return false;
        }
    }

    class BackgroundHandler extends Handler {
        boolean mRunning;
        final Stub mStatsObserver = new Stub() {
            public void onGetStatsCompleted(PackageStats stats, boolean succeeded) {
                boolean sizeChanged = false;
                synchronized (ApplicationsState.this.mEntriesMap) {
                    AppEntry entry = (AppEntry) ApplicationsState.this.mEntriesMap.get(stats.packageName);
                    if (entry != null) {
                        synchronized (entry) {
                            entry.sizeStale = false;
                            entry.sizeLoadStart = 0;
                            long externalCodeSize = stats.externalCodeSize + stats.externalObbSize;
                            long externalDataSize = stats.externalDataSize + stats.externalMediaSize;
                            long newSize = (externalCodeSize + externalDataSize) + ApplicationsState.this.getTotalInternalSize(stats);
                            if (!(entry.size == newSize && entry.cacheSize == stats.cacheSize && entry.codeSize == stats.codeSize && entry.dataSize == stats.dataSize && entry.externalCodeSize == externalCodeSize && entry.externalDataSize == externalDataSize && entry.externalCacheSize == stats.externalCacheSize)) {
                                entry.size = newSize;
                                entry.cacheSize = stats.cacheSize;
                                entry.codeSize = stats.codeSize;
                                entry.dataSize = stats.dataSize;
                                entry.externalCodeSize = externalCodeSize;
                                entry.externalDataSize = externalDataSize;
                                entry.externalCacheSize = stats.externalCacheSize;
                                entry.sizeStr = ApplicationsState.this.getSizeStr(entry.size);
                                entry.internalSize = ApplicationsState.this.getTotalInternalSize(stats);
                                entry.internalSizeStr = ApplicationsState.this.getSizeStr(entry.internalSize);
                                entry.externalSize = ApplicationsState.this.getTotalExternalSize(stats);
                                entry.externalSizeStr = ApplicationsState.this.getSizeStr(entry.externalSize);
                                sizeChanged = true;
                            }
                        }
                        if (sizeChanged) {
                            ApplicationsState.this.mMainHandler.sendMessage(ApplicationsState.this.mMainHandler.obtainMessage(4, stats.packageName));
                        }
                    }
                    if (ApplicationsState.this.mCurComputingSizePkg == null || ApplicationsState.this.mCurComputingSizePkg.equals(stats.packageName)) {
                        ApplicationsState.this.mCurComputingSizePkg = null;
                        BackgroundHandler.this.sendEmptyMessage(4);
                    }
                }
            }
        };

        BackgroundHandler(Looper looper) {
            super(looper);
        }

        /* JADX WARNING: inconsistent code. */
        /* Code decompiled incorrectly, please refer to instructions dump. */
        public void handleMessage(android.os.Message r19) {
            /*
            r18 = this;
            r9 = 0;
            r0 = r18;
            r11 = com.android.settings.applications.ApplicationsState.this;
            r12 = r11.mEntriesMap;
            monitor-enter(r12);
            r0 = r18;
            r11 = com.android.settings.applications.ApplicationsState.this;	 Catch:{ all -> 0x003f }
            r11 = r11.mRebuildingSessions;	 Catch:{ all -> 0x003f }
            r11 = r11.size();	 Catch:{ all -> 0x003f }
            if (r11 <= 0) goto L_0x0029;
        L_0x0014:
            r10 = new java.util.ArrayList;	 Catch:{ all -> 0x003f }
            r0 = r18;
            r11 = com.android.settings.applications.ApplicationsState.this;	 Catch:{ all -> 0x003f }
            r11 = r11.mRebuildingSessions;	 Catch:{ all -> 0x003f }
            r10.<init>(r11);	 Catch:{ all -> 0x003f }
            r0 = r18;
            r11 = com.android.settings.applications.ApplicationsState.this;	 Catch:{ all -> 0x022e }
            r11 = r11.mRebuildingSessions;	 Catch:{ all -> 0x022e }
            r11.clear();	 Catch:{ all -> 0x022e }
            r9 = r10;
        L_0x0029:
            monitor-exit(r12);	 Catch:{ all -> 0x003f }
            if (r9 == 0) goto L_0x0042;
        L_0x002c:
            r3 = 0;
        L_0x002d:
            r11 = r9.size();
            if (r3 >= r11) goto L_0x0042;
        L_0x0033:
            r11 = r9.get(r3);
            r11 = (com.android.settings.applications.ApplicationsState.Session) r11;
            r11.handleRebuildList();
            r3 = r3 + 1;
            goto L_0x002d;
        L_0x003f:
            r11 = move-exception;
        L_0x0040:
            monitor-exit(r12);	 Catch:{ all -> 0x003f }
            throw r11;
        L_0x0042:
            r0 = r19;
            r11 = r0.what;
            switch(r11) {
                case 1: goto L_0x0049;
                case 2: goto L_0x004a;
                case 3: goto L_0x00c1;
                case 4: goto L_0x015d;
                default: goto L_0x0049;
            };
        L_0x0049:
            return;
        L_0x004a:
            r8 = 0;
            r0 = r18;
            r11 = com.android.settings.applications.ApplicationsState.this;
            r12 = r11.mEntriesMap;
            monitor-enter(r12);
            r3 = 0;
        L_0x0053:
            r0 = r18;
            r11 = com.android.settings.applications.ApplicationsState.this;	 Catch:{ all -> 0x00b7 }
            r11 = r11.mApplications;	 Catch:{ all -> 0x00b7 }
            r11 = r11.size();	 Catch:{ all -> 0x00b7 }
            if (r3 >= r11) goto L_0x00ac;
        L_0x005f:
            r11 = 6;
            if (r8 >= r11) goto L_0x00ac;
        L_0x0062:
            r0 = r18;
            r11 = r0.mRunning;	 Catch:{ all -> 0x00b7 }
            if (r11 != 0) goto L_0x0086;
        L_0x0068:
            r11 = 1;
            r0 = r18;
            r0.mRunning = r11;	 Catch:{ all -> 0x00b7 }
            r0 = r18;
            r11 = com.android.settings.applications.ApplicationsState.this;	 Catch:{ all -> 0x00b7 }
            r11 = r11.mMainHandler;	 Catch:{ all -> 0x00b7 }
            r13 = 6;
            r14 = 1;
            r14 = java.lang.Integer.valueOf(r14);	 Catch:{ all -> 0x00b7 }
            r5 = r11.obtainMessage(r13, r14);	 Catch:{ all -> 0x00b7 }
            r0 = r18;
            r11 = com.android.settings.applications.ApplicationsState.this;	 Catch:{ all -> 0x00b7 }
            r11 = r11.mMainHandler;	 Catch:{ all -> 0x00b7 }
            r11.sendMessage(r5);	 Catch:{ all -> 0x00b7 }
        L_0x0086:
            r0 = r18;
            r11 = com.android.settings.applications.ApplicationsState.this;	 Catch:{ all -> 0x00b7 }
            r11 = r11.mApplications;	 Catch:{ all -> 0x00b7 }
            r4 = r11.get(r3);	 Catch:{ all -> 0x00b7 }
            r4 = (android.content.pm.ApplicationInfo) r4;	 Catch:{ all -> 0x00b7 }
            r0 = r18;
            r11 = com.android.settings.applications.ApplicationsState.this;	 Catch:{ all -> 0x00b7 }
            r11 = r11.mEntriesMap;	 Catch:{ all -> 0x00b7 }
            r13 = r4.packageName;	 Catch:{ all -> 0x00b7 }
            r11 = r11.get(r13);	 Catch:{ all -> 0x00b7 }
            if (r11 != 0) goto L_0x00a9;
        L_0x00a0:
            r8 = r8 + 1;
            r0 = r18;
            r11 = com.android.settings.applications.ApplicationsState.this;	 Catch:{ all -> 0x00b7 }
            r11.getEntryLocked(r4);	 Catch:{ all -> 0x00b7 }
        L_0x00a9:
            r3 = r3 + 1;
            goto L_0x0053;
        L_0x00ac:
            monitor-exit(r12);	 Catch:{ all -> 0x00b7 }
            r11 = 6;
            if (r8 < r11) goto L_0x00ba;
        L_0x00b0:
            r11 = 2;
            r0 = r18;
            r0.sendEmptyMessage(r11);
            goto L_0x0049;
        L_0x00b7:
            r11 = move-exception;
            monitor-exit(r12);	 Catch:{ all -> 0x00b7 }
            throw r11;
        L_0x00ba:
            r11 = 3;
            r0 = r18;
            r0.sendEmptyMessage(r11);
            goto L_0x0049;
        L_0x00c1:
            r8 = 0;
            r0 = r18;
            r11 = com.android.settings.applications.ApplicationsState.this;
            r12 = r11.mEntriesMap;
            monitor-enter(r12);
            r3 = 0;
        L_0x00ca:
            r0 = r18;
            r11 = com.android.settings.applications.ApplicationsState.this;	 Catch:{ all -> 0x012d }
            r11 = r11.mAppEntries;	 Catch:{ all -> 0x012d }
            r11 = r11.size();	 Catch:{ all -> 0x012d }
            if (r3 >= r11) goto L_0x0130;
        L_0x00d6:
            r11 = 2;
            if (r8 >= r11) goto L_0x0130;
        L_0x00d9:
            r0 = r18;
            r11 = com.android.settings.applications.ApplicationsState.this;	 Catch:{ all -> 0x012d }
            r11 = r11.mAppEntries;	 Catch:{ all -> 0x012d }
            r2 = r11.get(r3);	 Catch:{ all -> 0x012d }
            r2 = (com.android.settings.applications.ApplicationsState.AppEntry) r2;	 Catch:{ all -> 0x012d }
            r11 = r2.icon;	 Catch:{ all -> 0x012d }
            if (r11 == 0) goto L_0x00ed;
        L_0x00e9:
            r11 = r2.mounted;	 Catch:{ all -> 0x012d }
            if (r11 != 0) goto L_0x0127;
        L_0x00ed:
            monitor-enter(r2);	 Catch:{ all -> 0x012d }
            r0 = r18;
            r11 = com.android.settings.applications.ApplicationsState.this;	 Catch:{ all -> 0x012a }
            r11 = r11.mContext;	 Catch:{ all -> 0x012a }
            r0 = r18;
            r13 = com.android.settings.applications.ApplicationsState.this;	 Catch:{ all -> 0x012a }
            r13 = r13.mPm;	 Catch:{ all -> 0x012a }
            r11 = r2.ensureIconLocked(r11, r13);	 Catch:{ all -> 0x012a }
            if (r11 == 0) goto L_0x0126;
        L_0x0100:
            r0 = r18;
            r11 = r0.mRunning;	 Catch:{ all -> 0x012a }
            if (r11 != 0) goto L_0x0124;
        L_0x0106:
            r11 = 1;
            r0 = r18;
            r0.mRunning = r11;	 Catch:{ all -> 0x012a }
            r0 = r18;
            r11 = com.android.settings.applications.ApplicationsState.this;	 Catch:{ all -> 0x012a }
            r11 = r11.mMainHandler;	 Catch:{ all -> 0x012a }
            r13 = 6;
            r14 = 1;
            r14 = java.lang.Integer.valueOf(r14);	 Catch:{ all -> 0x012a }
            r5 = r11.obtainMessage(r13, r14);	 Catch:{ all -> 0x012a }
            r0 = r18;
            r11 = com.android.settings.applications.ApplicationsState.this;	 Catch:{ all -> 0x012a }
            r11 = r11.mMainHandler;	 Catch:{ all -> 0x012a }
            r11.sendMessage(r5);	 Catch:{ all -> 0x012a }
        L_0x0124:
            r8 = r8 + 1;
        L_0x0126:
            monitor-exit(r2);	 Catch:{ all -> 0x012a }
        L_0x0127:
            r3 = r3 + 1;
            goto L_0x00ca;
        L_0x012a:
            r11 = move-exception;
            monitor-exit(r2);	 Catch:{ all -> 0x012a }
            throw r11;	 Catch:{ all -> 0x012d }
        L_0x012d:
            r11 = move-exception;
            monitor-exit(r12);	 Catch:{ all -> 0x012d }
            throw r11;
        L_0x0130:
            monitor-exit(r12);	 Catch:{ all -> 0x012d }
            if (r8 <= 0) goto L_0x014a;
        L_0x0133:
            r0 = r18;
            r11 = com.android.settings.applications.ApplicationsState.this;
            r11 = r11.mMainHandler;
            r12 = 3;
            r11 = r11.hasMessages(r12);
            if (r11 != 0) goto L_0x014a;
        L_0x0140:
            r0 = r18;
            r11 = com.android.settings.applications.ApplicationsState.this;
            r11 = r11.mMainHandler;
            r12 = 3;
            r11.sendEmptyMessage(r12);
        L_0x014a:
            r11 = 2;
            if (r8 < r11) goto L_0x0155;
        L_0x014d:
            r11 = 3;
            r0 = r18;
            r0.sendEmptyMessage(r11);
            goto L_0x0049;
        L_0x0155:
            r11 = 4;
            r0 = r18;
            r0.sendEmptyMessage(r11);
            goto L_0x0049;
        L_0x015d:
            r0 = r18;
            r11 = com.android.settings.applications.ApplicationsState.this;
            r12 = r11.mEntriesMap;
            monitor-enter(r12);
            r0 = r18;
            r11 = com.android.settings.applications.ApplicationsState.this;	 Catch:{ all -> 0x016f }
            r11 = r11.mCurComputingSizePkg;	 Catch:{ all -> 0x016f }
            if (r11 == 0) goto L_0x0172;
        L_0x016c:
            monitor-exit(r12);	 Catch:{ all -> 0x016f }
            goto L_0x0049;
        L_0x016f:
            r11 = move-exception;
            monitor-exit(r12);	 Catch:{ all -> 0x016f }
            throw r11;
        L_0x0172:
            r6 = android.os.SystemClock.uptimeMillis();	 Catch:{ all -> 0x016f }
            r3 = 0;
        L_0x0177:
            r0 = r18;
            r11 = com.android.settings.applications.ApplicationsState.this;	 Catch:{ all -> 0x016f }
            r11 = r11.mAppEntries;	 Catch:{ all -> 0x016f }
            r11 = r11.size();	 Catch:{ all -> 0x016f }
            if (r3 >= r11) goto L_0x01f6;
        L_0x0183:
            r0 = r18;
            r11 = com.android.settings.applications.ApplicationsState.this;	 Catch:{ all -> 0x016f }
            r11 = r11.mAppEntries;	 Catch:{ all -> 0x016f }
            r2 = r11.get(r3);	 Catch:{ all -> 0x016f }
            r2 = (com.android.settings.applications.ApplicationsState.AppEntry) r2;	 Catch:{ all -> 0x016f }
            r14 = r2.size;	 Catch:{ all -> 0x016f }
            r16 = -1;
            r11 = (r14 > r16 ? 1 : (r14 == r16 ? 0 : -1));
            if (r11 == 0) goto L_0x019b;
        L_0x0197:
            r11 = r2.sizeStale;	 Catch:{ all -> 0x016f }
            if (r11 == 0) goto L_0x01f3;
        L_0x019b:
            r14 = r2.sizeLoadStart;	 Catch:{ all -> 0x016f }
            r16 = 0;
            r11 = (r14 > r16 ? 1 : (r14 == r16 ? 0 : -1));
            if (r11 == 0) goto L_0x01ad;
        L_0x01a3:
            r14 = r2.sizeLoadStart;	 Catch:{ all -> 0x016f }
            r16 = 20000; // 0x4e20 float:2.8026E-41 double:9.8813E-320;
            r16 = r6 - r16;
            r11 = (r14 > r16 ? 1 : (r14 == r16 ? 0 : -1));
            if (r11 >= 0) goto L_0x01f0;
        L_0x01ad:
            r0 = r18;
            r11 = r0.mRunning;	 Catch:{ all -> 0x016f }
            if (r11 != 0) goto L_0x01d1;
        L_0x01b3:
            r11 = 1;
            r0 = r18;
            r0.mRunning = r11;	 Catch:{ all -> 0x016f }
            r0 = r18;
            r11 = com.android.settings.applications.ApplicationsState.this;	 Catch:{ all -> 0x016f }
            r11 = r11.mMainHandler;	 Catch:{ all -> 0x016f }
            r13 = 6;
            r14 = 1;
            r14 = java.lang.Integer.valueOf(r14);	 Catch:{ all -> 0x016f }
            r5 = r11.obtainMessage(r13, r14);	 Catch:{ all -> 0x016f }
            r0 = r18;
            r11 = com.android.settings.applications.ApplicationsState.this;	 Catch:{ all -> 0x016f }
            r11 = r11.mMainHandler;	 Catch:{ all -> 0x016f }
            r11.sendMessage(r5);	 Catch:{ all -> 0x016f }
        L_0x01d1:
            r2.sizeLoadStart = r6;	 Catch:{ all -> 0x016f }
            r0 = r18;
            r11 = com.android.settings.applications.ApplicationsState.this;	 Catch:{ all -> 0x016f }
            r13 = r2.info;	 Catch:{ all -> 0x016f }
            r13 = r13.packageName;	 Catch:{ all -> 0x016f }
            r11.mCurComputingSizePkg = r13;	 Catch:{ all -> 0x016f }
            r0 = r18;
            r11 = com.android.settings.applications.ApplicationsState.this;	 Catch:{ all -> 0x016f }
            r11 = r11.mPm;	 Catch:{ all -> 0x016f }
            r0 = r18;
            r13 = com.android.settings.applications.ApplicationsState.this;	 Catch:{ all -> 0x016f }
            r13 = r13.mCurComputingSizePkg;	 Catch:{ all -> 0x016f }
            r0 = r18;
            r14 = r0.mStatsObserver;	 Catch:{ all -> 0x016f }
            r11.getPackageSizeInfo(r13, r14);	 Catch:{ all -> 0x016f }
        L_0x01f0:
            monitor-exit(r12);	 Catch:{ all -> 0x016f }
            goto L_0x0049;
        L_0x01f3:
            r3 = r3 + 1;
            goto L_0x0177;
        L_0x01f6:
            r0 = r18;
            r11 = com.android.settings.applications.ApplicationsState.this;	 Catch:{ all -> 0x016f }
            r11 = r11.mMainHandler;	 Catch:{ all -> 0x016f }
            r13 = 5;
            r11 = r11.hasMessages(r13);	 Catch:{ all -> 0x016f }
            if (r11 != 0) goto L_0x022b;
        L_0x0203:
            r0 = r18;
            r11 = com.android.settings.applications.ApplicationsState.this;	 Catch:{ all -> 0x016f }
            r11 = r11.mMainHandler;	 Catch:{ all -> 0x016f }
            r13 = 5;
            r11.sendEmptyMessage(r13);	 Catch:{ all -> 0x016f }
            r11 = 0;
            r0 = r18;
            r0.mRunning = r11;	 Catch:{ all -> 0x016f }
            r0 = r18;
            r11 = com.android.settings.applications.ApplicationsState.this;	 Catch:{ all -> 0x016f }
            r11 = r11.mMainHandler;	 Catch:{ all -> 0x016f }
            r13 = 6;
            r14 = 0;
            r14 = java.lang.Integer.valueOf(r14);	 Catch:{ all -> 0x016f }
            r5 = r11.obtainMessage(r13, r14);	 Catch:{ all -> 0x016f }
            r0 = r18;
            r11 = com.android.settings.applications.ApplicationsState.this;	 Catch:{ all -> 0x016f }
            r11 = r11.mMainHandler;	 Catch:{ all -> 0x016f }
            r11.sendMessage(r5);	 Catch:{ all -> 0x016f }
        L_0x022b:
            monitor-exit(r12);	 Catch:{ all -> 0x016f }
            goto L_0x0049;
        L_0x022e:
            r11 = move-exception;
            r9 = r10;
            goto L_0x0040;
            */
            throw new UnsupportedOperationException("Method not decompiled: com.android.settings.applications.ApplicationsState.BackgroundHandler.handleMessage(android.os.Message):void");
        }
    }

    public interface Callbacks {
        void onAllSizesComputed();

        void onPackageIconChanged();

        void onPackageListChanged();

        void onPackageSizeChanged(String str);

        void onRebuildComplete(ArrayList<AppEntry> arrayList);

        void onRunningStateChanged(boolean z);
    }

    class MainHandler extends Handler {
        MainHandler() {
        }

        public void handleMessage(Message msg) {
            ApplicationsState.this.rebuildActiveSessions();
            int i;
            switch (msg.what) {
                case 1:
                    Session s = msg.obj;
                    if (ApplicationsState.this.mActiveSessions.contains(s)) {
                        s.mCallbacks.onRebuildComplete(s.mLastAppList);
                        return;
                    }
                    return;
                case 2:
                    for (i = 0; i < ApplicationsState.this.mActiveSessions.size(); i++) {
                        ((Session) ApplicationsState.this.mActiveSessions.get(i)).mCallbacks.onPackageListChanged();
                    }
                    return;
                case 3:
                    for (i = 0; i < ApplicationsState.this.mActiveSessions.size(); i++) {
                        ((Session) ApplicationsState.this.mActiveSessions.get(i)).mCallbacks.onPackageIconChanged();
                    }
                    return;
                case 4:
                    for (i = 0; i < ApplicationsState.this.mActiveSessions.size(); i++) {
                        ((Session) ApplicationsState.this.mActiveSessions.get(i)).mCallbacks.onPackageSizeChanged((String) msg.obj);
                    }
                    return;
                case 5:
                    for (i = 0; i < ApplicationsState.this.mActiveSessions.size(); i++) {
                        ((Session) ApplicationsState.this.mActiveSessions.get(i)).mCallbacks.onAllSizesComputed();
                    }
                    return;
                case 6:
                    for (i = 0; i < ApplicationsState.this.mActiveSessions.size(); i++) {
                        ((Session) ApplicationsState.this.mActiveSessions.get(i)).mCallbacks.onRunningStateChanged(msg.arg1 != 0);
                    }
                    return;
                default:
                    return;
            }
        }
    }

    private class PackageIntentReceiver extends BroadcastReceiver {
        private PackageIntentReceiver() {
        }

        void registerReceiver() {
            IntentFilter filter = new IntentFilter("android.intent.action.PACKAGE_ADDED");
            filter.addAction("android.intent.action.PACKAGE_REMOVED");
            filter.addAction("android.intent.action.PACKAGE_CHANGED");
            filter.addDataScheme("package");
            ApplicationsState.this.mContext.registerReceiver(this, filter);
            IntentFilter sdFilter = new IntentFilter();
            sdFilter.addAction("android.intent.action.EXTERNAL_APPLICATIONS_AVAILABLE");
            sdFilter.addAction("android.intent.action.EXTERNAL_APPLICATIONS_UNAVAILABLE");
            ApplicationsState.this.mContext.registerReceiver(this, sdFilter);
        }

        void unregisterReceiver() {
            ApplicationsState.this.mContext.unregisterReceiver(this);
        }

        public void onReceive(Context context, Intent intent) {
            String actionStr = intent.getAction();
            if ("android.intent.action.PACKAGE_ADDED".equals(actionStr)) {
                ApplicationsState.this.addPackage(intent.getData().getEncodedSchemeSpecificPart());
            } else if ("android.intent.action.PACKAGE_REMOVED".equals(actionStr)) {
                ApplicationsState.this.removePackage(intent.getData().getEncodedSchemeSpecificPart());
            } else if ("android.intent.action.PACKAGE_CHANGED".equals(actionStr)) {
                ApplicationsState.this.invalidatePackage(intent.getData().getEncodedSchemeSpecificPart());
            } else if ("android.intent.action.EXTERNAL_APPLICATIONS_AVAILABLE".equals(actionStr) || "android.intent.action.EXTERNAL_APPLICATIONS_UNAVAILABLE".equals(actionStr)) {
                String[] pkgList = intent.getStringArrayExtra("android.intent.extra.changed_package_list");
                if (pkgList != null && pkgList.length != 0 && "android.intent.action.EXTERNAL_APPLICATIONS_AVAILABLE".equals(actionStr)) {
                    for (String pkgName : pkgList) {
                        ApplicationsState.this.invalidatePackage(pkgName);
                    }
                }
            }
        }
    }

    public class Session {
        final Callbacks mCallbacks;
        ArrayList<AppEntry> mLastAppList;
        boolean mRebuildAsync;
        Comparator<AppEntry> mRebuildComparator;
        AppFilter mRebuildFilter;
        boolean mRebuildRequested;
        ArrayList<AppEntry> mRebuildResult;
        final Object mRebuildSync = new Object();
        boolean mResumed;

        Session(Callbacks callbacks) {
            this.mCallbacks = callbacks;
        }

        public void resume() {
            synchronized (ApplicationsState.this.mEntriesMap) {
                if (!this.mResumed) {
                    this.mResumed = true;
                    ApplicationsState.this.mSessionsChanged = true;
                    ApplicationsState.this.doResumeIfNeededLocked();
                }
            }
        }

        public void pause() {
            synchronized (ApplicationsState.this.mEntriesMap) {
                if (this.mResumed) {
                    this.mResumed = false;
                    ApplicationsState.this.mSessionsChanged = true;
                    ApplicationsState.this.mBackgroundHandler.removeMessages(1, this);
                    ApplicationsState.this.doPauseIfNeededLocked();
                }
            }
        }

        ArrayList<AppEntry> rebuild(AppFilter filter, Comparator<AppEntry> comparator) {
            ArrayList<AppEntry> arrayList;
            synchronized (this.mRebuildSync) {
                synchronized (ApplicationsState.this.mEntriesMap) {
                    ApplicationsState.this.mRebuildingSessions.add(this);
                    this.mRebuildRequested = true;
                    this.mRebuildAsync = false;
                    this.mRebuildFilter = filter;
                    this.mRebuildComparator = comparator;
                    this.mRebuildResult = null;
                    if (!ApplicationsState.this.mBackgroundHandler.hasMessages(1)) {
                        ApplicationsState.this.mBackgroundHandler.sendMessage(ApplicationsState.this.mBackgroundHandler.obtainMessage(1));
                    }
                }
                long waitend = SystemClock.uptimeMillis() + 250;
                while (this.mRebuildResult == null) {
                    long now = SystemClock.uptimeMillis();
                    if (now >= waitend) {
                        break;
                    }
                    try {
                        this.mRebuildSync.wait(waitend - now);
                    } catch (InterruptedException e) {
                    }
                }
                this.mRebuildAsync = true;
                arrayList = this.mRebuildResult;
            }
            return arrayList;
        }

        /* JADX WARNING: inconsistent code. */
        /* Code decompiled incorrectly, please refer to instructions dump. */
        void handleRebuildList() {
            /*
            r11 = this;
            r9 = r11.mRebuildSync;
            monitor-enter(r9);
            r8 = r11.mRebuildRequested;	 Catch:{ all -> 0x0062 }
            if (r8 != 0) goto L_0x0009;
        L_0x0007:
            monitor-exit(r9);	 Catch:{ all -> 0x0062 }
        L_0x0008:
            return;
        L_0x0009:
            r3 = r11.mRebuildFilter;	 Catch:{ all -> 0x0062 }
            r1 = r11.mRebuildComparator;	 Catch:{ all -> 0x0062 }
            r8 = 0;
            r11.mRebuildRequested = r8;	 Catch:{ all -> 0x0062 }
            r8 = 0;
            r11.mRebuildFilter = r8;	 Catch:{ all -> 0x0062 }
            r8 = 0;
            r11.mRebuildComparator = r8;	 Catch:{ all -> 0x0062 }
            monitor-exit(r9);	 Catch:{ all -> 0x0062 }
            r8 = -2;
            android.os.Process.setThreadPriority(r8);
            if (r3 == 0) goto L_0x0020;
        L_0x001d:
            r3.init();
        L_0x0020:
            r8 = com.android.settings.applications.ApplicationsState.this;
            r9 = r8.mEntriesMap;
            monitor-enter(r9);
            r0 = new java.util.ArrayList;	 Catch:{ all -> 0x0065 }
            r8 = com.android.settings.applications.ApplicationsState.this;	 Catch:{ all -> 0x0065 }
            r8 = r8.mApplications;	 Catch:{ all -> 0x0065 }
            r0.<init>(r8);	 Catch:{ all -> 0x0065 }
            monitor-exit(r9);	 Catch:{ all -> 0x0065 }
            r4 = new java.util.ArrayList;
            r4.<init>();
            r5 = 0;
        L_0x0035:
            r8 = r0.size();
            if (r5 >= r8) goto L_0x006b;
        L_0x003b:
            r6 = r0.get(r5);
            r6 = (android.content.pm.ApplicationInfo) r6;
            if (r3 == 0) goto L_0x0049;
        L_0x0043:
            r8 = r3.filterApp(r6);
            if (r8 == 0) goto L_0x005f;
        L_0x0049:
            r8 = com.android.settings.applications.ApplicationsState.this;
            r9 = r8.mEntriesMap;
            monitor-enter(r9);
            r8 = com.android.settings.applications.ApplicationsState.this;	 Catch:{ all -> 0x0068 }
            r2 = r8.getEntryLocked(r6);	 Catch:{ all -> 0x0068 }
            r8 = com.android.settings.applications.ApplicationsState.this;	 Catch:{ all -> 0x0068 }
            r8 = r8.mContext;	 Catch:{ all -> 0x0068 }
            r2.ensureLabel(r8);	 Catch:{ all -> 0x0068 }
            r4.add(r2);	 Catch:{ all -> 0x0068 }
            monitor-exit(r9);	 Catch:{ all -> 0x0068 }
        L_0x005f:
            r5 = r5 + 1;
            goto L_0x0035;
        L_0x0062:
            r8 = move-exception;
            monitor-exit(r9);	 Catch:{ all -> 0x0062 }
            throw r8;
        L_0x0065:
            r8 = move-exception;
            monitor-exit(r9);	 Catch:{ all -> 0x0065 }
            throw r8;
        L_0x0068:
            r8 = move-exception;
            monitor-exit(r9);	 Catch:{ all -> 0x0068 }
            throw r8;
        L_0x006b:
            java.util.Collections.sort(r4, r1);
            r9 = r11.mRebuildSync;
            monitor-enter(r9);
            r8 = r11.mRebuildRequested;	 Catch:{ all -> 0x00a5 }
            if (r8 != 0) goto L_0x0082;
        L_0x0075:
            r11.mLastAppList = r4;	 Catch:{ all -> 0x00a5 }
            r8 = r11.mRebuildAsync;	 Catch:{ all -> 0x00a5 }
            if (r8 != 0) goto L_0x0089;
        L_0x007b:
            r11.mRebuildResult = r4;	 Catch:{ all -> 0x00a5 }
            r8 = r11.mRebuildSync;	 Catch:{ all -> 0x00a5 }
            r8.notifyAll();	 Catch:{ all -> 0x00a5 }
        L_0x0082:
            monitor-exit(r9);	 Catch:{ all -> 0x00a5 }
            r8 = 10;
            android.os.Process.setThreadPriority(r8);
            goto L_0x0008;
        L_0x0089:
            r8 = com.android.settings.applications.ApplicationsState.this;	 Catch:{ all -> 0x00a5 }
            r8 = r8.mMainHandler;	 Catch:{ all -> 0x00a5 }
            r10 = 1;
            r8 = r8.hasMessages(r10, r11);	 Catch:{ all -> 0x00a5 }
            if (r8 != 0) goto L_0x0082;
        L_0x0094:
            r8 = com.android.settings.applications.ApplicationsState.this;	 Catch:{ all -> 0x00a5 }
            r8 = r8.mMainHandler;	 Catch:{ all -> 0x00a5 }
            r10 = 1;
            r7 = r8.obtainMessage(r10, r11);	 Catch:{ all -> 0x00a5 }
            r8 = com.android.settings.applications.ApplicationsState.this;	 Catch:{ all -> 0x00a5 }
            r8 = r8.mMainHandler;	 Catch:{ all -> 0x00a5 }
            r8.sendMessage(r7);	 Catch:{ all -> 0x00a5 }
            goto L_0x0082;
        L_0x00a5:
            r8 = move-exception;
            monitor-exit(r9);	 Catch:{ all -> 0x00a5 }
            throw r8;
            */
            throw new UnsupportedOperationException("Method not decompiled: com.android.settings.applications.ApplicationsState.Session.handleRebuildList():void");
        }

        public void release() {
            pause();
            synchronized (ApplicationsState.this.mEntriesMap) {
                ApplicationsState.this.mSessions.remove(this);
            }
        }
    }

    public static String normalize(String str) {
        return REMOVE_DIACRITICALS_PATTERN.matcher(Normalizer.normalize(str, Form.NFD)).replaceAll("").toLowerCase();
    }

    void rebuildActiveSessions() {
        synchronized (this.mEntriesMap) {
            if (this.mSessionsChanged) {
                this.mActiveSessions.clear();
                for (int i = 0; i < this.mSessions.size(); i++) {
                    Session s = (Session) this.mSessions.get(i);
                    if (s.mResumed) {
                        this.mActiveSessions.add(s);
                    }
                }
                return;
            }
        }
    }

    static ApplicationsState getInstance(Application app) {
        ApplicationsState applicationsState;
        synchronized (sLock) {
            if (sInstance == null) {
                sInstance = new ApplicationsState(app);
            }
            applicationsState = sInstance;
        }
        return applicationsState;
    }

    private ApplicationsState(Application app) {
        this.mContext = app;
        this.mPm = this.mContext.getPackageManager();
        this.mThread = new HandlerThread("ApplicationsState.Loader", 10);
        this.mThread.start();
        this.mBackgroundHandler = new BackgroundHandler(this.mThread.getLooper());
        if (UserHandle.myUserId() == 0) {
            this.mRetrieveFlags = 41472;
        } else {
            this.mRetrieveFlags = 33280;
        }
        synchronized (this.mEntriesMap) {
            try {
                this.mEntriesMap.wait(1);
            } catch (InterruptedException e) {
            }
        }
    }

    public Session newSession(Callbacks callbacks) {
        Session s = new Session(callbacks);
        synchronized (this.mEntriesMap) {
            this.mSessions.add(s);
        }
        return s;
    }

    void doResumeIfNeededLocked() {
        if (!this.mResumed) {
            int i;
            this.mResumed = true;
            if (this.mPackageIntentReceiver == null) {
                this.mPackageIntentReceiver = new PackageIntentReceiver();
                this.mPackageIntentReceiver.registerReceiver();
            }
            this.mApplications = this.mPm.getInstalledApplications(this.mRetrieveFlags);
            if (this.mApplications == null) {
                this.mApplications = new ArrayList();
            }
            if (this.mInterestingConfigChanges.applyNewConfig(this.mContext.getResources())) {
                this.mEntriesMap.clear();
                this.mAppEntries.clear();
            } else {
                for (i = 0; i < this.mAppEntries.size(); i++) {
                    ((AppEntry) this.mAppEntries.get(i)).sizeStale = true;
                }
            }
            this.mHaveDisabledApps = false;
            i = 0;
            while (i < this.mApplications.size()) {
                ApplicationInfo info = (ApplicationInfo) this.mApplications.get(i);
                if (!info.enabled) {
                    if (info.enabledSetting != 3) {
                        this.mApplications.remove(i);
                        i--;
                        i++;
                    } else {
                        this.mHaveDisabledApps = true;
                    }
                }
                AppEntry entry = (AppEntry) this.mEntriesMap.get(info.packageName);
                if (entry != null) {
                    entry.info = info;
                }
                i++;
            }
            this.mCurComputingSizePkg = null;
            if (!this.mBackgroundHandler.hasMessages(2)) {
                this.mBackgroundHandler.sendEmptyMessage(2);
            }
        }
    }

    public boolean haveDisabledApps() {
        return this.mHaveDisabledApps;
    }

    void doPauseIfNeededLocked() {
        if (this.mResumed) {
            int i = 0;
            while (i < this.mSessions.size()) {
                if (!((Session) this.mSessions.get(i)).mResumed) {
                    i++;
                } else {
                    return;
                }
            }
            this.mResumed = false;
            if (this.mPackageIntentReceiver != null) {
                this.mPackageIntentReceiver.unregisterReceiver();
                this.mPackageIntentReceiver = null;
            }
        }
    }

    AppEntry getEntry(String packageName) {
        AppEntry entry;
        synchronized (this.mEntriesMap) {
            entry = (AppEntry) this.mEntriesMap.get(packageName);
            if (entry == null) {
                for (int i = 0; i < this.mApplications.size(); i++) {
                    ApplicationInfo info = (ApplicationInfo) this.mApplications.get(i);
                    if (packageName.equals(info.packageName)) {
                        entry = getEntryLocked(info);
                        break;
                    }
                }
            }
        }
        return entry;
    }

    void ensureIcon(AppEntry entry) {
        if (entry.icon == null) {
            synchronized (entry) {
                entry.ensureIconLocked(this.mContext, this.mPm);
            }
        }
    }

    void requestSize(String packageName) {
        synchronized (this.mEntriesMap) {
            if (((AppEntry) this.mEntriesMap.get(packageName)) != null) {
                this.mPm.getPackageSizeInfo(packageName, this.mBackgroundHandler.mStatsObserver);
            }
        }
    }

    long sumCacheSizes() {
        long sum = 0;
        synchronized (this.mEntriesMap) {
            for (int i = this.mAppEntries.size() - 1; i >= 0; i--) {
                sum += ((AppEntry) this.mAppEntries.get(i)).cacheSize;
            }
        }
        return sum;
    }

    int indexOfApplicationInfoLocked(String pkgName) {
        for (int i = this.mApplications.size() - 1; i >= 0; i--) {
            if (((ApplicationInfo) this.mApplications.get(i)).packageName.equals(pkgName)) {
                return i;
            }
        }
        return -1;
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    void addPackage(java.lang.String r5) {
        /*
        r4 = this;
        r2 = r4.mEntriesMap;	 Catch:{ NameNotFoundException -> 0x0014 }
        monitor-enter(r2);	 Catch:{ NameNotFoundException -> 0x0014 }
        r1 = r4.mResumed;	 Catch:{ all -> 0x0011 }
        if (r1 != 0) goto L_0x0009;
    L_0x0007:
        monitor-exit(r2);	 Catch:{ all -> 0x0011 }
    L_0x0008:
        return;
    L_0x0009:
        r1 = r4.indexOfApplicationInfoLocked(r5);	 Catch:{ all -> 0x0011 }
        if (r1 < 0) goto L_0x0016;
    L_0x000f:
        monitor-exit(r2);	 Catch:{ all -> 0x0011 }
        goto L_0x0008;
    L_0x0011:
        r1 = move-exception;
        monitor-exit(r2);	 Catch:{ all -> 0x0011 }
        throw r1;	 Catch:{ NameNotFoundException -> 0x0014 }
    L_0x0014:
        r1 = move-exception;
        goto L_0x0008;
    L_0x0016:
        r1 = r4.mPm;	 Catch:{ all -> 0x0011 }
        r3 = r4.mRetrieveFlags;	 Catch:{ all -> 0x0011 }
        r0 = r1.getApplicationInfo(r5, r3);	 Catch:{ all -> 0x0011 }
        r1 = r0.enabled;	 Catch:{ all -> 0x0011 }
        if (r1 != 0) goto L_0x002c;
    L_0x0022:
        r1 = r0.enabledSetting;	 Catch:{ all -> 0x0011 }
        r3 = 3;
        if (r1 == r3) goto L_0x0029;
    L_0x0027:
        monitor-exit(r2);	 Catch:{ all -> 0x0011 }
        goto L_0x0008;
    L_0x0029:
        r1 = 1;
        r4.mHaveDisabledApps = r1;	 Catch:{ all -> 0x0011 }
    L_0x002c:
        r1 = r4.mApplications;	 Catch:{ all -> 0x0011 }
        r1.add(r0);	 Catch:{ all -> 0x0011 }
        r1 = r4.mBackgroundHandler;	 Catch:{ all -> 0x0011 }
        r3 = 2;
        r1 = r1.hasMessages(r3);	 Catch:{ all -> 0x0011 }
        if (r1 != 0) goto L_0x0040;
    L_0x003a:
        r1 = r4.mBackgroundHandler;	 Catch:{ all -> 0x0011 }
        r3 = 2;
        r1.sendEmptyMessage(r3);	 Catch:{ all -> 0x0011 }
    L_0x0040:
        r1 = r4.mMainHandler;	 Catch:{ all -> 0x0011 }
        r3 = 2;
        r1 = r1.hasMessages(r3);	 Catch:{ all -> 0x0011 }
        if (r1 != 0) goto L_0x004f;
    L_0x0049:
        r1 = r4.mMainHandler;	 Catch:{ all -> 0x0011 }
        r3 = 2;
        r1.sendEmptyMessage(r3);	 Catch:{ all -> 0x0011 }
    L_0x004f:
        monitor-exit(r2);	 Catch:{ all -> 0x0011 }
        goto L_0x0008;
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.settings.applications.ApplicationsState.addPackage(java.lang.String):void");
    }

    void removePackage(String pkgName) {
        synchronized (this.mEntriesMap) {
            int idx = indexOfApplicationInfoLocked(pkgName);
            if (idx >= 0) {
                AppEntry entry = (AppEntry) this.mEntriesMap.get(pkgName);
                if (entry != null) {
                    this.mEntriesMap.remove(pkgName);
                    this.mAppEntries.remove(entry);
                }
                ApplicationInfo info = (ApplicationInfo) this.mApplications.get(idx);
                this.mApplications.remove(idx);
                if (!info.enabled) {
                    this.mHaveDisabledApps = false;
                    for (int i = 0; i < this.mApplications.size(); i++) {
                        if (!((ApplicationInfo) this.mApplications.get(i)).enabled) {
                            this.mHaveDisabledApps = true;
                            break;
                        }
                    }
                }
                if (!this.mMainHandler.hasMessages(2)) {
                    this.mMainHandler.sendEmptyMessage(2);
                }
            }
        }
    }

    void invalidatePackage(String pkgName) {
        removePackage(pkgName);
        addPackage(pkgName);
    }

    AppEntry getEntryLocked(ApplicationInfo info) {
        AppEntry entry = (AppEntry) this.mEntriesMap.get(info.packageName);
        if (entry == null) {
            Context context = this.mContext;
            long j = this.mCurId;
            this.mCurId = 1 + j;
            entry = new AppEntry(context, info, j);
            this.mEntriesMap.put(info.packageName, entry);
            this.mAppEntries.add(entry);
            return entry;
        } else if (entry.info == info) {
            return entry;
        } else {
            entry.info = info;
            return entry;
        }
    }

    private long getTotalInternalSize(PackageStats ps) {
        if (ps != null) {
            return ps.codeSize + ps.dataSize;
        }
        return -2;
    }

    private long getTotalExternalSize(PackageStats ps) {
        if (ps != null) {
            return (((ps.externalCodeSize + ps.externalDataSize) + ps.externalCacheSize) + ps.externalMediaSize) + ps.externalObbSize;
        }
        return -2;
    }

    private String getSizeStr(long size) {
        if (size >= 0) {
            return Formatter.formatFileSize(this.mContext, size);
        }
        return null;
    }
}
