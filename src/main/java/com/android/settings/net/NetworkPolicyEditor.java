package com.android.settings.net;

import android.net.NetworkPolicy;
import android.net.NetworkPolicyManager;
import android.net.NetworkTemplate;
import android.net.wifi.WifiInfo;
import android.os.AsyncTask;
import android.text.TextUtils;
import android.text.format.Time;
import com.android.internal.util.Preconditions;
import com.google.android.collect.Lists;
import com.google.android.collect.Sets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Objects;

public class NetworkPolicyEditor {
    private ArrayList<NetworkPolicy> mPolicies = Lists.newArrayList();
    private NetworkPolicyManager mPolicyManager;

    public NetworkPolicyEditor(NetworkPolicyManager policyManager) {
        this.mPolicyManager = (NetworkPolicyManager) Preconditions.checkNotNull(policyManager);
    }

    public void read() {
        NetworkPolicy[] policies = this.mPolicyManager.getNetworkPolicies();
        boolean modified = false;
        this.mPolicies.clear();
        for (NetworkPolicy policy : policies) {
            if (policy.limitBytes < -1) {
                policy.limitBytes = -1;
                modified = true;
            }
            if (policy.warningBytes < -1) {
                policy.warningBytes = -1;
                modified = true;
            }
            this.mPolicies.add(policy);
        }
        if (modified | forceMobilePolicyCombined()) {
            writeAsync();
        }
    }

    public void writeAsync() {
        final NetworkPolicy[] policies = (NetworkPolicy[]) this.mPolicies.toArray(new NetworkPolicy[this.mPolicies.size()]);
        new AsyncTask<Void, Void, Void>() {
            protected Void doInBackground(Void... params) {
                NetworkPolicyEditor.this.write(policies);
                return null;
            }
        }.execute(new Void[0]);
    }

    public void write(NetworkPolicy[] policies) {
        this.mPolicyManager.setNetworkPolicies(policies);
    }

    public NetworkPolicy getOrCreatePolicy(NetworkTemplate template) {
        NetworkPolicy policy = getPolicy(template);
        if (policy != null) {
            return policy;
        }
        policy = buildDefaultPolicy(template);
        this.mPolicies.add(policy);
        return policy;
    }

    public NetworkPolicy getPolicy(NetworkTemplate template) {
        Iterator i$ = this.mPolicies.iterator();
        while (i$.hasNext()) {
            NetworkPolicy policy = (NetworkPolicy) i$.next();
            if (policy.template.equals(template)) {
                return policy;
            }
        }
        return null;
    }

    public NetworkPolicy getPolicyMaybeUnquoted(NetworkTemplate template) {
        NetworkPolicy policy = getPolicy(template);
        return policy != null ? policy : getPolicy(buildUnquotedNetworkTemplate(template));
    }

    @Deprecated
    private static NetworkPolicy buildDefaultPolicy(NetworkTemplate template) {
        int cycleDay;
        String cycleTimezone;
        boolean metered;
        if (template.getMatchRule() == 4) {
            cycleDay = -1;
            cycleTimezone = "UTC";
            metered = false;
        } else {
            Time time = new Time();
            time.setToNow();
            cycleDay = time.monthDay;
            cycleTimezone = time.timezone;
            metered = true;
        }
        return new NetworkPolicy(template, cycleDay, cycleTimezone, -1, -1, -1, -1, metered, true);
    }

    public int getPolicyCycleDay(NetworkTemplate template) {
        NetworkPolicy policy = getPolicy(template);
        return policy != null ? policy.cycleDay : -1;
    }

    public void setPolicyCycleDay(NetworkTemplate template, int cycleDay, String cycleTimezone) {
        NetworkPolicy policy = getOrCreatePolicy(template);
        policy.cycleDay = cycleDay;
        policy.cycleTimezone = cycleTimezone;
        policy.inferred = false;
        policy.clearSnooze();
        writeAsync();
    }

    public long getPolicyWarningBytes(NetworkTemplate template) {
        NetworkPolicy policy = getPolicy(template);
        return policy != null ? policy.warningBytes : -1;
    }

    public void setPolicyWarningBytes(NetworkTemplate template, long warningBytes) {
        NetworkPolicy policy = getOrCreatePolicy(template);
        policy.warningBytes = warningBytes;
        policy.inferred = false;
        policy.clearSnooze();
        writeAsync();
    }

    public long getPolicyLimitBytes(NetworkTemplate template) {
        NetworkPolicy policy = getPolicy(template);
        return policy != null ? policy.limitBytes : -1;
    }

    public void setPolicyLimitBytes(NetworkTemplate template, long limitBytes) {
        NetworkPolicy policy = getOrCreatePolicy(template);
        policy.limitBytes = limitBytes;
        policy.inferred = false;
        policy.clearSnooze();
        writeAsync();
    }

