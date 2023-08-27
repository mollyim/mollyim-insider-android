/**
 * Copyright (C) 2014-2016 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */

package org.whispersystems.signalservice.internal.util;

import org.whispersystems.signalservice.api.push.ServiceId.ACI;
import org.whispersystems.signalservice.api.push.ServiceId.PNI;
import org.whispersystems.signalservice.api.util.CredentialsProvider;

public class StaticCredentialsProvider implements CredentialsProvider {

  private ACI    aci;
  private PNI    pni;
  private String e164;
  private int    deviceId;
  private String password;

  public StaticCredentialsProvider(ACI aci, PNI pni, String e164, int deviceId, String password) {
    this.aci      = aci;
    this.pni      = pni;
    this.e164     = e164;
    this.deviceId = deviceId;
    this.password = password;
  }

  @Override
  public ACI getAci() {
    return aci;
  }

  @Override
  public PNI getPni() {
    return pni;
  }

  @Override
  public String getE164() {
    return e164;
  }

  @Override
  public int getDeviceId() {
    return deviceId;
  }

  @Override
  public String getPassword() {
    return password;
  }

  public void setAci(ACI aci) {
    this.aci = aci;
  }

  public void setPni(PNI pni) {
    this.pni = pni;
  }

  public void setE164(String e164) {
    this.e164 = e164;
  }

  public void setDeviceId(int deviceId) {
    this.deviceId = deviceId;
  }

  public void setPassword(String password) {
    this.password = password;
  }
}
