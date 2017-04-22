package com.android.settings;

import android.app.Dialog;
import android.content.Context;
import android.preference.EditTextPreference;
import android.util.AttributeSet;
import android.view.View;
import android.widget.EditText;

class EditPinPreference extends EditTextPreference {
    private OnPinEnteredListener mPinListener;

    interface OnPinEnteredListener {
        void onPinEntered(EditPinPreference editPinPreference, boolean z);
    }

    public EditPinPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public EditPinPreference(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public void setOnPinEnteredListener(OnPinEnteredListener listener) {
        this.mPinListener = listener;
    }

    protected void onBindDialogView(View view) {
        super.onBindDialogView(view);
        EditText editText = getEditText();
        if (editText != null) {
            editText.setInputType(18);
        }
    }

    public boolean isDialogOpen() {
        Dialog dialog = getDialog();
        return dialog != null && dialog.isShowing();
    }

    protected void onDialogClosed(boolean positiveResult) {
        super.onDialogClosed(positiveResult);
        if (this.mPinListener != null) {
            this.mPinListener.onPinEntered(this, positiveResult);
        }
    }

    public void showPinDialog() {
        Dialog dialog = getDialog();
        if (dialog == null || !dialog.isShowing()) {
            showDialog(null);
        }
    }
}
