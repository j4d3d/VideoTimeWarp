package com.olioo.vtw;

import android.Manifest;
import android.os.Build;
import android.os.Environment;

import com.olioo.vtw.warp.WarpService;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.FileOutputStream;
import java.io.InputStream;

import androidx.test.filters.LargeTest;
import androidx.test.rule.ActivityTestRule;
import androidx.test.rule.GrantPermissionRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import static androidx.test.InstrumentationRegistry.getTargetContext;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;
import static org.junit.Assert.assertThat;

@RunWith(AndroidJUnit4.class)
@LargeTest
public class MyInstrumentedTest {

    @Rule
    public ActivityTestRule<TestActivity> mActivityRule
            = new ActivityTestRule<>(TestActivity.class);

    @Rule
    public GrantPermissionRule grantPermissionRule = GrantPermissionRule.grant(
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE);

    /*@Before
    public void grantPhonePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            getInstrumentation().getUiAutomation().executeShellCommand(
                    "pm grant " + getInstrumentation().getTargetContext().getPackageName()
                            + " android.permission.READ_EXTERNAL_STORAGE");
            getInstrumentation().getUiAutomation().executeShellCommand(
                    "pm grant " + getInstrumentation().getTargetContext().getPackageName()
                            + " android.permission.WRITE_EXTERNAL_STORAGE");
        }
    }*/

    @Test
    public void useAppContext() {

        final MainActivity mainActivity = mActivityRule.getActivity();

        ScreenShot.take(mainActivity, "vidSelected");

//        sleep(5000);

        // listener has been changed to simply simulate vid selection and startwarp
        onView(withId(R.id.btnWarp)).perform(click());
        onView(withId(R.id.btnStart)).perform(click());
        try {
            onView(withText("YES")).perform(click());
        } catch (Exception e) {
            e.printStackTrace();
        }

        ScreenShot.take(mainActivity, "warping");

        while (WarpService.instance == null || WarpService.instance.encodedLength < 4000000) {
            sleep(1000);
        }

        onView(withId(R.id.btnHalt)).perform(click());

        ScreenShot.take(mainActivity, "halting");

        onView(withText("YES")).perform(click());

        sleep(5000);

        ScreenShot.take(mainActivity, "halted");
    }

    void sleep(int ms) {
        try {
            Thread.sleep(ms);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
