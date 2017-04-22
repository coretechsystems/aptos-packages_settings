package com.android.settings.search;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ResolveInfo;
import android.content.res.XmlResourceParser;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.MergeCursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.AsyncTask;
import android.provider.SearchIndexableData;
import android.provider.SearchIndexableResource;
import android.provider.SearchIndexablesContract;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.util.Xml;
import com.android.internal.R;
import com.android.settings.search.Indexable.SearchIndexProvider;
import java.text.Normalizer;
import java.text.Normalizer.Form;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

public class Index {
    private static final List<String> EMPTY_LIST = Collections.emptyList();
    private static final String[] MATCH_COLUMNS_PRIMARY = new String[]{"data_title", "data_title_normalized", "data_keywords"};
    private static final String[] MATCH_COLUMNS_SECONDARY = new String[]{"data_summary_on", "data_summary_on_normalized", "data_summary_off", "data_summary_off_normalized", "data_entries"};
    private static long MAX_SAVED_SEARCH_QUERY = 64;
    private static final Pattern REMOVE_DIACRITICALS_PATTERN = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
    private static final String[] SELECT_COLUMNS = new String[]{"data_rank", "data_title", "data_summary_on", "data_summary_off", "data_entries", "data_keywords", "class_name", "screen_title", "icon", "intent_action", "intent_target_package", "intent_target_class", "enabled", "data_key_reference"};
    private static Index sInstance;
    private final String mBaseAuthority;
    private Context mContext;
    private final UpdateData mDataToProcess = new UpdateData();
    private final AtomicBoolean mIsAvailable = new AtomicBoolean(false);

    private class SaveSearchQueryTask extends AsyncTask<String, Void, Long> {
        private SaveSearchQueryTask() {
        }

        protected Long doInBackground(String... params) {
            long now = new Date().getTime();
            ContentValues values = new ContentValues();
            values.put("query", params[0]);
            values.put("timestamp", Long.valueOf(now));
            SQLiteDatabase database = Index.this.getWritableDatabase();
            long lastInsertedRowId = -1;
            try {
                database.delete("saved_queries", "query = ?", new String[]{params[0]});
                lastInsertedRowId = database.insertOrThrow("saved_queries", null, values);
                if (lastInsertedRowId - Index.MAX_SAVED_SEARCH_QUERY > 0) {
                    Log.d("Index", "Deleted '" + database.delete("saved_queries", "rowId <= ?", new String[]{Long.toString(lastInsertedRowId - Index.MAX_SAVED_SEARCH_QUERY)}) + "' saved Search query(ies)");
                }
            } catch (Exception e) {
                Log.d("Index", "Cannot update saved Search queries", e);
            }
            return Long.valueOf(lastInsertedRowId);
        }
    }

    private static class UpdateData {
        public List<SearchIndexableData> dataToDelete;
        public List<SearchIndexableData> dataToUpdate;
        public boolean forceUpdate;
        public Map<String, List<String>> nonIndexableKeys;

        public UpdateData() {
            this.forceUpdate = false;
            this.dataToUpdate = new ArrayList();
            this.dataToDelete = new ArrayList();
            this.nonIndexableKeys = new HashMap();
        }

        public UpdateData(UpdateData other) {
            this.forceUpdate = false;
            this.dataToUpdate = new ArrayList(other.dataToUpdate);
            this.dataToDelete = new ArrayList(other.dataToDelete);
            this.nonIndexableKeys = new HashMap(other.nonIndexableKeys);
            this.forceUpdate = other.forceUpdate;
        }

        public UpdateData copy() {
            return new UpdateData(this);
        }

        public void clear() {
            this.dataToUpdate.clear();
            this.dataToDelete.clear();
            this.nonIndexableKeys.clear();
            this.forceUpdate = false;
        }
    }

    private class UpdateIndexTask extends AsyncTask<UpdateData, Integer, Void> {
        private UpdateIndexTask() {
        }

