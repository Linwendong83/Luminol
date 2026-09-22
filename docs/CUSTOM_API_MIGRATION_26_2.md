# 26.2 自定义事件及睡眠计数迁移

本次只修改 26.2 服务端。26.1.2 和 SuperVanish-Folia、MonsterTweak、DeathTweak、Instance 均作为只读参考；不包含插件依赖更新、Instance NMS 适配或正式服部署。

## 接口契约

- `PreEntityPortalEvent`：在来源实体所属区域、移除实体之前触发，可取消；提供传送门位置、目标世界、`PortalType` 和 `TeleportCause`。
- `PostEntityPortalEvent`：根实体成功落地后在目标区域触发一次，提供来源传送门位置及实际落点；取消或未完成的传送不发布成功事件。事件发布后继续调用原有完成回调。
- `PostPlayerRespawnEvent`：继承 `AbstractRespawnEvent`，提供本次实际解析的重生位置、床／锚标志、缺失重生点标志及原因；在玩家加入目标世界后触发。
- `PlayerSpectateTeleportEvent`：目标区域先取得位置快照，随后在观察者区域、实际传送前触发；取消时以 `null` 完成回调。事件里的目标实体可能位于其他区域，应使用 `getDestination()`，不能直接读取目标实时状态。
- 事件位置采用快照，调用方修改传入位置或 getter 返回值不会改变事件内容。

包名继续使用 `me.earthme.luminol.api`，产物名称和 Maven 坐标使用目标仓库的 Lophine。接口以自定义 26.1.2 契约为基准，不承诺兼容旧版 Lophine 的简化事件构造器。

末地返回继续使用 `END_PORTAL` 原因的重生流程；末地折跃门不发布这里的跨维度传送门前后事件。进入末地仍保留平台生成。

## 睡眠行为

旁观者以及 `Player.isSleepingIgnored()` 为真的玩家，不计入活跃人数、睡眠人数或深睡人数。百分比所需人数保留向上取整及至少一人的规则，跳夜仍要求至少一名参与计数的玩家真正深睡。SuperVanish 继续使用标准 Bukkit 的 `setSleepingIgnored` 即可。

## 自动化验证

使用 JDK 25，在仓库根目录执行：

```powershell
.\gradlew.bat applyAllPatches --console=plain
.\gradlew.bat :lophine-api:test --tests me.earthme.luminol.api.TeleportEventContractTest --console=plain
.\gradlew.bat :lophine-server:test --tests me.earthme.luminol.CustomApiTestSuite --console=plain
.\gradlew.bat createPaperclipJar --console=plain
```

- API 测试：位置防御性复制、传送类型与原因、取消状态、重生标志。
- 睡眠行为测试：50%／100% 比例、忽略玩家与旁观者、没有真正深睡者、隐身状态切换。
- 旁观行为测试：实际调用 `TeleportUtils`，使用受控调度器验证目标快照→观察者任务→事件→传送的顺序，以及取消、目标退休、观察者退休的完成回调。
- 传送门行为测试：执行真实 `portalToAsync` 入口及完成回调，控制区块搜索和实体放置，覆盖成功落地、取消、不可传送、目标世界卸载及关服中止；验证完成事件不会提前发布，且原有回调和世界读引用释放仍有效。
- 编译产物检查：使用 ASM 验证传送门和重生事件已接入异步回调，并检查末地平台生成、普通传送及末地折跃门边界。这些检查不能替代完整游戏内验证。

## 本地验证结果（2026-09-22）

- `applyAllPatches` 成功；从未修改的生成源码重新应用新增 `0139` 补丁后，四个修改文件的内容哈希与迁移结果一致（统一换行后比较），生成源码 Git 工作区干净。
- JDK 25 下 API 和服务端编译成功，`createPaperclipJar` 成功。
- 新增 24 项测试全部通过：API 契约 4 项、传送门行为 5 项、睡眠行为 5 项、旁观传送行为 6 项、编译调用点检查 4 项。
- 额外执行全量 API 测试：521 项中 520 项通过，`org.bukkit.AnnotationTest.testAll` 因其他接口缺少 151 处空值注解失败。失败列表不涉及本次四个事件类；未扩展此次范围去修改这些接口。原始报告保存在 `build/reports/migration-api-annotation-failure.xml`。
- 可启动产物：`lophine-server/build/libs/lophine-paperclip-26.2.local-SNAPSHOT.jar`。
- 在 `build/migration-smoke` 隔离目录执行该 JAR 的 `--version`，成功应用启动器补丁、加载服务端主类并输出 `26.2-DEV-785219d`；未启动游戏世界。
- 上述本地验证产物 SHA-256：`F978F1CBD64F4BF03509551A1C8AF1B92B4DEF597B12A68912FFAED9749E9717`；GitHub Actions 重新构建的发布附件会有独立的校验值。
- 未修改四个插件及 26.1.2 工作区，未进行正式服部署或多人游戏内验收。

## 待游戏内验收

在隔离测试服进行；不能直接升级或改写生产存档。

1. 下界及末地传送成功时记录事件次数、实际来源／落点和线程归属；取消前置事件后实体仍在原地，平台及原有完成回调行为正常。
2. 床、重生锚、被破坏／阻挡的床、无充能重生锚、默认出生点，以及末地返回：核对位置、标志和原因。
3. 同区域与跨区域旁观，取消旁观、目标离线和观察者离线；核对无越区读取错误或重复回调。
4. 多人分别在线、隐身、旁观及睡眠，切换 50% 和 100% 睡眠比例；只有被忽略玩家深睡时不能跳夜。
5. 末地折跃门不能被记录为主世界／下界／末地之间的传送门状态继承。

现有插件能否在 26.2 完整运行，需要后续插件适配和联合测试，本次不据此作出保证。
