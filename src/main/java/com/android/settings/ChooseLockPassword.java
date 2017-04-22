package com.android.settings;

import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import android.content.Intent;
import android.inputmethodservice.KeyboardView;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.Editable;
import android.text.Selection;
import android.text.Spannable;
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
import com.android.settings.notification.RedactionInterstitial;

public class ChooseLockPassword extends SettingsActivity {

    public static class ChooseLockPasswordFragment extends Fragment implements TextWatcher, OnClickListener, OnEditorActionListener {
        private Button mCancelButton;
        private ChooseLockSettingsHelper mChooseLockSettingsHelper;
        private boolean mDone = false;
        private String mFirstPin;
        private Handler mHandler = new Handler() {
            public void handleMessage(Message msg) {
                if (msg.what == 1) {
                    ChooseLockPasswordFragment.this.updateStage((Stage) msg.obj);
                }
            }
        };
        private TextView mHeaderText;
        private boolean mIsAlphaMode;
        private PasswordEntryKeyboardHelper mKeyboardHelper;
        private KeyboardView mKeyboardView;
        private LockPatternUtils mLockPatternUtils;
        private Button mNextButton;
        private TextView mPasswordEntry;
        private int mPasswordMaxLength = 16;
        private int mPasswordMinLength = 4;
        private int mPasswordMinLetters = 0;
        private int mPasswordMinLowerCase = 0;
        private int mPasswordMinNonLetter = 0;
        private int mPasswordMinNumeric = 0;
        private int mPasswordMinSymbols = 0;
        private int mPasswordMinUpperCase = 0;
        private int mRequestedQuality = 131072;
        private Stage mUiStage = Stage.Introduction;

        protected enum Stage {
            Introduction(R.string.lockpassword_choose_your_password_header, R.string.lockpassword_choose_your_pin_header, R.string.lockpassword_continue_label),
            NeedToConfirm(R.string.lockpassword_confirm_your_password_header, R.string.lockpassword_confirm_your_pin_header, R.string.lockpassword_ok_label),
            ConfirmWrong(R.string.lockpassword_confirm_passwords_dont_match, R.string.lockpassword_confirm_pins_dont_match, R.string.lockpassword_continue_label);
            
            public final int alphaHint;
            public final int buttonText;
            public final int numericHint;

