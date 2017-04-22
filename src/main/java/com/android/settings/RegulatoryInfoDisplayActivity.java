package com.android.settings;

import android.app.Activity;
import android.app.AlertDialog.Builder;
import android.content.DialogInterface;
import android.content.DialogInterface.OnDismissListener;
import android.content.res.Resources;
import android.content.res.Resources.NotFoundException;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.SystemProperties;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

public class RegulatoryInfoDisplayActivity extends Activity implements OnDismissListener {
    private final String REGULATORY_INFO_RESOURCE = "regulatory_info";

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Resources resources = getResources();
        if (!resources.getBoolean(R.bool.config_show_regulatory_info)) {
            finish();
        }
        Builder builder = new Builder(this).setTitle(R.string.regulatory_information).setOnDismissListener(this);
        boolean regulatoryInfoDrawableExists = false;
        int resId = getResourceId();
        if (resId != 0) {
            try {
                Drawable d = resources.getDrawable(resId);
                regulatoryInfoDrawableExists = d.getIntrinsicWidth() > 2 && d.getIntrinsicHeight() > 2;
            } catch (NotFoundException e) {
                regulatoryInfoDrawableExists = false;
            }
        }
        CharSequence regulatoryText = resources.getText(R.string.regulatory_info_text);
        if (regulatoryInfoDrawableExists) {
            View view = getLayoutInflater().inflate(R.layout.regulatory_info, null);
            ((ImageView) view.findViewById(R.id.regulatoryInfo)).setImageResource(resId);
            builder.setView(view);
            builder.show();
        } else if (regulatoryText.length() > 0) {
            builder.setMessage(regulatoryText);
            ((TextView) builder.show().findViewById(16908299)).setGravity(17);
        } else {
            finish();
        }
    }

    private int getResourceId() {
        int resId = getResources().getIdentifier("regulatory_info", "drawable", getPackageName());
        String sku = SystemProperties.get("ro.boot.hardware.sku", "");
        if (TextUtils.isEmpty(sku)) {
            return resId;
        }
        int id = getResources().getIdentifier("regulatory_info_" + sku.toLowerCase(), "drawable", getPackageName());
        if (id != 0) {
            return id;
        }
        return resId;
    }

    public void onDismiss(DialogInterface dialog) {
        finish();
    }
}
