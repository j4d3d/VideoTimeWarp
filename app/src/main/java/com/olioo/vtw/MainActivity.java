package com.olioo.vtw;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.provider.MediaStore;
import android.support.constraint.ConstraintLayout;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;
import com.olioo.vtw.gui.jEditText;
import com.olioo.vtw.util.Helper;
import com.olioo.vtw.warp.WarpService;

import java.io.File;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends AppCompatActivity {

    public static final String TAG = "MainActivity";

    public static final int HNDL_WARP_DONE = 0;
    public static final int HNDL_PREVIEW_BMP = 1;
    public static final int HNDL_UPDATE_PROGRESS = 2;
    public static final int HNDL_HIDE_KEYBOARD = 3;
    public static final int HNDL_TOAST = 4;
    public static final int HNDL_UPDATE_GUI = 5;

    public static final int VSL_WARP = 0;
    public static final int VSL_WATCH = 1;

    public static Handler handle;

    // these vars determine state, and must be set to null when non applicable
    static String decPath = null;
    static String outPath = null;
    // vars necessary for gui
    static long decBitrate = -1;
    static int warpType = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //hide title and notification bars
//        this.requestWindowFeature(Window.FEATURE_NO_TITLE);
        this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

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
        final Context context = this;
        handle = new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
//                    case HNDL_PREVIEW_BMP: imageView.setImageBitmap((Bitmap)msg.obj); break;
                    case HNDL_UPDATE_PROGRESS: progWarp.setProgress((int)msg.obj); break;
                    case HNDL_WARP_DONE:
                        Log.d(TAG, "Warp done!");
                        stopWarpService();
                        outPath = msg.obj+"";
                        videoView.setVideoURI(Uri.parse(outPath));
                        videoView.start();
                        lytMain.setVisibility(View.VISIBLE);
                        lytWarping.setVisibility(View.GONE);
                        break;
                    //case HNDL_WATCH_VID: break;
                    case HNDL_HIDE_KEYBOARD:
                        InputMethodManager imm = (InputMethodManager) getBaseContext().getSystemService(Activity.INPUT_METHOD_SERVICE);
                        try { imm.hideSoftInputFromWindow(((View) msg.obj).getWindowToken(), 0);
                        } catch (NullPointerException e) { e.printStackTrace(); }
                        break;
                    case HNDL_TOAST:
                        Toast.makeText(context, (String)msg.obj, Toast.LENGTH_LONG).show();
                    case HNDL_UPDATE_GUI:
                        updateLytWarping(true); break;
                    default: Log.d("unhandled message", msg.what+"\t"+msg.obj); break;
                }
            }
        };

        videoView.setOnCompletionListener(new MediaPlayer.OnCompletionListener(){
            @Override
            public void onCompletion(MediaPlayer mediaPlayer) {
//                mediaPlayer.reset();
                mediaPlayer.start();
            }
        });

        Log.d(TAG, "Instance: "+WarpService.instance);
        if (outPath != null) {
            boolean notOverwriting = true;
            if (WarpService.instance != null) {
                File a = new File(WarpService.instance.args.encodePath);
                File b = new File(outPath);
                if (a.getAbsolutePath().equals(b.getAbsolutePath())) notOverwriting = false;
            }
            if (notOverwriting) {
                videoView.setVideoURI(Uri.parse(outPath));
                videoView.start();
            }
        }
    }

    @Override
    protected void onStop() {
        super.onStop();

        guiTimer.purge();
        guiTimer.cancel();
    }

    @Override
    protected void onDestroy() {
        // set static vars to null
        handle = null;

        super.onDestroy();
    }

    Timer guiTimer;
    public ConstraintLayout lytGUI;
    public VideoView videoView;

    public ConstraintLayout lytMain;
    public Button btnWarp;
    public Button btnWatch;

    public ConstraintLayout lytWarp;
    public jEditText boxFileName;
    public Spinner spinWarpType;
    public Switch swtInvert;
    public jEditText boxSeconds;
    public SeekBar seekSeconds;
    public jEditText boxScale;
    public SeekBar seekScale;
    public jEditText boxFramerate;
    public SeekBar seekFramerate;
    public jEditText boxBitrate;
    public SeekBar seekBitrate;
    public Button btnCancel;
    public Button btnStart;


    public ConstraintLayout lytWarping;
    public ProgressBar progWarp;
    public TextView txtFilename;
    public TextView txtWarptype;
    public TextView txtSeconds;
    public TextView txtScale;
    public TextView txtFramerate;
    public TextView txtBitrate;
    public TextView txtTimeleft;
    public Button btnWarpingWatch;
    public Button btnHalt;


    public void setupGUI() {
        lytGUI = findViewById(R.id.lytGUI);
        videoView = findViewById(R.id.videoView);

        lytMain = findViewById(R.id.lytMain);
        btnWarp = findViewById(R.id.btnWarp);
        btnWatch = findViewById(R.id.btnWatch);

        lytWarp = findViewById(R.id.lytWarp);
        boxFileName = findViewById(R.id.boxFileName);
        spinWarpType = findViewById(R.id.spinWarpType);
        swtInvert = findViewById(R.id.swtInvert);
        boxSeconds = findViewById(R.id.boxSeconds);
        seekSeconds = findViewById(R.id.seekSeconds);
        boxScale = findViewById(R.id.boxScale);
        seekScale = findViewById(R.id.seekScale);
        boxFramerate = findViewById(R.id.boxFramerate);
        seekFramerate = findViewById(R.id.seekFramerate);
        boxBitrate = findViewById(R.id.boxBitrate);
        seekBitrate = findViewById(R.id.seekBitrate);
        btnCancel = findViewById(R.id.btnCancel);
        btnStart = findViewById(R.id.btnStart);

        lytWarping = findViewById(R.id.lytWarping);
        txtFilename = findViewById(R.id.txtFilename);
        txtWarptype = findViewById(R.id.txtWarptype);
        txtSeconds = findViewById(R.id.txtSeconds);
        txtScale = findViewById(R.id.txtScale);
        txtFramerate = findViewById(R.id.txtFramerate);
        txtBitrate = findViewById(R.id.txtBitrate);
        txtTimeleft = findViewById(R.id.txtTimeLeft);
        btnWarpingWatch = findViewById(R.id.btnWarpingWatch);
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

        btnWatch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Video.Media.EXTERNAL_CONTENT_URI);
                startActivityForResult(intent, VSL_WATCH);
            }
        });

        // setup spinner
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.warp_modes, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinWarpType.setAdapter(adapter);
        spinWarpType.setOnItemSelectedListener(new Spinner.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView p, View v, int pos, long id) { warpType = pos; }
            @Override public void onNothingSelected(AdapterView p) { }
        });
        if (warpType != -1) spinWarpType.setSelection(warpType);

        // seconds slider
        seekSeconds.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) { boxSeconds.setText(""+(progress/1000f)); }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        });

        // scale slider
        seekScale.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float scale = progress/10000f;
                boxScale.setText(""+(scale));
                updateBoxBitrate();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        });

        // framerate slider
        seekFramerate.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int framerate = 1 + Math.round(progress/10000f*59);
                boxFramerate.setText(""+(framerate));
