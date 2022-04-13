package com.tapmedia.yoush.ui.base;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;

import androidx.annotation.ColorRes;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.PluralsRes;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;

import com.tapmedia.yoush.ApplicationContext;
import com.tapmedia.yoush.MainNavigator;
import com.tapmedia.yoush.R;
import com.tapmedia.yoush.util.ServiceUtil;
import com.tapmedia.yoush.util.ViewUtil;

import static android.view.View.NO_ID;


public abstract class BaseFragment extends Fragment
        implements BaseView {


    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        /*requireActivity().getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                onBackPressed();
            }
        });*/
    }

    @Override
    public void onStart() {
        super.onStart();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(layoutResource(), container, false);
        v.setOnTouchListener((v1, event) -> true);
        return v;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        onFindView();
        onViewCreated();
        onLiveDataObservers();
    }

    public void onBackPress() {
        requireActivity().onBackPressed();
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

    public MainNavigator getNavigator() {
        return MainNavigator.get(requireActivity());
    }

    public String getFragmentTag() {
        String tag = this.getClass().getSimpleName();
        if (tag.length() > 23) {
            return tag.substring(0, 23);
        }
        return tag;
    }

    public void addFragment(Fragment fragment) {
        String tag = fragment.getClass().getSimpleName();
        if (tag.length() > 22) {
            tag = tag.substring(0, 22);
        }
       getActivity()
                .getSupportFragmentManager()
                .beginTransaction()
                .setCustomAnimations(R.anim.slide_from_end, R.anim.slide_to_start, R.anim.slide_from_start, R.anim.slide_to_end)
                .add(R.id.viewFragmentContainer, fragment)
                .addToBackStack(tag)
                .commitAllowingStateLoss();
    }

    public void replaceFragment(Fragment fragment) {
        String tag = fragment.getClass().getSimpleName();
        if (tag.length() > 23) {
            tag = tag.substring(0, 23);
        }
        getActivity()
                .getSupportFragmentManager()
                .beginTransaction()
                .setCustomAnimations(R.anim.slide_from_end, R.anim.slide_to_start, R.anim.slide_from_start, R.anim.slide_to_end)
                .replace(R.id.viewFragmentContainer, fragment)
                .addToBackStack(tag)
                .commitAllowingStateLoss();
    }

    protected void remove(Fragment fragment) {
        getActivity()
                .getSupportFragmentManager()
                .beginTransaction()
                .remove(fragment)
                .commit();
    }

    protected <T extends Activity> void start(Class<T> cls) {
        startActivity(new Intent(getActivity(), cls));
    }

    protected void hideKeyboard() {
        InputMethodManager imm = ServiceUtil.getInputMethodManager(requireContext());
        imm.hideSoftInputFromWindow(requireView().getWindowToken(), 0);
    }

    public String quantityStr(@PluralsRes int id, int quantity, Object... formatArgs)
            throws Resources.NotFoundException {
        return ApplicationContext.getInstance().getResources().getQuantityString(id, quantity, formatArgs);
    }

    public String str(@StringRes int id, Object... formatArgs)
            throws Resources.NotFoundException {
        return ApplicationContext.getInstance().getString(id, formatArgs);
    }

    public int color(@ColorRes int res) {
        return ContextCompat.getColor(ApplicationContext.getInstance(), res);
    }

    public void setStatusBarColor(@ColorRes int colorRes) {
        Window window = requireActivity().getWindow();
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return;
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);

        int color = ContextCompat.getColor(requireContext(), colorRes);
        window.setStatusBarColor(color);
        double darkness = 1 - (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255;
        if (darkness >= 0.5) {
            lightStatusBarWidgets();
        } else {
            darkStatusBarWidgets();
        }

    }

    public void lightStatusBarWidgets() {
        getView().post(() -> {
            try {
                Window window = requireActivity().getWindow();
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    window.getInsetsController().setSystemBarsAppearance(
                            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                    );
                    return;
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    int flags = window.getDecorView().getSystemUiVisibility();
                    flags = flags ^ View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                    window.getDecorView().setSystemUiVisibility(flags);
                }
            }catch (Exception ignore){

            }
        });
    }

    public void darkStatusBarWidgets() {
        getView().post(() -> {
            Window window = requireActivity().getWindow();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.getInsetsController().setSystemBarsAppearance(
                        0,
                        WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                );
                return;
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                int flags = window.getDecorView().getSystemUiVisibility();
                flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                window.getDecorView().setSystemUiVisibility(flags);
            }
        });
    }

    public void toast(String s, Object... args) {
        ViewUtil.toast(s, args);
    }

    public void toast(@StringRes int res, Object... args) {
        ViewUtil.toast(res, args);
    }


}
