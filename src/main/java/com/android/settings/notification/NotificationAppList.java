package com.android.settings.notification;

import android.animation.LayoutTransition;
import android.app.INotificationManager;
import android.app.INotificationManager.Stub;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Parcelable;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.ArrayMap;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.SectionIndexer;
import android.widget.Spinner;
import android.widget.TextView;
import com.android.settings.PinnedHeaderListFragment;
import com.android.settings.R;
import com.android.settings.Settings.NotificationAppListActivity;
import com.android.settings.UserSpinnerAdapter;
import com.android.settings.Utils;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

public class NotificationAppList extends PinnedHeaderListFragment implements OnItemSelectedListener {
    private static final Intent APP_NOTIFICATION_PREFS_CATEGORY_INTENT = new Intent("android.intent.action.MAIN").addCategory("android.intent.category.NOTIFICATION_PREFERENCES");
    private static final boolean DEBUG = Log.isLoggable("NotificationAppList", 3);
    private static final Comparator<AppRow> mRowComparator = new Comparator<AppRow>() {
        private final Collator sCollator = Collator.getInstance();

        public int compare(AppRow lhs, AppRow rhs) {
            return this.sCollator.compare(lhs.label, rhs.label);
        }
    };
    private NotificationAppAdapter mAdapter;
    private Backend mBackend = new Backend();
    private final Runnable mCollectAppsRunnable = new Runnable() {
        public void run() {
            synchronized (NotificationAppList.this.mRows) {
                long start = SystemClock.uptimeMillis();
                if (NotificationAppList.DEBUG) {
                    Log.d("NotificationAppList", "Collecting apps...");
                }
                NotificationAppList.this.mRows.clear();
                NotificationAppList.this.mSortedRows.clear();
                List<ApplicationInfo> appInfos = new ArrayList();
                List<LauncherActivityInfo> lais = NotificationAppList.this.mLauncherApps.getActivityList(null, UserHandle.getCallingUserHandle());
                if (NotificationAppList.DEBUG) {
                    Log.d("NotificationAppList", "  launchable activities:");
                }
                for (LauncherActivityInfo lai : lais) {
                    if (NotificationAppList.DEBUG) {
                        Log.d("NotificationAppList", "    " + lai.getComponentName().toString());
                    }
                    appInfos.add(lai.getApplicationInfo());
                }
                List<ResolveInfo> resolvedConfigActivities = NotificationAppList.queryNotificationConfigActivities(NotificationAppList.this.mPM);
                if (NotificationAppList.DEBUG) {
                    Log.d("NotificationAppList", "  config activities:");
                }
                for (ResolveInfo ri : resolvedConfigActivities) {
                    if (NotificationAppList.DEBUG) {
                        Log.d("NotificationAppList", "    " + ri.activityInfo.packageName + "/" + ri.activityInfo.name);
                    }
                    appInfos.add(ri.activityInfo.applicationInfo);
                }
                for (ApplicationInfo info : appInfos) {
                    String key = info.packageName;
                    if (!NotificationAppList.this.mRows.containsKey(key)) {
                        NotificationAppList.this.mRows.put(key, NotificationAppList.loadAppRow(NotificationAppList.this.mPM, info, NotificationAppList.this.mBackend));
                    }
                }
                NotificationAppList.applyConfigActivities(NotificationAppList.this.mPM, NotificationAppList.this.mRows, resolvedConfigActivities);
                NotificationAppList.this.mSortedRows.addAll(NotificationAppList.this.mRows.values());
                Collections.sort(NotificationAppList.this.mSortedRows, NotificationAppList.mRowComparator);
                NotificationAppList.this.mSections.clear();
                String section = null;
                Iterator i$ = NotificationAppList.this.mSortedRows.iterator();
                while (i$.hasNext()) {
                    AppRow r = (AppRow) i$.next();
                    r.section = NotificationAppList.this.getSection(r.label);
                    if (!r.section.equals(section)) {
                        section = r.section;
                        NotificationAppList.this.mSections.add(section);
                    }
                }
                NotificationAppList.this.mHandler.post(NotificationAppList.this.mRefreshAppsListRunnable);
                long elapsed = SystemClock.uptimeMillis() - start;
                if (NotificationAppList.DEBUG) {
                    Log.d("NotificationAppList", "Collected " + NotificationAppList.this.mRows.size() + " apps in " + elapsed + "ms");
                }
            }
        }
    };
    private Context mContext;
    private final Handler mHandler = new Handler();
    private LayoutInflater mInflater;
    private LauncherApps mLauncherApps;
    private Parcelable mListViewState;
    private PackageManager mPM;
    private UserSpinnerAdapter mProfileSpinnerAdapter;
    private final Runnable mRefreshAppsListRunnable = new Runnable() {
        public void run() {
            NotificationAppList.this.refreshDisplayedItems();
        }
    };
    private final ArrayMap<String, AppRow> mRows = new ArrayMap();
    private final ArrayList<String> mSections = new ArrayList();
    private final ArrayList<AppRow> mSortedRows = new ArrayList();
    private UserManager mUM;

