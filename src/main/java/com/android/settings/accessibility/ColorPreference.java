package com.android.settings.accessibility;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import com.android.settings.R;

public class ColorPreference extends ListDialogPreference {
    private ColorDrawable mPreviewColor;
    private boolean mPreviewEnabled;

    public ColorPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        setDialogLayoutResource(R.layout.grid_picker_dialog);
        setListItemLayoutResource(R.layout.color_picker_item);
    }

    public boolean shouldDisableDependents() {
        return Color.alpha(getValue()) == 0 || super.shouldDisableDependents();
    }

    protected CharSequence getTitleAt(int index) {
        CharSequence title = super.getTitleAt(index);
        if (title != null) {
            return title;
        }
        int value = getValueAt(index);
        int r = Color.red(value);
        int g = Color.green(value);
        int b = Color.blue(value);
        return getContext().getString(R.string.color_custom, new Object[]{Integer.valueOf(r), Integer.valueOf(g), Integer.valueOf(b)});
    }

    protected void onBindView(View view) {
        super.onBindView(view);
        if (this.mPreviewEnabled) {
            ImageView previewImage = (ImageView) view.findViewById(R.id.color_preview);
            int argb = getValue();
            if (Color.alpha(argb) < 255) {
                previewImage.setBackgroundResource(R.drawable.transparency_tileable);
            } else {
                previewImage.setBackground(null);
            }
            if (this.mPreviewColor == null) {
                this.mPreviewColor = new ColorDrawable(argb);
                previewImage.setImageDrawable(this.mPreviewColor);
            } else {
                this.mPreviewColor.setColor(argb);
            }
            CharSequence summary = getSummary();
            if (TextUtils.isEmpty(summary)) {
                previewImage.setContentDescription(null);
            } else {
                previewImage.setContentDescription(summary);
            }
            previewImage.setAlpha(isEnabled() ? 1.0f : 0.2f);
        }
    }

    protected void onBindListItem(View view, int index) {
        int argb = getValueAt(index);
        ImageView swatch = (ImageView) view.findViewById(R.id.color_swatch);
        if (Color.alpha(argb) < 255) {
            swatch.setBackgroundResource(R.drawable.transparency_tileable);
        } else {
            swatch.setBackground(null);
        }
        Drawable foreground = swatch.getDrawable();
        if (foreground instanceof ColorDrawable) {
            ((ColorDrawable) foreground).setColor(argb);
        } else {
            swatch.setImageDrawable(new ColorDrawable(argb));
        }
        CharSequence title = getTitleAt(index);
        if (title != null) {
            ((TextView) view.findViewById(R.id.summary)).setText(title);
        }
    }
}
