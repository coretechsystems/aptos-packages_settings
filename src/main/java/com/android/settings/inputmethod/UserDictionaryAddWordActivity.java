package com.android.settings.inputmethod;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.view.View;
import com.android.settings.R;

public class UserDictionaryAddWordActivity extends Activity {
    private UserDictionaryAddWordContents mContents;

    public void onCreate(Bundle savedInstanceState) {
        int mode;
        super.onCreate(savedInstanceState);
        setContentView(R.layout.user_dictionary_add_word);
        Intent intent = getIntent();
        String action = intent.getAction();
        if ("com.android.settings.USER_DICTIONARY_EDIT".equals(action)) {
            mode = 0;
        } else if ("com.android.settings.USER_DICTIONARY_INSERT".equals(action)) {
            mode = 1;
        } else {
            throw new RuntimeException("Unsupported action: " + action);
        }
        Bundle args = intent.getExtras();
        args.putInt("mode", mode);
        if (savedInstanceState != null) {
            args.putAll(savedInstanceState);
        }
        this.mContents = new UserDictionaryAddWordContents(getWindow().getDecorView(), args);
    }

    public void onSaveInstanceState(Bundle outState) {
        this.mContents.saveStateIntoBundle(outState);
    }

    private void reportBackToCaller(int resultCode, Bundle result) {
        Messenger listener = getIntent().getExtras().get("listener");
        if (listener instanceof Messenger) {
            Messenger messenger = listener;
            Message m = Message.obtain();
            m.obj = result;
            m.what = resultCode;
            try {
                messenger.send(m);
            } catch (RemoteException e) {
            }
        }
    }

    public void onClickCancel(View v) {
        reportBackToCaller(1, null);
        finish();
    }

    public void onClickConfirm(View v) {
        Bundle parameters = new Bundle();
        reportBackToCaller(this.mContents.apply(this, parameters), parameters);
        finish();
    }
}
