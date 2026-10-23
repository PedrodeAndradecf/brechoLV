















































FROM maven:3.9-eclipse-temurin-25 AS build

WORKDIR /build



COPY pom.xml .
RUN mvn dependency:go-offline -B

COPY src ./src
RUN mvn clean package -DskipTests -B




FROM eclipse-temurin:25-jre-alpine


RUN addgroup -S brecho && adduser -S brecho -G brecho

WORKDIR /app










RUN apk add --no-cache wget

COPY --from=build /build/target/*.jar app.jar

RUN chown -R brecho:brecho /app
USER brecho

EXPOSE 8080

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+UseContainerSupport"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
