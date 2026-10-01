# Imagen del backend para Railway (y para probar en local con docker-compose.yml).
# Los tests NO se corren aquí (necesitan PostgreSQL): correr ./gradlew test antes de hacer merge a main.

# ---------- Etapa 1: compilar el .jar ----------
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# Primero solo Gradle y build.gradle: así Docker reutiliza las dependencias descargadas
# mientras no cambie build.gradle.
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN ./gradlew --no-daemon dependencies > /dev/null

COPY src src
RUN ./gradlew --no-daemon bootJar -x test

# ---------- Etapa 2: solo lo necesario para ejecutar ----------
FROM eclipse-temurin:21-jre
WORKDIR /app

# Usuario sin privilegios: si alguien comprometiera la app, no sería root dentro del contenedor.
RUN useradd --system --uid 1001 app
COPY --from=build /app/build/libs/*.jar app.jar
USER app

# TZ: hora de Bolivia para LocalDateTime.now() y la sesión de PostgreSQL.
# Memoria: el plan Free de Railway da 0,5 GB; la JVM usa como máximo el 60 % para el heap (deja margen al resto de la JVM).
ENV TZ=America/La_Paz \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=60 -XX:+UseSerialGC -XX:+ExitOnOutOfMemoryError"

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
