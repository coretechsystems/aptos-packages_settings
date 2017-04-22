package com.android.settings.wifi;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.net.wifi.WifiConfiguration;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import com.android.settings.R;

public class WifiApDialog extends AlertDialog implements TextWatcher, OnClickListener, OnItemSelectedListener {
    private final DialogInterface.OnClickListener mListener;
    private EditText mPassword;
    private int mSecurityTypeIndex = 0;
    private TextView mSsid;
    private View mView;
    WifiConfiguration mWifiConfig;

    public WifiApDialog(Context context, DialogInterface.OnClickListener listener, WifiConfiguration wifiConfig) {
        super(context);
        this.mListener = listener;
        this.mWifiConfig = wifiConfig;
        if (wifiConfig != null) {
            this.mSecurityTypeIndex = getSecurityTypeIndex(wifiConfig);
        }
    }

    public static int getSecurityTypeIndex(WifiConfiguration wifiConfig) {
        if (wifiConfig.allowedKeyManagement.get(4)) {
            return 1;
        }
        return 0;
    }

    public WifiConfiguration getConfig() {
        WifiConfiguration config = new WifiConfiguration();
        config.SSID = this.mSsid.getText().toString();
        switch (this.mSecurityTypeIndex) {
            case 0:
                config.allowedKeyManagement.set(0);
                return config;
            case 1:
                config.allowedKeyManagement.set(4);
                config.allowedAuthAlgorithms.set(0);
                if (this.mPassword.length() == 0) {
                    return config;
                }
                config.preSharedKey = this.mPassword.getText().toString();
                return config;
            default:
                return null;
        }
    }

    protected void onCreate(Bundle savedInstanceState) {
        this.mView = getLayoutInflater().inflate(R.layout.wifi_ap_dialog, null);
        Spinner mSecurity = (Spinner) this.mView.findViewById(R.id.security);
        setView(this.mView);
        setInverseBackgroundForced(true);
        Context context = getContext();
        setTitle(R.string.wifi_tether_configure_ap_text);
        this.mView.findViewById(R.id.type).setVisibility(0);
        this.mSsid = (TextView) this.mView.findViewById(R.id.ssid);
        this.mPassword = (EditText) this.mView.findViewById(R.id.password);
        setButton(-1, context.getString(R.string.wifi_save), this.mListener);
        setButton(-2, context.getString(R.string.wifi_cancel), this.mListener);
        if (this.mWifiConfig != null) {
            this.mSsid.setText(this.mWifiConfig.SSID);
            mSecurity.setSelection(this.mSecurityTypeIndex);
            if (this.mSecurityTypeIndex == 1) {
                this.mPassword.setText(this.mWifiConfig.preSharedKey);
            }
        }
        this.mSsid.addTextChangedListener(this);
        this.mPassword.addTextChangedListener(this);
        ((CheckBox) this.mView.findViewById(R.id.show_password)).setOnClickListener(this);
        mSecurity.setOnItemSelectedListener(this);
        super.onCreate(savedInstanceState);
        showSecurityFields();
        validate();
    }

    private void validate() {
        if ((this.mSsid == null || this.mSsid.length() != 0) && (this.mSecurityTypeIndex != 1 || this.mPassword.length() >= 8)) {
            getButton(-1).setEnabled(true);
        } else {
            getButton(-1).setEnabled(false);
        }
    }

    public void onClick(View view) {
        this.mPassword.setInputType((((CheckBox) view).isChecked() ? 144 : 128) | 1);
    }

    public void onTextChanged(CharSequence s, int start, int before, int count) {
    }

    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
    }

    public void afterTextChanged(Editable editable) {
        validate();
    }

    public void onItemSelected(AdapterView<?> adapterView, View view, int position, long id) {
        this.mSecurityTypeIndex = position;
        showSecurityFields();
        validate();
    }

    public void onNothingSelected(AdapterView<?> adapterView) {
    }

    private void showSecurityFields() {
        if (this.mSecurityTypeIndex == 0) {
            this.mView.findViewById(R.id.fields).setVisibility(8);
        } else {
            this.mView.findViewById(R.id.fields).setVisibility(0);
        }
    }
}
