FROM eclipse-temurin:21-jdk AS runtime

WORKDIR /app

COPY target/trades-capture-service-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8080

ENV JAVA_OPTS=""

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
