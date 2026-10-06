# Shoreline 模块交叉对照

本节基于你提供的 `shoreline-main` 源码，与前面的 JPI mapping、完整 JAR class 结构及 `native_mapping.json` 进行交叉分析。Shoreline 目标版本为 Minecraft 1.21.1；表中的低置信项是候选，不等同于已经恢复出的真实原名。

## Shoreline 类别与 Hachimi 类别枚举对应

Hachimi 的 `class_879`（`pw.hachimi.client.\u0000kV\u200e`）是模块类别枚举。结合多个强锚点后，可以得到下面的映射：

| Hachimi 枚举字段 | 对应 Shoreline | 主要锚点 |
|---|---|---|
| `a\u200e` | `COMBAT` | AutoCrystal 等战斗模块 |
| `b\u200e` | `MISCELLANEOUS` | AntiSpam、Spammer、BetterChat |
| `c\u200e` | `RENDER` | Shaders、Nametags |
| `d\u200e` | `MOVEMENT` | Velocity、Flight、ElytraFly |
| `e\u200e` | `WORLD` | FastPlace、Nuker、AirPlace |
| `f\u200e` | `EXPLOITS` | Crasher、PacketFly、InventorySync、AntiHunger |
| `g\u200e` | 特殊集成 / Baritone | `class_232` 直接调用 `BaritoneAPI` |
| `h\u200e` | CLIENT / HUD / UI 体系 | 与 Shoreline CLIENT 最接近，但 Hachimi 额外拆分 HUD/component 层，不能简单 1:1 |

> 注意：早期仅按枚举顺序猜类别很容易把 `b/c/e/f` 排错。本表是以实际 API/模块行为锚点反推的结果。

## 强锚点：建议直接用于重命名

这一节只放有 API / accessor / 第三方库 / 事件结构等较强证据的对应关系。

| Shoreline 模块 | Hachimi | 混淆类名 | 级别 | 关键依据 |
|---|---|---|---|---|
| `BaritoneModule` | `class_232` | `pw/hachimi/client/\u0000hb\u200e` | A｜高置信 | Hachimi 直接引用 `baritone/api/BaritoneAPI`、`Settings`，并访问 `allowSprint`、`allowBreak`、`allowPlace`、`blockReachDistance` 等设置；不是相似度猜测。 |
| `AntiHungerModule` | `class_667` | `pw/hachimi/client/\u0000dm\u200e` | A｜高置信 | 双方共享 `hookSetOnGround`，且类别位于 EXPLOITS。 |
| `CrasherModule` | `class_612` | `pw/hachimi/client/\u0000d\u200e` | A｜高置信 | 双方均使用 fastutil `Int2ObjectOpenHashMap`，Config 1↔1、Listener 2↔2。 |
| `ExtendedFireworkModule` | `class_524` | `pw/hachimi/client/\u0000e\u200e` | A｜高置信 | 双方共享 `hookExplodeAndRemove` / `hookGetShooter`，Listener 4↔4。 |
| `InventorySyncModule` | `class_514` | `pw/hachimi/client/\u0000pZ\u200e` | A｜高置信 | 双方共享 mixin accessor `hookSetRevision`，Config 1↔1、Listener 1↔1。 |
| `PacketFlyModule` | `class_503` | `pw/hachimi/client/\u0000hv\u200e` | A｜高置信 | 双方均出现 `ConcurrentMap`，且 Config 11↔11、Listener 8↔8。 |
| `AntiSpamModule` | `class_478` | `pw/hachimi/client/\u0000jC\u200e` | A｜高置信 | 双方均出现 `Pattern` / `Matcher` / regex，且 Config 1↔1、Listener 1↔1。 |
| `BetterChatModule` | `class_469` | `pw/hachimi/client/\u0000do\u200e` | A｜高置信 | 双方均出现 `SimpleDateFormat`，且 Config 7↔7、Listener 7↔7。 |
| `SpammerModule` | `class_433` | `pw/hachimi/client/\u0000ep\u200e` | A｜高置信 | 双方均有 charset / IOException 文件读取特征，且 Config 3↔3、Listener 2↔2。 |
| `VelocityModule` | `class_393` | `pw/hachimi/client/\u0000no\u200e` | A｜高置信 | 双方共享高度特异 accessor：`setVelocityX/Z`、`setPlayerVelocityX/Z`。 |
| `NametagsModule` | `class_362` | `pw/hachimi/client/\u0000hF\u200e` | A｜高置信 | 双方共享 `hookDrawLayer`、`Object2IntMap`、`Vector3f/Matrix4f` 等 nametag 渲染指纹。 |
| `ShadersModule` | `class_764` | `pw/hachimi/client/\u0000nM\u200e` | A｜高置信 | 双方共享 Satin `ManagedShaderEffect`、STB image、OpenGL texture、`setUniformValue` 等大量 shader 指纹。 |
| `FastPlaceModule` | `class_286` | `pw/hachimi/client/\u0000mq\u200e` | A｜高置信 | 双方共享 `hookGetItemUseCooldown` 与 `hookSetItemUseCooldown`，且设置/监听数量完全吻合。 |
| `NukerModule` | `class_470` | `pw/hachimi/client/\u0000fW\u200e` | A｜高置信 | 双方均出现 `LinkedList` / `ArrayDeque`，且 Config 22↔22。 |
| `HUDModule` | `class_268` | `pw/hachimi/client/\u0000cf\u200e` | B｜较高 | 配置规模与事件复杂度非常接近（32↔42、11↔10），并共享 `ConcurrentSkipListMap`；但 Hachimi HUD 还拆有额外组件层，因此保留 B/C 级而非 A。 |
| `AutoCrystalModule` | `class_609` | `pw/hachimi/client/\u0000eR\u200e` | B｜较高 | 类别、7 个监听、`ExecutorService`、`Deque`、`predict` 等结构一致；Hachimi 功能规模更大，因此设置数不是 1:1。 |
| `AutoLogModule` | `class_714` | `pw/hachimi/client/\u0000j\u200e` | B｜较高 | 指纹：`BitSet`；Config 9↔11；Listener 2↔2 |
| `AutoTotemModule` | `class_583` | `pw/hachimi/client/\u0000mG\u200e` | B｜较高 | 指纹：`LinkedHashSet`；Config 10↔15；Listener 4↔3；Native ID 8 / 1 methods |
| `FakeLatencyModule` | `class_539` | `pw/hachimi/client/\u0000A\u200e` | B｜较高 | 指纹：`ConcurrentMap`；Config 2↔2；Listener 2↔2 |
| `ChatNotifierModule` | `class_460` | `pw/hachimi/client/\u0000oA\u200e` | B｜较高 | 指纹：`authlib`, `GameProfile`；Config 6↔8；Listener 5↔6 |
| `SkinBlinkModule` | `class_435` | `pw/hachimi/client/\u0000pT\u200e` | B｜较高 | 双方共享 `getPlayerModelParts`，Config 2↔2、Listener 1↔1。 |
| `ElytraFlyModule` | `class_343` | `pw/hachimi/client/\u0000et\u200e` | B｜较高 | 双方共享 firework shooter accessor，Config 11↔11，并有相近事件结构。 |
| `FireworkBoostModule` | `class_605` | `pw/hachimi/client/\u0000pG\u200e` | B｜较高 | 指纹：`hookGetShooter`；Config 4↔8；Listener 1↔2；Native ID 3 / 4 methods |
| `FlightModule` | `class_401` | `pw/hachimi/client/\u0000mU\u200e` | B｜较高 | 双方共享 `hookSetY`，Config 7↔7、Listener 2↔2。 |
| `AnimationsModule` | `class_382` | `pw/hachimi/client/\u0000pl\u200e` | B｜较高 | 双方共享 `setIterable`，Config 9↔9，监听数接近。 |
| `SearchModule` | `class_321` | `pw/hachimi/client/\u0000jq\u200e` | B｜较高 | 指纹：`Matrix4f`；Config 5↔6；Listener 7↔6 |
| `TracersModule` | `class_314` | `pw/hachimi/client/\u0000oQ\u200e` | B｜较高 | 指纹：`Matrix4f`；Config 16↔16；Listener 1↔0 |
| `TrajectoriesModule` | `class_304` | `pw/hachimi/client/\u0000kz\u200e` | B｜较高 | 指纹：`setProjectionMatrix`, `getProjectionMatrix`, `Matrix4f`；Config 0↔2；Listener 1↔0 |
| `AirPlaceModule` | `class_298` | `pw/hachimi/client/\u0000mP\u200e` | B｜较高 | 指纹：`hookSetItemUseCooldown`；Config 4↔3；Listener 3↔1 |

