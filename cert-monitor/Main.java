///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS ch.qos.logback:logback-classic:1.3.0-alpha14
//DEPS io.fabric8:kubernetes-client:5.12.1
//DEPS com.sun.mail:jakarta.mail:2.0.1
//SOURCES CertificateMonitor.java
//SOURCES alert/*.java

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import alert.CertificateExpiringAlertSender;
import alert.EmailSender;

public class Main {

  private static final Logger logger = LoggerFactory.getLogger(Main.class);

  public static void main(String... args) throws Exception {
    final String namespace = getenv("K8S_NAMESPACE");
    final String certName = getenv("K8S_CERT_NAME");
    final String deploymentName = getenv("K8S_DEPLOYMENT_NAME");
    final Duration alertBeforeExpire = Duration.parse(getenv("CERT_ALERT_DURATION_BEFORE_EXPIRE"));
    final String smtpHost = getenv("ALERT_SMTP_HOST");
    final String smtpPort = getenv("ALERT_SMTP_PORT");
    final boolean smtpAuth = getenv("ALERT_SMTP_AUTH").toLowerCase().equals("true");
    final String sender = getenv("ALERT_EMAIL_SENDER");
    final String senderPassword = getenv("ALERT_EMAIL_SENDER_PASSWORD");
    final String recipient = getenv("ALERT_EMAIL_RECIPIENT");

    final CertificateExpiringAlertSender alertSender = new EmailSender(
        smtpHost, smtpPort, smtpAuth,
        sender, senderPassword, recipient);

    try (CertificateMonitor mon = new CertificateMonitor(namespace, certName, deploymentName, alertBeforeExpire, alertSender)) {
      mon.start();
      mon.waitForWatch();
    } catch (InterruptedException e) {
      logger.warn("CertificateMonitor is terminated.", e);
    }
  }

  private static String getenv(String name) {
    String value = System.getenv(name);
    if (value == null) {
      throw new IllegalArgumentException("Environment variable " + name + " was not set.");
    }
    return value;
  }
}
