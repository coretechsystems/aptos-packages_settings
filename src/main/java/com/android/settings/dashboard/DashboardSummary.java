package com.android.settings.dashboard;

import android.app.Fragment;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import com.android.settings.R;
import com.android.settings.SettingsActivity;
import java.util.List;

public class DashboardSummary extends Fragment {
    private ViewGroup mDashboard;
    private Handler mHandler = new Handler() {
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case 1:
                    DashboardSummary.this.rebuildUI(DashboardSummary.this.getActivity());
                    return;
                default:
                    return;
            }
        }
    };
    private HomePackageReceiver mHomePackageReceiver = new HomePackageReceiver();
    private LayoutInflater mLayoutInflater;

    private class HomePackageReceiver extends BroadcastReceiver {
        private HomePackageReceiver() {
        }

        public void onReceive(Context context, Intent intent) {
            DashboardSummary.this.rebuildUI(context);
        }
    }

    public void onResume() {
        super.onResume();
        sendRebuildUI();
        IntentFilter filter = new IntentFilter("android.intent.action.PACKAGE_ADDED");
        filter.addAction("android.intent.action.PACKAGE_REMOVED");
        filter.addAction("android.intent.action.PACKAGE_CHANGED");
        filter.addAction("android.intent.action.PACKAGE_REPLACED");
        filter.addDataScheme("package");
        getActivity().registerReceiver(this.mHomePackageReceiver, filter);
    }

    public void onPause() {
        super.onPause();
        getActivity().unregisterReceiver(this.mHomePackageReceiver);
    }

    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        this.mLayoutInflater = inflater;
        View rootView = inflater.inflate(R.layout.dashboard, container, false);
        this.mDashboard = (ViewGroup) rootView.findViewById(R.id.dashboard_container);
        return rootView;
    }

    private void rebuildUI(Context context) {
        if (isAdded()) {
            long start = System.currentTimeMillis();
            Resources res = getResources();
            this.mDashboard.removeAllViews();
            List<DashboardCategory> categories = ((SettingsActivity) context).getDashboardCategories(true);
            int count = categories.size();
            for (int n = 0; n < count; n++) {
                DashboardCategory category = (DashboardCategory) categories.get(n);
                View categoryView = this.mLayoutInflater.inflate(R.layout.dashboard_category, this.mDashboard, false);
                ((TextView) categoryView.findViewById(R.id.category_title)).setText(category.getTitle(res));
                ViewGroup categoryContent = (ViewGroup) categoryView.findViewById(R.id.category_content);
                int tilesCount = category.getTilesCount();
                for (int i = 0; i < tilesCount; i++) {
                    DashboardTile tile = category.getTile(i);
                    View dashboardTileView = new DashboardTileView(context);
                    updateTileView(context, res, tile, dashboardTileView.getImageView(), dashboardTileView.getTitleTextView(), dashboardTileView.getStatusTextView());
                    dashboardTileView.setTile(tile);
                    categoryContent.addView(dashboardTileView);
                }
                this.mDashboard.addView(categoryView);
            }
            Log.d("DashboardSummary", "rebuildUI took: " + (System.currentTimeMillis() - start) + " ms");
            return;
        }
        Log.w("DashboardSummary", "Cannot build the DashboardSummary UI yet as the Fragment is not added");
    }

    private void updateTileView(Context context, Resources res, DashboardTile tile, ImageView tileIcon, TextView tileTextView, TextView statusTextView) {
        if (tile.iconRes > 0) {
            tileIcon.setImageResource(tile.iconRes);
        } else {
            tileIcon.setImageDrawable(null);
            tileIcon.setBackground(null);
        }
        tileTextView.setText(tile.getTitle(res));
        CharSequence summary = tile.getSummary(res);
        if (TextUtils.isEmpty(summary)) {
            statusTextView.setVisibility(8);
            return;
        }
        statusTextView.setVisibility(0);
        statusTextView.setText(summary);
    }

    private void sendRebuildUI() {
        if (!this.mHandler.hasMessages(1)) {
            this.mHandler.sendEmptyMessage(1);
        }
    }
}
