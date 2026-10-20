# app.jar 는 GitHub Actions 가 빌드해서 이 폴더에 넣어 서버로 전송한다.
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 1001 angela
COPY app.jar app.jar
USER angela
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
