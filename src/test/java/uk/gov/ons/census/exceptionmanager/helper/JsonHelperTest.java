package uk.gov.ons.census.exceptionmanager.helper;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import uk.gov.ons.census.exceptionmanager.model.dto.BadMessageReport;
import uk.gov.ons.census.exceptionmanager.model.dto.ExceptionReport;
import uk.gov.ons.census.exceptionmanager.model.dto.ExceptionStats;

public class JsonHelperTest {
  @Test
  public void testConvertObjectToJsonPreservesPropertyOrderAndInstantFormat() {
    BadMessageReport badMessageReport = new BadMessageReport();
    badMessageReport.setExceptionReport(buildExceptionReport());
    badMessageReport.setStats(buildExceptionStats());

    String actualJson = JsonHelper.convertObjectToJson(List.of(badMessageReport));

    assertThat(actualJson)
        .isEqualTo(
            "[{\"exceptionReport\":{\"messageHash\":\"hash\",\"service\":\"service\",\"subscription\":\"subscription\",\"exceptionClass\":\"class\",\"exceptionMessage\":\"message\",\"exceptionRootCause\":\"cause\"},\"stats\":{\"firstSeen\":\"2024-01-02T03:04:05Z\",\"lastSeen\":\"2024-01-02T03:04:06Z\",\"seenCount\":7,\"loggedAtLeastOnce\":true}}]");
  }

  private static ExceptionReport buildExceptionReport() {
    ExceptionReport exceptionReport = new ExceptionReport();
    exceptionReport.setMessageHash("hash");
    exceptionReport.setService("service");
    exceptionReport.setSubscription("subscription");
    exceptionReport.setExceptionClass("class");
    exceptionReport.setExceptionMessage("message");
    exceptionReport.setExceptionRootCause("cause");
    return exceptionReport;
  }

  private static ExceptionStats buildExceptionStats() {
    ExceptionStats exceptionStats = new ExceptionStats();
    exceptionStats.setFirstSeen(Instant.parse("2024-01-02T03:04:05Z"));
    exceptionStats.setLastSeen(Instant.parse("2024-01-02T03:04:06Z"));
    exceptionStats.setSeenCount(new AtomicInteger(7));
    exceptionStats.setLoggedAtLeastOnce(true);
    return exceptionStats;
  }
}