    private static class Row {
        public String section;

        private Row() {
        }
    }

    public static class AppRow extends Row {
        public boolean banned;
        public boolean first;
        public Drawable icon;
        public CharSequence label;
        public String pkg;
        public boolean priority;
        public boolean sensitive;
        public Intent settingsIntent;
        public int uid;

        public AppRow() {
            super();
        }
    }

    public static class Backend {
        static INotificationManager sINM = Stub.asInterface(ServiceManager.getService("notification"));

        public boolean setNotificationsBanned(String pkg, int uid, boolean banned) {
            try {
                boolean z;
                INotificationManager iNotificationManager = sINM;
                if (banned) {
                    z = false;
                } else {
                    z = true;
                }
                iNotificationManager.setNotificationsEnabledForPackage(pkg, uid, z);
                return true;
            } catch (Exception e) {
                Log.w("NotificationAppList", "Error calling NoMan", e);
                return false;
            }
        }

        public boolean getNotificationsBanned(String pkg, int uid) {
            try {
                if (sINM.areNotificationsEnabledForPackage(pkg, uid)) {
                    return false;
                }
                return true;
            } catch (Exception e) {
                Log.w("NotificationAppList", "Error calling NoMan", e);
                return false;
            }
        }

        public boolean getHighPriority(String pkg, int uid) {
            try {
                return sINM.getPackagePriority(pkg, uid) == 2;
            } catch (Exception e) {
                Log.w("NotificationAppList", "Error calling NoMan", e);
                return false;
            }
        }

        public boolean setHighPriority(String pkg, int uid, boolean highPriority) {
            try {
                sINM.setPackagePriority(pkg, uid, highPriority ? 2 : 0);
                return true;
            } catch (Exception e) {
                Log.w("NotificationAppList", "Error calling NoMan", e);
                return false;
            }
        }

        public boolean getSensitive(String pkg, int uid) {
            try {
                return sINM.getPackageVisibilityOverride(pkg, uid) == 0;
            } catch (Exception e) {
                Log.w("NotificationAppList", "Error calling NoMan", e);
                return false;
            }
        }

        public boolean setSensitive(String pkg, int uid, boolean sensitive) {
            try {
                sINM.setPackageVisibilityOverride(pkg, uid, sensitive ? 0 : -1000);
                return true;
            } catch (Exception e) {
                Log.w("NotificationAppList", "Error calling NoMan", e);
                return false;
            }
        }
    }

    private class NotificationAppAdapter extends ArrayAdapter<Row> implements SectionIndexer {
        public NotificationAppAdapter(Context context) {
            super(context, 0, 0);
        }

        public boolean hasStableIds() {
            return true;
        }

        public long getItemId(int position) {
            return (long) position;
        }

        public int getViewTypeCount() {
            return 2;
        }

        public int getItemViewType(int position) {
            return ((Row) getItem(position)) instanceof AppRow ? 1 : 0;
        }

        public View getView(int position, View convertView, ViewGroup parent) {
            View v;
            Row r = (Row) getItem(position);
            if (convertView == null) {
                v = newView(parent, r);
            } else {
                v = convertView;
            }
            bindView(v, r, false);
            return v;
        }

        public View newView(ViewGroup parent, Row r) {
            if (!(r instanceof AppRow)) {
                return NotificationAppList.this.mInflater.inflate(R.layout.notification_app_section, parent, false);
            }
            View v = NotificationAppList.this.mInflater.inflate(R.layout.notification_app, parent, false);
            ViewHolder vh = new ViewHolder();
            vh.row = (ViewGroup) v;
            vh.row.setLayoutTransition(new LayoutTransition());
            vh.row.setLayoutTransition(new LayoutTransition());
            vh.icon = (ImageView) v.findViewById(16908294);
            vh.title = (TextView) v.findViewById(16908310);
            vh.subtitle = (TextView) v.findViewById(16908308);
            vh.rowDivider = v.findViewById(R.id.row_divider);
            v.setTag(vh);
            return v;
        }

        private void enableLayoutTransitions(ViewGroup vg, boolean enabled) {
            if (enabled) {
                vg.getLayoutTransition().enableTransitionType(2);
                vg.getLayoutTransition().enableTransitionType(3);
                return;
            }
            vg.getLayoutTransition().disableTransitionType(2);
            vg.getLayoutTransition().disableTransitionType(3);
        }

