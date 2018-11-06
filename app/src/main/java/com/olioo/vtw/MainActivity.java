package com.olioo.vtw;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.media.MediaPlayer;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
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
import com.olioo.vtw.warp.WarpArgs;
import com.olioo.vtw.warp.Warper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

public class MainActivity extends AppCompatActivity {

    public static final String TAG = "MainActivity";

    public static final int HNDL_WARP_DONE = 0;
    public static final int HNDL_PREVIEW_BMP = 1;
    public static final int HNDL_UPDATE_PROGRESS = 2;
    public static final int HNDL_HIDE_KEYBOARD = 2;

    public static Context context;
    public static Handler handle;
    public static WarpArgs args = new WarpArgs();
    public static Warper warper;

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
                        warper = null; Log.d(TAG, "Warp done!");

                        videoView.setVideoURI(Uri.parse(args.encodePath));
                        videoView.start();
                        break;
                    //case HNDL_WATCH_VID: break;
                    case HNDL_HIDE_KEYBOARD:
                        InputMethodManager imm = (InputMethodManager) context.getSystemService(Activity.INPUT_METHOD_SERVICE);
                        imm.hideSoftInputFromWindow(((jEditText)msg.obj).getWindowToken(), 0);
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


    public boolean vidSaved = false;
    public void saveRawVideo() {
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
            //save raw video
            String path = Environment.getExternalStorageDirectory()+"/test.mp4";

            try {
                InputStream in = getResources().openRawResource(R.raw.video_480x360_mp4_h264_500kbps_30fps_aac_stereo_128kbps_44100hz);
                FileOutputStream out = new FileOutputStream(path);
                byte[] buff = new byte[1024];
                int read = 0;

                while ((read = in.read(buff)) > 0)
                    out.write(buff, 0, read);

                in.close();
                out.close();
                vidSaved = true;
            } catch (Exception e) { e.printStackTrace(); }
            }
        });

        thread.start();
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
                start();
            }
        });
    }

    public void start() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                // for testing purposes, save a small video
                saveRawVideo();
                while (!vidSaved) {
                    try { Thread.sleep(1000); }
                    catch (InterruptedException e) { e.printStackTrace(); }
                }

                // warp args
                args.decodePath = Environment.getExternalStorageDirectory()+"/test.mp4";
                args.profileDecodee(args.decodePath);
                args.encodePath = Environment.getExternalStorageDirectory()+"/out.mp4";
                new File(args.encodePath).delete();
                int width = args.decWidth; width -= width % 16;
                int height = args.decHeight; height -= height % 16;
                args.outWidth = width;
                args.outHeight = height;
                args.amount = 500000; //us
                args.bitrate = 160000000;
                args.frameRate = 30;
                Log.d("WarpArgs", args.print());

                // create warper and use it
                warper = new Warper();
                warper.warp();
                warper.release();

                // mediascan the file afterwards
                File file = new File(args.encodePath);
                MediaScannerConnection.scanFile(
                        getApplicationContext(),
                        new String[]{file.getAbsolutePath()},
                        null,
                        new MediaScannerConnection.OnScanCompletedListener() {
                            @Override
                            public void onScanCompleted(String path, Uri uri) {
                                Log.d(TAG, "Scan completed: "+args.encodePath);
                                handle.obtainMessage(HNDL_WARP_DONE).sendToTarget();
                            }
                        });
            }
        }).start();
    }

}
