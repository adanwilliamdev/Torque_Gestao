# ---- build ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B -DskipTests package

# ---- runtime ----
FROM eclipse-temurin:21-jre
RUN useradd --system --uid 1001 torque
WORKDIR /app
# Usando o curinga *.jar para pegar o torque-gestao-3.0.0.jar ou qualquer outro nome que o Maven gerar
COPY --from=build /workspace/target/*.jar app.jar
USER torque
EXPOSE 8080
# Sem curl na imagem JRE: checa /api/health via socket TCP do bash
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD ["bash", "-c", "exec 3<>/dev/tcp/127.0.0.1/8080 && printf 'GET /api/health HTTP/1.0\\r\\n\\r\\n' >&3 && head -n1 <&3 | grep -q ' 200'"]
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]