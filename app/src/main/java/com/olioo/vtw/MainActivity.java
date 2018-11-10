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
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.VideoView;
import com.olioo.vtw.gui.jEditText;
import com.olioo.vtw.util.Helper;
import com.olioo.vtw.warp.WarpArgs;
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

    public static Handler handle;
    public static WarpService warpService;

    String decPath = null;

    VideoView videoView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
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
                    case HNDL_UPDATE_PROGRESS: progWarp.setProgress((int)msg.obj); break;
                    case HNDL_WARP_DONE:
                        Log.d(TAG, "Warp done!");
                        stopWarpService();
                        videoView.setVideoURI(Uri.parse(msg.obj+""));
                        videoView.start();
                        lytMain.setVisibility(View.VISIBLE);
                        break;
                    //case HNDL_WATCH_VID: break;
                    case HNDL_HIDE_KEYBOARD:
                        InputMethodManager imm = (InputMethodManager) getBaseContext().getSystemService(Activity.INPUT_METHOD_SERVICE);
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

        Log.d(TAG, "Instance: "+WarpService.instance);
//        videoView.setVideoURI(Uri.parse(Environment.getExternalStorageDirectory()+"/test.mp4"));
//        videoView.start();
    }

    @Override
    protected void onDestroy() {
        // set static vars to null
        handle = null;

        super.onDestroy();
    }


    public ConstraintLayout lytMain, lytWarp, lytWarping;

    public Button btnWarp;

    public jEditText boxFileName;
    public Spinner spinWarpType;
    public Switch swtInvert;
    public jEditText boxSeconds;
    public SeekBar seekSeconds;
    public jEditText boxScale;
    public SeekBar seekScale;

    public Button btnHalt;
    public ProgressBar progWarp;

    public Button btnStart;
    public void setupGUI() {
        lytMain = findViewById(R.id.lytMain);
        btnWarp = findViewById(R.id.btnWarp);

        lytWarp = findViewById(R.id.lytWarp);
        boxFileName = findViewById(R.id.boxFileName);
        spinWarpType = findViewById(R.id.spinWarpType);
        swtInvert = findViewById(R.id.swtInvert);
        boxSeconds = findViewById(R.id.boxSeconds);
        seekSeconds = findViewById(R.id.seekSeconds);
        boxScale = findViewById(R.id.boxScale);
        seekScale = findViewById(R.id.seekScale);
        btnStart = findViewById(R.id.btnStart);

        lytWarping = findViewById(R.id.lytWarping);
        btnHalt = findViewById(R.id.btnHalt);

        progWarp = findViewById(R.id.progWarp);

        btnWarp.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                lytMain.setVisibility(View.GONE);
                Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Video.Media.EXTERNAL_CONTENT_URI);
                startActivityForResult(intent, VSL_WARP);
            }
        });

        // setup spinner
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.warp_modes, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinWarpType.setAdapter(adapter);
        spinWarpType.setOnItemSelectedListener(new Spinner.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView p, View v, int pos, long id) { /*spinnerPos = pos;*/ }
            @Override public void onNothingSelected(AdapterView p) { }
        });

        // seconds slider
        seekSeconds.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) { boxSeconds.setText(""+(progress/1000f)); }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        });

        // scale slider
        seekScale.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) { boxScale.setText(""+(progress/10000f)); }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        });

        btnStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startWarpService();
                lytWarp.setVisibility(View.GONE);
                lytWarping.setVisibility(View.VISIBLE);
            }
        });

        btnHalt.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                WarpService.instance.warper.halt = true;
                lytWarping.setVisibility(View.GONE);
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
                            //halt WarpService.instance.warper if exists, catch nullpointer just in case
                            try {
                                if (WarpService.instance != null && WarpService.instance.started && !WarpService.instance.finished)
                                    WarpService.instance.warper.halt = true;
                            } catch (NullPointerException e) {
                                Log.d(TAG, "WarpService.instance became null as warper.halt set to true. No harm done.");
                            }

                            // wait for service to finish
                            while (WarpService.instance != null) try {
                                Log.d(TAG, "Waiting for Warper to be halted...");
                                Thread.sleep(100);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }

                            //path of chosen video
                            decPath = Helper.getRealPathFromURI(getBaseContext(), targetUri);

                            //show warp window
                            lytWarp.setVisibility(View.VISIBLE);
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

    void startWarpService() {
        Intent serviceIntent = new Intent(this, WarpService.class);
        serviceIntent.putExtra("filenameExtra", boxFileName.getText()+".mp4");
        serviceIntent.putExtra("decodePathExtra", decPath);
        serviceIntent.putExtra("warpTypeExtra", spinWarpType.getSelectedItemPosition());
        serviceIntent.putExtra("invertExtra", swtInvert.isChecked());
        serviceIntent.putExtra("secondsExtra", Float.parseFloat(boxSeconds.getText()+"")*1000000);
        serviceIntent.putExtra("scaleExtra", Float.parseFloat(boxScale.getText()+""));

        ContextCompat.startForegroundService(this, serviceIntent);
    }

    void stopWarpService() {
        Intent serviceIntent = new Intent(this, WarpService.class);
        stopService(serviceIntent);
    }

}
