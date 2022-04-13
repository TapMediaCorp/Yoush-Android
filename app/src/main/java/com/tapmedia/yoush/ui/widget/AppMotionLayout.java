package com.tapmedia.yoush.ui.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.IdRes;
import androidx.constraintlayout.motion.widget.MotionLayout;

import com.tapmedia.yoush.ui.base.ViewClickListener;

import java.util.ArrayList;
import java.util.List;


public class AppMotionLayout extends MotionLayout {


    public View.OnClickListener viewClickListener = new ViewClickListener() {
        @Override
        public void onClicks(View v) {
            onViewClick(v);
        }
    };

    private View touchView = null;
    private final boolean onEvent = false;
    private float touchX = -1f;
    private float touchY = -1f;
    private long touchTime = 0;
    private final List<View> clickableViews = new ArrayList<View>();
    private final Runnable enableInteraction = () -> setInteractionEnabled(true);

    public AppMotionLayout(Context context) {
        super(context);
    }

    public AppMotionLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public AppMotionLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    private boolean isTransitionCompleted() {
        return getProgress() == 0f || getProgress() == 1f;
    }

    public void addViewClickListener(View... views) {
        for (View v : views) {
            v.setClickable(false);
            clickableViews.add(v);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if ((getProgress() != 0f && getProgress() != 1f) && !onEvent) {
            touchView = null;
            return super.onTouchEvent(event);
        }
        if (event == null) {
            return super.onTouchEvent(null);
        }
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                touchX = event.getX();
                touchY = event.getY();
                touchTime = System.currentTimeMillis();
                touchView = getViewOnTouchEvent(event);
                break;
            case MotionEvent.ACTION_UP:
                detectActionClick(event);
                break;
        }
        return super.onTouchEvent(event);
    }

    private void detectActionClick(MotionEvent event) {
        View v = getViewOnTouchEvent(event);
        if (isTransitionCompleted() && touchView != null && touchView == v) {
            double touchDistance = Math.sqrt(Math.pow(event.getX() - touchX, 2.0) + Math.pow(event.getY() - touchY, 2.0));
            float touchDuration = System.currentTimeMillis() - touchTime;
            if (touchDistance < 6F && touchDuration < 250) {
                viewClickListener.onClick(v);
            }
        }
        touchView = null;
        touchX = -1f;
        touchY = -1f;
        touchTime = -1;
    }

    private View getViewOnTouchEvent(MotionEvent event) {
        int x = (int) event.getX();
        int y = (int) event.getY();
        for (View v : clickableViews) {
            boolean inXRange = x >= v.getLeft() && x < v.getRight();
            boolean inYRange = y >= v.getTop() && y < v.getBottom();
            if (inXRange && inYRange) {
                return v;
            }
        }
        return null;
    }

    public void safeTransitionTo(@IdRes int transitionId) {
        if (getProgress() == 0f && getProgress() == 1f) {
            return;
        }
        post(() -> {
            setInteractionEnabled(false);
            transitionToState(transitionId);
        });
        this.postDelayed(enableInteraction, 320);
    }

    public void onViewClick(View v) {
    }
}
