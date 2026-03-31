FROM eclipse-temurin:17-jdk-jammy AS snapshot-install

RUN apt-get update && apt-get install -y --no-install-recommends git && rm -rf /var/lib/apt/lists/*

WORKDIR /tmp/yaci-cardano-test
RUN git clone --depth 1 https://github.com/bloxbean/yaci-cardano-test.git . && \
    ./gradlew publishToMavenLocal -x test

FROM eclipse-temurin:21-jdk-jammy AS build

COPY --from=snapshot-install /root/.m2 /root/.m2

WORKDIR /app
COPY pom.xml /app/pom.xml
COPY mvnw /app/mvnw
COPY .mvn /app/.mvn

RUN ./mvnw verify clean --fail-never -nsu
COPY . /app
RUN ./mvnw clean package -DskipTests -nsu

FROM eclipse-temurin:21-jdk-jammy AS runtime
WORKDIR /app
COPY --from=build /app/target/*.jar /app/uverify-backend.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "uverify-backend.jar"]