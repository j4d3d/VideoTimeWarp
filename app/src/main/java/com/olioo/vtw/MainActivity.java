package com.olioo.vtw;

import android.Manifest;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.VideoView;
import com.olioo.vtw.gui.jEditText;
import com.olioo.vtw.util.Helper;
import com.olioo.vtw.warp.WarpService;

public class MainActivity extends AppCompatActivity {

    public static final String TAG = "MainActivity";

    public static final int HNDL_WARP_DONE = 0;
    public static final int HNDL_PREVIEW_BMP = 1;
    public static final int HNDL_UPDATE_PROGRESS = 2;
    public static final int HNDL_HIDE_KEYBOARD = 3;

    public Context context;
    public static Handler handle;

    public static WarpService warpService;
    boolean serviceBound = false;

    VideoView videoView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        context = getBaseContext();
    }

    @Override
    protected void onStart() {
        super.onStart();

        setupGUI();

        Helper.getPermissions(this, getBaseContext(), new String[]{
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
        });

        //create handle
        handle = new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
//                    case HNDL_PREVIEW_BMP: imageView.setImageBitmap((Bitmap)msg.obj); break;
//                    case HNDL_UPDATE_PROGRESS: mainProgress.setProgress((int)msg.obj); break;
                    case HNDL_WARP_DONE:
                        Log.d(TAG, "Warp done!");
                        stopService();
                        videoView.setVideoURI(Uri.parse(msg.obj+""));
                        videoView.start();
                        break;
                    //case HNDL_WATCH_VID: break;
                    case HNDL_HIDE_KEYBOARD:
                        InputMethodManager imm = (InputMethodManager) context.getSystemService(Activity.INPUT_METHOD_SERVICE);
                        try { imm.hideSoftInputFromWindow(((jEditText) msg.obj).getWindowToken(), 0);
                        } catch (NullPointerException e) { e.printStackTrace(); }
                        break;
                    default: Log.d("unhandled message", msg.what+"\t"+msg.obj); break;
                }
            }
        };


        videoView = findViewById(R.id.videoView);
        videoView.setOnCompletionListener(new MediaPlayer.OnCompletionListener(){
            @Override
            public void onCompletion(MediaPlayer mediaPlayer) {
//                mediaPlayer.reset();
                mediaPlayer.start();
            }
        });


//        videoView.setVideoURI(Uri.parse(Environment.getExternalStorageDirectory()+"/test.mp4"));
//        videoView.start();

//        start();

    }

    @Override
    protected void onDestroy() {
        // unbind the service without stopping it
        if (serviceBound) {
            unbindService(mConnection);
            serviceBound = false;
        }

        super.onDestroy();
    }


    public jEditText boxFileName;
    public Spinner spinWarpType;
    public SeekBar seekSeconds;
    public jEditText boxSeconds;
    public Button btnStart;
    public void setupGUI() {
        boxFileName = findViewById(R.id.boxFileName);
        spinWarpType = findViewById(R.id.spinWarpType);
        seekSeconds = findViewById(R.id.seekSeconds);
        boxSeconds = findViewById(R.id.boxSeconds);
        btnStart = findViewById(R.id.btnStart);

//        boxFileName.setOn
        //setup spinner
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.warp_modes, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinWarpType.setAdapter(adapter);
        spinWarpType.setOnItemSelectedListener(new Spinner.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView p, View v, int pos, long id) { /*spinnerPos = pos;*/ }
            @Override public void onNothingSelected(AdapterView p) { }
        });

        btnStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!WarpService.started) startService();
                else stopService();
            }
        });
    }

    void startService() {
        // start and bind buddy TTS service
        if (!WarpService.started) startService(new Intent(getBaseContext(), WarpService.class));
        bindService(new Intent(this, WarpService.class), mConnection, Context.BIND_AUTO_CREATE);
        serviceBound = true;
    }

    void stopService() {
        // unbind the service
        if (serviceBound) {
            unbindService(mConnection);
            serviceBound = false;
        } // stop service
        if (WarpService.started) {
            stopService(new Intent(context, WarpService.class));
            Log.d(TAG, "WarpService has been stopped.");
        } else Log.d(TAG, "WarpService hasn't yet been started.");
    }

    private ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            Log.d(TAG, "onServiceConnected");
            warpService = ((WarpService.LocalBinder)iBinder).getInstance();
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            Log.d(TAG, "onServiceDisconnected");
            warpService = null;
        }
    };

}
