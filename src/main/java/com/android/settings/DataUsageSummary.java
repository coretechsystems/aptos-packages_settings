package com.android.settings;

import android.animation.LayoutTransition;
import android.app.ActivityManager;
import android.app.AlertDialog.Builder;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.app.LoaderManager.LoaderCallbacks;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.Loader;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.ConnectivityManager;
import android.net.INetworkStatsService;
import android.net.INetworkStatsSession;
import android.net.NetworkPolicy;
import android.net.NetworkPolicyManager;
import android.net.NetworkStats;
import android.net.NetworkStats.Entry;
import android.net.NetworkStatsHistory;
import android.net.NetworkTemplate;
import android.net.TrafficStats;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.INetworkManagementService;
import android.os.INetworkManagementService.Stub;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.Parcelable.Creator;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.os.UserManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.text.format.Time;
import android.util.Log;
import android.util.SparseArray;
import android.util.SparseBooleanArray;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;
import android.widget.ListView;
import android.widget.NumberPicker;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TabHost;
import android.widget.TabHost.OnTabChangeListener;
import android.widget.TabHost.TabContentFactory;
import android.widget.TabHost.TabSpec;
import android.widget.TabWidget;
import android.widget.TextView;
import com.android.internal.R;
import com.android.internal.util.Preconditions;
import com.android.settings.drawable.InsetBoundsDrawable;
import com.android.settings.net.ChartData;
import com.android.settings.net.ChartDataLoader;
import com.android.settings.net.DataUsageMeteredSettings;
import com.android.settings.net.NetworkPolicyEditor;
import com.android.settings.net.SummaryForAllUidLoader;
import com.android.settings.net.UidDetail;
import com.android.settings.net.UidDetailProvider;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settings.search.Indexable;
import com.android.settings.search.Indexable.SearchIndexProvider;
import com.android.settings.search.SearchIndexableRaw;
import com.android.settings.widget.ChartDataUsageView;
import com.android.settings.widget.ChartDataUsageView.DataUsageChartListener;
import com.google.android.collect.Lists;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Formatter;
import java.util.List;
import java.util.Locale;
import libcore.util.Objects;

public class DataUsageSummary extends HighlightingFragment implements Indexable {
    public static final SearchIndexProvider SEARCH_INDEX_DATA_PROVIDER = new BaseSearchIndexProvider() {
        public List<SearchIndexableRaw> getRawDataToIndex(Context context, boolean enabled) {
            List<SearchIndexableRaw> result = new ArrayList();
            Resources res = context.getResources();
            SearchIndexableRaw data = new SearchIndexableRaw(context);
            data.title = res.getString(R.string.data_usage_summary_title);
            data.screenTitle = res.getString(R.string.data_usage_summary_title);
            result.add(data);
            data = new SearchIndexableRaw(context);
            data.key = "data_usage_enable_mobile";
            data.title = res.getString(R.string.data_usage_enable_mobile);
            data.screenTitle = res.getString(R.string.data_usage_summary_title);
            result.add(data);
            data = new SearchIndexableRaw(context);
            data.key = "data_usage_disable_mobile_limit";
            data.title = res.getString(R.string.data_usage_disable_mobile_limit);
            data.screenTitle = res.getString(R.string.data_usage_summary_title);
            result.add(data);
            data = new SearchIndexableRaw(context);
            data.key = "data_usage_cycle";
            data.title = res.getString(R.string.data_usage_cycle);
            data.screenTitle = res.getString(R.string.data_usage_summary_title);
            result.add(data);
            return result;
        }
    };
    private static final StringBuilder sBuilder = new StringBuilder(50);
    private static final Formatter sFormatter = new Formatter(sBuilder, Locale.getDefault());
    private DataUsageAdapter mAdapter;
    private TextView mAppBackground;
    private View mAppDetail;
    private TextView mAppForeground;
    private ImageView mAppIcon;
    private Switch mAppRestrict;
    private OnClickListener mAppRestrictListener = new OnClickListener() {
        public void onClick(View v) {
            boolean restrictBackground;
            if (DataUsageSummary.this.mAppRestrict.isChecked()) {
                restrictBackground = false;
            } else {
                restrictBackground = true;
            }
            if (restrictBackground) {
                ConfirmAppRestrictFragment.show(DataUsageSummary.this);
            } else {
                DataUsageSummary.this.setAppRestrictBackground(false);
            }
        }
    };
    private View mAppRestrictView;
    private Button mAppSettings;
    private Intent mAppSettingsIntent;
    private LinearLayout mAppSwitches;
    private ViewGroup mAppTitles;
    private TextView mAppTotal;
    private boolean mBinding;
    private ChartDataUsageView mChart;
    private ChartData mChartData;
    private final LoaderCallbacks<ChartData> mChartDataCallbacks = new LoaderCallbacks<ChartData>() {
        public Loader<ChartData> onCreateLoader(int id, Bundle args) {
            return new ChartDataLoader(DataUsageSummary.this.getActivity(), DataUsageSummary.this.mStatsSession, args);
        }

        public void onLoadFinished(Loader<ChartData> loader, ChartData data) {
            DataUsageSummary.this.mChartData = data;
            DataUsageSummary.this.mChart.bindNetworkStats(DataUsageSummary.this.mChartData.network);
            DataUsageSummary.this.mChart.bindDetailNetworkStats(DataUsageSummary.this.mChartData.detail);
            DataUsageSummary.this.updatePolicy(true);
            DataUsageSummary.this.updateAppDetail();
            if (DataUsageSummary.this.mChartData.detail != null) {
                DataUsageSummary.this.mListView.smoothScrollToPosition(0);
            }
        }

        public void onLoaderReset(Loader<ChartData> loader) {
            DataUsageSummary.this.mChartData = null;
            DataUsageSummary.this.mChart.bindNetworkStats(null);
            DataUsageSummary.this.mChart.bindDetailNetworkStats(null);
        }
    };
    private DataUsageChartListener mChartListener = new DataUsageChartListener() {
        public void onWarningChanged() {
            DataUsageSummary.this.setPolicyWarningBytes(DataUsageSummary.this.mChart.getWarningBytes());
        }

        public void onLimitChanged() {
            DataUsageSummary.this.setPolicyLimitBytes(DataUsageSummary.this.mChart.getLimitBytes());
        }

        public void requestWarningEdit() {
            WarningEditorFragment.show(DataUsageSummary.this);
        }

        public void requestLimitEdit() {
            LimitEditorFragment.show(DataUsageSummary.this);
        }
    };
    private AppItem mCurrentApp = null;
    private String mCurrentTab = null;
    private CycleAdapter mCycleAdapter;
    private OnItemSelectedListener mCycleListener = new OnItemSelectedListener() {
        public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
            CycleItem cycle = (CycleItem) parent.getItemAtPosition(position);
            if (cycle instanceof CycleChangeItem) {
                CycleEditorFragment.show(DataUsageSummary.this);
                DataUsageSummary.this.mCycleSpinner.setSelection(0);
                return;
            }
            DataUsageSummary.this.mChart.setVisibleRange(cycle.start, cycle.end);
            DataUsageSummary.this.updateDetailData();
        }

