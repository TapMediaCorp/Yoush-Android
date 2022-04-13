package com.tapmedia.yoush.util;

import android.content.res.Resources;

import androidx.annotation.ColorRes;
import androidx.annotation.PluralsRes;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;

import com.tapmedia.yoush.ApplicationContext;


public class Res {

    public static String quantityStr(@PluralsRes int id, int quantity, Object... formatArgs)
            throws Resources.NotFoundException {
        return ApplicationContext.getInstance().getResources().getQuantityString(id, quantity, formatArgs);
    }

    public static String str(@StringRes int id, Object... formatArgs)
            throws Resources.NotFoundException {
        return ApplicationContext.getInstance().getString(id, formatArgs);
    }

    public static int color(@ColorRes int res) {
        return ContextCompat.getColor(ApplicationContext.getInstance(), res);
    }
}
