/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.olioo.vtw.bigflake;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;
import android.graphics.SurfaceTexture;
import android.opengl.GLES10;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.util.Log;

import com.olioo.vtw.warp.WarpArgs;
import com.olioo.vtw.warp.WarpService;
import com.olioo.vtw.warp.Warper;

/**
 * Code for rendering a texture onto a surface using OpenGL ES 2.0.
 */
public class TextureRender {

    private static final String TAG = "TextureRender";
    private static final int FLOAT_SIZE_BYTES = 4;
    private static final int TRIANGLE_VERTICES_DATA_STRIDE_BYTES = 5 * FLOAT_SIZE_BYTES;
    private static final int TRIANGLE_VERTICES_DATA_POS_OFFSET = 0;
    private static final int TRIANGLE_VERTICES_DATA_UV_OFFSET = 3;
    private final float[] mTriangleVerticesData = {
        // X, Y, Z, U, V
        -1.0f, -1.0f, 0, 0.f, 0.f,
        1.0f, -1.0f, 0, 1.f, 0.f,
        -1.0f,  1.0f, 0, 0.f, 1.f,
        1.0f,  1.0f, 0, 1.f, 1.f,
    };
    private FloatBuffer mTriangleVertices;
    private static final String VERTEX_SHADER =
            "uniform mat4 uMVPMatrix;\n" +          // a var representing the combined model/view/projection matrix
            "uniform mat4 uSTMatrix;\n" +
            "attribute vec4 aPosition;\n" +         // Pre-vertex position information we will pass in
            "attribute vec4 aTextureCoord;\n" +     // passed in
            "varying vec2 vTextureCoord;\n" +       // passed through
            "void main() {\n" +
            "  gl_Position = uMVPMatrix * aPosition;\n" +
            "  vTextureCoord = (uSTMatrix * aTextureCoord).xy;\n" +
            "}\n";
    /* this shader simply draws the decoded texture where it goes */
    private static final String FRAGMENT_SHADER =
//            "#extension GL_OES_EGL_image_external : require\n" +
            "precision mediump float;\n" +      // highp here doesn't seem to matter
            "varying vec2 vTextureCoord;\n" +
//            "uniform samplerExternalOES sTexture;\n" +
            "uniform sampler2D sTexture;\n" +
            "void main() {\n" +
            "  gl_FragColor = texture2D(sTexture, vTextureCoord);\n" +
            "}\n";

    /* ultimately, this second shader will need the following information:
        current frame in batch
        current decoded frame
        warp amount
        sampler for decoded frame
        sampler for batch frame (used to blend new pixel data with old)

        and a warpmap or some hard coded warp modes
    */

    /*
        warp = coord.x * warpAmount (us)
        if (warp > lfTime && warp < cfTime) mod = (cfTime - warp) / (cfTime - lfTime);
     */
    private static final String FRAGMENT_SHADER2A =
            "#extension GL_OES_EGL_image_external : require\n" +
            "precision highp float;\n" +      // highp here DOES seem to matter
            "varying vec2 vTextureCoord;\n" +
            "uniform samplerExternalOES sTexture;\n" +

            "uniform float lframeTime;\n" +
            "uniform float nframeTime;\n" +
            "uniform float cframeTime;\n" +
            "uniform float warpAmount;\n" +
            "void main() {\n" +
            "  vec2 coord = vTextureCoord;\n" +
            "  vec2 scoord = vTextureCoord;\n"; // scoord used for color sampler

    private static final String FRAGMENT_SHADER2B =
            "  vec4 scol = texture2D(sTexture, scoord);\n" +
            "  float lfDiff = cframeTime - lframeTime;\n" +
            "  float nfDiff = nframeTime - cframeTime;\n" +
            "  float mod = 0.0;\n"+
            "  if (warp >= lframeTime && warp < cframeTime) { mod = (warp - lframeTime) / lfDiff; }\n" +
            "  if (warp >= cframeTime && warp <= nframeTime) { mod = (nframeTime - warp) / nfDiff; }\n" +
            "  if (mod > 0.0) { gl_FragColor = mod * scol; }\n" +
            "}";

