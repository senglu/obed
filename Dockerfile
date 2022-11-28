FROM eclipse-temurin:11.0.17_8-jre-alpine
COPY /target/kam-dnes-na-obed-1.0.jar .

EXPOSE 5000
ENTRYPOINT ["java", "-jar", "kam-dnes-na-obed-1.0.jar" ]

