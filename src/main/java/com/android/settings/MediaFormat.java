package com.android.settings;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.storage.StorageVolume;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import com.android.internal.os.storage.ExternalStorageFormatter;

public class MediaFormat extends Activity {
    private Button mFinalButton;
    private OnClickListener mFinalClickListener = new OnClickListener() {
        public void onClick(View v) {
            if (!Utils.isMonkeyRunning()) {
                Intent intent = new Intent("com.android.internal.os.storage.FORMAT_ONLY");
                intent.setComponent(ExternalStorageFormatter.COMPONENT_NAME);
                intent.putExtra("storage_volume", (StorageVolume) MediaFormat.this.getIntent().getParcelableExtra("storage_volume"));
                MediaFormat.this.startService(intent);
                MediaFormat.this.finish();
            }
        }
    };
    private View mFinalView;
    private LayoutInflater mInflater;
    private View mInitialView;
    private Button mInitiateButton;
    private OnClickListener mInitiateListener = new OnClickListener() {
        public void onClick(View v) {
            if (!MediaFormat.this.runKeyguardConfirmation(55)) {
                MediaFormat.this.establishFinalConfirmationState();
            }
        }
    };

    private boolean runKeyguardConfirmation(int request) {
        return new ChooseLockSettingsHelper(this).launchConfirmationActivity(request, getText(R.string.media_format_gesture_prompt), getText(R.string.media_format_gesture_explanation));
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 55) {
            if (resultCode == -1) {
                establishFinalConfirmationState();
            } else if (resultCode == 0) {
                finish();
            } else {
                establishInitialState();
            }
        }
    }

    private void establishFinalConfirmationState() {
        if (this.mFinalView == null) {
            this.mFinalView = this.mInflater.inflate(R.layout.media_format_final, null);
            this.mFinalButton = (Button) this.mFinalView.findViewById(R.id.execute_media_format);
            this.mFinalButton.setOnClickListener(this.mFinalClickListener);
        }
        setContentView(this.mFinalView);
    }

    private void establishInitialState() {
        if (this.mInitialView == null) {
            this.mInitialView = this.mInflater.inflate(R.layout.media_format_primary, null);
            this.mInitiateButton = (Button) this.mInitialView.findViewById(R.id.initiate_media_format);
            this.mInitiateButton.setOnClickListener(this.mInitiateListener);
        }
        setContentView(this.mInitialView);
    }

    protected void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        this.mInitialView = null;
        this.mFinalView = null;
        this.mInflater = LayoutInflater.from(this);
        establishInitialState();
    }

    public void onPause() {
        super.onPause();
        if (!isFinishing()) {
            establishInitialState();
        }
    }
}