    private static final String[] WARP_FUNCTION = new String[]{
            "  float warp = coord.y * warpAmount;\n",

            "  float warp = coord.x * warpAmount;\n",

            "  float warp = (coord.x + coord.y) / 2.0 * warpAmount;\n",

            "  float warp = ((1.0 - coord.x) + coord.y) / 2.0 * warpAmount;\n",

            "  vec2 acoord = vec2((coord.x - 0.5) * aRatio, (coord.y - 0.5));\n" +
            "  float warp = (1.0 - distance(acoord, vec2(0, 0)) / dist) * warpAmount;\n",

            "  vec2 acoord = vec2(coord.x * aRatio, coord.y);\n" +
            "  float warp = (1.0 - distance(acoord, vec2(0.5 * aRatio, 1.0)) / dist) * warpAmount;\n",

            "  vec2 acoord = vec2(coord.x * aRatio, coord.y);\n" +
            "  float warp = (1.0 - distance(acoord, vec2(0.5 * aRatio, 0.0)) / dist) * warpAmount;\n",

            "  float warp = sin(coord.y * 3.1416) * warpAmount;\n",

            "  float warp = sin(coord.x * 3.1416) * warpAmount;\n",
    };
    /** warpTypes:
     0:  Vertical
     1:  Horizontal
     2:  Diagonal 1
     3:  Diagonal 2
     4:  Radial
     5:  HalfDome-Top
     6:  HalfDome-Bottom  */
    public String getFragShader(WarpArgs args) {
        String o = FRAGMENT_SHADER2A;
        // adjust coord to account for orientations
        // and my lack of proper transformation adjustment
        switch (args.orientation) {
            case 0:
                o += "  scoord.y = 1.0 - coord.y;\n";
                break;
            case 180:
                o += "  scoord.x = 1.0 - coord.x;\n";
                break;
            case 90:
                o += "  scoord.x = 1.0 - coord.y;\n";
                o += "  scoord.y = 1.0 - coord.x;\n";
                break;
            case 270:
                o += "  scoord.x = coord.y;\n";
                o += "  scoord.y = coord.x;\n";
                break;
        }
        // embed aspect ratio variable if required
        float aRatio = (float)args.decWidth / args.decHeight;
        if (args.warpType >= 2) o += "  float aRatio = "+String.format("%.4f", (float)args.decWidth / args.decHeight)+";\n";
        switch (args.warpType) {
            case 4: o += "  float dist = "+String.format("%.4f", (float)Math.sqrt(aRatio/2*aRatio/2 + 1f/2/2))+";\n"; break;
            case 5: o += "  float dist = "+String.format("%.4f", (float)Math.sqrt(aRatio/2*aRatio/2 + 1))+";\n"; break;
            case 6: o += "  float dist = "+String.format("%.4f", (float)Math.sqrt(aRatio/2*aRatio/2 + 1))+";\n"; break;
        }
        o += WARP_FUNCTION[WarpService.instance.warper.args.warpType];
        if (WarpService.instance.warper.args.invertWarp) o += "  warp = warpAmount - warp;\n";
        o += FRAGMENT_SHADER2B;
        return o;
    }


    private float[] mMVPMatrix = new float[16];
    private float[] mSTMatrix = new float[16];
    private int mProgram; // plain, renders texture onto outputSurface
    private int mProgram2; // renders image onto batch frames
    private int mTextureID = -12345;
    private int[] batTexIds, batFBOIds;
    private int muMVPMatrixHandle, muMVPMatrixHandle2;
    private int muSTMatrixHandle, muSTMatrixHandle2;
    private int maPositionHandle, maPositionHandle2;
    private int maTextureHandle, maTextureHandle2;
    // uniform handles for warping
    private int lframeTimeHandle; // time of last decoded frame (offset from time of batch frame)
    private int nframeTimeHandle; // time of next decoded frame
    private int cframeTimeHandle; // time of current decoded frame, being drawn to batch frame
    private int warpAmountHandle;

    public TextureRender() {
        mTriangleVertices = ByteBuffer.allocateDirect(
                mTriangleVerticesData.length * FLOAT_SIZE_BYTES)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
        mTriangleVertices.put(mTriangleVerticesData).position(0);
        Matrix.setIdentityM(mSTMatrix, 0);
    }

    public int getTextureId() {
        return mTextureID;
    }

