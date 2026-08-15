# Sample: e-commerce order events

`order-v1.avsc` is the baseline, `order-v2.avsc` the proposed change. Comparing them exercises
every change type ContractGuard reports today except field removal:

| Path | Change | v1 → v2 |
|---|---|---|
| `OrderEvent.customerEmail` | `FIELD_OPTIONALITY_CHANGED` | `REQUIRED` → `OPTIONAL` |
| `OrderEvent.customerEmail` | `DEFAULT_VALUE_CHANGED` | none → `null` |
| `OrderEvent.status` | `ENUM_SYMBOL_ADDED` | → `RETURNED` |
| `OrderEvent.currency` | `DEFAULT_VALUE_CHANGED` | `USD` → `UNSPECIFIED` |
| `OrderEvent.channel` | `FIELD_ADDED` | → `string` |
| `OrderEvent.items[].discountCents` | `FIELD_ADDED` | → `union<null,int>` |

The `RETURNED` symbol is the motivating case: it is backward compatible by Avro's rules because
the enum declares a default, yet a consumer whose `switch` lacks that branch can still misbehave.
Detecting that is a later phase; Phase 1 only reports the structural change.
