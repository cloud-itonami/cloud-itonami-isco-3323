# cloud-itonami-isco-3323

Open Occupation Blueprint for **ISCO-08 3323**: Buyers.

This repository designs a forkable OSS business for an independent procurement and sourcing practice: a receiving-dock intake and inventory-tagging robot manages incoming shipments under a governor-gated actor, so the practice keeps its own purchasing records instead of renting a closed procurement SaaS.

## Robotics premise

All cloud-itonami verticals are designed on the premise that a **robot performs
the physical domain work**. Here a receiving-dock intake and inventory-tagging robot performs incoming-shipment scanning, tagging and staging under an actor that proposes
actions and an independent **Procurement Governor** that gates them. The governor never
dispatches hardware itself; `:high`/`:safety-critical` actions (such as
purchase order above the client's registered budget-authorization ceiling) require human sign-off.

A live sample of the operator console (robotics safety console, shared template) is rendered in [docs/samples/operator-console.html](docs/samples/operator-console.html) — pure-data HTML output of `kotoba.robotics.ui`.

## Core Contract

```text
purchase request + supplier catalog + budget authorization
        |
        v
Procurement Advisor -> Procurement Governor -> place order/approve, or human sign-off
        |
        v
robot actions (gated) + operating records + audit ledger
```

No automated advice can dispatch a robot action the governor refuses, suppress
an operating record, or disclose sensitive data without governor approval and
audit evidence.

## Capability layer

Resolves via [`kotoba-lang/occupation`](https://github.com/kotoba-lang/occupation)
(ISCO-08 `3323`). Required capabilities:

- :robotics
- :forms
- :audit-ledger

See [`docs/business-model.md`](docs/business-model.md) and
[`docs/operator-guide.md`](docs/operator-guide.md).

## License

AGPL-3.0-or-later.
