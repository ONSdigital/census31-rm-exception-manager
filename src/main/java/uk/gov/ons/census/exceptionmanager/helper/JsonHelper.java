package uk.gov.ons.census.exceptionmanager.helper;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

public class JsonHelper {
  private static final ObjectMapper objectMapper =
      JsonMapper.builder().disable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY).build();

  private JsonHelper() {}

  public static String convertObjectToJson(Object obj) {
    try {
      return objectMapper.writeValueAsString(obj);
    } catch (JacksonException e) {
      throw new RuntimeException("Failed converting Object To Json", e);
    }
  }
}
