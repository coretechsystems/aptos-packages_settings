package com.android.settings.bluetooth;

import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.text.Editable;
import android.text.Html;
import android.text.InputFilter;
import android.text.InputFilter.LengthFilter;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.TextView;
import com.android.internal.app.AlertActivity;
import com.android.internal.app.AlertController.AlertParams;
import com.android.settings.R;
import java.util.Locale;

public final class BluetoothPairingDialog extends AlertActivity implements OnClickListener, TextWatcher, OnCheckedChangeListener {
    private LocalBluetoothManager mBluetoothManager;
    private CachedBluetoothDeviceManager mCachedDeviceManager;
    private BluetoothDevice mDevice;
    private Button mOkButton;
    private String mPairingKey;
    private EditText mPairingView;
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if ("android.bluetooth.device.action.BOND_STATE_CHANGED".equals(action)) {
                int bondState = intent.getIntExtra("android.bluetooth.device.extra.BOND_STATE", Integer.MIN_VALUE);
                if (bondState == 12 || bondState == 10) {
                    BluetoothPairingDialog.this.dismiss();
                }
            } else if ("android.bluetooth.device.action.PAIRING_CANCEL".equals(action)) {
                BluetoothDevice device = (BluetoothDevice) intent.getParcelableExtra("android.bluetooth.device.extra.DEVICE");
                if (device == null || device.equals(BluetoothPairingDialog.this.mDevice)) {
                    BluetoothPairingDialog.this.dismiss();
                }
            }
        }
    };
    private int mType;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = getIntent();
        if (intent.getAction().equals("android.bluetooth.device.action.PAIRING_REQUEST")) {
            this.mBluetoothManager = LocalBluetoothManager.getInstance(this);
            if (this.mBluetoothManager == null) {
                Log.e("BluetoothPairingDialog", "Error: BluetoothAdapter not supported by system");
                finish();
                return;
            }
            this.mCachedDeviceManager = this.mBluetoothManager.getCachedDeviceManager();
            this.mDevice = (BluetoothDevice) intent.getParcelableExtra("android.bluetooth.device.extra.DEVICE");
            this.mType = intent.getIntExtra("android.bluetooth.device.extra.PAIRING_VARIANT", Integer.MIN_VALUE);
            switch (this.mType) {
                case 0:
                case 1:
                    createUserEntryDialog();
                    break;
                case 2:
                    if (intent.getIntExtra("android.bluetooth.device.extra.PAIRING_KEY", Integer.MIN_VALUE) != Integer.MIN_VALUE) {
                        this.mPairingKey = String.format(Locale.US, "%06d", new Object[]{Integer.valueOf(intent.getIntExtra("android.bluetooth.device.extra.PAIRING_KEY", Integer.MIN_VALUE))});
                        createConfirmationDialog();
                        break;
                    }
                    Log.e("BluetoothPairingDialog", "Invalid Confirmation Passkey received, not showing any dialog");
                    return;
                case 3:
                case 6:
                    createConsentDialog();
                    break;
                case 4:
                case 5:
                    if (intent.getIntExtra("android.bluetooth.device.extra.PAIRING_KEY", Integer.MIN_VALUE) != Integer.MIN_VALUE) {
                        if (this.mType == 4) {
                            this.mPairingKey = String.format("%06d", new Object[]{Integer.valueOf(pairingKey)});
                        } else {
                            this.mPairingKey = String.format("%04d", new Object[]{Integer.valueOf(pairingKey)});
                        }
                        createDisplayPasskeyOrPinDialog();
                        break;
                    }
                    Log.e("BluetoothPairingDialog", "Invalid Confirmation Passkey or PIN received, not showing any dialog");
                    return;
                default:
                    Log.e("BluetoothPairingDialog", "Incorrect pairing type received, not showing any dialog");
                    break;
            }
            registerReceiver(this.mReceiver, new IntentFilter("android.bluetooth.device.action.PAIRING_CANCEL"));
            registerReceiver(this.mReceiver, new IntentFilter("android.bluetooth.device.action.BOND_STATE_CHANGED"));
            return;
        }
        Log.e("BluetoothPairingDialog", "Error: this activity may be started only with intent android.bluetooth.device.action.PAIRING_REQUEST");
        finish();
    }

    private void createUserEntryDialog() {
        AlertParams p = this.mAlertParams;
        p.mTitle = getString(R.string.bluetooth_pairing_request);
        p.mView = createPinEntryView();
        p.mPositiveButtonText = getString(17039370);
        p.mPositiveButtonListener = this;
        p.mNegativeButtonText = getString(17039360);
        p.mNegativeButtonListener = this;
        setupAlert();
        this.mOkButton = this.mAlert.getButton(-1);
        this.mOkButton.setEnabled(false);
    }

    private View createPinEntryView() {
        int messageId1;
        int messageId2;
        int maxLength;
        View view = getLayoutInflater().inflate(R.layout.bluetooth_pin_entry, null);
        TextView messageViewCaption = (TextView) view.findViewById(R.id.message_caption);
        TextView messageViewContent = (TextView) view.findViewById(R.id.message_subhead);
        TextView messageView2 = (TextView) view.findViewById(R.id.message_below_pin);
        CheckBox alphanumericPin = (CheckBox) view.findViewById(R.id.alphanumeric_pin);
        this.mPairingView = (EditText) view.findViewById(R.id.text);
        this.mPairingView.addTextChangedListener(this);
        alphanumericPin.setOnCheckedChangeListener(this);
        switch (this.mType) {
            case 0:
                messageId1 = R.string.bluetooth_enter_pin_msg;
                messageId2 = R.string.bluetooth_enter_pin_other_device;
                maxLength = 16;
                break;
            case 1:
                messageId1 = R.string.bluetooth_enter_pin_msg;
                messageId2 = R.string.bluetooth_enter_passkey_other_device;
                maxLength = 6;
                alphanumericPin.setVisibility(8);
                break;
            default:
                Log.e("BluetoothPairingDialog", "Incorrect pairing type for createPinEntryView: " + this.mType);
                return null;
        }
        messageViewCaption.setText(messageId1);
        messageViewContent.setText(this.mCachedDeviceManager.getName(this.mDevice));
        messageView2.setText(messageId2);
        this.mPairingView.setInputType(2);
        this.mPairingView.setFilters(new InputFilter[]{new LengthFilter(maxLength)});
        return view;
    }

    private View createView() {
        String messageCaption;
        View view = getLayoutInflater().inflate(R.layout.bluetooth_pin_confirm, null);
        String name = Html.escapeHtml(this.mCachedDeviceManager.getName(this.mDevice));
        TextView messageViewCaption = (TextView) view.findViewById(R.id.message_caption);
        TextView messageViewContent = (TextView) view.findViewById(R.id.message_subhead);
        TextView pairingViewCaption = (TextView) view.findViewById(R.id.pairing_caption);
        TextView pairingViewContent = (TextView) view.findViewById(R.id.pairing_subhead);
        TextView messagePairing = (TextView) view.findViewById(R.id.pairing_code_message);
        String pairingContent = null;
        switch (this.mType) {
            case 2:
                break;
            case 3:
            case 6:
                messagePairing.setVisibility(0);
                messageCaption = getString(R.string.bluetooth_enter_pin_msg);
                break;
            case 4:
            case 5:
                messagePairing.setVisibility(0);
                break;
            default:
                Log.e("BluetoothPairingDialog", "Incorrect pairing type received, not creating view");
                return null;
        }
        messageCaption = getString(R.string.bluetooth_enter_pin_msg);
        pairingContent = this.mPairingKey;
        if (messageViewCaption != null) {
            messageViewCaption.setText(messageCaption);
            messageViewContent.setText(name);
        }
        if (pairingContent == null) {
            return view;
        }
        pairingViewCaption.setVisibility(0);
        pairingViewContent.setVisibility(0);
        pairingViewContent.setText(pairingContent);
        return view;
    }

    private void createConfirmationDialog() {
        AlertParams p = this.mAlertParams;
        p.mTitle = getString(R.string.bluetooth_pairing_request);
        p.mView = createView();
        p.mPositiveButtonText = getString(R.string.bluetooth_pairing_accept);
        p.mPositiveButtonListener = this;
        p.mNegativeButtonText = getString(R.string.bluetooth_pairing_decline);
        p.mNegativeButtonListener = this;
        setupAlert();
    }

    private void createConsentDialog() {
        AlertParams p = this.mAlertParams;
        p.mTitle = getString(R.string.bluetooth_pairing_request);
        p.mView = createView();
        p.mPositiveButtonText = getString(R.string.bluetooth_pairing_accept);
        p.mPositiveButtonListener = this;
        p.mNegativeButtonText = getString(R.string.bluetooth_pairing_decline);
        p.mNegativeButtonListener = this;
        setupAlert();
    }

    private void createDisplayPasskeyOrPinDialog() {
        AlertParams p = this.mAlertParams;
        p.mTitle = getString(R.string.bluetooth_pairing_request);
        p.mView = createView();
        p.mNegativeButtonText = getString(17039360);
        p.mNegativeButtonListener = this;
        setupAlert();
        if (this.mType == 4) {
            this.mDevice.setPairingConfirmation(true);
        } else if (this.mType == 5) {
            this.mDevice.setPin(BluetoothDevice.convertPinToBytes(this.mPairingKey));
        }
    }

    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(this.mReceiver);
    }

    public void afterTextChanged(Editable s) {
        if (this.mOkButton != null) {
            this.mOkButton.setEnabled(s.length() > 0);
        }
    }

    private void allowPhonebookAccess() {
        CachedBluetoothDevice cachedDevice = this.mCachedDeviceManager.findDevice(this.mDevice);
        if (cachedDevice == null) {
            cachedDevice = this.mCachedDeviceManager.addDevice(this.mBluetoothManager.getBluetoothAdapter(), this.mBluetoothManager.getProfileManager(), this.mDevice);
        }
        cachedDevice.setPhonebookPermissionChoice(1);
    }

    private void onPair(String value) {
        allowPhonebookAccess();
        switch (this.mType) {
            case 0:
                byte[] pinBytes = BluetoothDevice.convertPinToBytes(value);
                if (pinBytes != null) {
                    this.mDevice.setPin(pinBytes);
                    return;
                }
                return;
            case 1:
                this.mDevice.setPasskey(Integer.parseInt(value));
                return;
            case 2:
            case 3:
                this.mDevice.setPairingConfirmation(true);
                return;
            case 4:
            case 5:
                return;
            case 6:
                this.mDevice.setRemoteOutOfBandData();
                return;
            default:
                Log.e("BluetoothPairingDialog", "Incorrect pairing type received");
                return;
        }
    }

    private void onCancel() {
        this.mDevice.cancelPairingUserInput();
    }

    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == 4) {
            onCancel();
        }
        return super.onKeyDown(keyCode, event);
    }

    public void onClick(DialogInterface dialog, int which) {
        switch (which) {
            case -1:
                if (this.mPairingView != null) {
                    onPair(this.mPairingView.getText().toString());
                    return;
                } else {
                    onPair(null);
                    return;
                }
            default:
                onCancel();
                return;
        }
    }

    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
    }

    public void onTextChanged(CharSequence s, int start, int before, int count) {
    }

    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        if (isChecked) {
            this.mPairingView.setInputType(1);
        } else {
            this.mPairingView.setInputType(2);
        }
    }
}
