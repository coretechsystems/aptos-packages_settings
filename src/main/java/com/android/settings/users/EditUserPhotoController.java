package com.android.settings.users;

import android.app.Fragment;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.provider.ContactsContract.DisplayPhoto;
import android.support.v4.content.FileProvider;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListPopupWindow;
import com.android.settings.R;
import com.android.settings.drawable.CircleFramedDrawable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class EditUserPhotoController {
    private final Context mContext;
    private final Uri mCropPictureUri;
    private final Fragment mFragment;
    private final ImageView mImageView;
    private Bitmap mNewUserPhotoBitmap;
    private Drawable mNewUserPhotoDrawable;
    private final int mPhotoSize;
    private final Uri mTakePictureUri;

    private static final class AdapterItem {
        final int id;
        final String title;

        public AdapterItem(String title, int id) {
            this.title = title;
            this.id = id;
        }

        public String toString() {
            return this.title;
        }
    }

    public EditUserPhotoController(Fragment fragment, ImageView view, Bitmap bitmap, Drawable drawable, boolean waiting) {
        boolean z = true;
        this.mContext = view.getContext();
        this.mFragment = fragment;
        this.mImageView = view;
        this.mCropPictureUri = createTempImageUri(this.mContext, "CropEditUserPhoto.jpg", !waiting);
        Context context = this.mContext;
        String str = "TakeEditUserPhoto2.jpg";
        if (waiting) {
            z = false;
        }
        this.mTakePictureUri = createTempImageUri(context, str, z);
        this.mPhotoSize = getPhotoSize(this.mContext);
        this.mImageView.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                EditUserPhotoController.this.showUpdatePhotoPopup();
            }
        });
        this.mNewUserPhotoBitmap = bitmap;
        this.mNewUserPhotoDrawable = drawable;
    }

    public boolean onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode != -1) {
            return false;
        }
        Uri pictureUri = (data == null || data.getData() == null) ? this.mTakePictureUri : data.getData();
        switch (requestCode) {
            case 1001:
            case 1002:
                cropPhoto(pictureUri);
                return true;
            case 1003:
                onPhotoCropped(pictureUri, true);
                return true;
            default:
                return false;
        }
    }

    public Bitmap getNewUserPhotoBitmap() {
        return this.mNewUserPhotoBitmap;
    }

    public Drawable getNewUserPhotoDrawable() {
        return this.mNewUserPhotoDrawable;
    }

    private void showUpdatePhotoPopup() {
        boolean canTakePhoto = canTakePhoto();
        boolean canChoosePhoto = canChoosePhoto();
        if (canTakePhoto || canChoosePhoto) {
            Context context = this.mImageView.getContext();
            final List<AdapterItem> items = new ArrayList();
            if (canTakePhoto()) {
                items.add(new AdapterItem(this.mImageView.getContext().getString(R.string.user_image_take_photo), 2));
            }
            if (canChoosePhoto) {
                items.add(new AdapterItem(context.getString(R.string.user_image_choose_photo), 1));
            }
            final ListPopupWindow listPopupWindow = new ListPopupWindow(context);
            listPopupWindow.setAnchorView(this.mImageView);
            listPopupWindow.setModal(true);
            listPopupWindow.setInputMethodMode(2);
            listPopupWindow.setAdapter(new ArrayAdapter(context, R.layout.edit_user_photo_popup_item, items));
            listPopupWindow.setWidth(Math.max(this.mImageView.getWidth(), context.getResources().getDimensionPixelSize(R.dimen.update_user_photo_popup_min_width)));
            listPopupWindow.setOnItemClickListener(new OnItemClickListener() {
                public void onItemClick(AdapterView<?> adapterView, View view, int position, long id) {
                    switch (((AdapterItem) items.get(position)).id) {
                        case 1:
                            EditUserPhotoController.this.choosePhoto();
                            listPopupWindow.dismiss();
                            return;
                        case 2:
                            EditUserPhotoController.this.takePhoto();
                            listPopupWindow.dismiss();
                            return;
                        default:
                            return;
                    }
                }
            });
            listPopupWindow.show();
        }
    }

    private boolean canTakePhoto() {
        return this.mImageView.getContext().getPackageManager().queryIntentActivities(new Intent("android.media.action.IMAGE_CAPTURE"), 65536).size() > 0;
    }

    private boolean canChoosePhoto() {
        Intent intent = new Intent("android.intent.action.GET_CONTENT");
        intent.setType("image/*");
        if (this.mImageView.getContext().getPackageManager().queryIntentActivities(intent, 0).size() > 0) {
            return true;
        }
        return false;
    }

    private void takePhoto() {
        Intent intent = new Intent("android.media.action.IMAGE_CAPTURE");
        appendOutputExtra(intent, this.mTakePictureUri);
        this.mFragment.startActivityForResult(intent, 1002);
    }

    private void choosePhoto() {
        Intent intent = new Intent("android.intent.action.GET_CONTENT", null);
        intent.setType("image/*");
        appendOutputExtra(intent, this.mTakePictureUri);
        this.mFragment.startActivityForResult(intent, 1001);
    }

    private void cropPhoto(Uri pictureUri) {
        Intent intent = new Intent("com.android.camera.action.CROP");
        intent.setDataAndType(pictureUri, "image/*");
        appendOutputExtra(intent, this.mCropPictureUri);
        appendCropExtras(intent);
        if (intent.resolveActivity(this.mContext.getPackageManager()) != null) {
            this.mFragment.startActivityForResult(intent, 1003);
        } else {
            onPhotoCropped(pictureUri, false);
        }
    }

    private void appendOutputExtra(Intent intent, Uri pictureUri) {
        intent.putExtra("output", pictureUri);
        intent.addFlags(3);
        intent.setClipData(ClipData.newRawUri("output", pictureUri));
    }

    private void appendCropExtras(Intent intent) {
        intent.putExtra("crop", "true");
        intent.putExtra("scale", true);
        intent.putExtra("scaleUpIfNeeded", true);
        intent.putExtra("aspectX", 1);
        intent.putExtra("aspectY", 1);
        intent.putExtra("outputX", this.mPhotoSize);
        intent.putExtra("outputY", this.mPhotoSize);
    }

    private void onPhotoCropped(final Uri data, final boolean cropped) {
        new AsyncTask<Void, Void, Bitmap>() {
            protected Bitmap doInBackground(Void... params) {
                Bitmap decodeStream;
                if (cropped) {
                    InputStream imageStream = null;
                    try {
                        imageStream = EditUserPhotoController.this.mContext.getContentResolver().openInputStream(data);
                        decodeStream = BitmapFactory.decodeStream(imageStream);
                        if (imageStream == null) {
                            return decodeStream;
                        }
                        try {
                            imageStream.close();
                            return decodeStream;
                        } catch (IOException ioe) {
                            Log.w("EditUserPhotoController", "Cannot close image stream", ioe);
                            return decodeStream;
                        }
                    } catch (FileNotFoundException fe) {
                        Log.w("EditUserPhotoController", "Cannot find image file", fe);
                        if (imageStream == null) {
                            return null;
                        }
                        try {
                            imageStream.close();
                            return null;
                        } catch (IOException ioe2) {
                            Log.w("EditUserPhotoController", "Cannot close image stream", ioe2);
                            return null;
                        }
                    } catch (Throwable th) {
                        if (imageStream != null) {
                            try {
                                imageStream.close();
                            } catch (IOException ioe22) {
                                Log.w("EditUserPhotoController", "Cannot close image stream", ioe22);
                            }
                        }
                    }
                } else {
                    decodeStream = Bitmap.createBitmap(EditUserPhotoController.this.mPhotoSize, EditUserPhotoController.this.mPhotoSize, Config.ARGB_8888);
                    Canvas canvas = new Canvas(decodeStream);
                    try {
                        Bitmap fullImage = BitmapFactory.decodeStream(EditUserPhotoController.this.mContext.getContentResolver().openInputStream(data));
                        if (fullImage == null) {
                            return null;
                        }
                        int squareSize = Math.min(fullImage.getWidth(), fullImage.getHeight());
                        int left = (fullImage.getWidth() - squareSize) / 2;
                        int top = (fullImage.getHeight() - squareSize) / 2;
                        canvas.drawBitmap(fullImage, new Rect(left, top, left + squareSize, top + squareSize), new Rect(0, 0, EditUserPhotoController.this.mPhotoSize, EditUserPhotoController.this.mPhotoSize), new Paint());
                        return decodeStream;
                    } catch (FileNotFoundException e) {
                        return null;
                    }
                }
            }

            protected void onPostExecute(Bitmap bitmap) {
                if (bitmap != null) {
                    EditUserPhotoController.this.mNewUserPhotoBitmap = bitmap;
                    EditUserPhotoController.this.mNewUserPhotoDrawable = CircleFramedDrawable.getInstance(EditUserPhotoController.this.mImageView.getContext(), EditUserPhotoController.this.mNewUserPhotoBitmap);
                    EditUserPhotoController.this.mImageView.setImageDrawable(EditUserPhotoController.this.mNewUserPhotoDrawable);
                }
                new File(EditUserPhotoController.this.mContext.getCacheDir(), "TakeEditUserPhoto2.jpg").delete();
                new File(EditUserPhotoController.this.mContext.getCacheDir(), "CropEditUserPhoto.jpg").delete();
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, (Void[]) null);
    }

    private static int getPhotoSize(Context context) {
        Cursor cursor = context.getContentResolver().query(DisplayPhoto.CONTENT_MAX_DIMENSIONS_URI, new String[]{"display_max_dim"}, null, null, null);
        try {
            cursor.moveToFirst();
            int i = cursor.getInt(0);
            return i;
        } finally {
            cursor.close();
        }
    }

    private Uri createTempImageUri(Context context, String fileName, boolean purge) {
        File folder = context.getCacheDir();
        folder.mkdirs();
        File fullPath = new File(folder, fileName);
        if (purge) {
            fullPath.delete();
        }
        return FileProvider.getUriForFile(context, "com.android.settings.files", fullPath);
    }
}
