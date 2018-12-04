package com.olioo.vtw.util;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;

/**
 * Created by olioo on 7/21/2016.
 * Size matters... Really wish mediacodec could just tell me
 * these things. Whats the point of not having a grilfriend
 * when I have all the same problems? So fuck it.
 */
public class MediaCodecProfiler {

    public static String MIME_TYPE = "video/avc";

    public int minSize;

    public MediaCodecProfiler() {

        int floor = 1;
        int ceil = 1;
        while (!validSize(ceil*=2)) floor = ceil;
        //its between ceil n floor
        while (ceil - floor > 16) {
            int mid = (floor + ceil) / 2;
            if (validSize(mid)) ceil = mid;
            else floor = mid;
        } //minSize is approximately ceil
        minSize = ceil;


//        while (!validSize(minSize+=16)) ;//Helper.log("trySize", minSize+""); //could make this much more efficient if its too much
//        minSize += 8;
//        Helper.log("minSize", minSize+"");

//        Helper.log("vbrate", "Max Int\t"+Integer.MAX_VALUE);
//        for (int i = 0; i < 100; i++) {
//            int rate = 1 << i;
//            Helper.log("vbrate", i+"\t"+rate+"\t"+validBitrate(rate));
//        }

    }

    public boolean validSize(int size) {
        MediaCodec codec = null;

        try {
            MediaFormat format = MediaFormat.createVideoFormat(MIME_TYPE, size, size);
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible);
            format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
            format.setInteger(MediaFormat.KEY_BIT_RATE, 8000000);
            format.setInteger(MediaFormat.KEY_FRAME_RATE, 30);

            codec = MediaCodec.createEncoderByType(MIME_TYPE);
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            codec.start();
            codec.stop();

            codec.release();
            return true;
        } catch (Throwable th) {
            codec.stop();
            codec.release();
            return false;
        }
    }

    public boolean validBitrate(int bitrate) {
        MediaCodec codec = null;

        try {
            MediaFormat format = MediaFormat.createVideoFormat(MIME_TYPE, 640, 640);
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible);
            format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
            format.setInteger(MediaFormat.KEY_BIT_RATE, bitrate);
            format.setInteger(MediaFormat.KEY_FRAME_RATE, 30);

            codec = MediaCodec.createEncoderByType(MIME_TYPE);
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            codec.start();
            codec.stop();
            codec.release();
            return true;
        } catch (Throwable th) {
            //th.printStackTrace();
            codec.stop();
            codec.release();
            return false;
        }
    }

}
