# P0-02 Permission Engine 语义 PoC

运行：

```powershell
python -B -m unittest discover -s poc/p0-02 -p test_*.py -v
```

当前用 Python 3.14 标准库实现独立语义验证。授权匹配用户、角色（含继承）、岗位、部门。租户边界先于显式拒绝；拒绝先于授权。所有匹配授权的数据范围与记录共享取 OR，再与有限上下文条件取 AND。字段可见和编辑取并集，脱敏等级与二次授权取最严格。无资源操作授权时，记录共享不能单独授予访问。PolicySnapshot 按租户及数据库版本检查；通知只促使提前刷新，版本读取或加载失败时拒绝。

## P0-01 联调接口

`evaluate(...).scope` 返回 `Predicate(op, args)` 抽象树。P0-01 的 Query AST 适配器须将 `atom(column, operator, value)` 编译为**带参数**的 PostgreSQL 谓词，将 `or` / `and` / `true` / `false` 映射为对应 AST 节点。列名必须来自台账字段注册表，不能接受请求中的任意标识符。由 P0-01 独立注入 `tenant_id = :tenant_id` 和软删除条件，在 SQL 执行前与此 scope 取 AND；总数、列表、导出、详情和写入前行校验使用同一过滤语义。`FieldRule` 须映射为 Projection、只读/编辑检查、脱敏及敏感字段二次授权检查。

## 未完成的集成验收

- 尚无 PostgreSQL Schema、真实 SQL Golden Test、跨租户查询实测；待 P0-01 AST 适配后验证。
- 尚无 Redis 通道、数据库策略版本表、多实例轮询、LRU 和 P95 压测。当前 `SnapshotManager.get` 每次读取权威版本，失败即拒绝，未实现生产轮询间隔与有界陈旧窗口。
- 有限 Context 当前仅接收上游构造的谓词；时间/IP 条件求值、字段二次授权的具体业务验证仍需集成。
- 当前 PoC 无应用框架接线，也没有持久化授权关系。不能据此宣布 P0-02 门禁通过。
