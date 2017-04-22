package com.android.settings.vpn2;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.security.KeyStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.Spinner;
import android.widget.TextView;
import com.android.internal.net.VpnProfile;
import com.android.settings.R;
import java.net.InetAddress;

class VpnDialog extends AlertDialog implements TextWatcher, OnClickListener, OnItemSelectedListener {
    private TextView mDnsServers;
    private boolean mEditing;
    private Spinner mIpsecCaCert;
    private TextView mIpsecIdentifier;
    private TextView mIpsecSecret;
    private Spinner mIpsecServerCert;
    private Spinner mIpsecUserCert;
    private final KeyStore mKeyStore = KeyStore.getInstance();
    private TextView mL2tpSecret;
    private final DialogInterface.OnClickListener mListener;
    private CheckBox mMppe;
    private TextView mName;
    private TextView mPassword;
    private final VpnProfile mProfile;
    private TextView mRoutes;
    private CheckBox mSaveLogin;
    private TextView mSearchDomains;
    private TextView mServer;
    private Spinner mType;
    private TextView mUsername;
    private View mView;

    VpnDialog(Context context, DialogInterface.OnClickListener listener, VpnProfile profile, boolean editing) {
        super(context);
        this.mListener = listener;
        this.mProfile = profile;
        this.mEditing = editing;
    }

    protected void onCreate(Bundle savedState) {
        boolean z;
        this.mView = getLayoutInflater().inflate(R.layout.vpn_dialog, null);
        setView(this.mView);
        setInverseBackgroundForced(true);
        Context context = getContext();
        this.mName = (TextView) this.mView.findViewById(R.id.name);
        this.mType = (Spinner) this.mView.findViewById(R.id.type);
        this.mServer = (TextView) this.mView.findViewById(R.id.server);
        this.mUsername = (TextView) this.mView.findViewById(R.id.username);
        this.mPassword = (TextView) this.mView.findViewById(R.id.password);
        this.mSearchDomains = (TextView) this.mView.findViewById(R.id.search_domains);
        this.mDnsServers = (TextView) this.mView.findViewById(R.id.dns_servers);
        this.mRoutes = (TextView) this.mView.findViewById(R.id.routes);
        this.mMppe = (CheckBox) this.mView.findViewById(R.id.mppe);
        this.mL2tpSecret = (TextView) this.mView.findViewById(R.id.l2tp_secret);
        this.mIpsecIdentifier = (TextView) this.mView.findViewById(R.id.ipsec_identifier);
        this.mIpsecSecret = (TextView) this.mView.findViewById(R.id.ipsec_secret);
        this.mIpsecUserCert = (Spinner) this.mView.findViewById(R.id.ipsec_user_cert);
        this.mIpsecCaCert = (Spinner) this.mView.findViewById(R.id.ipsec_ca_cert);
        this.mIpsecServerCert = (Spinner) this.mView.findViewById(R.id.ipsec_server_cert);
        this.mSaveLogin = (CheckBox) this.mView.findViewById(R.id.save_login);
        this.mName.setText(this.mProfile.name);
        this.mType.setSelection(this.mProfile.type);
        this.mServer.setText(this.mProfile.server);
        if (this.mProfile.saveLogin) {
            this.mUsername.setText(this.mProfile.username);
            this.mPassword.setText(this.mProfile.password);
        }
        this.mSearchDomains.setText(this.mProfile.searchDomains);
        this.mDnsServers.setText(this.mProfile.dnsServers);
        this.mRoutes.setText(this.mProfile.routes);
        this.mMppe.setChecked(this.mProfile.mppe);
        this.mL2tpSecret.setText(this.mProfile.l2tpSecret);
        this.mIpsecIdentifier.setText(this.mProfile.ipsecIdentifier);
        this.mIpsecSecret.setText(this.mProfile.ipsecSecret);
        loadCertificates(this.mIpsecUserCert, "USRPKEY_", 0, this.mProfile.ipsecUserCert);
        loadCertificates(this.mIpsecCaCert, "CACERT_", R.string.vpn_no_ca_cert, this.mProfile.ipsecCaCert);
        loadCertificates(this.mIpsecServerCert, "USRCERT_", R.string.vpn_no_server_cert, this.mProfile.ipsecServerCert);
        this.mSaveLogin.setChecked(this.mProfile.saveLogin);
        this.mName.addTextChangedListener(this);
        this.mType.setOnItemSelectedListener(this);
        this.mServer.addTextChangedListener(this);
        this.mUsername.addTextChangedListener(this);
        this.mPassword.addTextChangedListener(this);
        this.mDnsServers.addTextChangedListener(this);
        this.mRoutes.addTextChangedListener(this);
        this.mIpsecSecret.addTextChangedListener(this);
        this.mIpsecUserCert.setOnItemSelectedListener(this);
        boolean valid = validate(true);
        if (this.mEditing || !valid) {
            z = true;
        } else {
            z = false;
        }
        this.mEditing = z;
        if (this.mEditing) {
            setTitle(R.string.vpn_edit);
            this.mView.findViewById(R.id.editor).setVisibility(0);
            changeType(this.mProfile.type);
            View showOptions = this.mView.findViewById(R.id.show_options);
            if (this.mProfile.searchDomains.isEmpty() && this.mProfile.dnsServers.isEmpty() && this.mProfile.routes.isEmpty()) {
                showOptions.setOnClickListener(this);
            } else {
                onClick(showOptions);
            }
            setButton(-1, context.getString(R.string.vpn_save), this.mListener);
        } else {
            setTitle(context.getString(R.string.vpn_connect_to, new Object[]{this.mProfile.name}));
            this.mView.findViewById(R.id.login).setVisibility(0);
            setButton(-1, context.getString(R.string.vpn_connect), this.mListener);
        }
        setButton(-2, context.getString(R.string.vpn_cancel), this.mListener);
        super.onCreate(null);
        Button button = getButton(-1);
        if (!this.mEditing) {
            valid = validate(false);
        }
        button.setEnabled(valid);
        getWindow().setSoftInputMode(20);
    }

