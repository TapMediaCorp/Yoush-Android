package com.tapmedia.yoush.jobmanager.migrations;

import org.junit.Test;
import com.tapmedia.yoush.database.MmsSmsDatabase;
import com.tapmedia.yoush.jobmanager.Data;
import com.tapmedia.yoush.jobmanager.JobMigration;
import com.tapmedia.yoush.jobs.SendReadReceiptJob;
import com.tapmedia.yoush.recipients.RecipientId;

import java.util.ArrayList;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class SendReadReceiptsJobMigrationTest {

  private final MmsSmsDatabase               mockDatabase = mock(MmsSmsDatabase.class);
  private final SendReadReceiptsJobMigration testSubject  = new SendReadReceiptsJobMigration(mockDatabase);

  @Test
  public void givenSendReadReceiptJobDataWithoutThreadIdAndThreadIdFound_whenIMigrate_thenIInsertThreadId() {
    // GIVEN
    SendReadReceiptJob   job     = new SendReadReceiptJob(1, RecipientId.from(2), new ArrayList<>());
    JobMigration.JobData jobData = new JobMigration.JobData(job.getFactoryKey(),
                                                            "asdf",
                                                            new Data.Builder()
                                                                    .putString("recipient", RecipientId.from(2).serialize())
                                                                    .putLongArray("message_ids", new long[]{1, 2, 3, 4, 5})
                                                                    .putLong("timestamp", 292837649).build());
    when(mockDatabase.getThreadForMessageId(anyLong())).thenReturn(1234L);

    // WHEN
    JobMigration.JobData result = testSubject.migrate(jobData);

    // THEN
    assertEquals(1234L, result.getData().getLong("thread"));
    assertEquals(RecipientId.from(2).serialize(), result.getData().getString("recipient"));
    assertTrue(result.getData().hasLongArray("message_ids"));
    assertTrue(result.getData().hasLong("timestamp"));
  }

  @Test
  public void givenSendReadReceiptJobDataWithoutThreadIdAndThreadIdNotFound_whenIMigrate_thenIGetAFailingJob() {
    // GIVEN
    SendReadReceiptJob   job     = new SendReadReceiptJob(1, RecipientId.from(2), new ArrayList<>());
    JobMigration.JobData jobData = new JobMigration.JobData(job.getFactoryKey(),
                                                            "asdf",
                                                            new Data.Builder()
                                                                .putString("recipient", RecipientId.from(2).serialize())
                                                                .putLongArray("message_ids", new long[]{})
                                                                .putLong("timestamp", 292837649).build());
    when(mockDatabase.getThreadForMessageId(anyLong())).thenReturn(-1L);

    // WHEN
    JobMigration.JobData result = testSubject.migrate(jobData);

    // THEN
    assertEquals("FailingJob", result.getFactoryKey());
  }

  @Test
  public void givenSendReadReceiptJobDataWithThreadId_whenIMigrate_thenIDoNotReplace() {
    // GIVEN
    SendReadReceiptJob   job     = new SendReadReceiptJob(1, RecipientId.from(2), new ArrayList<>());
    JobMigration.JobData jobData = new JobMigration.JobData(job.getFactoryKey(), "asdf", job.serialize());

    // WHEN
    JobMigration.JobData result = testSubject.migrate(jobData);

    // THEN
    assertEquals(jobData, result);
  }

  @Test
  public void givenSomeOtherJobDataWithThreadId_whenIMigrate_thenIDoNotReplace() {
    // GIVEN
    JobMigration.JobData jobData = new JobMigration.JobData("SomeOtherJob", "asdf", new Data.Builder().putLong("thread", 1).build());

    // WHEN
    JobMigration.JobData result = testSubject.migrate(jobData);

    // THEN
    assertEquals(jobData, result);
  }

  @Test
  public void givenSomeOtherJobDataWithoutThreadId_whenIMigrate_thenIDoNotReplace() {
    // GIVEN
    JobMigration.JobData jobData = new JobMigration.JobData("SomeOtherJob", "asdf", new Data.Builder().build());

    // WHEN
    JobMigration.JobData result = testSubject.migrate(jobData);

    // THEN
    assertEquals(jobData, result);
  }


}