package com.tapmedia.yoush.jobs;

import android.app.Application;

import androidx.annotation.NonNull;

import com.tapmedia.yoush.database.DatabaseFactory;
import com.tapmedia.yoush.jobmanager.Constraint;
import com.tapmedia.yoush.jobmanager.ConstraintObserver;
import com.tapmedia.yoush.jobmanager.Job;
import com.tapmedia.yoush.jobmanager.JobMigration;
import com.tapmedia.yoush.jobmanager.impl.CellServiceConstraintObserver;
import com.tapmedia.yoush.jobmanager.impl.NetworkConstraint;
import com.tapmedia.yoush.jobmanager.impl.NetworkConstraintObserver;
import com.tapmedia.yoush.jobmanager.impl.NetworkOrCellServiceConstraint;
import com.tapmedia.yoush.jobmanager.impl.SqlCipherMigrationConstraint;
import com.tapmedia.yoush.jobmanager.impl.SqlCipherMigrationConstraintObserver;
import com.tapmedia.yoush.jobmanager.impl.WebsocketDrainedConstraint;
import com.tapmedia.yoush.jobmanager.impl.WebsocketDrainedConstraintObserver;
import com.tapmedia.yoush.jobmanager.migrations.PushProcessMessageQueueJobMigration;
import com.tapmedia.yoush.jobmanager.migrations.RecipientIdFollowUpJobMigration;
import com.tapmedia.yoush.jobmanager.migrations.RecipientIdFollowUpJobMigration2;
import com.tapmedia.yoush.jobmanager.migrations.RecipientIdJobMigration;
import com.tapmedia.yoush.jobmanager.migrations.RetrieveProfileJobMigration;
import com.tapmedia.yoush.jobmanager.migrations.SendReadReceiptsJobMigration;
import com.tapmedia.yoush.migrations.AvatarIdRemovalMigrationJob;
import com.tapmedia.yoush.migrations.AvatarMigrationJob;
import com.tapmedia.yoush.migrations.CachedAttachmentsMigrationJob;
import com.tapmedia.yoush.migrations.DatabaseMigrationJob;
import com.tapmedia.yoush.migrations.LegacyMigrationJob;
import com.tapmedia.yoush.migrations.MigrationCompleteJob;
import com.tapmedia.yoush.migrations.PassingMigrationJob;
import com.tapmedia.yoush.migrations.PinReminderMigrationJob;
import com.tapmedia.yoush.migrations.ProfileMigrationJob;
import com.tapmedia.yoush.migrations.RecipientSearchMigrationJob;
import com.tapmedia.yoush.migrations.RegistrationPinV2MigrationJob;
import com.tapmedia.yoush.migrations.StickerAdditionMigrationJob;
import com.tapmedia.yoush.migrations.StickerLaunchMigrationJob;
import com.tapmedia.yoush.migrations.StorageCapabilityMigrationJob;
import com.tapmedia.yoush.migrations.StorageServiceMigrationJob;
import com.tapmedia.yoush.migrations.UuidMigrationJob;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class JobManagerFactories {

  public static Map<String, Job.Factory> getJobFactories(@NonNull Application application) {
    return new HashMap<String, Job.Factory>() {{
      put(AttachmentCopyJob.KEY,                     new AttachmentCopyJob.Factory());
      put(AttachmentDownloadJob.KEY,                 new AttachmentDownloadJob.Factory());
      put(AttachmentUploadJob.KEY,                   new AttachmentUploadJob.Factory());
      put(AttachmentMarkUploadedJob.KEY,             new AttachmentMarkUploadedJob.Factory());
      put(AttachmentCompressionJob.KEY,              new AttachmentCompressionJob.Factory());
      put(AvatarGroupsV1DownloadJob.KEY,             new AvatarGroupsV1DownloadJob.Factory());
      put(AvatarGroupsV2DownloadJob.KEY,             new AvatarGroupsV2DownloadJob.Factory());
      put(CleanPreKeysJob.KEY,                       new CleanPreKeysJob.Factory());
      put(CreateSignedPreKeyJob.KEY,                 new CreateSignedPreKeyJob.Factory());
      put(DirectoryRefreshJob.KEY,                   new DirectoryRefreshJob.Factory());
      put(FcmRefreshJob.KEY,                         new FcmRefreshJob.Factory());
      put(LeaveGroupJob.KEY,                         new LeaveGroupJob.Factory());
      put(LocalBackupJob.KEY,                        new LocalBackupJob.Factory());
      put(MmsDownloadJob.KEY,                        new MmsDownloadJob.Factory());
      put(MmsReceiveJob.KEY,                         new MmsReceiveJob.Factory());
      put(MmsSendJob.KEY,                            new MmsSendJob.Factory());
      put(MultiDeviceBlockedUpdateJob.KEY,           new MultiDeviceBlockedUpdateJob.Factory());
      put(MultiDeviceConfigurationUpdateJob.KEY,     new MultiDeviceConfigurationUpdateJob.Factory());
      put(MultiDeviceContactUpdateJob.KEY,           new MultiDeviceContactUpdateJob.Factory());
      put(MultiDeviceGroupUpdateJob.KEY,             new MultiDeviceGroupUpdateJob.Factory());
      put(MultiDeviceKeysUpdateJob.KEY,              new MultiDeviceKeysUpdateJob.Factory());
      put(MultiDeviceMessageRequestResponseJob.KEY,  new MultiDeviceMessageRequestResponseJob.Factory());
      put(MultiDeviceProfileContentUpdateJob.KEY,    new MultiDeviceProfileContentUpdateJob.Factory());
      put(MultiDeviceProfileKeyUpdateJob.KEY,        new MultiDeviceProfileKeyUpdateJob.Factory());
      put(MultiDeviceReadUpdateJob.KEY,              new MultiDeviceReadUpdateJob.Factory());
      put(MultiDeviceStickerPackOperationJob.KEY,    new MultiDeviceStickerPackOperationJob.Factory());
      put(MultiDeviceStickerPackSyncJob.KEY,         new MultiDeviceStickerPackSyncJob.Factory());
      put(MultiDeviceStorageSyncRequestJob.KEY,      new MultiDeviceStorageSyncRequestJob.Factory());
      put(MultiDeviceVerifiedUpdateJob.KEY,          new MultiDeviceVerifiedUpdateJob.Factory());
      put(MultiDeviceViewOnceOpenJob.KEY,            new MultiDeviceViewOnceOpenJob.Factory());
      put(ProfileKeySendJob.KEY,                     new ProfileKeySendJob.Factory());
      put(PushDecryptMessageJob.KEY,                 new PushDecryptMessageJob.Factory());
      put(PushProcessMessageJob.KEY,                 new PushProcessMessageJob.Factory());
      put(PushGroupSendJob.KEY,                      new PushGroupSendJob.Factory());
      put(PushGroupUpdateJob.KEY,                    new PushGroupUpdateJob.Factory());
      put(PushMediaSendJob.KEY,                      new PushMediaSendJob.Factory());
      put(PushNotificationReceiveJob.KEY,            new PushNotificationReceiveJob.Factory());
      put(PushTextSendJob.KEY,                       new PushTextSendJob.Factory());
      put(ReactionSendJob.KEY,                       new ReactionSendJob.Factory());
      put(RefreshAttributesJob.KEY,                  new RefreshAttributesJob.Factory());
      put(RefreshOwnProfileJob.KEY,                  new RefreshOwnProfileJob.Factory());
      put(RefreshPreKeysJob.KEY,                     new RefreshPreKeysJob.Factory());
      put(RemoteConfigRefreshJob.KEY,                new RemoteConfigRefreshJob.Factory());
      put(RemoteDeleteSendJob.KEY,                   new RemoteDeleteSendJob.Factory());
      put(RequestGroupInfoJob.KEY,                   new RequestGroupInfoJob.Factory());
      put(ResumableUploadSpecJob.KEY,                new ResumableUploadSpecJob.Factory());
      put(StorageAccountRestoreJob.KEY,              new StorageAccountRestoreJob.Factory());
      put(RequestGroupV2InfoJob.KEY,                 new RequestGroupV2InfoJob.Factory());
      put(WakeGroupV2Job.KEY,                        new WakeGroupV2Job.Factory());
      put(GroupV2UpdateSelfProfileKeyJob.KEY,        new GroupV2UpdateSelfProfileKeyJob.Factory());
      put(RetrieveProfileAvatarJob.KEY,              new RetrieveProfileAvatarJob.Factory());
      put(RetrieveProfileJob.KEY,                    new RetrieveProfileJob.Factory());
      put(RotateCertificateJob.KEY,                  new RotateCertificateJob.Factory());
      put(RotateProfileKeyJob.KEY,                   new RotateProfileKeyJob.Factory());
      put(RotateSignedPreKeyJob.KEY,                 new RotateSignedPreKeyJob.Factory());
      put(SendDeliveryReceiptJob.KEY,                new SendDeliveryReceiptJob.Factory());
      put(SendReadReceiptJob.KEY,                    new SendReadReceiptJob.Factory(application));
      put(ServiceOutageDetectionJob.KEY,             new ServiceOutageDetectionJob.Factory());
      put(SmsReceiveJob.KEY,                         new SmsReceiveJob.Factory());
      put(SmsSendJob.KEY,                            new SmsSendJob.Factory());
      put(SmsSentJob.KEY,                            new SmsSentJob.Factory());
      put(StickerDownloadJob.KEY,                    new StickerDownloadJob.Factory());
      put(StickerPackDownloadJob.KEY,                new StickerPackDownloadJob.Factory());
      put(StorageForcePushJob.KEY,                   new StorageForcePushJob.Factory());
      put(StorageSyncJob.KEY,                        new StorageSyncJob.Factory());
      put(TrimThreadJob.KEY,                         new TrimThreadJob.Factory());
      put(TypingSendJob.KEY,                         new TypingSendJob.Factory());
      put(UpdateApkJob.KEY,                          new UpdateApkJob.Factory());
      put(MarkerJob.KEY,                             new MarkerJob.Factory());
      put(ProfileUploadJob.KEY,                      new ProfileUploadJob.Factory());

      // Migrations
      put(AvatarIdRemovalMigrationJob.KEY,           new AvatarIdRemovalMigrationJob.Factory());
      put(AvatarMigrationJob.KEY,                    new AvatarMigrationJob.Factory());
      put(CachedAttachmentsMigrationJob.KEY,         new CachedAttachmentsMigrationJob.Factory());
      put(DatabaseMigrationJob.KEY,                  new DatabaseMigrationJob.Factory());
      put(LegacyMigrationJob.KEY,                    new LegacyMigrationJob.Factory());
      put(MigrationCompleteJob.KEY,                  new MigrationCompleteJob.Factory());
      put(PinReminderMigrationJob.KEY,               new PinReminderMigrationJob.Factory());
      put(RecipientSearchMigrationJob.KEY,           new RecipientSearchMigrationJob.Factory());
      put(RegistrationPinV2MigrationJob.KEY,         new RegistrationPinV2MigrationJob.Factory());
      put(StickerLaunchMigrationJob.KEY,             new StickerLaunchMigrationJob.Factory());
      put(StickerAdditionMigrationJob.KEY,           new StickerAdditionMigrationJob.Factory());
      put(StorageCapabilityMigrationJob.KEY,         new StorageCapabilityMigrationJob.Factory());
      put(StorageServiceMigrationJob.KEY,            new StorageServiceMigrationJob.Factory());
      put(UuidMigrationJob.KEY,                      new UuidMigrationJob.Factory());
      put(ProfileMigrationJob.KEY,                   new ProfileMigrationJob.Factory());

      // Dead jobs
      put(FailingJob.KEY,                            new FailingJob.Factory());
      put(PassingMigrationJob.KEY,                   new PassingMigrationJob.Factory());
      put("PushContentReceiveJob",                   new FailingJob.Factory());
      put("AttachmentUploadJob",                     new FailingJob.Factory());
      put("MmsSendJob",                              new FailingJob.Factory());
      put("RefreshUnidentifiedDeliveryAbilityJob",   new FailingJob.Factory());
      put("Argon2TestJob",                           new FailingJob.Factory());
      put("Argon2TestMigrationJob",                  new PassingMigrationJob.Factory());
      put("StorageKeyRotationMigrationJob",          new PassingMigrationJob.Factory());
    }};
  }

  public static Map<String, Constraint.Factory> getConstraintFactories(@NonNull Application application) {
    return new HashMap<String, Constraint.Factory>() {{
      put(NetworkConstraint.KEY,                     new NetworkConstraint.Factory(application));
      put(NetworkOrCellServiceConstraint.KEY,        new NetworkOrCellServiceConstraint.Factory(application));
      put(NetworkOrCellServiceConstraint.LEGACY_KEY, new NetworkOrCellServiceConstraint.Factory(application));
      put(SqlCipherMigrationConstraint.KEY,          new SqlCipherMigrationConstraint.Factory(application));
      put(WebsocketDrainedConstraint.KEY,            new WebsocketDrainedConstraint.Factory());
    }};
  }

  public static List<ConstraintObserver> getConstraintObservers(@NonNull Application application) {
    return Arrays.asList(CellServiceConstraintObserver.getInstance(application),
                         new NetworkConstraintObserver(application),
                         new SqlCipherMigrationConstraintObserver(),
                         new WebsocketDrainedConstraintObserver());
  }

  public static List<JobMigration> getJobMigrations(@NonNull Application application) {
    return Arrays.asList(new RecipientIdJobMigration(application),
                         new RecipientIdFollowUpJobMigration(),
                         new RecipientIdFollowUpJobMigration2(),
                         new SendReadReceiptsJobMigration(DatabaseFactory.getMmsSmsDatabase(application)),
                         new PushProcessMessageQueueJobMigration(application),
                         new RetrieveProfileJobMigration());
  }
}
