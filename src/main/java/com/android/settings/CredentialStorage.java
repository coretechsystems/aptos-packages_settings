package com.android.settings;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.DialogInterface.OnDismissListener;
import android.content.Intent;
import android.content.res.Resources;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.UserManager;
import android.security.KeyChain;
import android.security.KeyChain.KeyChainConnection;
import android.security.KeyStore;
import android.security.KeyStore.State;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import com.android.internal.widget.LockPatternUtils;
import com.android.org.bouncycastle.asn1.ASN1InputStream;
import com.android.org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import org.apache.harmony.security.utils.AlgNameMapper;

public final class CredentialStorage extends Activity {
    private Bundle mInstallBundle;
    private final KeyStore mKeyStore = KeyStore.getInstance();
    private int mRetriesRemaining = -1;

    static /* synthetic */ class AnonymousClass1 {
        static final /* synthetic */ int[] $SwitchMap$android$security$KeyStore$State = new int[State.values().length];

        static {
            try {
                $SwitchMap$android$security$KeyStore$State[State.UNINITIALIZED.ordinal()] = 1;
            } catch (NoSuchFieldError e) {
            }
            try {
                $SwitchMap$android$security$KeyStore$State[State.LOCKED.ordinal()] = 2;
            } catch (NoSuchFieldError e2) {
            }
            try {
                $SwitchMap$android$security$KeyStore$State[State.UNLOCKED.ordinal()] = 3;
            } catch (NoSuchFieldError e3) {
            }
        }
    }

    private class ConfigureKeyGuardDialog implements OnClickListener, OnDismissListener {
        private boolean mConfigureConfirmed;

        private ConfigureKeyGuardDialog() {
            AlertDialog dialog = new Builder(CredentialStorage.this).setTitle(17039380).setMessage(R.string.credentials_configure_lock_screen_hint).setPositiveButton(17039370, this).setNegativeButton(17039360, this).create();
            dialog.setOnDismissListener(this);
            dialog.show();
        }

        public void onClick(DialogInterface dialog, int button) {
            this.mConfigureConfirmed = button == -1;
        }

        public void onDismiss(DialogInterface dialog) {
            if (this.mConfigureConfirmed) {
                this.mConfigureConfirmed = false;
                Intent intent = new Intent("android.app.action.SET_NEW_PASSWORD");
                intent.putExtra("minimum_quality", 65536);
                CredentialStorage.this.startActivity(intent);
                return;
            }
            CredentialStorage.this.finish();
        }
    }

    private class ResetDialog implements OnClickListener, OnDismissListener {
        private boolean mResetConfirmed;

        private ResetDialog() {
            AlertDialog dialog = new Builder(CredentialStorage.this).setTitle(17039380).setMessage(R.string.credentials_reset_hint).setPositiveButton(17039370, this).setNegativeButton(17039360, this).create();
            dialog.setOnDismissListener(this);
            dialog.show();
        }

        public void onClick(DialogInterface dialog, int button) {
            this.mResetConfirmed = button == -1;
        }

        public void onDismiss(DialogInterface dialog) {
            if (this.mResetConfirmed) {
                this.mResetConfirmed = false;
                new ResetKeyStoreAndKeyChain().execute(new Void[0]);
                return;
            }
            CredentialStorage.this.finish();
        }
    }

    private class ResetKeyStoreAndKeyChain extends AsyncTask<Void, Void, Boolean> {
        private ResetKeyStoreAndKeyChain() {
        }

        protected Boolean doInBackground(Void... unused) {
            CredentialStorage.this.mKeyStore.reset();
            try {
                KeyChainConnection keyChainConnection = KeyChain.bind(CredentialStorage.this);
                Boolean valueOf;
                try {
                    valueOf = Boolean.valueOf(keyChainConnection.getService().reset());
                    return valueOf;
                } catch (RemoteException e) {
                    valueOf = Boolean.valueOf(false);
                    return valueOf;
                } finally {
                    keyChainConnection.close();
                }
            } catch (InterruptedException e2) {
                Thread.currentThread().interrupt();
                return Boolean.valueOf(false);
            }
        }

