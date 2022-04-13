/*
 * Copyright (C) 2016 Open Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.tapmedia.yoush.groups;

import android.content.Intent;
import android.os.Bundle;
import android.view.WindowManager;

import androidx.appcompat.app.AppCompatActivity;

import com.tapmedia.yoush.R;
import com.tapmedia.yoush.events.WebRtcViewModel;
import com.tapmedia.yoush.logging.Log;
import com.tapmedia.yoush.util.ViewUtil;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

public class GroupCallBeginActivity extends AppCompatActivity {

    private static final String TAG = GroupCallBeginActivity.class.getSimpleName();

    private ActivityGrouptCallBeginView      callScreen;

    public static final String ROOM_NAME                       = "room_name";
    public static final String GROUP_NAME                       = "group_name";
    public static final String SENDER_NAME                       = "sender_name";
    public static final String RECIPENT_PENDING                       = "recipent_pending";

    private String             roomName;
    private String             groupName;
    private String             senderName;
    private String             avatar;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_call_begin);

        roomName = getIntent().getStringExtra(ROOM_NAME);
        getIntent().getAction();

        groupName = getIntent().getStringExtra(GROUP_NAME);
        senderName = getIntent().getStringExtra(SENDER_NAME);

        initializeResources();

        callScreen.setRoomName(roomName);
        callScreen.setSenderName(senderName, groupName);

        EventBus.getDefault().register(this);

//        Bundle bundle = getIntent().getBundleExtra(GroupCallBeginActivity.RECIPENT_PENDING);
//
//        Recipient recipient = null;
//
//        if (bundle != null) {
//            recipient = (Recipient) bundle.getSerializable(GroupCallBeginActivity.RECIPENT_PENDING);
//        }

//        Recipient recipient = (Recipient) getIntent().getSerializableExtra("RECIPENT_PENDING");
//        callScreen.setAvatar(recipient);


    }

    @Override
    protected void onStart() {
        super.onStart();
        getDelegate().onStart();
        if (!EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().register(this);
        }
    }

    @Override
    public void onResume() {
        Log.i(TAG, "onResume()");
        super.onResume();

        if (!EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().register(this);
        }
    }

    @Override
    public void onNewIntent(Intent intent){
        Log.i(TAG, "onNewIntent");
        super.onNewIntent(intent);
    }

    @Override
    public void onPause() {
        Log.i(TAG, "onPause");
        super.onPause();

        EventBus.getDefault().unregister(this);
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    public void onEvent(final WebRtcViewModel event) {
        Log.i(TAG, "Got message from service: " + event);

//        viewModel.setRecipient(event.getRecipient());

        switch (event.getState()) {
            case CALL_INCOMING:                     handleIncomingCall(event);                                                 break;
            case CALL_DECLINED_ELSEWHERE:           handleDeclined(event);                                                 break;
        }
    }

    private void handleDeclined(WebRtcViewModel event) {
        super.onBackPressed();
    }

    private void handleIncomingCall(WebRtcViewModel event) {
        callScreen.setAvatar(event.getRecipient());
    }

    public static void launch()  { //Context context
//        Intent intent = new Intent(this, GroupCallBeginActivity.class);
//        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
//        this.startActivity(intent);
        GroupCallBeginActivity obj = new GroupCallBeginActivity();
        obj.onBackPressed();
    }

    public void finishAc() {

    }


    @Override
    protected void onStop() {
        super.onStop();
        getDelegate().onStop();
        EventBus.getDefault().unregister(this);
        super.onBackPressed();
    }

    @Override
    protected void onPostResume() {
            super.onPostResume();
            getDelegate().onPostResume();

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        getDelegate().onDestroy();
    }

    private void initializeResources() {
        callScreen = ViewUtil.findById(this, R.id.callScreenBegin);
        callScreen.setControlsListener(new ControlsListener());
    }

    private final class ControlsListener implements ActivityGrouptCallBeginView.ControlsListener {
        @Override
        public void onDenyCallPressed() {
            handleDenyCall();
        }
    }

    private void handleDenyCall() {
        super.onBackPressed();
        Intent     intent     = new Intent(this, GroupCallBeginService.class);
        intent.setAction(GroupCallBeginService.ACTION_DENY_CALL_BUTTON);
        this.startService(intent);
    }
}
