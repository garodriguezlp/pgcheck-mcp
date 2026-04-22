#!/bin/bash

cd "$(dirname "$0")/.."

docker-compose up -d

echo "Waiting for database to be healthy..."

while [ "`docker inspect -f {{.State.Health.Status}} pgcheck-mcp-db`" != "healthy" ]; do

  sleep 1

done

echo "Database is ready!"
