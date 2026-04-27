"""
Integration tests for portfolio-service + trading-service.

Usage:
    pip install requests
    python test_integration.py

Services must be running:
    docker compose up --build portfolio-service trading-service postgres kafka
"""

import argparse
import random
import sys
import time
import requests

GREEN = "\033[92m"
RED = "\033[91m"
YELLOW = "\033[93m"
RESET = "\033[0m"

passed = 0
failed = 0


def ok(name: str, detail: str = ""):
    global passed
    passed += 1
    print(f"  {GREEN}✓ {name}{RESET}")


def fail(name: str, detail: str = ""):
    global failed
    failed += 1
    print(f"  {RED}✗ {name}: {detail}{RESET}")


def check(name: str, condition: bool, detail: str = ""):
    if condition:
        ok(name)
    else:
        fail(name, detail)


def wait_for(url: str, timeout: int = 30):
    """Wait until service responds on health endpoint."""
    deadline = time.time() + timeout
    while time.time() < deadline:
        try:
            r = requests.get(url, timeout=3)
            if r.status_code == 200:
                return True
        except requests.ConnectionError:
            pass
        time.sleep(1)
    return False


# ─── Portfolio Service ──────────────────────────────────────────────────────

def test_portfolio_health(base: str):
    print(f"\n── Portfolio: Health ──")
    r = requests.get(f"{base}/system/health")
    check("GET /system/health → 200", r.status_code == 200)
    data = r.json()
    check("  status=UP", data.get("status") == "UP")
    check("  service=portfolio-service", data.get("service") == "portfolio-service")

    r = requests.get(f"{base}/system/ready")
    check("GET /system/ready → 200", r.status_code == 200)
    check("  status=READY", r.json().get("status") == "READY")


def test_portfolio_validation(base: str):
    print(f"\n── Portfolio: Validation ──")
    r = requests.get(f"{base}/portfolio")
    check("GET /portfolio без X-User-Id → 400", r.status_code == 400)

    r = requests.get(f"{base}/portfolio", headers={"X-User-Id": "not-a-number"})
    check("GET /portfolio невалидный X-User-Id → 400", r.status_code == 400)


def test_portfolio_deposit(base: str, user_id: int) -> str:
    print(f"\n── Portfolio: Deposit (user={user_id}) ──")
    r = requests.post(
        f"{base}/balance/deposit",
        headers={"X-User-Id": str(user_id)},
        json={"amount": "50000.00"},
    )
    check("POST /balance/deposit → 200", r.status_code == 200)
    data = r.json()
    check("  balance=50000.00", data.get("balance") == "50000.00")
    check("  currency=USD", data.get("currency") == "USD")
    check("  userId совпадает", data.get("userId") == user_id)

    # Второй депозит — баланс накапливается
    r = requests.post(
        f"{base}/balance/deposit",
        headers={"X-User-Id": str(user_id)},
        json={"amount": "10000.00"},
    )
    check("POST /balance/deposit (добавление) → 200", r.status_code == 200)
    check("  balance=60000.00", r.json().get("balance") == "60000.00")

    # Невалидные суммы
    r = requests.post(
        f"{base}/balance/deposit",
        headers={"X-User-Id": str(user_id)},
        json={"amount": "-100"},
    )
    check("POST /balance/deposit отрицательная сумма → 400", r.status_code == 400)

    return "60000.00"


def test_portfolio_withdraw(base: str, user_id: int):
    print(f"\n── Portfolio: Withdraw (user={user_id}) ──")
    # Пополняем свежего пользователя
    requests.post(
        f"{base}/balance/deposit",
        headers={"X-User-Id": str(user_id)},
        json={"amount": "5000.00"},
    )
    r = requests.post(
        f"{base}/balance/withdraw",
        headers={"X-User-Id": str(user_id)},
        json={"amount": "2000.00"},
    )
    check("POST /balance/withdraw → 200", r.status_code == 200)
    check("  balance=3000.00", r.json().get("balance") == "3000.00")

    r = requests.post(
        f"{base}/balance/withdraw",
        headers={"X-User-Id": str(user_id)},
        json={"amount": "99999.00"},
    )
    check("POST /balance/withdraw нехватка средств → 422", r.status_code == 422)
    check("  code=INSUFFICIENT_BALANCE", r.json().get("code") == "INSUFFICIENT_BALANCE")


