package com.android.settings;

import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.net.NetworkScoreManager;
import android.net.NetworkScorerAppManager;
import android.net.NetworkScorerAppManager.NetworkScorerAppData;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import com.android.internal.app.AlertActivity;
import com.android.internal.app.AlertController.AlertParams;

public final class ActiveNetworkScorerDialog extends AlertActivity implements OnClickListener {
    private String mNewPackageName;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.mNewPackageName = getIntent().getStringExtra("packageName");
        if (!buildDialog()) {
            finish();
        }
    }

    public void onClick(DialogInterface dialog, int which) {
        switch (which) {
            case -1:
                if (((NetworkScoreManager) getSystemService("network_score")).setActiveScorer(this.mNewPackageName)) {
                    setResult(-1);
                    return;
                }
                return;
            default:
                return;
        }
    }

    private boolean buildDialog() {
        NetworkScorerAppData newScorer = NetworkScorerAppManager.getScorer(this, this.mNewPackageName);
        if (newScorer == null) {
            Log.e("ActiveNetScorerDlg", "New package " + this.mNewPackageName + " is not a valid scorer.");
            return false;
        }
        NetworkScorerAppData oldScorer = NetworkScorerAppManager.getActiveScorer(this);
        if (oldScorer == null || !TextUtils.equals(oldScorer.mPackageName, this.mNewPackageName)) {
            CharSequence newName = newScorer.mScorerName;
            AlertParams p = this.mAlertParams;
            p.mTitle = getString(R.string.network_scorer_change_active_dialog_title);
            if (oldScorer != null) {
                p.mMessage = getString(R.string.network_scorer_change_active_dialog_text, new Object[]{newName, oldScorer.mScorerName});
            } else {
                p.mMessage = getString(R.string.network_scorer_change_active_no_previous_dialog_text, new Object[]{newName});
            }
            p.mPositiveButtonText = getString(R.string.yes);
            p.mNegativeButtonText = getString(R.string.no);
            p.mPositiveButtonListener = this;
            p.mNegativeButtonListener = this;
            setupAlert();
            return true;
        }
        Log.i("ActiveNetScorerDlg", "New package " + this.mNewPackageName + " is already the active scorer.");
        setResult(-1);
        return false;
    }
}
