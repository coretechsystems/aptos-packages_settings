package com.android.settings.wifi;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.NetworkInfo;
import android.net.NetworkInfo.DetailedState;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.WpsCallback;
import android.net.wifi.WpsInfo;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import com.android.settings.R;
import java.util.Timer;
import java.util.TimerTask;

public class WpsDialog extends AlertDialog {
    private Button mButton;
    private Context mContext;
    DialogState mDialogState = DialogState.WPS_INIT;
    private final IntentFilter mFilter;
    private Handler mHandler = new Handler();
    private String mMsgString = "";
    private ProgressBar mProgressBar;
    private BroadcastReceiver mReceiver;
    private TextView mTextView;
    private ProgressBar mTimeoutBar;
    private Timer mTimer;
    private View mView;
    private WifiManager mWifiManager;
    private WpsCallback mWpsListener;
    private int mWpsSetup;

    static /* synthetic */ class AnonymousClass5 {
        static final /* synthetic */ int[] $SwitchMap$com$android$settings$wifi$WpsDialog$DialogState = new int[DialogState.values().length];

        static {
            try {
                $SwitchMap$com$android$settings$wifi$WpsDialog$DialogState[DialogState.WPS_COMPLETE.ordinal()] = 1;
            } catch (NoSuchFieldError e) {
            }
            try {
                $SwitchMap$com$android$settings$wifi$WpsDialog$DialogState[DialogState.CONNECTED.ordinal()] = 2;
            } catch (NoSuchFieldError e2) {
            }
            try {
                $SwitchMap$com$android$settings$wifi$WpsDialog$DialogState[DialogState.WPS_FAILED.ordinal()] = 3;
            } catch (NoSuchFieldError e3) {
            }
        }
    }

    private enum DialogState {
        WPS_INIT,
        WPS_START,
        WPS_COMPLETE,
        CONNECTED,
        WPS_FAILED
    }

    public WpsDialog(Context context, int wpsSetup) {
        super(context);
        this.mContext = context;
        this.mWpsSetup = wpsSetup;
        this.mWpsListener = new WpsCallback() {
            public void onStarted(String pin) {
                if (pin != null) {
                    WpsDialog.this.updateDialog(DialogState.WPS_START, String.format(WpsDialog.this.mContext.getString(R.string.wifi_wps_onstart_pin), new Object[]{pin}));
                    return;
                }
                WpsDialog.this.updateDialog(DialogState.WPS_START, WpsDialog.this.mContext.getString(R.string.wifi_wps_onstart_pbc));
            }

            public void onSucceeded() {
                WpsDialog.this.updateDialog(DialogState.WPS_COMPLETE, WpsDialog.this.mContext.getString(R.string.wifi_wps_complete));
            }

            public void onFailed(int reason) {
                String msg;
                switch (reason) {
                    case 1:
                        msg = WpsDialog.this.mContext.getString(R.string.wifi_wps_in_progress);
                        break;
                    case 3:
                        msg = WpsDialog.this.mContext.getString(R.string.wifi_wps_failed_overlap);
                        break;
                    case 4:
                        msg = WpsDialog.this.mContext.getString(R.string.wifi_wps_failed_wep);
                        break;
                    case 5:
                        msg = WpsDialog.this.mContext.getString(R.string.wifi_wps_failed_tkip);
                        break;
                    default:
                        msg = WpsDialog.this.mContext.getString(R.string.wifi_wps_failed_generic);
                        break;
                }
                WpsDialog.this.updateDialog(DialogState.WPS_FAILED, msg);
            }
        };
        this.mFilter = new IntentFilter();
        this.mFilter.addAction("android.net.wifi.STATE_CHANGE");
        this.mReceiver = new BroadcastReceiver() {
            public void onReceive(Context context, Intent intent) {
                WpsDialog.this.handleEvent(context, intent);
            }
        };
        setCanceledOnTouchOutside(false);
    }

    public Bundle onSaveInstanceState() {
        Bundle bundle = super.onSaveInstanceState();
        bundle.putString("android:dialogState", this.mDialogState.toString());
        bundle.putString("android:dialogMsg", this.mMsgString.toString());
        return bundle;
    }