            private Stage(int hintInAlpha, int hintInNumeric, int nextButtonText) {
                this.alphaHint = hintInAlpha;
                this.numericHint = hintInNumeric;
                this.buttonText = nextButtonText;
            }
        }

        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            this.mLockPatternUtils = new LockPatternUtils(getActivity());
            Intent intent = getActivity().getIntent();
            if (getActivity() instanceof ChooseLockPassword) {
                this.mRequestedQuality = Math.max(intent.getIntExtra("lockscreen.password_type", this.mRequestedQuality), this.mLockPatternUtils.getRequestedPasswordQuality());
                this.mPasswordMinLength = Math.max(intent.getIntExtra("lockscreen.password_min", this.mPasswordMinLength), this.mLockPatternUtils.getRequestedMinimumPasswordLength());
                this.mPasswordMaxLength = intent.getIntExtra("lockscreen.password_max", this.mPasswordMaxLength);
                this.mPasswordMinLetters = Math.max(intent.getIntExtra("lockscreen.password_min_letters", this.mPasswordMinLetters), this.mLockPatternUtils.getRequestedPasswordMinimumLetters());
                this.mPasswordMinUpperCase = Math.max(intent.getIntExtra("lockscreen.password_min_uppercase", this.mPasswordMinUpperCase), this.mLockPatternUtils.getRequestedPasswordMinimumUpperCase());
                this.mPasswordMinLowerCase = Math.max(intent.getIntExtra("lockscreen.password_min_lowercase", this.mPasswordMinLowerCase), this.mLockPatternUtils.getRequestedPasswordMinimumLowerCase());
                this.mPasswordMinNumeric = Math.max(intent.getIntExtra("lockscreen.password_min_numeric", this.mPasswordMinNumeric), this.mLockPatternUtils.getRequestedPasswordMinimumNumeric());
                this.mPasswordMinSymbols = Math.max(intent.getIntExtra("lockscreen.password_min_symbols", this.mPasswordMinSymbols), this.mLockPatternUtils.getRequestedPasswordMinimumSymbols());
                this.mPasswordMinNonLetter = Math.max(intent.getIntExtra("lockscreen.password_min_nonletter", this.mPasswordMinNonLetter), this.mLockPatternUtils.getRequestedPasswordMinimumNonLetter());
                this.mChooseLockSettingsHelper = new ChooseLockSettingsHelper(getActivity());
                return;
            }
            throw new SecurityException("Fragment contained in wrong activity");
        }

        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            View view = inflater.inflate(R.layout.choose_lock_password, null);
            this.mCancelButton = (Button) view.findViewById(R.id.cancel_button);
            this.mCancelButton.setOnClickListener(this);
            this.mNextButton = (Button) view.findViewById(R.id.next_button);
            this.mNextButton.setOnClickListener(this);
            boolean z = 262144 == this.mRequestedQuality || 327680 == this.mRequestedQuality || 393216 == this.mRequestedQuality;
            this.mIsAlphaMode = z;
            this.mKeyboardView = (PasswordEntryKeyboardView) view.findViewById(R.id.keyboard);
            this.mPasswordEntry = (TextView) view.findViewById(R.id.password_entry);
            this.mPasswordEntry.setOnEditorActionListener(this);
            this.mPasswordEntry.addTextChangedListener(this);
            Activity activity = getActivity();
            this.mKeyboardHelper = new PasswordEntryKeyboardHelper(activity, this.mKeyboardView, this.mPasswordEntry);
            this.mKeyboardHelper.setKeyboardMode(this.mIsAlphaMode ? 0 : 1);
            this.mHeaderText = (TextView) view.findViewById(R.id.headerText);
            this.mKeyboardView.requestFocus();
            int currentType = this.mPasswordEntry.getInputType();
            TextView textView = this.mPasswordEntry;
            if (!this.mIsAlphaMode) {
                currentType = 18;
            }
            textView.setInputType(currentType);
            boolean confirmCredentials = getActivity().getIntent().getBooleanExtra("confirm_credentials", true);
            if (savedInstanceState == null) {
                updateStage(Stage.Introduction);
                if (confirmCredentials) {
                    this.mChooseLockSettingsHelper.launchConfirmationActivity(58, null, null);
                }
            } else {
                this.mFirstPin = savedInstanceState.getString("first_pin");
                String state = savedInstanceState.getString("ui_stage");
                if (state != null) {
                    this.mUiStage = Stage.valueOf(state);
                    updateStage(this.mUiStage);
                }
            }
            this.mDone = false;
            if (activity instanceof SettingsActivity) {
                ((SettingsActivity) activity).setTitle(getText(this.mIsAlphaMode ? R.string.lockpassword_choose_your_password_header : R.string.lockpassword_choose_your_pin_header));
            }
            return view;
        }

        public void onResume() {
            super.onResume();
            updateStage(this.mUiStage);
            this.mKeyboardView.requestFocus();
        }

        public void onPause() {
            this.mHandler.removeMessages(1);
            super.onPause();
        }

        public void onSaveInstanceState(Bundle outState) {
            super.onSaveInstanceState(outState);
            outState.putString("ui_stage", this.mUiStage.name());
            outState.putString("first_pin", this.mFirstPin);
        }

        public void onActivityResult(int requestCode, int resultCode, Intent data) {
            super.onActivityResult(requestCode, resultCode, data);
            switch (requestCode) {
                case 58:
                    if (resultCode != -1) {
                        getActivity().setResult(1);
                        getActivity().finish();
                        return;
                    }
                    return;
                default:
                    return;
            }
        }

        protected void updateStage(Stage stage) {
            Stage previousStage = this.mUiStage;
            this.mUiStage = stage;
            updateUi();
            if (previousStage != stage) {
                this.mHeaderText.announceForAccessibility(this.mHeaderText.getText());
            }
        }

        private String validatePassword(String password) {
            if (password.length() < this.mPasswordMinLength) {
                int i;
                if (this.mIsAlphaMode) {
                    i = R.string.lockpassword_password_too_short;
                } else {
                    i = R.string.lockpassword_pin_too_short;
                }
                return getString(i, new Object[]{Integer.valueOf(this.mPasswordMinLength)});
            } else if (password.length() > this.mPasswordMaxLength) {
                return getString(this.mIsAlphaMode ? R.string.lockpassword_password_too_long : R.string.lockpassword_pin_too_long, new Object[]{Integer.valueOf(this.mPasswordMaxLength + 1)});
            } else {
                int letters = 0;
                int numbers = 0;
                int lowercase = 0;
                int symbols = 0;
                int uppercase = 0;
                int nonletter = 0;
                for (int i2 = 0; i2 < password.length(); i2++) {
                    char c = password.charAt(i2);
                    if (c < ' ' || c > '') {
                        return getString(R.string.lockpassword_illegal_character);
                    }
                    if (c >= '0' && c <= '9') {
                        numbers++;
                        nonletter++;
                    } else if (c >= 'A' && c <= 'Z') {
                        letters++;
                        uppercase++;
                    } else if (c < 'a' || c > 'z') {
                        symbols++;
                        nonletter++;
                    } else {
                        letters++;
                        lowercase++;
                    }
                }
                if (131072 == this.mRequestedQuality || 196608 == this.mRequestedQuality) {
                    if (letters > 0 || symbols > 0) {
                        return getString(R.string.lockpassword_pin_contains_non_digits);
                    }
                    int sequence = LockPatternUtils.maxLengthSequence(password);
                    if (196608 == this.mRequestedQuality && sequence > 3) {
                        return getString(R.string.lockpassword_pin_no_sequential_digits);
                    }
                } else if (393216 != this.mRequestedQuality) {
                    boolean alphabetic = 262144 == this.mRequestedQuality;
                    boolean alphanumeric = 327680 == this.mRequestedQuality;
                    if ((alphabetic || alphanumeric) && letters == 0) {
                        return getString(R.string.lockpassword_password_requires_alpha);
                    }
                    if (alphanumeric && numbers == 0) {
                        return getString(R.string.lockpassword_password_requires_digit);
                    }
                } else if (letters < this.mPasswordMinLetters) {
                    return String.format(getResources().getQuantityString(R.plurals.lockpassword_password_requires_letters, this.mPasswordMinLetters), new Object[]{Integer.valueOf(this.mPasswordMinLetters)});
                } else if (numbers < this.mPasswordMinNumeric) {
                    return String.format(getResources().getQuantityString(R.plurals.lockpassword_password_requires_numeric, this.mPasswordMinNumeric), new Object[]{Integer.valueOf(this.mPasswordMinNumeric)});
                } else if (lowercase < this.mPasswordMinLowerCase) {
                    return String.format(getResources().getQuantityString(R.plurals.lockpassword_password_requires_lowercase, this.mPasswordMinLowerCase), new Object[]{Integer.valueOf(this.mPasswordMinLowerCase)});
                } else if (uppercase < this.mPasswordMinUpperCase) {
                    return String.format(getResources().getQuantityString(R.plurals.lockpassword_password_requires_uppercase, this.mPasswordMinUpperCase), new Object[]{Integer.valueOf(this.mPasswordMinUpperCase)});
                } else if (symbols < this.mPasswordMinSymbols) {
                    return String.format(getResources().getQuantityString(R.plurals.lockpassword_password_requires_symbols, this.mPasswordMinSymbols), new Object[]{Integer.valueOf(this.mPasswordMinSymbols)});
                } else if (nonletter < this.mPasswordMinNonLetter) {
                    return String.format(getResources().getQuantityString(R.plurals.lockpassword_password_requires_nonletter, this.mPasswordMinNonLetter), new Object[]{Integer.valueOf(this.mPasswordMinNonLetter)});
                }
                if (!this.mLockPatternUtils.checkPasswordHistory(password)) {
                    return null;
                }
                return getString(this.mIsAlphaMode ? R.string.lockpassword_password_recently_used : R.string.lockpassword_pin_recently_used);
            }
        }

        private void handleNext() {
            if (!this.mDone) {
                String pin = this.mPasswordEntry.getText().toString();
                if (!TextUtils.isEmpty(pin)) {
                    String errorMsg = null;
                    if (this.mUiStage == Stage.Introduction) {
                        errorMsg = validatePassword(pin);
                        if (errorMsg == null) {
                            this.mFirstPin = pin;
                            this.mPasswordEntry.setText("");
                            updateStage(Stage.NeedToConfirm);
                        }
                    } else if (this.mUiStage == Stage.NeedToConfirm) {
                        if (this.mFirstPin.equals(pin)) {
                            boolean isFallback = getActivity().getIntent().getBooleanExtra("lockscreen.biometric_weak_fallback", false);
                            this.mLockPatternUtils.clearLock(isFallback);
                            this.mLockPatternUtils.setCredentialRequiredToDecrypt(getActivity().getIntent().getBooleanExtra("extra_require_password", true));
                            this.mLockPatternUtils.saveLockPassword(pin, this.mRequestedQuality, isFallback);
                            getActivity().setResult(1);
                            getActivity().finish();
                            this.mDone = true;
                            startActivity(RedactionInterstitial.createStartIntent(getActivity()));
                        } else {
                            CharSequence tmp = this.mPasswordEntry.getText();
                            if (tmp != null) {
                                Selection.setSelection((Spannable) tmp, 0, tmp.length());
                            }
                            updateStage(Stage.ConfirmWrong);
                        }
                    }
                    if (errorMsg != null) {
                        showError(errorMsg, this.mUiStage);
                    }
                }
            }
        }

        public void onClick(View v) {
            switch (v.getId()) {
                case R.id.cancel_button:
                    getActivity().finish();
                    return;
                case R.id.next_button:
                    handleNext();
                    return;
                default:
                    return;
            }
        }

        private void showError(String msg, Stage next) {
            this.mHeaderText.setText(msg);
            this.mHeaderText.announceForAccessibility(this.mHeaderText.getText());
            Message mesg = this.mHandler.obtainMessage(1, next);
            this.mHandler.removeMessages(1);
            this.mHandler.sendMessageDelayed(mesg, 3000);
        }

        public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
            if (actionId != 0 && actionId != 6 && actionId != 5) {
                return false;
            }
            handleNext();
            return true;
        }

        private void updateUi() {
            String password = this.mPasswordEntry.getText().toString();
            int length = password.length();
            if (this.mUiStage != Stage.Introduction || length <= 0) {
                boolean z;
                this.mHeaderText.setText(this.mIsAlphaMode ? this.mUiStage.alphaHint : this.mUiStage.numericHint);
                Button button = this.mNextButton;
                if (length > 0) {
                    z = true;
                } else {
                    z = false;
                }
                button.setEnabled(z);
            } else if (length < this.mPasswordMinLength) {
                this.mHeaderText.setText(getString(this.mIsAlphaMode ? R.string.lockpassword_password_too_short : R.string.lockpassword_pin_too_short, new Object[]{Integer.valueOf(this.mPasswordMinLength)}));
                this.mNextButton.setEnabled(false);
            } else {
                String error = validatePassword(password);
                if (error != null) {
                    this.mHeaderText.setText(error);
                    this.mNextButton.setEnabled(false);
                } else {
                    this.mHeaderText.setText(R.string.lockpassword_press_continue);
                    this.mNextButton.setEnabled(true);
                }
            }
            this.mNextButton.setText(this.mUiStage.buttonText);
        }

        public void afterTextChanged(Editable s) {
            if (this.mUiStage == Stage.ConfirmWrong) {
                this.mUiStage = Stage.NeedToConfirm;
            }
            updateUi();
        }

        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }
    }

    public Intent getIntent() {
        Intent modIntent = new Intent(super.getIntent());
        modIntent.putExtra(":settings:show_fragment", ChooseLockPasswordFragment.class.getName());
        return modIntent;
    }

    public static Intent createIntent(Context context, int quality, boolean isFallback, int minLength, int maxLength, boolean requirePasswordToDecrypt, boolean confirmCredentials) {
        Intent intent = new Intent().setClass(context, ChooseLockPassword.class);
        intent.putExtra("lockscreen.password_type", quality);
        intent.putExtra("lockscreen.password_min", minLength);
        intent.putExtra("lockscreen.password_max", maxLength);
        intent.putExtra("confirm_credentials", confirmCredentials);
        intent.putExtra("lockscreen.biometric_weak_fallback", isFallback);
        intent.putExtra("extra_require_password", requirePasswordToDecrypt);
        return intent;
    }

    protected boolean isValidFragment(String fragmentName) {
        if (ChooseLockPasswordFragment.class.getName().equals(fragmentName)) {
            return true;
        }
        return false;
    }

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(getText(R.string.lockpassword_choose_your_password_header));
    }
}
