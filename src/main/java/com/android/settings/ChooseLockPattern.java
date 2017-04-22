package com.android.settings;

import android.app.Fragment;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.TextView;
import com.android.internal.widget.LinearLayoutWithDefaultTouchRecepient;
import com.android.internal.widget.LockPatternUtils;
import com.android.internal.widget.LockPatternView;
import com.android.internal.widget.LockPatternView.Cell;
import com.android.internal.widget.LockPatternView.DisplayMode;
import com.android.internal.widget.LockPatternView.OnPatternListener;
import com.android.settings.notification.RedactionInterstitial;
import com.google.android.collect.Lists;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ChooseLockPattern extends SettingsActivity {

    static /* synthetic */ class AnonymousClass1 {
        static final /* synthetic */ int[] $SwitchMap$com$android$settings$ChooseLockPattern$ChooseLockPatternFragment$Stage = new int[Stage.values().length];

        static {
            try {
                $SwitchMap$com$android$settings$ChooseLockPattern$ChooseLockPatternFragment$Stage[Stage.Introduction.ordinal()] = 1;
            } catch (NoSuchFieldError e) {
            }
            try {
                $SwitchMap$com$android$settings$ChooseLockPattern$ChooseLockPatternFragment$Stage[Stage.HelpScreen.ordinal()] = 2;
            } catch (NoSuchFieldError e2) {
            }
            try {
                $SwitchMap$com$android$settings$ChooseLockPattern$ChooseLockPatternFragment$Stage[Stage.ChoiceTooShort.ordinal()] = 3;
            } catch (NoSuchFieldError e3) {
            }
            try {
                $SwitchMap$com$android$settings$ChooseLockPattern$ChooseLockPatternFragment$Stage[Stage.FirstChoiceValid.ordinal()] = 4;
            } catch (NoSuchFieldError e4) {
            }
            try {
                $SwitchMap$com$android$settings$ChooseLockPattern$ChooseLockPatternFragment$Stage[Stage.NeedToConfirm.ordinal()] = 5;
            } catch (NoSuchFieldError e5) {
            }
            try {
                $SwitchMap$com$android$settings$ChooseLockPattern$ChooseLockPatternFragment$Stage[Stage.ConfirmWrong.ordinal()] = 6;
            } catch (NoSuchFieldError e6) {
            }
            try {
                $SwitchMap$com$android$settings$ChooseLockPattern$ChooseLockPatternFragment$Stage[Stage.ChoiceConfirmed.ordinal()] = 7;
            } catch (NoSuchFieldError e7) {
            }
        }
    }

    public static class ChooseLockPatternFragment extends Fragment implements OnClickListener {
        private final List<Cell> mAnimatePattern = Collections.unmodifiableList(Lists.newArrayList(new Cell[]{Cell.of(0, 0), Cell.of(0, 1), Cell.of(1, 1), Cell.of(2, 1)}));
        private ChooseLockSettingsHelper mChooseLockSettingsHelper;
        protected OnPatternListener mChooseNewLockPatternListener = new OnPatternListener() {
            public void onPatternStart() {
                ChooseLockPatternFragment.this.mLockPatternView.removeCallbacks(ChooseLockPatternFragment.this.mClearPatternRunnable);
                patternInProgress();
            }

            public void onPatternCleared() {
                ChooseLockPatternFragment.this.mLockPatternView.removeCallbacks(ChooseLockPatternFragment.this.mClearPatternRunnable);
            }

            public void onPatternDetected(List<Cell> pattern) {
                if (ChooseLockPatternFragment.this.mUiStage == Stage.NeedToConfirm || ChooseLockPatternFragment.this.mUiStage == Stage.ConfirmWrong) {
                    if (ChooseLockPatternFragment.this.mChosenPattern == null) {
                        throw new IllegalStateException("null chosen pattern in stage 'need to confirm");
                    } else if (ChooseLockPatternFragment.this.mChosenPattern.equals(pattern)) {
                        ChooseLockPatternFragment.this.updateStage(Stage.ChoiceConfirmed);
                    } else {
                        ChooseLockPatternFragment.this.updateStage(Stage.ConfirmWrong);
                    }
                } else if (ChooseLockPatternFragment.this.mUiStage != Stage.Introduction && ChooseLockPatternFragment.this.mUiStage != Stage.ChoiceTooShort) {
                    throw new IllegalStateException("Unexpected stage " + ChooseLockPatternFragment.this.mUiStage + " when " + "entering the pattern.");
                } else if (pattern.size() < 4) {
                    ChooseLockPatternFragment.this.updateStage(Stage.ChoiceTooShort);
                } else {
                    ChooseLockPatternFragment.this.mChosenPattern = new ArrayList(pattern);
                    ChooseLockPatternFragment.this.updateStage(Stage.FirstChoiceValid);
                }
            }

            public void onPatternCellAdded(List<Cell> list) {
            }

            private void patternInProgress() {
                ChooseLockPatternFragment.this.mHeaderText.setText(R.string.lockpattern_recording_inprogress);
                ChooseLockPatternFragment.this.mFooterText.setText("");
                ChooseLockPatternFragment.this.mFooterLeftButton.setEnabled(false);
                ChooseLockPatternFragment.this.mFooterRightButton.setEnabled(false);
            }
        };
        protected List<Cell> mChosenPattern = null;
        private Runnable mClearPatternRunnable = new Runnable() {
            public void run() {
                ChooseLockPatternFragment.this.mLockPatternView.clearPattern();
            }
        };
        private boolean mDone = false;
        private TextView mFooterLeftButton;
        private TextView mFooterRightButton;
        protected TextView mFooterText;
        protected TextView mHeaderText;
        protected LockPatternView mLockPatternView;
        private Stage mUiStage = Stage.Introduction;

        enum LeftButtonMode {
            Cancel(R.string.cancel, true),
            CancelDisabled(R.string.cancel, false),
            Retry(R.string.lockpattern_retry_button_text, true),
            RetryDisabled(R.string.lockpattern_retry_button_text, false),
            Gone(-1, false);
            
            final boolean enabled;
            final int text;

            private LeftButtonMode(int text, boolean enabled) {
                this.text = text;
                this.enabled = enabled;
            }
        }

        enum RightButtonMode {
            Continue(R.string.lockpattern_continue_button_text, true),
            ContinueDisabled(R.string.lockpattern_continue_button_text, false),
            Confirm(R.string.lockpattern_confirm_button_text, true),
            ConfirmDisabled(R.string.lockpattern_confirm_button_text, false),
            Ok(17039370, true);
            
            final boolean enabled;
            final int text;

            private RightButtonMode(int text, boolean enabled) {
                this.text = text;
                this.enabled = enabled;
            }
        }

        protected enum Stage {
            Introduction(R.string.lockpattern_recording_intro_header, LeftButtonMode.Cancel, RightButtonMode.ContinueDisabled, -1, true),
            HelpScreen(R.string.lockpattern_settings_help_how_to_record, LeftButtonMode.Gone, RightButtonMode.Ok, -1, false),
            ChoiceTooShort(R.string.lockpattern_recording_incorrect_too_short, LeftButtonMode.Retry, RightButtonMode.ContinueDisabled, -1, true),
            FirstChoiceValid(R.string.lockpattern_pattern_entered_header, LeftButtonMode.Retry, RightButtonMode.Continue, -1, false),
            NeedToConfirm(R.string.lockpattern_need_to_confirm, LeftButtonMode.Cancel, RightButtonMode.ConfirmDisabled, -1, true),
            ConfirmWrong(R.string.lockpattern_need_to_unlock_wrong, LeftButtonMode.Cancel, RightButtonMode.ConfirmDisabled, -1, true),
            ChoiceConfirmed(R.string.lockpattern_pattern_confirmed_header, LeftButtonMode.Cancel, RightButtonMode.Confirm, -1, false);
            
            final int footerMessage;
            final int headerMessage;
            final LeftButtonMode leftMode;
            final boolean patternEnabled;
            final RightButtonMode rightMode;

            private Stage(int headerMessage, LeftButtonMode leftMode, RightButtonMode rightMode, int footerMessage, boolean patternEnabled) {
                this.headerMessage = headerMessage;
                this.leftMode = leftMode;
                this.rightMode = rightMode;
                this.footerMessage = footerMessage;
                this.patternEnabled = patternEnabled;
            }
        }

        public void onActivityResult(int requestCode, int resultCode, Intent data) {
            super.onActivityResult(requestCode, resultCode, data);
            switch (requestCode) {
                case 55:
                    if (resultCode != -1) {
                        getActivity().setResult(1);
                        getActivity().finish();
                    }
                    updateStage(Stage.Introduction);
                    return;
                default:
                    return;
            }
        }

        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            this.mChooseLockSettingsHelper = new ChooseLockSettingsHelper(getActivity());
            if (!(getActivity() instanceof ChooseLockPattern)) {
                throw new SecurityException("Fragment contained in wrong activity");
            }
        }

        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            View view = inflater.inflate(R.layout.choose_lock_pattern, null);
            this.mHeaderText = (TextView) view.findViewById(R.id.headerText);
            this.mLockPatternView = (LockPatternView) view.findViewById(R.id.lockPattern);
            this.mLockPatternView.setOnPatternListener(this.mChooseNewLockPatternListener);
            this.mLockPatternView.setTactileFeedbackEnabled(this.mChooseLockSettingsHelper.utils().isTactileFeedbackEnabled());
            this.mFooterText = (TextView) view.findViewById(R.id.footerText);
            this.mFooterLeftButton = (TextView) view.findViewById(R.id.footerLeftButton);
            this.mFooterRightButton = (TextView) view.findViewById(R.id.footerRightButton);
            this.mFooterLeftButton.setOnClickListener(this);
            this.mFooterRightButton.setOnClickListener(this);
            ((LinearLayoutWithDefaultTouchRecepient) view.findViewById(R.id.topLayout)).setDefaultTouchRecepient(this.mLockPatternView);
            boolean confirmCredentials = getActivity().getIntent().getBooleanExtra("confirm_credentials", true);
            if (savedInstanceState != null) {
                String patternString = savedInstanceState.getString("chosenPattern");
                if (patternString != null) {
                    this.mChosenPattern = LockPatternUtils.stringToPattern(patternString);
                }
                updateStage(Stage.values()[savedInstanceState.getInt("uiStage")]);
            } else if (confirmCredentials) {
                updateStage(Stage.NeedToConfirm);
                if (!this.mChooseLockSettingsHelper.launchConfirmationActivity(55, null, null)) {
                    updateStage(Stage.Introduction);
                }
            } else {
                updateStage(Stage.Introduction);
            }
            this.mDone = false;
            return view;
        }

        public void onClick(View v) {
            if (v == this.mFooterLeftButton) {
                if (this.mUiStage.leftMode == LeftButtonMode.Retry) {
                    this.mChosenPattern = null;
                    this.mLockPatternView.clearPattern();
                    updateStage(Stage.Introduction);
                } else if (this.mUiStage.leftMode == LeftButtonMode.Cancel) {
                    getActivity().setResult(1);
                    getActivity().finish();
                } else {
                    throw new IllegalStateException("left footer button pressed, but stage of " + this.mUiStage + " doesn't make sense");
                }
            } else if (v != this.mFooterRightButton) {
            } else {
                if (this.mUiStage.rightMode == RightButtonMode.Continue) {
                    if (this.mUiStage != Stage.FirstChoiceValid) {
                        throw new IllegalStateException("expected ui stage " + Stage.FirstChoiceValid + " when button is " + RightButtonMode.Continue);
                    }
                    updateStage(Stage.NeedToConfirm);
                } else if (this.mUiStage.rightMode == RightButtonMode.Confirm) {
                    if (this.mUiStage != Stage.ChoiceConfirmed) {
                        throw new IllegalStateException("expected ui stage " + Stage.ChoiceConfirmed + " when button is " + RightButtonMode.Confirm);
                    }
                    saveChosenPatternAndFinish();
                } else if (this.mUiStage.rightMode != RightButtonMode.Ok) {
                } else {
                    if (this.mUiStage != Stage.HelpScreen) {
                        throw new IllegalStateException("Help screen is only mode with ok button, but stage is " + this.mUiStage);
                    }
                    this.mLockPatternView.clearPattern();
                    this.mLockPatternView.setDisplayMode(DisplayMode.Correct);
                    updateStage(Stage.Introduction);
                }
            }
        }

        public void onSaveInstanceState(Bundle outState) {
            super.onSaveInstanceState(outState);
            outState.putInt("uiStage", this.mUiStage.ordinal());
            if (this.mChosenPattern != null) {
                outState.putString("chosenPattern", LockPatternUtils.patternToString(this.mChosenPattern));
            }
        }

        protected void updateStage(Stage stage) {
            Stage previousStage = this.mUiStage;
            this.mUiStage = stage;
            if (stage == Stage.ChoiceTooShort) {
                this.mHeaderText.setText(getResources().getString(stage.headerMessage, new Object[]{Integer.valueOf(4)}));
            } else {
                this.mHeaderText.setText(stage.headerMessage);
            }
            if (stage.footerMessage == -1) {
                this.mFooterText.setText("");
            } else {
                this.mFooterText.setText(stage.footerMessage);
            }
            if (stage.leftMode == LeftButtonMode.Gone) {
                this.mFooterLeftButton.setVisibility(8);
            } else {
                this.mFooterLeftButton.setVisibility(0);
                this.mFooterLeftButton.setText(stage.leftMode.text);
                this.mFooterLeftButton.setEnabled(stage.leftMode.enabled);
            }
            this.mFooterRightButton.setText(stage.rightMode.text);
            this.mFooterRightButton.setEnabled(stage.rightMode.enabled);
            if (stage.patternEnabled) {
                this.mLockPatternView.enableInput();
            } else {
                this.mLockPatternView.disableInput();
            }
            this.mLockPatternView.setDisplayMode(DisplayMode.Correct);
            switch (AnonymousClass1.$SwitchMap$com$android$settings$ChooseLockPattern$ChooseLockPatternFragment$Stage[this.mUiStage.ordinal()]) {
                case 1:
                    this.mLockPatternView.clearPattern();
                    break;
                case 2:
                    this.mLockPatternView.setPattern(DisplayMode.Animate, this.mAnimatePattern);
                    break;
                case 3:
                    this.mLockPatternView.setDisplayMode(DisplayMode.Wrong);
                    postClearPatternRunnable();
                    break;
                case 5:
                    this.mLockPatternView.clearPattern();
                    break;
                case 6:
                    this.mLockPatternView.setDisplayMode(DisplayMode.Wrong);
                    postClearPatternRunnable();
                    break;
            }
            if (previousStage != stage) {
                this.mHeaderText.announceForAccessibility(this.mHeaderText.getText());
            }
        }

        private void postClearPatternRunnable() {
            this.mLockPatternView.removeCallbacks(this.mClearPatternRunnable);
            this.mLockPatternView.postDelayed(this.mClearPatternRunnable, 2000);
        }

        private void saveChosenPatternAndFinish() {
            if (!this.mDone) {
                boolean lockVirgin;
                LockPatternUtils utils = this.mChooseLockSettingsHelper.utils();
                if (utils.isPatternEverChosen()) {
                    lockVirgin = false;
                } else {
                    lockVirgin = true;
                }
                boolean isFallback = getActivity().getIntent().getBooleanExtra("lockscreen.biometric_weak_fallback", false);
                utils.setCredentialRequiredToDecrypt(getActivity().getIntent().getBooleanExtra("extra_require_password", true));
                utils.saveLockPattern(this.mChosenPattern, isFallback);
                utils.setLockPatternEnabled(true);
                if (lockVirgin) {
                    utils.setVisiblePatternEnabled(true);
                }
                getActivity().setResult(1);
                getActivity().finish();
                this.mDone = true;
                startActivity(RedactionInterstitial.createStartIntent(getActivity()));
            }
        }
    }

    public Intent getIntent() {
        Intent modIntent = new Intent(super.getIntent());
        modIntent.putExtra(":settings:show_fragment", ChooseLockPatternFragment.class.getName());
        return modIntent;
    }

    public static Intent createIntent(Context context, boolean isFallback, boolean requirePassword, boolean confirmCredentials) {
        Intent intent = new Intent(context, ChooseLockPattern.class);
        intent.putExtra("key_lock_method", "pattern");
        intent.putExtra("confirm_credentials", confirmCredentials);
        intent.putExtra("lockscreen.biometric_weak_fallback", isFallback);
        intent.putExtra("extra_require_password", requirePassword);
        return intent;
    }

    protected boolean isValidFragment(String fragmentName) {
        if (ChooseLockPatternFragment.class.getName().equals(fragmentName)) {
            return true;
        }
        return false;
    }

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(getText(R.string.lockpassword_choose_your_pattern_header));
    }

    public boolean onKeyDown(int keyCode, KeyEvent event) {
        return super.onKeyDown(keyCode, event);
    }
}
