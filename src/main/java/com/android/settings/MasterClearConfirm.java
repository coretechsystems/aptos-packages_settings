package com.android.settings;

import android.app.Fragment;
import android.app.ProgressDialog;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.UserManager;
import android.service.persistentdata.PersistentDataBlockManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import com.android.internal.os.storage.ExternalStorageFormatter;

public class MasterClearConfirm extends Fragment {
    private View mContentView;
    private boolean mEraseSdCard;
    private OnClickListener mFinalClickListener = new OnClickListener() {
        public void onClick(View v) {
            if (!Utils.isMonkeyRunning()) {
                final PersistentDataBlockManager pdbManager = (PersistentDataBlockManager) MasterClearConfirm.this.getActivity().getSystemService("persistent_data_block");
                if (pdbManager == null || pdbManager.getOemUnlockEnabled()) {
                    MasterClearConfirm.this.doMasterClear();
                    return;
                }
                final ProgressDialog progressDialog = getProgressDialog();
                progressDialog.show();
                final int oldOrientation = MasterClearConfirm.this.getActivity().getRequestedOrientation();
                MasterClearConfirm.this.getActivity().setRequestedOrientation(14);
                new AsyncTask<Void, Void, Void>() {
                    protected Void doInBackground(Void... params) {
                        pdbManager.wipe();
                        return null;
                    }

                    protected void onPostExecute(Void aVoid) {
                        progressDialog.hide();
                        MasterClearConfirm.this.getActivity().setRequestedOrientation(oldOrientation);
                        MasterClearConfirm.this.doMasterClear();
                    }
                }.execute(new Void[0]);
            }
        }

        private ProgressDialog getProgressDialog() {
            ProgressDialog progressDialog = new ProgressDialog(MasterClearConfirm.this.getActivity());
            progressDialog.setIndeterminate(true);
            progressDialog.setCancelable(false);
            progressDialog.setTitle(MasterClearConfirm.this.getActivity().getString(R.string.master_clear_progress_title));
            progressDialog.setMessage(MasterClearConfirm.this.getActivity().getString(R.string.master_clear_progress_text));
            return progressDialog;
        }
    };

    private void doMasterClear() {
        if (this.mEraseSdCard) {
            Intent intent = new Intent("com.android.internal.os.storage.FORMAT_AND_FACTORY_RESET");
            intent.putExtra("android.intent.extra.REASON", "MasterClearConfirm");
            intent.setComponent(ExternalStorageFormatter.COMPONENT_NAME);
            getActivity().startService(intent);
            return;
        }
        intent = new Intent("android.intent.action.MASTER_CLEAR");
        intent.addFlags(268435456);
        intent.putExtra("android.intent.extra.REASON", "MasterClearConfirm");
        getActivity().sendBroadcast(intent);
    }

    private void establishFinalConfirmationState() {
        this.mContentView.findViewById(R.id.execute_master_clear).setOnClickListener(this.mFinalClickListener);
    }

    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        if (UserManager.get(getActivity()).hasUserRestriction("no_factory_reset")) {
            return inflater.inflate(R.layout.master_clear_disallowed_screen, null);
        }
        this.mContentView = inflater.inflate(R.layout.master_clear_confirm, null);
        establishFinalConfirmationState();
        return this.mContentView;
    }

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle args = getArguments();
        boolean z = args != null && args.getBoolean("erase_sd");
        this.mEraseSdCard = z;
    }
}
