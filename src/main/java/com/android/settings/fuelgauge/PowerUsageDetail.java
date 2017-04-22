package com.android.settings.fuelgauge;

import android.app.ActivityManager;
import android.app.ApplicationErrorReport;
import android.app.ApplicationErrorReport.BatteryInfo;
import android.app.Fragment;
import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.BatteryStats.Uid;
import android.os.Bundle;
import android.os.Process;
import android.os.UserHandle;
import android.provider.Settings.Global;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import com.android.internal.os.BatterySipper.DrainType;
import com.android.internal.os.BatteryStatsHelper;
import com.android.internal.util.FastPrintWriter;
import com.android.settings.DisplaySettings;
import com.android.settings.R;
import com.android.settings.SettingsActivity;
import com.android.settings.Utils;
import com.android.settings.WirelessSettings;
import com.android.settings.applications.InstalledAppDetails;
import com.android.settings.bluetooth.BluetoothSettings;
import com.android.settings.location.LocationSettings;
import com.android.settings.wifi.WifiSettings;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;

public class PowerUsageDetail extends Fragment implements OnClickListener {
    private static int[] sDrainTypeDesciptions = new int[]{R.string.battery_desc_standby, R.string.battery_desc_radio, R.string.battery_desc_voice, R.string.battery_desc_wifi, R.string.battery_desc_bluetooth, R.string.battery_desc_flashlight, R.string.battery_desc_display, R.string.battery_desc_apps, R.string.battery_desc_users, R.string.battery_desc_unaccounted, R.string.battery_desc_overcounted};
    ApplicationInfo mApp;
    private Drawable mAppIcon;
    private final BroadcastReceiver mCheckKillProcessesReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            PowerUsageDetail.this.mForceStopButton.setEnabled(getResultCode() != 0);
        }
    };
    private ViewGroup mControlsParent;
    private ViewGroup mDetailsParent;
    private DevicePolicyManager mDpm;
    private DrainType mDrainType;
    private Button mForceStopButton;
    ComponentName mInstaller;
    private ViewGroup mMessagesParent;
    private double mNoCoverage;
    private String[] mPackages;
    private PackageManager mPm;
    private Button mReportButton;
    private View mRootView;
    private boolean mShowLocationButton;
    private long mStartTime;
    private String mTitle;
    private TextView mTitleView;
    private ViewGroup mTwoButtonsPanel;
    private int[] mTypes;
    private int mUid;
    private int mUsageSince;
    private boolean mUsesGps;
    private double[] mValues;

    static /* synthetic */ class AnonymousClass2 {
        static final /* synthetic */ int[] $SwitchMap$com$android$internal$os$BatterySipper$DrainType = new int[DrainType.values().length];

        static {
            try {
                $SwitchMap$com$android$internal$os$BatterySipper$DrainType[DrainType.APP.ordinal()] = 1;
            } catch (NoSuchFieldError e) {
            }
            try {
                $SwitchMap$com$android$internal$os$BatterySipper$DrainType[DrainType.USER.ordinal()] = 2;
            } catch (NoSuchFieldError e2) {
            }
            try {
                $SwitchMap$com$android$internal$os$BatterySipper$DrainType[DrainType.CELL.ordinal()] = 3;
            } catch (NoSuchFieldError e3) {
            }
            try {
                $SwitchMap$com$android$internal$os$BatterySipper$DrainType[DrainType.WIFI.ordinal()] = 4;
            } catch (NoSuchFieldError e4) {
            }
            try {
                $SwitchMap$com$android$internal$os$BatterySipper$DrainType[DrainType.BLUETOOTH.ordinal()] = 5;
            } catch (NoSuchFieldError e5) {
            }
            try {
                $SwitchMap$com$android$internal$os$BatterySipper$DrainType[DrainType.UNACCOUNTED.ordinal()] = 6;
            } catch (NoSuchFieldError e6) {
            }
            try {
                $SwitchMap$com$android$internal$os$BatterySipper$DrainType[DrainType.OVERCOUNTED.ordinal()] = 7;
            } catch (NoSuchFieldError e7) {
            }
            try {
                $SwitchMap$com$android$internal$os$BatterySipper$DrainType[DrainType.SCREEN.ordinal()] = 8;
            } catch (NoSuchFieldError e8) {
            }
        }
    }

    public static void startBatteryDetailPage(SettingsActivity caller, BatteryStatsHelper helper, int statsType, BatteryEntry entry, boolean showLocationButton) {
        int[] types;
        double[] values;
        helper.getStats();
        int dischargeAmount = helper.getStats().getDischargeAmount(statsType);
        Bundle args = new Bundle();
        args.putString("title", entry.name);
        args.putInt("percent", (int) (((entry.sipper.value * ((double) dischargeAmount)) / helper.getTotalPower()) + 0.5d));
        args.putInt("gauge", (int) Math.ceil((entry.sipper.value * 100.0d) / helper.getMaxPower()));
        args.putLong("duration", helper.getStatsPeriod());
        args.putString("iconPackage", entry.defaultPackageName);
        args.putInt("iconId", entry.iconId);
        args.putDouble("noCoverage", entry.sipper.noCoveragePercent);
        if (entry.sipper.uidObj != null) {
            args.putInt("uid", entry.sipper.uidObj.getUid());
        }
        args.putSerializable("drainType", entry.sipper.drainType);
        args.putBoolean("showLocationButton", showLocationButton);
        int userId = UserHandle.myUserId();
        switch (AnonymousClass2.$SwitchMap$com$android$internal$os$BatterySipper$DrainType[entry.sipper.drainType.ordinal()]) {
            case 1:
            case 2:
                Uid uid = entry.sipper.uidObj;
                types = new int[]{R.string.usage_type_cpu, R.string.usage_type_cpu_foreground, R.string.usage_type_wake_lock, R.string.usage_type_gps, R.string.usage_type_wifi_running, R.string.usage_type_data_recv, R.string.usage_type_data_send, R.string.usage_type_radio_active, R.string.usage_type_data_wifi_recv, R.string.usage_type_data_wifi_send, R.string.usage_type_audio, R.string.usage_type_video};
                values = new double[]{(double) entry.sipper.cpuTime, (double) entry.sipper.cpuFgTime, (double) entry.sipper.wakeLockTime, (double) entry.sipper.gpsTime, (double) entry.sipper.wifiRunningTime, (double) entry.sipper.mobileRxPackets, (double) entry.sipper.mobileTxPackets, (double) entry.sipper.mobileActive, (double) entry.sipper.wifiRxPackets, (double) entry.sipper.wifiTxPackets, 0.0d, 0.0d};
                if (entry.sipper.drainType == DrainType.APP) {
                    Writer result = new StringWriter();
                    PrintWriter printWriter = new FastPrintWriter(result, false, 1024);
                    helper.getStats().dumpLocked(caller, printWriter, "", helper.getStatsType(), uid.getUid());
                    printWriter.flush();
                    args.putString("report_details", result.toString());
                    result = new StringWriter();
                    printWriter = new FastPrintWriter(result, false, 1024);
                    helper.getStats().dumpCheckinLocked(caller, printWriter, helper.getStatsType(), uid.getUid());
                    printWriter.flush();
                    args.putString("report_checkin_details", result.toString());
                    userId = UserHandle.getUserId(uid.getUid());
                    break;
                }
                break;
            case 3:
                types = new int[]{R.string.usage_type_on_time, R.string.usage_type_no_coverage, R.string.usage_type_radio_active};
                values = new double[]{(double) entry.sipper.usageTime, entry.sipper.noCoveragePercent, (double) entry.sipper.mobileActive};
                break;
            case 4:
                types = new int[]{R.string.usage_type_wifi_running, R.string.usage_type_cpu, R.string.usage_type_cpu_foreground, R.string.usage_type_wake_lock, R.string.usage_type_data_recv, R.string.usage_type_data_send, R.string.usage_type_data_wifi_recv, R.string.usage_type_data_wifi_send};
                values = new double[]{(double) entry.sipper.usageTime, (double) entry.sipper.cpuTime, (double) entry.sipper.cpuFgTime, (double) entry.sipper.wakeLockTime, (double) entry.sipper.mobileRxPackets, (double) entry.sipper.mobileTxPackets, (double) entry.sipper.wifiRxPackets, (double) entry.sipper.wifiTxPackets};
                break;
            case 5:
                types = new int[]{R.string.usage_type_on_time, R.string.usage_type_cpu, R.string.usage_type_cpu_foreground, R.string.usage_type_wake_lock, R.string.usage_type_data_recv, R.string.usage_type_data_send, R.string.usage_type_data_wifi_recv, R.string.usage_type_data_wifi_send};
                values = new double[]{(double) entry.sipper.usageTime, (double) entry.sipper.cpuTime, (double) entry.sipper.cpuFgTime, (double) entry.sipper.wakeLockTime, (double) entry.sipper.mobileRxPackets, (double) entry.sipper.mobileTxPackets, (double) entry.sipper.wifiRxPackets, (double) entry.sipper.wifiTxPackets};
                break;
            case 6:
                types = new int[]{R.string.usage_type_total_battery_capacity, R.string.usage_type_computed_power, R.string.usage_type_actual_power};
                values = new double[]{helper.getPowerProfile().getBatteryCapacity(), helper.getComputedPower(), helper.getMinDrainedPower()};
                break;
            case 7:
                types = new int[]{R.string.usage_type_total_battery_capacity, R.string.usage_type_computed_power, R.string.usage_type_actual_power};
                values = new double[]{helper.getPowerProfile().getBatteryCapacity(), helper.getComputedPower(), helper.getMaxDrainedPower()};
                break;
            default:
                types = new int[]{R.string.usage_type_on_time};
                values = new double[]{(double) entry.sipper.usageTime};
                break;
        }
        args.putIntArray("types", types);
        args.putDoubleArray("values", values);
        if (userId == UserHandle.myUserId()) {
            caller.startPreferencePanel(PowerUsageDetail.class.getName(), args, R.string.details_title, null, null, 0);
            return;
        }
        caller.startPreferencePanelAsUser(PowerUsageDetail.class.getName(), args, R.string.details_title, null, new UserHandle(userId));
    }

    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        this.mPm = getActivity().getPackageManager();
        this.mDpm = (DevicePolicyManager) getActivity().getSystemService("device_policy");
    }

    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.power_usage_details, container, false);
        Utils.prepareCustomPreferencesList(container, view, view, false);
        this.mRootView = view;
        createDetails();
        return view;
    }

    public void onResume() {
        super.onResume();
        this.mStartTime = Process.getElapsedCpuTime();
        checkForceStop();
    }

    public void onPause() {
        super.onPause();
    }

    private void createDetails() {
        Bundle args = getArguments();
        this.mTitle = args.getString("title");
        int percentage = args.getInt("percent", 1);
        int gaugeValue = args.getInt("gauge", 1);
        this.mUsageSince = args.getInt("since", 1);
        this.mUid = args.getInt("uid", 0);
        this.mDrainType = (DrainType) args.getSerializable("drainType");
        this.mNoCoverage = args.getDouble("noCoverage", 0.0d);
        String iconPackage = args.getString("iconPackage");
        int iconId = args.getInt("iconId", 0);
        this.mShowLocationButton = args.getBoolean("showLocationButton");
        if (!TextUtils.isEmpty(iconPackage)) {
            try {
                PackageManager pm = getActivity().getPackageManager();
                ApplicationInfo ai = pm.getPackageInfo(iconPackage, 0).applicationInfo;
                if (ai != null) {
                    this.mAppIcon = ai.loadIcon(pm);
                }
            } catch (NameNotFoundException e) {
            }
        } else if (iconId != 0) {
            this.mAppIcon = getActivity().getResources().getDrawable(iconId);
        }
        if (this.mAppIcon == null) {
            this.mAppIcon = getActivity().getPackageManager().getDefaultActivityIcon();
        }
        TextView summary = (TextView) this.mRootView.findViewById(16908304);
        summary.setText(getDescriptionForDrainType());
        summary.setVisibility(0);
        this.mTypes = args.getIntArray("types");
        this.mValues = args.getDoubleArray("values");
        this.mTitleView = (TextView) this.mRootView.findViewById(16908310);
        this.mTitleView.setText(this.mTitle);
        ((TextView) this.mRootView.findViewById(16908308)).setText(Utils.formatPercentage(percentage));
        this.mTwoButtonsPanel = (ViewGroup) this.mRootView.findViewById(R.id.two_buttons_panel);
        this.mForceStopButton = (Button) this.mRootView.findViewById(R.id.left_button);
        this.mReportButton = (Button) this.mRootView.findViewById(R.id.right_button);
        this.mForceStopButton.setEnabled(false);
        ((ProgressBar) this.mRootView.findViewById(16908301)).setProgress(gaugeValue);
        ((ImageView) this.mRootView.findViewById(16908294)).setImageDrawable(this.mAppIcon);
        this.mDetailsParent = (ViewGroup) this.mRootView.findViewById(R.id.details);
        this.mControlsParent = (ViewGroup) this.mRootView.findViewById(R.id.controls);
        this.mMessagesParent = (ViewGroup) this.mRootView.findViewById(R.id.messages);
        fillDetailsSection();
        fillPackagesSection(this.mUid);
        fillControlsSection(this.mUid);
        fillMessagesSection(this.mUid);
        if (this.mUid >= 10000) {
            this.mForceStopButton.setText(R.string.force_stop);
            this.mForceStopButton.setTag(Integer.valueOf(7));
            this.mForceStopButton.setOnClickListener(this);
            this.mReportButton.setText(17040505);
            this.mReportButton.setTag(Integer.valueOf(8));
            this.mReportButton.setOnClickListener(this);
            if (Global.getInt(getActivity().getContentResolver(), "send_action_app_error", 0) != 0) {
                if (this.mPackages != null && this.mPackages.length > 0) {
                    try {
                        this.mApp = getActivity().getPackageManager().getApplicationInfo(this.mPackages[0], 0);
                        this.mInstaller = ApplicationErrorReport.getErrorReportReceiver(getActivity(), this.mPackages[0], this.mApp.flags);
                    } catch (NameNotFoundException e2) {
                    }
                }
                this.mReportButton.setEnabled(this.mInstaller != null);
                return;
            }
            this.mTwoButtonsPanel.setVisibility(8);
            return;
        }
        this.mTwoButtonsPanel.setVisibility(8);
    }

    public void onClick(View v) {
        doAction(((Integer) v.getTag()).intValue());
    }

    private void startApplicationDetailsActivity() {
        Bundle args = new Bundle();
        args.putString("package", this.mPackages[0]);
        ((SettingsActivity) getActivity()).startPreferencePanel(InstalledAppDetails.class.getName(), args, R.string.application_info_label, null, null, 0);
    }

    private void doAction(int action) {
        SettingsActivity sa = (SettingsActivity) getActivity();
        switch (action) {
            case 1:
                sa.startPreferencePanel(DisplaySettings.class.getName(), null, R.string.display_settings_title, null, null, 0);
                return;
            case 2:
                sa.startPreferencePanel(WifiSettings.class.getName(), null, R.string.wifi_settings, null, null, 0);
                return;
            case 3:
                sa.startPreferencePanel(BluetoothSettings.class.getName(), null, R.string.bluetooth_settings, null, null, 0);
                return;
            case 4:
                sa.startPreferencePanel(WirelessSettings.class.getName(), null, R.string.radio_controls_title, null, null, 0);
                return;
            case 5:
                startApplicationDetailsActivity();
                return;
            case 6:
                sa.startPreferencePanel(LocationSettings.class.getName(), null, R.string.location_settings_title, null, null, 0);
                return;
            case 7:
                killProcesses();
                return;
            case 8:
                reportBatteryUse();
                return;
            default:
                return;
        }
    }

    private void fillDetailsSection() {
        LayoutInflater inflater = getActivity().getLayoutInflater();
        if (this.mTypes != null && this.mValues != null) {
            for (int i = 0; i < this.mTypes.length; i++) {
                if (this.mValues[i] > 0.0d) {
                    String value;
                    String label = getString(this.mTypes[i]);
                    switch (this.mTypes[i]) {
                        case R.string.usage_type_gps:
                            this.mUsesGps = true;
                            break;
                        case R.string.usage_type_data_send:
                        case R.string.usage_type_data_recv:
                        case R.string.usage_type_data_wifi_send:
                        case R.string.usage_type_data_wifi_recv:
                            value = Long.toString((long) this.mValues[i]);
                            break;
                        case R.string.usage_type_no_coverage:
                            value = Utils.formatPercentage((int) Math.floor(this.mValues[i]));
                            break;
                        case R.string.usage_type_total_battery_capacity:
                        case R.string.usage_type_computed_power:
                        case R.string.usage_type_actual_power:
                            value = getActivity().getString(R.string.mah, new Object[]{Long.valueOf((long) this.mValues[i])});
                            break;
                    }
                    value = Utils.formatElapsedTime(getActivity(), this.mValues[i], true);
                    ViewGroup item = (ViewGroup) inflater.inflate(R.layout.power_usage_detail_item_text, null);
                    this.mDetailsParent.addView(item);
                    TextView valueView = (TextView) item.findViewById(R.id.value);
                    ((TextView) item.findViewById(R.id.label)).setText(label);
                    valueView.setText(value);
                }
            }
        }
    }

    private void fillControlsSection(int uid) {
        PackageManager pm = getActivity().getPackageManager();
        String[] packages = pm.getPackagesForUid(uid);
        PackageInfo pi = null;
        if (packages != null) {
            try {
                pi = pm.getPackageInfo(packages[0], 0);
            } catch (NameNotFoundException e) {
            }
        } else {
            pi = null;
        }
        if (pi != null) {
            ApplicationInfo ai = pi.applicationInfo;
        } else {
            Object obj = null;
        }
        boolean removeHeader = true;
        switch (AnonymousClass2.$SwitchMap$com$android$internal$os$BatterySipper$DrainType[this.mDrainType.ordinal()]) {
            case 1:
                if (packages != null && packages.length == 1) {
                    addControl(R.string.battery_action_app_details, R.string.battery_sugg_apps_info, 5);
                    removeHeader = false;
                }
                if (this.mUsesGps && this.mShowLocationButton) {
                    addControl(R.string.location_settings_title, R.string.battery_sugg_apps_gps, 6);
                    removeHeader = false;
                    break;
                }
            case 3:
                if (this.mNoCoverage > 10.0d) {
                    addControl(R.string.radio_controls_title, R.string.battery_sugg_radio, 4);
                    removeHeader = false;
                    break;
                }
                break;
            case 4:
                addControl(R.string.wifi_settings, R.string.battery_sugg_wifi, 2);
                removeHeader = false;
                break;
            case 5:
                addControl(R.string.bluetooth_settings, R.string.battery_sugg_bluetooth_basic, 3);
                removeHeader = false;
                break;
            case 8:
                addControl(R.string.display_settings, R.string.battery_sugg_display, 1);
                removeHeader = false;
                break;
        }
        if (removeHeader) {
            this.mControlsParent.setVisibility(8);
        }
    }

    private void addControl(int title, int summary, int action) {
        Resources res = getResources();
        ViewGroup item = (ViewGroup) getActivity().getLayoutInflater().inflate(R.layout.power_usage_action_item, null);
        this.mControlsParent.addView(item);
        Button actionButton = (Button) item.findViewById(R.id.action_button);
        TextView summaryView = (TextView) item.findViewById(R.id.summary);
        actionButton.setText(res.getString(title));
        summaryView.setText(res.getString(summary));
        actionButton.setOnClickListener(this);
        actionButton.setTag(new Integer(action));
    }

    private void fillMessagesSection(int uid) {
        boolean removeHeader = true;
        switch (AnonymousClass2.$SwitchMap$com$android$internal$os$BatterySipper$DrainType[this.mDrainType.ordinal()]) {
            case 6:
                addMessage(R.string.battery_msg_unaccounted);
                removeHeader = false;
                break;
        }
        if (removeHeader) {
            this.mMessagesParent.setVisibility(8);
        }
    }

    private void addMessage(int message) {
        Resources res = getResources();
        View item = getActivity().getLayoutInflater().inflate(R.layout.power_usage_message_item, null);
        this.mMessagesParent.addView(item);
        ((TextView) item.findViewById(R.id.message)).setText(res.getText(message));
    }

    private void removePackagesSection() {
        View view = this.mRootView.findViewById(R.id.packages_section_title);
        if (view != null) {
            view.setVisibility(8);
        }
        view = this.mRootView.findViewById(R.id.packages_section);
        if (view != null) {
            view.setVisibility(8);
        }
    }

    private void killProcesses() {
        if (this.mPackages != null) {
            ActivityManager am = (ActivityManager) getActivity().getSystemService("activity");
            int userId = UserHandle.getUserId(this.mUid);
            for (String forceStopPackageAsUser : this.mPackages) {
                am.forceStopPackageAsUser(forceStopPackageAsUser, userId);
            }
            checkForceStop();
        }
    }

    private void checkForceStop() {
        if (this.mPackages == null || this.mUid < 10000) {
            this.mForceStopButton.setEnabled(false);
            return;
        }
        for (String packageHasActiveAdmins : this.mPackages) {
            if (this.mDpm.packageHasActiveAdmins(packageHasActiveAdmins)) {
                this.mForceStopButton.setEnabled(false);
                return;
            }
        }
        int i = 0;
        while (i < this.mPackages.length) {
            try {
                if ((this.mPm.getApplicationInfo(this.mPackages[i], 0).flags & 2097152) == 0) {
                    this.mForceStopButton.setEnabled(true);
                    break;
                }
                i++;
            } catch (NameNotFoundException e) {
            }
        }
        Intent intent = new Intent("android.intent.action.QUERY_PACKAGE_RESTART", Uri.fromParts("package", this.mPackages[0], null));
        intent.putExtra("android.intent.extra.PACKAGES", this.mPackages);
        intent.putExtra("android.intent.extra.UID", this.mUid);
        intent.putExtra("android.intent.extra.user_handle", UserHandle.getUserId(this.mUid));
        getActivity().sendOrderedBroadcast(intent, null, this.mCheckKillProcessesReceiver, null, 0, null, null);
    }

    private void reportBatteryUse() {
        boolean z = false;
        if (this.mPackages != null) {
            ApplicationErrorReport report = new ApplicationErrorReport();
            report.type = 3;
            report.packageName = this.mPackages[0];
            report.installerPackageName = this.mInstaller.getPackageName();
            report.processName = this.mPackages[0];
            report.time = System.currentTimeMillis();
            if ((this.mApp.flags & 1) != 0) {
                z = true;
            }
            report.systemApp = z;
            Bundle args = getArguments();
            BatteryInfo batteryInfo = new BatteryInfo();
            batteryInfo.usagePercent = args.getInt("percent", 1);
            batteryInfo.durationMicros = args.getLong("duration", 0);
            batteryInfo.usageDetails = args.getString("report_details");
            batteryInfo.checkinDetails = args.getString("report_checkin_details");
            report.batteryInfo = batteryInfo;
            Intent result = new Intent("android.intent.action.APP_ERROR");
            result.setComponent(this.mInstaller);
            result.putExtra("android.intent.extra.BUG_REPORT", report);
            result.addFlags(268435456);
            startActivity(result);
        }
    }

    private void fillPackagesSection(int uid) {
        if (uid < 1) {
            removePackagesSection();
            return;
        }
        ViewGroup packagesParent = (ViewGroup) this.mRootView.findViewById(R.id.packages_section);
        if (packagesParent != null) {
            LayoutInflater inflater = getActivity().getLayoutInflater();
            PackageManager pm = getActivity().getPackageManager();
            this.mPackages = pm.getPackagesForUid(uid);
            if (this.mPackages == null || this.mPackages.length < 2) {
                removePackagesSection();
                return;
            }
            for (int i = 0; i < this.mPackages.length; i++) {
                try {
                    CharSequence label = pm.getApplicationInfo(this.mPackages[i], 0).loadLabel(pm);
                    if (label != null) {
                        this.mPackages[i] = label.toString();
                    }
                    View item = inflater.inflate(R.layout.power_usage_package_item, null);
                    packagesParent.addView(item);
                    ((TextView) item.findViewById(R.id.label)).setText(this.mPackages[i]);
                } catch (NameNotFoundException e) {
                }
            }
        }
    }

    private String getDescriptionForDrainType() {
        return getResources().getString(sDrainTypeDesciptions[this.mDrainType.ordinal()]);
    }
}
