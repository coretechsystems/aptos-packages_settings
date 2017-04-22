package com.android.settings;

import android.content.Context;
import android.preference.Preference;
import android.util.AttributeSet;
import android.view.View;

public class ProgressCategory extends ProgressCategoryBase {
    private int mEmptyTextRes;
    private boolean mNoDeviceFoundAdded;
    private Preference mNoDeviceFoundPreference;
    private boolean mProgress;

    public ProgressCategory(Context context) {
        this(context, null);
    }

    public ProgressCategory(Context context, AttributeSet attrs) {
        super(context, attrs, 0);
        this.mProgress = false;
    }

    public ProgressCategory(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public ProgressCategory(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        this.mProgress = false;
        setLayoutResource(R.layout.preference_progress_category);
    }

    public void setEmptyTextRes(int emptyTextRes) {
        this.mEmptyTextRes = emptyTextRes;
    }

    public void onBindView(View view) {
        super.onBindView(view);
        View progressBar = view.findViewById(R.id.scanning_progress);
        boolean noDeviceFound;
        if (getPreferenceCount() == 0 || (getPreferenceCount() == 1 && getPreference(0) == this.mNoDeviceFoundPreference)) {
            noDeviceFound = true;
        } else {
            noDeviceFound = false;
        }
        progressBar.setVisibility(this.mProgress ? 0 : 8);
        if (this.mProgress || !noDeviceFound) {
            if (this.mNoDeviceFoundAdded) {
                removePreference(this.mNoDeviceFoundPreference);
                this.mNoDeviceFoundAdded = false;
            }
        } else if (!this.mNoDeviceFoundAdded) {
            if (this.mNoDeviceFoundPreference == null) {
                this.mNoDeviceFoundPreference = new Preference(getContext());
                this.mNoDeviceFoundPreference.setLayoutResource(R.layout.preference_empty_list);
                this.mNoDeviceFoundPreference.setTitle(this.mEmptyTextRes);
                this.mNoDeviceFoundPreference.setSelectable(false);
            }
            addPreference(this.mNoDeviceFoundPreference);
            this.mNoDeviceFoundAdded = true;
        }
    }

    public void setProgress(boolean progressOn) {
        this.mProgress = progressOn;
        notifyChanged();
    }
}
