package com.wisega.wisegadebugtool;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothGatt;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.clj.fastble.BleManager;
import com.clj.fastble.callback.BleGattCallback;
import com.clj.fastble.callback.BleNotifyCallback;
import com.clj.fastble.callback.BleScanCallback;
import com.clj.fastble.data.BleDevice;
import com.clj.fastble.exception.BleException;
import com.clj.fastble.scan.BleScanRuleConfig;

import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final String STR_NAME = null;
    private static final String ET_MAC = null;
    private static final String TAG = "MainActivity";
    //    private static final String ET_UUID_NOTIFY1 = "00000000-0000-1000-8000-00805f9b34fb";
    private static final String ET_UUID_NOTIFY = "0000ffe2-0000-1000-8000-00805f9b34fb";
    private static final String ET_UUID_SERVICE = "00000000-0000-1000-8000-00805f9b34fb";
    /**
     * requestCode
     */
    private static final int REQUEST_CODE_ACCESS_COARSE_LOCATION = 1;
    private com.qmuiteam.qmui.alpha.QMUIAlphaButton mSacnDevices;
    private com.qmuiteam.qmui.widget.textview.QMUISpanTouchFixTextView mDeviceName, mDeviceRssi;
    private String[] names;
    private String mac;
    private String[] uuids;
    private boolean isAutoConnect, isNeedConnect;
    BleManager bleManager;
    private BleScanRuleConfig bleScanRuleConfig;
    private float numTmp;

    private int mScanDeviceRssi = -70;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        hideBar();
        registerReceiver(blueStateListner, new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));

        findView();
        initViews();

        names = new String[]{"CJ007", "Gamesir-X1"};

        uuids = new String[]{ET_UUID_SERVICE, ET_UUID_NOTIFY};

//        BleManager.getInstance().setOperateTimeout(4000);
        BleScanRuleConfig scanRuleConfig = new BleScanRuleConfig.Builder()
//                .setServiceUuids(serviceUuids)      // 只扫描指定的服务的设备，可选
                .setDeviceName(true, names)         // 只扫描指定广播名的设备，可选
//                .setDeviceMac(mac)                  // 只扫描指定mac的设备，可选
//                .setAutoConnect(isAutoConnect)      // 连接时的autoConnect参数，可选，默认false
                .setScanTimeOut(8000)              // 扫描超时时间，可选，默认10秒；小于等于0表示不限制扫描时间
                .build();
        BleManager.getInstance().initScanRule(scanRuleConfig);
    }

    private void hideBar() {
        //隐藏虚拟按键，并且全屏
        if (Build.VERSION.SDK_INT > 11 && Build.VERSION.SDK_INT < 19) { // lower api
            View v = this.getWindow().getDecorView();
            v.setSystemUiVisibility(View.GONE);
        } else if (Build.VERSION.SDK_INT >= 19) {
            //for new api versions.
            View decorView = getWindow().getDecorView();
            int uiOptions = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_FULLSCREEN;
            decorView.setSystemUiVisibility(uiOptions);
        }
    }


    private void findView() {
        mSacnDevices = findViewById(R.id.btn_scan);
        mDeviceName = findViewById(R.id.txt_name_show);
        mDeviceRssi = findViewById(R.id.txt_rssi_show);

    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn_scan:
                scanDevices();
                break;
        }
    }

    private void scanDevices() {
        BleManager.getInstance().scan(new BleScanCallback() {
            @Override
            public void onScanStarted(boolean success) {
                Log.e(TAG, "onScanStarted: ");
            }

            @Override
            public void onScanning(BleDevice result) {
                Log.e(TAG, "onScanning: devices=" + result.getName().toString() + "+1");
                if (result.getRssi() >= mScanDeviceRssi) {
                    BleManager.getInstance().connect(result, new BleGattCallback() {
                        @Override
                        public void onStartConnect() {

                        }

                        @Override
                        public void onConnectFail(BleException exception) {

                        }

                        @Override
                        public void onConnectSuccess(BleDevice bleDevice, BluetoothGatt gatt, int status) {
                            mDeviceName.setText(bleDevice.getName());
                            mDeviceRssi.setText(bleDevice.getRssi() + "");

                            BleManager.getInstance().notify(bleDevice, ET_UUID_SERVICE, ET_UUID_NOTIFY, new BleNotifyCallback() {
                                @Override
                                public void onNotifySuccess() {
                                    Log.e(TAG, "onNotifySuccess: ");
                                }

                                @Override
                                public void onNotifyFailure(BleException exception) {
                                    Log.e(TAG, "onNotifyFailure: ");

                                }

                                @Override
                                public void onCharacteristicChanged(byte[] data) {

                                }
                            });
                        }

                        @Override
                        public void onDisConnected(boolean isActiveDisConnected, BleDevice device, BluetoothGatt gatt, int status) {
                            BleManager.getInstance().disconnectAllDevice();
                            BleManager.getInstance().destroy();
                            mDeviceName.setText("Name");
                            mDeviceRssi.setText("Rssi");
                            Log.e(TAG, "onDisConnected: ");
                            scanDevices();

                        }
                    });
                } else {
                    return;
                }
//

            }

            @Override
            public void onScanFinished(List<BleDevice> scanResultList) {
                if (scanResultList == null || scanResultList.size() == 0) {
                    scanDevices();
                }
                for (BleDevice devices : scanResultList) {
                    Log.e(TAG, "onScanFinished: scanResultList=" + devices.getName().toString());
                }

            }
        });
    }

    void initViews() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {//如果 API level 是大于等于 23(Android 6.0) 时
            //判断是否具有权限
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                //判断是否需要向用户解释为什么需要申请该权限
                if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                        Manifest.permission.ACCESS_COARSE_LOCATION)) {
                    Toast.makeText(this, "自Android 6.0开始需要打开位置权限才可以搜索到Ble设备", Toast.LENGTH_SHORT).show();
                }
                //请求权限
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},
                        REQUEST_CODE_ACCESS_COARSE_LOCATION);
            }
        }
        //初始配置
        BleManager.getInstance().init(getApplication());
    }

    private BroadcastReceiver blueStateListner = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
                int blueState = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, 0);
                Log.i(TAG, "bluetooth state change:" + blueState);
                if (blueState == BluetoothAdapter.STATE_ON) {
                    Log.i(TAG, "检测到蓝牙打开，开启扫描！" + Thread.currentThread().getName());
                    //scanAndConnect();
                } else if (blueState == BluetoothAdapter.STATE_OFF) {
                    BleManager.getInstance().disconnectAllDevice();
                    BleManager.getInstance().destroy();
                    Log.i(TAG, "检测到蓝牙已经关闭，正在释放资源！");
                }
            }
        }
    };
}
