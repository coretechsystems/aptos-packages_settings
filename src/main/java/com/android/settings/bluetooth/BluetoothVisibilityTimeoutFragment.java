package com.android.settings.bluetooth;

import android.app.AlertDialog.Builder;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.os.Bundle;
import com.android.settings.R;

public final class BluetoothVisibilityTimeoutFragment extends DialogFragment implements OnClickListener {
    private final BluetoothDiscoverableEnabler mDiscoverableEnabler = LocalBluetoothManager.getInstance(getActivity()).getDiscoverableEnabler();

    public Dialog onCreateDialog(Bundle savedInstanceState) {
        return new Builder(getActivity()).setTitle(R.string.bluetooth_visibility_timeout).setSingleChoiceItems(R.array.bluetooth_visibility_timeout_entries, this.mDiscoverableEnabler.getDiscoverableTimeoutIndex(), this).setNegativeButton(17039360, null).create();
    }

    public void onClick(DialogInterface dialog, int which) {
        this.mDiscoverableEnabler.setDiscoverableTimeout(which);
        dismiss();
    }
}
