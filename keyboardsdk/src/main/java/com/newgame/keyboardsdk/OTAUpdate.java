package com.newgame.keyboardsdk;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.content.Context;
import android.util.Log;

import com.newgame.keyboardsdk.util.Conversion;
import com.newgame.keyboardsdk.util.Hex;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Created by CG_Dawson on 2017/12/19.
 */

public class OTAUpdate implements Runnable, KeyboardService.IBLENotify {
    public final static String ACTION_FW_REV_UPDATED = "com.example.ti.ble.btsig.ACTION_FW_REV_UPDATED";
    public final static String EXTRA_FW_REV_STRING = "com.example.ti.ble.btsig.EXTRA_FW_REV_STRING";
    public static final UUID CLIENT_CHARACTERISTIC_CONFIG = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    private static final String dISService_UUID = "0000180a-0000-1000-8000-00805f9b34fb";
    private static final String dISSystemID_UUID = "00002a23-0000-1000-8000-00805f9b34fb";
    private static final String dISModelNR_UUID = "00002a24-0000-1000-8000-00805f9b34fb";
    private static final String dISSerialNR_UUID = "00002a25-0000-1000-8000-00805f9b34fb";
    private static final String dISFirmwareREV_UUID = "00002a26-0000-1000-8000-00805f9b34fb";
    private static final String dISHardwareREV_UUID = "00002a27-0000-1000-8000-00805f9b34fb";
    private static final String dISSoftwareREV_UUID = "00002a28-0000-1000-8000-00805f9b34fb";
    private static final String dISManifacturerNAME_UUID = "00002a29-0000-1000-8000-00805f9b34fb";
    private static final String oadService_UUID = "f000ffc0-0451-4000-b000-000000000000";
    private static final String oadImageNotify_UUID = "f000ffc1-0451-4000-b000-000000000000";
    private static final String oadBlockRequest_UUID = "f000ffc2-0451-4000-b000-000000000000";
    private final String TAG = "OTAUpdate";
    private final byte[] mOadBuffer = new byte[20];
    private final Lock lock = new ReentrantLock();
    private final byte[] mFileBuffer = new byte[0x40000];
    public IOTACallBack iotaCallBack;
    private BluetoothGattService mOadService = null;
    private BluetoothGattService mConnControlService = null;
    private BluetoothGattService mBTService = null;
    private BluetoothGattCharacteristic mCharIdentify = null;
    private BluetoothGattCharacteristic mCharBlock = null;
    private BluetoothGattCharacteristic systemIDc;
    private BluetoothGattCharacteristic modelNRc;
    private BluetoothGattCharacteristic serialNRc;
    private BluetoothGattCharacteristic firmwareREVc;
    private BluetoothGattCharacteristic hardwareREVc;
    private BluetoothGattCharacteristic softwareREVc;
    private BluetoothGattCharacteristic ManifacturerNAMEc;
    private ImgHdr mFileImgHdr = new ImgHdr();
    private ImgHdr mTargImgHdr = new ImgHdr();
    private ProgInfo mProgInfo = new ProgInfo();
    private boolean mProgramming = false;
    private boolean ifBlockSend = false;
    private volatile LinkedList<bleRequest> procQueue = new LinkedList<>();
    private volatile boolean blocking = false;
    private BluetoothGatt gatt;
    private Context context;
    private String filePath;
    private String fwVersion = "";
    private String curImage = "";
    private byte[] curWrite;
    private int writeState;
    private volatile bleRequest curBleRequest = null;

    public OTAUpdate(Context context, BluetoothGatt gatt, String filePath) {
        this.gatt = gatt;
        this.context = context;
        this.filePath = filePath;
    }

    public void setIotaCallBack(IOTACallBack iotaCallBack) {
        this.iotaCallBack = iotaCallBack;
    }

    public String getFwVersion() {
        return fwVersion;
    }

    public String getCurImage() {
        return curImage;
    }

    private int calc_crc16(byte[] data, int len) {
        int i, j;
        byte ds;
        int crc = 0xffff;
        int poly[] = {0, 0xa001};

        for (j = 0; j < len; j++) {
            ds = data[j];
            for (i = 0; i < 8; i++) {
                crc = (crc >> 1) ^ poly[(crc ^ ds) & 1];
                ds = (byte) (ds >> 1);
            }
        }
        return crc;
    }

