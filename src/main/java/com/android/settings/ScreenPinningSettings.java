package com.android.settings;

import android.content.Context;
import android.content.res.Resources;
import android.os.Bundle;
import android.provider.Settings.SettingNotFoundException;
import android.provider.Settings.System;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Switch;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settings.search.Indexable;
import com.android.settings.search.Indexable.SearchIndexProvider;
import com.android.settings.search.SearchIndexableRaw;
import com.android.settings.widget.SwitchBar;
import com.android.settings.widget.SwitchBar.OnSwitchChangeListener;
import java.util.ArrayList;
import java.util.List;

public class ScreenPinningSettings extends SettingsPreferenceFragment implements Indexable, OnSwitchChangeListener {
    public static final SearchIndexProvider SEARCH_INDEX_DATA_PROVIDER = new BaseSearchIndexProvider() {
        public List<SearchIndexableRaw> getRawDataToIndex(Context context, boolean enabled) {
            List<SearchIndexableRaw> result = new ArrayList();
            Resources res = context.getResources();
            SearchIndexableRaw data = new SearchIndexableRaw(context);
            data.title = res.getString(R.string.screen_pinning_title);
            data.screenTitle = res.getString(R.string.screen_pinning_title);
            result.add(data);
            data = new SearchIndexableRaw(context);
            data.title = res.getString(R.string.screen_pinning_description);
            data.screenTitle = res.getString(R.string.screen_pinning_title);
            result.add(data);
            return result;
        }
    };
    private SwitchBar mSwitchBar;

    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        this.mSwitchBar = ((SettingsActivity) getActivity()).getSwitchBar();
        this.mSwitchBar.addOnSwitchChangeListener(this);
        this.mSwitchBar.show();
        this.mSwitchBar.setChecked(isLockToAppEnabled());
    }

    public void onDestroyView() {
        super.onDestroyView();
        this.mSwitchBar.removeOnSwitchChangeListener(this);
        this.mSwitchBar.hide();
    }

    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.screen_pinning_instructions, null);
    }

    private boolean isLockToAppEnabled() {
        try {
            return System.getInt(getContentResolver(), "lock_to_app_enabled") != 0;
        } catch (SettingNotFoundException e) {
            return false;
        }
    }

    private void setLockToAppEnabled(boolean isEnabled) {
        System.putInt(getContentResolver(), "lock_to_app_enabled", isEnabled ? 1 : 0);
    }

    public void onSwitchChanged(Switch switchView, boolean isChecked) {
        setLockToAppEnabled(isChecked);
    }
}
