# Generated Large Scenarios

These scenarios were generated for load and smoke testing the C pricing engine.
Each file contains exactly 1000 steps for a single instrument because the engine format supports one `symbol` per scenario file.

## Files

- `btcusdt_1000.yaml` - crypto trend session
- `ethusdt_1000.yaml` - crypto reversal session
- `aapl_1000.yaml` - equity intraday wave
- `msft_1000.yaml` - equity opening drive
- `tsla_1000.yaml` - volatile equity session

## Examples

```bash
./pricing_engine/pricing_engine --scenario pricing_engine/examples/generated/btcusdt_1000.yaml
./pricing_engine/pricing_engine --scenario pricing_engine/examples/generated/ethusdt_1000.yaml | ./pricing_engine/push_input_to_db.sh
./scripts/push-all-scenarios-to-clickhouse.sh
```
