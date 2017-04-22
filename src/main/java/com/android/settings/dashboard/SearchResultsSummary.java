package com.android.settings.dashboard;

import android.app.Fragment;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources.NotFoundException;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.SearchView;
import android.widget.TextView;
import com.android.settings.R;
import com.android.settings.SettingsActivity;
import com.android.settings.Utils;
import com.android.settings.search.Index;
import java.util.HashMap;

public class SearchResultsSummary extends Fragment {
    private static char ELLIPSIS = '…';
    private ViewGroup mLayoutResults;
    private ViewGroup mLayoutSuggestions;
    private String mQuery;
    private SearchResultsAdapter mResultsAdapter;
    private ListView mResultsListView;
    private SearchView mSearchView;
    private boolean mShowResults;
    private SuggestionsAdapter mSuggestionsAdapter;
    private ListView mSuggestionsListView;
    private UpdateSearchResultsTask mUpdateSearchResultsTask;
    private UpdateSuggestionsTask mUpdateSuggestionsTask;

    private static class SearchResult {
        public Context context;
        public String entries;
        public int iconResId;
        public String key;
        public String summaryOff;
        public String summaryOn;
        public String title;

        public SearchResult(Context context, String title, String summaryOn, String summaryOff, String entries, int iconResId, String key) {
            this.context = context;
            this.title = title;
            this.summaryOn = summaryOn;
            this.summaryOff = summaryOff;
            this.entries = entries;
            this.iconResId = iconResId;
            this.key = key;
        }
    }

    private static class SearchResultsAdapter extends BaseAdapter {
        private Context mContext;
        private HashMap<String, Context> mContextMap = new HashMap();
        private Cursor mCursor;
        private boolean mDataValid;
        private LayoutInflater mInflater;

        public SearchResultsAdapter(Context context) {
            this.mContext = context;
            this.mInflater = (LayoutInflater) this.mContext.getSystemService("layout_inflater");
            this.mDataValid = false;
        }

        public Cursor swapCursor(Cursor newCursor) {
            if (newCursor == this.mCursor) {
                return null;
            }
            Cursor oldCursor = this.mCursor;
            this.mCursor = newCursor;
            if (newCursor != null) {
                this.mDataValid = true;
                notifyDataSetChanged();
                return oldCursor;
            }
            this.mDataValid = false;
            notifyDataSetInvalidated();
            return oldCursor;
        }

        public int getCount() {
            if (!this.mDataValid || this.mCursor == null || this.mCursor.isClosed()) {
                return 0;
            }
            return this.mCursor.getCount();
        }

        public Object getItem(int position) {
            if (!this.mDataValid || !this.mCursor.moveToPosition(position)) {
                return null;
            }
            Context packageContext;
            String title = this.mCursor.getString(1);
            String summaryOn = this.mCursor.getString(2);
            String summaryOff = this.mCursor.getString(3);
            String entries = this.mCursor.getString(4);
            String iconResStr = this.mCursor.getString(8);
            String className = this.mCursor.getString(6);
            String packageName = this.mCursor.getString(10);
            String key = this.mCursor.getString(13);
            if (!TextUtils.isEmpty(className) || TextUtils.isEmpty(packageName)) {
                packageContext = this.mContext;
            } else {
                packageContext = (Context) this.mContextMap.get(packageName);
                if (packageContext == null) {
                    try {
                        packageContext = this.mContext.createPackageContext(packageName, 0);
                        this.mContextMap.put(packageName, packageContext);
                    } catch (NameNotFoundException e) {
                        Log.e("SearchResultsSummary", "Cannot create Context for package: " + packageName);
                        return null;
                    }
                }
            }
            return new SearchResult(packageContext, title, summaryOn, summaryOff, entries, TextUtils.isEmpty(iconResStr) ? R.drawable.empty_icon : Integer.parseInt(iconResStr), key);
        }

        public long getItemId(int position) {
            return 0;
        }

