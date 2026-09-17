package uk.gov.ons.census.exceptionmanager.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import uk.gov.ons.census.exceptionmanager.model.dto.BadMessageReport;
import uk.gov.ons.census.exceptionmanager.model.dto.ExceptionReport;
import uk.gov.ons.census.exceptionmanager.model.dto.ExceptionStats;
import uk.gov.ons.census.exceptionmanager.model.dto.Peek;
import uk.gov.ons.census.exceptionmanager.model.dto.SkippedMessage;
import uk.gov.ons.census.exceptionmanager.model.entity.AutoQuarantineRule;
import uk.gov.ons.census.exceptionmanager.model.repository.AutoQuarantineRuleRepository;

public class CachingDataStoreTest {
  @Test
  public void testUpdateStats() {
    AutoQuarantineRuleRepository autoQuarantineRuleRepository =
        mock(AutoQuarantineRuleRepository.class);
    when(autoQuarantineRuleRepository.findAll()).thenReturn(Collections.emptyList());
    CachingDataStore underTest = new CachingDataStore(autoQuarantineRuleRepository, 0);
    ExceptionReport exceptionReport = new ExceptionReport();
    exceptionReport.setMessageHash("test message hash");
    exceptionReport.setExceptionClass("test class");
    exceptionReport.setExceptionMessage("test exception message");
    exceptionReport.setSubscription("test subscription");
    exceptionReport.setService("test service");

    underTest.updateStats(exceptionReport);

    assertThat(underTest.shouldWeSkipThisMessage(exceptionReport)).isFalse();
    assertThat(underTest.shouldWeLogThisMessage(exceptionReport)).isFalse();
    assertThat(underTest.shouldWePeekThisMessage("test message hash")).isFalse();

    List<BadMessageReport> badMessageReports = underTest.getBadMessageReports("test message hash");

    assertThat(badMessageReports.size()).isEqualTo(1);
    assertThat(badMessageReports.get(0).getExceptionReport()).isEqualTo(exceptionReport);
    assertThat(badMessageReports.get(0).getStats().getSeenCount().get()).isEqualTo(1);
  }

  @Test
  public void testUpdateStatsSameTwice() {
    AutoQuarantineRuleRepository autoQuarantineRuleRepository =
        mock(AutoQuarantineRuleRepository.class);
    when(autoQuarantineRuleRepository.findAll()).thenReturn(Collections.emptyList());
    CachingDataStore underTest = new CachingDataStore(autoQuarantineRuleRepository, 0);
    ExceptionReport exceptionReport = new ExceptionReport();
    exceptionReport.setMessageHash("test message hash");
    exceptionReport.setExceptionClass("test class");
    exceptionReport.setExceptionMessage("test exception message");
    exceptionReport.setSubscription("test subscription");
    exceptionReport.setService("test service");

    underTest.updateStats(exceptionReport);
    underTest.updateStats(exceptionReport);

    assertThat(underTest.shouldWeSkipThisMessage(exceptionReport)).isFalse();
    assertThat(underTest.shouldWeLogThisMessage(exceptionReport)).isFalse();
    assertThat(underTest.shouldWePeekThisMessage("test message hash")).isFalse();

    List<BadMessageReport> badMessageReports = underTest.getBadMessageReports("test message hash");

    assertThat(badMessageReports.size()).isEqualTo(1);
    assertThat(badMessageReports.get(0).getExceptionReport()).isEqualTo(exceptionReport);
    assertThat(badMessageReports.get(0).getStats().getSeenCount().get()).isEqualTo(2);
  }

