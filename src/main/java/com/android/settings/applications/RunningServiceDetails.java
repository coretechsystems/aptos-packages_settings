package com.android.settings.applications;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityManager.RunningAppProcessInfo;
import android.app.AlertDialog.Builder;
import android.app.ApplicationErrorReport;
import android.app.ApplicationErrorReport.RunningServiceInfo;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentSender.SendIntentException;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ProviderInfo;
import android.content.pm.ServiceInfo;
import android.os.Bundle;
import android.os.Debug;
import android.os.Handler;
import android.os.SystemClock;
import android.os.UserHandle;
import android.provider.Settings.Global;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import com.android.settings.R;
import com.android.settings.Utils;
import com.android.settings.applications.RunningProcessesView.ActiveItem;
import com.android.settings.applications.RunningProcessesView.ViewHolder;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;

public class RunningServiceDetails extends Fragment implements OnRefreshUiListener {
    final ArrayList<ActiveDetail> mActiveDetails = new ArrayList();
    ViewGroup mAllDetails;
    ActivityManager mAm;
    StringBuilder mBuilder = new StringBuilder(128);
    boolean mHaveData;
    LayoutInflater mInflater;
    MergedItem mMergedItem;
    int mNumProcesses;
    int mNumServices;
    String mProcessName;
    TextView mProcessesHeader;
    View mRootView;
    TextView mServicesHeader;
    boolean mShowBackground;
    ViewGroup mSnippet;
    ActiveItem mSnippetActiveItem;
    ViewHolder mSnippetViewHolder;
    RunningState mState;
    int mUid;
    int mUserId;

    class ActiveDetail implements OnClickListener {
        ActiveItem mActiveItem;
        ComponentName mInstaller;
        PendingIntent mManageIntent;
        Button mReportButton;
        View mRootView;
        ServiceItem mServiceItem;
        Button mStopButton;
        ViewHolder mViewHolder;

        ActiveDetail() {
        }

        void stopActiveService(boolean confirmed) {
            ServiceItem si = this.mServiceItem;
            if (confirmed || (si.mServiceInfo.applicationInfo.flags & 1) == 0) {
                RunningServiceDetails.this.getActivity().stopService(new Intent().setComponent(si.mRunningService.service));
                if (RunningServiceDetails.this.mMergedItem == null) {
                    RunningServiceDetails.this.mState.updateNow();
                    RunningServiceDetails.this.finish();
                    return;
                } else if (RunningServiceDetails.this.mShowBackground || RunningServiceDetails.this.mMergedItem.mServices.size() > 1) {
                    RunningServiceDetails.this.mState.updateNow();
                    return;
                } else {
                    RunningServiceDetails.this.mState.updateNow();
                    RunningServiceDetails.this.finish();
                    return;
                }
            }
            RunningServiceDetails.this.showConfirmStopDialog(si.mRunningService.service);
        }

