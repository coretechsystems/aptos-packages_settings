package com.android.settings.print;

import android.app.Activity;
import android.app.AlertDialog.Builder;
import android.app.Dialog;
import android.app.LoaderManager.LoaderCallbacks;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.Loader;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ResolveInfo;
import android.database.ContentObserver;
import android.database.DataSetObserver;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.print.PrintManager;
import android.print.PrinterDiscoverySession;
import android.print.PrinterDiscoverySession.OnPrintersChangeListener;
import android.print.PrinterId;
import android.print.PrinterInfo;
import android.provider.Settings.Secure;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnAttachStateChangeListener;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityManager;
import android.widget.BaseAdapter;
import android.widget.Filter;
import android.widget.Filter.FilterResults;
import android.widget.Filterable;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.SearchView;
import android.widget.SearchView.OnQueryTextListener;
import android.widget.Switch;
import android.widget.TextView;
import com.android.settings.R;
import com.android.settings.SettingsActivity;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.widget.SwitchBar;
import com.android.settings.widget.SwitchBar.OnSwitchChangeListener;
import com.android.settings.widget.ToggleSwitch;
import com.android.settings.widget.ToggleSwitch.OnBeforeCheckedChangeListener;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class PrintServiceSettingsFragment extends SettingsPreferenceFragment implements OnClickListener, OnSwitchChangeListener {
    private Intent mAddPrintersIntent;
    private CharSequence mAddPrintersTitle;
    private ComponentName mComponentName;
    private final DataSetObserver mDataObserver = new DataSetObserver() {
        public void onChanged() {
            invalidateOptionsMenuIfNeeded();
            PrintServiceSettingsFragment.this.updateEmptyView();
        }

        public void onInvalidated() {
            invalidateOptionsMenuIfNeeded();
        }

        private void invalidateOptionsMenuIfNeeded() {
            int unfilteredItemCount = PrintServiceSettingsFragment.this.mPrintersAdapter.getUnfilteredCount();
            if ((PrintServiceSettingsFragment.this.mLastUnfilteredItemCount <= 0 && unfilteredItemCount > 0) || (PrintServiceSettingsFragment.this.mLastUnfilteredItemCount > 0 && unfilteredItemCount <= 0)) {
                PrintServiceSettingsFragment.this.getActivity().invalidateOptionsMenu();
            }
            PrintServiceSettingsFragment.this.mLastUnfilteredItemCount = unfilteredItemCount;
        }
    };
    private CharSequence mEnableWarningMessage;
    private CharSequence mEnableWarningTitle;
    private int mLastUnfilteredItemCount;
    private CharSequence mOldActivityTitle;
    private String mPreferenceKey;
    private PrintersAdapter mPrintersAdapter;
    private boolean mServiceEnabled;
    private final SettingsContentObserver mSettingsContentObserver = new SettingsContentObserver(new Handler()) {
        public void onChange(boolean selfChange, Uri uri) {
            PrintServiceSettingsFragment.this.updateUiForServiceState();
        }
    };
    private Intent mSettingsIntent;
    private CharSequence mSettingsTitle;
    private SwitchBar mSwitchBar;
    private ToggleSwitch mToggleSwitch;

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

    private final class PrintersAdapter extends BaseAdapter implements LoaderCallbacks<List<PrinterInfo>>, Filterable {
        private final List<PrinterInfo> mFilteredPrinters;
        private CharSequence mLastSearchString;
        private final Object mLock;
        private final List<PrinterInfo> mPrinters;

        private PrintersAdapter() {
            this.mLock = new Object();
            this.mPrinters = new ArrayList();
            this.mFilteredPrinters = new ArrayList();
        }

        public void enable() {
            PrintServiceSettingsFragment.this.getLoaderManager().initLoader(1, null, this);
        }

        public void disable() {
            PrintServiceSettingsFragment.this.getLoaderManager().destroyLoader(1);
            this.mPrinters.clear();
        }

        public int getUnfilteredCount() {
            return this.mPrinters.size();
        }

        public Filter getFilter() {
            return new Filter() {
                protected FilterResults performFiltering(CharSequence constraint) {
                    FilterResults filterResults;
                    synchronized (PrintersAdapter.this.mLock) {
                        if (TextUtils.isEmpty(constraint)) {
                            filterResults = null;
                        } else {
                            filterResults = new FilterResults();
                            List<PrinterInfo> filteredPrinters = new ArrayList();
                            String constraintLowerCase = constraint.toString().toLowerCase();
                            int printerCount = PrintersAdapter.this.mPrinters.size();
                            for (int i = 0; i < printerCount; i++) {
                                PrinterInfo printer = (PrinterInfo) PrintersAdapter.this.mPrinters.get(i);
                                if (printer.getName().toLowerCase().contains(constraintLowerCase)) {
                                    filteredPrinters.add(printer);
                                }
                            }
                            filterResults.values = filteredPrinters;
                            filterResults.count = filteredPrinters.size();
                        }
                    }
                    return filterResults;
                }

                protected void publishResults(CharSequence constraint, FilterResults results) {
                    synchronized (PrintersAdapter.this.mLock) {
                        PrintersAdapter.this.mLastSearchString = constraint;
                        PrintersAdapter.this.mFilteredPrinters.clear();
                        if (results == null) {
                            PrintersAdapter.this.mFilteredPrinters.addAll(PrintersAdapter.this.mPrinters);
                        } else {
                            PrintersAdapter.this.mFilteredPrinters.addAll(results.values);
                        }
                    }
                    PrintersAdapter.this.notifyDataSetChanged();
                }
            };
        }

        public int getCount() {
            int size;
            synchronized (this.mLock) {
                size = this.mFilteredPrinters.size();
            }
            return size;
        }

        public Object getItem(int position) {
            Object obj;
            synchronized (this.mLock) {
                obj = this.mFilteredPrinters.get(position);
            }
            return obj;
        }

        public long getItemId(int position) {
            return (long) position;
        }

        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = PrintServiceSettingsFragment.this.getActivity().getLayoutInflater().inflate(R.layout.printer_dropdown_item, parent, false);
            }
            PrinterInfo printer = (PrinterInfo) getItem(position);
            CharSequence title = printer.getName();
            CharSequence subtitle = null;
            Drawable icon = null;
            try {
                PackageInfo packageInfo = PrintServiceSettingsFragment.this.getPackageManager().getPackageInfo(printer.getId().getServiceName().getPackageName(), 0);
                subtitle = packageInfo.applicationInfo.loadLabel(PrintServiceSettingsFragment.this.getPackageManager());
                icon = packageInfo.applicationInfo.loadIcon(PrintServiceSettingsFragment.this.getPackageManager());
            } catch (NameNotFoundException e) {
            }
            ((TextView) convertView.findViewById(R.id.title)).setText(title);
            TextView subtitleView = (TextView) convertView.findViewById(R.id.subtitle);
            if (TextUtils.isEmpty(subtitle)) {
                subtitleView.setText(null);
                subtitleView.setVisibility(8);
            } else {
                subtitleView.setText(subtitle);
                subtitleView.setVisibility(0);
            }
            ImageView iconView = (ImageView) convertView.findViewById(R.id.icon);
            if (icon != null) {
                iconView.setImageDrawable(icon);
                iconView.setVisibility(0);
            } else {
                iconView.setVisibility(8);
            }
            return convertView;
        }

        public boolean isEnabled(int position) {
            return false;
        }

        public Loader<List<PrinterInfo>> onCreateLoader(int id, Bundle args) {
            if (id == 1) {
                return new PrintersLoader(PrintServiceSettingsFragment.this.getActivity());
            }
            return null;
        }

        public void onLoadFinished(Loader<List<PrinterInfo>> loader, List<PrinterInfo> printers) {
            synchronized (this.mLock) {
                this.mPrinters.clear();
                int printerCount = printers.size();
                for (int i = 0; i < printerCount; i++) {
                    PrinterInfo printer = (PrinterInfo) printers.get(i);
                    if (printer.getId().getServiceName().equals(PrintServiceSettingsFragment.this.mComponentName)) {
                        this.mPrinters.add(printer);
                    }
                }
                this.mFilteredPrinters.clear();
                this.mFilteredPrinters.addAll(this.mPrinters);
                if (!TextUtils.isEmpty(this.mLastSearchString)) {
                    getFilter().filter(this.mLastSearchString);
                }
            }
            notifyDataSetChanged();
        }

        public void onLoaderReset(Loader<List<PrinterInfo>> loader) {
            synchronized (this.mLock) {
                this.mPrinters.clear();
                this.mFilteredPrinters.clear();
                this.mLastSearchString = null;
            }
            notifyDataSetInvalidated();
        }
    }

    private static class PrintersLoader extends Loader<List<PrinterInfo>> {
        private PrinterDiscoverySession mDiscoverySession;
        private final Map<PrinterId, PrinterInfo> mPrinters = new LinkedHashMap();

        public PrintersLoader(Context context) {
            super(context);
        }

        public void deliverResult(List<PrinterInfo> printers) {
            if (isStarted()) {
                super.deliverResult(printers);
            }
        }

        protected void onStartLoading() {
            if (!this.mPrinters.isEmpty()) {
                deliverResult(new ArrayList(this.mPrinters.values()));
            }
            onForceLoad();
        }

        protected void onStopLoading() {
            onCancelLoad();
        }

        protected void onForceLoad() {
            loadInternal();
        }

        protected boolean onCancelLoad() {
            return cancelInternal();
        }

        protected void onReset() {
            onStopLoading();
            this.mPrinters.clear();
            if (this.mDiscoverySession != null) {
                this.mDiscoverySession.destroy();
                this.mDiscoverySession = null;
            }
        }

        protected void onAbandon() {
            onStopLoading();
        }

        private boolean cancelInternal() {
            if (this.mDiscoverySession == null || !this.mDiscoverySession.isPrinterDiscoveryStarted()) {
                return false;
            }
            this.mDiscoverySession.stopPrinterDiscovery();
            return true;
        }

        private void loadInternal() {
            if (this.mDiscoverySession == null) {
                this.mDiscoverySession = ((PrintManager) getContext().getSystemService("print")).createPrinterDiscoverySession();
                this.mDiscoverySession.setOnPrintersChangeListener(new OnPrintersChangeListener() {
                    public void onPrintersChanged() {
                        PrintersLoader.this.deliverResult(new ArrayList(PrintersLoader.this.mDiscoverySession.getPrinters()));
                    }
                });
            }
            this.mDiscoverySession.startPrinterDiscovery(null);
        }
    }

    public void onResume() {
        super.onResume();
        this.mSettingsContentObserver.register(getContentResolver());
        updateEmptyView();
        updateUiForServiceState();
    }

    public void onPause() {
        this.mSettingsContentObserver.unregister(getContentResolver());
        super.onPause();
    }

    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initComponents();
        updateUiForArguments();
    }

    public void onDestroyView() {
        if (this.mOldActivityTitle != null) {
            getActivity().getActionBar().setTitle(this.mOldActivityTitle);
        }
        super.onDestroyView();
        this.mSwitchBar.removeOnSwitchChangeListener(this);
        this.mSwitchBar.hide();
    }

    private void onPreferenceToggled(String preferenceKey, boolean enabled) {
        ComponentName service = ComponentName.unflattenFromString(preferenceKey);
        List<ComponentName> services = PrintSettingsUtils.readEnabledPrintServices(getActivity());
        if (enabled) {
            services.add(service);
        } else {
            services.remove(service);
        }
        PrintSettingsUtils.writeEnabledPrintServices(getActivity(), services);
    }

    public Dialog onCreateDialog(int dialogId) {
        switch (dialogId) {
            case 1:
                CharSequence title = this.mEnableWarningTitle;
                return new Builder(getActivity()).setTitle(title).setMessage(this.mEnableWarningMessage).setCancelable(true).setPositiveButton(17039370, this).setNegativeButton(17039360, this).create();
            default:
                throw new IllegalArgumentException();
        }
    }

    public void onClick(DialogInterface dialog, int which) {
        switch (which) {
            case -2:
                this.mSwitchBar.setCheckedInternal(false);
                getArguments().putBoolean("EXTRA_CHECKED", false);
                onPreferenceToggled(this.mPreferenceKey, false);
                return;
            case -1:
                this.mSwitchBar.setCheckedInternal(true);
                getArguments().putBoolean("EXTRA_CHECKED", true);
                onPreferenceToggled(this.mPreferenceKey, true);
                return;
            default:
                throw new IllegalArgumentException();
        }
    }

    private void updateEmptyView() {
        ListView listView = getListView();
        ViewGroup contentRoot = (ViewGroup) listView.getParent();
        View emptyView = listView.getEmptyView();
        if (!this.mToggleSwitch.isChecked()) {
            if (!(emptyView == null || emptyView.getId() == R.id.empty_print_state)) {
                contentRoot.removeView(emptyView);
                emptyView = null;
            }
            if (emptyView == null) {
                emptyView = getActivity().getLayoutInflater().inflate(R.layout.empty_print_state, contentRoot, false);
                ((ImageView) emptyView.findViewById(R.id.icon)).setContentDescription(getString(R.string.print_service_disabled));
                ((TextView) emptyView.findViewById(R.id.message)).setText(R.string.print_service_disabled);
                contentRoot.addView(emptyView);
                listView.setEmptyView(emptyView);
            }
        } else if (this.mPrintersAdapter.getUnfilteredCount() <= 0) {
            if (!(emptyView == null || emptyView.getId() == R.id.empty_printers_list_service_enabled)) {
                contentRoot.removeView(emptyView);
                emptyView = null;
            }
            if (emptyView == null) {
                emptyView = getActivity().getLayoutInflater().inflate(R.layout.empty_printers_list_service_enabled, contentRoot, false);
                contentRoot.addView(emptyView);
                listView.setEmptyView(emptyView);
            }
        } else if (this.mPrintersAdapter.getCount() <= 0) {
            if (!(emptyView == null || emptyView.getId() == R.id.empty_print_state)) {
                contentRoot.removeView(emptyView);
                emptyView = null;
            }
            if (emptyView == null) {
                emptyView = getActivity().getLayoutInflater().inflate(R.layout.empty_print_state, contentRoot, false);
                ((ImageView) emptyView.findViewById(R.id.icon)).setContentDescription(getString(R.string.print_no_printers_found));
                ((TextView) emptyView.findViewById(R.id.message)).setText(R.string.print_no_printers_found);
                contentRoot.addView(emptyView);
                listView.setEmptyView(emptyView);
            }
        }
    }

    private void updateUiForServiceState() {
        this.mServiceEnabled = PrintSettingsUtils.readEnabledPrintServices(getActivity()).contains(this.mComponentName);
        if (this.mServiceEnabled) {
            this.mSwitchBar.setCheckedInternal(true);
            this.mPrintersAdapter.enable();
        } else {
            this.mSwitchBar.setCheckedInternal(false);
            this.mPrintersAdapter.disable();
        }
        getActivity().invalidateOptionsMenu();
    }

    private void initComponents() {
        this.mPrintersAdapter = new PrintersAdapter();
        this.mPrintersAdapter.registerDataSetObserver(this.mDataObserver);
        this.mSwitchBar = ((SettingsActivity) getActivity()).getSwitchBar();
        this.mSwitchBar.addOnSwitchChangeListener(this);
        this.mSwitchBar.show();
        this.mToggleSwitch = this.mSwitchBar.getSwitch();
        this.mToggleSwitch.setOnBeforeCheckedChangeListener(new OnBeforeCheckedChangeListener() {
            public boolean onBeforeCheckedChanged(ToggleSwitch toggleSwitch, boolean checked) {
                if (!checked) {
                    PrintServiceSettingsFragment.this.onPreferenceToggled(PrintServiceSettingsFragment.this.mPreferenceKey, false);
                } else if (TextUtils.isEmpty(PrintServiceSettingsFragment.this.mEnableWarningMessage)) {
                    PrintServiceSettingsFragment.this.onPreferenceToggled(PrintServiceSettingsFragment.this.mPreferenceKey, true);
                } else {
                    PrintServiceSettingsFragment.this.mSwitchBar.setCheckedInternal(false);
                    PrintServiceSettingsFragment.this.getArguments().putBoolean("EXTRA_CHECKED", false);
                    PrintServiceSettingsFragment.this.showDialog(1);
                    return true;
                }
                return false;
            }
        });
        getListView().setSelector(new ColorDrawable(0));
        getListView().setAdapter(this.mPrintersAdapter);
    }

    public void onSwitchChanged(Switch switchView, boolean isChecked) {
        updateEmptyView();
    }

    private void updateUiForArguments() {
        List<ResolveInfo> resolvedActivities;
        Bundle arguments = getArguments();
        this.mPreferenceKey = arguments.getString("EXTRA_PREFERENCE_KEY");
        this.mSwitchBar.setCheckedInternal(arguments.getBoolean("EXTRA_CHECKED"));
        String settingsTitle = arguments.getString("EXTRA_SETTINGS_TITLE");
        String settingsComponentName = arguments.getString("EXTRA_SETTINGS_COMPONENT_NAME");
        if (!(TextUtils.isEmpty(settingsTitle) || TextUtils.isEmpty(settingsComponentName))) {
            Intent settingsIntent = new Intent("android.intent.action.MAIN").setComponent(ComponentName.unflattenFromString(settingsComponentName.toString()));
            resolvedActivities = getPackageManager().queryIntentActivities(settingsIntent, 0);
            if (!resolvedActivities.isEmpty() && ((ResolveInfo) resolvedActivities.get(0)).activityInfo.exported) {
                this.mSettingsTitle = settingsTitle;
                this.mSettingsIntent = settingsIntent;
            }
        }
        String addPrintersTitle = arguments.getString("EXTRA_ADD_PRINTERS_TITLE");
        String addPrintersComponentName = arguments.getString("EXTRA_ADD_PRINTERS_COMPONENT_NAME");
        if (!(TextUtils.isEmpty(addPrintersTitle) || TextUtils.isEmpty(addPrintersComponentName))) {
            Intent addPritnersIntent = new Intent("android.intent.action.MAIN").setComponent(ComponentName.unflattenFromString(addPrintersComponentName.toString()));
            resolvedActivities = getPackageManager().queryIntentActivities(addPritnersIntent, 0);
            if (!resolvedActivities.isEmpty() && ((ResolveInfo) resolvedActivities.get(0)).activityInfo.exported) {
                this.mAddPrintersTitle = addPrintersTitle;
                this.mAddPrintersIntent = addPritnersIntent;
            }
        }
        this.mEnableWarningTitle = arguments.getCharSequence("EXTRA_ENABLE_WARNING_TITLE");
        this.mEnableWarningMessage = arguments.getCharSequence("EXTRA_ENABLE_WARNING_MESSAGE");
        this.mComponentName = ComponentName.unflattenFromString(arguments.getString("EXTRA_SERVICE_COMPONENT_NAME"));
        setHasOptionsMenu(true);
    }

    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.print_service_settings, menu);
        MenuItem addPrinters = menu.findItem(R.id.print_menu_item_add_printer);
        if (!this.mServiceEnabled || TextUtils.isEmpty(this.mAddPrintersTitle) || this.mAddPrintersIntent == null) {
            menu.removeItem(R.id.print_menu_item_add_printer);
        } else {
            addPrinters.setIntent(this.mAddPrintersIntent);
        }
        MenuItem settings = menu.findItem(R.id.print_menu_item_settings);
        if (!this.mServiceEnabled || TextUtils.isEmpty(this.mSettingsTitle) || this.mSettingsIntent == null) {
            menu.removeItem(R.id.print_menu_item_settings);
        } else {
            settings.setIntent(this.mSettingsIntent);
        }
        MenuItem searchItem = menu.findItem(R.id.print_menu_item_search);
        if (!this.mServiceEnabled || this.mPrintersAdapter.getUnfilteredCount() <= 0) {
            menu.removeItem(R.id.print_menu_item_search);
            return;
        }
        SearchView searchView = (SearchView) searchItem.getActionView();
        searchView.setOnQueryTextListener(new OnQueryTextListener() {
            public boolean onQueryTextSubmit(String query) {
                return true;
            }

            public boolean onQueryTextChange(String searchString) {
                ((Filterable) PrintServiceSettingsFragment.this.getListView().getAdapter()).getFilter().filter(searchString);
                return true;
            }
        });
        searchView.addOnAttachStateChangeListener(new OnAttachStateChangeListener() {
            public void onViewAttachedToWindow(View view) {
                if (AccessibilityManager.getInstance(PrintServiceSettingsFragment.this.getActivity()).isEnabled()) {
                    view.announceForAccessibility(PrintServiceSettingsFragment.this.getString(R.string.print_search_box_shown_utterance));
                }
            }

            public void onViewDetachedFromWindow(View view) {
                Activity activity = PrintServiceSettingsFragment.this.getActivity();
                if (activity != null && !activity.isFinishing() && AccessibilityManager.getInstance(activity).isEnabled()) {
                    view.announceForAccessibility(PrintServiceSettingsFragment.this.getString(R.string.print_search_box_hidden_utterance));
                }
            }
        });
    }
}
