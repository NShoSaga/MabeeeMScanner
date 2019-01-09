/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package either.com.mabeeemscanner;

import android.Manifest;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.provider.AlarmClock;
import android.provider.Settings;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.util.SparseArray;
import android.widget.Toast;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.net.ssl.HttpsURLConnection;

import static android.support.v4.app.ActivityCompat.requestPermissions;
import static android.support.v4.app.ActivityCompat.startActivityForResult;
import static java.lang.Byte.toUnsignedInt;
import static java.net.Proxy.Type.HTTP;

import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
//import org.elasticsearch.client.http.entity.ContentType;
//import org.elasticsearch.client.http.HttpEntity;
//import org.elasticsearch.client.http.HttpHost;
//import org.elasticsearch.client.http.nio.entity.NStringEntity;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpStatus;
import org.apache.http.entity.ContentType;
import org.apache.http.nio.entity.NStringEntity;

import org.apache.http.entity.StringEntity;

import okhttp3.Request;
import okhttp3.RequestBody;

/**
 * Service for managing connection and data communication with a GATT server hosted on a
 * given Bluetooth LE device.
 */
public class BleService extends Service {
    private final static String TAG = BleService.class.getSimpleName();

    private BluetoothLeScanner mBluetoothLeScanner;
    private static BluetoothManager mBluetoothManager;
    private static BluetoothAdapter mBluetoothAdapter;
    private static String mBluetoothDeviceAddress;
    private static String mBluetoothDeviceName;
    private static BluetoothGatt mBluetoothGatt;
    private static BluetoothDevice mDevice;
    private static boolean mScanning = false;
    private static PowerManager.WakeLock m_wakeLock;
    private static boolean mAwsUpdate = true;

    private static Timer mTimer = null;
    private static Handler mHandler = null;

    public static final int IBEACON_MAJOR_VALUE_OFFSET = 25;
    public static final int IBEACON_MINOR_VALUE_OFFSET = IBEACON_MAJOR_VALUE_OFFSET + 2;
    public static final float V_BAT_SCALE = 3.6f / 256;
    public static final int AWS_UPDATE_INTERVAL_MS = 1000;

//    public static final String AWS_URL = "https://search-mbmd-sa6qxdlv3ohnzaozxvrnqfzbli.ap-northeast-1.es.amazonaws.com";
    public static final String AWS_URL = "https://search-mbmd-sa6qxdlv3ohnzaozxvrnqfzbli.ap-northeast-1.es.amazonaws.com";
    public static final String AWS_INDEX = "mbmb";
    public static final String AWS_TYPE = "doc";
//    public static final String AWS_URL = "search-mbmd-sa6qxdlv3ohnzaozxvrnqfzbli.ap-northeast-1.es.amazonaws.com";

    public static final String UUID_STRING_SERVICE_MABEEE_M = "1D700000-7CB9-47B3-B889-071D28299206";
    public static final UUID CCCD = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    public static final String PREFERENCES_NAME = "DataSave";
    public static final String PREFERENCES_NORTIFY_INTERVAL_DATA = "NortifyInterval";

    private static List<BluetoothDevice> deviceList;
    private static Map<String, Integer> devRssiValues;
    private static Map<String, ScanResult> devScanRecord;
    private static Map<String, Date> devScanDate;

    public static long mlNortifyIntervalSec = 0;
    SharedPreferences mSharedPref;

    public final static String NORTIFY_DATA_SET_CHANGED =
            "either.com.mabeeemscanner.BleService.NORTIFY_DATA_SET_CHANGED";

    private static int  m_StartId;

    @Override
    public void onCreate() {
        Log.i("UartService", "onCreate");
//        NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext());
//        startForeground(R.drawable.ic_launcher_foreground, builder.build());

        IntentFilter intentFilter = new IntentFilter(PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED);
        registerReceiver(new DozeStateReceiver(), intentFilter);

        populateList();
//        mBluetoothLeScanner = mBluetoothAdapter.getBluetoothLeScanner();

        PowerManager powerManager =  (PowerManager)getSystemService(POWER_SERVICE);
        if (null != powerManager){
            m_wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK , "MyWakelockTag");
            m_wakeLock.acquire();
        }