def test_portfolio_read(base: str, user_id: int):
    print(f"\n── Portfolio: Read (user={user_id}) ──")
    r = requests.get(f"{base}/portfolio", headers={"X-User-Id": str(user_id)})
    check("GET /portfolio → 200", r.status_code == 200)
    data = r.json()
    check("  userId присутствует", data.get("userId") == user_id)
    check("  cashBalance — строка", isinstance(data.get("cashBalance"), str))
    check("  positions — список", isinstance(data.get("positions"), list))
    check("  totalPnl присутствует", "totalPnl" in data)

    r = requests.get(f"{base}/positions", headers={"X-User-Id": str(user_id)})
    check("GET /positions → 200", r.status_code == 200)
    check("  items — список", isinstance(r.json().get("items"), list))

    r = requests.get(f"{base}/positions/NONEXISTENT", headers={"X-User-Id": str(user_id)})
    check("GET /positions/NONEXISTENT → 404", r.status_code == 404)

    r = requests.get(f"{base}/pnl", headers={"X-User-Id": str(user_id)})
    check("GET /pnl → 200", r.status_code == 200)
    data = r.json()
    check("  realizedPnl присутствует", "realizedPnl" in data)
    check("  unrealizedPnl присутствует", "unrealizedPnl" in data)
    check("  currency=USD", data.get("currency") == "USD")

    r = requests.get(f"{base}/orders", headers={"X-User-Id": str(user_id)})
    check("GET /orders → 200", r.status_code == 200)
    check("  items — список", isinstance(r.json().get("items"), list))

    r = requests.get(f"{base}/trades", headers={"X-User-Id": str(user_id)})
    check("GET /trades → 200", r.status_code == 200)
    check("  items — список", isinstance(r.json().get("items"), list))


# ─── Trading Service ────────────────────────────────────────────────────────

def test_trading_health(base: str):
    print(f"\n── Trading: Health ──")
    r = requests.get(f"{base}/system/health")
    check("GET /system/health → 200", r.status_code == 200)
    data = r.json()
    check("  status=UP", data.get("status") == "UP")
    check("  service=trading-service", data.get("service") == "trading-service")

    r = requests.get(f"{base}/system/ready")
    check("GET /system/ready → 200", r.status_code == 200)


def test_trading_validation(base: str, user_id: int):
    print(f"\n── Trading: Validation ──")
    r = requests.post(f"{base}/orders", json={"symbol": "AAPL", "side": "BUY", "type": "MARKET", "quantity": 1})
    check("POST /orders без X-User-Id → 400|401", r.status_code in (400, 401))

    r = requests.post(
        f"{base}/orders",
        headers={"X-User-Id": str(user_id)},
        json={"symbol": "AAPL", "side": "INVALID", "type": "MARKET", "quantity": 1},
    )
    # Невалидный side — может вернуть 422 или 400 в зависимости от валидации
    check("POST /orders невалидный side → не 201", r.status_code != 201)


def test_trading_buy_order(base: str, user_id: int) -> dict:
    print(f"\n── Trading: BUY order (user={user_id}) ──")
    r = requests.post(
        f"{base}/orders",
        headers={"X-User-Id": str(user_id)},
        json={"symbol": "AAPL", "side": "BUY", "type": "MARKET", "quantity": 10},
    )
    check("POST /orders BUY → 201", r.status_code == 201)
    data = r.json()
    check("  status=FILLED", data.get("status") == "FILLED")
    check("  symbol=AAPL", data.get("symbol") == "AAPL")
    check("  side=BUY", data.get("side") == "BUY")
    check("  quantity=10", data.get("quantity") == 10)
    check("  id присутствует", bool(data.get("id")))
    check("  filledQuantity=10", data.get("filledQuantity") == 10)
    return data


