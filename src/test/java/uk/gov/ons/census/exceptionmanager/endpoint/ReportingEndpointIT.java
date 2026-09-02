package uk.gov.ons.census.exceptionmanager.endpoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpStatus.OK;

import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.JsonNodeFactory;
import uk.gov.ons.census.exceptionmanager.model.dto.AutoQuarantineRule;
import uk.gov.ons.census.exceptionmanager.model.dto.ExceptionReport;
import uk.gov.ons.census.exceptionmanager.model.dto.Peek;
import uk.gov.ons.census.exceptionmanager.model.dto.Response;
import uk.gov.ons.census.exceptionmanager.model.dto.SkippedMessage;
import uk.gov.ons.census.exceptionmanager.model.entity.QuarantinedMessage;
import uk.gov.ons.census.exceptionmanager.model.repository.AutoQuarantineRuleRepository;
import uk.gov.ons.census.exceptionmanager.model.repository.QuarantinedMessageRepository;
import uk.gov.ons.census.exceptionmanager.persistence.CachingDataStore;

@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public class ReportingEndpointIT {
  private static final String TEST_MESSAGE_HASH =
      "9af5350f1e61149cd0bb7dfa5efae46f224aaaffed729b220d63e0fe5a8bf4b8";
  private static final ObjectMapper objectMapper =
      JsonMapper.builder().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();

  @Autowired private CachingDataStore cachingDataStore;
  @Autowired private QuarantinedMessageRepository quarantinedMessageRepository;
  @Autowired private AutoQuarantineRuleRepository autoQuarantineRuleRepository;

  @LocalServerPort private int port;

  @BeforeEach
  public void setUp() {
    quarantinedMessageRepository.deleteAllInBatch();
    cachingDataStore.reset(Optional.empty());
    autoQuarantineRuleRepository.deleteAllInBatch();
  }

  @Test
  public void testReportException() throws Exception {
    HttpResponse<String> response = reportException(port, TEST_MESSAGE_HASH);

    assertThat(response.getStatus()).isEqualTo(OK.value());

    Response actualResponse = objectMapper.readValue(response.getBody(), Response.class);
    assertThat(actualResponse.isSkipIt()).isFalse();
    assertThat(actualResponse.isLogIt()).isTrue();
    assertThat(actualResponse.isPeek()).isFalse();
    assertThat(actualResponse.isThrowAway()).isFalse();

    assertThat(cachingDataStore.getBadMessageReports(TEST_MESSAGE_HASH).size()).isEqualTo(1);
  }

  @Test
  public void testReportExceptionAutoQuarantine() throws Exception {
    AutoQuarantineRule autoQuarantineRule = new AutoQuarantineRule();
    autoQuarantineRule.setExpression("exceptionMessage.contains('quarantine_me')");
    autoQuarantineRule.setQuarantine(true);
    autoQuarantineRule.setRuleExpiryDateTime(OffsetDateTime.now().plusMinutes(1));

    ExceptionReport exceptionReport = new ExceptionReport();
    exceptionReport.setMessageHash(TEST_MESSAGE_HASH);
    exceptionReport.setService("test service");
    exceptionReport.setSubscription("test subscription");
    exceptionReport.setExceptionClass("test class");
    exceptionReport.setExceptionMessage("test quarantine_me message");

    Map<String, String> headers = new HashMap<>();
    headers.put("accept", "application/json");
    headers.put("Content-Type", "application/json");
    HttpResponse<String> response =
        Unirest.post(String.format("http://localhost:%d/quarantinerule", port))
            .headers(headers)
            .body(objectMapper.writeValueAsString(autoQuarantineRule))
            .asString();

    assertThat(response.getStatus()).isEqualTo(OK.value());

    response =
        Unirest.post(String.format("http://localhost:%d/reportexception", port))
            .headers(headers)
            .body(objectMapper.writeValueAsString(exceptionReport))
            .asString();

    assertThat(response.getStatus()).isEqualTo(OK.value());

    Response actualResponse = objectMapper.readValue(response.getBody(), Response.class);
    assertThat(actualResponse.isSkipIt()).isTrue();
    assertThat(actualResponse.isLogIt()).isTrue();
    assertThat(actualResponse.isPeek()).isFalse();
    assertThat(actualResponse.isThrowAway()).isFalse();

    assertThat(cachingDataStore.getBadMessageReports(TEST_MESSAGE_HASH).size()).isEqualTo(1);
  }

  @Test
  void quarantinedMessageHeadersRoundTripThroughJsonb() {
    QuarantinedMessage msg = new QuarantinedMessage();
    msg.setId(UUID.randomUUID());
    msg.setMessageHash("hash");
    msg.setHeaders(
        Map.of("amqp_receivedRoutingKey", JsonNodeFactory.instance.textNode("case.rh.case")));

    quarantinedMessageRepository.saveAndFlush(msg);

    QuarantinedMessage read = quarantinedMessageRepository.findById(msg.getId()).orElseThrow();
    assertThat(read.getHeaders()).isEqualTo(msg.getHeaders());
  }

  @Test
  public void testReportExceptionAutoQuarantineExpiredRule() throws Exception {
    AutoQuarantineRule autoQuarantineRule = new AutoQuarantineRule();
    autoQuarantineRule.setExpression("exceptionMessage.contains('expired_rule')");
    autoQuarantineRule.setQuarantine(true);
    autoQuarantineRule.setRuleExpiryDateTime(OffsetDateTime.now().minusSeconds(1));

    ExceptionReport exceptionReport = new ExceptionReport();
    exceptionReport.setMessageHash(TEST_MESSAGE_HASH);
    exceptionReport.setService("test service");
    exceptionReport.setSubscription("test subscription");
    exceptionReport.setExceptionClass("test class");
    exceptionReport.setExceptionMessage("test expired_rule message");

    Map<String, String> headers = new HashMap<>();
    headers.put("accept", "application/json");
    headers.put("Content-Type", "application/json");
    HttpResponse<String> response =
        Unirest.post(String.format("http://localhost:%d/quarantinerule", port))
            .headers(headers)
            .body(objectMapper.writeValueAsString(autoQuarantineRule))
            .asString();

    assertThat(response.getStatus()).isEqualTo(OK.value());

    response =
        Unirest.post(String.format("http://localhost:%d/reportexception", port))
            .headers(headers)
            .body(objectMapper.writeValueAsString(exceptionReport))
            .asString();

    assertThat(response.getStatus()).isEqualTo(OK.value());

    Response actualResponse = objectMapper.readValue(response.getBody(), Response.class);
    assertThat(actualResponse.isSkipIt()).isFalse();
    assertThat(actualResponse.isLogIt()).isTrue();
    assertThat(actualResponse.isPeek()).isFalse();
    assertThat(actualResponse.isThrowAway()).isFalse();

    assertThat(cachingDataStore.getBadMessageReports(TEST_MESSAGE_HASH).size()).isEqualTo(1);
  }

  @Test
  public void testPeekReply() throws Exception {
    Peek peek = new Peek();
    peek.setMessageHash(TEST_MESSAGE_HASH);
    peek.setMessagePayload("test payload".getBytes(StandardCharsets.UTF_8));

    Map<String, String> headers = new HashMap<>();
    headers.put("accept", "application/json");
    headers.put("Content-Type", "application/json");
    HttpResponse<String> response =
        Unirest.post(String.format("http://localhost:%d/peekreply", port))
            .headers(headers)
            .body(objectMapper.writeValueAsString(peek))
            .asString();

    assertThat(response.getStatus()).isEqualTo(OK.value());

    assertThat(cachingDataStore.getPeekedMessage(TEST_MESSAGE_HASH))
        .isEqualTo(peek.getMessagePayload());
  }

  @Test
  public void testStoreSkippedMessage() throws Exception {
    testReportException();

    SkippedMessage skippedMessage = new SkippedMessage();
    skippedMessage.setMessageHash(TEST_MESSAGE_HASH);
    skippedMessage.setSubscription("test subscription");
    skippedMessage.setRoutingKey("test routing key");
    skippedMessage.setContentType("application/xml");
    skippedMessage.setHeaders(Map.of("foo", "bar"));
    skippedMessage.setMessagePayload("<noodle>poodle</noodle>".getBytes(StandardCharsets.UTF_8));
    skippedMessage.setService("test service");
    skippedMessage.setSkippedTimestamp(null);

    Map<String, String> headers = new HashMap<>();
    headers.put("accept", "application/json");
    headers.put("Content-Type", "application/json");
    HttpResponse<String> response =
        Unirest.post(String.format("http://localhost:%d/storeskippedmessage", port))
            .headers(headers)
            .body(objectMapper.writeValueAsString(skippedMessage))
            .asString();

    assertThat(response.getStatus()).isEqualTo(OK.value());

    List<SkippedMessage> skippedMessages = cachingDataStore.getSkippedMessages(TEST_MESSAGE_HASH);
    assertThat(skippedMessages.size()).isEqualTo(1);
    assertThat(skippedMessages.get(0)).isEqualTo(skippedMessage);

    List<QuarantinedMessage> allQuarantinedMessages = quarantinedMessageRepository.findAll();
    assertThat(allQuarantinedMessages.size()).isEqualTo(1);
    QuarantinedMessage quarantinedMessage = allQuarantinedMessages.get(0);
    assertThat(quarantinedMessage.getContentType()).isEqualTo(skippedMessage.getContentType());
    assertThat(quarantinedMessage.getHeaders().size())
        .isEqualTo(skippedMessage.getHeaders().size());
    assertThat(quarantinedMessage.getHeaders().get("foo"))
        .isEqualTo(JsonNodeFactory.instance.textNode("bar"));
    assertThat(quarantinedMessage.getMessagePayload())
        .isEqualTo(skippedMessage.getMessagePayload());
    assertThat(quarantinedMessage.getRoutingKey()).isEqualTo(skippedMessage.getRoutingKey());
    assertThat(quarantinedMessage.getService()).isEqualTo(skippedMessage.getService());
    assertThat(quarantinedMessage.getSkippedTimestamp()).isNotNull();
    assertThat(quarantinedMessage.getErrorReports()).isNotBlank();
  }

  public static HttpResponse<String> reportException(int port, String messageHash)
      throws JacksonException, UnirestException {
    ExceptionReport exceptionReport = new ExceptionReport();
    exceptionReport.setMessageHash(messageHash);
    exceptionReport.setService("test service");
    exceptionReport.setSubscription("test subscription");
    exceptionReport.setExceptionClass("test class");
    exceptionReport.setExceptionMessage("test message");
    exceptionReport.setExceptionRootCause("test root cause");

    Map<String, String> headers = new HashMap<>();
    headers.put("accept", "application/json");
    headers.put("Content-Type", "application/json");
    return Unirest.post(String.format("http://localhost:%d/reportexception", port))
        .headers(headers)
        .body(objectMapper.writeValueAsString(exceptionReport))
        .asString();
  }
}
