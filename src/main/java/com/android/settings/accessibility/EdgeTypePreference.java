package com.android.settings.accessibility;

import android.content.Context;
import android.content.res.Resources;
import android.util.AttributeSet;
import android.view.View;
import android.widget.TextView;
import com.android.internal.widget.SubtitleView;
import com.android.settings.R;

public class EdgeTypePreference extends ListDialogPreference {
    public EdgeTypePreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        Resources res = context.getResources();
        setValues(res.getIntArray(R.array.captioning_edge_type_selector_values));
        setTitles(res.getStringArray(R.array.captioning_edge_type_selector_titles));
        setDialogLayoutResource(R.layout.grid_picker_dialog);
        setListItemLayoutResource(R.layout.preset_picker_item);
    }

    public boolean shouldDisableDependents() {
        return getValue() == 0 || super.shouldDisableDependents();
    }

    protected void onBindListItem(View view, int index) {
        SubtitleView preview = (SubtitleView) view.findViewById(R.id.preview);
        preview.setForegroundColor(-1);
        preview.setBackgroundColor(0);
        preview.setTextSize(32.0f * getContext().getResources().getDisplayMetrics().density);
        preview.setEdgeType(getValueAt(index));
        preview.setEdgeColor(-16777216);
        CharSequence title = getTitleAt(index);
        if (title != null) {
            ((TextView) view.findViewById(R.id.summary)).setText(title);
        }
    }
}
