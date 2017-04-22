package com.android.settings;

import android.app.Activity;
import android.app.AlertDialog.Builder;
import android.app.ListFragment;
import android.app.admin.DeviceAdminInfo;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.content.res.Resources.NotFoundException;
import android.os.Bundle;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.Log;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import com.android.internal.R;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.xmlpull.v1.XmlPullParserException;

public class DeviceAdminSettings extends ListFragment {
    private final SparseArray<ArrayList<DeviceAdminInfo>> mAdminsByProfile = new SparseArray();
    private DevicePolicyManager mDPM;
    private String mDeviceOwnerPkg;
    private SparseArray<ComponentName> mProfileOwnerComponents = new SparseArray();
    private UserManager mUm;

    class PolicyListAdapter extends BaseAdapter {
        final LayoutInflater mInflater;

        PolicyListAdapter() {
            this.mInflater = (LayoutInflater) DeviceAdminSettings.this.getActivity().getSystemService("layout_inflater");
        }

        public boolean hasStableIds() {
            return false;
        }

        public int getCount() {
            int adminCount = 0;
            int profileCount = DeviceAdminSettings.this.mAdminsByProfile.size();
            for (int i = 0; i < profileCount; i++) {
                adminCount += ((ArrayList) DeviceAdminSettings.this.mAdminsByProfile.valueAt(i)).size();
            }
            return adminCount + profileCount;
        }

        public Object getItem(int position) {
            if (position < 0) {
                throw new ArrayIndexOutOfBoundsException();
            }
            int adminPosition = position;
            int n = DeviceAdminSettings.this.mAdminsByProfile.size();
            int i = 0;
            while (i < n) {
                int listSize = ((ArrayList) DeviceAdminSettings.this.mAdminsByProfile.valueAt(i)).size() + 1;
                if (adminPosition < listSize) {
                    break;
                }
                adminPosition -= listSize;
                i++;
            }
            if (i == n) {
                throw new ArrayIndexOutOfBoundsException();
            } else if (adminPosition != 0) {
                return ((ArrayList) DeviceAdminSettings.this.mAdminsByProfile.valueAt(i)).get(adminPosition - 1);
            } else {
                Resources res = DeviceAdminSettings.this.getActivity().getResources();
                if (DeviceAdminSettings.this.mAdminsByProfile.keyAt(i) == UserHandle.myUserId()) {
                    return res.getString(R.string.personal_device_admin_title);
                }
                return res.getString(R.string.managed_device_admin_title);
            }
        }

        public long getItemId(int position) {
            return (long) position;
        }

        public boolean areAllItemsEnabled() {
            return false;
        }

        public int getViewTypeCount() {
            return 2;
        }

        public int getItemViewType(int position) {
            return getItem(position) instanceof String ? 1 : 0;
        }

        public boolean isEnabled(int position) {
            return isEnabled(getItem(position));
        }

        private boolean isEnabled(Object o) {
            if (!(o instanceof DeviceAdminInfo)) {
                return false;
            }
            DeviceAdminInfo info = (DeviceAdminInfo) o;
            if (DeviceAdminSettings.this.isActiveAdmin(info) && DeviceAdminSettings.this.getUserId(info) == UserHandle.myUserId() && (DeviceAdminSettings.this.isDeviceOwner(info) || DeviceAdminSettings.this.isProfileOwner(info))) {
                return false;
            }
            return true;
        }

        public View getView(int position, View convertView, ViewGroup parent) {
            Object o = getItem(position);
            if (o instanceof DeviceAdminInfo) {
                if (convertView == null) {
                    convertView = newDeviceAdminView(parent);
                }
                bindView(convertView, (DeviceAdminInfo) o);
            } else {
                if (convertView == null) {
                    convertView = newTitleView(parent);
                }
                ((TextView) convertView.findViewById(16908310)).setText((String) o);
            }
            return convertView;
        }

