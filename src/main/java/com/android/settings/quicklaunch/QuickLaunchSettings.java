package com.android.settings.quicklaunch;

import android.app.AlertDialog.Builder;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.ContentObserver;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Handler;
import android.preference.Preference;
import android.preference.PreferenceGroup;
import android.preference.PreferenceScreen;
import android.provider.Settings.Bookmarks;
import android.util.Log;
import android.util.SparseArray;
import android.util.SparseBooleanArray;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemLongClickListener;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import java.net.URISyntaxException;

public class QuickLaunchSettings extends SettingsPreferenceFragment implements OnClickListener, OnItemLongClickListener {
    private static final String[] sProjection = new String[]{"shortcut", "title", "intent"};
    private SparseBooleanArray mBookmarkedShortcuts;
    private Cursor mBookmarksCursor;
    private BookmarksObserver mBookmarksObserver;
    private CharSequence mClearDialogBookmarkTitle;
    private char mClearDialogShortcut;
    private PreferenceGroup mShortcutGroup;
    private SparseArray<ShortcutPreference> mShortcutToPreference;
    private Handler mUiHandler = new Handler();

    private class BookmarksObserver extends ContentObserver {
        public BookmarksObserver(Handler handler) {
            super(handler);
        }

        public void onChange(boolean selfChange) {
            super.onChange(selfChange);
            QuickLaunchSettings.this.refreshShortcuts();
        }
    }

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.quick_launch_settings);
        this.mShortcutGroup = (PreferenceGroup) findPreference("shortcut_category");
        this.mShortcutToPreference = new SparseArray();
        this.mBookmarksObserver = new BookmarksObserver(this.mUiHandler);
        initShortcutPreferences();
        this.mBookmarksCursor = getActivity().getContentResolver().query(Bookmarks.CONTENT_URI, sProjection, null, null, null);
    }

    public void onResume() {
        super.onResume();
        this.mBookmarksCursor = getActivity().getContentResolver().query(Bookmarks.CONTENT_URI, sProjection, null, null, null);
        getContentResolver().registerContentObserver(Bookmarks.CONTENT_URI, true, this.mBookmarksObserver);
        refreshShortcuts();
    }

    public void onPause() {
        super.onPause();
        getContentResolver().unregisterContentObserver(this.mBookmarksObserver);
    }

    public void onStop() {
        super.onStop();
        this.mBookmarksCursor.close();
    }

    public void onActivityCreated(Bundle state) {
        super.onActivityCreated(state);
        getListView().setOnItemLongClickListener(this);
        if (state != null) {
            this.mClearDialogBookmarkTitle = state.getString("CLEAR_DIALOG_BOOKMARK_TITLE");
            this.mClearDialogShortcut = (char) state.getInt("CLEAR_DIALOG_SHORTCUT", 0);
        }
    }

    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putCharSequence("CLEAR_DIALOG_BOOKMARK_TITLE", this.mClearDialogBookmarkTitle);
        outState.putInt("CLEAR_DIALOG_SHORTCUT", this.mClearDialogShortcut);
    }

    public Dialog onCreateDialog(int id) {
        switch (id) {
            case 0:
                return new Builder(getActivity()).setTitle(getString(R.string.quick_launch_clear_dialog_title)).setMessage(getString(R.string.quick_launch_clear_dialog_message, new Object[]{Character.valueOf(this.mClearDialogShortcut), this.mClearDialogBookmarkTitle})).setPositiveButton(R.string.quick_launch_clear_ok_button, this).setNegativeButton(R.string.quick_launch_clear_cancel_button, this).create();
            default:
                return super.onCreateDialog(id);
        }
    }

    private void showClearDialog(ShortcutPreference pref) {
        if (pref.hasBookmark()) {
            this.mClearDialogBookmarkTitle = pref.getTitle();
            this.mClearDialogShortcut = pref.getShortcut();
            showDialog(0);
        }
    }

    public void onClick(DialogInterface dialog, int which) {
        if (this.mClearDialogShortcut > '\u0000' && which == -1) {
            clearShortcut(this.mClearDialogShortcut);
        }
        this.mClearDialogBookmarkTitle = null;
        this.mClearDialogShortcut = '\u0000';
    }

    private void clearShortcut(char shortcut) {
        getContentResolver().delete(Bookmarks.CONTENT_URI, "shortcut=?", new String[]{String.valueOf(shortcut)});
    }

    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        if (!(preference instanceof ShortcutPreference)) {
            return false;
        }
        ShortcutPreference pref = (ShortcutPreference) preference;
        Intent intent = new Intent(getActivity(), BookmarkPicker.class);
        intent.putExtra("com.android.settings.quicklaunch.SHORTCUT", pref.getShortcut());
        startActivityForResult(intent, 1);
        return true;
    }

    public boolean onItemLongClick(AdapterView<?> adapterView, View view, int position, long id) {
        Preference pref = (Preference) getPreferenceScreen().getRootAdapter().getItem(position);
        if (!(pref instanceof ShortcutPreference)) {
            return false;
        }
        showClearDialog((ShortcutPreference) pref);
        return true;
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == -1) {
            if (requestCode != 1) {
                super.onActivityResult(requestCode, resultCode, data);
            } else if (data == null) {
                Log.w("QuickLaunchSettings", "Result from bookmark picker does not have an intent.");
            } else {
                updateShortcut(data.getCharExtra("com.android.settings.quicklaunch.SHORTCUT", '\u0000'), data);
            }
        }
    }

    private void updateShortcut(char shortcut, Intent intent) {
        Bookmarks.add(getContentResolver(), intent, "", "@quicklaunch", shortcut, 0);
    }

    private ShortcutPreference getOrCreatePreference(char shortcut) {
        ShortcutPreference pref = (ShortcutPreference) this.mShortcutToPreference.get(shortcut);
        if (pref != null) {
            return pref;
        }
        Log.w("QuickLaunchSettings", "Unknown shortcut '" + shortcut + "', creating preference anyway");
        return createPreference(shortcut);
    }

    private ShortcutPreference createPreference(char shortcut) {
        ShortcutPreference pref = new ShortcutPreference(getActivity(), shortcut);
        this.mShortcutGroup.addPreference(pref);
        this.mShortcutToPreference.put(shortcut, pref);
        return pref;
    }

    private void initShortcutPreferences() {
        SparseBooleanArray shortcutSeen = new SparseBooleanArray();
        KeyCharacterMap keyMap = KeyCharacterMap.load(-1);
        for (int keyCode = KeyEvent.getMaxKeyCode() - 1; keyCode >= 0; keyCode--) {
            char shortcut = Character.toLowerCase(keyMap.getDisplayLabel(keyCode));
            if (!(shortcut == '\u0000' || shortcutSeen.get(shortcut, false) || !Character.isLetterOrDigit(shortcut))) {
                shortcutSeen.put(shortcut, true);
                createPreference(shortcut);
            }
        }
    }

    private synchronized void refreshShortcuts() {
        Cursor c = this.mBookmarksCursor;
        if (c != null) {
            if (c.requery()) {
                ShortcutPreference pref;
                SparseBooleanArray noLongerBookmarkedShortcuts = this.mBookmarkedShortcuts;
                SparseBooleanArray newBookmarkedShortcuts = new SparseBooleanArray();
                while (c.moveToNext()) {
                    char shortcut = Character.toLowerCase((char) c.getInt(0));
                    if (shortcut != '\u0000') {
                        pref = getOrCreatePreference(shortcut);
                        CharSequence title = Bookmarks.getTitle(getActivity(), c);
                        String intentUri = c.getString(c.getColumnIndex("intent"));
                        PackageManager packageManager = getPackageManager();
                        try {
                            ResolveInfo info = packageManager.resolveActivity(Intent.parseUri(intentUri, 0), 0);
                            if (info != null) {
                                title = info.loadLabel(packageManager);
                            }
                        } catch (URISyntaxException e) {
                        }
                        pref.setTitle(title);
                        pref.setSummary(getString(R.string.quick_launch_shortcut, new Object[]{String.valueOf(shortcut)}));
                        pref.setHasBookmark(true);
                        newBookmarkedShortcuts.put(shortcut, true);
                        if (noLongerBookmarkedShortcuts != null) {
                            noLongerBookmarkedShortcuts.put(shortcut, false);
                        }
                    }
                }
                if (noLongerBookmarkedShortcuts != null) {
                    for (int i = noLongerBookmarkedShortcuts.size() - 1; i >= 0; i--) {
                        if (noLongerBookmarkedShortcuts.valueAt(i)) {
                            pref = (ShortcutPreference) this.mShortcutToPreference.get((char) noLongerBookmarkedShortcuts.keyAt(i));
                            if (pref != null) {
                                pref.setHasBookmark(false);
                            }
                        }
                    }
                }
                this.mBookmarkedShortcuts = newBookmarkedShortcuts;
                c.deactivate();
            } else {
                Log.e("QuickLaunchSettings", "Could not requery cursor when refreshing shortcuts.");
            }
        }
    }
}
