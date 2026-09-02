package uk.gov.ons.census.exceptionmanager.model.entity;

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.Type;
import org.hibernate.type.SqlTypes;
import tools.jackson.databind.JsonNode;

@Data
@Entity
public class QuarantinedMessage {
  @Id private UUID id;

  @Column(columnDefinition = "timestamp with time zone")
  @CreationTimestamp
  private OffsetDateTime skippedTimestamp;

  @Column private String messageHash;

  @Lob
  @JdbcTypeCode(SqlTypes.VARBINARY)
  @Column
  private byte[] messagePayload;

  @Column private String service;

  @Column private String subscription;

  @Column private String routingKey;

  @Column private String contentType;

  @Column private String skippingUser;

  @Type(JsonBinaryType.class)
  @Column(columnDefinition = "jsonb")
  private Map<String, JsonNode> headers;

  @Type(JsonBinaryType.class)
  @Column(columnDefinition = "jsonb")
  private String errorReports;
}
