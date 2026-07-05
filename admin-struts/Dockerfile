# ---- build stage ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
COPY common/pom.xml common/
COPY core-api/pom.xml core-api/
COPY admin-struts/pom.xml admin-struts/
# Warm the dependency layer cache; failures here only cost cache efficiency.
RUN mvn -B -q -pl admin-struts -am dependency:go-offline || true
COPY common/src common/src
COPY admin-struts/src admin-struts/src
RUN mvn -B -q -pl admin-struts -am package -DskipTests

# ---- runtime stage ----
FROM eclipse-temurin:8-jre-alpine
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=build /build/admin-struts/target/admin-struts-*.jar app.jar
RUN chown -R app:app /app
USER app
EXPOSE 8081
HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=3 \
  CMD wget -qO- http://localhost:8081/actuator/health | grep -q '"status":"UP"' || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