## 一个重要修正：Baritone

`BaritoneModule` 最终对应：

```text
Shoreline: BaritoneModule
Hachimi : class_232
Original: pw.hachimi.client.\u0000hb\u200e
Category: g\u200e (special integration)
```

`class_232` 的常量池直接包含：

- `baritone/api/BaritoneAPI`
- `baritone/api/Settings`
- `chatDebug`
- `allowWaterBucketFall`
- `disconnectOnArrival`
- `freeLook`
- `antiCheatCompatibility`
- `allowSprint`
- `blockReachDistance`
- `allowBreak`
- `allowPlace`
- `allowInventory`

因此这个对应关系比“Config 数量相近”的自动匹配明显更强。

## Shoreline → Hachimi 全模块候选表

说明：`A` 可以优先直接命名；`B` 很可能正确；`C/C-` 适合作为继续人工核对的候选；`D` 不建议直接重命名。

### COMBAT

| Shoreline | Hachimi 候选 | 混淆名 | 置信度 | 依据摘要 |
|---|---|---|---|---|
| `AuraModule` | `class_549` | `pw/hachimi/client/\u0000fH\u200e` | C-｜偏低候选 | Config 33↔32；Listener 5↔3 |
| `AutoAnchorModule` | `class_646` | `pw/hachimi/client/\u0000pg\u200e` | C｜中等候选 | Config 26↔27；Listener 2↔2 |
| `AutoArmorModule` | `class_656` | `pw/hachimi/client/\u0000fx\u200e` | C｜中等候选 | 指纹：`Comparable`；Config 6↔10；Listener 1↔1；Native ID 48 / 1 methods |
| `AutoBowReleaseModule` | `class_611` | `pw/hachimi/client/\u0000fB\u200e` | C-｜偏低候选 | Config 3↔3；Listener 1↔1 |
| `AutoCrawlTrapModule` | `class_648` | `pw/hachimi/client/\u0000bA\u200e` | C-｜偏低候选 | Config 14↔13；Listener 3↔1 |
| `AutoCrystalModule` | `class_609` | `pw/hachimi/client/\u0000eR\u200e` | B｜较高 | 类别、7 个监听、`ExecutorService`、`Deque`、`predict` 等结构一致；Hachimi 功能规模更大，因此设置数不是 1:1。 |
| `AutoLogModule` | `class_714` | `pw/hachimi/client/\u0000j\u200e` | B｜较高 | 指纹：`BitSet`；Config 9↔11；Listener 2↔2 |
| `AutoTotemModule` | `class_583` | `pw/hachimi/client/\u0000mG\u200e` | B｜较高 | 指纹：`LinkedHashSet`；Config 10↔15；Listener 4↔3；Native ID 8 / 1 methods |
| `AutoTrapModule` | `class_802` | `pw/hachimi/client/\u0000qV\u200e` | C-｜偏低候选 | Config 17↔16；Listener 3↔1；Native ID 56 / 1 methods |
| `AutoWebModule` | `class_651` | `pw/hachimi/client/\u0000fT\u200e` | C-｜偏低候选 | Config 12↔11；Listener 3↔1；Native ID 42 / 1 methods |
| `AutoXPModule` | `class_565` | `pw/hachimi/client/\u0000jP\u200e` | C-｜偏低候选 | Config 6↔10；Listener 1↔1 |
| `BasePlaceModule` | `class_563` | `pw/hachimi/client/\u0000qY\u200e` | C-｜偏低候选 | Config 11↔11；Listener 2↔1；Native ID 17 / 1 methods |
| `BowAimModule` | `class_784` | `pw/hachimi/client/\u0000fU\u200e` | D｜低置信候选 | Config 5↔9；Listener 2↔1；Native ID 20 / 2 methods |
| `ClickCrystalModule` | `class_628` | `pw/hachimi/client/\u0000bS\u200e` | C-｜偏低候选 | Config 4↔4；Listener 4↔2 |
| `CriticalsModule` | `class_272` | `pw/hachimi/client/\u0000lf\u200e` | D｜低置信候选 | Config 5↔9；Listener 2↔1；Native ID 53 / 1 methods |
| `HoleFillModule` | `class_632` | `pw/hachimi/client/\u0000qU\u200e` | C-｜偏低候选 | Config 18↔18；Listener 3↔1；Native ID 22 / 1 methods |
| `KeepSprintModule` | `class_712` | `pw/hachimi/client/\u0000bI\u200e` | D｜低置信候选 | Config 0↔11；Listener 1↔1；Native ID 33 / 1 methods |
| `NoHitDelayModule` | `class_534` | `pw/hachimi/client/\u0000oi\u200e` | C-｜偏低候选 | Config 0↔0；Listener 1↔1 |
| `ReplenishModule` | `class_717` | `pw/hachimi/client/\u0000bX\u200e` | D｜低置信候选 | Config 2↔2；Listener 3↔1 |
| `SelfBowModule` | `class_541` | `pw/hachimi/client/\u0000on\u200e` | C-｜偏低候选 | Config 0↔0；Listener 1↔1 |
| `SelfFillModule` | `class_804` | `pw/hachimi/client/\u0000ly\u200e` | C-｜偏低候选 | Config 7↔10；Listener 2↔2 |
| `SelfTrapModule` | `class_543` | `pw/hachimi/client/\u0000qk\u200e` | C｜中等候选 | Config 20↔20；Listener 3↔2 |
| `SurroundModule` | `class_558` | `pw/hachimi/client/\u0000ab\u200e` | C｜中等候选 | Config 18↔18；Listener 3↔3；Native ID 4 / 3 methods |
| `TriggerModule` | `class_663` | `pw/hachimi/client/\u0000nP\u200e` | C-｜偏低候选 | Config 3↔5；Listener 1↔1 |

