package com.newgame.keyboardsdk;

import android.app.Notification;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.BitmapFactory;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import com.clj.fastble.BleManager;
import com.clj.fastble.callback.BleWriteCallback;
import com.clj.fastble.data.BleDevice;
import com.clj.fastble.exception.OtherException;
import com.hexad.bluezime.BluezService;
import com.hexad.bluezime.HIDKeyboard;
import com.newgame.keyboardsdk.util.Hex;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * 键鼠设备操作（连接、断连等）服务。
 */
public class KeyboardService extends Service {

    private static final String TAG = "KeyboardService";

    public static final String ET_UUID_NOTIFY = "0000ffe2-0000-1000-8000-00805f9b34fb";
    public static final String ET_UUID_SERVICE = "00000000-0000-1000-8000-00805f9b34fb";
    public static final String ET_UUID_WRITE = "0000ffe1-0000-1000-8000-00805f9b34fb";
    private static final String ACTION_CONNECT_SUCCESS = "com.wisega.CONNECT_STATE";
    public final static int[] KEY = new int[]{//zikway201511_cj0
            Hex.toIntB("zikw".getBytes()), Hex.toIntB("ay20".getBytes()),
            Hex.toIntB("1511".getBytes()), Hex.toIntB("_cj0".getBytes())
    };
    private final Binder binder = new LocalBinder();
    private boolean isBleManagerInitialized;
    private BleDevice mBleDevice;
    private HIDKeyboard mHidKeyboard;
    //接收数据时的时间
    private long dateTimeMillis0;
    private IStateCallBack iBlueconect;
    private int mBlueConnectTime;

    public enum EState {STOPSCAN,TIMEOUTANDRESET,STARTSCAN,CONNECTED,CONNECTING,CONNECTFAIL,DISCONNECTED,USERDISCONNECTED,DISCONNECTING}
    private OTAUpdate otaUpdate;

