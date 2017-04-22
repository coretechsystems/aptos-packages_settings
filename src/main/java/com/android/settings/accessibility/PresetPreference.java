package com.android.settings.accessibility;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.accessibility.CaptioningManager;
import android.widget.TextView;
import com.android.internal.widget.SubtitleView;
import com.android.settings.R;

public class PresetPreference extends ListDialogPreference {
    private final CaptioningManager mCaptioningManager;

    public PresetPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        setDialogLayoutResource(R.layout.grid_picker_dialog);
        setListItemLayoutResource(R.layout.preset_picker_item);
        this.mCaptioningManager = (CaptioningManager) context.getSystemService("captioning");
    }

    public boolean shouldDisableDependents() {
        return getValue() != -1 || super.shouldDisableDependents();
    }

    protected void onBindListItem(View view, int index) {
        SubtitleView previewText = (SubtitleView) view.findViewById(R.id.preview);
        CaptionPropertiesFragment.applyCaptionProperties(this.mCaptioningManager, previewText, view.findViewById(R.id.preview_viewport), getValueAt(index));
        previewText.setTextSize(32.0f * getContext().getResources().getDisplayMetrics().density);
        CharSequence title = getTitleAt(index);
        if (title != null) {
            ((TextView) view.findViewById(R.id.summary)).setText(title);
        }
    }
}
