package uk.gov.ons.census.exceptionmanager.endpoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import uk.gov.ons.census.exceptionmanager.model.dto.AutoQuarantineRule;
import uk.gov.ons.census.exceptionmanager.model.dto.BadMessageReport;
import uk.gov.ons.census.exceptionmanager.model.dto.BadMessageSummary;
import uk.gov.ons.census.exceptionmanager.model.dto.ExceptionReport;
import uk.gov.ons.census.exceptionmanager.model.dto.ExceptionStats;
import uk.gov.ons.census.exceptionmanager.model.dto.SkipMessageRequest;
import uk.gov.ons.census.exceptionmanager.model.dto.SkippedMessage;
import uk.gov.ons.census.exceptionmanager.persistence.CachingDataStore;

public class AdminEndpointTest {

  @Test
  public void testGetBadMessages() {
    // Given
    CachingDataStore cachingDataStore = mock(CachingDataStore.class);
    Set testSet = Collections.emptySet();
    when(cachingDataStore.getSeenMessageHashes()).thenReturn(testSet);
    AdminEndpoint underTest = new AdminEndpoint(cachingDataStore, 500);

    // When
    ResponseEntity<Set<String>> actualResponse = underTest.getBadMessages(-1);

    // Then
    assertThat(actualResponse.getBody()).isEqualTo(testSet);
    verify(cachingDataStore).getSeenMessageHashes();
  }

  @Test
  public void testGetBadMessageCount() {
    // Given
    CachingDataStore cachingDataStore = mock(CachingDataStore.class);
    when(cachingDataStore.getSeenMessageCount()).thenReturn(10);
    AdminEndpoint underTest = new AdminEndpoint(cachingDataStore, 500);

    // When
    ResponseEntity<Integer> actualResponse = underTest.getBadMessagesCount();

    // Then
    assertThat(actualResponse.getBody()).isEqualTo(10);
    verify(cachingDataStore).getSeenMessageCount();
  }

  @Test
  public void getBadMessagesSummary() {
    // Given
    CachingDataStore cachingDataStore = mock(CachingDataStore.class);
    Set testSet = Set.of("test message hash");
    ExceptionReport exceptionReport = new ExceptionReport();
    exceptionReport.setSubscription("test subscription");
    exceptionReport.setService("test service");
    ExceptionStats exceptionStats = new ExceptionStats();
    exceptionStats.getSeenCount().set(666);
    BadMessageReport badMessageReport = new BadMessageReport();
    badMessageReport.setExceptionReport(exceptionReport);
    badMessageReport.setStats(exceptionStats);
    List<BadMessageReport> badMessageReportList = List.of(badMessageReport);
    when(cachingDataStore.getSeenMessageHashes()).thenReturn(testSet);
    when(cachingDataStore.getBadMessageReports(anyString())).thenReturn(badMessageReportList);
    when(cachingDataStore.isQuarantined(anyString())).thenReturn(true);
    AdminEndpoint underTest = new AdminEndpoint(cachingDataStore, 500);

    // When
    ResponseEntity<List<BadMessageSummary>> actualResponse = underTest.getBadMessagesSummary(-1);

    // Then
    verify(cachingDataStore).getSeenMessageHashes();
    verify(cachingDataStore).getBadMessageReports(eq("test message hash"));
    verify(cachingDataStore).isQuarantined(eq("test message hash"));

    assertThat(actualResponse.getBody()).isNotNull();
    assertThat(actualResponse.getBody().size()).isEqualTo(1);
    BadMessageSummary actualBadMessageSummary = actualResponse.getBody().get(0);
    assertThat(actualBadMessageSummary.getAffectedSubscriptions())
        .containsOnly("test subscription");
    assertThat(actualBadMessageSummary.getAffectedServices()).containsOnly("test service");
    assertThat(actualBadMessageSummary.getFirstSeen()).isEqualTo(exceptionStats.getFirstSeen());
    assertThat(actualBadMessageSummary.getLastSeen()).isEqualTo(exceptionStats.getLastSeen());
    assertThat(actualBadMessageSummary.getSeenCount()).isEqualTo(666);
    assertThat(actualBadMessageSummary.isQuarantined()).isTrue();
  }

  @Test
  public void testGetBadMessageDetails() {
    // Given
    String testMessageHash = "test message hash";
    CachingDataStore cachingDataStore = mock(CachingDataStore.class);
    List testList = Collections.emptyList();
    when(cachingDataStore.getBadMessageReports(anyString())).thenReturn(testList);
    AdminEndpoint underTest = new AdminEndpoint(cachingDataStore, 500);

    // When
    ResponseEntity<List<BadMessageReport>> actualResponse =
        underTest.getBadMessageDetails(testMessageHash);

    // Then
    assertThat(actualResponse.getBody()).isEqualTo(testList);
    verify(cachingDataStore).getBadMessageReports(eq(testMessageHash));
  }

  @Test
  public void testSkipMessage() {
    // Given
    String testMessageHash = "test message hash";
    String testOriginatingUser = "foo@bar.com";
    CachingDataStore cachingDataStore = mock(CachingDataStore.class);
    AdminEndpoint underTest = new AdminEndpoint(cachingDataStore, 500);

    // When
    SkipMessageRequest skipMessageRequest = new SkipMessageRequest();
    skipMessageRequest.setMessageHash(testMessageHash);
    skipMessageRequest.setSkippingUser(testOriginatingUser);
    underTest.skipMessage(skipMessageRequest);

    // Then
    verify(cachingDataStore).skipMessage(eq(testMessageHash), eq(testOriginatingUser));
  }

