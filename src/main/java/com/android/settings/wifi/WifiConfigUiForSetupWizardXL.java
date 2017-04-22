package com.android.settings.wifi;

import android.content.Context;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnFocusChangeListener;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;

public class WifiConfigUiForSetupWizardXL implements OnFocusChangeListener, WifiConfigUiBase {
    private final WifiSettingsForSetupWizardXL mActivity;
    private Button mCancelButton;
    private Button mConnectButton;
    private WifiConfigController mController;
    private Handler mHandler;
    private LayoutInflater mInflater;
    private final InputMethodManager mInputMethodManager;
    private View mView;

    private class FocusRunnable implements Runnable {
        final View mViewToBeFocused;

        public FocusRunnable(View viewToBeFocused) {
            this.mViewToBeFocused = viewToBeFocused;
        }

        public void run() {
            if (WifiConfigUiForSetupWizardXL.this.mInputMethodManager.showSoftInput(this.mViewToBeFocused, 0)) {
                WifiConfigUiForSetupWizardXL.this.mActivity.setPaddingVisibility(8);
            } else {
                Log.w("SetupWizard", "Failed to show software keyboard ");
            }
        }
    }

    public void requestFocusAndShowKeyboard(int editViewId) {
        View viewToBeFocused = this.mView.findViewById(editViewId);
        if (viewToBeFocused == null) {
            Log.w("SetupWizard", "password field to be focused not found.");
        } else if (!(viewToBeFocused instanceof EditText)) {
            Log.w("SetupWizard", "password field is not EditText");
        } else if (viewToBeFocused.isFocused()) {
            Log.i("SetupWizard", "Already focused");
            if (!this.mInputMethodManager.showSoftInput(viewToBeFocused, 0)) {
                Log.w("SetupWizard", "Failed to show SoftInput");
            }
        } else {
            viewToBeFocused.setOnFocusChangeListener(this);
            boolean requestFocusResult = viewToBeFocused.requestFocus();
            String str = "SetupWizard";
            String str2 = "Focus request: %s";
            Object[] objArr = new Object[1];
            objArr[0] = requestFocusResult ? "successful" : "failed";
            Log.i(str, String.format(str2, objArr));
            if (!requestFocusResult) {
                viewToBeFocused.setOnFocusChangeListener(null);
            }
        }
    }

    public WifiConfigController getController() {
        return this.mController;
    }

    public LayoutInflater getLayoutInflater() {
        return this.mInflater;
    }

    public Button getSubmitButton() {
        return this.mConnectButton;
    }

    public void setSubmitButton(CharSequence text) {
        this.mConnectButton.setVisibility(0);
        this.mConnectButton.setText(text);
    }

    public void setForgetButton(CharSequence text) {
    }

    public void setCancelButton(CharSequence text) {
        this.mCancelButton.setVisibility(0);
    }

    public Context getContext() {
        return this.mActivity;
    }

    public void setTitle(int id) {
        Log.d("SetupWizard", "Ignoring setTitle");
    }

    public void setTitle(CharSequence title) {
        Log.d("SetupWizard", "Ignoring setTitle");
    }

    public void onFocusChange(View view, boolean hasFocus) {
        view.setOnFocusChangeListener(null);
        if (hasFocus) {
            this.mHandler.post(new FocusRunnable(view));
        }
    }
}
