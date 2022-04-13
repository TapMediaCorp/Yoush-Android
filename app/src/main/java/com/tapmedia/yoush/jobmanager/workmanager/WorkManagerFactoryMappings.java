package com.tapmedia.yoush.jobmanager.workmanager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.tapmedia.yoush.jobs.AttachmentDownloadJob;
import com.tapmedia.yoush.jobs.AttachmentUploadJob;
import com.tapmedia.yoush.jobs.AvatarGroupsV1DownloadJob;
import com.tapmedia.yoush.jobs.CleanPreKeysJob;
import com.tapmedia.yoush.jobs.CreateSignedPreKeyJob;
import com.tapmedia.yoush.jobs.DirectoryRefreshJob;
import com.tapmedia.yoush.jobs.FailingJob;
import com.tapmedia.yoush.jobs.FcmRefreshJob;
import com.tapmedia.yoush.jobs.LocalBackupJob;
import com.tapmedia.yoush.jobs.MmsDownloadJob;
import com.tapmedia.yoush.jobs.MmsReceiveJob;
import com.tapmedia.yoush.jobs.MmsSendJob;
import com.tapmedia.yoush.jobs.MultiDeviceBlockedUpdateJob;
import com.tapmedia.yoush.jobs.MultiDeviceConfigurationUpdateJob;
import com.tapmedia.yoush.jobs.MultiDeviceContactUpdateJob;
import com.tapmedia.yoush.jobs.MultiDeviceGroupUpdateJob;
import com.tapmedia.yoush.jobs.MultiDeviceProfileKeyUpdateJob;
import com.tapmedia.yoush.jobs.MultiDeviceReadUpdateJob;
import com.tapmedia.yoush.jobs.MultiDeviceVerifiedUpdateJob;
import com.tapmedia.yoush.jobs.PushDecryptMessageJob;
import com.tapmedia.yoush.jobs.PushGroupSendJob;
import com.tapmedia.yoush.jobs.PushGroupUpdateJob;
import com.tapmedia.yoush.jobs.PushMediaSendJob;
import com.tapmedia.yoush.jobs.PushNotificationReceiveJob;
import com.tapmedia.yoush.jobs.PushTextSendJob;
import com.tapmedia.yoush.jobs.RefreshAttributesJob;
import com.tapmedia.yoush.jobs.RefreshPreKeysJob;
import com.tapmedia.yoush.jobs.RequestGroupInfoJob;
import com.tapmedia.yoush.jobs.RetrieveProfileAvatarJob;
import com.tapmedia.yoush.jobs.RetrieveProfileJob;
import com.tapmedia.yoush.jobs.RotateCertificateJob;
import com.tapmedia.yoush.jobs.RotateProfileKeyJob;
import com.tapmedia.yoush.jobs.RotateSignedPreKeyJob;
import com.tapmedia.yoush.jobs.SendDeliveryReceiptJob;
import com.tapmedia.yoush.jobs.SendReadReceiptJob;
import com.tapmedia.yoush.jobs.ServiceOutageDetectionJob;
import com.tapmedia.yoush.jobs.SmsReceiveJob;
import com.tapmedia.yoush.jobs.SmsSendJob;
import com.tapmedia.yoush.jobs.SmsSentJob;
import com.tapmedia.yoush.jobs.TrimThreadJob;
import com.tapmedia.yoush.jobs.TypingSendJob;
import com.tapmedia.yoush.jobs.UpdateApkJob;

import java.util.HashMap;
import java.util.Map;

public class WorkManagerFactoryMappings {

  private static final Map<String, String> FACTORY_MAP = new HashMap<String, String>() {{
    put("AttachmentDownloadJob", AttachmentDownloadJob.KEY);
    put("AttachmentUploadJob", AttachmentUploadJob.KEY);
    put("AvatarDownloadJob", AvatarGroupsV1DownloadJob.KEY);
    put("CleanPreKeysJob", CleanPreKeysJob.KEY);
    put("CreateSignedPreKeyJob", CreateSignedPreKeyJob.KEY);
    put("DirectoryRefreshJob", DirectoryRefreshJob.KEY);
    put("FcmRefreshJob", FcmRefreshJob.KEY);
    put("LocalBackupJob", LocalBackupJob.KEY);
    put("MmsDownloadJob", MmsDownloadJob.KEY);
    put("MmsReceiveJob", MmsReceiveJob.KEY);
    put("MmsSendJob", MmsSendJob.KEY);
    put("MultiDeviceBlockedUpdateJob", MultiDeviceBlockedUpdateJob.KEY);
    put("MultiDeviceConfigurationUpdateJob", MultiDeviceConfigurationUpdateJob.KEY);
    put("MultiDeviceContactUpdateJob", MultiDeviceContactUpdateJob.KEY);
    put("MultiDeviceGroupUpdateJob", MultiDeviceGroupUpdateJob.KEY);
    put("MultiDeviceProfileKeyUpdateJob", MultiDeviceProfileKeyUpdateJob.KEY);
    put("MultiDeviceReadUpdateJob", MultiDeviceReadUpdateJob.KEY);
    put("MultiDeviceVerifiedUpdateJob", MultiDeviceVerifiedUpdateJob.KEY);
    put("PushContentReceiveJob", FailingJob.KEY);
    put("PushDecryptJob", PushDecryptMessageJob.KEY);
    put("PushGroupSendJob", PushGroupSendJob.KEY);
    put("PushGroupUpdateJob", PushGroupUpdateJob.KEY);
    put("PushMediaSendJob", PushMediaSendJob.KEY);
    put("PushNotificationReceiveJob", PushNotificationReceiveJob.KEY);
    put("PushTextSendJob", PushTextSendJob.KEY);
    put("RefreshAttributesJob", RefreshAttributesJob.KEY);
    put("RefreshPreKeysJob", RefreshPreKeysJob.KEY);
    put("RefreshUnidentifiedDeliveryAbilityJob", FailingJob.KEY);
    put("RequestGroupInfoJob", RequestGroupInfoJob.KEY);
    put("RetrieveProfileAvatarJob", RetrieveProfileAvatarJob.KEY);
    put("RetrieveProfileJob", RetrieveProfileJob.KEY);
    put("RotateCertificateJob", RotateCertificateJob.KEY);
    put("RotateProfileKeyJob", RotateProfileKeyJob.KEY);
    put("RotateSignedPreKeyJob", RotateSignedPreKeyJob.KEY);
    put("SendDeliveryReceiptJob", SendDeliveryReceiptJob.KEY);
    put("SendReadReceiptJob", SendReadReceiptJob.KEY);
    put("ServiceOutageDetectionJob", ServiceOutageDetectionJob.KEY);
    put("SmsReceiveJob", SmsReceiveJob.KEY);
    put("SmsSendJob", SmsSendJob.KEY);
    put("SmsSentJob", SmsSentJob.KEY);
    put("TrimThreadJob", TrimThreadJob.KEY);
    put("TypingSendJob", TypingSendJob.KEY);
    put("UpdateApkJob", UpdateApkJob.KEY);
  }};

  public static @Nullable String getFactoryKey(@NonNull String workManagerClass) {
    return FACTORY_MAP.get(workManagerClass);
  }
}
