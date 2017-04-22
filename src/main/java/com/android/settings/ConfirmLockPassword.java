package com.android.settings;

import android.app.Activity;
import android.app.Fragment;
import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.SystemClock;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;
import com.android.internal.widget.LockPatternUtils;
import com.android.internal.widget.PasswordEntryKeyboardHelper;
import com.android.internal.widget.PasswordEntryKeyboardView;

public class ConfirmLockPassword extends SettingsActivity {

    public static class ConfirmLockPasswordFragment extends Fragment implements TextWatcher, OnClickListener, OnEditorActionListener {
        private Button mContinueButton;
        private CountDownTimer mCountdownTimer;
        private Handler mHandler = new Handler();
        private TextView mHeaderText;
        private boolean mIsAlpha;
        private PasswordEntryKeyboardHelper mKeyboardHelper;
        private PasswordEntryKeyboardView mKeyboardView;
        private LockPatternUtils mLockPatternUtils;
        private int mNumWrongConfirmAttempts;
        private TextView mPasswordEntry;
        private final Runnable mResetErrorRunnable = new Runnable() {
            public void run() {
                ConfirmLockPasswordFragment.this.mHeaderText.setText(ConfirmLockPasswordFragment.this.getDefaultHeader());
            }
        };

        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            this.mLockPatternUtils = new LockPatternUtils(getActivity());
            if (savedInstanceState != null) {
                this.mNumWrongConfirmAttempts = savedInstanceState.getInt("confirm_lock_password_fragment.key_num_wrong_confirm_attempts", 0);
            }
        }

        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            int storedQuality = this.mLockPatternUtils.getKeyguardStoredPasswordQuality();
            View view = inflater.inflate(R.layout.confirm_lock_password, null);
            view.findViewById(R.id.cancel_button).setOnClickListener(this);
            this.mContinueButton = (Button) view.findViewById(R.id.next_button);
            this.mContinueButton.setOnClickListener(this);
            this.mContinueButton.setEnabled(false);
            this.mPasswordEntry = (TextView) view.findViewById(R.id.password_entry);
            this.mPasswordEntry.setOnEditorActionListener(this);
            this.mPasswordEntry.addTextChangedListener(this);
            this.mKeyboardView = (PasswordEntryKeyboardView) view.findViewById(R.id.keyboard);
            this.mHeaderText = (TextView) view.findViewById(R.id.headerText);
            boolean z = 262144 == storedQuality || 327680 == storedQuality || 393216 == storedQuality;
            this.mIsAlpha = z;
            Intent intent = getActivity().getIntent();
            if (intent != null) {
                CharSequence headerMessage = intent.getCharSequenceExtra("com.android.settings.ConfirmLockPattern.header");
                if (TextUtils.isEmpty(headerMessage)) {
                    headerMessage = getString(getDefaultHeader());
                }
                this.mHeaderText.setText(headerMessage);
            }
            Activity activity = getActivity();
            this.mKeyboardHelper = new PasswordEntryKeyboardHelper(activity, this.mKeyboardView, this.mPasswordEntry);
            this.mKeyboardHelper.setKeyboardMode(this.mIsAlpha ? 0 : 1);
            this.mKeyboardView.requestFocus();
            int currentType = this.mPasswordEntry.getInputType();
            TextView textView = this.mPasswordEntry;
            if (!this.mIsAlpha) {
                currentType = 18;
            }
            textView.setInputType(currentType);
            if (activity instanceof SettingsActivity) {
                ((SettingsActivity) activity).setTitle(getText(getDefaultHeader()));
            }
            return view;
        }

        private int getDefaultHeader() {
            return this.mIsAlpha ? R.string.lockpassword_confirm_your_password_header : R.string.lockpassword_confirm_your_pin_header;
        }

        public void onPause() {
            super.onPause();
            this.mKeyboardView.requestFocus();
            if (this.mCountdownTimer != null) {
                this.mCountdownTimer.cancel();
                this.mCountdownTimer = null;
            }
        }

        public void onResume() {
            super.onResume();
            this.mKeyboardView.requestFocus();
            long deadline = this.mLockPatternUtils.getLockoutAttemptDeadline();
            if (deadline != 0) {
                handleAttemptLockout(deadline);
            }
        }

        public void onSaveInstanceState(Bundle outState) {
            super.onSaveInstanceState(outState);
            outState.putInt("confirm_lock_password_fragment.key_num_wrong_confirm_attempts", this.mNumWrongConfirmAttempts);
        }

        private void handleNext() {
            String pin = this.mPasswordEntry.getText().toString();
            if (this.mLockPatternUtils.checkPassword(pin)) {
                Intent intent = new Intent();
                if (getActivity() instanceof InternalActivity) {
                    intent.putExtra("type", this.mIsAlpha ? 0 : 3);
                    intent.putExtra("password", pin);
                }
                getActivity().setResult(-1, intent);
                getActivity().finish();
                return;
            }
            int i = this.mNumWrongConfirmAttempts + 1;
            this.mNumWrongConfirmAttempts = i;
            if (i >= 5) {
                handleAttemptLockout(this.mLockPatternUtils.setLockoutAttemptDeadline());
            } else {
                showError(R.string.lockpattern_need_to_unlock_wrong);
            }
        }

        private void handleAttemptLockout(long elapsedRealtimeDeadline) {
            long elapsedRealtime = SystemClock.elapsedRealtime();
            showError(R.string.lockpattern_too_many_failed_confirmation_attempts_header, 0);
            this.mPasswordEntry.setEnabled(false);
            this.mCountdownTimer = new CountDownTimer(elapsedRealtimeDeadline - elapsedRealtime, 1000) {
                public void onTick(long millisUntilFinished) {
                    int secondsCountdown = (int) (millisUntilFinished / 1000);
                    ConfirmLockPasswordFragment.this.mHeaderText.setText(ConfirmLockPasswordFragment.this.getString(R.string.lockpattern_too_many_failed_confirmation_attempts_footer, new Object[]{Integer.valueOf(secondsCountdown)}));
                }

                public void onFinish() {
                    ConfirmLockPasswordFragment.this.mPasswordEntry.setEnabled(true);
                    ConfirmLockPasswordFragment.this.mHeaderText.setText(ConfirmLockPasswordFragment.this.getDefaultHeader());
                    ConfirmLockPasswordFragment.this.mNumWrongConfirmAttempts = 0;
                }
            }.start();
        }

        public void onClick(View v) {
            switch (v.getId()) {
                case R.id.cancel_button:
                    getActivity().setResult(0);
                    getActivity().finish();
                    return;
                case R.id.next_button:
                    handleNext();
                    return;
                default:
                    return;
            }
        }

        private void showError(int msg) {
            showError(msg, 3000);
        }

        private void showError(int msg, long timeout) {
            this.mHeaderText.setText(msg);
            this.mHeaderText.announceForAccessibility(this.mHeaderText.getText());
            this.mPasswordEntry.setText(null);
            this.mHandler.removeCallbacks(this.mResetErrorRunnable);
            if (timeout != 0) {
                this.mHandler.postDelayed(this.mResetErrorRunnable, timeout);
            }
        }

        public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
            if (actionId != 0 && actionId != 6 && actionId != 5) {
                return false;
            }
            handleNext();
            return true;
        }

        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }

        public void afterTextChanged(Editable s) {
            this.mContinueButton.setEnabled(this.mPasswordEntry.getText().length() > 0);
        }
    }

    public static class InternalActivity extends ConfirmLockPassword {
    }

    public Intent getIntent() {
        Intent modIntent = new Intent(super.getIntent());
        modIntent.putExtra(":settings:show_fragment", ConfirmLockPasswordFragment.class.getName());
        return modIntent;
    }

    protected boolean isValidFragment(String fragmentName) {
        if (ConfirmLockPasswordFragment.class.getName().equals(fragmentName)) {
            return true;
        }
        return false;
    }

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(getText(R.string.lockpassword_confirm_your_password_header));
    }
}