def test_trading_sell_order(base: str, user_id: int) -> dict:
    print(f"\n── Trading: SELL order (user={user_id}) ──")
    r = requests.post(
        f"{base}/orders",
        headers={"X-User-Id": str(user_id)},
        json={"symbol": "AAPL", "side": "SELL", "type": "MARKET", "quantity": 5},
    )
    check("POST /orders SELL → 201", r.status_code == 201)
    data = r.json()
    check("  status=FILLED", data.get("status") == "FILLED")
    check("  side=SELL", data.get("side") == "SELL")
    return data


def test_trading_get_order(base: str, user_id: int, order_id: str):
    print(f"\n── Trading: Get order ──")
    r = requests.get(f"{base}/orders/{order_id}", headers={"X-User-Id": str(user_id)})
    check("GET /orders/{id} → 200", r.status_code == 200)
    check("  id совпадает", r.json().get("id") == order_id)

    r = requests.get(f"{base}/orders/00000000-0000-0000-0000-000000000000", headers={"X-User-Id": str(user_id)})
    check("GET /orders/несуществующий → 404", r.status_code == 404)


def test_trading_cancel_filled(base: str, user_id: int, order_id: str):
    print(f"\n── Trading: Cancel filled order ──")
    r = requests.post(f"{base}/orders/{order_id}/cancel", headers={"X-User-Id": str(user_id)})
    check("POST /orders/{id}/cancel на FILLED → 409", r.status_code == 409)
    check("  code=CONFLICT", r.json().get("code") == "CONFLICT")


def test_trading_list(base: str, user_id: int):
    print(f"\n── Trading: Lists ──")
    r = requests.get(f"{base}/orders", headers={"X-User-Id": str(user_id)})
    check("GET /orders → 200", r.status_code == 200)
    data = r.json()
    check("  items — список", isinstance(data.get("items"), list))
    check("  total >= 1", data.get("total", 0) >= 1)
    check("  limit присутствует", "limit" in data)

    r = requests.get(
        f"{base}/orders",
        headers={"X-User-Id": str(user_id)},
        params={"status": "FILLED", "symbol": "AAPL", "limit": 10},
    )
    check("GET /orders?status=FILLED&symbol=AAPL → 200", r.status_code == 200)

    r = requests.get(f"{base}/trades", headers={"X-User-Id": str(user_id)})
    check("GET /trades → 200", r.status_code == 200)
    data = r.json()
    check("  items — список", isinstance(data.get("items"), list))
    check("  total >= 1", data.get("total", 0) >= 1)

    if data["items"]:
        trade = data["items"][0]
        trade_id = trade["id"]
        r = requests.get(f"{base}/trades/{trade_id}", headers={"X-User-Id": str(user_id)})
        check("GET /trades/{id} → 200", r.status_code == 200)
        check("  id совпадает", r.json().get("id") == trade_id)


# ─── End-to-end: Trading → Kafka → Portfolio ───────────────────────────────