        protected void onPostExecute(Boolean success) {
            if (success.booleanValue()) {
                Toast.makeText(CredentialStorage.this, R.string.credentials_erased, 0).show();
            } else {
                Toast.makeText(CredentialStorage.this, R.string.credentials_not_erased, 0).show();
            }
            CredentialStorage.this.finish();
        }
    }

    private class UnlockDialog implements OnClickListener, OnDismissListener, TextWatcher {
        private final Button mButton;
        private final TextView mError;
        private final TextView mOldPassword;
        private boolean mUnlockConfirmed;

        private UnlockDialog() {
            CharSequence text;
            View view = View.inflate(CredentialStorage.this, R.layout.credentials_dialog, null);
            if (CredentialStorage.this.mRetriesRemaining == -1) {
                text = CredentialStorage.this.getResources().getText(R.string.credentials_unlock_hint);
            } else if (CredentialStorage.this.mRetriesRemaining > 3) {
                text = CredentialStorage.this.getResources().getText(R.string.credentials_wrong_password);
            } else if (CredentialStorage.this.mRetriesRemaining == 1) {
                text = CredentialStorage.this.getResources().getText(R.string.credentials_reset_warning);
            } else {
                text = CredentialStorage.this.getString(R.string.credentials_reset_warning_plural, new Object[]{Integer.valueOf(r9.mRetriesRemaining)});
            }
            ((TextView) view.findViewById(R.id.hint)).setText(text);
            this.mOldPassword = (TextView) view.findViewById(R.id.old_password);
            this.mOldPassword.setVisibility(0);
            this.mOldPassword.addTextChangedListener(this);
            this.mError = (TextView) view.findViewById(R.id.error);
            AlertDialog dialog = new Builder(CredentialStorage.this).setView(view).setTitle(R.string.credentials_unlock).setPositiveButton(17039370, this).setNegativeButton(17039360, this).create();
            dialog.setOnDismissListener(this);
            dialog.show();
            this.mButton = dialog.getButton(-1);
            this.mButton.setEnabled(false);
        }

        public void afterTextChanged(Editable editable) {
            Button button = this.mButton;
            boolean z = this.mOldPassword == null || this.mOldPassword.getText().length() > 0;
            button.setEnabled(z);
        }

        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }

        public void onClick(DialogInterface dialog, int button) {
            this.mUnlockConfirmed = button == -1;
        }

