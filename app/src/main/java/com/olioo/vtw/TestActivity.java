package com.olioo.vtw;


import android.os.Environment;
import android.view.View;

import com.olioo.vtw.util.Helper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

public class TestActivity extends MainActivity {


    @Override
    protected void onStart() {
        super.onStart();

        btnWarp.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startWarping();
            }
        });
    }

    public void startWarping() {
        //save raw video
        String path = Environment.getExternalStorageDirectory()+"/test.mp4";
        new File(path).delete();

        try {
            InputStream in = getResources().openRawResource(R.raw.video_480x360_mp4_h264_500kbps_30fps_aac_stereo_128kbps_44100hz);
            FileOutputStream out = new FileOutputStream(path);
            byte[] buff = new byte[1024];
            int read = 0;

            while ((read = in.read(buff)) > 0)
                out.write(buff, 0, read);

            in.close();
            out.close();

            Helper.log(TAG, "File created: "+path);
        } catch (Exception e) { e.printStackTrace(); }

        // simulate onActivityResult for VSL_WARP with test video's path
        showLytWarp(path);
    }
}