    public void drawFrame(int batFrame) {
        checkGlError("onDrawFrame start");
        //st.getTransformMatrix(mSTMatrix);
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
        GLES20.glClear(GLES20.GL_DEPTH_BUFFER_BIT | GLES20.GL_COLOR_BUFFER_BIT);

        GLES20.glUseProgram(mProgram);
        checkGlError("glUseProgram");
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, batTexIds[batFrame]);
        mTriangleVertices.position(TRIANGLE_VERTICES_DATA_POS_OFFSET);
        GLES20.glVertexAttribPointer(maPositionHandle, 3, GLES20.GL_FLOAT, false,
                TRIANGLE_VERTICES_DATA_STRIDE_BYTES, mTriangleVertices);
        checkGlError("glVertexAttribPointer maPosition");
        GLES20.glEnableVertexAttribArray(maPositionHandle);
        checkGlError("glEnableVertexAttribArray maPositionHandle");
        mTriangleVertices.position(TRIANGLE_VERTICES_DATA_UV_OFFSET);
        GLES20.glVertexAttribPointer(maTextureHandle, 2, GLES20.GL_FLOAT, false,
                TRIANGLE_VERTICES_DATA_STRIDE_BYTES, mTriangleVertices);
        checkGlError("glVertexAttribPointer maTextureHandle");
        GLES20.glEnableVertexAttribArray(maTextureHandle);
        checkGlError("glEnableVertexAttribArray maTextureHandle");
        Matrix.setIdentityM(mMVPMatrix, 0);

