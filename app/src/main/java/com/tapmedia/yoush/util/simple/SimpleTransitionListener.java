package com.tapmedia.yoush.util.simple;

import androidx.constraintlayout.motion.widget.MotionLayout;

public abstract class SimpleTransitionListener implements MotionLayout.TransitionListener {
    @Override
    public void onTransitionStarted(MotionLayout motionLayout, int startId, int endId) {
    }

    @Override
    public void onTransitionChange(MotionLayout motionLayout, int startId, int endId, float progress) {
    }

    @Override
    public void onTransitionCompleted(MotionLayout motionLayout, int currentId) {
    }

    @Override
    public void onTransitionTrigger(MotionLayout motionLayout, int triggerId, boolean positive, float progress) {
    }
}