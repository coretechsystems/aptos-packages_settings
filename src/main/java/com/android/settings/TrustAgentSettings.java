package com.android.settings;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceGroup;
import android.preference.SwitchPreference;
import android.util.ArrayMap;
import android.util.ArraySet;
import com.android.internal.widget.LockPatternUtils;
import java.util.List;

public class TrustAgentSettings extends SettingsPreferenceFragment implements OnPreferenceChangeListener {
    private final ArraySet<ComponentName> mActiveAgents = new ArraySet();
    private ArrayMap<ComponentName, AgentInfo> mAvailableAgents;
    private LockPatternUtils mLockPatternUtils;

    public static final class AgentInfo {
        ComponentName component;
        public Drawable icon;
        CharSequence label;
        SwitchPreference preference;

        public boolean equals(Object other) {
            if (other instanceof AgentInfo) {
                return this.component.equals(((AgentInfo) other).component);
            }
            return true;
        }
    }

    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        addPreferencesFromResource(R.xml.trust_agent_settings);
    }

    public void onResume() {
        super.onResume();
        updateAgents();
    }

    private void updateAgents() {
        Context context = getActivity();
        if (this.mAvailableAgents == null) {
            this.mAvailableAgents = findAvailableTrustAgents();
        }
        if (this.mLockPatternUtils == null) {
            this.mLockPatternUtils = new LockPatternUtils(getActivity());
        }
        loadActiveAgents();
        PreferenceGroup category = (PreferenceGroup) getPreferenceScreen().findPreference("trust_agents");
        category.removeAll();
        int count = this.mAvailableAgents.size();
        for (int i = 0; i < count; i++) {
            AgentInfo agent = (AgentInfo) this.mAvailableAgents.valueAt(i);
            SwitchPreference preference = new SwitchPreference(context);
            agent.preference = preference;
            preference.setPersistent(false);
            preference.setTitle(agent.label);
            preference.setIcon(agent.icon);
            preference.setPersistent(false);
            preference.setOnPreferenceChangeListener(this);
            preference.setChecked(this.mActiveAgents.contains(agent.component));
            category.addPreference(agent.preference);
        }
    }

    private void loadActiveAgents() {
        List<ComponentName> activeTrustAgents = this.mLockPatternUtils.getEnabledTrustAgents();
        if (activeTrustAgents != null) {
            this.mActiveAgents.addAll(activeTrustAgents);
        }
    }

    private void saveActiveAgents() {
        this.mLockPatternUtils.setEnabledTrustAgents(this.mActiveAgents);
    }

    ArrayMap<ComponentName, AgentInfo> findAvailableTrustAgents() {
        PackageManager pm = getActivity().getPackageManager();
        List<ResolveInfo> resolveInfos = pm.queryIntentServices(new Intent("android.service.trust.TrustAgentService"), 128);
        ArrayMap<ComponentName, AgentInfo> agents = new ArrayMap();
        int count = resolveInfos.size();
        agents.ensureCapacity(count);
        for (int i = 0; i < count; i++) {
            ResolveInfo resolveInfo = (ResolveInfo) resolveInfos.get(i);
            if (resolveInfo.serviceInfo != null && TrustAgentUtils.checkProvidePermission(resolveInfo, pm)) {
                ComponentName name = TrustAgentUtils.getComponentName(resolveInfo);
                AgentInfo agentInfo = new AgentInfo();
                agentInfo.label = resolveInfo.loadLabel(pm);
                agentInfo.icon = resolveInfo.loadIcon(pm);
                agentInfo.component = name;
                agents.put(name, agentInfo);
            }
        }
        return agents;
    }

    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (preference instanceof SwitchPreference) {
            int count = this.mAvailableAgents.size();
            for (int i = 0; i < count; i++) {
                AgentInfo agent = (AgentInfo) this.mAvailableAgents.valueAt(i);
                if (agent.preference == preference) {
                    if (!((Boolean) newValue).booleanValue()) {
                        this.mActiveAgents.remove(agent.component);
                    } else if (!this.mActiveAgents.contains(agent.component)) {
                        this.mActiveAgents.add(agent.component);
                    }
                    saveActiveAgents();
                    return true;
                }
            }
        }
        return false;
    }
}