        private View newDeviceAdminView(ViewGroup parent) {
            View v = this.mInflater.inflate(R.layout.device_admin_item, parent, false);
            ViewHolder h = new ViewHolder();
            h.icon = (ImageView) v.findViewById(R.id.icon);
            h.name = (TextView) v.findViewById(R.id.name);
            h.checkbox = (CheckBox) v.findViewById(R.id.checkbox);
            h.description = (TextView) v.findViewById(R.id.description);
            v.setTag(h);
            return v;
        }

        private View newTitleView(ViewGroup parent) {
            return this.mInflater.inflate(this.mInflater.getContext().obtainStyledAttributes(null, R.styleable.Preference, 16842892, 0).getResourceId(3, 0), parent, false);
        }

        private void bindView(View view, DeviceAdminInfo item) {
            Activity activity = DeviceAdminSettings.this.getActivity();
            ViewHolder vh = (ViewHolder) view.getTag();
            vh.icon.setImageDrawable(activity.getPackageManager().getUserBadgedIcon(item.loadIcon(activity.getPackageManager()), new UserHandle(DeviceAdminSettings.this.getUserId(item))));
            vh.name.setText(item.loadLabel(activity.getPackageManager()));
            vh.checkbox.setChecked(DeviceAdminSettings.this.isActiveAdmin(item));
            boolean enabled = isEnabled((Object) item);
            try {
                vh.description.setText(item.loadDescription(activity.getPackageManager()));
            } catch (NotFoundException e) {
            }
            vh.checkbox.setEnabled(enabled);
            vh.name.setEnabled(enabled);
            vh.description.setEnabled(enabled);
            vh.icon.setEnabled(enabled);
        }
    }

    static class ViewHolder {
        CheckBox checkbox;
        TextView description;
        ImageView icon;
        TextView name;

