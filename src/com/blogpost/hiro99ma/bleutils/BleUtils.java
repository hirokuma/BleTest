/**
 * 
 */
package com.blogpost.hiro99ma.bleutils;


import java.util.UUID;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
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
	public final static int ESCANFAIL = -6;
	
	private static final String TAG = "BleUtils";
	private static final int REQUEST_ENABLE_BT = 1;

	private static BluetoothAdapter sBluetoothAdapter;
	private static Handler sHandler = null;		//スキャン用
	private static String sScanDeviceName = null;
	
    private static BluetoothDevice sBluetoothDevice = null;
	//private static BluetoothGatt sBluetoothGatt = null;
	private static UUID sGattServiceUUID = null;
	private static UUID sGattChUUID = null;
	
	private static scanResultCallback sScanResultCallback = null;
	private static connectResultCallback sConnectResultCallback = null;
	private static characteristicChangedCallback sChChangedCallback = null;


	private enum Status {
	    INIT,
	    CREATE,
	    SCANNING,
	    FOUND,
	    CONNECTING,
	    CONNECTED,
	}
	
	private static Status sStatus = Status.INIT;
	
	/**
	 * BLEの使用準備
	 * @param context  呼び元のContext
	 * @return     SUCCESS(オープン成功)<br>
	 *             EOPENED(オープン済み)<br>
	 *             ENOTSUPPORT_BT(BT未サポート)<br>
	 *             ENOTSUPPORT_LE(BTサポートだがBLE未サポート)<br>
	 */
	public static int onCreate(Context context) {
		//既にprepareしているかどうか
		if (sBluetoothAdapter != null) {
			Log.d(TAG, "already prepared.");
			return SUCCESS;
		}
		
		//BTをサポートしているかどうか
		final BluetoothManager mgr = (BluetoothManager)context.getSystemService(Context.BLUETOOTH_SERVICE);
		sBluetoothAdapter = mgr.getAdapter();
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
		
		sStatus = Status.CREATE;
		return SUCCESS;
	}
	
	/**
	 * Activity.onResume()で呼んでもらう処理。
	 * BTが無効になっていたらユーザに問い合わせる。
	 * @param activity         呼び元のActivity
     * @return                  SUCCESS(スキャン成功)<br>
     *                          ENOTOPEN(オープンしていない)<br>
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
	 * 
	 * @param activity     呼び元のActivity
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
	 * @param activity         呼び元のActivity
     * @return                 SUCCESS(スキャン成功)<br>
     *                         ENOTOPEN(オープンしていない)<br>
	 */
	public static int onPause(Activity activity) {
		//onCreate()を呼んでいない場合の対処
		if (sBluetoothAdapter == null) {
			Log.d(TAG, "not open");
			return ENOTOPEN;
		}
		
		_stopScan();

		sStatus = Status.CREATE;
		return SUCCESS;
	}
	
	/**
	 * BLE機器のスキャン開始(デバイス名指定)
	 * @param deviceName       スキャンするデバイス名。一致するとスキャン停止。
	 * @param scanPeriodMs     スキャンする時間。deviceNameが一致しないままタイムアウトする時間でもある。
	 * @return                 SUCCESS(スキャン成功)<br>
	 *                         ENOTOPEN(オープンしていない)<br>
	 *                         ESCANNING(既にスキャン中)<br>
	 */
	public static int startScan(String deviceName, int scanPeriodMs, scanResultCallback callback) {
		//onCreate()を呼んでいない場合の対処
		if (sBluetoothAdapter == null) {
			Log.d(TAG, "not open");
			return ENOTOPEN;
		}
		
		//スキャン中？
		if (sStatus == Status.SCANNING) {
			Log.d(TAG, "scanning");
			return ESCANNING;
		}
		
		sHandler = new Handler();
		sHandler.postDelayed(new Runnable() {
			//一定時間後の動作
			@Override
            public void run() {
			    Log.w(TAG, "scan timeout");
				_stopScan();
				
				//呼出元にコールバック(失敗)
                if (sScanResultCallback != null) {
                    scanResultCallback callback = sScanResultCallback;
                    sScanResultCallback = null;
                    sStatus = Status.CREATE;
                    callback.onResult(ESCANFAIL, 0, null);
                }
            }
		}, scanPeriodMs);
		
		sStatus = Status.SCANNING;
		sScanDeviceName = deviceName;
		sScanResultCallback = callback;
		sBluetoothAdapter.startLeScan(sCallback);

		return SUCCESS;
	}
	
    /**
     * BLE機器スキャンの停止。
     * スキャンしていないときに呼んでも、エラーは返さない。
     * @return                 SUCCESS(スキャンしていない場合も含む)<br>
     *                         ENOTOPEN(オープンしていない)<br>
     */
	public static int stopScan() {
	    int ret = _stopScan();
	    sScanResultCallback = null;
	    sStatus = Status.CREATE;
	    
	    return ret;
	}
	
	/**
	 * BLE機器スキャンの停止(内部用)。
	 * スキャンしていないときに呼んでも、エラーは返さない。
	 * 上位から与えられたコールバックは消さないので、呼び元で対応すること。
     * @return                 SUCCESS(スキャンしていない場合も含む)<br>
     *                         ENOTOPEN(オープンしていない)<br>
	 */
	private static int _stopScan() {
		//onCreate()を呼んでいない場合の対処
		if (sBluetoothAdapter == null) {
			Log.d(TAG, "not open");
			return ENOTOPEN;
		}
		
		//スキャン中チェック
		if (sStatus != Status.SCANNING) {
			//スキャンしてないけど、エラーにはすまい。
			Log.d(TAG, "not scanning");
			return SUCCESS;
		}
		
		sBluetoothAdapter.stopLeScan(sCallback);
		sHandler = null;
		sScanDeviceName = null;
        sBluetoothDevice = null;
        
		return SUCCESS;
	}
	
	/**
	 * 
	 */
	private static BluetoothAdapter.LeScanCallback sCallback = new BluetoothAdapter.LeScanCallback() {
	    /**
	     * @param  device      デバイス情報
	     * @param  rssi        RSSI値
	     * @param  scanRecord  Advertのデータ
	     */
		@Override
		public void onLeScan(BluetoothDevice device, int rssi, byte[] scanRecord) {
			Log.d(TAG, "onLeScan[" + device.getName() + "]  RSSI:" + rssi);
			if ((sScanDeviceName != null) && (sScanDeviceName.equals(device.getName()))) {
				//デバイス名が一致
				Log.d(TAG, "device found");
				
				sStatus = Status.FOUND;
				
				//スキャン停止
				_stopScan();
                
                sBluetoothDevice = device;
				
				//呼出元にコールバック(成功)
				if (sScanResultCallback != null) {
				    scanResultCallback callback = sScanResultCallback;
				    sScanResultCallback = null;
				    callback.onResult(SUCCESS, rssi, scanRecord);
				}
			}
		}
	};
	
	public static int startConnect(Context context, UUID service, UUID ch, connectResultCallback callback) {
	    if (sStatus != Status.FOUND) {
	        //スキャンできていないので接続できない
	        return ESCANFAIL;
	    }
	    
	    sGattServiceUUID = service;
	    sGattChUUID = ch;
	    //sBluetoothGatt = sBluetoothDevice.connectGatt(context, false, sGattCallback);
        sBluetoothDevice.connectGatt(context, false, sGattCallback);
	    sConnectResultCallback = callback;
	    sChChangedCallback = null;
	    
	    return SUCCESS;
	}
	
	private static BluetoothGattCallback sGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            //connectGatt()の結果
            Log.d(TAG, "onConnectionStateChange");
            
            switch (newState) {
            case BluetoothProfile.STATE_CONNECTING:
                Log.d(TAG, "  STATE_CONNECTING");
                break;
            case BluetoothProfile.STATE_CONNECTED:
                //接続→サービス検索(onServicesDiscovered)
                Log.d(TAG, "  STATE_CONNECTED");
                gatt.discoverServices();
                break;
            case BluetoothProfile.STATE_DISCONNECTING:
                Log.d(TAG, "  STATE_DISCONNECTING");
                break;
            case BluetoothProfile.STATE_DISCONNECTED:
                Log.d(TAG, "  STATE_DISCONNECTED");
                //sBluetoothGatt = null;
                sStatus = Status.FOUND;     //またスキャンから始めるべきか？
                break;
            default:
                //エラーにすべき？
                Log.d(TAG, "  unknown state : " + newState);
                break;
            }
        }
        
        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            Log.d(TAG, "onServicesDiscovered");
            
            if (status != BluetoothGatt.GATT_SUCCESS) {
                //エラーにすべき？
                Log.d(TAG, "  not success : " + status);
                return;
            }
            
            BluetoothGattService service = gatt.getService(sGattServiceUUID);
            if (service == null) {
                //エラーにすべき？
                Log.d(TAG, "  service not found");
                return;
            }
            
            BluetoothGattCharacteristic ch = service.getCharacteristic(sGattChUUID);
            if (ch == null) {
                //エラーにすべき？
                Log.d(TAG, "  characteristic not found");
                return;
            }
            
            //サービスもキャラクタリスティックも見つかった
            Log.d(TAG, "GATT_SUCCESS");
            if (sConnectResultCallback != null) {
                sConnectResultCallback.onConnect(SUCCESS, gatt, ch);
            }
       }
        
	    @Override
	    public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
	        Log.d(TAG, "onCharacteristicChanged");
	        if (sChChangedCallback != null) {
	            sChChangedCallback.onChanged(gatt, characteristic);
	        }
	    }
	    
	    @Override
	    public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            Log.d(TAG, "onCharacteristicRead");
	        ;
	    }
	    
	    @Override
	    public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            Log.d(TAG, "onCharacteristicWrite");
	        ;
	    }
	    
	    @Override
	    public void onDescriptorRead(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            Log.d(TAG, "onDescriptorRead");
	        ;
	    }
	    
	    @Override
	    public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            Log.d(TAG, "onDescriptorWrite");
	        ;
	    }
	    
	    @Override
	    public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
            Log.d(TAG, "onReadRemoteRssi");
	        ;
	    }
	    
	    @Override
	    public void onReliableWriteCompleted(BluetoothGatt gatt, int status) {
            Log.d(TAG, "onReliableWriteCompleted");
	        ;
	    }
	};
	
    
    /**
     * startScan()の結果を返すコールバック関数
     * 
     * @param result       SUCCESS(スキャン成功), ESCANFAIL(スキャン失敗)
     * @param rssi         RSSI値(resultがSUCCESSの場合のみ有効)
     * @param scanRecord   ビーコンの中身？(resultがSUCCESSの場合のみ有効)
     */
    public interface scanResultCallback {
        public void onResult(int result, int rssi, byte[] scanRecord);
    }

    public interface connectResultCallback {
	    public characteristicChangedCallback onConnect(int result, BluetoothGatt gatt, BluetoothGattCharacteristic ch);
	}
    
    public interface characteristicChangedCallback {
        public void onChanged(BluetoothGatt gatt, BluetoothGattCharacteristic ch);
    }
}
