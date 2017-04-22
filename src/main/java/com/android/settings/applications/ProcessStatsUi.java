package com.android.settings.applications;

import android.app.ActivityManager;
import android.app.ActivityManager.MemoryInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.os.ParcelFileDescriptor.AutoCloseInputStream;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.os.UserManager;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceGroup;
import android.preference.PreferenceScreen;
import android.util.Log;
import android.util.SparseArray;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.SubMenu;
import com.android.internal.app.IProcessStats;
import com.android.internal.app.IProcessStats.Stub;
import com.android.internal.app.ProcessMap;
import com.android.internal.app.ProcessStats;
import com.android.internal.app.ProcessStats.PackageState;
import com.android.internal.app.ProcessStats.ProcessDataCollection;
import com.android.internal.app.ProcessStats.ProcessState;
import com.android.internal.app.ProcessStats.ServiceState;
import com.android.internal.app.ProcessStats.TotalMemoryUseCollection;
import com.android.internal.util.MemInfoReader;
import com.android.settings.R;
import com.android.settings.SettingsActivity;
import com.android.settings.Utils;
import com.android.settings.applications.LinearColorBar.OnRegionTappedListener;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

public class ProcessStatsUi extends PreferenceFragment implements OnRegionTappedListener {
    public static final int[] BACKGROUND_AND_SYSTEM_PROC_STATES = new int[]{0, 2, 3, 4, 5, 6, 7, 8};
    public static final int[] CACHED_PROC_STATES = new int[]{11, 12, 13};
    private static final long DURATION_QUANTUM = ProcessStats.COMMIT_PERIOD;
    public static final int[] FOREGROUND_PROC_STATES = new int[]{1};
    public static final int[] RED_MEM_STATES = new int[]{3};
    public static final int[] YELLOW_MEM_STATES = new int[]{3, 2, 1};
    private static int[] sDurationLabels = new int[]{R.string.menu_duration_3h, R.string.menu_duration_6h, R.string.menu_duration_12h, R.string.menu_duration_1d};
    private static long[] sDurations = new long[]{10800000 - (DURATION_QUANTUM / 2), 21600000 - (DURATION_QUANTUM / 2), 43200000 - (DURATION_QUANTUM / 2), 86400000 - (DURATION_QUANTUM / 2)};
    static final Comparator<ProcStatsEntry> sEntryCompare = new Comparator<ProcStatsEntry>() {
        public int compare(ProcStatsEntry lhs, ProcStatsEntry rhs) {
            if (lhs.mWeight < rhs.mWeight) {
                return 1;
            }
            if (lhs.mWeight > rhs.mWeight) {
                return -1;
            }
            if (lhs.mDuration < rhs.mDuration) {
                return 1;
            }
            if (lhs.mDuration > rhs.mDuration) {
                return -1;
            }
            return 0;
        }
    };
    private static ProcessStats sStatsXfer;
    private PreferenceGroup mAppListGroup;
    private long mDuration;
    private MenuItem[] mDurationMenus = new MenuItem[4];
    private long mLastDuration;
    long mMaxWeight;
    double mMemCachedWeight;
    double mMemFreeWeight;
    double mMemKernelWeight;
    double mMemNativeWeight;
    private int mMemRegion;
    int mMemState;
    double[] mMemStateWeights = new double[14];
    private Preference mMemStatusPref;
    long[] mMemTimes = new long[4];
    double mMemTotalWeight;
    double mMemZRamWeight;
    IProcessStats mProcessStats;
    private boolean mShowSystem;
    private MenuItem mShowSystemMenu;
    ProcessStats mStats;
    private int mStatsType;
    long mTotalTime;
    private MenuItem mTypeBackgroundMenu;
    private MenuItem mTypeCachedMenu;
    private MenuItem mTypeForegroundMenu;
    UserManager mUm;
    private boolean mUseUss;
    private MenuItem mUseUssMenu;