        ViewHolder() {
        }
    }

    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
    }

    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        this.mDPM = (DevicePolicyManager) getActivity().getSystemService("device_policy");
        this.mUm = (UserManager) getActivity().getSystemService("user");
        return inflater.inflate(R.layout.device_admin_settings, container, false);
    }

    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        Utils.forceCustomPadding(getListView(), true);
    }

    public void onResume() {
        super.onResume();
        this.mDeviceOwnerPkg = this.mDPM.getDeviceOwner();
        if (!(this.mDeviceOwnerPkg == null || this.mDPM.isDeviceOwner(this.mDeviceOwnerPkg))) {
            this.mDeviceOwnerPkg = null;
        }
        this.mProfileOwnerComponents.clear();
        List<UserHandle> profiles = this.mUm.getUserProfiles();
        int profilesSize = profiles.size();
        for (int i = 0; i < profilesSize; i++) {
            int profileId = ((UserHandle) profiles.get(i)).getIdentifier();
            this.mProfileOwnerComponents.put(profileId, this.mDPM.getProfileOwnerAsUser(profileId));
        }
        updateList();
    }

    void updateList() {
        this.mAdminsByProfile.clear();
        List<UserHandle> profiles = this.mUm.getUserProfiles();
        int profilesSize = profiles.size();
        for (int i = 0; i < profilesSize; i++) {
            updateAvailableAdminsForProfile(((UserHandle) profiles.get(i)).getIdentifier());
        }
        getListView().setAdapter(new PolicyListAdapter());
    }

    public void onListItemClick(ListView l, View v, int position, long id) {
        DeviceAdminInfo o = l.getAdapter().getItem(position);
        if (o instanceof DeviceAdminInfo) {
            DeviceAdminInfo dpi = o;
            Activity activity = getActivity();
            int userId = getUserId(dpi);
            if (userId == UserHandle.myUserId() || !isProfileOwner(dpi)) {
                Intent intent = new Intent();
                intent.setClass(activity, DeviceAdminAdd.class);
                intent.putExtra("android.app.extra.DEVICE_ADMIN", dpi.getComponent());
                activity.startActivityAsUser(intent, new UserHandle(userId));
                return;
            }
            Builder builder = new Builder(activity);
            builder.setMessage(getString(R.string.managed_profile_device_admin_info, new Object[]{dpi.loadLabel(activity.getPackageManager())}));
            builder.setPositiveButton(17039370, null);
            builder.create().show();
        }
    }

    private boolean isDeviceOwner(DeviceAdminInfo item) {
        return getUserId(item) == UserHandle.myUserId() && item.getPackageName().equals(this.mDeviceOwnerPkg);
    }

    private boolean isProfileOwner(DeviceAdminInfo item) {
        return item.getComponent().equals((ComponentName) this.mProfileOwnerComponents.get(getUserId(item)));
    }

    private boolean isActiveAdmin(DeviceAdminInfo item) {
        return this.mDPM.isAdminActiveAsUser(item.getComponent(), getUserId(item));
    }

    private void updateAvailableAdminsForProfile(int profileId) {
        List<ComponentName> activeAdminsListForProfile = this.mDPM.getActiveAdminsAsUser(profileId);
        addActiveAdminsForProfile(activeAdminsListForProfile, profileId);
        addDeviceAdminBroadcastReceiversForProfile(activeAdminsListForProfile, profileId);
    }

    private void addDeviceAdminBroadcastReceiversForProfile(Collection<ComponentName> alreadyAddedComponents, int profileId) {
        List<ResolveInfo> enabledForProfile = getActivity().getPackageManager().queryBroadcastReceivers(new Intent("android.app.action.DEVICE_ADMIN_ENABLED"), 32896, profileId);
        if (enabledForProfile == null) {
            enabledForProfile = Collections.emptyList();
        }
        int n = enabledForProfile.size();
        ArrayList<DeviceAdminInfo> deviceAdmins = (ArrayList) this.mAdminsByProfile.get(profileId);
        if (deviceAdmins == null) {
            deviceAdmins = new ArrayList(n);
        }
        for (int i = 0; i < n; i++) {
            ResolveInfo resolveInfo = (ResolveInfo) enabledForProfile.get(i);
            ComponentName riComponentName = new ComponentName(resolveInfo.activityInfo.packageName, resolveInfo.activityInfo.name);
            if (alreadyAddedComponents == null || !alreadyAddedComponents.contains(riComponentName)) {
                DeviceAdminInfo deviceAdminInfo = createDeviceAdminInfo(resolveInfo);
                if (deviceAdminInfo != null && deviceAdminInfo.isVisible()) {
                    deviceAdmins.add(deviceAdminInfo);
                }
            }
        }
        if (!deviceAdmins.isEmpty()) {
            this.mAdminsByProfile.put(profileId, deviceAdmins);
        }
    }

    private void addActiveAdminsForProfile(List<ComponentName> activeAdmins, int profileId) {
        if (activeAdmins != null) {
            PackageManager packageManager = getActivity().getPackageManager();
            int n = activeAdmins.size();
            ArrayList<DeviceAdminInfo> deviceAdmins = new ArrayList(n);
            for (int i = 0; i < n; i++) {
                List<ResolveInfo> resolved = packageManager.queryBroadcastReceivers(new Intent().setComponent((ComponentName) activeAdmins.get(i)), 32896, profileId);
                if (resolved != null) {
                    int resolvedMax = resolved.size();
                    for (int j = 0; j < resolvedMax; j++) {
                        DeviceAdminInfo deviceAdminInfo = createDeviceAdminInfo((ResolveInfo) resolved.get(j));
                        if (deviceAdminInfo != null) {
                            deviceAdmins.add(deviceAdminInfo);
                        }
                    }
                }
            }
            if (!deviceAdmins.isEmpty()) {
                this.mAdminsByProfile.put(profileId, deviceAdmins);
            }
        }
    }

    private DeviceAdminInfo createDeviceAdminInfo(ResolveInfo resolved) {
        try {
            return new DeviceAdminInfo(getActivity(), resolved);
        } catch (XmlPullParserException e) {
            Log.w("DeviceAdminSettings", "Skipping " + resolved.activityInfo, e);
        } catch (IOException e2) {
            Log.w("DeviceAdminSettings", "Skipping " + resolved.activityInfo, e2);
        }
        return null;
    }

    private int getUserId(DeviceAdminInfo adminInfo) {
        return UserHandle.getUserId(adminInfo.getActivityInfo().applicationInfo.uid);
    }
}
