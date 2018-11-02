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
    public final boolean VERBOSE = false;
    public final boolean WORK_AROUND_BUGS = true;
    public final int TIMEOUT_USEC = 10000;

    public static WarpArgs args = MainActivity.args;
    public static Warper self;

    MediaMetadataRetriever metadataRetriever;
    MediaExtractor extractor;
    MediaCodec decoder, encoder;
    int decoderTrackIndex = -1;
    int encoderTrackIndex = -1;
    MediaFormat inputFormat, outputFormat;
    InputSurface inputSurface;
    OutputSurface outputSurface;
    MediaMuxer muxer;
    boolean muxerStarted;

    public boolean decoderInputDone, decoderOutputDone;
    public boolean encoderInputDone, encoderOutputDone;

    // warper logic
    List<Long> frameTimes = new ArrayList<Long>();
    int currentFrame = 0;
    // batch stuff
    boolean encodingBatch = false;
    long drainOlderThan = -1;
    int batchEncodeProg = 0, batchFloor = 0;
    public int batchSize = 16;

    public Warper() {
        self = this;

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
            extractor.seekTo(0, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
            if (extractor.getSampleTime() != frameTimes.get(0))
                throw new RuntimeException("Failed at seeking to beginning of video. Seeked to: "+extractor.getSampleTime());

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

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void warp() {
        // codec and state vars
        ByteBuffer[] decoderInputBuffers = decoder.getInputBuffers();
        ByteBuffer[] encoderOutputBuffers = encoder.getOutputBuffers();
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        int inputChunk = 0;
        int outputCount = 0;
        boolean outputDone = false;
        boolean inputDone = false;
        boolean decoderDone = false;

        boolean letDecoderEnd = true; //todo: false
        boolean decoderReachedEnd = false;


        while (!outputDone) {
            if (VERBOSE) Log.d(TAG, "edit loop");
            // Feed more data to the decoder.
            if (!inputDone) {
                int inputBufIndex = decoder.dequeueInputBuffer(TIMEOUT_USEC);
                if (inputBufIndex >= 0) {
                    ByteBuffer inputBuf = decoderInputBuffers[inputBufIndex];
                    if (Build.VERSION.SDK_INT >= 21) inputBuf = decoder.getInputBuffer(inputBufIndex);

                    int chunkSize = extractor.readSampleData(inputBuf, 0);
                    if (chunkSize < 0) {
                        if (letDecoderEnd) {
                            // End of stream -- send empty frame with EOS flag set.
                            decoder.queueInputBuffer(inputBufIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            decoderInputDone = true;
                            if (VERBOSE) Log.d(TAG, "sent input EOS");
                        } else decoderReachedEnd = true;
                    } else {
                        if (extractor.getSampleTrackIndex() != decoderTrackIndex) {
                            Log.w(TAG, "WEIRD: got sample from track " + extractor.getSampleTrackIndex() + ", expected " + decoderTrackIndex);
                        }

                        decoder.queueInputBuffer(inputBufIndex, 0, chunkSize, extractor.getSampleTime(), 0 /*flags*/);
                        if (VERBOSE) Log.d(TAG, "submitted a frame " + inputChunk + " to dec, size=" + chunkSize);

                        inputChunk++;
                        extractor.advance();
                    }
                } else if (VERBOSE) Log.d(TAG, "input buffer not available");
            }

            // Assume output is available.  Loop until both assumptions are false.
            boolean decoderOutputAvailable = !decoderDone;
            boolean encoderOutputAvailable = true;
            while (decoderOutputAvailable || encoderOutputAvailable) {

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
                    // Write the data to the output "file".
                    if (info.size != 0) {
                        encodedData.position(info.offset);
                        encodedData.limit(info.offset + info.size);
                        //outputData.addChunk(encodedData, info.flags, info.presentationTimeUs);
                        muxer.writeSampleData(encoderTrackIndex, encodedData, info);
                        outputCount++; //Log.d(TAG, "outputCount: "+outputCount);
                        if (VERBOSE) Log.d(TAG, "encoder output " + info.size + " bytes");
                    }
                    outputDone = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                    encoder.releaseOutputBuffer(encoderStatus, false);
                }

                if (encoderStatus != MediaCodec.INFO_TRY_AGAIN_LATER) {
                    // Continue attempts to drain output.
                    continue;
                }

                // Encoder is drained, check to see if we've got a new frame of output from
                // the decoder.  (The output is going to a Surface, rather than a ByteBuffer,
                // but we still get information through BufferInfo.)
                if (!decoderDone) {
                    if (encodingBatch) {
                        // Send it to the encoder.
                        int batchFrame = batchFloor + batchEncodeProg;
                        long batchTime = batchFrame * 1000000000L / Warper.args.frameRate;
                        Log.d(TAG, "Encoding batch at frame: "+(batchFrame)+", "+batchTime);

                        outputSurface.drawImage(batchEncodeProg);
                        inputSurface.setPresentationTime(batchTime);
                        if (VERBOSE) Log.d(TAG, "swapBuffers");
                        inputSurface.swapBuffers();
                        batchEncodeProg++;
                        if (batchEncodeProg == batchSize) {
                            batchFloor += batchSize;
                            encodingBatch = false;
                            // seek
                            long time = batchFloor * 1000000L / Warper.args.frameRate;
                            Log.d(TAG, "Seeking to time: "+batchFloor+", at time: "+time);
                            extractor.seekTo(time, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
//                            while (extractor.getSampleTime() < time) extractor.advance();
                            long etime = extractor.getSampleTime();
                            drainOlderThan = etime; // skip and drain frames left in codec from last batch
                            for (int i=0; i<frameTimes.size(); i++) {
                                if (etime == frameTimes.get(i)) {
                                    currentFrame = i;
                                    Log.d(TAG, "sought batchFloor: " + batchFloor + ", currentFrame: " + currentFrame);
                                    break;
                                }
                            }
                        }
                        continue;
                    }

//                    11-02 00:39:41.374 30372-31020/com.olioo.vtw D/Warper: currentFrame: 239, ptime: 9966666
//                    11-02 00:39:41.454 30372-31020/com.olioo.vtw D/Warper: currentFrame: 240, ptime: 9966666

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
                        // The ByteBuffers are null references, but we still get a nonzero
                        // size for the decoded data.
                        boolean doRender = (info.size != 0);
                        // As soon as we call releaseOutputBuffer, the buffer will be forwarded
                        // to SurfaceTexture to convert to a texture.  The API doesn't
                        // guarantee that the texture will be available before the call
                        // returns, so we need to wait for the onFrameAvailable callback to
                        // fire.  If we don't wait, we risk rendering from the previous frame.
                        decoder.releaseOutputBuffer(decoderStatus, doRender);
                        if (doRender) {
                            Log.d(TAG, "currentFrame: "+currentFrame+", ptime: "+info.presentationTimeUs);

                            // This waits for the image and renders it after it arrives.
//                            if (VERBOSE) Log.d(TAG, "awaiting frame");
                            outputSurface.awaitNewImage();

                            //todo: could just drain these frames upon signaling encodingBatch
                            //todo: also might not need to awaitNewImage()
                            if (drainOlderThan > 0) {
                                if (info.presentationTimeUs > drainOlderThan) continue;
                                else drainOlderThan = -1;
                            }

                            float bfloorTime = Math.max((batchFloor-1), 0) * (1000000f / Warper.args.frameRate);
                            float lframeTime = frameTimes.get(Math.max(0, currentFrame-1));
                            float nframeTime = frameTimes.get(Math.min(currentFrame+1, frameTimes.size()-1));
                            float cframeTime = frameTimes.get(currentFrame);

                            if (cframeTime >= bfloorTime)
                                for (int i=0; i<batchSize; i++) {
                                    float bframeTime = (batchFloor + i - 1) * 1000000f / Warper.args.frameRate;
                                    if (cframeTime < bframeTime) continue;
                                    boolean clear = lframeTime < bframeTime && cframeTime >= bframeTime;
                                    if (clear)
                                        Log.d(TAG, "Clearing bframe: "+(batchFloor+i)+" @ bftime: "+bframeTime);
                                    outputSurface.drawOnBatchImage(
                                        i,
                                        lframeTime - bframeTime,
                                        nframeTime - bframeTime,
                                        cframeTime - bframeTime,
                                        clear);
                                }

                            // set mode to draw batch frames and seek extractor
                            float lbframeTime = (batchFloor + batchSize - 1) * 1000000L / Warper.args.frameRate;
                            if (frameTimes.get(currentFrame) >= lbframeTime + Warper.args.amount) {
                                encodingBatch = true;
                                batchEncodeProg = 0;
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
        if (inputChunk != outputCount) {
            throw new RuntimeException("frame lost: " + inputChunk + " in, " +
                    outputCount + " out");
        }
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