        public void bindView(View view, Row r, boolean animate) {
            int i = 0;
            if (r instanceof AppRow) {
                int i2;
                final AppRow row = (AppRow) r;
                ViewHolder vh = (ViewHolder) view.getTag();
                enableLayoutTransitions(vh.row, animate);
                View view2 = vh.rowDivider;
                if (row.first) {
                    i2 = 8;
                } else {
                    i2 = 0;
                }
                view2.setVisibility(i2);
                vh.row.setOnClickListener(new OnClickListener() {
                    public void onClick(View v) {
                        NotificationAppList.this.mContext.startActivity(new Intent("android.settings.APP_NOTIFICATION_SETTINGS").addFlags(67108864).putExtra("app_package", row.pkg).putExtra("app_uid", row.uid).putExtra("has_settings_intent", row.settingsIntent != null).putExtra("settings_intent", row.settingsIntent));
                    }
                });
                enableLayoutTransitions(vh.row, animate);
                vh.icon.setImageDrawable(row.icon);
                vh.title.setText(row.label);
                String sub = getSubtitle(row);
                vh.subtitle.setText(sub);
                TextView textView = vh.subtitle;
                if (sub.isEmpty()) {
                    i = 8;
                }
                textView.setVisibility(i);
                return;
            }
            ((TextView) view.findViewById(16908310)).setText(r.section);
        }

        private String getSubtitle(AppRow row) {
            if (row.banned) {
                return NotificationAppList.this.mContext.getString(R.string.app_notification_row_banned);
            }
            if (!row.priority && !row.sensitive) {
                return "";
            }
            String priString = NotificationAppList.this.mContext.getString(R.string.app_notification_row_priority);
            String senString = NotificationAppList.this.mContext.getString(R.string.app_notification_row_sensitive);
            if (row.priority == row.sensitive) {
                return priString + NotificationAppList.this.mContext.getString(R.string.summary_divider_text) + senString;
            }
            if (row.priority) {
                return priString;
            }
            return senString;
        }

        public Object[] getSections() {
            return NotificationAppList.this.mSections.toArray(new Object[NotificationAppList.this.mSections.size()]);
        }

        public int getPositionForSection(int sectionIndex) {
            String section = (String) NotificationAppList.this.mSections.get(sectionIndex);
            int n = getCount();
            for (int i = 0; i < n; i++) {
                if (((Row) getItem(i)).section.equals(section)) {
                    return i;
                }
            }
            return 0;
        }

        public int getSectionForPosition(int position) {
            return NotificationAppList.this.mSections.indexOf(((Row) getItem(position)).section);
        }
    }

    private static class ViewHolder {
        ImageView icon;
        ViewGroup row;
        View rowDivider;
        TextView subtitle;
        TextView title;