        public void onClick(View v) {
            IOException e;
            FileInputStream input;
            FileInputStream input2;
            byte[] buffer;
            Intent intent;
            Throwable th;
            if (v == this.mReportButton) {
                ApplicationErrorReport report = new ApplicationErrorReport();
                report.type = 5;
                report.packageName = this.mServiceItem.mServiceInfo.packageName;
                report.installerPackageName = this.mInstaller.getPackageName();
                report.processName = this.mServiceItem.mRunningService.process;
                report.time = System.currentTimeMillis();
                report.systemApp = (this.mServiceItem.mServiceInfo.applicationInfo.flags & 1) != 0;
                RunningServiceInfo info = new RunningServiceInfo();
                if (this.mActiveItem.mFirstRunTime >= 0) {
                    info.durationMillis = SystemClock.elapsedRealtime() - this.mActiveItem.mFirstRunTime;
                } else {
                    info.durationMillis = -1;
                }
                ComponentName comp = new ComponentName(this.mServiceItem.mServiceInfo.packageName, this.mServiceItem.mServiceInfo.name);
                File filename = RunningServiceDetails.this.getActivity().getFileStreamPath("service_dump.txt");
                FileOutputStream output = null;
                try {
                    FileOutputStream fileOutputStream = new FileOutputStream(filename);
                    try {
                        Debug.dumpService("activity", fileOutputStream.getFD(), new String[]{"-a", "service", comp.flattenToString()});
                        if (fileOutputStream != null) {
                            try {
                                fileOutputStream.close();
                                output = fileOutputStream;
                            } catch (IOException e2) {
                                output = fileOutputStream;
                            }
                        } else {
                            output = fileOutputStream;
                        }
                    } catch (IOException e3) {
                        e = e3;
                        output = fileOutputStream;
                        try {
                            Log.w("RunningServicesDetails", "Can't dump service: " + comp, e);
                            if (output != null) {
                                try {
                                    output.close();
                                } catch (IOException e4) {
                                }
                            }
                            input = null;
                            input2 = new FileInputStream(filename);
                            buffer = new byte[((int) filename.length())];
                            input2.read(buffer);
                            info.serviceDetails = new String(buffer);
                            if (input2 != null) {
                                try {
                                    input2.close();
                                    input = input2;
                                } catch (IOException e5) {
                                    input = input2;
                                }
                            } else {
                                input = input2;
                            }
                            filename.delete();
                            Log.i("RunningServicesDetails", "Details: " + info.serviceDetails);
                            report.runningServiceInfo = info;
                            intent = new Intent("android.intent.action.APP_ERROR");
                            intent.setComponent(this.mInstaller);
                            intent.putExtra("android.intent.extra.BUG_REPORT", report);
                            intent.addFlags(268435456);
                            RunningServiceDetails.this.startActivity(intent);
                        } catch (Throwable th2) {
                            th = th2;
                            if (output != null) {
                                try {
                                    output.close();
                                } catch (IOException e6) {
                                }
                            }
                            throw th;
                        }
                    } catch (Throwable th3) {
                        th = th3;
                        output = fileOutputStream;
                        if (output != null) {
                            output.close();
                        }
                        throw th;
                    }
                } catch (IOException e7) {
                    e = e7;
                    Log.w("RunningServicesDetails", "Can't dump service: " + comp, e);
                    if (output != null) {
                        output.close();
                    }
                    input = null;
                    input2 = new FileInputStream(filename);
                    buffer = new byte[((int) filename.length())];
                    input2.read(buffer);
                    info.serviceDetails = new String(buffer);
                    if (input2 != null) {
                        input = input2;
                    } else {
                        input2.close();
                        input = input2;
                    }
                    filename.delete();
                    Log.i("RunningServicesDetails", "Details: " + info.serviceDetails);
                    report.runningServiceInfo = info;
                    intent = new Intent("android.intent.action.APP_ERROR");
                    intent.setComponent(this.mInstaller);
                    intent.putExtra("android.intent.extra.BUG_REPORT", report);
                    intent.addFlags(268435456);
                    RunningServiceDetails.this.startActivity(intent);
                }
                input = null;
                try {
                    input2 = new FileInputStream(filename);
                    try {
                        buffer = new byte[((int) filename.length())];
                        input2.read(buffer);
                        info.serviceDetails = new String(buffer);
                        if (input2 != null) {
                            input2.close();
                            input = input2;
                        } else {
                            input = input2;
                        }
                    } catch (IOException e8) {
                        e = e8;
                        input = input2;
                        try {
                            Log.w("RunningServicesDetails", "Can't read service dump: " + comp, e);
                            if (input != null) {
                                try {
                                    input.close();
                                } catch (IOException e9) {
                                }
                            }
                            filename.delete();
                            Log.i("RunningServicesDetails", "Details: " + info.serviceDetails);
                            report.runningServiceInfo = info;
                            intent = new Intent("android.intent.action.APP_ERROR");
                            intent.setComponent(this.mInstaller);
                            intent.putExtra("android.intent.extra.BUG_REPORT", report);
                            intent.addFlags(268435456);
                            RunningServiceDetails.this.startActivity(intent);
                        } catch (Throwable th4) {
                            th = th4;
                            if (input != null) {
                                try {
                                    input.close();
                                } catch (IOException e10) {
                                }
                            }
                            throw th;
                        }
                    } catch (Throwable th5) {
                        th = th5;
                        input = input2;
                        if (input != null) {
                            input.close();
                        }
                        throw th;
                    }
                } catch (IOException e11) {
                    e = e11;
                    Log.w("RunningServicesDetails", "Can't read service dump: " + comp, e);
                    if (input != null) {
                        input.close();
                    }
                    filename.delete();
                    Log.i("RunningServicesDetails", "Details: " + info.serviceDetails);
                    report.runningServiceInfo = info;
                    intent = new Intent("android.intent.action.APP_ERROR");
                    intent.setComponent(this.mInstaller);
                    intent.putExtra("android.intent.extra.BUG_REPORT", report);
                    intent.addFlags(268435456);
                    RunningServiceDetails.this.startActivity(intent);
                }
                filename.delete();
                Log.i("RunningServicesDetails", "Details: " + info.serviceDetails);
                report.runningServiceInfo = info;
                intent = new Intent("android.intent.action.APP_ERROR");
                intent.setComponent(this.mInstaller);
                intent.putExtra("android.intent.extra.BUG_REPORT", report);
                intent.addFlags(268435456);
                RunningServiceDetails.this.startActivity(intent);
            } else if (this.mManageIntent != null) {
                try {
                    RunningServiceDetails.this.getActivity().startIntentSender(this.mManageIntent.getIntentSender(), null, 268959744, 524288, 0);
                } catch (SendIntentException e12) {
                    Log.w("RunningServicesDetails", e12);
                } catch (IllegalArgumentException e13) {
                    Log.w("RunningServicesDetails", e13);
                } catch (ActivityNotFoundException e14) {
                    Log.w("RunningServicesDetails", e14);
                }
            } else if (this.mServiceItem != null) {
                stopActiveService(false);
            } else if (this.mActiveItem.mItem.mBackground) {
                RunningServiceDetails.this.mAm.killBackgroundProcesses(this.mActiveItem.mItem.mPackageInfo.packageName);
                RunningServiceDetails.this.finish();
            } else {
                RunningServiceDetails.this.mAm.forceStopPackage(this.mActiveItem.mItem.mPackageInfo.packageName);
                RunningServiceDetails.this.finish();
            }
        }
    }

