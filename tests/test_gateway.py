"""
End-to-end integration tests for the full backend via auth-gateway.

All requests go through http://localhost:8080/api/v1 — no direct service calls.
JWT is obtained via /auth/login and injected into every protected request.
X-User-Id is NOT sent by the client; the gateway extracts it from the JWT.

Usage:
    pip install requests
    python tests/test_gateway.py [--base-url http://localhost:8080/api/v1] [--kafka-wait 3]
"""

import argparse
import random
import string
import sys
import time
import requests as _requests

TIMEOUT = 10


class _Session:
    def get(self, url, **kw):
        kw.setdefault("timeout", TIMEOUT)
        return _requests.get(url, **kw)

    def post(self, url, **kw):
        kw.setdefault("timeout", TIMEOUT)
        return _requests.post(url, **kw)


requests = _Session()

GREEN  = "\033[92m"
RED    = "\033[91m"
YELLOW = "\033[93m"
RESET  = "\033[0m"

passed = 0
failed = 0


def ok(name: str):
    global passed
    passed += 1
    print(f"  {GREEN}✓ {name}{RESET}")


def fail(name: str, detail: str = ""):
    global failed
    failed += 1
    suffix = f": {detail}" if detail else ""
    print(f"  {RED}✗ {name}{suffix}{RESET}")


def check(name: str, condition: bool, detail: str = ""):
    if condition:
        ok(name)
    else:
        fail(name, detail)


def safe_json(r) -> dict:
    try:
        return r.json()
    except Exception:
        raise AssertionError(
            f"Не JSON: {r.request.method} {r.url} → {r.status_code}\n{r.text[:300]}"
        )


def rand_user() -> dict:
    suffix = "".join(random.choices(string.ascii_lowercase + string.digits, k=8))
    return {
        "username": f"testuser_{suffix}",
        "email":    f"test_{suffix}@example.com",
        "password": "StrongPass123!",
    }


def auth_header(token: str) -> dict:
    return {"Authorization": f"Bearer {token}"}


# ── Gateway system ─────────────────────────────────────────────────────────────

def test_health(base: str):
    print("\n── Gateway: Health ──")
    r = requests.get(f"{base}/system/health")
    check("GET /system/health → 200", r.status_code == 200)
    data = safe_json(r)
    check("  status=UP", data.get("status") == "UP")
    check("  service=auth-gateway", data.get("service") == "auth-gateway")


def test_ready(base: str):
    print("\n── Gateway: Ready ──")
    r = requests.get(f"{base}/system/ready")
    check("GET /system/ready → 200|503", r.status_code in (200, 503))
    data = safe_json(r)
    check("  status присутствует", "status" in data)
    check("  dependencies — список", isinstance(data.get("dependencies"), list))
    deps = {d["name"]: d["status"] for d in data.get("dependencies", [])}
    for svc in ("market-data-service", "trading-service", "portfolio-service"):
        check(f"  dependency {svc} присутствует", svc in deps)


# ── Auth ───────────────────────────────────────────────────────────────────────

def test_register_validation(base: str):
    print("\n── Auth: Register (validation) ──")

    r = requests.post(f"{base}/auth/register",
        json={"username": "ab", "email": "x@x.com", "password": "StrongPass123!"})
    check("username < 3 символов → 400", r.status_code == 400)

    r = requests.post(f"{base}/auth/register",
        json={"username": "valid_user", "email": "not-an-email", "password": "StrongPass123!"})
    check("невалидный email → 400", r.status_code == 400)

    r = requests.post(f"{base}/auth/register",
        json={"username": "valid_user", "email": "x@x.com", "password": "short"})
    check("пароль < 8 символов → 400", r.status_code == 400)

    r = requests.post(f"{base}/auth/register",
        json={"username": "invalid user!", "email": "x@x.com", "password": "StrongPass123!"})
    check("недопустимые символы в username → 400", r.status_code == 400)


def test_register(base: str, user: dict) -> dict:
    print(f"\n── Auth: Register ({user['username']}) ──")
    r = requests.post(f"{base}/auth/register", json=user)
    check("POST /auth/register → 201", r.status_code == 201)
    data = safe_json(r)
    check("  accessToken присутствует", bool(data.get("accessToken")))
    check("  refreshToken присутствует", bool(data.get("refreshToken")))
    check("  tokenType=Bearer", data.get("tokenType") == "Bearer")
    check("  user.username совпадает", data.get("user", {}).get("username") == user["username"])
    check("  user.id присутствует", bool(data.get("user", {}).get("id")))
    return data


def test_register_duplicate(base: str, user: dict):
    print("\n── Auth: Register (duplicate) ──")
    r = requests.post(f"{base}/auth/register", json=user)
    check("повторная регистрация → 409", r.status_code == 409)
    check("  code=USER_ALREADY_EXISTS", safe_json(r).get("code") == "USER_ALREADY_EXISTS")


