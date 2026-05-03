#!/bin/sh
set -e

echo "Running database migration..."
# Выполняем SQL-файл с помощью переменных окружения, которые есть в контейнере
# (DATABASE_URL содержит полный URL подключения)
# Извлекаем параметры подключения из DATABASE_URL
# Ожидаем формат: postgres://user:password@host:port/dbname?sslmode=disable
DB_URL="${DATABASE_URL}"
# Простейший парсинг (для продакшена лучше использовать библиотеку)
# Предполагаем, что URL имеет вид postgres://trading:trading@postgres:5432/trading?sslmode=disable
PGUSER=$(echo $DB_URL | sed -n 's/.*:\/\/\([^:]*\):.*/\1/p')
PGPASSWORD=$(echo $DB_URL | sed -n 's/.*:\/\/[^:]*:\([^@]*\)@.*/\1/p')
PGHOST=$(echo $DB_URL | sed -n 's/.*@\([^:]*\):.*/\1/p')
PGPORT=$(echo $DB_URL | sed -n 's/.*:\([0-9]*\)\/.*/\1/p')
PGDATABASE=$(echo $DB_URL | sed -n 's/.*\/\([^?]*\).*/\1/p')

# Экспортируем переменные для psql
export PGPASSWORD

echo "Waiting for PostgreSQL to be ready..."
until psql -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" -d "$PGDATABASE" -c '\q' 2>/dev/null; do
  sleep 2
done

echo "Applying migration..."
psql -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" -d "$PGDATABASE" -f /migrations/001_init.sql

echo "Starting trading service..."
exec ./trading-service