        public void onNothingSelected(AdapterView<?> adapterView) {
        }
    };
    private Spinner mCycleSpinner;
    private TextView mCycleSummary;
    private View mCycleView;
    private Switch mDataEnabled;
    private OnClickListener mDataEnabledListener = new OnClickListener() {
        public void onClick(View v) {
            if (!DataUsageSummary.this.mBinding) {
                boolean dataEnabled;
                if (DataUsageSummary.this.mDataEnabled.isChecked()) {
                    dataEnabled = false;
                } else {
                    dataEnabled = true;
                }
                if ("mobile".equals(DataUsageSummary.this.mCurrentTab)) {
                    if (dataEnabled) {
                        DataUsageSummary.this.setMobileDataEnabled(true);
                    } else {
                        ConfirmDataDisableFragment.show(DataUsageSummary.this);
                    }
                }
                DataUsageSummary.this.updatePolicy(false);
            }
        }
    };
    private boolean mDataEnabledSupported;
    private View mDataEnabledView;
    private Switch mDisableAtLimit;
    private OnClickListener mDisableAtLimitListener = new OnClickListener() {
        public void onClick(View v) {
            if (!DataUsageSummary.this.mDisableAtLimit.isChecked()) {
                ConfirmLimitFragment.show(DataUsageSummary.this);
            } else {
                DataUsageSummary.this.setPolicyLimitBytes(-1);
            }
        }
    };
    private boolean mDisableAtLimitSupported;
    private View mDisableAtLimitView;
    private View mDisclaimer;
    private TextView mEmpty;
    private TabContentFactory mEmptyTabContent = new TabContentFactory() {
        public View createTabContent(String tag) {
            return new View(DataUsageSummary.this.mTabHost.getContext());
        }
    };
    private ViewGroup mHeader;
    private int mInsetSide = 0;
    private String mIntentTab = null;
    private OnItemClickListener mListListener = new OnItemClickListener() {
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            Context context = view.getContext();
            AppItem app = (AppItem) parent.getItemAtPosition(position);
            if (DataUsageSummary.this.mUidDetailProvider != null && app != null) {
                AppDetailsFragment.show(DataUsageSummary.this, app, DataUsageSummary.this.mUidDetailProvider.getUidDetail(app.key, true).label);
            }
        }
    };
    private ListView mListView;
    private MenuItem mMenuCellularNetworks;
    private MenuItem mMenuRestrictBackground;
    private MenuItem mMenuShowEthernet;
    private MenuItem mMenuShowWifi;
    private MenuItem mMenuSimCards;
    private Boolean mMobileDataEnabled;
    private INetworkManagementService mNetworkService;
    private LinearLayout mNetworkSwitches;
    private ViewGroup mNetworkSwitchesContainer;
    private NetworkPolicyEditor mPolicyEditor;
    private NetworkPolicyManager mPolicyManager;
    private SharedPreferences mPrefs;
    private boolean mShowEthernet = false;
    private boolean mShowWifi = false;
    private INetworkStatsService mStatsService;
    private INetworkStatsSession mStatsSession;
    private View mStupidPadding;
    private final LoaderCallbacks<NetworkStats> mSummaryCallbacks = new LoaderCallbacks<NetworkStats>() {
        public Loader<NetworkStats> onCreateLoader(int id, Bundle args) {
            return new SummaryForAllUidLoader(DataUsageSummary.this.getActivity(), DataUsageSummary.this.mStatsSession, args);
        }

        public void onLoadFinished(Loader<NetworkStats> loader, NetworkStats data) {
            DataUsageSummary.this.mAdapter.bindStats(data, DataUsageSummary.this.mPolicyManager.getUidsWithPolicy(1));
            updateEmptyVisible();
        }

        public void onLoaderReset(Loader<NetworkStats> loader) {
            DataUsageSummary.this.mAdapter.bindStats(null, new int[0]);
            updateEmptyVisible();
        }

        private void updateEmptyVisible() {
            boolean isEmpty;
            int i;
            int i2 = 0;
            if (!DataUsageSummary.this.mAdapter.isEmpty() || DataUsageSummary.this.isAppDetailMode()) {
                isEmpty = false;
            } else {
                isEmpty = true;
            }
            TextView access$2400 = DataUsageSummary.this.mEmpty;
            if (isEmpty) {
                i = 0;
            } else {
                i = 8;
            }
            access$2400.setVisibility(i);
            View access$2500 = DataUsageSummary.this.mStupidPadding;
            if (!isEmpty) {
                i2 = 8;
            }
            access$2500.setVisibility(i2);
        }
    };
    private TabHost mTabHost;
    private OnTabChangeListener mTabListener = new OnTabChangeListener() {
        public void onTabChanged(String tabId) {
            DataUsageSummary.this.updateBody();
        }
    };
    private TabWidget mTabWidget;
    private ViewGroup mTabsContainer;
    private TelephonyManager mTelephonyManager;
    private NetworkTemplate mTemplate;
    private UidDetailProvider mUidDetailProvider;

    public static class AppDetailsFragment extends Fragment {
        public static void show(DataUsageSummary parent, AppItem app, CharSequence label) {
            if (parent.isAdded()) {
                Bundle args = new Bundle();
                args.putParcelable("app", app);
                AppDetailsFragment fragment = new AppDetailsFragment();
                fragment.setArguments(args);
                fragment.setTargetFragment(parent, 0);
                FragmentTransaction ft = parent.getFragmentManager().beginTransaction();
                ft.add(fragment, "appDetails");
                ft.addToBackStack("appDetails");
                ft.setBreadCrumbTitle(parent.getResources().getString(R.string.data_usage_app_summary_title));
                ft.commitAllowingStateLoss();
            }
        }

        public void onStart() {
            super.onStart();
            DataUsageSummary target = (DataUsageSummary) getTargetFragment();
            target.mCurrentApp = (AppItem) getArguments().getParcelable("app");
            target.updateBody();
        }

        public void onStop() {
            super.onStop();
            DataUsageSummary target = (DataUsageSummary) getTargetFragment();
            target.mCurrentApp = null;
            target.updateBody();
        }
    }

    public static class AppItem implements Parcelable, Comparable<AppItem> {
        public static final Creator<AppItem> CREATOR = new Creator<AppItem>() {
            public AppItem createFromParcel(Parcel in) {
                return new AppItem(in);
            }

            public AppItem[] newArray(int size) {
                return new AppItem[size];
            }
        };
        public int category;
        public final int key;
        public boolean restricted;
        public long total;
        public SparseBooleanArray uids;

        public AppItem() {
            this.uids = new SparseBooleanArray();
            this.key = 0;
        }

        public AppItem(int key) {
            this.uids = new SparseBooleanArray();
            this.key = key;
        }

        public AppItem(Parcel parcel) {
            this.uids = new SparseBooleanArray();
            this.key = parcel.readInt();
            this.uids = parcel.readSparseBooleanArray();
            this.total = parcel.readLong();
        }

        public void addUid(int uid) {
            this.uids.put(uid, true);
        }

        public void writeToParcel(Parcel dest, int flags) {
            dest.writeInt(this.key);
            dest.writeSparseBooleanArray(this.uids);
            dest.writeLong(this.total);
        }

        public int describeContents() {
            return 0;
        }

        public int compareTo(AppItem another) {
            int comparison = Integer.compare(this.category, another.category);
            if (comparison == 0) {
                return Long.compare(another.total, this.total);
            }
            return comparison;
        }
    }

    public static class ConfirmAppRestrictFragment extends DialogFragment {
        public static void show(DataUsageSummary parent) {
            if (parent.isAdded()) {
                ConfirmAppRestrictFragment dialog = new ConfirmAppRestrictFragment();
                dialog.setTargetFragment(parent, 0);
                dialog.show(parent.getFragmentManager(), "confirmAppRestrict");
            }
        }

        public Dialog onCreateDialog(Bundle savedInstanceState) {
            Builder builder = new Builder(getActivity());
            builder.setTitle(R.string.data_usage_app_restrict_dialog_title);
            builder.setMessage(R.string.data_usage_app_restrict_dialog);
            builder.setPositiveButton(17039370, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    DataUsageSummary target = (DataUsageSummary) ConfirmAppRestrictFragment.this.getTargetFragment();
                    if (target != null) {
                        target.setAppRestrictBackground(true);
                    }
                }
            });
            builder.setNegativeButton(17039360, null);
            return builder.create();
        }
    }

    public static class ConfirmDataDisableFragment extends DialogFragment {
        public static void show(DataUsageSummary parent) {
            if (parent.isAdded()) {
                ConfirmDataDisableFragment dialog = new ConfirmDataDisableFragment();
                dialog.setTargetFragment(parent, 0);
                dialog.show(parent.getFragmentManager(), "confirmDataDisable");
            }
        }

        public Dialog onCreateDialog(Bundle savedInstanceState) {
            Builder builder = new Builder(getActivity());
            builder.setMessage(R.string.data_usage_disable_mobile);
            builder.setPositiveButton(17039370, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    DataUsageSummary target = (DataUsageSummary) ConfirmDataDisableFragment.this.getTargetFragment();
                    if (target != null) {
                        target.setMobileDataEnabled(false);
                    }
                }
            });
            builder.setNegativeButton(17039360, null);
            return builder.create();
        }
    }

    public static class ConfirmLimitFragment extends DialogFragment {
        public static void show(DataUsageSummary parent) {
            if (parent.isAdded()) {
                NetworkPolicy policy = parent.mPolicyEditor.getPolicy(parent.mTemplate);
                if (policy != null) {
                    CharSequence message;
                    long limitBytes;
                    Resources res = parent.getResources();
                    long minLimitBytes = (long) (((float) policy.warningBytes) * 1.2f);
                    String currentTab = parent.mCurrentTab;
                    if ("3g".equals(currentTab)) {
                        message = res.getString(R.string.data_usage_limit_dialog_mobile);
                        limitBytes = Math.max(5368709120L, minLimitBytes);
                    } else if ("4g".equals(currentTab)) {
                        message = res.getString(R.string.data_usage_limit_dialog_mobile);
                        limitBytes = Math.max(5368709120L, minLimitBytes);
                    } else if ("mobile".equals(currentTab)) {
                        message = res.getString(R.string.data_usage_limit_dialog_mobile);
                        limitBytes = Math.max(5368709120L, minLimitBytes);
                    } else {
                        throw new IllegalArgumentException("unknown current tab: " + currentTab);
                    }
                    Bundle args = new Bundle();
                    args.putCharSequence("message", message);
                    args.putLong("limitBytes", limitBytes);
                    ConfirmLimitFragment dialog = new ConfirmLimitFragment();
                    dialog.setArguments(args);
                    dialog.setTargetFragment(parent, 0);
                    dialog.show(parent.getFragmentManager(), "confirmLimit");
                }
            }
        }

        public Dialog onCreateDialog(Bundle savedInstanceState) {
            Context context = getActivity();
            CharSequence message = getArguments().getCharSequence("message");
            final long limitBytes = getArguments().getLong("limitBytes");
            Builder builder = new Builder(context);
            builder.setTitle(R.string.data_usage_limit_dialog_title);
            builder.setMessage(message);
            builder.setPositiveButton(17039370, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    DataUsageSummary target = (DataUsageSummary) ConfirmLimitFragment.this.getTargetFragment();
                    if (target != null) {
                        target.setPolicyLimitBytes(limitBytes);
                    }
                }
            });
            return builder.create();
        }
    }

    public static class ConfirmRestrictFragment extends DialogFragment {
        public static void show(DataUsageSummary parent) {
            if (parent.isAdded()) {
                ConfirmRestrictFragment dialog = new ConfirmRestrictFragment();
                dialog.setTargetFragment(parent, 0);
                dialog.show(parent.getFragmentManager(), "confirmRestrict");
            }
        }

        public Dialog onCreateDialog(Bundle savedInstanceState) {
            Context context = getActivity();
            Builder builder = new Builder(context);
            builder.setTitle(R.string.data_usage_restrict_background_title);
            if (Utils.hasMultipleUsers(context)) {
                builder.setMessage(R.string.data_usage_restrict_background_multiuser);
            } else {
                builder.setMessage(R.string.data_usage_restrict_background);
            }
            builder.setPositiveButton(17039370, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    DataUsageSummary target = (DataUsageSummary) ConfirmRestrictFragment.this.getTargetFragment();
                    if (target != null) {
                        target.setRestrictBackground(true);
                    }
                }
            });
            builder.setNegativeButton(17039360, null);
            return builder.create();
        }
    }

    public static class CycleAdapter extends ArrayAdapter<CycleItem> {
        private final CycleChangeItem mChangeItem;
        private boolean mChangePossible = false;
        private boolean mChangeVisible = false;

        public CycleAdapter(Context context) {
            super(context, R.layout.data_usage_cycle_item);
            setDropDownViewResource(R.layout.data_usage_cycle_item_dropdown);
            this.mChangeItem = new CycleChangeItem(context);
        }

        public void setChangePossible(boolean possible) {
            this.mChangePossible = possible;
            updateChange();
        }

        public void setChangeVisible(boolean visible) {
            this.mChangeVisible = visible;
            updateChange();
        }

        private void updateChange() {
            remove(this.mChangeItem);
            if (this.mChangePossible && this.mChangeVisible) {
                add(this.mChangeItem);
            }
        }

        public int findNearestPosition(CycleItem target) {
            if (target != null) {
                for (int i = getCount() - 1; i >= 0; i--) {
                    CycleItem item = (CycleItem) getItem(i);
                    if (!(item instanceof CycleChangeItem) && item.compareTo(target) >= 0) {
                        return i;
                    }
                }
            }
            return 0;
        }
    }

    public static class CycleItem implements Comparable<CycleItem> {
        public long end;
        public CharSequence label;
        public long start;

        CycleItem(CharSequence label) {
            this.label = label;
        }

        public CycleItem(Context context, long start, long end) {
            this.label = DataUsageSummary.formatDateRange(context, start, end);
            this.start = start;
            this.end = end;
        }

        public String toString() {
            return this.label.toString();
        }

        public boolean equals(Object o) {
            if (!(o instanceof CycleItem)) {
                return false;
            }
            CycleItem another = (CycleItem) o;
            if (this.start == another.start && this.end == another.end) {
                return true;
            }
            return false;
        }

        public int compareTo(CycleItem another) {
            return Long.compare(this.start, another.start);
        }
    }

    public static class CycleChangeItem extends CycleItem {
        public CycleChangeItem(Context context) {
            super(context.getString(R.string.data_usage_change_cycle));
        }
    }

    public static class CycleEditorFragment extends DialogFragment {
        public static void show(DataUsageSummary parent) {
            if (parent.isAdded()) {
                Bundle args = new Bundle();
                args.putParcelable("template", parent.mTemplate);
                CycleEditorFragment dialog = new CycleEditorFragment();
                dialog.setArguments(args);
                dialog.setTargetFragment(parent, 0);
                dialog.show(parent.getFragmentManager(), "cycleEditor");
            }
        }

        public Dialog onCreateDialog(Bundle savedInstanceState) {
            Context context = getActivity();
            final DataUsageSummary target = (DataUsageSummary) getTargetFragment();
            final NetworkPolicyEditor editor = target.mPolicyEditor;
            Builder builder = new Builder(context);
            View view = LayoutInflater.from(builder.getContext()).inflate(R.layout.data_usage_cycle_editor, null, false);
            final NumberPicker cycleDayPicker = (NumberPicker) view.findViewById(R.id.cycle_day);
            final NetworkTemplate template = (NetworkTemplate) getArguments().getParcelable("template");
            int cycleDay = editor.getPolicyCycleDay(template);
            cycleDayPicker.setMinValue(1);
            cycleDayPicker.setMaxValue(31);
            cycleDayPicker.setValue(cycleDay);
            cycleDayPicker.setWrapSelectorWheel(true);
            builder.setTitle(R.string.data_usage_cycle_editor_title);
            builder.setView(view);
            builder.setPositiveButton(R.string.data_usage_cycle_editor_positive, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    cycleDayPicker.clearFocus();
                    editor.setPolicyCycleDay(template, cycleDayPicker.getValue(), new Time().timezone);
                    target.updatePolicy(true);
                }
            });
            return builder.create();
        }
    }

    public static class DataUsageAdapter extends BaseAdapter {
        private final int mInsetSide;
        private ArrayList<AppItem> mItems = Lists.newArrayList();
        private long mLargest;
        private final UidDetailProvider mProvider;
        private final UserManager mUm;

        public DataUsageAdapter(UserManager userManager, UidDetailProvider provider, int insetSide) {
            this.mProvider = (UidDetailProvider) Preconditions.checkNotNull(provider);
            this.mInsetSide = insetSide;
            this.mUm = userManager;
        }

        public void bindStats(NetworkStats stats, int[] restrictedUids) {
            this.mItems.clear();
            this.mLargest = 0;
            int currentUserId = ActivityManager.getCurrentUser();
            List<UserHandle> profiles = this.mUm.getUserProfiles();
            SparseArray<AppItem> knownItems = new SparseArray();
            Entry entry = null;
            int size = stats != null ? stats.size() : 0;
            for (int i = 0; i < size; i++) {
                int collapseKey;
                int category;
                entry = stats.getValues(i, entry);
                int uid = entry.uid;
                int userId = UserHandle.getUserId(uid);
                if (UserHandle.isApp(uid)) {
                    if (profiles.contains(new UserHandle(userId))) {
                        if (userId != currentUserId) {
                            accumulate(UidDetailProvider.buildKeyForUser(userId), knownItems, entry, 0);
                        }
                        collapseKey = uid;
                        category = 2;
                    } else {
                        collapseKey = UidDetailProvider.buildKeyForUser(userId);
                        category = 0;
                    }
                } else if (uid == -4 || uid == -5) {
                    collapseKey = uid;
                    category = 2;
                } else {
                    collapseKey = 1000;
                    category = 2;
                }
                accumulate(collapseKey, knownItems, entry, category);
            }
            for (int uid2 : restrictedUids) {
                if (profiles.contains(new UserHandle(UserHandle.getUserId(uid2)))) {
                    AppItem item = (AppItem) knownItems.get(uid2);
                    if (item == null) {
                        item = new AppItem(uid2);
                        item.total = -1;
                        this.mItems.add(item);
                        knownItems.put(item.key, item);
                    }
                    item.restricted = true;
                }
            }
            if (!this.mItems.isEmpty()) {
                AppItem title = new AppItem();
                title.category = 1;
                this.mItems.add(title);
            }
            Collections.sort(this.mItems);
            notifyDataSetChanged();
        }

        private void accumulate(int collapseKey, SparseArray<AppItem> knownItems, Entry entry, int itemCategory) {
            int uid = entry.uid;
            AppItem item = (AppItem) knownItems.get(collapseKey);
            if (item == null) {
                item = new AppItem(collapseKey);
                item.category = itemCategory;
                this.mItems.add(item);
                knownItems.put(item.key, item);
            }
            item.addUid(uid);
            item.total += entry.rxBytes + entry.txBytes;
            if (this.mLargest < item.total) {
                this.mLargest = item.total;
            }
        }

        public int getCount() {
            return this.mItems.size();
        }

        public Object getItem(int position) {
            return this.mItems.get(position);
        }

        public long getItemId(int position) {
            return (long) ((AppItem) this.mItems.get(position)).key;
        }

        public int getViewTypeCount() {
            return 2;
        }

        public int getItemViewType(int position) {
            if (((AppItem) this.mItems.get(position)).category == 1) {
                return 1;
            }
            return 0;
        }

        public boolean areAllItemsEnabled() {
            return false;
        }

        public boolean isEnabled(int position) {
            if (position <= this.mItems.size()) {
                return getItemViewType(position) == 0;
            } else {
                throw new ArrayIndexOutOfBoundsException();
            }
        }

        public View getView(int position, View convertView, ViewGroup parent) {
            int percentTotal = 0;
            AppItem item = (AppItem) this.mItems.get(position);
            if (getItemViewType(position) == 1) {
                if (convertView == null) {
                    convertView = DataUsageSummary.inflateCategoryHeader(LayoutInflater.from(parent.getContext()), parent);
                }
                ((TextView) convertView.findViewById(16908310)).setText(R.string.data_usage_app);
            } else {
                if (convertView == null) {
                    convertView = LayoutInflater.from(parent.getContext()).inflate(R.layout.data_usage_item, parent, false);
                    if (this.mInsetSide > 0) {
                        convertView.setPaddingRelative(this.mInsetSide, 0, this.mInsetSide, 0);
                    }
                }
                Context context = parent.getContext();
                TextView text1 = (TextView) convertView.findViewById(16908308);
                ProgressBar progress = (ProgressBar) convertView.findViewById(16908301);
                UidDetailTask.bindView(this.mProvider, item, convertView);
                if (!item.restricted || item.total > 0) {
                    text1.setText(android.text.format.Formatter.formatFileSize(context, item.total));
                    progress.setVisibility(0);
                } else {
                    text1.setText(R.string.data_usage_app_restricted);
                    progress.setVisibility(8);
                }
                if (this.mLargest != 0) {
                    percentTotal = (int) ((item.total * 100) / this.mLargest);
                }
                progress.setProgress(percentTotal);
            }
            return convertView;
        }
    }

    public static class DeniedRestrictFragment extends DialogFragment {
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            Builder builder = new Builder(getActivity());
            builder.setTitle(R.string.data_usage_app_restrict_background);
            builder.setMessage(R.string.data_usage_restrict_denied_dialog);
            builder.setPositiveButton(17039370, null);
            return builder.create();
        }
    }

    public static class LimitEditorFragment extends DialogFragment {
        public static void show(DataUsageSummary parent) {
            if (parent.isAdded()) {
                Bundle args = new Bundle();
                args.putParcelable("template", parent.mTemplate);
                LimitEditorFragment dialog = new LimitEditorFragment();
                dialog.setArguments(args);
                dialog.setTargetFragment(parent, 0);
                dialog.show(parent.getFragmentManager(), "limitEditor");
            }
        }

        public Dialog onCreateDialog(Bundle savedInstanceState) {
            Context context = getActivity();
            final DataUsageSummary target = (DataUsageSummary) getTargetFragment();
            final NetworkPolicyEditor editor = target.mPolicyEditor;
            Builder builder = new Builder(context);
            View view = LayoutInflater.from(builder.getContext()).inflate(R.layout.data_usage_bytes_editor, null, false);
            final NumberPicker bytesPicker = (NumberPicker) view.findViewById(R.id.bytes);
            final NetworkTemplate template = (NetworkTemplate) getArguments().getParcelable("template");
            long warningBytes = editor.getPolicyWarningBytes(template);
            long limitBytes = editor.getPolicyLimitBytes(template);
            bytesPicker.setMaxValue(Integer.MAX_VALUE);
            if (warningBytes == -1 || limitBytes <= 0) {
                bytesPicker.setMinValue(0);
            } else {
                bytesPicker.setMinValue(((int) (warningBytes / 1048576)) + 1);
            }
            bytesPicker.setValue((int) (limitBytes / 1048576));
            bytesPicker.setWrapSelectorWheel(false);
            builder.setTitle(R.string.data_usage_limit_editor_title);
            builder.setView(view);
            builder.setPositiveButton(R.string.data_usage_cycle_editor_positive, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    bytesPicker.clearFocus();
                    editor.setPolicyLimitBytes(template, ((long) bytesPicker.getValue()) * 1048576);
                    target.updatePolicy(false);
                }
            });
            return builder.create();
        }
    }

    private static class UidDetailTask extends AsyncTask<Void, Void, UidDetail> {
        private final AppItem mItem;
        private final UidDetailProvider mProvider;
        private final View mTarget;

        private UidDetailTask(UidDetailProvider provider, AppItem item, View target) {
            this.mProvider = (UidDetailProvider) Preconditions.checkNotNull(provider);
            this.mItem = (AppItem) Preconditions.checkNotNull(item);
            this.mTarget = (View) Preconditions.checkNotNull(target);
        }

        public static void bindView(UidDetailProvider provider, AppItem item, View target) {
            UidDetailTask existing = (UidDetailTask) target.getTag();
            if (existing != null) {
                existing.cancel(false);
            }
            UidDetail cachedDetail = provider.getUidDetail(item.key, false);
            if (cachedDetail != null) {
                bindView(cachedDetail, target);
            } else {
                target.setTag(new UidDetailTask(provider, item, target).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, new Void[0]));
            }
        }

        private static void bindView(UidDetail detail, View target) {
            ImageView icon = (ImageView) target.findViewById(16908294);
            TextView title = (TextView) target.findViewById(16908310);
            if (detail != null) {
                icon.setImageDrawable(detail.icon);
                title.setText(detail.label);
                title.setContentDescription(detail.contentDescription);
                return;
            }
            icon.setImageDrawable(null);
            title.setText(null);
        }

        protected void onPreExecute() {
            bindView(null, this.mTarget);
        }

        protected UidDetail doInBackground(Void... params) {
            return this.mProvider.getUidDetail(this.mItem.key, true);
        }

        protected void onPostExecute(UidDetail result) {
            bindView(result, this.mTarget);
        }
    }

    public static class WarningEditorFragment extends DialogFragment {
        public static void show(DataUsageSummary parent) {
            if (parent.isAdded()) {
                Bundle args = new Bundle();
                args.putParcelable("template", parent.mTemplate);
                WarningEditorFragment dialog = new WarningEditorFragment();
                dialog.setArguments(args);
                dialog.setTargetFragment(parent, 0);
                dialog.show(parent.getFragmentManager(), "warningEditor");
            }
        }

        public Dialog onCreateDialog(Bundle savedInstanceState) {
            Context context = getActivity();
            final DataUsageSummary target = (DataUsageSummary) getTargetFragment();
            final NetworkPolicyEditor editor = target.mPolicyEditor;
            Builder builder = new Builder(context);
            View view = LayoutInflater.from(builder.getContext()).inflate(R.layout.data_usage_bytes_editor, null, false);
            final NumberPicker bytesPicker = (NumberPicker) view.findViewById(R.id.bytes);
            final NetworkTemplate template = (NetworkTemplate) getArguments().getParcelable("template");
            long warningBytes = editor.getPolicyWarningBytes(template);
            long limitBytes = editor.getPolicyLimitBytes(template);
            bytesPicker.setMinValue(0);
            if (limitBytes != -1) {
                bytesPicker.setMaxValue(((int) (limitBytes / 1048576)) - 1);
            } else {
                bytesPicker.setMaxValue(Integer.MAX_VALUE);
            }
            bytesPicker.setValue((int) (warningBytes / 1048576));
            bytesPicker.setWrapSelectorWheel(false);
            builder.setTitle(R.string.data_usage_warning_editor_title);
            builder.setView(view);
            builder.setPositiveButton(R.string.data_usage_cycle_editor_positive, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    bytesPicker.clearFocus();
                    editor.setPolicyWarningBytes(template, ((long) bytesPicker.getValue()) * 1048576);
                    target.updatePolicy(false);
                }
            });
            return builder.create();
        }
    }

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Context context = getActivity();
        this.mNetworkService = Stub.asInterface(ServiceManager.getService("network_management"));
        this.mStatsService = INetworkStatsService.Stub.asInterface(ServiceManager.getService("netstats"));
        this.mPolicyManager = NetworkPolicyManager.from(context);
        this.mTelephonyManager = TelephonyManager.from(context);
        this.mPrefs = getActivity().getSharedPreferences("data_usage", 0);
        this.mPolicyEditor = new NetworkPolicyEditor(this.mPolicyManager);
        this.mPolicyEditor.read();
        try {
            if (!this.mNetworkService.isBandwidthControlEnabled()) {
                Log.w("DataUsage", "No bandwidth control; leaving");
                getActivity().finish();
            }
        } catch (RemoteException e) {
            Log.w("DataUsage", "No bandwidth control; leaving");
            getActivity().finish();
        }
        try {
            this.mStatsSession = this.mStatsService.openSession();
            this.mShowWifi = this.mPrefs.getBoolean("show_wifi", false);
            this.mShowEthernet = this.mPrefs.getBoolean("show_ethernet", false);
            if (!hasReadyMobileRadio(context)) {
                this.mShowWifi = true;
                this.mShowEthernet = true;
            }
            setHasOptionsMenu(true);
        } catch (RemoteException e2) {
            throw new RuntimeException(e2);
        }
    }

    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        Context context = inflater.getContext();
        View view = inflater.inflate(R.layout.data_usage_summary, container, false);
        this.mUidDetailProvider = new UidDetailProvider(context);
        this.mTabHost = (TabHost) view.findViewById(16908306);
        this.mTabsContainer = (ViewGroup) view.findViewById(R.id.tabs_container);
        this.mTabWidget = (TabWidget) view.findViewById(16908307);
        this.mListView = (ListView) view.findViewById(16908298);
        boolean shouldInset;
        if (this.mListView.getScrollBarStyle() == 33554432) {
            shouldInset = true;
        } else {
            shouldInset = false;
        }
        this.mInsetSide = 0;
        Utils.prepareCustomPreferencesList(container, view, this.mListView, false);
        this.mTabHost.setup();
        this.mTabHost.setOnTabChangedListener(this.mTabListener);
        this.mHeader = (ViewGroup) inflater.inflate(R.layout.data_usage_header, this.mListView, false);
        this.mHeader.setClickable(true);
        this.mListView.addHeaderView(new View(context), null, true);
        this.mListView.addHeaderView(this.mHeader, null, true);
        this.mListView.setItemsCanFocus(true);
        if (this.mInsetSide > 0) {
            insetListViewDrawables(this.mListView, this.mInsetSide);
            this.mHeader.setPaddingRelative(this.mInsetSide, 0, this.mInsetSide, 0);
        }
        this.mNetworkSwitchesContainer = (ViewGroup) this.mHeader.findViewById(R.id.network_switches_container);
        this.mNetworkSwitches = (LinearLayout) this.mHeader.findViewById(R.id.network_switches);
        this.mDataEnabled = new Switch(inflater.getContext());
        this.mDataEnabled.setClickable(false);
        this.mDataEnabled.setFocusable(false);
        this.mDataEnabledView = inflatePreference(inflater, this.mNetworkSwitches, this.mDataEnabled);
        this.mDataEnabledView.setTag(R.id.preference_highlight_key, "data_usage_enable_mobile");
        this.mDataEnabledView.setClickable(true);
        this.mDataEnabledView.setFocusable(true);
        this.mDataEnabledView.setOnClickListener(this.mDataEnabledListener);
        this.mNetworkSwitches.addView(this.mDataEnabledView);
        this.mDisableAtLimit = new Switch(inflater.getContext());
        this.mDisableAtLimit.setClickable(false);
        this.mDisableAtLimit.setFocusable(false);
        this.mDisableAtLimitView = inflatePreference(inflater, this.mNetworkSwitches, this.mDisableAtLimit);
        this.mDisableAtLimitView.setTag(R.id.preference_highlight_key, "data_usage_disable_mobile_limit");
        this.mDisableAtLimitView.setClickable(true);
        this.mDisableAtLimitView.setFocusable(true);
        this.mDisableAtLimitView.setOnClickListener(this.mDisableAtLimitListener);
        this.mNetworkSwitches.addView(this.mDisableAtLimitView);
        this.mCycleView = inflater.inflate(R.layout.data_usage_cycles, this.mNetworkSwitches, false);
        this.mCycleView.setTag(R.id.preference_highlight_key, "data_usage_cycle");
        this.mCycleSpinner = (Spinner) this.mCycleView.findViewById(R.id.cycles_spinner);
        this.mCycleAdapter = new CycleAdapter(context);
        this.mCycleSpinner.setAdapter(this.mCycleAdapter);
        this.mCycleSpinner.setOnItemSelectedListener(this.mCycleListener);
        this.mCycleSummary = (TextView) this.mCycleView.findViewById(R.id.cycle_summary);
        this.mNetworkSwitches.addView(this.mCycleView);
        this.mChart = (ChartDataUsageView) this.mHeader.findViewById(R.id.chart);
        this.mChart.setListener(this.mChartListener);
        this.mChart.bindNetworkPolicy(null);
        this.mAppDetail = this.mHeader.findViewById(R.id.app_detail);
        this.mAppIcon = (ImageView) this.mAppDetail.findViewById(R.id.app_icon);
        this.mAppTitles = (ViewGroup) this.mAppDetail.findViewById(R.id.app_titles);
        this.mAppForeground = (TextView) this.mAppDetail.findViewById(R.id.app_foreground);
        this.mAppBackground = (TextView) this.mAppDetail.findViewById(R.id.app_background);
        this.mAppSwitches = (LinearLayout) this.mAppDetail.findViewById(R.id.app_switches);
        this.mAppSettings = (Button) this.mAppDetail.findViewById(R.id.app_settings);
        this.mAppRestrict = new Switch(inflater.getContext());
        this.mAppRestrict.setClickable(false);
        this.mAppRestrict.setFocusable(false);
        this.mAppRestrictView = inflatePreference(inflater, this.mAppSwitches, this.mAppRestrict);
        this.mAppRestrictView.setClickable(true);
        this.mAppRestrictView.setFocusable(true);
        this.mAppRestrictView.setOnClickListener(this.mAppRestrictListener);
        this.mAppSwitches.addView(this.mAppRestrictView);
        this.mDisclaimer = this.mHeader.findViewById(R.id.disclaimer);
        this.mEmpty = (TextView) this.mHeader.findViewById(16908292);
        this.mStupidPadding = this.mHeader.findViewById(R.id.stupid_padding);
        this.mAdapter = new DataUsageAdapter((UserManager) context.getSystemService("user"), this.mUidDetailProvider, this.mInsetSide);
        this.mListView.setOnItemClickListener(this.mListListener);
        this.mListView.setAdapter(this.mAdapter);
        return view;
    }

    public void onViewStateRestored(Bundle savedInstanceState) {
        super.onViewStateRestored(savedInstanceState);
        this.mIntentTab = computeTabFromIntent(getActivity().getIntent());
        updateTabs();
    }

    public void onResume() {
        super.onResume();
        getView().post(new Runnable() {
            public void run() {
                DataUsageSummary.this.highlightViewIfNeeded();
            }
        });
        new AsyncTask<Void, Void, Void>() {
            protected Void doInBackground(Void... params) {
                try {
                    Thread.sleep(2000);
                    DataUsageSummary.this.mStatsService.forceUpdate();
                } catch (InterruptedException e) {
                } catch (RemoteException e2) {
                }
                return null;
            }

            protected void onPostExecute(Void result) {
                if (DataUsageSummary.this.isAdded()) {
                    DataUsageSummary.this.updateBody();
                }
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, new Void[0]);
    }

    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.data_usage, menu);
    }

    public void onPrepareOptionsMenu(Menu menu) {
        MenuItem menuItem;
        boolean z;
        boolean z2 = true;
        Context context = getActivity();
        boolean appDetailMode = isAppDetailMode();
        boolean isOwner;
        if (ActivityManager.getCurrentUser() == 0) {
            isOwner = true;
        } else {
            isOwner = false;
        }
        this.mMenuShowWifi = menu.findItem(R.id.data_usage_menu_show_wifi);
        if (hasWifiRadio(context) && hasReadyMobileRadio(context)) {
            menuItem = this.mMenuShowWifi;
            if (appDetailMode) {
                z = false;
            } else {
                z = true;
            }
            menuItem.setVisible(z);
        } else {
            this.mMenuShowWifi.setVisible(false);
        }
        this.mMenuShowEthernet = menu.findItem(R.id.data_usage_menu_show_ethernet);
        if (hasEthernet(context) && hasReadyMobileRadio(context)) {
            menuItem = this.mMenuShowEthernet;
            if (appDetailMode) {
                z = false;
            } else {
                z = true;
            }
            menuItem.setVisible(z);
        } else {
            this.mMenuShowEthernet.setVisible(false);
        }
        this.mMenuRestrictBackground = menu.findItem(R.id.data_usage_menu_restrict_background);
        menuItem = this.mMenuRestrictBackground;
        if (hasReadyMobileRadio(context) && isOwner && !appDetailMode) {
            z = true;
        } else {
            z = false;
        }
        menuItem.setVisible(z);
        MenuItem metered = menu.findItem(R.id.data_usage_menu_metered);
        if (hasReadyMobileRadio(context) || hasWifiRadio(context)) {
            if (appDetailMode) {
                z = false;
            } else {
                z = true;
            }
            metered.setVisible(z);
        } else {
            metered.setVisible(false);
        }
        this.mMenuSimCards = menu.findItem(R.id.data_usage_menu_sim_cards);
        this.mMenuSimCards.setVisible(false);
        this.mMenuCellularNetworks = menu.findItem(R.id.data_usage_menu_cellular_networks);
        MenuItem menuItem2 = this.mMenuCellularNetworks;
        if (!(hasReadyMobileRadio(context) && !appDetailMode && isOwner)) {
            z2 = false;
        }
        menuItem2.setVisible(z2);
        MenuItem help = menu.findItem(R.id.data_usage_menu_help);
        String helpUrl = getResources().getString(R.string.help_url_data_usage);
        if (TextUtils.isEmpty(helpUrl)) {
            help.setVisible(false);
        } else {
            HelpUtils.prepareHelpMenuItem(context, help, helpUrl);
        }
        updateMenuTitles();
    }

    private void updateMenuTitles() {
        if (this.mPolicyManager.getRestrictBackground()) {
            this.mMenuRestrictBackground.setTitle(R.string.data_usage_menu_allow_background);
        } else {
            this.mMenuRestrictBackground.setTitle(R.string.data_usage_menu_restrict_background);
        }
        if (this.mShowWifi) {
            this.mMenuShowWifi.setTitle(R.string.data_usage_menu_hide_wifi);
        } else {
            this.mMenuShowWifi.setTitle(R.string.data_usage_menu_show_wifi);
        }
        if (this.mShowEthernet) {
            this.mMenuShowEthernet.setTitle(R.string.data_usage_menu_hide_ethernet);
        } else {
            this.mMenuShowEthernet.setTitle(R.string.data_usage_menu_show_ethernet);
        }
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        boolean z = false;
        switch (item.getItemId()) {
            case R.id.data_usage_menu_restrict_background:
                boolean restrictBackground;
                if (this.mPolicyManager.getRestrictBackground()) {
                    restrictBackground = false;
                } else {
                    restrictBackground = true;
                }
                if (restrictBackground) {
                    ConfirmRestrictFragment.show(this);
                    return true;
                }
                setRestrictBackground(false);
                return true;
            case R.id.data_usage_menu_show_wifi:
                if (!this.mShowWifi) {
                    z = true;
                }
                this.mShowWifi = z;
                this.mPrefs.edit().putBoolean("show_wifi", this.mShowWifi).apply();
                updateMenuTitles();
                updateTabs();
                return true;
            case R.id.data_usage_menu_show_ethernet:
                if (!this.mShowEthernet) {
                    z = true;
                }
                this.mShowEthernet = z;
                this.mPrefs.edit().putBoolean("show_ethernet", this.mShowEthernet).apply();
                updateMenuTitles();
                updateTabs();
                return true;
            case R.id.data_usage_menu_metered:
                ((SettingsActivity) getActivity()).startPreferencePanel(DataUsageMeteredSettings.class.getCanonicalName(), null, R.string.data_usage_metered_title, null, this, 0);
                return true;
            case R.id.data_usage_menu_sim_cards:
                return true;
            case R.id.data_usage_menu_cellular_networks:
                Intent intent = new Intent("android.intent.action.MAIN");
                intent.setComponent(new ComponentName("com.android.phone", "com.android.phone.MobileNetworkSettings"));
                startActivity(intent);
                return true;
            default:
                return false;
        }
    }

    public void onDestroy() {
        this.mDataEnabledView = null;
        this.mDisableAtLimitView = null;
        this.mUidDetailProvider.clearCache();
        this.mUidDetailProvider = null;
        TrafficStats.closeQuietly(this.mStatsSession);
        super.onDestroy();
    }

    private void ensureLayoutTransitions() {
        if (this.mChart.getLayoutTransition() == null) {
            this.mTabsContainer.setLayoutTransition(buildLayoutTransition());
            this.mHeader.setLayoutTransition(buildLayoutTransition());
            this.mNetworkSwitchesContainer.setLayoutTransition(buildLayoutTransition());
            LayoutTransition chartTransition = buildLayoutTransition();
            chartTransition.disableTransitionType(2);
            chartTransition.disableTransitionType(3);
            this.mChart.setLayoutTransition(chartTransition);
        }
    }

    private static LayoutTransition buildLayoutTransition() {
        LayoutTransition transition = new LayoutTransition();
        transition.setAnimateParentHierarchy(false);
        return transition;
    }

    private void updateTabs() {
        boolean noTabs;
        boolean multipleTabs;
        int i = 0;
        Context context = getActivity();
        this.mTabHost.clearAllTabs();
        if (isMobilePolicySplit() && hasReadyMobile4gRadio(context)) {
            this.mTabHost.addTab(buildTabSpec("3g", R.string.data_usage_tab_3g));
            this.mTabHost.addTab(buildTabSpec("4g", R.string.data_usage_tab_4g));
        } else if (hasReadyMobileRadio(context)) {
            this.mTabHost.addTab(buildTabSpec("mobile", R.string.data_usage_tab_mobile));
        }
        if (this.mShowWifi && hasWifiRadio(context)) {
            this.mTabHost.addTab(buildTabSpec("wifi", R.string.data_usage_tab_wifi));
        }
        if (this.mShowEthernet && hasEthernet(context)) {
            this.mTabHost.addTab(buildTabSpec("ethernet", R.string.data_usage_tab_ethernet));
        }
        if (this.mTabWidget.getTabCount() == 0) {
            noTabs = true;
        } else {
            noTabs = false;
        }
        if (this.mTabWidget.getTabCount() > 1) {
            multipleTabs = true;
        } else {
            multipleTabs = false;
        }
        TabWidget tabWidget = this.mTabWidget;
        if (!multipleTabs) {
            i = 8;
        }
        tabWidget.setVisibility(i);
        if (this.mIntentTab != null) {
            if (Objects.equal(this.mIntentTab, this.mTabHost.getCurrentTabTag())) {
                updateBody();
            } else {
                this.mTabHost.setCurrentTabByTag(this.mIntentTab);
            }
            this.mIntentTab = null;
        } else if (noTabs) {
            updateBody();
        }
    }

    private TabSpec buildTabSpec(String tag, int titleRes) {
        return this.mTabHost.newTabSpec(tag).setIndicator(getText(titleRes)).setContent(this.mEmptyTabContent);
    }

    private void updateBody() {
        this.mBinding = true;
        if (isAdded()) {
            boolean isOwner;
            Context context = getActivity();
            String currentTab = this.mTabHost.getCurrentTabTag();
            if (ActivityManager.getCurrentUser() == 0) {
                isOwner = true;
            } else {
                isOwner = false;
            }
            if (currentTab == null) {
                Log.w("DataUsage", "no tab selected; hiding body");
                this.mListView.setVisibility(8);
                return;
            }
            this.mListView.setVisibility(0);
            this.mCurrentTab = currentTab;
            this.mDataEnabledSupported = isOwner;
            this.mDisableAtLimitSupported = true;
            if ("mobile".equals(currentTab)) {
                setPreferenceTitle(this.mDataEnabledView, R.string.data_usage_enable_mobile);
                setPreferenceTitle(this.mDisableAtLimitView, R.string.data_usage_disable_mobile_limit);
                this.mTemplate = NetworkTemplate.buildTemplateMobileAll(getActiveSubscriberId(context));
            } else if ("3g".equals(currentTab)) {
                setPreferenceTitle(this.mDataEnabledView, R.string.data_usage_enable_3g);
                setPreferenceTitle(this.mDisableAtLimitView, R.string.data_usage_disable_3g_limit);
                this.mTemplate = NetworkTemplate.buildTemplateMobile3gLower(getActiveSubscriberId(context));
            } else if ("4g".equals(currentTab)) {
                setPreferenceTitle(this.mDataEnabledView, R.string.data_usage_enable_4g);
                setPreferenceTitle(this.mDisableAtLimitView, R.string.data_usage_disable_4g_limit);
                this.mTemplate = NetworkTemplate.buildTemplateMobile4g(getActiveSubscriberId(context));
            } else if ("wifi".equals(currentTab)) {
                this.mDataEnabledSupported = false;
                this.mDisableAtLimitSupported = false;
                this.mTemplate = NetworkTemplate.buildTemplateWifiWildcard();
            } else if ("ethernet".equals(currentTab)) {
                this.mDataEnabledSupported = false;
                this.mDisableAtLimitSupported = false;
                this.mTemplate = NetworkTemplate.buildTemplateEthernet();
            } else {
                throw new IllegalStateException("unknown tab: " + currentTab);
            }
            getLoaderManager().restartLoader(2, ChartDataLoader.buildArgs(this.mTemplate, this.mCurrentApp), this.mChartDataCallbacks);
            getActivity().invalidateOptionsMenu();
            this.mBinding = false;
        }
    }

    private boolean isAppDetailMode() {
        return this.mCurrentApp != null;
    }

    private void updateAppDetail() {
        Context context = getActivity();
        PackageManager pm = context.getPackageManager();
        LayoutInflater inflater = getActivity().getLayoutInflater();
        if (isAppDetailMode()) {
            this.mAppDetail.setVisibility(0);
            this.mCycleAdapter.setChangeVisible(false);
            this.mChart.bindNetworkPolicy(null);
            int uid = this.mCurrentApp.key;
            UidDetail detail = this.mUidDetailProvider.getUidDetail(uid, true);
            this.mAppIcon.setImageDrawable(detail.icon);
            this.mAppTitles.removeAllViews();
            View title = null;
            TextView appTitle;
            if (detail.detailLabels != null) {
                int n = detail.detailLabels.length;
                for (int i = 0; i < n; i++) {
                    CharSequence label = detail.detailLabels[i];
                    CharSequence contentDescription = detail.detailContentDescriptions[i];
                    title = inflater.inflate(R.layout.data_usage_app_title, this.mAppTitles, false);
                    appTitle = (TextView) title.findViewById(R.id.app_title);
                    appTitle.setText(label);
                    appTitle.setContentDescription(contentDescription);
                    this.mAppTitles.addView(title);
                }
            } else {
                title = inflater.inflate(R.layout.data_usage_app_title, this.mAppTitles, false);
                appTitle = (TextView) title.findViewById(R.id.app_title);
                appTitle.setText(detail.label);
                appTitle.setContentDescription(detail.contentDescription);
                this.mAppTitles.addView(title);
            }
            if (title != null) {
                this.mAppTotal = (TextView) title.findViewById(R.id.app_summary);
            } else {
                this.mAppTotal = null;
            }
            String[] packageNames = pm.getPackagesForUid(uid);
            if (packageNames == null || packageNames.length <= 0) {
                this.mAppSettingsIntent = null;
                this.mAppSettings.setOnClickListener(null);
                this.mAppSettings.setVisibility(8);
            } else {
                this.mAppSettingsIntent = new Intent("android.intent.action.MANAGE_NETWORK_USAGE");
                this.mAppSettingsIntent.addCategory("android.intent.category.DEFAULT");
                boolean matchFound = false;
                for (String packageName : packageNames) {
                    this.mAppSettingsIntent.setPackage(packageName);
                    if (pm.resolveActivity(this.mAppSettingsIntent, 0) != null) {
                        matchFound = true;
                        break;
                    }
                }
                final int i2 = uid;
                this.mAppSettings.setOnClickListener(new OnClickListener() {
                    public void onClick(View v) {
                        if (DataUsageSummary.this.isAdded()) {
                            DataUsageSummary.this.getActivity().startActivityAsUser(DataUsageSummary.this.mAppSettingsIntent, new UserHandle(UserHandle.getUserId(i2)));
                        }
                    }
                });
                this.mAppSettings.setEnabled(matchFound);
                this.mAppSettings.setVisibility(0);
            }
            updateDetailData();
            if (UserHandle.isApp(uid) && !this.mPolicyManager.getRestrictBackground() && isBandwidthControlEnabled() && hasReadyMobileRadio(context)) {
                setPreferenceTitle(this.mAppRestrictView, R.string.data_usage_app_restrict_background);
                setPreferenceSummary(this.mAppRestrictView, getString(R.string.data_usage_app_restrict_background_summary));
                this.mAppRestrictView.setVisibility(0);
                this.mAppRestrict.setChecked(getAppRestrictBackground());
                return;
            }
            this.mAppRestrictView.setVisibility(8);
            return;
        }
        this.mAppDetail.setVisibility(8);
        this.mCycleAdapter.setChangeVisible(true);
        this.mChart.bindDetailNetworkStats(null);
    }

    private void setPolicyWarningBytes(long warningBytes) {
        this.mPolicyEditor.setPolicyWarningBytes(this.mTemplate, warningBytes);
        updatePolicy(false);
    }

    private void setPolicyLimitBytes(long limitBytes) {
        this.mPolicyEditor.setPolicyLimitBytes(this.mTemplate, limitBytes);
        updatePolicy(false);
    }

    private boolean isMobileDataEnabled() {
        if (this.mMobileDataEnabled != null) {
            return this.mMobileDataEnabled.booleanValue();
        }
        return this.mTelephonyManager.getDataEnabled();
    }

    private void setMobileDataEnabled(boolean enabled) {
        this.mTelephonyManager.setDataEnabled(enabled);
        this.mMobileDataEnabled = Boolean.valueOf(enabled);
        updatePolicy(false);
    }

    private boolean isNetworkPolicyModifiable(NetworkPolicy policy) {
        return policy != null && isBandwidthControlEnabled() && this.mDataEnabled.isChecked() && ActivityManager.getCurrentUser() == 0;
    }

    private boolean isBandwidthControlEnabled() {
        try {
            return this.mNetworkService.isBandwidthControlEnabled();
        } catch (RemoteException e) {
            Log.w("DataUsage", "problem talking with INetworkManagementService: " + e);
            return false;
        }
    }

    public void setRestrictBackground(boolean restrictBackground) {
        this.mPolicyManager.setRestrictBackground(restrictBackground);
        updateMenuTitles();
    }

    private boolean getAppRestrictBackground() {
        return (this.mPolicyManager.getUidPolicy(this.mCurrentApp.key) & 1) != 0;
    }

    private void setAppRestrictBackground(boolean restrictBackground) {
        this.mPolicyManager.setUidPolicy(this.mCurrentApp.key, restrictBackground ? 1 : 0);
        this.mAppRestrict.setChecked(restrictBackground);
    }

    private void updatePolicy(boolean refreshCycle) {
        int i;
        boolean z = true;
        int i2 = 0;
        boolean dataEnabledVisible = this.mDataEnabledSupported;
        boolean disableAtLimitVisible = this.mDisableAtLimitSupported;
        if (isAppDetailMode()) {
            dataEnabledVisible = false;
            disableAtLimitVisible = false;
        }
        if ("mobile".equals(this.mCurrentTab)) {
            this.mBinding = true;
            this.mDataEnabled.setChecked(isMobileDataEnabled());
            this.mBinding = false;
        }
        NetworkPolicy policy = this.mPolicyEditor.getPolicy(this.mTemplate);
        if (isNetworkPolicyModifiable(policy)) {
            Switch switchR = this.mDisableAtLimit;
            if (policy == null || policy.limitBytes == -1) {
                z = false;
            }
            switchR.setChecked(z);
            if (!isAppDetailMode()) {
                this.mChart.bindNetworkPolicy(policy);
            }
        } else {
            disableAtLimitVisible = false;
            this.mChart.bindNetworkPolicy(null);
        }
        View view = this.mDataEnabledView;
        if (dataEnabledVisible) {
            i = 0;
        } else {
            i = 8;
        }
        view.setVisibility(i);
        View view2 = this.mDisableAtLimitView;
        if (!disableAtLimitVisible) {
            i2 = 8;
        }
        view2.setVisibility(i2);
        if (refreshCycle) {
            updateCycleList(policy);
        }
    }

    private void updateCycleList(NetworkPolicy policy) {
        long cycleEnd;
        CycleItem previousItem = (CycleItem) this.mCycleSpinner.getSelectedItem();
        this.mCycleAdapter.clear();
        Context context = this.mCycleSpinner.getContext();
        long historyStart = Long.MAX_VALUE;
        long historyEnd = Long.MIN_VALUE;
        if (this.mChartData != null) {
            historyStart = this.mChartData.network.getStart();
            historyEnd = this.mChartData.network.getEnd();
        }
        long now = System.currentTimeMillis();
        if (historyStart == Long.MAX_VALUE) {
            historyStart = now;
        }
        if (historyEnd == Long.MIN_VALUE) {
            historyEnd = now + 1;
        }
        boolean hasCycles = false;
        if (policy != null) {
            cycleEnd = NetworkPolicyManager.computeNextCycleBoundary(historyEnd, policy);
            while (cycleEnd > historyStart) {
                long cycleStart = NetworkPolicyManager.computeLastCycleBoundary(cycleEnd, policy);
                Log.d("DataUsage", "generating cs=" + cycleStart + " to ce=" + cycleEnd + " waiting for hs=" + historyStart);
                this.mCycleAdapter.add(new CycleItem(context, cycleStart, cycleEnd));
                cycleEnd = cycleStart;
                hasCycles = true;
            }
            this.mCycleAdapter.setChangePossible(isNetworkPolicyModifiable(policy));
        }
        if (!hasCycles) {
            cycleEnd = historyEnd;
            while (cycleEnd > historyStart) {
                cycleStart = cycleEnd - 2419200000L;
                this.mCycleAdapter.add(new CycleItem(context, cycleStart, cycleEnd));
                cycleEnd = cycleStart;
            }
            this.mCycleAdapter.setChangePossible(false);
        }
        if (this.mCycleAdapter.getCount() > 0) {
            int position = this.mCycleAdapter.findNearestPosition(previousItem);
            this.mCycleSpinner.setSelection(position);
            if (Objects.equal((CycleItem) this.mCycleAdapter.getItem(position), previousItem)) {
                updateDetailData();
                return;
            } else {
                this.mCycleListener.onItemSelected(this.mCycleSpinner, null, position, 0);
                return;
            }
        }
        updateDetailData();
    }

    private void updateDetailData() {
        long start = this.mChart.getInspectStart();
        long end = this.mChart.getInspectEnd();
        long now = System.currentTimeMillis();
        Context context = getActivity();
        NetworkStatsHistory.Entry entry = null;
        if (!isAppDetailMode() || this.mChartData == null || this.mChartData.detail == null) {
            if (this.mChartData != null) {
                entry = this.mChartData.network.getValues(start, end, now, null);
            }
            this.mCycleSummary.setVisibility(0);
            getLoaderManager().restartLoader(3, SummaryForAllUidLoader.buildArgs(this.mTemplate, start, end), this.mSummaryCallbacks);
        } else {
            entry = this.mChartData.detailDefault.getValues(start, end, now, null);
            long defaultBytes = entry.rxBytes + entry.txBytes;
            entry = this.mChartData.detailForeground.getValues(start, end, now, entry);
            long foregroundBytes = entry.rxBytes + entry.txBytes;
            long totalBytes = defaultBytes + foregroundBytes;
            if (this.mAppTotal != null) {
                this.mAppTotal.setText(android.text.format.Formatter.formatFileSize(context, totalBytes));
            }
            this.mAppBackground.setText(android.text.format.Formatter.formatFileSize(context, defaultBytes));
            this.mAppForeground.setText(android.text.format.Formatter.formatFileSize(context, foregroundBytes));
            entry = this.mChartData.detail.getValues(start, end, now, null);
            getLoaderManager().destroyLoader(3);
            this.mCycleSummary.setVisibility(8);
        }
        this.mCycleSummary.setText(android.text.format.Formatter.formatFileSize(context, entry != null ? entry.rxBytes + entry.txBytes : 0));
        if (!"mobile".equals(this.mCurrentTab) && !"3g".equals(this.mCurrentTab) && !"4g".equals(this.mCurrentTab)) {
            this.mDisclaimer.setVisibility(8);
        } else if (isAppDetailMode()) {
            this.mDisclaimer.setVisibility(8);
        } else {
            this.mDisclaimer.setVisibility(0);
        }
        ensureLayoutTransitions();
    }

    @Deprecated
    private boolean isMobilePolicySplit() {
        Context context = getActivity();
        if (!hasReadyMobileRadio(context)) {
            return false;
        }
        TelephonyManager tele = TelephonyManager.from(context);
        return this.mPolicyEditor.isMobilePolicySplit(getActiveSubscriberId(context));
    }

    private static String getActiveSubscriberId(Context context) {
        return SystemProperties.get("test.subscriberid", TelephonyManager.from(context).getSubscriberId());
    }

    public static String formatDateRange(Context context, long start, long end) {
        String formatter;
        synchronized (sBuilder) {
            sBuilder.setLength(0);
            formatter = DateUtils.formatDateRange(context, sFormatter, start, end, 65552, null).toString();
        }
        return formatter;
    }

    private static String computeTabFromIntent(Intent intent) {
        NetworkTemplate template = (NetworkTemplate) intent.getParcelableExtra("android.net.NETWORK_TEMPLATE");
        if (template == null) {
            return null;
        }
        switch (template.getMatchRule()) {
            case 1:
                return "mobile";
            case 2:
                return "3g";
            case 3:
                return "4g";
            case 4:
                return "wifi";
            default:
                return null;
        }
    }

    public static boolean hasReadyMobileRadio(Context context) {
        ConnectivityManager conn = ConnectivityManager.from(context);
        TelephonyManager tele = TelephonyManager.from(context);
        if (conn.isNetworkSupported(0) && tele.getSimState() == 5) {
            return true;
        }
        return false;
    }

    public static boolean hasReadyMobile4gRadio(Context context) {
        return false;
    }

    public static boolean hasWifiRadio(Context context) {
        return ConnectivityManager.from(context).isNetworkSupported(1);
    }

    public boolean hasEthernet(Context context) {
        boolean hasEthernet = ConnectivityManager.from(context).isNetworkSupported(9);
        if (this.mStatsSession != null) {
            try {
                long ethernetBytes = this.mStatsSession.getSummaryForNetwork(NetworkTemplate.buildTemplateEthernet(), Long.MIN_VALUE, Long.MAX_VALUE).getTotalBytes();
            } catch (RemoteException e) {
                throw new RuntimeException(e);
            }
        }
        ethernetBytes = 0;
        if (!hasEthernet || ethernetBytes <= 0) {
            return false;
        }
        return true;
    }

    private static View inflatePreference(LayoutInflater inflater, ViewGroup root, View widget) {
        View view = inflater.inflate(R.layout.preference, root, false);
        ((LinearLayout) view.findViewById(16908312)).addView(widget, new LayoutParams(-2, -2));
        return view;
    }

    private static View inflateCategoryHeader(LayoutInflater inflater, ViewGroup root) {
        return inflater.inflate(inflater.getContext().obtainStyledAttributes(null, R.styleable.Preference, 16842892, 0).getResourceId(3, 0), root, false);
    }

    private static void insetListViewDrawables(ListView view, int insetSide) {
        Drawable selector = view.getSelector();
        Drawable divider = view.getDivider();
        Drawable stub = new ColorDrawable(0);
        view.setSelector(stub);
        view.setDivider(stub);
        view.setSelector(new InsetBoundsDrawable(selector, insetSide));
        view.setDivider(new InsetBoundsDrawable(divider, insetSide));
    }

    private static void setPreferenceTitle(View parent, int resId) {
        ((TextView) parent.findViewById(16908310)).setText(resId);
    }

    private static void setPreferenceSummary(View parent, CharSequence string) {
        TextView summary = (TextView) parent.findViewById(16908304);
        summary.setVisibility(0);
        summary.setText(string);
    }
}
