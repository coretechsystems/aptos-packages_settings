package com.android.settings.quicklaunch;

import android.app.ListActivity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ResolveInfo.DisplayNameComparator;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.SimpleAdapter.ViewBinder;
import com.android.settings.R;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class BookmarkPicker extends ListActivity implements ViewBinder {
    private static final String[] sKeys = new String[]{"TITLE", "RESOLVE_INFO"};
    private static Intent sLaunchIntent;
    private static final int[] sResourceIds = new int[]{R.id.title, R.id.icon};
    private static Intent sShortcutIntent;
    private int mDisplayMode = 0;
    private SimpleAdapter mMyAdapter;
    private List<ResolveInfo> mResolveList;
    private Handler mUiHandler = new Handler();

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        updateListAndAdapter();
    }

    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, 0, 0, R.string.quick_launch_display_mode_applications).setIcon(17302477);
        menu.add(0, 1, 0, R.string.quick_launch_display_mode_shortcuts).setIcon(17302505);
        return true;
    }

    public boolean onPrepareOptionsMenu(Menu menu) {
        boolean z;
        boolean z2 = false;
        MenuItem findItem = menu.findItem(0);
        if (this.mDisplayMode != 0) {
            z = true;
        } else {
            z = false;
        }
        findItem.setVisible(z);
        MenuItem findItem2 = menu.findItem(1);
        if (this.mDisplayMode != 1) {
            z2 = true;
        }
        findItem2.setVisible(z2);
        return true;
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case 0:
                this.mDisplayMode = 0;
                break;
            case 1:
                this.mDisplayMode = 1;
                break;
            default:
                return false;
        }
        updateListAndAdapter();
        return true;
    }

    private void ensureIntents() {
        if (sLaunchIntent == null) {
            sLaunchIntent = new Intent("android.intent.action.MAIN").addCategory("android.intent.category.LAUNCHER");
            sShortcutIntent = new Intent("android.intent.action.CREATE_SHORTCUT");
        }
    }

    private void updateListAndAdapter() {
        new Thread("data updater") {
            public void run() {
                synchronized (BookmarkPicker.this) {
                    ArrayList<ResolveInfo> newResolveList = new ArrayList();
                    ArrayList<Map<String, ?>> newAdapterList = new ArrayList();
                    BookmarkPicker.this.fillResolveList(newResolveList);
                    Collections.sort(newResolveList, new DisplayNameComparator(BookmarkPicker.this.getPackageManager()));
                    BookmarkPicker.this.fillAdapterList(newAdapterList, newResolveList);
                    BookmarkPicker.this.updateAdapterToUseNewLists(newAdapterList, newResolveList);
                }
            }
        }.start();
    }

    private void updateAdapterToUseNewLists(final ArrayList<Map<String, ?>> newAdapterList, final ArrayList<ResolveInfo> newResolveList) {
        this.mUiHandler.post(new Runnable() {
            public void run() {
                BookmarkPicker.this.mMyAdapter = BookmarkPicker.this.createResolveAdapter(newAdapterList);
                BookmarkPicker.this.mResolveList = newResolveList;
                BookmarkPicker.this.setListAdapter(BookmarkPicker.this.mMyAdapter);
            }
        });
    }

    private void fillResolveList(List<ResolveInfo> list) {
        ensureIntents();
        PackageManager pm = getPackageManager();
        list.clear();
        if (this.mDisplayMode == 0) {
            list.addAll(pm.queryIntentActivities(sLaunchIntent, 0));
        } else if (this.mDisplayMode == 1) {
            list.addAll(pm.queryIntentActivities(sShortcutIntent, 0));
        }
    }

    private SimpleAdapter createResolveAdapter(List<Map<String, ?>> list) {
        SimpleAdapter adapter = new SimpleAdapter(this, list, R.layout.bookmark_picker_item, sKeys, sResourceIds);
        adapter.setViewBinder(this);
        return adapter;
    }

    private void fillAdapterList(List<Map<String, ?>> list, List<ResolveInfo> resolveList) {
        list.clear();
        int resolveListSize = resolveList.size();
        for (int i = 0; i < resolveListSize; i++) {
            ResolveInfo info = (ResolveInfo) resolveList.get(i);
            Map<String, Object> map = new TreeMap();
            map.put("TITLE", getResolveInfoTitle(info));
            map.put("RESOLVE_INFO", info);
            list.add(map);
        }
    }

    private String getResolveInfoTitle(ResolveInfo info) {
        CharSequence label = info.loadLabel(getPackageManager());
        if (label == null) {
            label = info.activityInfo.name;
        }
        return label != null ? label.toString() : null;
    }

    protected void onListItemClick(ListView l, View v, int position, long id) {
        if (position < this.mResolveList.size()) {
            ResolveInfo info = (ResolveInfo) this.mResolveList.get(position);
            switch (this.mDisplayMode) {
                case 0:
                    Intent intent = getIntentForResolveInfo(info, "android.intent.action.MAIN");
                    intent.addCategory("android.intent.category.LAUNCHER");
                    finish(intent, getResolveInfoTitle(info));
                    return;
                case 1:
                    startShortcutActivity(info);
                    return;
                default:
                    return;
            }
        }
    }

    private static Intent getIntentForResolveInfo(ResolveInfo info, String action) {
        Intent intent = new Intent(action);
        ActivityInfo ai = info.activityInfo;
        intent.setClassName(ai.packageName, ai.name);
        return intent;
    }

    private void startShortcutActivity(ResolveInfo info) {
        startActivityForResult(getIntentForResolveInfo(info, "android.intent.action.CREATE_SHORTCUT"), 1);
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == -1) {
            switch (requestCode) {
                case 1:
                    if (data != null) {
                        finish((Intent) data.getParcelableExtra("android.intent.extra.shortcut.INTENT"), data.getStringExtra("android.intent.extra.shortcut.NAME"));
                        return;
                    }
                    return;
                default:
                    super.onActivityResult(requestCode, resultCode, data);
                    return;
            }
        }
    }

    private void finish(Intent intent, String title) {
        intent.putExtras(getIntent());
        intent.putExtra("com.android.settings.quicklaunch.TITLE", title);
        setResult(-1, intent);
        finish();
    }

    public boolean setViewValue(View view, Object data, String textRepresentation) {
        if (view.getId() != R.id.icon) {
            return false;
        }
        Drawable icon = ((ResolveInfo) data).loadIcon(getPackageManager());
        if (icon != null) {
            ((ImageView) view).setImageDrawable(icon);
        }
        return true;
    }
}
