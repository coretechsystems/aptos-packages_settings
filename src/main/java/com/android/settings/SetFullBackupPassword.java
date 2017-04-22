package com.android.settings;

import android.app.Activity;
import android.app.backup.IBackupManager;
import android.app.backup.IBackupManager.Stub;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

public class SetFullBackupPassword extends Activity {
    IBackupManager mBackupManager;
    OnClickListener mButtonListener = new OnClickListener() {
        public void onClick(View v) {
            if (v == SetFullBackupPassword.this.mSet) {
                String curPw = SetFullBackupPassword.this.mCurrentPw.getText().toString();
                String newPw = SetFullBackupPassword.this.mNewPw.getText().toString();
                if (!newPw.equals(SetFullBackupPassword.this.mConfirmNewPw.getText().toString())) {
                    Log.i("SetFullBackupPassword", "password mismatch");
                    Toast.makeText(SetFullBackupPassword.this, R.string.local_backup_password_toast_confirmation_mismatch, 1).show();
                } else if (SetFullBackupPassword.this.setBackupPassword(curPw, newPw)) {
                    Log.i("SetFullBackupPassword", "password set successfully");
                    Toast.makeText(SetFullBackupPassword.this, R.string.local_backup_password_toast_success, 1).show();
                    SetFullBackupPassword.this.finish();
                } else {
                    Log.i("SetFullBackupPassword", "failure; password mismatch?");
                    Toast.makeText(SetFullBackupPassword.this, R.string.local_backup_password_toast_validation_failure, 1).show();
                }
            } else if (v == SetFullBackupPassword.this.mCancel) {
                SetFullBackupPassword.this.finish();
            } else {
                Log.w("SetFullBackupPassword", "Click on unknown view");
            }
        }
    };
    Button mCancel;
    TextView mConfirmNewPw;
    TextView mCurrentPw;
    TextView mNewPw;
    Button mSet;

    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        this.mBackupManager = Stub.asInterface(ServiceManager.getService("backup"));
        setContentView(R.layout.set_backup_pw);
        this.mCurrentPw = (TextView) findViewById(R.id.current_backup_pw);
        this.mNewPw = (TextView) findViewById(R.id.new_backup_pw);
        this.mConfirmNewPw = (TextView) findViewById(R.id.confirm_new_backup_pw);
        this.mCancel = (Button) findViewById(R.id.backup_pw_cancel_button);
        this.mSet = (Button) findViewById(R.id.backup_pw_set_button);
        this.mCancel.setOnClickListener(this.mButtonListener);
        this.mSet.setOnClickListener(this.mButtonListener);
    }

    private boolean setBackupPassword(String currentPw, String newPw) {
        try {
            return this.mBackupManager.setBackupPassword(currentPw, newPw);
        } catch (RemoteException e) {
            Log.e("SetFullBackupPassword", "Unable to communicate with backup manager");
            return false;
        }
    }
}
