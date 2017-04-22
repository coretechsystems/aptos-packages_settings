package com.android.settings;

import android.app.AlertDialog.Builder;
import android.app.Dialog;
import android.app.Fragment;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.net.http.SslCertificate;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.UserHandle;
import android.os.UserManager;
import android.security.IKeyChainService;
import android.security.KeyChain;
import android.security.KeyChain.KeyChainConnection;
import android.util.Log;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.BaseExpandableListAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ExpandableListView;
import android.widget.ExpandableListView.OnChildClickListener;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TabHost;
import android.widget.TextView;
import com.android.internal.R;
import com.android.internal.util.ParcelableString;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TrustedCredentialsSettings extends Fragment {
    private final SparseArray<KeyChainConnection> mKeyChainConnectionByProfileId = new SparseArray();
    private TabHost mTabHost;
    private UserManager mUserManager;

    static /* synthetic */ class AnonymousClass6 {
        static final /* synthetic */ int[] $SwitchMap$com$android$settings$TrustedCredentialsSettings$Tab = new int[Tab.values().length];

        static {
            try {
                $SwitchMap$com$android$settings$TrustedCredentialsSettings$Tab[Tab.SYSTEM.ordinal()] = 1;
            } catch (NoSuchFieldError e) {
            }
            try {
                $SwitchMap$com$android$settings$TrustedCredentialsSettings$Tab[Tab.USER.ordinal()] = 2;
            } catch (NoSuchFieldError e2) {
            }
        }
    }

    private class AdapterData {
        private final TrustedCertificateAdapterCommons mAdapter;
        private final SparseArray<List<CertHolder>> mCertHoldersByUserId;
        private final Tab mTab;

        private class AliasLoader extends AsyncTask<Void, Integer, SparseArray<List<CertHolder>>> {
            private View mList;
            private ProgressBar mProgressBar;

            private AliasLoader() {
            }

            protected void onPreExecute() {
                View content = TrustedCredentialsSettings.this.mTabHost.getTabContentView();
                this.mProgressBar = (ProgressBar) content.findViewById(AdapterData.this.mTab.mProgress);
                this.mList = content.findViewById(AdapterData.this.mAdapter.getListViewId(AdapterData.this.mTab));
                this.mProgressBar.setVisibility(0);
                this.mList.setVisibility(8);
            }

            protected SparseArray<List<CertHolder>> doInBackground(Void... params) {
                SparseArray<List<CertHolder>> sparseArray = new SparseArray();
                try {
                    int i;
                    int profileId;
                    List<ParcelableString> aliases;
                    List<UserHandle> profiles = TrustedCredentialsSettings.this.mUserManager.getUserProfiles();
                    int n = profiles.size();
                    SparseArray<List<ParcelableString>> aliasesByProfileId = new SparseArray(n);
                    int max = 0;
                    int progress = 0;
                    for (i = 0; i < n; i++) {
                        UserHandle profile = (UserHandle) profiles.get(i);
                        profileId = profile.getIdentifier();
                        KeyChainConnection keyChainConnection = KeyChain.bindAsUser(TrustedCredentialsSettings.this.getActivity(), profile);
                        TrustedCredentialsSettings.this.mKeyChainConnectionByProfileId.put(profileId, keyChainConnection);
                        aliases = AdapterData.this.mTab.getAliases(keyChainConnection.getService());
                        max += aliases.size();
                        aliasesByProfileId.put(profileId, aliases);
                    }
                    for (i = 0; i < n; i++) {
                        profileId = ((UserHandle) profiles.get(i)).getIdentifier();
                        aliases = (List) aliasesByProfileId.get(profileId);
                        IKeyChainService service = ((KeyChainConnection) TrustedCredentialsSettings.this.mKeyChainConnectionByProfileId.get(profileId)).getService();
                        List<CertHolder> certHolders = new ArrayList(max);
                        int aliasMax = aliases.size();
                        for (int j = 0; j < aliasMax; j++) {
                            String alias = ((ParcelableString) aliases.get(j)).string;
                            certHolders.add(new CertHolder(service, AdapterData.this.mAdapter, AdapterData.this.mTab, alias, KeyChain.toCertificate(service.getEncodedCaCertificate(alias, true)), profileId));
                            r2 = new Integer[2];
                            progress++;
                            r2[0] = Integer.valueOf(progress);
                            r2[1] = Integer.valueOf(max);
                            publishProgress(r2);
                        }
                        Collections.sort(certHolders);
                        sparseArray.put(profileId, certHolders);
                    }
                    return sparseArray;
                } catch (RemoteException e) {
                    Log.e("TrustedCredentialsSettings", "Remote exception while loading aliases.", e);
                    return new SparseArray();
                } catch (InterruptedException e2) {
                    Log.e("TrustedCredentialsSettings", "InterruptedException while loading aliases.", e2);
                    return new SparseArray();
                }
            }

            protected void onProgressUpdate(Integer... progressAndMax) {
                int progress = progressAndMax[0].intValue();
                int max = progressAndMax[1].intValue();
                if (max != this.mProgressBar.getMax()) {
                    this.mProgressBar.setMax(max);
                }
                this.mProgressBar.setProgress(progress);
            }

            protected void onPostExecute(SparseArray<List<CertHolder>> certHolders) {
                AdapterData.this.mCertHoldersByUserId.clear();
                int n = certHolders.size();
                for (int i = 0; i < n; i++) {
                    AdapterData.this.mCertHoldersByUserId.put(certHolders.keyAt(i), certHolders.valueAt(i));
                }
                AdapterData.this.mAdapter.notifyDataSetChanged();
                this.mProgressBar.setVisibility(8);
                this.mList.setVisibility(0);
                this.mProgressBar.setProgress(0);
            }
        }

        private AdapterData(Tab tab, TrustedCertificateAdapterCommons adapter) {
            this.mCertHoldersByUserId = new SparseArray();
            this.mAdapter = adapter;
            this.mTab = tab;
        }

        public void remove(CertHolder certHolder) {
            if (this.mCertHoldersByUserId != null) {
                List<CertHolder> certs = (List) this.mCertHoldersByUserId.get(certHolder.mProfileId);
                if (certs != null) {
                    certs.remove(certHolder);
                }
            }
        }
    }

    private class AliasOperation extends AsyncTask<Void, Void, Boolean> {
        private final CertHolder mCertHolder;

        private AliasOperation(CertHolder certHolder) {
            this.mCertHolder = certHolder;
        }

        protected Boolean doInBackground(Void... params) {
            try {
                IKeyChainService service = ((KeyChainConnection) TrustedCredentialsSettings.this.mKeyChainConnectionByProfileId.get(this.mCertHolder.mProfileId)).getService();
                if (!this.mCertHolder.mDeleted) {
                    return Boolean.valueOf(service.deleteCaCertificate(this.mCertHolder.mAlias));
                }
                service.installCaCertificate(this.mCertHolder.mX509Cert.getEncoded());
                return Boolean.valueOf(true);
            } catch (CertificateEncodingException e) {
                Log.w("TrustedCredentialsSettings", "Error while toggling alias " + this.mCertHolder.mAlias, e);
                return Boolean.valueOf(false);
            } catch (IllegalStateException e2) {
                Log.w("TrustedCredentialsSettings", "Error while toggling alias " + this.mCertHolder.mAlias, e2);
                return Boolean.valueOf(false);
            } catch (RemoteException e3) {
                Log.w("TrustedCredentialsSettings", "Error while toggling alias " + this.mCertHolder.mAlias, e3);
                return Boolean.valueOf(false);
            }
        }

        protected void onPostExecute(Boolean ok) {
            this.mCertHolder.mTab.postOperationUpdate(ok.booleanValue(), this.mCertHolder);
        }
    }

    private static class CertHolder implements Comparable<CertHolder> {
        private final TrustedCertificateAdapterCommons mAdapter;
        private final String mAlias;
        private boolean mDeleted;
        public int mProfileId;
        private final IKeyChainService mService;
        private final SslCertificate mSslCert;
        private final String mSubjectPrimary;
        private final String mSubjectSecondary;
        private final Tab mTab;
        private final X509Certificate mX509Cert;

        private CertHolder(IKeyChainService service, TrustedCertificateAdapterCommons adapter, Tab tab, String alias, X509Certificate x509Cert, int profileId) {
            this.mProfileId = profileId;
            this.mService = service;
            this.mAdapter = adapter;
            this.mTab = tab;
            this.mAlias = alias;
            this.mX509Cert = x509Cert;
            this.mSslCert = new SslCertificate(x509Cert);
            String cn = this.mSslCert.getIssuedTo().getCName();
            String o = this.mSslCert.getIssuedTo().getOName();
            String ou = this.mSslCert.getIssuedTo().getUName();
            if (o.isEmpty()) {
                if (cn.isEmpty()) {
                    this.mSubjectPrimary = this.mSslCert.getIssuedTo().getDName();
                    this.mSubjectSecondary = "";
                } else {
                    this.mSubjectPrimary = cn;
                    this.mSubjectSecondary = "";
                }
            } else if (cn.isEmpty()) {
                this.mSubjectPrimary = o;
                this.mSubjectSecondary = ou;
            } else {
                this.mSubjectPrimary = o;
                this.mSubjectSecondary = cn;
            }
            try {
                this.mDeleted = this.mTab.deleted(this.mService, this.mAlias);
            } catch (RemoteException e) {
                Log.e("TrustedCredentialsSettings", "Remote exception while checking if alias " + this.mAlias + " is deleted.", e);
                this.mDeleted = false;
            }
        }

        public int compareTo(CertHolder o) {
            int primary = this.mSubjectPrimary.compareToIgnoreCase(o.mSubjectPrimary);
            return primary != 0 ? primary : this.mSubjectSecondary.compareToIgnoreCase(o.mSubjectSecondary);
        }

        public boolean equals(Object o) {
            if (!(o instanceof CertHolder)) {
                return false;
            }
            return this.mAlias.equals(((CertHolder) o).mAlias);
        }

        public int hashCode() {
            return this.mAlias.hashCode();
        }
    }

    private enum Tab {
        SYSTEM("system", R.string.trusted_credentials_system_tab, R.id.system_tab, R.id.system_progress, R.id.system_list, R.id.system_expandable_list, true),
        USER("user", R.string.trusted_credentials_user_tab, R.id.user_tab, R.id.user_progress, R.id.user_list, R.id.user_expandable_list, false);
        
        private final boolean mCheckbox;
        private final int mExpandableList;
        private final int mLabel;
        private final int mList;
        private final int mProgress;
        private final String mTag;
        private final int mView;

        private Tab(String tag, int label, int view, int progress, int list, int expandableList, boolean checkbox) {
            this.mTag = tag;
            this.mLabel = label;
            this.mView = view;
            this.mProgress = progress;
            this.mList = list;
            this.mExpandableList = expandableList;
            this.mCheckbox = checkbox;
        }

        private List<ParcelableString> getAliases(IKeyChainService service) throws RemoteException {
            switch (AnonymousClass6.$SwitchMap$com$android$settings$TrustedCredentialsSettings$Tab[ordinal()]) {
                case 1:
                    return service.getSystemCaAliases().getList();
                case 2:
                    return service.getUserCaAliases().getList();
                default:
                    throw new AssertionError();
            }
        }

        private boolean deleted(IKeyChainService service, String alias) throws RemoteException {
            switch (AnonymousClass6.$SwitchMap$com$android$settings$TrustedCredentialsSettings$Tab[ordinal()]) {
                case 1:
                    if (service.containsCaAlias(alias)) {
                        return false;
                    }
                    return true;
                case 2:
                    return false;
                default:
                    throw new AssertionError();
            }
        }

        private int getButtonLabel(CertHolder certHolder) {
            switch (AnonymousClass6.$SwitchMap$com$android$settings$TrustedCredentialsSettings$Tab[ordinal()]) {
                case 1:
                    if (certHolder.mDeleted) {
                        return R.string.trusted_credentials_enable_label;
                    }
                    return R.string.trusted_credentials_disable_label;
                case 2:
                    return R.string.trusted_credentials_remove_label;
                default:
                    throw new AssertionError();
            }
        }

        private int getButtonConfirmation(CertHolder certHolder) {
            switch (AnonymousClass6.$SwitchMap$com$android$settings$TrustedCredentialsSettings$Tab[ordinal()]) {
                case 1:
                    if (certHolder.mDeleted) {
                        return R.string.trusted_credentials_enable_confirmation;
                    }
                    return R.string.trusted_credentials_disable_confirmation;
                case 2:
                    return R.string.trusted_credentials_remove_confirmation;
                default:
                    throw new AssertionError();
            }
        }

        private void postOperationUpdate(boolean ok, CertHolder certHolder) {
            if (ok) {
                if (certHolder.mTab.mCheckbox) {
                    certHolder.mDeleted = !certHolder.mDeleted;
                } else {
                    certHolder.mAdapter.remove(certHolder);
                }
                certHolder.mAdapter.notifyDataSetChanged();
                return;
            }
            certHolder.mAdapter.load();
        }
    }

    private interface TrustedCertificateAdapterCommons {
        int getListViewId(Tab tab);

        void load();

        void notifyDataSetChanged();

        void remove(CertHolder certHolder);
    }

    private class TrustedCertificateAdapter extends BaseAdapter implements TrustedCertificateAdapterCommons {
        private final AdapterData mData;

        private TrustedCertificateAdapter(Tab tab) {
            this.mData = new AdapterData(tab, this);
            load();
        }

        public void remove(CertHolder certHolder) {
            this.mData.remove(certHolder);
        }

        public int getListViewId(Tab tab) {
            return tab.mList;
        }

        public void load() {
            AdapterData adapterData = this.mData;
            adapterData.getClass();
            new AliasLoader().execute(new Void[0]);
        }

        public int getCount() {
            List<CertHolder> certHolders = (List) this.mData.mCertHoldersByUserId.valueAt(0);
            if (certHolders != null) {
                return certHolders.size();
            }
            return 0;
        }

        public CertHolder getItem(int position) {
            return (CertHolder) ((List) this.mData.mCertHoldersByUserId.valueAt(0)).get(position);
        }

        public long getItemId(int position) {
            return (long) position;
        }

        public View getView(int position, View view, ViewGroup parent) {
            return TrustedCredentialsSettings.this.getViewForCertificate(getItem(position), this.mData.mTab, view, parent);
        }
    }

    private class TrustedCertificateExpandableAdapter extends BaseExpandableListAdapter implements TrustedCertificateAdapterCommons {
        private AdapterData mData;

        private TrustedCertificateExpandableAdapter(Tab tab) {
            this.mData = new AdapterData(tab, this);
            load();
        }

        public void remove(CertHolder certHolder) {
            this.mData.remove(certHolder);
        }

        public int getGroupCount() {
            return this.mData.mCertHoldersByUserId.size();
        }

        public int getChildrenCount(int groupPosition) {
            List<CertHolder> certHolders = (List) this.mData.mCertHoldersByUserId.valueAt(groupPosition);
            if (certHolders != null) {
                return certHolders.size();
            }
            return 0;
        }

        public UserHandle getGroup(int groupPosition) {
            return new UserHandle(this.mData.mCertHoldersByUserId.keyAt(groupPosition));
        }

        public CertHolder getChild(int groupPosition, int childPosition) {
            return (CertHolder) ((List) this.mData.mCertHoldersByUserId.valueAt(groupPosition)).get(childPosition);
        }

        public long getGroupId(int groupPosition) {
            return (long) this.mData.mCertHoldersByUserId.keyAt(groupPosition);
        }

        public long getChildId(int groupPosition, int childPosition) {
            return (long) childPosition;
        }

        public boolean hasStableIds() {
            return false;
        }

        public View getGroupView(int groupPosition, boolean isExpanded, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = inflateCategoryHeader((LayoutInflater) TrustedCredentialsSettings.this.getActivity().getSystemService("layout_inflater"), parent);
            }
            TextView title = (TextView) convertView.findViewById(16908310);
            if (TrustedCredentialsSettings.this.mUserManager.getUserInfo(getGroup(groupPosition).getIdentifier()).isManagedProfile()) {
                title.setText(R.string.category_work);
            } else {
                title.setText(R.string.category_personal);
            }
            title.setTextAlignment(6);
            return convertView;
        }

        public View getChildView(int groupPosition, int childPosition, boolean isLastChild, View convertView, ViewGroup parent) {
            return TrustedCredentialsSettings.this.getViewForCertificate(getChild(groupPosition, childPosition), this.mData.mTab, convertView, parent);
        }

        public boolean isChildSelectable(int groupPosition, int childPosition) {
            return true;
        }

        public void load() {
            AdapterData adapterData = this.mData;
            adapterData.getClass();
            new AliasLoader().execute(new Void[0]);
        }

        public int getListViewId(Tab tab) {
            return tab.mExpandableList;
        }

        private View inflateCategoryHeader(LayoutInflater inflater, ViewGroup parent) {
            return inflater.inflate(inflater.getContext().obtainStyledAttributes(null, R.styleable.Preference, 16842892, 0).getResourceId(3, 0), parent, false);
        }
    }

    private static class ViewHolder {
        private CheckBox mCheckBox;
        private TextView mSubjectPrimaryView;
        private TextView mSubjectSecondaryView;

        private ViewHolder() {
        }
    }

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.mUserManager = (UserManager) getActivity().getSystemService("user");
    }

    public View onCreateView(LayoutInflater inflater, ViewGroup parent, Bundle savedInstanceState) {
        this.mTabHost = (TabHost) inflater.inflate(R.layout.trusted_credentials, parent, false);
        this.mTabHost.setup();
        addTab(Tab.SYSTEM);
        addTab(Tab.USER);
        if (getActivity().getIntent() != null && "com.android.settings.TRUSTED_CREDENTIALS_USER".equals(getActivity().getIntent().getAction())) {
            this.mTabHost.setCurrentTabByTag(Tab.USER.mTag);
        }
        return this.mTabHost;
    }

    public void onDestroy() {
        closeKeyChainConnections();
        super.onDestroy();
    }

    private void closeKeyChainConnections() {
        int n = this.mKeyChainConnectionByProfileId.size();
        for (int i = 0; i < n; i++) {
            ((KeyChainConnection) this.mKeyChainConnectionByProfileId.valueAt(i)).close();
        }
        this.mKeyChainConnectionByProfileId.clear();
    }

    private void addTab(Tab tab) {
        this.mTabHost.addTab(this.mTabHost.newTabSpec(tab.mTag).setIndicator(getActivity().getString(tab.mLabel)).setContent(tab.mView));
        if (this.mUserManager.getUserProfiles().size() > 1) {
            ExpandableListView lv = (ExpandableListView) this.mTabHost.findViewById(tab.mExpandableList);
            final TrustedCertificateExpandableAdapter adapter = new TrustedCertificateExpandableAdapter(tab);
            lv.setAdapter(adapter);
            lv.setOnChildClickListener(new OnChildClickListener() {
                public boolean onChildClick(ExpandableListView parent, View v, int groupPosition, int childPosition, long id) {
                    TrustedCredentialsSettings.this.showCertDialog(adapter.getChild(groupPosition, childPosition));
                    return true;
                }
            });
            return;
        }
        ListView lv2 = (ListView) this.mTabHost.findViewById(tab.mList);
        final TrustedCertificateAdapter adapter2 = new TrustedCertificateAdapter(tab);
        lv2.setAdapter(adapter2);
        lv2.setOnItemClickListener(new OnItemClickListener() {
            public void onItemClick(AdapterView<?> adapterView, View view, int pos, long id) {
                TrustedCredentialsSettings.this.showCertDialog(adapter2.getItem(pos));
            }
        });
    }

    private View getViewForCertificate(CertHolder certHolder, Tab mTab, View convertView, ViewGroup parent) {
        ViewHolder holder;
        if (convertView == null) {
            convertView = LayoutInflater.from(getActivity()).inflate(R.layout.trusted_credential, parent, false);
            holder = new ViewHolder();
            holder.mSubjectPrimaryView = (TextView) convertView.findViewById(R.id.trusted_credential_subject_primary);
            holder.mSubjectSecondaryView = (TextView) convertView.findViewById(R.id.trusted_credential_subject_secondary);
            holder.mCheckBox = (CheckBox) convertView.findViewById(R.id.trusted_credential_status);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }
        holder.mSubjectPrimaryView.setText(certHolder.mSubjectPrimary);
        holder.mSubjectSecondaryView.setText(certHolder.mSubjectSecondary);
        if (mTab.mCheckbox) {
            boolean z;
            CheckBox access$2700 = holder.mCheckBox;
            if (certHolder.mDeleted) {
                z = false;
            } else {
                z = true;
            }
            access$2700.setChecked(z);
            holder.mCheckBox.setVisibility(0);
        }
        return convertView;
    }

    private void showCertDialog(final CertHolder certHolder) {
        Builder builder = new Builder(getActivity());
        builder.setTitle(17040800);
        final ArrayList<View> views = new ArrayList();
        ArrayList<String> titles = new ArrayList();
        addCertChain(certHolder, views, titles);
        ArrayAdapter<String> arrayAdapter = new ArrayAdapter(getActivity(), 17367048, titles);
        arrayAdapter.setDropDownViewResource(17367049);
        Spinner spinner = new Spinner(getActivity());
        spinner.setAdapter(arrayAdapter);
        spinner.setOnItemSelectedListener(new OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> adapterView, View view, int position, long id) {
                int i = 0;
                while (i < views.size()) {
                    ((View) views.get(i)).setVisibility(i == position ? 0 : 8);
                    i++;
                }
            }

            public void onNothingSelected(AdapterView<?> adapterView) {
            }
        });
        LinearLayout container = new LinearLayout(getActivity());
        container.setOrientation(1);
        container.addView(spinner);
        for (int i = 0; i < views.size(); i++) {
            View certificateView = (View) views.get(i);
            if (i != 0) {
                certificateView.setVisibility(8);
            }
            container.addView(certificateView);
        }
        builder.setView(container);
        builder.setPositiveButton(17039370, new OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                dialog.dismiss();
            }
        });
        final Dialog certDialog = builder.create();
        ViewGroup body = (ViewGroup) container.findViewById(16909148);
        Button removeButton = (Button) LayoutInflater.from(getActivity()).inflate(R.layout.trusted_credential_details, body, false);
        if (!this.mUserManager.hasUserRestriction("no_config_credentials")) {
            body.addView(removeButton);
        }
        removeButton.setText(certHolder.mTab.getButtonLabel(certHolder));
        removeButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Builder builder = new Builder(TrustedCredentialsSettings.this.getActivity());
                builder.setMessage(certHolder.mTab.getButtonConfirmation(certHolder));
                builder.setPositiveButton(17039379, new OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        new AliasOperation(certHolder).execute(new Void[0]);
                        dialog.dismiss();
                        certDialog.dismiss();
                    }
                });
                builder.setNegativeButton(17039369, new OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.cancel();
                    }
                });
                builder.create().show();
            }
        });
        certDialog.show();
    }

    private void addCertChain(CertHolder certHolder, ArrayList<View> views, ArrayList<String> titles) {
        RemoteException ex;
        List<X509Certificate> list;
        try {
            IKeyChainService service = ((KeyChainConnection) this.mKeyChainConnectionByProfileId.get(certHolder.mProfileId)).getService();
            List<String> chain = service.getCaCertificateChainAliases(certHolder.mAlias, true);
            int n = chain.size();
            List<X509Certificate> certificates = new ArrayList(n);
            int i = 0;
            while (i < n) {
                try {
                    certificates.add(KeyChain.toCertificate(service.getEncodedCaCertificate((String) chain.get(i), true)));
                    i++;
                } catch (RemoteException e) {
                    ex = e;
                    list = certificates;
                }
            }
            for (X509Certificate certificate : certificates) {
                addCertDetails(certificate, views, titles);
            }
            list = certificates;
        } catch (RemoteException e2) {
            ex = e2;
            Log.e("TrustedCredentialsSettings", "RemoteException while retrieving certificate chain for root " + certHolder.mAlias, ex);
        }
    }

    private void addCertDetails(X509Certificate certificate, ArrayList<View> views, ArrayList<String> titles) {
        SslCertificate sslCert = new SslCertificate(certificate);
        views.add(sslCert.inflateCertificateView(getActivity()));
        titles.add(sslCert.getIssuedTo().getCName());
    }
}
