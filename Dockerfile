FROM eclipse-temurin:21-jre
WORKDIR /app
COPY foci-trace.jar /app/foci-trace.jar
COPY lib /app/lib
RUN mkdir -p /app/data /app/uploads
ENV FOCI_PORT=8080 FOCI_DATA_DIR=/app/data FOCI_UPLOAD_DIR=/app/uploads FOCI_COOKIE_SECURE=false
EXPOSE 8080
ENTRYPOINT ["java","--add-modules","jdk.httpserver","-jar","/app/foci-trace.jar"]
