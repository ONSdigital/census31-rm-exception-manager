package uk.gov.ons.census.exceptionmanager.endpoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import uk.gov.ons.census.exceptionmanager.model.dto.BadMessageReport;
import uk.gov.ons.census.exceptionmanager.model.dto.ExceptionReport;
import uk.gov.ons.census.exceptionmanager.model.dto.ExceptionStats;
import uk.gov.ons.census.exceptionmanager.model.dto.Peek;
import uk.gov.ons.census.exceptionmanager.model.dto.Response;
import uk.gov.ons.census.exceptionmanager.model.dto.SkippedMessage;
import uk.gov.ons.census.exceptionmanager.model.entity.QuarantinedMessage;
import uk.gov.ons.census.exceptionmanager.model.repository.QuarantinedMessageRepository;
import uk.gov.ons.census.exceptionmanager.persistence.CachingDataStore;

public class ReportingEndpointTest {
  private static final String EXPECTED_ERROR_REPORTS_JSON =
      "[{\"exceptionReport\":{\"messageHash\":\"test message hash\",\"service\":\"test service\",\"subscription\":\"test subscription\",\"exceptionClass\":\"test class\",\"exceptionMessage\":\"test message\",\"exceptionRootCause\":\"test root cause\"},\"stats\":{\"firstSeen\":\"2024-01-02T03:04:05Z\",\"lastSeen\":\"2024-01-02T03:04:06Z\",\"seenCount\":7,\"loggedAtLeastOnce\":true}}]";

  @Test
  public void testReportError() {
    String testMessageHash = "test message hash";
    CachingDataStore cachingDataStore = mock(CachingDataStore.class);
    ReportingEndpoint underTest = new ReportingEndpoint(cachingDataStore, null);
    ExceptionReport exceptionReport = new ExceptionReport();
    exceptionReport.setMessageHash(testMessageHash);

    when(cachingDataStore.shouldWeSkipThisMessage(any(ExceptionReport.class))).thenReturn(true);
    when(cachingDataStore.shouldWePeekThisMessage(anyString())).thenReturn(true);
    when(cachingDataStore.shouldWeLogThisMessage(exceptionReport)).thenReturn(true);

    Response actualResponse = underTest.reportError(exceptionReport).getBody();

    verify(cachingDataStore).shouldWeSkipThisMessage(eq(exceptionReport));
    verify(cachingDataStore).shouldWePeekThisMessage(eq(testMessageHash));
    verify(cachingDataStore).shouldWeLogThisMessage(eq(exceptionReport));
    assertThat(actualResponse).isNotNull();
    assertThat(actualResponse.isSkipIt()).isTrue();
    assertThat(actualResponse.isPeek()).isTrue();
    assertThat(actualResponse.isLogIt()).isTrue();
  }

  @Test
  public void testPeekReply() {
    CachingDataStore cachingDataStore = mock(CachingDataStore.class);
    ReportingEndpoint underTest = new ReportingEndpoint(cachingDataStore, null);
    Peek peek = new Peek();

    underTest.peekReply(peek);

    verify(cachingDataStore).storePeekMessageReply(eq(peek));
  }

  @Test
  public void testStoreSkippedMessage() {
    CachingDataStore cachingDataStore = mock(CachingDataStore.class);
    QuarantinedMessageRepository quarantinedMessageRepository =
        mock(QuarantinedMessageRepository.class);
    ReportingEndpoint underTest =
        new ReportingEndpoint(cachingDataStore, quarantinedMessageRepository);
    SkippedMessage skippedMessage = new SkippedMessage();
    skippedMessage.setMessageHash("test message hash");
    skippedMessage.setSubscription("test subscription");
    skippedMessage.setRoutingKey("test message key");
    skippedMessage.setContentType("application/xml");
    skippedMessage.setHeaders(Map.of("foo", "bar"));
    skippedMessage.setMessagePayload("<noodle>poodle</noodle>".getBytes(StandardCharsets.UTF_8));
    skippedMessage.setService("test service");

    BadMessageReport badMessageReport = new BadMessageReport();
    badMessageReport.setExceptionReport(buildExceptionReport());
    badMessageReport.setStats(buildExceptionStats());

    when(cachingDataStore.getBadMessageReports(eq(skippedMessage.getMessageHash())))
        .thenReturn(List.of(badMessageReport));
    when(cachingDataStore.getOriginatingUserOfSkipRequest(eq(skippedMessage.getMessageHash())))
        .thenReturn("test user");

    underTest.storeSkippedMessage(skippedMessage);

    verify(cachingDataStore).storeSkippedMessage(eq(skippedMessage));

    ArgumentCaptor<QuarantinedMessage> quarantinedMessageArgCaptor =
        ArgumentCaptor.forClass(QuarantinedMessage.class);
    verify(quarantinedMessageRepository).save(quarantinedMessageArgCaptor.capture());
    QuarantinedMessage quarantinedMessage = quarantinedMessageArgCaptor.getValue();
    assertThat(quarantinedMessage.getContentType()).isEqualTo(skippedMessage.getContentType());
    assertThat(quarantinedMessage.getHeaders()).isEqualTo(skippedMessage.getHeaders());
    assertThat(quarantinedMessage.getMessagePayload())
        .isEqualTo(skippedMessage.getMessagePayload());
    assertThat(quarantinedMessage.getErrorReports()).isEqualTo(EXPECTED_ERROR_REPORTS_JSON);
    assertThat(quarantinedMessage.getRoutingKey()).isEqualTo(skippedMessage.getRoutingKey());
    assertThat(quarantinedMessage.getService()).isEqualTo(skippedMessage.getService());
  }

  private static ExceptionReport buildExceptionReport() {
    ExceptionReport exceptionReport = new ExceptionReport();
    exceptionReport.setMessageHash("test message hash");
    exceptionReport.setService("test service");
    exceptionReport.setSubscription("test subscription");
    exceptionReport.setExceptionClass("test class");
    exceptionReport.setExceptionMessage("test message");
    exceptionReport.setExceptionRootCause("test root cause");
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
