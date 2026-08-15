# Sample: e-commerce order events

`order-v1.avsc` is the baseline, `order-v2.avsc` the proposed change. Comparing them produces six
changes across four of the nine change types:

| Path | Change | v1 → v2 |
|---|---|---|
| `OrderEvent.customerEmail` | `FIELD_OPTIONALITY_CHANGED` | `REQUIRED` → `OPTIONAL` |
| `OrderEvent.customerEmail` | `DEFAULT_VALUE_CHANGED` | none → `null` |
| `OrderEvent.status` | `ENUM_SYMBOL_ADDED` | → `RETURNED` |
| `OrderEvent.currency` | `DEFAULT_VALUE_CHANGED` | `USD` → `UNSPECIFIED` |
| `OrderEvent.channel` | `FIELD_ADDED` | → `string` |
| `OrderEvent.items[].discountCents` | `FIELD_ADDED` | → `union<null,int>` |

The `RETURNED` symbol is the motivating case: it is backward compatible by Avro's rules because
the enum declares a default, yet a consumer that gives the default its own business behaviour can
still misbehave. The `ENUM_SEMANTIC_FALLBACK_RISK` rule detects exactly that, using the consumers
under [`consumers/`](consumers/) — see the risk section of the top-level README.
