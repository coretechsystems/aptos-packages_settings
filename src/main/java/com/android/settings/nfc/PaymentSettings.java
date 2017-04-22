package com.android.settings.nfc;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceScreen;
import android.provider.Settings.Secure;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.TextView;
import com.android.internal.content.PackageMonitor;
import com.android.settings.HelpUtils;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.nfc.PaymentBackend.PaymentAppInfo;
import java.util.List;

public class PaymentSettings extends SettingsPreferenceFragment implements OnPreferenceChangeListener, OnClickListener {
    private final Handler mHandler = new Handler() {
        public void dispatchMessage(Message msg) {
            PaymentSettings.this.refresh();
        }
    };
    private LayoutInflater mInflater;
    private PaymentBackend mPaymentBackend;
    private final PackageMonitor mSettingsPackageMonitor = new SettingsPackageMonitor();

    public static class PaymentAppPreference extends Preference {
        private final PaymentAppInfo appInfo;
        private final OnClickListener listener;

        public PaymentAppPreference(Context context, PaymentAppInfo appInfo, OnClickListener listener) {
            super(context);
            setLayoutResource(R.layout.nfc_payment_option);
            this.appInfo = appInfo;
            this.listener = listener;
        }

        protected void onBindView(View view) {
            super.onBindView(view);
            RadioButton radioButton = (RadioButton) view.findViewById(16908313);
            radioButton.setChecked(this.appInfo.isDefault);
            radioButton.setOnClickListener(this.listener);
            radioButton.setTag(this.appInfo);
            ImageView banner = (ImageView) view.findViewById(R.id.banner);
            banner.setImageDrawable(this.appInfo.banner);
            banner.setOnClickListener(this.listener);
            banner.setTag(this.appInfo);
        }
    }

    private class SettingsPackageMonitor extends PackageMonitor {
        private SettingsPackageMonitor() {
        }

        public void onPackageAdded(String packageName, int uid) {
            PaymentSettings.this.mHandler.obtainMessage().sendToTarget();
        }

        public void onPackageAppeared(String packageName, int reason) {
            PaymentSettings.this.mHandler.obtainMessage().sendToTarget();
        }

        public void onPackageDisappeared(String packageName, int reason) {
            PaymentSettings.this.mHandler.obtainMessage().sendToTarget();
        }

        public void onPackageRemoved(String packageName, int uid) {
            PaymentSettings.this.mHandler.obtainMessage().sendToTarget();
        }
    }

    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        this.mPaymentBackend = new PaymentBackend(getActivity());
        this.mInflater = (LayoutInflater) getSystemService("layout_inflater");
        setHasOptionsMenu(true);
    }

    public void refresh() {
        PreferenceScreen screen = getPreferenceManager().createPreferenceScreen(getActivity());
        List<PaymentAppInfo> appInfos = this.mPaymentBackend.getPaymentAppInfos();
        if (appInfos != null && appInfos.size() > 0) {
            for (PaymentAppInfo appInfo : appInfos) {
                PaymentAppPreference preference = new PaymentAppPreference(getActivity(), appInfo, this);
                preference.setTitle(appInfo.caption);
                if (appInfo.banner != null) {
                    screen.addPreference(preference);
                } else {
                    Log.e("PaymentSettings", "Couldn't load banner drawable of service " + appInfo.componentName);
                }
            }
        }
        TextView emptyText = (TextView) getView().findViewById(R.id.nfc_payment_empty_text);
        TextView learnMore = (TextView) getView().findViewById(R.id.nfc_payment_learn_more);
        ImageView emptyImage = (ImageView) getView().findViewById(R.id.nfc_payment_tap_image);
        if (screen.getPreferenceCount() == 0) {
            emptyText.setVisibility(0);
            learnMore.setVisibility(0);
            emptyImage.setVisibility(0);
            getListView().setVisibility(8);
        } else {
            CheckBoxPreference foreground = new CheckBoxPreference(getActivity());
            boolean foregroundMode = this.mPaymentBackend.isForegroundMode();
            foreground.setPersistent(false);
            foreground.setTitle(getString(R.string.nfc_payment_favor_foreground));
            foreground.setChecked(foregroundMode);
            foreground.setOnPreferenceChangeListener(this);
            screen.addPreference(foreground);
            emptyText.setVisibility(8);
            learnMore.setVisibility(8);
            emptyImage.setVisibility(8);
            getListView().setVisibility(0);
        }
        setPreferenceScreen(screen);
    }

    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        View v = this.mInflater.inflate(R.layout.nfc_payment, container, false);
        ((TextView) v.findViewById(R.id.nfc_payment_learn_more)).setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                String helpUrl = PaymentSettings.this.getResources().getString(R.string.help_url_nfc_payment);
                if (TextUtils.isEmpty(helpUrl)) {
                    Log.e("PaymentSettings", "Help url not set.");
                    return;
                }
                Intent intent = new Intent("android.intent.action.VIEW", HelpUtils.uriWithAddedParameters(PaymentSettings.this.getActivity(), Uri.parse(helpUrl)));
                intent.setFlags(276824064);
                PaymentSettings.this.startActivity(intent);
            }
        });
        return v;
    }

    public void onClick(View v) {
        if (v.getTag() instanceof PaymentAppInfo) {
            PaymentAppInfo appInfo = (PaymentAppInfo) v.getTag();
            if (appInfo.componentName != null) {
                this.mPaymentBackend.setDefaultPaymentApp(appInfo.componentName);
            }
            refresh();
        }
    }

    public void onResume() {
        super.onResume();
        this.mSettingsPackageMonitor.register(getActivity(), getActivity().getMainLooper(), false);
        refresh();
    }

    public void onPause() {
        this.mSettingsPackageMonitor.unregister();
        super.onPause();
    }

    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        String searchUri = Secure.getString(getContentResolver(), "payment_service_search_uri");
        if (!TextUtils.isEmpty(searchUri)) {
            MenuItem menuItem = menu.add(R.string.nfc_payment_menu_item_add_service);
            menuItem.setShowAsActionFlags(1);
            menuItem.setIntent(new Intent("android.intent.action.VIEW", Uri.parse(searchUri)));
        }
    }

    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (!(preference instanceof CheckBoxPreference)) {
            return false;
        }
        this.mPaymentBackend.setForegroundMode(((Boolean) newValue).booleanValue());
        return true;
    }
}
