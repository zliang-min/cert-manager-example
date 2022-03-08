package model;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Map;

public record CertificateStatus(
    int revision,
    Instant notAfter,
    Instant renewalTime,
    String nextPrivateKeySecretName) {

  public static CertificateStatus from(Map<String, Object> statusMap) throws DateTimeParseException {
    return new CertificateStatus(
        (int) statusMap.get("revision"),
        Instant.parse((String) statusMap.get("notAfter")),
        Instant.parse((String) statusMap.get("renewalTime")),
        (String) statusMap.get("nextPrivateKeySecretName"));
  }

  public boolean expireIn(Duration duration) {
    return Instant.now().plus(duration).isAfter(notAfter);
  }

  public boolean issuing() {
    return nextPrivateKeySecretName != null;
  }
}
