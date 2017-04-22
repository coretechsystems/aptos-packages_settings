package com.android.settings.accounts;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.preference.Preference;

public class ProviderPreference extends Preference {
    private String mAccountType;

    public ProviderPreference(Context context, String accountType, Drawable icon, CharSequence providerName) {
        super(context);
        this.mAccountType = accountType;
        setIcon(icon);
        setPersistent(false);
        setTitle(providerName);
    }

    public String getAccountType() {
        return this.mAccountType;
    }
}