### MISCELLANEOUS

| Shoreline | Hachimi 候选 | 混淆名 | 置信度 | 依据摘要 |
|---|---|---|---|---|
| `AntiAFKModule` | `class_483` | `pw/hachimi/client/\u0000nT\u200e` | C｜中等候选 | Config 8↔8；Listener 3↔3 |
| `AntiAimModule` | `class_481` | `pw/hachimi/client/\u0000cs\u200e` | C｜中等候选 | Config 6↔6；Listener 1↔1 |
| `AntiSpamModule` | `class_478` | `pw/hachimi/client/\u0000jC\u200e` | A｜高置信 | 双方均出现 `Pattern` / `Matcher` / regex，且 Config 1↔1、Listener 1↔1。 |
| `AntiVanishModule` | `class_816` | `pw/hachimi/client/\u0000cd\u200e` | D｜低置信候选 | Config 0↔0；Listener 2↔0 |
| `AutoAcceptModule` | `class_637` | `pw/hachimi/client/\u0000nJ\u200e` | C-｜偏低候选 | Config 1↔1；Listener 1↔0 |
| `AutoAnvilRenameModule` | `class_477` | `pw/hachimi/client/\u0000ni\u200e` | B｜较高 | Config 6↔6；Listener 1↔1 |
| `AutoEatModule` | `class_471` | `pw/hachimi/client/\u0000gi\u200e` | D｜低置信候选 | Config 1↔6；Listener 1↔2 |
| `AutoFishModule` | `class_527` | `pw/hachimi/client/\u0000iu\u200e` | C-｜偏低候选 | Config 3↔4；Listener 2↔2 |
| `AutoFrameDupeModule` | `class_446` | `pw/hachimi/client/\u0000pq\u200e` | C-｜偏低候选 | Config 4↔6；Listener 1↔1 |
| `AutoMountModule` | `class_277` | `pw/hachimi/client/\u0000eG\u200e` | C-｜偏低候选 | Config 6↔4；Listener 2↔2 |
| `AutoReconnectModule` | `class_100` | `pw/hachimi/client/\u0000ob\u200e` | D｜低置信候选 | Config 1↔0；Listener 0↔0 |
| `AutoRespawnModule` | `class_584` | `pw/hachimi/client/\u0000ir\u200e` | D｜低置信候选 | Config 0↔4；Listener 2↔0 |
| `BetterChatModule` | `class_469` | `pw/hachimi/client/\u0000do\u200e` | A｜高置信 | 双方均出现 `SimpleDateFormat`，且 Config 7↔7、Listener 7↔7。 |
| `BetterInvModule` | `class_353` | `pw/hachimi/client/\u0000bp\u200e` | C-｜偏低候选 | Config 3↔4；Listener 2↔2 |
| `ChatNotifierModule` | `class_460` | `pw/hachimi/client/\u0000oA\u200e` | B｜较高 | 指纹：`authlib`, `GameProfile`；Config 6↔8；Listener 5↔6 |
| `ChestStealerModule` | `class_425` | `pw/hachimi/client/\u0000cq\u200e` | D｜低置信候选 | Config 2↔4；Listener 1↔3 |
| `ChestSwapModule` | `class_316` | `pw/hachimi/client/\u0000na\u200e` | D｜低置信候选 | Config 2↔9；Listener 0↔0 |
| `FakePlayerModule` | `class_776` | `pw/hachimi/client/\u0000fE\u200e` | D｜低置信候选 | Config 0↔12；Listener 2↔4 |
| `InvCleanerModule` | `class_753` | `pw/hachimi/client/\u0000dd\u200e` | C-｜偏低候选 | Config 3↔4；Listener 1↔1 |
| `MiddleClickModule` | `class_756` | `pw/hachimi/client/\u0000gT\u200e` | D｜低置信候选 | Config 3↔3；Listener 1↔3；Native ID 68 / 2 methods |
| `NoPacketKickModule` | `class_443` | `pw/hachimi/client/\u0000ic\u200e` | C-｜偏低候选 | Config 0↔0；Listener 1↔1 |
| `NoSoundLagModule` | `class_271` | `pw/hachimi/client/\u0000hf\u200e` | C-｜偏低候选 | Config 0↔0；Listener 1↔1 |
| `PMSoundModule` | `class_340` | `pw/hachimi/client/\u0000cn\u200e` | D｜低置信候选 | Config 1↔1；Listener 1↔1 |
| `PacketLoggerModule` | `class_273` | `pw/hachimi/client/\u0000oD\u200e` | C-｜偏低候选 | Config 21↔21；Listener 3↔3 |
| `ServerModule` | `class_450` | `pw/hachimi/client/\u0000hw\u200e` | D｜低置信候选 | Config 4↔5；Listener 2↔4 |
| `ShulkerceptionModule` | `class_426` | `pw/hachimi/client/\u0000eb\u200e` | C-｜偏低候选 | Config 0↔0；Listener 1↔1 |
| `SkinBlinkModule` | `class_435` | `pw/hachimi/client/\u0000pT\u200e` | B｜较高 | 双方共享 `getPlayerModelParts`，Config 2↔2、Listener 1↔1。 |
| `SpammerModule` | `class_433` | `pw/hachimi/client/\u0000ep\u200e` | A｜高置信 | 双方均有 charset / IOException 文件读取特征，且 Config 3↔3、Listener 2↔2。 |
| `SwingModule` | `class_765` | `pw/hachimi/client/\u0000qn\u200e` | C-｜偏低候选 | Config 1↔1；Listener 1↔1 |
| `TimerModule` | `class_428` | `pw/hachimi/client/\u0000bc\u200e` | C｜中等候选 | Config 2↔2；Listener 2↔2 |
| `TrueDurabilityModule` | `class_618` | `pw/hachimi/client/\u0000U\u200e` | C-｜偏低候选 | Config 0↔0；Listener 1↔1 |
| `UnfocusedFPSModule` | `class_472` | `pw/hachimi/client/\u0000dC\u200e` | D｜低置信候选 | Config 1↔3；Listener 1↔2 |
| `XCarryModule` | `class_721` | `pw/hachimi/client/\u0000qA\u200e` | D｜低置信候选 | Config 1↔28；Listener 1↔1；Native ID 21 / 1 methods |

