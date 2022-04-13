package com.tapmedia.yoush.ui.base;

import android.view.View;

public abstract class ViewClickListener implements View.OnClickListener {

    private long delayedInterval;

    private boolean hasDelayed = false;

    public ViewClickListener() {
        this.delayedInterval = 400;
    }
    public ViewClickListener(long delayedInterval) {
        this.delayedInterval = delayedInterval;
    }

    public abstract void onClicks(View v);

    @Override
    public final void onClick(View v) {
        if (isDelayed() || isAcceptClick(v)) {
            ClickTime.lastClickViewId = v.getId();
            ClickTime.lastClickTime = 0;
            hasDelayed = false;
            onClicks(v);
        }
        if (!hasDelayed) {
            hasDelayed = true;
            ClickTime.lastClickTime = System.currentTimeMillis();
        }
    }

    private boolean isAcceptClick(View v) {
        return v.getId() != ClickTime.lastClickViewId && delayedInterval == 0;
    }

    private boolean isDelayed() {
        return System.currentTimeMillis() - ClickTime.lastClickTime > delayedInterval;
    }

    private static class ClickTime {

        public static long lastClickTime = 0;

        public static long lastClickViewId = 0;
    }

}
