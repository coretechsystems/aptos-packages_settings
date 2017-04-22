package com.android.settings;

import android.app.Activity;
import android.app.AlertDialog.Builder;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MenuItem.OnMenuItemClickListener;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.Switch;
import android.widget.TextView;
import com.android.settings.DreamBackend.DreamInfo;
import com.android.settings.widget.SwitchBar;
import com.android.settings.widget.SwitchBar.OnSwitchChangeListener;

public class DreamSettings extends SettingsPreferenceFragment implements OnSwitchChangeListener {
    private static final String TAG = DreamSettings.class.getSimpleName();
    private DreamInfoAdapter mAdapter;
    private DreamBackend mBackend;
    private Context mContext;
    private MenuItem[] mMenuItemsWhenEnabled;
    private final PackageReceiver mPackageReceiver = new PackageReceiver();
    private boolean mRefreshing;
    private SwitchBar mSwitchBar;

    private class DreamInfoAdapter extends ArrayAdapter<DreamInfo> {
        private final LayoutInflater mInflater;

        public DreamInfoAdapter(Context context) {
            super(context, 0);
            this.mInflater = (LayoutInflater) context.getSystemService("layout_inflater");
        }

        public View getView(int position, View convertView, ViewGroup parent) {
            int i;
            boolean showSettings = true;
            int i2 = 0;
            DreamInfo dreamInfo = (DreamInfo) getItem(position);
            DreamSettings.logd("getView(%s)", dreamInfo.caption);
            final View row = convertView != null ? convertView : createDreamInfoRow(parent);
            row.setTag(dreamInfo);
            ((ImageView) row.findViewById(16908294)).setImageDrawable(dreamInfo.icon);
            ((TextView) row.findViewById(16908310)).setText(dreamInfo.caption);
            RadioButton radioButton = (RadioButton) row.findViewById(16908313);
            radioButton.setChecked(dreamInfo.isActive);
            radioButton.setOnTouchListener(new OnTouchListener() {
                public boolean onTouch(View v, MotionEvent event) {
                    row.onTouchEvent(event);
                    return false;
                }
            });
            if (dreamInfo.settingsComponentName == null) {
                showSettings = false;
            }
            View settingsDivider = row.findViewById(R.id.divider);
            if (showSettings) {
                i = 0;
            } else {
                i = 4;
            }
            settingsDivider.setVisibility(i);
            ImageView settingsButton = (ImageView) row.findViewById(16908314);
            if (!showSettings) {
                i2 = 4;
            }
            settingsButton.setVisibility(i2);
            settingsButton.setAlpha(dreamInfo.isActive ? 1.0f : 0.4f);
            settingsButton.setEnabled(dreamInfo.isActive);
            settingsButton.setFocusable(dreamInfo.isActive);
            settingsButton.setOnClickListener(new OnClickListener() {
                public void onClick(View v) {
                    DreamSettings.this.mBackend.launchSettings((DreamInfo) row.getTag());
                }
            });
            return row;
        }

        private View createDreamInfoRow(ViewGroup parent) {
            final View row = this.mInflater.inflate(R.layout.dream_info_row, parent, false);
            row.findViewById(16908312).setOnClickListener(new OnClickListener() {
                public void onClick(View v) {
                    v.setPressed(true);
                    DreamInfoAdapter.this.activate((DreamInfo) row.getTag());
                }
            });
            return row;
        }

        private DreamInfo getCurrentSelection() {
            for (int i = 0; i < getCount(); i++) {
                DreamInfo dreamInfo = (DreamInfo) getItem(i);
                if (dreamInfo.isActive) {
                    return dreamInfo;
                }
            }
            return null;
        }

        private void activate(DreamInfo dreamInfo) {
            if (!dreamInfo.equals(getCurrentSelection())) {
                for (int i = 0; i < getCount(); i++) {
                    ((DreamInfo) getItem(i)).isActive = false;
                }
                dreamInfo.isActive = true;
                DreamSettings.this.mBackend.setActiveDream(dreamInfo.componentName);
                notifyDataSetChanged();
            }
        }
    }

    private class PackageReceiver extends BroadcastReceiver {
        private PackageReceiver() {
        }

        public void onReceive(Context context, Intent intent) {
            DreamSettings.logd("PackageReceiver.onReceive", new Object[0]);
            DreamSettings.this.refreshFromBackend();
        }
    }

    public int getHelpResource() {
        return R.string.help_url_dreams;
    }

    public void onAttach(Activity activity) {
        logd("onAttach(%s)", activity.getClass().getSimpleName());
        super.onAttach(activity);
        this.mContext = activity;
    }

    public void onCreate(Bundle icicle) {
        logd("onCreate(%s)", icicle);
        super.onCreate(icicle);
        this.mBackend = new DreamBackend(getActivity());
        setHasOptionsMenu(true);
    }

    public void onSwitchChanged(Switch switchView, boolean isChecked) {
        if (!this.mRefreshing) {
            this.mBackend.setEnabled(isChecked);
            refreshFromBackend();
        }
    }

    public void onStart() {
        logd("onStart()", new Object[0]);
        super.onStart();
    }

    public void onDestroyView() {
        logd("onDestroyView()", new Object[0]);
        super.onDestroyView();
        this.mSwitchBar.removeOnSwitchChangeListener(this);
        this.mSwitchBar.hide();
    }

