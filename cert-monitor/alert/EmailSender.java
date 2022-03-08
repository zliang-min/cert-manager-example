//SOURCES CertificateExpiringAlertSender.java
//SOURCES ../model/CertificateStatus.java

package alert;

import java.util.Date;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.mail.Authenticator;
import jakarta.mail.MessagingException;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.Message.RecipientType;
import jakarta.mail.internet.MimeMessage;

import model.CertificateStatus;

public class EmailSender implements CertificateExpiringAlertSender {

  private static final Logger logger = LoggerFactory.getLogger(EmailSender.class);

  private static final String TITLE_TEMPLATE = "Certificate %s is expiring";
  private static final String BODY_TEMPLATE = "Certificate %s will expire at %s. But no worries, it will be renewed at %s.";

  private final Session session;
  private final String from;
  private final String to;

  public EmailSender(
      final String smtpHost,
      final String smtpPort,
      final boolean smtpAuth,
      final String sender,
      final String senderPassword,
      final String recipient) {
    Properties prop = new Properties();
    if (smtpAuth) {
      prop.put("mail.smtp.auth", true);
      prop.put("mail.smtp.starttls.enable", "true");
    }
    prop.put("mail.smtp.host", smtpHost);
    prop.put("mail.smtp.port", smtpPort);
    prop.put("mail.debug", true);

    session = Session.getInstance(prop, new Authenticator() {

      @Override
      protected PasswordAuthentication getPasswordAuthentication() {
        return new PasswordAuthentication(sender, senderPassword);
      }
    });

    from = sender;
    to = recipient;
      }

  @Override
  public void sendAlert(final String certName, final CertificateStatus cs) {
    MimeMessage msg = new MimeMessage(session);
    try {
      msg.setFrom(from);
      msg.setRecipients(RecipientType.TO, to);
      msg.setSubject(String.format(TITLE_TEMPLATE, certName));
      msg.setText(String.format(BODY_TEMPLATE, certName, cs.notAfter(), cs.renewalTime()), "UTF-8");
      msg.setSentDate(new Date());

      Transport.send(msg);
      logger.info("Email sent!");
    } catch (MessagingException e) {
      logger.error("Failed to send the email.", e);
    }
  }
}
