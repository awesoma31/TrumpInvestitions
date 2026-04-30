"""
Integration tests for Portfolio Service.

Usage:
    pip install requests
    python test_endpoints.py [--base-url http://localhost:8080]

The service must be running (e.g. via docker-compose up).
"""

import argparse
import sys
import requests

GREEN = "\033[92m"
RED = "\033[91m"
RESET = "\033[0m"

passed = 0
failed = 0


def report(name: str, ok: bool, detail: str = ""):
    global passed, failed
    if ok:
        passed += 1
        print(f"  {GREEN}✓ {name}{RESET}")
    else:
        failed += 1
        print(f"  {RED}✗ {name}: {detail}{RESET}")


def test_health(base: str):
    print("\n── System ──")
    r = requests.get(f"{base}/system/health")
    report("GET /system/health → 200", r.status_code == 200)
    data = r.json()
    report("  status=UP", data.get("status") == "UP")
    report("  service=portfolio-service", data.get("service") == "portfolio-service")


def test_ready(base: str):
    r = requests.get(f"{base}/system/ready")
    ok = r.status_code in (200, 503)
    report("GET /system/ready → 200|503", ok)
    if r.status_code == 200:
        data = r.json()
        report("  status=READY", data.get("status") == "READY")
        report("  has dependencies list", isinstance(data.get("dependencies"), list))


def test_portfolio_no_user(base: str):
    print("\n── Portfolio (validation) ──")
    r = requests.get(f"{base}/portfolio")
    report("GET /portfolio without X-User-Id → 400", r.status_code == 400)

    r = requests.get(f"{base}/portfolio", headers={"X-User-Id": "abc"})
    report("GET /portfolio invalid X-User-Id → 400", r.status_code == 400)


def test_deposit(base: str, user_id: int = 1) -> None:
    print("\n── Balance: Deposit ──")
    r = requests.post(
        f"{base}/balance/deposit",
        headers={"X-User-Id": str(user_id)},
        json={"amount": "10000.00"},
    )
    report("POST /balance/deposit → 200", r.status_code == 200)
    data = r.json()
    report("  balance=10000.00", data.get("balance") == "10000.00")
    report("  currency=USD", data.get("currency") == "USD")
    report("  userId present", data.get("userId") == user_id)

    # deposit more
    r = requests.post(
        f"{base}/balance/deposit",
        headers={"X-User-Id": str(user_id)},
        json={"amount": "5000.00"},
    )
    report("POST /balance/deposit (add) → 200", r.status_code == 200)
    report("  balance=15000.00", r.json().get("balance") == "15000.00")


def test_deposit_invalid(base: str, user_id: int = 1):
    print("\n── Balance: Deposit (invalid) ──")
    r = requests.post(
        f"{base}/balance/deposit",
        headers={"X-User-Id": str(user_id)},
        json={"amount": "-500"},
    )
    report("POST /balance/deposit negative → 400", r.status_code == 400)

    r = requests.post(
        f"{base}/balance/deposit",
        headers={"X-User-Id": str(user_id)},
        json={"amount": "not-a-number"},
    )
    report("POST /balance/deposit NaN → 400", r.status_code == 400)


def test_withdraw(base: str, user_id: int = 2):
    print("\n── Balance: Withdraw ──")
    # seed balance
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
    report("POST /balance/withdraw → 200", r.status_code == 200)
    report("  balance=3000.00", r.json().get("balance") == "3000.00")


def test_withdraw_insufficient(base: str, user_id: int = 3):
    print("\n── Balance: Withdraw (insufficient) ──")
    requests.post(
        f"{base}/balance/deposit",
        headers={"X-User-Id": str(user_id)},
        json={"amount": "100.00"},
    )
    r = requests.post(
        f"{base}/balance/withdraw",
        headers={"X-User-Id": str(user_id)},
        json={"amount": "9999.00"},
    )
    report("POST /balance/withdraw insufficient → 422", r.status_code == 422)
    data = r.json()
    report("  code=INSUFFICIENT_BALANCE", data.get("code") == "INSUFFICIENT_BALANCE")


def test_portfolio(base: str, user_id: int = 1):
    print("\n── Portfolio ──")
    r = requests.get(f"{base}/portfolio", headers={"X-User-Id": str(user_id)})
    report("GET /portfolio → 200", r.status_code == 200)
    data = r.json()
    report("  userId present", data.get("userId") == user_id)
    report("  cashBalance is string", isinstance(data.get("cashBalance"), str))
    report("  positions is list", isinstance(data.get("positions"), list))
    report("  totalPnl present", "totalPnl" in data)