    public void onActivityCreated(Bundle savedInstanceState) {
        logd("onActivityCreated(%s)", savedInstanceState);
        super.onActivityCreated(savedInstanceState);
        ListView listView = getListView();
        listView.setItemsCanFocus(true);
        TextView emptyView = (TextView) getView().findViewById(16908292);
        emptyView.setText(R.string.screensaver_settings_disabled_prompt);
        listView.setEmptyView(emptyView);
        this.mAdapter = new DreamInfoAdapter(this.mContext);
        listView.setAdapter(this.mAdapter);
        this.mSwitchBar = ((SettingsActivity) getActivity()).getSwitchBar();
        this.mSwitchBar.addOnSwitchChangeListener(this);
        this.mSwitchBar.show();
    }

    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        logd("onCreateOptionsMenu()", new Object[0]);
        boolean isEnabled = this.mBackend.isEnabled();
        MenuItem start = createMenuItem(menu, R.string.screensaver_settings_dream_start, 0, isEnabled, new Runnable() {
            public void run() {
                DreamSettings.this.mBackend.startDreaming();
            }
        });
        MenuItem whenToDream = createMenuItem(menu, R.string.screensaver_settings_when_to_dream, 0, isEnabled, new Runnable() {
            public void run() {
                DreamSettings.this.showDialog(1);
            }
        });
        super.onCreateOptionsMenu(menu, inflater);
        this.mMenuItemsWhenEnabled = new MenuItem[]{start, whenToDream};
    }

    private MenuItem createMenuItem(Menu menu, int titleRes, int actionEnum, boolean isEnabled, final Runnable onClick) {
        MenuItem item = menu.add(titleRes);
        item.setShowAsAction(actionEnum);
        item.setEnabled(isEnabled);
        item.setOnMenuItemClickListener(new OnMenuItemClickListener() {
            public boolean onMenuItemClick(MenuItem item) {
                onClick.run();
                return true;
            }
        });
        return item;
    }

    public Dialog onCreateDialog(int dialogId) {
        logd("onCreateDialog(%s)", Integer.valueOf(dialogId));
        if (dialogId == 1) {
            return createWhenToDreamDialog();
        }
        return super.onCreateDialog(dialogId);
    }

    private Dialog createWhenToDreamDialog() {
        int initialSelection = 2;
        CharSequence[] items = new CharSequence[]{this.mContext.getString(R.string.screensaver_settings_summary_dock), this.mContext.getString(R.string.screensaver_settings_summary_sleep), this.mContext.getString(R.string.screensaver_settings_summary_either_short)};
        if (!(this.mBackend.isActivatedOnDock() && this.mBackend.isActivatedOnSleep())) {
            initialSelection = this.mBackend.isActivatedOnDock() ? 0 : this.mBackend.isActivatedOnSleep() ? 1 : -1;
        }
        return new Builder(this.mContext).setTitle(R.string.screensaver_settings_when_to_dream).setSingleChoiceItems(items, initialSelection, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int item) {
                boolean z;
                boolean z2 = false;
                DreamBackend access$100 = DreamSettings.this.mBackend;
                if (item == 0 || item == 2) {
                    z = true;
                } else {
                    z = false;
                }
                access$100.setActivatedOnDock(z);
                DreamBackend access$1002 = DreamSettings.this.mBackend;
                if (item == 1 || item == 2) {
                    z2 = true;
                }
                access$1002.setActivatedOnSleep(z2);
                dialog.dismiss();
            }
        }).create();
    }

    public void onPause() {
        logd("onPause()", new Object[0]);
        super.onPause();
        this.mContext.unregisterReceiver(this.mPackageReceiver);
    }

    public void onResume() {
        logd("onResume()", new Object[0]);
        super.onResume();
        refreshFromBackend();
        IntentFilter filter = new IntentFilter();
        filter.addAction("android.intent.action.PACKAGE_ADDED");
        filter.addAction("android.intent.action.PACKAGE_CHANGED");
        filter.addAction("android.intent.action.PACKAGE_REMOVED");
        filter.addAction("android.intent.action.PACKAGE_REPLACED");
        filter.addDataScheme("package");
        this.mContext.registerReceiver(this.mPackageReceiver, filter);
    }

    public static CharSequence getSummaryTextWithDreamName(Context context) {
        DreamBackend backend = new DreamBackend(context);
        if (backend.isEnabled()) {
            return backend.getActiveDreamName();
        }
        return context.getString(R.string.screensaver_settings_summary_off);
    }

    private void refreshFromBackend() {
        logd("refreshFromBackend()", new Object[0]);
        this.mRefreshing = true;
        boolean dreamsEnabled = this.mBackend.isEnabled();
        if (this.mSwitchBar.isChecked() != dreamsEnabled) {
            this.mSwitchBar.setChecked(dreamsEnabled);
        }
        this.mAdapter.clear();
        if (dreamsEnabled) {
            this.mAdapter.addAll(this.mBackend.getDreamInfos());
        }
        if (this.mMenuItemsWhenEnabled != null) {
            for (MenuItem menuItem : this.mMenuItemsWhenEnabled) {
                menuItem.setEnabled(dreamsEnabled);
            }
        }
        this.mRefreshing = false;
    }

    private static void logd(String msg, Object... args) {
    }
}
