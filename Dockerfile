
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
RUN dnf install -y findutils && ./gradlew build -x test

# 실행환경 베이스 이미지 지정
FROM amazoncorretto:21-al2023-headless
WORKDIR /app

# shadow-utils: useradd용 (curl은 베이스 이미지에 이미 포함됨 → docker-compose healthcheck 가능)
RUN dnf install -y shadow-utils && \
    groupadd -g 1000 appuser && \
    useradd -u 1000 -g appuser -m appuser && \
    dnf clean all
USER appuser

# 빌드 결과물 복사
COPY --from=builder /app/build/libs/*-SNAPSHOT.jar app.jar

# HEALTHCHECK는 docker-compose.deploy.yml 레벨에서 정의 (SERVER_PORT 가변 처리 위해)

# JVM 옵션 적용
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=60.0", "-jar", "app.jar"]