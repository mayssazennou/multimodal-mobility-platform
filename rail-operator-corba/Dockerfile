# Opérateur ferroviaire — serveur CORBA (JacORB). FOURNI, ne pas modifier.
# Build multi-étapes : compilation Maven puis image d'exécution légère.

FROM maven:3.9-eclipse-temurin-8 AS build
WORKDIR /app
COPY pom.xml .
# Pré-téléchargement des dépendances (cache de couche)
RUN mvn -q -B -e dependency:go-offline || true
COPY src ./src
RUN mvn -q -B -DskipTests package

FROM eclipse-temurin:8-jre
WORKDIR /app
COPY --from=build /app/target/rail-operator-corba-1.0.0-jar-with-dependencies.jar app.jar
# Port IIOP annoncé dans l'IOR (voir docker-compose.yml)
EXPOSE 1050
ENTRYPOINT ["java", "-jar", "app.jar"]
