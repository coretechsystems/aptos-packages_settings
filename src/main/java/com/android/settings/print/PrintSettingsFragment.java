package com.android.settings.print;

import android.app.ActivityManager;
import android.app.LoaderManager.LoaderCallbacks;
import android.content.AsyncTaskLoader;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.Loader;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.UserHandle;
import android.os.UserManager;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceScreen;
import android.print.PrintJob;
import android.print.PrintJobId;
import android.print.PrintJobInfo;
import android.print.PrintManager;
import android.print.PrintManager.PrintJobStateChangeListener;
import android.printservice.PrintServiceInfo;
import android.provider.SearchIndexableResource;
import android.provider.Settings.Secure;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.Spinner;
import android.widget.TextView;
import com.android.internal.content.PackageMonitor;
import com.android.settings.DialogCreatable;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.UserSpinnerAdapter;
import com.android.settings.Utils;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settings.search.Indexable;
import com.android.settings.search.Indexable.SearchIndexProvider;
import com.android.settings.search.SearchIndexableRaw;
import java.util.ArrayList;
import java.util.List;

public class PrintSettingsFragment extends SettingsPreferenceFragment implements OnItemSelectedListener, DialogCreatable, Indexable {
    public static final SearchIndexProvider SEARCH_INDEX_DATA_PROVIDER = new BaseSearchIndexProvider() {
        public List<SearchIndexableRaw> getRawDataToIndex(Context context, boolean enabled) {
            List<SearchIndexableRaw> indexables = new ArrayList();
            PackageManager packageManager = context.getPackageManager();
            PrintManager printManager = (PrintManager) context.getSystemService("print");
            String screenTitle = context.getResources().getString(R.string.print_settings);
            SearchIndexableRaw data = new SearchIndexableRaw(context);
            data.title = screenTitle;
            data.screenTitle = screenTitle;
            indexables.add(data);
            List<PrintServiceInfo> services = printManager.getInstalledPrintServices();
            int serviceCount = services.size();
            for (int i = 0; i < serviceCount; i++) {
                PrintServiceInfo service = (PrintServiceInfo) services.get(i);
                ComponentName componentName = new ComponentName(service.getResolveInfo().serviceInfo.packageName, service.getResolveInfo().serviceInfo.name);
                data = new SearchIndexableRaw(context);
                data.key = componentName.flattenToString();
                data.title = service.getResolveInfo().loadLabel(packageManager).toString();
                data.summaryOn = context.getString(R.string.print_feature_state_on);
                data.summaryOff = context.getString(R.string.print_feature_state_off);
                data.screenTitle = screenTitle;
                indexables.add(data);
            }
            return indexables;
        }

        public List<SearchIndexableResource> getXmlResourcesToIndex(Context context, boolean enabled) {
            List<SearchIndexableResource> indexables = new ArrayList();
            SearchIndexableResource indexable = new SearchIndexableResource(context);
            indexable.xmlResId = R.xml.print_settings;
            indexables.add(indexable);
            return indexables;
        }
    };
    private PreferenceCategory mActivePrintJobsCategory;
    private final Handler mHandler = new Handler() {
        public void dispatchMessage(Message msg) {
            PrintSettingsFragment.this.updateServicesPreferences();
        }
    };
    private PrintJobsController mPrintJobsController;
    private PreferenceCategory mPrintServicesCategory;
    private UserSpinnerAdapter mProfileSpinnerAdapter;
    private final SettingsContentObserver mSettingsContentObserver = new SettingsContentObserver(this.mHandler) {
        public void onChange(boolean selfChange, Uri uri) {
            PrintSettingsFragment.this.updateServicesPreferences();
        }
    };
    private final PackageMonitor mSettingsPackageMonitor = new SettingsPackageMonitor();

    private static abstract class SettingsContentObserver extends ContentObserver {
        public SettingsContentObserver(Handler handler) {
            super(handler);
        }

        public void register(ContentResolver contentResolver) {
            contentResolver.registerContentObserver(Secure.getUriFor("enabled_print_services"), false, this);
        }

        public void unregister(ContentResolver contentResolver) {
            contentResolver.unregisterContentObserver(this);
        }
    }

    private final class PrintJobsController implements LoaderCallbacks<List<PrintJobInfo>> {
        private PrintJobsController() {
        }

        public Loader<List<PrintJobInfo>> onCreateLoader(int id, Bundle args) {
            if (id == 1) {
                return new PrintJobsLoader(PrintSettingsFragment.this.getActivity());
            }
            return null;
        }

