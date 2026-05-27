FROM eclipse-temurin:21-jre-alpine

CMD ["java", "-jar", "/opt/census-rm-exception-manager.jar"]
# Create a system group and user without forcing UID/GID
RUN addgroup --system exceptionmanager && \
    adduser --system --ingroup exceptionmanager exceptionmanager

USER exceptionmanager

COPY target/census-rm-exception-manager*.jar /opt/census-rm-exception-manager.jar
