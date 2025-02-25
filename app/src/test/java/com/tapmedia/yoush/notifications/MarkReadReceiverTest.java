package com.tapmedia.yoush.notifications;

import android.content.Context;

import androidx.annotation.NonNull;

import com.annimon.stream.Stream;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import com.tapmedia.yoush.database.MessagingDatabase;
import com.tapmedia.yoush.dependencies.ApplicationDependencies;
import com.tapmedia.yoush.jobmanager.Data;
import com.tapmedia.yoush.jobmanager.Job;
import com.tapmedia.yoush.jobmanager.JobManager;
import com.tapmedia.yoush.jobs.MultiDeviceReadUpdateJob;
import com.tapmedia.yoush.recipients.RecipientId;
import com.tapmedia.yoush.util.Util;
import org.whispersystems.libsignal.util.Pair;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.powermock.api.mockito.PowerMockito.doAnswer;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.mockStatic;
import static org.powermock.api.mockito.PowerMockito.when;

@RunWith(PowerMockRunner.class)
@PrepareForTest(ApplicationDependencies.class)
public class MarkReadReceiverTest {

  private final Context    mockContext    = mock(Context.class);
  private final JobManager mockJobManager = mock(JobManager.class);
  private final List<Job>  jobs           = new LinkedList<>();

  @Before
  public void setUp() {
    mockStatic(ApplicationDependencies.class);
    when(ApplicationDependencies.getJobManager()).thenReturn(mockJobManager);
    doAnswer((Answer<Void>) invocation -> {
      jobs.add((Job) invocation.getArguments()[0]);
      return null;
    }).when(mockJobManager).add(any());
  }

  @Test
  public void givenMultipleThreadsWithMultipleMessagesEach_whenIProcess_thenIProperlyGroupByThreadAndRecipient() {
    // GIVEN
    List<RecipientId> recipients = Stream.range(1L, 4L).map(RecipientId::from).toList();
    List<Long>        threads    = Stream.range(4L, 7L).toList();
    int               expected   = recipients.size() * threads.size() + 1;

    List<MessagingDatabase.MarkedMessageInfo> infoList = Stream.of(threads)
                                                               .flatMap(threadId -> Stream.of(recipients)
                                                                                          .map(recipientId -> createMarkedMessageInfo(threadId, recipientId)))
                                                               .toList();

    List<MessagingDatabase.MarkedMessageInfo> duplicatedList = Util.concatenatedList(infoList, infoList);

    // WHEN
    MarkReadReceiver.process(mockContext, duplicatedList);

    // THEN
    assertEquals("Should have 10 total jobs, including MultiDeviceReadUpdateJob", expected, jobs.size());

    Set<Pair<Long, String>> threadRecipientPairs = new HashSet<>();
    Stream.of(jobs).forEach(job -> {
      if (job instanceof MultiDeviceReadUpdateJob) {
        return;
      }

      Data data = job.serialize();

      long   threadId    = data.getLong("thread");
      String recipientId = data.getString("recipient");
      long[] messageIds  = data.getLongArray("message_ids");

      assertEquals("Each job should contain two messages.", 2, messageIds.length);
      assertTrue("Each thread recipient pair should only exist once.", threadRecipientPairs.add(new Pair<>(threadId, recipientId)));
    });

    assertEquals("Should have 9 total combinations.", 9, threadRecipientPairs.size());
  }

  private MessagingDatabase.MarkedMessageInfo createMarkedMessageInfo(long threadId, @NonNull RecipientId recipientId) {
    return new MessagingDatabase.MarkedMessageInfo(threadId,
                                                   new MessagingDatabase.SyncMessageId(recipientId, 0),
                                                   new MessagingDatabase.ExpirationInfo(0, 0, 0, false));
  }
}