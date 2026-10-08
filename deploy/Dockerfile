# Runtime-only image: the jar is built on a developer machine or in CI
# (`npm run build` then `./mvnw package`) and copied next to this file.
# Nothing is compiled on the server — the shared box has ~1.9 GB of RAM.
FROM eclipse-temurin:17-jre

WORKDIR /app
RUN groupadd --system krugworld && useradd --system --gid krugworld krugworld
COPY krugworld.jar app.jar
USER krugworld

EXPOSE 8080
# Cap the heap: this box is shared with other projects.
ENV JAVA_OPTS="-Xms128m -Xmx384m"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
