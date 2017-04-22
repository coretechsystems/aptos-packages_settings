package com.android.settings.inputmethod;

import android.app.Fragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import com.android.internal.app.LocalePicker.LocaleSelectionListener;
import com.android.settings.R;
import com.android.settings.SettingsActivity;
import com.android.settings.inputmethod.UserDictionaryAddWordContents.LocaleRenderer;
import java.util.Locale;

public class UserDictionaryAddWordFragment extends Fragment implements OnItemSelectedListener, LocaleSelectionListener {
    private UserDictionaryAddWordContents mContents;
    private boolean mIsDeleting = false;
    private View mRootView;

    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        setHasOptionsMenu(true);
        getActivity().getActionBar().setTitle(R.string.user_dict_settings_title);
        setRetainInstance(true);
    }

    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedState) {
        this.mRootView = inflater.inflate(R.layout.user_dictionary_add_word_fullscreen, null);
        this.mIsDeleting = false;
        if (this.mContents == null) {
            this.mContents = new UserDictionaryAddWordContents(this.mRootView, getArguments());
        } else {
            this.mContents = new UserDictionaryAddWordContents(this.mRootView, this.mContents);
        }
        getActivity().getActionBar().setSubtitle(UserDictionarySettingsUtils.getLocaleDisplayName(getActivity(), this.mContents.getCurrentUserDictionaryLocale()));
        return this.mRootView;
    }

    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        menu.add(0, 1, 0, R.string.delete).setIcon(17301564).setShowAsAction(5);
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() != 1) {
            return false;
        }
        this.mContents.delete(getActivity());
        this.mIsDeleting = true;
        getActivity().onBackPressed();
        return true;
    }

    public void onResume() {
        super.onResume();
        updateSpinner();
    }

    private void updateSpinner() {
        new ArrayAdapter(getActivity(), 17367048, this.mContents.getLocalesList(getActivity())).setDropDownViewResource(17367049);
    }

    public void onPause() {
        super.onPause();
        if (!this.mIsDeleting) {
            this.mContents.apply(getActivity(), null);
        }
    }

    public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
        LocaleRenderer locale = (LocaleRenderer) parent.getItemAtPosition(pos);
        if (locale.isMoreLanguages()) {
            ((SettingsActivity) getActivity()).startPreferenceFragment(new UserDictionaryLocalePicker(this), true);
        } else {
            this.mContents.updateLocale(locale.getLocaleString());
        }
    }

    public void onNothingSelected(AdapterView<?> adapterView) {
        this.mContents.updateLocale(getArguments().getString("locale"));
    }

    public void onLocaleSelected(Locale locale) {
        this.mContents.updateLocale(locale.toString());
        getActivity().onBackPressed();
    }
}
