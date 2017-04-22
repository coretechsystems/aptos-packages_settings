package com.android.settings.applications;

import android.app.ActivityManager;
import android.app.AlertDialog.Builder;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.app.INotificationManager;
import android.app.admin.DevicePolicyManager;
import android.appwidget.AppWidgetManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.IPackageDataObserver.Stub;
import android.content.pm.IPackageMoveObserver;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.hardware.usb.IUsbManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.UserHandle;
import android.os.UserManager;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.format.Formatter;
import android.text.style.BulletSpan;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.AppSecurityPermissions;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import com.android.internal.telephony.ISms;
import com.android.settings.R;
import com.android.settings.SettingsActivity;
import com.android.settings.Utils;
import com.android.settings.applications.ApplicationsState.AppEntry;
import com.android.settings.applications.ApplicationsState.Callbacks;
import com.android.settings.applications.ApplicationsState.Session;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public class InstalledAppDetails extends Fragment implements OnClickListener, OnCheckedChangeListener, Callbacks {
    private Button mActivitiesButton;
    private boolean mAppControlRestricted = false;
    private AppEntry mAppEntry;
    private TextView mAppSize;
    private TextView mAppVersion;
    private AppWidgetManager mAppWidgetManager;
    private CheckBox mAskCompatibilityCB;
    private TextView mCacheSize;
    private CanBeOnSdCardChecker mCanBeOnSdCardChecker;
    private boolean mCanClearData = true;
    private final BroadcastReceiver mCheckKillProcessesReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            InstalledAppDetails.this.updateForceStopButton(getResultCode() != 0);
        }
    };
    private Button mClearCacheButton;
    private ClearCacheObserver mClearCacheObserver;
    private Button mClearDataButton;
    private ClearUserDataObserver mClearDataObserver;
    private CharSequence mComputingStr;
    private TextView mDataSize;
    private boolean mDisableAfterUninstall;
    private DevicePolicyManager mDpm;
    private CheckBox mEnableCompatibilityCB;
    private TextView mExternalCodeSize;
    private TextView mExternalDataSize;
    private Button mForceStopButton;
    private Handler mHandler = new Handler() {
        public void handleMessage(Message msg) {
            if (InstalledAppDetails.this.getView() != null) {
                switch (msg.what) {
                    case 1:
                        InstalledAppDetails.this.processClearMsg(msg);
                        return;
                    case 3:
                        InstalledAppDetails.this.mState.requestSize(InstalledAppDetails.this.mAppEntry.info.packageName);
                        return;
                    case 4:
                        InstalledAppDetails.this.processMoveMsg(msg);
                        return;
                    default:
                        return;
                }
            }
        }
    };
    private boolean mHaveSizes = false;
    private final HashSet<String> mHomePackages = new HashSet();
    private boolean mInitialized;
    private CharSequence mInvalidSizeStr;
    private long mLastCacheSize = -1;
    private long mLastCodeSize = -1;
    private long mLastDataSize = -1;
    private long mLastExternalCodeSize = -1;
    private long mLastExternalDataSize = -1;
    private long mLastTotalSize = -1;
    private View mMoreControlButtons;
    private Button mMoveAppButton;
    private boolean mMoveInProgress = false;
    private CompoundButton mNotificationSwitch;
    private PackageInfo mPackageInfo;
    private PackageMoveObserver mPackageMoveObserver;
    private PackageManager mPm;
    private View mRootView;
    private View mScreenCompatSection;
    private Session mSession;
    private boolean mShowUninstalled;
    private ISms mSmsManager;
    private Button mSpecialDisableButton;
    private ApplicationsState mState;
    private TextView mTotalSize;
    private Button mUninstallButton;
    private boolean mUpdatedSysApp = false;
    private IUsbManager mUsbManager;
    private UserManager mUserManager;

    class ClearCacheObserver extends Stub {
        ClearCacheObserver() {
        }

        public void onRemoveCompleted(String packageName, boolean succeeded) {
            Message msg = InstalledAppDetails.this.mHandler.obtainMessage(3);
            msg.arg1 = succeeded ? 1 : 2;
            InstalledAppDetails.this.mHandler.sendMessage(msg);
        }
    }

    class ClearUserDataObserver extends Stub {
        ClearUserDataObserver() {
        }

        public void onRemoveCompleted(String packageName, boolean succeeded) {
            int i = 1;
            Message msg = InstalledAppDetails.this.mHandler.obtainMessage(1);
            if (!succeeded) {
                i = 2;
            }
            msg.arg1 = i;
            InstalledAppDetails.this.mHandler.sendMessage(msg);
        }
    }

    static class DisableChanger extends AsyncTask<Object, Object, Object> {
        final WeakReference<InstalledAppDetails> mActivity;
        final ApplicationInfo mInfo;
        final PackageManager mPm;
        final int mState;

        DisableChanger(InstalledAppDetails activity, ApplicationInfo info, int state) {
            this.mPm = activity.mPm;
            this.mActivity = new WeakReference(activity);
            this.mInfo = info;
            this.mState = state;
        }

        protected Object doInBackground(Object... params) {
            this.mPm.setApplicationEnabledSetting(this.mInfo.packageName, this.mState, 0);
            return null;
        }
    }

    public static class MyAlertDialogFragment extends DialogFragment {
        public static MyAlertDialogFragment newInstance(int id, int moveErrorCode) {
            MyAlertDialogFragment frag = new MyAlertDialogFragment();
            Bundle args = new Bundle();
            args.putInt("id", id);
            args.putInt("moveError", moveErrorCode);
            frag.setArguments(args);
            return frag;
        }

        InstalledAppDetails getOwner() {
            return (InstalledAppDetails) getTargetFragment();
        }

        public Dialog onCreateDialog(Bundle savedInstanceState) {
            int id = getArguments().getInt("id");
            int moveErrorCode = getArguments().getInt("moveError");
            switch (id) {
                case 1:
                    return new Builder(getActivity()).setTitle(getActivity().getText(R.string.clear_data_dlg_title)).setMessage(getActivity().getText(R.string.clear_data_dlg_text)).setPositiveButton(R.string.dlg_ok, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            MyAlertDialogFragment.this.getOwner().initiateClearUserData();
                        }
                    }).setNegativeButton(R.string.dlg_cancel, null).create();
                case 2:
                    return new Builder(getActivity()).setTitle(getActivity().getText(R.string.app_factory_reset_dlg_title)).setMessage(getActivity().getText(R.string.app_factory_reset_dlg_text)).setPositiveButton(R.string.dlg_ok, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            MyAlertDialogFragment.this.getOwner().uninstallPkg(MyAlertDialogFragment.this.getOwner().mAppEntry.info.packageName, false, false);
                        }
                    }).setNegativeButton(R.string.dlg_cancel, null).create();
                case 3:
                    return new Builder(getActivity()).setTitle(getActivity().getText(R.string.app_not_found_dlg_title)).setMessage(getActivity().getText(R.string.app_not_found_dlg_title)).setNeutralButton(getActivity().getText(R.string.dlg_ok), new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            MyAlertDialogFragment.this.getOwner().setIntentAndFinish(true, true);
                        }
                    }).create();
                case 4:
                    return new Builder(getActivity()).setTitle(getActivity().getText(R.string.clear_failed_dlg_title)).setMessage(getActivity().getText(R.string.clear_failed_dlg_text)).setNeutralButton(R.string.dlg_ok, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            MyAlertDialogFragment.this.getOwner().mClearDataButton.setEnabled(false);
                            MyAlertDialogFragment.this.getOwner().setIntentAndFinish(false, false);
                        }
                    }).create();
                case 5:
                    return new Builder(getActivity()).setTitle(getActivity().getText(R.string.force_stop_dlg_title)).setMessage(getActivity().getText(R.string.force_stop_dlg_text)).setPositiveButton(R.string.dlg_ok, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            MyAlertDialogFragment.this.getOwner().forceStopPackage(MyAlertDialogFragment.this.getOwner().mAppEntry.info.packageName);
                        }
                    }).setNegativeButton(R.string.dlg_cancel, null).create();
                case 6:
                    return new Builder(getActivity()).setTitle(getActivity().getText(R.string.move_app_failed_dlg_title)).setMessage(getActivity().getString(R.string.move_app_failed_dlg_text, new Object[]{getOwner().getMoveErrMsg(moveErrorCode)})).setNeutralButton(R.string.dlg_ok, null).create();
                case 7:
                    return new Builder(getActivity()).setTitle(getActivity().getText(R.string.app_disable_dlg_title)).setMessage(getActivity().getText(R.string.app_disable_dlg_text)).setPositiveButton(R.string.dlg_ok, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            new DisableChanger(MyAlertDialogFragment.this.getOwner(), MyAlertDialogFragment.this.getOwner().mAppEntry.info, 3).execute(new Object[]{null});
                        }
                    }).setNegativeButton(R.string.dlg_cancel, null).create();
                case 8:
                    return new Builder(getActivity()).setTitle(getActivity().getText(R.string.app_disable_notifications_dlg_title)).setMessage(getActivity().getText(R.string.app_disable_notifications_dlg_text)).setPositiveButton(R.string.dlg_ok, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            MyAlertDialogFragment.this.getOwner().setNotificationsEnabled(false);
                        }
                    }).setNegativeButton(R.string.dlg_cancel, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            MyAlertDialogFragment.this.getOwner().mNotificationSwitch.setChecked(true);
                        }
                    }).create();
                case 9:
                    return new Builder(getActivity()).setTitle(getActivity().getText(R.string.app_special_disable_dlg_title)).setMessage(getActivity().getText(R.string.app_special_disable_dlg_text)).setPositiveButton(R.string.dlg_ok, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            MyAlertDialogFragment.this.getOwner().uninstallPkg(MyAlertDialogFragment.this.getOwner().mAppEntry.info.packageName, false, true);
                        }
                    }).setNegativeButton(R.string.dlg_cancel, null).create();
                default:
                    throw new IllegalArgumentException("unknown id " + id);
            }
        }
    }

    class PackageMoveObserver extends IPackageMoveObserver.Stub {
        PackageMoveObserver() {
        }

        public void packageMoved(String packageName, int returnCode) throws RemoteException {
            Message msg = InstalledAppDetails.this.mHandler.obtainMessage(4);
            msg.arg1 = returnCode;
            InstalledAppDetails.this.mHandler.sendMessage(msg);
        }
    }

    private static class PremiumSmsSelectionListener implements OnItemSelectedListener {
        private final String mPackageName;
        private final ISms mSmsManager;

        PremiumSmsSelectionListener(String packageName, ISms smsManager) {
            this.mPackageName = packageName;
            this.mSmsManager = smsManager;
        }

        public void onItemSelected(AdapterView<?> adapterView, View view, int position, long id) {
            if (position < 0 || position >= 3) {
                Log.e("InstalledAppDetails", "Error: unknown premium SMS policy " + position);
                return;
            }
            Log.d("InstalledAppDetails", "Selected premium SMS policy " + position);
            setPremiumSmsPermission(this.mPackageName, position + 1);
        }

        public void onNothingSelected(AdapterView<?> adapterView) {
        }

        private void setPremiumSmsPermission(String packageName, int permission) {
            try {
                if (this.mSmsManager != null) {
                    this.mSmsManager.setPremiumSmsPermission(packageName, permission);
                }
            } catch (RemoteException e) {
            }
        }
    }

    private String getSizeStr(long size) {
        if (size == -1) {
            return this.mInvalidSizeStr.toString();
        }
        return Formatter.formatFileSize(getActivity(), size);
    }

    private void initDataButtons() {
        if (this.mAppEntry.info.manageSpaceActivityName == null && ((this.mAppEntry.info.flags & 65) == 1 || this.mDpm.packageHasActiveAdmins(this.mPackageInfo.packageName))) {
            this.mClearDataButton.setText(R.string.clear_user_data_text);
            this.mClearDataButton.setEnabled(false);
            this.mCanClearData = false;
        } else {
            if (this.mAppEntry.info.manageSpaceActivityName != null) {
                this.mClearDataButton.setText(R.string.manage_space_text);
            } else {
                this.mClearDataButton.setText(R.string.clear_user_data_text);
            }
            this.mClearDataButton.setOnClickListener(this);
        }
        if (this.mAppControlRestricted) {
            this.mClearDataButton.setEnabled(false);
        }
    }

    private CharSequence getMoveErrMsg(int errCode) {
        switch (errCode) {
            case -6:
                return "";
            case -5:
                return getActivity().getString(R.string.invalid_location);
            case -4:
                return getActivity().getString(R.string.app_forward_locked);
            case -3:
                return getActivity().getString(R.string.system_package);
            case -2:
                return getActivity().getString(R.string.does_not_exist);
            case -1:
                return getActivity().getString(R.string.insufficient_storage);
            default:
                return "";
        }
    }

    private void initMoveButton() {
        if (Environment.isExternalStorageEmulated()) {
            this.mMoveAppButton.setVisibility(4);
            return;
        }
        boolean dataOnly;
        if (this.mPackageInfo != null || this.mAppEntry == null) {
            dataOnly = false;
        } else {
            dataOnly = true;
        }
        boolean moveDisable = true;
        if (dataOnly) {
            this.mMoveAppButton.setText(R.string.move_app);
        } else if ((this.mAppEntry.info.flags & 262144) != 0) {
            this.mMoveAppButton.setText(R.string.move_app_to_internal);
            moveDisable = false;
        } else {
            this.mMoveAppButton.setText(R.string.move_app_to_sdcard);
            this.mCanBeOnSdCardChecker.init();
            moveDisable = !this.mCanBeOnSdCardChecker.check(this.mAppEntry.info);
        }
        if (moveDisable || this.mAppControlRestricted) {
            this.mMoveAppButton.setEnabled(false);
            return;
        }
        this.mMoveAppButton.setOnClickListener(this);
        this.mMoveAppButton.setEnabled(true);
    }

    private boolean handleDisableable(Button button) {
        if (this.mHomePackages.contains(this.mAppEntry.info.packageName) || Utils.isSystemPackage(this.mPm, this.mPackageInfo)) {
            button.setText(R.string.disable_text);
            return false;
        } else if (this.mAppEntry.info.enabled) {
            button.setText(R.string.disable_text);
            return true;
        } else {
            button.setText(R.string.enable_text);
            return true;
        }
    }

    private void initUninstallButtons() {
        boolean z;
        boolean isBundled;
        if ((this.mAppEntry.info.flags & 128) != 0) {
            z = true;
        } else {
            z = false;
        }
        this.mUpdatedSysApp = z;
        if ((this.mAppEntry.info.flags & 1) != 0) {
            isBundled = true;
        } else {
            isBundled = false;
        }
        boolean enabled = true;
        if (this.mUpdatedSysApp) {
            int i;
            this.mUninstallButton.setText(R.string.app_factory_reset);
            boolean showSpecialDisable = false;
            if (isBundled) {
                showSpecialDisable = handleDisableable(this.mSpecialDisableButton);
                this.mSpecialDisableButton.setOnClickListener(this);
            }
            if (this.mAppControlRestricted) {
                showSpecialDisable = false;
            }
            View view = this.mMoreControlButtons;
            if (showSpecialDisable) {
                i = 0;
            } else {
                i = 8;
            }
            view.setVisibility(i);
        } else {
            this.mMoreControlButtons.setVisibility(8);
            if (isBundled) {
                enabled = handleDisableable(this.mUninstallButton);
            } else if ((this.mPackageInfo.applicationInfo.flags & 8388608) != 0 || this.mUserManager.getUsers().size() < 2) {
                this.mUninstallButton.setText(R.string.uninstall_text);
            } else {
                this.mUninstallButton.setText(R.string.uninstall_text);
                enabled = false;
            }
        }
        if (this.mDpm.packageHasActiveAdmins(this.mPackageInfo.packageName)) {
            enabled = false;
        }
        if (enabled && this.mHomePackages.contains(this.mPackageInfo.packageName)) {
            if (isBundled) {
                enabled = false;
            } else {
                ComponentName currentDefaultHome = this.mPm.getHomeActivities(new ArrayList());
                if (currentDefaultHome == null) {
                    enabled = this.mHomePackages.size() > 1;
                } else {
                    enabled = !this.mPackageInfo.packageName.equals(currentDefaultHome.getPackageName());
                }
            }
        }
        if (this.mAppControlRestricted) {
            enabled = false;
        }
        this.mUninstallButton.setEnabled(enabled);
        if (enabled) {
            this.mUninstallButton.setOnClickListener(this);
        }
    }

    private void initNotificationButton() {
        boolean enabled = true;
        try {
            enabled = INotificationManager.Stub.asInterface(ServiceManager.getService("notification")).areNotificationsEnabledForPackage(this.mAppEntry.info.packageName, this.mAppEntry.info.uid);
        } catch (RemoteException e) {
        }
        this.mNotificationSwitch.setChecked(enabled);
        if (Utils.isSystemPackage(this.mPm, this.mPackageInfo)) {
            this.mNotificationSwitch.setEnabled(false);
            return;
        }
        this.mNotificationSwitch.setEnabled(true);
        this.mNotificationSwitch.setOnCheckedChangeListener(this);
    }

    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        this.mState = ApplicationsState.getInstance(getActivity().getApplication());
        this.mSession = this.mState.newSession(this);
        this.mPm = getActivity().getPackageManager();
        this.mUserManager = (UserManager) getActivity().getSystemService("user");
        this.mUsbManager = IUsbManager.Stub.asInterface(ServiceManager.getService("usb"));
        this.mAppWidgetManager = AppWidgetManager.getInstance(getActivity());
        this.mDpm = (DevicePolicyManager) getActivity().getSystemService("device_policy");
        this.mSmsManager = ISms.Stub.asInterface(ServiceManager.getService("isms"));
        this.mCanBeOnSdCardChecker = new CanBeOnSdCardChecker();
        this.mSession.resume();
        retrieveAppEntry();
        setHasOptionsMenu(true);
    }

    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.installed_app_details, container, false);
        Utils.forceCustomPadding((ViewGroup) view.findViewById(R.id.all_details), true);
        this.mRootView = view;
        this.mComputingStr = getActivity().getText(R.string.computing_size);
        this.mTotalSize = (TextView) view.findViewById(R.id.total_size_text);
        this.mAppSize = (TextView) view.findViewById(R.id.application_size_text);
        this.mDataSize = (TextView) view.findViewById(R.id.data_size_text);
        this.mExternalCodeSize = (TextView) view.findViewById(R.id.external_code_size_text);
        this.mExternalDataSize = (TextView) view.findViewById(R.id.external_data_size_text);
        if (Environment.isExternalStorageEmulated()) {
            ((View) this.mExternalCodeSize.getParent()).setVisibility(8);
            ((View) this.mExternalDataSize.getParent()).setVisibility(8);
        }
        View btnPanel = view.findViewById(R.id.control_buttons_panel);
        this.mForceStopButton = (Button) btnPanel.findViewById(R.id.left_button);
        this.mForceStopButton.setText(R.string.force_stop);
        this.mUninstallButton = (Button) btnPanel.findViewById(R.id.right_button);
        this.mForceStopButton.setEnabled(false);
        this.mMoreControlButtons = view.findViewById(R.id.more_control_buttons_panel);
        this.mMoreControlButtons.findViewById(R.id.left_button).setVisibility(4);
        this.mSpecialDisableButton = (Button) this.mMoreControlButtons.findViewById(R.id.right_button);
        this.mMoreControlButtons.setVisibility(8);
        View data_buttons_panel = view.findViewById(R.id.data_buttons_panel);
        this.mClearDataButton = (Button) data_buttons_panel.findViewById(R.id.right_button);
        this.mMoveAppButton = (Button) data_buttons_panel.findViewById(R.id.left_button);
        this.mCacheSize = (TextView) view.findViewById(R.id.cache_size_text);
        this.mClearCacheButton = (Button) view.findViewById(R.id.clear_cache_button);
        this.mActivitiesButton = (Button) view.findViewById(R.id.clear_activities_button);
        this.mScreenCompatSection = view.findViewById(R.id.screen_compatibility_section);
        this.mAskCompatibilityCB = (CheckBox) view.findViewById(R.id.ask_compatibility_cb);
        this.mEnableCompatibilityCB = (CheckBox) view.findViewById(R.id.enable_compatibility_cb);
        this.mNotificationSwitch = (CompoundButton) view.findViewById(R.id.notification_switch);
        return view;
    }

    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        menu.add(0, 1, 1, R.string.uninstall_all_users_text).setShowAsAction(0);
    }

    public void onPrepareOptionsMenu(Menu menu) {
        boolean showIt = true;
        if (this.mUpdatedSysApp) {
            showIt = false;
        } else if (this.mAppEntry == null) {
            showIt = false;
        } else if ((this.mAppEntry.info.flags & 1) != 0) {
            showIt = false;
        } else if (this.mPackageInfo == null || this.mDpm.packageHasActiveAdmins(this.mPackageInfo.packageName)) {
            showIt = false;
        } else if (UserHandle.myUserId() != 0) {
            showIt = false;
        } else if (this.mUserManager.getUsers().size() < 2) {
            showIt = false;
        }
        menu.findItem(1).setVisible(showIt);
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() != 1) {
            return false;
        }
        uninstallPkg(this.mAppEntry.info.packageName, true, false);
        return true;
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1) {
            if (this.mDisableAfterUninstall) {
                this.mDisableAfterUninstall = false;
                try {
                    if ((getActivity().getPackageManager().getApplicationInfo(this.mAppEntry.info.packageName, 8704).flags & 128) == 0) {
                        new DisableChanger(this, this.mAppEntry.info, 3).execute(new Object[]{null});
                    }
                } catch (NameNotFoundException e) {
                }
            }
            if (!refreshUi()) {
                setIntentAndFinish(true, true);
            }
        }
    }

    private void setAppLabelAndIcon(PackageInfo pkgInfo) {
        View appSnippet = this.mRootView.findViewById(R.id.app_snippet);
        appSnippet.setPaddingRelative(0, appSnippet.getPaddingTop(), 0, appSnippet.getPaddingBottom());
        ImageView icon = (ImageView) appSnippet.findViewById(R.id.app_icon);
        this.mState.ensureIcon(this.mAppEntry);
        icon.setImageDrawable(this.mAppEntry.icon);
        ((TextView) appSnippet.findViewById(R.id.app_name)).setText(this.mAppEntry.label);
        this.mAppVersion = (TextView) appSnippet.findViewById(R.id.app_size);
        if (pkgInfo == null || pkgInfo.versionName == null) {
            this.mAppVersion.setVisibility(4);
            return;
        }
        this.mAppVersion.setVisibility(0);
        this.mAppVersion.setText(getActivity().getString(R.string.version_text, new Object[]{String.valueOf(pkgInfo.versionName)}));
    }

    public void onResume() {
        super.onResume();
        this.mAppControlRestricted = this.mUserManager.hasUserRestriction("no_control_apps");
        this.mSession.resume();
        if (!refreshUi()) {
            setIntentAndFinish(true, true);
        }
    }

    public void onPause() {
        super.onPause();
        this.mSession.pause();
    }

    public void onDestroyView() {
        super.onDestroyView();
        this.mSession.release();
    }

    public void onAllSizesComputed() {
    }

    public void onPackageIconChanged() {
    }

    public void onPackageListChanged() {
        refreshUi();
    }

    public void onRebuildComplete(ArrayList<AppEntry> arrayList) {
    }

    public void onPackageSizeChanged(String packageName) {
        if (packageName.equals(this.mAppEntry.info.packageName)) {
            refreshSizeInfo();
        }
    }

    public void onRunningStateChanged(boolean running) {
    }

    private String retrieveAppEntry() {
        String packageName;
        Bundle args = getArguments();
        if (args != null) {
            packageName = args.getString("package");
        } else {
            packageName = null;
        }
        if (packageName == null) {
            Intent intent;
            if (args == null) {
                intent = getActivity().getIntent();
            } else {
                intent = (Intent) args.getParcelable("intent");
            }
            if (intent != null) {
                packageName = intent.getData().getSchemeSpecificPart();
            }
        }
        this.mAppEntry = this.mState.getEntry(packageName);
        if (this.mAppEntry != null) {
            try {
                this.mPackageInfo = this.mPm.getPackageInfo(this.mAppEntry.info.packageName, 8768);
            } catch (NameNotFoundException e) {
                Log.e("InstalledAppDetails", "Exception when retrieving package:" + this.mAppEntry.info.packageName, e);
            }
        } else {
            Log.w("InstalledAppDetails", "Missing AppEntry; maybe reinstalling?");
            this.mPackageInfo = null;
        }
        return packageName;
    }

    private boolean signaturesMatch(String pkg1, String pkg2) {
        if (!(pkg1 == null || pkg2 == null)) {
            try {
                if (this.mPm.checkSignatures(pkg1, pkg2) >= 0) {
                    return true;
                }
            } catch (Exception e) {
            }
        }
        return false;
    }

    private boolean refreshUi() {
        if (this.mMoveInProgress) {
            return true;
        }
        String packageName = retrieveAppEntry();
        if (this.mAppEntry == null) {
            return false;
        }
        if (this.mPackageInfo == null) {
            return false;
        }
        int i;
        List<ResolveInfo> homeActivities = new ArrayList();
        this.mPm.getHomeActivities(homeActivities);
        this.mHomePackages.clear();
        for (i = 0; i < homeActivities.size(); i++) {
            ResolveInfo ri = (ResolveInfo) homeActivities.get(i);
            String activityPkg = ri.activityInfo.packageName;
            this.mHomePackages.add(activityPkg);
            Bundle metadata = ri.activityInfo.metaData;
            if (metadata != null) {
                String metaPkg = metadata.getString("android.app.home.alternate");
                if (signaturesMatch(metaPkg, activityPkg)) {
                    this.mHomePackages.add(metaPkg);
                }
            }
        }
        List<ComponentName> prefActList = new ArrayList();
        this.mPm.getPreferredActivities(new ArrayList(), prefActList, packageName);
        boolean hasUsbDefaults = false;
        try {
            hasUsbDefaults = this.mUsbManager.hasDefaults(packageName, UserHandle.myUserId());
        } catch (Throwable e) {
            Log.e("InstalledAppDetails", "mUsbManager.hasDefaults", e);
        }
        boolean hasBindAppWidgetPermission = this.mAppWidgetManager.hasBindAppWidgetPermission(this.mAppEntry.info.packageName);
        TextView autoLaunchTitleView = (TextView) this.mRootView.findViewById(R.id.auto_launch_title);
        TextView autoLaunchView = (TextView) this.mRootView.findViewById(R.id.auto_launch);
        boolean autoLaunchEnabled = prefActList.size() > 0 || hasUsbDefaults;
        if (autoLaunchEnabled || hasBindAppWidgetPermission) {
            SpannableString spannableString;
            boolean useBullets = hasBindAppWidgetPermission && autoLaunchEnabled;
            if (hasBindAppWidgetPermission) {
                autoLaunchTitleView.setText(R.string.auto_launch_label_generic);
            } else {
                autoLaunchTitleView.setText(R.string.auto_launch_label);
            }
            CharSequence text = null;
            int bulletIndent = getResources().getDimensionPixelSize(R.dimen.installed_app_details_bullet_offset);
            if (autoLaunchEnabled) {
                CharSequence autoLaunchEnableText = getText(R.string.auto_launch_enable_text);
                spannableString = new SpannableString(autoLaunchEnableText);
                if (useBullets) {
                    spannableString.setSpan(new BulletSpan(bulletIndent), 0, autoLaunchEnableText.length(), 0);
                }
                if (null == null) {
                    text = TextUtils.concat(new CharSequence[]{spannableString, "\n"});
                } else {
                    text = TextUtils.concat(new CharSequence[]{null, "\n", spannableString, "\n"});
                }
            }
            if (hasBindAppWidgetPermission) {
                CharSequence alwaysAllowBindAppWidgetsText = getText(R.string.always_allow_bind_appwidgets_text);
                spannableString = new SpannableString(alwaysAllowBindAppWidgetsText);
                if (useBullets) {
                    spannableString.setSpan(new BulletSpan(bulletIndent), 0, alwaysAllowBindAppWidgetsText.length(), 0);
                }
                if (text == null) {
                    text = TextUtils.concat(new CharSequence[]{spannableString, "\n"});
                } else {
                    text = TextUtils.concat(new CharSequence[]{text, "\n", spannableString, "\n"});
                }
            }
            autoLaunchView.setText(text);
            this.mActivitiesButton.setEnabled(true);
            this.mActivitiesButton.setOnClickListener(this);
        } else {
            resetLaunchDefaultsUi(autoLaunchTitleView, autoLaunchView);
        }
        int compatMode = ((ActivityManager) getActivity().getSystemService("activity")).getPackageScreenCompatMode(packageName);
        this.mScreenCompatSection.setVisibility(8);
        LinearLayout permsView = (LinearLayout) this.mRootView.findViewById(R.id.permissions_section);
        AppSecurityPermissions asp = new AppSecurityPermissions(getActivity(), packageName);
        int premiumSmsPermission = getPremiumSmsPermission(packageName);
        if (asp.getPermissionCount() > 0 || premiumSmsPermission != 0) {
            permsView.setVisibility(0);
        } else {
            permsView.setVisibility(8);
        }
        TextView securityBillingDesc = (TextView) permsView.findViewById(R.id.security_settings_billing_desc);
        LinearLayout securityBillingList = (LinearLayout) permsView.findViewById(R.id.security_settings_billing_list);
        if (premiumSmsPermission != 0) {
            securityBillingDesc.setVisibility(0);
            securityBillingList.setVisibility(0);
            Spinner spinner = (Spinner) permsView.findViewById(R.id.security_settings_premium_sms_list);
            ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(getActivity(), R.array.security_settings_premium_sms_values, 17367048);
            adapter.setDropDownViewResource(17367049);
            spinner.setAdapter(adapter);
            spinner.setSelection(premiumSmsPermission - 1);
            spinner.setOnItemSelectedListener(new PremiumSmsSelectionListener(packageName, this.mSmsManager));
        } else {
            securityBillingDesc.setVisibility(8);
            securityBillingList.setVisibility(8);
        }
        if (asp.getPermissionCount() > 0) {
            LinearLayout securityList = (LinearLayout) permsView.findViewById(R.id.security_settings_list);
            securityList.removeAllViews();
            securityList.addView(asp.getPermissionsViewWithRevokeButtons());
            String[] packages = this.mPm.getPackagesForUid(this.mPackageInfo.applicationInfo.uid);
            if (packages != null && packages.length > 1) {
                ArrayList<CharSequence> pnames = new ArrayList();
                for (String pkg : packages) {
                    if (!this.mPackageInfo.packageName.equals(pkg)) {
                        try {
                            pnames.add(this.mPm.getApplicationInfo(pkg, 0).loadLabel(this.mPm));
                        } catch (NameNotFoundException e2) {
                        }
                    }
                }
                int N = pnames.size();
                if (N > 0) {
                    String appListStr;
                    Resources res = getActivity().getResources();
                    if (N == 1) {
                        appListStr = ((CharSequence) pnames.get(0)).toString();
                    } else if (N == 2) {
                        appListStr = res.getString(R.string.join_two_items, new Object[]{pnames.get(0), pnames.get(1)});
                    } else {
                        appListStr = ((CharSequence) pnames.get(N - 2)).toString();
                        i = N - 3;
                        while (i >= 0) {
                            appListStr = res.getString(i == 0 ? R.string.join_many_items_first : R.string.join_many_items_middle, new Object[]{pnames.get(i), appListStr});
                            i--;
                        }
                        appListStr = res.getString(R.string.join_many_items_last, new Object[]{appListStr, pnames.get(N - 1)});
                    }
                    ((TextView) this.mRootView.findViewById(R.id.security_settings_desc)).setText(res.getString(R.string.security_settings_desc_multi, new Object[]{this.mPackageInfo.applicationInfo.loadLabel(this.mPm), appListStr}));
                }
            }
        }
        checkForceStop();
        setAppLabelAndIcon(this.mPackageInfo);
        refreshButtons();
        refreshSizeInfo();
        if (this.mInitialized) {
            try {
                ApplicationInfo ainfo = getActivity().getPackageManager().getApplicationInfo(this.mAppEntry.info.packageName, 8704);
                if (!this.mShowUninstalled) {
                    return (ainfo.flags & 8388608) != 0;
                }
            } catch (NameNotFoundException e3) {
                return false;
            }
        }
        boolean z;
        this.mInitialized = true;
        if ((this.mAppEntry.info.flags & 8388608) == 0) {
            z = true;
        } else {
            z = false;
        }
        this.mShowUninstalled = z;
        return true;
    }

    private void resetLaunchDefaultsUi(TextView title, TextView autoLaunchView) {
        title.setText(R.string.auto_launch_label);
        autoLaunchView.setText(R.string.auto_launch_disable_text);
        this.mActivitiesButton.setEnabled(false);
    }

    private void setIntentAndFinish(boolean finish, boolean appChanged) {
        Intent intent = new Intent();
        intent.putExtra("chg", appChanged);
        ((SettingsActivity) getActivity()).finishPreferencePanel(this, -1, intent);
    }

    private void refreshSizeInfo() {
        if (this.mAppEntry.size == -2 || this.mAppEntry.size == -1) {
            this.mLastTotalSize = -1;
            this.mLastCacheSize = -1;
            this.mLastDataSize = -1;
            this.mLastCodeSize = -1;
            if (!this.mHaveSizes) {
                this.mAppSize.setText(this.mComputingStr);
                this.mDataSize.setText(this.mComputingStr);
                this.mCacheSize.setText(this.mComputingStr);
                this.mTotalSize.setText(this.mComputingStr);
            }
            this.mClearDataButton.setEnabled(false);
            this.mClearCacheButton.setEnabled(false);
        } else {
            this.mHaveSizes = true;
            long codeSize = this.mAppEntry.codeSize;
            long dataSize = this.mAppEntry.dataSize;
            if (Environment.isExternalStorageEmulated()) {
                codeSize += this.mAppEntry.externalCodeSize;
                dataSize += this.mAppEntry.externalDataSize;
            } else {
                if (this.mLastExternalCodeSize != this.mAppEntry.externalCodeSize) {
                    this.mLastExternalCodeSize = this.mAppEntry.externalCodeSize;
                    this.mExternalCodeSize.setText(getSizeStr(this.mAppEntry.externalCodeSize));
                }
                if (this.mLastExternalDataSize != this.mAppEntry.externalDataSize) {
                    this.mLastExternalDataSize = this.mAppEntry.externalDataSize;
                    this.mExternalDataSize.setText(getSizeStr(this.mAppEntry.externalDataSize));
                }
            }
            if (this.mLastCodeSize != codeSize) {
                this.mLastCodeSize = codeSize;
                this.mAppSize.setText(getSizeStr(codeSize));
            }
            if (this.mLastDataSize != dataSize) {
                this.mLastDataSize = dataSize;
                this.mDataSize.setText(getSizeStr(dataSize));
            }
            long cacheSize = this.mAppEntry.cacheSize + this.mAppEntry.externalCacheSize;
            if (this.mLastCacheSize != cacheSize) {
                this.mLastCacheSize = cacheSize;
                this.mCacheSize.setText(getSizeStr(cacheSize));
            }
            if (this.mLastTotalSize != this.mAppEntry.size) {
                this.mLastTotalSize = this.mAppEntry.size;
                this.mTotalSize.setText(getSizeStr(this.mAppEntry.size));
            }
            if (this.mAppEntry.dataSize + this.mAppEntry.externalDataSize <= 0 || !this.mCanClearData) {
                this.mClearDataButton.setEnabled(false);
            } else {
                this.mClearDataButton.setEnabled(true);
                this.mClearDataButton.setOnClickListener(this);
            }
            if (cacheSize <= 0) {
                this.mClearCacheButton.setEnabled(false);
            } else {
                this.mClearCacheButton.setEnabled(true);
                this.mClearCacheButton.setOnClickListener(this);
            }
        }
        if (this.mAppControlRestricted) {
            this.mClearCacheButton.setEnabled(false);
            this.mClearDataButton.setEnabled(false);
        }
    }

    private void processClearMsg(Message msg) {
        int result = msg.arg1;
        String packageName = this.mAppEntry.info.packageName;
        this.mClearDataButton.setText(R.string.clear_user_data_text);
        if (result == 1) {
            Log.i("InstalledAppDetails", "Cleared user data for package : " + packageName);
            this.mState.requestSize(this.mAppEntry.info.packageName);
        } else {
            this.mClearDataButton.setEnabled(true);
        }
        checkForceStop();
    }

    private void refreshButtons() {
        if (this.mMoveInProgress) {
            this.mMoveAppButton.setText(R.string.moving);
            this.mMoveAppButton.setEnabled(false);
            this.mUninstallButton.setEnabled(false);
            this.mSpecialDisableButton.setEnabled(false);
            return;
        }
        initUninstallButtons();
        initDataButtons();
        initMoveButton();
        initNotificationButton();
    }

    private void processMoveMsg(Message msg) {
        int result = msg.arg1;
        String packageName = this.mAppEntry.info.packageName;
        this.mMoveInProgress = false;
        if (result == 1) {
            Log.i("InstalledAppDetails", "Moved resources for " + packageName);
            this.mState.requestSize(this.mAppEntry.info.packageName);
        } else {
            showDialogInner(6, result);
        }
        refreshUi();
    }

    private void initiateClearUserData() {
        this.mClearDataButton.setEnabled(false);
        String packageName = this.mAppEntry.info.packageName;
        Log.i("InstalledAppDetails", "Clearing user data for package : " + packageName);
        if (this.mClearDataObserver == null) {
            this.mClearDataObserver = new ClearUserDataObserver();
        }
        if (((ActivityManager) getActivity().getSystemService("activity")).clearApplicationUserData(packageName, this.mClearDataObserver)) {
            this.mClearDataButton.setText(R.string.recompute_size);
            return;
        }
        Log.i("InstalledAppDetails", "Couldnt clear application user data for package:" + packageName);
        showDialogInner(4, 0);
    }

    private void showDialogInner(int id, int moveErrorCode) {
        DialogFragment newFragment = MyAlertDialogFragment.newInstance(id, moveErrorCode);
        newFragment.setTargetFragment(this, 0);
        newFragment.show(getFragmentManager(), "dialog " + id);
    }

    private void uninstallPkg(String packageName, boolean allUsers, boolean andDisable) {
        Intent uninstallIntent = new Intent("android.intent.action.UNINSTALL_PACKAGE", Uri.parse("package:" + packageName));
        uninstallIntent.putExtra("android.intent.extra.UNINSTALL_ALL_USERS", allUsers);
        startActivityForResult(uninstallIntent, 1);
        this.mDisableAfterUninstall = andDisable;
    }

    private void forceStopPackage(String pkgName) {
        ((ActivityManager) getActivity().getSystemService("activity")).forceStopPackage(pkgName);
        this.mState.invalidatePackage(pkgName);
        AppEntry newEnt = this.mState.getEntry(pkgName);
        if (newEnt != null) {
            this.mAppEntry = newEnt;
        }
        checkForceStop();
    }

    private void updateForceStopButton(boolean enabled) {
        if (this.mAppControlRestricted) {
            this.mForceStopButton.setEnabled(false);
            return;
        }
        this.mForceStopButton.setEnabled(enabled);
        this.mForceStopButton.setOnClickListener(this);
    }

    private void checkForceStop() {
        if (this.mDpm.packageHasActiveAdmins(this.mPackageInfo.packageName)) {
            updateForceStopButton(false);
        } else if ((this.mAppEntry.info.flags & 2097152) == 0) {
            updateForceStopButton(true);
        } else {
            Intent intent = new Intent("android.intent.action.QUERY_PACKAGE_RESTART", Uri.fromParts("package", this.mAppEntry.info.packageName, null));
            intent.putExtra("android.intent.extra.PACKAGES", new String[]{this.mAppEntry.info.packageName});
            intent.putExtra("android.intent.extra.UID", this.mAppEntry.info.uid);
            intent.putExtra("android.intent.extra.user_handle", UserHandle.getUserId(this.mAppEntry.info.uid));
            getActivity().sendOrderedBroadcastAsUser(intent, UserHandle.CURRENT, null, this.mCheckKillProcessesReceiver, null, 0, null, null);
        }
    }

    private void setNotificationsEnabled(boolean enabled) {
        String packageName = this.mAppEntry.info.packageName;
        INotificationManager nm = INotificationManager.Stub.asInterface(ServiceManager.getService("notification"));
        try {
            boolean enable = this.mNotificationSwitch.isChecked();
            nm.setNotificationsEnabledForPackage(packageName, this.mAppEntry.info.uid, enabled);
        } catch (RemoteException e) {
            this.mNotificationSwitch.setChecked(!enabled);
        }
    }

    private int getPremiumSmsPermission(String packageName) {
        try {
            if (this.mSmsManager != null) {
                return this.mSmsManager.getPremiumSmsPermission(packageName);
            }
        } catch (RemoteException e) {
        }
        return 0;
    }

    public void onClick(View v) {
        int moveFlags = 2;
        String packageName = this.mAppEntry.info.packageName;
        if (v == this.mUninstallButton) {
            if (this.mUpdatedSysApp) {
                showDialogInner(2, 0);
            } else if ((this.mAppEntry.info.flags & 1) != 0) {
                if (this.mAppEntry.info.enabled) {
                    showDialogInner(7, 0);
                    return;
                }
                new DisableChanger(this, this.mAppEntry.info, 0).execute(new Object[]{null});
            } else if ((this.mAppEntry.info.flags & 8388608) == 0) {
                uninstallPkg(packageName, true, false);
            } else {
                uninstallPkg(packageName, false, false);
            }
        } else if (v == this.mSpecialDisableButton) {
            showDialogInner(9, 0);
        } else if (v == this.mActivitiesButton) {
            this.mPm.clearPackagePreferredActivities(packageName);
            try {
                this.mUsbManager.clearDefaults(packageName, UserHandle.myUserId());
            } catch (RemoteException e) {
                Log.e("InstalledAppDetails", "mUsbManager.clearDefaults", e);
            }
            this.mAppWidgetManager.setBindAppWidgetPermission(packageName, false);
            resetLaunchDefaultsUi((TextView) this.mRootView.findViewById(R.id.auto_launch_title), (TextView) this.mRootView.findViewById(R.id.auto_launch));
        } else if (v == this.mClearDataButton) {
            if (this.mAppEntry.info.manageSpaceActivityName == null) {
                showDialogInner(1, 0);
            } else if (!Utils.isMonkeyRunning()) {
                Intent intent = new Intent("android.intent.action.VIEW");
                intent.setClassName(this.mAppEntry.info.packageName, this.mAppEntry.info.manageSpaceActivityName);
                startActivityForResult(intent, 2);
            }
        } else if (v == this.mClearCacheButton) {
            if (this.mClearCacheObserver == null) {
                this.mClearCacheObserver = new ClearCacheObserver();
            }
            this.mPm.deleteApplicationCacheFiles(packageName, this.mClearCacheObserver);
        } else if (v == this.mForceStopButton) {
            showDialogInner(5, 0);
        } else if (v == this.mMoveAppButton) {
            if (this.mPackageMoveObserver == null) {
                this.mPackageMoveObserver = new PackageMoveObserver();
            }
            if ((this.mAppEntry.info.flags & 262144) != 0) {
                moveFlags = 1;
            }
            this.mMoveInProgress = true;
            refreshButtons();
            this.mPm.movePackage(this.mAppEntry.info.packageName, this.mPackageMoveObserver, moveFlags);
        }
    }

    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        int i = 1;
        String packageName = this.mAppEntry.info.packageName;
        ActivityManager am = (ActivityManager) getActivity().getSystemService("activity");
        if (buttonView == this.mAskCompatibilityCB) {
            am.setPackageAskScreenCompat(packageName, isChecked);
        } else if (buttonView == this.mEnableCompatibilityCB) {
            if (!isChecked) {
                i = 0;
            }
            am.setPackageScreenCompatMode(packageName, i);
        } else if (buttonView != this.mNotificationSwitch) {
        } else {
            if (isChecked) {
                setNotificationsEnabled(true);
            } else {
                showDialogInner(8, 0);
            }
        }
    }
}