  @Test
  public void testUpdateStatsDifferentTimes() {
    AutoQuarantineRuleRepository autoQuarantineRuleRepository =
        mock(AutoQuarantineRuleRepository.class);
    when(autoQuarantineRuleRepository.findAll()).thenReturn(Collections.emptyList());
    CachingDataStore underTest = new CachingDataStore(autoQuarantineRuleRepository, 0);
    ExceptionReport exceptionReportOne = new ExceptionReport();
    exceptionReportOne.setMessageHash("test message hash");
    exceptionReportOne.setExceptionClass("test class");
    exceptionReportOne.setExceptionMessage("test exception message");
    exceptionReportOne.setSubscription("test subscription");
    exceptionReportOne.setService("test service");

    underTest.updateStats(exceptionReportOne);

    ExceptionReport exceptionReportTwo = new ExceptionReport();
    exceptionReportTwo.setMessageHash("test message hash");
    exceptionReportTwo.setExceptionClass("test class");
    exceptionReportTwo.setExceptionMessage("different test exception message");
    exceptionReportTwo.setSubscription("test subscription");
    exceptionReportTwo.setService("test service");
    underTest.updateStats(exceptionReportTwo);
    underTest.updateStats(exceptionReportTwo);
    underTest.updateStats(exceptionReportTwo);

    assertThat(underTest.shouldWeSkipThisMessage(exceptionReportOne)).isFalse();
    assertThat(underTest.shouldWeLogThisMessage(exceptionReportOne)).isFalse();
    assertThat(underTest.shouldWeLogThisMessage(exceptionReportTwo)).isFalse();
    assertThat(underTest.shouldWePeekThisMessage("test message hash")).isFalse();

    List<BadMessageReport> badMessageReports = underTest.getBadMessageReports("test message hash");

    assertThat(badMessageReports.size()).isEqualTo(2);
    assertThat(badMessageReports)
        .extracting(BadMessageReport::getExceptionReport)
        .contains(exceptionReportOne, exceptionReportTwo);
    assertThat(badMessageReports)
        .extracting(BadMessageReport::getStats)
        .extracting(ExceptionStats::getSeenCount)
        .extracting(AtomicInteger::get)
        .contains(1, 3);
  }

  @Test
  public void testShouldWeLog() {
    AutoQuarantineRuleRepository autoQuarantineRuleRepository =
        mock(AutoQuarantineRuleRepository.class);
    when(autoQuarantineRuleRepository.findAll()).thenReturn(Collections.emptyList());
    CachingDataStore underTest = new CachingDataStore(autoQuarantineRuleRepository, 0);
    ExceptionReport exceptionReport = new ExceptionReport();
    exceptionReport.setMessageHash("test message hash");
    exceptionReport.setExceptionClass("test class");
    exceptionReport.setExceptionMessage("test exception message");
    exceptionReport.setSubscription("test subscription");
    exceptionReport.setService("test service");

    assertThat(underTest.shouldWeLogThisMessage(exceptionReport)).isTrue();

    underTest.updateStats(exceptionReport);

    assertThat(underTest.shouldWeLogThisMessage(exceptionReport)).isFalse();
  }

  @Test
  public void testShouldWeLogAfterRetries() {
    AutoQuarantineRuleRepository autoQuarantineRuleRepository =
        mock(AutoQuarantineRuleRepository.class);
    when(autoQuarantineRuleRepository.findAll()).thenReturn(Collections.emptyList());
    CachingDataStore underTest = new CachingDataStore(autoQuarantineRuleRepository, 1);
    ExceptionReport exceptionReport = new ExceptionReport();
    exceptionReport.setMessageHash("test message hash");
    exceptionReport.setExceptionClass("test class");
    exceptionReport.setExceptionMessage("test exception message");
    exceptionReport.setSubscription("test subscription");
    exceptionReport.setService("test service");

    assertThat(underTest.shouldWeLogThisMessage(exceptionReport)).isFalse();

    underTest.updateStats(exceptionReport);

    assertThat(underTest.shouldWeLogThisMessage(exceptionReport)).isTrue();

    underTest.updateStats(exceptionReport);

    assertThat(underTest.shouldWeLogThisMessage(exceptionReport)).isFalse();
  }

  @Test
  public void testShouldWeSkip() {
    AutoQuarantineRuleRepository autoQuarantineRuleRepository =
        mock(AutoQuarantineRuleRepository.class);
    when(autoQuarantineRuleRepository.findAll()).thenReturn(Collections.emptyList());
    CachingDataStore underTest = new CachingDataStore(autoQuarantineRuleRepository, 0);
    ExceptionReport exceptionReport = new ExceptionReport();
    exceptionReport.setMessageHash("test message hash");
    exceptionReport.setExceptionClass("test class");
    exceptionReport.setExceptionMessage("test exception message");
    exceptionReport.setSubscription("test subscription");
    exceptionReport.setService("test service");

    assertThat(underTest.shouldWeSkipThisMessage(exceptionReport)).isFalse();

    underTest.updateStats(exceptionReport);

    assertThat(underTest.shouldWeSkipThisMessage(exceptionReport)).isFalse();

    underTest.skipMessage("test message hash", "foo@bar.com");

    assertThat(underTest.shouldWeSkipThisMessage(exceptionReport)).isTrue();
  }