### RENDER

| Shoreline | Hachimi 候选 | 混淆名 | 置信度 | 依据摘要 |
|---|---|---|---|---|
| `AmbienceModule` | `class_365` | `pw/hachimi/client/\u0000kl\u200e` | C-｜偏低候选 | Config 8↔6；Listener 5↔4 |
| `AnimationsModule` | `class_382` | `pw/hachimi/client/\u0000pl\u200e` | B｜较高 | 双方共享 `setIterable`，Config 9↔9，监听数接近。 |
| `BlockHighlightModule` | `class_361` | `pw/hachimi/client/\u0000Z\u200e` | C-｜偏低候选 | Config 5↔7；Listener 2↔1 |
| `BreadcrumbsModule` | `class_360` | `pw/hachimi/client/\u0000ql\u200e` | C-｜偏低候选 | Config 9↔9；Listener 3↔2 |
| `BreakHighlightModule` | `class_554` | `pw/hachimi/client/\u0000qH\u200e` | D｜低置信候选 | Config 2↔8；Listener 2↔2 |
| `ChamsModule` | `class_357` | `pw/hachimi/client/\u0000gq\u200e` | C｜中等候选 | Config 14↔17；Listener 7↔6 |
| `CrosshairModule` | `class_317` | `pw/hachimi/client/\u0000gg\u200e` | C-｜偏低候选 | Config 8↔12；Listener 1↔0 |
| `CrystalModelModule` | `class_587` | `pw/hachimi/client/\u0000jo\u200e` | C-｜偏低候选 | Config 3↔5；Listener 1↔0 |
| `ESPModule` | `class_355` | `pw/hachimi/client/\u0000hG\u200e` | C-｜偏低候选 | Config 17↔23；Listener 3↔0 |
| `ExtraTabModule` | `class_412` | `pw/hachimi/client/\u0000gI\u200e` | C-｜偏低候选 | Config 4↔4；Listener 5↔3 |
| `FreeLookModule` | `class_542` | `pw/hachimi/client/\u0000cm\u200e` | D｜低置信候选 | Config 0↔7；Listener 4↔3；Native ID 64 / 2 methods |
| `FreecamModule` | `class_359` | `pw/hachimi/client/\u0000jW\u200e` | D｜低置信候选 | Config 5↔10；Listener 12↔0 |
| `FullbrightModule` | `class_610` | `pw/hachimi/client/\u0000ok\u200e` | D｜低置信候选 | Config 1↔2；Listener 4↔5 |
| `HoleESPModule` | `class_342` | `pw/hachimi/client/\u0000aF\u200e` | C-｜偏低候选 | Config 15↔19；Listener 1↔0 |
| `KillEffectsModule` | `class_749` | `pw/hachimi/client/\u0000eM\u200e` | C-｜偏低候选 | Config 2↔3；Listener 2↔1 |
| `NameProtectModule` | `class_278` | `pw/hachimi/client/\u0000mS\u200e` | D｜低置信候选 | Config 1↔1；Listener 1↔1 |
| `NametagsModule` | `class_362` | `pw/hachimi/client/\u0000hF\u200e` | A｜高置信 | 双方共享 `hookDrawLayer`、`Object2IntMap`、`Vector3f/Matrix4f` 等 nametag 渲染指纹。 |
| `NoBobModule` | `class_414` | `pw/hachimi/client/\u0000iF\u200e` | D｜低置信候选 | Config 0↔12；Listener 1↔0 |
| `NoRenderModule` | `class_755` | `pw/hachimi/client/\u0000bb\u200e` | D｜低置信候选 | Config 27↔36；Listener 28↔25 |
| `NoRotateModule` | `class_303` | `pw/hachimi/client/\u0000m\u200e` | D｜低置信候选 | Config 1↔1；Listener 1↔0 |
| `NoWeatherModule` | `class_665` | `pw/hachimi/client/\u0000gD\u200e` | D｜低置信候选 | Config 2↔2；Listener 3↔0 |
| `ParticlesModule` | `class_332` | `pw/hachimi/client/\u0000ky\u200e` | C｜中等候选 | Config 14↔14；Listener 5↔6 |
| `PhaseESPModule` | `class_358` | `pw/hachimi/client/\u0000cj\u200e` | C｜中等候选 | Config 6↔6；Listener 1↔1 |
| `SearchModule` | `class_321` | `pw/hachimi/client/\u0000jq\u200e` | B｜较高 | 指纹：`Matrix4f`；Config 5↔6；Listener 7↔6 |
| `ShadersModule` | `class_764` | `pw/hachimi/client/\u0000nM\u200e` | A｜高置信 | 双方共享 Satin `ManagedShaderEffect`、STB image、OpenGL texture、`setUniformValue` 等大量 shader 指纹。 |
| `SkyboxModule` | `class_320` | `pw/hachimi/client/\u0000qi\u200e` | C｜中等候选 | Config 9↔11；Listener 6↔6 |
| `StorageESPModule` | `class_322` | `pw/hachimi/client/\u0000ib\u200e` | C｜中等候选 | Config 13↔15；Listener 1↔1 |
| `TooltipsModule` | `class_327` | `pw/hachimi/client/\u0000po\u200e` | D｜低置信候选 | Config 2↔3；Listener 1↔0 |
| `TracersModule` | `class_314` | `pw/hachimi/client/\u0000oQ\u200e` | B｜较高 | 指纹：`Matrix4f`；Config 16↔16；Listener 1↔0 |
| `TrajectoriesModule` | `class_304` | `pw/hachimi/client/\u0000kz\u200e` | B｜较高 | 指纹：`setProjectionMatrix`, `getProjectionMatrix`, `Matrix4f`；Config 0↔2；Listener 1↔0 |
| `TrueSightModule` | `class_330` | `pw/hachimi/client/\u0000eI\u200e` | D｜低置信候选 | Config 1↔7；Listener 1↔0 |
| `ViewClipModule` | `class_363` | `pw/hachimi/client/\u0000lR\u200e` | D｜低置信候选 | Config 1↔1；Listener 1↔0 |
| `ViewModelModule` | `class_701` | `pw/hachimi/client/\u0000ex\u200e` | C-｜偏低候选 | Config 9↔9；Listener 1↔1 |
| `WaypointsModule` | `class_301` | `pw/hachimi/client/\u0000dY\u200e` | C｜中等候选 | 指纹：`newConcurrentMap`；Config 4↔8；Listener 6↔6 |
| `ZoomModule` | `class_633` | `pw/hachimi/client/\u0000rk\u200e` | C｜中等候选 | Config 3↔3；Listener 2↔2 |

