# 빌드 단계 — Gradle 래퍼로 부트 JAR 을 만든다.
FROM eclipse-temurin:17-jdk AS build
WORKDIR /workspace

# 의존성 캐시를 살리기 위해 빌드 스크립트를 먼저 복사한다.
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon || true

COPY src ./src
RUN ./gradlew bootJar --no-daemon -x test

# 실행 단계 — JRE 만 담아 이미지를 가볍게 유지한다.
FROM eclipse-temurin:17-jre
WORKDIR /app

# PDF 리포트용 한글 폰트는 JAR 안에 번들돼 있어 별도 설치가 필요 없다.
COPY --from=build /workspace/build/libs/*.jar app.jar

# 생성된 리포트 파일 저장 위치 (compose 에서 볼륨으로 붙인다)
RUN mkdir -p /app/reports

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