  @Test
  public void testPeekMessage() {
    // Given
    String testMessageHash = "test message hash";
    CachingDataStore cachingDataStore = mock(CachingDataStore.class);
    byte[] testPeekedMessageBody = "test message body".getBytes(StandardCharsets.UTF_8);
    when(cachingDataStore.getPeekedMessage(anyString()))
        .thenReturn(null)
        .thenReturn(null)
        .thenReturn(null)
        .thenReturn(null)
        .thenReturn(null)
        .thenReturn(null)
        .thenReturn(null)
        .thenReturn(null)
        .thenReturn(null)
        .thenReturn(null)
        .thenReturn(null)
        .thenReturn(null)
        .thenReturn(null)
        .thenReturn(null)
        .thenReturn(null)
        .thenReturn(testPeekedMessageBody);
    AdminEndpoint underTest = new AdminEndpoint(cachingDataStore, 500);

    // When
    ResponseEntity<String> actualResponse = underTest.peekMessage(testMessageHash);

    // Then
    assertThat(actualResponse.getBody())
        .isEqualTo(new String(testPeekedMessageBody, StandardCharsets.UTF_8));
    verify(cachingDataStore).peekMessage(eq(testMessageHash));
  }

  @Test
  public void testGetAllSkippedMessages() {
    // Given
    CachingDataStore cachingDataStore = mock(CachingDataStore.class);
    Map testMap = Collections.emptyMap();
    when(cachingDataStore.getAllSkippedMessages()).thenReturn(testMap);
    AdminEndpoint underTest = new AdminEndpoint(cachingDataStore, 500);

    // When
    ResponseEntity<Map<String, List<SkippedMessage>>> actualResponse =
        underTest.getAllSkippedMessages();

    // Then
    assertThat(actualResponse.getBody()).isEqualTo(testMap);
    verify(cachingDataStore).getAllSkippedMessages();
  }

  @Test
  public void testGetSkippedMessage() {
    // Given
    String testMessageHash = "test message hash";
    CachingDataStore cachingDataStore = mock(CachingDataStore.class);
    List testList = Collections.emptyList();
    when(cachingDataStore.getSkippedMessages(anyString())).thenReturn(testList);
    AdminEndpoint underTest = new AdminEndpoint(cachingDataStore, 500);

    // When
    ResponseEntity<List<SkippedMessage>> actualResponse =
        underTest.getSkippedMessage(testMessageHash);

    // Then
    assertThat(actualResponse.getBody()).isEqualTo(testList);
    verify(cachingDataStore).getSkippedMessages(eq(testMessageHash));
  }

  @Test
  public void testReset() {
    // Given
    CachingDataStore cachingDataStore = mock(CachingDataStore.class);
    AdminEndpoint underTest = new AdminEndpoint(cachingDataStore, 500);

    // When
    underTest.reset(Optional.empty());

    // Then
    verify(cachingDataStore).reset(Optional.empty());
  }

  @Test
  public void testAddQuarantineRule() {
    // Given
    CachingDataStore cachingDataStore = mock(CachingDataStore.class);
    AdminEndpoint underTest = new AdminEndpoint(cachingDataStore, 500);

    // When
    AutoQuarantineRule autoQuarantineRule = new AutoQuarantineRule();
    autoQuarantineRule.setExpression("true");
    autoQuarantineRule.setRuleExpiryDateTime(OffsetDateTime.MAX);
    underTest.addQuarantineRule(autoQuarantineRule);

    // Then
    verify(cachingDataStore)
        .addQuarantineRuleExpression(
            eq("true"), eq(false), eq(false), eq(false), any(OffsetDateTime.class));
  }

  @Test
  public void testDeleteQuarantineRule() {
    // Given
    CachingDataStore cachingDataStore = mock(CachingDataStore.class);
    AdminEndpoint underTest = new AdminEndpoint(cachingDataStore, 500);

    // When
    underTest.deleteQuarantineRules("test id");

    // Then
    verify(cachingDataStore).deleteQuarantineRule(eq("test id"));
  }

  @Test
  public void testGetQuarantineRules() {
    // Given
    CachingDataStore cachingDataStore = mock(CachingDataStore.class);
    uk.gov.ons.census.exceptionmanager.model.entity.AutoQuarantineRule autoQuarantineRule =
        new uk.gov.ons.census.exceptionmanager.model.entity.AutoQuarantineRule();
    autoQuarantineRule.setExpression("true == true");
    when(cachingDataStore.getQuarantineRules())
        .thenReturn(Collections.singletonList(autoQuarantineRule));
    AdminEndpoint underTest = new AdminEndpoint(cachingDataStore, 500);

    // When
    ResponseEntity<List<AutoQuarantineRule>> quarantineRulesResponse =
        underTest.getQuarantineRules();

    // Then
    AutoQuarantineRule expectedAutoQuarantineRule = new AutoQuarantineRule();
    expectedAutoQuarantineRule.setExpression("true == true");
    assertThat(quarantineRulesResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(quarantineRulesResponse.getBody().size()).isEqualTo(1);
    assertThat(quarantineRulesResponse.getBody().get(0)).isEqualTo(expectedAutoQuarantineRule);
  }
}
