//SOURCES alert/CertificateExpiringAlertSender.java
//SOURCES model/CertificateStatus.java

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.WatcherException;
import io.fabric8.kubernetes.client.dsl.base.CustomResourceDefinitionContext;

import alert.CertificateExpiringAlertSender;
import model.CertificateStatus;

public class CertificateMonitor implements AutoCloseable {

  private static final Logger logger = LoggerFactory.getLogger(CertificateMonitor.class);

  private final KubernetesClient k8sClient;
  private final CertificateExpiringAlertSender alertSender;
  private final String certName;
  private final String deploymentName;
  private final Duration alertBeforeExpire;

  private final Semaphore watchGuard = new Semaphore(1);
  private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1, new ThreadFactory() {

    private final AtomicInteger count = new AtomicInteger();

    @Override
    public Thread newThread(Runnable r) {
      Thread t = Executors.defaultThreadFactory().newThread(r);
      t.setName("alertSender-" + count.incrementAndGet());
      return t;
    }

  });

  public CertificateMonitor(final String namespace, final String certName, final String deploymentName,
      Duration alertBeforeExpire, CertificateExpiringAlertSender alertSender) {
    k8sClient = new DefaultKubernetesClient(
        new ConfigBuilder()
            .withNamespace(namespace).build());

    this.certName = certName;
    this.deploymentName = deploymentName;
    this.alertBeforeExpire = alertBeforeExpire;
    this.alertSender = alertSender;
  }

  public void start() {
    if (!watchGuard.tryAcquire()) {
      return; // already started
    }

    k8sClient
        .genericKubernetesResources(new CustomResourceDefinitionContext.Builder()
            .withGroup("cert-manager.io")
            .withKind("Certificate")
            .withPlural("certificates")
            .withVersion("v1")
            .withStatusSubresource(true)
            .build())
        .withName(certName)
        .watch(new Watcher<GenericKubernetesResource>() {
          private int currentRevision = 0;

          @Override
          public void eventReceived(Action action,
              GenericKubernetesResource cert) {
            CertificateStatus cs = CertificateStatus.from(cert.<Map<String, Object>>get("status"));
            int newRevision = cs.revision();

            logger.info("Got cert event={} notAfter={}", action.name(), cs.notAfter());

            switch (action) {
              case ADDED:
                currentRevision = newRevision;
                logger.info("Set revision to {}", currentRevision);
                scheduleAlert(cs);
                break;
              case MODIFIED:
                if (newRevision > currentRevision && !cs.issuing()) {
                  logger.info("Got new revision: {}", newRevision);
                  rolloutRestartDeployment();
                  currentRevision = newRevision;
                  logger.info("Updated revision to {}.", currentRevision);
                  scheduleAlert(cs);
                }
                break;
              default:
                break;
            }
          }

          @Override
          public void onClose(WatcherException ex) {
            watchGuard.release();

            if (ex != null) {
              logger.error("Failed to watch Certificate!", ex);
            }
          }

        });
  }

  public void waitForWatch() throws InterruptedException {
    watchGuard.acquire();
    watchGuard.release();
  }

  @Override
  public void close() throws Exception {
    if (k8sClient != null) {
      k8sClient.close();
    }
    scheduler.shutdownNow();
    watchGuard.tryAcquire();
    watchGuard.release();
  }

  private void rolloutRestartDeployment() {
    k8sClient
        .apps()
        .deployments()
        .withName(deploymentName)
        .rolling()
        .restart();
    logger.info("Rollout restarted deployment {}", deploymentName);
  }

  private void scheduleAlert(CertificateStatus cs) {
    if (cs.expireIn(alertBeforeExpire)) {
      alertSender.sendAlert(certName, cs);
    } else {
      var when = cs.notAfter().minus(alertBeforeExpire);
      var delay = Instant.now().until(when, ChronoUnit.MILLIS);
      logger.info("Scheduled an alert to be sent {} milliseconds later.", delay);
      scheduler.schedule(() -> alertSender.sendAlert(certName, cs), delay, TimeUnit.MILLISECONDS);
    }
  }
}
