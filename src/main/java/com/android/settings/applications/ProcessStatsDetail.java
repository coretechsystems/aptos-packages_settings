package com.android.settings.applications;

import android.app.ActivityManager;
import android.app.Fragment;
import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.Uri;
import android.os.Bundle;
import android.os.UserHandle;
import android.text.format.Formatter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import com.android.settings.R;
import com.android.settings.Utils;
import com.android.settings.applications.ProcStatsEntry.Service;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

public class ProcessStatsDetail extends Fragment implements OnClickListener {
    static final Comparator<Service> sServiceCompare = new Comparator<Service>() {
        public int compare(Service lhs, Service rhs) {
            if (lhs.mDuration < rhs.mDuration) {
                return 1;
            }
            if (lhs.mDuration > rhs.mDuration) {
                return -1;
            }
            return 0;
        }
    };
    static final Comparator<ArrayList<Service>> sServicePkgCompare = new Comparator<ArrayList<Service>>() {
        public int compare(ArrayList<Service> lhs, ArrayList<Service> rhs) {
            long topLhs;
            long topRhs;
            if (lhs.size() > 0) {
                topLhs = ((Service) lhs.get(0)).mDuration;
            } else {
                topLhs = 0;
            }
            if (rhs.size() > 0) {
                topRhs = ((Service) rhs.get(0)).mDuration;
            } else {
                topRhs = 0;
            }
            if (topLhs < topRhs) {
                return 1;
            }
            return topLhs > topRhs ? -1 : 0;
        }
    };
    private final BroadcastReceiver mCheckKillProcessesReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            ProcessStatsDetail.this.mForceStopButton.setEnabled(getResultCode() != 0);
        }
    };
    private ViewGroup mDetailsParent;
    private DevicePolicyManager mDpm;
    private ProcStatsEntry mEntry;
    private Button mForceStopButton;
    private long mMaxWeight;
    private PackageManager mPm;
    private Button mReportButton;
    private View mRootView;
    private ViewGroup mServicesParent;
    private TextView mTitleView;
    private long mTotalTime;
    private ViewGroup mTwoButtonsPanel;
    private boolean mUseUss;

    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        this.mPm = getActivity().getPackageManager();
        this.mDpm = (DevicePolicyManager) getActivity().getSystemService("device_policy");
        Bundle args = getArguments();
        this.mEntry = (ProcStatsEntry) args.getParcelable("entry");
        this.mEntry.retrieveUiData(this.mPm);
        this.mUseUss = args.getBoolean("use_uss");
        this.mMaxWeight = args.getLong("max_weight");
        this.mTotalTime = args.getLong("total_time");
    }

    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.process_stats_details, container, false);
        Utils.prepareCustomPreferencesList(container, view, view, false);
        this.mRootView = view;
        createDetails();
        return view;
    }

    public void onResume() {
        super.onResume();
        checkForceStop();
    }

    public void onPause() {
        super.onPause();
    }

    private void createDetails() {
        int appLevel = (int) Math.ceil((((double) this.mEntry.mWeight) / ((double) this.mMaxWeight)) * 100.0d);
        String appLevelText = Utils.formatPercentage(this.mEntry.mDuration, this.mTotalTime);
        TextView summary = (TextView) this.mRootView.findViewById(16908304);
        summary.setText(this.mEntry.mName);
        summary.setVisibility(0);
        this.mTitleView = (TextView) this.mRootView.findViewById(16908310);
        this.mTitleView.setText(this.mEntry.mUiBaseLabel);
        ((TextView) this.mRootView.findViewById(16908308)).setText(appLevelText);
        ((ProgressBar) this.mRootView.findViewById(16908301)).setProgress(appLevel);
        ImageView icon = (ImageView) this.mRootView.findViewById(16908294);
        if (this.mEntry.mUiTargetApp != null) {
            icon.setImageDrawable(this.mEntry.mUiTargetApp.loadIcon(this.mPm));
        }
        this.mTwoButtonsPanel = (ViewGroup) this.mRootView.findViewById(R.id.two_buttons_panel);
        this.mForceStopButton = (Button) this.mRootView.findViewById(R.id.right_button);
        this.mReportButton = (Button) this.mRootView.findViewById(R.id.left_button);
        this.mForceStopButton.setEnabled(false);
        this.mReportButton.setVisibility(4);
        this.mDetailsParent = (ViewGroup) this.mRootView.findViewById(R.id.details);
        this.mServicesParent = (ViewGroup) this.mRootView.findViewById(R.id.services);
        fillDetailsSection();
        fillServicesSection();
        if (this.mEntry.mUid >= 10000) {
            this.mForceStopButton.setText(R.string.force_stop);
            this.mForceStopButton.setTag(Integer.valueOf(1));
            this.mForceStopButton.setOnClickListener(this);
            this.mTwoButtonsPanel.setVisibility(0);
            return;
        }
        this.mTwoButtonsPanel.setVisibility(8);
    }

    public void onClick(View v) {
        doAction(((Integer) v.getTag()).intValue());
    }

    private void doAction(int action) {
        switch (action) {
            case 1:
                killProcesses();
                return;
            default:
                return;
        }
    }

    private void addPackageHeaderItem(ViewGroup parent, String packageName) {
        ViewGroup item = (ViewGroup) getActivity().getLayoutInflater().inflate(R.layout.running_processes_item, null);
        parent.addView(item);
        ImageView icon = (ImageView) item.findViewById(R.id.icon);
        TextView nameView = (TextView) item.findViewById(R.id.name);
        TextView descriptionView = (TextView) item.findViewById(R.id.description);
        try {
            ApplicationInfo ai = this.mPm.getApplicationInfo(packageName, 0);
            icon.setImageDrawable(ai.loadIcon(this.mPm));
            nameView.setText(ai.loadLabel(this.mPm));
        } catch (NameNotFoundException e) {
        }
        descriptionView.setText(packageName);
    }

    private void addDetailsItem(ViewGroup parent, CharSequence label, CharSequence value) {
        ViewGroup item = (ViewGroup) getActivity().getLayoutInflater().inflate(R.layout.power_usage_detail_item_text, null);
        parent.addView(item);
        TextView valueView = (TextView) item.findViewById(R.id.value);
        ((TextView) item.findViewById(R.id.label)).setText(label);
        valueView.setText(value);
    }

    private void fillDetailsSection() {
        addDetailsItem(this.mDetailsParent, getResources().getText(R.string.process_stats_avg_ram_use), Formatter.formatShortFileSize(getActivity(), (this.mUseUss ? this.mEntry.mAvgUss : this.mEntry.mAvgPss) * 1024));
        addDetailsItem(this.mDetailsParent, getResources().getText(R.string.process_stats_max_ram_use), Formatter.formatShortFileSize(getActivity(), (this.mUseUss ? this.mEntry.mMaxUss : this.mEntry.mMaxPss) * 1024));
        addDetailsItem(this.mDetailsParent, getResources().getText(R.string.process_stats_run_time), Utils.formatPercentage(this.mEntry.mDuration, this.mTotalTime));
    }

    private void fillServicesSection() {
        if (this.mEntry.mServices.size() > 0) {
            int ip;
            ArrayList<Service> services;
            boolean addPackageSections = false;
            ArrayList<ArrayList<Service>> servicePkgs = new ArrayList();
            for (ip = 0; ip < this.mEntry.mServices.size(); ip++) {
                services = (ArrayList) ((ArrayList) this.mEntry.mServices.valueAt(ip)).clone();
                Collections.sort(services, sServiceCompare);
                servicePkgs.add(services);
            }
            if (this.mEntry.mServices.size() > 1 || !((Service) ((ArrayList) this.mEntry.mServices.valueAt(0)).get(0)).mPackage.equals(this.mEntry.mPackage)) {
                addPackageSections = true;
                Collections.sort(servicePkgs, sServicePkgCompare);
            }
            for (ip = 0; ip < servicePkgs.size(); ip++) {
                services = (ArrayList) servicePkgs.get(ip);
                if (addPackageSections) {
                    addPackageHeaderItem(this.mServicesParent, ((Service) services.get(0)).mPackage);
                }
                for (int is = 0; is < services.size(); is++) {
                    Service service = (Service) services.get(is);
                    String label = service.mName;
                    int tail = label.lastIndexOf(46);
                    if (tail >= 0 && tail < label.length() - 1) {
                        label = label.substring(tail + 1);
                    }
                    addDetailsItem(this.mServicesParent, label, Utils.formatPercentage(service.mDuration, this.mTotalTime));
                }
            }
        }
    }

    private void killProcesses() {
        ((ActivityManager) getActivity().getSystemService("activity")).forceStopPackage(this.mEntry.mUiPackage);
        checkForceStop();
    }

    private void checkForceStop() {
        if (this.mEntry.mUiPackage == null || this.mEntry.mUid < 10000) {
            this.mForceStopButton.setEnabled(false);
        } else if (this.mDpm.packageHasActiveAdmins(this.mEntry.mUiPackage)) {
            this.mForceStopButton.setEnabled(false);
        } else {
            try {
                if ((this.mPm.getApplicationInfo(this.mEntry.mUiPackage, 0).flags & 2097152) == 0) {
                    this.mForceStopButton.setEnabled(true);
                }
            } catch (NameNotFoundException e) {
            }
            Intent intent = new Intent("android.intent.action.QUERY_PACKAGE_RESTART", Uri.fromParts("package", this.mEntry.mUiPackage, null));
            intent.putExtra("android.intent.extra.PACKAGES", new String[]{this.mEntry.mUiPackage});
            intent.putExtra("android.intent.extra.UID", this.mEntry.mUid);
            intent.putExtra("android.intent.extra.user_handle", UserHandle.getUserId(this.mEntry.mUid));
            getActivity().sendOrderedBroadcast(intent, null, this.mCheckKillProcessesReceiver, null, 0, null, null);
        }
    }
}
