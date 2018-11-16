package com.olioo.vtw.warp;

import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.util.Log;

import com.olioo.vtw.MainActivity;

import java.io.IOException;

/**
 * Created by olioo on 7/9/2016.
 */
public class WarpArgs {

    public String MIME_TYPE = "video/avc"; //for encoding, I think muxer only takes this type
    public String decodePath;
    public String encodePath;

    //decodee stats
    public int decWidth, decHeight; //decoded video
    public long decBitrate;
    public long vidDuration;
    public int numFrames;
    public float decFrameRate;
    //decodee dependent
    public float minScale;

    //ui
    //public WarpFunction function;
    public String filename;
    public int warpType;
    public boolean invertWarp;
    public float amount;
    public int frameRate;
    public float speed;
    public long startTimeUs, stopTimeUs;
    public boolean trimStart, trimEnd;
    public float scale;
    public int outWidth, outHeight; //output
    public int bitrate;
    public int iframeInterval = 1;

    //ui work vars
    public float maxTimeFactor;
    public long outVidDuration;

    public WarpArgs(){
//        scale = 0.25f;
//        amount = maxRAMFrames / 2;
        //function = WarpFunction.LeftToRight(this);

        //subject to change based on video dimensions / warp mode
//        bitrate = 8 * 1024 * 512;
//        iframeInterval = 1;
//        frameRate = 30;
    }

    public void profileDecodee(String decodePath) {
        this.decodePath = decodePath;

        //estimate framerate and get duration with MediaExtractor
        MediaExtractor extractor = new MediaExtractor();
        try { extractor.setDataSource(decodePath); }
        catch (IOException e) {e.printStackTrace();}
        int numTracks = extractor.getTrackCount();
        for (int i = 0; i < numTracks; ++i) {
            MediaFormat format = extractor.getTrackFormat(i);
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (mime.startsWith("video/")) {
                extractor.selectTrack(i);
                break;
            }
        }

        //get avg frametime
        int samples = 100;
        long lt = extractor.getSampleTime();
        for (int i = 0; i < samples; i++) {
            extractor.advance();
            long st = extractor.getSampleTime();
//            Log.d("MAURO", st+"\t"+(st-lt));
            lt = st;
        } decFrameRate = 1000000 / lt * samples;
        maxTimeFactor = 60 / decFrameRate;

        //get last sample time
//        extractor.seekTo(Long.MAX_VALUE, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
//        vidDuration = extractor.getSampleTime();
//        startTimeUs = 0;
//        stopTimeUs = vidDuration;

        extractor.release();
        //just use MediaMetadataRetriever

        MediaMetadataRetriever mmr = new MediaMetadataRetriever();
        mmr.setDataSource(decodePath);
        //use mmr if duration is still 0
        if (true || vidDuration == 0) stopTimeUs = vidDuration =
                Long.parseLong(mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)) * 1000;

        decBitrate = (int)Long.parseLong(mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE));
        //height and width
        decWidth = Integer.parseInt(mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));
        decHeight = Integer.parseInt(mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));
        //calc min scale for mediaCodec
        int minDim = decWidth; if (decHeight < decWidth) minDim = decHeight;
        minScale = (float) 128 / minDim; //todo: profiler.minSize
        //portrait or landscape?
        int vidOrientation = Integer.parseInt(mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION));
        if (vidOrientation % 180 != 0) {
            int swap = decWidth;
            decWidth = decHeight;
            decHeight = swap;
        }

        outWidth = (int)(scale * decWidth); outWidth -= outWidth % 16;
        outHeight = (int)(scale * decHeight); outHeight -= outHeight % 16;

    }



    public String print() {
        String o = "!@#$%^&*()(*&^%$#@!@#$%^&*()(*&^%$#@!@#$%^&*(  WarpParams yo  )(*&^%$#@!@#$%^&*()(*&^%$#@!@#$%^&*()(*&^%$#@!";
        o += "\ndecodee MIME_TYPE:\t"+MIME_TYPE;
        o += "\ndecodePath:\t"+decodePath;
        //vid paramses
        o += "\ndecWidth:	" + decWidth + "\tdecHeight: " + decHeight;

        o += "\nduration: " + vidDuration;// + "\tnumFrames:\t" + numFrames;
        //warp paramses
        o += "\nscale:\t" + scale + "\tminScale:\t"+ minScale;
        o += "\nstartTimeUs:\t" + startTimeUs + "\tstopTimeUs:\t"+ stopTimeUs;
        o += "\namount:\t" + amount;// + "\tfunction:\t"+ function;
        o += "\nbitrate\t:" + bitrate + "\tiframeInt:\t"+ iframeInterval;
        o += "\nspeed\t:" + speed + "\tframeRate:\t"+ frameRate;
        //out vid paramses
        o += "\nencodePath:\t" + encodePath;
        o += "\noutWidth:	" + outWidth + "\toutHeight: " + outHeight;
        return o;
    };

}
