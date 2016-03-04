package com.example.hongyi.foregroundtest;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;

public class MainActivity extends AppCompatActivity{
    private MyReceiver broadcastreceiver;
    SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, HH:mm:ss");

    public class MyReceiver extends BroadcastReceiver {
        private String lb_MAC = "MAC";
        private String lb_status = "Status";
        public MyReceiver() {
            super();
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            String MAC = intent.getStringExtra("name");
            String status = intent.getStringExtra("status");
            String index = "";
            switch (MAC) {
                case Constants.SENSORS.SENSOR1:
                    index = "_1";
                    break;
                case Constants.SENSORS.SENSOR2:
                    index = "_2";
                    break;
                case Constants.SENSORS.SENSOR3:
                    index = "_3";
                    break;
                case Constants.SENSORS.SENSOR4:
                    index = "_4";
                    break;
                case Constants.SENSORS.SENSOR5:
                    index = "_5";
                    break;
            }
            if (!index.equals("")) {
                TextView TV_MAC = (TextView) findViewById(getResources().getIdentifier(lb_MAC+index,"id","com.example.hongyi.foregroundtest"));
                TV_MAC.setText(MAC);
                TextView TV_Status = (TextView) findViewById(getResources().getIdentifier(lb_status+index,"id","com.example.hongyi.foregroundtest"));
                TV_Status.setText(status);
            }
        }

        @Override
        public IBinder peekService(Context myContext, Intent service) {
            return super.peekService(myContext, service);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Assign handlers for the two buttons
        Button btn_connect = (Button) findViewById(R.id.btn_connect);
        btn_connect.setOnClickListener(btn_connect_lstnr);
        Button btn_disconnect = (Button) findViewById(R.id.btn_disconnect);
        btn_disconnect.setOnClickListener(btn_disconnect_lstnr);

        // Assign handlers for the two switches
        Switch swc_subject = (Switch) findViewById(R.id.swc_subject);
        swc_subject.setOnClickListener(swc_subject_lstnr);
        Switch swc_label = (Switch) findViewById(R.id.swc_label);
        swc_label.setOnClickListener(swc_label_lstnr);

        // Set up display handler
        broadcastreceiver = new MyReceiver();
        IntentFilter intentfilter = new IntentFilter();
        intentfilter.addAction(Constants.NOTIFICATION_ID.BROADCAST_TAG);
        registerReceiver(broadcastreceiver, intentfilter);
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(broadcastreceiver);
        super.onDestroy();
    }

    private ArrayList<String> getSelectedMAC() {
        ArrayList<String> lst_MAC = new ArrayList<>();
        Switch swc_1 = (Switch) findViewById(R.id.swc_1);
        Switch swc_2 = (Switch) findViewById(R.id.swc_2);
        Switch swc_3 = (Switch) findViewById(R.id.swc_3);
        Switch swc_4 = (Switch) findViewById(R.id.swc_4);
        Switch swc_5 = (Switch) findViewById(R.id.swc_5);
        if (swc_1.isChecked()) {
            lst_MAC.add(Constants.SENSORS.SENSOR1);
        }
        if (swc_2.isChecked()) {
            lst_MAC.add(Constants.SENSORS.SENSOR2);
        }
        if (swc_3.isChecked()) {
            lst_MAC.add(Constants.SENSORS.SENSOR3);
        }
        if (swc_4.isChecked()) {
            lst_MAC.add(Constants.SENSORS.SENSOR4);
        }
        if (swc_5.isChecked()) {
            lst_MAC.add(Constants.SENSORS.SENSOR5);
        }
        return lst_MAC;
    }

    View.OnClickListener btn_connect_lstnr = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            // Get Sampling Frequency
            RadioGroup rg = (RadioGroup) findViewById(R.id.radioGroup);
            int rb_id = rg.getCheckedRadioButtonId();
            RadioButton rb = (RadioButton) rg.getChildAt(rg.indexOfChild(rg.findViewById(rb_id)));
            float frequency = Float.valueOf((String) rb.getText());
            Log.i("Frequency", ""+frequency);

            // Get Selected Sensors
            ArrayList<String> selectedMAC = getSelectedMAC();
            for (String MAC: selectedMAC) {
                Log.i("MAC", MAC);
            }

            // Make sure at least one sensor is selected
            if (!selectedMAC.isEmpty()){
                Intent service = new Intent(MainActivity.this, ForegroundService.class);
                service.putExtra("lst_MAC", selectedMAC);
                service.putExtra("frequency", frequency);
                if (!ForegroundService.IS_SERVICE_RUNNING) {
                    service.setAction(Constants.ACTION.STARTFOREGROUND_ACTION);
                    ForegroundService.IS_SERVICE_RUNNING = true;
                    startService(service);
                }
            }
        }
    };

    View.OnClickListener btn_disconnect_lstnr = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            Intent service = new Intent(MainActivity.this, ForegroundService.class);
            service.setAction(Constants.ACTION.STOPFOREGROUND_ACTION);
            ForegroundService.IS_SERVICE_RUNNING = false;
            startService(service);
        }
    };

    View.OnClickListener swc_subject_lstnr = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            Switch swc_subject = (Switch) findViewById(R.id.swc_subject);
            String subject_label;
            if (swc_subject.isChecked()){
                EditText subject = (EditText) findViewById(R.id.lbl_subject);
                subject_label = subject.getText().toString();
            } else {
                subject_label = "null";
            }
            Log.i("Subject", subject_label);
            Intent change_subject = new Intent(Constants.NOTIFICATION_ID.LABEL_TAG);
            change_subject.putExtra("subject", subject_label);
            sendBroadcast(change_subject);
        }
    };

    View.OnClickListener swc_label_lstnr = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            Switch swc_label = (Switch) findViewById(R.id.swc_label);
            String label;
            if (swc_label.isChecked()) {
                Spinner spinner = (Spinner) findViewById(R.id.spinner_label);
                label = spinner.getSelectedItem().toString();
            } else {
                label = "0";
            }
            Log.i("Label", label);
            Intent change_subject = new Intent(Constants.NOTIFICATION_ID.LABEL_TAG);
            change_subject.putExtra("label", label);
            sendBroadcast(change_subject);
        }
    };

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        return id == R.id.action_settings || super.onOptionsItemSelected(item);
    }

}
