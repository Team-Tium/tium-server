# syntax=docker/dockerfile:1

# 1단계 - gradle wrapper로 bootJar를 빌드한다
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# gradle 배포본 다운로드를 소스 변경과 분리해 레이어 캐시에 태운다
COPY gradlew ./
COPY gradle gradle
RUN chmod +x gradlew && ./gradlew --no-daemon --version

# 의존성 해석도 소스와 분리한다. 소스만 고치면 이 레이어는 재사용된다
COPY settings.gradle build.gradle ./
RUN ./gradlew --no-daemon dependencies

# 테스트는 Actions의 ci job에서 이미 돌리므로 여기서는 건너뛴다
COPY src src
RUN ./gradlew --no-daemon clean bootJar -x test \
 && find build/libs -name '*.jar' ! -name '*-plain.jar' -exec cp {} /workspace/app.jar \;

# 2단계 - JRE만 올린 실행 이미지
FROM eclipse-temurin:21-jre
WORKDIR /app

# 컨테이너 헬스체크에 쓸 curl만 넣는다
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/*

# root로 실행하지 않는다
RUN useradd --system --create-home --shell /usr/sbin/nologin tium
COPY --from=build --chown=tium:tium /workspace/app.jar app.jar
USER tium

EXPOSE 8080

# EC2 메모리가 908MB로 빠듯해 힙 상한을 컨테이너 메모리 기준으로 잡는다
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
