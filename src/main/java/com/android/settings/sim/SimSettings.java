package com.android.settings.sim;

import android.app.AlertDialog.Builder;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.res.Resources;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceScreen;
import android.provider.SearchIndexableResource;
import android.telephony.SubInfoRecord;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.view.View;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import com.android.settings.R;
import com.android.settings.RestrictedSettingsFragment;
import com.android.settings.Utils;
import com.android.settings.notification.DropDownPreference;
import com.android.settings.notification.DropDownPreference.Callback;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settings.search.Indexable;
import com.android.settings.search.Indexable.SearchIndexProvider;
import java.util.ArrayList;
import java.util.List;

public class SimSettings extends RestrictedSettingsFragment implements Indexable {
    public static final SearchIndexProvider SEARCH_INDEX_DATA_PROVIDER = new BaseSearchIndexProvider() {
        public List<SearchIndexableResource> getXmlResourcesToIndex(Context context, boolean enabled) {
            ArrayList<SearchIndexableResource> result = new ArrayList();
            if (Utils.showSimCardTile(context)) {
                SearchIndexableResource sir = new SearchIndexableResource(context);
                sir.xmlResId = R.xml.sim_settings;
                result.add(sir);
            }
            return result;
        }
    };
    private List<SubInfoRecord> mAvailableSubInfos = null;
    private SubInfoRecord mCalls = null;
    private SubInfoRecord mCellularData = null;
    private int mNumSims;
    private SubInfoRecord mSMS = null;
    private List<SubInfoRecord> mSubInfoList = null;

    private class SimPreference extends Preference {
        private int mSlotId;
        private SubInfoRecord mSubInfoRecord;

        public SimPreference(Context context, SubInfoRecord subInfoRecord, int slotId) {
            super(context);
            this.mSubInfoRecord = subInfoRecord;
            this.mSlotId = slotId;
            setKey("sim" + this.mSlotId);
            update();
        }

        public void update() {
            Resources res = SimSettings.this.getResources();
            setTitle(res.getString(R.string.sim_card_number_title, new Object[]{Integer.valueOf(this.mSlotId + 1)}));
            if (this.mSubInfoRecord != null) {
                setSummary(res.getString(R.string.sim_settings_summary, new Object[]{this.mSubInfoRecord.displayName, this.mSubInfoRecord.number}));
                setEnabled(true);
                return;
            }
            setSummary(R.string.sim_slot_empty);
            setFragment(null);
            setEnabled(false);
        }

