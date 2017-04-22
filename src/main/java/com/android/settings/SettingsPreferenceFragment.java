package com.android.settings;

import android.app.Activity;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnDismissListener;
import android.content.pm.PackageManager;
import android.database.DataSetObserver;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceFragment;
import android.preference.PreferenceGroupAdapter;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ListAdapter;
import android.widget.ListView;

public class SettingsPreferenceFragment extends PreferenceFragment implements DialogCreatable {
    private ContentResolver mContentResolver;
    private ListAdapter mCurrentRootAdapter;
    private DataSetObserver mDataSetObserver = new DataSetObserver() {
        public void onChanged() {
            SettingsPreferenceFragment.this.highlightPreferenceIfNeeded();
        }

        public void onInvalidated() {
            SettingsPreferenceFragment.this.highlightPreferenceIfNeeded();
        }
    };
    private SettingsDialogFragment mDialogFragment;
    private String mHelpUrl;
    private Drawable mHighlightDrawable;
    private boolean mIsDataSetObserverRegistered = false;
    private ViewGroup mPinnedHeaderFrameLayout;
    private boolean mPreferenceHighlighted = false;
    private String mPreferenceKey;

    public static class SettingsDialogFragment extends DialogFragment {
        private int mDialogId;
        private OnCancelListener mOnCancelListener;
        private OnDismissListener mOnDismissListener;
        private Fragment mParentFragment;

        public SettingsDialogFragment(DialogCreatable fragment, int dialogId) {
            this.mDialogId = dialogId;
            if (fragment instanceof Fragment) {
                this.mParentFragment = (Fragment) fragment;
                return;
            }
            throw new IllegalArgumentException("fragment argument must be an instance of " + Fragment.class.getName());
        }

        public void onSaveInstanceState(Bundle outState) {
            super.onSaveInstanceState(outState);
            if (this.mParentFragment != null) {
                outState.putInt("key_dialog_id", this.mDialogId);
                outState.putInt("key_parent_fragment_id", this.mParentFragment.getId());
            }
        }

        public void onStart() {
            super.onStart();
            if (this.mParentFragment != null && (this.mParentFragment instanceof SettingsPreferenceFragment)) {
                ((SettingsPreferenceFragment) this.mParentFragment).onDialogShowing();
            }
        }

        public Dialog onCreateDialog(Bundle savedInstanceState) {
            if (savedInstanceState != null) {
                this.mDialogId = savedInstanceState.getInt("key_dialog_id", 0);
                this.mParentFragment = getParentFragment();
                int mParentFragmentId = savedInstanceState.getInt("key_parent_fragment_id", -1);
                if (this.mParentFragment == null) {
                    this.mParentFragment = getFragmentManager().findFragmentById(mParentFragmentId);
                }
                if (!(this.mParentFragment instanceof DialogCreatable)) {
                    throw new IllegalArgumentException((this.mParentFragment != null ? this.mParentFragment.getClass().getName() : Integer.valueOf(mParentFragmentId)) + " must implement " + DialogCreatable.class.getName());
                } else if (this.mParentFragment instanceof SettingsPreferenceFragment) {
                    ((SettingsPreferenceFragment) this.mParentFragment).mDialogFragment = this;
                }
            }
            return ((DialogCreatable) this.mParentFragment).onCreateDialog(this.mDialogId);
        }

        public void onCancel(DialogInterface dialog) {
            super.onCancel(dialog);
            if (this.mOnCancelListener != null) {
                this.mOnCancelListener.onCancel(dialog);
            }
        }

        public void onDismiss(DialogInterface dialog) {
            super.onDismiss(dialog);
            if (this.mOnDismissListener != null) {
                this.mOnDismissListener.onDismiss(dialog);
            }
        }

        public int getDialogId() {
            return this.mDialogId;
        }

