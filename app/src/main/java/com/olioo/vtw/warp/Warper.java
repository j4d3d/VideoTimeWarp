package com.olioo.vtw.warp;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.media.MediaMuxer;
import android.opengl.GLES20;
import android.os.Build;
import android.util.Log;

import com.olioo.vtw.MainActivity;
import com.olioo.vtw.bigflake.AndroidTestCase;
import com.olioo.vtw.bigflake.InputSurface;
import com.olioo.vtw.bigflake.OutputSurface;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Warper extends AndroidTestCase {

    public final String TAG = "Warper";
    public final boolean VERBOSE = true;
    public final boolean WORK_AROUND_BUGS = true;
    public final int TIMEOUT_USEC = 10000;

    public static Warper self;
    public static WarpArgs args;

    List<Long> frameTimes = new ArrayList<Long>();

    // warp components
    MediaMetadataRetriever metadataRetriever;
    MediaExtractor extractor;
    MediaCodec decoder, encoder;
    MediaMuxer muxer;
    // warp component stuff
    ByteBuffer[] decoderInputBuffers, encoderOutputBuffers;
    MediaCodec.BufferInfo info;
    int decoderTrackIndex = -1;
    int encoderTrackIndex = -1;
    MediaFormat inputFormat, outputFormat;
    InputSurface inputSurface;
    OutputSurface outputSurface;
    // warp component status / info
    boolean muxerStarted;

    // warper logic
    public boolean halt = false;
    boolean outputDone = false;
    boolean extractorReachedEnd = false;
    boolean encoderOutputAvailable, decoderOutputAvailable;
    int currentFrame = 0;
    float startOffset, endPad; // for trimEnds setting
    // batch stuff
    boolean encodingBatch = false;
    boolean justOneBatch = false; // set to true if only one batch will be rendered
    long drainOlderThan = -1;
    int batchEncodeProg = 0, batchFloor = 0;
    public int batchSize = 16;

    public Warper(WarpArgs args) {
        self = this;
        Warper.args = args;

        // padding frame variables, dependent on swtTrimEnds in lytWarp
        startOffset = (args.trimStart) ? args.amount : 0f;
        endPad = (args.trimEnd) ? 0f : args.amount;

        try {
            extractor = new MediaExtractor();
            extractor.setDataSource(args.decodePath);  //  IOException
            for (int i = 0; i < extractor.getTrackCount(); i++) {
                MediaFormat inputFormat = extractor.getTrackFormat(i);
                String mime = inputFormat.getString(MediaFormat.KEY_MIME);
                if (mime.startsWith("video/")) {
                    decoderTrackIndex = i;
                    extractor.selectTrack(i);
                    break;
                }
            }

            // get frametimes
            do {
                long sampleTime = extractor.getSampleTime();
                if (sampleTime != -1) frameTimes.add(extractor.getSampleTime());
            } while (extractor.advance());
            Collections.sort(frameTimes);
            frameTimes.add(-1L);
            extractor.seekTo(0, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
            if (extractor.getSampleTime() != frameTimes.get(0))
                throw new RuntimeException("Failed at seeking to beginning of video. Seeked to: "+extractor.getSampleTime());
            WarpService.instance.anticipatedVideoDuration = (long)(frameTimes.get(frameTimes.size()-2) + args.amount);

            // setup formats
            inputFormat = extractor.getTrackFormat(decoderTrackIndex);

            outputFormat = MediaFormat.createVideoFormat(args.MIME_TYPE, args.outWidth, args.outHeight);
            outputFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
            outputFormat.setInteger(MediaFormat.KEY_BIT_RATE, args.bitrate);
            outputFormat.setInteger(MediaFormat.KEY_FRAME_RATE, args.frameRate);
            outputFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, args.iframeInterval);
            //unrecommended by bigflake, but seemingly necessary. at least for my samsung s5 active.
            //outputFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 0);

            // config and start encoder
            encoder = MediaCodec.createEncoderByType(args.MIME_TYPE);
            encoder.configure(outputFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            inputSurface = new InputSurface(encoder.createInputSurface());
            inputSurface.makeCurrent();
            encoder.start();

            // config and start decoder
            decoder = MediaCodec.createDecoderByType(args.MIME_TYPE);
            outputSurface = new OutputSurface();
            decoder.configure(inputFormat, outputSurface.getSurface(), null, 0);
            decoder.start();

            // codec vars
            decoderInputBuffers = decoder.getInputBuffers();
            encoderOutputBuffers = encoder.getOutputBuffers();
            info = new MediaCodec.BufferInfo();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void warp() {
        while (!outputDone) {
            if (VERBOSE) Log.d(TAG, "edit loop");
            // Feed more data to the decoder.
            if (!extractorReachedEnd || halt) {
                int inputBufIndex = decoder.dequeueInputBuffer(TIMEOUT_USEC);
                if (inputBufIndex >= 0) {
                    ByteBuffer inputBuf = decoderInputBuffers[inputBufIndex];
                    if (Build.VERSION.SDK_INT >= 21) inputBuf = decoder.getInputBuffer(inputBufIndex);

                    int chunkSize = extractor.readSampleData(inputBuf, 0);
                    boolean end = halt;
                    if (extractor.getSampleTime() == frameTimes.get(frameTimes.size()-2)) {
                        extractorReachedEnd = true;
                        encodingBatch = true;
                    }

                    if (end) {
                        // End of stream -- send empty frame with EOS flag set.
                        decoder.queueInputBuffer(inputBufIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        if (VERBOSE) Log.d(TAG, "sent input EOS");
                        encodingBatch = true;

                    } else {
                        if (extractor.getSampleTrackIndex() != decoderTrackIndex)
                            Log.w(TAG, "WEIRD: got sample from track " + extractor.getSampleTrackIndex() + ", expected " + decoderTrackIndex);
                        decoder.queueInputBuffer(inputBufIndex, 0, chunkSize, extractor.getSampleTime(), 0 /*flags*/);
                        extractor.advance();
                    }
                } else if (VERBOSE) Log.d(TAG, "input buffer not available");
            }

            // Assume output is available.  Loop until both assumptions are false.
            decoderOutputAvailable = encoderOutputAvailable = true;
            while (decoderOutputAvailable || encoderOutputAvailable) {

                // Drain encoder
                while (drainEncoder());

                // Encode a batch frame and continue if encodingBatch
                if (!halt && encodingBatch) {
                    encodeBatchFrame();
                    continue;
                }

                // Drain decoder, drawing applicable decoded frames on batchFrames

                int decoderStatus = decoder.dequeueOutputBuffer(info, TIMEOUT_USEC);
                if (decoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    // no output available yet
                    if (VERBOSE) Log.d(TAG, "no output from decoder available");
                    decoderOutputAvailable = false;
                } else if (decoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                    //decoderOutputBuffers = decoder.getOutputBuffers();
                    if (VERBOSE) Log.d(TAG, "decoder output buffers changed (we don't care)");
                } else if (decoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    // expected before first buffer of data
                    MediaFormat newFormat = decoder.getOutputFormat();
                    if (VERBOSE) Log.d(TAG, "decoder output format changed: " + newFormat);
                } else if (decoderStatus < 0) {
                    throw new RuntimeException("unexpected result from decoder.dequeueOutputBuffer: "+decoderStatus);
                } else { // decoderStatus >= 0
                    if (VERBOSE) Log.d(TAG, "surface decoder given buffer "
                            + decoderStatus + " (size=" + info.size + ")");

                    boolean doRender = (info.size != 0);
                    decoder.releaseOutputBuffer(decoderStatus, doRender);
                    if (doRender) {
                        if (VERBOSE) Log.d(TAG, "currentFrame: "+currentFrame+", ptime: "+info.presentationTimeUs);

                        // track progress
                        if (MainActivity.handle != null)
                            try {
                                float prog = (float)info.presentationTimeUs / frameTimes.get(frameTimes.size()-2);
                                MainActivity.handle.obtainMessage(MainActivity.HNDL_UPDATE_PROGRESS, (int)(10000*prog)).sendToTarget();
                            } catch (NullPointerException e) {
                                if (VERBOSE) Log.d(TAG, "MainActivity.handle became null as we sent it a message.");
                            }

                        // This waits for the image and renders it after it arrives.
                        outputSurface.awaitNewImage();

                        // Skip frames left over from last batch
                        if (drainOlderThan > 0) {
                            if (info.presentationTimeUs > drainOlderThan) continue;
                            else drainOlderThan = -1;
                        }

                        // ~~~ ONFRAME ~~~

                        float bceilTime = (batchFloor + batchSize - 1) * 1000000L / args.frameRate + startOffset;
                        float lframeTime = frameTimes.get(Math.max(0, currentFrame-1));
                        float nframeTime = frameTimes.get(Math.min(currentFrame+1, frameTimes.size()-2));
                        float cframeTime = frameTimes.get(currentFrame);

                        if (lframeTime <= bceilTime)
                            for (int i=0; i<batchSize; i++) {
                                float bframeTime = (batchFloor + i) * 1000000f / args.frameRate + startOffset;
                                float pframeTime = bframeTime - args.amount;
                                if (lframeTime > bframeTime) continue; // todo: || nframeTime < pframeTime, or something?
                                float clearTime = Math.max(pframeTime, frameTimes.get(0));
                                boolean clear = cframeTime <= clearTime && nframeTime > clearTime;
                                if (clear)
                                    if (VERBOSE) Log.d(TAG, "Clearing bframe: "+(batchFloor+i)+" @ bftime: "+bframeTime+" & cftime: "+cframeTime+" & ptime: "+pframeTime);
                                outputSurface.drawOnBatchImage(
                                    i,
                                    lframeTime - pframeTime,
                                    nframeTime - pframeTime,
                                    cframeTime - pframeTime,
                                    clear);
                            }

                        // set mode to draw batch frames and seek extractor
                        if (lframeTime >= bceilTime) {
                            encodingBatch = true;
                        } else currentFrame++;
                    }

                    if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        // forward decoder EOS to encoder
                        if (VERBOSE) Log.d(TAG, "signaling input EOS");
                        if (WORK_AROUND_BUGS) {
                            // Bail early, possibly dropping a frame.
                            return;
                        } else {
                            encoder.signalEndOfInputStream();
                        }
                    }
                }
            }
        }
    }

    public void encodeBatchFrame() {
        // get frame # and time
        int batchFrame = batchFloor + batchEncodeProg;
        long batchTime = batchFrame * 1000000000L / args.frameRate;
        // halt warper if we've made it far enough
        if (!justOneBatch && batchTime / 1000 > frameTimes.get(frameTimes.size() - 2) - startOffset + endPad ) {
            // if nothing encoded yet, set to just encode what we have in batch frames.
            // that'll teach em to trimEnds with warp amounts greater than the video duration...
            if (batchFloor == 0) justOneBatch = true;
            else {
                halt = true;
                encodingBatch = false;
                return;
            }
        }

        if (VERBOSE) Log.d(TAG, "Encoding batch at frame: "+(batchFrame)+", "+batchTime);
        outputSurface.drawImage(batchEncodeProg);
        inputSurface.setPresentationTime(batchTime);
        if (VERBOSE) Log.d(TAG, "swapBuffers");
        inputSurface.swapBuffers();
        batchEncodeProg++;
        // est. time remaining variables
        WarpService.instance.encodedLength = batchTime / 1000;
        WarpService.instance.lastBatchFrameTime = System.currentTimeMillis();

        if (batchEncodeProg == batchSize) {
            // end of justOneBatch? halt!
            if (justOneBatch) {
                halt = true;
                encodingBatch = false;
                MainActivity.handle.obtainMessage(
                    MainActivity.HNDL_TOAST,
                    "Try using a shorter warp amount, or disabling 'Trim Start/End'."
                ).sendToTarget();
                return;
            }

            // increment bfloor and reset batch controller state
            batchFloor += batchSize;
            encodingBatch = false;
            batchEncodeProg = 0;
            // seek
            extractorReachedEnd = false;
            long time = Math.max(0, (long)(batchFloor * 1000000L / args.frameRate - args.amount + startOffset));
            if (VERBOSE) Log.d(TAG, "Seeking to time: "+batchFloor+", at time: "+time);
            extractor.seekTo(time, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
            // what frame did we land on
            long etime = extractor.getSampleTime();
            drainOlderThan = etime; // tell to skip frames that are in
            for (int i=0; i<frameTimes.size(); i++) {
                if (etime == frameTimes.get(i)) {
                    currentFrame = i;
                    if (VERBOSE) Log.d(TAG, "sought batchFloor: " + batchFloor + ", currentFrame: " + currentFrame);
                    break;
                }
            }
        }
    };

    /** drainEncoder boilerplate
     * @return draining not done? */
    public boolean drainEncoder() {
        // Start by draining any pending output from the encoder.  It's important to
        // do this before we try to stuff any more data in.
        int encoderStatus = encoder.dequeueOutputBuffer(info, TIMEOUT_USEC);
        if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
            // no output available yet
            if (VERBOSE) Log.d(TAG, "no output from encoder available");
            encoderOutputAvailable = false;
        } else if (encoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
            encoderOutputBuffers = encoder.getOutputBuffers();
            if (VERBOSE) Log.d(TAG, "encoder output buffers changed");
        } else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
            MediaFormat newFormat = encoder.getOutputFormat();
            if (VERBOSE) Log.d(TAG, "encoder output format changed: " + newFormat);

            //start muxer now that we have the thing
            if (muxerStarted) throw new RuntimeException("format changed twice");
            try {
                muxer = new MediaMuxer(args.encodePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            } catch (IOException ioe) {
                throw new RuntimeException("MediaMuxer creation failed, outputPath=\"" + args.encodePath + "\"", ioe);
            }
            encoderTrackIndex = muxer.addTrack(newFormat);
            muxer.start();
            muxerStarted = true;

        } else if (encoderStatus < 0) {
            fail("unexpected result from encoder.dequeueOutputBuffer: " + encoderStatus);
        } else { // encoderStatus >= 0

            ByteBuffer encodedData = encoderOutputBuffers[encoderStatus];
            if (encodedData == null) {
                fail("encoderOutputBuffer " + encoderStatus + " was null");
            }
            // Write the data to the output file.
            if (info.size != 0) {
                encodedData.position(info.offset);
                encodedData.limit(info.offset + info.size);
                muxer.writeSampleData(encoderTrackIndex, encodedData, info);
                if (VERBOSE) Log.d(TAG, "encoder output " + info.size + " bytes");
            }
            outputDone = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
            encoder.releaseOutputBuffer(encoderStatus, false);
        }

        return (encoderStatus != MediaCodec.INFO_TRY_AGAIN_LATER);
    }

    public void release() {
        if (encoder != null) {
            encoder.stop();
            encoder.release();
            encoder = null;
        } if (decoder != null) {
            decoder.stop();
            decoder.release();
            decoder = null;
        } if (outputSurface != null) {
            //outputSurface.mTextureRender.unloadTexture();
            outputSurface.release();
            outputSurface = null;
        } if (inputSurface != null) {
            inputSurface.release();
            inputSurface = null;
        } if (muxer != null) {
            if (muxerStarted) muxer.stop();
            muxer.release();
            muxer = null;
        }
    }

}
