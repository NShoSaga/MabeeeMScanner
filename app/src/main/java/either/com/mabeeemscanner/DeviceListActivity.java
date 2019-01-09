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

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;


import android.Manifest;
import android.app.Activity;
import android.app.Dialog;
import android.app.TimePickerDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.ParcelUuid;
import android.os.PowerManager;
import android.provider.Settings;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.util.SparseArray;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ListView;
import android.widget.NumberPicker;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

public class DeviceListActivity extends Activity {
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothLeScanner mBluetoothLeScanner;

   // private BluetoothAdapter mBtAdapter;
    private TextView mEmptyList;
    public static final String TAG = "DeviceListActivity";

    private CompoundButton mtogglebtnAws = null;

    private BleService mBleService = null;
    List<BluetoothDevice> deviceList;
    private DeviceAdapter deviceAdapter;
    private ServiceConnection onService = null;

    private static final long SCAN_PERIOD = 100000; //100 seconds
    private static final int REQUEST_BATTERY = 1;
    private static final int REQUEST_ENABLE_BT = 2;

    private Handler mHandler;
    private boolean mScanning;
    private BluetoothAdapter mBtAdapter = null;

    private TimePickerDialog mTimePickerDialog;
    int mHour;
    int mMinute;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
    	
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");
//        getWindow().setFeatureInt(Window.FEATURE_CUSTOM_TITLE, R.layout.title_bar);
        if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.ICE_CREAM_SANDWICH){
            requestWindowFeature(Window.FEATURE_ACTION_BAR);
        }
        else{
            requestWindowFeature(Window.FEATURE_NO_TITLE);
        }
        setContentView(R.layout.device_list);
        android.view.WindowManager.LayoutParams layoutParams = this.getWindow().getAttributes();
        layoutParams.gravity=Gravity.TOP;
        layoutParams.y = 200;
        mHandler = new Handler();

        // Use this check to determine whether BLE is supported on the device.  Then you can
        // selectively disable BLE-related features.
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_SHORT).show();
            finish();
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                // 権限がない場合はリクエスト
                requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, 1);
            }