    private BluetoothGatt mGatt;
    private int mConnectState;
    private CopyOnWriteArraySet<IBLENotify> notifies = new CopyOnWriteArraySet<>();
    private Thread watchConnectDevices;
    private boolean watchflag = true;
    private  BluetoothGattCharacteristic writeCharacter;
    public KeyboardService() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || intent.getAction() == null)
            return START_STICKY;

        if (intent.getAction().equals(BluezService.REQUEST_CONNECT)) {
            if (!isBleManagerInitialized) {
                initBleManager();
            }
            scanBound();
            //setScanRule();
            //scanAndConnect();
        } else if (intent.getAction().equals(BluezService.REQUEST_DISCONNECT)) {
            BleManager.getInstance().disconnectAllDevice();
            BleManager.getInstance().destroy();
            mHidKeyboard = null;
        } else if (intent.getAction().equals(BluezService.REQUEST_FEATURECHANGE)) {

        } else if (intent.getAction().equals(BluezService.REQUEST_STATE)) {

        } else if (intent.getAction().equals(BluezService.REQUEST_CONFIG)) {

        }
        registerReceiver(blueStateListner,new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));
        return START_STICKY;
    }
   private BroadcastReceiver blueStateListner = new BroadcastReceiver() {
       @Override
       public void onReceive(Context context, Intent intent) {
          if(intent.getAction().equals(BluetoothAdapter.ACTION_STATE_CHANGED))
          {
              int blueState = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, 0);
              Log.i(TAG,"bluetooth state change:"+blueState);
              if(blueState==BluetoothAdapter.STATE_ON)
              {
                  Log.i(TAG,"检测到蓝牙打开，开启扫描！"+Thread.currentThread().getName());
                  //scanAndConnect();
              }
              else if(blueState==BluetoothAdapter.STATE_OFF)
              {
                 // BleManager.getInstance().disconnectAllDevice();
                 // BleManager.getInstance().destroy();
                 // mHidKeyboard = null;
                  Log.i(TAG,"检测到蓝牙已经关闭，正在释放资源！");

              }
          }
       }
   };
    private void initBleManager() {
        BleManager.getInstance().init(getApplication());
        BleManager.getInstance()
                .enableLog(true)
                .setMaxConnectCount(7)
                .setOperateTimeout(5000);
        isBleManagerInitialized = true;
    }

    public synchronized void scanBound()
    {
        Log.i(TAG,"start scanBound");
        if(mConnectState!=0||BleManager.getInstance().getBluetoothAdapter()==null||!BleManager.getInstance().getBluetoothAdapter().isEnabled())
        {
            return;
        }
        mConnectState = 1;
        boolean isConnect = false;
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        Class<BluetoothAdapter> bluetoothAdapterClass = BluetoothAdapter.class;//得到BluetoothAdapter的Class对象
        try {//得到连接状态的方法
            Method method = bluetoothAdapterClass.getDeclaredMethod("getConnectionState", (Class[]) null);
            method.setAccessible(true);
            int state = (int) method.invoke(adapter, (Object[]) null);
            Log.i(TAG,"===================================:"+state);
            if(state == BluetoothAdapter.STATE_CONNECTED){
                Set<BluetoothDevice> devices = adapter.getBondedDevices();
                Log.i(TAG,"device num:"+devices.size());
                for(BluetoothDevice device : devices){
                    Method isConnectedMethod = BluetoothDevice.class.getDeclaredMethod("isConnected", (Class[]) null);
                    method.setAccessible(true);
                    boolean isConnected = (boolean) isConnectedMethod.invoke(device, (Object[]) null);
                    if(isConnected&&(device.getName().contains("CJ007")||device.getName().contains("Gamesir-X1"))){
                        Log.i(TAG,"get the device:"+device.getName()+"["+device.getAddress()+"]");
                        isConnect = true;
                        connect(device);
                        break;
                    }
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
            Log.i(TAG,"===================================异常");
        }
       if(!isConnect){
           mConnectState = 0;
       }
    }
    public void connect(BluetoothDevice bluetoothDevice)
    {

           bluetoothDevice.connectGatt(getApplicationContext(), false, new BluetoothGattCallback() {
            @Override
            public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
                //Log.i(TAG,"RRRRRRRRRR:["+characteristic.getUuid().toString()+"]"+Hex.toString(characteristic.getValue()));
                notifyAllEx(1,gatt,characteristic,status);
            }

            @Override
            public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
                //Log.i(TAG,"WWWWWWWWWW:["+characteristic.getUuid().toString()+"]"+Hex.toString(characteristic.getValue()));
                notifyAllEx(2,gatt,characteristic,status);
            }

            @Override
            public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {

                notifyAllEx(3,gatt,characteristic,-100);
                byte[] data = characteristic.getValue();
                handleRecData(data);

            }

            @Override
            public void onConnectionStateChange(final BluetoothGatt gatt, final int status, final int newState) {
                Log.i(TAG,status+","+newState);


                switch(newState)
                {
                    case BluetoothProfile.STATE_CONNECTED:
                        if(watchConnectDevices!=null)
                        {
                            watchflag = false;
                            watchConnectDevices = null;
                        }
                        Log.i(TAG,"蓝牙已经连接");
                        mConnectState = 2;
                        mGatt = gatt;
                        iBlueconect.connectstate(1,EState.CONNECTED,gatt.getDevice());
                        break;
                    case BluetoothProfile.STATE_CONNECTING:
                        iBlueconect.connectstate(1,EState.CONNECTING,null);
                        break;
                    case BluetoothProfile.STATE_DISCONNECTED:
                        mGatt = null;
                        mConnectState = 0;
                        iBlueconect.connectstate(1,EState.DISCONNECTED,null);
                        gatt.close();
                        if(watchConnectDevices!=null)
                        {
                            if(watchConnectDevices.isAlive())
                            {
                                watchConnectDevices.interrupt();
                            }
                            watchConnectDevices = null;
                        }
                        watchflag = true;
                        watchConnectDevices = new Thread(new Runnable() {
                            @Override
                            public void run() {
                                while(watchflag)
                                {
                                    try {
                                        Thread.sleep(3000);
                                        scanBound();
                                    } catch (InterruptedException e) {
                                        e.printStackTrace();
                                    }
                                }
                            }
                        });
                        watchConnectDevices.start();

                        break;
                }


                if(newState==BluetoothProfile.STATE_CONNECTED)
                {
                       new Thread(new Runnable() {
                           @Override
                           public void run() {
                               gatt.discoverServices();
                               try {
                                   Thread.sleep(1000);
                               } catch (InterruptedException e) {
                                   e.printStackTrace();
                               }
                               if (mHidKeyboard == null) {
                                   try {

                                       mHidKeyboard = new HIDKeyboard(gatt.getDevice().getAddress(),
                                               "session1",
                                               KeyboardService.this,
                                               false);

                                   } catch (Exception e) {
                                       e.printStackTrace();
                                   }
                               }
                               enableNotificationOfCharacteristic2(gatt);


                               if(otaUpdate!=null)
                               {
                                   notifies.remove(otaUpdate);

                               }
                               otaUpdate = new OTAUpdate(getApplicationContext(),gatt,"");
                               otaUpdate.getInfomation();
                               notifies.add(otaUpdate);
                               try {
                                   Thread.sleep(1500);
                               } catch (InterruptedException e) {
                                   e.printStackTrace();
                               }
                               iBlueconect.connectstate(3,null,null);

                           }
                       }).start();
                }

            }
        });
    }


    private void enableNotificationOfCharacteristic2(BluetoothGatt gatt) {
        UUID ServiceUUID = UUID.fromString(ET_UUID_SERVICE);
        UUID CharaUUID = UUID.fromString(ET_UUID_NOTIFY);
        List<BluetoothGattService> services = gatt.getServices();
        Log.i(TAG,"services-size:"+services.size());
        for(BluetoothGattService service:services)
        {
            Log.i(TAG,"subservice:"+service.getUuid().toString());
        }
        BluetoothGattService service = gatt.getService(ServiceUUID);

            if(service != null){
                BluetoothGattCharacteristic chara= service.getCharacteristic(CharaUUID);
                gatt.setCharacteristicNotification(chara,true);
                if(chara != null){

                    List<BluetoothGattDescriptor> descriptors = chara.getDescriptors();
                    for(BluetoothGattDescriptor dp:descriptors)
                    {
                        if (dp != null) {
                            if ((chara.getProperties() & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) {
                                dp.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                            } else if ((chara.getProperties() & BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0) {
                                dp.setValue(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE);
                            }
                            gatt.writeDescriptor(dp);
                        }
                    }
                }
            }

    }
   private void handleRecData(byte[] data)
   {
       Log.i("templog&chararec", Hex.toString(data));
       if(data.length<3)
       {
           return;
       }
       if(data[0]==(byte)0xa5)//包头
       {
           int len = data[1]&0xff;
           if(data.length<len)
           {
               Log.e(TAG,"丢包！");
               return;
           }
           if(sumCheck(Arrays.copyOfRange(data,0,len-1))!=data[len-1])
           {
               Log.e(TAG,"校验错误！");
               return;
           }
           //得到有效数据
           byte[] content = Arrays.copyOfRange(data,3,len);

           switch(data[2]&0xff)
           {
               case 0x01:
                   if (data.length < 17) {
                       Log.e(TAG, "onCharacteristicChanged: bad length = " + data.length);
                   }

                   try {
//                            Log.i(TAG, "onCharacteristicChanged: mHidKeyboard = " + mHidKeyboard);
                       //// FIXME: 2017/12/3  bytes
                       byte[] bytes = new byte[9];
                       // FIXME: 2017/12/3 data
                       bytes[0] = data[3];//mouse but
                       System.arraycopy(data, 9, bytes, 1, 8);
                       // FIXME: 2017/12/3
                       mHidKeyboard.handleMouseMessage(data);
                       mHidKeyboard.handleHIDMessage((byte) 1, (byte) 1, bytes);
                   } catch (Exception e) {
                       e.printStackTrace();
                   }
                   break;
               case 0x02:
                   iBlueconect.connectstate(2,null,content);
                   break;
               case 0x11:
                   //确认信息返回
                   //Tea tea = new Tea();
                   //tea.decrypt(content,0,KEY,32);
                   break;
           }
       }
   }
    private byte sumCheck(byte[] data) {
      byte result = 0;
      int len = data.length;
      for(int i=0;i<len;i++)
      {
         result = (byte) (result+data[i]);
      }
      return result;
    }
    private void notifyAllEx(int mode,BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status)
    {
        for(IBLENotify ibleNotify:notifies)
        {
            ibleNotify.notify(mode,gatt,characteristic,status);
        }
    }
    public void write(byte[] data,String serviceUUID_, String charactUUID_,BleWriteCallback bleWriteCallback)
    {
        UUID serviceUUID = UUID.fromString(serviceUUID_);
        UUID charactUUID = UUID.fromString(charactUUID_);
        BluetoothGattService service = null;
        if (serviceUUID == null || mGatt == null) {
            bleWriteCallback.onWriteFailure(new OtherException("null point-1:"+serviceUUID+","+mGatt));
            return;
        }
        service = mGatt.getService(serviceUUID);

        if (service == null || charactUUID == null) {
            bleWriteCallback.onWriteFailure(new OtherException("null point-1:"+service+","+charactUUID));
            return;
        }
        writeCharacter = service.getCharacteristic(charactUUID);
        if (data == null || data.length <= 0) {
            if (bleWriteCallback != null)
                bleWriteCallback.onWriteFailure(new OtherException("the data to be written is empty"));
            return;
        }

        if (writeCharacter == null
                || (writeCharacter.getProperties() & (BluetoothGattCharacteristic.PROPERTY_WRITE | BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)) == 0) {
            if (bleWriteCallback != null)
                bleWriteCallback.onWriteFailure(new OtherException("this characteristic not support write!"));
            return;
        }

        if (writeCharacter.setValue(data)) {
            if (!mGatt.writeCharacteristic(writeCharacter)) {
                if (bleWriteCallback != null)
                    bleWriteCallback.onWriteFailure(new OtherException("gatt writeCharacteristic fail"));
            }
            else{
                bleWriteCallback.onWriteSuccess();
            }
        } else {
            if (bleWriteCallback != null)
                bleWriteCallback.onWriteFailure(new OtherException("Updates the locally stored value of this characteristic fail"));
        }
    }
    public class LocalBinder extends Binder implements Serializable {
        KeyboardService getService() {
            return (KeyboardService.this);
        }
        public void setIStateCallBack(IStateCallBack iBlueconect_)
        {
            iBlueconect = iBlueconect_;
        }
        public void isResume()
        {
            KeyboardService.this.scanBound();
        }
        public BluetoothDevice getDevice()
        {
            return KeyboardService.this.mGatt!=null?KeyboardService.this.mGatt.getDevice():null;
        }
        public void write(byte[] data,String serviceUUID, String charactUUID,BleWriteCallback bleWriteCallback)
        {
            KeyboardService.this.write(data,serviceUUID,charactUUID,bleWriteCallback);
        }
        public void addNotify(IBLENotify ibleNotify)
        {
            notifies.add(ibleNotify);
        }

        public void removeNotify(IBLENotify ibleNotify)
        {
            notifies.remove(ibleNotify);
        }
        public String getfwVersion()
        {
            return otaUpdate==null?"":otaUpdate.getFwVersion();
        }
        public String getImgVersion()
        {
            return otaUpdate==null?"":otaUpdate.getCurImage();
        }
        public void setOTACallback(OTAUpdate.IOTACallBack iotaCallBack)
        {
            if(otaUpdate==null)
            {
                return;
            }
            otaUpdate.setIotaCallBack(iotaCallBack);
        }
        public void loadFile(String filepath,boolean isAsset)
        {
            if(otaUpdate==null)
            {
                return;
            }
            otaUpdate.loadFile(filepath,isAsset);
        }
        public void startOTAUpdate()
        {
            if(otaUpdate==null)
            {
                return;
            }
            Thread thread = new Thread(otaUpdate);
            thread.start();
        }
    }

    /**
     * 设置服务为前台
     *
     * @param text
     */
    private void setUpAsForeground(String text) {
        Log.i(TAG, "setUpAsForeground: 将服务设置成前台服务");
        Notification notification = null;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN) {
            notification = new Notification.Builder(this)
                    .setContentTitle(getString(R.string.app_name))
                    .setContentText(text)
                    .setTicker(text)
                    .setLargeIcon(BitmapFactory.decodeResource(getResources(), R.mipmap.ic_bluetooth))
                    .build();
        }
        startForeground(1, notification);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        //将蓝牙连接服务设置为前台
        setUpAsForeground(getString(R.string.foreground_notification_ble));
    }
    public interface IStateCallBack
    {
        void connectstate(int type,EState state,Object obj);
    }
    public interface IBLENotify
    {
        void notify(int mode,BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status);
    }
}
