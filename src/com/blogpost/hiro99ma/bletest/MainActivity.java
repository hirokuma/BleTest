package com.blogpost.hiro99ma.bletest;

import com.blogpost.hiro99ma.bleutils.BleUtils;

import android.os.Bundle;
import android.app.Activity;
import android.content.Intent;
import android.util.Log;
import android.view.Menu;
import android.widget.Toast;

public class MainActivity extends Activity {
    
    private static final String TAG = "MainActivity";

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
			finish();
			return;
		}
		
		//5秒検索
		int ret = BleUtils.startScan("PaSoRi", 5000, mScanResultCallback);
		if (ret == BleUtils.SUCCESS) {
			Toast.makeText(this, "scanning...", Toast.LENGTH_LONG).show();
		} else {
			if (ret == BleUtils.ESCANNING) {
				Toast.makeText(this, "scanning now", Toast.LENGTH_LONG).show();
			} else {
				finish();
				return;
			}
		}
	}
	
	private BleUtils.scanResultCallback mScanResultCallback = new BleUtils.scanResultCallback() {
        
        @Override
        public void onResult(final int result) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    String str;
                    if (result == BleUtils.SUCCESS) {
                        str = "detect";
                    } else {
                        str = "not found";
                    }
                    Toast.makeText(getApplicationContext(), str, Toast.LENGTH_LONG).show();
                }
            });
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
