package com.tapmedia.yoush.dialog;

import android.graphics.Color;
import android.view.View;
import android.widget.TextView;

import com.tapmedia.yoush.R;
import com.tapmedia.yoush.ui.base.BaseBottomFragment;


public class AlertBottomDialog extends BaseBottomFragment {


    public String title;
    public String subTitle;
    public String confirmLabel;
    public String cancelLabel;
    public int confirmColor = Color.BLACK;
    public Runnable runnable;
    private TextView textViewTitle;
    private TextView textViewSubTitle;
    private TextView textViewConfirm;
    private TextView textViewCancel;


    @Override
    public int layoutResource() {
        return R.layout.alert_bottom_dialog;
    }

    @Override
    public void onFindView() {
        textViewTitle = find(R.id.alertTextViewTitle);
        textViewSubTitle = find(R.id.alertTextViewSubTitle);
        textViewConfirm = find(R.id.alertViewConfirm);
        textViewCancel = find(R.id.alertViewCancel);
    }

    @Override
    public void onViewCreated() {
        textViewTitle.setText(title);
        textViewSubTitle.setText(subTitle);
        textViewConfirm.setText(confirmLabel);
        textViewConfirm.setTextColor(confirmColor);
        textViewCancel.setText(cancelLabel);
        addViewClicks(textViewConfirm, textViewCancel);
    }

    @Override
    public void onLiveDataObservers() {

    }

    @Override
    protected void onViewClick(View v) {
        switch (v.getId()) {
            case R.id.alertViewConfirm:
                dismiss();
                if (null != runnable) {
                    runnable.run();
                }
                break;
            case R.id.alertViewCancel:
                dismiss();
                break;
        }
    }


}