        public void onLoadFinished(Loader<List<PrintJobInfo>> loader, List<PrintJobInfo> printJobs) {
            if (printJobs == null || printJobs.isEmpty()) {
                PrintSettingsFragment.this.getPreferenceScreen().removePreference(PrintSettingsFragment.this.mActivePrintJobsCategory);
                return;
            }
            if (PrintSettingsFragment.this.getPreferenceScreen().findPreference("print_jobs_category") == null) {
                PrintSettingsFragment.this.getPreferenceScreen().addPreference(PrintSettingsFragment.this.mActivePrintJobsCategory);
            }
            PrintSettingsFragment.this.mActivePrintJobsCategory.removeAll();
            int printJobCount = printJobs.size();
            for (int i = 0; i < printJobCount; i++) {
                PrintJobInfo printJob = (PrintJobInfo) printJobs.get(i);
                PreferenceScreen preference = PrintSettingsFragment.this.getPreferenceManager().createPreferenceScreen(PrintSettingsFragment.this.getActivity());
                preference.setPersistent(false);
                preference.setFragment(PrintJobSettingsFragment.class.getName());
                preference.setKey(printJob.getId().flattenToString());
                switch (printJob.getState()) {
                    case 2:
                    case 3:
                        if (!printJob.isCancelling()) {
                            preference.setTitle(PrintSettingsFragment.this.getString(R.string.print_printing_state_title_template, new Object[]{printJob.getLabel()}));
                            break;
                        }
                        preference.setTitle(PrintSettingsFragment.this.getString(R.string.print_cancelling_state_title_template, new Object[]{printJob.getLabel()}));
                        break;
                    case 4:
                        if (!printJob.isCancelling()) {
                            preference.setTitle(PrintSettingsFragment.this.getString(R.string.print_blocked_state_title_template, new Object[]{printJob.getLabel()}));
                            break;
                        }
                        preference.setTitle(PrintSettingsFragment.this.getString(R.string.print_cancelling_state_title_template, new Object[]{printJob.getLabel()}));
                        break;
                    case 6:
                        preference.setTitle(PrintSettingsFragment.this.getString(R.string.print_failed_state_title_template, new Object[]{printJob.getLabel()}));
                        break;
                }
                preference.setSummary(PrintSettingsFragment.this.getString(R.string.print_job_summary, new Object[]{printJob.getPrinterName(), DateUtils.formatSameDayTime(printJob.getCreationTime(), printJob.getCreationTime(), 3, 3)}));
                switch (printJob.getState()) {
                    case 2:
                    case 3:
                        preference.setIcon(R.drawable.ic_print);
                        break;
                    case 4:
                    case 6:
                        preference.setIcon(R.drawable.ic_print_error);
                        break;
                    default:
                        break;
                }
                preference.getExtras().putString("EXTRA_PRINT_JOB_ID", printJob.getId().flattenToString());
                PrintSettingsFragment.this.mActivePrintJobsCategory.addPreference(preference);
            }
        }

        public void onLoaderReset(Loader<List<PrintJobInfo>> loader) {
            PrintSettingsFragment.this.getPreferenceScreen().removePreference(PrintSettingsFragment.this.mActivePrintJobsCategory);
        }
    }

    private static final class PrintJobsLoader extends AsyncTaskLoader<List<PrintJobInfo>> {
        private PrintJobStateChangeListener mPrintJobStateChangeListener;
        private List<PrintJobInfo> mPrintJobs = new ArrayList();
        private final PrintManager mPrintManager;

        public PrintJobsLoader(Context context) {
            super(context);
            this.mPrintManager = ((PrintManager) context.getSystemService("print")).getGlobalPrintManagerForUser(ActivityManager.getCurrentUser());
        }

        public void deliverResult(List<PrintJobInfo> printJobs) {
            if (isStarted()) {
                super.deliverResult(printJobs);
            }
        }

        protected void onStartLoading() {
            if (!this.mPrintJobs.isEmpty()) {
                deliverResult(new ArrayList(this.mPrintJobs));
            }
            if (this.mPrintJobStateChangeListener == null) {
                this.mPrintJobStateChangeListener = new PrintJobStateChangeListener() {
                    public void onPrintJobStateChanged(PrintJobId printJobId) {
                        PrintJobsLoader.this.onForceLoad();
                    }
                };
                this.mPrintManager.addPrintJobStateChangeListener(this.mPrintJobStateChangeListener);
            }
            if (this.mPrintJobs.isEmpty()) {
                onForceLoad();
            }
        }

