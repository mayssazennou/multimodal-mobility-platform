# rail-bridge — Java 8 car JacORB 3.9 a besoin de javax.rmi.CORBA (retiré du JDK depuis Java 11).
FROM maven:3.9-eclipse-temurin-8 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn -q -B -DskipTests package

FROM eclipse-temurin:8-jre
WORKDIR /app
COPY --from=build /app/target/rail-bridge.jar app.jar
ENV JAVA_OPTS="-Xmx256m"
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]