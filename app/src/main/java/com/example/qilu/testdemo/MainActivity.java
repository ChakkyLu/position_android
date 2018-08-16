package com.example.qilu.testdemo;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.LocationManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.renderscript.ScriptGroup;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.support.design.widget.BottomNavigationView;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.text.Layout;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import static android.view.View.OnClickListener;

public class MainActivity extends AppCompatActivity {

    private WifiManager wifiManager;
    List<ScanResult> wifi_list;
    private Button start;
    private Button connect;
    private Button send;
    private Button stop;
    private Button generate;
    private Button test;
    private TextView message;
    private Socket mSocketClient;
    private LinearLayout contentView;
    private View.OnClickListener btnOnclick;
    private ArrayList<WifiInfo> stable_wifi=new ArrayList<WifiInfo>();

    private TextView IPText;
    private boolean isConnecting = false, isSend=false;
    private Thread mThreadClient = null;
    Handler myhandler;
    static InputStream inputStream	= null;
    static PrintWriter mPrintWriterServer = null;
    static BufferedReader mBufferedReaderClient	= null;
    static PrintWriter mPrintWriterClient = null;
    static DataInputStream inputstreamreader = null;
    private String recvMessageClient = "";
    private View[] ButtonGroups;
    private String MsgToServer = "";

    private EditText m_height;
    private EditText m_width;
    private MyListener listener = new MyListener();
    private Boolean constant_stop=false;
    private int stable_times = 20;
    private String Send_Mode = "Constant";


    private int pb=0;
    private int stable_count=0;

    Timer timer;
    int time;

    Timer innertimer = null;
    TimerTask innertask = null;

    private LinearLayout parentLL;


    /*
        class WifiInfo: for collect average and stable wifi strength
        BSSID: MAC address
        SSID: WIFI name
        level: strength
        max: maximum strength
        min: minimum strength
        alltime: the number of loops to calculate average strength
    */
    public class WifiInfo{
        public String BSSID;
        public String SSID;
        public int level;
        public int max=0;
        public int min=-100;
        public int alltime=0;
    }

    /*
    class MyListener: when position button is clicked, record its position and do some rendering
    current_button: the current button clicked
    constant_stop: first press as false, second press as true

     */

    public class MyListener implements View.OnClickListener {
        @Override
        public void onClick(View v) {
            if(constant_stop){
                v.setBackground(getResources().getDrawable(R.drawable.rectangle));
                if(timer!=null) timer.cancel();
                if(innertimer!=null) innertimer.cancel();
                constant_stop = false;
                message.setText("");
            } else{
                int tag = (Integer) v.getTag();
                v.setBackground(getResources().getDrawable(R.drawable.rectangle_c));
                message.setText("");
                message.setText("Now collect area"+tag+"'s WIFI message");
                Toast.makeText(MainActivity.this, "you have chosen area"+tag, Toast.LENGTH_SHORT).show();
                pb = tag;
                constant_stop = true;
            }
        }
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        requestLocationPermission();

        // >API23, GPS must be open if want wifi message
        LocationManager locManager = (LocationManager)getApplicationContext().getSystemService(Context.LOCATION_SERVICE);
        if(!locManager.isProviderEnabled(LocationManager.GPS_PROVIDER)){
            Toast.makeText(this,"未打开GPS,无法扫描", Toast.LENGTH_SHORT).show();
        }

        //button group
        start = findViewById(R.id.start);
        connect = findViewById(R.id.connect);
        stop = findViewById(R.id.stop);
        send = findViewById(R.id.send);
        generate = findViewById(R.id.generate);
        test = findViewById(R.id.test);

        //test group
        message = findViewById(R.id.message);
        IPText = findViewById(R.id.IPText);
        m_height = findViewById(R.id.height);
        m_width = findViewById(R.id.width);

        parentLL = findViewById(R.id.positionButton);

        IPText.setText("192.168.12.137:3674");
        stop.setEnabled(false);

        message.setMovementMethod(new ScrollingMovementMethod(){

        });

        start.setText("Start Scan");