    public void afterTextChanged(Editable field) {
        getButton(-1).setEnabled(validate(this.mEditing));
    }

    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
    }

    public void onTextChanged(CharSequence s, int start, int before, int count) {
    }

    public void onClick(View showOptions) {
        showOptions.setVisibility(8);
        this.mView.findViewById(R.id.options).setVisibility(0);
    }

    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        if (parent == this.mType) {
            changeType(position);
        }
        getButton(-1).setEnabled(validate(this.mEditing));
    }

    public void onNothingSelected(AdapterView<?> adapterView) {
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    private void changeType(int r8) {
        /*
        r7 = this;
        r6 = 2131624382; // 0x7f0e01be float:1.8875942E38 double:1.053162377E-314;
        r5 = 2131624379; // 0x7f0e01bb float:1.8875936E38 double:1.0531623755E-314;
        r4 = 2131624377; // 0x7f0e01b9 float:1.8875932E38 double:1.0531623745E-314;
        r3 = 8;
        r2 = 0;
        r0 = r7.mMppe;
        r0.setVisibility(r3);
        r0 = r7.mView;
        r0 = r0.findViewById(r4);
        r0.setVisibility(r3);
        r0 = r7.mView;
        r0 = r0.findViewById(r5);
        r0.setVisibility(r3);
        r0 = r7.mView;
        r0 = r0.findViewById(r6);
        r0.setVisibility(r3);
        r0 = r7.mView;
        r1 = 2131624384; // 0x7f0e01c0 float:1.8875946E38 double:1.053162378E-314;
        r0 = r0.findViewById(r1);
        r0.setVisibility(r3);
        switch(r8) {
            case 0: goto L_0x003c;
            case 1: goto L_0x0042;
            case 2: goto L_0x0055;
            case 3: goto L_0x004b;
            case 4: goto L_0x005e;
            case 5: goto L_0x0067;
            default: goto L_0x003b;
        };
    L_0x003b:
        return;
    L_0x003c:
        r0 = r7.mMppe;
        r0.setVisibility(r2);
        goto L_0x003b;
    L_0x0042:
        r0 = r7.mView;
        r0 = r0.findViewById(r4);
        r0.setVisibility(r2);
    L_0x004b:
        r0 = r7.mView;
        r0 = r0.findViewById(r5);
        r0.setVisibility(r2);
        goto L_0x003b;
    L_0x0055:
        r0 = r7.mView;
        r0 = r0.findViewById(r4);
        r0.setVisibility(r2);
    L_0x005e:
        r0 = r7.mView;
        r0 = r0.findViewById(r6);
        r0.setVisibility(r2);
    L_0x0067:
        r0 = r7.mView;
        r1 = 2131624384; // 0x7f0e01c0 float:1.8875946E38 double:1.053162378E-314;
        r0 = r0.findViewById(r1);
        r0.setVisibility(r2);
        goto L_0x003b;
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.settings.vpn2.VpnDialog.changeType(int):void");
    }

    private boolean validate(boolean editing) {
        if (editing) {
            if (this.mName.getText().length() == 0 || this.mServer.getText().length() == 0 || !validateAddresses(this.mDnsServers.getText().toString(), false) || !validateAddresses(this.mRoutes.getText().toString(), true)) {
                return false;
            }
            switch (this.mType.getSelectedItemPosition()) {
                case 0:
                case 5:
                    return true;
                case 1:
                case 3:
                    if (this.mIpsecSecret.getText().length() == 0) {
                        return false;
                    }
                    return true;
                case 2:
                case 4:
                    if (this.mIpsecUserCert.getSelectedItemPosition() == 0) {
                        return false;
                    }
                    return true;
                default:
                    return false;
            }
        } else if (this.mUsername.getText().length() == 0 || this.mPassword.getText().length() == 0) {
            return false;
        } else {
            return true;
        }
    }

    private boolean validateAddresses(String addresses, boolean cidr) {
        try {
            for (String address : addresses.split(" ")) {
                String address2;
                if (!address2.isEmpty()) {
                    int prefixLength = 32;
                    if (cidr) {
                        String[] parts = address2.split("/", 2);
                        address2 = parts[0];
                        prefixLength = Integer.parseInt(parts[1]);
                    }
                    byte[] bytes = InetAddress.parseNumericAddress(address2).getAddress();
                    int integer = (((bytes[3] & 255) | ((bytes[2] & 255) << 8)) | ((bytes[1] & 255) << 16)) | ((bytes[0] & 255) << 24);
                    if (bytes.length != 4 || prefixLength < 0 || prefixLength > 32 || (prefixLength < 32 && (integer << prefixLength) != 0)) {
                        return false;
                    }
                }
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void loadCertificates(Spinner spinner, String prefix, int firstId, String selected) {
        Context context = getContext();
        String first = firstId == 0 ? "" : context.getString(firstId);
        String[] certificates = this.mKeyStore.saw(prefix);
        if (certificates == null || certificates.length == 0) {
            certificates = new String[]{first};
        } else {
            String[] array = new String[(certificates.length + 1)];
            array[0] = first;
            System.arraycopy(certificates, 0, array, 1, certificates.length);
            certificates = array;
        }
        ArrayAdapter<String> adapter = new ArrayAdapter(context, 17367048, certificates);
        adapter.setDropDownViewResource(17367049);
        spinner.setAdapter(adapter);
        for (int i = 1; i < certificates.length; i++) {
            if (certificates[i].equals(selected)) {
                spinner.setSelection(i);
                return;
            }
        }
    }

    boolean isEditing() {
        return this.mEditing;
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    com.android.internal.net.VpnProfile getProfile() {
        /*
        r2 = this;
        r0 = new com.android.internal.net.VpnProfile;
        r1 = r2.mProfile;
        r1 = r1.key;
        r0.<init>(r1);
        r1 = r2.mName;
        r1 = r1.getText();
        r1 = r1.toString();
        r0.name = r1;
        r1 = r2.mType;
        r1 = r1.getSelectedItemPosition();
        r0.type = r1;
        r1 = r2.mServer;
        r1 = r1.getText();
        r1 = r1.toString();
        r1 = r1.trim();
        r0.server = r1;
        r1 = r2.mUsername;
        r1 = r1.getText();
        r1 = r1.toString();
        r0.username = r1;
        r1 = r2.mPassword;
        r1 = r1.getText();
        r1 = r1.toString();
        r0.password = r1;
        r1 = r2.mSearchDomains;
        r1 = r1.getText();
        r1 = r1.toString();
        r1 = r1.trim();
        r0.searchDomains = r1;
        r1 = r2.mDnsServers;
        r1 = r1.getText();
        r1 = r1.toString();
        r1 = r1.trim();
        r0.dnsServers = r1;
        r1 = r2.mRoutes;
        r1 = r1.getText();
        r1 = r1.toString();
        r1 = r1.trim();
        r0.routes = r1;
        r1 = r0.type;
        switch(r1) {
            case 0: goto L_0x0083;
            case 1: goto L_0x008c;
            case 2: goto L_0x00b1;
            case 3: goto L_0x0098;
            case 4: goto L_0x00bd;
            case 5: goto L_0x00cf;
            default: goto L_0x007a;
        };
    L_0x007a:
        r1 = r2.mSaveLogin;
        r1 = r1.isChecked();
        r0.saveLogin = r1;
        return r0;
    L_0x0083:
        r1 = r2.mMppe;
        r1 = r1.isChecked();
        r0.mppe = r1;
        goto L_0x007a;
    L_0x008c:
        r1 = r2.mL2tpSecret;
        r1 = r1.getText();
        r1 = r1.toString();
        r0.l2tpSecret = r1;
    L_0x0098:
        r1 = r2.mIpsecIdentifier;
        r1 = r1.getText();
        r1 = r1.toString();
        r0.ipsecIdentifier = r1;
        r1 = r2.mIpsecSecret;
        r1 = r1.getText();
        r1 = r1.toString();
        r0.ipsecSecret = r1;
        goto L_0x007a;
    L_0x00b1:
        r1 = r2.mL2tpSecret;
        r1 = r1.getText();
        r1 = r1.toString();
        r0.l2tpSecret = r1;
    L_0x00bd:
        r1 = r2.mIpsecUserCert;
        r1 = r1.getSelectedItemPosition();
        if (r1 == 0) goto L_0x00cf;
    L_0x00c5:
        r1 = r2.mIpsecUserCert;
        r1 = r1.getSelectedItem();
        r1 = (java.lang.String) r1;
        r0.ipsecUserCert = r1;
    L_0x00cf:
        r1 = r2.mIpsecCaCert;
        r1 = r1.getSelectedItemPosition();
        if (r1 == 0) goto L_0x00e1;
    L_0x00d7:
        r1 = r2.mIpsecCaCert;
        r1 = r1.getSelectedItem();
        r1 = (java.lang.String) r1;
        r0.ipsecCaCert = r1;
    L_0x00e1:
        r1 = r2.mIpsecServerCert;
        r1 = r1.getSelectedItemPosition();
        if (r1 == 0) goto L_0x007a;
    L_0x00e9:
        r1 = r2.mIpsecServerCert;
        r1 = r1.getSelectedItem();
        r1 = (java.lang.String) r1;
        r0.ipsecServerCert = r1;
        goto L_0x007a;
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.settings.vpn2.VpnDialog.getProfile():com.android.internal.net.VpnProfile");
    }
}
