package com.android.settings.bluetooth;

import android.app.AlertDialog;
import android.bluetooth.BluetoothClass;
import android.content.Context;
import android.content.DialogInterface;
import android.os.UserManager;
import android.preference.Preference;
import android.text.Html;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageView;
import com.android.settings.R;
import com.android.settings.bluetooth.CachedBluetoothDevice.Callback;
import com.android.settings.search.Index;
import com.android.settings.search.SearchIndexableRaw;

public final class BluetoothDevicePreference extends Preference implements OnClickListener, Callback {
    private static int sDimAlpha = Integer.MIN_VALUE;
    private final CachedBluetoothDevice mCachedDevice;
    private AlertDialog mDisconnectDialog;
    private OnClickListener mOnSettingsClickListener;

    public BluetoothDevicePreference(Context context, CachedBluetoothDevice cachedDevice) {
        super(context);
        if (sDimAlpha == Integer.MIN_VALUE) {
            TypedValue outValue = new TypedValue();
            context.getTheme().resolveAttribute(16842803, outValue, true);
            sDimAlpha = (int) (outValue.getFloat() * 255.0f);
        }
        this.mCachedDevice = cachedDevice;
        setLayoutResource(R.layout.preference_bt_icon);
        if (cachedDevice.getBondState() == 12 && !((UserManager) context.getSystemService("user")).hasUserRestriction("no_config_bluetooth")) {
            setWidgetLayoutResource(R.layout.preference_bluetooth);
        }
        this.mCachedDevice.registerCallback(this);
        onDeviceAttributesChanged();
    }

    CachedBluetoothDevice getCachedDevice() {
        return this.mCachedDevice;
    }

    public void setOnSettingsClickListener(OnClickListener listener) {
        this.mOnSettingsClickListener = listener;
    }

    protected void onPrepareForRemoval() {
        super.onPrepareForRemoval();
        this.mCachedDevice.unregisterCallback(this);
        if (this.mDisconnectDialog != null) {
            this.mDisconnectDialog.dismiss();
            this.mDisconnectDialog = null;
        }
    }

    public void onDeviceAttributesChanged() {
        setTitle(this.mCachedDevice.getName());
        int summaryResId = getConnectionSummary();
        if (summaryResId != 0) {
            setSummary(summaryResId);
        } else {
            setSummary(null);
        }
        int iconResId = getBtClassDrawable();
        if (iconResId != 0) {
            setIcon(iconResId);
        }
        setEnabled(!this.mCachedDevice.isBusy());
        notifyHierarchyChanged();
    }

    protected void onBindView(View view) {
        if (findPreferenceInHierarchy("bt_checkbox") != null) {
            setDependency("bt_checkbox");
        }
        if (this.mCachedDevice.getBondState() == 12) {
            ImageView deviceDetails = (ImageView) view.findViewById(R.id.deviceDetails);
            if (deviceDetails != null) {
                deviceDetails.setOnClickListener(this);
                deviceDetails.setTag(this.mCachedDevice);
            }
        }
        super.onBindView(view);
    }

    public void onClick(View v) {
        if (this.mOnSettingsClickListener != null) {
            this.mOnSettingsClickListener.onClick(v);
        }
    }

    public boolean equals(Object o) {
        if (o == null || !(o instanceof BluetoothDevicePreference)) {
            return false;
        }
        return this.mCachedDevice.equals(((BluetoothDevicePreference) o).mCachedDevice);
    }

    public int hashCode() {
        return this.mCachedDevice.hashCode();
    }

    public int compareTo(Preference another) {
        if (another instanceof BluetoothDevicePreference) {
            return this.mCachedDevice.compareTo(((BluetoothDevicePreference) another).mCachedDevice);
        }
        return super.compareTo(another);
    }

    void onClicked() {
        int bondState = this.mCachedDevice.getBondState();
        if (this.mCachedDevice.isConnected()) {
            askDisconnect();
        } else if (bondState == 12) {
            this.mCachedDevice.connect(true);
        } else if (bondState == 10) {
            pair();
        }
    }

