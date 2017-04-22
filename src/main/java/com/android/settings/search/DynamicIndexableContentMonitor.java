package com.android.settings.search;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.database.ContentObserver;
import android.hardware.input.InputManager;
import android.hardware.input.InputManager.InputDeviceListener;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.UserHandle;
import android.print.PrintManager;
import android.printservice.PrintServiceInfo;
import android.provider.UserDictionary.Words;
import android.view.accessibility.AccessibilityManager;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import com.android.internal.content.PackageMonitor;
import com.android.settings.accessibility.AccessibilitySettings;
import com.android.settings.inputmethod.InputMethodAndLanguageSettings;
import com.android.settings.print.PrintSettingsFragment;
import java.util.ArrayList;
import java.util.List;

public final class DynamicIndexableContentMonitor extends PackageMonitor implements InputDeviceListener {
    private final List<String> mAccessibilityServices = new ArrayList();
    private Context mContext;
    private final Handler mHandler = new Handler() {
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case 1:
                    DynamicIndexableContentMonitor.this.handlePackageAvailable(msg.obj);
                    return;
                case 2:
                    DynamicIndexableContentMonitor.this.handlePackageUnavailable((String) msg.obj);
                    return;
                default:
                    return;
            }
        }
    };
    private boolean mHasFeatureIme;
    private boolean mHasFeaturePrinting;
    private final List<String> mImeServices = new ArrayList();
    private final List<String> mPrintServices = new ArrayList();
    private final ContentObserver mUserDictionaryContentObserver = new UserDictionaryContentObserver(this.mHandler);

    private final class UserDictionaryContentObserver extends ContentObserver {
        public UserDictionaryContentObserver(Handler handler) {
            super(handler);
        }

        public void onChange(boolean selfChange, Uri uri) {
            if (Words.CONTENT_URI.equals(uri)) {
                Index.getInstance(DynamicIndexableContentMonitor.this.mContext).updateFromClassNameResource(InputMethodAndLanguageSettings.class.getName(), true, true);
            }
        }
    }

    private static Intent getAccessibilityServiceIntent(String packageName) {
        Intent intent = new Intent("android.accessibilityservice.AccessibilityService");
        intent.setPackage(packageName);
        return intent;
    }

    private static Intent getPrintServiceIntent(String packageName) {
        Intent intent = new Intent("android.printservice.PrintService");
        intent.setPackage(packageName);
        return intent;
    }

    private static Intent getIMEServiceIntent(String packageName) {
        Intent intent = new Intent("android.view.InputMethod");
        intent.setPackage(packageName);
        return intent;
    }

    public void register(Context context) {
        int i;
        this.mContext = context;
        this.mHasFeaturePrinting = this.mContext.getPackageManager().hasSystemFeature("android.software.print");
        this.mHasFeatureIme = this.mContext.getPackageManager().hasSystemFeature("android.software.input_methods");
        List<AccessibilityServiceInfo> accessibilityServices = ((AccessibilityManager) this.mContext.getSystemService("accessibility")).getInstalledAccessibilityServiceList();
        int accessibilityServiceCount = accessibilityServices.size();
        for (i = 0; i < accessibilityServiceCount; i++) {
            ResolveInfo resolveInfo = ((AccessibilityServiceInfo) accessibilityServices.get(i)).getResolveInfo();
            if (!(resolveInfo == null || resolveInfo.serviceInfo == null)) {
                this.mAccessibilityServices.add(resolveInfo.serviceInfo.packageName);
            }
        }
        if (this.mHasFeaturePrinting) {
            List<PrintServiceInfo> printServices = ((PrintManager) this.mContext.getSystemService("print")).getInstalledPrintServices();
            int serviceCount = printServices.size();
            for (i = 0; i < serviceCount; i++) {
                resolveInfo = ((PrintServiceInfo) printServices.get(i)).getResolveInfo();
                if (!(resolveInfo == null || resolveInfo.serviceInfo == null)) {
                    this.mPrintServices.add(resolveInfo.serviceInfo.packageName);
                }
            }
        }
        if (this.mHasFeatureIme) {
            List<InputMethodInfo> inputMethods = ((InputMethodManager) this.mContext.getSystemService("input_method")).getInputMethodList();
            int inputMethodCount = inputMethods.size();
            for (i = 0; i < inputMethodCount; i++) {
                ServiceInfo serviceInfo = ((InputMethodInfo) inputMethods.get(i)).getServiceInfo();
                if (serviceInfo != null) {
                    this.mImeServices.add(serviceInfo.packageName);
                }
            }
            this.mContext.getContentResolver().registerContentObserver(Words.CONTENT_URI, true, this.mUserDictionaryContentObserver);
        }
        ((InputManager) context.getSystemService("input")).registerInputDeviceListener(this, this.mHandler);
        register(context, Looper.getMainLooper(), UserHandle.CURRENT, false);
    }

    public void unregister() {
        super.unregister();
        ((InputManager) this.mContext.getSystemService("input")).unregisterInputDeviceListener(this);
        if (this.mHasFeatureIme) {
            this.mContext.getContentResolver().unregisterContentObserver(this.mUserDictionaryContentObserver);
        }
        this.mAccessibilityServices.clear();
        this.mPrintServices.clear();
        this.mImeServices.clear();
    }

    public void onPackageAppeared(String packageName, int uid) {
        postMessage(1, packageName);
    }

    public void onPackageDisappeared(String packageName, int uid) {
        postMessage(2, packageName);
    }

    public void onPackageModified(String packageName) {
        super.onPackageModified(packageName);
        int state = this.mContext.getPackageManager().getApplicationEnabledSetting(packageName);
        if (state == 0 || state == 1) {
            postMessage(1, packageName);
        } else {
            postMessage(2, packageName);
        }
    }

    public void onInputDeviceAdded(int deviceId) {
        Index.getInstance(this.mContext).updateFromClassNameResource(InputMethodAndLanguageSettings.class.getName(), false, true);
    }

    public void onInputDeviceRemoved(int deviceId) {
        onInputDeviceChanged(deviceId);
    }

    public void onInputDeviceChanged(int deviceId) {
        Index.getInstance(this.mContext).updateFromClassNameResource(InputMethodAndLanguageSettings.class.getName(), true, true);
    }

    private void postMessage(int what, String packageName) {
        this.mHandler.sendMessageDelayed(this.mHandler.obtainMessage(what, packageName), 2000);
    }

    private void handlePackageAvailable(String packageName) {
        if (!this.mAccessibilityServices.contains(packageName)) {
            if (!this.mContext.getPackageManager().queryIntentServices(getAccessibilityServiceIntent(packageName), 0).isEmpty()) {
                this.mAccessibilityServices.add(packageName);
                Index.getInstance(this.mContext).updateFromClassNameResource(AccessibilitySettings.class.getName(), false, true);
            }
        }
        if (this.mHasFeaturePrinting && !this.mPrintServices.contains(packageName)) {
            if (!this.mContext.getPackageManager().queryIntentServices(getPrintServiceIntent(packageName), 0).isEmpty()) {
                this.mPrintServices.add(packageName);
                Index.getInstance(this.mContext).updateFromClassNameResource(PrintSettingsFragment.class.getName(), false, true);
            }
        }
        if (this.mHasFeatureIme && !this.mImeServices.contains(packageName)) {
            if (!this.mContext.getPackageManager().queryIntentServices(getIMEServiceIntent(packageName), 0).isEmpty()) {
                this.mImeServices.add(packageName);
                Index.getInstance(this.mContext).updateFromClassNameResource(InputMethodAndLanguageSettings.class.getName(), false, true);
            }
        }
    }

    private void handlePackageUnavailable(String packageName) {
        int accessibilityIndex = this.mAccessibilityServices.indexOf(packageName);
        if (accessibilityIndex >= 0) {
            this.mAccessibilityServices.remove(accessibilityIndex);
            Index.getInstance(this.mContext).updateFromClassNameResource(AccessibilitySettings.class.getName(), true, true);
        }
        if (this.mHasFeaturePrinting) {
            int printIndex = this.mPrintServices.indexOf(packageName);
            if (printIndex >= 0) {
                this.mPrintServices.remove(printIndex);
                Index.getInstance(this.mContext).updateFromClassNameResource(PrintSettingsFragment.class.getName(), true, true);
            }
        }
        if (this.mHasFeatureIme) {
            int imeIndex = this.mImeServices.indexOf(packageName);
            if (imeIndex >= 0) {
                this.mImeServices.remove(imeIndex);
                Index.getInstance(this.mContext).updateFromClassNameResource(InputMethodAndLanguageSettings.class.getName(), true, true);
            }
        }
    }
}
