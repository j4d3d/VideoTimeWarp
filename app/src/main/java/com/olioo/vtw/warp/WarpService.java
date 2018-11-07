package com.olioo.vtw.warp;

import android.app.Service;
import android.content.Intent;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Binder;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.speech.tts.TextToSpeech;
import android.support.annotation.Nullable;
import android.util.Log;

import com.olioo.vtw.MainActivity;
import com.olioo.vtw.R;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

public class WarpService extends Service {

    public static final String TAG = "WarpService";

    public static WarpService instance;
    public static boolean started = false;
    public static boolean warpDone = false;

    IBinder mBinder = new LocalBinder();

    WarpArgs args;
    Warper warper;

    @Override
    public void onCreate() {
        super.onCreate();

        instance = this;
        started = true;

        Log.d(TAG, "OnCreate");

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
                args = new WarpArgs();
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
                warper = new Warper(args);
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
                                MainActivity.handle.obtainMessage(MainActivity.HNDL_WARP_DONE, args.encodePath).sendToTarget();
                            }
                        });

                warpDone = false;
            }
        }).start();

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

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        Log.v(TAG, "in onBind");
        return mBinder;
    }

    @Override
    public void onRebind(Intent intent) {
        Log.v(TAG, "in onRebind");
        super.onRebind(intent);
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "OnDestroy()");
        instance = null;
        started = false;
        super.onDestroy();
    }

    public class LocalBinder extends Binder {
        public WarpService getInstance() {
            return WarpService.this;
        }
    }
}
