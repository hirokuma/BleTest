package com.blogpost.hiro99ma.bletest;

import java.util.UUID;

import android.app.Activity;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.widget.Toast;

import com.blogpost.hiro99ma.bleutils.BleUtils;

public class MainActivity extends Activity {

    private static final String TAG = "MainActivity";
    private static final String BLE_DEVICE_NAME = "SensorTag";
    private static final int BLE_SCANTIME = 5000;
    private static final UUID SERVICE = UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb");
    private static final UUID CH = UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb");
    private static final UUID CONFIG = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    private boolean mDetect = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        int ret = BleUtils.onCreate(this);
        if (ret != BleUtils.SUCCESS) {
            Toast.makeText(this, "Sorry. Cannot start.", Toast.LENGTH_LONG).show();
            finish();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (BleUtils.onResume(this) != BleUtils.SUCCESS) {
            Toast.makeText(this, "fail... BLE cannot resume", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        if (!mDetect) {
            //名前で検索
            int ret = BleUtils.startScan(BLE_DEVICE_NAME, BLE_SCANTIME, mScanResultCallback);
            if (ret == BleUtils.SUCCESS) {
                Toast.makeText(this, "scanning...", Toast.LENGTH_LONG).show();
            } else {
                if (ret == BleUtils.ESCANNING) {
                    Toast.makeText(this, "scanning now", Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(this, "oops... error", Toast.LENGTH_LONG).show();
                    finish();
                    return;
                }
            }
        }
    }

    private BleUtils.scanResultCallback mScanResultCallback = new BleUtils.scanResultCallback() {
        @Override
        public void onResult(final int result, final int rssi, final byte[] scanRecord) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (result == BleUtils.SUCCESS) {
                        //検出成功
                        mDetect = true;

                        BleUtils.startConnect(getApplicationContext(), SERVICE, CH, mConnectResultCallback);
                    } else {
                        //検出できず
                        Toast.makeText(getApplicationContext(), getString(R.string.scan_fail), Toast.LENGTH_LONG).show();
                        mDetect = false;
                    }
                }
            });
        }
    };

    private BleUtils.connectResultCallback mConnectResultCallback = new BleUtils.connectResultCallback() {
        @Override
        public BleUtils.characteristicChangedCallback onConnect(final int result, final BluetoothGatt gatt, final BluetoothGattCharacteristic ch) {
            Log.d(TAG, "onConnect : " + result);

            boolean registered = gatt.setCharacteristicNotification(ch, true);
            if (registered) {
                Log.d(TAG, "  Notification REQ success");

                BluetoothGattDescriptor desc = ch.getDescriptor(CONFIG);
                desc.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                gatt.writeDescriptor(desc);
            } else {
                Log.e(TAG, "  Notification REQ fail");
            }

            return mChChangedCallback;
        }
    };

    private BleUtils.characteristicChangedCallback mChChangedCallback = new BleUtils.characteristicChangedCallback() {
        @Override
        public void onChanged(final BluetoothGatt gatt, final BluetoothGattCharacteristic ch) {
            Log.d(TAG, "onChanged");
        }
    };

    @Override
    protected void onPause() {
        super.onPause();

        BleUtils.onPause(this);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (BleUtils.onActivityResult(this, requestCode, resultCode, data) != BleUtils.SUCCESS) {
            Toast.makeText(this, "Sorry. Cannot use BT.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }
}
