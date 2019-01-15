package com.olioo.vtw.util;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;

import com.olioo.vtw.BuildConfig;
import com.olioo.vtw.R;

import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by olioo on 6/9/2016.
 */
public class Helper {

    /**
     *
     * @param activity
     * @param context
     * @param permissions
     * @param requestCode
     * @return true if permissions already granted, false if request is made
     */
    public static boolean getPermissions(Activity activity, Context context, String[] permissions, int requestCode) {
        //check whech permissions we dont have yet
        int count = 0; boolean[] needPermission = new boolean[permissions.length];
        for (int i = 0; i < permissions.length; i++) {
            int check = ContextCompat.checkSelfPermission(context, permissions[i]);
            if (check != PackageManager.PERMISSION_GRANTED) {
                needPermission[i] = true;
                count++;
            }
        }

        if (count == 0) return true;

        //make a nice array for them
        String[] needed = new String[count];
        count = 0;
        for (int i = 0; i < needed.length; i++)
            if (needPermission[i]) needed[count++] = permissions[i];

        //request needed permissions
        ActivityCompat.requestPermissions(activity, needed, requestCode);
        return false;
    }

    public static void deleteRecursive(File fileOrDirectory) {
        if (fileOrDirectory.isDirectory())
            for (File child : fileOrDirectory.listFiles())
                deleteRecursive(child);

        fileOrDirectory.delete();
    }

    public static String getRealPathFromURI(Context main, Uri _uri) {
        String filePath = null;
        if (_uri != null && "content".equals(_uri.getScheme())) {
            Cursor cursor = main.getContentResolver().query(_uri, new String[]{MediaStore.Images.ImageColumns.DATA}, null, null, null);
            cursor.moveToFirst();
            filePath = cursor.getString(0);
            cursor.close();
        } else {
            filePath = _uri.getPath();
        }
        //Log.d("","Chosen path = "+ filePath);
        return filePath;
    }

    static final int filename_digits = 3; //append 0's so this works well with alphabetical order
    public static Pattern FILENAME = Pattern.compile("(.+[/\\\\])([^/\\\\]+?)(?:-\\d{"+filename_digits+"})?(\\.[^\\.]+)");
    public static String makeFilenameUnique(String path) {

        if (true || !new File(path).exists()) return path;

        //get name
        Matcher mat = FILENAME.matcher(path);
        if (!mat.matches()) { Helper.log("alucard", "sup"); return null; }
        String parentPath = mat.group(1);
        String name = mat.group(2); //pattern removes -//d{3} affix from name
        String extension = mat.group(3);
        //make pattern to read file affixes
        Pattern count = Pattern.compile(".+"+ Pattern.quote(name)+"-(\\d+)\\."+ Pattern.quote(extension));
        //get highest affix
        int topAffix = 0;
        String[] files = new File(mat.group(1)).list();
        for (int i=0; i < files.length; i++) {
            mat = count.matcher(files[i]);
            if (!mat.matches()) continue;
            int affix = Integer.parseInt(mat.group(1));
            if (affix > topAffix) topAffix = affix;
        }

        //gen and return new path
        String saffix = topAffix+"";
        while (saffix.length() < filename_digits) saffix = "0"+saffix;
        return parentPath + name + "-" + saffix + extension;
    }

    //utilities
    public static void hideKeyboard(Activity activity) {
        InputMethodManager imm = (InputMethodManager) activity.getSystemService(Activity.INPUT_METHOD_SERVICE);
        //Find the currently focused view, so we can grab the correct window token from it.
        View view = activity.getCurrentFocus();
        //If no view currently has focus, create a new one, just so we can grab a window token from it
        if (view == null) {
            view = new View(activity);
        }
        imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }

    public static void hideKeyboardFrom(Context context, View view) {
        InputMethodManager imm = (InputMethodManager) context.getSystemService(Activity.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }

    public static void alert(String title, String prompt, Context context) {

//        Helper.log("alert: "+title, prompt);
        AlertDialog.Builder builder1 = new AlertDialog.Builder(context);
        builder1.setTitle(title);
        builder1.setMessage(prompt);
        builder1.setCancelable(true);
        builder1.setNeutralButton(android.R.string.ok,
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.cancel();
                    }
                });

        AlertDialog alert11 = builder1.create();
        alert11.show();
    }

    public static void runOnYes(String prompt, Context context, Runnable runme) { runOnCancel("No", "Yes", prompt, context, runme); }
    public static void runOnCancel(String noBtn, String yesBtn, String prompt, Context context, final Runnable runme) {
        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(context, R.style.DialogTheme);

        // set title
        alertDialogBuilder.setTitle(prompt);

        // set dialog message
        alertDialogBuilder
//                .setMessage("Click yes to exit!")
                .setCancelable(false)
                .setPositiveButton(yesBtn,new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        runme.run();
                        dialog.cancel();
                    }
                })
                .setNegativeButton(noBtn,new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        // if this button is clicked, just close
                        // the dialog box and do nothing
                        dialog.cancel();
                    }
                });

        // create alert dialog
        AlertDialog alertDialog = alertDialogBuilder.create();

        // show it
        alertDialog.show();
    }

    public static byte[] rgbToYUV(int[] rgb_8888, int width, int height) {
        byte[] yuvFrame = new byte[(int)(rgb_8888.length*1.5)];
        int yIndex = 0;
        int uvIndex = width * height;
        int index = 0;
        int R, G, B, Y, U, V;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {

//                    a = (warpedFrame[index] & 0xff000000) >> 24; // a is not used obviously
                R = (rgb_8888[index] & 0xff0000) >> 16;
                G = (rgb_8888[index] & 0xff00) >> 8;
                B = (rgb_8888[index] & 0xff) >> 0;

                // well known RGB to YUV algorithm
                Y = ( (  66 * R + 129 * G +  25 * B + 128) >> 8) +  16;
                U = ( ( -38 * R -  74 * G + 112 * B + 128) >> 8) + 128;
                V = ( ( 112 * R -  94 * G -  18 * B + 128) >> 8) + 128;

                // NV21 has a plane of Y and interleaved planes of VU each sampled by a factor of 2
                //    meaning for every 4 Y pixels there are 1 V and 1 U.  Note the sampling is every other
                //    pixel AND every other scanline.
                yuvFrame[yIndex++] = (byte) ((Y < 0) ? 0 : ((Y > 255) ? 255 : Y));
                if (y % 2 == 0 && index % 2 == 0) {
                    yuvFrame[uvIndex++] = (byte)((V<0) ? 0 : ((V > 255) ? 255 : V));
                    yuvFrame[uvIndex++] = (byte)((U<0) ? 0 : ((U > 255) ? 255 : U));
                }

                index ++;
            }
        } return yuvFrame;
    }

    public static void log(String tag, String msg) {
        if (BuildConfig.DEBUG) {
            Log.d(tag, msg);
        }
    }

    /*private void showExplanation(String title,
                                 String message,
                                 final String permission,
                                 final int permissionRequestCode) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(title)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        requestPermission(permission, permissionRequestCode);
                    }
                });
        builder.create().show();
    }

    private void requestPermission(String permissionName, int permissionRequestCode) {
        ActivityCompat.requestPermissions(this,
                new String[]{permissionName}, permissionRequestCode);
    }*/
}
