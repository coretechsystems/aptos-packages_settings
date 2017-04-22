package com.android.settings.notification;

import android.content.Context;
import android.net.Uri;
import android.preference.PreferenceManager.OnActivityStopListener;
import android.preference.SeekBarPreference;
import android.preference.SeekBarVolumizer;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.SeekBar;
import com.android.settings.R;

public class VolumeSeekBarPreference extends SeekBarPreference implements OnActivityStopListener {
    private Callback mCallback;
    private ImageView mIconView;
    private SeekBar mSeekBar;
    private int mStream;
    private SeekBarVolumizer mVolumizer;

    public interface Callback {
        void onSampleStarting(SeekBarVolumizer seekBarVolumizer);

        void onStreamValueChanged(int i, int i2);
    }

    public VolumeSeekBarPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public VolumeSeekBarPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public VolumeSeekBarPreference(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public VolumeSeekBarPreference(Context context) {
        this(context, null);
    }

    public void setStream(int stream) {
        this.mStream = stream;
    }

    public void setCallback(Callback callback) {
        this.mCallback = callback;
    }

    public void onActivityStop() {
        if (this.mVolumizer != null) {
            this.mVolumizer.stop();
        }
    }

    protected void onBindView(View view) {
        super.onBindView(view);
        if (this.mStream == 0) {
            Log.w("VolumeSeekBarPreference", "No stream found, not binding volumizer");
            return;
        }
        getPreferenceManager().registerOnActivityStopListener(this);
        SeekBar seekBar = (SeekBar) view.findViewById(16909146);
        if (seekBar != this.mSeekBar) {
            this.mSeekBar = seekBar;
            android.preference.SeekBarVolumizer.Callback sbvc = new android.preference.SeekBarVolumizer.Callback() {
                public void onSampleStarting(SeekBarVolumizer sbv) {
                    if (VolumeSeekBarPreference.this.mCallback != null) {
                        VolumeSeekBarPreference.this.mCallback.onSampleStarting(sbv);
                    }
                }
            };
            Uri sampleUri = this.mStream == 3 ? getMediaVolumeUri() : null;
            if (this.mVolumizer == null) {
                this.mVolumizer = new SeekBarVolumizer(getContext(), this.mStream, sampleUri, sbvc) {
                    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromTouch) {
                        super.onProgressChanged(seekBar, progress, fromTouch);
                        VolumeSeekBarPreference.this.mCallback.onStreamValueChanged(VolumeSeekBarPreference.this.mStream, progress);
                    }
                };
            }
            this.mVolumizer.setSeekBar(this.mSeekBar);
            this.mIconView = (ImageView) view.findViewById(16908294);
            this.mCallback.onStreamValueChanged(this.mStream, this.mSeekBar.getProgress());
        }
    }

    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromTouch) {
        super.onProgressChanged(seekBar, progress, fromTouch);
        this.mCallback.onStreamValueChanged(this.mStream, progress);
    }

    public void showIcon(int resId) {
        if (this.mIconView != null) {
            this.mIconView.setImageResource(resId);
        }
    }

    private Uri getMediaVolumeUri() {
        return Uri.parse("android.resource://" + getContext().getPackageName() + "/" + R.raw.media_volume);
    }
}
