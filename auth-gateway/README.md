# Auth Gateway

Kotlin + Ktor service for `swagger/auth-gateway.yml`.

It owns authentication endpoints and proxies public API routes to internal services:

- `/api/v1/market/**` -> `MARKET_SERVICE_URL`
- `/api/v1/orders/**`, `/api/v1/trades/**` -> `ORDER_SERVICE_URL`
- `/api/v1/portfolio/**` -> `PORTFOLIO_SERVICE_URL`

Protected routes validate JWT and forward `X-User-Id` to internal services.

## Configuration

The values below match the root `docker-compose.yml` used to run the full stack locally.

| Variable | Value |
| --- | --- |
| `PORT` | `8080` |
| `DATABASE_URL` | `jdbc:postgresql://postgres:5432/auth_gateway` |
| `DATABASE_USER` | `auth` |
| `DATABASE_PASSWORD` | `auth` |
| `JWT_ISSUER` | `trump-investitions-auth-gateway` |
| `JWT_AUDIENCE` | `trump-investitions-clients` |
| `JWT_REALM` | `trump-investitions` |
| `JWT_SECRET` | `change-me-in-production` |
| `ACCESS_TOKEN_TTL_SECONDS` | `900` |
| `REFRESH_TOKEN_TTL_SECONDS` | `2592000` |
| `MARKET_SERVICE_URL` | `http://market-data-service:8081/api/v1` |
| `ORDER_SERVICE_URL` | `http://trading-service:8083/api/v1` |
| `PORTFOLIO_SERVICE_URL` | `http://portfolio-service:8082/api/v1` |

## Run

```bash
gradle run
```

Health check:

```bash
curl http://localhost:8080/api/v1/system/health
```