        public void onDismiss(DialogInterface dialog) {
            if (this.mUnlockConfirmed) {
                this.mUnlockConfirmed = false;
                this.mError.setVisibility(0);
                CredentialStorage.this.mKeyStore.unlock(this.mOldPassword.getText().toString());
                int error = CredentialStorage.this.mKeyStore.getLastError();
                if (error == 1) {
                    CredentialStorage.this.mRetriesRemaining = -1;
                    Toast.makeText(CredentialStorage.this, R.string.credentials_enabled, 0).show();
                    CredentialStorage.this.ensureKeyGuard();
                    return;
                } else if (error == 3) {
                    CredentialStorage.this.mRetriesRemaining = -1;
                    Toast.makeText(CredentialStorage.this, R.string.credentials_erased, 0).show();
                    CredentialStorage.this.handleUnlockOrInstall();
                    return;
                } else if (error >= 10) {
                    CredentialStorage.this.mRetriesRemaining = (error - 10) + 1;
                    CredentialStorage.this.handleUnlockOrInstall();
                    return;
                } else {
                    return;
                }
            }
            CredentialStorage.this.finish();
        }
    }

    protected void onResume() {
        super.onResume();
        Intent intent = getIntent();
        String action = intent.getAction();
        if (((UserManager) getSystemService("user")).hasUserRestriction("no_config_credentials")) {
            finish();
        } else if ("com.android.credentials.RESET".equals(action)) {
            ResetDialog resetDialog = new ResetDialog();
        } else {
            if ("com.android.credentials.INSTALL".equals(action) && "com.android.certinstaller".equals(getCallingPackage())) {
                this.mInstallBundle = intent.getExtras();
            }
            handleUnlockOrInstall();
        }
    }

    private void handleUnlockOrInstall() {
        if (!isFinishing()) {
            switch (AnonymousClass1.$SwitchMap$android$security$KeyStore$State[this.mKeyStore.state().ordinal()]) {
                case 1:
                    ensureKeyGuard();
                    return;
                case 2:
                    UnlockDialog unlockDialog = new UnlockDialog();
                    return;
                case 3:
                    if (checkKeyGuardQuality()) {
                        installIfAvailable();
                        finish();
                        return;
                    }
                    ConfigureKeyGuardDialog configureKeyGuardDialog = new ConfigureKeyGuardDialog();
                    return;
                default:
                    return;
            }
        }
    }

    private void ensureKeyGuard() {
        if (!checkKeyGuardQuality()) {
            ConfigureKeyGuardDialog configureKeyGuardDialog = new ConfigureKeyGuardDialog();
        } else if (!confirmKeyGuard()) {
            finish();
        }
    }

    private boolean checkKeyGuardQuality() {
        return new LockPatternUtils(this).getActivePasswordQuality() >= 65536;
    }

    private boolean isHardwareBackedKey(byte[] keyData) {
        try {
            return KeyChain.isBoundKeyAlgorithm(AlgNameMapper.map2AlgName(PrivateKeyInfo.getInstance(new ASN1InputStream(new ByteArrayInputStream(keyData)).readObject()).getAlgorithmId().getAlgorithm().getId()));
        } catch (IOException e) {
            Log.e("CredentialStorage", "Failed to parse key data");
            return false;
        }
    }

    private void installIfAvailable() {
        if (this.mInstallBundle != null && !this.mInstallBundle.isEmpty()) {
            int flags;
            Bundle bundle = this.mInstallBundle;
            this.mInstallBundle = null;
            int uid = bundle.getInt("install_as_uid", -1);
            if (bundle.containsKey("user_private_key_name")) {
                String key = bundle.getString("user_private_key_name");
                byte[] value = bundle.getByteArray("user_private_key_data");
                flags = 1;
                if (uid == 1010 && isHardwareBackedKey(value)) {
                    Log.d("CredentialStorage", "Saving private key with FLAG_NONE for WIFI_UID");
                    flags = 0;
                }
                if (!this.mKeyStore.importKey(key, value, uid, flags)) {
                    Log.e("CredentialStorage", "Failed to install " + key + " as user " + uid);
                    return;
                }
            }
            flags = uid == 1010 ? 0 : 1;
            if (bundle.containsKey("user_certificate_name")) {
                String certName = bundle.getString("user_certificate_name");
                if (!this.mKeyStore.put(certName, bundle.getByteArray("user_certificate_data"), uid, flags)) {
                    Log.e("CredentialStorage", "Failed to install " + certName + " as user " + uid);
                    return;
                }
            }
            if (bundle.containsKey("ca_certificates_name")) {
                String caListName = bundle.getString("ca_certificates_name");
                if (!this.mKeyStore.put(caListName, bundle.getByteArray("ca_certificates_data"), uid, flags)) {
                    Log.e("CredentialStorage", "Failed to install " + caListName + " as user " + uid);
                    return;
                }
            }
            setResult(-1);
        }
    }

    private boolean confirmKeyGuard() {
        Resources res = getResources();
        return new ChooseLockSettingsHelper(this).launchConfirmationActivity(1, res.getText(R.string.credentials_install_gesture_prompt), res.getText(R.string.credentials_install_gesture_explanation), true);
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1) {
            if (resultCode == -1) {
                String password = data.getStringExtra("password");
                if (!TextUtils.isEmpty(password)) {
                    this.mKeyStore.password(password);
                    return;
                }
            }
            finish();
        }
    }
}
