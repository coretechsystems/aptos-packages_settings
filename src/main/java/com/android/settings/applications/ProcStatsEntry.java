package com.android.settings.applications;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.Parcelable.Creator;
import android.util.ArrayMap;
import android.util.Log;
import android.util.SparseArray;
import com.android.internal.app.ProcessStats;
import com.android.internal.app.ProcessStats.PackageState;
import com.android.internal.app.ProcessStats.ProcessDataCollection;
import com.android.internal.app.ProcessStats.ProcessState;
import com.android.internal.app.ProcessStats.ServiceState;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public final class ProcStatsEntry implements Parcelable {
    public static final Creator<ProcStatsEntry> CREATOR = new Creator<ProcStatsEntry>() {
        public ProcStatsEntry createFromParcel(Parcel in) {
            return new ProcStatsEntry(in);
        }

        public ProcStatsEntry[] newArray(int size) {
            return new ProcStatsEntry[size];
        }
    };
    private static boolean DEBUG = false;
    final long mAvgPss;
    final long mAvgUss;
    String mBestTargetPackage;
    final long mDuration;
    final long mMaxPss;
    final long mMaxUss;
    final String mName;
    final String mPackage;
    final ArrayList<String> mPackages = new ArrayList();
    ArrayMap<String, ArrayList<Service>> mServices = new ArrayMap(1);
    public String mUiBaseLabel;
    public String mUiLabel;
    public String mUiPackage;
    public ApplicationInfo mUiTargetApp;
    final int mUid;
    final long mWeight;

    public static final class Service implements Parcelable {
        public static final Creator<Service> CREATOR = new Creator<Service>() {
            public Service createFromParcel(Parcel in) {
                return new Service(in);
            }

            public Service[] newArray(int size) {
                return new Service[size];
            }
        };
        final long mDuration;
        final String mName;
        final String mPackage;
        final String mProcess;

        public Service(ServiceState service) {
            this.mPackage = service.mPackage;
            this.mName = service.mName;
            this.mProcess = service.mProcessName;
            this.mDuration = ProcessStats.dumpSingleServiceTime(null, null, service, 0, -1, 0, 0);
        }

        public Service(Parcel in) {
            this.mPackage = in.readString();
            this.mName = in.readString();
            this.mProcess = in.readString();
            this.mDuration = in.readLong();
        }

        public int describeContents() {
            return 0;
        }

        public void writeToParcel(Parcel dest, int flags) {
            dest.writeString(this.mPackage);
            dest.writeString(this.mName);
            dest.writeString(this.mProcess);
            dest.writeLong(this.mDuration);
        }
    }

    public ProcStatsEntry(ProcessState proc, String packageName, ProcessDataCollection tmpTotals, boolean useUss, boolean weightWithTime) {
        ProcessStats.computeProcessData(proc, tmpTotals, 0);
        this.mPackage = proc.mPackage;
        this.mUid = proc.mUid;
        this.mName = proc.mName;
        this.mPackages.add(packageName);
        this.mDuration = tmpTotals.totalTime;
        this.mAvgPss = tmpTotals.avgPss;
        this.mMaxPss = tmpTotals.maxPss;
        this.mAvgUss = tmpTotals.avgUss;
        this.mMaxUss = tmpTotals.maxUss;
        this.mWeight = (useUss ? this.mAvgUss : this.mAvgPss) * (weightWithTime ? this.mDuration : 1);
        if (DEBUG) {
            Log.d("ProcStatsEntry", "New proc entry " + proc.mName + ": dur=" + this.mDuration + " avgpss=" + this.mAvgPss + " weight=" + this.mWeight);
        }
    }

    public ProcStatsEntry(Parcel in) {
        this.mPackage = in.readString();
        this.mUid = in.readInt();
        this.mName = in.readString();
        in.readStringList(this.mPackages);
        this.mDuration = in.readLong();
        this.mAvgPss = in.readLong();
        this.mMaxPss = in.readLong();
        this.mAvgUss = in.readLong();
        this.mMaxUss = in.readLong();
        this.mWeight = in.readLong();
        this.mBestTargetPackage = in.readString();
        int N = in.readInt();
        if (N > 0) {
            this.mServices.ensureCapacity(N);
            for (int i = 0; i < N; i++) {
                String key = in.readString();
                ArrayList<Service> value = new ArrayList();
                in.readTypedList(value, Service.CREATOR);
                this.mServices.append(key, value);
            }
        }
    }

    public void addPackage(String packageName) {
        this.mPackages.add(packageName);
    }

    public void evaluateTargetPackage(PackageManager pm, ProcessStats stats, ProcessDataCollection totals, Comparator<ProcStatsEntry> compare, boolean useUss, boolean weightWithTime) {
        this.mBestTargetPackage = null;
        if (this.mPackages.size() == 1) {
            if (DEBUG) {
                Log.d("ProcStatsEntry", "Eval pkg of " + this.mName + ": single pkg " + ((String) this.mPackages.get(0)));
            }
            this.mBestTargetPackage = (String) this.mPackages.get(0);
            return;
        }
        ArrayList<ProcStatsEntry> subProcs = new ArrayList();
        for (int ipkg = 0; ipkg < this.mPackages.size(); ipkg++) {
            SparseArray<PackageState> vpkgs = (SparseArray) stats.mPackages.get((String) this.mPackages.get(ipkg), this.mUid);
            for (int ivers = 0; ivers < vpkgs.size(); ivers++) {
                PackageState pkgState = (PackageState) vpkgs.valueAt(ivers);
                if (DEBUG) {
                    Log.d("ProcStatsEntry", "Eval pkg of " + this.mName + ", pkg " + pkgState + ":");
                }
                if (pkgState == null) {
                    Log.w("ProcStatsEntry", "No package state found for " + ((String) this.mPackages.get(ipkg)) + "/" + this.mUid + " in process " + this.mName);
                } else {
                    ProcessState pkgProc = (ProcessState) pkgState.mProcesses.get(this.mName);
                    if (pkgProc == null) {
                        Log.w("ProcStatsEntry", "No process " + this.mName + " found in package state " + ((String) this.mPackages.get(ipkg)) + "/" + this.mUid);
                    } else {
                        subProcs.add(new ProcStatsEntry(pkgProc, pkgState.mPackageName, totals, useUss, weightWithTime));
                    }
                }
            }
        }
        if (subProcs.size() > 1) {
            Collections.sort(subProcs, compare);
            if (((ProcStatsEntry) subProcs.get(0)).mWeight > ((ProcStatsEntry) subProcs.get(1)).mWeight * 3) {
                if (DEBUG) {
                    Log.d("ProcStatsEntry", "Eval pkg of " + this.mName + ": best pkg " + ((ProcStatsEntry) subProcs.get(0)).mPackage + " weight " + ((ProcStatsEntry) subProcs.get(0)).mWeight + " better than " + ((ProcStatsEntry) subProcs.get(1)).mPackage + " weight " + ((ProcStatsEntry) subProcs.get(1)).mWeight);
                }
                this.mBestTargetPackage = ((ProcStatsEntry) subProcs.get(0)).mPackage;
                return;
            }
            long maxWeight = ((ProcStatsEntry) subProcs.get(0)).mWeight;
            long bestRunTime = -1;
            for (int i = 0; i < subProcs.size(); i++) {
                if (((ProcStatsEntry) subProcs.get(i)).mWeight >= maxWeight / 2) {
                    try {
                        if (pm.getApplicationInfo(((ProcStatsEntry) subProcs.get(i)).mPackage, 0).icon != 0) {
                            ArrayList<Service> subProcServices = null;
                            int NSP = this.mServices.size();
                            for (int isp = 0; isp < NSP; isp++) {
                                ArrayList<Service> subServices = (ArrayList) this.mServices.valueAt(isp);
                                if (((Service) subServices.get(0)).mPackage.equals(((ProcStatsEntry) subProcs.get(i)).mPackage)) {
                                    subProcServices = subServices;
                                    break;
                                }
                            }
                            long thisRunTime = 0;
                            if (subProcServices != null) {
                                int iss = 0;
                                int NSS = subProcServices.size();
                                while (iss < NSS) {
                                    Service service = (Service) subProcServices.get(iss);
                                    if (service.mDuration > 0) {
                                        if (DEBUG) {
                                            Log.d("ProcStatsEntry", "Eval pkg of " + this.mName + ": pkg " + ((ProcStatsEntry) subProcs.get(i)).mPackage + " service " + service.mName + " run time is " + service.mDuration);
                                        }
                                        thisRunTime = service.mDuration;
                                    } else {
                                        iss++;
                                    }
                                }
                            }
                            if (thisRunTime > bestRunTime) {
                                if (DEBUG) {
                                    Log.d("ProcStatsEntry", "Eval pkg of " + this.mName + ": pkg " + ((ProcStatsEntry) subProcs.get(i)).mPackage + " new best run time " + thisRunTime);
                                }
                                this.mBestTargetPackage = ((ProcStatsEntry) subProcs.get(i)).mPackage;
                                bestRunTime = thisRunTime;
                            } else if (DEBUG) {
                                Log.d("ProcStatsEntry", "Eval pkg of " + this.mName + ": pkg " + ((ProcStatsEntry) subProcs.get(i)).mPackage + " run time " + thisRunTime + " not as good as last " + bestRunTime);
                            }
                        } else if (DEBUG) {
                            Log.d("ProcStatsEntry", "Eval pkg of " + this.mName + ": pkg " + ((ProcStatsEntry) subProcs.get(i)).mPackage + " has no icon");
                        }
                    } catch (NameNotFoundException e) {
                        if (DEBUG) {
                            Log.d("ProcStatsEntry", "Eval pkg of " + this.mName + ": pkg " + ((ProcStatsEntry) subProcs.get(i)).mPackage + " failed finding app info");
                        }
                    }
                } else if (DEBUG) {
                    Log.d("ProcStatsEntry", "Eval pkg of " + this.mName + ": pkg " + ((ProcStatsEntry) subProcs.get(i)).mPackage + " weight " + ((ProcStatsEntry) subProcs.get(i)).mWeight + " too small");
                }
            }
        } else if (subProcs.size() == 1) {
            this.mBestTargetPackage = ((ProcStatsEntry) subProcs.get(0)).mPackage;
        }
    }

    public void retrieveUiData(PackageManager pm) {
        this.mUiTargetApp = null;
        String str = this.mName;
        this.mUiBaseLabel = str;
        this.mUiLabel = str;
        this.mUiPackage = this.mBestTargetPackage;
        if (this.mUiPackage != null) {
            try {
                this.mUiTargetApp = pm.getApplicationInfo(this.mUiPackage, 41472);
                String name = this.mUiTargetApp.loadLabel(pm).toString();
                this.mUiBaseLabel = name;
                if (this.mName.equals(this.mUiPackage)) {
                    this.mUiLabel = name;
                } else if (this.mName.startsWith(this.mUiPackage)) {
                    int off = this.mUiPackage.length();
                    if (this.mName.length() > off) {
                        off++;
                    }
                    this.mUiLabel = name + " (" + this.mName.substring(off) + ")";
                } else {
                    this.mUiLabel = name + " (" + this.mName + ")";
                }
            } catch (NameNotFoundException e) {
            }
        }
        if (this.mUiTargetApp == null) {
            String[] packages = pm.getPackagesForUid(this.mUid);
            if (packages != null) {
                String[] arr$ = packages;
                int len$ = arr$.length;
                int i$ = 0;
                while (i$ < len$) {
                    String curPkg = arr$[i$];
                    try {
                        PackageInfo pi = pm.getPackageInfo(curPkg, 41472);
                        if (pi.sharedUserLabel != 0) {
                            this.mUiTargetApp = pi.applicationInfo;
                            CharSequence nm = pm.getText(curPkg, pi.sharedUserLabel, pi.applicationInfo);
                            if (nm != null) {
                                this.mUiBaseLabel = nm.toString();
                                this.mUiLabel = this.mUiBaseLabel + " (" + this.mName + ")";
                                return;
                            }
                            this.mUiBaseLabel = this.mUiTargetApp.loadLabel(pm).toString();
                            this.mUiLabel = this.mUiBaseLabel + " (" + this.mName + ")";
                            return;
                        }
                        continue;
                        i$++;
                    } catch (NameNotFoundException e2) {
                    }
                }
                return;
            }
            Log.i("ProcStatsEntry", "No package for uid " + this.mUid);
        }
    }

    public void addService(ServiceState svc) {
        ArrayList<Service> services = (ArrayList) this.mServices.get(svc.mPackage);
        if (services == null) {
            services = new ArrayList();
            this.mServices.put(svc.mPackage, services);
        }
        services.add(new Service(svc));
    }

    public int describeContents() {
        return 0;
    }

    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(this.mPackage);
        dest.writeInt(this.mUid);
        dest.writeString(this.mName);
        dest.writeStringList(this.mPackages);
        dest.writeLong(this.mDuration);
        dest.writeLong(this.mAvgPss);
        dest.writeLong(this.mMaxPss);
        dest.writeLong(this.mAvgUss);
        dest.writeLong(this.mMaxUss);
        dest.writeLong(this.mWeight);
        dest.writeString(this.mBestTargetPackage);
        int N = this.mServices.size();
        dest.writeInt(N);
        for (int i = 0; i < N; i++) {
            dest.writeString((String) this.mServices.keyAt(i));
            dest.writeTypedList((List) this.mServices.valueAt(i));
        }
    }
}
