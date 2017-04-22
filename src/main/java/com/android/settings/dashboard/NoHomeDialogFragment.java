package com.android.settings.dashboard;

import android.app.Activity;
import android.app.AlertDialog.Builder;
import android.app.Dialog;
import android.app.DialogFragment;
import android.os.Bundle;
import com.android.settings.R;

public class NoHomeDialogFragment extends DialogFragment {
    public static void show(Activity parent) {
        new NoHomeDialogFragment().show(parent.getFragmentManager(), null);
    }

    public Dialog onCreateDialog(Bundle savedInstanceState) {
        return new Builder(getActivity()).setMessage(R.string.only_one_home_message).setPositiveButton(17039370, null).create();
    }
}
