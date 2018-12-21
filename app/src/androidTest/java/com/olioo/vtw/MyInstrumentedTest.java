package com.olioo.vtw;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.support.test.InstrumentationRegistry;
import android.support.test.espresso.Espresso;
import android.support.test.espresso.IdlingRegistry;
import android.support.test.espresso.IdlingResource;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.util.Log;
import android.view.View;

import com.olioo.vtw.idle.LytWarpIdlingResource;
import com.olioo.vtw.warp.WarpService;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

import static android.support.test.espresso.Espresso.onView;
import static android.support.test.espresso.action.ViewActions.click;
import static android.support.test.espresso.assertion.ViewAssertions.doesNotExist;
import static android.support.test.espresso.assertion.ViewAssertions.matches;
import static android.support.test.espresso.matcher.ViewMatchers.isDisplayed;
import static android.support.test.espresso.matcher.ViewMatchers.withId;
import static android.support.test.espresso.matcher.ViewMatchers.withText;
import static org.junit.Assert.assertEquals;

@RunWith(AndroidJUnit4.class)
public class MyInstrumentedTest {
//    LytWarpIdlingResource lytWarpIdlingResource;

    @Rule
    public ActivityTestRule<MainActivity> mActivityRule
            = new ActivityTestRule<>(MainActivity.class);

    /*@Before
    public void registerIntentServiceIdlingResource() {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        lytWarpIdlingResource = new LytWarpIdlingResource(mActivityRule.getActivity());
//        Espresso.registerIdlingResources(idlingResource);
        IdlingRegistry.getInstance().register(lytWarpIdlingResource);
    }

    @After
    public void unregisterIntentServiceIdlingResource() {
//        Espresso.unregisterIdlingResources(lytWarpIdlingResource);
        IdlingRegistry.getInstance().unregister(lytWarpIdlingResource);
    }*/


    @Test
    public void useAppContext() {
        final MainActivity mainActivity = mActivityRule.getActivity();
        final String uriPath = Environment.getExternalStorageDirectory()+"/test.mp4";

        // save video and select it as VSL_WARP
        saveRawVideo(mainActivity);
        MainActivity.handle.obtainMessage(MainActivity.HNDL_RUNNABLE, new Runnable() {
            @Override
            public void run() {
                mainActivity.onActivityResult(MainActivity.VSL_WARP,
                        Activity.RESULT_OK,
                        new Intent().setData(Uri.fromFile(new File(uriPath))));
            }
        }).sendToTarget();

        sleep(300);

        ScreenShot.take(mainActivity, "vidSelected");



        // click buttons
        onView(withId(R.id.btnStart)).perform(click());
        try {
            onView(withText("YES")).perform(click());
        } catch (Exception e) {
            e.printStackTrace();
        }

        while (WarpService.instance == null || WarpService.instance.encodedLength < 2000000) {
            sleep(1000);
        }

        onView(withId(R.id.btnHalt)).perform(click());

        onView(withText("YES")).perform(click());

        sleep(5000);
    }

    void retryLoop(Runnable runnable) {
        boolean success = false;
        while (!success) {
            try {
                runnable.run();
                success = true;
            } catch (Exception e) {
                e.printStackTrace();
                sleep(1000);
            }
        }
    }

    void sleep(int ms) {
        try {
            Thread.sleep(ms);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public boolean vidSaved = false;
    public void saveRawVideo(final MainActivity mainActivity) {
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                //save raw video
                String path = Environment.getExternalStorageDirectory()+"/test.mp4";

                try {
                    InputStream in = mainActivity.getResources().openRawResource(R.raw.video_480x360_mp4_h264_500kbps_30fps_aac_stereo_128kbps_44100hz);
                    FileOutputStream out = new FileOutputStream(path);
                    byte[] buff = new byte[1024];
                    int read = 0;

                    while ((read = in.read(buff)) > 0)
                        out.write(buff, 0, read);

                    in.close();
                    out.close();
                    vidSaved = true;
                } catch (Exception e) { e.printStackTrace(); }
            }
        });

        thread.start();

        while (!vidSaved) {
            try { Thread.sleep(1000); }
            catch (InterruptedException e) { e.printStackTrace(); }
        }
    }
}
