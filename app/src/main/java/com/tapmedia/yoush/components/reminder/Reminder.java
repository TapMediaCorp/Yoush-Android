package com.tapmedia.yoush.components.reminder;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import android.view.View;
import android.view.View.OnClickListener;

import java.util.LinkedList;
import java.util.List;

public abstract class Reminder {
  private CharSequence title;
  private CharSequence text;

  private OnClickListener okListener;
  private OnClickListener dismissListener;

  private final List<Action> actions = new LinkedList<>();

  public Reminder(@Nullable CharSequence title,
                  @NonNull  CharSequence text)
  {
    this.title = title;
    this.text  = text;
  }

  public @Nullable CharSequence getTitle() {
    return title;
  }

  public CharSequence getText() {
    return text;
  }

  public OnClickListener getOkListener() {
    return okListener;
  }

  public OnClickListener getDismissListener() {
    return dismissListener;
  }

  public void setOkListener(OnClickListener okListener) {
    this.okListener = okListener;
  }

  public void setDismissListener(OnClickListener dismissListener) {
    this.dismissListener = dismissListener;
  }

  public boolean isDismissable() {
    return true;
  }

  public @NonNull Importance getImportance() {
    return Importance.NORMAL;
  }

  public void addAction(@NonNull Action action) {
    actions.add(action);
  }

  public List<Action> getActions() {
    return actions;
  }

  public int getProgress() {
    return -1;
  }

  public enum Importance {
    NORMAL, ERROR
  }

  public final class Action {
    private final CharSequence title;
    private final int          actionId;

    public Action(CharSequence title, @IdRes int actionId) {
      this.title    = title;
      this.actionId = actionId;
    }

    CharSequence getTitle() {
      return title;
    }

    int getActionId() {
      return actionId;
    }
  }
}
