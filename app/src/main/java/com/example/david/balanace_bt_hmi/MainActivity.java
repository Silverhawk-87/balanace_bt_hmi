package com.example.david.balanace_bt_hmi;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Array;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    BluetoothSocket mBluetoothSocket;
    BluetoothDevice mBluetoothDevice;
    BluetoothAdapter mBluetoothAdapter;
    OutputStream mOutputStream;
    InputStream mInputStream;

    // ---------------------------------------------------------------------------------------

    EditText edtext_Kp;
    EditText edtext_Ki;
    EditText edtext_Kd;

    Button bttn_Kp;
    Button bttn_Ki;
    Button bttn_Kd;

    TextView textv_angle;
    TextView textv_pid;

    Switch switch_prepbt;

    // ---------------------------------------------------------------------------------------

    boolean bool_stopWorker = false;

    byte delimiter;

    String str_global_data = "";

    double flt_pid = 0.0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // ---------------------------------------------------------------------------------------

        edtext_Kp = (EditText) findViewById(R.id.edtext_Kp);
        edtext_Ki = (EditText) findViewById(R.id.edtext_Ki);
        edtext_Kd = (EditText) findViewById(R.id.edtext_Kd);

        bttn_Kp = (Button) findViewById(R.id.bttn_Kp);
        bttn_Ki = (Button) findViewById(R.id.bttn_Ki);
        bttn_Kd = (Button) findViewById(R.id.bttn_Kd);

        textv_angle = (TextView) findViewById(R.id.textv_angle);
        textv_pid = (TextView) findViewById(R.id.textv_pid);

        switch_prepbt = (Switch) findViewById(R.id.switch_prepbt);

        // ---------------------------------------------------------------------------------------

        switch_prepbt.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                if(b)
                    find_BT_device();
                else
                    try {
                        close_BT_device();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
            }
        });

        bttn_Kp.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                try{
                    send_to_BT_device("p" + edtext_Kp.getText());
                }catch (IOException e){
                    e.printStackTrace();
                }
            }
        });

        bttn_Ki.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                try{
                    send_to_BT_device("i" + edtext_Ki.getText());
                }catch (IOException e){
                    e.printStackTrace();
                }
            }
        });

        bttn_Kd.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                try{
                    send_to_BT_device("d" + edtext_Kd.getText());
                }catch (IOException e){
                    e.printStackTrace();
                }
            }
        });

    }   //  onCreate activity_main
    
    //  ------------------------------------------------------------------------------------------

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_menu,menu);
        return true;
    }   //  onCreateOptionsMenu

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Intent i_settings = new Intent(this,PreferencesActivity.class);
        startActivity(i_settings);
        return true;
    }   //  onOptionsItemSelected
    
    //  ------------------------------------------------------------------------------------------

    void find_BT_device(){

        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        if (mBluetoothAdapter == null) {

            // Device doesn't support Bluetooth
            toastMessage("Device doesn't support Bluetooth :(");
            finish();
        }

        if (mBluetoothAdapter.isEnabled()){
            // Bluetooth on
        }
        else {
            Intent turnBTon = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(turnBTon,1);
        }

        Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
        if(pairedDevices.size() > 0)
        {
            for(BluetoothDevice device : pairedDevices)
            {
                if(device.getName().equals("HC-05"))
                {
                    mBluetoothDevice = device;
                    toastMessage(device.getName() + " " + device.getAddress() + " found");

                    try {
                        open_BT_device(device);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    break;
                }
            }
        }

    }   //  find_BT_device

    void open_BT_device(BluetoothDevice device) throws IOException {
        UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"); //Standard SerialPortService ID
        mBluetoothSocket = device.createRfcommSocketToServiceRecord(uuid);
        mBluetoothSocket.connect();
        mOutputStream = mBluetoothSocket.getOutputStream();
        mInputStream = mBluetoothSocket.getInputStream();

        listen_to_BT_device();

        toastMessage("Bluetooth Opened");
    }   //  open_BT_device

    void close_BT_device() throws IOException {
        bool_stopWorker = true;
        mOutputStream.close();
        mInputStream.close();
        mBluetoothSocket.close();
        toastMessage("Bluetooth Closed");
    }   //  close_BT_device

    void listen_to_BT_device() throws IOException {
        final byte delimiter_lf = 10;      // LF according to ASCII code, sort of like CR or \n... I think
        final byte delimiter_cr = 13;      // CR according to ASCII code, for raspi
        final Handler mhandler = new Handler();

        bool_stopWorker = false;
        final int[] readBufferPosition = {0};
        final byte [] readBuffer = new byte[1024];

        Thread workerThread = new Thread(new Runnable()
        {
            public void run()
            {
                mBluetoothAdapter.cancelDiscovery();

                while(!Thread.currentThread().isInterrupted() && !bool_stopWorker)
                {
                    try
                    {
                        int bytesAvailable = mInputStream.available();
                        if(bytesAvailable > 0)
                        {
                            byte[] packetBytes = new byte[bytesAvailable];
                            mInputStream.read(packetBytes);
                            for(int i=0;i<bytesAvailable;i++)
                            {
                                byte b = packetBytes[i];

                                check_for_eol_pref();

                                if(b == delimiter)       //delimiter
                                {
                                    byte[] encodedBytes = new byte[readBufferPosition[0]];
                                    System.arraycopy(readBuffer, 0, encodedBytes, 0, encodedBytes.length);
                                    final String data = new String(encodedBytes, "US-ASCII");
                                    readBufferPosition[0] = 0;


                                    mhandler.post(new Runnable()
                                    {
                                        public void run()
                                        {
                                            str_global_data = data;
                                            show_proprietary_data();
                                            // textview here  .setText(data);
                                        }
                                    });
                                }
                                else
                                {
                                    if (b == delimiter_lf || b == delimiter_cr){
                                        //  ¯\_(ツ)_/¯
                                        //toastMessage("wrong delimiter");
                                        readBufferPosition[0] = 0;
                                    }
                                    else
                                        readBuffer[readBufferPosition[0]++] = b;
                                }
                            }
                        }
                    }
                    catch (IOException ex)
                    {
                        bool_stopWorker = true;
                    }
                }
            }

        });
        //mInputStream.reset();
        workerThread.start();
    }   // listen_to_BT_device

    void send_to_BT_device(String message_to_send) throws IOException {
        message_to_send= message_to_send + "\n";
        mOutputStream.write(message_to_send.getBytes());
    }   //  send_to_BT_device

    //  -------------------------------------------------------------------------------------------

    public void check_for_eol_pref(){
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        String str_delimiter = pref.getString("eol_pref","LF");

        if(str_delimiter.equals("LF"))
            delimiter = 10;
        else
            delimiter = 13;
    }   //  check_for_eol_pref

    void show_proprietary_data() {
        char[] chr_myarray = str_global_data.toCharArray();
        String str_extract = "0";

        Log.d("my tag", String.valueOf(chr_myarray[0]));

        if (chr_myarray[0] == 'P' || chr_myarray[0] == 'p') {
            str_extract = str_global_data.substring(1);
            textv_pid.setText("PID = " + str_extract);
        } else if (chr_myarray[0] == 'Z' || chr_myarray[0] == 'z'){
            str_extract = str_global_data.substring(1);
            textv_angle.setText("Angle = " + str_extract);
        }

        Log.d("my tag", str_extract);
    }   //  show_proprietary_data

    private void toastMessage(String message){
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }   //  toastMessage

}   //  Main Activity