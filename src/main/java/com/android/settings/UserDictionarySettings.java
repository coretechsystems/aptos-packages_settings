package com.android.settings;

import android.app.ListFragment;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.provider.UserDictionary.Words;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AlphabetIndexer;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.SectionIndexer;
import android.widget.SimpleCursorAdapter;
import android.widget.SimpleCursorAdapter.ViewBinder;
import android.widget.TextView;
import com.android.settings.inputmethod.UserDictionaryAddWordFragment;
import com.android.settings.inputmethod.UserDictionarySettingsUtils;
import java.util.Locale;

public class UserDictionarySettings extends ListFragment {
    private static final String[] QUERY_PROJECTION = new String[]{"_id", "word", "shortcut"};
    private Cursor mCursor;
    protected String mLocale;

    private static class MyAdapter extends SimpleCursorAdapter implements SectionIndexer {
        private AlphabetIndexer mIndexer;
        private final ViewBinder mViewBinder = new ViewBinder() {
            public boolean setViewValue(View v, Cursor c, int columnIndex) {
                if (columnIndex != 2) {
                    return false;
                }
                String shortcut = c.getString(2);
                if (TextUtils.isEmpty(shortcut)) {
                    v.setVisibility(8);
                } else {
                    ((TextView) v).setText(shortcut);
                    v.setVisibility(0);
                }
                v.invalidate();
                return true;
            }
        };

        public MyAdapter(Context context, int layout, Cursor c, String[] from, int[] to, UserDictionarySettings settings) {
            super(context, layout, c, from, to);
            if (c != null) {
                this.mIndexer = new AlphabetIndexer(c, c.getColumnIndexOrThrow("word"), context.getString(17040630));
            }
            setViewBinder(this.mViewBinder);
        }

        public int getPositionForSection(int section) {
            return this.mIndexer == null ? 0 : this.mIndexer.getPositionForSection(section);
        }

        public int getSectionForPosition(int position) {
            return this.mIndexer == null ? 0 : this.mIndexer.getSectionForPosition(position);
        }

        public Object[] getSections() {
            return this.mIndexer == null ? null : this.mIndexer.getSections();
        }
    }

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getActivity().getActionBar().setTitle(R.string.user_dict_settings_title);
    }

    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(17367198, container, false);
    }

    public void onActivityCreated(Bundle savedInstanceState) {
        String locale;
        super.onActivityCreated(savedInstanceState);
        Intent intent = getActivity().getIntent();
        String localeFromIntent = intent == null ? null : intent.getStringExtra("locale");
        Bundle arguments = getArguments();
        String localeFromArguments = arguments == null ? null : arguments.getString("locale");
        if (localeFromArguments != null) {
            locale = localeFromArguments;
        } else if (localeFromIntent != null) {
            locale = localeFromIntent;
        } else {
            locale = null;
        }
        this.mLocale = locale;
        this.mCursor = createCursor(locale);
        TextView emptyView = (TextView) getView().findViewById(16908292);
        emptyView.setText(R.string.user_dict_settings_empty_text);
        ListView listView = getListView();
        listView.setAdapter(createAdapter());
        listView.setFastScrollEnabled(true);
        listView.setEmptyView(emptyView);
        setHasOptionsMenu(true);
        getActivity().getActionBar().setSubtitle(UserDictionarySettingsUtils.getLocaleDisplayName(getActivity(), this.mLocale));
    }

    private Cursor createCursor(String locale) {
        if ("".equals(locale)) {
            return getActivity().managedQuery(Words.CONTENT_URI, QUERY_PROJECTION, "locale is null", null, "UPPER(word)");
        }
        String queryLocale = locale != null ? locale : Locale.getDefault().toString();
        return getActivity().managedQuery(Words.CONTENT_URI, QUERY_PROJECTION, "locale=?", new String[]{queryLocale}, "UPPER(word)");
    }

    private ListAdapter createAdapter() {
        return new MyAdapter(getActivity(), R.layout.user_dictionary_item, this.mCursor, new String[]{"word", "shortcut"}, new int[]{16908308, 16908309}, this);
    }

    public void onListItemClick(ListView l, View v, int position, long id) {
        String word = getWord(position);
        String shortcut = getShortcut(position);
        if (word != null) {
            showAddOrEditDialog(word, shortcut);
        }
    }

    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        menu.add(0, 1, 0, R.string.user_dict_settings_add_menu_title).setIcon(R.drawable.ic_menu_add_dark).setShowAsAction(5);
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() != 1) {
            return false;
        }
        showAddOrEditDialog(null, null);
        return true;
    }

    private void showAddOrEditDialog(String editingWord, String editingShortcut) {
        int i;
        Bundle args = new Bundle();
        String str = "mode";
        if (editingWord == null) {
            i = 1;
        } else {
            i = 0;
        }
        args.putInt(str, i);
        args.putString("word", editingWord);
        args.putString("shortcut", editingShortcut);
        args.putString("locale", this.mLocale);
        ((SettingsActivity) getActivity()).startPreferencePanel(UserDictionaryAddWordFragment.class.getName(), args, R.string.user_dict_settings_add_dialog_title, null, null, 0);
    }

    private String getWord(int position) {
        if (this.mCursor == null) {
            return null;
        }
        this.mCursor.moveToPosition(position);
        if (this.mCursor.isAfterLast()) {
            return null;
        }
        return this.mCursor.getString(this.mCursor.getColumnIndexOrThrow("word"));
    }

    private String getShortcut(int position) {
        if (this.mCursor == null) {
            return null;
        }
        this.mCursor.moveToPosition(position);
        if (this.mCursor.isAfterLast()) {
            return null;
        }
        return this.mCursor.getString(this.mCursor.getColumnIndexOrThrow("shortcut"));
    }

    public static void deleteWord(String word, String shortcut, ContentResolver resolver) {
        if (TextUtils.isEmpty(shortcut)) {
            resolver.delete(Words.CONTENT_URI, "word=? AND shortcut is null OR shortcut=''", new String[]{word});
            return;
        }
        resolver.delete(Words.CONTENT_URI, "word=? AND shortcut=?", new String[]{word, shortcut});
    }
}