  @Test
  public void testAutoQuarantineMatch() {
    AutoQuarantineRuleRepository autoQuarantineRuleRepository =
        mock(AutoQuarantineRuleRepository.class);
    AutoQuarantineRule rule = new AutoQuarantineRule();
    rule.setQuarantine(true);
    rule.setRuleExpiryDateTime(OffsetDateTime.MAX);
    rule.setExpression(
        "exceptionClass == \"test class\" and subscription == \"test subscription\"");
    when(autoQuarantineRuleRepository.findAll()).thenReturn(Collections.singletonList(rule));
    CachingDataStore underTest = new CachingDataStore(autoQuarantineRuleRepository, 0);
    ExceptionReport exceptionReport = new ExceptionReport();
    exceptionReport.setMessageHash("test message hash");
    exceptionReport.setExceptionClass("test class");
    exceptionReport.setExceptionMessage("test exception message");
    exceptionReport.setSubscription("test subscription");
    exceptionReport.setService("test service");

    List<AutoQuarantineRule> matchingRules = underTest.findMatchingRules(exceptionReport);
    assertThat(matchingRules.size()).isEqualTo(1);
    assertThat(matchingRules.get(0).isQuarantine()).isTrue();
  }

  @Test
  public void testfindMatchingRulesMatchSubstring() {
    AutoQuarantineRuleRepository autoQuarantineRuleRepository =
        mock(AutoQuarantineRuleRepository.class);
    AutoQuarantineRule rule = new AutoQuarantineRule();
    rule.setExpression("exceptionMessage.contains('message')");
    rule.setQuarantine(true);
    rule.setRuleExpiryDateTime(OffsetDateTime.MAX);
    when(autoQuarantineRuleRepository.findAll()).thenReturn(Collections.singletonList(rule));
    CachingDataStore underTest = new CachingDataStore(autoQuarantineRuleRepository, 0);
    ExceptionReport exceptionReport = new ExceptionReport();
    exceptionReport.setMessageHash("test message hash");
    exceptionReport.setExceptionClass("test class");
    exceptionReport.setExceptionMessage("test exception message");
    exceptionReport.setSubscription("test subscription");
    exceptionReport.setService("test service");

    List<AutoQuarantineRule> matchingRules = underTest.findMatchingRules(exceptionReport);
    assertThat(matchingRules.size()).isEqualTo(1);
    assertThat(matchingRules.get(0).isQuarantine()).isTrue();
  }

  @Test
  public void testShouldWeSkipAutoQuarantineDoesNotMatch() {
    AutoQuarantineRuleRepository autoQuarantineRuleRepository =
        mock(AutoQuarantineRuleRepository.class);
    AutoQuarantineRule rule = new AutoQuarantineRule();
    rule.setQuarantine(true);
    rule.setExpression("exceptionClass == \"noodle\" and subscription == \"test subscription\"");
    rule.setRuleExpiryDateTime(OffsetDateTime.MAX);
    when(autoQuarantineRuleRepository.findAll()).thenReturn(Collections.singletonList(rule));
    CachingDataStore underTest = new CachingDataStore(autoQuarantineRuleRepository, 0);
    ExceptionReport exceptionReport = new ExceptionReport();
    exceptionReport.setMessageHash("test message hash");
    exceptionReport.setExceptionClass("test class");
    exceptionReport.setExceptionMessage("test exception message");
    exceptionReport.setSubscription("test subscription");
    exceptionReport.setService("test service");

    List<AutoQuarantineRule> matchingRules = underTest.findMatchingRules(exceptionReport);
    assertThat(matchingRules.size()).isEqualTo(0);
  }

