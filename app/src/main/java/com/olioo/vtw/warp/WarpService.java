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

public class WarpService extends Service {

    public static final String TAG = "WarpService";

    public static final String ACTION_START_FOREGROUND_SERVICE = "ACTION_START_FOREGROUND_SERVICE";
    public static final String ACTION_STOP_FOREGROUND_SERVICE = "ACTION_STOP_FOREGROUND_SERVICE";
    public static final String ACTION_STOP_WARP = "ACTION_STOP_WARP";

    IBinder mBinder = new LocalBinder();

    public boolean started = false;
    public boolean finished = false;
    public WarpArgs args;
    public Warper warper;

    public Timer heartbeat;
    public long birth = -1;

    @Override
    public void onCreate() {
        super.onCreate();
        if (birth != -1) Log.d(TAG, "service wtf");
        birth = System.currentTimeMillis();
        heartbeat = new Timer();
        heartbeat.schedule(new TimerTask() {
            @Override
            public void run() {
                Log.d(TAG, "Wizrd: "+System.currentTimeMillis());
//                if (System.currentTimeMillis() - birth > 5000) stopForegroundService();
            }
        }, 1000, 1000);

        startWarp();

        Log.d(TAG, "OnCreate");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if(intent != null)
        {
            String action = intent.getAction();

            switch (action) {
                case ACTION_START_FOREGROUND_SERVICE:
                    startForegroundService();
                    Toast.makeText(getApplicationContext(), "Foreground service is started.", Toast.LENGTH_LONG).show();
                    break;
                case ACTION_STOP_FOREGROUND_SERVICE:
                    stopForegroundService();
                    Toast.makeText(getApplicationContext(), "Foreground service is stopped.", Toast.LENGTH_LONG).show();
                    break;
                case ACTION_STOP_WARP:
                    Toast.makeText(getApplicationContext(), "You click Play button.", Toast.LENGTH_LONG).show();
                    warper.halt = true;
                    break;
            }
        }
        return super.onStartCommand(intent, flags, startId);
    }

    /* Used to build and start foreground service. */
    private void startForegroundService()
    {
        Log.d(TAG, "Start foreground service.");

        // Create notification default intent.
        Intent intent = new Intent();
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, 0);

        // Create notification builder.
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this);

        // Make notification show big text.
        NotificationCompat.BigTextStyle bigTextStyle = new NotificationCompat.BigTextStyle();
        bigTextStyle.setBigContentTitle("Time-Warping a Video.");
        bigTextStyle.bigText("This notification will be removed when Video Time Warp is finished working. Hit stop to end the video prematurely.");
        // Set big text style.
        builder.setStyle(bigTextStyle);

        builder.setWhen(System.currentTimeMillis());
        builder.setSmallIcon(R.mipmap.ic_launcher);
        Bitmap largeIconBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.ic_launcher_foreground);
        builder.setLargeIcon(largeIconBitmap);
        // Make the notification max priority.
        builder.setPriority(Notification.PRIORITY_MAX);
        // Make head-up notification.
//        builder.setFullScreenIntent(pendingIntent, true);

        // Add Stop button intent in notification.
        Intent stopIntent = new Intent(this, WarpService.class);
        stopIntent.setAction(ACTION_STOP_WARP);
        PendingIntent pendingPlayIntent = PendingIntent.getService(this, 0, stopIntent, 0);
        NotificationCompat.Action stopAction = new NotificationCompat.Action(android.R.drawable.ic_menu_close_clear_cancel, "Stop", pendingPlayIntent);
        builder.addAction(stopAction);

        // Build the notification.
        Notification notification = builder.build();

        // Start foreground service.
        startForeground(1, notification);
    }

    private void stopForegroundService()
    {
        Log.d(TAG, "Stop foreground service.");

        // Stop foreground service and remove the notification.
        stopForeground(true);

        // Stop the foreground service.
        stopSelf();
    }

    public void startWarp() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                started = true;

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

                finished = true;

                stopForegroundService();
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
        heartbeat.purge();
        heartbeat.cancel();
        super.onDestroy();
    }



    public class LocalBinder extends Binder {
        public WarpService getInstance() {
            return WarpService.this;
        }
    }
}