def test_positions(base: str, user_id: int = 1):
    print("\n── Positions ──")
    r = requests.get(f"{base}/positions", headers={"X-User-Id": str(user_id)})
    report("GET /positions → 200", r.status_code == 200)
    data = r.json()
    report("  items is list", isinstance(data.get("items"), list))

    # with symbol filter
    r = requests.get(
        f"{base}/positions", headers={"X-User-Id": str(user_id)}, params={"symbol": "AAPL"}
    )
    report("GET /positions?symbol=AAPL → 200", r.status_code == 200)


def test_position_by_symbol(base: str, user_id: int = 1):
    print("\n── Position by symbol ──")
    r = requests.get(
        f"{base}/positions/NONEXISTENT", headers={"X-User-Id": str(user_id)}
    )
    report("GET /positions/NONEXISTENT → 404", r.status_code == 404)


def test_pnl(base: str, user_id: int = 1):
    print("\n── PnL ──")
    r = requests.get(f"{base}/pnl", headers={"X-User-Id": str(user_id)})
    report("GET /pnl → 200", r.status_code == 200)
    data = r.json()
    report("  currency=USD", data.get("currency") == "USD")
    report("  realizedPnl present", "realizedPnl" in data)
    report("  unrealizedPnl present", "unrealizedPnl" in data)
    report("  totalPnl present", "totalPnl" in data)


def test_orders(base: str, user_id: int = 1):
    print("\n── Orders History ──")
    r = requests.get(f"{base}/orders", headers={"X-User-Id": str(user_id)})
    report("GET /orders → 200", r.status_code == 200)
    data = r.json()
    report("  items is list", isinstance(data.get("items"), list))
    report("  total is int", isinstance(data.get("total"), int))
    report("  limit is int", isinstance(data.get("limit"), int))
    report("  offset is int", isinstance(data.get("offset"), int))

    # with filters
    r = requests.get(
        f"{base}/orders",
        headers={"X-User-Id": str(user_id)},
        params={"status": "FILLED", "symbol": "AAPL", "limit": 10, "offset": 0},
    )
    report("GET /orders with filters → 200", r.status_code == 200)


def test_trades(base: str, user_id: int = 1):
    print("\n── Trades History ──")
    r = requests.get(f"{base}/trades", headers={"X-User-Id": str(user_id)})
    report("GET /trades → 200", r.status_code == 200)
    data = r.json()
    report("  items is list", isinstance(data.get("items"), list))
    report("  total is int", isinstance(data.get("total"), int))

    # with filters
    r = requests.get(
        f"{base}/trades",
        headers={"X-User-Id": str(user_id)},
        params={"symbol": "AAPL", "side": "BUY", "limit": 5, "offset": 0},
    )
    report("GET /trades with filters → 200", r.status_code == 200)


def main():
    parser = argparse.ArgumentParser(description="Portfolio Service integration tests")
    parser.add_argument(
        "--base-url",
        default="http://localhost:8080/api/v1",
        help="Base URL of the portfolio service (default: http://localhost:8080/api/v1)",
    )
    args = parser.parse_args()
    base = args.base_url.rstrip("/")

    print(f"Testing Portfolio Service at {base}\n")

    try:
        requests.get(f"{base}/system/health", timeout=5)
    except requests.ConnectionError:
        print(f"{RED}ERROR: Cannot connect to {base}. Is the service running?{RESET}")
        sys.exit(1)

    test_health(base)
    test_ready(base)
    test_portfolio_no_user(base)
    test_deposit(base, user_id=100)
    test_deposit_invalid(base, user_id=100)
    test_withdraw(base, user_id=200)
    test_withdraw_insufficient(base, user_id=300)
    test_portfolio(base, user_id=100)
    test_positions(base, user_id=100)
    test_position_by_symbol(base, user_id=100)
    test_pnl(base, user_id=100)
    test_orders(base, user_id=100)
    test_trades(base, user_id=100)

    print(f"\n{'='*40}")
    print(f"  {GREEN}Passed: {passed}{RESET}")
    if failed:
        print(f"  {RED}Failed: {failed}{RESET}")
    else:
        print(f"  Failed: 0")
    print(f"  Total:  {passed + failed}")
    print(f"{'='*40}")
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
