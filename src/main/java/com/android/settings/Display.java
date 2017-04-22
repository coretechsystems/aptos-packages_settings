package com.android.settings;

import android.R;
import android.app.Activity;
import android.app.ActivityManagerNative;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.os.Bundle;
import android.os.RemoteException;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;

public class Display extends Activity implements OnClickListener {
    private Configuration mCurConfig = new Configuration();
    private DisplayMetrics mDisplayMetrics;
    private Spinner mFontSize;
    private OnItemSelectedListener mFontSizeChanged = new OnItemSelectedListener() {
        public void onItemSelected(AdapterView av, View v, int position, long id) {
            if (position == 0) {
                Display.this.mCurConfig.fontScale = 0.75f;
            } else if (position == 2) {
                Display.this.mCurConfig.fontScale = 1.25f;
            } else {
                Display.this.mCurConfig.fontScale = 1.0f;
            }
            Display.this.updateFontScale();
        }

        public void onNothingSelected(AdapterView av) {
        }
    };
    private TextView mPreview;
    private TypedValue mTextSizeTyped;

    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        setContentView(R.layout.display);
        this.mFontSize = (Spinner) findViewById(R.id.fontSize);
        this.mFontSize.setOnItemSelectedListener(this.mFontSizeChanged);
        states = new String[3];
        Resources r = getResources();
        states[0] = r.getString(R.string.small_font);
        states[1] = r.getString(R.string.medium_font);
        states[2] = r.getString(R.string.large_font);
        ArrayAdapter<String> adapter = new ArrayAdapter(this, 17367048, states);
        adapter.setDropDownViewResource(17367049);
        this.mFontSize.setAdapter(adapter);
        this.mPreview = (TextView) findViewById(R.id.preview);
        this.mPreview.setText(r.getText(R.string.font_size_preview_text));
        Button save = (Button) findViewById(R.id.save);
        save.setText(r.getText(R.string.font_size_save));
        save.setOnClickListener(this);
        this.mTextSizeTyped = new TypedValue();
        TypedArray styledAttributes = obtainStyledAttributes(R.styleable.TextView);
        styledAttributes.getValue(2, this.mTextSizeTyped);
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        this.mDisplayMetrics = new DisplayMetrics();
        this.mDisplayMetrics.density = metrics.density;
        this.mDisplayMetrics.heightPixels = metrics.heightPixels;
        this.mDisplayMetrics.scaledDensity = metrics.scaledDensity;
        this.mDisplayMetrics.widthPixels = metrics.widthPixels;
        this.mDisplayMetrics.xdpi = metrics.xdpi;
        this.mDisplayMetrics.ydpi = metrics.ydpi;
        styledAttributes.recycle();
    }

    public void onResume() {
        super.onResume();
        try {
            this.mCurConfig.updateFrom(ActivityManagerNative.getDefault().getConfiguration());
        } catch (RemoteException e) {
        }
        if (this.mCurConfig.fontScale < 1.0f) {
            this.mFontSize.setSelection(0);
        } else if (this.mCurConfig.fontScale > 1.0f) {
            this.mFontSize.setSelection(2);
        } else {
            this.mFontSize.setSelection(1);
        }
        updateFontScale();
    }

    private void updateFontScale() {
        this.mDisplayMetrics.scaledDensity = this.mDisplayMetrics.density * this.mCurConfig.fontScale;
        this.mPreview.setTextSize(0, this.mTextSizeTyped.getDimension(this.mDisplayMetrics));
    }

    public void onClick(View v) {
        try {
            ActivityManagerNative.getDefault().updatePersistentConfiguration(this.mCurConfig);
        } catch (RemoteException e) {
        }
        finish();
    }
}
