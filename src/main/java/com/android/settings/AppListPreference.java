package com.android.settings;

import android.app.Activity;
import android.app.AlertDialog.Builder;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.drawable.Drawable;
import android.preference.ListPreference;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckedTextView;
import android.widget.ImageView;

public class AppListPreference extends ListPreference {
    private Drawable[] mEntryDrawables;

    public class AppArrayAdapter extends ArrayAdapter<CharSequence> {
        private Drawable[] mImageDrawables = null;
        private int mSelectedIndex = 0;

        public AppArrayAdapter(Context context, int textViewResourceId, CharSequence[] objects, Drawable[] imageDrawables, int selectedIndex) {
            super(context, textViewResourceId, objects);
            this.mSelectedIndex = selectedIndex;
            this.mImageDrawables = imageDrawables;
        }

        public View getView(int position, View convertView, ViewGroup parent) {
            View view = ((Activity) getContext()).getLayoutInflater().inflate(R.layout.app_preference_item, parent, false);
            CheckedTextView checkedTextView = (CheckedTextView) view.findViewById(R.id.app_label);
            checkedTextView.setText((CharSequence) getItem(position));
            if (position == this.mSelectedIndex) {
                checkedTextView.setChecked(true);
            }
            ((ImageView) view.findViewById(R.id.app_image)).setImageDrawable(this.mImageDrawables[position]);
            return view;
        }
    }

    public AppListPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void setPackageNames(String[] packageNames, String defaultPackageName) {
        int foundPackages = 0;
        PackageManager pm = getContext().getPackageManager();
        ApplicationInfo[] appInfos = new ApplicationInfo[packageNames.length];
        for (int i = 0; i < packageNames.length; i++) {
            try {
                appInfos[i] = pm.getApplicationInfo(packageNames[i], 0);
                foundPackages++;
            } catch (NameNotFoundException e) {
            }
        }
        CharSequence[] applicationNames = new CharSequence[foundPackages];
        this.mEntryDrawables = new Drawable[foundPackages];
        int index = 0;
        int selectedIndex = -1;
        for (ApplicationInfo appInfo : appInfos) {
            if (appInfo != null) {
                applicationNames[index] = appInfo.loadLabel(pm);
                this.mEntryDrawables[index] = appInfo.loadIcon(pm);
                if (defaultPackageName != null && appInfo.packageName.contentEquals(defaultPackageName)) {
                    selectedIndex = index;
                }
                index++;
            }
        }
        setEntries(applicationNames);
        setEntryValues(packageNames);
        if (selectedIndex != -1) {
            setValueIndex(selectedIndex);
        }
    }

    protected void onPrepareDialogBuilder(Builder builder) {
        builder.setAdapter(new AppArrayAdapter(getContext(), R.layout.app_preference_item, getEntries(), this.mEntryDrawables, findIndexOfValue(getValue())), this);
        super.onPrepareDialogBuilder(builder);
    }
}