def test_login(base: str, user: dict) -> dict:
    print(f"\n── Auth: Login ({user['username']}) ──")

    r = requests.post(f"{base}/auth/login",
        json={"login": user["username"], "password": "wrong-password-999"})
    check("неверный пароль → 401", r.status_code == 401)
    check("  code=INVALID_CREDENTIALS", safe_json(r).get("code") == "INVALID_CREDENTIALS")

    r = requests.post(f"{base}/auth/login",
        json={"login": "nonexistent_user_xyz", "password": "StrongPass123!"})
    check("несуществующий пользователь → 401", r.status_code == 401)

    r = requests.post(f"{base}/auth/login",
        json={"login": user["username"], "password": user["password"]})
    check("POST /auth/login → 200", r.status_code == 200)
    data = safe_json(r)
    check("  accessToken присутствует", bool(data.get("accessToken")))
    check("  refreshToken присутствует", bool(data.get("refreshToken")))
    check("  user.id присутствует", bool(data.get("user", {}).get("id")))
    return data


def test_refresh(base: str, refresh_token: str) -> str:
    print("\n── Auth: Refresh token ──")
    r = requests.post(f"{base}/auth/refresh", json={"refreshToken": refresh_token})
    check("POST /auth/refresh → 200", r.status_code == 200)
    data = safe_json(r)
    new_token = data.get("accessToken", "")
    check("  новый accessToken получен", bool(new_token))
    check("  новый refreshToken получен", bool(data.get("refreshToken")))
    return new_token


def test_logout(base: str, refresh_token: str):
    print("\n── Auth: Logout ──")
    r = requests.post(f"{base}/auth/logout", json={"refreshToken": refresh_token})
    check("POST /auth/logout → 204", r.status_code == 204)

    r = requests.post(f"{base}/auth/refresh", json={"refreshToken": refresh_token})
    check("refresh отозванного токена → 401", r.status_code == 401)
    check("  code=INVALID_REFRESH_TOKEN", safe_json(r).get("code") == "INVALID_REFRESH_TOKEN")


# ── Protected routes without token ────────────────────────────────────────────

def test_unauthorized(base: str):
    print("\n── Auth: Protected routes без токена ──")

    for method, path in [
        ("GET",  "/portfolio"),
        ("POST", "/orders"),
        ("GET",  "/orders"),
        ("GET",  "/trades"),
    ]:
        if method == "GET":
            r = requests.get(f"{base}{path}")
        else:
            r = requests.post(f"{base}{path}", json={})
        check(f"{method} {path} без токена → 401", r.status_code == 401,
              f"got {r.status_code}")

    r = requests.get(f"{base}/portfolio",
        headers={"Authorization": "Bearer invalid.token.here"})
    check("невалидный JWT → 401", r.status_code == 401)


# ── Market (публичный, без авторизации) ───────────────────────────────────────

def test_market_public(base: str):
    print("\n── Market: Public API (без авторизации) ──")
    r = requests.get(f"{base}/market/quotes/BTCUSDT")
    check("GET /market/quotes/BTCUSDT → 200", r.status_code == 200,
          f"got {r.status_code}")
    if r.status_code == 200:
        data = safe_json(r)
        check("  symbol=BTCUSDT", data.get("symbol") == "BTCUSDT")
        check("  bid присутствует", "bid" in data)
        check("  ask присутствует", "ask" in data)

    r = requests.get(f"{base}/market/instruments")
    check("GET /market/instruments → 200", r.status_code == 200,
          f"got {r.status_code}")

    r = requests.get(f"{base}/market/system/health")
    check("GET /market/system/health → 200 (проксирование health)", r.status_code == 200,
          f"got {r.status_code}")


# ── Portfolio через гейтвей ────────────────────────────────────────────────────