        protected void onStopLoading() {
            onCancelLoad();
        }

        protected void onReset() {
            onStopLoading();
            this.mPrintJobs.clear();
            if (this.mPrintJobStateChangeListener != null) {
                this.mPrintManager.removePrintJobStateChangeListener(this.mPrintJobStateChangeListener);
                this.mPrintJobStateChangeListener = null;
            }
        }

        public List<PrintJobInfo> loadInBackground() {
            List<PrintJobInfo> printJobInfos = null;
            List<PrintJob> printJobs = this.mPrintManager.getPrintJobs();
            int printJobCount = printJobs.size();
            for (int i = 0; i < printJobCount; i++) {
                PrintJobInfo printJob = ((PrintJob) printJobs.get(i)).getInfo();
                if (shouldShowToUser(printJob)) {
                    if (printJobInfos == null) {
                        printJobInfos = new ArrayList();
                    }
                    printJobInfos.add(printJob);
                }
            }
            return printJobInfos;
        }

        private static boolean shouldShowToUser(PrintJobInfo printJob) {
            switch (printJob.getState()) {
                case 2:
                case 3:
                case 4:
                case 6:
                    return true;
                default:
                    return false;
            }
        }
    }

    private class SettingsPackageMonitor extends PackageMonitor {
        private SettingsPackageMonitor() {
        }

        public void onPackageAdded(String packageName, int uid) {
            PrintSettingsFragment.this.mHandler.obtainMessage().sendToTarget();
        }

        public void onPackageAppeared(String packageName, int reason) {
            PrintSettingsFragment.this.mHandler.obtainMessage().sendToTarget();
        }

        public void onPackageDisappeared(String packageName, int reason) {
            PrintSettingsFragment.this.mHandler.obtainMessage().sendToTarget();
        }

