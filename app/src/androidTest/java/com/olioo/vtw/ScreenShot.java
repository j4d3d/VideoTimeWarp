package com.olioo.vtw;

import android.app.Activity;
import android.graphics.Bitmap;
import android.os.Environment;
import android.util.Log;
import android.view.View;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public class ScreenShot {
    private static final String TAG = "SCREENSHOT_TAG";

    public static void take(Activity activity, String name) {
        final String dir = Environment.getExternalStorageDirectory().getAbsolutePath() + "/test-screenshots/";
        final String path = dir + name;

        File filePath = new File(dir);     // Create directory if not present
        if (!filePath.isDirectory()) {
            Log.i(TAG, "Creating directory " + filePath);
            filePath.mkdirs();
        }

        Log.i(TAG, "Saving to path: " +  path);

        View phoneView = activity.getWindow().getDecorView().getRootView();
        phoneView.setDrawingCacheEnabled(true);
        Bitmap bitmap = Bitmap.createBitmap(phoneView.getDrawingCache());
        phoneView.setDrawingCacheEnabled(false);

        OutputStream out = null;

        File imageFile = new File(path);

        try {
            out = new FileOutputStream(imageFile);
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
            out.flush();
        }
        catch (FileNotFoundException e) {
            Log.e(TAG, e.toString());
        }
        catch (IOException e) {
            Log.e(TAG, e.toString());
        }

        finally {
            try {
                if (out != null) {
                    out.close();
                }
            } catch (IOException e) {
                Log.e(TAG, e.toString());
            }
        }
    }
}
