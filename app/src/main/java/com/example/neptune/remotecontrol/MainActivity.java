package com.example.neptune.remotecontrol;

// Remote Control App

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.SystemClock;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.speech.RecognizerIntent;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.RunnableFuture;
import java.util.regex.Pattern;

import static com.example.neptune.remotecontrol.StatusFragment.StatusItem;

public class MainActivity extends AppCompatActivity implements SetADFNameDialog.CallbackListenerADF,
                                                    SetLocationNameDialog.CallbackListenerLocation{

    Socket mSocket;

    String mIP="0";
    int mPort=0;

    TextView mDumpText;
    ScrollView mDumpScroll;
    PrintWriter mWriter=null;
    DataInputStream mReader;
    Thread mListenerThread;

    MapView mMapView;

    int mTimerDelay=500;
    int mTimerCount=0;

    AsyncTask mConnectTask;

    boolean mInPrefs;
    boolean mConnecting;//while asynctask is going

    Timer mTimer;
    String mLocationUse="";

    @Override
    protected void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar=(Toolbar)findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        getFragmentManager().beginTransaction().add(R.id.frameLeft, new StatusFragment()).commit();
        findViewById(android.R.id.content).setKeepScreenOn(true);
        setTitle("Mission Control");
        mDumpText=(TextView)findViewById(R.id.dumpText);
        mDumpScroll=(ScrollView)findViewById(R.id.demoScroller);
        mMapView=(MapView)findViewById(R.id.imageMap);
    }

    void dumpTitle(final String t){
        runOnUiThread(new Runnable(){
            @Override
            public void run(){
                setTitle(t);
            }
        });
    }

    void dump(final String mess){
        runOnUiThread(new Runnable(){
            @Override
            public void run(){
                mDumpText.append(mess + "\n");
                mDumpScroll.fullScroll(View.FOCUS_DOWN);
            }
        });
    }


    void readPrefs(){
        SharedPreferences pref=PreferenceManager.getDefaultSharedPreferences(this);
        mIP=pref.getString("pref_ip", "192.168.50.1");
        mPort=Integer.parseInt(pref.getString("pref_port", "6000"));
    }

    public void onCommandClick(View view){
        String command=(((Button)view).getText().toString());

        //if not connected don't do anything
        if(mSocket == null || !mSocket.isConnected() || mListenerThread==null || !mListenerThread.isAlive()){
            dump("~"+command);
        }

        // connected, execute command
        else{

            switch(command){
                case "Save ADF":
                    showSetADFNameDialog();
                    break;
                case "Add Location":
                    mWriter.println("stop");
                    mLocationUse="Add";
                    new SetLocationNameDialog().show(getFragmentManager(), "LocationNameDialog");
                    break;
                case "Go To Location":
                    mLocationUse="Go";
                    new SetLocationNameDialog().show(getFragmentManager(), "LocationNameDialog");
                    break;
                case "Listen":
                    //recognizeSpeech("What location");
                    mWriter.println(command);
                    break;
                case "Depth On":
                    mMapView.mDepthOn=true;
                    mWriter.println(command);
                    break;
                case "Depth Off":
                    mMapView.mDepthOn=false;
                    mWriter.println(command);
                    break;
                default:
                    Log.w("SEND","sending "+command+" "+mWriter.checkError());
                    mWriter.println(command);
            }
        }
    }

    public void recognizeSpeech(String p) {
        new AsyncTask<Void, Void, String>() {
            @Override
            protected String doInBackground(Void... params) {
                Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
                intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
                intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
                intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "What is your favorite Color");
                try {
                    startActivityForResult(intent, 2);

                } catch (ActivityNotFoundException e) {
                    e.printStackTrace();
                }
                return null;
            }
        }.execute();
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data){
        super.onActivityResult(requestCode, resultCode, data);

            if(resultCode==RESULT_OK && null != data){
                ArrayList<String> result = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);

                for(String s:result){
                    //full+=" "+s;
                }

            }
    }

    private void showSetADFNameDialog(){
        mWriter.println("Stop");
        new SetADFNameDialog().show(getFragmentManager(), "ADFNameDialog");
    }
    @Override
    public void onAdfNameOk(String name){
        dump("Save ADF"+"%"+name);
        mWriter.println("Save ADF"+"%"+name);
    }
    @Override
    public void onLocationNameOk(String name){
        switch(mLocationUse){
            case "Add":
                dump("Add Location" + "%" + name);
                mWriter.println("Save Location" + "%" + name);
                break;
            case "Go":
                dump("Go Location" + "%" + name);
                mWriter.println("Go To Location" + "%" + name);
                break;
        }
    }

    void disconnect(){
        Log.e("TAG","disconnect" );
        if(mConnecting&&mConnectTask!=null)
            mConnectTask.cancel(true);

        if(mSocket != null){
            if(mSocket.isConnected())
                try{
                    mSocket.close();
                }catch(IOException e){
                    e.printStackTrace();
                }
            mSocket=null;
        }
    }

    void connect(){
        readPrefs();
        if(mConnecting){
            dump("can't start another connect task");
            return;
        }
        mConnectTask=new AsyncTask<Void,Void,Boolean>(){
            @Override
            protected Boolean doInBackground(Void... params){
                mConnecting=true;
                try{
                    InetAddress serverAddr=InetAddress.getByName(mIP);
                    dumpTitle("Mission Control   (Trying to Connect)");
                    mSocket=new Socket();
                    mSocket.connect(new InetSocketAddress(serverAddr, mPort), 400);
                    return mSocket.isConnected();
                }catch(UnknownHostException e){
                    Log.w("CONNECTUNKNOWN", e.getMessage());
                    mSocket=null;
                    return false;
                }catch(IOException e){
                    //Log.w("CONNECTIO", e.getMessage());
                    mSocket=null;
                    return false;
                }
            }

            @Override
            protected void onCancelled(Boolean aBoolean){
                super.onCancelled(aBoolean);
                Log.e("TAG","Connect task cancelled");
                mConnecting=false;
                mSocket=null;
            }

            @Override
            protected void onPostExecute(Boolean isConnected){
                mConnecting=false;
                if(isConnected){
                    try{
                        if(mSocket != null){
                            mTimerCount=0;
                            mTimerDelay=500;
                            mWriter=new PrintWriter(new BufferedWriter(
                                    new OutputStreamWriter(mSocket.getOutputStream())),
                                    true);
                            mReader=new DataInputStream(mSocket.getInputStream());
                            startListener();
                            dumpTitle("Mission Control   (Connected)");
                        }
                    }catch(IOException e){
                        dump("GetOutputStream Error");
                    }
                }else{
                    mSocket=null;
                }
            }
        }.execute();
    }

    void startListener(){
        mListenerThread=new Thread(new ReaderThread());
        mListenerThread.start();
    }

    enum SendDataType{
        POSITIONROTATION(Type.FLOAT,4),
        STRINGCOMMAND(Type.STRING,0),
        TARGETADDED(Type.BOTH,2),
        TARGETSCLEARED(Type.NONE,0),
        DEPTHDATA(Type.FLOAT,3),
        ADDOBSTACLE(Type.FLOAT,2);
        Type type;
        int numVals;
        SendDataType(Type t, int n){type=t;numVals=n;}
        enum Type{STRING,FLOAT,NONE,BOTH}
    }

    class ReaderThread implements Runnable{
        public void run(){
            //setListenerStatus(true);
            float[] fdata=null;
            byte[] sdata=null;
            Pattern pat=Pattern.compile("%");
            readloop:
            while(mSocket != null && mSocket.isConnected() && !Thread.currentThread().isInterrupted()){
                try{
                    // read code (SendDataType ordinal)
                    int code=mReader.readInt();
                    //check for error
                    if(code<0 || code>SendDataType.values().length)
                        return;

                    int n,len;
                    switch(SendDataType.values()[code].type){
                        case STRING:
                            len=mReader.readInt();
                            if(len>100)//probably error
                                return;
                            sdata=new byte[len];
                            mReader.readFully(sdata, 0, len);
                            break;

                        case FLOAT:
                            n=SendDataType.values()[code].numVals;
                            fdata=new float[n];
                            for(int i=0; i<n; i++){
                                fdata[i]=mReader.readFloat();
                            }
                            break;

                        case BOTH:
                            n=SendDataType.values()[code].numVals;
                            fdata=new float[n];
                            for(int i=0; i<n; i++){
                                fdata[i]=mReader.readFloat();
                            }

                            len=mReader.readInt();
                            if(len>100)//probably error
                                return;
                            sdata=new byte[len];
                            mReader.readFully(sdata, 0, len);

                    }

                    switch(SendDataType.values()[code]){

                        case STRINGCOMMAND:
                            stringCommand(new String(sdata),pat);
                            continue;

                        case POSITIONROTATION:
                           mMapView.setRobot(fdata[0], fdata[1], fdata[3]);
                            continue;

                        case TARGETADDED:
                            mMapView.addTarget(fdata[0],fdata[1],new String(sdata));
                            break;
                        case TARGETSCLEARED:
                            mMapView.clearTargets();
                            break;
                        case DEPTHDATA:
                            mMapView.addDepthPt(fdata[0],fdata[1],fdata[2]);
                            break;
                        case ADDOBSTACLE:
                            mMapView.addObstPt(fdata[0],fdata[1]);
                            break;
                        default:
                            Log.e("READTHREAD","default");
                            return;
                    }
                }catch(IOException e){
                    Log.e("TAG","Reader()excep: " + e.getMessage() );
                    return;
                }
            }
        }
    }

    private void stringCommand(String s, Pattern pat){
        if(s != null){
            if(s.contains("%")){
                String[] key=pat.split(s, 5);
                if(key.length != 2){
                    dump("bad key");
                    return;
                }
                for(StatusItem item : StatusItem.values()){
                    if(item.string.equals(key[0])){

                        switch(item){
                            case POSITION:
                            case ROTATION:
                                setStatus(item,key[1]);
                                break;
                            case REMOTECON:
                            case REMOTEIP:
                            case REMOTEPORT:
                            case REMOTERUN:
                                break;
                            case LEARNING:
                                final String fs=key[1];
                                runOnUiThread(new Runnable(){
                                    @Override
                                    public void run(){
                                        if(fs.equals("YES"))
                                            ((Button)findViewById(R.id.buttonLearnADF)).setText("Cancel Learn");
                                        else
                                            ((Button)findViewById(R.id.buttonLearnADF)).setText("Learn ADF");
                                    }
                                });
                           default:
                                setStatus(item,key[1]);
                                //dump("     $" + s);
                        }
                        return;
                    }
                }
            }else{
                switch(s){
                    case "Clear Obstacles":
                        mMapView.clearObstacles();
                        break;
                    case "Clear Targets":
                        mMapView.clearTargets();
                        break;
                    case "Clear Path":
                        break;
                    case "Added Location":

                        break;
                    case "Depth On":
                        mMapView.mDepthOn=true;
                        break;
                    case "Depth Off":
                        mMapView.mDepthOn=false;
                        break;
                }

                dump("   >" + s);
            }
        }
    }

    void setSocketStatus(String ip,int port,boolean connected){
        setStatus(StatusItem.REMOTEIP,ip);
        setStatus(StatusItem.REMOTEPORT,port+"");
        setStatus(StatusItem.REMOTERUN,connected? "YES":"NO");
        dumpTitle("Mission Control   "+(!connected? "(Trying to Connect)": "Connected to "+ip+" :"+port));
    }
    void setListenerStatus(boolean running){
        setStatus(StatusItem.REMOTECON,running? "YES":"NO");
    }
    void setStatus(final StatusItem item, final String s){
        runOnUiThread(new Runnable(){
            @Override
            public void run(){
                StatusFragment.setStatusItem(item, s);
            }
        });
    }

    void startTimer(){
        mTimer=new Timer();
        mTimer.schedule(new TimerTask(){
            @Override
            public void run(){
                mTimerCount++;
                if(mTimerCount==20){
                    mTimerDelay=2000;
                    cancel();
                    startTimer();
                    return;
                }
                if(mSocket==null){
                    connect();
                    return;
                }
                boolean list=false,sock=false;
                if(mListenerThread!=null){
                    list=mListenerThread.isAlive();
                    if(mSocket==null || mSocket.isClosed()){
                        sock=false;
                    }else{
                        sock=true && list;
                    }
                }

                setSocketStatus(mIP,mPort,sock);
                setListenerStatus(list);

                if(!list || !sock){
                    Log.w("TIMER","restarting connection");
                    restart();
                }
            }
        }, 500, mTimerDelay);
    }

    private void restart(){
        disconnect();
        connect();
    }

    @Override
    protected void onResume(){
        super.onResume();
        readPrefs();
        startTimer();
    }

    @Override
    protected void onPause(){
        super.onPause();
        if(mTimer!=null)mTimer.cancel();
        disconnect();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu){
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item){
        int id=item.getItemId();

        if(id == R.id.action_settings){

            if(!mInPrefs){
                getFragmentManager().beginTransaction().replace(R.id.frameLeft, new PrefsActivity()).addToBackStack(null).commit();
                mInPrefs=true;
            }else{
                popSettings();
            }

            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void popSettings(){
        if(mInPrefs){
            mInPrefs=false;
            getFragmentManager().popBackStack();
        }
    }

    public static class PrefsActivity extends PreferenceFragment{
        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState){
            View v=super.onCreateView(inflater, container, savedInstanceState);

            addPreferencesFromResource(R.xml.prefs);

            return v;
        }
    }
}


