package com.android.settings;

import android.app.Fragment;
import android.os.Bundle;
import android.os.UserHandle;
import android.os.UserManager;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import com.android.internal.widget.LockPatternUtils;

public class OwnerInfoSettings extends Fragment {
    private CheckBox mCheckbox;
    private LockPatternUtils mLockPatternUtils;
    private EditText mNickname;
    private EditText mOwnerInfo;
    private boolean mShowNickname;
    private int mUserId;
    private View mView;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle args = getArguments();
        if (args != null && args.containsKey("show_nickname")) {
            this.mShowNickname = args.getBoolean("show_nickname");
        }
    }

    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        this.mView = inflater.inflate(R.layout.ownerinfo, container, false);
        this.mUserId = UserHandle.myUserId();
        this.mLockPatternUtils = new LockPatternUtils(getActivity());
        initView();
        return this.mView;
    }

    private void initView() {
        this.mNickname = (EditText) this.mView.findViewById(R.id.owner_info_nickname);
        if (this.mShowNickname) {
            this.mNickname.setText(UserManager.get(getActivity()).getUserName());
            this.mNickname.setSelected(true);
        } else {
            this.mNickname.setVisibility(8);
        }
        boolean enabled = this.mLockPatternUtils.isOwnerInfoEnabled();
        this.mCheckbox = (CheckBox) this.mView.findViewById(R.id.show_owner_info_on_lockscreen_checkbox);
        this.mCheckbox.setChecked(enabled);
        if (UserHandle.myUserId() != 0) {
            if (UserManager.get(getActivity()).isLinkedUser()) {
                this.mCheckbox.setText(R.string.show_profile_info_on_lockscreen_label);
            } else {
                this.mCheckbox.setText(R.string.show_user_info_on_lockscreen_label);
            }
        }
        this.mCheckbox.setOnCheckedChangeListener(new OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                OwnerInfoSettings.this.mLockPatternUtils.setOwnerInfoEnabled(isChecked);
                OwnerInfoSettings.this.mOwnerInfo.setEnabled(isChecked);
            }
        });
        String info = this.mLockPatternUtils.getOwnerInfo(this.mUserId);
        this.mOwnerInfo = (EditText) this.mView.findViewById(R.id.owner_info_edit_text);
        this.mOwnerInfo.setEnabled(enabled);
        if (!TextUtils.isEmpty(info)) {
            this.mOwnerInfo.setText(info);
        }
    }

    public void onPause() {
        super.onPause();
        saveChanges();
    }

    void saveChanges() {
        this.mLockPatternUtils.setOwnerInfo(this.mOwnerInfo.getText().toString(), this.mUserId);
        if (this.mShowNickname) {
            String oldName = UserManager.get(getActivity()).getUserName();
            CharSequence newName = this.mNickname.getText();
            if (!TextUtils.isEmpty(newName) && !newName.equals(oldName)) {
                UserManager.get(getActivity()).setUserName(UserHandle.myUserId(), newName.toString());
            }
        }
    }
}