  @Test
  public void testShouldWePeek() {
    AutoQuarantineRuleRepository autoQuarantineRuleRepository =
        mock(AutoQuarantineRuleRepository.class);
    when(autoQuarantineRuleRepository.findAll()).thenReturn(Collections.emptyList());
    CachingDataStore underTest = new CachingDataStore(autoQuarantineRuleRepository, 0);
    ExceptionReport exceptionReport = new ExceptionReport();
    exceptionReport.setMessageHash("test message hash");
    exceptionReport.setExceptionClass("test class");
    exceptionReport.setExceptionMessage("test exception message");
    exceptionReport.setSubscription("test subscription");
    exceptionReport.setService("test service");

    assertThat(underTest.shouldWePeekThisMessage("test message hash")).isFalse();

    underTest.updateStats(exceptionReport);

    assertThat(underTest.shouldWePeekThisMessage("test message hash")).isFalse();

    underTest.peekMessage("test message hash");

    assertThat(underTest.shouldWePeekThisMessage("test message hash")).isTrue();
  }

  @Test
  public void testGetSeenMessageHashes() {
    AutoQuarantineRuleRepository autoQuarantineRuleRepository =
        mock(AutoQuarantineRuleRepository.class);
    when(autoQuarantineRuleRepository.findAll()).thenReturn(Collections.emptyList());
    CachingDataStore underTest = new CachingDataStore(autoQuarantineRuleRepository, 0);
    ExceptionReport exceptionReportOne = new ExceptionReport();
    exceptionReportOne.setMessageHash("test message hash");
    exceptionReportOne.setExceptionClass("test class");
    exceptionReportOne.setExceptionMessage("test exception message");
    exceptionReportOne.setSubscription("test subscription");
    exceptionReportOne.setService("test service");

    underTest.updateStats(exceptionReportOne);

    ExceptionReport exceptionReportTwo = new ExceptionReport();
    exceptionReportTwo.setMessageHash("another test message hash");
    exceptionReportTwo.setExceptionClass("test class");
    exceptionReportTwo.setExceptionMessage("test exception message");
    exceptionReportTwo.setSubscription("test subscription");
    exceptionReportTwo.setService("test service");
    underTest.updateStats(exceptionReportTwo);
    underTest.updateStats(exceptionReportTwo);
    underTest.updateStats(exceptionReportTwo);

    underTest.updateStats(exceptionReportTwo);

    assertThat(underTest.getSeenMessageHashes())
        .contains("test message hash", "another test message hash");
  }

  @Test
  public void testStorePeekMessageReply() {
    AutoQuarantineRuleRepository autoQuarantineRuleRepository =
        mock(AutoQuarantineRuleRepository.class);
    when(autoQuarantineRuleRepository.findAll()).thenReturn(Collections.emptyList());
    CachingDataStore underTest = new CachingDataStore(autoQuarantineRuleRepository, 0);
    Peek peek = new Peek();
    peek.setMessageHash("test message hash");
    peek.setMessagePayload("test message".getBytes(StandardCharsets.UTF_8));
    underTest.storePeekMessageReply(peek);

    assertThat(underTest.getPeekedMessage("test message hash"))
        .isEqualTo("test message".getBytes(StandardCharsets.UTF_8));
  }

  @Test
  public void testStoreSkippedMessage() {
    AutoQuarantineRuleRepository autoQuarantineRuleRepository =
        mock(AutoQuarantineRuleRepository.class);
    when(autoQuarantineRuleRepository.findAll()).thenReturn(Collections.emptyList());
    CachingDataStore underTest = new CachingDataStore(autoQuarantineRuleRepository, 0);
    SkippedMessage skippedMessage = new SkippedMessage();
    skippedMessage.setMessageHash("test message hash");
    underTest.storeSkippedMessage(skippedMessage);

    assertThat(underTest.getSkippedMessages("test message hash")).contains(skippedMessage);
    assertThat(underTest.getAllSkippedMessages())
        .containsOnlyKeys("test message hash")
        .containsValue(List.of(skippedMessage));
  }

