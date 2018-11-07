package com.olioo.vtw.bigflake;

import android.content.Context;
import android.util.Log;

import com.olioo.vtw.MainActivity;

/** my own little class to satisfy bigflake unit testing dependency,
 * leaving DecodeEditEncodeTest relatively unedited from original */

public class AndroidTestCase {

    public void fail() { fail("default"); }
    public void fail(String msg) {
        Log.d("FAIL!", msg);
    }

    public void assertTrue(boolean bool) {
        if (!bool) fail();
    }

    public void assertEquals(String msg, long a, long b) {
        if (a != b) fail(msg);
    }

}
