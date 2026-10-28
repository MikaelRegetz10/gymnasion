# Estágio 1: Build (Compilação)
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app

# Copia arquivos de dependência e baixa em cache
COPY pom.xml .
COPY .mvn .mvn
COPY mvnw .
RUN chmod +x mvnw
RUN ./mvnw dependency:go-offline -B

# Copia o código-fonte e gera o JAR ignorando testes (os testes rodam no CI)
COPY src ./src
RUN ./mvnw package -DskipTests -B

# Estágio 2: Runtime (Execução)
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Roda a aplicação com um usuário sem privilégios
RUN addgroup -S spring && adduser -S spring -G spring

COPY --from=build --chown=spring:spring /app/target/*.jar app.jar

USER spring

# Ajusta o heap ao limite de memória do container; a JVM lê JAVA_TOOL_OPTIONS sozinha,
# então dá para sobrescrever pelo compose sem mudar o ENTRYPOINT
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