        public void onDetach() {
            super.onDetach();
            if ((this.mParentFragment instanceof SettingsPreferenceFragment) && ((SettingsPreferenceFragment) this.mParentFragment).mDialogFragment == this) {
                ((SettingsPreferenceFragment) this.mParentFragment).mDialogFragment = null;
            }
        }
    }

    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        if (icicle != null) {
            this.mPreferenceHighlighted = icicle.getBoolean("android:preference_highlighted");
        }
        int helpResource = getHelpResource();
        if (helpResource != 0) {
            this.mHelpUrl = getResources().getString(helpResource);
        }
    }

    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View root = super.onCreateView(inflater, container, savedInstanceState);
        this.mPinnedHeaderFrameLayout = (ViewGroup) root.findViewById(R.id.pinned_header);
        return root;
    }

    public void setPinnedHeaderView(View pinnedHeader) {
        this.mPinnedHeaderFrameLayout.addView(pinnedHeader);
        this.mPinnedHeaderFrameLayout.setVisibility(0);
    }

    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean("android:preference_highlighted", this.mPreferenceHighlighted);
    }

    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        if (!TextUtils.isEmpty(this.mHelpUrl)) {
            setHasOptionsMenu(true);
        }
    }

    public void onResume() {
        super.onResume();
        Bundle args = getArguments();
        if (args != null) {
            this.mPreferenceKey = args.getString(":settings:fragment_args_key");
            highlightPreferenceIfNeeded();
        }
    }

    protected void onBindPreferences() {
        registerObserverIfNeeded();
    }

    protected void onUnbindPreferences() {
        unregisterObserverIfNeeded();
    }

    public void onStop() {
        super.onStop();
        unregisterObserverIfNeeded();
    }

    public void registerObserverIfNeeded() {
        if (!this.mIsDataSetObserverRegistered) {
            if (this.mCurrentRootAdapter != null) {
                this.mCurrentRootAdapter.unregisterDataSetObserver(this.mDataSetObserver);
            }
            this.mCurrentRootAdapter = getPreferenceScreen().getRootAdapter();
            this.mCurrentRootAdapter.registerDataSetObserver(this.mDataSetObserver);
            this.mIsDataSetObserverRegistered = true;
        }
    }

    public void unregisterObserverIfNeeded() {
        if (this.mIsDataSetObserverRegistered) {
            if (this.mCurrentRootAdapter != null) {
                this.mCurrentRootAdapter.unregisterDataSetObserver(this.mDataSetObserver);
                this.mCurrentRootAdapter = null;
            }
            this.mIsDataSetObserverRegistered = false;
        }
    }

    public void highlightPreferenceIfNeeded() {
        if (isAdded() && !this.mPreferenceHighlighted && !TextUtils.isEmpty(this.mPreferenceKey)) {
            highlightPreference(this.mPreferenceKey);
        }
    }

    private Drawable getHighlightDrawable() {
        if (this.mHighlightDrawable == null) {
            this.mHighlightDrawable = getActivity().getDrawable(R.drawable.preference_highlight);
        }
        return this.mHighlightDrawable;
    }

    private int canUseListViewForHighLighting(String key) {
        if (!hasListView()) {
            return -1;
        }
        ListAdapter adapter = getListView().getAdapter();
        if (adapter == null || !(adapter instanceof PreferenceGroupAdapter)) {
            return -1;
        }
        return findListPositionFromKey(adapter, key);
    }

    private void highlightPreference(String key) {
        final Drawable highlight = getHighlightDrawable();
        final int position = canUseListViewForHighLighting(key);
        if (position >= 0) {
            this.mPreferenceHighlighted = true;
            final ListView listView = getListView();
            ListAdapter adapter = listView.getAdapter();
            ((PreferenceGroupAdapter) adapter).setHighlightedDrawable(highlight);
            ((PreferenceGroupAdapter) adapter).setHighlighted(position);
            listView.post(new Runnable() {
                public void run() {
                    listView.setSelection(position);
                    listView.postDelayed(new Runnable() {
                        public void run() {
                            int index = position - listView.getFirstVisiblePosition();
                            if (index >= 0 && index < listView.getChildCount()) {
                                View v = listView.getChildAt(index);
                                highlight.setHotspot((float) (v.getWidth() / 2), (float) (v.getHeight() / 2));
                                v.setPressed(true);
                                v.setPressed(false);
                            }
                        }
                    }, 600);
                }
            });
        }
    }

    private int findListPositionFromKey(ListAdapter adapter, String key) {
        int count = adapter.getCount();
        for (int n = 0; n < count; n++) {
            Preference item = adapter.getItem(n);
            if (item instanceof Preference) {
                String preferenceKey = item.getKey();
                if (preferenceKey != null && preferenceKey.equals(key)) {
                    return n;
                }
            }
        }
        return -1;
    }

    protected void removePreference(String key) {
        Preference pref = findPreference(key);
        if (pref != null) {
            getPreferenceScreen().removePreference(pref);
        }
    }

    protected int getHelpResource() {
        return 0;
    }

    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        if (this.mHelpUrl != null && getActivity() != null) {
            HelpUtils.prepareHelpMenuItem(getActivity(), menu.add(0, 101, 0, R.string.help_label), this.mHelpUrl);
        }
    }

    public final void finishFragment() {
        getActivity().onBackPressed();
    }

    protected ContentResolver getContentResolver() {
        Context context = getActivity();
        if (context != null) {
            this.mContentResolver = context.getContentResolver();
        }
        return this.mContentResolver;
    }

    protected Object getSystemService(String name) {
        return getActivity().getSystemService(name);
    }

    protected PackageManager getPackageManager() {
        return getActivity().getPackageManager();
    }

    public void onDetach() {
        if (isRemoving() && this.mDialogFragment != null) {
            this.mDialogFragment.dismiss();
            this.mDialogFragment = null;
        }
        super.onDetach();
    }

    protected void showDialog(int dialogId) {
        if (this.mDialogFragment != null) {
            Log.e("SettingsPreferenceFragment", "Old dialog fragment not null!");
        }
        this.mDialogFragment = new SettingsDialogFragment(this, dialogId);
        this.mDialogFragment.show(getChildFragmentManager(), Integer.toString(dialogId));
    }

    public Dialog onCreateDialog(int dialogId) {
        return null;
    }

    protected void removeDialog(int dialogId) {
        if (this.mDialogFragment != null && this.mDialogFragment.getDialogId() == dialogId) {
            this.mDialogFragment.dismiss();
        }
        this.mDialogFragment = null;
    }

    protected void setOnDismissListener(OnDismissListener listener) {
        if (this.mDialogFragment != null) {
            this.mDialogFragment.mOnDismissListener = listener;
        }
    }

    public void onDialogShowing() {
    }

    protected boolean hasNextButton() {
        return ((ButtonBarHandler) getActivity()).hasNextButton();
    }

    protected Button getNextButton() {
        return ((ButtonBarHandler) getActivity()).getNextButton();
    }

    public void finish() {
        getActivity().onBackPressed();
    }

    public boolean startFragment(Fragment caller, String fragmentClass, int titleRes, int requestCode, Bundle extras) {
        Activity activity = getActivity();
        if (activity instanceof SettingsActivity) {
            ((SettingsActivity) activity).startPreferencePanel(fragmentClass, extras, titleRes, null, caller, requestCode);
            return true;
        } else if (activity instanceof PreferenceActivity) {
            ((PreferenceActivity) activity).startPreferencePanel(fragmentClass, extras, titleRes, null, caller, requestCode);
            return true;
        } else {
            Log.w("SettingsPreferenceFragment", "Parent isn't SettingsActivity nor PreferenceActivity, thus there's no way to launch the given Fragment (name: " + fragmentClass + ", requestCode: " + requestCode + ")");
            return false;
        }
    }
}
