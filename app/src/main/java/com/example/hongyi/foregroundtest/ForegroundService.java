package com.example.hongyi.foregroundtest;

/**
 * Created by Hongyi on 11/9/2015.
 */
import android.annotation.TargetApi;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.IBinder;
import android.os.ParcelUuid;
import android.support.v7.app.NotificationCompat;
import android.util.Log;

import com.mbientlab.metawear.AsyncOperation;
import com.mbientlab.metawear.Message;
import com.mbientlab.metawear.MetaWearBleService;
import com.mbientlab.metawear.MetaWearBoard;
import com.mbientlab.metawear.RouteManager;
import com.mbientlab.metawear.UnsupportedModuleException;
import com.mbientlab.metawear.data.CartesianFloat;
import com.mbientlab.metawear.module.Accelerometer;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.UUID;

public class ForegroundService extends Service implements ServiceConnection{
    private static final String LOG_TAG = "ForegroundService";
    private final ArrayList<String> SENSOR_MAC = new ArrayList<>();
    private final ArrayList<BoardObject> boards = new ArrayList<>();
    private MetaWearBoard mwBoard;
    private BluetoothAdapter btAdapter;
    private boolean isScanning= false;
    private HashSet<UUID> filterServiceUuids;
    private ArrayList<ScanFilter> api21ScanFilters;
    private final static UUID[] serviceUuids;
    private float frequency = 0;
    private BufferedWriter bw;
    private MyReceiver broadcastReceiver;
    private MetaWearBleService.LocalBinder serviceBinder;

    public static boolean IS_SERVICE_RUNNING = false;

    static {
        serviceUuids= new UUID[] {
                MetaWearBoard.METAWEAR_SERVICE_UUID,
                MetaWearBoard.METABOOT_SERVICE_UUID
        };
    }

