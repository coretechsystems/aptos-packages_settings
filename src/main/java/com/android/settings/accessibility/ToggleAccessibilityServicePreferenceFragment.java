package com.android.settings.accessibility;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.accessibilityservice.AccessibilityServiceInfo.CapabilityInfo;
import android.app.AlertDialog.Builder;
import android.app.Dialog;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings.Global;
import android.provider.Settings.Secure;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.accessibility.AccessibilityManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.android.internal.widget.LockPatternUtils;
import com.android.settings.ConfirmDeviceCredentialActivity;
import com.android.settings.R;
import com.android.settings.widget.ToggleSwitch;
import com.android.settings.widget.ToggleSwitch.OnBeforeCheckedChangeListener;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ToggleAccessibilityServicePreferenceFragment extends ToggleFeaturePreferenceFragment implements OnClickListener {
    private ComponentName mComponentName;
    private LockPatternUtils mLockPatternUtils;
    private final SettingsContentObserver mSettingsContentObserver = new SettingsContentObserver(new Handler()) {
        public void onChange(boolean selfChange, Uri uri) {
            ToggleAccessibilityServicePreferenceFragment.this.mSwitchBar.setCheckedInternal(Secure.getString(ToggleAccessibilityServicePreferenceFragment.this.getContentResolver(), "enabled_accessibility_services").contains(ToggleAccessibilityServicePreferenceFragment.this.mComponentName.flattenToString()));
        }
    };
    private int mShownDialogId;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.mLockPatternUtils = new LockPatternUtils(getActivity());
    }

    public void onResume() {
        this.mSettingsContentObserver.register(getContentResolver());
        super.onResume();
    }

    public void onPause() {
        this.mSettingsContentObserver.unregister(getContentResolver());
        super.onPause();
    }

    public void onPreferenceToggled(String preferenceKey, boolean enabled) {
        Set<ComponentName> enabledServices = AccessibilityUtils.getEnabledServicesFromSettings(getActivity());
        if (enabledServices == Collections.emptySet()) {
            enabledServices = new HashSet();
        }
        ComponentName toggledService = ComponentName.unflattenFromString(preferenceKey);
        boolean accessibilityEnabled = false;
        if (!enabled) {
            enabledServices.remove(toggledService);
            Set<ComponentName> installedServices = AccessibilitySettings.sInstalledServices;
            for (ComponentName enabledService : enabledServices) {
                if (installedServices.contains(enabledService)) {
                    accessibilityEnabled = true;
                    break;
                }
            }
        }
        enabledServices.add(toggledService);
        accessibilityEnabled = true;
        StringBuilder enabledServicesBuilder = new StringBuilder();
        for (ComponentName enabledService2 : enabledServices) {
            enabledServicesBuilder.append(enabledService2.flattenToString());
            enabledServicesBuilder.append(':');
        }
        int enabledServicesBuilderLength = enabledServicesBuilder.length();
        if (enabledServicesBuilderLength > 0) {
            enabledServicesBuilder.deleteCharAt(enabledServicesBuilderLength - 1);
        }
        Secure.putString(getContentResolver(), "enabled_accessibility_services", enabledServicesBuilder.toString());
        Secure.putInt(getContentResolver(), "accessibility_enabled", accessibilityEnabled ? 1 : 0);
    }

    private AccessibilityServiceInfo getAccessibilityServiceInfo() {
        List<AccessibilityServiceInfo> serviceInfos = AccessibilityManager.getInstance(getActivity()).getInstalledAccessibilityServiceList();
        int serviceInfoCount = serviceInfos.size();
        for (int i = 0; i < serviceInfoCount; i++) {
            AccessibilityServiceInfo serviceInfo = (AccessibilityServiceInfo) serviceInfos.get(i);
            ResolveInfo resolveInfo = serviceInfo.getResolveInfo();
            if (this.mComponentName.getPackageName().equals(resolveInfo.serviceInfo.packageName) && this.mComponentName.getClassName().equals(resolveInfo.serviceInfo.name)) {
                return serviceInfo;
            }
        }
        return null;
    }

    public Dialog onCreateDialog(int dialogId) {
        AccessibilityServiceInfo info;
        switch (dialogId) {
            case 1:
                this.mShownDialogId = 1;
                info = getAccessibilityServiceInfo();
                if (info == null) {
                    return null;
                }
                Dialog ad = new Builder(getActivity()).setTitle(getString(R.string.enable_service_title, new Object[]{info.getResolveInfo().loadLabel(getPackageManager())})).setView(createEnableDialogContentView(info)).setCancelable(true).setPositiveButton(17039370, this).setNegativeButton(17039360, this).create();
                ad.create();
                ad.getButton(-1).setFilterTouchesWhenObscured(true);
                return ad;
            case 2:
                this.mShownDialogId = 2;
                if (getAccessibilityServiceInfo() == null) {
                    return null;
                }
                return new Builder(getActivity()).setTitle(getString(R.string.disable_service_title, new Object[]{info.getResolveInfo().loadLabel(getPackageManager())})).setMessage(getString(R.string.disable_service_message, new Object[]{info.getResolveInfo().loadLabel(getPackageManager())})).setCancelable(true).setPositiveButton(17039370, this).setNegativeButton(17039360, this).create();
            default:
                throw new IllegalArgumentException();
        }
    }

    private View createEnableDialogContentView(AccessibilityServiceInfo info) {
        LayoutInflater inflater = (LayoutInflater) getSystemService("layout_inflater");
        View content = inflater.inflate(R.layout.enable_accessibility_service_dialog_content, null);
        TextView encryptionWarningView = (TextView) content.findViewById(R.id.encryption_warning);
        if (LockPatternUtils.isDeviceEncrypted()) {
            encryptionWarningView.setText(getString(R.string.enable_service_encryption_warning, new Object[]{info.getResolveInfo().loadLabel(getPackageManager())}));
            encryptionWarningView.setVisibility(0);
        } else {
            encryptionWarningView.setVisibility(8);
        }
        ((TextView) content.findViewById(R.id.capabilities_header)).setText(getString(R.string.capabilities_list_title, new Object[]{info.getResolveInfo().loadLabel(getPackageManager())}));
        LinearLayout capabilitiesView = (LinearLayout) content.findViewById(R.id.capabilities);
        View capabilityView = inflater.inflate(17367092, null);
        ((ImageView) capabilityView.findViewById(16909011)).setImageDrawable(getResources().getDrawable(17302590));
        ((TextView) capabilityView.findViewById(16909015)).setText(getString(R.string.capability_title_receiveAccessibilityEvents));
        ((TextView) capabilityView.findViewById(16909016)).setText(getString(R.string.capability_desc_receiveAccessibilityEvents));
        List<CapabilityInfo> capabilities = info.getCapabilityInfos();
        capabilitiesView.addView(capabilityView);
        int capabilityCount = capabilities.size();
        for (int i = 0; i < capabilityCount; i++) {
            CapabilityInfo capability = (CapabilityInfo) capabilities.get(i);
            capabilityView = inflater.inflate(17367092, null);
            ((ImageView) capabilityView.findViewById(16909011)).setImageDrawable(getResources().getDrawable(17302590));
            ((TextView) capabilityView.findViewById(16909015)).setText(getString(capability.titleResId));
            ((TextView) capabilityView.findViewById(16909016)).setText(getString(capability.descResId));
            capabilitiesView.addView(capabilityView);
        }
        return content;
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode != 1) {
            return;
        }
        if (resultCode == -1) {
            handleConfirmServiceEnabled(true);
            if (LockPatternUtils.isDeviceEncrypted()) {
                this.mLockPatternUtils.clearEncryptionPassword();
                Global.putInt(getContentResolver(), "require_password_to_decrypt", 0);
                return;
            }
            return;
        }
        handleConfirmServiceEnabled(false);
    }

    public void onClick(DialogInterface dialog, int which) {
        boolean checked = true;
        switch (which) {
            case -2:
                if (this.mShownDialogId != 2) {
                    checked = false;
                }
                handleConfirmServiceEnabled(checked);
                return;
            case -1:
                if (this.mShownDialogId != 1) {
                    handleConfirmServiceEnabled(false);
                    return;
                } else if (LockPatternUtils.isDeviceEncrypted()) {
                    startActivityForResult(ConfirmDeviceCredentialActivity.createIntent(createConfirmCredentialReasonMessage(), null), 1);
                    return;
                } else {
                    handleConfirmServiceEnabled(true);
                    return;
                }
            default:
                throw new IllegalArgumentException();
        }
    }

    private void handleConfirmServiceEnabled(boolean confirmed) {
        this.mSwitchBar.setCheckedInternal(confirmed);
        getArguments().putBoolean("checked", confirmed);
        onPreferenceToggled(this.mPreferenceKey, confirmed);
    }

    private String createConfirmCredentialReasonMessage() {
        int resId = R.string.enable_service_password_reason;
        switch (this.mLockPatternUtils.getKeyguardStoredPasswordQuality()) {
            case 65536:
                resId = R.string.enable_service_pattern_reason;
                break;
            case 131072:
            case 196608:
                resId = R.string.enable_service_pin_reason;
                break;
        }
        return getString(resId, new Object[]{getAccessibilityServiceInfo().getResolveInfo().loadLabel(getPackageManager())});
    }

    protected void onInstallSwitchBarToggleSwitch() {
        super.onInstallSwitchBarToggleSwitch();
        this.mToggleSwitch.setOnBeforeCheckedChangeListener(new OnBeforeCheckedChangeListener() {
            public boolean onBeforeCheckedChanged(ToggleSwitch toggleSwitch, boolean checked) {
                if (checked) {
                    ToggleAccessibilityServicePreferenceFragment.this.mSwitchBar.setCheckedInternal(false);
                    ToggleAccessibilityServicePreferenceFragment.this.getArguments().putBoolean("checked", false);
                    ToggleAccessibilityServicePreferenceFragment.this.showDialog(1);
                } else {
                    ToggleAccessibilityServicePreferenceFragment.this.mSwitchBar.setCheckedInternal(true);
                    ToggleAccessibilityServicePreferenceFragment.this.getArguments().putBoolean("checked", true);
                    ToggleAccessibilityServicePreferenceFragment.this.showDialog(2);
                }
                return true;
            }
        });
    }

    protected void onProcessArguments(Bundle arguments) {
        super.onProcessArguments(arguments);
        String settingsTitle = arguments.getString("settings_title");
        String settingsComponentName = arguments.getString("settings_component_name");
        if (!(TextUtils.isEmpty(settingsTitle) || TextUtils.isEmpty(settingsComponentName))) {
            Intent settingsIntent = new Intent("android.intent.action.MAIN").setComponent(ComponentName.unflattenFromString(settingsComponentName.toString()));
            if (!getPackageManager().queryIntentActivities(settingsIntent, 0).isEmpty()) {
                this.mSettingsTitle = settingsTitle;
                this.mSettingsIntent = settingsIntent;
                setHasOptionsMenu(true);
            }
        }
        this.mComponentName = (ComponentName) arguments.getParcelable("component_name");
    }
}
