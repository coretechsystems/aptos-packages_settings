package com.android.settings.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.Parcelable.Creator;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.BaseSavedState;
import android.view.View.OnClickListener;
import android.view.ViewGroup.MarginLayoutParams;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import com.android.settings.R;
import java.util.ArrayList;

public class SwitchBar extends LinearLayout implements OnClickListener, OnCheckedChangeListener {
    private static int[] MARGIN_ATTRIBUTES = new int[]{R.attr.switchBarMarginStart, R.attr.switchBarMarginEnd};
    private ToggleSwitch mSwitch;
    private ArrayList<OnSwitchChangeListener> mSwitchChangeListeners;
    private TextView mTextView;

    public interface OnSwitchChangeListener {
        void onSwitchChanged(Switch switchR, boolean z);
    }

    static class SavedState extends BaseSavedState {
        public static final Creator<SavedState> CREATOR = new Creator<SavedState>() {
            public SavedState createFromParcel(Parcel in) {
                return new SavedState(in);
            }

            public SavedState[] newArray(int size) {
                return new SavedState[size];
            }
        };
        boolean checked;
        boolean visible;

        SavedState(Parcelable superState) {
            super(superState);
        }

        private SavedState(Parcel in) {
            super(in);
            this.checked = ((Boolean) in.readValue(null)).booleanValue();
            this.visible = ((Boolean) in.readValue(null)).booleanValue();
        }

        public void writeToParcel(Parcel out, int flags) {
            super.writeToParcel(out, flags);
            out.writeValue(Boolean.valueOf(this.checked));
            out.writeValue(Boolean.valueOf(this.visible));
        }

        public String toString() {
            return "SwitchBar.SavedState{" + Integer.toHexString(System.identityHashCode(this)) + " checked=" + this.checked + " visible=" + this.visible + "}";
        }
    }

    public SwitchBar(Context context) {
        this(context, null);
    }

    public SwitchBar(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SwitchBar(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public SwitchBar(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        this.mSwitchChangeListeners = new ArrayList();
        LayoutInflater.from(context).inflate(R.layout.switch_bar, this);
        TypedArray a = context.obtainStyledAttributes(attrs, MARGIN_ATTRIBUTES);
        int switchBarMarginStart = (int) a.getDimension(0, 0.0f);
        int switchBarMarginEnd = (int) a.getDimension(1, 0.0f);
        a.recycle();
        this.mTextView = (TextView) findViewById(R.id.switch_text);
        this.mTextView.setText(R.string.switch_off_text);
        ((MarginLayoutParams) this.mTextView.getLayoutParams()).setMarginStart(switchBarMarginStart);
        this.mSwitch = (ToggleSwitch) findViewById(R.id.switch_widget);
        this.mSwitch.setSaveEnabled(false);
        ((MarginLayoutParams) this.mSwitch.getLayoutParams()).setMarginEnd(switchBarMarginEnd);
        addOnSwitchChangeListener(new OnSwitchChangeListener() {
            public void onSwitchChanged(Switch switchView, boolean isChecked) {
                SwitchBar.this.setTextViewLabel(isChecked);
            }
        });
        setOnClickListener(this);
        setVisibility(8);
    }

    public void setTextViewLabel(boolean isChecked) {
        this.mTextView.setText(isChecked ? R.string.switch_on_text : R.string.switch_off_text);
    }

    public void setChecked(boolean checked) {
        setTextViewLabel(checked);
        this.mSwitch.setChecked(checked);
    }

    public void setCheckedInternal(boolean checked) {
        setTextViewLabel(checked);
        this.mSwitch.setCheckedInternal(checked);
    }

    public boolean isChecked() {
        return this.mSwitch.isChecked();
    }

    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        this.mTextView.setEnabled(enabled);
        this.mSwitch.setEnabled(enabled);
    }

    public final ToggleSwitch getSwitch() {
        return this.mSwitch;
    }

    public void show() {
        if (!isShowing()) {
            setVisibility(0);
            this.mSwitch.setOnCheckedChangeListener(this);
        }
    }

    public void hide() {
        if (isShowing()) {
            setVisibility(8);
            this.mSwitch.setOnCheckedChangeListener(null);
        }
    }

    public boolean isShowing() {
        return getVisibility() == 0;
    }

    public void onClick(View v) {
        setChecked(!this.mSwitch.isChecked());
    }

    public void propagateChecked(boolean isChecked) {
        int count = this.mSwitchChangeListeners.size();
        for (int n = 0; n < count; n++) {
            ((OnSwitchChangeListener) this.mSwitchChangeListeners.get(n)).onSwitchChanged(this.mSwitch, isChecked);
        }
    }

    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        propagateChecked(isChecked);
    }

    public void addOnSwitchChangeListener(OnSwitchChangeListener listener) {
        if (this.mSwitchChangeListeners.contains(listener)) {
            throw new IllegalStateException("Cannot add twice the same OnSwitchChangeListener");
        }
        this.mSwitchChangeListeners.add(listener);
    }

    public void removeOnSwitchChangeListener(OnSwitchChangeListener listener) {
        if (this.mSwitchChangeListeners.contains(listener)) {
            this.mSwitchChangeListeners.remove(listener);
            return;
        }
        throw new IllegalStateException("Cannot remove OnSwitchChangeListener");
    }

    public Parcelable onSaveInstanceState() {
        SavedState ss = new SavedState(super.onSaveInstanceState());
        ss.checked = this.mSwitch.isChecked();
        ss.visible = isShowing();
        return ss;
    }

    public void onRestoreInstanceState(Parcelable state) {
        SavedState ss = (SavedState) state;
        super.onRestoreInstanceState(ss.getSuperState());
        this.mSwitch.setCheckedInternal(ss.checked);
        setTextViewLabel(ss.checked);
        setVisibility(ss.visible ? 0 : 8);
        this.mSwitch.setOnCheckedChangeListener(ss.visible ? this : null);
        requestLayout();
    }
}
