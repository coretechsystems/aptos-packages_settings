package com.android.settings.bluetooth;

import android.app.Activity;
import android.os.Bundle;
import com.android.settings.R;

public final class DevicePickerActivity extends Activity {
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.bluetooth_device_picker);
    }
}
