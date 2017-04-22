package com.android.settings;

import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Process;
import android.os.UserHandle;
import android.util.Log;
import android.view.LayoutInflater;
import android.widget.CheckBox;
import com.android.internal.app.AlertActivity;
import com.android.internal.app.AlertController.AlertParams;

public class AllowBindAppWidgetActivity extends AlertActivity implements OnClickListener {
    private CheckBox mAlwaysUse;
    private int mAppWidgetId;
    private AppWidgetManager mAppWidgetManager;
    private String mCallingPackage;
    private boolean mClicked;
    private ComponentName mComponentName;
    private UserHandle mProfile;

    public void onClick(DialogInterface dialog, int which) {
        if (which == -1) {
            setResult(0);
            if (!(this.mAppWidgetId == -1 || this.mComponentName == null || this.mCallingPackage == null)) {
                try {
                    if (this.mAppWidgetManager.bindAppWidgetIdIfAllowed(this.mAppWidgetId, this.mProfile, this.mComponentName, null)) {
                        Intent result = new Intent();
                        result.putExtra("appWidgetId", this.mAppWidgetId);
                        setResult(-1, result);
                    }
                } catch (Exception e) {
                    Log.v("BIND_APPWIDGET", "Error binding widget with id " + this.mAppWidgetId + " and component " + this.mComponentName);
                }
                boolean alwaysAllowBind = this.mAlwaysUse.isChecked();
                if (alwaysAllowBind != this.mAppWidgetManager.hasBindAppWidgetPermission(this.mCallingPackage)) {
                    this.mAppWidgetManager.setBindAppWidgetPermission(this.mCallingPackage, alwaysAllowBind);
                }
            }
        }
        finish();
    }

    protected void onPause() {
        if (isDestroyed() && !this.mClicked) {
            setResult(0);
        }
        super.onDestroy();
    }

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = getIntent();
        CharSequence label = "";
        if (intent != null) {
            try {
                this.mAppWidgetId = intent.getIntExtra("appWidgetId", -1);
                this.mProfile = (UserHandle) intent.getParcelableExtra("appWidgetProviderProfile");
                if (this.mProfile == null) {
                    this.mProfile = Process.myUserHandle();
                }
                this.mComponentName = (ComponentName) intent.getParcelableExtra("appWidgetProvider");
                this.mCallingPackage = getCallingPackage();
                PackageManager pm = getPackageManager();
                label = pm.getApplicationLabel(pm.getApplicationInfo(this.mCallingPackage, 0));
            } catch (Exception e) {
                this.mAppWidgetId = -1;
                this.mComponentName = null;
                this.mCallingPackage = null;
                Log.v("BIND_APPWIDGET", "Error getting parameters");
                setResult(0);
                finish();
                return;
            }
        }
        AlertParams ap = this.mAlertParams;
        ap.mTitle = getString(R.string.allow_bind_app_widget_activity_allow_bind_title);
        ap.mMessage = getString(R.string.allow_bind_app_widget_activity_allow_bind, new Object[]{label});
        ap.mPositiveButtonText = getString(R.string.create);
        ap.mNegativeButtonText = getString(17039360);
        ap.mPositiveButtonListener = this;
        ap.mNegativeButtonListener = this;
        ap.mView = ((LayoutInflater) getSystemService("layout_inflater")).inflate(17367087, null);
        this.mAlwaysUse = (CheckBox) ap.mView.findViewById(16909004);
        this.mAlwaysUse.setText(getString(R.string.allow_bind_app_widget_activity_always_allow_bind, new Object[]{label}));
        this.mAlwaysUse.setPadding(this.mAlwaysUse.getPaddingLeft(), this.mAlwaysUse.getPaddingTop(), this.mAlwaysUse.getPaddingRight(), (int) (((float) this.mAlwaysUse.getPaddingBottom()) + getResources().getDimension(R.dimen.bind_app_widget_dialog_checkbox_bottom_padding)));
        this.mAppWidgetManager = AppWidgetManager.getInstance(this);
        this.mAlwaysUse.setChecked(this.mAppWidgetManager.hasBindAppWidgetPermission(this.mCallingPackage, this.mProfile.getIdentifier()));
        setupAlert();
    }
}
