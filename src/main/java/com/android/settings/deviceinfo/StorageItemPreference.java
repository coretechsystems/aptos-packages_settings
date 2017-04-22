package com.android.settings.deviceinfo;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.RectShape;
import android.preference.Preference;
import com.android.settings.R;

public class StorageItemPreference extends Preference {
    public final int color;
    public final int userHandle;

    public StorageItemPreference(Context context, int titleRes, int colorRes) {
        this(context, context.getText(titleRes), colorRes, -10000);
    }

    public StorageItemPreference(Context context, CharSequence title, int colorRes, int userHandle) {
        super(context);
        if (colorRes != 0) {
            this.color = context.getResources().getColor(colorRes);
            Resources res = context.getResources();
            setIcon(createRectShape(res.getDimensionPixelSize(R.dimen.device_memory_usage_button_width), res.getDimensionPixelSize(R.dimen.device_memory_usage_button_height), this.color));
        } else {
            this.color = -65281;
        }
        setTitle(title);
        setSummary(R.string.memory_calculating_size);
        this.userHandle = userHandle;
    }

    private static ShapeDrawable createRectShape(int width, int height, int color) {
        ShapeDrawable shape = new ShapeDrawable(new RectShape());
        shape.setIntrinsicHeight(height);
        shape.setIntrinsicWidth(width);
        shape.getPaint().setColor(color);
        return shape;
    }
}