    private void askDisconnect() {
        Context context = getContext();
        String name = this.mCachedDevice.getName();
        if (TextUtils.isEmpty(name)) {
            name = context.getString(R.string.bluetooth_device);
        }
        String message = context.getString(R.string.bluetooth_disconnect_all_profiles, new Object[]{name});
        String title = context.getString(R.string.bluetooth_disconnect_title);
        this.mDisconnectDialog = Utils.showDisconnectDialog(context, this.mDisconnectDialog, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                BluetoothDevicePreference.this.mCachedDevice.disconnect();
            }
        }, title, Html.fromHtml(message));
    }

    private void pair() {
        if (this.mCachedDevice.startPairing()) {
            Context context = getContext();
            SearchIndexableRaw data = new SearchIndexableRaw(context);
            data.className = BluetoothSettings.class.getName();
            data.title = this.mCachedDevice.getName();
            data.screenTitle = context.getResources().getString(R.string.bluetooth_settings);
            data.iconResId = R.drawable.ic_settings_bluetooth2;
            data.enabled = true;
            Index.getInstance(context).updateFromSearchIndexableData(data);
            return;
        }
        Utils.showError(getContext(), this.mCachedDevice.getName(), R.string.bluetooth_pairing_error_message);
    }

    private int getConnectionSummary() {
        CachedBluetoothDevice cachedDevice = this.mCachedDevice;
        boolean profileConnected = false;
        boolean a2dpNotConnected = false;
        boolean headsetNotConnected = false;
        for (LocalBluetoothProfile profile : cachedDevice.getProfiles()) {
            int connectionStatus = cachedDevice.getProfileConnectionState(profile);
            switch (connectionStatus) {
                case 0:
                    if (profile.isProfileReady()) {
                        if (!(profile instanceof A2dpProfile)) {
                            if (!(profile instanceof HeadsetProfile)) {
                                break;
                            }
                            headsetNotConnected = true;
                            break;
                        }
                        a2dpNotConnected = true;
                        break;
                    }
                    break;
                case 1:
                case 3:
                    return Utils.getConnectionStateSummary(connectionStatus);
                case 2:
                    profileConnected = true;
                    break;
                default:
                    break;
            }
        }
        if (!profileConnected) {
            switch (cachedDevice.getBondState()) {
                case 11:
                    return R.string.bluetooth_pairing;
                default:
                    return 0;
            }
        } else if (a2dpNotConnected && headsetNotConnected) {
            return R.string.bluetooth_connected_no_headset_no_a2dp;
        } else {
            if (a2dpNotConnected) {
                return R.string.bluetooth_connected_no_a2dp;
            }
            if (headsetNotConnected) {
                return R.string.bluetooth_connected_no_headset;
            }
            return R.string.bluetooth_connected;
        }
    }

    private int getBtClassDrawable() {
        BluetoothClass btClass = this.mCachedDevice.getBtClass();
        if (btClass != null) {
            switch (btClass.getMajorDeviceClass()) {
                case 256:
                    return R.drawable.ic_bt_laptop;
                case 512:
                    return R.drawable.ic_bt_cellphone;
                case 1280:
                    return HidProfile.getHidClassDrawable(btClass);
                case 1536:
                    return R.drawable.ic_bt_imaging;
            }
        }
        Log.w("BluetoothDevicePreference", "mBtClass is null");
        for (LocalBluetoothProfile profile : this.mCachedDevice.getProfiles()) {
            int resId = profile.getDrawableResource(btClass);
            if (resId != 0) {
                return resId;
            }
        }
        if (btClass != null) {
            if (btClass.doesClassMatch(1)) {
                return R.drawable.ic_bt_headphones_a2dp;
            }
            if (btClass.doesClassMatch(0)) {
                return R.drawable.ic_bt_headset_hfp;
            }
        }
        return R.drawable.ic_settings_bluetooth2;
    }
}
