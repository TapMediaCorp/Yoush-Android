package com.tapmedia.yoush.components;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Rect;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.TouchDelegate;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.core.widget.TextViewCompat;

import com.tapmedia.yoush.R;
import com.tapmedia.yoush.util.ServiceUtil;
import com.tapmedia.yoush.util.views.DarkOverflowToolbar;

public final class ContactFilterToolbar extends DarkOverflowToolbar {
  private   OnFilterChangedListener listener;

  private final EditText        searchText;
  private final AnimatingToggle toggle;
  private final ImageView       keyboardToggle;
  private final ImageView       dialpadToggle;
  private final ImageView       clearToggle;
  private final LinearLayout    toggleContainer;

  public ContactFilterToolbar(Context context) {
    this(context, null);
  }

  public ContactFilterToolbar(Context context, AttributeSet attrs) {
    this(context, attrs, R.attr.toolbarStyle);
  }

  public ContactFilterToolbar(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    inflate(context, R.layout.contact_filter_toolbar, this);

    this.searchText      = findViewById(R.id.search_view);
    this.toggle          = findViewById(R.id.button_toggle);
    this.keyboardToggle  = findViewById(R.id.search_keyboard);
    this.dialpadToggle   = findViewById(R.id.search_dialpad);
    this.clearToggle     = findViewById(R.id.search_clear);
    this.toggleContainer = findViewById(R.id.toggle_container);

    this.keyboardToggle.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        searchText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PERSON_NAME);
        ServiceUtil.getInputMethodManager(getContext()).showSoftInput(searchText, 0);
        displayTogglingView(dialpadToggle);
      }
    });

    this.dialpadToggle.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        searchText.setInputType(InputType.TYPE_CLASS_PHONE);
        ServiceUtil.getInputMethodManager(getContext()).showSoftInput(searchText, 0);
        displayTogglingView(keyboardToggle);
      }
    });

    this.clearToggle.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        searchText.setText("");

        if (SearchUtil.isTextInput(searchText)) displayTogglingView(dialpadToggle);
        else displayTogglingView(keyboardToggle);
      }
    });

    this.searchText.addTextChangedListener(new TextWatcher() {
      @Override
      public void beforeTextChanged(CharSequence s, int start, int count, int after) {

      }

      @Override
      public void onTextChanged(CharSequence s, int start, int before, int count) {

      }

      @Override
      public void afterTextChanged(Editable s) {
        if (!SearchUtil.isEmpty(searchText)) displayTogglingView(clearToggle);
        else if (SearchUtil.isTextInput(searchText)) displayTogglingView(dialpadToggle);
        else if (SearchUtil.isPhoneInput(searchText)) displayTogglingView(keyboardToggle);
        notifyListener();
      }
    });

    setLogo(null);
    setContentInsetStartWithNavigation(0);
    expandTapArea(toggleContainer, dialpadToggle);
    applyAttributes(searchText, context, attrs, defStyleAttr);
    searchText.requestFocus();
  }

  private void applyAttributes(@NonNull EditText searchText,
                               @NonNull Context context,
                               @NonNull AttributeSet attrs,
                               int defStyle)
  {
    final TypedArray attributes = context.obtainStyledAttributes(attrs,
                                                                 R.styleable.ContactFilterToolbar,
                                                                 defStyle,
                                                                 0);

    int styleResource = attributes.getResourceId(R.styleable.ContactFilterToolbar_searchTextStyle, -1);
    if (styleResource != -1) {
      TextViewCompat.setTextAppearance(searchText, styleResource);
    }
    if (!attributes.getBoolean(R.styleable.ContactFilterToolbar_showDialpad, true)) {
      dialpadToggle.setVisibility(GONE);
    }
    attributes.recycle();
  }

  public void clear() {
    searchText.setText("");
    notifyListener();
  }

  public void setOnFilterChangedListener(OnFilterChangedListener listener) {
    this.listener = listener;
  }

  public void setHint(@StringRes int hint) {
    searchText.setHint(hint);
  }

  private void notifyListener() {
    if (listener != null) listener.onFilterChanged(searchText.getText().toString());
  }

  private void displayTogglingView(View view) {
    toggle.display(view);
    expandTapArea(toggleContainer, view);
  }

  private void expandTapArea(final View container, final View child) {
    final int padding = getResources().getDimensionPixelSize(R.dimen.contact_selection_actions_tap_area);

    container.post(new Runnable() {
      @Override
      public void run() {
        Rect rect = new Rect();
        child.getHitRect(rect);

        rect.top -= padding;
        rect.left -= padding;
        rect.right += padding;
        rect.bottom += padding;

        container.setTouchDelegate(new TouchDelegate(rect, child));
      }
    });
  }

  private static class SearchUtil {
    static boolean isTextInput(EditText editText) {
      return (editText.getInputType() & InputType.TYPE_MASK_CLASS) == InputType.TYPE_CLASS_TEXT;
    }

    static boolean isPhoneInput(EditText editText) {
      return (editText.getInputType() & InputType.TYPE_MASK_CLASS) == InputType.TYPE_CLASS_PHONE;
    }

    public static boolean isEmpty(EditText editText) {
      return editText.getText().length() <= 0;
    }
  }

  public interface OnFilterChangedListener {
    void onFilterChanged(String filter);
  }
}
