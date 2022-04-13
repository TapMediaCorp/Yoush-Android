package com.tapmedia.yoush.database.loaders;

import android.content.Context;
import android.database.Cursor;

import com.tapmedia.yoush.database.DatabaseFactory;
import com.tapmedia.yoush.util.AbstractCursorLoader;

public class BlockedContactsLoader extends AbstractCursorLoader {

  public BlockedContactsLoader(Context context) {
    super(context);
  }

  @Override
  public Cursor getCursor() {
    return DatabaseFactory.getRecipientDatabase(getContext()).getBlocked();
  }

}
