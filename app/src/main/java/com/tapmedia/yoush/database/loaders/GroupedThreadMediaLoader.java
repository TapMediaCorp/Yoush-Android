package com.tapmedia.yoush.database.loaders;

import android.content.Context;
import android.database.ContentObserver;
import android.database.Cursor;
import android.util.SparseArray;

import androidx.annotation.NonNull;
import androidx.loader.content.AsyncTaskLoader;

import com.tapmedia.yoush.R;
import com.tapmedia.yoush.database.DatabaseFactory;
import com.tapmedia.yoush.database.MediaDatabase;
import com.tapmedia.yoush.logging.Log;
import com.tapmedia.yoush.util.CalendarDateOnly;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

public final class GroupedThreadMediaLoader extends AsyncTaskLoader<GroupedThreadMediaLoader.GroupedThreadMedia> {

  @SuppressWarnings("unused")
  private static final String TAG = Log.tag(GroupedThreadMediaLoader.class);

  private final ContentObserver       observer;
  private final MediaLoader.MediaType mediaType;
  private final MediaDatabase.Sorting sorting;
  private final long                  threadId;

  public GroupedThreadMediaLoader(@NonNull Context context,
                                  long threadId,
                                  @NonNull MediaLoader.MediaType mediaType,
                                  @NonNull MediaDatabase.Sorting sorting)
  {
    super(context);
    this.threadId  = threadId;
    this.mediaType = mediaType;
    this.sorting   = sorting;
    this.observer  = new ForceLoadContentObserver();

    onContentChanged();
  }

  @Override
  protected void onStartLoading() {
    if (takeContentChanged()) {
      forceLoad();
    }
  }

  @Override
  protected void onStopLoading() {
    cancelLoad();
  }

  @Override
  protected void onAbandon() {
    DatabaseFactory.getMediaDatabase(getContext()).unsubscribeToMediaChanges(observer);
  }

  @Override
  public GroupedThreadMedia loadInBackground() {
    Context        context        = getContext();
    GroupingMethod groupingMethod = sorting.isRelatedToFileSize()
                                    ? new RoughSizeGroupingMethod(context)
                                    : new DateGroupingMethod(context, CalendarDateOnly.getInstance());

    PopulatedGroupedThreadMedia mediaGrouping = new PopulatedGroupedThreadMedia(groupingMethod);

    DatabaseFactory.getMediaDatabase(context).subscribeToMediaChanges(observer);
    try (Cursor cursor = ThreadMediaLoader.createThreadMediaCursor(context, threadId, mediaType, sorting)) {
      while (cursor != null && cursor.moveToNext()) {
        mediaGrouping.add(MediaDatabase.MediaRecord.from(context, cursor));
      }
    }

    if (sorting == MediaDatabase.Sorting.Oldest || sorting == MediaDatabase.Sorting.Largest) {
      return new ReversedGroupedThreadMedia(mediaGrouping);
    } else {
      return mediaGrouping;
    }
  }

  public interface GroupingMethod {

   int groupForRecord(@NonNull MediaDatabase.MediaRecord mediaRecord);

   @NonNull String groupName(int groupNo);
  }

  public static class DateGroupingMethod implements GroupingMethod {

    private final Context context;
    private final long    yesterdayStart;
    private final long    todayStart;
    private final long    thisWeekStart;
    private final long    thisMonthStart;

    private static final int TODAY      = Integer.MIN_VALUE;
    private static final int YESTERDAY  = Integer.MIN_VALUE + 1;
    private static final int THIS_WEEK  = Integer.MIN_VALUE + 2;
    private static final int THIS_MONTH = Integer.MIN_VALUE + 3;

    DateGroupingMethod(@NonNull Context context, @NonNull Calendar today) {
      this.context = context;
      todayStart     = today.getTimeInMillis();
      yesterdayStart = getTimeInMillis(today, Calendar.DAY_OF_YEAR, -1);
      thisWeekStart  = getTimeInMillis(today, Calendar.DAY_OF_YEAR, -6);
      thisMonthStart = getTimeInMillis(today, Calendar.DAY_OF_YEAR, -30);
    }

    private static long getTimeInMillis(@NonNull Calendar now, int field, int offset) {
      Calendar copy = (Calendar) now.clone();
      copy.add(field, offset);
      return copy.getTimeInMillis();
    }

    @Override
    public int groupForRecord(@NonNull MediaDatabase.MediaRecord mediaRecord) {
      long date = mediaRecord.getDate();

      if (date > todayStart)     return TODAY;
      if (date > yesterdayStart) return YESTERDAY;
      if (date > thisWeekStart)  return THIS_WEEK;
      if (date > thisMonthStart) return THIS_MONTH;

      Calendar calendar = Calendar.getInstance();
      calendar.setTimeInMillis(date);

      int year  = calendar.get(Calendar.YEAR);
      int month = calendar.get(Calendar.MONTH);

      return -(year * 12 + month);
    }

