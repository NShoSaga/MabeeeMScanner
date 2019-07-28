package either.com.mabeeemscanner;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanResult;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.provider.Settings;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.content.res.ResourcesCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.util.SparseArray;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_BATTERY = 1;
    private static final int REQUEST_ENABLE_BT = 2;
    private static final int TOP_LAYER_INDEX = 1;

    private static final int TOP_LAYER_CLEAR_DRAY_MS = 1000;

    public static final String TAG = "MainActivity";
    private BluetoothAdapter mBluetoothAdapter;
    private BleService mBleService = null;
    List<BluetoothDevice> m_deviceList;
    private BluetoothLeScanner mBluetoothLeScanner;
//    Map<String, Drawable> m_mpRemCode2Layer = new HashMap<String, Drawable>();
    Map<String, Integer> m_mpRemCode2Layer = new HashMap<String, Integer>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
//        Toolbar toolbar = findViewById(R.id.toolbar);
//        setSupportActionBar(toolbar);

//        ImageView imageView2 = findViewById(R.id.imageView);
//        imageView2.setImageResource(R.drawable.remo_base);

        // LayerDrawable の素材となる Drawable を取得
        Resources r = getResources();
        Drawable shape = r.getDrawable(R.drawable.remo_base);
        Drawable btn = r.getDrawable(R.drawable.remo_crear);

// LayerDrawable を生成
        Drawable[] layers = { shape, btn };
        LayerDrawable layerDrawable = new LayerDrawable(layers);

// padding を設定する
        int padding = r.getDimensionPixelSize(R.dimen.fab_margin);
        layerDrawable.setLayerInset(TOP_LAYER_INDEX, padding, padding, padding, padding);

