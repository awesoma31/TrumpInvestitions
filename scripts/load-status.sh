#!/usr/bin/env bash
set -euo pipefail

curl -s http://localhost:8095/api/v1/load/status | python3 -c "
import json, sys

d = json.load(sys.stdin)

running  = 'YES ▶' if d['running'] else 'NO  ■'
total    = d['totalRequests']
ok       = d['successfulRequests']
fail     = d['failedRequests']
ok_pct   = f'{ok/total*100:.1f}%' if total else '-'
fail_pct = f'{fail/total*100:.1f}%' if total else '-'

print('=== Load Test Status ===')
print(f\"Running:       {running}\")
print(f\"Users:         {d['activeUsers']} / {d['configuredUsers']} active\")
print(f\"Started:       {d.get('startedAt') or '-'}\")
if d.get('finishedAt'):
    print(f\"Finished:      {d['finishedAt']}\")
print()
print('=== Requests ===')
print(f'Total:         {total}')
print(f'Success:       {ok}  ({ok_pct})')
print(f'Failed:        {fail}  ({fail_pct})')
print(f\"Registered:    {d['registeredUsers']}\")
print(f\"Orders sent:   {d['ordersSubmitted']}\")

s = d.get('summary')
if s:
    lat = s['latency']
    print()
    print('=== Performance ===')
    print(f\"RPS:           {s['requestsPerSecond']}\")
    print(f\"Latency min:   {lat['minMs']} ms\")
    print(f\"Latency avg:   {lat['avgMs']} ms\")
    print(f\"Latency p50:   {lat['p50Ms']} ms\")
    print(f\"Latency p95:   {lat['p95Ms']} ms\")
    print(f\"Latency p99:   {lat['p99Ms']} ms\")
    print(f\"Latency max:   {lat['maxMs']} ms\")
    print()
    print('=== Top Endpoints ===')
    for e in s['endpoints'][:8]:
        print(f\"  {e['name']}: {e['total']} req, {e['failed']} err\")
"
