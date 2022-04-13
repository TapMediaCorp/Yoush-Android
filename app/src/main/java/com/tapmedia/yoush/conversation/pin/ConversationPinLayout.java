package com.tapmedia.yoush.conversation.pin;


import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.TextView;

import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.Observer;
import androidx.lifecycle.OnLifecycleEvent;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import net.cachapa.expandablelayout.ExpandableLayout;

import com.tapmedia.yoush.R;
import com.tapmedia.yoush.conversation.model.PinWrapper;
import com.tapmedia.yoush.ui.base.BaseRecyclerAdapter;
import com.tapmedia.yoush.ui.base.BaseViewHolder;
import com.tapmedia.yoush.ui.base.ViewClickListener;
import com.tapmedia.yoush.database.model.MessageRecord;

import java.util.List;

public class ConversationPinLayout extends ConstraintLayout {

    public ConversationPinLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    private View viewHeader;
    private TextView textViewDropdown;
    private View conversationPinViewExpand;
    private ExpandableLayout expandableLayout;
    private ConversationPinItemView pinItemView;
    private RecyclerView recyclerView;
    private TextView textViewEdit;
    private TextView textViewCollapse;
    private ValueAnimator backgroundAnim;
    private ConversationPinAdapter adapter = new ConversationPinAdapter();
    public PinMessageClickListener pinMessageClickListener;
    public CollapseListener collapseListener;

    public void initializePinView(LifecycleOwner lifecycleOwner) {
        viewHeader = this.findViewById(R.id.conversationPinHeader);
        conversationPinViewExpand = this.findViewById(R.id.conversationPinViewExpand);
        pinItemView = this.findViewById(R.id.pinItemView);
        textViewDropdown = this.findViewById(R.id.conversationPinTextDropdown);
        expandableLayout = this.findViewById(R.id.conversationPinExpandableLayout);
        textViewEdit = this.findViewById(R.id.conversationTextEdit);
        textViewCollapse = this.findViewById(R.id.conversationTextCollapse);
        recyclerView = this.findViewById(R.id.conversationPinRecyclerView);
        viewHeader.setOnClickListener(new ViewClickListener() {
            @Override
            public void onClicks(View v) {
                PinWrapper firstRecordWrapper = adapter.get(0);
                if (null != pinMessageClickListener && null != firstRecordWrapper && null != firstRecordWrapper.refRecord) {
                    pinMessageClickListener.onPinMessageClick(firstRecordWrapper.refRecord);
                }
            }
        });
        conversationPinViewExpand.setOnClickListener(new ViewClickListener() {
            @Override
            public void onClicks(View v) {
                expand();
            }
        });
        textViewCollapse.setOnClickListener(new ViewClickListener() {
            @Override
            public void onClicks(View v) {
                collapse();
            }
        });
        textViewEdit.setOnClickListener(new ViewClickListener() {
            @Override
            public void onClicks(View v) {
                if (v.getContext() instanceof Activity) {
                    Activity activity = (Activity) v.getContext();
                    activity.startActivity(new Intent(v.getContext(), ConversationPinManagementActivity.class));
                }
            }
        });
        expandableLayout.setDuration(280);
        expandableLayout.setInterpolator(new AccelerateDecelerateInterpolator());
        expandableLayout.setOnExpansionUpdateListener((expansionFraction, state) -> {
            if (state == ExpandableLayout.State.EXPANDED) {
                textViewEdit.setVisibility(View.VISIBLE);
                textViewCollapse.setVisibility(View.VISIBLE);

            } else if (state == ExpandableLayout.State.COLLAPSED) {

                if (null != collapseListener) {
                    collapseListener.onCollapse();
                }
            }
        });

        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setAdapter(adapter);
        adapter.setOnItemClickListener((item, position) -> {
            if (null == item.refRecord) return;
            collapseListener = new CollapseListener() {
                @Override
                public void onCollapse() {
                    if (item != null) {
                        pinMessageClickListener.onPinMessageClick(item.refRecord);
                    }
                    collapseListener = null;
                }
            };
            collapse();
        });
        initBackgroundAnim();
        lifecycleOwner.getLifecycle().addObserver(new LifecycleObserver() {
            @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
            public void onDestroy() {
                viewHeader.setVisibility(View.INVISIBLE);
            }
        });
        PinData.recordLiveData.observe(lifecycleOwner, new Observer<List<PinWrapper>>() {
            @Override
            public void onChanged(List<PinWrapper> pinWrappers) {
                adapter.setListItem(pinWrappers);
                updatePinView();
            }
        });
    }

    void collapse() {
        if (!expandableLayout.isExpanded()) return;
        expandableLayout.collapse();
        textViewEdit.setVisibility(View.GONE);
        textViewCollapse.setVisibility(View.GONE);
        backgroundAnim.reverse();
        this.setOnTouchListener((v, event) -> false);

    }

    void expand() {
        if (expandableLayout.isExpanded()) return;
        if (adapter.size() < 1) return;
        expandableLayout.expand();
        backgroundAnim.start();
        this.setOnTouchListener((v, event) -> {
            collapse();
            return true;
        });
    }

    public void updatePinView() {
        if (adapter.size() == 0) {
            hidePinHeader();
            collapse();
            return;
        }
        showPinHeader();
        PinWrapper item = adapter.get(0);
        pinItemView.bindItem(item);
        if (adapter.size() > 1) {
            textViewDropdown.setText(String.format("   +%s", adapter.size() - 1));
        } else {
            textViewDropdown.setText(null);
        }
    }

    private void hidePinHeader() {
        if (viewHeader.getVisibility() != View.INVISIBLE) {
            viewHeader.setVisibility(View.INVISIBLE);
        }
    }

    private void showPinHeader() {
        if (viewHeader.getVisibility() != View.VISIBLE) {
            viewHeader.setVisibility(View.VISIBLE);
        }
    }

    void initBackgroundAnim() {
        int colorTo = Color.parseColor("#C8000000");
        backgroundAnim = ValueAnimator.ofObject(new ArgbEvaluator(), Color.TRANSPARENT, colorTo);
        backgroundAnim.setDuration(250);
        backgroundAnim.addUpdateListener(animator ->
                this.setBackgroundColor((int) animator.getAnimatedValue())
        );

    }

    public interface PinMessageClickListener {
        void onPinMessageClick(MessageRecord record);
    }

    public interface CollapseListener {
        void onCollapse();
    }

    private class ConversationPinAdapter extends BaseRecyclerAdapter<PinWrapper> {

        @Override
        protected int getLayoutResource() {
            return R.layout.conversation_activity_pin_item;
        }

        @Override
        public void onBindHolder(BaseViewHolder holder, PinWrapper item, int position) {
            ConversationPinItemView pinItemView = holder.findViewById(R.id.pinItemView);
            pinItemView.bindItem(item);
            pinItemView.visibleSeparator(View.VISIBLE);
        }

    }


}