### MOVEMENT

| Shoreline | Hachimi 候选 | 混淆名 | 置信度 | 依据摘要 |
|---|---|---|---|---|
| `AntiLevitationModule` | `class_422` | `pw/hachimi/client/\u0000mz\u200e` | C-｜偏低候选 | Config 0↔0；Listener 1↔1 |
| `AutoWalkModule` | `class_379` | `pw/hachimi/client/\u0000ek\u200e` | D｜低置信候选 | Config 1↔3；Listener 1↔2 |
| `ElytraFlyModule` | `class_343` | `pw/hachimi/client/\u0000et\u200e` | B｜较高 | 双方共享 firework shooter accessor，Config 11↔11，并有相近事件结构。 |
| `EntityControlModule` | `class_392` | `pw/hachimi/client/\u0000rl\u200e` | C-｜偏低候选 | Config 2↔4；Listener 4↔4；Native ID 36 / 4 methods |
| `EntitySpeedModule` | `class_413` | `pw/hachimi/client/\u0000aA\u200e` | C-｜偏低候选 | Config 3↔3；Listener 2↔2 |
| `FakeLagModule` | `class_411` | `pw/hachimi/client/\u0000pr\u200e` | C｜中等候选 | Config 4↔4；Listener 3↔3 |
| `FastFallModule` | `class_398` | `pw/hachimi/client/\u0000ay\u200e` | C-｜偏低候选 | Config 4↔11；Listener 3↔3；Native ID 16 / 1 methods |
| `FastSwimModule` | `class_687` | `pw/hachimi/client/\u0000kY\u200e` | C｜中等候选 | Config 7↔7；Listener 1↔1 |
| `FireworkBoostModule` | `class_605` | `pw/hachimi/client/\u0000pG\u200e` | B｜较高 | 指纹：`hookGetShooter`；Config 4↔8；Listener 1↔2；Native ID 3 / 4 methods |
| `FlightModule` | `class_401` | `pw/hachimi/client/\u0000mU\u200e` | B｜较高 | 双方共享 `hookSetY`，Config 7↔7、Listener 2↔2。 |
| `IceSpeedModule` | `class_380` | `pw/hachimi/client/\u0000nK\u200e` | D｜低置信候选 | Config 0↔0；Listener 0↔1 |
| `JesusModule` | `class_548` | `pw/hachimi/client/\u0000oj\u200e` | C｜中等候选 | 指纹：`hookSetY`；Config 2↔6；Listener 5↔4 |
| `LongJumpModule` | `class_396` | `pw/hachimi/client/\u0000eJ\u200e` | C｜中等候选 | 指纹：`ArrayIndexOutOfBoundsException`；Config 4↔3；Listener 5↔3 |
| `NoAccelModule` | `class_287` | `pw/hachimi/client/\u0000ei\u200e` | D｜低置信候选 | Config 2↔6；Listener 1↔2 |
| `NoFallModule` | `class_274` | `pw/hachimi/client/\u0000jE\u200e` | D｜低置信候选 | Config 1↔0；Listener 2↔2；Native ID 40 / 2 methods |
| `NoJumpDelayModule` | `class_519` | `pw/hachimi/client/\u0000hs\u200e` | C-｜偏低候选 | Config 0↔0；Listener 1↔1 |
| `NoSlowModule` | `class_387` | `pw/hachimi/client/\u0000kM\u200e` | C｜中等候选 | 指纹：`getBoundKey`；Config 17↔13；Listener 10↔13；Native ID 39 / 2 methods |
| `ParkourModule` | `class_388` | `pw/hachimi/client/\u0000cx\u200e` | D｜低置信候选 | Config 0↔0；Listener 1↔1 |
| `SafeWalkModule` | `class_873` | `pw/hachimi/client/\u0000aX\u200e` | C-｜偏低候选 | Config 1↔1；Listener 1↔1 |
| `SpeedModule` | `class_378` | `pw/hachimi/client/\u0000cH\u200e` | C｜中等候选 | Config 8↔9；Listener 4↔4 |
| `SprintModule` | `class_376` | `pw/hachimi/client/\u0000dj\u200e` | C-｜偏低候选 | Config 2↔4；Listener 4↔4 |
| `StepModule` | `class_383` | `pw/hachimi/client/\u0000lY\u200e` | C-｜偏低候选 | Config 5↔9；Listener 4↔4；Native ID 15 / 3 methods |
| `TickShiftModule` | `class_402` | `pw/hachimi/client/\u0000lG\u200e` | C-｜偏低候选 | Config 3↔3；Listener 1↔1 |
| `TridentFlyModule` | `class_415` | `pw/hachimi/client/\u0000fG\u200e` | C-｜偏低候选 | Config 4↔2；Listener 3↔3 |
| `VelocityModule` | `class_393` | `pw/hachimi/client/\u0000no\u200e` | A｜高置信 | 双方共享高度特异 accessor：`setVelocityX/Z`、`setPlayerVelocityX/Z`。 |
| `YawModule` | `class_367` | `pw/hachimi/client/\u0000iU\u200e` | C-｜偏低候选 | Config 1↔1；Listener 2↔2 |