def test_e2e(portfolio_base: str, trading_base: str, user_id: int, kafka_wait: int):
    print(f"\n── E2E: order → kafka → portfolio (user={user_id}, ожидание {kafka_wait}с) ──")

    # Пополняем баланс
    requests.post(
        f"{portfolio_base}/balance/deposit",
        headers={"X-User-Id": str(user_id)},
        json={"amount": "100000.00"},
    )

    # Позиций до сделки
    r = requests.get(f"{portfolio_base}/positions", headers={"X-User-Id": str(user_id)})
    positions_before = {p["symbol"]: p for p in r.json().get("items", [])}
    qty_before = positions_before.get("TSLA", {}).get("quantity", 0)

    # Покупаем через trading-service
    r = requests.post(
        f"{trading_base}/orders",
        headers={"X-User-Id": str(user_id)},
        json={"symbol": "TSLA", "side": "BUY", "type": "MARKET", "quantity": 7},
    )
    check("BUY TSLA 7 шт. → FILLED", r.status_code == 201 and r.json().get("status") == "FILLED")

    # Ждём Kafka
    print(f"  {YELLOW}⏳ ждём {kafka_wait}с пока Kafka доставит событие...{RESET}")
    time.sleep(kafka_wait)

    # Проверяем позицию в portfolio-service
    r = requests.get(f"{portfolio_base}/positions/TSLA", headers={"X-User-Id": str(user_id)})
    if r.status_code == 200:
        qty_after = r.json().get("quantity", 0)
        check(
            f"  позиция TSLA в портфеле: {qty_before} → {qty_after} (ожидаем +7)",
            qty_after == qty_before + 7,
            f"quantity={qty_after}, ожидали {qty_before + 7}",
        )
    else:
        fail("GET /positions/TSLA → 200", f"got {r.status_code}")

    # Проверяем историю ордеров в portfolio-service
    r = requests.get(
        f"{portfolio_base}/orders",
        headers={"X-User-Id": str(user_id)},
        params={"symbol": "TSLA"},
    )
    check("  ордер TSLA виден в portfolio-service", r.status_code == 200 and r.json().get("total", 0) >= 1)


# ─── Main ───────────────────────────────────────────────────────────────────

def main():
    parser = argparse.ArgumentParser(description="Integration tests: portfolio-service + trading-service")
    parser.add_argument("--portfolio-url", default="http://localhost:8081/api/v1")
    parser.add_argument("--trading-url", default="http://localhost:8082/api/v1")
    parser.add_argument("--kafka-wait", type=int, default=3, help="Секунд ждать Kafka (default: 3)")
    args = parser.parse_args()

    pbase = args.portfolio_url.rstrip("/")
    tbase = args.trading_url.rstrip("/")

    print(f"Portfolio Service : {pbase}")
    print(f"Trading Service   : {tbase}")

    # Проверяем доступность
    print(f"\nПроверяем доступность сервисов...")
    if not wait_for(f"{pbase}/system/health"):
        print(f"{RED}ERROR: portfolio-service недоступен на {pbase}{RESET}")
        sys.exit(1)
    if not wait_for(f"{tbase}/system/health"):
        print(f"{RED}ERROR: trading-service недоступен на {tbase}{RESET}")
        sys.exit(1)
    print(f"  {GREEN}оба сервиса доступны{RESET}")

    # Уникальные user_id для каждого прогона чтобы не зависеть от старых данных в БД
    base_id = random.randint(100_000, 999_999)
    u_portfolio  = base_id
    u_withdraw   = base_id + 1
    u_trading    = base_id + 2
    u_e2e        = base_id + 3

    # Portfolio tests
    test_portfolio_health(pbase)
    test_portfolio_validation(pbase)
    test_portfolio_deposit(pbase, user_id=u_portfolio)
    test_portfolio_withdraw(pbase, user_id=u_withdraw)
    test_portfolio_read(pbase, user_id=u_portfolio)

    # Trading tests
    test_trading_health(tbase)
    test_trading_validation(tbase, user_id=u_trading)
    buy_order = test_trading_buy_order(tbase, user_id=u_trading)
    test_trading_sell_order(tbase, user_id=u_trading)
    if buy_order:
        test_trading_get_order(tbase, user_id=u_trading, order_id=buy_order["id"])
        test_trading_cancel_filled(tbase, user_id=u_trading, order_id=buy_order["id"])
    test_trading_list(tbase, user_id=u_trading)

    # End-to-end
    test_e2e(pbase, tbase, user_id=u_e2e, kafka_wait=args.kafka_wait)

    # Итог
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