        test.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {

                if(ScanResult() & isConnecting & mSocketClient.isConnected()) {
//                    test.setBackgroundColor(getResources().getColor(R.color.colorPrimary));
                    Toast.makeText(MainActivity.this, "test currect position", Toast.LENGTH_SHORT).show();
                    if(ScanResult()){
                        Send_Mode = "Once";
                        generateMap();
                        averageSample();
                    }else {
                        Toast.makeText(MainActivity.this, "Open your wifi and location", Toast.LENGTH_SHORT).show();
                    }
                }else{
                    Toast.makeText(MainActivity.this, "No available socket", Toast.LENGTH_SHORT).show();
                }
            }
        });


        // send current wifi message once
        start.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.d("testDemo", "send once");
                if (ScanResult()) {
                    message.setText("");
                    message.setText("wifi list here: \n\r");
                    if (isConnecting & mSocketClient.isConnected()) {
                        Send_Mode = "Once";
                        averageSample();
                    }
                    else{
                        Toast.makeText(MainActivity.this, "Socket is invalid", Toast.LENGTH_SHORT).show();
                    }
                }else{
                    message.setText("Please Open wifi at first");
                }

            }
        });

        // connect to socket server
        connect.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                if (isConnecting)
                {
                    isConnecting = false;
                    try {
                        if(mSocketClient!=null)
                        {
                            mSocketClient.close();
                            mSocketClient = null;
                        }
                    } catch (IOException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                    mThreadClient.interrupt();
                    IPText.setEnabled(true);
                }
                else
                {
                    stop.setEnabled(true);
                    connect.setEnabled(false);
                    mThreadClient = new Thread(mRunnable);
                    mThreadClient.start();
                    isConnecting = true;
                }
            }
        });

        // send wifi message continously
        send.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.d("testDemo", "continous send");
                if (ScanResult()) {
                    message.setText("wifi list here: \n\r");
                    if (isConnecting && mSocketClient.isConnected()) {
                        Send_Mode = "Constant";
                        averageSample();
                    }else{
                        Toast.makeText(MainActivity.this, "Socket is invalid", Toast.LENGTH_SHORT).show();
                    }
                }

            }
        });


        generate.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                generateMap();
            }
        });



        stop.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                if (isConnecting)
                {
                    isConnecting = false;
                    connect.setEnabled(true);
                    mPrintWriterClient.print(-1); // means stop connection
                    mPrintWriterClient.flush();
                    try {
                        if(mSocketClient!=null)
                        {
                            mSocketClient.close();
                            mSocketClient = null;
                            mPrintWriterClient.close();
                            mPrintWriterClient = null;
                        }
                    } catch (IOException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                    mThreadClient.interrupt();
                    if(timer!=null) timer.cancel();
                    stop.setEnabled(false);
                }
            }
        });

    }


    private Runnable	mRunnable	= new Runnable(){
        public void run(){
            String msgText =IPText.getText().toString();
            if(msgText.length()<=0)
            {
                Message msg = new Message();
                msg.what = 1;
                mHandler.sendMessage(msg);
                return;
            }
            int start = msgText.indexOf(":");
            if( (start == -1) ||(start+1 >= msgText.length()) )
            {
                Message msg = new Message();
                msg.what = 1;
                mHandler.sendMessage(msg);
                return;
            }

            String sIP = msgText.substring(0, start);
            String sPort = msgText.substring(start+1);
            int port = Integer.parseInt(sPort);

            Log.d("testDemo", "IP:"+ sIP + ":" + port);

            try
            {
                mSocketClient = new Socket(sIP, port);
                mSocketClient.setSoTimeout(2000);
                mBufferedReaderClient = new BufferedReader(new InputStreamReader(mSocketClient.getInputStream()));
                mPrintWriterClient = new PrintWriter(mSocketClient.getOutputStream(), true);

                runOnUiThread(new Runnable(){

                    @Override
                    public void run() {
                        // TODO Auto-generated method stub
                        Log.d("testDemo", "connected:" + mSocketClient.isConnected());
                        isConnecting = true;
                        message.setText("Connected to Server!");
                    }
                });
                Message msg = new Message();
                msg.what = 0;
                mHandler.sendMessage(msg);
            }
            catch (Exception e)
            {
                Message msg = new Message();
                msg.what = 0;
                mHandler.sendMessage(msg);
                return;
            }
            char[] buffer = new char[256];
            int count = 0;
            while (isConnecting)
            {
                try
                {
                    if(isSend){
                        mPrintWriterClient.print(MsgToServer);
                        mPrintWriterClient.flush();
                        isSend = false;
                    }
                    if((count = mBufferedReaderClient.read(buffer))>0)
                    {
                        recvMessageClient = getInfoBuff(buffer, count);
                        Log.d("testDemo", "get Msg on thread");
                        Message msg = new Message();
                        msg.what = 2;
                        mHandler.sendMessage(msg);
                    }
                }
                catch (Exception e)
                {
                    Message msg = new Message();
                    msg.what = 3;
                    mHandler.sendMessage(msg);
                }
            }
        }
    };

    Handler mHandler = new Handler(){
        public void handleMessage(Message msg)		{
            super.handleMessage(msg);
            switch (msg.what){
                case 1:
//                    message.setText("Errors happen in IP address");
                    Log.d("testDemo", "Errors happen in IP address");
                    break;
                case 2:
                    Log.d("testDemo", recvMessageClient);
                    message.setText("position message:  "+ recvMessageClient);
                    recvMessageClient = recvMessageClient.replace("\n","");
                    int k = Integer.parseInt(recvMessageClient);
                    ButtonGroups[k].setBackgroundColor(getResources().getColor(R.color.Lavenda));
                    recvMessageClient = "";
                    break;
                case 3:
                    break;

            }
        }
    };


    private String getInfoBuff(char[] buff, int count){
        char[] temp = new char[count];
        for(int i=0; i<count; i++){
            temp[i] = buff[i];
        }
        return new String(temp);
    }


    private void generateMap(){
        parentLL.removeAllViews();
        int height = Integer.parseInt(m_height.getText().toString());
        int width = Integer.parseInt(m_width.getText().toString());
        double height_n = height/3;
        double width_n = width/3;
        int horizn_pixel = (int) (parentLL.getHeight()/width_n);
        int vertica_pixel = (int) (parentLL.getWidth()/height_n);
        int size = (int)Math.floor(height_n*width_n);
        String[] btnName = new String[size];
        ButtonGroups = new View[size];
        for (int i=0; i<size; i++){
            btnName[i] = "Btn" + i;
        }
        LinearLayout.LayoutParams layoutParams =  new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        layoutParams.setMargins(0, -2, 0, 0);

        ArrayList<Button> childBtns = new ArrayList<Button>();
        int totalBtns = 0;

        for (int i=0; i<size; i++){
            String item = Integer.toString(i);
            LinearLayout.LayoutParams itemParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            itemParams.weight = 1;
            totalBtns++;
            itemParams.height = horizn_pixel;
            itemParams.width = vertica_pixel;
            Button childBtn = (Button) LayoutInflater.from(MainActivity.this).inflate(R.layout.press_button, null);
            childBtn.setText(item);
            childBtn.setTag(i);
            childBtn.setBackground(getResources().getDrawable(R.drawable.rectangle));
            childBtn.setLayoutParams(itemParams);
            childBtn.setOnClickListener(listener);
            childBtns.add(childBtn);
            ButtonGroups[i] = childBtn;


            if(totalBtns >= 3){
                LinearLayout horizLL = new LinearLayout(MainActivity.this);
                horizLL.setOrientation(LinearLayout.HORIZONTAL);
                horizLL.setLayoutParams(layoutParams);

                for(Button addBtn:childBtns){
                    horizLL.addView(addBtn);
                }
                parentLL.addView(horizLL);
                childBtns.clear();
                totalBtns = 0;
            }
        }
    }

    public void onDestroy() {
        super.onDestroy();
        if (isConnecting)
        {
            isConnecting = false;
            try {
                if(mSocketClient!=null)
                {
                    mSocketClient.close();
                    mSocketClient = null;

                    mPrintWriterClient.close();
                    mPrintWriterClient = null;
                }
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            mThreadClient.interrupt();
        }
    }


    public void requestLocationPermission(){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            //判断是否具有权限
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                        Manifest.permission.ACCESS_COARSE_LOCATION)) {
                    Toast.makeText(this, "You have to allow GPS if you want wifi info", Toast.LENGTH_SHORT);

                }
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},
                        1);
            }
        }
    }


    private void averageSample(){
        try{
            if(innertimer!=null) innertimer.cancel();
            stable_wifi = new ArrayList<WifiInfo>();
            innertimer = new Timer();
            stable_count = 0;
            isSend = false;
            innertask = new TimerTask() {
                @Override
                public void run() {
                    Message message = new Message();
                    message.what = 2;
                    myhandler.sendMessage(message);
                };
            };
            myhandler = new Handler(){
                @Override
                public void handleMessage(Message msg) {
                    super.handleMessage(msg);
                    switch (msg.what){
                        case 2:
                            stable_count++;
                            pointlist();
                            if (stable_count == stable_times){
                                String str = "";
                                String str2= "";
                                if(stable_wifi.size()==0) message.setText("No wifi message here");
                                else{
                                    for(int j=0; j<stable_wifi.size(); j++){
                                        if(stable_wifi.get(j).alltime >= stable_times*0.7){
                                            int temp = (stable_wifi.get(j).level - stable_wifi.get(j).max - stable_wifi.get(j).min) / (stable_wifi.get(j).alltime - 2);
                                            str += stable_wifi.get(j).SSID + " "+ stable_wifi.get(j).BSSID +"  " + temp + "\n";
                                            str2 += stable_wifi.get(j).BSSID +"%" + temp + "$";

                                        }
                                    }
                                    str2 += pb + "#";
                                    message.setText(str);
                                    Log.d("testDemo", str);
                                    MsgToServer = str2;
                                    isSend = true;
//                                    mPrintWriterClient.print(str2);
//                                    mPrintWriterClient.flush();
                                    innertimer.cancel();
                                    if(Send_Mode == "Once") innertimer.cancel();
                                    if(Send_Mode == "Constant"){
                                        stable_count = 0;
                                        stable_wifi.removeAll(stable_wifi);
                                        stable_wifi = new ArrayList<WifiInfo>();
                                    }
                                }
                            }
                            break;
                    }
                }
            };
            innertimer.schedule(innertask, 0, 50);
        }catch (Exception e){
            Toast.makeText(MainActivity.this, "error"+e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void openWifi() {
        if (!wifiManager.isWifiEnabled()) {
            wifiManager.setWifiEnabled(true);
        }
    }

    public void pointlist(){
        String BSSID = "";
        String SSID = "";
        int level = -100;
        boolean b;
        wifiManager.startScan();
        wifi_list = wifiManager.getScanResults();
        if(wifi_list!=null && wifi_list.size()>0) {
            for (int i = 0; i < wifi_list.size(); i++) {
                b = false;
                BSSID = wifi_list.get(i).BSSID;
                SSID = wifi_list.get(i).SSID;
                level = wifi_list.get(i).level;
                for (int j = 0; j < stable_wifi.size(); j++) {
                    if (stable_wifi.get(j).BSSID.equals(BSSID)) {
                        stable_wifi.get(j).level += level;
                        stable_wifi.get(j).alltime++;
                        if (level > stable_wifi.get(j).max) stable_wifi.get(j).max = level;
                        if (level < stable_wifi.get(j).min) stable_wifi.get(j).min = level;
                        b = true;
                        break;
                    }

                }
                if (!b) {
                    WifiInfo t_wifi = new WifiInfo();
                    t_wifi.SSID = SSID;
                    t_wifi.BSSID = BSSID;
                    t_wifi.level = level;
                    t_wifi.alltime++;
                    t_wifi.max = t_wifi.min = level;
                    stable_wifi.add(t_wifi);
                }
            }
        }
    }

    private boolean ScanResult() {
        LocationManager locManager = (LocationManager)getApplicationContext().getSystemService(Context.LOCATION_SERVICE);

        if(!locManager.isProviderEnabled(LocationManager.GPS_PROVIDER)){
            Toast.makeText(this,"cannot scan without GPS", Toast.LENGTH_SHORT).show();
            return false;
        }
        else{
            wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            openWifi();
            return true;
        }
    }

}