        public void createEditDialog(SimPreference simPref) {
            Builder builder = new Builder(SimSettings.this.getActivity());
            final View dialogLayout = SimSettings.this.getActivity().getLayoutInflater().inflate(R.layout.multi_sim_dialog, null);
            builder.setView(dialogLayout);
            ((EditText) dialogLayout.findViewById(R.id.sim_name)).setText(this.mSubInfoRecord.displayName);
            ((TextView) dialogLayout.findViewById(R.id.number)).setText(this.mSubInfoRecord.number);
            ((TextView) dialogLayout.findViewById(R.id.carrier)).setText(this.mSubInfoRecord.displayName);
            builder.setTitle(R.string.sim_editor_title);
            builder.setPositiveButton(R.string.okay, new OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {
                    EditText nameText = (EditText) dialogLayout.findViewById(R.id.sim_name);
                    SubscriptionManager.setDisplayNumberFormat(((Spinner) dialogLayout.findViewById(R.id.display_numbers)).getSelectedItemPosition() == 0 ? 2 : 1, SimPreference.this.mSubInfoRecord.subId);
                    SimPreference.this.mSubInfoRecord.displayName = nameText.getText().toString();
                    SubscriptionManager.setDisplayName(SimPreference.this.mSubInfoRecord.displayName, SimPreference.this.mSubInfoRecord.subId);
                    SimSettings.this.updateAllOptions();
                    SimPreference.this.update();
                }
            });
            builder.setNegativeButton(R.string.cancel, new OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {
                    dialog.dismiss();
                }
            });
            builder.create().show();
        }
    }

    public SimSettings() {
        super("no_config_sim");
    }

    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        if (this.mSubInfoList == null) {
            this.mSubInfoList = SubscriptionManager.getActiveSubInfoList();
        }
        createPreferences();
        updateAllOptions();
    }

    private void createPreferences() {
        TelephonyManager tm = (TelephonyManager) getActivity().getSystemService("phone");
        addPreferencesFromResource(R.xml.sim_settings);
        PreferenceCategory simCards = (PreferenceCategory) findPreference("sim_cards");
        int numSlots = tm.getSimCount();
        this.mAvailableSubInfos = new ArrayList(numSlots);
        this.mNumSims = 0;
        for (int i = 0; i < numSlots; i++) {
            SubInfoRecord sir = findRecordBySlotId(i);
            simCards.addPreference(new SimPreference(getActivity(), sir, i));
            this.mAvailableSubInfos.add(sir);
            if (sir != null) {
                this.mNumSims++;
            }
        }
        updateActivitesCategory();
    }

    private void updateAllOptions() {
        updateSimSlotValues();
        updateActivitesCategory();
    }

    private void updateSimSlotValues() {
        SubscriptionManager.getAllSubInfoList();
        PreferenceCategory simCards = (PreferenceCategory) findPreference("sim_cards");
        PreferenceScreen prefScreen = getPreferenceScreen();
        int prefSize = prefScreen.getPreferenceCount();
        for (int i = 0; i < prefSize; i++) {
            Preference pref = prefScreen.getPreference(i);
            if (pref instanceof SimPreference) {
                ((SimPreference) pref).update();
            }
        }
    }

    private void updateActivitesCategory() {
        createDropDown((DropDownPreference) findPreference("sim_cellular_data"));
        createDropDown((DropDownPreference) findPreference("sim_calls"));
        createDropDown((DropDownPreference) findPreference("sim_sms"));
        updateCellularDataValues();
        updateCallValues();
        updateSmsValues();
    }

    private SubInfoRecord findRecordBySubId(long subId) {
        int availableSubInfoLength = this.mAvailableSubInfos.size();
        for (int i = 0; i < availableSubInfoLength; i++) {
            SubInfoRecord sir = (SubInfoRecord) this.mAvailableSubInfos.get(i);
            if (sir != null && sir.subId == subId) {
                return sir;
            }
        }
        return null;
    }

    private SubInfoRecord findRecordBySlotId(int slotId) {
        if (this.mSubInfoList != null) {
            int availableSubInfoLength = this.mSubInfoList.size();
            for (int i = 0; i < availableSubInfoLength; i++) {
                SubInfoRecord sir = (SubInfoRecord) this.mSubInfoList.get(i);
                if (sir.slotId == slotId) {
                    return sir;
                }
            }
        }
        return null;
    }

    private void updateSmsValues() {
        boolean z = true;
        DropDownPreference simPref = (DropDownPreference) findPreference("sim_sms");
        SubInfoRecord sir = findRecordBySubId(SubscriptionManager.getDefaultSmsSubId());
        if (sir != null) {
            simPref.setSelectedItem(sir.slotId + 1);
        }
        if (this.mNumSims <= 1) {
            z = false;
        }
        simPref.setEnabled(z);
    }

    private void updateCellularDataValues() {
        boolean z = true;
        DropDownPreference simPref = (DropDownPreference) findPreference("sim_cellular_data");
        SubInfoRecord sir = findRecordBySubId(SubscriptionManager.getDefaultDataSubId());
        if (sir != null) {
            simPref.setSelectedItem(sir.slotId);
        }
        if (this.mNumSims <= 1) {
            z = false;
        }
        simPref.setEnabled(z);
    }

    private void updateCallValues() {
        boolean z = true;
        DropDownPreference simPref = (DropDownPreference) findPreference("sim_calls");
        SubInfoRecord sir = findRecordBySubId(SubscriptionManager.getDefaultVoiceSubId());
        if (sir != null) {
            simPref.setSelectedItem(sir.slotId + 1);
        }
        if (this.mNumSims <= 1) {
            z = false;
        }
        simPref.setEnabled(z);
    }

    public void onResume() {
        super.onResume();
        updateAllOptions();
    }

    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        if (preference instanceof SimPreference) {
            ((SimPreference) preference).createEditDialog((SimPreference) preference);
        }
        return true;
    }

    public void createDropDown(DropDownPreference preference) {
        final DropDownPreference simPref = preference;
        String keyPref = simPref.getKey();
        boolean askFirst = keyPref.equals("sim_calls") || keyPref.equals("sim_sms");
        simPref.clearItems();
        if (askFirst) {
            simPref.addItem(getResources().getString(R.string.sim_calls_ask_first_prefs_title), null);
        }
        int subAvailableSize = this.mAvailableSubInfos.size();
        for (int i = 0; i < subAvailableSize; i++) {
            Object sir = (SubInfoRecord) this.mAvailableSubInfos.get(i);
            if (sir != null) {
                simPref.addItem(sir.displayName, sir);
            }
        }
        simPref.setCallback(new Callback() {
            public boolean onItemSelected(int pos, Object value) {
                long subId = value == null ? 0 : ((SubInfoRecord) value).subId;
                if (simPref.getKey().equals("sim_cellular_data")) {
                    SubscriptionManager.setDefaultDataSubId(subId);
                } else if (simPref.getKey().equals("sim_calls")) {
                    SubscriptionManager.setDefaultVoiceSubId(subId);
                } else if (simPref.getKey().equals("sim_sms")) {
                }
                return true;
            }
        });
    }
}