    public void onRestoreInstanceState(Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            super.onRestoreInstanceState(savedInstanceState);
            DialogState dialogState = this.mDialogState;
            updateDialog(DialogState.valueOf(savedInstanceState.getString("android:dialogState")), savedInstanceState.getString("android:dialogMsg"));
        }
    }

    protected void onCreate(Bundle savedInstanceState) {
        this.mView = getLayoutInflater().inflate(R.layout.wifi_wps_dialog, null);
        this.mTextView = (TextView) this.mView.findViewById(R.id.wps_dialog_txt);
        this.mTextView.setText(R.string.wifi_wps_setup_msg);
        this.mTimeoutBar = (ProgressBar) this.mView.findViewById(R.id.wps_timeout_bar);
        this.mTimeoutBar.setMax(120);
        this.mTimeoutBar.setProgress(0);
        this.mProgressBar = (ProgressBar) this.mView.findViewById(R.id.wps_progress_bar);
        this.mProgressBar.setVisibility(8);
        this.mButton = (Button) this.mView.findViewById(R.id.wps_dialog_btn);
        this.mButton.setText(R.string.wifi_cancel);
        this.mButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                WpsDialog.this.dismiss();
            }
        });
        this.mWifiManager = (WifiManager) this.mContext.getSystemService("wifi");
        setView(this.mView);
        super.onCreate(savedInstanceState);
    }

    protected void onStart() {
        this.mTimer = new Timer(false);
        this.mTimer.schedule(new TimerTask() {
            public void run() {
                WpsDialog.this.mHandler.post(new Runnable() {
                    public void run() {
                        WpsDialog.this.mTimeoutBar.incrementProgressBy(1);
                    }
                });
            }
        }, 1000, 1000);
        this.mContext.registerReceiver(this.mReceiver, this.mFilter);
        WpsInfo wpsConfig = new WpsInfo();
        wpsConfig.setup = this.mWpsSetup;
        this.mWifiManager.startWps(wpsConfig, this.mWpsListener);
    }

    protected void onStop() {
        if (this.mDialogState != DialogState.WPS_COMPLETE) {
            this.mWifiManager.cancelWps(null);
        }
        if (this.mReceiver != null) {
            this.mContext.unregisterReceiver(this.mReceiver);
            this.mReceiver = null;
        }
        if (this.mTimer != null) {
            this.mTimer.cancel();
        }
    }

    private void updateDialog(final DialogState state, final String msg) {
        if (this.mDialogState.ordinal() < state.ordinal()) {
            this.mDialogState = state;
            this.mMsgString = msg;
            this.mHandler.post(new Runnable() {
                public void run() {
                    switch (AnonymousClass5.$SwitchMap$com$android$settings$wifi$WpsDialog$DialogState[state.ordinal()]) {
                        case 1:
                            WpsDialog.this.mTimeoutBar.setVisibility(8);
                            WpsDialog.this.mProgressBar.setVisibility(0);
                            break;
                        case 2:
                        case 3:
                            WpsDialog.this.mButton.setText(WpsDialog.this.mContext.getString(R.string.dlg_ok));
                            WpsDialog.this.mTimeoutBar.setVisibility(8);
                            WpsDialog.this.mProgressBar.setVisibility(8);
                            if (WpsDialog.this.mReceiver != null) {
                                WpsDialog.this.mContext.unregisterReceiver(WpsDialog.this.mReceiver);
                                WpsDialog.this.mReceiver = null;
                                break;
                            }
                            break;
                    }
                    WpsDialog.this.mTextView.setText(msg);
                }
            });
        }
    }

    private void handleEvent(Context context, Intent intent) {
        if ("android.net.wifi.STATE_CHANGE".equals(intent.getAction()) && ((NetworkInfo) intent.getParcelableExtra("networkInfo")).getDetailedState() == DetailedState.CONNECTED && this.mDialogState == DialogState.WPS_COMPLETE && this.mWifiManager.getConnectionInfo() != null) {
            updateDialog(DialogState.CONNECTED, String.format(this.mContext.getString(R.string.wifi_wps_connected), new Object[]{wifiInfo.getSSID()}));
        }
    }
}
