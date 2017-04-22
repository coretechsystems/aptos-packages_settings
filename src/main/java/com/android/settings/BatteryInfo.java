package com.android.settings;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.IPowerManager;
import android.os.Message;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.text.format.DateUtils;
import android.widget.TextView;
import com.android.internal.app.IBatteryStats;
import com.android.internal.app.IBatteryStats.Stub;

public class BatteryInfo extends Activity {
    private IBatteryStats mBatteryStats;
    private Handler mHandler = new Handler() {
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case 1:
                    BatteryInfo.this.updateBatteryStats();
                    sendEmptyMessageDelayed(1, 1000);
                    return;
                default:
                    return;
            }
        }
    };
    private TextView mHealth;
    private IntentFilter mIntentFilter;
    private BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals("android.intent.action.BATTERY_CHANGED")) {
                String healthString;
                int plugType = intent.getIntExtra("plugged", 0);
                BatteryInfo.this.mLevel.setText("" + intent.getIntExtra("level", 0));
                BatteryInfo.this.mScale.setText("" + intent.getIntExtra("scale", 0));
                BatteryInfo.this.mVoltage.setText("" + intent.getIntExtra("voltage", 0) + " " + BatteryInfo.this.getString(R.string.battery_info_voltage_units));
                BatteryInfo.this.mTemperature.setText("" + BatteryInfo.this.tenthsToFixedString(intent.getIntExtra("temperature", 0)) + BatteryInfo.this.getString(R.string.battery_info_temperature_units));
                BatteryInfo.this.mTechnology.setText("" + intent.getStringExtra("technology"));
                BatteryInfo.this.mStatus.setText(Utils.getBatteryStatus(BatteryInfo.this.getResources(), intent));
                switch (plugType) {
                    case 0:
                        BatteryInfo.this.mPower.setText(BatteryInfo.this.getString(R.string.battery_info_power_unplugged));
                        break;
                    case 1:
                        BatteryInfo.this.mPower.setText(BatteryInfo.this.getString(R.string.battery_info_power_ac));
                        break;
                    case 2:
                        BatteryInfo.this.mPower.setText(BatteryInfo.this.getString(R.string.battery_info_power_usb));
                        break;
                    case 3:
                        BatteryInfo.this.mPower.setText(BatteryInfo.this.getString(R.string.battery_info_power_ac_usb));
                        break;
                    case 4:
                        BatteryInfo.this.mPower.setText(BatteryInfo.this.getString(R.string.battery_info_power_wireless));
                        break;
                    default:
                        BatteryInfo.this.mPower.setText(BatteryInfo.this.getString(R.string.battery_info_power_unknown));
                        break;
                }
                int health = intent.getIntExtra("health", 1);
                if (health == 2) {
                    healthString = BatteryInfo.this.getString(R.string.battery_info_health_good);
                } else if (health == 3) {
                    healthString = BatteryInfo.this.getString(R.string.battery_info_health_overheat);
                } else if (health == 4) {
                    healthString = BatteryInfo.this.getString(R.string.battery_info_health_dead);
                } else if (health == 5) {
                    healthString = BatteryInfo.this.getString(R.string.battery_info_health_over_voltage);
                } else if (health == 6) {
                    healthString = BatteryInfo.this.getString(R.string.battery_info_health_unspecified_failure);
                } else if (health == 7) {
                    healthString = BatteryInfo.this.getString(R.string.battery_info_health_cold);
                } else {
                    healthString = BatteryInfo.this.getString(R.string.battery_info_health_unknown);
                }
                BatteryInfo.this.mHealth.setText(healthString);
            }
        }
    };
    private TextView mLevel;
    private TextView mPower;
    private TextView mScale;
    private IPowerManager mScreenStats;
    private TextView mStatus;
    private TextView mTechnology;
    private TextView mTemperature;
    private TextView mUptime;
    private TextView mVoltage;

    private final String tenthsToFixedString(int x) {
        int tens = x / 10;
        return Integer.toString(tens) + "." + Math.abs(x - (tens * 10));
    }

    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        setContentView(R.layout.battery_info);
        this.mIntentFilter = new IntentFilter();
        this.mIntentFilter.addAction("android.intent.action.BATTERY_CHANGED");
    }

    public void onResume() {
        super.onResume();
        this.mStatus = (TextView) findViewById(R.id.status);
        this.mPower = (TextView) findViewById(R.id.power);
        this.mLevel = (TextView) findViewById(R.id.level);
        this.mScale = (TextView) findViewById(R.id.scale);
        this.mHealth = (TextView) findViewById(R.id.health);
        this.mTechnology = (TextView) findViewById(R.id.technology);
        this.mVoltage = (TextView) findViewById(R.id.voltage);
        this.mTemperature = (TextView) findViewById(R.id.temperature);
        this.mUptime = (TextView) findViewById(R.id.uptime);
        this.mBatteryStats = Stub.asInterface(ServiceManager.getService("batterystats"));
        this.mScreenStats = IPowerManager.Stub.asInterface(ServiceManager.getService("power"));
        this.mHandler.sendEmptyMessageDelayed(1, 1000);
        registerReceiver(this.mIntentReceiver, this.mIntentFilter);
    }

    public void onPause() {
        super.onPause();
        this.mHandler.removeMessages(1);
        unregisterReceiver(this.mIntentReceiver);
    }

    private void updateBatteryStats() {
        this.mUptime.setText(DateUtils.formatElapsedTime(SystemClock.elapsedRealtime() / 1000));
    }
}
