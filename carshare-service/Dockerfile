# Contexte de build = racine du dépôt (le service dépend du module canonical-model) :
#   docker build -f carshare-service/Dockerfile .
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /build
COPY canonical-model/pom.xml canonical-model/pom.xml
COPY canonical-model/src canonical-model/src
RUN mvn -q -B -f canonical-model/pom.xml install -DskipTests
COPY carshare-service/pom.xml carshare-service/pom.xml
COPY carshare-service/src carshare-service/src
RUN mvn -q -B -f carshare-service/pom.xml package -DskipTests

FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /build/carshare-service/target/carshare-service.jar app.jar
ENV JAVA_OPTS="-Xmx192m"
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