    @Override
    public @NonNull String groupName(int groupNo) {
      switch (groupNo) {
        case TODAY:
          return context.getString(R.string.BucketedThreadMedia_Today);
        case YESTERDAY:
          return context.getString(R.string.BucketedThreadMedia_Yesterday);
        case THIS_WEEK:
          return context.getString(R.string.BucketedThreadMedia_This_week);
        case THIS_MONTH:
          return context.getString(R.string.BucketedThreadMedia_This_month);
        default:
          int yearAndMonth = -groupNo;
          int month        = yearAndMonth % 12;
          int year         = yearAndMonth / 12;

          Calendar calendar = Calendar.getInstance();
          calendar.set(Calendar.YEAR, year);
          calendar.set(Calendar.MONTH, month);

          return new SimpleDateFormat("MMMM, yyyy", Locale.getDefault()).format(calendar.getTime());
      }
    }
  }

  public static class RoughSizeGroupingMethod implements GroupingMethod {

    private final String largeDescription;
    private final String mediumDescription;
    private final String smallDescription;

    private static final int MB     = 1024 * 1024;
    private static final int SMALL  = 0;
    private static final int MEDIUM = 1;
    private static final int LARGE  = 2;

    RoughSizeGroupingMethod(@NonNull Context context) {
      smallDescription  = context.getString(R.string.BucketedThreadMedia_Small);
      mediumDescription = context.getString(R.string.BucketedThreadMedia_Medium);
      largeDescription  = context.getString(R.string.BucketedThreadMedia_Large);
    }

    @Override
    public int groupForRecord(@NonNull MediaDatabase.MediaRecord mediaRecord) {
      long size = mediaRecord.getAttachment().getSize();

      if (size < MB)      return SMALL;
      if (size < 20 * MB) return MEDIUM;

      return LARGE;
    }

    @Override
    public @NonNull String groupName(int groupNo) {
      switch (groupNo) {
        case SMALL : return smallDescription;
        case MEDIUM: return mediumDescription;
        case LARGE : return largeDescription;
        default: throw new AssertionError();
      }
    }
  }

  public static abstract class GroupedThreadMedia {

    public abstract int getSectionCount();

    public abstract int getSectionItemCount(int section);

    public abstract @NonNull MediaDatabase.MediaRecord get(int section, int item);

    public abstract @NonNull String getName(int section);

  }

  public static class EmptyGroupedThreadMedia extends GroupedThreadMedia {

    @Override
    public int getSectionCount() {
      return 0;
    }

    @Override
    public int getSectionItemCount(int section) {
      return 0;
    }

    @Override
    public @NonNull MediaDatabase.MediaRecord get(int section, int item) {
      throw new AssertionError();
    }

    @Override
    public @NonNull String getName(int section) {
      throw new AssertionError();
    }
  }

  public static class ReversedGroupedThreadMedia extends GroupedThreadMedia {

    private final GroupedThreadMedia decorated;

    ReversedGroupedThreadMedia(@NonNull GroupedThreadMedia decorated) {
      this.decorated = decorated;
    }

    @Override
    public int getSectionCount() {
      return decorated.getSectionCount();
    }

    @Override
    public int getSectionItemCount(int section) {
      return decorated.getSectionItemCount(getReversedSection(section));
    }

    @Override
    public @NonNull MediaDatabase.MediaRecord get(int section, int item) {
      return decorated.get(getReversedSection(section), item);
    }

    @Override
    public @NonNull String getName(int section) {
      return decorated.getName(getReversedSection(section));
    }

    private int getReversedSection(int section) {
      return decorated.getSectionCount() - 1 - section;
    }
  }

  private static class PopulatedGroupedThreadMedia extends GroupedThreadMedia {

    @NonNull
    private final GroupingMethod groupingMethod;

    private final SparseArray<List<MediaDatabase.MediaRecord>> records = new SparseArray<>();

    private PopulatedGroupedThreadMedia(@NonNull GroupingMethod groupingMethod) {
      this.groupingMethod = groupingMethod;
    }

    private void add(@NonNull MediaDatabase.MediaRecord mediaRecord) {
      int groupNo = groupingMethod.groupForRecord(mediaRecord);

      List<MediaDatabase.MediaRecord> mediaRecords = records.get(groupNo);
      if (mediaRecords == null) {
        mediaRecords = new LinkedList<>();
        records.put(groupNo, mediaRecords);
      }

      mediaRecords.add(mediaRecord);
    }

    @Override
    public int getSectionCount() {
      return records.size();
    }

    @Override
    public int getSectionItemCount(int section) {
      return records.get(records.keyAt(section)).size();
    }

    @Override
    public @NonNull MediaDatabase.MediaRecord get(int section, int item) {
      return records.get(records.keyAt(section)).get(item);
    }

    @Override
    public @NonNull String getName(int section) {
      return groupingMethod.groupName(records.keyAt(section));
    }
  }
}
