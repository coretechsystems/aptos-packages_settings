package com.android.settings.notification;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.INotificationManager;
import android.app.INotificationManager.Stub;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.DateTimeView;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.Utils;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class NotificationStation extends SettingsPreferenceFragment {
    private static final String TAG = NotificationStation.class.getSimpleName();
    private NotificationHistoryAdapter mAdapter;
    private Context mContext;
    private NotificationListenerService mListener = new NotificationListenerService() {
        public void onNotificationPosted(StatusBarNotification notification) {
            NotificationStation.logd("onNotificationPosted: %s", notification);
            Handler h = NotificationStation.this.getListView().getHandler();
            h.removeCallbacks(NotificationStation.this.mRefreshListRunnable);
            h.postDelayed(NotificationStation.this.mRefreshListRunnable, 100);
        }

        public void onNotificationRemoved(StatusBarNotification notification) {
            Handler h = NotificationStation.this.getListView().getHandler();
            h.removeCallbacks(NotificationStation.this.mRefreshListRunnable);
            h.postDelayed(NotificationStation.this.mRefreshListRunnable, 100);
        }
    };
    private INotificationManager mNoMan;
    private final Comparator<HistoricalNotificationInfo> mNotificationSorter = new Comparator<HistoricalNotificationInfo>() {
        public int compare(HistoricalNotificationInfo lhs, HistoricalNotificationInfo rhs) {
            return (int) (rhs.timestamp - lhs.timestamp);
        }
    };
    private PackageManager mPm;
    private Runnable mRefreshListRunnable = new Runnable() {
        public void run() {
            NotificationStation.this.refreshList();
        }
    };

    private static class HistoricalNotificationInfo {
        public boolean active;
        public Drawable icon;
        public String pkg;
        public Drawable pkgicon;
        public CharSequence pkgname;
        public int priority;
        public long timestamp;
        public CharSequence title;
        public int user;

        private HistoricalNotificationInfo() {
        }
    }

    private class NotificationHistoryAdapter extends ArrayAdapter<HistoricalNotificationInfo> {
        private final LayoutInflater mInflater;

        public NotificationHistoryAdapter(Context context) {
            super(context, 0);
            this.mInflater = (LayoutInflater) context.getSystemService("layout_inflater");
        }

        public View getView(int position, View convertView, ViewGroup parent) {
            final HistoricalNotificationInfo info = (HistoricalNotificationInfo) getItem(position);
            NotificationStation.logd("getView(%s/%s)", info.pkg, info.title);
            View row = convertView != null ? convertView : createRow(parent);
            row.setTag(info);
            if (info.icon != null) {
                ((ImageView) row.findViewById(16908294)).setImageDrawable(info.icon);
            }
            if (info.pkgicon != null) {
                ((ImageView) row.findViewById(R.id.pkgicon)).setImageDrawable(info.pkgicon);
            }
            ((DateTimeView) row.findViewById(R.id.timestamp)).setTime(info.timestamp);
            ((TextView) row.findViewById(16908310)).setText(info.title);
            ((TextView) row.findViewById(R.id.pkgname)).setText(info.pkgname);
            row.findViewById(R.id.extra).setVisibility(8);
            row.setAlpha(info.active ? 1.0f : 0.5f);
            row.setOnClickListener(new OnClickListener() {
                public void onClick(View v) {
                    v.setPressed(true);
                    NotificationStation.this.startApplicationDetailsActivity(info.pkg);
                }
            });
            return row;
        }

        private View createRow(ViewGroup parent) {
            return this.mInflater.inflate(R.layout.notification_log_row, parent, false);
        }
    }

    public void onAttach(Activity activity) {
        logd("onAttach(%s)", activity.getClass().getSimpleName());
        super.onAttach(activity);
        this.mContext = activity;
        this.mPm = this.mContext.getPackageManager();
        this.mNoMan = Stub.asInterface(ServiceManager.getService("notification"));
        try {
            this.mListener.registerAsSystemService(this.mContext, new ComponentName(this.mContext.getPackageName(), getClass().getCanonicalName()), ActivityManager.getCurrentUser());
        } catch (RemoteException e) {
            Log.e(TAG, "Cannot register listener", e);
        }
    }

    public void onDetach() {
        try {
            this.mListener.unregisterAsSystemService();
        } catch (RemoteException e) {
            Log.e(TAG, "Cannot unregister listener", e);
        }
        super.onDetach();
    }

    public void onActivityCreated(Bundle savedInstanceState) {
        logd("onActivityCreated(%s)", savedInstanceState);
        super.onActivityCreated(savedInstanceState);
        ListView listView = getListView();
        Utils.forceCustomPadding(listView, false);
        this.mAdapter = new NotificationHistoryAdapter(this.mContext);
        listView.setAdapter(this.mAdapter);
    }

    public void onResume() {
        logd("onResume()", new Object[0]);
        super.onResume();
        refreshList();
    }

    private void refreshList() {
        List<HistoricalNotificationInfo> infos = loadNotifications();
        if (infos != null) {
            logd("adding %d infos", Integer.valueOf(infos.size()));
            this.mAdapter.clear();
            this.mAdapter.addAll(infos);
            this.mAdapter.sort(this.mNotificationSorter);
        }
    }

    private static void logd(String msg, Object... args) {
    }

    private List<HistoricalNotificationInfo> loadNotifications() {
        int currentUserId = ActivityManager.getCurrentUser();
        try {
            StatusBarNotification[] active = this.mNoMan.getActiveNotifications(this.mContext.getPackageName());
            List<HistoricalNotificationInfo> arrayList = new ArrayList(active.length + this.mNoMan.getHistoricalNotifications(this.mContext.getPackageName(), 50).length);
            for (StatusBarNotification[] resultset : new StatusBarNotification[][]{active, dismissed}) {
                for (StatusBarNotification sbn : resultset) {
                    HistoricalNotificationInfo info = new HistoricalNotificationInfo();
                    info.pkg = sbn.getPackageName();
                    info.user = sbn.getUserId();
                    info.icon = loadIconDrawable(info.pkg, info.user, sbn.getNotification().icon);
                    info.pkgicon = loadPackageIconDrawable(info.pkg, info.user);
                    info.pkgname = loadPackageName(info.pkg);
                    if (sbn.getNotification().extras != null) {
                        info.title = sbn.getNotification().extras.getString("android.title");
                        if (info.title == null || "".equals(info.title)) {
                            info.title = sbn.getNotification().extras.getString("android.text");
                        }
                    }
                    if (info.title == null || "".equals(info.title)) {
                        info.title = sbn.getNotification().tickerText;
                    }
                    if (info.title == null || "".equals(info.title)) {
                        info.title = info.pkgname;
                    }
                    info.timestamp = sbn.getPostTime();
                    info.priority = sbn.getNotification().priority;
                    logd("   [%d] %s: %s", Long.valueOf(info.timestamp), info.pkg, info.title);
                    info.active = resultset == active;
                    if (info.user == -1 || info.user == currentUserId) {
                        arrayList.add(info);
                    }
                }
            }
            return arrayList;
        } catch (RemoteException e) {
            Log.e(TAG, "Cannot load Notifications: ", e);
            return null;
        }
    }

    private Resources getResourcesForUserPackage(String pkg, int userId) {
        Resources r;
        if (pkg != null) {
            if (userId == -1) {
                userId = 0;
            }
            try {
                r = this.mPm.getResourcesForApplicationAsUser(pkg, userId);
            } catch (NameNotFoundException ex) {
                Log.e(TAG, "Icon package not found: " + pkg, ex);
                return null;
            }
        }
        r = this.mContext.getResources();
        return r;
    }

    private Drawable loadPackageIconDrawable(String pkg, int userId) {
        Drawable icon = null;
        try {
            icon = this.mPm.getApplicationIcon(pkg);
        } catch (NameNotFoundException e) {
            Log.e(TAG, "Cannot get application icon", e);
        }
        return icon;
    }

    private CharSequence loadPackageName(String pkg) {
        try {
            ApplicationInfo info = this.mPm.getApplicationInfo(pkg, 8192);
            if (info != null) {
                pkg = this.mPm.getApplicationLabel(info);
            }
        } catch (NameNotFoundException e) {
            Log.e(TAG, "Cannot load package name", e);
        }
        return pkg;
    }

    private Drawable loadIconDrawable(String pkg, int userId, int resId) {
        Resources r = getResourcesForUserPackage(pkg, userId);
        if (resId == 0) {
            return null;
        }
        try {
            return r.getDrawable(resId);
        } catch (RuntimeException e) {
            Log.w(TAG, "Icon not found in " + (pkg != null ? Integer.valueOf(resId) : "<system>") + ": " + Integer.toHexString(resId), e);
            return null;
        }
    }

    private void startApplicationDetailsActivity(String packageName) {
        Intent intent = new Intent("android.settings.APPLICATION_DETAILS_SETTINGS", Uri.fromParts("package", packageName, null));
        intent.setComponent(intent.resolveActivity(this.mPm));
        startActivity(intent);
    }
}
