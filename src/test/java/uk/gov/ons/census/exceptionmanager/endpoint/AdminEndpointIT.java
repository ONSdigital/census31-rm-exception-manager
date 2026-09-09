package uk.gov.ons.census.exceptionmanager.endpoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpStatus.OK;

import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.Unirest;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import uk.gov.ons.census.exceptionmanager.model.repository.QuarantinedMessageRepository;
import uk.gov.ons.census.exceptionmanager.persistence.CachingDataStore;

@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public class AdminEndpointIT {
  private static final String TEST_MESSAGE_HASH =
      "9af5350f1e61149cd0bb7dfa5efae46f224aaaffed729b220d63e0fe5a8bf4b9";
  private static final ObjectMapper objectMapper = new ObjectMapper();

  @LocalServerPort private int port;

  @Autowired private QuarantinedMessageRepository quarantinedMessageRepository;

  @Autowired private CachingDataStore cachingDataStore;

  @BeforeEach
  @Transactional
  public void setUp() {
    quarantinedMessageRepository.deleteAllInBatch();
    cachingDataStore.reset(Optional.empty());
  }

  @Test
  public void testGetBadMessages() throws Exception {
    Map<String, String> headers = new HashMap<>();
    headers.put("accept", "application/json");
    headers.put("Content-Type", "application/json");
    HttpResponse<String> response =
        Unirest.get(String.format("http://localhost:%d/badmessages", port))
            .headers(headers)
            .asString();

    assertThat(response.getStatus()).isEqualTo(OK.value());

    Set actualResponse = objectMapper.readValue(response.getBody(), Set.class);
    assertThat(actualResponse.size()).isEqualTo(0);
  }

  @Test
  public void testGetBadMessagesMinimumSeenCount() throws Exception {
    ReportingEndpointIT.reportException(port, TEST_MESSAGE_HASH);

    Map<String, String> headers = new HashMap<>();
    headers.put("accept", "application/json");
    headers.put("Content-Type", "application/json");
    HttpResponse<String> response =
        Unirest.get(String.format("http://localhost:%d/badmessages?minimumSeenCount=2", port))
            .headers(headers)
            .asString();

    assertThat(response.getStatus()).isEqualTo(OK.value());

    Set actualResponse = objectMapper.readValue(response.getBody(), Set.class);
    assertThat(actualResponse.size()).isEqualTo(0);

    // Now report a second exception
    ReportingEndpointIT.reportException(port, TEST_MESSAGE_HASH);

    response =
        Unirest.get(String.format("http://localhost:%d/badmessages?minimumSeenCount=2", port))
            .headers(headers)
            .asString();

    assertThat(response.getStatus()).isEqualTo(OK.value());

    actualResponse = objectMapper.readValue(response.getBody(), Set.class);
    assertThat(actualResponse.size()).isEqualTo(1);
    assertThat(actualResponse).containsExactly(TEST_MESSAGE_HASH);
  }

  @Test
  public void testGetBadMessage() throws Exception {
    Map<String, String> headers = new HashMap<>();
    headers.put("accept", "application/json");
    headers.put("Content-Type", "application/json");
    HttpResponse<String> response =
        Unirest.get(String.format("http://localhost:%d/badmessage/%s", port, TEST_MESSAGE_HASH))
            .headers(headers)
            .asString();

    assertThat(response.getStatus()).isEqualTo(OK.value());

    List actualResponse = objectMapper.readValue(response.getBody(), List.class);
    assertThat(actualResponse.size()).isEqualTo(0);
  }

  @Test
  public void testGetBadMessageSummary() throws Exception {
    Map<String, String> headers = new HashMap<>();
    headers.put("accept", "application/json");
    headers.put("Content-Type", "application/json");
    HttpResponse<String> response =
        Unirest.get(String.format("http://localhost:%d/badmessages/summary", port))
            .headers(headers)
            .asString();

    assertThat(response.getStatus()).isEqualTo(OK.value());

    List actualResponse = objectMapper.readValue(response.getBody(), List.class);
    assertThat(actualResponse.size()).isEqualTo(0);
  }

  @Test
  public void testGetBadMessageSummaryMinimumSeenCount() throws Exception {
    ReportingEndpointIT.reportException(port, TEST_MESSAGE_HASH);

    Map<String, String> headers = new HashMap<>();
    headers.put("accept", "application/json");
    headers.put("Content-Type", "application/json");
    HttpResponse<String> response =
        Unirest.get(
                String.format("http://localhost:%d/badmessages/summary?minimumSeenCount=2", port))
            .headers(headers)
            .asString();

    assertThat(response.getStatus()).isEqualTo(OK.value());

    List<Map> actualResponse = objectMapper.readValue(response.getBody(), List.class);
    assertThat(actualResponse.size()).isEqualTo(0);

    // Now send a second exception
    ReportingEndpointIT.reportException(port, TEST_MESSAGE_HASH);

    response =
        Unirest.get(
                String.format("http://localhost:%d/badmessages/summary?minimumSeenCount=2", port))
            .headers(headers)
            .asString();

    assertThat(response.getStatus()).isEqualTo(OK.value());

    actualResponse = objectMapper.readValue(response.getBody(), List.class);
    assertThat(actualResponse.size()).isEqualTo(1);
    assertThat(actualResponse.get(0).get("messageHash")).isEqualTo(TEST_MESSAGE_HASH);
    assertThat(actualResponse.get(0).get("seenCount")).isEqualTo(2);
  }
}