        private ViewHolder() {
        }
    }

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.mContext = getActivity();
        this.mInflater = (LayoutInflater) this.mContext.getSystemService("layout_inflater");
        this.mAdapter = new NotificationAppAdapter(this.mContext);
        this.mUM = UserManager.get(this.mContext);
        this.mPM = this.mContext.getPackageManager();
        this.mLauncherApps = (LauncherApps) this.mContext.getSystemService("launcherapps");
        getActivity().setTitle(R.string.app_notifications_title);
    }

    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.notification_app_list, container, false);
    }

    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        this.mProfileSpinnerAdapter = Utils.createUserSpinnerAdapter(this.mUM, this.mContext);
        if (this.mProfileSpinnerAdapter != null) {
            Spinner spinner = (Spinner) getActivity().getLayoutInflater().inflate(R.layout.spinner_view, null);
            spinner.setAdapter(this.mProfileSpinnerAdapter);
            spinner.setOnItemSelectedListener(this);
            setPinnedHeaderView(spinner);
        }
    }

    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        repositionScrollbar();
        getListView().setAdapter(this.mAdapter);
    }

    public void onPause() {
        super.onPause();
        if (DEBUG) {
            Log.d("NotificationAppList", "Saving listView state");
        }
        this.mListViewState = getListView().onSaveInstanceState();
    }

    public void onDestroyView() {
        super.onDestroyView();
        this.mListViewState = null;
    }

    public void onResume() {
        super.onResume();
        loadAppsList();
    }

    public void onItemSelected(AdapterView<?> adapterView, View view, int position, long id) {
        UserHandle selectedUser = this.mProfileSpinnerAdapter.getUserHandle(position);
        if (selectedUser.getIdentifier() != UserHandle.myUserId()) {
            Intent intent = new Intent(getActivity(), NotificationAppListActivity.class);
            intent.addFlags(268435456);
            intent.addFlags(32768);
            this.mContext.startActivityAsUser(intent, selectedUser);
        }
    }

    public void onNothingSelected(AdapterView<?> adapterView) {
    }

    private void loadAppsList() {
        AsyncTask.execute(this.mCollectAppsRunnable);
    }

    private String getSection(CharSequence label) {
        if (label == null || label.length() == 0) {
            return "*";
        }
        char c = Character.toUpperCase(label.charAt(0));
        if (c < 'A') {
            return "*";
        }
        if (c > 'Z') {
            return "**";
        }
        return Character.toString(c);
    }

    private void repositionScrollbar() {
        View parent = (View) getView().getParent();
        int eat = Math.min((int) TypedValue.applyDimension(1, (float) getListView().getScrollBarSize(), getResources().getDisplayMetrics()), parent.getPaddingEnd());
        if (eat > 0) {
            if (DEBUG) {
                Log.d("NotificationAppList", String.format("Eating %dpx into %dpx padding for %dpx scroll, ld=%d", new Object[]{Integer.valueOf(eat), Integer.valueOf(parent.getPaddingEnd()), Integer.valueOf(sbWidthPx), Integer.valueOf(getListView().getLayoutDirection())}));
            }
            parent.setPaddingRelative(parent.getPaddingStart(), parent.getPaddingTop(), parent.getPaddingEnd() - eat, parent.getPaddingBottom());
        }
    }

    public static AppRow loadAppRow(PackageManager pm, ApplicationInfo app, Backend backend) {
        AppRow row = new AppRow();
        row.pkg = app.packageName;
        row.uid = app.uid;
        try {
            row.label = app.loadLabel(pm);
        } catch (Throwable t) {
            Log.e("NotificationAppList", "Error loading application label for " + row.pkg, t);
            row.label = row.pkg;
        }
        row.icon = app.loadIcon(pm);
        row.banned = backend.getNotificationsBanned(row.pkg, row.uid);
        row.priority = backend.getHighPriority(row.pkg, row.uid);
        row.sensitive = backend.getSensitive(row.pkg, row.uid);
        return row;
    }

    public static List<ResolveInfo> queryNotificationConfigActivities(PackageManager pm) {
        if (DEBUG) {
            Log.d("NotificationAppList", "APP_NOTIFICATION_PREFS_CATEGORY_INTENT is " + APP_NOTIFICATION_PREFS_CATEGORY_INTENT);
        }
        return pm.queryIntentActivities(APP_NOTIFICATION_PREFS_CATEGORY_INTENT, 0);
    }

    public static void collectConfigActivities(PackageManager pm, ArrayMap<String, AppRow> rows) {
        applyConfigActivities(pm, rows, queryNotificationConfigActivities(pm));
    }

    public static void applyConfigActivities(PackageManager pm, ArrayMap<String, AppRow> rows, List<ResolveInfo> resolveInfos) {
        if (DEBUG) {
            Log.d("NotificationAppList", "Found " + resolveInfos.size() + " preference activities" + (resolveInfos.size() == 0 ? " ;_;" : ""));
        }
        for (ResolveInfo ri : resolveInfos) {
            ActivityInfo activityInfo = ri.activityInfo;
            AppRow row = (AppRow) rows.get(activityInfo.applicationInfo.packageName);
            if (row == null) {
                Log.v("NotificationAppList", "Ignoring notification preference activity (" + activityInfo.name + ") for unknown package " + activityInfo.packageName);
            } else if (row.settingsIntent != null) {
                Log.v("NotificationAppList", "Ignoring duplicate notification preference activity (" + activityInfo.name + ") for package " + activityInfo.packageName);
            } else {
                row.settingsIntent = new Intent(APP_NOTIFICATION_PREFS_CATEGORY_INTENT).setClassName(activityInfo.packageName, activityInfo.name);
            }
        }
    }

    private void refreshDisplayedItems() {
        if (DEBUG) {
            Log.d("NotificationAppList", "Refreshing apps...");
        }
        this.mAdapter.clear();
        synchronized (this.mSortedRows) {
            String section = null;
            int N = this.mSortedRows.size();
            boolean first = true;
            for (int i = 0; i < N; i++) {
                AppRow row = (AppRow) this.mSortedRows.get(i);
                if (!row.section.equals(section)) {
                    section = row.section;
                    Row r = new Row();
                    r.section = section;
                    this.mAdapter.add(r);
                    first = true;
                }
                row.first = first;
                this.mAdapter.add(row);
                first = false;
            }
        }
        if (this.mListViewState != null) {
            if (DEBUG) {
                Log.d("NotificationAppList", "Restoring listView state");
            }
            getListView().onRestoreInstanceState(this.mListViewState);
            this.mListViewState = null;
        }
        if (DEBUG) {
            Log.d("NotificationAppList", "Refreshed " + this.mSortedRows.size() + " displayed items");
        }
    }
}