        public void onPackageRemoved(String packageName, int uid) {
            PrintSettingsFragment.this.mHandler.obtainMessage().sendToTarget();
        }
    }

    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        addPreferencesFromResource(R.xml.print_settings);
        this.mActivePrintJobsCategory = (PreferenceCategory) findPreference("print_jobs_category");
        this.mPrintServicesCategory = (PreferenceCategory) findPreference("print_services_category");
        getPreferenceScreen().removePreference(this.mActivePrintJobsCategory);
        this.mPrintJobsController = new PrintJobsController();
        getActivity().getLoaderManager().initLoader(1, null, this.mPrintJobsController);
    }

    public void onResume() {
        super.onResume();
        this.mSettingsPackageMonitor.register(getActivity(), getActivity().getMainLooper(), false);
        this.mSettingsContentObserver.register(getContentResolver());
        updateServicesPreferences();
        setHasOptionsMenu(true);
        startSubSettingsIfNeeded();
    }

    public void onPause() {
        this.mSettingsPackageMonitor.unregister();
        this.mSettingsContentObserver.unregister(getContentResolver());
        super.onPause();
    }

    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        String searchUri = Secure.getString(getContentResolver(), "print_service_search_uri");
        if (!TextUtils.isEmpty(searchUri)) {
            MenuItem menuItem = menu.add(R.string.print_menu_item_add_service);
            menuItem.setShowAsActionFlags(0);
            menuItem.setIntent(new Intent("android.intent.action.VIEW", Uri.parse(searchUri)));
        }
    }

    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        ViewGroup contentRoot = (ViewGroup) getListView().getParent();
        View emptyView = getActivity().getLayoutInflater().inflate(R.layout.empty_print_state, contentRoot, false);
        ((TextView) emptyView.findViewById(R.id.message)).setText(R.string.print_no_services_installed);
        contentRoot.addView(emptyView);
        getListView().setEmptyView(emptyView);
        this.mProfileSpinnerAdapter = Utils.createUserSpinnerAdapter((UserManager) getSystemService("user"), getActivity());
        if (this.mProfileSpinnerAdapter != null) {
            Spinner spinner = (Spinner) getActivity().getLayoutInflater().inflate(R.layout.spinner_view, null);
            spinner.setAdapter(this.mProfileSpinnerAdapter);
            spinner.setOnItemSelectedListener(this);
            setPinnedHeaderView(spinner);
        }
    }

    private void updateServicesPreferences() {
        if (getPreferenceScreen().findPreference("print_services_category") == null) {
            getPreferenceScreen().addPreference(this.mPrintServicesCategory);
        } else {
            this.mPrintServicesCategory.removeAll();
        }
        List<ComponentName> enabledServices = PrintSettingsUtils.readEnabledPrintServices(getActivity());
        List<ResolveInfo> installedServices = getActivity().getPackageManager().queryIntentServices(new Intent("android.printservice.PrintService"), 132);
        int installedServiceCount = installedServices.size();
        for (int i = 0; i < installedServiceCount; i++) {
            ResolveInfo installedService = (ResolveInfo) installedServices.get(i);
            PreferenceScreen preference = getPreferenceManager().createPreferenceScreen(getActivity());
            String title = installedService.loadLabel(getPackageManager()).toString();
            preference.setTitle(title);
            ComponentName componentName = new ComponentName(installedService.serviceInfo.packageName, installedService.serviceInfo.name);
            preference.setKey(componentName.flattenToString());
            preference.setOrder(i);
            preference.setFragment(PrintServiceSettingsFragment.class.getName());
            preference.setPersistent(false);
            boolean serviceEnabled = enabledServices.contains(componentName);
            if (serviceEnabled) {
                preference.setSummary(getString(R.string.print_feature_state_on));
            } else {
                preference.setSummary(getString(R.string.print_feature_state_off));
            }
            Bundle extras = preference.getExtras();
            extras.putString("EXTRA_PREFERENCE_KEY", preference.getKey());
            extras.putBoolean("EXTRA_CHECKED", serviceEnabled);
            extras.putString("EXTRA_TITLE", title);
            PrintServiceInfo printServiceInfo = PrintServiceInfo.create(installedService, getActivity());
            CharSequence applicationLabel = installedService.loadLabel(getPackageManager());
            extras.putString("EXTRA_ENABLE_WARNING_TITLE", getString(R.string.print_service_security_warning_title, new Object[]{applicationLabel}));
            extras.putString("EXTRA_ENABLE_WARNING_MESSAGE", getString(R.string.print_service_security_warning_summary, new Object[]{applicationLabel}));
            String settingsClassName = printServiceInfo.getSettingsActivityName();
            if (!TextUtils.isEmpty(settingsClassName)) {
                extras.putString("EXTRA_SETTINGS_TITLE", getString(R.string.print_menu_item_settings));
                extras.putString("EXTRA_SETTINGS_COMPONENT_NAME", new ComponentName(installedService.serviceInfo.packageName, settingsClassName).flattenToString());
            }
            String addPrinterClassName = printServiceInfo.getAddPrintersActivityName();
            if (!TextUtils.isEmpty(addPrinterClassName)) {
                extras.putString("EXTRA_ADD_PRINTERS_TITLE", getString(R.string.print_menu_item_add_printers));
                extras.putString("EXTRA_ADD_PRINTERS_COMPONENT_NAME", new ComponentName(installedService.serviceInfo.packageName, addPrinterClassName).flattenToString());
            }
            extras.putString("EXTRA_SERVICE_COMPONENT_NAME", componentName.flattenToString());
            this.mPrintServicesCategory.addPreference(preference);
        }
        if (this.mPrintServicesCategory.getPreferenceCount() == 0) {
            getPreferenceScreen().removePreference(this.mPrintServicesCategory);
        }
    }

    private void startSubSettingsIfNeeded() {
        if (getArguments() != null) {
            String componentName = getArguments().getString("EXTRA_PRINT_SERVICE_COMPONENT_NAME");
            if (componentName != null) {
                getArguments().remove("EXTRA_PRINT_SERVICE_COMPONENT_NAME");
                Preference prereference = findPreference(componentName);
                if (prereference != null) {
                    prereference.performClick(getPreferenceScreen());
                }
            }
        }
    }

    public void onItemSelected(AdapterView<?> adapterView, View view, int position, long id) {
        UserHandle selectedUser = this.mProfileSpinnerAdapter.getUserHandle(position);
        if (selectedUser.getIdentifier() != UserHandle.myUserId()) {
            Intent intent = new Intent("android.settings.ACTION_PRINT_SETTINGS");
            intent.addFlags(268435456);
            intent.addFlags(32768);
            getActivity().startActivityAsUser(intent, selectedUser);
        }
    }

    public void onNothingSelected(AdapterView<?> adapterView) {
    }
}
