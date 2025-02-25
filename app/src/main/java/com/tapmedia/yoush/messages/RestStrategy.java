package com.tapmedia.yoush.messages;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import com.tapmedia.yoush.dependencies.ApplicationDependencies;
import com.tapmedia.yoush.jobmanager.JobManager;
import com.tapmedia.yoush.jobmanager.JobTracker;
import com.tapmedia.yoush.jobs.MarkerJob;
import com.tapmedia.yoush.jobs.PushDecryptMessageJob;
import com.tapmedia.yoush.jobs.PushProcessMessageJob;
import com.tapmedia.yoush.logging.Log;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.SignalServiceMessageReceiver;

import java.io.IOException;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Retrieves messages over the REST endpoint.
 */
public class RestStrategy extends MessageRetrievalStrategy {

  private static final String TAG = Log.tag(RestStrategy.class);

  @WorkerThread
  @Override
  public boolean execute(long timeout) {
    long                    startTime     = System.currentTimeMillis();
    JobManager              jobManager    = ApplicationDependencies.getJobManager();
    QueueFindingJobListener queueListener = new QueueFindingJobListener();

    try (IncomingMessageProcessor.Processor processor = ApplicationDependencies.getIncomingMessageProcessor().acquire()) {
      jobManager.addListener(job -> job.getParameters().getQueue() != null && job.getParameters().getQueue().startsWith(PushProcessMessageJob.QUEUE_PREFIX), queueListener);

      int jobCount = enqueuePushDecryptJobs(processor, startTime, timeout);

      if (jobCount == 0) {
        Log.d(TAG, "No PushDecryptMessageJobs were enqueued.");
        return true;
      } else {
        Log.d(TAG, jobCount + " PushDecryptMessageJob(s) were enqueued.");
      }

      long        timeRemainingMs = blockUntilQueueDrained(PushDecryptMessageJob.QUEUE, TimeUnit.SECONDS.toMillis(10));
      Set<String> processQueues   = queueListener.getQueues();

      Log.d(TAG, "Discovered " + processQueues.size() + " queue(s): " + processQueues);

      if (timeRemainingMs > 0) {
        Iterator<String> iter = processQueues.iterator();

        while (iter.hasNext() && timeRemainingMs > 0) {
          timeRemainingMs = blockUntilQueueDrained(iter.next(), timeRemainingMs);
        }

        if (timeRemainingMs <= 0) {
          Log.w(TAG, "Ran out of time while waiting for queues to drain.");
        }
      } else {
        Log.w(TAG, "Ran out of time before we could even wait on individual queues!");
      }

      return true;
    } catch (IOException e) {
      Log.w(TAG, "Failed to retrieve messages. Resetting the SignalServiceMessageReceiver.", e);
      ApplicationDependencies.resetSignalServiceMessageReceiver();
      return false;
    } finally {
      jobManager.removeListener(queueListener);
    }
  }

  private static int enqueuePushDecryptJobs(IncomingMessageProcessor.Processor processor, long startTime, long timeout)
      throws IOException
  {
    SignalServiceMessageReceiver receiver = ApplicationDependencies.getSignalServiceMessageReceiver();
    AtomicInteger                jobCount = new AtomicInteger(0);

    receiver.setSoTimeoutMillis(timeout);

    receiver.retrieveMessages(envelope -> {
      Log.i(TAG, "Retrieved an envelope." + timeSuffix(startTime));
      String jobId = processor.processEnvelope(envelope);

      if (jobId != null) {
        jobCount.incrementAndGet();
      }
      Log.i(TAG, "Successfully processed an envelope." + timeSuffix(startTime));
    });

    return jobCount.get();
  }

  private static long blockUntilQueueDrained(@NonNull String queue, long timeoutMs) {
    long             startTime  = System.currentTimeMillis();
    final JobManager jobManager = ApplicationDependencies.getJobManager();
    final MarkerJob  markerJob  = new MarkerJob(queue);

    Optional<JobTracker.JobState> jobState = jobManager.runSynchronously(markerJob, timeoutMs);

    if (!jobState.isPresent()) {
      Log.w(TAG, "Timed out waiting for " + queue + " job(s) to finish!");
    }

    long endTime  = System.currentTimeMillis();
    long duration = endTime - startTime;

    Log.d(TAG, "Waited " + duration + " ms for the " + queue + " job(s) to finish.");
    return timeoutMs - duration;
  }

  @Override
  public @NonNull String toString() {
    return RestStrategy.class.getSimpleName();
  }
}
