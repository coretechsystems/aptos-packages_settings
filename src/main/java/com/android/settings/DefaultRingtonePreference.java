package com.android.settings;

import android.content.Context;
import android.content.Intent;
import android.media.RingtoneManager;
import android.net.Uri;
import android.preference.RingtonePreference;
import android.util.AttributeSet;

public class DefaultRingtonePreference extends RingtonePreference {
    public DefaultRingtonePreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    protected void onPrepareRingtonePickerIntent(Intent ringtonePickerIntent) {
        super.onPrepareRingtonePickerIntent(ringtonePickerIntent);
        ringtonePickerIntent.putExtra("android.intent.extra.ringtone.SHOW_DEFAULT", false);
    }

    protected void onSaveRingtone(Uri ringtoneUri) {
        RingtoneManager.setActualDefaultRingtoneUri(getContext(), getRingtoneType(), ringtoneUri);
    }

    protected Uri onRestoreRingtone() {
        return RingtoneManager.getActualDefaultRingtoneUri(getContext(), getRingtoneType());
    }
}
