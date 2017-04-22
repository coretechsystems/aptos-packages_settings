package com.android.settings.deviceinfo;

import android.content.Context;
import android.os.Environment.UserEnvironment;
import android.os.UserHandle;
import android.util.AttributeSet;
import android.view.ViewDebug.ExportedProperty;
import android.widget.CheckBox;
import android.widget.Checkable;
import android.widget.RelativeLayout;
import android.widget.TextView;
import com.android.settings.R;

public class FileItemInfoLayout extends RelativeLayout implements Checkable {
    private static final int sLengthExternalStorageDirPrefix = (new UserEnvironment(UserHandle.myUserId()).getExternalStorageDirectory().getAbsolutePath().length() + 1);
    private CheckBox mCheckbox;
    private TextView mFileNameView;
    private TextView mFileSizeView;

    public FileItemInfoLayout(Context context) {
        this(context, null);
    }

    public FileItemInfoLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public FileItemInfoLayout(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public void toggle() {
        setChecked(!this.mCheckbox.isChecked());
    }

    protected void onFinishInflate() {
        super.onFinishInflate();
        this.mFileNameView = (TextView) findViewById(R.id.misc_filename);
        this.mFileSizeView = (TextView) findViewById(R.id.misc_filesize);
        this.mCheckbox = (CheckBox) findViewById(R.id.misc_checkbox);
    }

    public void setFileName(String fileName) {
        this.mFileNameView.setText(fileName.substring(sLengthExternalStorageDirPrefix));
    }

    public void setFileSize(String filesize) {
        this.mFileSizeView.setText(filesize);
    }

    @ExportedProperty
    public boolean isChecked() {
        return this.mCheckbox.isChecked();
    }

    public CheckBox getCheckBox() {
        return this.mCheckbox;
    }

    public void setChecked(boolean checked) {
        this.mCheckbox.setChecked(checked);
    }
}