def test_portfolio_via_gateway(base: str, token: str, user_id: int):
    print("\n── Portfolio via Gateway ──")
    hdrs = auth_header(token)

    # Deposit через /portfolio/balance/deposit
    r = requests.post(f"{base}/portfolio/balance/deposit",
        headers=hdrs, json={"amount": "25000.00"})
    check("POST /portfolio/balance/deposit → 200", r.status_code == 200,
          f"got {r.status_code}, body={r.text[:200]}")
    if r.status_code == 200:
        data = safe_json(r)
        check("  balance=25000.00", data.get("balance") == "25000.00")
        check("  userId из JWT совпадает", data.get("userId") == user_id)

    # Withdraw
    r = requests.post(f"{base}/portfolio/balance/withdraw",
        headers=hdrs, json={"amount": "5000.00"})
    check("POST /portfolio/balance/withdraw → 200", r.status_code == 200,
          f"got {r.status_code}")
    if r.status_code == 200:
        check("  balance=20000.00", safe_json(r).get("balance") == "20000.00")

    # GET /portfolio
    r = requests.get(f"{base}/portfolio", headers=hdrs)
    check("GET /portfolio → 200", r.status_code == 200)
    if r.status_code == 200:
        data = safe_json(r)
        check("  cashBalance присутствует", "cashBalance" in data)
        check("  positions — список", isinstance(data.get("positions"), list))
        check("  userId из JWT совпадает", data.get("userId") == user_id)

    # GET /portfolio/positions
    r = requests.get(f"{base}/portfolio/positions", headers=hdrs)
    check("GET /portfolio/positions → 200", r.status_code == 200)

    # GET /portfolio/pnl
    r = requests.get(f"{base}/portfolio/pnl", headers=hdrs)
    check("GET /portfolio/pnl → 200", r.status_code == 200)
    if r.status_code == 200:
        data = safe_json(r)
        check("  realizedPnl присутствует", "realizedPnl" in data)
        check("  unrealizedPnl присутствует", "unrealizedPnl" in data)

    # GET /portfolio/orders
    r = requests.get(f"{base}/portfolio/orders", headers=hdrs)
    check("GET /portfolio/orders → 200", r.status_code == 200)

    # GET /portfolio/trades
    r = requests.get(f"{base}/portfolio/trades", headers=hdrs)
    check("GET /portfolio/trades → 200", r.status_code == 200)

    # Withdraw нехватка средств
    r = requests.post(f"{base}/portfolio/balance/withdraw",
        headers=hdrs, json={"amount": "999999.00"})
    check("POST /portfolio/balance/withdraw (недостаточно) → 422", r.status_code == 422)
    check("  code=INSUFFICIENT_BALANCE",
          safe_json(r).get("code") == "INSUFFICIENT_BALANCE")


# ── Trading через гейтвей ──────────────────────────────────────────────────────

def test_trading_via_gateway(base: str, token: str) -> dict:
    print("\n── Trading via Gateway ──")
    hdrs = auth_header(token)

    # Validation
    r = requests.post(f"{base}/orders", headers=hdrs,
        json={"symbol": "AAPL", "side": "INVALID", "type": "MARKET", "quantity": 1})
    check("POST /orders невалидный side → не 201", r.status_code != 201,
          f"got {r.status_code}")

    r = requests.post(f"{base}/orders", headers=hdrs,
        json={"symbol": "AAPL", "side": "BUY", "type": "MARKET", "quantity": 0})
    check("POST /orders quantity=0 → не 201", r.status_code != 201,
          f"got {r.status_code}")

    # BUY order
    r = requests.post(f"{base}/orders", headers=hdrs,
        json={"symbol": "AAPL", "side": "BUY", "type": "MARKET", "quantity": 5})
    check("POST /orders BUY → 201", r.status_code == 201,
          f"got {r.status_code}, body={r.text[:200]}")
    buy_order = safe_json(r) if r.status_code == 201 else {}
    if buy_order:
        check("  status=FILLED", buy_order.get("status") == "FILLED")
        check("  symbol=AAPL", buy_order.get("symbol") == "AAPL")
        check("  filledQuantity=5", buy_order.get("filledQuantity") == 5)
        check("  id присутствует", bool(buy_order.get("id")))

    # GET /orders/{id}
    if buy_order.get("id"):
        r = requests.get(f"{base}/orders/{buy_order['id']}", headers=hdrs)
        check("GET /orders/{id} → 200", r.status_code == 200)
        check("  id совпадает", safe_json(r).get("id") == buy_order["id"])

        r = requests.get(f"{base}/orders/00000000-0000-0000-0000-000000000000", headers=hdrs)
        check("GET /orders/несуществующий → 404", r.status_code == 404)

    # Cancel FILLED order → 409
    if buy_order.get("id"):
        r = requests.post(f"{base}/orders/{buy_order['id']}/cancel", headers=hdrs)
        check("POST /orders/{id}/cancel на FILLED → 409", r.status_code == 409)

    # SELL order
    r = requests.post(f"{base}/orders", headers=hdrs,
        json={"symbol": "AAPL", "side": "SELL", "type": "MARKET", "quantity": 3})
    check("POST /orders SELL → 201", r.status_code == 201,
          f"got {r.status_code}")

    # GET /orders list
    r = requests.get(f"{base}/orders", headers=hdrs)
    check("GET /orders → 200", r.status_code == 200)
    if r.status_code == 200:
        data = safe_json(r)
        check("  total >= 2", data.get("total", 0) >= 2)

    # GET /trades list
    r = requests.get(f"{base}/trades", headers=hdrs)
    check("GET /trades → 200", r.status_code == 200)
    if r.status_code == 200:
        data = safe_json(r)
        check("  items — список", isinstance(data.get("items"), list))
        check("  total >= 1", data.get("total", 0) >= 1)

        if data["items"]:
            trade_id = data["items"][0]["id"]
            r = requests.get(f"{base}/trades/{trade_id}", headers=hdrs)
            check("GET /trades/{id} → 200", r.status_code == 200)

    return buy_order