  @Test
  public void testStoreTwoSkippedMessages() {
    AutoQuarantineRuleRepository autoQuarantineRuleRepository =
        mock(AutoQuarantineRuleRepository.class);
    when(autoQuarantineRuleRepository.findAll()).thenReturn(Collections.emptyList());
    CachingDataStore underTest = new CachingDataStore(autoQuarantineRuleRepository, 0);
    SkippedMessage skippedMessageOne = new SkippedMessage();
    skippedMessageOne.setMessageHash("test message hash");
    skippedMessageOne.setSubscription("test subscription one");
    underTest.storeSkippedMessage(skippedMessageOne);

    SkippedMessage skippedMessageTwo = new SkippedMessage();
    skippedMessageTwo.setMessageHash("test message hash");
    skippedMessageTwo.setSubscription("test subscription two");
    underTest.storeSkippedMessage(skippedMessageTwo);

    assertThat(underTest.getSkippedMessages("test message hash"))
        .contains(skippedMessageOne, skippedMessageTwo);
  }

  @Test
  public void testReset() {
    AutoQuarantineRuleRepository autoQuarantineRuleRepository =
        mock(AutoQuarantineRuleRepository.class);
    when(autoQuarantineRuleRepository.findAll()).thenReturn(Collections.emptyList());
    CachingDataStore underTest = new CachingDataStore(autoQuarantineRuleRepository, 0);
    SkippedMessage skippedMessageOne = new SkippedMessage();
    skippedMessageOne.setMessageHash("test message hash");
    skippedMessageOne.setSubscription("test subscription one");
    underTest.storeSkippedMessage(skippedMessageOne);

    SkippedMessage skippedMessageTwo = new SkippedMessage();
    skippedMessageTwo.setMessageHash("test message hash");
    skippedMessageTwo.setSubscription("test subscription two");
    underTest.storeSkippedMessage(skippedMessageTwo);

    Peek peek = new Peek();
    peek.setMessageHash("test message hash");
    peek.setMessagePayload("test message".getBytes(StandardCharsets.UTF_8));
    underTest.storePeekMessageReply(peek);

    ExceptionReport exceptionReportOne = new ExceptionReport();
    exceptionReportOne.setMessageHash("test message hash");
    exceptionReportOne.setExceptionClass("test class");
    exceptionReportOne.setExceptionMessage("test exception message");
    exceptionReportOne.setSubscription("test subscription");
    exceptionReportOne.setService("test service");

    underTest.updateStats(exceptionReportOne);

    ExceptionReport exceptionReportTwo = new ExceptionReport();
    exceptionReportTwo.setMessageHash("another test message hash");
    exceptionReportTwo.setExceptionClass("test class");
    exceptionReportTwo.setExceptionMessage("test exception message");
    exceptionReportTwo.setSubscription("test subscription");
    exceptionReportTwo.setService("test service");
    underTest.updateStats(exceptionReportTwo);
    underTest.updateStats(exceptionReportTwo);
    underTest.updateStats(exceptionReportTwo);
    underTest.updateStats(exceptionReportTwo);

    SkippedMessage skippedMessage = new SkippedMessage();
    skippedMessage.setMessageHash("test message hash");
    underTest.storeSkippedMessage(skippedMessage);

    assertThat(underTest.getSkippedMessages("test message hash")).contains(skippedMessage);
    assertThat(underTest.getAllSkippedMessages().get("test message hash")).contains(skippedMessage);

    underTest.reset(java.util.Optional.empty());

    assertThat(underTest.getSkippedMessages("test message hash")).contains(skippedMessage);
    assertThat(underTest.getAllSkippedMessages().get("test message hash")).contains(skippedMessage);

    assertThat(underTest.getAllSkippedMessages()).isNotEmpty();
    assertThat(underTest.getPeekedMessage("test message hash")).isNullOrEmpty();
    assertThat(underTest.getBadMessageReports("test message hash")).isEmpty();
    assertThat(underTest.getBadMessageReports("test message hash")).isEmpty();
    assertThat(underTest.getAllSkippedMessages()).isNotEmpty();
    assertThat(underTest.getAllSkippedMessages()).isNotEmpty();
  }

