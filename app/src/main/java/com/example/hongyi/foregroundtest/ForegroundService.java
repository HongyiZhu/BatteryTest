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
import com.mbientlab.metawear.module.Bmi160Accelerometer;
import com.mbientlab.metawear.module.MultiChannelTemperature;

import org.json.JSONException;
import org.json.JSONObject;

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
import java.util.List;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

public class ForegroundService extends Service implements ServiceConnection{
    private int connected_count;
    private static final String LOG_TAG = "ForegroundService";
    private final ArrayList<String> SENSOR_MAC = new ArrayList<>();
    private final ArrayList<BodyBoard> boards = new ArrayList<>();
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
    private Set<String> nearByDevices;

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
        String s = String.valueOf(TS) + "," + sbj + "," + label + "," + devicename + "," +
                String.valueOf(sampleFreq) + "," + String.valueOf(x) + "," +
                String.valueOf(y) + "," + String.valueOf(z) + "\n";
        try {
            bw.write(s);
            bw.flush();
//            Log.i("data",s);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public String getJSON (String name, String ts, String temperature) {
        JSONObject jsonstring = new JSONObject();
        try {
            jsonstring.put("s", name);
            jsonstring.put("t", Double.valueOf(ts));
            jsonstring.put("c", Float.valueOf(temperature));
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return jsonstring.toString();
    }

    public String getJSON (String name, String ts, int battery) {
        JSONObject jsonstring = new JSONObject();
        try {
            jsonstring.put("s", name);
            jsonstring.put("t", Double.valueOf(ts));
            jsonstring.put("b", Float.valueOf(battery));
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return jsonstring.toString();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        connected_count = 0;
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
        } else if (intent.getAction().equals(Constants.ACTION.STOPFOREGROUND_ACTION)) {
            Log.i(LOG_TAG, "Received Stop Foreground Intent");
            stopForeground(true);
            stopSelf();
        }
        return START_STICKY;
    }

    private BluetoothAdapter.LeScanCallback deprecatedScanCallback= null;
    private ScanCallback api21ScallCallback= null;

    @TargetApi(22)
    public Set<String> scanBle(long interval) {
        nearByDevices = new HashSet<>();
        long scanTS = System.currentTimeMillis();

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
            deprecatedScanCallback= new BluetoothAdapter.LeScanCallback() {
                private void foundDevice(final BluetoothDevice btDevice, final int rssi) {
                    nearByDevices.add(btDevice.getAddress());
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

        while ((System.currentTimeMillis()-scanTS) <= interval) {

        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            btAdapter.stopLeScan(deprecatedScanCallback);
        } else {
            btAdapter.getBluetoothLeScanner().stopScan(api21ScallCallback);
        }

        return nearByDevices;
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

        Set<String> nearMW = scanBle(5000);
        Log.i("BLE", String.valueOf(nearMW.size()));


        for (int i = 0; i < SENSOR_MAC.size(); i++){
            BluetoothDevice btDevice = btAdapter.getRemoteDevice(SENSOR_MAC.get(i));
            boards.add(new BodyBoard(serviceBinder.getMetaWearBoard(btDevice), SENSOR_MAC.get(i), frequency));
            Log.i(LOG_TAG, "Board added");
        }

        for (int i = 0; i < SENSOR_MAC.size();i++){
            BodyBoard b = boards.get(i);
            b.sensor_status = b.CONNECTING;
            b.broadcastStatus();
            b.board.connect();
        }
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {

    }

    public class Board {
        public MetaWearBoard board;
        public Bmi160Accelerometer accel_module;
        public String sensor_status;
        public boolean ActiveDisconnect = false;
        public String MAC_ADDRESS;
        public String temperature = "-99999";
        public String battery;
        public boolean needs_to_reboot;
        public final String CONNECTED = "Connected.\nStreaming Data",
                AWAY = "Sensor out of range",
                DISCONNECTED_BODY = "Lost connection.\nReconnecting",
                DISCONNECTED_OBJ = "Download finished.",
                FAILURE = "Connection error.\nReconnecting",
                CONFIGURED = "Board configured.",
                CONNECTING = "Connecting",
                INITIATED = "Board reset",
                LOG_TAG = "Board_Log",
                DOWNLOAD_COMPLETED = "Data download completed";
        public Board() {}

        public ArrayList<String> filtering(ArrayList<String> previousCache, ArrayList<String> dataCache, int thres, int interval) {
            ArrayList<String> filteredCache = new ArrayList<> ();
            if (dataCache.size() == 0) {
                return filteredCache;
            }
            float last_ts;
            int prev_x, prev_y, prev_z;
            if (previousCache.size() == 0) {
                String[] f0 = dataCache.get(0).split(",");
                last_ts = 0;
                prev_x = Integer.valueOf(f0[1]);
                prev_y = Integer.valueOf(f0[2]);
                prev_z = Integer.valueOf(f0[3]);
            } else {
                String[] f0 = previousCache.get(previousCache.size() - 1).split(",");
                last_ts = 0;
                prev_x = Integer.valueOf(f0[1]);
                prev_y = Integer.valueOf(f0[2]);
                prev_z = Integer.valueOf(f0[3]);
//                Log.i("filter","previousCache with last_ts " + last_ts + ", x " + prev_x + ", y " + prev_y + ", z " + prev_z);
            }
            for (int i = 1; i < dataCache.size(); i++) {
                String s = dataCache.get(i);
                String[] fields = s.split(",");
                float ts = Float.valueOf(fields[0]);
                int x = Integer.valueOf(fields[1]);
                int y = Integer.valueOf(fields[2]);
                int z = Integer.valueOf(fields[3]);
                if (Math.abs(ts - last_ts) <= interval || Math.abs(x - prev_x) >= thres || Math.abs(y - prev_y) >= thres || Math.abs(z - prev_z) >= thres) {
                    filteredCache.add(s);
                    if (Math.abs(x - prev_x) >= thres || Math.abs(y - prev_y) >= thres || Math.abs(z - prev_z) >= thres) {
//                        Log.i("filter","Last timestamp updated from " + last_ts + " to " + ts);
                        last_ts = ts;
                    }
                    prev_x = x;
                    prev_y = y;
                    prev_z = z;
                }
            }
            return filteredCache;
        }

        public ArrayList<String> filtering(ArrayList<String> dataCache, int thres, int interval) {
            return filtering(new ArrayList<String>(), dataCache, thres, interval);
        }

        public void broadcastStatus() {
            Intent intent = new Intent(Constants.NOTIFICATION_ID.BROADCAST_TAG);
            intent.putExtra("name", this.MAC_ADDRESS);
            intent.putExtra("status", this.sensor_status);
            intent.putExtra("temperature", this.temperature);
            intent.putExtra("timestamp", System.currentTimeMillis());
            Log.i("Intent", intent.getStringExtra("name"));
            sendBroadcast(intent);
        }
    }

    public class BodyBoard extends Board{
        public long[] startTimestamp;
        public ArrayList<String> dataCache;
        private ArrayList<String> workCache;
        public ArrayList<String> previousCache;
        public int dataCount;
        private float sampleFreq;
        private int uploadCount;
        private int sampleInterval;
        private final String devicename;
        private long temperature_timestamp;
        private long battery_timestamp;
        private java.util.Timer searchTM;
        private boolean away;
        private int reconnect_count;
        private String subject;
        private String label;
        private Bmi160Accelerometer.OutputDataRate rate;

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

        public BodyBoard(MetaWearBoard mxBoard, final String MAC, float freq) {
            this.board = mxBoard;
            this.MAC_ADDRESS = MAC;
            this.dataCount = 0;
            this.dataCache = new ArrayList<>();
            this.previousCache = new ArrayList<>();
            this.startTimestamp = new long[1];
            this.sampleFreq = freq;
            this.uploadCount = (int) (8 * sampleFreq);
            this.sampleInterval = (int) (1000 / sampleFreq);
            this.devicename = MAC_ADDRESS.replace(":", "");
            this.sensor_status = CONNECTING;
            this.temperature_timestamp = 0;
            this.battery_timestamp = 0;
            this.away = false;
            final String SENSOR_DATA_LOG = "Data:Sensor:" + MAC_ADDRESS;
            if (freq == 12.5f) {
                rate = Bmi160Accelerometer.OutputDataRate.ODR_12_5_HZ;
            } else if (freq == 25f) {
                rate = Bmi160Accelerometer.OutputDataRate.ODR_25_HZ;
            } else if (freq == 6.25f) {
                rate = Bmi160Accelerometer.OutputDataRate.ODR_6_25_HZ;
            } else if (freq == 3.125f) {
                rate = Bmi160Accelerometer.OutputDataRate.ODR_3_125_HZ;
            }

            this.board.setConnectionStateHandler(new MetaWearBoard.ConnectionStateHandler() {
                @Override
                public void connected() {
                    reconnect_count = 0;
                    if (searchTM != null) {
                        searchTM.cancel();
                        searchTM.purge();
                        searchTM = null;
                    }
                    away = false;
//                    writeSensorLog("Sensor connected", _info, devicename);
                    sensor_status = CONNECTED;
                    broadcastStatus();
                    final MultiChannelTemperature mcTempModule;
                    try {
                        //TODO: Work on adjusting sample rate
                        accel_module = board.getModule(Bmi160Accelerometer.class);
                        accel_module.setOutputDataRate(sampleFreq);
                        accel_module.routeData().fromAxes().stream(SENSOR_DATA_LOG).commit()
                                .onComplete(new AsyncOperation.CompletionHandler<RouteManager>() {
                                    @Override
                                    public void success(RouteManager result) {
                                        result.subscribe(SENSOR_DATA_LOG, new RouteManager.MessageHandler() {
                                            @Override
                                            public void process(Message message) {
                                                if (dataCache.size() == uploadCount) {
                                                    startTimestamp[0] = System.currentTimeMillis();
                                                    workCache = dataCache;
                                                    dataCache = new ArrayList<>();
                                                    dataCount = 0;
                                                    long timestamp = startTimestamp[0] + (long) (dataCount * sampleInterval);
                                                    double timestamp_in_seconds = timestamp / 1000.0;
                                                    CartesianFloat result = message.getData(CartesianFloat.class);
                                                    float x = result.x();
                                                    int x_int = (int) (x * 1000);
                                                    float y = result.y();
                                                    int y_int = (int) (y * 1000);
                                                    float z = result.z();
                                                    int z_int = (int) (z * 1000);
                                                    dataCache.add(String.format("%.3f", timestamp_in_seconds) + "," + String.valueOf(x_int) +
                                                            "," + String.valueOf(y_int) + "," + String.valueOf(z_int));

                                                    ArrayList<String> filteredCache = filtering(previousCache, workCache, 32, 3);
                                                    previousCache = new ArrayList<>(workCache);
                                                    workCache.clear();
                                                } else {
                                                    dataCount += 1;
                                                    long timestamp = startTimestamp[0] + (long) (dataCount * sampleInterval);
                                                    double timestamp_in_seconds = timestamp / 1000.0;
                                                    CartesianFloat result = message.getData(CartesianFloat.class);
                                                    float x = result.x();
                                                    int x_int = (int) (x * 1000);
                                                    float y = result.y();
                                                    int y_int = (int) (y * 1000);
                                                    float z = result.z();
                                                    int z_int = (int) (z * 1000);
                                                    dataCache.add(String.format("%.3f", timestamp_in_seconds) + "," + String.valueOf(x_int) +
                                                            "," + String.valueOf(y_int) + "," + String.valueOf(z_int));
                                                }
                                            }
                                        });
                                    }
                                });
                        mcTempModule = board.getModule(MultiChannelTemperature.class);
                        final List<MultiChannelTemperature.Source> tempSources= mcTempModule.getSources();
//                        final MultiChannelTemperature finalMcTempModule = mcTempModule;
                        mcTempModule.routeData()
                                .fromSource(tempSources.get(MultiChannelTemperature.MetaWearProChannel.ON_BOARD_THERMISTOR)).stream("thermistor_stream")
                                .commit().onComplete(new AsyncOperation.CompletionHandler<RouteManager>() {
                            @Override
                            public void success(RouteManager result) {
                                result.subscribe("thermistor_stream", new RouteManager.MessageHandler() {
                                    @Override
                                    public void process(Message message) {
                                        long timestamp = System.currentTimeMillis();
                                        if (timestamp - temperature_timestamp >= 220000) {
                                            temperature_timestamp = timestamp;
                                            double ts_in_sec = timestamp / 1000.0;
                                            String jsonstr = getJSON(devicename, String.format("%.3f", ts_in_sec), String.format("%.3f", message.getData(Float.class)));
                                            //send to server
//                                            postTempAsync task = new postTempAsync();
//                                            task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, jsonstr);
//                                                    task.execute(jsonstr);
                                            temperature = String.format("%.3f", message.getData(Float.class));
                                            Log.i(devicename, jsonstr);
                                            // get body sensor battery info
                                            broadcastStatus();
                                        }
                                    }
                                });

                                java.util.Timer timer = new java.util.Timer();
                                timer.schedule(new TimerTask() {
                                    @Override
                                    public void run() {
                                        mcTempModule.readTemperature(tempSources.get(MultiChannelTemperature.MetaWearProChannel.ON_BOARD_THERMISTOR));
                                    }
                                }, 0, 900000);

                                timer.schedule(new TimerTask() {
                                    @Override
                                    public void run() {
                                        board.readBatteryLevel().onComplete(new AsyncOperation.CompletionHandler<Byte>() {
                                            @Override
                                            public void success(Byte result) {
                                                // Send Battery Info
                                                long timestamp = System.currentTimeMillis();
                                                if (timestamp - battery_timestamp >= 220000) {
                                                    battery_timestamp = timestamp;
                                                    battery = result.toString();
                                                    Log.i("battery_Body", result.toString());
                                                    String jsonstr = getJSON(devicename, String.format("%.3f", System.currentTimeMillis() / 1000.0), Integer.valueOf(result.toString()));
//                                                    postBatteryAsync task = new postBatteryAsync();
//                                                    task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, jsonstr);
                                                }
                                            }
                                        });
                                    }
                                }, 120000, 1800000);
                            }
                        });
                    } catch (UnsupportedModuleException e) {
                        Log.i(LOG_TAG, "Cannot find sensor:" + MAC_ADDRESS, e);
                    }
                    accel_module.enableAxisSampling();
                    startTimestamp[0] = System.currentTimeMillis();
                    dataCount = 0;
                    accel_module.startLowPower();
                }

                @Override
                public void disconnected() {
                    if (!ActiveDisconnect) {
                        sensor_status = DISCONNECTED_BODY;
                        broadcastStatus();
//                        writeSensorLog("Connection dropped, try to reconnect", _info, devicename);
                        board.connect();
                    }
                }

                @Override
                public void failure(int status, Throwable error) {
                    reconnect_count += 1;
                    if (!away && reconnect_count <= 5) {
                        if (searchTM != null) {
                            searchTM.cancel();
                            searchTM.purge();
                            searchTM = null;
                        }
                        error.printStackTrace();
//                        writeSensorLog(error.getMessage(), _error, devicename);
                        sensor_status = FAILURE;
                        broadcastStatus();
//                        if (dataCache.size() != 0) {
//                            ArrayList<String> temp = new ArrayList<>(dataCache);
//                            dataCache.clear();
//                            startTimestamp[0] = System.currentTimeMillis();
//                            dataCount = 0;
//                            ArrayList<String> filteredCache = filtering(previousCache, temp, 32, 3);
//                            previousCache = new ArrayList<String>(temp);
//                            if (filteredCache.size() != 0) {
//                                ArrayList<String> data_array = getJSONList(devicename, filteredCache);
//                                for (String s : data_array) {
//                                    resendDataQueue.offer(s);
//                                }
//                            }
//                        }
//                        writeSensorLog("Try to connect", _info, devicename);
                        board.connect();
                    } else {
                        searchTM = new Timer();
                        reconnect_count = 0;
                        searchTM.schedule(new TimerTask() {
                            @Override
                            public void run() {
                                Set<String> nearMW = scanBle(8000);
                                if (nearMW.contains(SENSOR_MAC.get(0))) {
                                    away = false;
                                    board.connect();
                                } else {
                                    away = true;
                                    sensor_status = AWAY;
                                    broadcastStatus();
                                }
                            }
                        }, 0, 120000);
                    }
                }
            });
        }
    }
}
