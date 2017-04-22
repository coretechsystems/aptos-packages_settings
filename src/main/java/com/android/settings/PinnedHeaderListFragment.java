package com.android.settings;

import android.app.ListFragment;
import android.view.View;
import android.view.ViewGroup;

public class PinnedHeaderListFragment extends ListFragment {
    public void setPinnedHeaderView(View pinnedHeaderView) {
        ((ViewGroup) getListView().getParent()).addView(pinnedHeaderView, 0);
    }
}
