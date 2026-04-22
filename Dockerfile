
# 빌드 환경 베이스 이미지 지정 
FROM amazoncorretto:21-al2023 AS builder
# 작업 디렉토리 설정
WORKDIR /app

# Gradle 파일 먼저 복사 (캐시 활용)
COPY gradlew .
COPY gradle gradle
COPY build.gradle.kts .
COPY settings.gradle.kts .
COPY src src

# 빌드 명령어 실행 
RUN ./gradlew build -x test

# 실행환경 베이스 이미지 지정
FROM amazoncorretto:21-al2023-jre
WORKDIR /app

# 보안을 위한 Non-root 유저 생성
RUN groupadd -g 1000 appuser && \
    useradd -u 1000 -g appuser -m appuser
USER appuser

# 빌드 결과물 복사
COPY --from=builder /app/build/libs/*-SNAPSHOT.jar app.jar

# HEALTHCHECK 추가
HEALTHCHECK --interval=30s --timeout=3s \
  CMD curl -f http://localhost:8080/actuator/health/readiness || exit 1

# JVM 옵션 적용
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=60.0", "-jar", "app.jar"]