# ── E2E: полный цикл через гейтвей ────────────────────────────────────────────

def test_e2e(base: str, kafka_wait: int):
    print(f"\n── E2E: register → login → deposit → BUY → Kafka → portfolio ──")

    user = rand_user()
    r = requests.post(f"{base}/auth/register", json=user)
    check("register → 201", r.status_code == 201)
    token = safe_json(r).get("accessToken", "")
    user_id = safe_json(r).get("user", {}).get("id")
    if not token:
        fail("не удалось получить токен, E2E пропущен")
        return
    hdrs = auth_header(token)

    # Deposit
    r = requests.post(f"{base}/portfolio/balance/deposit",
        headers=hdrs, json={"amount": "200000.00"})
    check("deposit 200000 → 200", r.status_code == 200)

    # Позиция до сделки
    r = requests.get(f"{base}/portfolio/positions", headers=hdrs)
    qty_before = 0
    if r.status_code == 200:
        items = safe_json(r).get("items", [])
        pos = next((p for p in items if p["symbol"] == "AAPL"), None)
        qty_before = pos["quantity"] if pos else 0

    # BUY через trading
    r = requests.post(f"{base}/orders", headers=hdrs,
        json={"symbol": "AAPL", "side": "BUY", "type": "MARKET", "quantity": 10})
    check("BUY AAPL 10 → FILLED",
          r.status_code == 201 and safe_json(r).get("status") == "FILLED",
          f"got {r.status_code}")

    print(f"  {YELLOW}⏳ ждём {kafka_wait}с (Kafka)...{RESET}")
    time.sleep(kafka_wait)

    # Позиция после
    r = requests.get(f"{base}/portfolio/positions/AAPL", headers=hdrs)
    check("GET /portfolio/positions/AAPL → 200 (позиция появилась)", r.status_code == 200,
          f"got {r.status_code}")
    if r.status_code == 200:
        qty_after = safe_json(r).get("quantity", 0)
        check(
            f"  quantity: {qty_before} → {qty_after} (ожидаем +10)",
            qty_after == qty_before + 10,
            f"quantity={qty_after}, ожидали {qty_before + 10}",
        )

    # Ордер виден в истории portfolio
    r = requests.get(f"{base}/portfolio/orders", headers=hdrs,
        params={"symbol": "AAPL"})
    check("ордер AAPL виден в portfolio/orders",
          r.status_code == 200 and safe_json(r).get("total", 0) >= 1)


# ── Main ───────────────────────────────────────────────────────────────────────

def main():
    parser = argparse.ArgumentParser(description="Integration tests via auth-gateway")
    parser.add_argument("--base-url", default="http://localhost:8080/api/v1")
    parser.add_argument("--kafka-wait", type=int, default=3)
    args = parser.parse_args()
    base = args.base_url.rstrip("/")

    print(f"Gateway: {base}\n")

    try:
        _requests.get(f"{base}/system/health", timeout=5)
    except _requests.ConnectionError:
        print(f"{RED}ERROR: gateway недоступен на {base}{RESET}")
        sys.exit(1)

    user = rand_user()

    test_health(base)
    test_ready(base)
    test_register_validation(base)
    auth_data = test_register(base, user)
    test_register_duplicate(base, user)
    login_data = test_login(base, user)
    token = login_data.get("accessToken", "")
    refresh_token = login_data.get("refreshToken", "")
    user_id = login_data.get("user", {}).get("id")

    test_unauthorized(base)
    test_market_public(base)

    if token:
        test_portfolio_via_gateway(base, token, user_id)
        test_trading_via_gateway(base, token)

    if refresh_token:
        new_token = test_refresh(base, refresh_token)
        new_refresh = login_data.get("refreshToken", "")
        test_logout(base, new_refresh if new_token else refresh_token)

    test_e2e(base, kafka_wait=args.kafka_wait)

    print(f"\n{'='*45}")
    print(f"  {GREEN}Passed: {passed}{RESET}")
    if failed:
        print(f"  {RED}Failed: {failed}{RESET}")
    else:
        print(f"  Failed: 0")
    print(f"  Total:  {passed + failed}")
    print(f"{'='*45}")
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
