package com.android.settings.bluetooth;

import android.content.Context;
import android.util.AttributeSet;
import com.android.settings.ProgressCategory;
import com.android.settings.R;

public class BluetoothProgressCategory extends ProgressCategory {
    public BluetoothProgressCategory(Context context) {
        this(context, null);
    }

    public BluetoothProgressCategory(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public BluetoothProgressCategory(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public BluetoothProgressCategory(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        setEmptyTextRes(R.string.bluetooth_no_devices_found);
    }
}
