# ---------- Étape 1 : build (génération JAXB + jar) ----------
FROM maven:3-eclipse-temurin-25 AS build
WORKDIR /build

# Cache des dépendances : on copie d'abord le pom seul
COPY pom.xml .
RUN mvn -B dependency:go-offline

# Puis les sources (dont src/main/resources/wsdl/air.wsdl)
COPY src ./src
RUN mvn -B package -DskipTests

# ---------- Étape 2 : exécution ----------
FROM eclipse-temurin:25-jre
WORKDIR /app

COPY --from=build /build/target/air-bridge-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]