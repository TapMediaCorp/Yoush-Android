/**
 * Copyright (C) 2014-2016 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */

package org.whispersystems.signalservice.internal.push;


import com.fasterxml.jackson.annotation.JsonProperty;

public class OutgoingPushMessage {

  @JsonProperty
  private int    type;
  @JsonProperty
  private int    destinationDeviceId;
  @JsonProperty
  private int    destinationRegistrationId;
  @JsonProperty
  private String content;

  @JsonProperty
  private Boolean isVoip;

  @JsonProperty
  private Boolean audioOnly;

  @JsonProperty
  private String groupId;

  @JsonProperty
  private String callerPhoneNumber;

  @JsonProperty
  private int silent;

  @JsonProperty
  private String groupName;

  @JsonProperty
  private String callerName;

  public OutgoingPushMessage(int type,
                             int destinationDeviceId,
                             int destinationRegistrationId,
                             String content,
                             Boolean isVoip,
                             String groupId,
                             String callerPhoneNumber,
                             int silent,
                             String groupName,
                             String callerName,
                             Boolean audioOnly)
  {
    this.type                      = type;
    this.destinationDeviceId       = destinationDeviceId;
    this.destinationRegistrationId = destinationRegistrationId;
    this.content                   = content;
    this.isVoip                 = isVoip;
    this.groupId                 = groupId;
    this.callerPhoneNumber                = callerPhoneNumber;
    this.silent                = silent;
    this.groupName             = groupName;
    this.callerName            = callerName;
    this.audioOnly = audioOnly;
  }

  public void setSilent(int silent) {
    this.silent = silent;
  }

  public int getType() {
    return type;
  }

  public void setType(int type) {
    this.type = type;
  }

  public int getDestinationDeviceId() {
    return destinationDeviceId;
  }

  public void setDestinationDeviceId(int destinationDeviceId) {
    this.destinationDeviceId = destinationDeviceId;
  }

  public int getDestinationRegistrationId() {
    return destinationRegistrationId;
  }

  public void setDestinationRegistrationId(int destinationRegistrationId) {
    this.destinationRegistrationId = destinationRegistrationId;
  }

  public String getContent() {
    return content;
  }

  public void setContent(String content) {
    this.content = content;
  }

  public Boolean getIsVoip() {
    return isVoip;
  }

  public void setIsVoip(Boolean isVoip) {
    this.isVoip = isVoip;
  }

  public String getGroupId() {
    return groupId;
  }

  public void setGroupId(String groupId) {
    this.groupId = groupId;
  }

  public String getCallerPhoneNumber() {
    return callerPhoneNumber;
  }

  public void setCallerPhoneNumber(String callerPhoneNumber) {
    this.callerPhoneNumber = callerPhoneNumber;
  }

  public String getGroupName() {
    return groupName;
  }

  public void setGroupName(String groupName) {
    this.groupName = groupName;
  }

  public String getCallerName() {
    return callerName;
  }

  public void setCallerName(String callerName) {
    this.callerName = callerName;
  }

  public Boolean getAudioOnly() {
    return audioOnly;
  }

  public void setAudioOnly(Boolean audioOnly) {
    this.audioOnly = audioOnly;
  }
}
