package com.olioo.vtw;

import android.content.Context;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

import com.olioo.vtw.bigflake.DecodeEditEncodeTest;

public class MainActivity extends AppCompatActivity {

    public static Context context;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        context = getBaseContext();
    }

    @Override
    protected void onStart() {
        super.onStart();

        Thread th = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    DecodeEditEncodeTest test = new DecodeEditEncodeTest();
                    test.testVideoEdit720p();
                } catch (Throwable throwable) {
                    throwable.printStackTrace();
                }
            }
        }); th.start();
    }
}