        GLES20.glUniformMatrix4fv(muMVPMatrixHandle, 1, false, mMVPMatrix, 0);
        GLES20.glUniformMatrix4fv(muSTMatrixHandle, 1, false, mSTMatrix, 0);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        checkGlError("glDrawArrays");
        GLES20.glFinish();
    }

    /** render from decoded frame to a batch frame
     */
    public void drawOnBatchFrame(SurfaceTexture st, int bframe, float lframeTime, float nframeTime, float cframeTime, boolean clear) {
        checkGlError("drawOnBatchFrame start");
        st.getTransformMatrix(mSTMatrix);

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, batFBOIds[bframe]);
        checkGlError("glBindFramebuffer( bframe: "+bframe+" )");
        GLES20.glViewport(0, 0, Warper.args.outWidth, Warper.args.outHeight);
        checkGlError("glViewport");

        if (clear) {
            GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
            GLES20.glClear(GLES20.GL_DEPTH_BUFFER_BIT | GLES20.GL_COLOR_BUFFER_BIT);
        } else GLES20.glClear(GLES20.GL_DEPTH_BUFFER_BIT);

        GLES20.glUseProgram(mProgram2);
        checkGlError("glUseProgram");

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, mTextureID);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, batTexIds[bframe]);

        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE);

        mTriangleVertices.position(TRIANGLE_VERTICES_DATA_POS_OFFSET);
        GLES20.glVertexAttribPointer(maPositionHandle2, 3, GLES20.GL_FLOAT, false,
                TRIANGLE_VERTICES_DATA_STRIDE_BYTES, mTriangleVertices);
        checkGlError("glVertexAttribPointer maPosition2");
        GLES20.glEnableVertexAttribArray(maPositionHandle2);
        checkGlError("glEnableVertexAttribArray maPositionHandle2");

        mTriangleVertices.position(TRIANGLE_VERTICES_DATA_UV_OFFSET);
        GLES20.glVertexAttribPointer(maTextureHandle2, 2, GLES20.GL_FLOAT, false,
                TRIANGLE_VERTICES_DATA_STRIDE_BYTES, mTriangleVertices);
        checkGlError("glVertexAttribPointer maTextureHandle2");
        GLES20.glEnableVertexAttribArray(maTextureHandle2);
        checkGlError("glEnableVertexAttribArray maTextureHandle2");

        Matrix.setIdentityM(mMVPMatrix, 0);
        GLES20.glUniformMatrix4fv(muMVPMatrixHandle2, 1, false, mMVPMatrix, 0);
        GLES20.glUniformMatrix4fv(muSTMatrixHandle2, 1, false, mSTMatrix, 0);

        GLES20.glUniform1f(warpAmountHandle, Warper.args.amount);
        GLES20.glUniform1f(lframeTimeHandle, lframeTime);
        GLES20.glUniform1f(nframeTimeHandle, nframeTime);
        GLES20.glUniform1f(cframeTimeHandle, cframeTime);

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        checkGlError("glDrawArrays");
        GLES20.glFinish();

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
    }

    /**
     * Initializes GL state.  Call this after the EGL surface has been created and made current.
     */
    public void surfaceCreated() {
        mProgram = createProgram(VERTEX_SHADER, FRAGMENT_SHADER);
        if (mProgram == 0) {
            throw new RuntimeException("failed creating program");
        }
        maPositionHandle = GLES20.glGetAttribLocation(mProgram, "aPosition");
        checkGlError("glGetAttribLocation aPosition");
        if (maPositionHandle == -1) {
            throw new RuntimeException("Could not get attrib location for aPosition");
        }
        maTextureHandle = GLES20.glGetAttribLocation(mProgram, "aTextureCoord");
        checkGlError("glGetAttribLocation aTextureCoord");
        if (maTextureHandle == -1) {
            throw new RuntimeException("Could not get attrib location for aTextureCoord");
        }
        muMVPMatrixHandle = GLES20.glGetUniformLocation(mProgram, "uMVPMatrix");
        checkGlError("glGetUniformLocation uMVPMatrix");
        if (muMVPMatrixHandle == -1) {
            throw new RuntimeException("Could not get attrib location for uMVPMatrix");
        }
        muSTMatrixHandle = GLES20.glGetUniformLocation(mProgram, "uSTMatrix");
        checkGlError("glGetUniformLocation uSTMatrix");
        if (muSTMatrixHandle == -1) {
            throw new RuntimeException("Could not get attrib location for uSTMatrix");
        }

        // construct frag shader
        String FRAG_SRC = getFragShader(WarpService.instance.warper.args);

        // print frag shader for debug
//        if ("".length() == 0) {
            String[] lines = FRAG_SRC.split("\n");
            String o="";
            for (int i=0; i<lines.length; i++) o += "\n"+i+":\t"+lines[i];
            Log.d(TAG, "FRAG_SRC:"+o);
//        }

        mProgram2 = createProgram(VERTEX_SHADER, FRAG_SRC);
        if (mProgram2 == 0) {
            throw new RuntimeException("failed creating program");
        }
        maPositionHandle2 = GLES20.glGetAttribLocation(mProgram2, "aPosition");
        checkGlError("glGetAttribLocation aPosition");
        if (maPositionHandle2 == -1) {
            throw new RuntimeException("Could not get attrib location for aPosition");
        }
        maTextureHandle2 = GLES20.glGetAttribLocation(mProgram2, "aTextureCoord");
        checkGlError("glGetAttribLocation aTextureCoord");
        if (maTextureHandle2 == -1) {
            throw new RuntimeException("Could not get attrib location for aTextureCoord");
        }
        muMVPMatrixHandle2 = GLES20.glGetUniformLocation(mProgram2, "uMVPMatrix");
        checkGlError("glGetUniformLocation uMVPMatrix");
        if (muMVPMatrixHandle2 == -1) {
            throw new RuntimeException("Could not get attrib location for uMVPMatrix");
        }
        muSTMatrixHandle2 = GLES20.glGetUniformLocation(mProgram2, "uSTMatrix");
        checkGlError("glGetUniformLocation uSTMatrix");
        if (muSTMatrixHandle2 == -1) {
            throw new RuntimeException("Could not get attrib location for uSTMatrix");
        }

        lframeTimeHandle = GLES20.glGetUniformLocation(mProgram2, "lframeTime");
        if (lframeTimeHandle == -1)
            throw new RuntimeException("Could not get attrib location for lframeTime");
        nframeTimeHandle = GLES20.glGetUniformLocation(mProgram2, "nframeTime");
        if (nframeTimeHandle == -1)
            throw new RuntimeException("Could not get attrib location for nframeTime");
        cframeTimeHandle = GLES20.glGetUniformLocation(mProgram2, "cframeTime");
        if (cframeTimeHandle == -1)
            throw new RuntimeException("Could not get attrib location for cframeTime");
        warpAmountHandle = GLES20.glGetUniformLocation(mProgram2, "warpAmount");
        if (warpAmountHandle == -1) {
            throw new RuntimeException("Could not get attrib location for warpAmount");
        }

        int[] textures = new int[1];
        GLES20.glGenTextures(1, textures, 0);
        mTextureID = textures[0];
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, mTextureID);
        checkGlError("glBindTexture mTextureID");
        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER,
                GLES20.GL_NEAREST);
        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER,
                GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S,
                GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T,
                GLES20.GL_CLAMP_TO_EDGE);
        checkGlError("glTexParameter");

        // calc batch size
