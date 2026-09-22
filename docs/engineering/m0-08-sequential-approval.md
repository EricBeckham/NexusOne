# M0-08 sequential approval — engineering notes

## Compatibility spike (hard gate)

| Item | Result |
|---|---|
| JDK | 21 |
| Spring Boot | **4.1.1** (Spring Framework 7) |
| Flowable | **7.1.0** (`ProcessEngine.VERSION` reports `7.1.0.2`) |
| Wiring | `org.flowable:flowable-engine` + `flowable-spring` `SpringProcessEngineConfiguration` |
| Official starter | **Not used in product.** `flowable-spring-boot-starter-process` 7.1.0 still `@AutoConfigureAfter` Boot 3 `org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration`, which is absent in Boot 4. |
| Same JVM | Yes. ProcessEngine is a Spring bean in `nexus-bootstrap` / module tests. |
| Schema | Engine DDL in `flowable`; Flyway V550 only creates the empty schema + `business_meta.process_*`. |
| Shared TX | Business insert + `runtimeService.startProcessInstance*` roll back together. |

Recommended pin if a future work package needs the **Boot starter**: Flowable **8.0.x** (first OSS line declared on Spring Boot 4 / Jackson 3). Do not silently replace 7.1.0 in M0; P0 froze 7.1.0 and the engine path is proven.

## Boundaries

- No SchemaService / ledger physical tables (M0-06).
- No form Draft→Publish engine (M0-07).
- No SQL against `act_*`.
- Tenant from M0-04 issued context + M0-02 `IsolationKernel.tenants().issue`.
