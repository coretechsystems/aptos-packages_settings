package com.android.settings;

import android.preference.EditTextPreference;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;

public class SelectableEditTextPreference extends EditTextPreference {
    private int mSelectionMode;

    protected void onBindDialogView(View view) {
        int length;
        super.onBindDialogView(view);
        EditText editText = getEditText();
        if (editText.getText() != null) {
            length = editText.getText().length();
        } else {
            length = 0;
        }
        if (!TextUtils.isEmpty(editText.getText())) {
            switch (this.mSelectionMode) {
                case 0:
                    editText.setSelection(length);
                    return;
                case 1:
                    editText.setSelection(0);
                    return;
                case 2:
                    editText.setSelection(0, length);
                    return;
                default:
                    return;
            }
        }
    }
}
