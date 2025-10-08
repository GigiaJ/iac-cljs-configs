FROM node:20

RUN apt-get update && \
    DEBIAN_FRONTEND=noninteractive apt-get install -y \
    curl \
    grep \
    sed \
    unzip \
    ca-certificates \
    && rm -rf /var/lib/apt/lists/*

ARG FOUNDRY_USERNAME
ARG FOUNDRY_PASSWORD

ENV FOUNDRY_USERNAME=$FOUNDRY_USERNAME
ENV FOUNDRY_PASSWORD=$FOUNDRY_PASSWORD

WORKDIR /app

COPY . .

RUN token=$(curl -s -c cookie.txt https://foundryvtt.com | grep "csrfmiddlewaretoken" | head -1 | sed 's/.*"\([^"]*\)".*/\1/') && \
    curl -s -X POST -c cookie.txt --cookie cookie.txt \
        -F "username=${FOUNDRY_USERNAME}" \
        -F "password=${FOUNDRY_PASSWORD}" \
        -F "csrfmiddlewaretoken=${token}" \
        -H "Referer: https://foundryvtt.com" \
        https://foundryvtt.com/auth/login/ && \
    release=$(curl -s --cookie cookie.txt https://foundryvtt.com/releases/ | grep -B 5 "Stable" | grep 'title="Release ' | head -1 | sed -n 's/.*\/releases\/[0-9]\+\.\([0-9]\+\).*/\1/p') && \
    curl -s -L --cookie cookie.txt -o foundryvtt.zip "https://foundryvtt.com/releases/download?build=${release}&platform=linux" && \
    unzip foundryvtt.zip && rm foundryvtt.zip

EXPOSE 30000

CMD ["node", "resources/app/main.js"]