    public void setPolicyMetered(NetworkTemplate template, boolean metered) {
        boolean modified = false;
        NetworkPolicy policy = getPolicy(template);
        if (metered) {
            if (policy == null) {
                policy = buildDefaultPolicy(template);
                policy.metered = true;
                policy.inferred = false;
                this.mPolicies.add(policy);
                modified = true;
            } else if (!policy.metered) {
                policy.metered = true;
                policy.inferred = false;
                modified = true;
            }
        } else if (policy != null && policy.metered) {
            policy.metered = false;
            policy.inferred = false;
            modified = true;
        }
        NetworkPolicy unquotedPolicy = getPolicy(buildUnquotedNetworkTemplate(template));
        if (unquotedPolicy != null) {
            this.mPolicies.remove(unquotedPolicy);
            modified = true;
        }
        if (modified) {
            writeAsync();
        }
    }

    private boolean forceMobilePolicyCombined() {
        HashSet<String> subscriberIds = Sets.newHashSet();
        Iterator i$ = this.mPolicies.iterator();
        while (i$.hasNext()) {
            subscriberIds.add(((NetworkPolicy) i$.next()).template.getSubscriberId());
        }
        boolean modified = false;
        i$ = subscriberIds.iterator();
        while (i$.hasNext()) {
            modified |= setMobilePolicySplitInternal((String) i$.next(), false);
        }
        return modified;
    }

    @Deprecated
    public boolean isMobilePolicySplit(String subscriberId) {
        boolean has3g = false;
        boolean has4g = false;
        Iterator i$ = this.mPolicies.iterator();
        while (i$.hasNext()) {
            NetworkTemplate template = ((NetworkPolicy) i$.next()).template;
            if (Objects.equals(subscriberId, template.getSubscriberId())) {
                switch (template.getMatchRule()) {
                    case 2:
                        has3g = true;
                        break;
                    case 3:
                        has4g = true;
                        break;
                    default:
                        break;
                }
            }
        }
        return has3g && has4g;
    }

    @Deprecated
    private boolean setMobilePolicySplitInternal(String subscriberId, boolean split) {
        boolean beforeSplit = isMobilePolicySplit(subscriberId);
        NetworkTemplate template3g = NetworkTemplate.buildTemplateMobile3gLower(subscriberId);
        NetworkTemplate template4g = NetworkTemplate.buildTemplateMobile4g(subscriberId);
        NetworkTemplate templateAll = NetworkTemplate.buildTemplateMobileAll(subscriberId);
        if (split == beforeSplit) {
            return false;
        }
        if (beforeSplit && !split) {
            NetworkPolicy policy3g = getPolicy(template3g);
            NetworkPolicy policy4g = getPolicy(template4g);
            if (policy3g == null && policy4g == null) {
                return false;
            }
            NetworkPolicy restrictive;
            if (policy3g == null) {
                restrictive = policy4g;
            } else if (policy4g == null) {
                restrictive = policy3g;
            } else {
                restrictive = policy3g.compareTo(policy4g) < 0 ? policy3g : policy4g;
            }
            this.mPolicies.remove(policy3g);
            this.mPolicies.remove(policy4g);
            this.mPolicies.add(new NetworkPolicy(templateAll, restrictive.cycleDay, restrictive.cycleTimezone, restrictive.warningBytes, restrictive.limitBytes, -1, -1, restrictive.metered, restrictive.inferred));
            return true;
        } else if (beforeSplit || !split) {
            return false;
        } else {
            NetworkPolicy policyAll = getPolicy(templateAll);
            if (policyAll == null) {
                return false;
            }
            this.mPolicies.remove(policyAll);
            this.mPolicies.add(new NetworkPolicy(template3g, policyAll.cycleDay, policyAll.cycleTimezone, policyAll.warningBytes, policyAll.limitBytes, -1, -1, policyAll.metered, policyAll.inferred));
            this.mPolicies.add(new NetworkPolicy(template4g, policyAll.cycleDay, policyAll.cycleTimezone, policyAll.warningBytes, policyAll.limitBytes, -1, -1, policyAll.metered, policyAll.inferred));
            return true;
        }
    }

    private static NetworkTemplate buildUnquotedNetworkTemplate(NetworkTemplate template) {
        if (template == null) {
            return null;
        }
        String networkId = template.getNetworkId();
        String strippedNetworkId = WifiInfo.removeDoubleQuotes(networkId);
        if (TextUtils.equals(strippedNetworkId, networkId)) {
            return null;
        }
        return new NetworkTemplate(template.getMatchRule(), template.getSubscriberId(), strippedNetworkId);
    }
}
