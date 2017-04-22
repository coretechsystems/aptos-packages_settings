package com.android.settings.applications;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.AppOpsManager;
import android.app.Fragment;
import android.app.INotificationManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.DialogInterface.OnDismissListener;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.ApplicationInfo;
import android.content.pm.IPackageManager;
import android.content.pm.PackageManager;
import android.net.NetworkPolicyManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.UserHandle;
import android.os.UserManager;
import android.preference.PreferenceFrameLayout;
import android.preference.PreferenceFrameLayout.LayoutParams;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.PagerTabStrip;
import android.support.v4.view.ViewPager;
import android.support.v4.view.ViewPager.OnPageChangeListener;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.widget.AbsListView.RecyclerListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.Filter;
import android.widget.Filter.FilterResults;
import android.widget.Filterable;
import android.widget.ListView;
import android.widget.Spinner;
import com.android.internal.app.IMediaContainerService;
import com.android.internal.app.IMediaContainerService.Stub;
import com.android.settings.R;
import com.android.settings.Settings.RunningServicesActivity;
import com.android.settings.Settings.StorageUseActivity;
import com.android.settings.SettingsActivity;
import com.android.settings.UserSpinnerAdapter;
import com.android.settings.Utils;
import com.android.settings.applications.ApplicationsState.AppEntry;
import com.android.settings.applications.ApplicationsState.AppFilter;
import com.android.settings.applications.ApplicationsState.Callbacks;
import com.android.settings.applications.ApplicationsState.Session;
import com.android.settings.deviceinfo.StorageMeasurement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class ManageApplications extends Fragment implements OnClickListener, OnDismissListener, OnItemSelectedListener, AppClickListener {
    private boolean mActivityResumed;
    private ApplicationsState mApplicationsState;
    private CharSequence mComputingSizeStr;
    private final ServiceConnection mContainerConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName name, IBinder service) {
            ManageApplications.this.mContainerService = Stub.asInterface(service);
            for (int i = 0; i < ManageApplications.this.mTabs.size(); i++) {
                ((TabInfo) ManageApplications.this.mTabs.get(i)).setContainerService(ManageApplications.this.mContainerService);
            }
        }

        public void onServiceDisconnected(ComponentName name) {
            ManageApplications.this.mContainerService = null;
        }
    };
    private volatile IMediaContainerService mContainerService;
    private ViewGroup mContentContainer;
    private Context mContext;
    TabInfo mCurTab = null;
    private String mCurrentPkgName;
    private int mDefaultListType = -1;
    private LayoutInflater mInflater;
    CharSequence mInvalidSizeStr;
    private int mNumTabs;
    private Menu mOptionsMenu;
    private UserSpinnerAdapter mProfileSpinnerAdapter;
    AlertDialog mResetDialog;
    private View mRootView;
    private boolean mShowBackground = false;
    private int mSortOrder = 4;
    private final ArrayList<TabInfo> mTabs = new ArrayList();
    private ViewPager mViewPager;

    static class ApplicationsAdapter extends BaseAdapter implements RecyclerListener, Filterable, Callbacks {
        private final ArrayList<View> mActive = new ArrayList();
        private ArrayList<AppEntry> mBaseEntries;
        private final Context mContext;
        CharSequence mCurFilterPrefix;
        private ArrayList<AppEntry> mEntries;
        private Filter mFilter = new Filter() {
            protected FilterResults performFiltering(CharSequence constraint) {
                ArrayList<AppEntry> entries = ApplicationsAdapter.this.applyPrefixFilter(constraint, ApplicationsAdapter.this.mBaseEntries);
                FilterResults fr = new FilterResults();
                fr.values = entries;
                fr.count = entries.size();
                return fr;
            }

            protected void publishResults(CharSequence constraint, FilterResults results) {
                ApplicationsAdapter.this.mCurFilterPrefix = constraint;
                ApplicationsAdapter.this.mEntries = (ArrayList) results.values;
                ApplicationsAdapter.this.notifyDataSetChanged();
                ApplicationsAdapter.this.mTab.updateStorageUsage();
            }
        };
        private final int mFilterMode;
        private int mLastSortMode = -1;
        private boolean mResumed;
        private final Session mSession;
        private final ApplicationsState mState;
        private final TabInfo mTab;
        private boolean mWaitingForData;
        private int mWhichSize = 0;

        public ApplicationsAdapter(ApplicationsState state, TabInfo tab, int filterMode) {
            this.mState = state;
            this.mSession = state.newSession(this);
            this.mTab = tab;
            this.mContext = tab.mOwner.getActivity();
            this.mFilterMode = filterMode;
        }

        public void resume(int sort) {
            if (this.mResumed) {
                rebuild(sort);
                return;
            }
            this.mResumed = true;
            this.mSession.resume();
            this.mLastSortMode = sort;
            rebuild(true);
        }

        public void pause() {
            if (this.mResumed) {
                this.mResumed = false;
                this.mSession.pause();
            }
        }

        public void release() {
            this.mSession.release();
        }

        public void rebuild(int sort) {
            if (sort != this.mLastSortMode) {
                this.mLastSortMode = sort;
                rebuild(true);
            }
        }

        public void rebuild(boolean eraseold) {
            AppFilter filterObj;
            Comparator<AppEntry> comparatorObj;
            boolean emulated = Environment.isExternalStorageEmulated();
            if (emulated) {
                this.mWhichSize = 0;
            } else {
                this.mWhichSize = 1;
            }
            switch (this.mFilterMode) {
                case 1:
                    filterObj = ApplicationsState.THIRD_PARTY_FILTER;
                    break;
                case 2:
                    filterObj = ApplicationsState.ON_SD_CARD_FILTER;
                    if (!emulated) {
                        this.mWhichSize = 2;
                        break;
                    }
                    break;
                case 3:
                    filterObj = ApplicationsState.DISABLED_FILTER;
                    break;
                default:
                    filterObj = ApplicationsState.ALL_ENABLED_FILTER;
                    break;
            }
            switch (this.mLastSortMode) {
                case 5:
                    switch (this.mWhichSize) {
                        case 1:
                            comparatorObj = ApplicationsState.INTERNAL_SIZE_COMPARATOR;
                            break;
                        case 2:
                            comparatorObj = ApplicationsState.EXTERNAL_SIZE_COMPARATOR;
                            break;
                        default:
                            comparatorObj = ApplicationsState.SIZE_COMPARATOR;
                            break;
                    }
                default:
                    comparatorObj = ApplicationsState.ALPHA_COMPARATOR;
                    break;
            }
            ArrayList<AppEntry> entries = this.mSession.rebuild(filterObj, comparatorObj);
            if (entries != null || eraseold) {
                this.mBaseEntries = entries;
                if (this.mBaseEntries != null) {
                    this.mEntries = applyPrefixFilter(this.mCurFilterPrefix, this.mBaseEntries);
                } else {
                    this.mEntries = null;
                }
                notifyDataSetChanged();
                this.mTab.updateStorageUsage();
                if (entries == null) {
                    this.mWaitingForData = true;
                    this.mTab.mListContainer.setVisibility(4);
                    this.mTab.mLoadingContainer.setVisibility(0);
                    return;
                }
                this.mTab.mListContainer.setVisibility(0);
                this.mTab.mLoadingContainer.setVisibility(8);
            }
        }

        ArrayList<AppEntry> applyPrefixFilter(CharSequence prefix, ArrayList<AppEntry> origEntries) {
            if (prefix == null || prefix.length() == 0) {
                return origEntries;
            }
            String prefixStr = ApplicationsState.normalize(prefix.toString());
            String spacePrefixStr = " " + prefixStr;
            ArrayList<AppEntry> newEntries = new ArrayList();
            for (int i = 0; i < origEntries.size(); i++) {
                AppEntry entry = (AppEntry) origEntries.get(i);
                String nlabel = entry.getNormalizedLabel();
                if (nlabel.startsWith(prefixStr) || nlabel.indexOf(spacePrefixStr) != -1) {
                    newEntries.add(entry);
                }
            }
            return newEntries;
        }

        public void onRunningStateChanged(boolean running) {
            this.mTab.mOwner.getActivity().setProgressBarIndeterminateVisibility(running);
        }

        public void onRebuildComplete(ArrayList<AppEntry> apps) {
            if (this.mTab.mLoadingContainer.getVisibility() == 0) {
                this.mTab.mLoadingContainer.startAnimation(AnimationUtils.loadAnimation(this.mContext, 17432577));
                this.mTab.mListContainer.startAnimation(AnimationUtils.loadAnimation(this.mContext, 17432576));
            }
            this.mTab.mListContainer.setVisibility(0);
            this.mTab.mLoadingContainer.setVisibility(8);
            this.mWaitingForData = false;
            this.mBaseEntries = apps;
            this.mEntries = applyPrefixFilter(this.mCurFilterPrefix, this.mBaseEntries);
            notifyDataSetChanged();
            this.mTab.updateStorageUsage();
        }

        public void onPackageListChanged() {
            rebuild(false);
        }

        public void onPackageIconChanged() {
        }

        public void onPackageSizeChanged(String packageName) {
            for (int i = 0; i < this.mActive.size(); i++) {
                AppViewHolder holder = (AppViewHolder) ((View) this.mActive.get(i)).getTag();
                if (holder.entry.info.packageName.equals(packageName)) {
                    synchronized (holder.entry) {
                        holder.updateSizeText(this.mTab.mInvalidSizeStr, this.mWhichSize);
                    }
                    if (holder.entry.info.packageName.equals(this.mTab.mOwner.mCurrentPkgName) && this.mLastSortMode == 5) {
                        rebuild(false);
                    }
                    this.mTab.updateStorageUsage();
                    return;
                }
            }
        }

        public void onAllSizesComputed() {
            if (this.mLastSortMode == 5) {
                rebuild(false);
            }
            this.mTab.updateStorageUsage();
        }

        public int getCount() {
            return this.mEntries != null ? this.mEntries.size() : 0;
        }

        public Object getItem(int position) {
            return this.mEntries.get(position);
        }

        public AppEntry getAppEntry(int position) {
            return (AppEntry) this.mEntries.get(position);
        }

        public long getItemId(int position) {
            return ((AppEntry) this.mEntries.get(position)).id;
        }

        public View getView(int position, View convertView, ViewGroup parent) {
            boolean z = false;
            AppViewHolder holder = AppViewHolder.createOrRecycle(this.mTab.mInflater, convertView);
            convertView = holder.rootView;
            AppEntry entry = (AppEntry) this.mEntries.get(position);
            synchronized (entry) {
                holder.entry = entry;
                if (entry.label != null) {
                    holder.appName.setText(entry.label);
                }
                this.mState.ensureIcon(entry);
                if (entry.icon != null) {
                    holder.appIcon.setImageDrawable(entry.icon);
                }
                holder.updateSizeText(this.mTab.mInvalidSizeStr, this.mWhichSize);
                if ((entry.info.flags & 8388608) == 0) {
                    holder.disabled.setVisibility(0);
                    holder.disabled.setText(R.string.not_installed);
                } else if (entry.info.enabled) {
                    holder.disabled.setVisibility(8);
                } else {
                    holder.disabled.setVisibility(0);
                    holder.disabled.setText(R.string.disabled);
                }
                if (this.mFilterMode == 2) {
                    holder.checkBox.setVisibility(0);
                    CheckBox checkBox = holder.checkBox;
                    if ((entry.info.flags & 262144) != 0) {
                        z = true;
                    }
                    checkBox.setChecked(z);
                } else {
                    holder.checkBox.setVisibility(8);
                }
            }
            this.mActive.remove(convertView);
            this.mActive.add(convertView);
            return convertView;
        }

        public Filter getFilter() {
            return this.mFilter;
        }

        public void onMovedToScrapHeap(View view) {
            this.mActive.remove(view);
        }
    }

    class MyPagerAdapter extends PagerAdapter implements OnPageChangeListener {
        int mCurPos = 0;

        MyPagerAdapter() {
        }

        public int getCount() {
            return ManageApplications.this.mNumTabs;
        }

        public Object instantiateItem(ViewGroup container, int position) {
            TabInfo tab = (TabInfo) ManageApplications.this.mTabs.get(position);
            View root = tab.build(ManageApplications.this.mInflater, ManageApplications.this.mContentContainer, ManageApplications.this.mRootView);
            container.addView(root);
            root.setTag(R.id.name, tab);
            return root;
        }

        public void destroyItem(ViewGroup container, int position, Object object) {
            container.removeView((View) object);
        }

        public boolean isViewFromObject(View view, Object object) {
            return view == object;
        }

        public int getItemPosition(Object object) {
            return super.getItemPosition(object);
        }

        public CharSequence getPageTitle(int position) {
            return ((TabInfo) ManageApplications.this.mTabs.get(position)).mLabel;
        }

        public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
        }

        public void onPageSelected(int position) {
            this.mCurPos = position;
        }

        public void onPageScrollStateChanged(int state) {
            if (state == 0) {
                ManageApplications.this.updateCurrentTab(this.mCurPos);
            }
        }
    }

    public static class TabInfo implements OnItemClickListener {
        private long mAppStorage = 0;
        public ApplicationsAdapter mApplications;
        public final ApplicationsState mApplicationsState;
        public final AppClickListener mClickListener;
        public final CharSequence mComputingSizeStr;
        private IMediaContainerService mContainerService;
        public final int mFilter;
        private long mFreeStorage = 0;
        public LayoutInflater mInflater;
        public final CharSequence mInvalidSizeStr;
        public final CharSequence mLabel;
        private View mListContainer;
        public final int mListType;
        private ListView mListView;
        private View mLoadingContainer;
        public final ManageApplications mOwner;
        private ViewGroup mPinnedHeader;
        public View mRootView;
        final Runnable mRunningProcessesAvail = new Runnable() {
            public void run() {
                TabInfo.this.handleRunningProcessesAvail();
            }
        };
        private RunningProcessesView mRunningProcessesView;
        private final Bundle mSavedInstanceState;
        private long mTotalStorage = 0;

        public TabInfo(ManageApplications owner, ApplicationsState apps, CharSequence label, int listType, AppClickListener clickListener, Bundle savedInstanceState) {
            this.mOwner = owner;
            this.mApplicationsState = apps;
            this.mLabel = label;
            this.mListType = listType;
            switch (listType) {
                case 0:
                    this.mFilter = 1;
                    break;
                case 2:
                    this.mFilter = 2;
                    break;
                case 4:
                    this.mFilter = 3;
                    break;
                default:
                    this.mFilter = 0;
                    break;
            }
            this.mClickListener = clickListener;
            this.mInvalidSizeStr = owner.getActivity().getText(R.string.invalid_size_value);
            this.mComputingSizeStr = owner.getActivity().getText(R.string.computing_size);
            this.mSavedInstanceState = savedInstanceState;
        }

        public void setContainerService(IMediaContainerService containerService) {
            this.mContainerService = containerService;
            updateStorageUsage();
        }

        public View build(LayoutInflater inflater, ViewGroup contentParent, View contentChild) {
            if (this.mRootView != null) {
                return this.mRootView;
            }
            this.mInflater = inflater;
            this.mRootView = inflater.inflate(this.mListType == 1 ? R.layout.manage_applications_running : R.layout.manage_applications_apps, null);
            this.mPinnedHeader = (ViewGroup) this.mRootView.findViewById(R.id.pinned_header);
            if (this.mOwner.mProfileSpinnerAdapter != null) {
                Spinner spinner = (Spinner) inflater.inflate(R.layout.spinner_view, null);
                spinner.setAdapter(this.mOwner.mProfileSpinnerAdapter);
                spinner.setOnItemSelectedListener(this.mOwner);
                this.mPinnedHeader.addView(spinner);
                this.mPinnedHeader.setVisibility(0);
            }
            this.mLoadingContainer = this.mRootView.findViewById(R.id.loading_container);
            this.mLoadingContainer.setVisibility(0);
            this.mListContainer = this.mRootView.findViewById(R.id.list_container);
            if (this.mListContainer != null) {
                View emptyView = this.mListContainer.findViewById(16908292);
                ListView lv = (ListView) this.mListContainer.findViewById(16908298);
                if (emptyView != null) {
                    lv.setEmptyView(emptyView);
                }
                lv.setOnItemClickListener(this);
                lv.setSaveEnabled(true);
                lv.setItemsCanFocus(true);
                lv.setTextFilterEnabled(true);
                this.mListView = lv;
                this.mApplications = new ApplicationsAdapter(this.mApplicationsState, this, this.mFilter);
                this.mListView.setAdapter(this.mApplications);
                this.mListView.setRecyclerListener(this.mApplications);
                Utils.prepareCustomPreferencesList(contentParent, contentChild, this.mListView, false);
                if (this.mFilter == 2) {
                    applyCurrentStorage();
                } else {
                    applyCurrentStorage();
                }
            }
            this.mRunningProcessesView = (RunningProcessesView) this.mRootView.findViewById(R.id.running_processes);
            if (this.mRunningProcessesView != null) {
                this.mRunningProcessesView.doCreate(this.mSavedInstanceState);
            }
            return this.mRootView;
        }

        public void detachView() {
            if (this.mRootView != null) {
                ViewGroup group = (ViewGroup) this.mRootView.getParent();
                if (group != null) {
                    group.removeView(this.mRootView);
                }
            }
        }

        public void resume(int sortOrder) {
            if (this.mApplications != null) {
                this.mApplications.resume(sortOrder);
            }
            if (this.mRunningProcessesView == null) {
                return;
            }
            if (this.mRunningProcessesView.doResume(this.mOwner, this.mRunningProcessesAvail)) {
                this.mRunningProcessesView.setVisibility(0);
                this.mLoadingContainer.setVisibility(4);
                return;
            }
            this.mLoadingContainer.setVisibility(0);
        }

        public void pause() {
            if (this.mApplications != null) {
                this.mApplications.pause();
            }
            if (this.mRunningProcessesView != null) {
                this.mRunningProcessesView.doPause();
            }
        }

        public void release() {
            if (this.mApplications != null) {
                this.mApplications.release();
            }
        }

        void updateStorageUsage() {
            if (this.mOwner.getActivity() != null && this.mApplications != null) {
                this.mFreeStorage = 0;
                this.mAppStorage = 0;
                this.mTotalStorage = 0;
                long[] stats;
                int N;
                int i;
                AppEntry ae;
                if (this.mFilter == 2) {
                    if (this.mContainerService != null) {
                        try {
                            stats = this.mContainerService.getFileSystemStats(Environment.getExternalStorageDirectory().getPath());
                            this.mTotalStorage = stats[0];
                            this.mFreeStorage = stats[1];
                        } catch (RemoteException e) {
                            Log.w("ManageApplications", "Problem in container service", e);
                        }
                    }
                    if (this.mApplications != null) {
                        N = this.mApplications.getCount();
                        for (i = 0; i < N; i++) {
                            ae = this.mApplications.getAppEntry(i);
                            this.mAppStorage += (ae.externalCodeSize + ae.externalDataSize) + ae.externalCacheSize;
                        }
                    }
                } else {
                    if (this.mContainerService != null) {
                        try {
                            stats = this.mContainerService.getFileSystemStats(Environment.getDataDirectory().getPath());
                            this.mTotalStorage = stats[0];
                            this.mFreeStorage = stats[1];
                        } catch (RemoteException e2) {
                            Log.w("ManageApplications", "Problem in container service", e2);
                        }
                    }
                    boolean emulatedStorage = Environment.isExternalStorageEmulated();
                    if (this.mApplications != null) {
                        N = this.mApplications.getCount();
                        for (i = 0; i < N; i++) {
                            ae = this.mApplications.getAppEntry(i);
                            this.mAppStorage += ae.codeSize + ae.dataSize;
                            if (emulatedStorage) {
                                this.mAppStorage += ae.externalCodeSize + ae.externalDataSize;
                            }
                        }
                    }
                    this.mFreeStorage += this.mApplicationsState.sumCacheSizes();
                }
                applyCurrentStorage();
            }
        }

        void applyCurrentStorage() {
            if (this.mRootView != null) {
            }
        }

        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            this.mClickListener.onItemClick(this, parent, view, position, id);
        }

        void handleRunningProcessesAvail() {
            this.mLoadingContainer.startAnimation(AnimationUtils.loadAnimation(this.mOwner.getActivity(), 17432577));
            this.mRunningProcessesView.startAnimation(AnimationUtils.loadAnimation(this.mOwner.getActivity(), 17432576));
            this.mRunningProcessesView.setVisibility(0);
            this.mLoadingContainer.setVisibility(8);
        }
    }

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        this.mContext = getActivity();
        this.mApplicationsState = ApplicationsState.getInstance(getActivity().getApplication());
        Intent intent = getActivity().getIntent();
        String action = intent.getAction();
        int defaultListType = 0;
        String className = getArguments() != null ? getArguments().getString("classname") : null;
        if (className == null) {
            className = intent.getComponent().getClassName();
        }
        if (className.equals(RunningServicesActivity.class.getName()) || className.endsWith(".RunningServices")) {
            defaultListType = 1;
        } else if (className.equals(StorageUseActivity.class.getName()) || "android.intent.action.MANAGE_PACKAGE_STORAGE".equals(action) || className.endsWith(".StorageUse")) {
            this.mSortOrder = 5;
            defaultListType = 3;
        } else if ("android.settings.MANAGE_ALL_APPLICATIONS_SETTINGS".equals(action)) {
            defaultListType = 3;
        }
        if (savedInstanceState != null) {
            this.mSortOrder = savedInstanceState.getInt("sortOrder", this.mSortOrder);
            int tmp = savedInstanceState.getInt("defaultListType", -1);
            if (tmp != -1) {
                defaultListType = tmp;
            }
            this.mShowBackground = savedInstanceState.getBoolean("showBackground", false);
        }
        this.mDefaultListType = defaultListType;
        getActivity().bindService(new Intent().setComponent(StorageMeasurement.DEFAULT_CONTAINER_COMPONENT), this.mContainerConnection, 1);
        this.mInvalidSizeStr = getActivity().getText(R.string.invalid_size_value);
        this.mComputingSizeStr = getActivity().getText(R.string.computing_size);
        this.mTabs.add(new TabInfo(this, this.mApplicationsState, getActivity().getString(R.string.filter_apps_third_party), 0, this, savedInstanceState));
        if (!Environment.isExternalStorageEmulated()) {
            this.mTabs.add(new TabInfo(this, this.mApplicationsState, getActivity().getString(R.string.filter_apps_onsdcard), 2, this, savedInstanceState));
        }
        this.mTabs.add(new TabInfo(this, this.mApplicationsState, getActivity().getString(R.string.filter_apps_running), 1, this, savedInstanceState));
        this.mTabs.add(new TabInfo(this, this.mApplicationsState, getActivity().getString(R.string.filter_apps_all), 3, this, savedInstanceState));
        this.mTabs.add(new TabInfo(this, this.mApplicationsState, getActivity().getString(R.string.filter_apps_disabled), 4, this, savedInstanceState));
        this.mNumTabs = this.mTabs.size();
        this.mProfileSpinnerAdapter = Utils.createUserSpinnerAdapter((UserManager) this.mContext.getSystemService("user"), this.mContext);
    }

    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        this.mInflater = inflater;
        View rootView = this.mInflater.inflate(R.layout.manage_applications_content, container, false);
        this.mContentContainer = container;
        this.mRootView = rootView;
        this.mViewPager = (ViewPager) rootView.findViewById(R.id.pager);
        MyPagerAdapter adapter = new MyPagerAdapter();
        this.mViewPager.setAdapter(adapter);
        this.mViewPager.setOnPageChangeListener(adapter);
        ((PagerTabStrip) rootView.findViewById(R.id.tabs)).setTabIndicatorColorResource(R.color.theme_accent);
        if (container instanceof PreferenceFrameLayout) {
            ((LayoutParams) rootView.getLayoutParams()).removeBorders = true;
        }
        if (savedInstanceState != null && savedInstanceState.getBoolean("resetDialog")) {
            buildResetDialog();
        }
        if (savedInstanceState == null) {
            int extraCurrentListType = getActivity().getIntent().getIntExtra("currentListType", -1);
            int currentListType = extraCurrentListType != -1 ? extraCurrentListType : this.mDefaultListType;
            for (int i = 0; i < this.mNumTabs; i++) {
                if (((TabInfo) this.mTabs.get(i)).mListType == currentListType) {
                    this.mViewPager.setCurrentItem(i);
                    break;
                }
            }
        }
        return rootView;
    }

    public void onStart() {
        super.onStart();
    }

    public void onResume() {
        super.onResume();
        this.mActivityResumed = true;
        updateCurrentTab(this.mViewPager.getCurrentItem());
        updateNumTabs();
        updateOptionsMenu();
    }

    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("sortOrder", this.mSortOrder);
        if (this.mDefaultListType != -1) {
            outState.putInt("defaultListType", this.mDefaultListType);
        }
        outState.putBoolean("showBackground", this.mShowBackground);
        if (this.mResetDialog != null) {
            outState.putBoolean("resetDialog", true);
        }
    }

    public void onPause() {
        super.onPause();
        this.mActivityResumed = false;
        for (int i = 0; i < this.mTabs.size(); i++) {
            ((TabInfo) this.mTabs.get(i)).pause();
        }
    }

    public void onStop() {
        super.onStop();
        if (this.mResetDialog != null) {
            this.mResetDialog.dismiss();
            this.mResetDialog = null;
        }
    }

    public void onDestroyView() {
        super.onDestroyView();
        for (int i = 0; i < this.mTabs.size(); i++) {
            ((TabInfo) this.mTabs.get(i)).detachView();
            ((TabInfo) this.mTabs.get(i)).release();
        }
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == 1 && this.mCurrentPkgName != null) {
            this.mApplicationsState.requestSize(this.mCurrentPkgName);
        }
    }

    public void onItemSelected(AdapterView<?> adapterView, View view, int position, long id) {
        UserHandle selectedUser = this.mProfileSpinnerAdapter.getUserHandle(position);
        if (selectedUser.getIdentifier() != UserHandle.myUserId()) {
            Intent intent = new Intent("android.settings.APPLICATION_SETTINGS");
            intent.addFlags(268435456);
            intent.addFlags(32768);
            intent.putExtra("currentListType", ((TabInfo) this.mTabs.get(this.mViewPager.getCurrentItem())).mListType);
            this.mContext.startActivityAsUser(intent, selectedUser);
        }
    }

    public void onNothingSelected(AdapterView<?> adapterView) {
    }

    private void updateNumTabs() {
        int newNum = this.mApplicationsState.haveDisabledApps() ? this.mTabs.size() : this.mTabs.size() - 1;
        if (newNum != this.mNumTabs) {
            this.mNumTabs = newNum;
            if (this.mViewPager != null) {
                this.mViewPager.getAdapter().notifyDataSetChanged();
            }
        }
    }

    TabInfo tabForType(int type) {
        for (int i = 0; i < this.mTabs.size(); i++) {
            TabInfo tab = (TabInfo) this.mTabs.get(i);
            if (tab.mListType == type) {
                return tab;
            }
        }
        return null;
    }

    private void startApplicationDetailsActivity() {
        Bundle args = new Bundle();
        args.putString("package", this.mCurrentPkgName);
        ((SettingsActivity) getActivity()).startPreferencePanel(InstalledAppDetails.class.getName(), args, R.string.application_info_label, null, this, 1);
    }

    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        this.mOptionsMenu = menu;
        menu.add(0, 4, 1, R.string.sort_order_alpha).setShowAsAction(0);
        menu.add(0, 5, 2, R.string.sort_order_size).setShowAsAction(0);
        menu.add(0, 6, 3, R.string.show_running_services).setShowAsAction(1);
        menu.add(0, 7, 3, R.string.show_background_processes).setShowAsAction(1);
        menu.add(0, 8, 4, R.string.reset_app_preferences).setShowAsAction(0);
        updateOptionsMenu();
    }

    public void onPrepareOptionsMenu(Menu menu) {
        updateOptionsMenu();
    }

    public void onDestroyOptionsMenu() {
        this.mOptionsMenu = null;
    }

    public void onDestroy() {
        getActivity().unbindService(this.mContainerConnection);
        super.onDestroy();
    }

    void updateOptionsMenu() {
        boolean z = true;
        if (this.mOptionsMenu != null) {
            if (this.mCurTab == null || this.mCurTab.mListType != 1) {
                boolean z2;
                MenuItem findItem = this.mOptionsMenu.findItem(4);
                if (this.mSortOrder != 4) {
                    z2 = true;
                } else {
                    z2 = false;
                }
                findItem.setVisible(z2);
                findItem = this.mOptionsMenu.findItem(5);
                if (this.mSortOrder != 5) {
                    z2 = true;
                } else {
                    z2 = false;
                }
                findItem.setVisible(z2);
                this.mOptionsMenu.findItem(6).setVisible(false);
                this.mOptionsMenu.findItem(7).setVisible(false);
                this.mOptionsMenu.findItem(8).setVisible(true);
                return;
            }
            boolean showingBackground;
            TabInfo tab = tabForType(1);
            if (tab == null || tab.mRunningProcessesView == null) {
                showingBackground = false;
            } else {
                showingBackground = tab.mRunningProcessesView.mAdapter.getShowBackground();
            }
            this.mOptionsMenu.findItem(4).setVisible(false);
            this.mOptionsMenu.findItem(5).setVisible(false);
            this.mOptionsMenu.findItem(6).setVisible(showingBackground);
            MenuItem findItem2 = this.mOptionsMenu.findItem(7);
            if (showingBackground) {
                z = false;
            }
            findItem2.setVisible(z);
            this.mOptionsMenu.findItem(8).setVisible(false);
            this.mShowBackground = showingBackground;
        }
    }

    void buildResetDialog() {
        if (this.mResetDialog == null) {
            Builder builder = new Builder(getActivity());
            builder.setTitle(R.string.reset_app_preferences_title);
            builder.setMessage(R.string.reset_app_preferences_desc);
            builder.setPositiveButton(R.string.reset_app_preferences_button, this);
            builder.setNegativeButton(R.string.cancel, null);
            this.mResetDialog = builder.show();
            this.mResetDialog.setOnDismissListener(this);
        }
    }

    public void onDismiss(DialogInterface dialog) {
        if (this.mResetDialog == dialog) {
            this.mResetDialog = null;
        }
    }

    public void onClick(DialogInterface dialog, int which) {
        if (this.mResetDialog == dialog) {
            final PackageManager pm = getActivity().getPackageManager();
            final IPackageManager mIPm = IPackageManager.Stub.asInterface(ServiceManager.getService("package"));
            final INotificationManager nm = INotificationManager.Stub.asInterface(ServiceManager.getService("notification"));
            final NetworkPolicyManager npm = NetworkPolicyManager.from(getActivity());
            final AppOpsManager aom = (AppOpsManager) getActivity().getSystemService("appops");
            final Handler handler = new Handler(getActivity().getMainLooper());
            new AsyncTask<Void, Void, Void>() {
                protected Void doInBackground(Void... params) {
                    List<ApplicationInfo> apps = pm.getInstalledApplications(512);
                    for (int i = 0; i < apps.size(); i++) {
                        ApplicationInfo app = (ApplicationInfo) apps.get(i);
                        try {
                            nm.setNotificationsEnabledForPackage(app.packageName, app.uid, true);
                        } catch (RemoteException e) {
                        }
                        if (!app.enabled && pm.getApplicationEnabledSetting(app.packageName) == 3) {
                            pm.setApplicationEnabledSetting(app.packageName, 0, 1);
                        }
                    }
                    try {
                        mIPm.resetPreferredActivities(UserHandle.myUserId());
                    } catch (RemoteException e2) {
                    }
                    aom.resetAllModes();
                    int[] restrictedUids = npm.getUidsWithPolicy(1);
                    int currentUserId = ActivityManager.getCurrentUser();
                    for (int uid : restrictedUids) {
                        if (UserHandle.getUserId(uid) == currentUserId) {
                            npm.setUidPolicy(uid, 0);
                        }
                    }
                    handler.post(new Runnable() {
                        public void run() {
                            if (ManageApplications.this.getActivity() != null && ManageApplications.this.mActivityResumed) {
                                for (int i = 0; i < ManageApplications.this.mTabs.size(); i++) {
                                    TabInfo tab = (TabInfo) ManageApplications.this.mTabs.get(i);
                                    if (tab.mApplications != null) {
                                        tab.mApplications.pause();
                                    }
                                }
                                if (ManageApplications.this.mCurTab != null) {
                                    ManageApplications.this.mCurTab.resume(ManageApplications.this.mSortOrder);
                                }
                            }
                        }
                    });
                    return null;
                }
            }.execute(new Void[0]);
        }
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        int menuId = item.getItemId();
        if (menuId == 4 || menuId == 5) {
            this.mSortOrder = menuId;
            if (!(this.mCurTab == null || this.mCurTab.mApplications == null)) {
                this.mCurTab.mApplications.rebuild(this.mSortOrder);
            }
        } else if (menuId == 6) {
            this.mShowBackground = false;
            if (!(this.mCurTab == null || this.mCurTab.mRunningProcessesView == null)) {
                this.mCurTab.mRunningProcessesView.mAdapter.setShowBackground(false);
            }
        } else if (menuId == 7) {
            this.mShowBackground = true;
            if (!(this.mCurTab == null || this.mCurTab.mRunningProcessesView == null)) {
                this.mCurTab.mRunningProcessesView.mAdapter.setShowBackground(true);
            }
        } else if (menuId != 8) {
            return false;
        } else {
            buildResetDialog();
        }
        updateOptionsMenu();
        return true;
    }

    public void onItemClick(TabInfo tab, AdapterView<?> adapterView, View view, int position, long id) {
        if (tab.mApplications != null && tab.mApplications.getCount() > position) {
            this.mCurrentPkgName = tab.mApplications.getAppEntry(position).info.packageName;
            startApplicationDetailsActivity();
        }
    }

    public void updateCurrentTab(int position) {
        this.mCurTab = (TabInfo) this.mTabs.get(position);
        if (this.mActivityResumed) {
            this.mCurTab.build(this.mInflater, this.mContentContainer, this.mRootView);
            this.mCurTab.resume(this.mSortOrder);
        } else {
            this.mCurTab.pause();
        }
        for (int i = 0; i < this.mTabs.size(); i++) {
            TabInfo t = (TabInfo) this.mTabs.get(i);
            if (t != this.mCurTab) {
                t.pause();
            }
        }
        this.mCurTab.updateStorageUsage();
        updateOptionsMenu();
        Activity host = getActivity();
        if (host != null) {
            host.invalidateOptionsMenu();
        }
    }
}
