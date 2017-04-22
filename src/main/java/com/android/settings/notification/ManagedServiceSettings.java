package com.android.settings.notification;

import android.app.ActivityManager;
import android.app.AlertDialog.Builder;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.ListFragment;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageItemInfo.DisplayNameComparator;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings.Secure;
import android.util.Slog;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import com.android.settings.R;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;

public abstract class ManagedServiceSettings extends ListFragment {
    private ContentResolver mCR;
    private final Config mConfig = getConfig();
    private final HashSet<ComponentName> mEnabledServices = new HashSet();
    private ServiceListAdapter mListAdapter;
    private PackageManager mPM;
    private final BroadcastReceiver mPackageReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            ManagedServiceSettings.this.updateList();
        }
    };
    private final ContentObserver mSettingsObserver = new ContentObserver(new Handler()) {
        public void onChange(boolean selfChange, Uri uri) {
            ManagedServiceSettings.this.updateList();
        }
    };

    protected static class Config {
        int emptyText;
        String intentAction;
        String noun;
        String permission;
        String setting;
        String tag;
        int warningDialogSummary;
        int warningDialogTitle;

        protected Config() {
        }
    }

    public class ScaryWarningDialogFragment extends DialogFragment {
        public ScaryWarningDialogFragment setServiceInfo(ComponentName cn, String label) {
            Bundle args = new Bundle();
            args.putString("c", cn.flattenToString());
            args.putString("l", label);
            setArguments(args);
            return this;
        }

        public Dialog onCreateDialog(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            Bundle args = getArguments();
            String label = args.getString("l");
            final ComponentName cn = ComponentName.unflattenFromString(args.getString("c"));
            return new Builder(getActivity()).setMessage(getResources().getString(ManagedServiceSettings.this.mConfig.warningDialogSummary, new Object[]{label})).setTitle(getResources().getString(ManagedServiceSettings.this.mConfig.warningDialogTitle, new Object[]{label})).setCancelable(true).setPositiveButton(17039370, new OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    ManagedServiceSettings.this.mEnabledServices.add(cn);
                    ManagedServiceSettings.this.saveEnabledServices();
                }
            }).setNegativeButton(17039360, new OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                }
            }).create();
        }
    }

    private class ServiceListAdapter extends ArrayAdapter<ServiceInfo> {
        final LayoutInflater mInflater;

        ServiceListAdapter(Context context) {
            super(context, 0, 0);
            this.mInflater = (LayoutInflater) ManagedServiceSettings.this.getActivity().getSystemService("layout_inflater");
        }

        public boolean hasStableIds() {
            return true;
        }

        public long getItemId(int position) {
            return (long) position;
        }

        public View getView(int position, View convertView, ViewGroup parent) {
            View v;
            if (convertView == null) {
                v = newView(parent);
            } else {
                v = convertView;
            }
            bindView(v, position);
            return v;
        }

        public View newView(ViewGroup parent) {
            View v = this.mInflater.inflate(R.layout.managed_service_item, parent, false);
            ViewHolder h = new ViewHolder();
            h.icon = (ImageView) v.findViewById(R.id.icon);
            h.name = (TextView) v.findViewById(R.id.name);
            h.checkbox = (CheckBox) v.findViewById(R.id.checkbox);
            h.description = (TextView) v.findViewById(R.id.description);
            v.setTag(h);
            return v;
        }

        public void bindView(View view, int position) {
            ViewHolder vh = (ViewHolder) view.getTag();
            ServiceInfo info = (ServiceInfo) getItem(position);
            vh.icon.setImageDrawable(info.loadIcon(ManagedServiceSettings.this.mPM));
            vh.name.setText(info.loadLabel(ManagedServiceSettings.this.mPM));
            vh.description.setVisibility(8);
            vh.checkbox.setChecked(ManagedServiceSettings.this.isServiceEnabled(info));
        }
    }

    private static class ViewHolder {
        CheckBox checkbox;
        TextView description;
        ImageView icon;
        TextView name;

        private ViewHolder() {
        }
    }

    protected abstract Config getConfig();

    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        this.mPM = getActivity().getPackageManager();
        this.mCR = getActivity().getContentResolver();
        this.mListAdapter = new ServiceListAdapter(getActivity());
    }

    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.managed_service_settings, container, false);
        ((TextView) v.findViewById(16908292)).setText(this.mConfig.emptyText);
        return v;
    }

    public void onResume() {
        super.onResume();
        updateList();
        IntentFilter filter = new IntentFilter();
        filter.addAction("android.intent.action.PACKAGE_ADDED");
        filter.addAction("android.intent.action.PACKAGE_CHANGED");
        filter.addAction("android.intent.action.PACKAGE_REMOVED");
        filter.addAction("android.intent.action.PACKAGE_REPLACED");
        filter.addDataScheme("package");
        getActivity().registerReceiver(this.mPackageReceiver, filter);
        this.mCR.registerContentObserver(Secure.getUriFor(this.mConfig.setting), false, this.mSettingsObserver);
    }

    public void onPause() {
        super.onPause();
        getActivity().unregisterReceiver(this.mPackageReceiver);
        this.mCR.unregisterContentObserver(this.mSettingsObserver);
    }

    private void loadEnabledServices() {
        this.mEnabledServices.clear();
        String flat = Secure.getString(this.mCR, this.mConfig.setting);
        if (flat != null && !"".equals(flat)) {
            String[] names = flat.split(":");
            for (String unflattenFromString : names) {
                ComponentName cn = ComponentName.unflattenFromString(unflattenFromString);
                if (cn != null) {
                    this.mEnabledServices.add(cn);
                }
            }
        }
    }

    private void saveEnabledServices() {
        StringBuilder sb = null;
        Iterator i$ = this.mEnabledServices.iterator();
        while (i$.hasNext()) {
            ComponentName cn = (ComponentName) i$.next();
            if (sb == null) {
                sb = new StringBuilder();
            } else {
                sb.append(':');
            }
            sb.append(cn.flattenToString());
        }
        Secure.putString(this.mCR, this.mConfig.setting, sb != null ? sb.toString() : "");
    }

    private void updateList() {
        loadEnabledServices();
        getServices(this.mConfig, this.mListAdapter, this.mPM);
        this.mListAdapter.sort(new DisplayNameComparator(this.mPM));
        getListView().setAdapter(this.mListAdapter);
    }

    protected static int getEnabledServicesCount(Config config, Context context) {
        String flat = Secure.getString(context.getContentResolver(), config.setting);
        if (flat == null || "".equals(flat)) {
            return 0;
        }
        return flat.split(":").length;
    }

    protected static int getServicesCount(Config c, PackageManager pm) {
        return getServices(c, null, pm);
    }

    private static int getServices(Config c, ArrayAdapter<ServiceInfo> adapter, PackageManager pm) {
        int services = 0;
        if (adapter != null) {
            adapter.clear();
        }
        List<ResolveInfo> installedServices = pm.queryIntentServicesAsUser(new Intent(c.intentAction), 132, ActivityManager.getCurrentUser());
        int count = installedServices.size();
        for (int i = 0; i < count; i++) {
            ServiceInfo info = ((ResolveInfo) installedServices.get(i)).serviceInfo;
            if (c.permission.equals(info.permission)) {
                if (adapter != null) {
                    adapter.add(info);
                }
                services++;
            } else {
                Slog.w(c.tag, "Skipping " + c.noun + " service " + info.packageName + "/" + info.name + ": it does not require the permission " + c.permission);
            }
        }
        return services;
    }

    private boolean isServiceEnabled(ServiceInfo info) {
        return this.mEnabledServices.contains(new ComponentName(info.packageName, info.name));
    }

    public void onListItemClick(ListView l, View v, int position, long id) {
        ServiceInfo info = (ServiceInfo) this.mListAdapter.getItem(position);
        ComponentName cn = new ComponentName(info.packageName, info.name);
        if (this.mEnabledServices.contains(cn)) {
            this.mEnabledServices.remove(cn);
            saveEnabledServices();
            return;
        }
        new ScaryWarningDialogFragment().setServiceInfo(cn, info.loadLabel(this.mPM).toString()).show(getFragmentManager(), "dialog");
    }
}