    public void getInfomation() {
        new Thread(new Runnable() {
            @Override
            public void run() {

                //初始化
                List<BluetoothGattService> services = gatt.getServices();

                for (BluetoothGattService service : services) {
                    Log.i(TAG, "serviceName:" + service.getUuid().toString());
                    List<BluetoothGattCharacteristic> characterList = service.getCharacteristics();

                    if (characterList.size() == 0) {
                        Log.i(TAG, "This service has no characteristic");
                        return;
                    }
                    //发现OTA服务！！！
                    if ((service.getUuid().toString().compareTo(oadService_UUID)) == 0) {
                        mOadService = service;
                        Log.i(TAG, "get OTA service！");
                        List<BluetoothGattCharacteristic> oadCharacterList = mOadService.getCharacteristics();

                        mCharIdentify = oadCharacterList.get(0);
                        Log.i(TAG, "get OTA-Ident:" + mCharIdentify.getUuid().toString());

                        mCharIdentify.setValue(new byte[]{0});

                        try {
                            Thread.sleep(500);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        enableNotify(gatt, mCharIdentify);
                        try {
                            Thread.sleep(500);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        Log.i(TAG, "get image:" + gatt.writeCharacteristic(mCharIdentify));

                        mCharBlock = oadCharacterList.get(1);

                        Log.i(TAG, "get OTA-BLOCK:" + mCharBlock.getUuid().toString());
                        mCharBlock.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);

                        //开启升级

                    } else if ((service.getUuid().toString().compareTo("f000ffc0-0451-4000-b000-000000000000")) == 0) {
                        //System.out.println("get control service success");
                        mConnControlService = service;
                    } else if ((service.getUuid().toString().compareTo(dISService_UUID)) == 0) {
                        //System.out.println("get control service success");
                        mBTService = service;

                        List<BluetoothGattCharacteristic> characteristics = mBTService.getCharacteristics();


                        for (final BluetoothGattCharacteristic c : characteristics) {
                            //gatt.setCharacteristicNotification(c,true);
                            Log.i(TAG, "subService" + c.getUuid().toString() + "    " + c.getProperties());

                            if (c.getUuid().toString().equals(dISSystemID_UUID)) {
                                systemIDc = c;
                            }
                            if (c.getUuid().toString().equals(dISModelNR_UUID)) {
                                modelNRc = c;
                            }
                            if (c.getUuid().toString().equals(dISSerialNR_UUID)) {
                                serialNRc = c;
                            }
                            if (c.getUuid().toString().equals(dISFirmwareREV_UUID)) {
                                firmwareREVc = c;
                                Log.i(TAG, "open the fw notify:" + enableNotify(gatt, firmwareREVc));
                                try {
                                    Thread.sleep(500);
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                }
                                Log.i(TAG, "read the fw version:" + gatt.readCharacteristic(firmwareREVc));
                            }
                            if (c.getUuid().toString().equals(dISHardwareREV_UUID)) {
                                hardwareREVc = c;
                            }
                            if (c.getUuid().toString().equals(dISSoftwareREV_UUID)) {
                                softwareREVc = c;
                            }
                            if (c.getUuid().toString().equals(dISManifacturerNAME_UUID)) {
                                ManifacturerNAMEc = c;
                            }

                        }

                    }
                }
            }
        }).start();
    }

    @Override
    public void run() {
        update();

    }

    public boolean loadFile(String filepath, boolean isAsset) {
        //初始化文件

        try {
            // Read the file raw into a buffer
            InputStream stream;
            if (isAsset) {
                stream = context.getAssets().open(filepath);
            } else {
                File f = new File(filepath);
                stream = new FileInputStream(f);
            }
            stream.read(mFileBuffer, 0, mFileBuffer.length);
            stream.close();
        } catch (IOException e) {
            // Handle exceptions here
            Log.i(TAG, "File open failed: " + filepath + "\n");
            return false;
        }

        mFileImgHdr.ver = Conversion.buildUint16(mFileBuffer[5], mFileBuffer[4]);
        mFileImgHdr.len = Conversion.buildUint16(mFileBuffer[7], mFileBuffer[6]);
        //long templen = mFileImgHdr.len;
        //templen |= 0x80000000L;
        mFileImgHdr.imgType = ((mFileImgHdr.ver & 1) == 1) ? 'B' : 'A';
        System.arraycopy(mFileBuffer, 8, mFileImgHdr.uid, 0, 4);

        Log.i(TAG, "load file ver:" + mFileImgHdr.ver);
        Log.i(TAG, "load file len:" + mFileImgHdr.len);
        //System.out.println("load file templen:"+templen);
        Log.i(TAG, "load file imgType:" + mFileImgHdr.imgType);
        iotaCallBack.callback(1, (int) mFileImgHdr.ver, (int) mFileImgHdr.len, mFileImgHdr.imgType);
        return true;
    }

    private void update() {
        //准备发送数据

        byte[] buf = new byte[8 + 2 + 2];
        buf[0] = Conversion.loUint16(mFileImgHdr.ver);
        buf[1] = Conversion.hiUint16(mFileImgHdr.ver);
        buf[2] = Conversion.loUint16(mFileImgHdr.len);
        buf[3] = Conversion.hiUint16(mFileImgHdr.len);
        System.arraycopy(mFileImgHdr.uid, 0, buf, 4, 4);

        // Send image notification
        mCharIdentify.setValue(buf);
        boolean notifyOK = gatt.writeCharacteristic(mCharIdentify);
        Log.i(TAG, "notifyok?" + notifyOK);

        mProgInfo.reset();
        //进度条初始化回调
        iotaCallBack.callback(2, (int) mProgInfo.nBlocks, 0, null);
        //开始发送数据
        mProgramming = true;
        while (mProgramming) {
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            for (int i = 0; i < 6 & mProgramming; i++) {
                if (!mProgramming)
                    return;
                ifBlockSend = true;
                if (mProgInfo.iBlocks < mProgInfo.nBlocks) {
                    mProgramming = true;


                    // Prepare block
                    mOadBuffer[0] = Conversion.loUint16(mProgInfo.iBlocks);
                    mOadBuffer[1] = Conversion.hiUint16(mProgInfo.iBlocks);
                    System.arraycopy(mFileBuffer, (int) mProgInfo.iBytes, mOadBuffer, 2, 16);

                    int crc = calc_crc16(mOadBuffer, 18);
                    mOadBuffer[18] = Conversion.loUint16(crc);
                    mOadBuffer[19] = Conversion.hiUint16(crc);

                    mCharBlock.setValue(mOadBuffer);

                    //Log.i(TAG,"send block data:"+ Hex.toString(mOadBuffer));
                    blocking = true;
                    /*try {
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }*/
                    boolean success = true;
                    int retryCount = 0;
                    curWrite = mOadBuffer;

                    writeState = 1;
                    gatt.writeCharacteristic(mCharBlock);
                    try {
                        Thread.sleep(12);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    while (writeState != 0) {
                        if (writeState == 2)//发送失败，要求重试
                        {
                            gatt.writeCharacteristic(mCharBlock);
                            try {
                                Thread.sleep(12);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                            if (retryCount++ > 50000000) {
                                writeState = 3;
                                break;
                            }
                        }
                    }

                    /*while(!(success=gatt.writeCharacteristic(mCharBlock)))
                    {
                        if(retryCount++>50000000)
                        {
                            break;
                        }
                    }*/

                    if (writeState == 0) {
                        // Update stats
                        mProgInfo.iBlocks++;
                        mProgInfo.iBytes += 16;
                        // mProgressBar.setProgress((short)((mProgInfo.iBlocks * 100) / mProgInfo.nBlocks));
                        iotaCallBack.callback(3, (int) mProgInfo.iBlocks, 0, Hex.toString(mOadBuffer));

                    } else {
                        mProgramming = false;
                        Log.i(TAG, "GATT writeCharacteristic failed:" + retryCount);
                        iotaCallBack.callback(4, 0, 0, null);
                    }
                    if (!success) {
                        //if (success!=0) {
                        //mLog.append(msg);
                    }
                } else {
                    mProgramming = false;
                }
                ifBlockSend = false;

            }
            if ((mProgInfo.iBlocks % 100) < 6) {
                // Display statistics each 100th block

            }
        }
    }

    private boolean enableNotify(BluetoothGatt gatt, BluetoothGattCharacteristic c) {

        if (!gatt.setCharacteristicNotification(c, true)) {
            return false;
        }
        List<BluetoothGattDescriptor> descriptors = c.getDescriptors();
        for (BluetoothGattDescriptor dp : descriptors) {
            if (dp != null) {
                if ((c.getProperties() & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) {
                    if (!dp.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)) {
                        return false;
                    }
                } else if ((c.getProperties() & BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0) {
                    if (!dp.setValue(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE)) {
                        return false;
                    }
                }
                if (!gatt.writeDescriptor(dp)) {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public void notify(int mode, BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
        if (oadImageNotify_UUID.equals(characteristic.getUuid().toString())) {
            //chara back
            if (mode == 3) {

                byte[] imageData = characteristic.getValue();
                if (imageData.length > 4) {
                    Log.i(TAG, "get image data:" + Hex.toString(imageData));
                    mTargImgHdr.ver = Conversion.buildUint16(imageData[1], imageData[0]);
                    mTargImgHdr.imgType = ((mTargImgHdr.ver & 1) == 1) ? 'B' : 'A';
                    mTargImgHdr.len = Conversion.buildUint16(imageData[3], imageData[2]);
                    long imgVer = (mTargImgHdr.ver) >> 1;
                    long imgSize = mTargImgHdr.len * 4;
                    curImage = String.format("Type: %c Ver.: %d Size: %d", mTargImgHdr.imgType, imgVer, imgSize);
                }

            }

        } else if (dISFirmwareREV_UUID.equals(characteristic.getUuid().toString())) {

            if (mode == 1) {
                byte[] fwData = characteristic.getValue();
                Log.i(TAG, "get fw brand:" + Hex.toString(fwData));
                try {
                    fwVersion = "Firmware Revision: " + new String(fwData, "UTF-8");
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }
            }
        } else if (oadBlockRequest_UUID.equals(characteristic.getUuid().toString())) {
            //chara back
            if (mode == 2) {
                if (Arrays.equals(characteristic.getValue(), curWrite)) {
                    if (status == 0) {
                        writeState = 0;
                    }
                } else {
                    writeState = 2;
                }
                //iotaCallBack.callback(5,0,0,Hex.toString(characteristic.getValue())+"  state:"+status);
            }

        }

    }

    public boolean addRequestToQueue(bleRequest req) {
        lock.lock();
        if (procQueue.peekLast() != null) {
            req.id = procQueue.peek().id++;
        } else {
            req.id = 0;
            procQueue.add(req);
        }
        lock.unlock();
        return true;
    }

    public bleRequestStatus pollForStatusofRequest(bleRequest req) {
        lock.lock();
        if (req == curBleRequest) {
            bleRequestStatus stat = curBleRequest.status;
            if (stat == bleRequestStatus.done) {
                curBleRequest = null;
            }
            if (stat == bleRequestStatus.timeout) {
                curBleRequest = null;
            }
            lock.unlock();
            return stat;
        } else {
            lock.unlock();
            return bleRequestStatus.no_such_request;
        }
    }

    public enum bleRequestOperation {
        wrBlocking,
        wr,
        rdBlocking,
        rd,
        nsBlocking,
    }

    public enum bleRequestStatus {
        not_queued,
        queued,
        processing,
        timeout,
        done,
        no_such_request,
        failed,
    }


    public interface IOTACallBack {
        void callback(int mode, int arg1, int arg2, Object obj);
    }

    private class ImgHdr {
        long ver;
        long len;
        Character imgType;
        byte[] uid = new byte[4];
    }

    public class bleRequest {
        public int id;
        public BluetoothGattCharacteristic characteristic;
        public bleRequestOperation operation;
        public volatile bleRequestStatus status;
        public int timeout;
        public int curTimeout;
        public boolean notifyenable;
    }

    private class ProgInfo {
        long iBytes = 0; // Number of bytes programmed
        long iBlocks = 0; // Number of blocks programmed
        long nBlocks = 0; // Total number of blocks
        int iTimeElapsed = 0; // Time elapsed in milliseconds

        void reset() {
            iBytes = 0;
            iBlocks = 0;
            iTimeElapsed = 0;
            nBlocks = (short) (mFileImgHdr.len / (16 / 4));
            //System.out.println("nBlocks:"+nBlocks);
        }
    }

}
