package com.olioo.vtw;

import android.app.Activity;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;

import com.olioo.vtw.util.Helper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

public class MinTestActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //hide title and notification bars
//        this.requestWindowFeature(Window.FEATURE_NO_TITLE);
        this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        setContentView(R.layout.activity_main);
    }

    @Override
    protected void onStart() {
        super.onStart();

        Log.d("DEBUG", "Consider yourself tested.");
    }

    public void startWarping() {

    }
}