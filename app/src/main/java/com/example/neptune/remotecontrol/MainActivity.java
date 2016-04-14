package com.example.neptune.remotecontrol;

import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;

public class MainActivity extends AppCompatActivity{

    Socket mSocket;

    String mIP="0";
    int mPort=0;

    EditText mEditIP, mEditPort;
    TextView mDumpText;
    ScrollView mDumpScroll;
    PrintWriter mWriter=null;

    AsyncTask mConnectTask;
    boolean mWasConnected;

    @Override
    protected void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar=(Toolbar)findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);


        mEditIP=(EditText)findViewById(R.id.editIP);
        mEditPort=(EditText)findViewById(R.id.editPort);
        mDumpText=(TextView)findViewById(R.id.dumpText);
        mDumpScroll=(ScrollView)findViewById(R.id.demoScroller);

        readPrefs();
        if(mWasConnected)
            connect();


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

    void writePrefs(){
        SharedPreferences pref=this.getPreferences(this.MODE_PRIVATE);
        SharedPreferences.Editor editor=pref.edit();
        editor.putString("IPAddress", mIP);
        editor.putInt("Port", mPort);
        editor.putBoolean("WasConnected", mWasConnected);
        editor.apply();
    }

    void readPrefs(){
        SharedPreferences pref=this.getPreferences(this.MODE_PRIVATE);
        mIP=pref.getString("IPAddress", "192.168.50.1");
        mPort=pref.getInt("Port", 6000);
        mWasConnected=pref.getBoolean("WasConnected", false);
        mEditIP.setText(mIP);
        mEditPort.setText(mPort + "");
    }

    public void onConnectClick(View view){
        mIP=mEditIP.getText().toString();
        mIP.replaceAll("\\s+", "");
        mEditIP.setText(mIP);
        String ps=mEditPort.getText().toString();
        ps.replaceAll("\\s+", "");
        try{
            mPort=Integer.parseInt(ps);
            mEditPort.setText(mPort + "");
            setTitle("Connecting to " + mIP + " Port: " + mPort + " ...");
            // save ip and port to preferences
            writePrefs();
            connect();
        } catch(NumberFormatException e){
            setTitle("Invalid Port");
        }
    }

    public void onCommandClick(View view){
        String command=(((Button)view).getText().toString());

        if(mSocket==null){
            setTitle("Connect First");
            return;
        }
        if(!mSocket.isConnected()){
            setTitle("Not Connected");
            return;
        }

        dump(command);
        mWriter.println(command);
    }

    public void onDisconnectClick(View view){
        disconnect();
    }

    void disconnect(){
        if(mConnectTask!=null && mConnectTask.getStatus()==AsyncTask.Status.RUNNING){
            mConnectTask.cancel(true);
        }
        if(mSocket!=null){
            if(mSocket.isConnected())
                try{
                    mSocket.close();
                } catch(IOException e){
                }
            mSocket=null;
        }
        setTitle("Disconnected");
        mWasConnected=false;
    }

    void connect(){
        mConnectTask=new AsyncTask<Void, Void, Boolean>(){
            @Override
            protected Boolean doInBackground(Void... params){
                try{
                    InetAddress serverAddr=InetAddress.getByName(mIP);
                    runOnUiThread(new Runnable(){
                        @Override
                        public void run(){
                            setTitle("Trying to Connect to " + mIP + " :" + mPort);
                        }
                    });

                    mSocket=new Socket();
                    InetSocketAddress addr=new InetSocketAddress(serverAddr, mPort);
                    mSocket.connect(addr, 2000);
                } catch(UnknownHostException e1){
                    e1.printStackTrace();
                    mSocket=null;
                    return false;
                } catch(IOException e1){
                    e1.printStackTrace();
                    mSocket=null;
                    return false;
                }

                return mSocket.isConnected();
            }

            @Override
            protected void onPostExecute(Boolean isCon){
                if(isCon){
                    setTitle("Connected to " + mIP + " :" + mPort);
                    try{
                        mWriter=new PrintWriter(new BufferedWriter(
                                new OutputStreamWriter(mSocket.getOutputStream())),
                                true);
                        mWasConnected=true;
                    } catch(IOException e){
                        setTitle("GetOutputStream Error");
                    }
                } else{
                    setTitle("Failed to Connect");
                    mSocket=null;
                }
            }
        }.execute();
    }

    @Override
    protected void onResume(){
        super.onResume();
        readPrefs();
        if(mWasConnected)
            connect();
    }

    @Override
    protected void onPause(){
        super.onPause();
        if(mSocket!=null && mSocket.isConnected())
            mWasConnected=true;
        writePrefs();
        disconnect();
    }
}