    public static class MyAlertDialogFragment extends DialogFragment {
        public static MyAlertDialogFragment newConfirmStop(int id, ComponentName comp) {
            MyAlertDialogFragment frag = new MyAlertDialogFragment();
            Bundle args = new Bundle();
            args.putInt("id", id);
            args.putParcelable("comp", comp);
            frag.setArguments(args);
            return frag;
        }

        RunningServiceDetails getOwner() {
            return (RunningServiceDetails) getTargetFragment();
        }

        public Dialog onCreateDialog(Bundle savedInstanceState) {
            int id = getArguments().getInt("id");
            switch (id) {
                case 1:
                    final ComponentName comp = (ComponentName) getArguments().getParcelable("comp");
                    if (getOwner().activeDetailForService(comp) == null) {
                        return null;
                    }
                    return new Builder(getActivity()).setTitle(getActivity().getString(R.string.runningservicedetails_stop_dlg_title)).setMessage(getActivity().getString(R.string.runningservicedetails_stop_dlg_text)).setPositiveButton(R.string.dlg_ok, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            ActiveDetail ad = MyAlertDialogFragment.this.getOwner().activeDetailForService(comp);
                            if (ad != null) {
                                ad.stopActiveService(true);
                            }
                        }
                    }).setNegativeButton(R.string.dlg_cancel, null).create();
                default:
                    throw new IllegalArgumentException("unknown id " + id);
            }
        }
    }

    boolean findMergedItem() {
        MergedItem item = null;
        ArrayList<MergedItem> newItems = this.mShowBackground ? this.mState.getCurrentBackgroundItems() : this.mState.getCurrentMergedItems();
        if (newItems != null) {
            for (int i = 0; i < newItems.size(); i++) {
                MergedItem mi = (MergedItem) newItems.get(i);
                if (mi.mUserId == this.mUserId && ((this.mUid < 0 || mi.mProcess == null || mi.mProcess.mUid == this.mUid) && (this.mProcessName == null || (mi.mProcess != null && this.mProcessName.equals(mi.mProcess.mProcessName))))) {
                    item = mi;
                    break;
                }
            }
        }
        if (this.mMergedItem == item) {
            return false;
        }
        this.mMergedItem = item;
        return true;
    }

    void addServicesHeader() {
        if (this.mNumServices == 0) {
            this.mServicesHeader = (TextView) this.mInflater.inflate(R.layout.separator_label, this.mAllDetails, false);
            this.mServicesHeader.setText(R.string.runningservicedetails_services_title);
            this.mAllDetails.addView(this.mServicesHeader);
        }
        this.mNumServices++;
    }

    void addProcessesHeader() {
        if (this.mNumProcesses == 0) {
            this.mProcessesHeader = (TextView) this.mInflater.inflate(R.layout.separator_label, this.mAllDetails, false);
            this.mProcessesHeader.setText(R.string.runningservicedetails_processes_title);
            this.mAllDetails.addView(this.mProcessesHeader);
        }
        this.mNumProcesses++;
    }

    void addServiceDetailsView(ServiceItem si, MergedItem mi, boolean isService, boolean inclDetails) {
        BaseItem bi;
        if (isService) {
            addServicesHeader();
        } else if (mi.mUserId != UserHandle.myUserId()) {
            addProcessesHeader();
        }
        if (si != null) {
            bi = si;
        } else {
            bi = mi;
        }
        ActiveDetail detail = new ActiveDetail();
        View root = this.mInflater.inflate(R.layout.running_service_details_service, this.mAllDetails, false);
        this.mAllDetails.addView(root);
        detail.mRootView = root;
        detail.mServiceItem = si;
        detail.mViewHolder = new ViewHolder(root);
        detail.mActiveItem = detail.mViewHolder.bind(this.mState, bi, this.mBuilder);
        if (!inclDetails) {
            root.findViewById(R.id.service).setVisibility(8);
        }
        if (!(si == null || si.mRunningService.clientLabel == 0)) {
            detail.mManageIntent = this.mAm.getRunningServiceControlPanel(si.mRunningService.service);
        }
        TextView description = (TextView) root.findViewById(R.id.comp_description);
        detail.mStopButton = (Button) root.findViewById(R.id.left_button);
        detail.mReportButton = (Button) root.findViewById(R.id.right_button);
        if (!isService || mi.mUserId == UserHandle.myUserId()) {
            if (si != null && si.mServiceInfo.descriptionRes != 0) {
                description.setText(getActivity().getPackageManager().getText(si.mServiceInfo.packageName, si.mServiceInfo.descriptionRes, si.mServiceInfo.applicationInfo));
            } else if (mi.mBackground) {
                description.setText(R.string.background_process_stop_description);
            } else if (detail.mManageIntent != null) {
                try {
                    String label = getActivity().getPackageManager().getResourcesForApplication(si.mRunningService.clientPackage).getString(si.mRunningService.clientLabel);
                    description.setText(getActivity().getString(R.string.service_manage_description, new Object[]{label}));
                } catch (NameNotFoundException e) {
                }
            } else {
                description.setText(getActivity().getText(si != null ? R.string.service_stop_description : R.string.heavy_weight_stop_description));
            }
            detail.mStopButton.setOnClickListener(detail);
            detail.mStopButton.setText(getActivity().getText(detail.mManageIntent != null ? R.string.service_manage : R.string.service_stop));
            detail.mReportButton.setOnClickListener(detail);
            detail.mReportButton.setText(17040505);
            if (Global.getInt(getActivity().getContentResolver(), "send_action_app_error", 0) == 0 || si == null) {
                detail.mReportButton.setEnabled(false);
            } else {
                boolean z;
                detail.mInstaller = ApplicationErrorReport.getErrorReportReceiver(getActivity(), si.mServiceInfo.packageName, si.mServiceInfo.applicationInfo.flags);
                Button button = detail.mReportButton;
                if (detail.mInstaller != null) {
                    z = true;
                } else {
                    z = false;
                }
                button.setEnabled(z);
            }
        } else {
            description.setVisibility(8);
            root.findViewById(R.id.control_buttons_panel).setVisibility(8);
        }
        this.mActiveDetails.add(detail);
    }

    void addProcessDetailsView(ProcessItem pi, boolean isMain) {
        addProcessesHeader();
        ActiveDetail detail = new ActiveDetail();
        View root = this.mInflater.inflate(R.layout.running_service_details_process, this.mAllDetails, false);
        this.mAllDetails.addView(root);
        detail.mRootView = root;
        detail.mViewHolder = new ViewHolder(root);
        detail.mActiveItem = detail.mViewHolder.bind(this.mState, pi, this.mBuilder);
        TextView description = (TextView) root.findViewById(R.id.comp_description);
        if (pi.mUserId != UserHandle.myUserId()) {
            description.setVisibility(8);
        } else if (isMain) {
            description.setText(R.string.main_running_process_description);
        } else {
            int textid = 0;
            CharSequence label = null;
            RunningAppProcessInfo rpi = pi.mRunningProcessInfo;
            ComponentName comp = rpi.importanceReasonComponent;
            switch (rpi.importanceReasonCode) {
                case 1:
                    textid = R.string.process_provider_in_use_description;
                    if (rpi.importanceReasonComponent != null) {
                        try {
                            ProviderInfo prov = getActivity().getPackageManager().getProviderInfo(rpi.importanceReasonComponent, 0);
                            label = RunningState.makeLabel(getActivity().getPackageManager(), prov.name, prov);
                            break;
                        } catch (NameNotFoundException e) {
                            break;
                        }
                    }
                    break;
                case 2:
                    textid = R.string.process_service_in_use_description;
                    if (rpi.importanceReasonComponent != null) {
                        try {
                            ServiceInfo serv = getActivity().getPackageManager().getServiceInfo(rpi.importanceReasonComponent, 0);
                            label = RunningState.makeLabel(getActivity().getPackageManager(), serv.name, serv);
                            break;
                        } catch (NameNotFoundException e2) {
                            break;
                        }
                    }
                    break;
            }
            if (!(textid == 0 || label == null)) {
                description.setText(getActivity().getString(textid, new Object[]{label}));
            }
        }
        this.mActiveDetails.add(detail);
    }

    void addDetailsViews(MergedItem item, boolean inclServices, boolean inclProcesses) {
        if (item != null) {
            int i;
            if (inclServices) {
                for (i = 0; i < item.mServices.size(); i++) {
                    addServiceDetailsView((ServiceItem) item.mServices.get(i), item, true, true);
                }
            }
            if (!inclProcesses) {
                return;
            }
            if (item.mServices.size() <= 0) {
                boolean z;
                if (item.mUserId != UserHandle.myUserId()) {
                    z = true;
                } else {
                    z = false;
                }
                addServiceDetailsView(null, item, false, z);
                return;
            }
            i = -1;
            while (i < item.mOtherProcesses.size()) {
                ProcessItem pi = i < 0 ? item.mProcess : (ProcessItem) item.mOtherProcesses.get(i);
                if (pi == null || pi.mPid > 0) {
                    addProcessDetailsView(pi, i < 0);
                }
                i++;
            }
        }
    }

    void addDetailViews() {
        int i;
        for (i = this.mActiveDetails.size() - 1; i >= 0; i--) {
            this.mAllDetails.removeView(((ActiveDetail) this.mActiveDetails.get(i)).mRootView);
        }
        this.mActiveDetails.clear();
        if (this.mServicesHeader != null) {
            this.mAllDetails.removeView(this.mServicesHeader);
            this.mServicesHeader = null;
        }
        if (this.mProcessesHeader != null) {
            this.mAllDetails.removeView(this.mProcessesHeader);
            this.mProcessesHeader = null;
        }
        this.mNumProcesses = 0;
        this.mNumServices = 0;
        if (this.mMergedItem == null) {
            return;
        }
        if (this.mMergedItem.mUser != null) {
            ArrayList<MergedItem> items;
            if (this.mShowBackground) {
                items = new ArrayList(this.mMergedItem.mChildren);
                Collections.sort(items, this.mState.mBackgroundComparator);
            } else {
                items = this.mMergedItem.mChildren;
            }
            for (i = 0; i < items.size(); i++) {
                addDetailsViews((MergedItem) items.get(i), true, false);
            }
            for (i = 0; i < items.size(); i++) {
                addDetailsViews((MergedItem) items.get(i), false, true);
            }
            return;
        }
        addDetailsViews(this.mMergedItem, true, true);
    }

    void refreshUi(boolean dataChanged) {
        if (findMergedItem()) {
            dataChanged = true;
        }
        if (dataChanged) {
            if (this.mMergedItem != null) {
                this.mSnippetActiveItem = this.mSnippetViewHolder.bind(this.mState, this.mMergedItem, this.mBuilder);
            } else if (this.mSnippetActiveItem != null) {
                this.mSnippetActiveItem.mHolder.size.setText("");
                this.mSnippetActiveItem.mHolder.uptime.setText("");
                this.mSnippetActiveItem.mHolder.description.setText(R.string.no_services);
            } else {
                finish();
                return;
            }
            addDetailViews();
        }
    }

    private void finish() {
        new Handler().post(new Runnable() {
            public void run() {
                Activity a = RunningServiceDetails.this.getActivity();
                if (a != null) {
                    a.onBackPressed();
                }
            }
        });
    }

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.mUid = getArguments().getInt("uid", -1);
        this.mUserId = getArguments().getInt("user_id", 0);
        this.mProcessName = getArguments().getString("process", null);
        this.mShowBackground = getArguments().getBoolean("background", false);
        this.mAm = (ActivityManager) getActivity().getSystemService("activity");
        this.mInflater = (LayoutInflater) getActivity().getSystemService("layout_inflater");
        this.mState = RunningState.getInstance(getActivity());
    }

    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.running_service_details, container, false);
        Utils.prepareCustomPreferencesList(container, view, view, false);
        this.mRootView = view;
        this.mAllDetails = (ViewGroup) view.findViewById(R.id.all_details);
        this.mSnippet = (ViewGroup) view.findViewById(R.id.snippet);
        this.mSnippetViewHolder = new ViewHolder(this.mSnippet);
        ensureData();
        return view;
    }

    public void onPause() {
        super.onPause();
        this.mHaveData = false;
        this.mState.pause();
    }

    public void onResume() {
        super.onResume();
        ensureData();
    }

    ActiveDetail activeDetailForService(ComponentName comp) {
        for (int i = 0; i < this.mActiveDetails.size(); i++) {
            ActiveDetail ad = (ActiveDetail) this.mActiveDetails.get(i);
            if (ad.mServiceItem != null && ad.mServiceItem.mRunningService != null && comp.equals(ad.mServiceItem.mRunningService.service)) {
                return ad;
            }
        }
        return null;
    }

    private void showConfirmStopDialog(ComponentName comp) {
        DialogFragment newFragment = MyAlertDialogFragment.newConfirmStop(1, comp);
        newFragment.setTargetFragment(this, 0);
        newFragment.show(getFragmentManager(), "confirmstop");
    }

    void ensureData() {
        if (!this.mHaveData) {
            this.mHaveData = true;
            this.mState.resume(this);
            this.mState.waitForData();
            refreshUi(true);
        }
    }

    void updateTimes() {
        if (this.mSnippetActiveItem != null) {
            this.mSnippetActiveItem.updateTime(getActivity(), this.mBuilder);
        }
        for (int i = 0; i < this.mActiveDetails.size(); i++) {
            ((ActiveDetail) this.mActiveDetails.get(i)).mActiveItem.updateTime(getActivity(), this.mBuilder);
        }
    }

    public void onRefreshUi(int what) {
        if (getActivity() != null) {
            switch (what) {
                case 0:
                    updateTimes();
                    return;
                case 1:
                    refreshUi(false);
                    updateTimes();
                    return;
                case 2:
                    refreshUi(true);
                    updateTimes();
                    return;
                default:
                    return;
            }
        }
    }
}