// View にセットする
        ImageView view = (ImageView) findViewById(R.id.imageView);
        view.setImageDrawable(layerDrawable);

        Remcode2LayerMapInit();

        EnableBt();
        service_init();
    }

    private void Remcode2LayerMapInit(){
        m_mpRemCode2Layer.put("1502", R.drawable.remo_b0);
        m_mpRemCode2Layer.put("1702", R.drawable.remo_b1);
        m_mpRemCode2Layer.put("2502", R.drawable.remo_b2);
        m_mpRemCode2Layer.put("102e", R.drawable.remo_b3);
        m_mpRemCode2Layer.put("102e", R.drawable.remo_b4);
        m_mpRemCode2Layer.put("322e", R.drawable.remo_b5);
        m_mpRemCode2Layer.put("1835", R.drawable.remo_b6);
        m_mpRemCode2Layer.put("3d35", R.drawable.remo_b7);
        m_mpRemCode2Layer.put("362e", R.drawable.remo_b8);
        m_mpRemCode2Layer.put("3335", R.drawable.remo_b9);

        m_mpRemCode2Layer.put("0002", R.drawable.remo_b10);
        m_mpRemCode2Layer.put("0102", R.drawable.remo_b11);
        m_mpRemCode2Layer.put("0202", R.drawable.remo_b12);
        m_mpRemCode2Layer.put("0302", R.drawable.remo_b13);
        m_mpRemCode2Layer.put("0402", R.drawable.remo_b14);
        m_mpRemCode2Layer.put("0502", R.drawable.remo_b15);
        m_mpRemCode2Layer.put("0602", R.drawable.remo_b16);
        m_mpRemCode2Layer.put("0702", R.drawable.remo_b17);
        m_mpRemCode2Layer.put("0802", R.drawable.remo_b18);
        m_mpRemCode2Layer.put("0902", R.drawable.remo_b19);
        m_mpRemCode2Layer.put("0a02", R.drawable.remo_b20);
        m_mpRemCode2Layer.put("0b02", R.drawable.remo_b21);

        m_mpRemCode2Layer.put("3a02", R.drawable.remo_b22);
        m_mpRemCode2Layer.put("0c2e", R.drawable.remo_b23);
        m_mpRemCode2Layer.put("1402", R.drawable.remo_b24);
        m_mpRemCode2Layer.put("1002", R.drawable.remo_b25);
        m_mpRemCode2Layer.put("1102", R.drawable.remo_b26);
        m_mpRemCode2Layer.put("1202", R.drawable.remo_b27);
        m_mpRemCode2Layer.put("1302", R.drawable.remo_b28);

        m_mpRemCode2Layer.put("1b09", R.drawable.remo_b29);
        m_mpRemCode2Layer.put("2003", R.drawable.remo_b30);
        m_mpRemCode2Layer.put("3403", R.drawable.remo_b31);
        m_mpRemCode2Layer.put("3302", R.drawable.remo_b32);
        m_mpRemCode2Layer.put("3402", R.drawable.remo_b33);
        m_mpRemCode2Layer.put("3503", R.drawable.remo_b34);
        m_mpRemCode2Layer.put("2503", R.drawable.remo_b35);
        m_mpRemCode2Layer.put("232e", R.drawable.remo_b36);
        m_mpRemCode2Layer.put("152e", R.drawable.remo_b37);

        m_mpRemCode2Layer.put("242e", R.drawable.remo_b38);
        m_mpRemCode2Layer.put("252e", R.drawable.remo_b39);
        m_mpRemCode2Layer.put("262e", R.drawable.remo_b40);
        m_mpRemCode2Layer.put("272e", R.drawable.remo_b41);

        m_mpRemCode2Layer.put("1b2e", R.drawable.remo_b42);
        m_mpRemCode2Layer.put("1a2e", R.drawable.remo_b43);
        m_mpRemCode2Layer.put("1c2e", R.drawable.remo_b44);
        m_mpRemCode2Layer.put("202e", R.drawable.remo_b45);
        m_mpRemCode2Layer.put("182e", R.drawable.remo_b46);
        m_mpRemCode2Layer.put("192e", R.drawable.remo_b47);
    }

    void EnableBt(){
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

        m_deviceList = new ArrayList<BluetoothDevice>();
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

    private void service_init() {
        startService(new Intent(this, BleService.class));
        Intent bindIntent = new Intent(this, BleService.class);
        startService(bindIntent);
        bindService(bindIntent, mServiceConnection, Context.BIND_AUTO_CREATE);

        LocalBroadcastManager.getInstance(this).registerReceiver(BleStatusChangeReceiver, makeGattUpdateIntentFilter());
    }

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
//                            m_deviceList.add(device);
//                            m_deviceList = mBleService.GetDeviceList();
                            m_deviceList.clear();
                            for (BluetoothDevice BtDevice : mBleService.GetDeviceList()){
//                                comList2.add(comB.clone());
                                m_deviceList.add(BtDevice);
                            }
                            if (false == m_deviceList.isEmpty()){
                                UpdateUi(m_deviceList.get(0));
                            }
//                            mEmptyList.setVisibility(View.GONE);
//                            deviceAdapter.notifyDataSetChanged();
                        }

                    }
                });
            }

        }
    };

    private void UpdateUi(BluetoothDevice device){
        ScanResult scanResult = mBleService.GetScanRecord().get(device.getAddress());
        SparseArray<byte[]> banufacturerSpecificData = scanResult.getScanRecord().getManufacturerSpecificData();
        byte[] baScanRecord = scanResult.getScanRecord().getBytes();

        short sRemData = (short)(baScanRecord[BleService.IBEACON_REMOCON_DATA_VALUE_OFFSET] & 0xFF);
        short sRemAddr = (short)(baScanRecord[BleService.IBEACON_REMOCON_ADDR_VALUE_OFFSET] & 0xFF);

        String strRemData = String.format("%02x", sRemData);
        String strRemAddr = String.format("%02x", sRemAddr);
        Log.d(TAG, "RemoData " + strRemData + strRemAddr);

        ImageView view = (ImageView) findViewById(R.id.imageView);
        LayerDrawable layer = (LayerDrawable) view.getDrawable();

        // LayerDrawable の素材となる Drawable を取得
        if (false == m_mpRemCode2Layer.containsKey(strRemData + strRemAddr)){
            return;
        }
        int nResourceId = m_mpRemCode2Layer.get(strRemData + strRemAddr).intValue();
        Drawable drawableLayer = ResourcesCompat.getDrawable(getResources(), m_mpRemCode2Layer.get(strRemData + strRemAddr).intValue(), null);
        // 入れ替え前の Drawable から Bounds を取得してセットする
        if (null != drawableLayer) {
            int nLayerCount = layer.getNumberOfLayers();
            Rect bounds = layer.getDrawable(TOP_LAYER_INDEX).getBounds();
            drawableLayer.setBounds(bounds);
            layer.setDrawable(TOP_LAYER_INDEX, drawableLayer);
            layer.invalidateSelf();

            new Handler().postDelayed(ClearUiTopLayer, TOP_LAYER_CLEAR_DRAY_MS);
        }
    }

    private final Runnable ClearUiTopLayer= new Runnable() {
        @Override
        public void run() {
            //ここに実行したい処理を記述
            ImageView view = (ImageView) findViewById(R.id.imageView);
            LayerDrawable layer = (LayerDrawable) view.getDrawable();

            // LayerDrawable の素材となる Drawable を取得
            Drawable drawableLayer = getResources().getDrawable(R.drawable.remo_crear);
            // 入れ替え前の Drawable から Bounds を取得してセットする
            int nLayerCount = layer.getNumberOfLayers();
            Rect bounds = layer.getDrawable(TOP_LAYER_INDEX).getBounds();
            drawableLayer.setBounds(bounds);
            layer.setDrawable(TOP_LAYER_INDEX, drawableLayer);
            layer.invalidateSelf();
        }
    };

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BleService.NORTIFY_DATA_SET_CHANGED);

        return intentFilter;
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
        }

        public void onServiceDisconnected(ComponentName classname) {
            ////     mService.disconnect(mDevice);
            mBleService = null;
        }
    };

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



}
