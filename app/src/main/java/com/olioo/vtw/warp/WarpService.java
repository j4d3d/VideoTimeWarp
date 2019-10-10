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
import com.olioo.vtw.util.Helper;

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

    public long lastBatchFrameTime = 0; // real world time of last encoded batchframe
    public long encodedLength = 0; // video-time of last encoded batchframe
    public long anticipatedVideoDuration = 0; // video duration + amount

    @Override
    public void onCreate() {
        super.onCreate();
        Helper.log(TAG, "OnCreate");
        instance = this;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        args = new WarpArgs();

        args.decodePath = intent.getStringExtra("decodePathExtra");
        args.filename = intent.getStringExtra("filenameExtra");
        args.encodePath = MainActivity.folderPath+args.filename;
        args.warpType = intent.getIntExtra("warpTypeExtra", 0);
        args.invertWarp = intent.getBooleanExtra("invertExtra", false);
        args.amount = intent.getFloatExtra("secondsExtra", 1f); // 1 microsecond lol
        args.trimStart = args.trimEnd = intent.getBooleanExtra("trimEndsExtra", false);
        args.scale = intent.getFloatExtra("scaleExtra", 1f);
        args.frameRate = intent.getIntExtra("framerateExtra", 0);
        args.bitrate = intent.getIntExtra("bitrateExtra", 1);

        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this,
                0, notificationIntent, 0);

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Video Time Warp")
                .setContentText("Warping video: \""+args.filename+"\".")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setLargeIcon(BitmapFactory.decodeResource(this.getResources(),
                        R.mipmap.ic_launcher))
                .setContentIntent(pendingIntent)
                .build();

        startForeground(1, notification);

        // my stuff

        birth = System.currentTimeMillis();
        heartbeat = new Timer();
        heartbeat.schedule(new TimerTask() {
            @Override
            public void run() {
                Helper.log(TAG, "Wizrd: "+((System.currentTimeMillis()-birth)/1000f));
//                if (System.currentTimeMillis() - birth > 5000) stopForegroundService();
            }
        }, 1000, 1000);

        startWarp();

        return START_NOT_STICKY;
    }

    public void startWarp() {
        MainActivity.HandleMessage(MainActivity.HNDL_UPDATE_STATUS, "Preparing to warp!");

        new Thread(new Runnable() {
            @Override
            public void run() {
                started = true;

                // check if folder exists, if not, create it
                File folder = new File(MainActivity.folderPath);
                if (!folder.exists()) folder.mkdir();

                // delete old video at this path if it exists
                new File(args.encodePath).delete();

                try {
                    // warp args that must be profiled
                    args.profileDecodee(args.decodePath);
                    Helper.log("WarpArgs", args.print());

                    // create warper and use it
                    warper = new Warper(args);
                    warper.warp();
                } catch (Exception e) { e.printStackTrace(); }
                finally { finishWarpService(); }


            }
        }).start();
    }

    public void finishWarpService() {
        if (warper != null) warper.release();

        MainActivity.HandleMessage(MainActivity.HNDL_UPDATE_STATUS, "Warping done, scanning file.");

        String warpDonePath = args.encodePath; // gets set to null if aborted

        // mediascan the file afterwards, or delete if nothing encoded
        File file = new File(args.encodePath);
        boolean exists = file.exists();
        if (!exists || encodedLength == 0) {
            if (exists) {
                file.delete();
                MainActivity.HandleMessage(MainActivity.HNDL_TOAST, "Nothing encoded, video deleted.");
            } else MainActivity.HandleMessage(MainActivity.HNDL_TOAST, "Nothing encoded.");
            // send WARP_DONE with null encodePath signifying that file was deleted
            warpDonePath = null;
        } else MediaScannerConnection.scanFile(
                getApplicationContext(),
                new String[]{file.getAbsolutePath()},
                null,
                new MediaScannerConnection.OnScanCompletedListener() {
                    @Override
                    public void onScanCompleted(String path, Uri uri) {
                        Helper.log(TAG, "Scan completed: " + args.encodePath);
                    }
                });

        finished = true;

        // set instance to null early for guiFromState() to read
        instance = null;
        MainActivity.HandleMessage(MainActivity.HNDL_WARP_DONE, warpDonePath);

        stopSelf();
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

        while (!vidSaved) {
            try { Thread.sleep(1000); }
            catch (InterruptedException e) { e.printStackTrace(); }
        }
    }

    @Override
    public void onDestroy() {
        if (!finished)
            finishWarpService();

        Helper.log(TAG, "OnDestroy()");
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