        public View getView(int position, View convertView, ViewGroup parent) {
            if (!this.mDataValid && convertView == null) {
                throw new IllegalStateException("this should only be called when the cursor is valid");
            } else if (this.mCursor.moveToPosition(position)) {
                View view;
                if (convertView == null) {
                    view = this.mInflater.inflate(R.layout.search_result_item, parent, false);
                } else {
                    view = convertView;
                }
                ImageView imageView = (ImageView) view.findViewById(R.id.icon);
                SearchResult result = (SearchResult) getItem(position);
                ((TextView) view.findViewById(R.id.title)).setText(result.title);
                if (result.iconResId != R.drawable.empty_icon) {
                    try {
                        imageView.setImageDrawable(result.context.getDrawable(result.iconResId));
                    } catch (NotFoundException e) {
                        Log.e("SearchResultsSummary", "Cannot load Drawable for " + result.title);
                    }
                } else {
                    imageView.setImageDrawable(null);
                    imageView.setBackgroundResource(R.drawable.empty_icon);
                }
                return view;
            } else {
                throw new IllegalStateException("couldn't move cursor to position " + position);
            }
        }
    }

    private static class SuggestionItem {
        public String query;

        public SuggestionItem(String query) {
            this.query = query;
        }
    }

    private static class SuggestionsAdapter extends BaseAdapter {
        private Context mContext;
        private Cursor mCursor;
        private boolean mDataValid = false;
        private LayoutInflater mInflater;

        public SuggestionsAdapter(Context context) {
            this.mContext = context;
            this.mInflater = (LayoutInflater) this.mContext.getSystemService("layout_inflater");
            this.mDataValid = false;
        }

        public Cursor swapCursor(Cursor newCursor) {
            if (newCursor == this.mCursor) {
                return null;
            }
            Cursor oldCursor = this.mCursor;
            this.mCursor = newCursor;
            if (newCursor != null) {
                this.mDataValid = true;
                notifyDataSetChanged();
                return oldCursor;
            }
            this.mDataValid = false;
            notifyDataSetInvalidated();
            return oldCursor;
        }

        public int getCount() {
            if (!this.mDataValid || this.mCursor == null || this.mCursor.isClosed()) {
                return 0;
            }
            return this.mCursor.getCount();
        }

        public Object getItem(int position) {
            if (this.mDataValid && this.mCursor.moveToPosition(position)) {
                return new SuggestionItem(this.mCursor.getString(0));
            }
            return null;
        }

        public long getItemId(int position) {
            return 0;
        }

        public View getView(int position, View convertView, ViewGroup parent) {
            if (!this.mDataValid && convertView == null) {
                throw new IllegalStateException("this should only be called when the cursor is valid");
            } else if (this.mCursor.moveToPosition(position)) {
                View view;
                if (convertView == null) {
                    view = this.mInflater.inflate(R.layout.search_suggestion_item, parent, false);
                } else {
                    view = convertView;
                }
                ((TextView) view.findViewById(R.id.title)).setText(((SuggestionItem) getItem(position)).query);
                return view;
            } else {
                throw new IllegalStateException("couldn't move cursor to position " + position);
            }
        }
    }

    private class UpdateSearchResultsTask extends AsyncTask<String, Void, Cursor> {
        private UpdateSearchResultsTask() {
        }

        protected Cursor doInBackground(String... params) {
            return Index.getInstance(SearchResultsSummary.this.getActivity()).search(params[0]);
        }

        protected void onPostExecute(Cursor cursor) {
            if (!isCancelled()) {
                SearchResultsSummary.this.setResultsCursor(cursor);
                SearchResultsSummary.this.setResultsVisibility(cursor.getCount() > 0);
            } else if (cursor != null) {
                cursor.close();
            }
        }
    }

    private class UpdateSuggestionsTask extends AsyncTask<String, Void, Cursor> {
        private UpdateSuggestionsTask() {
        }

        protected Cursor doInBackground(String... params) {
            return Index.getInstance(SearchResultsSummary.this.getActivity()).getSuggestions(params[0]);
        }

