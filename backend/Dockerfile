FROM maven:3.9.9-eclipse-temurin-21-alpine AS build
WORKDIR /workspace

RUN apk update && \
    apk add --no-cache ca-certificates curl && \
    update-ca-certificates

COPY pom.xml ./

RUN mvn -q -B -DskipTests dependency:go-offline || \
    (echo "Retry with insecure SSL..." && \
     mvn -q -B -DskipTests dependency:go-offline \
       -Dmaven.wagon.http.ssl.insecure=true \
       -Dmaven.wagon.http.ssl.allowall=true \
       -Dmaven.wagon.http.ssl.ignore.validity.dates=true)

# Build application
COPY src ./src

RUN mvn -q -B -DskipTests package -Dmaven.test.skip=true


FROM eclipse-temurin:21-jre-alpine AS production

WORKDIR /app

COPY --from=build /workspace/target/*.jar /app/app.jar

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java -jar /app/app.jar"]
