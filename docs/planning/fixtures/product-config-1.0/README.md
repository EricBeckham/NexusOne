# Product config package format 1.0 (IM1)

Fixtures for IM wave §2 — **docs only**, no export/import engine in this card.

| File | Role |
|---|---|
| `nexusone-product-config.schema.json` | JSON Schema (draft 2020-12) for format **1.0** |
| `sample-demo-leave.product-config.json` | Valid sample matching the schema |

## Top-level keys (`formatVersion` = `1.0`)

| Key | Required | Notes |
|---|---|---|
| `manifest` | yes | `format` = `nexusone.product-config`, `formatVersion` = `1.0`, `exportedAt`, `source`, optional `checksum` |
| `formTemplates` | yes | Published template snapshots (fields only) |
| `processDefinitions` | yes | Nodes/edges; no runtime instances |
| `productDefinition` | yes | Binds form + process versions |
| `skuBindings` | yes | skuCode + entitlement keys; **no prices** |
| `printTemplates` | no | Later placeholder, default `[]` |
| `printSupport` | no | Constant `config+preview-later` when present |

ZIP export (later BE): root file `nexusone-product-config.json` with this shape.