package com.android.settings.wifi;

import android.content.Context;
import android.net.NetworkInfo.DetailedState;
import com.android.settings.R;

class Summary {
    static String get(Context context, String ssid, DetailedState state) {
        String[] formats = context.getResources().getStringArray(ssid == null ? R.array.wifi_status : R.array.wifi_status_with_ssid);
        int index = state.ordinal();
        if (index >= formats.length || formats[index].length() == 0) {
            return null;
        }
        return String.format(formats[index], new Object[]{ssid});
    }

    static String get(Context context, DetailedState state) {
        return get(context, null, state);
    }
}