        protected void onPreExecute() {
            super.onPreExecute();
            Index.this.mIsAvailable.set(false);
        }

        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);
            Index.this.mIsAvailable.set(true);
        }

        protected Void doInBackground(UpdateData... params) {
            List<SearchIndexableData> dataToUpdate = params[0].dataToUpdate;
            List<SearchIndexableData> dataToDelete = params[0].dataToDelete;
            Map<String, List<String>> nonIndexableKeys = params[0].nonIndexableKeys;
            boolean forceUpdate = params[0].forceUpdate;
            SQLiteDatabase database = Index.this.getWritableDatabase();
            String localeStr = Locale.getDefault().toString();
            try {
                database.beginTransaction();
                if (dataToDelete.size() > 0) {
                    processDataToDelete(database, localeStr, dataToDelete);
                }
                if (dataToUpdate.size() > 0) {
                    processDataToUpdate(database, localeStr, dataToUpdate, nonIndexableKeys, forceUpdate);
                }
                database.setTransactionSuccessful();
                return null;
            } finally {
                database.endTransaction();
            }
        }

        private boolean processDataToUpdate(SQLiteDatabase database, String localeStr, List<SearchIndexableData> dataToUpdate, Map<String, List<String>> nonIndexableKeys, boolean forceUpdate) {
            if (forceUpdate || !isLocaleAlreadyIndexed(database, localeStr)) {
                long current = System.currentTimeMillis();
                int count = dataToUpdate.size();
                for (int n = 0; n < count; n++) {
                    SearchIndexableData data = (SearchIndexableData) dataToUpdate.get(n);
                    try {
                        Index.this.indexOneSearchIndexableData(database, localeStr, data, nonIndexableKeys);
                    } catch (Exception e) {
                        Log.e("Index", "Cannot index: " + data.className + " for locale: " + localeStr, e);
                    }
                }
                Log.d("Index", "Indexing locale '" + localeStr + "' took " + (System.currentTimeMillis() - current) + " millis");
                return false;
            }
            Log.d("Index", "Locale '" + localeStr + "' is already indexed");
            return true;
        }

        private boolean processDataToDelete(SQLiteDatabase database, String localeStr, List<SearchIndexableData> dataToDelete) {
            long current = System.currentTimeMillis();
            int count = dataToDelete.size();
            for (int n = 0; n < count; n++) {
                SearchIndexableData data = (SearchIndexableData) dataToDelete.get(n);
                if (data != null) {
                    if (!TextUtils.isEmpty(data.className)) {
                        delete(database, "class_name", data.className);
                    } else if (data instanceof SearchIndexableRaw) {
                        SearchIndexableRaw raw = (SearchIndexableRaw) data;
                        if (!TextUtils.isEmpty(raw.title)) {
                            delete(database, "data_title", raw.title);
                        }
                    }
                }
            }
            Log.d("Index", "Deleting data for locale '" + localeStr + "' took " + (System.currentTimeMillis() - current) + " millis");
            return false;
        }

        private int delete(SQLiteDatabase database, String columName, String value) {
            return database.delete("prefs_index", columName + "=?", new String[]{value});
        }

        private boolean isLocaleAlreadyIndexed(SQLiteDatabase database, String locale) {
            Cursor cursor = null;
            StringBuilder sb = new StringBuilder("locale");
            sb.append(" = ");
            DatabaseUtils.appendEscapedSQLString(sb, locale);
            try {
                cursor = database.query("prefs_index", null, sb.toString(), null, null, null, null, "1");
                boolean result = cursor.getCount() >= 1;
                if (cursor != null) {
                    cursor.close();
                }
                return result;
            } catch (Throwable th) {
                if (cursor != null) {
                    cursor.close();
                }
            }
        }
    }

    public static Index getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new Index(context, "com.android.settings");
        } else {
            sInstance.setContext(context);
        }
        return sInstance;
    }

    public Index(Context context, String baseAuthority) {
        this.mContext = context;
        this.mBaseAuthority = baseAuthority;
    }

    public void setContext(Context context) {
        this.mContext = context;
    }

    public Cursor search(String query) {
        SQLiteDatabase database = getReadableDatabase();
        Cursor[] cursors = new Cursor[2];
        String primarySql = buildSearchSQL(query, MATCH_COLUMNS_PRIMARY, true);
        Log.d("Index", "Search primary query: " + primarySql);
        cursors[0] = database.rawQuery(primarySql, null);
        StringBuilder sql = new StringBuilder(buildSearchSQL(query, MATCH_COLUMNS_SECONDARY, false));
        sql.append(" EXCEPT ");
        sql.append(primarySql);
        String secondarySql = sql.toString();
        Log.d("Index", "Search secondary query: " + secondarySql);
        cursors[1] = database.rawQuery(secondarySql, null);
        return new MergeCursor(cursors);
    }

    public Cursor getSuggestions(String query) {
        String sql = buildSuggestionsSQL(query);
        Log.d("Index", "Suggestions query: " + sql);
        return getReadableDatabase().rawQuery(sql, null);
    }

    private String buildSuggestionsSQL(String query) {
        StringBuilder sb = new StringBuilder();
        sb.append("SELECT ");
        sb.append("query");
        sb.append(" FROM ");
        sb.append("saved_queries");
        if (TextUtils.isEmpty(query)) {
            sb.append(" ORDER BY rowId DESC");
        } else {
            sb.append(" WHERE ");
            sb.append("query");
            sb.append(" LIKE ");
            sb.append("'");
            sb.append(query);
            sb.append("%");
            sb.append("'");
        }
        sb.append(" LIMIT ");
        sb.append(5);
        return sb.toString();
    }

    public long addSavedQuery(String query) {
        SaveSearchQueryTask task = new SaveSearchQueryTask();
        task.execute(new String[]{query});
        try {
            return ((Long) task.get()).longValue();
        } catch (InterruptedException e) {
            Log.e("Index", "Cannot insert saved query: " + query, e);
            return -1;
        } catch (ExecutionException e2) {
            Log.e("Index", "Cannot insert saved query: " + query, e2);
            return -1;
        }
    }

    public void update() {
        List<ResolveInfo> list = this.mContext.getPackageManager().queryIntentContentProviders(new Intent("android.content.action.SEARCH_INDEXABLES_PROVIDER"), 0);
        int size = list.size();
        for (int n = 0; n < size; n++) {
            ResolveInfo info = (ResolveInfo) list.get(n);
            if (isWellKnownProvider(info)) {
                String authority = info.providerInfo.authority;
                String packageName = info.providerInfo.packageName;
                addIndexablesFromRemoteProvider(packageName, authority);
                addNonIndexablesKeysFromRemoteProvider(packageName, authority);
            }
        }
        updateInternal();
    }

    private boolean addIndexablesFromRemoteProvider(String packageName, String authority) {
        try {
            Context context;
            int baseRank = Ranking.getBaseRankForAuthority(authority);
            if (this.mBaseAuthority.equals(authority)) {
                context = this.mContext;
            } else {
                context = this.mContext.createPackageContext(packageName, 0);
            }
            addIndexablesForXmlResourceUri(context, packageName, buildUriForXmlResources(authority), SearchIndexablesContract.INDEXABLES_XML_RES_COLUMNS, baseRank);
            addIndexablesForRawDataUri(context, packageName, buildUriForRawData(authority), SearchIndexablesContract.INDEXABLES_RAW_COLUMNS, baseRank);
            return true;
        } catch (NameNotFoundException e) {
            Log.w("Index", "Could not create context for " + packageName + ": " + Log.getStackTraceString(e));
            return false;
        }
    }

    private void addNonIndexablesKeysFromRemoteProvider(String packageName, String authority) {
        addNonIndexableKeys(packageName, getNonIndexablesKeysFromRemoteProvider(packageName, authority));
    }

    private List<String> getNonIndexablesKeysFromRemoteProvider(String packageName, String authority) {
        try {
            return getNonIndexablesKeys(this.mContext.createPackageContext(packageName, 0), buildUriForNonIndexableKeys(authority), SearchIndexablesContract.NON_INDEXABLES_KEYS_COLUMNS);
        } catch (NameNotFoundException e) {
            Log.w("Index", "Could not create context for " + packageName + ": " + Log.getStackTraceString(e));
            return EMPTY_LIST;
        }
    }

    private List<String> getNonIndexablesKeys(Context packageContext, Uri uri, String[] projection) {
        Cursor cursor = packageContext.getContentResolver().query(uri, projection, null, null, null);
        if (cursor == null) {
            Log.w("Index", "Cannot add index data for Uri: " + uri.toString());
            return EMPTY_LIST;
        }
        List<String> result = new ArrayList();
        try {
            if (cursor.getCount() > 0) {
                while (cursor.moveToNext()) {
                    result.add(cursor.getString(0));
                }
            }
            cursor.close();
            return result;
        } catch (Throwable th) {
            cursor.close();
        }
    }

    public void addIndexableData(SearchIndexableData data) {
        synchronized (this.mDataToProcess) {
            this.mDataToProcess.dataToUpdate.add(data);
        }
    }

    public void deleteIndexableData(SearchIndexableData data) {
        synchronized (this.mDataToProcess) {
            this.mDataToProcess.dataToDelete.add(data);
        }
    }

    public void addNonIndexableKeys(String authority, List<String> keys) {
        synchronized (this.mDataToProcess) {
            this.mDataToProcess.nonIndexableKeys.put(authority, keys);
        }
    }

    private boolean isWellKnownProvider(ResolveInfo info) {
        String authority = info.providerInfo.authority;
        String packageName = info.providerInfo.applicationInfo.packageName;
        if (TextUtils.isEmpty(authority) || TextUtils.isEmpty(packageName)) {
            return false;
        }
        String readPermission = info.providerInfo.readPermission;
        String writePermission = info.providerInfo.writePermission;
        if (TextUtils.isEmpty(readPermission) || TextUtils.isEmpty(writePermission) || !"android.permission.READ_SEARCH_INDEXABLES".equals(readPermission) || !"android.permission.READ_SEARCH_INDEXABLES".equals(writePermission)) {
            return false;
        }
        return isPrivilegedPackage(packageName);
    }

    private boolean isPrivilegedPackage(String packageName) {
        try {
            if ((this.mContext.getPackageManager().getPackageInfo(packageName, 0).applicationInfo.flags & 1073741824) != 0) {
                return true;
            }
            return false;
        } catch (NameNotFoundException e) {
            return false;
        }
    }

    public void updateFromClassNameResource(String className, boolean rebuild, boolean includeInSearchResults) {
        if (className == null) {
            throw new IllegalArgumentException("class name cannot be null!");
        }
        SearchIndexableResource res = SearchIndexableResources.getResourceByName(className);
        if (res == null) {
            Log.e("Index", "Cannot find SearchIndexableResources for class name: " + className);
            return;
        }
        res.context = this.mContext;
        res.enabled = includeInSearchResults;
        if (rebuild) {
            deleteIndexableData(res);
        }
        addIndexableData(res);
        this.mDataToProcess.forceUpdate = true;
        updateInternal();
        res.enabled = false;
    }

    public void updateFromSearchIndexableData(SearchIndexableData data) {
        addIndexableData(data);
        this.mDataToProcess.forceUpdate = true;
        updateInternal();
    }

    private SQLiteDatabase getReadableDatabase() {
        return IndexDatabaseHelper.getInstance(this.mContext).getReadableDatabase();
    }

    private SQLiteDatabase getWritableDatabase() {
        return IndexDatabaseHelper.getInstance(this.mContext).getWritableDatabase();
    }

    private static Uri buildUriForXmlResources(String authority) {
        return Uri.parse("content://" + authority + "/" + "settings/indexables_xml_res");
    }

    private static Uri buildUriForRawData(String authority) {
        return Uri.parse("content://" + authority + "/" + "settings/indexables_raw");
    }

    private static Uri buildUriForNonIndexableKeys(String authority) {
        return Uri.parse("content://" + authority + "/" + "settings/non_indexables_key");
    }

    private void updateInternal() {
        synchronized (this.mDataToProcess) {
            UpdateIndexTask task = new UpdateIndexTask();
            UpdateData copy = this.mDataToProcess.copy();
            task.execute(new UpdateData[]{copy});
            this.mDataToProcess.clear();
        }
    }

    private void addIndexablesForXmlResourceUri(Context packageContext, String packageName, Uri uri, String[] projection, int baseRank) {
        Cursor cursor = packageContext.getContentResolver().query(uri, projection, null, null, null);
        if (cursor == null) {
            Log.w("Index", "Cannot add index data for Uri: " + uri.toString());
            return;
        }
        try {
            if (cursor.getCount() > 0) {
                while (cursor.moveToNext()) {
                    int rank;
                    int providerRank = cursor.getInt(0);
                    if (providerRank > 0) {
                        rank = baseRank + providerRank;
                    } else {
                        rank = baseRank;
                    }
                    int xmlResId = cursor.getInt(1);
                    String className = cursor.getString(2);
                    int iconResId = cursor.getInt(3);
                    String action = cursor.getString(4);
                    String targetPackage = cursor.getString(5);
                    String targetClass = cursor.getString(6);
                    SearchIndexableResource sir = new SearchIndexableResource(packageContext);
                    sir.rank = rank;
                    sir.xmlResId = xmlResId;
                    sir.className = className;
                    sir.packageName = packageName;
                    sir.iconResId = iconResId;
                    sir.intentAction = action;
                    sir.intentTargetPackage = targetPackage;
                    sir.intentTargetClass = targetClass;
                    addIndexableData(sir);
                }
            }
            cursor.close();
        } catch (Throwable th) {
            cursor.close();
        }
    }

    private void addIndexablesForRawDataUri(Context packageContext, String packageName, Uri uri, String[] projection, int baseRank) {
        Cursor cursor = packageContext.getContentResolver().query(uri, projection, null, null, null);
        if (cursor == null) {
            Log.w("Index", "Cannot add index data for Uri: " + uri.toString());
            return;
        }
        try {
            if (cursor.getCount() > 0) {
                while (cursor.moveToNext()) {
                    int rank;
                    int providerRank = cursor.getInt(0);
                    if (providerRank > 0) {
                        rank = baseRank + providerRank;
                    } else {
                        rank = baseRank;
                    }
                    String title = cursor.getString(1);
                    String summaryOn = cursor.getString(2);
                    String summaryOff = cursor.getString(3);
                    String entries = cursor.getString(4);
                    String keywords = cursor.getString(5);
                    String screenTitle = cursor.getString(6);
                    String className = cursor.getString(7);
                    int iconResId = cursor.getInt(8);
                    String action = cursor.getString(9);
                    String targetPackage = cursor.getString(10);
                    String targetClass = cursor.getString(11);
                    String key = cursor.getString(12);
                    int userId = cursor.getInt(13);
                    SearchIndexableRaw data = new SearchIndexableRaw(packageContext);
                    data.rank = rank;
                    data.title = title;
                    data.summaryOn = summaryOn;
                    data.summaryOff = summaryOff;
                    data.entries = entries;
                    data.keywords = keywords;
                    data.screenTitle = screenTitle;
                    data.className = className;
                    data.packageName = packageName;
                    data.iconResId = iconResId;
                    data.intentAction = action;
                    data.intentTargetPackage = targetPackage;
                    data.intentTargetClass = targetClass;
                    data.key = key;
                    data.userId = userId;
                    addIndexableData(data);
                }
            }
            cursor.close();
        } catch (Throwable th) {
            cursor.close();
        }
    }

    private String buildSearchSQL(String query, String[] colums, boolean withOrderBy) {
        StringBuilder sb = new StringBuilder();
        sb.append(buildSearchSQLForColumn(query, colums));
        if (withOrderBy) {
            sb.append(" ORDER BY ");
            sb.append("data_rank");
        }
        return sb.toString();
    }

    private String buildSearchSQLForColumn(String query, String[] columnNames) {
        StringBuilder sb = new StringBuilder();
        sb.append("SELECT ");
        for (int n = 0; n < SELECT_COLUMNS.length; n++) {
            sb.append(SELECT_COLUMNS[n]);
            if (n < SELECT_COLUMNS.length - 1) {
                sb.append(", ");
            }
        }
        sb.append(" FROM ");
        sb.append("prefs_index");
        sb.append(" WHERE ");
        sb.append(buildSearchWhereStringForColumns(query, columnNames));
        return sb.toString();
    }

    private String buildSearchWhereStringForColumns(String query, String[] columnNames) {
        StringBuilder sb = new StringBuilder("prefs_index");
        sb.append(" MATCH ");
        DatabaseUtils.appendEscapedSQLString(sb, buildSearchMatchStringForColumns(query, columnNames));
        sb.append(" AND ");
        sb.append("locale");
        sb.append(" = ");
        DatabaseUtils.appendEscapedSQLString(sb, Locale.getDefault().toString());
        sb.append(" AND ");
        sb.append("enabled");
        sb.append(" = 1");
        return sb.toString();
    }

    private String buildSearchMatchStringForColumns(String query, String[] columnNames) {
        String value = query + "*";
        StringBuilder sb = new StringBuilder();
        int count = columnNames.length;
        for (int n = 0; n < count; n++) {
            sb.append(columnNames[n]);
            sb.append(":");
            sb.append(value);
            if (n < count - 1) {
                sb.append(" OR ");
            }
        }
        return sb.toString();
    }

    private void indexOneSearchIndexableData(SQLiteDatabase database, String localeStr, SearchIndexableData data, Map<String, List<String>> nonIndexableKeys) {
        if (data instanceof SearchIndexableResource) {
            indexOneResource(database, localeStr, (SearchIndexableResource) data, nonIndexableKeys);
        } else if (data instanceof SearchIndexableRaw) {
            indexOneRaw(database, localeStr, (SearchIndexableRaw) data);
        }
    }

    private void indexOneRaw(SQLiteDatabase database, String localeStr, SearchIndexableRaw raw) {
        if (raw.locale.toString().equalsIgnoreCase(localeStr)) {
            updateOneRowWithFilteredData(database, localeStr, raw.title, raw.summaryOn, raw.summaryOff, raw.entries, raw.className, raw.screenTitle, raw.iconResId, raw.rank, raw.keywords, raw.intentAction, raw.intentTargetPackage, raw.intentTargetClass, raw.enabled, raw.key, raw.userId);
        }
    }

    private static boolean isIndexableClass(Class<?> clazz) {
        return clazz != null && Indexable.class.isAssignableFrom(clazz);
    }

    private static Class<?> getIndexableClass(String className) {
        try {
            Class<?> clazz = Class.forName(className);
            if (isIndexableClass(clazz)) {
                return clazz;
            }
            return null;
        } catch (ClassNotFoundException e) {
            Log.d("Index", "Cannot find class: " + className);
            return null;
        }
    }

    private void indexOneResource(SQLiteDatabase database, String localeStr, SearchIndexableResource sir, Map<String, List<String>> nonIndexableKeysFromResource) {
        if (sir == null) {
            Log.e("Index", "Cannot index a null resource!");
            return;
        }
        List<String> nonIndexableKeys = new ArrayList();
        if (sir.xmlResId > SearchIndexableResources.NO_DATA_RES_ID) {
            List<String> resNonIndxableKeys = (List) nonIndexableKeysFromResource.get(sir.packageName);
            if (resNonIndxableKeys != null && resNonIndxableKeys.size() > 0) {
                nonIndexableKeys.addAll(resNonIndxableKeys);
            }
            indexFromResource(sir.context, database, localeStr, sir.xmlResId, sir.className, sir.iconResId, sir.rank, sir.intentAction, sir.intentTargetPackage, sir.intentTargetClass, nonIndexableKeys);
        } else if (TextUtils.isEmpty(sir.className)) {
            Log.w("Index", "Cannot index an empty Search Provider name!");
        } else {
            Class<?> clazz = getIndexableClass(sir.className);
            if (clazz == null) {
                Log.d("Index", "SearchIndexableResource '" + sir.className + "' should implement the " + Indexable.class.getName() + " interface!");
                return;
            }
            SearchIndexProvider provider = getSearchIndexProvider(clazz);
            if (provider != null) {
                List<String> providerNonIndexableKeys = provider.getNonIndexableKeys(sir.context);
                if (providerNonIndexableKeys != null && providerNonIndexableKeys.size() > 0) {
                    nonIndexableKeys.addAll(providerNonIndexableKeys);
                }
                indexFromProvider(this.mContext, database, localeStr, provider, sir.className, sir.iconResId, sir.rank, sir.enabled, nonIndexableKeys);
            }
        }
    }

    private SearchIndexProvider getSearchIndexProvider(Class<?> clazz) {
        try {
            return (SearchIndexProvider) clazz.getField("SEARCH_INDEX_DATA_PROVIDER").get(null);
        } catch (NoSuchFieldException e) {
            Log.d("Index", "Cannot find field 'SEARCH_INDEX_DATA_PROVIDER'");
        } catch (SecurityException e2) {
            Log.d("Index", "Security exception for field 'SEARCH_INDEX_DATA_PROVIDER'");
        } catch (IllegalAccessException e3) {
            Log.d("Index", "Illegal access to field 'SEARCH_INDEX_DATA_PROVIDER'");
        } catch (IllegalArgumentException e4) {
            Log.d("Index", "Illegal argument when accessing field 'SEARCH_INDEX_DATA_PROVIDER'");
        }
        return null;
    }

    private void indexFromResource(Context context, SQLiteDatabase database, String localeStr, int xmlResId, String fragmentName, int iconResId, int rank, String intentAction, String intentTargetPackage, String intentTargetClass, List<String> nonIndexableKeys) {
        XmlResourceParser parser = null;
        try {
            int type;
            parser = context.getResources().getXml(xmlResId);
            do {
                type = parser.next();
                if (type == 1) {
                    break;
                }
            } while (type != 2);
            String nodeName = parser.getName();
            if ("PreferenceScreen".equals(nodeName)) {
                int outerDepth = parser.getDepth();
                AttributeSet attrs = Xml.asAttributeSet(parser);
                String screenTitle = getDataTitle(context, attrs);
                String key = getDataKey(context, attrs);
                if (!nonIndexableKeys.contains(key)) {
                    updateOneRowWithFilteredData(database, localeStr, getDataTitle(context, attrs), getDataSummary(context, attrs), null, null, fragmentName, screenTitle, iconResId, rank, getDataKeywords(context, attrs), intentAction, intentTargetPackage, intentTargetClass, true, key, -1);
                }
                while (true) {
                    type = parser.next();
                    if (type == 1 || (type == 3 && parser.getDepth() <= outerDepth)) {
                        if (parser != null) {
                            parser.close();
                            return;
                        }
                        return;
                    } else if (!(type == 3 || type == 4)) {
                        nodeName = parser.getName();
                        key = getDataKey(context, attrs);
                        if (nonIndexableKeys.contains(key)) {
                            continue;
                        } else {
                            String title = getDataTitle(context, attrs);
                            String keywords = getDataKeywords(context, attrs);
                            if (nodeName.equals("CheckBoxPreference")) {
                                String summaryOn = getDataSummaryOn(context, attrs);
                                String summaryOff = getDataSummaryOff(context, attrs);
                                if (TextUtils.isEmpty(summaryOn) && TextUtils.isEmpty(summaryOff)) {
                                    summaryOn = getDataSummary(context, attrs);
                                }
                                updateOneRowWithFilteredData(database, localeStr, title, summaryOn, summaryOff, null, fragmentName, screenTitle, iconResId, rank, keywords, intentAction, intentTargetPackage, intentTargetClass, true, key, -1);
                            } else {
                                String summary = getDataSummary(context, attrs);
                                String entries = null;
                                if (nodeName.endsWith("ListPreference")) {
                                    entries = getDataEntries(context, attrs);
                                }
                                updateOneRowWithFilteredData(database, localeStr, title, summary, null, entries, fragmentName, screenTitle, iconResId, rank, keywords, intentAction, intentTargetPackage, intentTargetClass, true, key, -1);
                            }
                        }
                    }
                }
                if (parser != null) {
                    parser.close();
                    return;
                }
                return;
            }
            throw new RuntimeException("XML document must start with <PreferenceScreen> tag; found" + nodeName + " at " + parser.getPositionDescription());
        } catch (Throwable e) {
            throw new RuntimeException("Error parsing PreferenceScreen", e);
        } catch (Throwable e2) {
            throw new RuntimeException("Error parsing PreferenceScreen", e2);
        } catch (Throwable th) {
            if (parser != null) {
                parser.close();
            }
        }
    }

    private void indexFromProvider(Context context, SQLiteDatabase database, String localeStr, SearchIndexProvider provider, String className, int iconResId, int rank, boolean enabled, List<String> nonIndexableKeys) {
        if (provider == null) {
            Log.w("Index", "Cannot find provider: " + className);
            return;
        }
        int i;
        List<SearchIndexableRaw> rawList = provider.getRawDataToIndex(context, enabled);
        if (rawList != null) {
            int rawSize = rawList.size();
            for (i = 0; i < rawSize; i++) {
                SearchIndexableRaw raw = (SearchIndexableRaw) rawList.get(i);
                if (raw.locale.toString().equalsIgnoreCase(localeStr)) {
                    if (!nonIndexableKeys.contains(raw.key)) {
                        updateOneRowWithFilteredData(database, localeStr, raw.title, raw.summaryOn, raw.summaryOff, raw.entries, className, raw.screenTitle, iconResId, rank, raw.keywords, raw.intentAction, raw.intentTargetPackage, raw.intentTargetClass, raw.enabled, raw.key, raw.userId);
                    }
                }
            }
        }
        List<SearchIndexableResource> resList = provider.getXmlResourcesToIndex(context, enabled);
        if (resList != null) {
            int resSize = resList.size();
            for (i = 0; i < resSize; i++) {
                SearchIndexableResource item = (SearchIndexableResource) resList.get(i);
                if (item.locale.toString().equalsIgnoreCase(localeStr)) {
                    indexFromResource(context, database, localeStr, item.xmlResId, TextUtils.isEmpty(item.className) ? className : item.className, item.iconResId == 0 ? iconResId : item.iconResId, item.rank == 0 ? rank : item.rank, item.intentAction, item.intentTargetPackage, item.intentTargetClass, nonIndexableKeys);
                }
            }
        }
    }

    private void updateOneRowWithFilteredData(SQLiteDatabase database, String locale, String title, String summaryOn, String summaryOff, String entries, String className, String screenTitle, int iconResId, int rank, String keywords, String intentAction, String intentTargetPackage, String intentTargetClass, boolean enabled, String key, int userId) {
        String updatedTitle = normalizeHyphen(title);
        String updatedSummaryOn = normalizeHyphen(summaryOn);
        String updatedSummaryOff = normalizeHyphen(summaryOff);
        updateOneRow(database, locale, updatedTitle, normalizeString(updatedTitle), updatedSummaryOn, normalizeString(updatedSummaryOn), updatedSummaryOff, normalizeString(updatedSummaryOff), entries, className, screenTitle, iconResId, rank, keywords, intentAction, intentTargetPackage, intentTargetClass, enabled, key, userId);
    }

    private static String normalizeHyphen(String input) {
        return input != null ? input.replaceAll("‑", "-") : "";
    }

    private static String normalizeString(String input) {
        return REMOVE_DIACRITICALS_PATTERN.matcher(Normalizer.normalize(input != null ? input.replaceAll("-", "") : "", Form.NFD)).replaceAll("").toLowerCase();
    }

    private void updateOneRow(SQLiteDatabase database, String locale, String updatedTitle, String normalizedTitle, String updatedSummaryOn, String normalizedSummaryOn, String updatedSummaryOff, String normalizedSummaryOff, String entries, String className, String screenTitle, int iconResId, int rank, String keywords, String intentAction, String intentTargetPackage, String intentTargetClass, boolean enabled, String key, int userId) {
        if (!TextUtils.isEmpty(updatedTitle)) {
            StringBuilder sb = new StringBuilder(updatedTitle);
            sb.append(screenTitle);
            int docId = sb.toString().hashCode();
            ContentValues values = new ContentValues();
            values.put("docid", Integer.valueOf(docId));
            values.put("locale", locale);
            values.put("data_rank", Integer.valueOf(rank));
            values.put("data_title", updatedTitle);
            values.put("data_title_normalized", normalizedTitle);
            values.put("data_summary_on", updatedSummaryOn);
            values.put("data_summary_on_normalized", normalizedSummaryOn);
            values.put("data_summary_off", updatedSummaryOff);
            values.put("data_summary_off_normalized", normalizedSummaryOff);
            values.put("data_entries", entries);
            values.put("data_keywords", keywords);
            values.put("class_name", className);
            values.put("screen_title", screenTitle);
            values.put("intent_action", intentAction);
            values.put("intent_target_package", intentTargetPackage);
            values.put("intent_target_class", intentTargetClass);
            values.put("icon", Integer.valueOf(iconResId));
            values.put("enabled", Boolean.valueOf(enabled));
            values.put("data_key_reference", key);
            values.put("user_id", Integer.valueOf(userId));
            database.replaceOrThrow("prefs_index", null, values);
        }
    }

    private String getDataKey(Context context, AttributeSet attrs) {
        return getData(context, attrs, R.styleable.Preference, 6);
    }

    private String getDataTitle(Context context, AttributeSet attrs) {
        return getData(context, attrs, R.styleable.Preference, 4);
    }

    private String getDataSummary(Context context, AttributeSet attrs) {
        return getData(context, attrs, R.styleable.Preference, 7);
    }

    private String getDataSummaryOn(Context context, AttributeSet attrs) {
        return getData(context, attrs, R.styleable.CheckBoxPreference, 0);
    }

    private String getDataSummaryOff(Context context, AttributeSet attrs) {
        return getData(context, attrs, R.styleable.CheckBoxPreference, 1);
    }

    private String getDataEntries(Context context, AttributeSet attrs) {
        return getDataEntries(context, attrs, R.styleable.ListPreference, 0);
    }

    private String getDataKeywords(Context context, AttributeSet attrs) {
        return getData(context, attrs, com.android.settings.R.styleable.Preference, 0);
    }

    private String getData(Context context, AttributeSet set, int[] attrs, int resId) {
        TypedValue tv = context.obtainStyledAttributes(set, attrs).peekValue(resId);
        CharSequence data = null;
        if (tv != null && tv.type == 3) {
            data = tv.resourceId != 0 ? context.getText(tv.resourceId) : tv.string;
        }
        return data != null ? data.toString() : null;
    }

    private String getDataEntries(Context context, AttributeSet set, int[] attrs, int resId) {
        TypedValue tv = context.obtainStyledAttributes(set, attrs).peekValue(resId);
        String[] data = null;
        if (!(tv == null || tv.type != 1 || tv.resourceId == 0)) {
            data = context.getResources().getStringArray(tv.resourceId);
        }
        int count = data == null ? 0 : data.length;
        if (count == 0) {
            return null;
        }
        StringBuilder result = new StringBuilder();
        for (int n = 0; n < count; n++) {
            result.append(data[n]);
            result.append("|");
        }
        return result.toString();
    }
}