### WORLD

| Shoreline | Hachimi 候选 | 混淆名 | 置信度 | 依据摘要 |
|---|---|---|---|---|
| `AirPlaceModule` | `class_298` | `pw/hachimi/client/\u0000mP\u200e` | B｜较高 | 指纹：`hookSetItemUseCooldown`；Config 4↔3；Listener 3↔1 |
| `AntiInteractModule` | `class_297` | `pw/hachimi/client/\u0000lE\u200e` | C-｜偏低候选 | Config 2↔2；Listener 3↔2 |
| `AutoMineModule` | `class_296` | `pw/hachimi/client/\u0000oP\u200e` | D｜低置信候选 | Config 31↔49；Listener 5↔8；Native ID 24 / 3 methods |
| `AutoToolModule` | `class_325` | `pw/hachimi/client/\u0000kI\u200e` | D｜低置信候选 | Config 0↔0；Listener 1↔1 |
| `AvoidModule` | `class_532` | `pw/hachimi/client/\u0000ih\u200e` | C-｜偏低候选 | Config 6↔3；Listener 3↔3 |
| `FastPlaceModule` | `class_286` | `pw/hachimi/client/\u0000mq\u200e` | A｜高置信 | 双方共享 `hookGetItemUseCooldown` 与 `hookSetItemUseCooldown`，且设置/监听数量完全吻合。 |
| `MultitaskModule` | `class_430` | `pw/hachimi/client/\u0000Y\u200e` | D｜低置信候选 | Config 0↔1；Listener 1↔1 |
| `NoGlitchBlocksModule` | `class_352` | `pw/hachimi/client/\u0000kr\u200e` | C-｜偏低候选 | Config 2↔2；Listener 2↔2 |
| `NukerModule` | `class_470` | `pw/hachimi/client/\u0000fW\u200e` | A｜高置信 | 双方均出现 `LinkedList` / `ArrayDeque`，且 Config 22↔22。 |
| `ScaffoldModule` | `class_283` | `pw/hachimi/client/\u0000qm\u200e` | C｜中等候选 | Config 16↔13；Listener 2↔1 |
| `SpeedmineModule` | `class_454` | `pw/hachimi/client/\u0000hx\u200e` | D｜低置信候选 | Config 16↔14；Listener 6↔0；Native ID 57 / 2 methods |

### EXPLOITS