        protected void onPostExecute(Cursor cursor) {
            if (!isCancelled()) {
                SearchResultsSummary.this.setSuggestionsCursor(cursor);
                SearchResultsSummary.this.setSuggestionsVisibility(cursor.getCount() > 0);
            } else if (cursor != null) {
                cursor.close();
            }
        }
    }

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.mResultsAdapter = new SearchResultsAdapter(getActivity());
        this.mSuggestionsAdapter = new SuggestionsAdapter(getActivity());
        if (savedInstanceState != null) {
            this.mShowResults = savedInstanceState.getBoolean(":settings:show_results");
        }
    }

    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(":settings:show_results", this.mShowResults);
    }

    public void onStop() {
        super.onStop();
        clearSuggestions();
        clearResults();
    }

    public void onDestroy() {
        this.mResultsListView = null;
        this.mResultsAdapter = null;
        this.mUpdateSearchResultsTask = null;
        this.mSuggestionsListView = null;
        this.mSuggestionsAdapter = null;
        this.mUpdateSuggestionsTask = null;
        this.mSearchView = null;
        super.onDestroy();
    }

    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.search_panel, container, false);
        this.mLayoutSuggestions = (ViewGroup) view.findViewById(R.id.layout_suggestions);
        this.mLayoutResults = (ViewGroup) view.findViewById(R.id.layout_results);
        this.mResultsListView = (ListView) view.findViewById(R.id.list_results);
        this.mResultsListView.setAdapter(this.mResultsAdapter);
        this.mResultsListView.setOnItemClickListener(new OnItemClickListener() {
            public void onItemClick(AdapterView<?> adapterView, View view, int position, long id) {
                position--;
                if (position >= 0) {
                    Cursor cursor = SearchResultsSummary.this.mResultsAdapter.mCursor;
                    cursor.moveToPosition(position);
                    String className = cursor.getString(6);
                    String screenTitle = cursor.getString(7);
                    String action = cursor.getString(9);
                    String key = cursor.getString(13);
                    SettingsActivity sa = (SettingsActivity) SearchResultsSummary.this.getActivity();
                    sa.needToRevertToInitialFragment();
                    if (TextUtils.isEmpty(action)) {
                        Bundle args = new Bundle();
                        args.putString(":settings:fragment_args_key", key);
                        Utils.startWithFragment(sa, className, args, null, 0, -1, screenTitle);
                    } else {
                        Intent intent = new Intent(action);
                        String targetPackage = cursor.getString(10);
                        String targetClass = cursor.getString(11);
                        if (!(TextUtils.isEmpty(targetPackage) || TextUtils.isEmpty(targetClass))) {
                            intent.setComponent(new ComponentName(targetPackage, targetClass));
                        }
                        intent.putExtra(":settings:fragment_args_key", key);
                        sa.startActivity(intent);
                    }
                    SearchResultsSummary.this.saveQueryToDatabase();
                }
            }
        });
        this.mResultsListView.addHeaderView(LayoutInflater.from(getActivity()).inflate(R.layout.search_panel_results_header, this.mResultsListView, false), null, false);
        this.mSuggestionsListView = (ListView) view.findViewById(R.id.list_suggestions);
        this.mSuggestionsListView.setAdapter(this.mSuggestionsAdapter);
        this.mSuggestionsListView.setOnItemClickListener(new OnItemClickListener() {
            public void onItemClick(AdapterView<?> adapterView, View view, int position, long id) {
                position--;
                if (position >= 0) {
                    Cursor cursor = SearchResultsSummary.this.mSuggestionsAdapter.mCursor;
                    cursor.moveToPosition(position);
                    SearchResultsSummary.this.mShowResults = true;
                    SearchResultsSummary.this.mQuery = cursor.getString(0);
                    SearchResultsSummary.this.mSearchView.setQuery(SearchResultsSummary.this.mQuery, false);
                }
            }
        });
        this.mSuggestionsListView.addHeaderView(LayoutInflater.from(getActivity()).inflate(R.layout.search_panel_suggestions_header, this.mSuggestionsListView, false), null, false);
        return view;
    }

    public void onResume() {
        super.onResume();
        if (!this.mShowResults) {
            showSomeSuggestions();
        }
    }

    public void setSearchView(SearchView searchView) {
        this.mSearchView = searchView;
    }

    private void setSuggestionsVisibility(boolean visible) {
        if (this.mLayoutSuggestions != null) {
            this.mLayoutSuggestions.setVisibility(visible ? 0 : 8);
        }
    }

    private void setResultsVisibility(boolean visible) {
        if (this.mLayoutResults != null) {
            this.mLayoutResults.setVisibility(visible ? 0 : 8);
        }
    }

    private void saveQueryToDatabase() {
        Index.getInstance(getActivity()).addSavedQuery(this.mQuery);
    }

    public boolean onQueryTextSubmit(String query) {
        this.mQuery = getFilteredQueryString(query);
        this.mShowResults = true;
        setSuggestionsVisibility(false);
        updateSearchResults();
        saveQueryToDatabase();
        return true;
    }

    public boolean onQueryTextChange(String query) {
        this.mQuery = getFilteredQueryString(query);
        if (TextUtils.isEmpty(this.mQuery)) {
            this.mShowResults = false;
            setResultsVisibility(false);
            updateSuggestions();
        } else {
            this.mShowResults = true;
            setSuggestionsVisibility(false);
            updateSearchResults();
        }
        return true;
    }

    public void showSomeSuggestions() {
        setResultsVisibility(false);
        this.mQuery = "";
        updateSuggestions();
    }

    private void clearSuggestions() {
        if (this.mUpdateSuggestionsTask != null) {
            this.mUpdateSuggestionsTask.cancel(false);
            this.mUpdateSuggestionsTask = null;
        }
        setSuggestionsCursor(null);
    }

    private void setSuggestionsCursor(Cursor cursor) {
        if (this.mSuggestionsAdapter != null) {
            Cursor oldCursor = this.mSuggestionsAdapter.swapCursor(cursor);
            if (oldCursor != null) {
                oldCursor.close();
            }
        }
    }

    private void clearResults() {
        if (this.mUpdateSearchResultsTask != null) {
            this.mUpdateSearchResultsTask.cancel(false);
            this.mUpdateSearchResultsTask = null;
        }
        setResultsCursor(null);
    }

    private void setResultsCursor(Cursor cursor) {
        if (this.mResultsAdapter != null) {
            Cursor oldCursor = this.mResultsAdapter.swapCursor(cursor);
            if (oldCursor != null) {
                oldCursor.close();
            }
        }
    }

    private String getFilteredQueryString(CharSequence query) {
        if (query == null) {
            return null;
        }
        StringBuilder filtered = new StringBuilder();
        for (int n = 0; n < query.length(); n++) {
            char c = query.charAt(n);
            if (Character.isLetterOrDigit(c) || Character.isSpaceChar(c)) {
                filtered.append(c);
            }
        }
        return filtered.toString();
    }

    private void clearAllTasks() {
        if (this.mUpdateSearchResultsTask != null) {
            this.mUpdateSearchResultsTask.cancel(false);
            this.mUpdateSearchResultsTask = null;
        }
        if (this.mUpdateSuggestionsTask != null) {
            this.mUpdateSuggestionsTask.cancel(false);
            this.mUpdateSuggestionsTask = null;
        }
    }

    private void updateSuggestions() {
        clearAllTasks();
        if (this.mQuery == null) {
            setSuggestionsCursor(null);
            return;
        }
        this.mUpdateSuggestionsTask = new UpdateSuggestionsTask();
        this.mUpdateSuggestionsTask.execute(new String[]{this.mQuery});
    }

    private void updateSearchResults() {
        clearAllTasks();
        if (TextUtils.isEmpty(this.mQuery)) {
            setResultsVisibility(false);
            setResultsCursor(null);
            return;
        }
        this.mUpdateSearchResultsTask = new UpdateSearchResultsTask();
        this.mUpdateSearchResultsTask.execute(new String[]{this.mQuery});
    }
}
