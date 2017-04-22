package com.android.settings;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AuthenticatorDescription;
import android.app.Fragment;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Environment;
import android.os.Process;
import android.os.SystemProperties;
import android.os.UserManager;
import android.preference.Preference;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MasterClear extends Fragment {
    private View mContentView;
    private CheckBox mExternalStorage;
    private View mExternalStorageContainer;
    private Button mInitiateButton;
    private final OnClickListener mInitiateListener = new OnClickListener() {
        public void onClick(View v) {
            if (!MasterClear.this.runKeyguardConfirmation(55)) {
                MasterClear.this.showFinalConfirmation();
            }
        }
    };

    private boolean runKeyguardConfirmation(int request) {
        Resources res = getActivity().getResources();
        return new ChooseLockSettingsHelper(getActivity(), this).launchConfirmationActivity(request, res.getText(R.string.master_clear_gesture_prompt), res.getText(R.string.master_clear_gesture_explanation));
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 55) {
            if (resultCode == -1) {
                showFinalConfirmation();
            } else {
                establishInitialState();
            }
        }
    }

    private void showFinalConfirmation() {
        Preference preference = new Preference(getActivity());
        preference.setFragment(MasterClearConfirm.class.getName());
        preference.setTitle(R.string.master_clear_confirm_title);
        preference.getExtras().putBoolean("erase_sd", this.mExternalStorage.isChecked());
        ((SettingsActivity) getActivity()).onPreferenceStartFragment(null, preference);
    }

    private void establishInitialState() {
        this.mInitiateButton = (Button) this.mContentView.findViewById(R.id.initiate_master_clear);
        this.mInitiateButton.setOnClickListener(this.mInitiateListener);
        this.mExternalStorageContainer = this.mContentView.findViewById(R.id.erase_external_container);
        this.mExternalStorage = (CheckBox) this.mContentView.findViewById(R.id.erase_external);
        boolean isExtStorageEmulated = Environment.isExternalStorageEmulated();
        if (isExtStorageEmulated || (!Environment.isExternalStorageRemovable() && isExtStorageEncrypted())) {
            boolean z;
            this.mExternalStorageContainer.setVisibility(8);
            this.mContentView.findViewById(R.id.erase_external_option_text).setVisibility(8);
            this.mContentView.findViewById(R.id.also_erases_external).setVisibility(0);
            CheckBox checkBox = this.mExternalStorage;
            if (isExtStorageEmulated) {
                z = false;
            } else {
                z = true;
            }
            checkBox.setChecked(z);
        } else {
            this.mExternalStorageContainer.setOnClickListener(new OnClickListener() {
                public void onClick(View v) {
                    MasterClear.this.mExternalStorage.toggle();
                }
            });
        }
        loadAccountList();
    }

    private boolean isExtStorageEncrypted() {
        return !"".equals(SystemProperties.get("vold.decrypt"));
    }

    private void loadAccountList() {
        View accountsLabel = this.mContentView.findViewById(R.id.accounts_label);
        LinearLayout contents = (LinearLayout) this.mContentView.findViewById(R.id.accounts);
        contents.removeAllViews();
        Context context = getActivity();
        if (N == 0) {
            accountsLabel.setVisibility(8);
            contents.setVisibility(8);
            return;
        }
        LayoutInflater inflater = (LayoutInflater) context.getSystemService("layout_inflater");
        AuthenticatorDescription[] descs = AccountManager.get(context).getAuthenticatorTypes();
        int M = descs.length;
        for (Account account : AccountManager.get(context).getAccounts()) {
            AuthenticatorDescription desc = null;
            for (int j = 0; j < M; j++) {
                if (account.type.equals(descs[j].type)) {
                    desc = descs[j];
                    break;
                }
            }
            if (desc == null) {
                Log.w("MasterClear", "No descriptor for account name=" + account.name + " type=" + account.type);
            } else {
                Drawable icon = null;
                try {
                    if (desc.iconId != 0) {
                        icon = context.createPackageContext(desc.packageName, 0).getResources().getDrawable(desc.iconId);
                    }
                } catch (NameNotFoundException e) {
                    Log.w("MasterClear", "No icon for account type " + desc.type);
                }
                TextView child = (TextView) inflater.inflate(R.layout.master_clear_account, contents, false);
                child.setText(account.name);
                if (icon != null) {
                    child.setCompoundDrawablesWithIntrinsicBounds(icon, null, null, null);
                }
                contents.addView(child);
            }
        }
        accountsLabel.setVisibility(0);
        contents.setVisibility(0);
    }

    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        if (!Process.myUserHandle().isOwner() || UserManager.get(getActivity()).hasUserRestriction("no_factory_reset")) {
            return inflater.inflate(R.layout.master_clear_disallowed_screen, null);
        }
        this.mContentView = inflater.inflate(R.layout.master_clear, null);
        establishInitialState();
        return this.mContentView;
    }
}
