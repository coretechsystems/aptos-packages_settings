package com.android.settings;

import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.hardware.input.InputManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.Parcelable.Creator;
import android.preference.Preference.BaseSavedState;
import android.preference.SeekBarDialogPreference;
import android.provider.Settings.System;
import android.util.AttributeSet;
import android.view.View;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;

public class PointerSpeedPreference extends SeekBarDialogPreference implements OnSeekBarChangeListener {
    private final InputManager mIm = ((InputManager) getContext().getSystemService("input"));
    private int mOldSpeed;
    private boolean mRestoredOldState;
    private SeekBar mSeekBar;
    private ContentObserver mSpeedObserver = new ContentObserver(new Handler()) {
        public void onChange(boolean selfChange) {
            PointerSpeedPreference.this.onSpeedChanged();
        }
    };
    private boolean mTouchInProgress;

    private static class SavedState extends BaseSavedState {
        public static final Creator<SavedState> CREATOR = new Creator<SavedState>() {
            public SavedState createFromParcel(Parcel in) {
                return new SavedState(in);
            }

            public SavedState[] newArray(int size) {
                return new SavedState[size];
            }
        };
        int oldSpeed;
        int progress;

        public SavedState(Parcel source) {
            super(source);
            this.progress = source.readInt();
            this.oldSpeed = source.readInt();
        }

        public void writeToParcel(Parcel dest, int flags) {
            super.writeToParcel(dest, flags);
            dest.writeInt(this.progress);
            dest.writeInt(this.oldSpeed);
        }

        public SavedState(Parcelable superState) {
            super(superState);
        }
    }

    public PointerSpeedPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    protected void showDialog(Bundle state) {
        super.showDialog(state);
        getContext().getContentResolver().registerContentObserver(System.getUriFor("pointer_speed"), true, this.mSpeedObserver);
        this.mRestoredOldState = false;
    }

    protected void onBindDialogView(View view) {
        super.onBindDialogView(view);
        this.mSeekBar = getSeekBar(view);
        this.mSeekBar.setMax(14);
        this.mOldSpeed = this.mIm.getPointerSpeed(getContext());
        this.mSeekBar.setProgress(this.mOldSpeed + 7);
        this.mSeekBar.setOnSeekBarChangeListener(this);
    }

    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromTouch) {
        if (!this.mTouchInProgress) {
            this.mIm.tryPointerSpeed(progress - 7);
        }
    }

    public void onStartTrackingTouch(SeekBar seekBar) {
        this.mTouchInProgress = true;
    }

    public void onStopTrackingTouch(SeekBar seekBar) {
        this.mTouchInProgress = false;
        this.mIm.tryPointerSpeed(seekBar.getProgress() - 7);
    }

    private void onSpeedChanged() {
        this.mSeekBar.setProgress(this.mIm.getPointerSpeed(getContext()) + 7);
    }

    protected void onDialogClosed(boolean positiveResult) {
        super.onDialogClosed(positiveResult);
        ContentResolver resolver = getContext().getContentResolver();
        if (positiveResult) {
            this.mIm.setPointerSpeed(getContext(), this.mSeekBar.getProgress() - 7);
        } else {
            restoreOldState();
        }
        resolver.unregisterContentObserver(this.mSpeedObserver);
    }

    private void restoreOldState() {
        if (!this.mRestoredOldState) {
            this.mIm.tryPointerSpeed(this.mOldSpeed);
            this.mRestoredOldState = true;
        }
    }

    protected Parcelable onSaveInstanceState() {
        Parcelable superState = super.onSaveInstanceState();
        if (getDialog() == null || !getDialog().isShowing()) {
            return superState;
        }
        Parcelable myState = new SavedState(superState);
        myState.progress = this.mSeekBar.getProgress();
        myState.oldSpeed = this.mOldSpeed;
        restoreOldState();
        return myState;
    }

    protected void onRestoreInstanceState(Parcelable state) {
        if (state == null || !state.getClass().equals(SavedState.class)) {
            super.onRestoreInstanceState(state);
            return;
        }
        SavedState myState = (SavedState) state;
        super.onRestoreInstanceState(myState.getSuperState());
        this.mOldSpeed = myState.oldSpeed;
        this.mSeekBar.setProgress(myState.progress);
        this.mIm.tryPointerSpeed(myState.progress - 7);
    }
}