        scanBleThread();

        desableDose();

        mSharedPref = getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = mSharedPref.edit();
        if (false == mSharedPref.contains(PREFERENCES_NORTIFY_INTERVAL_DATA)){
            editor.putLong(PREFERENCES_NORTIFY_INTERVAL_DATA, 0);
            editor.apply();
        } else {
            GetNortifyIntervalSec();
        }


        super.onCreate();
    }

    void SetNotification(int startId, long lwen, String strContentText){
        Intent activityIntent = new Intent(this, DeviceListActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, activityIntent, 0);
        Notification notification;
        if (0 != lwen && false == strContentText.isEmpty()){
            notification = new Notification.Builder(this)
                    .setContentTitle("MabeeeMScanner")
                    .setContentText(strContentText)
                    .setContentIntent(pendingIntent)
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setWhen(lwen)
                    .setDefaults(Notification.DEFAULT_ALL)
                    .build();
        } else {
            notification = new Notification.Builder(this)
                    .setContentTitle("MabeeeMScanner")
                    //                .setContentText("Test")
                    .setContentIntent(pendingIntent)
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .build();
        }
        startForeground(startId, notification);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // to do something
        m_StartId = startId;
        SetNotification(m_StartId, 0, "");

        return START_NOT_STICKY;
    }

    private void desableDose(){
        //電池の最適化無効
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            String packageName = getPackageName();
            if (pm.isIgnoringBatteryOptimizations(packageName)) return;

            Intent intent = new Intent();
            // Activity以外からActivityを呼び出すためのフラグを設定
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            //バッテリー最適化の無視
            intent.setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            intent.setData(Uri.parse("package:" + packageName));
            startActivity(intent);
        }
    }



    private void populateList() {
        /* Initialize device list container */
        Log.d(TAG, "populateList");
        deviceList = new ArrayList<BluetoothDevice>();
        devRssiValues = new HashMap<String, Integer>();
        devScanRecord = new HashMap<String, ScanResult>();
        devScanDate = new HashMap<String, Date>();


        //       BtScan();
    }

    public boolean GetScanStatus(){
        return mScanning;
    }

    private void scanBleThread(){
        mHandler = new Handler();
        mTimer = new Timer();
        mTimer.schedule(new TimerTask() {
            @Override
            public void run() {

                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        // 実行したい処理
 //                       if(false == GetScanStatus()) {
 //                           scanLeDevice(true);
 //                       }
                        android.os.Process.setThreadPriority(Thread.MAX_PRIORITY);
                        if (false == GetScanStatus()) {
                            scanLeDevice(false);
                            scanLeDevice(true);
                        }
 //                       setAlarm();
                        PopDeviceList();
                    }
                });
            }
        }, 1000, 1000 * 60); // 実行したい間隔(ミリ秒)
 //       }, 1000, 5000); // 実行したい間隔(ミリ秒)
 //       }, 1000, 1000); // 実行したい間隔(ミリ秒)
    }

    private void PopDeviceList(){
        boolean blUpdate = false;
        Date dTimNow = new Date(System.currentTimeMillis());
        Date dateLastUpdate = null;
        long lAwsUpdateIntervalMs;
        if (0 == mlNortifyIntervalSec){
            lAwsUpdateIntervalMs = AWS_UPDATE_INTERVAL_MS;
        } else {
            lAwsUpdateIntervalMs = mlNortifyIntervalSec * AWS_UPDATE_INTERVAL_MS;
        }

//        for (BluetoothDevice listDev : deviceList) {
        for (int i=0; i<deviceList.size();i++) {
            String strDeviceAddr = deviceList.get(i).getAddress();
            dateLastUpdate = new Date(devScanDate.get(strDeviceAddr).getTime());
            long lTimeDiffMsec = dTimNow.getTime() - dateLastUpdate.getTime();
            if (lAwsUpdateIntervalMs < lTimeDiffMsec) {
                devRssiValues.remove(strDeviceAddr);
                devScanRecord.remove(strDeviceAddr);
                devScanDate.remove(strDeviceAddr);
                deviceList.remove(i);

                blUpdate = true;
            }
        }

        if(true == blUpdate){
            broadcastUpdate(NORTIFY_DATA_SET_CHANGED);
        }
    }

    void setAlarm(){
        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
//        PendingIntent pendingIntent = getPendingIntent();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            alarmManager.setAlarmClock(new AlarmManager.AlarmClockInfo(1000, null), null);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, 1000, null);
        } else {
            alarmManager.set(AlarmManager.RTC_WAKEUP, 1000, null);
        }
    }

    public void scanLeDevice(final boolean enable) {

        if (mBluetoothAdapter == null) {
            final BluetoothManager bluetoothManager =
                    (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            mBluetoothAdapter = bluetoothManager.getAdapter();
        }
        if (null == mBluetoothLeScanner) {
            mBluetoothLeScanner = mBluetoothAdapter.getBluetoothLeScanner();
        }
        if (mBluetoothLeScanner == null) {
            return;
        }

        if (enable) {

            // Empty data
            byte[] manData = new byte[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0};
            byte[] manDataRev = new byte[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0};

            // Data Mask
//            byte[] mask    = new byte[]{0,0,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,0,0,0,0,0};
            byte bMask = (byte)0xFF;
            byte[] mask    = new byte[]{0x00,0x00,bMask,bMask,bMask,bMask,bMask,bMask,bMask,bMask,
                    bMask,bMask,bMask,bMask,bMask,bMask,bMask,bMask,0x00,0x00,0x00,0x00,0x00};

            // Copy UUID into data array and remove all "-"
            System.arraycopy(hexStringToByteArray(UUID_STRING_SERVICE_MABEEE_M.replace("-",""), false), 0, manData, 2, 16);
            System.arraycopy(hexStringToByteArray(UUID_STRING_SERVICE_MABEEE_M.replace("-",""), true), 0, manDataRev, 2, 16);
            String strBuf = "";
            for(int i=0; i<manData.length; i++){
                strBuf += IntToHex2(manData[i] & 0xFF);
            }
            Log.i(TAG, "uuid:" + strBuf);

            // Add data array to filters
//            ScanFilter filter = new ScanFilter.Builder().setServiceUuid();
            ScanFilter filter = new ScanFilter.Builder().setManufacturerData(0x004C, manData, mask).build();
            ScanFilter filterRev = new ScanFilter.Builder().setManufacturerData(0x004C, manDataRev, mask).build();

            List<ScanFilter> filters = new ArrayList<>();

            filters.add(filter);
            filters.add(filterRev);
            
            ScanSettings settings = new ScanSettings.Builder()
//                    .setScanMode(ScanSettings.SCAN_MODE_BALANCED)
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                    .build();

            mScanning = true;
//            mBluetoothLeScanner.startScan(mLeScanCallback);
            mBluetoothLeScanner.startScan(filters, settings, mLeScanCallback);

//            mBluetoothAdapter.startLeScan(mLeScanCallback);

        } else {
            mScanning = false;
            mBluetoothLeScanner.stopScan(mLeScanCallback);
            //mBluetoothAdapter.stopLeScan(mLeScanCallback);
        }

    }

    /*
    public static byte[] hexStringToByteArray(String s) {
        int len = s.length();
        int byte_len = len / 2;
        int data_pointer = 0;
        byte[] data = new byte[byte_len];
        for (int i = 0; i < len; i += 2) {
            data_pointer = len - 2 - i;
            data[i / 2] = (byte) ((Character.digit(s.charAt(data_pointer), 16) << 4)
                    + Character.digit(s.charAt(data_pointer + 1), 16));
        }
        return data;
    }
    */
    public static byte[] hexStringToByteArray(String s, boolean blSwapEndian) {
        int len = s.length();
        int byte_len = len / 2;
        int data_pointer = 0;
        byte[] data = new byte[byte_len];
        for (int i = 0; i < len; i += 2) {
            if(false == blSwapEndian){
                data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                        + Character.digit(s.charAt(i + 1), 16));
            } else {
                data_pointer = len - 2 - i;
                data[i / 2] = (byte) ((Character.digit(s.charAt(data_pointer), 16) << 4)
                        + Character.digit(s.charAt(data_pointer + 1), 16));
            }

        }
        return data;
    }

    public String IntToHex2(int i) {
        char hex_2[] = {Character.forDigit((i>>4) & 0x0f,16),Character.forDigit(i&0x0f, 16)};
        String hex_2_str = new String(hex_2);
        return hex_2_str.toUpperCase();
    }

    private ScanCallback mLeScanCallback =
            new ScanCallback() {

                //        public void onScanResult(final BluetoothDevice device, final int rssi, byte[] scanRecord) {
                public void onScanResult(int callbackType, ScanResult result) {
                    final BluetoothDevice device = result.getDevice();
                    final int rssi = result.getRssi();
                    final ScanResult scanResult = result;

                    SparseArray<byte[]> banufacturerSpecificData = scanResult.getScanRecord().getManufacturerSpecificData();
                    byte[] baScanRecord = scanResult.getScanRecord().getBytes();
//                    byte[] baScanRecord = scanResult.getScanRecord().getManufacturerSpecificData().;

                    String strBuf = "";
                    for(int i=0; i<baScanRecord.length; i++){
                        strBuf += IntToHex2(baScanRecord[i] & 0xFF);
                    }
                    Log.i(TAG, "ManufacturerSpecificData:" + scanResult.getScanRecord().getManufacturerSpecificData().toString());

                    Log.i(TAG, "ScanCallback:" + device.toString());
                    addDevice(device,rssi,scanResult);

                }
            };

    private void addDevice(BluetoothDevice device, int rssi, ScanResult result) {
        boolean deviceFound = false;
        boolean blAwsNortify = false;
        Date dateLastUpdate = null;

        for (BluetoothDevice listDev : deviceList) {
            if (listDev.getAddress().equals(device.getAddress())) {
                deviceFound = true;
                dateLastUpdate = new Date(devScanDate.get(device.getAddress()).getTime());
                break;
            }
        }

        devRssiValues.put(device.getAddress(), rssi);
        devScanRecord.put(device.getAddress(), result);

        if (!deviceFound) {
            deviceList.add(device);
//           mEmptyList.setVisibility(View.GONE);
            devScanDate.put(device.getAddress(), new Date(System.currentTimeMillis()));
            blAwsNortify = true;
        } else {
//            long lTimeDiffMsec = devScanDate.get(device.getAddress()).getTime() - dateLastUpdate.getTime();
            Date dTimNow = new Date(System.currentTimeMillis());
            long lTimeDiffMsec = dTimNow.getTime() - dateLastUpdate.getTime();
            Log.i(TAG, "timediff:" + String.valueOf(lTimeDiffMsec));
            long lAwsUpdateIntervalMs;
            if (0 == mlNortifyIntervalSec){
                lAwsUpdateIntervalMs = AWS_UPDATE_INTERVAL_MS;
            } else {
                lAwsUpdateIntervalMs = mlNortifyIntervalSec * AWS_UPDATE_INTERVAL_MS;
            }

            if (lAwsUpdateIntervalMs < lTimeDiffMsec){
                devScanDate.put(device.getAddress(), dTimNow);
                notifyUpDate(device.getAddress(), dTimNow.getTime());
                blAwsNortify = true;
            }
        }
        broadcastUpdate(NORTIFY_DATA_SET_CHANGED);
//        deviceAdapter.notifyDataSetChanged();
        if (blAwsNortify) {
            if(mAwsUpdate){
                awsUpdate(device.getAddress());
            }
        }

    }

    private void notifyUpDate(String strDeviceAddress, long lWen){

        ScanResult scanResult = devScanRecord.get(strDeviceAddress);
        byte[] baScanRecord = scanResult.getScanRecord().getBytes();
        int nVBat = baScanRecord[IBEACON_MAJOR_VALUE_OFFSET + 1] & 0xFF;
        float fVBat = V_BAT_SCALE * nVBat;
        short sTemp = (short)((baScanRecord[IBEACON_MINOR_VALUE_OFFSET] & 0xFF) << 8 | baScanRecord[IBEACON_MINOR_VALUE_OFFSET + 1] & 0xFF);
        float fTemp = (float)sTemp / 10.0f;
        String strDeviceName = scanResult.getDevice().getName();
        String strVBat = String.format("%1$.2fV", fVBat);
        String strTemp = String.format("%1$.1f℃", fTemp);

        SetNotification(m_StartId, lWen, strDeviceName + " " + strVBat + " " + strTemp);


    }

    public void SetNortifyIntervalSec(long lTimeSec){
        SharedPreferences.Editor editor = mSharedPref.edit();
        editor.putLong(PREFERENCES_NORTIFY_INTERVAL_DATA, lTimeSec);
        editor.apply();
        mlNortifyIntervalSec = lTimeSec;
    }

    public long GetNortifyIntervalSec(){
        mlNortifyIntervalSec = mSharedPref.getLong(PREFERENCES_NORTIFY_INTERVAL_DATA, 0);
        return mlNortifyIntervalSec;
    }

    public List<BluetoothDevice> GetDeviceList(){
        return deviceList;
    }

    public Map<String, Integer> GetRssiValues(){
        return devRssiValues;
    }

    public Map<String, ScanResult> GetScanRecord(){
        return devScanRecord;
    }

    public Map<String, Date> GetScanDate(){
        return devScanDate;
    }

    public boolean GetAwsUpdate(){
        return mAwsUpdate;
    }

    public void SetAwsUpdate(boolean blAwsUpDate){
        mAwsUpdate = blAwsUpDate;
    }

    private void broadcastUpdate(final String action) {
        final Intent intent = new Intent(action);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    private void broadcastUpdate(final String action,
                                 final BluetoothGattCharacteristic characteristic) {
        final Intent intent = new Intent(action);
/*
        // This is special handling for the Heart Rate Measurement profile.  Data parsing is
        // carried out as per profile specifications:
        // http://developer.bluetooth.org/gatt/characteristics/Pages/CharacteristicViewer.aspx?u=org.bluetooth.characteristic.heart_rate_measurement.xml
        if (SEISMO_DATA_UUID.equals(characteristic.getUuid())) {
        	
//            Log.d(TAG, String.format("Received TX: %d",characteristic.getValue() ));
            intent.putExtra(EXTRA_DATA, characteristic.getValue());
            try {
//                SaveRcvDat(new String(characteristic.getValue(), "UTF-8"));
            } catch (Exception e) {
                Log.e(TAG, e.toString());
            }
        } else {
        	
        }
*/
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    private void awsUpdate(String strDeviceAddress){
        //AWS_URL
        JSONObject JsonObj = new JSONObject();

        TimeZone tz = TimeZone.getTimeZone("UTC");
 //       DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm'Z'"); // Quoted "Z" to indicate UTC, no timezone offset
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"); // Quoted "Z" to indicate UTC, no timezone offset
        df.setTimeZone(tz);
        String TimeAsISO = df.format(devScanDate.get(strDeviceAddress));
        ScanResult scanResult = devScanRecord.get(strDeviceAddress);
        byte[] baScanRecord = scanResult.getScanRecord().getBytes();
        int nVBat = baScanRecord[IBEACON_MAJOR_VALUE_OFFSET + 1] & 0xFF;
        float fVBat = V_BAT_SCALE * nVBat;
        short sTemp = (short)((baScanRecord[IBEACON_MINOR_VALUE_OFFSET] & 0xFF) << 8 | baScanRecord[IBEACON_MINOR_VALUE_OFFSET + 1] & 0xFF);
        float fTemp = (float)sTemp / 10.0f;

        try {
            JsonObj.put("timestamp", TimeAsISO);
            JsonObj.put("device_name", scanResult.getDevice().getName());
            JsonObj.put("device_addr", strDeviceAddress);
            JsonObj.put("device_model", "MBMB");
            JsonObj.put("rssi_values", devRssiValues.get(strDeviceAddress).intValue());
            JsonObj.put("battery_voltage", String.format("%1$.2f", fVBat));
            JsonObj.put("temp", String.format("%1$.1f", fTemp));

//            String strBuf = JsonObj.toString(JsonObj.length());
//            Log.i(TAG, ":" + JsonObj.toString(JsonObj.length()));
//            String strJson = JsonObj.toString();
            postData(AWS_URL + "/" + AWS_INDEX + "/" + AWS_TYPE  + "/", JsonObj);

        } catch (JSONException e) {
            e.printStackTrace();
        }


    }

    public void postData(String strUrl,JSONObject JsonObj) {
        // Create a new HttpClient and Post Header
        final String fstrUrl = strUrl;
        final JSONObject fJsonObj = JsonObj;
        new Thread(new Runnable() {
            @Override
            public void run() {
                HttpPost httpPost = new HttpPost(fstrUrl);
                DefaultHttpClient client = new DefaultHttpClient();
                JSONObject jsonObject = new JSONObject();
                try{
//                    java.util.logging.Logger.getLogger("org.apache.http.wire").setLevel(java.util.logging.Level.FINEST);
//                    java.util.logging.Logger.getLogger("org.apache.http.headers").setLevel(java.util.logging.Level.FINEST);
                    StringEntity se = new StringEntity(fJsonObj.toString());
                    httpPost.setEntity(se);
                    httpPost.setHeader("Accept", "application/json");
                    httpPost.setHeader("Content-Type", "application/json");
                    HttpResponse response = client.execute(httpPost);
                    Log.i(TAG, "json:" + fJsonObj.toString());
                    Log.i(TAG, "response:" + response.getStatusLine().toString());
                } catch(Exception e) {
                    e.printStackTrace();
                }

            }

        }).start();
    }
    /*
    public void postData(String strUrl,JSONObject JsonObj) {
        // Create a new HttpClient and Post Header
        final String fstrUrl = strUrl;
        final JSONObject fJsonObj = JsonObj;
        new Thread(new Runnable() {
            @Override
            public void run() {
                okhttp3.MediaType mediaTypeJson = okhttp3.MediaType.parse("application/json; charset=utf-8");

                RequestBody requestBody = RequestBody.create(mediaTypeJson, fJsonObj.toString());

                final Request request = new Request.Builder()
                        .url(fstrUrl)
                        .post(requestBody)
                        .build();
            }

        }).start();
    }
*/
/*
    public void postData(String strUrl,JSONObject JsonObj) {
        // Create a new HttpClient and Post Header
        final String fstrUrl = strUrl;
        final JSONObject fJsonObj = JsonObj;
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    int status;
                    String buffer = "";
                    HttpURLConnection con = null;
                    URL url = new URL(fstrUrl);
                    con = (HttpURLConnection) url.openConnection();

                    con.setDoOutput(true);
                    con.setRequestMethod("POST");


                    //接続タイムアウトを設定する。
                    con.setConnectTimeout(100000);
                    //レスポンスデータ読み取りタイムアウトを設定する。
                    con.setReadTimeout(100000);
//                    Log.i(TAG, "http header:" + con.getHeaderFields().toString());
//                    con.setInstanceFollowRedirects(false);
//                    con.setRequestProperty("Accept-Language", "jp");
//                    con.setRequestProperty("Content-Type", "application/json; charset=utf-8");
//                    con.setRequestProperty("Content-Type", "application/octet-stream; charset=utf-8");

                    con.connect();
//                    status = con.getResponseCode();

                    OutputStream os = con.getOutputStream();
                    PrintStream ps = new PrintStream(os);
                    //            ps.print(json);
                    ps.print(fJsonObj.toString());
//                ps.flush();
                    ps.close();

                    BufferedReader reader =
                            new BufferedReader(new InputStreamReader(con.getInputStream(), "UTF-8"));
                    buffer = reader.readLine();

                    JSONArray jsonArray = new JSONArray(buffer);
                    for (int i = 0; i < jsonArray.length(); i++) {
                        JSONObject jsonObject = jsonArray.getJSONObject(i);
                        Log.d("HTTP REQ", jsonObject.getString("name"));
                    }

                    status = con.getResponseCode();
                    con.disconnect();
                    status = con.getResponseCode();
                } catch (MalformedURLException e) {
                    e.printStackTrace();
                } catch (ProtocolException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }
*/
    /*
    public void postData(String strUrl,JSONObject JsonObj) {
        // Create a new HttpClient and Post Header
        final String fstrUrl = strUrl;
        final JSONObject fJsonObj = JsonObj;
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String host = fstrUrl; // For example, my-test-domain.us-east-1.es.amazonaws.com
                    String index = "mbmb";
                    String type = "mbmb";
                    String id = "6";

                    String json = "{" + "\"title\":\"Walk the Line\"," + "\"director\":\"James Mangold\"," + "\"year\":\"2005\""
                            + "}";

                    RestClient client = RestClient.builder(new HttpHost(host, 443, "https")).build();

                    HttpEntity entity = new NStringEntity(json, ContentType.APPLICATION_JSON);

                    Response response = client.performRequest("PUT", "/" + index + "/" + type + "/" + id,
                            Collections.<String, String>emptyMap(), entity);

                    System.out.println(response.toString());
                } catch (IOException e) {
                    e.printStackTrace();
                }

            }
        }).start();
    }
    */

    public class LocalBinder extends Binder {
        BleService getService() {
            return BleService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        // After using a given device, you should make sure that BluetoothGatt.close() is called
        // such that resources are cleaned up properly.  In this particular example, close() is
        // invoked when the UI is disconnected from the Service.
//        close();
        return super.onUnbind(intent);
    }

    private final IBinder mBinder = new LocalBinder();

    /**
     * Initializes a reference to the local Bluetooth adapter.
     *
     * @return Return true if the initialization is successful.
     */
    public boolean initialize() {
        // For API level 18 and above, get a reference to BluetoothAdapter through
        // BluetoothManager.
        if (mBluetoothManager == null) {
            mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            if (mBluetoothManager == null) {
                Log.e(TAG, "Unable to initialize BluetoothManager.");
                return false;
            }
        }

        mBluetoothAdapter = mBluetoothManager.getAdapter();
        if (mBluetoothAdapter == null) {
            Log.e(TAG, "Unable to obtain a BluetoothAdapter.");
            return false;
        }

        return true;
    }

    public String GetRegisterdDeviceAddress(){
        return mBluetoothDeviceAddress;
    }

    public String GetRegisterdDeviceName(){
        return mBluetoothDeviceName;
    }

    public BluetoothDevice GetBluetoothDevice(){
        return mDevice;
    }

    /**
     * Disconnects an existing connection or cancel a pending connection. The disconnection result
     * is reported asynchronously through the
     * {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
     * callback.
     */
    public void disconnect() {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.disconnect();
       // mBluetoothGatt.close();
    }

    /**
     * After using a given BLE device, the app must call this method to ensure resources are
     * released properly.
     */
    public void close() {
        if (mBluetoothGatt == null) {
            return;
        }
        Log.w(TAG, "mBluetoothGatt closed");
 //       mBluetoothDeviceAddress = null;
        mBluetoothGatt.close();
        mBluetoothGatt = null;
    }


    private void showMessage(String msg) {
        Log.e(TAG, msg);
    }
    /**
     * Retrieves a list of supported GATT services on the connected device. This should be
     * invoked only after {@code BluetoothGatt#discoverServices()} completes successfully.
     *
     * @return A {@code List} of supported services.
     */
    public List<BluetoothGattService> getSupportedGattServices() {
        if (mBluetoothGatt == null) return null;

        return mBluetoothGatt.getServices();
    }


    private byte MakeSum(byte[] bSrc, int nLen) {
        byte bSum = 0;
        for (int i=0;i<nLen;i++) {
//            bSum |= bSrc[i];
            bSum = (byte)(bSum + bSrc[i]);
        }
        return bSum;
    }

    public class DozeStateReceiver extends BroadcastReceiver {
        private final String TAG = DozeStateReceiver.class.getSimpleName();

        @Override
        public void onReceive(Context context, Intent intent) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PowerManager powerManager = context.getSystemService(PowerManager.class);
                boolean isDoze = powerManager.isDeviceIdleMode();
                Log.d(TAG, "isDoze: " + isDoze);
                AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
//                PendingIntent pendingIntent = getPendingIntent();
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    alarmManager.setAlarmClock(new AlarmManager.AlarmClockInfo(1, null), null);
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                    alarmManager.setExact(AlarmManager.RTC_WAKEUP, 1, null);
                } else {
                    alarmManager.set(AlarmManager.RTC_WAKEUP, 1, null);
                }
            }
        }
    }

}

