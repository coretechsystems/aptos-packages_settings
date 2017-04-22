package com.android.settings.deviceinfo;

import android.app.AlertDialog.Builder;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.IPackageDataObserver.Stub;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.UserManager;
import android.os.storage.IMountService;
import android.os.storage.StorageEventListener;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.preference.Preference;
import android.preference.PreferenceScreen;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.Toast;
import com.android.settings.R;
import com.android.settings.SettingsActivity;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.Utils;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settings.search.Indexable;
import com.android.settings.search.Indexable.SearchIndexProvider;
import com.android.settings.search.SearchIndexableRaw;
import com.google.android.collect.Lists;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class Memory extends SettingsPreferenceFragment implements Indexable {
    public static final SearchIndexProvider SEARCH_INDEX_DATA_PROVIDER = new BaseSearchIndexProvider() {
        public List<SearchIndexableRaw> getRawDataToIndex(Context context, boolean enabled) {
            List<SearchIndexableRaw> result = new ArrayList();
            SearchIndexableRaw data = new SearchIndexableRaw(context);
            data.title = context.getString(R.string.storage_settings);
            data.screenTitle = context.getString(R.string.storage_settings);
            result.add(data);
            data = new SearchIndexableRaw(context);
            data.title = context.getString(R.string.internal_storage);
            data.screenTitle = context.getString(R.string.storage_settings);
            result.add(data);
            data = new SearchIndexableRaw(context);
            for (StorageVolume volume : StorageManager.from(context).getVolumeList()) {
                if (!volume.isEmulated()) {
                    data.title = volume.getDescription(context);
                    data.screenTitle = context.getString(R.string.storage_settings);
                    result.add(data);
                }
            }
            data = new SearchIndexableRaw(context);
            data.title = context.getString(R.string.memory_size);
            data.screenTitle = context.getString(R.string.storage_settings);
            result.add(data);
            data = new SearchIndexableRaw(context);
            data.title = context.getString(R.string.memory_available);
            data.screenTitle = context.getString(R.string.storage_settings);
            result.add(data);
            data = new SearchIndexableRaw(context);
            data.title = context.getString(R.string.memory_apps_usage);
            data.screenTitle = context.getString(R.string.storage_settings);
            result.add(data);
            data = new SearchIndexableRaw(context);
            data.title = context.getString(R.string.memory_dcim_usage);
            data.screenTitle = context.getString(R.string.storage_settings);
            result.add(data);
            data = new SearchIndexableRaw(context);
            data.title = context.getString(R.string.memory_music_usage);
            data.screenTitle = context.getString(R.string.storage_settings);
            result.add(data);
            data = new SearchIndexableRaw(context);
            data.title = context.getString(R.string.memory_downloads_usage);
            data.screenTitle = context.getString(R.string.storage_settings);
            result.add(data);
            data = new SearchIndexableRaw(context);
            data.title = context.getString(R.string.memory_media_cache_usage);
            data.screenTitle = context.getString(R.string.storage_settings);
            result.add(data);
            data = new SearchIndexableRaw(context);
            data.title = context.getString(R.string.memory_media_misc_usage);
            data.screenTitle = context.getString(R.string.storage_settings);
            result.add(data);
            return result;
        }
    };
    private static String sClickedMountPoint;
    private static Preference sLastClickedMountToggle;
    private ArrayList<StorageVolumePreferenceCategory> mCategories = Lists.newArrayList();
    private final BroadcastReceiver mMediaScannerReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Iterator i$;
            if (action.equals("android.hardware.usb.action.USB_STATE")) {
                boolean isUsbConnected = intent.getBooleanExtra("connected", false);
                String usbFunction = Memory.this.mUsbManager.getDefaultFunction();
                i$ = Memory.this.mCategories.iterator();
                while (i$.hasNext()) {
                    ((StorageVolumePreferenceCategory) i$.next()).onUsbStateChanged(isUsbConnected, usbFunction);
                }
            } else if (action.equals("android.intent.action.MEDIA_SCANNER_FINISHED")) {
                i$ = Memory.this.mCategories.iterator();
                while (i$.hasNext()) {
                    ((StorageVolumePreferenceCategory) i$.next()).onMediaScannerFinished();
                }
            }
        }
    };
    private IMountService mMountService;
    StorageEventListener mStorageListener = new StorageEventListener() {
        public void onStorageStateChanged(String path, String oldState, String newState) {
            Log.i("MemorySettings", "Received storage state changed notification that " + path + " changed state from " + oldState + " to " + newState);
            Iterator i$ = Memory.this.mCategories.iterator();
            while (i$.hasNext()) {
                StorageVolumePreferenceCategory category = (StorageVolumePreferenceCategory) i$.next();
                StorageVolume volume = category.getStorageVolume();
                if (volume != null && path.equals(volume.getPath())) {
                    category.onStorageStateChanged();
                    return;
                }
            }
        }
    };
    private StorageManager mStorageManager;
    private UsbManager mUsbManager;

    private static class ClearCacheObserver extends Stub {
        private int mRemaining;
        private final Memory mTarget;

        public ClearCacheObserver(Memory target, int remaining) {
            this.mTarget = target;
            this.mRemaining = remaining;
        }

        public void onRemoveCompleted(String packageName, boolean succeeded) {
            synchronized (this) {
                int i = this.mRemaining - 1;
                this.mRemaining = i;
                if (i == 0) {
                    this.mTarget.onCacheCleared();
                }
            }
        }
    }

    public static class ConfirmClearCacheFragment extends DialogFragment {
        public static void show(Memory parent) {
            if (parent.isAdded()) {
                ConfirmClearCacheFragment dialog = new ConfirmClearCacheFragment();
                dialog.setTargetFragment(parent, 0);
                dialog.show(parent.getFragmentManager(), "confirmClearCache");
            }
        }

        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final Context context = getActivity();
            Builder builder = new Builder(context);
            builder.setTitle(R.string.memory_clear_cache_title);
            builder.setMessage(getString(R.string.memory_clear_cache_message));
            builder.setPositiveButton(17039370, new OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    Memory target = (Memory) ConfirmClearCacheFragment.this.getTargetFragment();
                    PackageManager pm = context.getPackageManager();
                    List<PackageInfo> infos = pm.getInstalledPackages(0);
                    ClearCacheObserver observer = new ClearCacheObserver(target, infos.size());
                    for (PackageInfo info : infos) {
                        pm.deleteApplicationCacheFiles(info.packageName, observer);
                    }
                }
            });
            builder.setNegativeButton(17039360, null);
            return builder.create();
        }
    }

    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        Context context = getActivity();
        this.mUsbManager = (UsbManager) getSystemService("usb");
        this.mStorageManager = StorageManager.from(context);
        this.mStorageManager.registerListener(this.mStorageListener);
        addPreferencesFromResource(R.xml.device_info_memory);
        addCategory(StorageVolumePreferenceCategory.buildForInternal(context));
        for (StorageVolume volume : this.mStorageManager.getVolumeList()) {
            if (!volume.isEmulated()) {
                addCategory(StorageVolumePreferenceCategory.buildForPhysical(context, volume));
            }
        }
        setHasOptionsMenu(true);
    }

    private void addCategory(StorageVolumePreferenceCategory category) {
        this.mCategories.add(category);
        getPreferenceScreen().addPreference(category);
        category.init();
    }

    private boolean isMassStorageEnabled() {
        StorageVolume primary = StorageManager.getPrimaryVolume(this.mStorageManager.getVolumeList());
        return primary != null && primary.allowMassStorage();
    }

    public void onResume() {
        super.onResume();
        IntentFilter intentFilter = new IntentFilter("android.intent.action.MEDIA_SCANNER_STARTED");
        intentFilter.addAction("android.intent.action.MEDIA_SCANNER_FINISHED");
        intentFilter.addDataScheme("file");
        getActivity().registerReceiver(this.mMediaScannerReceiver, intentFilter);
        intentFilter = new IntentFilter();
        intentFilter.addAction("android.hardware.usb.action.USB_STATE");
        getActivity().registerReceiver(this.mMediaScannerReceiver, intentFilter);
        Iterator i$ = this.mCategories.iterator();
        while (i$.hasNext()) {
            ((StorageVolumePreferenceCategory) i$.next()).onResume();
        }
    }

    public void onPause() {
        super.onPause();
        getActivity().unregisterReceiver(this.mMediaScannerReceiver);
        Iterator i$ = this.mCategories.iterator();
        while (i$.hasNext()) {
            ((StorageVolumePreferenceCategory) i$.next()).onPause();
        }
    }

    public void onDestroy() {
        if (!(this.mStorageManager == null || this.mStorageListener == null)) {
            this.mStorageManager.unregisterListener(this.mStorageListener);
        }
        super.onDestroy();
    }

    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.storage, menu);
    }

    public void onPrepareOptionsMenu(Menu menu) {
        MenuItem usb = menu.findItem(R.id.storage_usb);
        boolean usbItemVisible = (isMassStorageEnabled() || ((UserManager) getActivity().getSystemService("user")).hasUserRestriction("no_usb_file_transfer")) ? false : true;
        usb.setVisible(usbItemVisible);
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.storage_usb:
                if (getActivity() instanceof SettingsActivity) {
                    ((SettingsActivity) getActivity()).startPreferencePanel(UsbSettings.class.getCanonicalName(), null, R.string.storage_title_usb, null, this, 0);
                } else {
                    startFragment(this, UsbSettings.class.getCanonicalName(), R.string.storage_title_usb, -1, null);
                }
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private synchronized IMountService getMountService() {
        if (this.mMountService == null) {
            IBinder service = ServiceManager.getService("mount");
            if (service != null) {
                this.mMountService = IMountService.Stub.asInterface(service);
            } else {
                Log.e("MemorySettings", "Can't get mount service");
            }
        }
        return this.mMountService;
    }

    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        if ("cache".equals(preference.getKey())) {
            ConfirmClearCacheFragment.show(this);
            return true;
        }
        Iterator i$ = this.mCategories.iterator();
        while (i$.hasNext()) {
            StorageVolumePreferenceCategory category = (StorageVolumePreferenceCategory) i$.next();
            Intent intent = category.intentForClick(preference);
            if (intent == null) {
                StorageVolume volume = category.getStorageVolume();
                if (volume != null && category.mountToggleClicked(preference)) {
                    sLastClickedMountToggle = preference;
                    sClickedMountPoint = volume.getPath();
                    String state = this.mStorageManager.getVolumeState(volume.getPath());
                    if ("mounted".equals(state) || "mounted_ro".equals(state)) {
                        unmount();
                        return true;
                    }
                    mount();
                    return true;
                }
            } else if (Utils.isMonkeyRunning()) {
                return true;
            } else {
                try {
                    startActivity(intent);
                    return true;
                } catch (ActivityNotFoundException e) {
                    Log.w("MemorySettings", "No activity found for intent " + intent);
                    return true;
                }
            }
        }
        return false;
    }

    public Dialog onCreateDialog(int id) {
        switch (id) {
            case 1:
                return new Builder(getActivity()).setTitle(R.string.dlg_confirm_unmount_title).setPositiveButton(R.string.dlg_ok, new OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        Memory.this.doUnmount();
                    }
                }).setNegativeButton(R.string.cancel, null).setMessage(R.string.dlg_confirm_unmount_text).create();
            case 2:
                return new Builder(getActivity()).setTitle(R.string.dlg_error_unmount_title).setNeutralButton(R.string.dlg_ok, null).setMessage(R.string.dlg_error_unmount_text).create();
            default:
                return null;
        }
    }

    private void doUnmount() {
        Toast.makeText(getActivity(), R.string.unmount_inform_text, 0).show();
        IMountService mountService = getMountService();
        try {
            sLastClickedMountToggle.setEnabled(false);
            sLastClickedMountToggle.setTitle(getString(R.string.sd_ejecting_title));
            sLastClickedMountToggle.setSummary(getString(R.string.sd_ejecting_summary));
            mountService.unmountVolume(sClickedMountPoint, true, false);
        } catch (RemoteException e) {
            showDialogInner(2);
        }
    }

    private void showDialogInner(int id) {
        removeDialog(id);
        showDialog(id);
    }

    private boolean hasAppsAccessingStorage() throws RemoteException {
        int[] stUsers = getMountService().getStorageUsers(sClickedMountPoint);
        return (stUsers == null || stUsers.length > 0) ? true : true;
    }

    private void unmount() {
        try {
            if (hasAppsAccessingStorage()) {
                showDialogInner(1);
            } else {
                doUnmount();
            }
        } catch (RemoteException e) {
            Log.e("MemorySettings", "Is MountService running?");
            showDialogInner(2);
        }
    }

    private void mount() {
        IMountService mountService = getMountService();
        if (mountService != null) {
            try {
                mountService.mountVolume(sClickedMountPoint);
                return;
            } catch (RemoteException e) {
                return;
            }
        }
        Log.e("MemorySettings", "Mount service is null, can't mount");
    }

    private void onCacheCleared() {
        Iterator i$ = this.mCategories.iterator();
        while (i$.hasNext()) {
            ((StorageVolumePreferenceCategory) i$.next()).onCacheCleared();
        }
    }
}