| Shoreline | Hachimi 候选 | 混淆名 | 置信度 | 依据摘要 |
|---|---|---|---|---|
| `AntiHungerModule` | `class_667` | `pw/hachimi/client/\u0000dm\u200e` | A｜高置信 | 双方共享 `hookSetOnGround`，且类别位于 EXPLOITS。 |
| `BacktrackModule` | `class_722` | `pw/hachimi/client/\u0000cu\u200e` | D｜低置信候选 | Config 1↔1；Listener 5↔2；Native ID 18 / 1 methods |
| `ChorusControlModule` | `class_531` | `pw/hachimi/client/\u0000di\u200e` | D｜低置信候选 | Config 0↔1；Listener 4↔4 |
| `ChorusInvincibilityModule` | `class_371` | `pw/hachimi/client/\u0000qZ\u200e` | C-｜偏低候选 | Config 4↔5；Listener 3↔4 |
| `ClientSpoofModule` | `class_506` | `pw/hachimi/client/\u0000pR\u200e` | C-｜偏低候选 | Config 0↔0；Listener 1↔1 |
| `CrasherModule` | `class_612` | `pw/hachimi/client/\u0000d\u200e` | A｜高置信 | 双方均使用 fastutil `Int2ObjectOpenHashMap`，Config 1↔1、Listener 2↔2。 |
| `DisablerModule` | `class_684` | `pw/hachimi/client/\u0000k\u200e` | D｜低置信候选 | Config 1↔2；Listener 5↔4 |
| `ExtendedFireworkModule` | `class_524` | `pw/hachimi/client/\u0000e\u200e` | A｜高置信 | 双方共享 `hookExplodeAndRemove` / `hookGetShooter`，Listener 4↔4。 |
| `FakeLatencyModule` | `class_539` | `pw/hachimi/client/\u0000A\u200e` | B｜较高 | 指纹：`ConcurrentMap`；Config 2↔2；Listener 2↔2 |
| `FastLatencyModule` | `class_520` | `pw/hachimi/client/\u0000ji\u200e` | C-｜偏低候选 | Config 0↔0；Listener 3↔3 |
| `FastProjectileModule` | `class_518` | `pw/hachimi/client/\u0000gx\u200e` | C｜中等候选 | Config 7↔6；Listener 1↔1 |
| `GodModeModule` | `class_270` | `pw/hachimi/client/\u0000dq\u200e` | C-｜偏低候选 | Config 2↔4；Listener 4↔4；Native ID 23 / 3 methods |
| `InventorySyncModule` | `class_514` | `pw/hachimi/client/\u0000pZ\u200e` | A｜高置信 | 双方共享 mixin accessor `hookSetRevision`，Config 1↔1、Listener 1↔1。 |
| `NewChunksModule` | `class_686` | `pw/hachimi/client/\u0000N\u200e` | C｜中等候选 | Config 9↔9；Listener 4↔5 |
| `NoMineAnimationModule` | `class_423` | `pw/hachimi/client/\u0000dT\u200e` | C-｜偏低候选 | Config 0↔0；Listener 1↔1 |
| `PacketCancelerModule` | `class_505` | `pw/hachimi/client/\u0000ol\u200e` | C-｜偏低候选 | Config 14↔14；Listener 1↔1 |
| `PacketFlyModule` | `class_503` | `pw/hachimi/client/\u0000hv\u200e` | A｜高置信 | 双方均出现 `ConcurrentMap`，且 Config 11↔11、Listener 8↔8。 |
| `PhaseModule` | `class_487` | `pw/hachimi/client/\u0000qe\u200e` | C-｜偏低候选 | Config 14↔21；Listener 4↔5；Native ID 13 / 1 methods |
| `ReachModule` | `class_288` | `pw/hachimi/client/\u0000dG\u200e` | C-｜偏低候选 | Config 3↔3；Listener 3↔2 |

### CLIENT

| Shoreline | Hachimi 候选 | 混淆名 | 置信度 | 依据摘要 |
|---|---|---|---|---|
| `AnticheatModule` | `class_247` | `pw/hachimi/client/\u0000aM\u200e` | D｜未确认 | Config 6↔10；Listener 3↔0 |
| `BaritoneModule` | `class_232` | `pw/hachimi/client/\u0000hb\u200e` | A｜高置信 | Hachimi 直接引用 `baritone/api/BaritoneAPI`、`Settings`，并访问 `allowSprint`、`allowBreak`、`allowPlace`、`blockReachDistance` 等设置；不是相似度猜测。 |
| `CapesModule` | `class_240` | `pw/hachimi/client/\u0000ka\u200e` | D｜未确认 | Config 3↔10；Listener 3↔0 |
| `ChatModule` | `class_242` | `pw/hachimi/client/\u0000ce\u200e` | D｜未确认 | Config 1↔10；Listener 4↔0 |
| `ClickGuiModule` | `class_237` | `pw/hachimi/client/\u0000cl\u200e` | D｜未确认 | Config 5↔11；Listener 1↔0 |
| `ColorsModule` | `class_246` | `pw/hachimi/client/\u0000ej\u200e` | D｜未确认 | Config 1↔9；Listener 1↔0 |
| `FontModule` | `class_236` | `pw/hachimi/client/\u0000cb\u200e` | D｜未确认 | Config 4↔11；Listener 2↔0 |
| `HUDModule` | `class_268` | `pw/hachimi/client/\u0000cf\u200e` | B｜较高 | 配置规模与事件复杂度非常接近（32↔42、11↔10），并共享 `ConcurrentSkipListMap`；但 Hachimi HUD 还拆有额外组件层，因此保留 B/C 级而非 A。 |
| `RichPresenceModule` | `class_238` | `pw/hachimi/client/\u0000gp\u200e` | D｜未确认 | Config 2↔10；Listener 2↔0 |
| `RotationsModule` | `class_253` | `pw/hachimi/client/\u0000gY\u200e` | D｜未确认 | Config 3↔10；Listener 0↔0 |
| `SocialsModule` | `class_245` | `pw/hachimi/client/\u0000bH\u200e` | D｜未确认 | 指纹：`function`；Config 3↔11；Listener 1↔0 |

## Hachimi → Shoreline 推荐重命名（A/B 级）

