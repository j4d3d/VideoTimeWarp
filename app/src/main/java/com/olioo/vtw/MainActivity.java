package com.olioo.vtw;

import android.Manifest;
import android.app.Activity;
import android.app.ActivityManager;
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
import android.provider.MediaStore;
import android.support.constraint.ConstraintLayout;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.Layout;
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

import java.util.List;

public class MainActivity extends AppCompatActivity {

    public static final String TAG = "MainActivity";

    public static final int HNDL_WARP_DONE = 0;
    public static final int HNDL_PREVIEW_BMP = 1;
    public static final int HNDL_UPDATE_PROGRESS = 2;
    public static final int HNDL_HIDE_KEYBOARD = 3;

    public static final int VSL_WARP = 0;
    public static final int VSL_WATCH = 1;

    public Context context;
    public static Handler handle;

    public static WarpService warpService;

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
                        stopWarpService();
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

        // bind to the service if started, or start if if not
//        boolean serviceRunning = warpServiceRunning();
//        Log.d(TAG, "serviceRunning:"+serviceRunning);
//        if (serviceRunning)
//            bindService(new Intent(this, WarpService.class), mConnection, 0);
//        else startWarpService();

//        videoView.setVideoURI(Uri.parse(Environment.getExternalStorageDirectory()+"/test.mp4"));
//        videoView.start();
    }

    @Override
    protected void onDestroy() {
        // unbind the service without stopping it
        if (warpService != null) {
//            unbindService(mConnection);
        }
//        stopWarpService();

        super.onDestroy();
    }


    public ConstraintLayout lytMain, lytWarp;
    public Button btnWarp;
    public jEditText boxFileName;
    public Spinner spinWarpType;
    public SeekBar seekSeconds;
    public jEditText boxSeconds;
    public Button btnStart;
    public void setupGUI() {
        lytMain = findViewById(R.id.lytMain);
        btnWarp = findViewById(R.id.btnWarp);
        btnWarp.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Video.Media.EXTERNAL_CONTENT_URI);
                startActivityForResult(intent, VSL_WARP);
            }
        });

        lytWarp = findViewById(R.id.lytWarp);
        boxFileName = findViewById(R.id.boxFileName);
        spinWarpType = findViewById(R.id.spinWarpType);
        seekSeconds = findViewById(R.id.seekSeconds);
        boxSeconds = findViewById(R.id.boxSeconds);
        btnStart = findViewById(R.id.btnStart);

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
                startWarpService();
                lytMain.setVisibility(View.VISIBLE);
                lytWarp.setVisibility(View.GONE);
            }
        });
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == RESULT_OK) {
            final Uri targetUri = data.getData();
            switch (requestCode) {
                case VSL_WARP:
                    Runnable runnable = new Runnable() {
                        @Override
                        public void run() {
                            //cancel previous if there is one unfinished
                            if (warpService != null && warpService.started && !warpService.finished) {
                                warpService.warper.halt = true;
                                while (!warpService.finished) try {
                                    Log.d(TAG, "Waiting for warpControl to be halted...");
                                    Thread.sleep(100);
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }

                            //change gui to warp window
                            lytMain.setVisibility(View.GONE);
                            lytWarp.setVisibility(View.VISIBLE);

                            //init args for chosen video
                            new Thread(new Runnable() {
                                @Override
                                public void run() {
//                                    args.profileDecodee(Helper.getRealPathFromURI(context, targetUri));
//                                    Message.obtain(handle, HNDL_SET_WARP_UI).sendToTarget();
                                }
                            }).start();
                        }
                    };

                    //ask user to cancel current warping video if any, or just start
                    if (warpService != null && warpService.started && !warpService.finished)
                        Helper.runOnYes("A video is currently being warped, cancel it?", this, runnable);
                    else runnable.run();

                    break;
                case VSL_WATCH:
//                    try {
//                        DiaWatch.watchPath = Helper.getRealPathFromURI(this, targetUri);
//                        diaWatch.show(fragmentManager);
//                    } catch (Throwable th) {
//                        diaWatch.dismiss();
//                    }

                    break;
            }
        }
    }

    boolean warpServiceRunning() {
        ActivityManager manager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        List<ActivityManager.RunningServiceInfo> services = manager.getRunningServices(Integer.MAX_VALUE);
        int count = 0;
        for (ActivityManager.RunningServiceInfo service : services){
            if(WarpService.class.getName().equals(service.service.getClassName())) {
                count++;
//                return true;
            }
        }
        Log.d(TAG, "WarpServices running: "+count);
        return count > 0;
    }

    void startWarpService() {
        String input = "heheheeee";//editTextInput.getText().toString();

        Intent serviceIntent = new Intent(this, WarpService.class);
        serviceIntent.putExtra("inputExtra", input);

        ContextCompat.startForegroundService(this, serviceIntent);

//        Intent intent = new Intent(this, WarpService.class);
//        intent.setAction(WarpService.ACTION_START_FOREGROUND_SERVICE);
//        startService(intent);

//        startService(new Intent(this, WarpService.class));
//        bindService(new Intent(this, WarpService.class), mConnection, 0);
    }

    void stopWarpService() {
        Intent serviceIntent = new Intent(this, WarpService.class);
        stopService(serviceIntent);

//        Intent intent = new Intent(this, WarpService.class);
//        intent.setAction(WarpService.ACTION_STOP_FOREGROUND_SERVICE);
//        startService(intent);

//        if (warpService != null) {
//            unbindService(mConnection);
//            stopService(new Intent(context, WarpService.class));
//        }
    }

//    private ServiceConnection mConnection = new ServiceConnection() {
//        @Override
//        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
//            Log.d(TAG, "onServiceConnected");
//            warpService = ((WarpService.LocalBinder)iBinder).getInstance();
//            Log.d(TAG, "Service age: "+(System.currentTimeMillis() - warpService.birth));
//        }
//
//        @Override
//        public void onServiceDisconnected(ComponentName componentName) {
//            Log.d(TAG, "onServiceDisconnected");
//            warpService = null;
//        }
//    };

}
