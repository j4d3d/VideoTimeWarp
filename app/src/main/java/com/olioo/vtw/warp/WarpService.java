package com.olioo.vtw.warp;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Binder;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.speech.tts.TextToSpeech;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.widget.Toast;

import com.olioo.vtw.MainActivity;
import com.olioo.vtw.R;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Timer;
import java.util.TimerTask;

import static com.olioo.vtw.App.CHANNEL_ID;

public class WarpService extends Service {

    public static final String TAG = "WarpService";
    public static WarpService instance = null;

    public boolean started = false;
    public boolean finished = false;
    public WarpArgs args;
    public Warper warper;

    public Timer heartbeat;
    public long birth = -1;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "OnCreate");
        instance = this;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        args = new WarpArgs();

        args.decodePath = intent.getStringExtra("decodePathExtra");
        String filename = intent.getStringExtra("filenameExtra");
        args.encodePath = Environment.getExternalStorageDirectory()+"/"+filename;
        args.warpType = intent.getIntExtra("warpTypeExtra", 0);
        args.invertWarp = intent.getBooleanExtra("invertExtra", false);
        args.amount = intent.getFloatExtra("secondsExtra", 1f); // 1 microsecond lol
        args.scale = intent.getFloatExtra("scaleExtra", 1f);

        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this,
                0, notificationIntent, 0);

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Video Time Warp")
                .setContentText("Warping video: \""+filename+"\".")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentIntent(pendingIntent)
                .build();

        startForeground(1, notification);

        // my stuff

        birth = System.currentTimeMillis();
        heartbeat = new Timer();
        heartbeat.schedule(new TimerTask() {
            @Override
            public void run() {
                Log.d(TAG, "Wizrd: "+((System.currentTimeMillis()-birth)/1000f));
//                if (System.currentTimeMillis() - birth > 5000) stopForegroundService();
            }
        }, 1000, 1000);

        startWarp();

        //do heavy work on a background thread
        //stopSelf();

        return START_NOT_STICKY;
    }

    public void startWarp() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                started = true;

                // for testing purposes, save a small video
//                saveRawVideo();
//                while (!vidSaved) {
//                    try { Thread.sleep(1000); }
//                    catch (InterruptedException e) { e.printStackTrace(); }
//                }

                // delete old video at this path if it exists
                new File(args.encodePath).delete();

                // warp args that must be profiled
                args.profileDecodee(args.decodePath);
//                args.outWidth = args.decWidth - args.decWidth % 16;
//                args.outHeight = args.decHeight - args.decHeight % 16;
                args.bitrate = 1600000;
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

                finished = true;
                stopSelf();
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

    @Override
    public void onDestroy() {
        Log.d(TAG, "OnDestroy()");
        heartbeat.purge();
        heartbeat.cancel();
        instance = null;
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