| Hachimi | 建议名 | 原始混淆名 | 置信度 |
|---|---|---|---|
| `class_232` | `BaritoneModule` | `pw/hachimi/client/\u0000hb\u200e` | A｜高置信 |
| `class_268` | `HUDModule` | `pw/hachimi/client/\u0000cf\u200e` | B｜较高 |
| `class_286` | `FastPlaceModule` | `pw/hachimi/client/\u0000mq\u200e` | A｜高置信 |
| `class_298` | `AirPlaceModule` | `pw/hachimi/client/\u0000mP\u200e` | B｜较高 |
| `class_304` | `TrajectoriesModule` | `pw/hachimi/client/\u0000kz\u200e` | B｜较高 |
| `class_314` | `TracersModule` | `pw/hachimi/client/\u0000oQ\u200e` | B｜较高 |
| `class_321` | `SearchModule` | `pw/hachimi/client/\u0000jq\u200e` | B｜较高 |
| `class_343` | `ElytraFlyModule` | `pw/hachimi/client/\u0000et\u200e` | B｜较高 |
| `class_362` | `NametagsModule` | `pw/hachimi/client/\u0000hF\u200e` | A｜高置信 |
| `class_382` | `AnimationsModule` | `pw/hachimi/client/\u0000pl\u200e` | B｜较高 |
| `class_393` | `VelocityModule` | `pw/hachimi/client/\u0000no\u200e` | A｜高置信 |
| `class_401` | `FlightModule` | `pw/hachimi/client/\u0000mU\u200e` | B｜较高 |
| `class_433` | `SpammerModule` | `pw/hachimi/client/\u0000ep\u200e` | A｜高置信 |
| `class_435` | `SkinBlinkModule` | `pw/hachimi/client/\u0000pT\u200e` | B｜较高 |
| `class_460` | `ChatNotifierModule` | `pw/hachimi/client/\u0000oA\u200e` | B｜较高 |
| `class_469` | `BetterChatModule` | `pw/hachimi/client/\u0000do\u200e` | A｜高置信 |
| `class_470` | `NukerModule` | `pw/hachimi/client/\u0000fW\u200e` | A｜高置信 |
| `class_477` | `AutoAnvilRenameModule` | `pw/hachimi/client/\u0000ni\u200e` | B｜较高 |
| `class_478` | `AntiSpamModule` | `pw/hachimi/client/\u0000jC\u200e` | A｜高置信 |
| `class_503` | `PacketFlyModule` | `pw/hachimi/client/\u0000hv\u200e` | A｜高置信 |
| `class_514` | `InventorySyncModule` | `pw/hachimi/client/\u0000pZ\u200e` | A｜高置信 |
| `class_524` | `ExtendedFireworkModule` | `pw/hachimi/client/\u0000e\u200e` | A｜高置信 |
| `class_539` | `FakeLatencyModule` | `pw/hachimi/client/\u0000A\u200e` | B｜较高 |
| `class_583` | `AutoTotemModule` | `pw/hachimi/client/\u0000mG\u200e` | B｜较高 |
| `class_605` | `FireworkBoostModule` | `pw/hachimi/client/\u0000pG\u200e` | B｜较高 |
| `class_609` | `AutoCrystalModule` | `pw/hachimi/client/\u0000eR\u200e` | B｜较高 |
| `class_612` | `CrasherModule` | `pw/hachimi/client/\u0000d\u200e` | A｜高置信 |
| `class_667` | `AntiHungerModule` | `pw/hachimi/client/\u0000dm\u200e` | A｜高置信 |
| `class_714` | `AutoLogModule` | `pw/hachimi/client/\u0000j\u200e` | B｜较高 |
| `class_764` | `ShadersModule` | `pw/hachimi/client/\u0000nM\u200e` | A｜高置信 |

## 对照方法与限制

这不是按“设置数量最像”就硬套名称，而是按下列证据组合评分并人工覆核：

1. 模块类别：Hachimi `a~f` 与 Shoreline 六个功能类别先做硬约束。
2. 继承树：`class_874 (\u0000re\u200e)` 为 Hachimi Module 核心基类，并识别其辅助/中间层，避免把 helper 当成普通模块。
3. Config 数量与类型：Boolean / Number / Enum / Color / List / String 等设置结构。
4. Event listener：监听数量及能够识别的事件类型。
5. Minecraft / Mixin accessor 指纹：例如 `hookSetRevision`、`setVelocityX`、`hookGetItemUseCooldown`。
6. 第三方 API：Baritone、Satin、STB、fastutil、authlib 等。
7. Native mapping：若类被 native registrar 注册，则把 Registration ID 和 native 方法数量作为附加证据。
8. 全局一对一分配只作为“候选生成器”，最终以语义/API 指纹优先；例如自动分配一度把 Baritone 指向 `class_233`，但 `class_232` 直接引用 `BaritoneAPI`，因此最终覆写为 `class_232`。

### 为什么不能保证 159 个模块全部精确 1:1

- Shoreline `ModuleManager` 注册约 159 个模块；Hachimi 的 Module 继承树里约有 197 个叶子类，并且还有 HUD component、辅助模块和额外功能。
- Hachimi 大量字符串经过 DES + invokedynamic 解密，真实模块名称/描述在纯静态 classfile 中不可直接读取。
- 部分核心逻辑进入 native 方法，Java 层只保留事件入口或桥接方法。
- 多个 Shoreline 模块共享同一 Mixin accessor，例如 `hookSetY`、firework shooter accessor、projection matrix，因此单一 accessor 不能独立完成定名。
- CLIENT/HUD 架构差异最大：Hachimi 的 `h` 类别和 `class_808 / class_256` 一带明显存在额外 HUD/component 层，所以该类别低置信项尤其不应强行命名。

因此，本 README 把“可以直接重命名的锚点”和“仅供继续逆向的候选”明确分开。

