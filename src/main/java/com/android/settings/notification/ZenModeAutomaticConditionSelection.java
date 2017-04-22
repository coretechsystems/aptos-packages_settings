package com.android.settings.notification;

import android.animation.LayoutTransition;
import android.app.INotificationManager;
import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Message;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.service.notification.Condition;
import android.service.notification.IConditionListener;
import android.service.notification.IConditionListener.Stub;
import android.util.ArraySet;
import android.util.Log;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.LinearLayout;
import com.android.settings.R;

public class ZenModeAutomaticConditionSelection extends LinearLayout {
    private final Context mContext;
    private final H mHandler = new H();
    private final IConditionListener mListener = new Stub() {
        public void onConditionsReceived(Condition[] conditions) {
            if (conditions != null && conditions.length != 0) {
                ZenModeAutomaticConditionSelection.this.mHandler.obtainMessage(1, conditions).sendToTarget();
            }
        }
    };
    private final INotificationManager mNoMan;
    private final ArraySet<Uri> mSelectedConditions = new ArraySet();

    private final class H extends Handler {
        private H() {
        }

        public void handleMessage(Message msg) {
            if (msg.what == 1) {
                ZenModeAutomaticConditionSelection.this.handleConditions((Condition[]) msg.obj);
            }
        }
    }

    public ZenModeAutomaticConditionSelection(Context context) {
        super(context);
        this.mContext = context;
        setOrientation(1);
        setLayoutTransition(new LayoutTransition());
        int p = this.mContext.getResources().getDimensionPixelSize(R.dimen.content_margin_left);
        setPadding(p, p, p, 0);
        this.mNoMan = INotificationManager.Stub.asInterface(ServiceManager.getService("notification"));
        refreshSelectedConditions();
    }

    private void refreshSelectedConditions() {
        try {
            Condition[] automatic = this.mNoMan.getAutomaticZenModeConditions();
            this.mSelectedConditions.clear();
            if (automatic != null) {
                for (Condition c : automatic) {
                    this.mSelectedConditions.add(c.id);
                }
            }
        } catch (RemoteException e) {
            Log.w("ZenModeAutomaticConditionSelection", "Error calling getAutomaticZenModeConditions", e);
        }
    }

    private CheckBox newCheckBox(Object tag) {
        final CheckBox button = new CheckBox(this.mContext);
        button.setTag(tag);
        button.setOnCheckedChangeListener(new OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                ZenModeAutomaticConditionSelection.this.setSelectedCondition((Uri) button.getTag(), isChecked);
            }
        });
        addView(button);
        return button;
    }

    private void setSelectedCondition(Uri conditionId, boolean selected) {
        Log.d("ZenModeAutomaticConditionSelection", "setSelectedCondition conditionId=" + conditionId + " selected=" + selected);
        if (selected) {
            this.mSelectedConditions.add(conditionId);
        } else {
            this.mSelectedConditions.remove(conditionId);
        }
        Uri[] automatic = new Uri[this.mSelectedConditions.size()];
        for (int i = 0; i < automatic.length; i++) {
            automatic[i] = (Uri) this.mSelectedConditions.valueAt(i);
        }
        try {
            this.mNoMan.setAutomaticZenModeConditions(automatic);
        } catch (RemoteException e) {
            Log.w("ZenModeAutomaticConditionSelection", "Error calling setAutomaticZenModeConditions", e);
        }
    }

    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        requestZenModeConditions(2);
    }

    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        requestZenModeConditions(0);
    }

    protected void requestZenModeConditions(int relevance) {
        Log.d("ZenModeAutomaticConditionSelection", "requestZenModeConditions " + Condition.relevanceToString(relevance));
        try {
            this.mNoMan.requestZenModeConditions(this.mListener, relevance);
        } catch (RemoteException e) {
            Log.w("ZenModeAutomaticConditionSelection", "Error calling requestZenModeConditions", e);
        }
    }

    protected void handleConditions(Condition[] conditions) {
        for (Condition c : conditions) {
            CheckBox v = (CheckBox) findViewWithTag(c.id);
            if (c.state != 3 && v == null) {
                v = newCheckBox(c.id);
            }
            if (v != null) {
                v.setText(c.summary);
                v.setEnabled(c.state != 3);
                v.setChecked(this.mSelectedConditions.contains(c.id));
            }
        }
    }
}