    public void onCreate(Bundle icicle) {
        boolean z;
        int i;
        boolean z2 = false;
        super.onCreate(icicle);
        if (icicle != null) {
            this.mStats = sStatsXfer;
        }
        addPreferencesFromResource(R.xml.process_stats_summary);
        this.mProcessStats = Stub.asInterface(ServiceManager.getService("procstats"));
        this.mUm = (UserManager) getActivity().getSystemService("user");
        this.mAppListGroup = (PreferenceGroup) findPreference("app_list");
        this.mMemStatusPref = this.mAppListGroup.findPreference("mem_status");
        this.mDuration = icicle != null ? icicle.getLong("duration", sDurations[0]) : sDurations[0];
        if (icicle != null) {
            z = icicle.getBoolean("show_system");
        } else {
            z = false;
        }
        this.mShowSystem = z;
        if (icicle != null) {
            z2 = icicle.getBoolean("use_uss");
        }
        this.mUseUss = z2;
        if (icicle != null) {
            i = icicle.getInt("stats_type", 8);
        } else {
            i = 8;
        }
        this.mStatsType = i;
        if (icicle != null) {
            i = icicle.getInt("mem_region", 4);
        } else {
            i = 4;
        }
        this.mMemRegion = i;
        setHasOptionsMenu(true);
    }

    public void onResume() {
        super.onResume();
        refreshStats();
    }

