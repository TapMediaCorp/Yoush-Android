package com.tapmedia.yoush.ui.base;

import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import static android.view.View.NO_ID;


public abstract class BaseBottomFragment extends BottomSheetDialogFragment
        implements BaseView {

    public DialogInterface.OnDismissListener onDismissListener;


    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(layoutResource(), container, false);
       /* BottomSheetDialog dialog = (BottomSheetDialog) getDialog();
        View bottomSheet = dialog.findViewById(R.id.design_bottom_sheet);
        CoordinatorLayout coordinatorLayout = (CoordinatorLayout) bottomSheet.getParent();
        BottomSheetBehavior behavior = BottomSheetBehavior.from(bottomSheet);
        behavior.setPeekHeight(bottomSheet.getHeight());
        coordinatorLayout.getParent().requestLayout();*/
        return v;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        onFindView();
        onViewCreated();
        onLiveDataObservers();
    }

    protected void onWindowConfig(Window window) {
    }

    /**
     * {@link BaseView} implements
     */
    @Override
    public final BaseActivity getBaseActivity() {
        if (requireActivity() instanceof BaseActivity) {
            return (BaseActivity) requireActivity();
        }
        return null;
    }

    @Override
    public final NavController getNavController() {
        return NavHostFragment.findNavController(this);
    }

    protected final void addViewClicks(View... views) {
        ViewClickListener listener = new ViewClickListener(360L) {
            @Override
            public void onClicks(View v) {
                onViewClick(v);
            }
        };
        for (View v : views) {
            v.setOnClickListener(listener);
        }
    }

    protected void onViewClick(View v) {

    }

    protected final <T extends ViewModel> T getViewModel(Class<T> cls) {
        return new ViewModelProvider(this).get(cls);
    }

    protected final <T extends ViewModel> T getActivityViewModel(Class<T> cls) {
        return new ViewModelProvider(requireActivity()).get(cls);
    }

    public final <T extends View> T find(@IdRes int id) {
        if (id == NO_ID) {
            return null;
        }
        return getView().findViewById(id);
    }

}
