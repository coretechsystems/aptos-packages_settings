package com.android.settings.notification;

import android.content.Context;
import android.service.notification.ZenModeConfig;
import android.util.SparseBooleanArray;
import android.view.LayoutInflater;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import com.android.settings.R;
import java.text.SimpleDateFormat;
import java.util.Calendar;

public class ZenModeDowntimeDaysSelection extends ScrollView {
    public static final int[] DAYS = new int[]{2, 3, 4, 5, 6, 7, 1};
    private static final SimpleDateFormat DAY_FORMAT = new SimpleDateFormat("EEEE");
    private final SparseBooleanArray mDays = new SparseBooleanArray();
    private final LinearLayout mLayout = new LinearLayout(this.mContext);

    public ZenModeDowntimeDaysSelection(Context context, String mode) {
        super(context);
        int hPad = context.getResources().getDimensionPixelSize(R.dimen.zen_downtime_margin);
        this.mLayout.setPadding(hPad, 0, hPad, 0);
        addView(this.mLayout);
        int[] days = ZenModeConfig.tryParseDays(mode);
        if (days != null) {
            for (int put : days) {
                this.mDays.put(put, true);
            }
        }
        this.mLayout.setOrientation(1);
        Calendar c = Calendar.getInstance();
        LayoutInflater inflater = LayoutInflater.from(context);
        for (final int day : DAYS) {
            CheckBox checkBox = (CheckBox) inflater.inflate(R.layout.zen_downtime_day, this, false);
            c.set(7, day);
            checkBox.setText(DAY_FORMAT.format(c.getTime()));
            checkBox.setChecked(this.mDays.get(day));
            checkBox.setOnCheckedChangeListener(new OnCheckedChangeListener() {
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    ZenModeDowntimeDaysSelection.this.mDays.put(day, isChecked);
                    ZenModeDowntimeDaysSelection.this.onChanged(ZenModeDowntimeDaysSelection.this.getMode());
                }
            });
            this.mLayout.addView(checkBox);
        }
    }

    private String getMode() {
        StringBuilder sb = new StringBuilder("days:");
        boolean empty = true;
        for (int i = 0; i < this.mDays.size(); i++) {
            int day = this.mDays.keyAt(i);
            if (this.mDays.valueAt(i)) {
                if (empty) {
                    empty = false;
                } else {
                    sb.append(',');
                }
                sb.append(day);
            }
        }
        return empty ? null : sb.toString();
    }

    protected void onChanged(String mode) {
    }
}