  @Test
  public void testResetUnseenMessages() {
    AutoQuarantineRuleRepository autoQuarantineRuleRepository =
        mock(AutoQuarantineRuleRepository.class);
    when(autoQuarantineRuleRepository.findAll()).thenReturn(Collections.emptyList());
    CachingDataStore underTest = new CachingDataStore(autoQuarantineRuleRepository, 0);

    ExceptionReport exceptionReportOne = new ExceptionReport();
    exceptionReportOne.setMessageHash("test message hash");
    exceptionReportOne.setExceptionClass("test class");
    exceptionReportOne.setExceptionMessage("test exception message");
    exceptionReportOne.setSubscription("test subscription");
    exceptionReportOne.setService("test service");

    underTest.updateStats(exceptionReportOne);

    ExceptionReport exceptionReportTwo = new ExceptionReport();
    exceptionReportTwo.setMessageHash("another test message hash");
    exceptionReportTwo.setExceptionClass("test class");
    exceptionReportTwo.setExceptionMessage("test exception message");
    exceptionReportTwo.setSubscription("test subscription");
    exceptionReportTwo.setService("test service");
    underTest.updateStats(exceptionReportTwo);
    underTest.reset(Optional.of(0));

    assertThat(underTest.getBadMessageReports("test message hash")).isEmpty();
    assertThat(underTest.getBadMessageReports("another test message hash")).isEmpty();
  }

  @Test
  public void testResetDoesNotRemoveMessages() {
    AutoQuarantineRuleRepository autoQuarantineRuleRepository =
        mock(AutoQuarantineRuleRepository.class);
    when(autoQuarantineRuleRepository.findAll()).thenReturn(Collections.emptyList());
    CachingDataStore underTest = new CachingDataStore(autoQuarantineRuleRepository, 0);

    ExceptionReport exceptionReportOne = new ExceptionReport();
    exceptionReportOne.setMessageHash("test message hash");
    exceptionReportOne.setExceptionClass("test class");
    exceptionReportOne.setExceptionMessage("test exception message");
    exceptionReportOne.setSubscription("test subscription");
    exceptionReportOne.setService("test service");

    underTest.updateStats(exceptionReportOne);
    underTest.skipMessage("test message hash", "foo@bar.com");

    ExceptionReport exceptionReportTwo = new ExceptionReport();
    exceptionReportTwo.setMessageHash("another test message hash");
    exceptionReportTwo.setExceptionClass("test class");
    exceptionReportTwo.setExceptionMessage("test exception message");
    exceptionReportTwo.setSubscription("test subscription");
    exceptionReportTwo.setService("test service");
    underTest.updateStats(exceptionReportTwo);

    underTest.reset(Optional.of(1000));

    assertThat(underTest.getBadMessageReports("test message hash")).isNotEmpty();

    assertThat(underTest.getBadMessageReports("another test message hash")).isNotEmpty();
  }

  @Test
  public void testGetQuarantineRules() {
    // Given
    List<AutoQuarantineRule> expectedAutoQuarantineRules = Collections.EMPTY_LIST;
    AutoQuarantineRuleRepository autoQuarantineRuleRepository =
        mock(AutoQuarantineRuleRepository.class);
    when(autoQuarantineRuleRepository.findAll()).thenReturn(expectedAutoQuarantineRules);
    CachingDataStore underTest = new CachingDataStore(autoQuarantineRuleRepository, 0);

    // When
    List<AutoQuarantineRule> actualQuarantineRules = underTest.getQuarantineRules();

    // Then
    assertThat(actualQuarantineRules).isEqualTo(expectedAutoQuarantineRules);
  }

  @Test
  public void testDeleteQuarantineRule() {
    List<AutoQuarantineRule> expectedAutoQuarantineRules = Collections.EMPTY_LIST;
    AutoQuarantineRuleRepository autoQuarantineRuleRepository =
        mock(AutoQuarantineRuleRepository.class);
    when(autoQuarantineRuleRepository.findAll()).thenReturn(expectedAutoQuarantineRules);
    CachingDataStore underTest = new CachingDataStore(autoQuarantineRuleRepository, 0);
    UUID testId = UUID.randomUUID();

    // When
    underTest.deleteQuarantineRule(testId.toString());

    // Then
    verify(autoQuarantineRuleRepository).deleteById(eq(testId));
  }
}
