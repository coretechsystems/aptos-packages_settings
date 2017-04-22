package com.android.settings.users;

import android.app.Activity;
import android.app.AlertDialog.Builder;
import android.app.Dialog;
import android.app.Fragment;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.UserHandle;
import android.os.UserManager;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import com.android.settings.R;
import com.android.settings.drawable.CircleFramedDrawable;

public class EditUserInfoController {
    private Dialog mEditUserInfoDialog;
    private EditUserPhotoController mEditUserPhotoController;
    private Bitmap mSavedPhoto;
    private UserHandle mUser;
    private UserManager mUserManager;
    private boolean mWaitingForActivityResult = false;

    public interface OnContentChangedCallback {
        void onLabelChanged(CharSequence charSequence);

        void onPhotoChanged(Drawable drawable);
    }

    public void clear() {
        this.mEditUserInfoDialog = null;
        this.mSavedPhoto = null;
    }

    public void onRestoreInstanceState(Bundle icicle) {
        this.mSavedPhoto = (Bitmap) icicle.getParcelable("pending_photo");
        this.mWaitingForActivityResult = icicle.getBoolean("awaiting_result", false);
    }

    public void onSaveInstanceState(Bundle outState) {
        if (!(this.mEditUserInfoDialog == null || !this.mEditUserInfoDialog.isShowing() || this.mEditUserPhotoController == null)) {
            outState.putParcelable("pending_photo", this.mEditUserPhotoController.getNewUserPhotoBitmap());
        }
        if (this.mWaitingForActivityResult) {
            outState.putBoolean("awaiting_result", this.mWaitingForActivityResult);
        }
    }

    public void startingActivityForResult() {
        this.mWaitingForActivityResult = true;
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        this.mWaitingForActivityResult = false;
        if (this.mEditUserInfoDialog != null && this.mEditUserInfoDialog.isShowing() && !this.mEditUserPhotoController.onActivityResult(requestCode, resultCode, data)) {
        }
    }

    Drawable getCircularUserIcon(Activity activity) {
        Bitmap userIcon = this.mUserManager.getUserIcon(this.mUser.getIdentifier());
        if (userIcon == null) {
            return null;
        }
        return CircleFramedDrawable.getInstance(activity, userIcon);
    }

    public Dialog createDialog(Fragment fragment, Drawable currentUserIcon, CharSequence currentUserName, int titleResId, OnContentChangedCallback callback, UserHandle user) {
        Drawable drawable;
        Activity activity = fragment.getActivity();
        this.mUser = user;
        if (this.mUserManager == null) {
            this.mUserManager = UserManager.get(activity);
        }
        View content = activity.getLayoutInflater().inflate(R.layout.edit_user_info_dialog_content, null);
        final EditText userNameView = (EditText) content.findViewById(R.id.user_name);
        userNameView.setText(this.mUserManager.getUserInfo(this.mUser.getIdentifier()).name);
        ImageView userPhotoView = (ImageView) content.findViewById(R.id.user_photo);
        if (this.mSavedPhoto != null) {
            drawable = CircleFramedDrawable.getInstance(activity, this.mSavedPhoto);
        } else {
            drawable = currentUserIcon;
            if (drawable == null) {
                drawable = getCircularUserIcon(activity);
            }
        }
        userPhotoView.setImageDrawable(drawable);
        this.mEditUserPhotoController = new EditUserPhotoController(fragment, userPhotoView, this.mSavedPhoto, drawable, this.mWaitingForActivityResult);
        final CharSequence charSequence = currentUserName;
        final OnContentChangedCallback onContentChangedCallback = callback;
        final Drawable drawable2 = currentUserIcon;
        final Fragment fragment2 = fragment;
        this.mEditUserInfoDialog = new Builder(activity).setTitle(R.string.profile_info_settings_title).setView(content).setCancelable(true).setPositiveButton(17039370, new OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                if (which == -1) {
                    CharSequence userName = userNameView.getText();
                    if (!TextUtils.isEmpty(userName) && (charSequence == null || !userName.toString().equals(charSequence.toString()))) {
                        if (onContentChangedCallback != null) {
                            onContentChangedCallback.onLabelChanged(userName.toString());
                        }
                        EditUserInfoController.this.mUserManager.setUserName(EditUserInfoController.this.mUser.getIdentifier(), userName.toString());
                    }
                    Drawable drawable = EditUserInfoController.this.mEditUserPhotoController.getNewUserPhotoDrawable();
                    Bitmap bitmap = EditUserInfoController.this.mEditUserPhotoController.getNewUserPhotoBitmap();
                    if (!(drawable == null || bitmap == null || drawable.equals(drawable2))) {
                        if (onContentChangedCallback != null) {
                            onContentChangedCallback.onPhotoChanged(drawable);
                        }
                        new AsyncTask<Void, Void, Void>() {
                            protected Void doInBackground(Void... params) {
                                EditUserInfoController.this.mUserManager.setUserIcon(EditUserInfoController.this.mUser.getIdentifier(), EditUserInfoController.this.mEditUserPhotoController.getNewUserPhotoBitmap());
                                return null;
                            }
                        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, (Void[]) null);
                    }
                    fragment2.getActivity().removeDialog(1);
                }
                EditUserInfoController.this.clear();
            }
        }).setNegativeButton(17039360, new OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                EditUserInfoController.this.clear();
            }
        }).create();
        this.mEditUserInfoDialog.getWindow().setSoftInputMode(4);
        return this.mEditUserInfoDialog;
    }
}