//                updateBoxBitrate();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        });

        // bitrate slider
        seekBitrate.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float scale = Float.parseFloat(boxScale.getText()+"");
                updateBoxBitrate();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        });

        btnCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                decPath = null;
                lytWarp.setVisibility(View.GONE);
                lytMain.setVisibility(View.VISIBLE);
            }
        });

        btnStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // check that decPath does match encPath
                File a = new File(decPath);
                File b = new File(Environment.getExternalStorageDirectory()+"/"+boxFileName.getText()+".mp4");
                if (a.getAbsolutePath().equals(b.getAbsolutePath())) {
                    handle.obtainMessage(HNDL_TOAST, "Can not overwrite source video, please change Filename.").sendToTarget();
                    return;
                }

                progWarp.setProgress(0);
                startWarpService();

                decPath = null; // gui state based on this
                handle.obtainMessage(HNDL_HIDE_KEYBOARD, lytWarp).sendToTarget();
                lytWarp.setVisibility(View.GONE);
                lytWarping.setVisibility(View.VISIBLE);
            }
        });

        btnWarpingWatch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Video.Media.EXTERNAL_CONTENT_URI);
                startActivityForResult(intent, VSL_WATCH);
            }
        });

        btnHalt.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                try { // instance may become null at any moment
                    WarpService.instance.warper.halt = true;
                } catch (NullPointerException e) {
                    e.printStackTrace();
                }
                lytWarping.setVisibility(View.GONE);
            }
        });

        videoView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                if (lytGUI.getVisibility() == View.VISIBLE)
                    lytGUI.setVisibility(View.GONE);
                else lytGUI.setVisibility(View.VISIBLE);
                return false;
            }
        });

        // which lyt to show?
        guiByState();

        // start GUI heartbeat timer, updates gui elements
        guiTimer = new Timer();
        guiTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                // update time remaining estimate
                if (handle != null) handle.obtainMessage(HNDL_UPDATE_GUI).sendToTarget();
            }
        }, 0, 1000);
    }

    void updateBoxBitrate() {
        // todo: factor in framerate, this means we will need the framerate of the decoded video
        float scale = Float.parseFloat(boxScale.getText()+"");
        boxBitrate.setText(""+(int)(seekBitrate.getProgress()/10000f*decBitrate*1.5f*scale*scale));
    }

    void guiByState() {
        if (WarpService.instance != null) {
            lytMain.setVisibility(View.GONE);
            lytWarp.setVisibility(View.GONE);
            lytWarping.setVisibility(View.VISIBLE);
            updateLytWarping(false);
        } else if (decPath != null) {
            lytMain.setVisibility(View.GONE);
            lytWarp.setVisibility(View.VISIBLE);
            lytWarping.setVisibility(View.GONE);
        } else {
            lytMain.setVisibility(View.VISIBLE);
            lytWarp.setVisibility(View.GONE);
            lytWarping.setVisibility(View.GONE);
        }
    }

    void updateLytWarping(boolean justClock) {
        if ( lytWarping.getVisibility() != View.VISIBLE) return;
        // temp save instance reference in case it goes null on us
        WarpService _instance = WarpService.instance;
        if (_instance == null) return;

        if (true || !justClock) {
            // update warping GUI
            txtFilename.setText(_instance.args.filename);
            txtWarptype.setText(getResources().getStringArray(R.array.warp_modes)[_instance.args.warpType]);
            txtSeconds.setText(String.format("%.4f", (_instance.args.amount / 1000000f)));
            txtScale.setText(String.format("%.4f", _instance.args.scale));
            txtFramerate.setText(""+_instance.args.frameRate);
            txtBitrate.setText(_instance.args.bitrate + "");
        }

        if (_instance.anticipatedVideoDuration != 0) {
            long elapsed = _instance.lastBatchFrameTime - _instance.birth;
            float prog = (float) _instance.encodedLength / _instance.anticipatedVideoDuration;
            if (prog == 0) txtTimeleft.setText("Calculating...");
            else {
                float remaining = (elapsed / prog) - (System.currentTimeMillis() - _instance.birth);
                int hours = (int) Math.floor(remaining / 3600000);
                remaining -= hours * 3600000;
                int min = (int) Math.floor(remaining / 60000);
                remaining -= min * 60000;
                int sec = (int) Math.floor(remaining / 1000);
                txtTimeleft.setText(String.format("%02d", hours) + ":" + String.format("%02d", min) + ":" + String.format("%02d", sec));
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == RESULT_OK) {
            final Uri targetUri = data.getData();
            final String uriPath = Helper.getRealPathFromURI(getBaseContext(), targetUri);
            // ensure path actually exists
            if (!new File(uriPath).exists()) {
                handle.obtainMessage(HNDL_TOAST, "Selected video doesn't actually exist!").sendToTarget();
                return;
            }

            switch (requestCode) {
                case VSL_WARP:
                    Runnable runnable = new Runnable() {
                        @Override
                        public void run() {
                            // halt WarpService.instance.warper if exists, catch nullpointer just in case
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
                            decPath = uriPath;

                            MediaMetadataRetriever mmr = new MediaMetadataRetriever();
                            mmr.setDataSource(decPath);
                            // todo: does this ever return null?
                            decBitrate = (int)Long.parseLong(mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE));
                            float scale = Float.parseFloat(boxScale.getText()+"");
                            boxBitrate.setText(""+(int)(seekBitrate.getProgress()/10000f*decBitrate*1.2f*scale*scale));

                            //show warp window
                            lytWarp.setVisibility(View.VISIBLE);
                        }
                    };

                    //ask user to cancel current warping video if any, or just start
                    if (WarpService.instance != null && WarpService.instance.started && !WarpService.instance.finished)
                        Helper.runOnYes("A video is currently being warped, cancel it?", this, runnable);
                    else runnable.run();

                    break;
                case VSL_WATCH:
                    outPath = uriPath;

                    videoView.setVideoURI(Uri.parse(outPath));
                    videoView.start();
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
        serviceIntent.putExtra("framerateExtra", Integer.parseInt(boxFramerate.getText()+""));
        serviceIntent.putExtra("bitrateExtra", Integer.parseInt(boxBitrate.getText()+""));

        ContextCompat.startForegroundService(this, serviceIntent);

//        updateLytWarping(); // service isn't necessarily started yet, so we need to do this manually
        // update warping GUI
        txtFilename.setText(boxFileName.getText()+".mp4");
        txtWarptype.setText(getResources().getStringArray(R.array.warp_modes)[spinWarpType.getSelectedItemPosition()]);
        txtSeconds.setText(boxSeconds.getText());
        txtScale.setText(boxScale.getText());
        txtBitrate.setText(boxBitrate.getText());
        txtTimeleft.setText("Who knows?");
    }

    void stopWarpService() {
        Intent serviceIntent = new Intent(this, WarpService.class);
        stopService(serviceIntent);
    }

}
