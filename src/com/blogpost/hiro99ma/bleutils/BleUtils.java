/**
 * 
 */
package com.blogpost.hiro99ma.bleutils;


import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.util.Log;

/**
 * @author hiroshi
 *
 */
public class BleUtils {

	//エラー値なんだけど・・・例外にするべき？
	public final static int SUCCESS = 0;
	public final static int EOPENED = -1;
	public final static int ENOTOPEN = -2;
	public final static int ENOTSUPPORT_BT = -3;
	public final static int ENOTSUPPORT_LE = -4;
	public final static int ESCANNING = -5;
	
	private static final String TAG = "BleUtils";
	private static final int REQUEST_ENABLE_BT = 1;

	private static BluetoothAdapter sBluetoothAdapter;
	private static Handler sHandler = new Handler();		//スキャン用
	private static boolean sScanning = false;
	private static String sScanDeviceName = null;

	
	/**
	 * BLEの使用準備
	 * @param context
	 * @return		SUCCESS:
	 */
	public static int onCreate(Context context) {
		//既にprepareしているかどうか
		if (sBluetoothAdapter != null) {
			Log.d(TAG, "already prepared.");
			return EOPENED;
		}
		
		//BTをサポートしているかどうか
		final BluetoothManager man = (BluetoothManager)context.getSystemService(Context.BLUETOOTH_SERVICE);
		sBluetoothAdapter = man.getAdapter();
		if(sBluetoothAdapter == null) {
			Log.d(TAG, "BT not support");
			return ENOTSUPPORT_BT;
		}
		
		//BLEをサポートしているかどうか
		boolean ret = context.getPackageManager().hasSystemFeature(
				PackageManager.FEATURE_BLUETOOTH_LE);
		if(!ret) {
			Log.d(TAG, "BLE not support");
			return ENOTSUPPORT_LE;
		}
		
		return SUCCESS;
	}
	
	/**
	 * Activity.onResume()で呼んでもらう処理。
	 * BTが無効になっていたらユーザに問い合わせる。
	 * @param activity
	 */
	public static int onResume(Activity activity) {
		//onCreate()を呼んでいない場合の対処
		if (sBluetoothAdapter == null) {
			Log.d(TAG, "not open");
			return ENOTOPEN;
		}
		
		//BT無効時の問い合わせ
		if (!sBluetoothAdapter.isEnabled()) {
			//なぜ2回読んでいるのかわからないが、サンプルのまねをしておこう
			if (!sBluetoothAdapter.isEnabled()) {
				Log.d(TAG, "send intent to Enabling BT");
				Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
				activity.startActivityForResult(intent, REQUEST_ENABLE_BT);
			}
		}
		
		return SUCCESS;
	}
	
	/**
	 * onResume()でユーザに問い合わせた場合のonActivityResult()で呼んでもらう処理。
	 * BTキャンセルされたかどうかの確認。
	 * @param activity
	 * @param requestCode
	 * @param resultCode
	 * @param data
	 * @return SUCCESS:BTが有効にされた
	 */
	public static int onActivityResult(Activity activity, int requestCode, int resultCode, Intent data) {
		if ((requestCode == REQUEST_ENABLE_BT) && (resultCode == Activity.RESULT_CANCELED)) {
			Log.d(TAG, "user canceled");
			sBluetoothAdapter = null;
			return ENOTSUPPORT_BT;
		}
		
		return SUCCESS;
	}
	
	/**
	 * onPause()で呼んでもらう処理。
	 * スキャンしていたら止める。
	 * @param activity
	 * @return
	 */
	public static int onPause(Activity activity) {
		//onCreate()を呼んでいない場合の対処
		if (sBluetoothAdapter == null) {
			Log.d(TAG, "not open");
			return ENOTOPEN;
		}
		
		stopScan();

		return SUCCESS;
	}
	
	/**
	 * BLE機器のスキャン開始(デバイス名指定)
	 * @param deviceName
	 * @param scanPeriodMs
	 * @return
	 */
	public static int startScan(String deviceName, int scanPeriodMs) {
		//onCreate()を呼んでいない場合の対処
		if (sBluetoothAdapter == null) {
			Log.d(TAG, "not open");
			return ENOTOPEN;
		}
		
		//スキャン中？
		if (sScanning) {
			Log.d(TAG, "scanning");
			return ESCANNING;
		}
		
		sHandler.postDelayed(new Runnable() {
			//一定時間後の動作
			@Override
            public void run() {
				stopScan();
				
				//TODO:呼出元にコールバック
            }
		}, scanPeriodMs);
		
		sScanning = true;
		sScanDeviceName = deviceName;
		sBluetoothAdapter.startLeScan(sCallback);

		return SUCCESS;
	}
	
	/**
	 * BLE機器スキャンの停止。
	 * スキャンしていないときに呼んでも、エラーは返さない。
	 * @return
	 */
	public static int stopScan() {
		//onCreate()を呼んでいない場合の対処
		if (sBluetoothAdapter == null) {
			Log.d(TAG, "not open");
			return ENOTOPEN;
		}
		
		//スキャン中チェック
		if (!sScanning) {
			//スキャンしてないけど、エラーにはすまい。
			Log.d(TAG, "not scanning");
			return SUCCESS;
		}
		
		sBluetoothAdapter.stopLeScan(sCallback);
		sScanning = false;
		sScanDeviceName = null;
		
		return SUCCESS;
	}
	
	private static BluetoothAdapter.LeScanCallback sCallback = new BluetoothAdapter.LeScanCallback() {
		@Override
		public void onLeScan(BluetoothDevice device, int rssi, byte[] scanRecord) {
			Log.d(TAG, "onLeScan[" + device.getName() + "]  RSSI:" + rssi);
			if ((sScanDeviceName != null) && (sScanDeviceName.equals(device.getName()))) {
				//デバイス名が一致
				Log.d(TAG, "device found");
				stopScan();
				
				//TODO:呼出元にコールバック
			}
		}
	};
}