//            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                // 権限がない場合はリクエスト
//                requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
//            }
        }

        EnableLocationManager();
        // Initializes a Bluetooth adapter.  For API level 18 and above, get a reference to
        // BluetoothAdapter through BluetoothManager.
        final BluetoothManager bluetoothManager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();

        // Checks if Bluetooth is supported on the device.
        if (mBluetoothAdapter == null) {
            Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

//        mBluetoothLeScanner = mBluetoothAdapter.getBluetoothLeScanner();

        populateList();
        mEmptyList = (TextView) findViewById(R.id.empty);
        /*
        Button cancelButton = (Button) findViewById(R.id.btn_cancel);
        cancelButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
            	
//            	if (mScanning==false) scanLeDevice(true);
//            	else finish();
            }
        });
        */
        mtogglebtnAws = (CompoundButton)findViewById(R.id.togglebtn_aws);
        mtogglebtnAws.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                // 状態が変更された
                mBleService.SetAwsUpdate(isChecked);
//                Toast.makeText(DeviceListActivity.this, "isChecked : " + isChecked, Toast.LENGTH_SHORT).show();
            }
        });

        service_init();

    }

    private void EnableLocationManager(){
        // 位置情報を管理している LocationManager のインスタンスを生成する
        LocationManager locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        String locationProvider = null;

        // GPSが利用可能になっているかどうかをチェック
        if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            locationProvider = LocationManager.GPS_PROVIDER;
        }
        // GPSプロバイダーが有効になっていない場合は基地局情報が利用可能になっているかをチェック
        else if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            locationProvider = LocationManager.NETWORK_PROVIDER;
        }
        // いずれも利用可能でない場合は、GPSを設定する画面に遷移する
        else {
            Intent settingsIntent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
            startActivity(settingsIntent);
            return;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.device_list_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()){
            case R.id.menu_settings:
                Log.d(TAG, "Settings Selected.");
                ShowTimePickerDlg();
                break;
        }
        return true;
    }

    private void ShowTimePickerDlg(){
        // レイアウト設定
        final Dialog popupDialog = new Dialog(DeviceListActivity.this);
//        popupDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        popupDialog.setTitle("Set interval");
        popupDialog.setCanceledOnTouchOutside(false);
        popupDialog.setContentView(R.layout.popup_setting);
        final NumberPicker NumberPicer1 = (NumberPicker) popupDialog.findViewById(R.id.numberPicker1);
        final NumberPicker NumberPicer2 = (NumberPicker) popupDialog.findViewById(R.id.numberPicker2);
        final NumberPicker NumberPicer3 = (NumberPicker) popupDialog.findViewById(R.id.numberPicker3);
        NumberPicer1.setMaxValue(24);
        NumberPicer2.setMaxValue(59);
        NumberPicer3.setMaxValue(59);

        long lGetTimeSec = mBleService.GetNortifyIntervalSec();
        NumberPicer1.setValue((int)(lGetTimeSec / 3600));
        NumberPicer2.setValue((int)((lGetTimeSec / 60) % 60));
        NumberPicer3.setValue((int)(lGetTimeSec % 60));
        popupDialog.findViewById(R.id.cancel_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                popupDialog.dismiss();
            }
        });
        popupDialog.findViewById(R.id.ok_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                long SetTimeSec = (long)NumberPicer1.getValue() * 3600 +
                                    (long)NumberPicer2.getValue() * 60 +
                                    NumberPicer3.getValue();
                mBleService.SetNortifyIntervalSec(SetTimeSec);
                if (popupDialog.isShowing()) {
                    popupDialog.dismiss();
                }
            }
        });

        popupDialog.setOnKeyListener(new DialogInterface.OnKeyListener() {
            @Override
            public boolean onKey(DialogInterface dialog, int keyCode, KeyEvent event) {
                if (event.getAction() == KeyEvent.ACTION_UP && keyCode == KeyEvent.KEYCODE_BACK) {
                    if (popupDialog.isShowing()) {
                        popupDialog.dismiss();
                    }

                }
                return false;
            }
        });

        // 表示サイズの設定 今回は幅300dp
        float width = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 300, getResources().getDisplayMetrics());
        popupDialog.show();
    }


    private void BtScan() {
        if (!mBluetoothAdapter.isEnabled()) {
            Log.i(TAG, "onClick - BT not enabled yet");
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
        } else {
            if (null != mBleService) {
                if(false == mBleService.GetScanStatus()){
//                   mBleService.scanLeDevice(true);
                }
            }
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {

            case REQUEST_ENABLE_BT:
                // When the request to enable Bluetooth returns
                if (resultCode == Activity.RESULT_OK) {
                    Toast.makeText(this, "Bluetooth has turned on ", Toast.LENGTH_SHORT).show();
                    final BluetoothManager bluetoothManager =
                            (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
                    mBluetoothAdapter = bluetoothManager.getAdapter();
                    mBluetoothLeScanner = mBluetoothAdapter.getBluetoothLeScanner();
                    if (null != mBleService) {
                        if(false == mBleService.GetScanStatus()){
//                            mBleService.scanLeDevice(true);
                        }

                    }
                } else {
                    // User did not enable Bluetooth or an error occurred
                    Log.d(TAG, "BT not enabled");
                    Toast.makeText(this, "Problem in BT Turning ON ", Toast.LENGTH_SHORT).show();
                    finish();
                }
                break;
            default:
                Log.e(TAG, "wrong request code");
                break;
        }
    }

    private void populateList() {
        /* Initialize device list container */
        Log.d(TAG, "populateList");
        deviceList = new ArrayList<BluetoothDevice>();
        deviceAdapter = new DeviceAdapter(this, deviceList);
 //       devRssiValues = new HashMap<String, Integer>();
 //       devScanRecord = new HashMap<String, ScanResult>();
 //       devScanDate = new HashMap<String, Date>();

        ListView newDevicesListView = (ListView) findViewById(R.id.new_devices);
        newDevicesListView.setAdapter(deviceAdapter);
        newDevicesListView.setOnItemClickListener(mDeviceClickListener);

 //       BtScan();
    }

    //service connected/disconnected
    private ServiceConnection mServiceConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder rawBinder) {
            mBleService = ((BleService.LocalBinder) rawBinder).getService();
            Log.d(TAG, "onServiceConnected mService= " + mBleService);
            if (!mBleService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                finish();
            }
            BtScan();
            if (null != mtogglebtnAws){
                mtogglebtnAws.setChecked(mBleService.GetAwsUpdate());
            }
        }

        public void onServiceDisconnected(ComponentName classname) {
            ////     mService.disconnect(mDevice);
            mBleService = null;
        }
    };

    private final BroadcastReceiver BleStatusChangeReceiver = new BroadcastReceiver() {

        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            final Intent mIntent = intent;
            //*********************//

            if (action.equals(BleService.NORTIFY_DATA_SET_CHANGED)) {
                runOnUiThread(new Runnable() {
                    public void run() {
//                        String currentDateTimeString = DateFormat.getTimeInstance().format(new Date());
                        Log.d(TAG, "BLE_CONNECT_MSG");
                        if (null != mBleService) {
//                            deviceList.add(device);
//                            deviceList = mBleService.GetDeviceList();
                            deviceList.clear();
                            for (BluetoothDevice BtDevice : mBleService.GetDeviceList()){
//                                comList2.add(comB.clone());
                                deviceList.add(BtDevice);
                            }
//                            deviceList.add(mBleService.GetDeviceList());
                            mEmptyList.setVisibility(View.GONE);
                            deviceAdapter.notifyDataSetChanged();
                        }

                    }
                });
            }

        }
    };

    @Override
    public void onStart() {
        super.onStart();
       
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
    }

    @Override
    public void onStop() {
        super.onStop();
        //mBluetoothLeScanner.stopScan(mLeScanCallback);
        //mBluetoothAdapter.stopLeScan(mLeScanCallback);
    
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
//        mBluetoothLeScanner.stopScan(mLeScanCallback);
        try {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(BleStatusChangeReceiver);
        } catch (Exception ignore) {
            Log.e(TAG, ignore.toString());
        }
        unbindService(mServiceConnection);
//        mBleService.stopSelf();
        mBleService= null;
    }

    private OnItemClickListener mDeviceClickListener = new OnItemClickListener() {
    	
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
 /*
            BluetoothDevice device = deviceList.get(position);
            mBluetoothLeScanner.stopScan(mLeScanCallback);
            //mBluetoothAdapter.stopLeScan(mLeScanCallback);

            Bundle b = new Bundle();
            b.putString(BluetoothDevice.EXTRA_DEVICE, deviceList.get(position).getAddress());

            Intent result = new Intent();
            result.putExtras(b);
            setResult(Activity.RESULT_OK, result);
            finish();
  */
        }
    };

    @Override
    protected void onResume() {
        super.onResume();
//        BtScan();

    }

    protected void onPause() {
        super.onPause();

    }

    private void service_init() {
        startService(new Intent(this, BleService.class));
        Intent bindIntent = new Intent(this, BleService.class);
        startService(bindIntent);
        bindService(bindIntent, mServiceConnection, Context.BIND_AUTO_CREATE);

        LocalBroadcastManager.getInstance(this).registerReceiver(BleStatusChangeReceiver, makeGattUpdateIntentFilter());
    }

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BleService.NORTIFY_DATA_SET_CHANGED);

        return intentFilter;
    }
    
    class DeviceAdapter extends BaseAdapter {
        Context context;
        List<BluetoothDevice> devices;
        LayoutInflater inflater;

        public DeviceAdapter(Context context, List<BluetoothDevice> devices) {
            this.context = context;
            inflater = LayoutInflater.from(context);
            this.devices = devices;
        }

        @Override
        public int getCount() {
            return devices.size();
        }

        @Override
        public Object getItem(int position) {
            return devices.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewGroup vg;

            if (convertView != null) {
                vg = (ViewGroup) convertView;
            } else {
                vg = (ViewGroup) inflater.inflate(R.layout.device_element, null);
            }

            if (null == mBleService) {
                return vg;
            }

            BluetoothDevice device = devices.get(position);
            final TextView tvadd = ((TextView) vg.findViewById(R.id.address));
            final TextView tvname = ((TextView) vg.findViewById(R.id.name));
            final TextView tvtime = ((TextView) vg.findViewById(R.id.time));
            final TextView battery = ((TextView) vg.findViewById(R.id.battery));
            final TextView temp = ((TextView) vg.findViewById(R.id.temp));
            final TextView tvpaired = (TextView) vg.findViewById(R.id.paired);
            final TextView tvrssi = (TextView) vg.findViewById(R.id.rssi);

            tvrssi.setVisibility(View.VISIBLE);
            byte rssival = (byte) mBleService.GetRssiValues().get(device.getAddress()).intValue();
            if (rssival != 0) {
                tvrssi.setText("Rssi = " + String.valueOf(rssival));
            }

            ScanResult scanResult = mBleService.GetScanRecord().get(device.getAddress());
            SparseArray<byte[]> banufacturerSpecificData = scanResult.getScanRecord().getManufacturerSpecificData();
            byte[] baScanRecord = scanResult.getScanRecord().getBytes();

            String strBuf = "";
            for(int i=0; i<baScanRecord.length; i++){
                strBuf += mBleService.IntToHex2(baScanRecord[i] & 0xFF);
            }
//            Log.i(TAG, "ScanRecord:" + strBuf);

            int nVBat = baScanRecord[BleService.IBEACON_MAJOR_VALUE_OFFSET + 1] & 0xFF;
            float fVBat = BleService.V_BAT_SCALE * nVBat;

            battery.setText(String.format("%1$.2fV", fVBat));

            short sTemp = (short)((baScanRecord[BleService.IBEACON_MINOR_VALUE_OFFSET] & 0xFF) << 8 | baScanRecord[BleService.IBEACON_MINOR_VALUE_OFFSET + 1] & 0xFF);
            float fTemp = (float)sTemp / 10.0f;

            temp.setText(String.format("%1$.1f℃", fTemp));

 //           tvname.setText(device.getName());
            tvname.setText(scanResult.getDevice().getName());
            tvadd.setText(device.getAddress());
            final DateFormat df = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
            final Date date = mBleService.GetScanDate().get(device.getAddress());
            tvtime.setText(df.format(date));

            if (device.getBondState() == BluetoothDevice.BOND_BONDED) {
                Log.i(TAG, "device::"+device.getName());
                tvname.setTextColor(Color.WHITE);
                tvadd.setTextColor(Color.WHITE);
                tvpaired.setTextColor(Color.GRAY);
                tvpaired.setVisibility(View.VISIBLE);
                tvpaired.setText(R.string.paired);
                tvrssi.setVisibility(View.VISIBLE);
                tvrssi.setTextColor(Color.WHITE);
                
            } else {
                tvname.setTextColor(Color.WHITE);
                battery.setTextColor(Color.WHITE);
                temp.setTextColor(Color.WHITE);
                tvadd.setTextColor(Color.WHITE);
                tvpaired.setVisibility(View.GONE);
                tvrssi.setVisibility(View.VISIBLE);
                tvrssi.setTextColor(Color.WHITE);
            }
            return vg;
        }
    }


    private void showMessage(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}
