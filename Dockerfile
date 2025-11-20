# =========================
# 1️⃣ Build Stage (Construction du JAR)
# =========================
FROM maven:3.9.9-eclipse-temurin-21 AS builder
WORKDIR /app

# Copie pom.xml pour télécharger les dépendances (optimisation du cache)
COPY pom.xml .
RUN mvn dependency:go-offline

# Copie le code source et lance la compilation, en ignorant les tests
COPY src ./src
RUN mvn clean package -DskipTests

# =========================
# 2️⃣ Runtime Stage (Exécution légère)
# =========================
FROM eclipse-temurin:21-jre
WORKDIR /app

# Copie le JAR construit dans l'étape 'builder'
COPY --from=builder /app/target/transactions-service-*.jar app.jar

# Expose le port de Transaction Service (8085 selon .env)
EXPOSE 8085

# Commande pour démarrer l'application
ENTRYPOINT ["java", "-jar", "app.jar"]