//        int[] maxSize = new int[1];
//        GLES20.glGetIntegerv(GLES10.GL_MAX_TEXTURE_SIZE, maxSize, 0);
//        int[] maxNum = new int[1];
//        GLES20.glGetIntegerv(GLES20.GL_MAX_TEXTURE_IMAGE_UNITS, maxNum, 0);
//        int maxPixels = maxSize[0] * maxNum[0];
//        int sframePixels = Warper.args.decWidth * Warper.args.decHeight;
//        int bframePixels = Warper.args.outWidth * Warper.args.outHeight;
//        Warper.self.batchSize = (int)(Math.floor(maxPixels - sframePixels) / bframePixels);
//        Log.d(TAG, "batchSize: "+Warper.self.batchSize);

        // batch vars
        batTexIds = new int[Warper.self.batchSize];
        batFBOIds = new int[Warper.self.batchSize];
        GLES20.glGenTextures(Warper.self.batchSize, batTexIds, 0);
        GLES20.glGenFramebuffers(Warper.self.batchSize, batFBOIds, 0);
        for (int i=0; i<Warper.self.batchSize; i++) {
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, batFBOIds[i]);
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0 + 1 + i);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, batTexIds[i]);
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, Warper.args.outWidth, Warper.args.outHeight, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
            checkGlError("after 7");

            GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, batTexIds[i], 0);
            checkGlError("after 8");

            if (GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER) != GLES20.GL_FRAMEBUFFER_COMPLETE) {
                throw new IllegalStateException("GL_FRAMEBUFFER status incomplete");
            }

            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        }
        checkGlError("after batch tex/fbos initiated");
    }

    /**
     * Replaces the fragment shader.
     */
    public void changeFragmentShader(String fragmentShader) {
        GLES20.glDeleteProgram(mProgram);
        mProgram = createProgram(VERTEX_SHADER, fragmentShader);
        if (mProgram == 0) {
            throw new RuntimeException("failed creating program");
        }
    }

    private int loadShader(int shaderType, String source) {
        int shader = GLES20.glCreateShader(shaderType);
        checkGlError("glCreateShader type=" + shaderType);
        GLES20.glShaderSource(shader, source);
        GLES20.glCompileShader(shader);
        int[] compiled = new int[1];
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0);
        if (compiled[0] == 0) {
            Log.e(TAG, "Could not compile shader " + shaderType + ":");
            Log.e(TAG, " " + GLES20.glGetShaderInfoLog(shader));
            GLES20.glDeleteShader(shader);
            shader = 0;
        }
        return shader;
    }

    private int createProgram(String vertexSource, String fragmentSource) {
        int vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexSource);
        if (vertexShader == 0) {
            return 0;
        }
        int pixelShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource);
        if (pixelShader == 0) {
            return 0;
        }
        int program = GLES20.glCreateProgram();
        checkGlError("glCreateProgram");
        if (program == 0) {
            Log.e(TAG, "Could not create program");
        }
        GLES20.glAttachShader(program, vertexShader);
        checkGlError("glAttachShader");
        GLES20.glAttachShader(program, pixelShader);
        checkGlError("glAttachShader");
        GLES20.glLinkProgram(program);
        int[] linkStatus = new int[1];
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0);
        if (linkStatus[0] != GLES20.GL_TRUE) {
            Log.e(TAG, "Could not link program: ");
            Log.e(TAG, GLES20.glGetProgramInfoLog(program));
            GLES20.glDeleteProgram(program);
            program = 0;
        }
        return program;
    }

    public void checkGlError(String op) {
        int error;
        while ((error = GLES20.glGetError()) != GLES20.GL_NO_ERROR) {
            Log.e(TAG, op + ": glError " + error);
            throw new RuntimeException(op + ": glError " + error);
        }
    }

}