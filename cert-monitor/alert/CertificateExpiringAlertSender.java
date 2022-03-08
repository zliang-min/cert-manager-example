//SOURCES ../model/CertificateStatus.java

package alert;

import model.CertificateStatus;

public interface CertificateExpiringAlertSender {
  void sendAlert(final String certName, final CertificateStatus cs);
}
