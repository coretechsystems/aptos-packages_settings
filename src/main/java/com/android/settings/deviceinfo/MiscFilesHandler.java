package com.android.settings.deviceinfo;

import android.app.Activity;
import android.app.ListActivity;
import android.content.Context;
import android.os.Bundle;
import android.os.storage.StorageVolume;
import android.text.format.Formatter;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.view.ActionMode;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.ViewGroup;
import android.widget.AbsListView.MultiChoiceModeListener;
import android.widget.BaseAdapter;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.ListView;
import com.android.settings.R;
import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class MiscFilesHandler extends ListActivity {
    private MemoryMearurementAdapter mAdapter;
    private LayoutInflater mInflater;
    private String mNumBytesSelectedFormat;
    private String mNumSelectedFormat;

    class MemoryMearurementAdapter extends BaseAdapter {
        private Context mContext;
        private ArrayList<FileInfo> mData = null;
        private long mDataSize = 0;

        public MemoryMearurementAdapter(Activity activity) {
            this.mContext = activity;
            StorageMeasurement mMeasurement = StorageMeasurement.getInstance(activity, (StorageVolume) activity.getIntent().getParcelableExtra("storage_volume"));
            if (mMeasurement != null) {
                this.mData = (ArrayList) mMeasurement.mFileInfoForMisc;
                if (this.mData != null) {
                    Iterator i$ = this.mData.iterator();
                    while (i$.hasNext()) {
                        this.mDataSize += ((FileInfo) i$.next()).mSize;
                    }
                }
            }
        }

        public int getCount() {
            return this.mData == null ? 0 : this.mData.size();
        }

        public FileInfo getItem(int position) {
            if (this.mData == null || this.mData.size() <= position) {
                return null;
            }
            return (FileInfo) this.mData.get(position);
        }

        public long getItemId(int position) {
            if (this.mData == null || this.mData.size() <= position) {
                return 0;
            }
            return ((FileInfo) this.mData.get(position)).mId;
        }

        public void removeAll(List<Object> objs) {
            if (this.mData != null) {
                for (Object o : objs) {
                    this.mData.remove(o);
                    this.mDataSize -= ((FileInfo) o).mSize;
                }
            }
        }

        public long getDataSize() {
            return this.mDataSize;
        }

        public void notifyDataSetChanged() {
            super.notifyDataSetChanged();
        }

        public View getView(int position, View convertView, ViewGroup parent) {
            final FileItemInfoLayout view = convertView == null ? (FileItemInfoLayout) MiscFilesHandler.this.mInflater.inflate(R.layout.settings_storage_miscfiles, parent, false) : (FileItemInfoLayout) convertView;
            FileInfo item = getItem(position);
            view.setFileName(item.mFileName);
            view.setFileSize(Formatter.formatFileSize(this.mContext, item.mSize));
            final ListView listView = (ListView) parent;
            final int listPosition = position;
            view.getCheckBox().setOnCheckedChangeListener(new OnCheckedChangeListener() {
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    listView.setItemChecked(listPosition, isChecked);
                }
            });
            view.setOnLongClickListener(new OnLongClickListener() {
                public boolean onLongClick(View v) {
                    boolean z = false;
                    if (listView.getCheckedItemCount() > 0) {
                        return false;
                    }
                    ListView listView = listView;
                    int i = listPosition;
                    if (!view.isChecked()) {
                        z = true;
                    }
                    listView.setItemChecked(i, z);
                    return true;
                }
            });
            view.setOnClickListener(new OnClickListener() {
                public void onClick(View v) {
                    if (listView.getCheckedItemCount() > 0) {
                        listView.setItemChecked(listPosition, !view.isChecked());
                    }
                }
            });
            return view;
        }
    }

    private class ModeCallback implements MultiChoiceModeListener {
        private final Context mContext;
        private int mDataCount;

        public ModeCallback(Context context) {
            this.mContext = context;
            this.mDataCount = MiscFilesHandler.this.mAdapter.getCount();
        }

        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            MiscFilesHandler.this.getMenuInflater().inflate(R.menu.misc_files_menu, menu);
            return true;
        }

        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            return true;
        }

        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            ListView lv = MiscFilesHandler.this.getListView();
            int i;
            switch (item.getItemId()) {
                case R.id.action_delete:
                    SparseBooleanArray checkedItems = lv.getCheckedItemPositions();
                    int checkedCount = MiscFilesHandler.this.getListView().getCheckedItemCount();
                    if (checkedCount <= this.mDataCount) {
                        if (this.mDataCount > 0) {
                            ArrayList<Object> toRemove = new ArrayList();
                            for (i = 0; i < this.mDataCount; i++) {
                                if (checkedItems.get(i)) {
                                    if (StorageMeasurement.LOGV) {
                                        Log.i("MemorySettings", "deleting: " + MiscFilesHandler.this.mAdapter.getItem(i));
                                    }
                                    File file = new File(MiscFilesHandler.this.mAdapter.getItem(i).mFileName);
                                    if (file.isDirectory()) {
                                        deleteDir(file);
                                    } else {
                                        file.delete();
                                    }
                                    toRemove.add(MiscFilesHandler.this.mAdapter.getItem(i));
                                }
                            }
                            MiscFilesHandler.this.mAdapter.removeAll(toRemove);
                            MiscFilesHandler.this.mAdapter.notifyDataSetChanged();
                            this.mDataCount = MiscFilesHandler.this.mAdapter.getCount();
                        }
                        mode.finish();
                        break;
                    }
                    throw new IllegalStateException("checked item counts do not match. checkedCount: " + checkedCount + ", dataSize: " + this.mDataCount);
                case R.id.action_select_all:
                    for (i = 0; i < this.mDataCount; i++) {
                        lv.setItemChecked(i, true);
                    }
                    onItemCheckedStateChanged(mode, 1, 0, true);
                    break;
            }
            return true;
        }

        private boolean deleteDir(File dir) {
            String[] children = dir.list();
            if (children != null) {
                for (String file : children) {
                    if (!deleteDir(new File(dir, file))) {
                        return false;
                    }
                }
            }
            return dir.delete();
        }

        public void onDestroyActionMode(ActionMode mode) {
        }

        public void onItemCheckedStateChanged(ActionMode mode, int position, long id, boolean checked) {
            ListView lv = MiscFilesHandler.this.getListView();
            int numChecked = lv.getCheckedItemCount();
            mode.setTitle(String.format(MiscFilesHandler.this.mNumSelectedFormat, new Object[]{Integer.valueOf(numChecked), Integer.valueOf(MiscFilesHandler.this.mAdapter.getCount())}));
            SparseBooleanArray checkedItems = lv.getCheckedItemPositions();
            long selectedDataSize = 0;
            if (numChecked > 0) {
                for (int i = 0; i < this.mDataCount; i++) {
                    if (checkedItems.get(i)) {
                        selectedDataSize += MiscFilesHandler.this.mAdapter.getItem(i).mSize;
                    }
                }
            }
            mode.setSubtitle(String.format(MiscFilesHandler.this.mNumBytesSelectedFormat, new Object[]{Formatter.formatFileSize(this.mContext, selectedDataSize), Formatter.formatFileSize(this.mContext, MiscFilesHandler.this.mAdapter.getDataSize())}));
        }
    }

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setFinishOnTouchOutside(true);
        setTitle(R.string.misc_files);
        this.mNumSelectedFormat = getString(R.string.misc_files_selected_count);
        this.mNumBytesSelectedFormat = getString(R.string.misc_files_selected_count_bytes);
        this.mAdapter = new MemoryMearurementAdapter(this);
        this.mInflater = (LayoutInflater) getSystemService("layout_inflater");
        setContentView(R.layout.settings_storage_miscfiles_list);
        ListView lv = getListView();
        lv.setItemsCanFocus(true);
        lv.setChoiceMode(3);
        lv.setMultiChoiceModeListener(new ModeCallback(this));
        setListAdapter(this.mAdapter);
    }
}