    private class MyReceiver extends BroadcastReceiver{
        MyReceiver() {
            super();
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.hasExtra("subject")) {
                setSubjectLabel(intent.getStringExtra("subject"));
            } else if (intent.hasExtra("label")) {
                setLabel(intent.getStringExtra("label"));
            }
        }
    }

    public void setSubjectLabel(String subject) {
        for (int i = 0; i < SENSOR_MAC.size();i++){
            boards.get(i).setSubject(subject);
        }
    }

    public void setLabel(String label){
        for (int i = 0; i < SENSOR_MAC.size();i++){
            boards.get(i).setLabel(label);
        }
    }

    private void writeLog(long TS, String sbj, String label, String devicename, float sampleFreq, int x, int y, int z) {

        try {
            String s = String.valueOf(TS) + "," + sbj + "," + label + "," + devicename + "," +
                    String.valueOf(sampleFreq) + "," + String.valueOf(x) + "," +
                    String.valueOf(y) + "," + String.valueOf(z);
            bw.write(s);
            bw.newLine();
            bw.flush();
            Log.i("data",s);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        broadcastReceiver = new MyReceiver();
        IntentFilter intentfilter = new IntentFilter();
        intentfilter.addAction(Constants.NOTIFICATION_ID.LABEL_TAG);
        registerReceiver(broadcastReceiver, intentfilter);
        getApplicationContext().bindService(new Intent(this, MetaWearBleService.class), this, Context.BIND_AUTO_CREATE);
        Log.i(LOG_TAG, "successfully on create");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent.getAction().equals(Constants.ACTION.STARTFOREGROUND_ACTION)) {
            Log.i(LOG_TAG, "Received Start Foreground Intent ");
            showNotification();
            frequency = intent.getFloatExtra("frequency", 0f);
            ArrayList<String> lstMAC = intent.getStringArrayListExtra("lst_MAC");
            for (String MAC: lstMAC) {
                SENSOR_MAC.add(MAC);
            }
            if (intent.hasExtra("file")) {
                String log_file_name = intent.getStringExtra("file");
                File logfile = new File(log_file_name);
                try {
                    this.bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(logfile, true)));
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                }
            }
        } else if (intent.getAction().equals(
                Constants.ACTION.STOPFOREGROUND_ACTION)) {
            Log.i(LOG_TAG, "Received Stop Foreground Intent");
            stopForeground(true);
            stopSelf();
        }
        return START_STICKY;
    }

    private void showNotification() {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        notificationIntent.setAction(Constants.ACTION.MAIN_ACTION);
        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0,
                notificationIntent, 0);

        Bitmap icon = BitmapFactory.decodeResource(getResources(),
                R.mipmap.ic_launcher);

        Notification notification = new NotificationCompat.Builder(this)
                .setContentTitle("GaitTest")
                .setTicker("GaitTest")
                .setContentText("Gait Test Application is Running")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setLargeIcon(Bitmap.createScaledBitmap(icon, 128, 128, false))
                .setContentIntent(pendingIntent)
                .setOngoing(true).build();
        Log.i(LOG_TAG, "successfully build a notification");
        startForeground(Constants.NOTIFICATION_ID.FOREGROUND_SERVICE,
                notification);
        Log.i(LOG_TAG, "started foreground");
    }

    @Override
    public void onDestroy() {
        unregisterReceiver(broadcastReceiver);
        try {
            if (bw != null) {
                bw.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        for (int i = 0; i < SENSOR_MAC.size(); i++) {
            boards.get(i).accel_module.stop();
            boards.get(i).accel_module.disableAxisSampling();
            boards.get(i).ActiveDisconnect = true;
            boards.get(i).board.disconnect();
        }
        super.onDestroy();
        getApplicationContext().unbindService(this);
        Log.i(LOG_TAG, "In onDestroy");
    }

    @Override
    public IBinder onBind(Intent intent) {
        // Used only in case if services are bound (Bound Services).
        return null;
    }

    private BluetoothAdapter.LeScanCallback deprecatedScanCallback= null;
    private ScanCallback api21ScallCallback= null;

    @TargetApi(22)
    public void startBleScan() {
        isScanning= true;

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            filterServiceUuids = new HashSet<>();
        } else {
            api21ScanFilters= new ArrayList<>();
        }

        if (serviceUuids != null) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                filterServiceUuids.addAll(Arrays.asList(serviceUuids));
            } else {
                for (UUID uuid : serviceUuids) {
                    api21ScanFilters.add(new ScanFilter.Builder().setServiceUuid(new ParcelUuid(uuid)).build());
                }
            }
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            ///< TODO: Use startScan method instead from API 21
            deprecatedScanCallback= new BluetoothAdapter.LeScanCallback() {
                private void foundDevice(final BluetoothDevice btDevice, final int rssi) {

                }
                @Override
                public void onLeScan(BluetoothDevice bluetoothDevice, int rssi, byte[] scanRecord) {
                    ///< Service UUID parsing code taking from stack overflow= http://stackoverflow.com/a/24539704

                    ByteBuffer buffer= ByteBuffer.wrap(scanRecord).order(ByteOrder.LITTLE_ENDIAN);
                    boolean stop= false;
                    while (!stop && buffer.remaining() > 2) {
                        byte length = buffer.get();
                        if (length == 0) break;

                        byte type = buffer.get();
                        switch (type) {
                            case 0x02: // Partial list of 16-bit UUIDs
                            case 0x03: // Complete list of 16-bit UUIDs
                                while (length >= 2) {
                                    UUID serviceUUID= UUID.fromString(String.format("%08x-0000-1000-8000-00805f9b34fb", buffer.getShort()));
                                    stop= filterServiceUuids.isEmpty() || filterServiceUuids.contains(serviceUUID);
                                    if (stop) {
                                        foundDevice(bluetoothDevice, rssi);
                                    }

                                    length -= 2;
                                }
                                break;

                            case 0x06: // Partial list of 128-bit UUIDs
                            case 0x07: // Complete list of 128-bit UUIDs
                                while (!stop && length >= 16) {
                                    long lsb= buffer.getLong(), msb= buffer.getLong();
                                    stop= filterServiceUuids.isEmpty() || filterServiceUuids.contains(new UUID(msb, lsb));
                                    if (stop) {
                                        foundDevice(bluetoothDevice, rssi);
                                    }
                                    length -= 16;
                                }
                                break;

                            default:
                                buffer.position(buffer.position() + length - 1);
                                break;
                        }
                    }

                    if (!stop && filterServiceUuids.isEmpty()) {
                        foundDevice(bluetoothDevice, rssi);
                    }
                }
            };
            btAdapter.startLeScan(deprecatedScanCallback);
        } else {
            api21ScallCallback= new ScanCallback() {
                @Override
                public void onScanResult(int callbackType, final ScanResult result) {


                    super.onScanResult(callbackType, result);
                }
            };
            btAdapter.getBluetoothLeScanner().startScan(api21ScanFilters, new ScanSettings.Builder().build(), api21ScallCallback);
        }
    }

    public void stopBleScan() {
        if (isScanning) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                ///< TODO: Use stopScan method instead from API 21
                btAdapter.stopLeScan(deprecatedScanCallback);
            } else {
                btAdapter.getBluetoothLeScanner().stopScan(api21ScallCallback);
            }
            isScanning= false;
        }
    }


    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        serviceBinder = (MetaWearBleService.LocalBinder) service;

        btAdapter = ((BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE)).getAdapter();

        BluetoothAdapter btAdapter = ((BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE)).getAdapter();
        if (!btAdapter.isEnabled()) {
            btAdapter.enable();
        }

        while (!btAdapter.isEnabled()) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        startBleScan();

        try {
            Thread.sleep(10000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        stopBleScan();


        for (int i = 0; i < SENSOR_MAC.size(); i++){
            BluetoothDevice btDevice = btAdapter.getRemoteDevice(SENSOR_MAC.get(i));
            boards.add(new BoardObject(serviceBinder.getMetaWearBoard(btDevice), SENSOR_MAC.get(i), frequency));
            Log.i(LOG_TAG, "Board added");
        }

        for (int i = 0; i < SENSOR_MAC.size();i++){
            BoardObject b = boards.get(i);
            b.sensor_status = b.CONNECTING;
            b.broadcastStatus();
            b.board.connect();
        }
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {

    }

    public class BoardObject {
        private final String CONNECTED = "Connected. Streaming Data",
                DISCONNECTED = "Lost connection. Reconnecting",
                FAILURE = "Connection error. Reconnecting",
                CONNECTING = "Connecting",
                LOG_TAG = "Board_Log";
        public MetaWearBoard board;
        public Accelerometer accel_module;
        public String MAC_ADDRESS;
        private float sampleFreq;
        private float sampleInterval;
        public boolean ActiveDisconnect = false;
        private final String devicename;
        public String sensor_status;
        private String subject = "null";
        private String label = "0";

        public void setSubject(String subject) {
            this.subject = subject;
        }

        public void setLabel(String label) {
            this.label = label;
        }

        public void broadcastStatus() {
            Intent intent = new Intent(Constants.NOTIFICATION_ID.BROADCAST_TAG);
            intent.putExtra("name", this.MAC_ADDRESS);
            intent.putExtra("status", this.sensor_status);
            sendBroadcast(intent);
        }

        public BoardObject(MetaWearBoard mxBoard, final String MAC, float freq) {
            this.board = mxBoard;
            this.MAC_ADDRESS = MAC;
            this.sampleFreq = freq;
            this.sampleInterval = 1000 / sampleFreq;
            this.devicename = MAC_ADDRESS.replace(":", "");
            this.sensor_status = CONNECTING;
            final String SENSOR_DATA_LOG = "Data:Sensor:" + MAC_ADDRESS;

            this.board.setConnectionStateHandler(new MetaWearBoard.ConnectionStateHandler() {
                @Override
                public void connected() {
                    sensor_status = CONNECTED;
                    broadcastStatus();
                    try {
                        accel_module = board.getModule(Accelerometer.class);
                        accel_module.setOutputDataRate(sampleFreq);
                        accel_module.routeData().fromAxes().stream(SENSOR_DATA_LOG).commit()
                                .onComplete(new AsyncOperation.CompletionHandler<RouteManager>() {
                                    @Override
                                    public void success(RouteManager result) {
                                        result.subscribe(SENSOR_DATA_LOG, new RouteManager.MessageHandler() {
                                            @Override
                                            public void process(Message message) {
                                                long TS = System.currentTimeMillis();
                                                CartesianFloat result = message.getData(CartesianFloat.class);
                                                float x = result.x();
                                                int x_int = (int) (x * 1000);
                                                float y = result.y();
                                                int y_int = (int) (y * 1000);
                                                float z = result.z();
                                                int z_int = (int) (z * 1000);
                                                writeLog(TS, subject, label, devicename, sampleFreq, x_int, y_int, z_int);
                                            }
                                        });
                                    }
                                });
                    } catch (UnsupportedModuleException e) {
                        Log.i(LOG_TAG, "Cannot find sensor:" + MAC_ADDRESS, e);
                    }
                    accel_module.enableAxisSampling();
                    accel_module.start();
                }

                @Override
                public void disconnected() {
                    if (!ActiveDisconnect) {
                        sensor_status = DISCONNECTED;
                        broadcastStatus();
                        board.connect();
                    }
                }

                @Override
                public void failure(int status, Throwable error) {
                    error.printStackTrace();
                    sensor_status = FAILURE;
                    broadcastStatus();
                    board.connect();
                }
            });
        }
    }
}