    public void onPause() {
        super.onPause();
    }

    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putLong("duration", this.mDuration);
        outState.putBoolean("show_system", this.mShowSystem);
        outState.putBoolean("use_uss", this.mUseUss);
        outState.putInt("stats_type", this.mStatsType);
        outState.putInt("mem_region", this.mMemRegion);
    }

    public void onDestroy() {
        super.onDestroy();
        if (getActivity().isChangingConfigurations()) {
            sStatsXfer = this.mStats;
        }
    }

    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        Bundle args;
        if (preference instanceof LinearColorPreference) {
            args = new Bundle();
            args.putLongArray("mem_times", this.mMemTimes);
            args.putDoubleArray("mem_state_weights", this.mMemStateWeights);
            args.putDouble("mem_cached_weight", this.mMemCachedWeight);
            args.putDouble("mem_free_weight", this.mMemFreeWeight);
            args.putDouble("mem_zram_weight", this.mMemZRamWeight);
            args.putDouble("mem_kernel_weight", this.mMemKernelWeight);
            args.putDouble("mem_native_weight", this.mMemNativeWeight);
            args.putDouble("mem_total_weight", this.mMemTotalWeight);
            args.putBoolean("use_uss", this.mUseUss);
            args.putLong("total_time", this.mTotalTime);
            ((SettingsActivity) getActivity()).startPreferencePanel(ProcessStatsMemDetail.class.getName(), args, R.string.mem_details_title, null, null, 0);
            return true;
        } else if (!(preference instanceof ProcessStatsPreference)) {
            return false;
        } else {
            ProcessStatsPreference pgp = (ProcessStatsPreference) preference;
            args = new Bundle();
            args.putParcelable("entry", pgp.getEntry());
            args.putBoolean("use_uss", this.mUseUss);
            args.putLong("max_weight", this.mMaxWeight);
            args.putLong("total_time", this.mTotalTime);
            ((SettingsActivity) getActivity()).startPreferencePanel(ProcessStatsDetail.class.getName(), args, R.string.details_title, null, null, 0);
            return super.onPreferenceTreeClick(preferenceScreen, preference);
        }
    }

    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        menu.add(0, 1, 0, R.string.menu_stats_refresh).setIcon(R.drawable.ic_menu_refresh_holo_dark).setAlphabeticShortcut('r').setShowAsAction(5);
        SubMenu subMenu = menu.addSubMenu(R.string.menu_proc_stats_duration);
        for (int i = 0; i < 4; i++) {
            this.mDurationMenus[i] = subMenu.add(0, i + 2, 0, sDurationLabels[i]).setCheckable(true);
        }
        this.mShowSystemMenu = menu.add(0, 6, 0, R.string.menu_show_system).setAlphabeticShortcut('s').setCheckable(true);
        this.mUseUssMenu = menu.add(0, 7, 0, R.string.menu_use_uss).setAlphabeticShortcut('u').setCheckable(true);
        subMenu = menu.addSubMenu(R.string.menu_proc_stats_type);
        this.mTypeBackgroundMenu = subMenu.add(0, 8, 0, R.string.menu_proc_stats_type_background).setAlphabeticShortcut('b').setCheckable(true);
        this.mTypeForegroundMenu = subMenu.add(0, 9, 0, R.string.menu_proc_stats_type_foreground).setAlphabeticShortcut('f').setCheckable(true);
        this.mTypeCachedMenu = subMenu.add(0, 10, 0, R.string.menu_proc_stats_type_cached).setCheckable(true);
        updateMenus();
    }

    void updateMenus() {
        int i;
        int closestIndex = 0;
        long closestDelta = Math.abs(sDurations[0] - this.mDuration);
        for (i = 1; i < 4; i++) {
            long delta = Math.abs(sDurations[i] - this.mDuration);
            if (delta < closestDelta) {
                closestDelta = delta;
                closestIndex = i;
            }
        }
        i = 0;
        while (i < 4) {
            if (this.mDurationMenus[i] != null) {
                this.mDurationMenus[i].setChecked(i == closestIndex);
            }
            i++;
        }
        this.mDuration = sDurations[closestIndex];
        if (this.mShowSystemMenu != null) {
            this.mShowSystemMenu.setChecked(this.mShowSystem);
            this.mShowSystemMenu.setEnabled(this.mStatsType == 8);
        }
        if (this.mUseUssMenu != null) {
            this.mUseUssMenu.setChecked(this.mUseUss);
        }
        if (this.mTypeBackgroundMenu != null) {
            this.mTypeBackgroundMenu.setChecked(this.mStatsType == 8);
        }
        if (this.mTypeForegroundMenu != null) {
            this.mTypeForegroundMenu.setChecked(this.mStatsType == 9);
        }
        if (this.mTypeCachedMenu != null) {
            this.mTypeCachedMenu.setChecked(this.mStatsType == 10);
        }
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        boolean z = false;
        int id = item.getItemId();
        switch (id) {
            case 1:
                this.mStats = null;
                refreshStats();
                return true;
            case 6:
                if (!this.mShowSystem) {
                    z = true;
                }
                this.mShowSystem = z;
                refreshStats();
                return true;
            case 7:
                if (!this.mUseUss) {
                    z = true;
                }
                this.mUseUss = z;
                refreshStats();
                return true;
            case 8:
            case 9:
            case 10:
                this.mStatsType = item.getItemId();
                refreshStats();
                return true;
            default:
                if (id >= 2 && id < 6) {
                    this.mDuration = sDurations[id - 2];
                    refreshStats();
                }
                return false;
        }
    }

    public void onRegionTapped(int region) {
        if (this.mMemRegion != region) {
            this.mMemRegion = region;
            refreshStats();
        }
    }

    private void refreshStats() {
        int[] stats;
        int statsLabel;
        CharSequence memString;
        int i;
        long memTotalTime;
        int[] memStates;
        int iu;
        int iv;
        updateMenus();
        if (this.mStats == null || this.mLastDuration != this.mDuration) {
            load();
        }
        if (this.mStatsType == 9) {
            stats = FOREGROUND_PROC_STATES;
            statsLabel = R.string.process_stats_type_foreground;
        } else if (this.mStatsType == 10) {
            stats = CACHED_PROC_STATES;
            statsLabel = R.string.process_stats_type_cached;
        } else {
            stats = this.mShowSystem ? BACKGROUND_AND_SYSTEM_PROC_STATES : ProcessStats.BACKGROUND_PROC_STATES;
            statsLabel = R.string.process_stats_type_background;
        }
        this.mAppListGroup.removeAll();
        this.mAppListGroup.setOrderingAsAdded(false);
        long elapsedTime = this.mStats.mTimePeriodEndRealtime - this.mStats.mTimePeriodStartRealtime;
        this.mMemStatusPref.setOrder(-2);
        this.mAppListGroup.addPreference(this.mMemStatusPref);
        String durationString = Utils.formatElapsedTime(getActivity(), (double) elapsedTime, false);
        CharSequence[] memStatesStr = getResources().getTextArray(R.array.ram_states);
        if (this.mMemState < 0 || this.mMemState >= memStatesStr.length) {
            memString = "?";
        } else {
            memString = memStatesStr[this.mMemState];
        }
        this.mMemStatusPref.setTitle(getActivity().getString(R.string.process_stats_total_duration, new Object[]{getActivity().getString(statsLabel), durationString}));
        this.mMemStatusPref.setSummary(getActivity().getString(R.string.process_stats_memory_status, new Object[]{memString}));
        long now = SystemClock.uptimeMillis();
        PackageManager pm = getActivity().getPackageManager();
        this.mTotalTime = ProcessStats.dumpSingleTime(null, null, this.mStats.mMemFactorDurations, this.mStats.mMemFactor, this.mStats.mStartTime, now);
        for (i = 0; i < this.mMemTimes.length; i++) {
            this.mMemTimes[i] = 0;
        }
        for (int iscreen = 0; iscreen < 8; iscreen += 4) {
            for (int imem = 0; imem < 4; imem++) {
                int state = imem + iscreen;
                long[] jArr = this.mMemTimes;
                jArr[imem] = jArr[imem] + this.mStats.mMemFactorDurations[state];
            }
        }
        Preference linearColorPreference = new LinearColorPreference(getActivity());
        linearColorPreference.setOrder(-1);
        switch (this.mMemRegion) {
            case 1:
                memTotalTime = this.mMemTimes[3];
                memStates = RED_MEM_STATES;
                break;
            case 2:
                memTotalTime = (this.mMemTimes[3] + this.mMemTimes[2]) + this.mMemTimes[1];
                memStates = YELLOW_MEM_STATES;
                break;
            default:
                memTotalTime = this.mTotalTime;
                memStates = ProcessStats.ALL_MEM_ADJ;
                break;
        }
        linearColorPreference.setColoredRegions(1);
        int[] badColors = Utils.BADNESS_COLORS;
        int badnessColor = badColors[Math.round(((float) (badColors.length - 2)) * (((float) ((this.mMemTimes[0] + ((this.mMemTimes[1] * 2) / 3)) + (this.mMemTimes[2] / 3))) / ((float) this.mTotalTime))) + 1];
        linearColorPreference.setColors(badnessColor, badnessColor, badnessColor);
        for (i = 0; i < 4; i++) {
            this.mMemTimes[i] = (long) ((((double) this.mMemTimes[i]) * ((double) elapsedTime)) / ((double) this.mTotalTime));
        }
        TotalMemoryUseCollection totalMemoryUseCollection = new TotalMemoryUseCollection(ProcessStats.ALL_SCREEN_ADJ, memStates);
        this.mStats.computeTotalMemoryUse(totalMemoryUseCollection, now);
        double freeWeight = totalMemoryUseCollection.sysMemFreeWeight + totalMemoryUseCollection.sysMemCachedWeight;
        double usedWeight = (totalMemoryUseCollection.sysMemKernelWeight + totalMemoryUseCollection.sysMemNativeWeight) + totalMemoryUseCollection.sysMemZRamWeight;
        double backgroundWeight = 0.0d;
        double persBackgroundWeight = 0.0d;
        this.mMemCachedWeight = totalMemoryUseCollection.sysMemCachedWeight;
        this.mMemFreeWeight = totalMemoryUseCollection.sysMemFreeWeight;
        this.mMemZRamWeight = totalMemoryUseCollection.sysMemZRamWeight;
        this.mMemKernelWeight = totalMemoryUseCollection.sysMemKernelWeight;
        this.mMemNativeWeight = totalMemoryUseCollection.sysMemNativeWeight;
        for (i = 0; i < 14; i++) {
            if (i == 7) {
                this.mMemStateWeights[i] = 0.0d;
            } else {
                this.mMemStateWeights[i] = totalMemoryUseCollection.processStateWeight[i];
                if (i >= 9) {
                    freeWeight += totalMemoryUseCollection.processStateWeight[i];
                } else {
                    usedWeight += totalMemoryUseCollection.processStateWeight[i];
                }
                if (i >= 2) {
                    backgroundWeight += totalMemoryUseCollection.processStateWeight[i];
                    persBackgroundWeight += totalMemoryUseCollection.processStateWeight[i];
                }
                if (i == 0) {
                    persBackgroundWeight += totalMemoryUseCollection.processStateWeight[i];
                }
            }
        }
        this.mMemTotalWeight = freeWeight + usedWeight;
        double usedRam = (1024.0d * usedWeight) / ((double) memTotalTime);
        double freeRam = (1024.0d * freeWeight) / ((double) memTotalTime);
        double totalRam = usedRam + freeRam;
        MemInfoReader memReader = new MemInfoReader();
        memReader.readMemInfo();
        double totalScale = ((double) memReader.getTotalSize()) / totalRam;
        double realUsedRam = usedRam * totalScale;
        double realFreeRam = freeRam * totalScale;
        MemoryInfo memInfo = new MemoryInfo();
        ((ActivityManager) getActivity().getSystemService("activity")).getMemoryInfo(memInfo);
        if (((double) memInfo.hiddenAppThreshold) >= realFreeRam) {
            realUsedRam = realFreeRam;
            realFreeRam = 0.0d;
        } else {
            realUsedRam += (double) memInfo.hiddenAppThreshold;
            realFreeRam -= (double) memInfo.hiddenAppThreshold;
        }
        float usedRatio = (float) (realUsedRam / (realFreeRam + realUsedRam));
        linearColorPreference.setRatios(usedRatio, 0.0f, 1.0f - usedRatio);
        this.mAppListGroup.addPreference(linearColorPreference);
        ProcessDataCollection totals = new ProcessDataCollection(ProcessStats.ALL_SCREEN_ADJ, memStates, stats);
        ArrayList<ProcStatsEntry> entries = new ArrayList();
        ProcessMap<ProcStatsEntry> entriesMap = new ProcessMap();
        int N = this.mStats.mPackages.getMap().size();
        for (int ipkg = 0; ipkg < N; ipkg++) {
            SparseArray<SparseArray<PackageState>> pkgUids = (SparseArray) this.mStats.mPackages.getMap().valueAt(ipkg);
            for (iu = 0; iu < pkgUids.size(); iu++) {
                SparseArray<PackageState> vpkgs = (SparseArray) pkgUids.valueAt(iu);
                for (iv = 0; iv < vpkgs.size(); iv++) {
                    PackageState st = (PackageState) vpkgs.valueAt(iv);
                    for (int iproc = 0; iproc < st.mProcesses.size(); iproc++) {
                        ProcStatsEntry ent;
                        ProcessState pkgProc = (ProcessState) st.mProcesses.valueAt(iproc);
                        ProcessState proc = (ProcessState) this.mStats.mProcesses.get(pkgProc.mName, pkgProc.mUid);
                        if (proc == null) {
                            Log.w("ProcessStatsUi", "No process found for pkg " + st.mPackageName + "/" + st.mUid + " proc name " + pkgProc.mName);
                        } else {
                            ent = (ProcStatsEntry) entriesMap.get(proc.mName, proc.mUid);
                            if (ent == null) {
                                ent = new ProcStatsEntry(proc, st.mPackageName, totals, this.mUseUss, this.mStatsType == 8);
                                if (ent.mDuration > 0) {
                                    entriesMap.put(proc.mName, proc.mUid, ent);
                                    entries.add(ent);
                                }
                            } else {
                                ent.addPackage(st.mPackageName);
                            }
                        }
                    }
                }
            }
        }
        if (this.mStatsType == 8) {
            N = this.mStats.mPackages.getMap().size();
            for (int ip = 0; ip < N; ip++) {
                SparseArray<SparseArray<PackageState>> uids = (SparseArray) this.mStats.mPackages.getMap().valueAt(ip);
                for (iu = 0; iu < uids.size(); iu++) {
                    vpkgs = (SparseArray) uids.valueAt(iu);
                    for (iv = 0; iv < vpkgs.size(); iv++) {
                        PackageState ps = (PackageState) vpkgs.valueAt(iv);
                        int NS = ps.mServices.size();
                        for (int is = 0; is < NS; is++) {
                            ServiceState ss = (ServiceState) ps.mServices.valueAt(is);
                            if (ss.mProcessName != null) {
                                ent = (ProcStatsEntry) entriesMap.get(ss.mProcessName, uids.keyAt(iu));
                                if (ent != null) {
                                    ent.addService(ss);
                                } else {
                                    Log.w("ProcessStatsUi", "No process " + ss.mProcessName + "/" + uids.keyAt(iu) + " for service " + ss.mName);
                                }
                            }
                        }
                    }
                }
            }
        }
        Collections.sort(entries, sEntryCompare);
        long maxWeight = 1;
        i = 0;
        N = entries != null ? entries.size() : 0;
        while (i < N) {
            ProcStatsEntry proc2 = (ProcStatsEntry) entries.get(i);
            if (maxWeight < proc2.mWeight) {
                maxWeight = proc2.mWeight;
            }
            i++;
        }
        if (this.mStatsType == 8) {
            if (!this.mShowSystem) {
                persBackgroundWeight = backgroundWeight;
            }
            this.mMaxWeight = (long) persBackgroundWeight;
            if (this.mMaxWeight < maxWeight) {
                this.mMaxWeight = maxWeight;
            }
        } else {
            this.mMaxWeight = maxWeight;
        }
        end = entries != null ? entries.size() - 1 : -1;
        while (end >= 0) {
            proc2 = (ProcStatsEntry) entries.get(end);
            double percentOfTime = (((double) proc2.mDuration) / ((double) memTotalTime)) * 100.0d;
            if ((((double) proc2.mWeight) / ((double) this.mMaxWeight)) * 100.0d < 1.0d && percentOfTime < 25.0d) {
                end--;
            }
            i = 0;
            while (i <= end) {
                proc2 = (ProcStatsEntry) entries.get(i);
                double percentOfWeight = (((double) proc2.mWeight) / ((double) this.mMaxWeight)) * 100.0d;
                percentOfTime = (((double) proc2.mDuration) / ((double) memTotalTime)) * 100.0d;
                linearColorPreference = new ProcessStatsPreference(getActivity());
                linearColorPreference.init(null, proc2);
                proc2.evaluateTargetPackage(pm, this.mStats, totals, sEntryCompare, this.mUseUss, this.mStatsType != 8);
                proc2.retrieveUiData(pm);
                linearColorPreference.setTitle(proc2.mUiLabel);
                if (proc2.mUiTargetApp != null) {
                    linearColorPreference.setIcon(proc2.mUiTargetApp.loadIcon(pm));
                }
                linearColorPreference.setOrder(i);
                linearColorPreference.setPercent(percentOfWeight, percentOfTime);
                this.mAppListGroup.addPreference(linearColorPreference);
                if (this.mStatsType != 8) {
                }
                if (this.mAppListGroup.getPreferenceCount() > 61) {
                    i++;
                } else {
                    return;
                }
            }
        }
        i = 0;
        while (i <= end) {
            proc2 = (ProcStatsEntry) entries.get(i);
            double percentOfWeight2 = (((double) proc2.mWeight) / ((double) this.mMaxWeight)) * 100.0d;
            percentOfTime = (((double) proc2.mDuration) / ((double) memTotalTime)) * 100.0d;
            linearColorPreference = new ProcessStatsPreference(getActivity());
            linearColorPreference.init(null, proc2);
            if (this.mStatsType != 8) {
            }
            proc2.evaluateTargetPackage(pm, this.mStats, totals, sEntryCompare, this.mUseUss, this.mStatsType != 8);
            proc2.retrieveUiData(pm);
            linearColorPreference.setTitle(proc2.mUiLabel);
            if (proc2.mUiTargetApp != null) {
                linearColorPreference.setIcon(proc2.mUiTargetApp.loadIcon(pm));
            }
            linearColorPreference.setOrder(i);
            linearColorPreference.setPercent(percentOfWeight2, percentOfTime);
            this.mAppListGroup.addPreference(linearColorPreference);
            if (this.mStatsType != 8) {
            }
            if (this.mAppListGroup.getPreferenceCount() > 61) {
                i++;
            } else {
                return;
            }
        }
    }

    private void load() {
        try {
            this.mLastDuration = this.mDuration;
            this.mMemState = this.mProcessStats.getCurrentMemoryState();
            ParcelFileDescriptor pfd = this.mProcessStats.getStatsOverTime(this.mDuration);
            this.mStats = new ProcessStats(false);
            InputStream is = new AutoCloseInputStream(pfd);
            this.mStats.read(is);
            try {
                is.close();
            } catch (IOException e) {
            }
            if (this.mStats.mReadError != null) {
                Log.w("ProcessStatsUi", "Failure reading process stats: " + this.mStats.mReadError);
            }
        } catch (RemoteException e2) {
            Log.e("ProcessStatsUi", "RemoteException:", e2);
        }
    }
}
