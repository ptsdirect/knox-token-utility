# Multi-stage build for Knox Guard Token Utility
# Stage 1: Build
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY . .
# Allow skipping tests via build arg (default run tests)
ARG SKIP_TESTS=false
RUN if [ "$SKIP_TESTS" = "true" ]; then \
      mvn -q -ntp -DskipTests -Dgpg.skip=true clean package ; \
    else \
      mvn -q -ntp -Dgpg.skip=true clean verify ; \
    fi

# Stage 2: Runtime (distroless style using JRE image)
FROM eclipse-temurin:21-jre
WORKDIR /app
# Copy fat jar
COPY --from=build /workspace/target/pts-*-jar-with-dependencies.jar /app/app.jar
# Non-root user (optional: use UID 10001)
USER 10001
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
# Default: show help (TokenClient)
CMD ["--help"]
