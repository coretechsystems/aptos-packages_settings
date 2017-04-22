package com.android.settings;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnDismissListener;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemProperties;
import android.text.TextUtils;
import android.util.Log;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.zip.GZIPInputStream;

public class SettingsLicenseActivity extends Activity {
    private Handler mHandler = null;
    private ProgressDialog mSpinnerDlg = null;
    private AlertDialog mTextDlg = null;
    private WebView mWebView = null;

    private class LicenseFileLoader implements Runnable {
        private String mFileName;
        private Handler mHandler;

        public LicenseFileLoader(String fileName, Handler handler) {
            this.mFileName = fileName;
            this.mHandler = handler;
        }

        public void run() {
            int status = 0;
            InputStreamReader inputReader = null;
            StringBuilder data = new StringBuilder(2048);
            try {
                char[] tmp = new char[2048];
                if (this.mFileName.endsWith(".gz")) {
                    inputReader = new InputStreamReader(new GZIPInputStream(new FileInputStream(this.mFileName)));
                } else {
                    inputReader = new FileReader(this.mFileName);
                }
                while (true) {
                    int numRead = inputReader.read(tmp);
                    if (numRead < 0) {
                        break;
                    }
                    data.append(tmp, 0, numRead);
                }
                if (inputReader != null) {
                    try {
                        inputReader.close();
                    } catch (IOException e) {
                    }
                }
            } catch (FileNotFoundException e2) {
                Log.e("SettingsLicenseActivity.LicenseFileLoader", "License HTML file not found at " + this.mFileName, e2);
                status = 1;
                if (inputReader != null) {
                    try {
                        inputReader.close();
                    } catch (IOException e3) {
                    }
                }
            } catch (FileNotFoundException e22) {
                Log.e("SettingsLicenseActivity.LicenseFileLoader", "Error reading license HTML file at " + this.mFileName, e22);
                status = 2;
                if (inputReader != null) {
                    try {
                        inputReader.close();
                    } catch (IOException e4) {
                    }
                }
            } catch (Throwable th) {
                if (inputReader != null) {
                    try {
                        inputReader.close();
                    } catch (IOException e5) {
                    }
                }
            }
            if (status == 0 && TextUtils.isEmpty(data)) {
                Log.e("SettingsLicenseActivity.LicenseFileLoader", "License HTML is empty (from " + this.mFileName + ")");
                status = 3;
            }
            Message msg = this.mHandler.obtainMessage(status, null);
            if (status == 0) {
                msg.obj = data.toString();
            }
            this.mHandler.sendMessage(msg);
        }
    }

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String fileName = SystemProperties.get("ro.config.license_path", "/system/etc/NOTICE.html.gz");
        if (TextUtils.isEmpty(fileName)) {
            Log.e("SettingsLicenseActivity", "The system property for the license file is empty.");
            showErrorAndFinish();
            return;
        }
        setVisible(false);
        this.mWebView = new WebView(this);
        this.mHandler = new Handler() {
            public void handleMessage(Message msg) {
                super.handleMessage(msg);
                if (msg.what == 0) {
                    SettingsLicenseActivity.this.showPageOfText(msg.obj);
                    return;
                }
                SettingsLicenseActivity.this.showErrorAndFinish();
            }
        };
        ProgressDialog pd = ProgressDialog.show(this, getText(R.string.settings_license_activity_title), getText(R.string.settings_license_activity_loading), true, false);
        pd.setProgressStyle(0);
        this.mSpinnerDlg = pd;
        new Thread(new LicenseFileLoader(fileName, this.mHandler)).start();
    }

    protected void onDestroy() {
        if (this.mTextDlg != null && this.mTextDlg.isShowing()) {
            this.mTextDlg.dismiss();
        }
        if (this.mSpinnerDlg != null && this.mSpinnerDlg.isShowing()) {
            this.mSpinnerDlg.dismiss();
        }
        super.onDestroy();
    }

    private void showPageOfText(String text) {
        Builder builder = new Builder(this);
        builder.setCancelable(true).setView(this.mWebView).setTitle(R.string.settings_license_activity_title);
        this.mTextDlg = builder.create();
        this.mTextDlg.setOnDismissListener(new OnDismissListener() {
            public void onDismiss(DialogInterface dlgi) {
                SettingsLicenseActivity.this.finish();
            }
        });
        this.mWebView.loadDataWithBaseURL(null, text, "text/html", "utf-8", null);
        this.mWebView.setWebViewClient(new WebViewClient() {
            public void onPageFinished(WebView view, String url) {
                SettingsLicenseActivity.this.mSpinnerDlg.dismiss();
                if (SettingsLicenseActivity.this.isResumed()) {
                    SettingsLicenseActivity.this.mTextDlg.show();
                }
            }
        });
        this.mWebView = null;
    }

    private void showErrorAndFinish() {
        this.mSpinnerDlg.dismiss();
        this.mSpinnerDlg = null;
        Toast.makeText(this, R.string.settings_license_activity_unavailable, 1).show();
        finish();
    }
}
