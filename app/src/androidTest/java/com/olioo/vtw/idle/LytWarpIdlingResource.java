package com.olioo.vtw.idle;

import android.support.test.espresso.IdlingResource;
import android.view.View;

import com.olioo.vtw.MainActivity;
import com.olioo.vtw.R;

public class LytWarpIdlingResource implements IdlingResource {
    MainActivity mainActivity;
    ResourceCallback resourceCallback;

    public LytWarpIdlingResource(MainActivity mainActivity) {
        this.mainActivity = mainActivity;
    }

    @Override
    public String getName() {
        return LytWarpIdlingResource.class.getName();
    }

    @Override
    public boolean isIdleNow() {
        boolean idle = mainActivity.findViewById(R.id.lytWarp).getVisibility() == View.VISIBLE;
        return idle;
    }

    @Override
    public void registerIdleTransitionCallback(ResourceCallback callback) {
        this.resourceCallback = callback;
    }
}
