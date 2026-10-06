# Hachimi B4 (`hachimi_b4_pw_1006.jar`) Java + Native 类结构分析

> 本文档基于 `hachimi_b4_pw_1006.jar`、`jpi-mappings_pw.json` 与 `native_mapping.json` 的联合静态分析生成。重点是恢复“普通解压器看不到的 class”、建立映射，并按继承关系/API 调用推断每个 class 的职责。由于大量字符串采用 DES + `invokedynamic` 动态解密，无法从纯静态常量中可靠得到所有原始模块显示名，因此本文对这部分使用“结构角色 + 行为域 + 置信度”，避免凭空命名。

## 1. 结论摘要

- JAR ZIP 条目总数：**928**；其中解析到 **928 个有效 Java class**。
- 正常以 `.class` 命名、可直接看到的 class：**107**。
- 被伪装成重复目录项 `pw/hachimi/client/` 的隐藏 class：**821**。这些条目内部均以 `CAFEBABE` 开头，实际就是 class 文件。
- 隐藏 class 的真实内部名大量使用 `NUL (U+0000)` + 短名 + `LRM (U+200E)`，例如 `pw/hachimi/client/\u0000ln\u200E`。Windows/Unix 文件名都不能直接包含 NUL，所以即便恢复真实内部名，也不适合按原名落盘。
- `jpi-mappings_pw.json` 中的 `class_1 ... class_928` 与 JAR 中 928 个 class 可以 **一一对应**；因此最安全的导出文件名应使用 `class_N.class`，README 中同时保留真实内部名的转义形式。
- 保护层明显包含：DES/CBC 字符串加密、`MutableCallSite`/`MethodHandle`/`invokedynamic`、控制流扁平化、合成桥接方法，以及 `skidonion/...` 运行时调用。
- `NativeBridge` 还存在 Windows/JNA 本地层：会定位名为 PhantomShield 的已加载模块，验证特定构建，安装 512-byte key table，并通过 `VirtualProtect` 修改代码页后 `FlushInstructionCache`。
- `native_mapping.json` 记录的统一 native registrar 为 `skidonion.vLZkx.___.___(ILjava/lang/Class;)V`；共列出 **109 个 native 相关类**，其中 **108 个带 Registration ID**，合计 **464 个 native 方法签名**。
- 其中 `pw.hachimi.client.*` 有 **72 个**（**69 个**可直接映射到 `class_N`，**3 个**只出现在 native mapping），`skidonion.vLZkx.*` 有 **37 个**。
- 大量 native 注册发生在各类 `<clinit>()V`，说明本地绑定属于类初始化流程的一部分；因此仅看 Java 方法体会漏掉一批真正业务逻辑。

### 主要结构族

| 基类 | mapping | 直接子类数 | 静态推断 |
|---|---:|---:|---|
| `pw/hachimi/client/\u0000re\u200E` | `class_874` | 146 | 模块系统核心基类 |
| `pw/hachimi/client/\u0000nZ\u200E` | `class_919` | 149 | 事件系统核心基类 |
| `pw/hachimi/client/\u0000mZ\u200E` | `class_883` | 38 | Brigadier 命令基类 |
| `pw/hachimi/client/\u0000lQ\u200E` | `class_877` | 29 | 大型枚举基类/枚举常量专用匿名类宿主 |
| `pw/hachimi/client/\u0000ot\u200E` | `class_806` | 12 | 模块二级基类 |
| `pw/hachimi/client/\u0000kb\u200E` | `class_805` | 23 | 玩家筛选/目标选择模块二级基类 |
| `pw/hachimi/client/\u0000aU\u200E` | `class_810` | 11 | DrawContext 渲染事件基类 |
| `pw/hachimi/client/\u0000qt\u200E` | `class_908` | 9 | Setting/配置项基类 |

## 2. 为什么“无法解压缩的文件名”其实是 class

JAR 中有 821 个 ZIP central-directory 条目都叫 `pw/hachimi/client/`，名字以 `/` 结尾，普通 ZIP 工具自然把它们当成目录并相互覆盖；但这些条目的 `file_size` 非零，解压后的首 4 bytes 全是 `CA FE BA BE`。解析 class constant pool 后，可以读到真实 `this_class`。这些真实名称包含 U+0000，因此不能原样作为操作系统文件名。

建议使用以下安全命名策略：

```text
class_1.class        -> pw.hachimi.client.\u0000ln\u200E
class_2.class        -> pw.hachimi.client.\u0000fd\u200E
...
class_928.class      -> 对应 mapping 中 class_928 的真实内部名
```

其中 `hidden#NNN` 是本文为了定位原始 ZIP 中第 N 个伪目录 class 而添加的索引；它不是程序原始名称。

## 3. 核心组件分析

### 3.1 模块 / 事件 / Setting / 命令

- `class_874` / `\u0000re\u200E`：模块系统核心。构造器包含模块名称/描述/类别等形态的参数，并持有多个 `qt` Setting；其下直接继承约 146 个类，是主要功能模块族。
- `class_919` / `\u0000nZ\u200E`：事件基础对象。其下约 149 个事件子类；很多事件只携带一个 Minecraft 对象或少量 primitive 状态。
- `class_883` / `\u0000mZ\u200E`：命令基类，直接调用 Brigadier 的 `LiteralArgumentBuilder` / `RequiredArgumentBuilder` / suggestion API。
- `class_908` / `\u0000qt\u200E`：Setting/配置项基础对象，含 name/value/default/supplier，并具备 Gson JSON 读写接口。
- `class_910` / `\u0000jl\u200E`：配置项容器/注册表，维护 `qt` 集合并进行 JSON 导入导出。
- `class_872` / `\u0000nH\u200E`：带 `Runnable` 的动作型 Setting，形态接近按钮/执行动作配置。

### 3.2 混淆运行时

- `pw.hachimi.client.ac/ad/ae/af` 构成一组明显的保护运行时。`ae` 负责 64-bit 状态/密钥派生，`af` 负责反射字段/方法解析、CallSite/MethodHandle 与动态字符串解密。
- 大量业务 class 自带 `DES/CBC/PKCS5Padding`、`ISO-8859-1`、`MutableCallSite`，说明原始模块名称、描述、部分常量并不直接出现在 constant pool 的明文中。
- 许多 `pw.hachimi.client.a` ~ `z`、`aa`、`ab` 没有业务字段，只有一批无参静态方法，且引用隐藏类；它们更像初始化/注册分片，而不是独立业务模块。

### 3.3 NativeBridge / PhantomShield

`pw.hachimi.local.NativeBridge` 是 Windows/JNA 层桥接类。静态代码显示它通过 `kernel32` 获取模块与符号地址，检查 `JNI_OnLoad` 相对基址以确认 native build，读取 `native-key.bin`（要求 512 bytes），设置 native key state；随后用 `VirtualProtect` 临时放开页面写权限，写入补丁并调用 `FlushInstructionCache`。代码中的提示文字明确提到 `PhantomShield`、login entry 和 `Ultimate role check`。这意味着 JAR 的一部分授权/角色逻辑并不只在 Java 层。

### 3.4 Native 注册体系（来自 `native_mapping.json`）

`native_mapping.json` 给出统一 registrar：`skidonion.vLZkx.___.___(ILjava/lang/Class;)V`。除 registrar 本身外，记录中的类基本都在 `<clinit>()V` 调用注册流程，并以一个整数 `registrationId` 绑定到对应 native 实现。当前映射共有 **109 个 native 相关类 / 108 个 Registration ID / 464 个 native 方法**。

这使得之前的“Java 静态分析”可以进一步分成三层：

1. **Java 可见实现**：方法体能直接反编译，功能可以按字段、继承和 Minecraft API 推断。
2. **Java 壳 + native 实现**：类结构、参数/返回类型可见，但核心方法只有 native 签名；功能应优先根据参数类型、事件调用链、父类和 Registration ID 判断。
3. **native mapping 独有类**：映射中存在，但当前 928-class JAR/JPI workspace 中没有同名 class，不能强行绑定到 `class_N`。

特别重要的是，Setting/JSON 路径也被 native 化：`class_908 / \u0000qt\u200E`、`class_910 / \u0000jl\u200E`、`class_872 / \u0000nH\u200E` 等均有 Gson `JsonObject` 相关 native 方法。也就是说，配置序列化并非完全由 Java 层完成。

另一方面，多数模块类的 native 方法形如 `(... \u0000jx\u200E)V`、`(... \u0000jI\u200E)V`、`(... \u0000pH\u200E)V`、`(... \u0000pj\u200E)V`，很像事件回调/内部上下文入口；而直接接受 `Entity`、`BlockPos`、`ItemStack`、`Screen`、`Vec3d` 的方法则能提供更强的行为域证据。

### 3.5 `skidonion.vLZkx` native 运行时

`skidonion.vLZkx.*` 并不只是一个单独 registrar。native mapping 中有 **37 个**此包类。按公开方法形状可看出至少包含：

- 一套 **JSON tree / parser / writer 风格对象模型**：支持 object/array、primitive、`size()`、`isEmpty()`、迭代器、Reader/Writer、字符串与数值取值；这些类的方法大量 native 化。
- 一组 **AWT GUI / 键鼠事件辅助类**：出现 `MouseEvent`、`KeyEvent`、`ActionEvent`、`Runnable` / `Thread`。
- 一组 **字节/字符串编码或变换辅助类**：输入输出为 `byte[]` / `String` / integer state。

因此 `skidonion.vLZkx` 更像随保护方案一起打包的 native-backed runtime，而不是 Hachimi 单一业务模块。这里仅按接口形状归类，不把它武断命名成某个具体第三方库。

### 3.6 Native mapping 与 JPI mapping 的不一致

以下 3 个 `pw.hachimi.client.*` 类存在于 native mapping，但不在当前 `jpi-mappings_pw.json` 的 928 个 class 映射中：

- `pw.hachimi.client.\u0000gS\u200E` — Registration ID `38`，native 方法：`aoS\u200E(CSI)V`
- `pw.hachimi.client.\u0000lm\u200E` — Registration ID `70`，native 方法：`WQ\u200E(Lpw/hachimi/client/\u0000jx\u200E;)V`
- `pw.hachimi.client.\u0000ff\u200E` — Registration ID `61`，native 方法：`xq\u200E(BJ)V`, `xr\u200E(J)V`, `xs\u200E(J)V`, `xt\u200E(IBI)V`, `xu\u200E(J)Z`

这不应直接解释成“mapping 错误”。更稳妥的可能性包括：native 层运行时注入/生成、JAR 版本差异、映射生成阶段未捕获，或类只存在于另一加载阶段。本文将它们标成 `native-only / 未匹配`。

### 3.7 Satin / Shader

`pw.hachimi.satin.*` 基本是 shader 管理和 uniform 抽象：ManagedShaderEffect、ManagedCoreShader、Uniform1f~4f、Uniform1i~4i、UniformMat4、UniformFinder 等。`Checks` 额外包含 early-access/BETA 权限警告，不只是纯 shader 工具类。

## 4. 静态分析中能直接识别的数据对象

| mapping | 原始类名 | 明文组件/字段提示 | 推断 |
|---|---|---|---|
| `class_33` | `pw.hachimi.client.\u0000qy\u200E` | `pos;needPlaceSide` | 数据载体 |
| `class_215` | `pw.hachimi.client.\u0000nR\u200E` | `name;pos;color;timer` | 数据载体 |
| `class_657` | `pw.hachimi.client.\u0000kx\u200E` | `entity;ticks;playerPos;offset;speed` | 数据载体 |
| `class_44` | `pw.hachimi.client.\u0000rh\u200E` | `textureWidth;textureHeight;width;height;value;owner` | 纹理尺寸/数值归属数据 |
| `class_227` | `pw.hachimi.client.\u0000li\u200E` | `pos;time` | 数据载体 |
| `class_590` | `pw.hachimi.client.\u0000bV\u200E` | `pos;damage` | 数据载体 |
| `class_38` | `pw.hachimi.client.\u0000aw\u200E` | `value;key` | 数据载体 |
| `class_461` | `pw.hachimi.client.\u0000gK\u200E` | `shulker;compact;color;slot;stacks` | Shulker/容器预览数据 |
| `class_681` | `pw.hachimi.client.\u0000fn\u200E` | `x;y;r;g;b;glyph;matrix4f;a;mode` | 字体/字形渲染数据 |
| `class_87` | `pw.hachimi.client.\u0000pD\u200E` | `start;end` | 区间/线段数据 |
| `class_299` | `pw.hachimi.client.\u0000nA\u200E` | `lastPopTime;pops` | 玩家 Totem pop 计数/时间数据 |
| `class_706` | `pw.hachimi.client.\u0000cJ\u200E` | `pos;time` | 数据载体 |
| `class_167` | `pw.hachimi.satin.ManagedShaderEffect` | `Method pw/hachimi/satin/ManagedShaderEffect.findUniform1i(Ljava/lang/String;)Lpw/hachimi/satin/uniform/Uniform1i; is abstract` | 数据载体 |
| `class_585` | `pw.hachimi.client.\u0000nL\u200E` | `damageData;attackTarget;damage;selfDamage;blockPos;antiSurround;support` | 战斗伤害/放置候选数据 |
| `class_627` | `pw.hachimi.client.\u0000ml\u200E` | `pos;soundEvent` | 位置 + 声音事件数据 |
| `class_213` | `pw.hachimi.client.\u0000L\u200E` | `position;timeMS;teleportID` | 位置/传送跟踪数据 |

## 5. 全部 928 个 class 功能索引

说明：

- “原始类名”用 `\u0000` / `\u200E` 转义，避免 Markdown/编辑器吞掉控制字符。
- “位置”=`visible` 表示 ZIP 中存在正常 `.class` 文件名；`hidden#NNN` 表示原 ZIP 中第 N 个伪装目录 class。
- “功能”若写“客户端功能模块实现”或“事件对象”，是由继承主干直接判断；后面的行为域来自 Minecraft/JDK API 引用。
- “中-低/低”不代表 class 不重要，只表示字符串加密/控制流混淆让静态命名证据不足。

| mapping | 原始类名 | 位置 | 父类 | 字段/方法 | Native | 功能推断 | 置信度 |
|---:|---|---|---|---:|---|---|---|
| `class_1` | `pw.hachimi.client.\u0000ln\u200E` | `hidden#440` | `java.lang.Object` | 1/2 | — | 内部辅助类/值对象/适配器；精确业务名受混淆影响 | 低 |
| `class_2` | `pw.hachimi.client.\u0000fd\u200E` | `hidden#516` | `class_919 / pw.hachimi.client.\u0000nZ\u200E` | 2/3 | — | 事件对象/事件上下文；领域：具体事件类型需运行时字符串/调用点确认 | 中-低 |
| `class_3` | `pw.hachimi.client.\u0000aD\u200E` | `hidden#587` | `java.lang.Object` | 1/2 | — | 内部辅助类/值对象/适配器；精确业务名受混淆影响 | 低 |
| `class_4` | `pw.hachimi.client.\u0000hZ\u200E` | `hidden#277` | `java.lang.Object` | 1/2 | — | 内部辅助类/值对象/适配器；精确业务名受混淆影响 | 低 |
| `class_5` | `pw.hachimi.client.\u0000ng\u200E` | `hidden#349` | `class_15 / pw.hachimi.client.\u0000bR\u200E` | 10/17 | — | 内部辅助/管理类；主要涉及：渲染/HUD | 中 |
| `class_6` | `pw.hachimi.client.\u0000np\u200E` | `hidden#467` | `class_13 / pw.hachimi.client.\u0000gl\u200E` | 5/12 | — | 内部辅助/管理类；主要涉及：渲染/HUD | 中 |
| `class_7` | `pw.hachimi.client.\u0000s\u200E` | `hidden#343` | `class_13 / pw.hachimi.client.\u0000gl\u200E` | 14/20 | — | 内部辅助/管理类；主要涉及：渲染/HUD | 中 |
| `class_8` | `pw.hachimi.client.\u0000dB\u200E` | `hidden#140` | `class_13 / pw.hachimi.client.\u0000gl\u200E` | 6/12 | — | 内部辅助/管理类；主要涉及：渲染/HUD | 中 |
| `class_9` | `pw.hachimi.client.\u0000hI\u200E` | `hidden#074` | `class_13 / pw.hachimi.client.\u0000gl\u200E` | 4/13 | — | 内部辅助/管理类；主要涉及：渲染/HUD | 中 |
| `class_10` | `pw.hachimi.client.\u0000fl\u200E` | `hidden#598` | `class_13 / pw.hachimi.client.\u0000gl\u200E` | 6/13 | — | 内部辅助/管理类；主要涉及：渲染/HUD | 中 |
| `class_11` | `pw.hachimi.client.\u0000cX\u200E` | `hidden#784` | `class_13 / pw.hachimi.client.\u0000gl\u200E` | 5/15 | — | 内部辅助/管理类；主要涉及：渲染/HUD | 中 |
| `class_12` | `pw.hachimi.client.\u0000ez\u200E` | `hidden#401` | `class_13 / pw.hachimi.client.\u0000gl\u200E` | 1/9 | — | 内部辅助/管理类；主要涉及：渲染/HUD | 中 |
| `class_13` | `pw.hachimi.client.\u0000gl\u200E` | `hidden#107` | `class_15 / pw.hachimi.client.\u0000bR\u200E` | 3/6 | — | 内部辅助/管理类；主要涉及：渲染/HUD | 中 |
| `class_14` | `pw.hachimi.client.\u0000oX\u200E` | `hidden#485` | `class_15 / pw.hachimi.client.\u0000bR\u200E` | 12/19 | — | 内部辅助/管理类；主要涉及：渲染/HUD | 中 |
| `class_15` | `pw.hachimi.client.\u0000bR\u200E` | `hidden#335` | `class_881 / pw.hachimi.client.\u0000eU\u200E` | 6/9 | — | 内部辅助/管理类；主要涉及：渲染/HUD | 中 |
| `class_16` | `pw.hachimi.client.\u0000pu\u200E` | `hidden#461` | `java.lang.Object` | 5/14 | — | 内部辅助/管理类；主要涉及：方块/世界交互 | 中 |
| `class_17` | `pw.hachimi.client.\u0000V\u200E` | `hidden#493` | `class_18 / pw.hachimi.client.\u0000fk\u200E` | 12/27 | ID 2 / 3 methods | 内部辅助/管理类；主要涉及：渲染/HUD | 中 |
| `class_18` | `pw.hachimi.client.\u0000fk\u200E` | `hidden#535` | `class_881 / pw.hachimi.client.\u0000eU\u200E` | 3/8 | — | 内部辅助/管理类；主要涉及：渲染/HUD | 中 |
| `class_19` | `pw.hachimi.client.\u0000kH\u200E` | `hidden#457` | `java.lang.Object` | 0/5 | — | 内部辅助类/值对象/适配器；精确业务名受混淆影响 | 低 |
| `class_20` | `pw.hachimi.client.\u0000hW\u200E` | `hidden#293` | `Screen` | 20/22 | — | Minecraft GUI Screen/界面实现 | 高 |
| `class_21` | `pw.hachimi.client.\u0000fZ\u200E` | `hidden#341` | `java.lang.Object` | 2/13 | — | 内部辅助/管理类；主要涉及：实体/战斗、移动/玩家状态 | 中 |
| `class_22` | `pw.hachimi.client.\u0000kA\u200E` | `hidden#366` | `java.lang.Object` | 2/4 | — | 内部辅助类/值对象/适配器；精确业务名受混淆影响 | 低 |
| `class_23` | `pw.hachimi.client.\u0000mh\u200E` | `hidden#743` | `class_919 / pw.hachimi.client.\u0000nZ\u200E` | 3/5 | — | 事件对象/事件上下文；领域：方块/世界交互 | 中 |
| `class_24` | `pw.hachimi.client.\u0000oT\u200E` | `hidden#435` | `class_919 / pw.hachimi.client.\u0000nZ\u200E` | 2/3 | — | 事件对象/事件上下文；领域：具体事件类型需运行时字符串/调用点确认 | 中-低 |
| `class_25` | `pw.hachimi.client.\u0000gz\u200E` | `hidden#353` | `class_919 / pw.hachimi.client.\u0000nZ\u200E` | 2/3 | — | 事件对象/事件上下文；领域：具体事件类型需运行时字符串/调用点确认 | 中-低 |
| `class_26` | `pw.hachimi.client.\u0000ps\u200E` | `hidden#381` | `class_919 / pw.hachimi.client.\u0000nZ\u200E` | 4/6 | — | 事件对象/事件上下文；领域：实体/战斗 | 中 |
| `class_27` | `pw.hachimi.client.\u0000ns\u200E` | `hidden#436` | `class_644 / pw.hachimi.client.\u0000iQ\u200E` | 2/3 | — | 事件对象/事件上下文；领域：具体事件类型需运行时字符串/调用点确认 | 中-低 |
| `class_28` | `pw.hachimi.client.\u0000C\u200E` | `hidden#701` | `class_919 / pw.hachimi.client.\u0000nZ\u200E` | 2/3 | — | 事件对象/事件上下文；领域：具体事件类型需运行时字符串/调用点确认 | 中-低 |
| `class_29` | `pw.hachimi.client.\u0000kS\u200E` | `hidden#522` | `class_919 / pw.hachimi.client.\u0000nZ\u200E` | 3/5 | — | 事件对象/事件上下文；领域：物品/背包 | 中 |
| `class_30` | `pw.hachimi.client.\u0000pQ\u200E` | `hidden#787` | `class_919 / pw.hachimi.client.\u0000nZ\u200E` | 3/5 | — | 事件对象/事件上下文；领域：具体事件类型需运行时字符串/调用点确认 | 中-低 |
| `class_31` | `pw.hachimi.client.\u0000gX\u200E` | `hidden#683` | `java.lang.Object` | 11/13 | — | 内部辅助类/值对象/适配器；精确业务名受混淆影响 | 低 |
| `class_32` | `pw.hachimi.client.\u0000oC\u200E` | `hidden#234` | `class_919 / pw.hachimi.client.\u0000nZ\u200E` | 2/3 | — | 事件对象/事件上下文；领域：具体事件类型需运行时字符串/调用点确认 | 中-低 |
| `class_33` | `pw.hachimi.client.\u0000qy\u200E` | `hidden#014` | `java.lang.Record` | 3/8 | — | 数据载体/Record-like；组件：pos、needPlaceSide | 高 |
| `class_34` | `pw.hachimi.client.\u0000iC\u200E` | `hidden#382` | `class_919 / pw.hachimi.client.\u0000nZ\u200E` | 2/3 | — | 事件对象/事件上下文；领域：具体事件类型需运行时字符串/调用点确认 | 中-低 |
| `class_35` | `pw.hachimi.client.\u0000cv\u200E` | `hidden#402` | `class_919 / pw.hachimi.client.\u0000nZ\u200E` | 2/3 | — | 事件对象/事件上下文；领域：具体事件类型需运行时字符串/调用点确认 | 中-低 |
| `class_36` | `pw.hachimi.client.\u0000hc\u200E` | `hidden#459` | `class_919 / pw.hachimi.client.\u0000nZ\u200E` | 3/5 | — | 事件对象/事件上下文；领域：具体事件类型需运行时字符串/调用点确认 | 中-低 |
| `class_37` | `pw.hachimi.client.\u0000rj\u200E` | `hidden#231` | `class_919 / pw.hachimi.client.\u0000nZ\u200E` | 2/3 | — | 事件对象/事件上下文；领域：具体事件类型需运行时字符串/调用点确认 | 中-低 |
| `class_38` | `pw.hachimi.client.\u0000aw\u200E` | `hidden#465` | `java.lang.Record` | 3/8 | — | 数据载体/Record-like；组件：value、key | 高 |
| `class_39` | `pw.hachimi.client.\u0000lW\u200E` | `hidden#189` | `java.lang.Object` | 4/9 | — | 内部辅助类/值对象/适配器；精确业务名受混淆影响 | 低 |
| `class_40` | `pw.hachimi.client.\u0000ij\u200E` | `hidden#098` | `class_507 / pw.hachimi.client.\u0000hA\u200E` | 1/8 | — | 内部辅助/管理类；主要涉及：方块/世界交互、实体/战斗 | 中 |
| `class_41` | `pw.hachimi.client.\u0000jG\u200E` | `hidden#053` | `java.lang.Object` | 5/20 | — | 内部辅助类/值对象/适配器；精确业务名受混淆影响 | 低 |
| `class_42` | `pw.hachimi.client.\u0000he\u200E` | `hidden#430` | `java.lang.Object` | 17/36 | ID 10 / 3 methods | 内部辅助/管理类；主要涉及：数据包/网络、方块/世界交互、实体/战斗、移动/玩家状态 | 中 |
| `class_43` | `pw.hachimi.client.\u0000dA\u200E` | `hidden#078` | `java.lang.Object` | 2/10 | — | 内部辅助/管理类；主要涉及：实体/战斗、移动/玩家状态 | 中 |
| `class_44` | `pw.hachimi.client.\u0000rh\u200E` | `hidden#262` | `java.lang.Record` | 7/12 | — | 数据载体/Record-like；组件：textureWidth、textureHeight、width、height、value、owner | 高 |
| `class_45` | `pw.hachimi.client.\u0000kt\u200E` | `hidden#105` | `java.lang.Object` | 6/10 | — | 内部辅助类/值对象/适配器；精确业务名受混淆影响 | 低 |
| `class_46` | `pw.hachimi.client.\u0000er\u200E` | `hidden#295` | `java.lang.Object` | 13/14 | — | 内部辅助/管理类；主要涉及：渲染/HUD、文件/IO | 中 |
| `class_47` | `pw.hachimi.client.\u0000fp\u200E` | `hidden#643` | `class_810 / pw.hachimi.client.\u0000aU\u200E` | 2/3 | — | 事件对象/事件上下文；领域：渲染/HUD | 中 |
| `class_48` | `pw.hachimi.client.\u0000ac\u200E` | `hidden#165` | `java.lang.Object` | 2/2 | — | 内部辅助类/值对象/适配器；精确业务名受混淆影响 | 低 |
| `class_49` | `pw.hachimi.client.\u0000dz\u200E` | `hidden#772` | `class_919 / pw.hachimi.client.\u0000nZ\u200E` | 2/3 | — | 事件对象/事件上下文；领域：具体事件类型需运行时字符串/调用点确认 | 中-低 |
| `class_50` | `pw.hachimi.client.\u0000iN\u200E` | `hidden#561` | `class_919 / pw.hachimi.client.\u0000nZ\u200E` | 3/5 | — | 事件对象/事件上下文；领域：实体/战斗 | 中 |
| `class_51` | `pw.hachimi.client.\u0000bM\u200E` | `hidden#271` | `java.lang.Object` | 5/25 | — | 内部辅助/管理类；主要涉及：渲染/HUD | 中 |
| `class_52` | `pw.hachimi.client.\u0000fo\u200E` | `hidden#582` | `class_919 / pw.hachimi.client.\u0000nZ\u200E` | 3/6 | — | 事件对象/事件上下文；领域：具体事件类型需运行时字符串/调用点确认 | 中-低 |
| `class_53` | `pw.hachimi.client.\u0000dS\u200E` | `hidden#345` | `class_919 / pw.hachimi.client.\u0000nZ\u200E` | 9/4 | — | 事件对象/事件上下文；领域：渲染/HUD | 中 |
| `class_54` | `pw.hachimi.client.\u0000ai\u200E` | `hidden#225` | `class_511 / pw.hachimi.client.\u0000aC\u200E` | 1/3 | — | 事件对象/事件上下文；领域：渲染/HUD、物品/背包 | 中 |
| `class_55` | `pw.hachimi.client.\u0000px\u200E` | `hidden#494` | `class_919 / pw.hachimi.client.\u0000nZ\u200E` | 3/4 | — | 事件对象/事件上下文；领域：方块/世界交互 | 中 |
| `class_56` | `pw.hachimi.client.\u0000lU\u200E` | `hidden#104` | `java.lang.Object` | 3/30 | — | 内部辅助/管理类；主要涉及：渲染/HUD、方块/世界交互、移动/玩家状态 | 中 |
| `class_57` | `pw.hachimi.client.\u0000aH\u200E` | `hidden#633` | `class_919 / pw.hachimi.client.\u0000nZ\u200E` | 2/3 | — | 事件对象/事件上下文；领域：具体事件类型需运行时字符串/调用点确认 | 中-低 |
| `class_58` | `pw.hachimi.client.\u0000cT\u200E` | `hidden#731` | `class_266 / pw.hachimi.client.\u0000pU\u200E` | 2/3 | — | 事件对象/事件上下文；领域：具体事件类型需运行时字符串/调用点确认 | 中-低 |
| `class_59` | `pw.hachimi.client.\u0000dx\u200E` | `hidden#800` | `class_266 / pw.hachimi.client.\u0000pU\u200E` | 3/5 | — | 事件对象/事件上下文；领域：具体事件类型需运行时字符串/调用点确认 | 中-低 |
| `class_60` | `pw.hachimi.client.\u0000hd\u200E` | `hidden#473` | `class_919 / pw.hachimi.client.\u0000nZ\u200E` | 5/10 | — | 事件对象/事件上下文；领域：具体事件类型需运行时字符串/调用点确认 | 中-低 |
| `class_61` | `pw.hachimi.client.\u0000fy\u200E` | `hidden#763` | `class_919 / pw.hachimi.client.\u0000nZ\u200E` | 5/8 | — | 事件对象/事件上下文；领域：实体/战斗、移动/玩家状态 | 中 |
| `class_62` | `pw.hachimi.client.\u0000qp\u200E` | `hidden#740` | `class_608 / pw.hachimi.client.\u0000iG\u200E` | 2/3 | — | 事件对象/事件上下文；领域：具体事件类型需运行时字符串/调用点确认 | 中-低 |
| `class_63` | `pw.hachimi.client.\u0000ip\u200E` | `hidden#106` | `class_919 / pw.hachimi.client.\u0000nZ\u200E` | 4/6 | — | 事件对象/事件上下文；领域：实体/战斗 | 中 |
| `class_64` | `pw.hachimi.client.\u0000hl\u200E` | `hidden#550` | `class_919 / pw.hachimi.client.\u0000nZ\u200E` | 5/7 | — | 事件对象/事件上下文；领域：移动/玩家状态 | 中 |
| `class_65` | `pw.hachimi.client.\u0000bd\u200E` | `hidden#617` | `java.lang.Object` | 8/19 | — | 内部辅助类/值对象/适配器；精确业务名受混淆影响 | 低 |
| `class_66` | `pw.hachimi.client.\u0000kB\u200E` | `hidden#325` | `java.lang.Object` | 6/23 | — | 内部辅助/管理类；主要涉及：实体/战斗、移动/玩家状态 | 中 |
| `class_67` | `pw.hachimi.client.\u0000fi\u200E` | `hidden#563` | `class_919 / pw.hachimi.client.\u0000nZ\u200E` | 3/5 | — | 事件对象/事件上下文；领域：具体事件类型需运行时字符串/调用点确认 | 中-低 |
| `class_68` | `pw.hachimi.client.\u0000kK\u200E` | `hidden#443` | `class_919 / pw.hachimi.client.\u0000nZ\u200E` | 3/6 | — | 事件对象/事件上下文；领域：具体事件类型需运行时字符串/调用点确认 | 中-低 |
| `class_69` | `pw.hachimi.client.\u0000o\u200E` | `hidden#384` | `class_919 / pw.hachimi.client.\u0000nZ\u200E` | 3/5 | — | 事件对象/事件上下文；领域：渲染/HUD | 中 |
| `class_70` | `pw.hachimi.client.mixin.ba` | `visible` | `java.lang.Object` | 0/1 | — | Mixin 注入/拦截类 | 高 |
| `class_71` | `pw.hachimi.client.mixin.an` | `visible` | `java.lang.Object` | 0/1 | — | Mixin 注入/拦截类 | 高 |
| `class_72` | `pw.hachimi.client.\u0000eo\u200E` | `hidden#251` | `class_919 / pw.hachimi.client.\u0000nZ\u200E` | 3/5 | — | 事件对象/事件上下文；领域：具体事件类型需运行时字符串/调用点确认 | 中-低 |
| `class_73` | `pw.hachimi.client.mixin.ah` | `visible` | `java.lang.Object` | 0/3 | — | Mixin 注入/拦截类 | 高 |
| `class_74` | `pw.hachimi.client.mixin.aN` | `visible` | `java.lang.Object` | 0/1 | — | Mixin 注入/拦截类 | 高 |
| `class_75` | `pw.hachimi.client.\u0000jF\u200E` | `hidden#041` | `java.lang.Object` | 0/5 | — | 内部辅助/管理类；主要涉及：实体/战斗 | 中 |
| `class_76` | `pw.hachimi.client.mixin.bB` | `visible` | `java.lang.Object` | 0/1 | — | Mixin 注入/拦截类 | 高 |
| `class_77` | `pw.hachimi.client.mixin.bO` | `visible` | `java.lang.Object` | 0/1 | — | Mixin 注入/拦截类 | 高 |
| `class_78` | `pw.hachimi.client.mixin.bf` | `visible` | `java.lang.Object` | 0/2 | — | Mixin 注入/拦截类 | 高 |
| `class_79` | `pw.hachimi.client.\u0000mA\u200E` | `hidden#318` | `class_80 / pw.hachimi.client.\u0000eu\u200E` | 2/3 | — | 事件对象/事件上下文；领域：具体事件类型需运行时字符串/调用点确认 | 中-低 |
| `class_80` | `pw.hachimi.client.\u0000eu\u200E` | `hidden#279` | `class_919 / pw.hachimi.client.\u0000nZ\u200E` | 2/3 | — | 事件对象/事件上下文；领域：具体事件类型需运行时字符串/调用点确认 | 中-低 |
| `class_81` | `pw.hachimi.client.\u0000mi\u200E` | `hidden#806` | `class_919 / pw.hachimi.client.\u0000nZ\u200E` | 3/5 | — | 事件对象/事件上下文；领域：实体/战斗 | 中 |
| `class_82` | `pw.hachimi.client.\u0000ra\u200E` | `hidden#110` | `class_919 / pw.hachimi.client.\u0000nZ\u200E` | 2/3 | — | 事件对象/事件上下文；领域：具体事件类型需运行时字符串/调用点确认 | 中-低 |
| `class_83` | `pw.hachimi.client.\u0000ee\u200E` | `hidden#138` | `class_919 / pw.hachimi.client.\u0000nZ\u200E` | 2/3 | — | 事件对象/事件上下文；领域：具体事件类型需运行时字符串/调用点确认 | 中-低 |
| `class_84` | `pw.hachimi.client.\u0000fD\u200E` | `hidden#065` | `class_823 / pw.hachimi.client.\u0000pj\u200E` | 1/3 | — | 事件对象/事件上下文；领域：数据包/网络 | 中 |
| `class_85` | `pw.hachimi.client.\u0000ck\u200E` | `hidden#252` | `class_919 / pw.hachimi.client.\u0000nZ\u200E` | 3/6 | — | 事件对象/事件上下文；领域：物品/背包 | 中 |
| `class_86` | `pw.hachimi.client.\u0000cE\u200E` | `hidden#618` | `class_919 / pw.hachimi.client.\u0000nZ\u200E` | 4/5 | — | 事件对象/事件上下文；领域：渲染/HUD | 中 |
| `class_87` | `pw.hachimi.client.\u0000pD\u200E` | `hidden#630` | `java.lang.Record` | 3/8 | — | 数据载体/Record-like；组件：start、end | 高 |
| `class_88` | `pw.hachimi.client.mixin.aE` | `visible` | `java.lang.Object` | 0/3 | — | Mixin 注入/拦截类 | 高 |
| `class_89` | `pw.hachimi.client.\u0000ku\u200E` | `hidden#116` | `class_919 / pw.hachimi.client.\u0000nZ\u200E` | 3/6 | — | 事件对象/事件上下文；领域：方块/世界交互 | 中 |
| `class_90` | `pw.hachimi.client.\u0000dO\u200E` | `hidden#297` | `class_919 / pw.hachimi.client.\u0000nZ\u200E` | 2/3 | — | 事件对象/事件上下文；领域：具体事件类型需运行时字符串/调用点确认 | 中-低 |
| `class_91` | `pw.hachimi.client.\u0000gd\u200E` | `hidden#024` | `class_919 / pw.hachimi.client.\u0000nZ\u200E` | 4/6 | — | 事件对象/事件上下文；领域：实体/战斗 | 中 |
| `class_92` | `pw.hachimi.client.\u0000mu\u200E` | `hidden#069` | `class_919 / pw.hachimi.client.\u0000nZ\u200E` | 2/6 | — | 事件对象/事件上下文；领域：实体/战斗 | 中 |
| `class_93` | `pw.hachimi.client.\u0000iD\u200E` | `hidden#460` | `class_919 / pw.hachimi.client.\u0000nZ\u200E` | 4/7 | — | 事件对象/事件上下文；领域：具体事件类型需运行时字符串/调用点确认 | 中-低 |
| `class_94` | `pw.hachimi.client.mixin.bT` | `visible` | `java.lang.Object` | 0/1 | — | Mixin 注入/拦截类 | 高 |
| `class_95` | `pw.hachimi.client.mixin.q` | `visible` | `java.lang.Object` | 0/2 | — | Mixin 注入/拦截类 | 高 |
| `class_96` | `pw.hachimi.client.mixin.aB` | `visible` | `java.lang.Object` | 0/1 | — | Mixin 注入/拦截类 | 高 |
| `class_97` | `pw.hachimi.client.mixin.bK` | `visible` | `java.lang.Object` | 0/1 | — | Mixin 注入/拦截类 | 高 |
| `class_98` | `pw.hachimi.client.mixin.aP` | `visible` | `java.lang.Object` | 0/2 | — | Mixin 注入/拦截类 | 高 |
| `class_99` | `pw.hachimi.client.mixin.bh` | `visible` | `java.lang.Object` | 0/1 | — | Mixin 注入/拦截类 | 高 |
| `class_100` | `pw.hachimi.client.\u0000ob\u200E` | `hidden#668` | `class_874 / pw.hachimi.client.\u0000re\u200E` | 5/8 | — | 客户端功能模块实现；行为域：具体功能名被字符串加密隐藏 | 中-低 |
| `class_101` | `pw.hachimi.satin.ManagedUniform` | `visible` | `class_102 / pw.hachimi.satin.ManagedUniformBase` | 13/19 | — | 统一变量（uniform）统一接口 | 高 |
| `class_102` | `pw.hachimi.satin.ManagedUniformBase` | `visible` | `java.lang.Object` | 1/6 | — | Uniform 基类 | 高 |
| `class_103` | `pw.hachimi.satin.uniform.UniformMat4` | `visible` | `java.lang.Object` | 0/3 | — | 4x4 矩阵 uniform 接口 | 高 |
| `class_104` | `pw.hachimi.satin.uniform.Uniform4f` | `visible` | `java.lang.Object` | 0/3 | — | 4 个 float 的 uniform 接口 | 高 |
| `class_105` | `pw.hachimi.satin.uniform.Uniform3f` | `visible` | `java.lang.Object` | 0/3 | — | 3 个 float 的 uniform 接口 | 高 |
| `class_106` | `pw.hachimi.satin.uniform.Uniform2f` | `visible` | `java.lang.Object` | 0/3 | — | 2 个 float 的 uniform 接口 | 高 |
| `class_107` | `pw.hachimi.satin.uniform.Uniform1f` | `visible` | `java.lang.Object` | 0/2 | — | 1 个 float 的 uniform 接口 | 高 |
| `class_108` | `pw.hachimi.satin.uniform.Uniform4i` | `visible` | `java.lang.Object` | 0/2 | — | 4 个 int 的 uniform 接口 | 高 |
| `class_109` | `pw.hachimi.satin.uniform.Uniform3i` | `visible` | `java.lang.Object` | 0/2 | — | 3 个 int 的 uniform 接口 | 高 |
| `class_110` | `pw.hachimi.satin.uniform.Uniform2i` | `visible` | `java.lang.Object` | 0/2 | — | 2 个 int 的 uniform 接口 | 高 |
| `class_111` | `pw.hachimi.satin.uniform.Uniform1i` | `visible` | `java.lang.Object` | 0/2 | — | 1 个 int 的 uniform 接口 | 高 |
| `class_112` | `pw.hachimi.client.\u0000aO\u200E` | `hidden#724` | `Screen` | 13/35 | — | Minecraft GUI Screen/界面实现 | 高 |
| `class_113` | `pw.hachimi.client.\u0000ki\u200E` | `hidden#039` | `LivingEntity` | 4/14 | — | 内部辅助/管理类；主要涉及：物品/背包、方块/世界交互、实体/战斗、移动/玩家状态 | 中 |
| `class_114` | `pw.hachimi.client.\u0000mn\u200E` | `hidden#050` | `java.lang.Object` | 13/29 | — | 内部辅助/管理类；主要涉及：渲染/HUD、文件/IO | 中 |
| `class_115` | `pw.hachimi.client.mixin.K` | `visible` | `java.lang.Object` | 0/2 | — | Mixin 注入/拦截类 | 高 |
| `class_116` | `pw.hachimi.client.mixin.R` | `visible` | `java.lang.Object` | 0/8 | — | Mixin 注入/拦截类 | 高 |
| `class_117` | `pw.hachimi.client.\u0000hO\u200E` | `hidden#181` | `class_919 / pw.hachimi.client.\u0000nZ\u200E` | 2/3 | — | 事件对象/事件上下文；领域：具体事件类型需运行时字符串/调用点确认 | 中-低 |
| `class_118` | `pw.hachimi.client.\u0000gy\u200E` | `hidden#278` | `class_919 / pw.hachimi.client.\u0000nZ\u200E` | 4/5 | — | 事件对象/事件上下文；领域：具体事件类型需运行时字符串/调用点确认 | 中-低 |
| `class_119` | `pw.hachimi.client.\u0000aK\u200E` | `hidden#669` | `java.lang.Object` | 0/3 | — | 内部辅助类/值对象/适配器；精确业务名受混淆影响 | 低 |
| `class_120` | `pw.hachimi.client.mixin.aI` | `visible` | `java.lang.Object` | 0/1 | — | Mixin 注入/拦截类 | 高 |
| `class_121` | `pw.hachimi.client.\u0000gC\u200E` | `hidden#447` | `java.lang.Object` | 0/3 | — | 内部辅助类/值对象/适配器；精确业务名受混淆影响 | 低 |
| `class_122` | `pw.hachimi.client.\u0000q\u200E` | `hidden#362` | `class_919 / pw.hachimi.client.\u0000nZ\u200E` | 3/5 | — | 事件对象/事件上下文；领域：具体事件类型需运行时字符串/调用点确认 | 中-低 |
| `class_123` | `pw.hachimi.client.\u0000H\u200E` | `hidden#661` | `java.lang.Object` | 0/4 | — | 内部辅助/管理类；主要涉及：物品/背包 | 中 |
| `class_124` | `pw.hachimi.client.\u0000mY\u200E` | `hidden#603` | `class_781 / pw.hachimi.client.\u0000pB\u200E` | 3/5 | — | 事件对象/事件上下文；领域：移动/玩家状态 | 中 |
| `class_125` | `pw.hachimi.client.\u0000dp\u200E` | `hidden#702` | `class_919 / pw.hachimi.client.\u0000nZ\u200E` | 2/3 | — | 事件对象/事件上下文；领域：具体事件类型需运行时字符串/调用点确认 | 中-低 |
| `class_126` | `pw.hachimi.client.mixin.f` | `visible` | `java.lang.Object` | 0/1 | — | Mixin 注入/拦截类 | 高 |
| `class_127` | `pw.hachimi.client.\u0000ig\u200E` | `hidden#011` | `class_919 / pw.hachimi.client.\u0000nZ\u200E` | 10/4 | — | 事件对象/事件上下文；领域：渲染/HUD、实体/战斗 | 中 |
| `class_128` | `pw.hachimi.client.\u0000mr\u200E` | `hidden#082` | `class_137 / pw.hachimi.client.\u0000eh\u200E` | 3/5 | — | 内部辅助类/值对象/适配器；精确业务名受混淆影响 | 低 |
| `class_129` | `pw.hachimi.client.\u0000oq\u200E` | `hidden#035` | `java.lang.Object` | 7/18 | — | 内部辅助/管理类；主要涉及：渲染/HUD | 中 |
| `class_130` | `pw.hachimi.client.\u0000ov\u200E` | `hidden#080` | `java.lang.Object` | 0/2 | — | 内部辅助类/值对象/适配器；精确业务名受混淆影响 | 低 |
| `class_131` | `pw.hachimi.client.mixin.ad` | `visible` | `java.lang.Object` | 0/1 | — | Mixin 注入/拦截类 | 高 |
| `class_132` | `pw.hachimi.client.\u0000oO\u200E` | `hidden#391` | `java.lang.Object` | 3/5 | — | 内部辅助类/值对象/适配器；精确业务名受混淆影响 | 低 |
| `class_133` | `pw.hachimi.client.\u0000co\u200E` | `hidden#312` | `class_919 / pw.hachimi.client.\u0000nZ\u200E` | 3/5 | — | 事件对象/事件上下文；领域：渲染/HUD | 中 |
| `class_134` | `pw.hachimi.client.\u0000kJ\u200E` | `hidden#428` | `java.lang.Object` | 4/7 | — | 内部辅助/管理类；主要涉及：方块/世界交互 | 中 |
| `class_135` | `pw.hachimi.client.\u0000gW\u200E` | `hidden#671` | `java.lang.Enum` | 9/10 | — | 枚举：模式/类别/状态选项（枚举常量名称多数已加密） | 中 |
| `class_136` | `pw.hachimi.client.\u0000aY\u200E` | `hidden#084` | `java.lang.Enum` | 5/9 | — | 枚举：模式/类别/状态选项（枚举常量名称多数已加密） | 中 |
| `class_137` | `pw.hachimi.client.\u0000eh\u200E` | `hidden#108` | `java.lang.Object` | 27/28 | — | 内部辅助/管理类；主要涉及：渲染/HUD | 中 |
| `class_138` | `pw.hachimi.client.\u0000km\u200E` | `hidden#085` | `java.lang.Object` | 3/7 | ID 9 / 1 methods | 内部辅助/管理类；主要涉及：渲染/HUD | 中 |
| `class_139` | `pw.hachimi.client.\u0000hH\u200E` | `hidden#063` | `java.lang.Object` | 15/66 | — | 内部辅助/管理类；主要涉及：渲染/HUD | 中 |
| `class_140` | `pw.hachimi.client.mixin.al` | `visible` | `java.lang.Object` | 0/1 | — | Mixin 注入/拦截类；涉及 Minecraft 类型：class_286 | 高 |
| `class_141` | `pw.hachimi.client.mixin.bN` | `visible` | `java.lang.Object` | 0/1 | — | Mixin 注入/拦截类 | 高 |
| `class_142` | `pw.hachimi.client.mixin.ap` | `visible` | `java.lang.Object` | 0/2 | — | Mixin 注入/拦截类 | 高 |
| `class_143` | `pw.hachimi.client.mixin.be` | `visible` | `java.lang.Object` | 0/1 | — | Mixin 注入/拦截类 | 高 |
| `class_144` | `pw.hachimi.client.\u0000mm\u200E` | `hidden#037` | `java.lang.Object` | 0/3 | — | 内部辅助类/值对象/适配器；精确业务名受混淆影响 | 低 |
| `class_145` | `pw.hachimi.client.\u0000jV\u200E` | `hidden#176` | `java.lang.Object` | 0/2 | — | 内部辅助类/值对象/适配器；精确业务名受混淆影响 | 低 |
| `class_146` | `pw.hachimi.client.mixin.br` | `visible` | `java.lang.Object` | 0/1 | — | Mixin 注入/拦截类 | 高 |
| `class_147` | `pw.hachimi.client.mixin.T` | `visible` | `java.lang.Object` | 0/1 | — | Mixin 注入/拦截类 | 高 |
| `class_148` | `pw.hachimi.client.mixin.aM` | `visible` | `java.lang.Object` | 0/2 | — | Mixin 注入/拦截类 | 高 |
| `class_149` | `pw.hachimi.client.mixin.bn` | `visible` | `java.lang.Object` | 0/1 | — | Mixin 注入/拦截类 | 高 |
| `class_150` | `pw.hachimi.client.mixin.bc` | `visible` | `java.lang.Object` | 0/1 | — | Mixin 注入/拦截类 | 高 |
| `class_151` | `pw.hachimi.client.\u0000kZ\u200E` | `hidden#662` | `class_349 / pw.hachimi.client.\u0000pf\u200E` | 5/11 | ID 50 / 1 methods | 内部辅助/管理类；主要涉及：JSON/配置、文件/IO | 中 |
| `class_152` | `pw.hachimi.client.\u0000cc\u200E` | `hidden#114` | `class_349 / pw.hachimi.client.\u0000pf\u200E` | 5/12 | ID 19 / 1 methods | 内部辅助/管理类；主要涉及：JSON/配置、文件/IO | 中 |
| `class_153` | `pw.hachimi.client.\u0000oU\u200E` | `hidden#496` | `java.lang.Enum` | 3/8 | — | 枚举：模式/类别/状态选项（枚举常量名称多数已加密） | 中 |
| `class_154` | `pw.hachimi.client.\u0000cI\u200E` | `hidden#646` | `class_349 / pw.hachimi.client.\u0000pf\u200E` | 5/11 | ID 43 / 1 methods | 内部辅助/管理类；主要涉及：JSON/配置、文件/IO | 中 |
| `class_155` | `pw.hachimi.client.\u0000cN\u200E` | `hidden#712` | `class_349 / pw.hachimi.client.\u0000pf\u200E` | 6/10 | ID 12 / 1 methods | 内部辅助/管理类；主要涉及：JSON/配置、文件/IO | 中 |
| `class_156` | `pw.hachimi.client.\u0000ft\u200E` | `hidden#699` | `class_349 / pw.hachimi.client.\u0000pf\u200E` | 5/13 | ID 28 / 1 methods | 内部辅助/管理类；主要涉及：JSON/配置、文件/IO | 中 |
| `class_157` | `pw.hachimi.client.\u0000oY\u200E` | `hidden#545` | `java.lang.Object` | 13/32 | ID 65 / 5 methods | 内部辅助/管理类；主要涉及：文件/IO | 中 |
| `class_158` | `pw.hachimi.client.\u0000ia\u200E` | `hidden#798` | `java.lang.Object` | 3/7 | — | 内部辅助类/值对象/适配器；精确业务名受混淆影响 | 低 |
| `class_159` | `pw.hachimi.client.\u0000mK\u200E` | `hidden#393` | `java.lang.Object` | 5/22 | — | 内部辅助/管理类；主要涉及：渲染/HUD、移动/玩家状态 | 中 |
| `class_160` | `pw.hachimi.client.\u0000mL\u200E` | `hidden#455` | `java.lang.Object` | 5/14 | — | 内部辅助/管理类；主要涉及：JSON/配置、HTTP/网络 | 中 |
| `class_161` | `pw.hachimi.client.\u0000nm\u200E` | `hidden#376` | `class_919 / pw.hachimi.client.\u0000nZ\u200E` | 3/6 | — | 事件对象/事件上下文；领域：物品/背包 | 中 |
| `class_162` | `pw.hachimi.client.\u0000cC\u200E` | `hidden#543` | `java.lang.Object` | 4/8 | — | 内部辅助类/值对象/适配器；精确业务名受混淆影响 | 低 |
| `class_163` | `pw.hachimi.client.mixin.s` | `visible` | `java.lang.Object` | 0/1 | — | Mixin 注入/拦截类 | 高 |
| `class_164` | `pw.hachimi.satin.ResettableManagedShaderEffect` | `visible` | `class_165 / pw.hachimi.satin.ResettableManagedShaderBase` | 3/21 | — | 可重置/重载的 ManagedShaderEffect 实现 | 高 |
| `class_165` | `pw.hachimi.satin.ResettableManagedShaderBase` | `visible` | `java.lang.Object` | 6/34 | — | 可重置 Shader 基类 | 高 |
| `class_166` | `pw.hachimi.satin.ManagedCoreShader` | `visible` | `java.lang.Object` | 0/3 | — | 核心 Shader 管理接口/封装 | 高 |
| `class_167` | `pw.hachimi.satin.ManagedShaderEffect` | `visible` | `java.lang.Object` | 0/12 | — | Managed Shader Effect 接口 | 高 |
| `class_168` | `pw.hachimi.satin.uniform.UniformFinder` | `visible` | `java.lang.Object` | 0/11 | — | Uniform 查找接口 | 高 |
| `class_169` | `pw.hachimi.satin.ReloadableShaderEffectManager` | `visible` | `java.lang.Object` | 2/14 | — | 可重载屏幕后处理 Shader Effect 管理器 | 高 |
| `class_170` | `pw.hachimi.satin.ShaderEffectManager` | `visible` | `java.lang.Object` | 0/7 | — | Shader Effect 管理接口 | 高 |
| `class_171` | `pw.hachimi.client.\u0000pP\u200E` | `hidden#777` | `net.minecraft.class_276` | 2/4 | — | 内部辅助/管理类；主要涉及：渲染/HUD | 中 |
| `class_172` | `pw.hachimi.client.mixin.j` | `visible` | `java.lang.Object` | 0/2 | — | Mixin 注入/拦截类 | 高 |
| `class_173` | `pw.hachimi.client.\u0000kU\u200E` | `hidden#595` | `java.lang.Object` | 11/19 | — | 内部辅助/管理类；主要涉及：渲染/HUD | 中 |
| `class_174` | `pw.hachimi.client.\u0000bQ\u200E` | `hidden#321` | `java.lang.Object` | 12/12 | — | 内部辅助类/值对象/适配器；精确业务名受混淆影响 | 低 |
| `class_175` | `pw.hachimi.client.\u0000iP\u200E` | `hidden#533` | `java.lang.Object` | 5/13 | — | 自定义 Brigadier 命令参数解析器 | 高 |
| `class_176` | `pw.hachimi.client.\u0000y\u200E` | `hidden#049` | `class_919 / pw.hachimi.client.\u0000nZ\u200E` | 8/10 | — | 事件对象/事件上下文；领域：实体/战斗 | 中 |
| `class_177` | `pw.hachimi.client.\u0000oI\u200E` | `hidden#363` | `java.lang.Object` | 6/14 | — | 自定义 Brigadier 命令参数解析器 | 高 |
| `class_178` | `pw.hachimi.client.\u0000mJ\u200E` | `hidden#378` | `class_919 / pw.hachimi.client.\u0000nZ\u200E` | 4/6 | — | 事件对象/事件上下文；领域：渲染/HUD | 中 |
| `class_179` | `pw.hachimi.client.\u0000pm\u200E` | `hidden#329` | `class_919 / pw.hachimi.client.\u0000nZ\u200E` | 5/7 | — | 事件对象/事件上下文；领域：方块/世界交互 | 中 |
| `class_180` | `pw.hachimi.client.\u0000fa\u200E` | `hidden#432` | `java.lang.Object` | 9/15 | — | 内部辅助类/值对象/适配器；精确业务名受混淆影响 | 低 |
| `class_181` | `pw.hachimi.client.\u0000qX\u200E` | `hidden#434` | `java.lang.Object` | 2/10 | — | 自定义 Brigadier 命令参数解析器 | 高 |
| `class_182` | `pw.hachimi.client.\u0000nc\u200E` | `hidden#303` | `class_919 / pw.hachimi.client.\u0000nZ\u200E` | 2/3 | — | 事件对象/事件上下文；领域：具体事件类型需运行时字符串/调用点确认 | 中-低 |
| `class_183` | `pw.hachimi.client.\u0000gs\u200E` | `hidden#260` | `class_883 / pw.hachimi.client.\u0000mZ\u200E` | 6/11 | — | Brigadier 客户端命令实现 | 高 |
| `class_184` | `pw.hachimi.client.\u0000qS\u200E` | `hidden#389` | `class_883 / pw.hachimi.client.\u0000mZ\u200E` | 5/11 | — | Brigadier 客户端命令实现 | 高 |
| `class_185` | `pw.hachimi.client.\u0000r\u200E` | `hidden#323` | `class_883 / pw.hachimi.client.\u0000mZ\u200E` | 5/11 | — | Brigadier 客户端命令实现 | 高 |
| `class_186` | `pw.hachimi.client.\u0000hU\u200E` | `hidden#215` | `class_883 / pw.hachimi.client.\u0000mZ\u200E` | 5/14 | — | Brigadier 客户端命令实现 | 高 |
| `class_187` | `pw.hachimi.client.\u0000kW\u200E` | `hidden#580` | `class_883 / pw.hachimi.client.\u0000mZ\u200E` | 5/15 | — | Brigadier 客户端命令实现 | 高 |
| `class_188` | `pw.hachimi.client.\u0000dD\u200E` | `hidden#115` | `class_883 / pw.hachimi.client.\u0000mZ\u200E` | 5/12 | — | Brigadier 客户端命令实现 | 高 |
| `class_189` | `pw.hachimi.client.\u0000ld\u200E` | `hidden#364` | `class_883 / pw.hachimi.client.\u0000mZ\u200E` | 5/10 | — | Brigadier 客户端命令实现 | 高 |
| `class_190` | `pw.hachimi.client.\u0000bP\u200E` | `hidden#360` | `class_883 / pw.hachimi.client.\u0000mZ\u200E` | 5/11 | — | Brigadier 客户端命令实现 | 高 |
| `class_191` | `pw.hachimi.client.\u0000aP\u200E` | `hidden#733` | `class_883 / pw.hachimi.client.\u0000mZ\u200E` | 5/9 | — | Brigadier 客户端命令实现 | 高 |
| `class_192` | `pw.hachimi.client.\u0000aV\u200E` | `hidden#048` | `class_883 / pw.hachimi.client.\u0000mZ\u200E` | 5/10 | — | Brigadier 客户端命令实现 | 高 |
| `class_193` | `pw.hachimi.client.\u0000ha\u200E` | `hidden#369` | `class_883 / pw.hachimi.client.\u0000mZ\u200E` | 5/9 | — | Brigadier 客户端命令实现 | 高 |
| `class_194` | `pw.hachimi.client.\u0000ko\u200E` | `hidden#096` | `class_883 / pw.hachimi.client.\u0000mZ\u200E` | 5/11 | — | Brigadier 客户端命令实现 | 高 |
| `class_195` | `pw.hachimi.client.\u0000nC\u200E` | `hidden#718` | `class_883 / pw.hachimi.client.\u0000mZ\u200E` | 5/14 | — | Brigadier 客户端命令实现 | 高 |
| `class_196` | `pw.hachimi.client.\u0000hC\u200E` | `hidden#055` | `class_883 / pw.hachimi.client.\u0000mZ\u200E` | 5/9 | — | Brigadier 客户端命令实现 | 高 |
| `class_197` | `pw.hachimi.client.\u0000iK\u200E` | `hidden#490` | `class_883 / pw.hachimi.client.\u0000mZ\u200E` | 5/10 | — | Brigadier 客户端命令实现 | 高 |
| `class_198` | `pw.hachimi.client.mixin.bb` | `visible` | `java.lang.Object` | 0/1 | — | Mixin 注入/拦截类 | 高 |
| `class_199` | `pw.hachimi.client.\u0000cB\u200E` | `hidden#530` | `class_883 / pw.hachimi.client.\u0000mZ\u200E` | 5/13 | — | Brigadier 客户端命令实现 | 高 |
| `class_200` | `pw.hachimi.client.\u0000iv\u200E` | `hidden#244` | `class_883 / pw.hachimi.client.\u0000mZ\u200E` | 5/12 | — | Brigadier 客户端命令实现 | 高 |
| `class_201` | `pw.hachimi.client.\u0000jz\u200E` | `hidden#625` | `class_883 / pw.hachimi.client.\u0000mZ\u200E` | 5/11 | — | Brigadier 客户端命令实现 | 高 |
| `class_202` | `pw.hachimi.client.\u0000pA\u200E` | `hidden#588` | `class_883 / pw.hachimi.client.\u0000mZ\u200E` | 5/11 | — | Brigadier 客户端命令实现 | 高 |
| `class_203` | `pw.hachimi.client.\u0000qG\u200E` | `hidden#232` | `class_883 / pw.hachimi.client.\u0000mZ\u200E` | 5/10 | — | Brigadier 客户端命令实现 | 高 |
| `class_204` | `pw.hachimi.client.\u0000lP\u200E` | `hidden#097` | `class_883 / pw.hachimi.client.\u0000mZ\u200E` | 5/10 | — | Brigadier 客户端命令实现 | 高 |
| `class_205` | `pw.hachimi.client.\u0000mD\u200E` | `hidden#350` | `class_883 / pw.hachimi.client.\u0000mZ\u200E` | 5/11 | — | Brigadier 客户端命令实现 | 高 |
| `class_206` | `pw.hachimi.client.\u0000pe\u200E` | `hidden#219` | `class_883 / pw.hachimi.client.\u0000mZ\u200E` | 5/13 | — | Brigadier 客户端命令实现 | 高 |
| `class_207` | `pw.hachimi.client.\u0000mR\u200E` | `hidden#510` | `java.lang.Object` | 9/59 | ID 46 / 2 methods | 内部辅助/管理类；主要涉及：命令 | 中 |
| `class_208` | `pw.hachimi.client.\u0000oa\u200E` | `hidden#649` | `java.lang.Object` | 2/7 | — | 内部辅助类/值对象/适配器；精确业务名受混淆影响 | 低 |
| `class_209` | `pw.hachimi.client.\u0000mI\u200E` | `hidden#420` | `java.lang.Object` | 4/11 | — | 内部辅助/管理类；主要涉及：数据包/网络、实体/战斗 | 中 |
| `class_210` | `pw.hachimi.client.\u0000mV\u200E` | `hidden#557` | `java.lang.Object` | 5/22 | — | 内部辅助/管理类；主要涉及：方块/世界交互、实体/战斗、移动/玩家状态 | 中 |
| `class_211` | `pw.hachimi.client.\u0000fQ\u200E` | `hidden#217` | `java.lang.Object` | 4/8 | — | 内部辅助/管理类；主要涉及：方块/世界交互、移动/玩家状态 | 中 |
| `class_212` | `pw.hachimi.client.\u0000eO\u200E` | `hidden#620` | `java.lang.Object` | 3/13 | — | 内部辅助/管理类；主要涉及：数据包/网络、实体/战斗 | 中 |
| `class_213` | `pw.hachimi.client.\u0000L\u200E` | `hidden#812` | `java.lang.Record` | 5/10 | — | 数据载体/Record-like；组件：position、timeMS、teleportID | 高 |
| `class_214` | `pw.hachimi.client.\u0000hy\u200E` | `hidden#707` | `java.lang.Object` | 8/15 | — | 内部辅助/管理类；主要涉及：移动/玩家状态 | 中 |
| `class_215` | `pw.hachimi.client.\u0000nR\u200E` | `hidden#017` | `java.lang.Record` | 5/10 | — | 数据载体/Record-like；组件：name、pos、color、timer | 高 |
| `class_216` | `pw.hachimi.client.\u0000nr\u200E` | `hidden#424` | `class_781 / pw.hachimi.client.\u0000pB\u200E` | 4/7 | — | 事件对象/事件上下文；领域：具体事件类型需运行时字符串/调用点确认 | 中-低 |
| `class_217` | `pw.hachimi.client.\u0000jf\u200E` | `hidden#395` | `class_919 / pw.hachimi.client.\u0000nZ\u200E` | 3/6 | — | 事件对象/事件上下文；领域：具体事件类型需运行时字符串/调用点确认 | 中-低 |
| `class_218` | `pw.hachimi.client.\u0000dw\u200E` | `hidden#738` | `class_919 / pw.hachimi.client.\u0000nZ\u200E` | 5/6 | — | 事件对象/事件上下文；领域：实体/战斗、移动/玩家状态 | 中 |
| `class_219` | `pw.hachimi.client.\u0000jx\u200E` | `hidden#651` | `class_919 / pw.hachimi.client.\u0000nZ\u200E` | 2/3 | — | 事件对象/事件上下文；领域：具体事件类型需运行时字符串/调用点确认 | 中-低 |
| `class_220` | `pw.hachimi.client.\u0000lM\u200E` | `hidden#020` | `class_781 / pw.hachimi.client.\u0000pB\u200E` | 3/4 | — | 事件对象/事件上下文；领域：具体事件类型需运行时字符串/调用点确认 | 中-低 |
| `class_221` | `pw.hachimi.client.\u0000if\u200E` | `hidden#054` | `class_919 / pw.hachimi.client.\u0000nZ\u200E` | 4/6 | — | 事件对象/事件上下文；领域：移动/玩家状态 | 中 |
| `class_222` | `pw.hachimi.client.\u0000iz\u200E` | `hidden#292` | `java.lang.Object` | 15/30 | ID 54 / 4 methods | 内部辅助/管理类；主要涉及：数据包/网络、移动/玩家状态 | 中 |
| `class_223` | `pw.hachimi.client.mixin.aC` | `visible` | `java.lang.Object` | 0/6 | — | Mixin 注入/拦截类 | 高 |
| `class_224` | `pw.hachimi.client.\u0000bD\u200E` | `hidden#166` | `class_919 / pw.hachimi.client.\u0000nZ\u200E` | 3/4 | — | 事件对象/事件上下文；领域：实体/战斗 | 中 |
| `class_225` | `pw.hachimi.client.\u0000aS\u200E` | `hidden#769` | `java.lang.Object` | 1/3 | — | 内部辅助类/值对象/适配器；精确业务名受混淆影响 | 低 |
| `class_226` | `pw.hachimi.client.\u0000hJ\u200E` | `hidden#136` | `java.lang.Object` | 4/39 | — | 内部辅助/管理类；主要涉及：数据包/网络、物品/背包 | 中 |
| `class_227` | `pw.hachimi.client.\u0000li\u200E` | `hidden#379` | `java.lang.Record` | 3/8 | — | 数据载体/Record-like；组件：pos、time | 高 |
| `class_228` | `pw.hachimi.client.\u0000eQ\u200E` | `hidden#703` | `java.lang.Object` | 5/17 | — | 内部辅助类/值对象/适配器；精确业务名受混淆影响 | 低 |
| `class_229` | `pw.hachimi.client.\u0000oJ\u200E` | `hidden#331` | `java.lang.Object` | 3/19 | — | 内部辅助类/值对象/适配器；精确业务名受混淆影响 | 低 |
| `class_230` | `pw.hachimi.client.\u0000nD\u200E` | `hidden#681` | `class_919 / pw.hachimi.client.\u0000nZ\u200E` | 3/6 | — | 事件对象/事件上下文；领域：具体事件类型需运行时字符串/调用点确认 | 中-低 |
| `class_231` | `pw.hachimi.client.\u0000lo\u200E` | `hidden#437` | `class_919 / pw.hachimi.client.\u0000nZ\u200E` | 2/3 | — | 事件对象/事件上下文；领域：具体事件类型需运行时字符串/调用点确认 | 中-低 |
| `class_232` | `pw.hachimi.client.\u0000hb\u200E` | `hidden#383` | `class_747 / pw.hachimi.client.\u0000in\u200E` | 31/15 | — | 内部辅助/管理类；主要涉及：渲染/HUD | 中 |
| `class_233` | `pw.hachimi.client.\u0000oV\u200E` | `hidden#508` | `class_808 / pw.hachimi.client.\u0000mQ\u200E` | 37/17 | — | 客户端功能模块实现；行为域：渲染/HUD、物品/背包 | 中 |
| `class_234` | `pw.hachimi.client.\u0000dU\u200E` | `hidden#319` | `class_256 / pw.hachimi.client.\u0000gr\u200E` | 7/13 | — | 客户端功能模块实现；行为域：渲染/HUD、物品/背包 | 中 |
| `class_235` | `pw.hachimi.client.\u0000me\u200E` | `hidden#753` | `class_808 / pw.hachimi.client.\u0000mQ\u200E` | 15/15 | — | 客户端功能模块实现；行为域：渲染/HUD、实体/战斗 | 中 |
| `class_236` | `pw.hachimi.client.\u0000cb\u200E` | `hidden#150` | `class_808 / pw.hachimi.client.\u0000mQ\u200E` | 8/13 | — | 客户端功能模块实现；行为域：渲染/HUD | 中 |
| `class_237` | `pw.hachimi.client.\u0000cl\u200E` | `hidden#208` | `class_256 / pw.hachimi.client.\u0000gr\u200E` | 5/9 | — | 客户端功能模块实现；行为域：方块/世界交互 | 中 |
| `class_238` | `pw.hachimi.client.\u0000gp\u200E` | `hidden#152` | `class_256 / pw.hachimi.client.\u0000gr\u200E` | 4/8 | — | 客户端功能模块实现；行为域：具体功能名被字符串加密隐藏 | 中-低 |
| `class_239` | `pw.hachimi.client.\u0000qr\u200E` | `hidden#814` | `class_256 / pw.hachimi.client.\u0000gr\u200E` | 5/9 | — | 客户端功能模块实现；行为域：具体功能名被字符串加密隐藏 | 中-低 |
| `class_240` | `pw.hachimi.client.\u0000ka\u200E` | `hidden#755` | `class_256 / pw.hachimi.client.\u0000gr\u200E` | 4/8 | — | 客户端功能模块实现；行为域：具体功能名被字符串加密隐藏 | 中-低 |
| `class_241` | `pw.hachimi.client.\u0000id\u200E` | `hidden#792` | `class_808 / pw.hachimi.client.\u0000mQ\u200E` | 13/14 | — | 客户端功能模块实现；行为域：渲染/HUD | 中 |
| `class_242` | `pw.hachimi.client.\u0000ce\u200E` | `hidden#191` | `class_256 / pw.hachimi.client.\u0000gr\u200E` | 4/9 | — | 客户端功能模块实现；行为域：具体功能名被字符串加密隐藏 | 中-低 |
| `class_243` | `pw.hachimi.client.\u0000at\u200E` | `hidden#372` | `java.lang.Enum` | 6/8 | — | 枚举：模式/类别/状态选项（枚举常量名称多数已加密） | 中 |
| `class_244` | `pw.hachimi.client.\u0000lZ\u200E` | `hidden#174` | `class_808 / pw.hachimi.client.\u0000mQ\u200E` | 16/18 | — | 客户端功能模块实现；行为域：渲染/HUD | 中 |
| `class_245` | `pw.hachimi.client.\u0000bH\u200E` | `hidden#254` | `class_808 / pw.hachimi.client.\u0000mQ\u200E` | 6/13 | — | 客户端功能模块实现；行为域：渲染/HUD、物品/背包、实体/战斗 | 中 |
| `class_246` | `pw.hachimi.client.\u0000ej\u200E` | `hidden#183` | `class_808 / pw.hachimi.client.\u0000mQ\u200E` | 2/8 | — | 客户端功能模块实现；行为域：渲染/HUD、物品/背包 | 中 |
| `class_247` | `pw.hachimi.client.\u0000aM\u200E` | `hidden#750` | `class_256 / pw.hachimi.client.\u0000gr\u200E` | 5/9 | — | 客户端功能模块实现；行为域：具体功能名被字符串加密隐藏 | 中-低 |
| `class_248` | `pw.hachimi.client.\u0000mv\u200E` | `hidden#128` | `java.lang.Enum` | 5/8 | — | 枚举：模式/类别/状态选项（枚举常量名称多数已加密） | 中 |
| `class_249` | `pw.hachimi.client.\u0000lj\u200E` | `hidden#394` | `java.lang.Enum` | 5/8 | — | 枚举：模式/类别/状态选项（枚举常量名称多数已加密） | 中 |
| `class_250` | `pw.hachimi.client.\u0000iO\u200E` | `hidden#524` | `java.lang.Enum` | 4/8 | — | 枚举：模式/类别/状态选项（枚举常量名称多数已加密） | 中 |
| `class_251` | `pw.hachimi.client.\u0000kh\u200E` | `hidden#790` | `class_808 / pw.hachimi.client.\u0000mQ\u200E` | 17/22 | — | 客户端功能模块实现；行为域：渲染/HUD | 中 |
| `class_252` | `pw.hachimi.client.\u0000qR\u200E` | `hidden#374` | `class_256 / pw.hachimi.client.\u0000gr\u200E` | 11/9 | — | 客户端功能模块实现；行为域：具体功能名被字符串加密隐藏 | 中-低 |
| `class_253` | `pw.hachimi.client.\u0000gY\u200E` | `hidden#745` | `class_256 / pw.hachimi.client.\u0000gr\u200E` | 4/9 | — | 客户端功能模块实现；行为域：方块/世界交互 | 中 |
| `class_254` | `pw.hachimi.client.\u0000nV\u200E` | `hidden#070` | `java.lang.Enum` | 4/8 | — | 枚举：模式/类别/状态选项（枚举常量名称多数已加密） | 中 |
| `class_255` | `pw.hachimi.client.\u0000lb\u200E` | `hidden#290` | `class_256 / pw.hachimi.client.\u0000gr\u200E` | 11/9 | — | 客户端功能模块实现；行为域：移动/玩家状态 | 中 |
| `class_256` | `pw.hachimi.client.\u0000gr\u200E` | `hidden#246` | `class_808 / pw.hachimi.client.\u0000mQ\u200E` | 10/13 | — | 客户端功能模块实现；行为域：渲染/HUD | 中 |
| `class_257` | `pw.hachimi.client.\u0000hP\u200E` | `hidden#197` | `java.lang.Enum` | 6/8 | — | 枚举：模式/类别/状态选项（枚举常量名称多数已加密） | 中 |
| `class_258` | `pw.hachimi.client.\u0000ea\u200E` | `hidden#089` | `java.lang.Enum` | 4/8 | — | 枚举：模式/类别/状态选项（枚举常量名称多数已加密） | 中 |
| `class_259` | `pw.hachimi.client.\u0000pJ\u200E` | `hidden#751` | `class_883 / pw.hachimi.client.\u0000mZ\u200E` | 5/10 | — | Brigadier 客户端命令实现 | 高 |
| `class_260` | `pw.hachimi.client.\u0000fJ\u200E` | `hidden#121` | `java.lang.Enum` | 5/8 | — | 枚举：模式/类别/状态选项（枚举常量名称多数已加密） | 中 |
| `class_261` | `pw.hachimi.client.\u0000bU\u200E` | `hidden#373` | `class_919 / pw.hachimi.client.\u0000nZ\u200E` | 2/3 | — | 事件对象/事件上下文；领域：具体事件类型需运行时字符串/调用点确认 | 中-低 |
| `class_262` | `pw.hachimi.client.\u0000dE\u200E` | `hidden#125` | `java.lang.Enum` | 5/8 | — | 枚举：模式/类别/状态选项（枚举常量名称多数已加密） | 中 |
| `class_263` | `pw.hachimi.client.\u0000lc\u200E` | `hidden#351` | `class_919 / pw.hachimi.client.\u0000nZ\u200E` | 4/5 | — | 事件对象/事件上下文；领域：实体/战斗 | 中 |
| `class_264` | `pw.hachimi.client.\u0000aN\u200E` | `hidden#759` | `java.lang.Enum` | 5/8 | — | 枚举：模式/类别/状态选项（枚举常量名称多数已加密） | 中 |
| `class_265` | `pw.hachimi.client.\u0000im\u200E` | `hidden#072` | `java.lang.Enum` | 7/8 | — | 枚举：模式/类别/状态选项（枚举常量名称多数已加密） | 中 |
| `class_266` | `pw.hachimi.client.\u0000pU\u200E` | `hidden#001` | `class_919 / pw.hachimi.client.\u0000nZ\u200E` | 3/8 | — | 事件对象/事件上下文；领域：渲染/HUD、移动/玩家状态 | 中 |
| `class_267` | `pw.hachimi.client.\u0000lL\u200E` | `hidden#009` | `java.lang.Enum` | 7/8 | — | 枚举：模式/类别/状态选项（枚举常量名称多数已加密） | 中 |
| `class_268` | `pw.hachimi.client.\u0000cf\u200E` | `hidden#185` | `class_808 / pw.hachimi.client.\u0000mQ\u200E` | 54/48 | — | 客户端功能模块实现；行为域：渲染/HUD、物品/背包、方块/世界交互 | 中 |
| `class_269` | `pw.hachimi.client.\u0000bi\u200E` | `hidden#658` | `java.lang.Object` | 0/1 | — | 内部辅助类/值对象/适配器；精确业务名受混淆影响 | 低 |
| `class_270` | `pw.hachimi.client.\u0000dq\u200E` | `hidden#710` | `class_874 / pw.hachimi.client.\u0000re\u200E` | 14/22 | ID 23 / 3 methods | 客户端功能模块实现；行为域：物品/背包、移动/玩家状态 | 中 |
| `class_271` | `pw.hachimi.client.\u0000hf\u200E` | `hidden#445` | `class_874 / pw.hachimi.client.\u0000re\u200E` | 3/6 | — | 客户端功能模块实现；行为域：具体功能名被字符串加密隐藏 | 中-低 |
| `class_272` | `pw.hachimi.client.\u0000lf\u200E` | `hidden#337` | `class_805 / pw.hachimi.client.\u0000kb\u200E` | 14/15 | ID 53 / 1 methods | 客户端功能模块实现；行为域：方块/世界交互 | 中 |
| `class_273` | `pw.hachimi.client.\u0000oD\u200E` | `hidden#304` | `class_874 / pw.hachimi.client.\u0000re\u200E` | 28/21 | — | 客户端功能模块实现；行为域：数据包/网络、方块/世界交互 | 中 |
| `class_274` | `pw.hachimi.client.\u0000jE\u200E` | `hidden#793` | `class_874 / pw.hachimi.client.\u0000re\u200E` | 9/14 | ID 40 / 2 methods | 客户端功能模块实现；行为域：实体/战斗、移动/玩家状态 | 中 |
| `class_275` | `pw.hachimi.client.\u0000nG\u200E` | `hidden#760` | `java.lang.Enum` | 5/8 | — | 枚举：模式/类别/状态选项（枚举常量名称多数已加密） | 中 |
| `class_276` | `pw.hachimi.client.\u0000eS\u200E` | `hidden#673` | `java.lang.Enum` | 3/8 | — | 枚举：模式/类别/状态选项（枚举常量名称多数已加密） | 中 |
| `class_277` | `pw.hachimi.client.\u0000eG\u200E` | `hidden#541` | `class_874 / pw.hachimi.client.\u0000re\u200E` | 11/14 | — | 客户端功能模块实现；行为域：具体功能名被字符串加密隐藏 | 中-低 |
| `class_278` | `pw.hachimi.client.\u0000mS\u200E` | `hidden#476` | `class_874 / pw.hachimi.client.\u0000re\u200E` | 6/12 | — | 客户端功能模块实现；行为域：渲染/HUD、实体/战斗、移动/玩家状态 | 中 |
| `class_279` | `pw.hachimi.client.\u0000eg\u200E` | `hidden#112` | `class_810 / pw.hachimi.client.\u0000aU\u200E` | 2/3 | — | 事件对象/事件上下文；领域：渲染/HUD | 中 |
| `class_280` | `pw.hachimi.client.\u0000jk\u200E` | `hidden#501` | `java.lang.Enum` | 4/8 | — | 枚举：模式/类别/状态选项（枚举常量名称多数已加密） | 中 |
| `class_281` | `pw.hachimi.client.\u0000gM\u200E` | `hidden#599` | `class_870 / pw.hachimi.client.\u0000hR\u200E` | 2/5 | — | 内部辅助类/值对象/适配器；精确业务名受混淆影响 | 低 |
| `class_282` | `pw.hachimi.client.\u0000de\u200E` | `hidden#528` | `java.lang.Enum` | 5/8 | — | 枚举：模式/类别/状态选项（枚举常量名称多数已加密） | 中 |
| `class_283` | `pw.hachimi.client.\u0000qm\u200E` | `hidden#757` | `class_805 / pw.hachimi.client.\u0000kb\u200E` | 22/31 | — | 客户端功能模块实现；行为域：渲染/HUD、物品/背包、方块/世界交互 | 中 |
| `class_284` | `pw.hachimi.client.\u0000R\u200E` | `hidden#734` | `class_874 / pw.hachimi.client.\u0000re\u200E` | 8/14 | — | 客户端功能模块实现；行为域：数据包/网络、方块/世界交互、实体/战斗 | 中 |
| `class_285` | `pw.hachimi.client.\u0000cP\u200E` | `hidden#687` | `java.lang.Enum` | 5/8 | — | 枚举：模式/类别/状态选项（枚举常量名称多数已加密） | 中 |
| `class_286` | `pw.hachimi.client.\u0000mq\u200E` | `hidden#018` | `class_874 / pw.hachimi.client.\u0000re\u200E` | 12/19 | — | 客户端功能模块实现；行为域：数据包/网络、物品/背包、方块/世界交互 | 中 |
| `class_287` | `pw.hachimi.client.\u0000ei\u200E` | `hidden#122` | `class_874 / pw.hachimi.client.\u0000re\u200E` | 11/12 | — | 客户端功能模块实现；行为域：方块/世界交互、移动/玩家状态 | 中 |
| `class_288` | `pw.hachimi.client.\u0000dG\u200E` | `hidden#186` | `class_874 / pw.hachimi.client.\u0000re\u200E` | 12/16 | — | 客户端功能模块实现；行为域：数据包/网络、物品/背包、实体/战斗 | 中 |
| `class_289` | `pw.hachimi.client.\u0000qf\u200E` | `hidden#667` | `class_874 / pw.hachimi.client.\u0000re\u200E` | 6/12 | — | 客户端功能模块实现；行为域：数据包/网络 | 中 |
| `class_290` | `pw.hachimi.client.\u0000ap\u200E` | `hidden#322` | `java.lang.Enum` | 5/11 | — | 枚举：模式/类别/状态选项（枚举常量名称多数已加密） | 中 |
| `class_291` | `pw.hachimi.client.\u0000ht\u200E` | `hidden#653` | `java.lang.Enum` | 7/11 | — | 枚举：模式/类别/状态选项（枚举常量名称多数已加密） | 中 |
| `class_292` | `pw.hachimi.client.\u0000pV\u200E` | `hidden#015` | `java.lang.Enum` | 5/8 | — | 枚举：模式/类别/状态选项（枚举常量名称多数已加密） | 中 |
| `class_293` | `pw.hachimi.client.\u0000bG\u200E` | `hidden#241` | `class_919 / pw.hachimi.client.\u0000nZ\u200E` | 2/3 | — | 事件对象/事件上下文；领域：具体事件类型需运行时字符串/调用点确认 | 中-低 |
| `class_294` | `pw.hachimi.client.\u0000or\u200E` | `hidden#028` | `java.lang.Enum` | 4/8 | — | 枚举：模式/类别/状态选项（枚举常量名称多数已加密） | 中 |
| `class_295` | `pw.hachimi.client.\u0000iS\u200E` | `hidden#572` | `java.lang.Object` | 8/7 | — | 内部辅助/管理类；主要涉及：方块/世界交互、实体/战斗 | 中 |
| `class_296` | `pw.hachimi.client.\u0000oP\u200E` | `hidden#452` | `class_805 / pw.hachimi.client.\u0000kb\u200E` | 65/101 | ID 24 / 3 methods | 客户端功能模块实现；行为域：渲染/HUD、数据包/网络、物品/背包 | 中 |
| `class_297` | `pw.hachimi.client.\u0000lE\u200E` | `hidden#735` | `class_874 / pw.hachimi.client.\u0000re\u200E` | 8/14 | — | 客户端功能模块实现；行为域：数据包/网络、方块/世界交互 | 中 |
| `class_298` | `pw.hachimi.client.\u0000mP\u200E` | `hidden#438` | `class_874 / pw.hachimi.client.\u0000re\u200E` | 10/19 | — | 客户端功能模块实现；行为域：数据包/网络、物品/背包、方块/世界交互 | 中 |
| `class_299` | `pw.hachimi.client.\u0000nA\u200E` | `hidden#642` | `java.lang.Record` | 3/8 | — | 数据载体/Record-like；组件：lastPopTime、pops | 高 |
| `class_300` | `pw.hachimi.client.\u0000hp\u200E` | `hidden#596` | `class_806 / pw.hachimi.client.\u0000ot\u200E` | 12/18 | ID 41 / 1 methods | 客户端功能模块实现；行为域：数据包/网络、物品/背包 | 中 |
| `class_301` | `pw.hachimi.client.\u0000dY\u200E` | `hidden#370` | `class_874 / pw.hachimi.client.\u0000re\u200E` | 16/31 | — | 客户端功能模块实现；行为域：渲染/HUD、数据包/网络、实体/战斗 | 中 |
| `class_302` | `pw.hachimi.client.\u0000lO\u200E` | `hidden#083` | `class_919 / pw.hachimi.client.\u0000nZ\u200E` | 3/5 | — | 事件对象/事件上下文；领域：实体/战斗 | 中 |
| `class_303` | `pw.hachimi.client.\u0000m\u200E` | `hidden#415` | `class_874 / pw.hachimi.client.\u0000re\u200E` | 6/10 | — | 客户端功能模块实现；行为域：实体/战斗 | 中 |
| `class_304` | `pw.hachimi.client.\u0000kz\u200E` | `hidden#242` | `class_874 / pw.hachimi.client.\u0000re\u200E` | 7/18 | — | 客户端功能模块实现；行为域：渲染/HUD、物品/背包、方块/世界交互 | 中 |
| `class_305` | `pw.hachimi.client.\u0000bn\u200E` | `hidden#676` | `class_919 / pw.hachimi.client.\u0000nZ\u200E` | 3/5 | — | 事件对象/事件上下文；领域：具体事件类型需运行时字符串/调用点确认 | 中-低 |
| `class_306` | `pw.hachimi.client.\u0000ox\u200E` | `hidden#056` | `java.lang.Enum` | 5/8 | — | 枚举：模式/类别/状态选项（枚举常量名称多数已加密） | 中 |
| `class_307` | `pw.hachimi.client.\u0000B\u200E` | `hidden#118` | `java.lang.Enum` | 6/8 | — | 枚举：模式/类别/状态选项（枚举常量名称多数已加密） | 中 |
| `class_308` | `pw.hachimi.client.\u0000js\u200E` | `hidden#532` | `java.lang.Enum` | 4/8 | — | 枚举：模式/类别/状态选项（枚举常量名称多数已加密） | 中 |
| `class_309` | `pw.hachimi.client.\u0000bL\u200E` | `hidden#305` | `class_874 / pw.hachimi.client.\u0000re\u200E` | 18/29 | ID 32 / 1 methods | 客户端功能模块实现；行为域：渲染/HUD、数据包/网络、方块/世界交互 | 中 |
| `class_310` | `pw.hachimi.client.\u0000fw\u200E` | `hidden#684` | `java.lang.Enum` | 5/8 | — | 枚举：模式/类别/状态选项（枚举常量名称多数已加密） | 中 |
| `class_311` | `pw.hachimi.client.\u0000qj\u200E` | `hidden#715` | `class_312 / pw.hachimi.client.\u0000gk\u200E` | 6/7 | — | 内部辅助/管理类；主要涉及：渲染/HUD、实体/战斗、移动/玩家状态 | 中 |
| `class_312` | `pw.hachimi.client.\u0000gk\u200E` | `hidden#147` | `net.minecraft.class_745` | 4/12 | — | 内部辅助/管理类；主要涉及：实体/战斗 | 中 |
| `class_313` | `pw.hachimi.client.\u0000mt\u200E` | `hidden#057` | `java.lang.Enum` | 5/8 | — | 枚举：模式/类别/状态选项（枚举常量名称多数已加密） | 中 |
| `class_314` | `pw.hachimi.client.\u0000oQ\u200E` | `hidden#468` | `class_874 / pw.hachimi.client.\u0000re\u200E` | 21/18 | — | 客户端功能模块实现；行为域：渲染/HUD、实体/战斗、移动/玩家状态 | 中 |
| `class_315` | `pw.hachimi.client.\u0000nl\u200E` | `hidden#419` | `class_919 / pw.hachimi.client.\u0000nZ\u200E` | 2/3 | — | 事件对象/事件上下文；领域：具体事件类型需运行时字符串/调用点确认 | 中-低 |
| `class_316` | `pw.hachimi.client.\u0000na\u200E` | `hidden#221` | `class_874 / pw.hachimi.client.\u0000re\u200E` | 28/37 | — | 客户端功能模块实现；行为域：渲染/HUD、物品/背包 | 中 |
| `class_317` | `pw.hachimi.client.\u0000gg\u200E` | `hidden#064` | `class_874 / pw.hachimi.client.\u0000re\u200E` | 17/16 | — | 客户端功能模块实现；行为域：渲染/HUD、方块/世界交互 | 中 |
| `class_318` | `pw.hachimi.client.\u0000mp\u200E` | `hidden#003` | `class_919 / pw.hachimi.client.\u0000nZ\u200E` | 3/5 | — | 事件对象/事件上下文；领域：具体事件类型需运行时字符串/调用点确认 | 中-低 |
| `class_319` | `pw.hachimi.client.\u0000nq\u200E` | `hidden#463` | `java.lang.Enum` | 7/8 | — | 枚举：模式/类别/状态选项（枚举常量名称多数已加密） | 中 |
| `class_320` | `pw.hachimi.client.\u0000qi\u200E` | `hidden#704` | `class_874 / pw.hachimi.client.\u0000re\u200E` | 17/29 | — | 客户端功能模块实现；行为域：渲染/HUD、数据包/网络 | 中 |
| `class_321` | `pw.hachimi.client.\u0000jq\u200E` | `hidden#559` | `class_874 / pw.hachimi.client.\u0000re\u200E` | 13/25 | — | 客户端功能模块实现；行为域：渲染/HUD、方块/世界交互、实体/战斗 | 中 |
| `class_322` | `pw.hachimi.client.\u0000ib\u200E` | `hidden#809` | `class_874 / pw.hachimi.client.\u0000re\u200E` | 21/21 | — | 客户端功能模块实现；行为域：渲染/HUD、数据包/网络、实体/战斗 | 中 |
| `class_323` | `pw.hachimi.client.\u0000gH\u200E` | `hidden#552` | `class_919 / pw.hachimi.client.\u0000nZ\u200E` | 6/14 | — | 事件对象/事件上下文；领域：移动/玩家状态 | 中 |
| `class_324` | `pw.hachimi.client.\u0000bC\u200E` | `hidden#206` | `java.lang.Enum` | 6/8 | — | 枚举：模式/类别/状态选项（枚举常量名称多数已加密） | 中 |
| `class_325` | `pw.hachimi.client.\u0000kI\u200E` | `hidden#471` | `class_874 / pw.hachimi.client.\u0000re\u200E` | 6/12 | — | 客户端功能模块实现；行为域：物品/背包、方块/世界交互 | 中 |
| `class_326` | `pw.hachimi.client.\u0000p\u200E` | `hidden#352` | `class_919 / pw.hachimi.client.\u0000nZ\u200E` | 3/5 | — | 事件对象/事件上下文；领域：具体事件类型需运行时字符串/调用点确认 | 中-低 |
| `class_327` | `pw.hachimi.client.\u0000po\u200E` | `hidden#405` | `class_874 / pw.hachimi.client.\u0000re\u200E` | 11/23 | — | 客户端功能模块实现；行为域：渲染/HUD、实体/战斗、移动/玩家状态 | 中 |
| `class_328` | `pw.hachimi.client.\u0000mb\u200E` | `hidden#719` | `java.lang.Enum` | 5/8 | — | 枚举：模式/类别/状态选项（枚举常量名称多数已加密） | 中 |
| `class_329` | `pw.hachimi.client.\u0000al\u200E` | `hidden#272` | `class_883 / pw.hachimi.client.\u0000mZ\u200E` | 5/9 | — | Brigadier 客户端命令实现 | 高 |
| `class_330` | `pw.hachimi.client.\u0000eI\u200E` | `hidden#600` | `class_874 / pw.hachimi.client.\u0000re\u200E` | 11/12 | — | 客户端功能模块实现；行为域：渲染/HUD、方块/世界交互、移动/玩家状态 | 中 |
| `class_331` | `pw.hachimi.client.\u0000fv\u200E` | `hidden#672` | `java.lang.Enum` | 5/8 | — | 枚举：模式/类别/状态选项（枚举常量名称多数已加密） | 中 |
| `class_332` | `pw.hachimi.client.\u0000ky\u200E` | `hidden#175` | `class_874 / pw.hachimi.client.\u0000re\u200E` | 19/23 | — | 客户端功能模块实现；行为域：渲染/HUD | 中 |
| `class_333` | `pw.hachimi.client.\u0000ho\u200E` | `hidden#534` | `class_874 / pw.hachimi.client.\u0000re\u200E` | 7/14 | — | 客户端功能模块实现；行为域：数据包/网络、移动/玩家状态 | 中 |
| `class_334` | `pw.hachimi.client.\u0000eq\u200E` | `hidden#229` | `java.lang.Enum` | 5/8 | — | 枚举：模式/类别/状态选项（枚举常量名称多数已加密） | 中 |
| `class_335` | `pw.hachimi.client.\u0000dc\u200E` | `hidden#554` | `class_908 / pw.hachimi.client.\u0000qt\u200E` | 5/17 | ID 29 / 3 methods | 内部辅助类/值对象/适配器；精确业务名受混淆影响 | 低 |
| `class_336` | `pw.hachimi.client.\u0000rf\u200E` | `hidden#178` | `java.lang.Enum` | 6/8 | — | 枚举：模式/类别/状态选项（枚举常量名称多数已加密） | 中 |
| `class_337` | `pw.hachimi.client.\u0000mW\u200E` | `hidden#519` | `class_883 / pw.hachimi.client.\u0000mZ\u200E` | 5/12 | — | Brigadier 客户端命令实现 | 高 |
| `class_338` | `pw.hachimi.client.\u0000nQ\u200E` | `hidden#004` | `java.lang.Enum` | 8/10 | — | 枚举：模式/类别/状态选项（枚举常量名称多数已加密） | 中 |
| `class_339` | `pw.hachimi.client.\u0000cr\u200E` | `hidden#346` | `class_919 / pw.hachimi.client.\u0000nZ\u200E` | 5/7 | — | 事件对象/事件上下文；领域：实体/战斗 | 中 |
| `class_340` | `pw.hachimi.client.\u0000cn\u200E` | `hidden#298` | `class_874 / pw.hachimi.client.\u0000re\u200E` | 6/10 | — | 客户端功能模块实现；行为域：具体功能名被字符串加密隐藏 | 中-低 |
| `class_341` | `pw.hachimi.client.\u0000qQ\u200E` | `hidden#417` | `java.lang.Object` | 5/10 | ID 51 / 3 methods | 内部辅助类/值对象/适配器；精确业务名受混淆影响 | 低 |
| `class_342` | `pw.hachimi.client.\u0000aF\u200E` | `hidden#660` | `class_874 / pw.hachimi.client.\u0000re\u200E` | 25/23 | — | 客户端功能模块实现；行为域：渲染/HUD | 中 |
| `class_343` | `pw.hachimi.client.\u0000et\u200E` | `hidden#266` | `class_874 / pw.hachimi.client.\u0000re\u200E` | 23/32 | ID 34 / 1 methods | 客户端功能模块实现；行为域：数据包/网络、物品/背包、实体/战斗 | 中 |
| `class_344` | `pw.hachimi.client.\u0000gE\u200E` | `hidden#517` | `java.lang.Enum` | 5/8 | — | 枚举：模式/类别/状态选项（枚举常量名称多数已加密） | 中 |
| `class_345` | `pw.hachimi.client.\u0000jT\u200E` | `hidden#195` | `class_871 / pw.hachimi.client.\u0000F\u200E` | 2/5 | — | 内部辅助类/值对象/适配器；精确业务名受混淆影响 | 低 |
| `class_346` | `pw.hachimi.client.\u0000fR\u200E` | `hidden#230` | `class_874 / pw.hachimi.client.\u0000re\u200E` | 8/15 | — | 客户端功能模块实现；行为域：具体功能名被字符串加密隐藏 | 中-低 |
| `class_347` | `pw.hachimi.client.\u0000ga\u200E` | `hidden#030` | `java.lang.Object` | 9/26 | — | 内部辅助/管理类；主要涉及：数据包/网络、方块/世界交互、实体/战斗、移动/玩家状态 | 中 |
| `class_348` | `pw.hachimi.client.\u0000qv\u200E` | `hidden#027` | `class_349 / pw.hachimi.client.\u0000pf\u200E` | 6/12 | ID 44 / 1 methods | 内部辅助/管理类；主要涉及：JSON/配置、文件/IO | 中 |
| `class_349` | `pw.hachimi.client.\u0000pf\u200E` | `hidden#233` | `java.lang.Object` | 7/21 | ID 66 / 1 methods | 内部辅助/管理类；主要涉及：JSON/配置、文件/IO | 中 |
| `class_350` | `pw.hachimi.client.\u0000fK\u200E` | `hidden#184` | `java.lang.Enum` | 4/8 | — | 枚举：模式/类别/状态选项（枚举常量名称多数已加密） | 中 |
| `class_351` | `pw.hachimi.client.\u0000br\u200E` | `hidden#722` | `class_874 / pw.hachimi.client.\u0000re\u200E` | 16/39 | — | 客户端功能模块实现；行为域：移动/玩家状态 | 中 |
| `class_352` | `pw.hachimi.client.\u0000kr\u200E` | `hidden#130` | `class_874 / pw.hachimi.client.\u0000re\u200E` | 7/11 | — | 客户端功能模块实现；行为域：具体功能名被字符串加密隐藏 | 中-低 |
| `class_353` | `pw.hachimi.client.\u0000bp\u200E` | `hidden#749` | `class_874 / pw.hachimi.client.\u0000re\u200E` | 9/15 | — | 客户端功能模块实现；行为域：具体功能名被字符串加密隐藏 | 中-低 |
| `class_354` | `pw.hachimi.client.\u0000jd\u200E` | `hidden#412` | `java.lang.Enum` | 3/8 | — | 枚举：模式/类别/状态选项（枚举常量名称多数已加密） | 中 |
| `class_355` | `pw.hachimi.client.\u0000hG\u200E` | `hidden#099` | `class_874 / pw.hachimi.client.\u0000re\u200E` | 28/26 | — | 客户端功能模块实现；行为域：渲染/HUD、实体/战斗 | 中 |
| `class_356` | `pw.hachimi.client.\u0000it\u200E` | `hidden#162` | `java.lang.Object` | 6/9 | — | 内部辅助类/值对象/适配器；精确业务名受混淆影响 | 低 |
| `class_357` | `pw.hachimi.client.\u0000gq\u200E` | `hidden#167` | `class_874 / pw.hachimi.client.\u0000re\u200E` | 35/41 | — | 客户端功能模块实现；行为域：渲染/HUD、方块/世界交互、实体/战斗 | 中 |
| `class_358` | `pw.hachimi.client.\u0000cj\u200E` | `hidden#239` | `class_874 / pw.hachimi.client.\u0000re\u200E` | 13/23 | — | 客户端功能模块实现；行为域：渲染/HUD、移动/玩家状态 | 中 |
| `class_359` | `pw.hachimi.client.\u0000jW\u200E` | `hidden#245` | `class_874 / pw.hachimi.client.\u0000re\u200E` | 18/16 | — | 客户端功能模块实现；行为域：渲染/HUD、方块/世界交互、实体/战斗 | 中 |
| `class_360` | `pw.hachimi.client.\u0000ql\u200E` | `hidden#694` | `class_874 / pw.hachimi.client.\u0000re\u200E` | 14/13 | — | 客户端功能模块实现；行为域：实体/战斗、移动/玩家状态 | 中 |
| `class_361` | `pw.hachimi.client.\u0000Z\u200E` | `hidden#442` | `class_874 / pw.hachimi.client.\u0000re\u200E` | 14/21 | — | 客户端功能模块实现；行为域：渲染/HUD、方块/世界交互、实体/战斗 | 中 |
| `class_362` | `pw.hachimi.client.\u0000hF\u200E` | `hidden#088` | `class_874 / pw.hachimi.client.\u0000re\u200E` | 34/41 | — | 客户端功能模块实现；行为域：渲染/HUD、物品/背包、实体/战斗 | 中 |
| `class_363` | `pw.hachimi.client.\u0000lR\u200E` | `hidden#071` | `class_874 / pw.hachimi.client.\u0000re\u200E` | 7/10 | — | 客户端功能模块实现；行为域：具体功能名被字符串加密隐藏 | 中-低 |
| `class_364` | `pw.hachimi.client.\u0000gn\u200E` | `hidden#182` | `class_883 / pw.hachimi.client.\u0000mZ\u200E` | 5/10 | — | Brigadier 客户端命令实现 | 高 |
| `class_365` | `pw.hachimi.client.\u0000kl\u200E` | `hidden#021` | `class_874 / pw.hachimi.client.\u0000re\u200E` | 12/20 | — | 客户端功能模块实现；行为域：渲染/HUD | 中 |
| `class_366` | `pw.hachimi.client.\u0000gh\u200E` | `hidden#075` | `class_919 / pw.hachimi.client.\u0000nZ\u200E` | 2/3 | — | 事件对象/事件上下文；领域：具体事件类型需运行时字符串/调用点确认 | 中-低 |
| `class_367` | `pw.hachimi.client.\u0000iU\u200E` | `hidden#654` | `class_874 / pw.hachimi.client.\u0000re\u200E` | 6/11 | — | 客户端功能模块实现；行为域：实体/战斗 | 中 |
| `class_368` | `pw.hachimi.client.\u0000il\u200E` | `hidden#061` | `java.lang.Enum` | 4/8 | — | 枚举：模式/类别/状态选项（枚举常量名称多数已加密） | 中 |
| `class_369` | `pw.hachimi.client.\u0000jj\u200E` | `hidden#444` | `class_919 / pw.hachimi.client.\u0000nZ\u200E` | 5/9 | — | 事件对象/事件上下文；领域：具体事件类型需运行时字符串/调用点确认 | 中-低 |
| `class_370` | `pw.hachimi.client.\u0000oR\u200E` | `hidden#464` | `class_806 / pw.hachimi.client.\u0000ot\u200E` | 31/58 | ID 31 / 4 methods | 客户端功能模块实现；行为域：数据包/网络、方块/世界交互、实体/战斗 | 中 |
| `class_371` | `pw.hachimi.client.\u0000qZ\u200E` | `hidden#506` | `class_874 / pw.hachimi.client.\u0000re\u200E` | 10/17 | — | 客户端功能模块实现；行为域：数据包/网络、方块/世界交互 | 中 |
| `class_372` | `pw.hachimi.client.\u0000fF\u200E` | `hidden#139` | `class_919 / pw.hachimi.client.\u0000nZ\u200E` | 3/5 | — | 事件对象/事件上下文；领域：具体事件类型需运行时字符串/调用点确认 | 中-低 |
| `class_373` | `pw.hachimi.client.\u0000lA\u200E` | `hidden#693` | `java.lang.Enum` | 5/8 | — | 枚举：模式/类别/状态选项（枚举常量名称多数已加密） | 中 |
| `class_374` | `pw.hachimi.client.\u0000gB\u200E` | `hidden#433` | `class_919 / pw.hachimi.client.\u0000nZ\u200E` | 4/7 | — | 事件对象/事件上下文；领域：实体/战斗 | 中 |
| `class_375` | `pw.hachimi.client.\u0000pb\u200E` | `hidden#173` | `java.lang.Enum` | 7/8 | — | 枚举：模式/类别/状态选项（枚举常量名称多数已加密） | 中 |
| `class_376` | `pw.hachimi.client.\u0000dj\u200E` | `hidden#576` | `class_806 / pw.hachimi.client.\u0000ot\u200E` | 11/22 | — | 客户端功能模块实现；行为域：移动/玩家状态 | 中 |
| `class_377` | `pw.hachimi.client.\u0000jD\u200E` | `hidden#770` | `java.lang.Enum` | 11/8 | — | 枚举：模式/类别/状态选项（枚举常量名称多数已加密） | 中 |
| `class_378` | `pw.hachimi.client.\u0000cH\u200E` | `hidden#586` | `class_874 / pw.hachimi.client.\u0000re\u200E` | 28/37 | — | 客户端功能模块实现；行为域：实体/战斗、移动/玩家状态 | 中 |
| `class_379` | `pw.hachimi.client.\u0000ek\u200E` | `hidden#198` | `class_874 / pw.hachimi.client.\u0000re\u200E` | 8/15 | — | 客户端功能模块实现；行为域：具体功能名被字符串加密隐藏 | 中-低 |
| `class_380` | `pw.hachimi.client.\u0000nK\u200E` | `hidden#817` | `class_874 / pw.hachimi.client.\u0000re\u200E` | 6/10 | — | 客户端功能模块实现；行为域：移动/玩家状态 | 中 |
| `class_381` | `pw.hachimi.client.\u0000fg\u200E` | `hidden#553` | `java.lang.Enum` | 4/8 | — | 枚举：模式/类别/状态选项（枚举常量名称多数已加密） | 中 |
| `class_382` | `pw.hachimi.client.\u0000pl\u200E` | `hidden#367` | `class_874 / pw.hachimi.client.\u0000re\u200E` | 14/22 | — | 客户端功能模块实现；行为域：数据包/网络 | 中 |
| `class_383` | `pw.hachimi.client.\u0000lY\u200E` | `hidden#160` | `class_874 / pw.hachimi.client.\u0000re\u200E` | 21/29 | ID 15 / 3 methods | 客户端功能模块实现；行为域：物品/背包、移动/玩家状态 | 中 |
| `class_384` | `pw.hachimi.client.\u0000aG\u200E` | `hidden#623` | `java.lang.Enum` | 6/8 | — | 枚举：模式/类别/状态选项（枚举常量名称多数已加密） | 中 |
| `class_385` | `pw.hachimi.client.\u0000hz\u200E` | `hidden#670` | `class_919 / pw.hachimi.client.\u0000nZ\u200E` | 5/8 | — | 事件对象/事件上下文；领域：具体事件类型需运行时字符串/调用点确认 | 中-低 |
| `class_386` | `pw.hachimi.client.\u0000gt\u200E` | `hidden#216` | `java.lang.Enum` | 8/8 | — | 枚举：模式/类别/状态选项（枚举常量名称多数已加密） | 中 |
| `class_387` | `pw.hachimi.client.\u0000kM\u200E` | `hidden#513` | `class_874 / pw.hachimi.client.\u0000re\u200E` | 21/42 | ID 39 / 2 methods | 客户端功能模块实现；行为域：数据包/网络、物品/背包、方块/世界交互 | 中 |
| `class_388` | `pw.hachimi.client.\u0000cx\u200E` | `hidden#371` | `class_874 / pw.hachimi.client.\u0000re\u200E` | 5/8 | — | 客户端功能模块实现；行为域：具体功能名被字符串加密隐藏 | 中-低 |
| `class_389` | `pw.hachimi.client.\u0000qP\u200E` | `hidden#406` | `class_883 / pw.hachimi.client.\u0000mZ\u200E` | 5/12 | — | Brigadier 客户端命令实现 | 高 |
| `class_390` | `pw.hachimi.client.\u0000oc\u200E` | `hidden#631` | `java.lang.Enum` | 9/8 | — | 枚举：模式/类别/状态选项（枚举常量名称多数已加密） | 中 |
| `class_391` | `pw.hachimi.client.\u0000nI\u200E` | `hidden#744` | `class_919 / pw.hachimi.client.\u0000nZ\u200E` | 3/5 | — | 事件对象/事件上下文；领域：实体/战斗 | 中 |
| `class_392` | `pw.hachimi.client.\u0000rl\u200E` | `hidden#314` | `class_874 / pw.hachimi.client.\u0000re\u200E` | 16/25 | ID 36 / 4 methods | 客户端功能模块实现；行为域：移动/玩家状态 | 中 |
| `class_393` | `pw.hachimi.client.\u0000no\u200E` | `hidden#453` | `class_874 / pw.hachimi.client.\u0000re\u200E` | 15/13 | — | 客户端功能模块实现；行为域：数据包/网络、移动/玩家状态 | 中 |
| `class_394` | `pw.hachimi.client.\u0000dV\u200E` | `hidden#333` | `java.lang.Object` | 2/6 | — | 内部辅助/管理类；主要涉及：物品/背包、方块/世界交互、实体/战斗 | 中 |
| `class_395` | `pw.hachimi.client.\u0000ne\u200E` | `hidden#273` | `java.lang.Enum` | 4/8 | — | 枚举：模式/类别/状态选项（枚举常量名称多数已加密） | 中 |
| `class_396` | `pw.hachimi.client.\u0000eJ\u200E` | `hidden#609` | `class_874 / pw.hachimi.client.\u0000re\u200E` | 14/21 | — | 客户端功能模块实现；行为域：数据包/网络、实体/战斗、移动/玩家状态 | 中 |
| `class_397` | `pw.hachimi.client.\u0000mH\u200E` | `hidden#408` | `java.lang.Enum` | 4/8 | — | 枚举：模式/类别/状态选项（枚举常量名称多数已加密） | 中 |
| `class_398` | `pw.hachimi.client.\u0000ay\u200E` | `hidden#439` | `class_805 / pw.hachimi.client.\u0000kb\u200E` | 18/29 | ID 16 / 1 methods | 客户端功能模块实现；行为域：数据包/网络、方块/世界交互、实体/战斗 | 中 |
| `class_399` | `pw.hachimi.client.\u0000kj\u200E` | `hidden#052` | `java.lang.Enum` | 5/8 | — | 枚举：模式/类别/状态选项（枚举常量名称多数已加密） | 中 |
| `class_400` | `pw.hachimi.client.\u0000jw\u200E` | `hidden#589` | `java.lang.Enum` | 5/8 | — | 枚举：模式/类别/状态选项（枚举常量名称多数已加密） | 中 |
| `class_401` | `pw.hachimi.client.\u0000mU\u200E` | `hidden#547` | `class_874 / pw.hachimi.client.\u0000re\u200E` | 18/26 | — | 客户端功能模块实现；行为域：数据包/网络、实体/战斗、移动/玩家状态 | 中 |
| `class_402` | `pw.hachimi.client.\u0000lG\u200E` | `hidden#819` | `class_874 / pw.hachimi.client.\u0000re\u200E` | 9/12 | — | 客户端功能模块实现；行为域：具体功能名被字符串加密隐藏 | 中-低 |
| `class_403` | `pw.hachimi.client.\u0000dv\u200E` | `hidden#720` | `java.lang.Enum` | 5/8 | — | 枚举：模式/类别/状态选项（枚举常量名称多数已加密） | 中 |
| `class_404` | `pw.hachimi.client.\u0000iM\u200E` | `hidden#551` | `class_919 / pw.hachimi.client.\u0000nZ\u200E` | 3/5 | — | 事件对象/事件上下文；领域：具体事件类型需运行时字符串/调用点确认 | 中-低 |
| `class_405` | `pw.hachimi.client.\u0000og\u200E` | `hidden#679` | `class_919 / pw.hachimi.client.\u0000nZ\u200E` | 3/6 | — | 事件对象/事件上下文；领域：具体事件类型需运行时字符串/调用点确认 | 中-低 |
| `class_406` | `pw.hachimi.client.\u0000dP\u200E` | `hidden#313` | `java.lang.Enum` | 4/8 | — | 枚举：模式/类别/状态选项（枚举常量名称多数已加密） | 中 |
| `class_407` | `pw.hachimi.client.\u0000em\u200E` | `hidden#169` | `class_919 / pw.hachimi.client.\u0000nZ\u200E` | 2/3 | — | 事件对象/事件上下文；领域：具体事件类型需运行时字符串/调用点确认 | 中-低 |
| `class_408` | `pw.hachimi.client.\u0000mx\u200E` | `hidden#111` | `java.lang.Object` | 2/10 | — | 内部辅助/管理类；主要涉及：物品/背包、移动/玩家状态 | 中 |
| `class_409` | `pw.hachimi.client.\u0000bw\u200E` | `hidden#785` | `java.lang.Enum` | 4/8 | — | 枚举：模式/类别/状态选项（枚举常量名称多数已加密） | 中 |
| `class_410` | `pw.hachimi.client.\u0000dN\u200E` | `hidden#223` | `class_919 / pw.hachimi.client.\u0000nZ\u200E` | 4/7 | — | 事件对象/事件上下文；领域：实体/战斗 | 中 |
| `class_411` | `pw.hachimi.client.\u0000pr\u200E` | `hidden#390` | `class_874 / pw.hachimi.client.\u0000re\u200E` | 16/23 | — | 客户端功能模块实现；行为域：移动/玩家状态 | 中 |
| `class_412` | `pw.hachimi.client.\u0000gI\u200E` | `hidden#567` | `class_874 / pw.hachimi.client.\u0000re\u200E` | 16/20 | — | 客户端功能模块实现；行为域：移动/玩家状态 | 中 |
| `class_413` | `pw.hachimi.client.\u0000aA\u200E` | `hidden#619` | `class_874 / pw.hachimi.client.\u0000re\u200E` | 9/16 | — | 客户端功能模块实现；行为域：方块/世界交互、实体/战斗、移动/玩家状态 | 中 |
| `class_414` | `pw.hachimi.client.\u0000iF\u200E` | `hidden#431` | `class_874 / pw.hachimi.client.\u0000re\u200E` | 19/23 | — | 客户端功能模块实现；行为域：渲染/HUD、方块/世界交互 | 中 |
| `class_415` | `pw.hachimi.client.\u0000fG\u200E` | `hidden#148` | `class_874 / pw.hachimi.client.\u0000re\u200E` | 7/14 | — | 客户端功能模块实现；行为域：实体/战斗 | 中 |
| `class_416` | `pw.hachimi.client.\u0000kX\u200E` | `hidden#590` | `class_417 / pw.hachimi.client.\u0000fh\u200E` | 3/5 | — | 事件对象/事件上下文；领域：具体事件类型需运行时字符串/调用点确认 | 中-低 |
| `class_417` | `pw.hachimi.client.\u0000fh\u200E` | `hidden#568` | `class_919 / pw.hachimi.client.\u0000nZ\u200E` | 3/5 | — | 事件对象/事件上下文；领域：具体事件类型需运行时字符串/调用点确认 | 中-低 |
| `class_418` | `pw.hachimi.client.\u0000M\u200E` | `hidden#773` | `java.lang.Enum` | 5/8 | — | 枚举：模式/类别/状态选项（枚举常量名称多数已加密） | 中 |
| `class_419` | `pw.hachimi.client.\u0000dW\u200E` | `hidden#403` | `class_919 / pw.hachimi.client.\u0000nZ\u200E` | 15/11 | — | 事件对象/事件上下文；领域：渲染/HUD | 中 |
| `class_420` | `pw.hachimi.client.\u0000dl\u200E` | `hidden#644` | `java.lang.Enum` | 4/8 | — | 枚举：模式/类别/状态选项（枚举常量名称多数已加密） | 中 |
| `class_421` | `pw.hachimi.client.\u0000av\u200E` | `hidden#450` | `class_806 / pw.hachimi.client.\u0000ot\u200E` | 34/52 | ID 52 / 2 methods | 客户端功能模块实现；行为域：数据包/网络、实体/战斗 | 中 |
| `class_422` | `pw.hachimi.client.\u0000mz\u200E` | `hidden#188` | `class_874 / pw.hachimi.client.\u0000re\u200E` | 5/8 | — | 客户端功能模块实现；行为域：具体功能名被字符串加密隐藏 | 中-低 |
| `class_423` | `pw.hachimi.client.\u0000dT\u200E` | `hidden#358` | `class_874 / pw.hachimi.client.\u0000re\u200E` | 5/9 | — | 客户端功能模块实现；行为域：数据包/网络 | 中 |
| `class_424` | `pw.hachimi.client.\u0000jX\u200E` | `hidden#258` | `java.lang.Object` | 5/15 | — | 内部辅助/管理类；主要涉及：数据包/网络、方块/世界交互 | 中 |
| `class_425` | `pw.hachimi.client.\u0000cq\u200E` | `hidden#282` | `class_874 / pw.hachimi.client.\u0000re\u200E` | 9/13 | — | 客户端功能模块实现；行为域：渲染/HUD | 中 |
| `class_426` | `pw.hachimi.client.\u0000eb\u200E` | `hidden#101` | `class_874 / pw.hachimi.client.\u0000re\u200E` | 5/8 | — | 客户端功能模块实现；行为域：具体功能名被字符串加密隐藏 | 中-低 |
| `class_427` | `pw.hachimi.client.\u0000rg\u200E` | `hidden#248` | `java.lang.Object` | 6/22 | — | 内部辅助/管理类；主要涉及：实体/战斗 | 中 |
| `class_428` | `pw.hachimi.client.\u0000bc\u200E` | `hidden#605` | `class_874 / pw.hachimi.client.\u0000re\u200E` | 10/21 | — | 客户端功能模块实现；行为域：具体功能名被字符串加密隐藏 | 中-低 |
| `class_429` | `pw.hachimi.client.\u0000qu\u200E` | `hidden#782` | `java.lang.Enum` | 6/8 | — | 枚举：模式/类别/状态选项（枚举常量名称多数已加密） | 中 |
| `class_430` | `pw.hachimi.client.\u0000Y\u200E` | `hidden#426` | `class_874 / pw.hachimi.client.\u0000re\u200E` | 9/18 | — | 客户端功能模块实现；行为域：具体功能名被字符串加密隐藏 | 中-低 |
| `class_431` | `pw.hachimi.client.\u0000lz\u200E` | `hidden#579` | `class_432 / pw.hachimi.client.\u0000ik\u200E` | 1/3 | — | 事件对象/事件上下文；领域：具体事件类型需运行时字符串/调用点确认 | 中-低 |
| `class_432` | `pw.hachimi.client.\u0000ik\u200E` | `hidden#062` | `class_919 / pw.hachimi.client.\u0000nZ\u200E` | 3/5 | — | 事件对象/事件上下文；领域：具体事件类型需运行时字符串/调用点确认 | 中-低 |
| `class_433` | `pw.hachimi.client.\u0000ep\u200E` | `hidden#218` | `class_874 / pw.hachimi.client.\u0000re\u200E` | 11/18 | — | 客户端功能模块实现；行为域：命令、文件/IO | 中 |
| `class_434` | `pw.hachimi.client.\u0000qN\u200E` | `hidden#330` | `class_919 / pw.hachimi.client.\u0000nZ\u200E` | 3/5 | — | 事件对象/事件上下文；领域：实体/战斗 | 中 |
| `class_435` | `pw.hachimi.client.\u0000pT\u200E` | `hidden#044` | `class_874 / pw.hachimi.client.\u0000re\u200E` | 9/14 | — | 客户端功能模块实现；行为域：具体功能名被字符串加密隐藏 | 中-低 |
| `class_436` | `pw.hachimi.client.\u0000gU\u200E` | `hidden#700` | `class_919 / pw.hachimi.client.\u0000nZ\u200E` | 2/3 | — | 事件对象/事件上下文；领域：具体事件类型需运行时字符串/调用点确认 | 中-低 |
| `class_437` | `pw.hachimi.client.\u0000aL\u200E` | `hidden#689` | `net.minecraft.class_743` | 4/6 | — | 内部辅助类/值对象/适配器；精确业务名受混淆影响 | 低 |
| `class_438` | `pw.hachimi.client.\u0000cG\u200E` | `hidden#577` | `class_747 / pw.hachimi.client.\u0000in\u200E` | 9/15 | — | 内部辅助/管理类；主要涉及：数据包/网络、移动/玩家状态 | 中 |
| `class_439` | `pw.hachimi.client.\u0000qs\u200E` | `hidden#776` | `class_440 / pw.hachimi.client.\u0000qL\u200E` | 2/3 | — | 事件对象/事件上下文；领域：具体事件类型需运行时字符串/调用点确认 | 中-低 |
| `class_440` | `pw.hachimi.client.\u0000qL\u200E` | `hidden#354` | `class_919 / pw.hachimi.client.\u0000nZ\u200E` | 3/6 | — | 事件对象/事件上下文；领域：具体事件类型需运行时字符串/调用点确认 | 中-低 |
| `class_441` | `pw.hachimi.client.\u0000df\u200E` | `hidden#542` | `java.lang.Enum` | 6/8 | — | 枚举：模式/类别/状态选项（枚举常量名称多数已加密） | 中 |
| `class_442` | `pw.hachimi.client.\u0000kL\u200E` | `hidden#502` | `class_919 / pw.hachimi.client.\u0000nZ\u200E` | 5/7 | — | 事件对象/事件上下文；领域：方块/世界交互 | 中 |
| `class_443` | `pw.hachimi.client.\u0000ic\u200E` | `hidden#771` | `class_874 / pw.hachimi.client.\u0000re\u200E` | 5/9 | — | 客户端功能模块实现；行为域：数据包/网络 | 中 |
| `class_444` | `pw.hachimi.client.\u0000pp\u200E` | `hidden#418` | `class_919 / pw.hachimi.client.\u0000nZ\u200E` | 2/3 | — | 事件对象/事件上下文；领域：具体事件类型需运行时字符串/调用点确认 | 中-低 |
| `class_445` | `pw.hachimi.client.\u0000nw\u200E` | `hidden#486` | `java.lang.Object` | 4/3 | — | 内部辅助类/值对象/适配器；精确业务名受混淆影响 | 低 |
| `class_446` | `pw.hachimi.client.\u0000pq\u200E` | `hidden#375` | `class_874 / pw.hachimi.client.\u0000re\u200E` | 12/17 | — | 客户端功能模块实现；行为域：数据包/网络、物品/背包、方块/世界交互 | 中 |
| `class_447` | `pw.hachimi.client.\u0000fr\u200E` | `hidden#628` | `class_919 / pw.hachimi.client.\u0000nZ\u200E` | 6/13 | — | 事件对象/事件上下文；领域：移动/玩家状态 | 中 |
| `class_448` | `pw.hachimi.client.\u0000jp\u200E` | `hidden#548` | `class_747 / pw.hachimi.client.\u0000in\u200E` | 11/31 | ID 62 / 2 methods | 内部辅助/管理类；主要涉及：物品/背包、文件/IO | 中 |
| `class_449` | `pw.hachimi.client.\u0000gc\u200E` | `hidden#013` | `class_810 / pw.hachimi.client.\u0000aU\u200E` | 2/3 | — | 事件对象/事件上下文；领域：渲染/HUD | 中 |
| `class_450` | `pw.hachimi.client.\u0000hw\u200E` | `hidden#635` | `class_874 / pw.hachimi.client.\u0000re\u200E` | 14/24 | — | 客户端功能模块实现；行为域：数据包/网络、方块/世界交互、实体/战斗 | 中 |
| `class_451` | `pw.hachimi.client.\u0000nx\u200E` | `hidden#546` | `class_919 / pw.hachimi.client.\u0000nZ\u200E` | 2/3 | — | 事件对象/事件上下文；领域：具体事件类型需运行时字符串/调用点确认 | 中-低 |
| `class_452` | `pw.hachimi.client.\u0000gO\u200E` | `hidden#574` | `java.lang.Enum` | 4/8 | — | 枚举：模式/类别/状态选项（枚举常量名称多数已加密） | 中 |
| `class_453` | `pw.hachimi.client.\u0000hu\u200E` | `hidden#664` | `class_883 / pw.hachimi.client.\u0000mZ\u200E` | 6/21 | — | Brigadier 客户端命令实现 | 高 |
| `class_454` | `pw.hachimi.client.\u0000hx\u200E` | `hidden#697` | `class_806 / pw.hachimi.client.\u0000ot\u200E` | 19/37 | ID 57 / 2 methods | 客户端功能模块实现；行为域：数据包/网络、物品/背包、方块/世界交互 | 中 |
| `class_455` | `pw.hachimi.client.\u0000c\u200E` | `hidden#538` | `class_919 / pw.hachimi.client.\u0000nZ\u200E` | 5/9 | — | 事件对象/事件上下文；领域：具体事件类型需运行时字符串/调用点确认 | 中-低 |
| `class_456` | `pw.hachimi.client.\u0000jJ\u200E` | `hidden#086` | `java.lang.Enum` | 5/8 | — | 枚举：模式/类别/状态选项（枚举常量名称多数已加密） | 中 |
| `class_457` | `pw.hachimi.client.\u0000cZ\u200E` | `hidden#046` | `class_919 / pw.hachimi.client.\u0000nZ\u200E` | 2/3 | — | 事件对象/事件上下文；领域：具体事件类型需运行时字符串/调用点确认 | 中-低 |
| `class_458` | `pw.hachimi.client.\u0000hT\u200E` | `hidden#261` | `class_874 / pw.hachimi.client.\u0000re\u200E` | 13/27 | ID 27 / 1 methods | 客户端功能模块实现；行为域：物品/背包 | 中 |
| `class_459` | `pw.hachimi.client.\u0000nX\u200E` | `hidden#142` | `java.lang.Object` | 2/9 | — | 自定义 Brigadier 命令参数解析器 | 高 |
| `class_460` | `pw.hachimi.client.\u0000oA\u200E` | `hidden#256` | `class_874 / pw.hachimi.client.\u0000re\u200E` | 13/23 | — | 客户端功能模块实现；行为域：数据包/网络、实体/战斗 | 中 |
| `class_461` | `pw.hachimi.client.\u0000gK\u200E` | `hidden#526` | `java.lang.Record` | 7/19 | — | 数据载体/Record-like；组件：shulker、compact、color、slot、stacks | 高 |
| `class_462` | `pw.hachimi.client.\u0000nv\u200E` | `hidden#475` | `java.lang.Enum` | 4/8 | — | 枚举：模式/类别/状态选项（枚举常量名称多数已加密） | 中 |
| `class_463` | `pw.hachimi.client.\u0000eY\u200E` | `hidden#801` | `java.lang.Object` | 5/10 | — | 内部辅助/管理类；主要涉及：渲染/HUD | 中 |
| `class_464` | `pw.hachimi.client.\u0000mO\u200E` | `hidden#441` | `class_874 / pw.hachimi.client.\u0000re\u200E` | 9/14 | — | 客户端功能模块实现；行为域：方块/世界交互 | 中 |
| `class_465` | `pw.hachimi.client.\u0000dg\u200E` | `hidden#537` | `java.lang.Enum` | 5/8 | — | 枚举：模式/类别/状态选项（枚举常量名称多数已加密） | 中 |
| `class_466` | `pw.hachimi.client.\u0000cg\u200E` | `hidden#200` | `class_644 / pw.hachimi.client.\u0000iQ\u200E` | 2/3 | — | 事件对象/事件上下文；领域：具体事件类型需运行时字符串/调用点确认 | 中-低 |
| `class_467` | `pw.hachimi.client.\u0000kd\u200E` | `hidden#736` | `java.lang.Enum` | 6/8 | — | 枚举：模式/类别/状态选项（枚举常量名称多数已加密） | 中 |
| `class_468` | `pw.hachimi.client.\u0000qa\u200E` | `hidden#602` | `class_919 / pw.hachimi.client.\u0000nZ\u200E` | 5/9 | — | 事件对象/事件上下文；领域：方块/世界交互 | 中 |
| `class_469` | `pw.hachimi.client.\u0000do\u200E` | `hidden#639` | `class_874 / pw.hachimi.client.\u0000re\u200E` | 13/23 | — | 客户端功能模块实现；行为域：具体功能名被字符串加密隐藏 | 中-低 |
| `class_470` | `pw.hachimi.client.\u0000fW\u200E` | `hidden#344` | `class_805 / pw.hachimi.client.\u0000kb\u200E` | 36/57 | — | 客户端功能模块实现；行为域：渲染/HUD、数据包/网络、物品/背包 | 中 |
| `class_471` | `pw.hachimi.client.\u0000gi\u200E` | `hidden#137` | `class_874 / pw.hachimi.client.\u0000re\u200E` | 11/13 | — | 客户端功能模块实现；行为域：数据包/网络、实体/战斗 | 中 |
| `class_472` | `pw.hachimi.client.\u0000dC\u200E` | `hidden#151` | `class_874 / pw.hachimi.client.\u0000re\u200E` | 11/12 | — | 客户端功能模块实现；行为域：数据包/网络、移动/玩家状态 | 中 |
| `class_473` | `pw.hachimi.client.\u0000ke\u200E` | `hidden#796` | `class_874 / pw.hachimi.client.\u0000re\u200E` | 7/13 | — | 客户端功能模块实现；行为域：物品/背包 | 中 |
| `class_474` | `pw.hachimi.client.\u0000bW\u200E` | `hidden#451` | `java.lang.Object` | 8/5 | — | 内部辅助类/值对象/适配器；精确业务名受混淆影响 | 低 |
| `class_475` | `pw.hachimi.client.\u0000pL\u200E` | `hidden#729` | `java.lang.Enum` | 5/8 | — | 枚举：模式/类别/状态选项（枚举常量名称多数已加密） | 中 |
| `class_476` | `pw.hachimi.client.\u0000bB\u200E` | `hidden#193` | `class_883 / pw.hachimi.client.\u0000mZ\u200E` | 5/10 | — | Brigadier 客户端命令实现 | 高 |
| `class_477` | `pw.hachimi.client.\u0000ni\u200E` | `hidden#332` | `class_874 / pw.hachimi.client.\u0000re\u200E` | 12/17 | — | 客户端功能模块实现；行为域：物品/背包 | 中 |
| `class_478` | `pw.hachimi.client.\u0000jC\u200E` | `hidden#810` | `class_874 / pw.hachimi.client.\u0000re\u200E` | 7/11 | — | 客户端功能模块实现；行为域：数据包/网络 | 中 |
| `class_479` | `pw.hachimi.client.\u0000dL\u200E` | `hidden#253` | `java.lang.Enum` | 8/8 | — | 枚举：模式/类别/状态选项（枚举常量名称多数已加密） | 中 |
| `class_480` | `pw.hachimi.client.\u0000ll\u200E` | `hidden#469` | `java.lang.Enum` | 7/8 | — | 枚举：模式/类别/状态选项（枚举常量名称多数已加密） | 中 |
| `class_481` | `pw.hachimi.client.\u0000cs\u200E` | `hidden#359` | `class_806 / pw.hachimi.client.\u0000ot\u200E` | 15/13 | — | 客户端功能模块实现；行为域：具体功能名被字符串加密隐藏 | 中-低 |
| `class_482` | `pw.hachimi.client.\u0000bN\u200E` | `hidden#284` | `class_883 / pw.hachimi.client.\u0000mZ\u200E` | 5/12 | — | Brigadier 客户端命令实现 | 高 |
| `class_483` | `pw.hachimi.client.\u0000nT\u200E` | `hidden#094` | `class_806 / pw.hachimi.client.\u0000ot\u200E` | 15/17 | — | 客户端功能模块实现；行为域：数据包/网络、移动/玩家状态 | 中 |
| `class_484` | `pw.hachimi.client.\u0000eL\u200E` | `hidden#584` | `class_919 / pw.hachimi.client.\u0000nZ\u200E` | 2/3 | — | 事件对象/事件上下文；领域：具体事件类型需运行时字符串/调用点确认 | 中-低 |
| `class_485` | `pw.hachimi.client.\u0000lh\u200E` | `hidden#421` | `java.lang.Enum` | 7/9 | — | 枚举：模式/类别/状态选项（枚举常量名称多数已加密） | 中 |
| `class_486` | `pw.hachimi.client.\u0000jb\u200E` | `hidden#339` | `class_919 / pw.hachimi.client.\u0000nZ\u200E` | 4/7 | — | 事件对象/事件上下文；领域：具体事件类型需运行时字符串/调用点确认 | 中-低 |
| `class_487` | `pw.hachimi.client.\u0000qe\u200E` | `hidden#655` | `class_806 / pw.hachimi.client.\u0000ot\u200E` | 27/49 | ID 13 / 1 methods | 客户端功能模块实现；行为域：数据包/网络、方块/世界交互、移动/玩家状态 | 中 |
| `class_488` | `pw.hachimi.client.\u0000ny\u200E` | `hidden#565` | `java.lang.Enum` | 6/8 | — | 枚举：模式/类别/状态选项（枚举常量名称多数已加密） | 中 |
| `class_489` | `pw.hachimi.client.\u0000iA\u200E` | `hidden#414` | `class_498 / pw.hachimi.client.\u0000ow\u200E` | 1/5 | — | 内部辅助/管理类；主要涉及：移动/玩家状态 | 中 |
| `class_490` | `pw.hachimi.client.\u0000iJ\u200E` | `hidden#479` | `class_498 / pw.hachimi.client.\u0000ow\u200E` | 1/5 | — | 内部辅助/管理类；主要涉及：移动/玩家状态 | 中 |
| `class_491` | `pw.hachimi.client.\u0000iL\u200E` | `hidden#489` | `class_498 / pw.hachimi.client.\u0000ow\u200E` | 1/4 | — | 内部辅助/管理类；主要涉及：移动/玩家状态 | 中 |
| `class_492` | `pw.hachimi.client.\u0000jY\u200E` | `hidden#213` | `class_498 / pw.hachimi.client.\u0000ow\u200E` | 1/6 | — | 内部辅助/管理类；主要涉及：移动/玩家状态 | 中 |
| `class_493` | `pw.hachimi.client.\u0000iT\u200E` | `hidden#591` | `class_498 / pw.hachimi.client.\u0000ow\u200E` | 1/4 | — | 内部辅助/管理类；主要涉及：移动/玩家状态 | 中 |
| `class_494` | `pw.hachimi.client.\u0000mk\u200E` | `hidden#778` | `class_498 / pw.hachimi.client.\u0000ow\u200E` | 1/4 | — | 内部辅助/管理类；主要涉及：移动/玩家状态 | 中 |
| `class_495` | `pw.hachimi.client.\u0000mj\u200E` | `hidden#818` | `class_498 / pw.hachimi.client.\u0000ow\u200E` | 1/4 | — | 内部辅助/管理类；主要涉及：移动/玩家状态 | 中 |
| `class_496` | `pw.hachimi.client.\u0000ju\u200E` | `hidden#615` | `class_498 / pw.hachimi.client.\u0000ow\u200E` | 1/4 | — | 内部辅助/管理类；主要涉及：移动/玩家状态 | 中 |
| `class_497` | `pw.hachimi.client.\u0000jn\u200E` | `hidden#477` | `class_919 / pw.hachimi.client.\u0000nZ\u200E` | 3/4 | — | 事件对象/事件上下文；领域：渲染/HUD | 中 |
| `class_498` | `pw.hachimi.client.\u0000ow\u200E` | `hidden#092` | `java.lang.Enum` | 10/16 | — | 枚举：模式/类别/状态选项（枚举常量名称多数已加密） | 中 |
| `class_499` | `pw.hachimi.client.\u0000kN\u200E` | `hidden#511` | `java.lang.Enum` | 5/8 | — | 枚举：模式/类别/状态选项（枚举常量名称多数已加密） | 中 |
| `class_500` | `pw.hachimi.client.\u0000qF\u200E` | `hidden#220` | `class_919 / pw.hachimi.client.\u0000nZ\u200E` | 2/3 | — | 事件对象/事件上下文；领域：具体事件类型需运行时字符串/调用点确认 | 中-低 |
| `class_501` | `pw.hachimi.client.\u0000fY\u200E` | `hidden#327` | `java.lang.Enum` | 5/8 | — | 枚举：模式/类别/状态选项（枚举常量名称多数已加密） | 中 |
| `class_502` | `pw.hachimi.client.\u0000eE\u200E` | `hidden#569` | `class_919 / pw.hachimi.client.\u0000nZ\u200E` | 3/5 | — | 事件对象/事件上下文；领域：具体事件类型需运行时字符串/调用点确认 | 中-低 |
| `class_503` | `pw.hachimi.client.\u0000hv\u200E` | `hidden#626` | `class_874 / pw.hachimi.client.\u0000re\u200E` | 26/40 | — | 客户端功能模块实现；行为域：数据包/网络、移动/玩家状态 | 中 |
| `class_504` | `pw.hachimi.client.\u0000cU\u200E` | `hidden#794` | `class_919 / pw.hachimi.client.\u0000nZ\u200E` | 4/7 | — | 事件对象/事件上下文；领域：方块/世界交互 | 中 |
| `class_505` | `pw.hachimi.client.\u0000ol\u200E` | `hidden#741` | `class_874 / pw.hachimi.client.\u0000re\u200E` | 19/10 | — | 客户端功能模块实现；行为域：数据包/网络、移动/玩家状态 | 中 |
| `class_506` | `pw.hachimi.client.\u0000pR\u200E` | `hidden#036` | `class_874 / pw.hachimi.client.\u0000re\u200E` | 5/10 | — | 客户端功能模块实现；行为域：数据包/网络、方块/世界交互 | 中 |
| `class_507` | `pw.hachimi.client.\u0000hA\u200E` | `hidden#783` | `java.lang.Object` | 2/10 | — | 内部辅助/管理类；主要涉及：方块/世界交互、实体/战斗 | 中 |
| `class_508` | `pw.hachimi.client.\u0000X\u200E` | `hidden#470` | `java.lang.Enum` | 5/8 | — | 枚举：模式/类别/状态选项（枚举常量名称多数已加密） | 中 |
| `class_509` | `pw.hachimi.client.\u0000cp\u200E` | `hidden#268` | `java.lang.Object` | 2/13 | — | 内部辅助/管理类；主要涉及：实体/战斗 | 中 |
| `class_510` | `pw.hachimi.client.\u0000kQ\u200E` | `hidden#549` | `java.lang.Enum` | 4/8 | — | 枚举：模式/类别/状态选项（枚举常量名称多数已加密） | 中 |
| `class_511` | `pw.hachimi.client.\u0000aC\u200E` | `hidden#594` | `class_919 / pw.hachimi.client.\u0000nZ\u200E` | 6/3 | — | 事件对象/事件上下文；领域：渲染/HUD、物品/背包 | 中 |
| `class_512` | `pw.hachimi.client.\u0000ew\u200E` | `hidden#356` | `class_874 / pw.hachimi.client.\u0000re\u200E` | 15/23 | — | 客户端功能模块实现；行为域：数据包/网络、物品/背包、移动/玩家状态 | 中 |
| `class_513` | `pw.hachimi.client.\u0000fO\u200E` | `hidden#237` | `class_919 / pw.hachimi.client.\u0000nZ\u200E` | 2/3 | — | 事件对象/事件上下文；领域：具体事件类型需运行时字符串/调用点确认 | 中-低 |
| `class_514` | `pw.hachimi.client.\u0000pZ\u200E` | `hidden#067` | `class_874 / pw.hachimi.client.\u0000re\u200E` | 6/10 | — | 客户端功能模块实现；行为域：数据包/网络、物品/背包 | 中 |
| `class_515` | `pw.hachimi.client.\u0000es\u200E` | `hidden#309` | `class_919 / pw.hachimi.client.\u0000nZ\u200E` | 3/4 | — | 事件对象/事件上下文；领域：具体事件类型需运行时字符串/调用点确认 | 中-低 |
| `class_516` | `pw.hachimi.client.\u0000jh\u200E` | `hidden#472` | `java.lang.Enum` | 4/8 | — | 枚举：模式/类别/状态选项（枚举常量名称多数已加密） | 中 |
| `class_517` | `pw.hachimi.client.\u0000pW\u200E` | `hidden#081` | `java.lang.Object` | 5/9 | — | 内部辅助/管理类；主要涉及：渲染/HUD | 中 |
| `class_518` | `pw.hachimi.client.\u0000gx\u200E` | `hidden#264` | `class_874 / pw.hachimi.client.\u0000re\u200E` | 12/16 | — | 客户端功能模块实现；行为域：数据包/网络、物品/背包、实体/战斗 | 中 |
| `class_519` | `pw.hachimi.client.\u0000hs\u200E` | `hidden#592` | `class_874 / pw.hachimi.client.\u0000re\u200E` | 8/21 | — | 客户端功能模块实现；行为域：实体/战斗、移动/玩家状态 | 中 |
| `class_520` | `pw.hachimi.client.\u0000ji\u200E` | `hidden#429` | `class_874 / pw.hachimi.client.\u0000re\u200E` | 11/16 | — | 客户端功能模块实现；行为域：数据包/网络 | 中 |
| `class_521` | `pw.hachimi.client.\u0000qW\u200E` | `hidden#422` | `class_883 / pw.hachimi.client.\u0000mZ\u200E` | 5/11 | — | Brigadier 客户端命令实现 | 高 |
| `class_522` | `pw.hachimi.client.\u0000fI\u200E` | `hidden#109` | `class_919 / pw.hachimi.client.\u0000nZ\u200E` | 3/5 | — | 事件对象/事件上下文；领域：具体事件类型需运行时字符串/调用点确认 | 中-低 |
| `class_523` | `pw.hachimi.client.\u0000dI\u200E` | `hidden#155` | `class_874 / pw.hachimi.client.\u0000re\u200E` | 7/12 | — | 客户端功能模块实现；行为域：数据包/网络 | 中 |
| `class_524` | `pw.hachimi.client.\u0000e\u200E` | `hidden#310` | `class_874 / pw.hachimi.client.\u0000re\u200E` | 11/21 | — | 客户端功能模块实现；行为域：数据包/网络、实体/战斗 | 中 |
| `class_525` | `pw.hachimi.client.\u0000eB\u200E` | `hidden#484` | `java.lang.Enum` | 5/8 | — | 枚举：模式/类别/状态选项（枚举常量名称多数已加密） | 中 |
| `class_526` | `pw.hachimi.client.\u0000eT\u200E` | `hidden#685` | `java.lang.Enum` | 5/8 | — | 枚举：模式/类别/状态选项（枚举常量名称多数已加密） | 中 |
| `class_527` | `pw.hachimi.client.\u0000iu\u200E` | `hidden#177` | `class_874 / pw.hachimi.client.\u0000re\u200E` | 10/39 | — | 客户端功能模块实现；行为域：渲染/HUD、数据包/网络、方块/世界交互 | 中 |
| `class_528` | `pw.hachimi.client.\u0000fm\u200E` | `hidden#607` | `java.lang.Enum` | 7/8 | — | 枚举：模式/类别/状态选项（枚举常量名称多数已加密） | 中 |
| `class_529` | `pw.hachimi.client.\u0000eA\u200E` | `hidden#518` | `class_874 / pw.hachimi.client.\u0000re\u200E` | 5/12 | — | 客户端功能模块实现；行为域：数据包/网络 | 中 |
| `class_530` | `pw.hachimi.client.\u0000kD\u200E` | `hidden#399` | `class_919 / pw.hachimi.client.\u0000nZ\u200E` | 4/6 | — | 事件对象/事件上下文；领域：渲染/HUD | 中 |
| `class_531` | `pw.hachimi.client.\u0000di\u200E` | `hidden#610` | `class_874 / pw.hachimi.client.\u0000re\u200E` | 8/18 | — | 客户端功能模块实现；行为域：数据包/网络、物品/背包、移动/玩家状态 | 中 |
| `class_532` | `pw.hachimi.client.\u0000ih\u200E` | `hidden#022` | `class_874 / pw.hachimi.client.\u0000re\u200E` | 8/14 | — | 客户端功能模块实现；行为域：具体功能名被字符串加密隐藏 | 中-低 |
| `class_533` | `pw.hachimi.client.\u0000dZ\u200E` | `hidden#385` | `java.lang.Object` | 4/15 | — | 内部辅助/管理类；主要涉及：方块/世界交互 | 中 |
| `class_534` | `pw.hachimi.client.\u0000oi\u200E` | `hidden#752` | `class_806 / pw.hachimi.client.\u0000ot\u200E` | 8/13 | — | 客户端功能模块实现；行为域：数据包/网络、物品/背包、方块/世界交互 | 中 |
| `class_535` | `pw.hachimi.client.\u0000hn\u200E` | `hidden#525` | `class_883 / pw.hachimi.client.\u0000mZ\u200E` | 5/11 | — | Brigadier 客户端命令实现 | 高 |
| `class_536` | `pw.hachimi.client.\u0000i\u200E` | `hidden#257` | `class_805 / pw.hachimi.client.\u0000kb\u200E` | 39/50 | ID 0 / 2 methods | 客户端功能模块实现；行为域：物品/背包、方块/世界交互、实体/战斗 | 中 |
| `class_537` | `pw.hachimi.client.\u0000ie\u200E` | `hidden#042` | `class_919 / pw.hachimi.client.\u0000nZ\u200E` | 2/3 | — | 事件对象/事件上下文；领域：具体事件类型需运行时字符串/调用点确认 | 中-低 |
| `class_538` | `pw.hachimi.client.\u0000jv\u200E` | `hidden#581` | `java.lang.Enum` | 5/8 | — | 枚举：模式/类别/状态选项（枚举常量名称多数已加密） | 中 |
| `class_539` | `pw.hachimi.client.\u0000A\u200E` | `hidden#103` | `class_874 / pw.hachimi.client.\u0000re\u200E` | 8/16 | — | 客户端功能模块实现；行为域：数据包/网络 | 中 |
| `class_540` | `pw.hachimi.client.\u0000fM\u200E` | `hidden#154` | `class_805 / pw.hachimi.client.\u0000kb\u200E` | 21/20 | ID 26 / 1 methods | 客户端功能模块实现；行为域：物品/背包、方块/世界交互、移动/玩家状态 | 中 |
| `class_541` | `pw.hachimi.client.\u0000on\u200E` | `hidden#815` | `class_874 / pw.hachimi.client.\u0000re\u200E` | 5/8 | — | 客户端功能模块实现；行为域：具体功能名被字符串加密隐藏 | 中-低 |
| `class_542` | `pw.hachimi.client.\u0000cm\u200E` | `hidden#224` | `class_874 / pw.hachimi.client.\u0000re\u200E` | 13/24 | ID 64 / 2 methods | 客户端功能模块实现；行为域：数据包/网络、物品/背包、方块/世界交互 | 中 |
| `class_543` | `pw.hachimi.client.\u0000qk\u200E` | `hidden#678` | `class_805 / pw.hachimi.client.\u0000kb\u200E` | 25/22 | — | 客户端功能模块实现；行为域：数据包/网络、方块/世界交互、实体/战斗 | 中 |
| `class_544` | `pw.hachimi.client.\u0000mB\u200E` | `hidden#275` | `java.lang.Enum` | 5/8 | — | 枚举：模式/类别/状态选项（枚举常量名称多数已加密） | 中 |
| `class_545` | `pw.hachimi.client.\u0000pX\u200E` | `hidden#093` | `java.lang.Enum` | 5/8 | — | 枚举：模式/类别/状态选项（枚举常量名称多数已加密） | 中 |
| `class_546` | `pw.hachimi.client.\u0000pc\u200E` | `hidden#249` | `java.lang.Enum` | 4/8 | — | 枚举：模式/类别/状态选项（枚举常量名称多数已加密） | 中 |
| `class_547` | `pw.hachimi.client.\u0000lv\u200E` | `hidden#520` | `class_919 / pw.hachimi.client.\u0000nZ\u200E` | 2/3 | — | 事件对象/事件上下文；领域：具体事件类型需运行时字符串/调用点确认 | 中-低 |
| `class_548` | `pw.hachimi.client.\u0000oj\u200E` | `hidden#766` | `class_874 / pw.hachimi.client.\u0000re\u200E` | 16/22 | — | 客户端功能模块实现；行为域：数据包/网络、实体/战斗、移动/玩家状态 | 中 |
| `class_549` | `pw.hachimi.client.\u0000fH\u200E` | `hidden#113` | `class_805 / pw.hachimi.client.\u0000kb\u200E` | 43/46 | — | 客户端功能模块实现；行为域：数据包/网络、方块/世界交互、实体/战斗 | 中 |
| `class_550` | `pw.hachimi.client.\u0000nB\u200E` | `hidden#705` | `java.lang.Enum` | 5/8 | — | 枚举：模式/类别/状态选项（枚举常量名称多数已加密） | 中 |
| `class_551` | `pw.hachimi.client.\u0000oz\u200E` | `hidden#135` | `java.lang.Enum` | 5/8 | — | 枚举：模式/类别/状态选项（枚举常量名称多数已加密） | 中 |
| `class_552` | `pw.hachimi.client.\u0000jm\u200E` | `hidden#512` | `java.lang.Object` | 2/5 | — | 内部辅助类/值对象/适配器；精确业务名受混淆影响 | 低 |
| `class_553` | `pw.hachimi.client.\u0000db\u200E` | `hidden#492` | `java.lang.Enum` | 4/8 | — | 枚举：模式/类别/状态选项（枚举常量名称多数已加密） | 中 |
| `class_554` | `pw.hachimi.client.\u0000qH\u200E` | `hidden#301` | `class_874 / pw.hachimi.client.\u0000re\u200E` | 18/29 | — | 客户端功能模块实现；行为域：渲染/HUD、数据包/网络、实体/战斗 | 中 |
| `class_555` | `pw.hachimi.client.\u0000lV\u200E` | `hidden#117` | `class_805 / pw.hachimi.client.\u0000kb\u200E` | 45/46 | — | 客户端功能模块实现；行为域：数据包/网络、方块/世界交互、实体/战斗 | 中 |
| `class_556` | `pw.hachimi.client.\u0000ks\u200E` | `hidden#144` | `class_919 / pw.hachimi.client.\u0000nZ\u200E` | 3/6 | — | 事件对象/事件上下文；领域：具体事件类型需运行时字符串/调用点确认 | 中-低 |
| `class_557` | `pw.hachimi.client.\u0000kw\u200E` | `hidden#204` | `java.lang.Enum` | 9/8 | — | 枚举：模式/类别/状态选项（枚举常量名称多数已加密） | 中 |
| `class_558` | `pw.hachimi.client.\u0000ab\u200E` | `hidden#207` | `class_874 / pw.hachimi.client.\u0000re\u200E` | 31/44 | ID 4 / 3 methods | 客户端功能模块实现；行为域：数据包/网络、方块/世界交互、实体/战斗 | 中 |
| `class_559` | `pw.hachimi.client.\u0000fC\u200E` | `hidden#102` | `class_805 / pw.hachimi.client.\u0000kb\u200E` | 27/33 | — | 客户端功能模块实现；行为域：物品/背包、方块/世界交互、实体/战斗 | 中 |
| `class_560` | `pw.hachimi.client.\u0000dk\u200E` | `hidden#585` | `java.lang.Object` | 2/4 | ID 37 / 1 methods | 内部辅助类/值对象/适配器；精确业务名受混淆影响 | 低 |
| `class_561` | `pw.hachimi.client.\u0000oy\u200E` | `hidden#068` | `java.lang.Enum` | 6/8 | — | 枚举：模式/类别/状态选项（枚举常量名称多数已加密） | 中 |
| `class_562` | `pw.hachimi.client.\u0000dn\u200E` | `hidden#621` | `class_874 / pw.hachimi.client.\u0000re\u200E` | 14/34 | — | 客户端功能模块实现；行为域：数据包/网络、方块/世界交互、实体/战斗 | 中 |
| `class_563` | `pw.hachimi.client.\u0000qY\u200E` | `hidden#495` | `class_805 / pw.hachimi.client.\u0000kb\u200E` | 17/16 | ID 17 / 1 methods | 客户端功能模块实现；行为域：物品/背包、实体/战斗 | 中 |
| `class_564` | `pw.hachimi.client.\u0000kf\u200E` | `hidden#820` | `java.lang.Object` | 4/12 | — | 自定义 Brigadier 命令参数解析器 | 高 |
| `class_565` | `pw.hachimi.client.\u0000jP\u200E` | `hidden#145` | `class_806 / pw.hachimi.client.\u0000ot\u200E` | 17/21 | — | 客户端功能模块实现；行为域：数据包/网络、物品/背包 | 中 |
| `class_566` | `pw.hachimi.client.\u0000J\u200E` | `hidden#634` | `class_919 / pw.hachimi.client.\u0000nZ\u200E` | 2/3 | — | 事件对象/事件上下文；领域：具体事件类型需运行时字符串/调用点确认 | 中-低 |
| `class_567` | `pw.hachimi.client.\u0000ms\u200E` | `hidden#095` | `java.lang.Enum` | 5/8 | — | 枚举：模式/类别/状态选项（枚举常量名称多数已加密） | 中 |
| `class_568` | `pw.hachimi.client.\u0000os\u200E` | `hidden#045` | `class_572 / pw.hachimi.client.\u0000md\u200E` | 1/4 | — | 内部辅助类/值对象/适配器；精确业务名受混淆影响 | 低 |
| `class_569` | `pw.hachimi.client.\u0000lS\u200E` | `hidden#131` | `class_572 / pw.hachimi.client.\u0000md\u200E` | 1/4 | — | 内部辅助类/值对象/适配器；精确业务名受混淆影响 | 低 |
| `class_570` | `pw.hachimi.client.\u0000lJ\u200E` | `hidden#040` | `class_572 / pw.hachimi.client.\u0000md\u200E` | 1/4 | — | 内部辅助类/值对象/适配器；精确业务名受混淆影响 | 低 |
| `class_571` | `pw.hachimi.client.\u0000gL\u200E` | `hidden#536` | `class_572 / pw.hachimi.client.\u0000md\u200E` | 1/4 | — | 内部辅助类/值对象/适配器；精确业务名受混淆影响 | 低 |
| `class_572` | `pw.hachimi.client.\u0000md\u200E` | `hidden#692` | `java.lang.Enum` | 6/12 | — | 枚举：模式/类别/状态选项（枚举常量名称多数已加密） | 中 |
| `class_573` | `pw.hachimi.client.\u0000eD\u200E` | `hidden#555` | `class_919 / pw.hachimi.client.\u0000nZ\u200E` | 3/5 | — | 事件对象/事件上下文；领域：具体事件类型需运行时字符串/调用点确认 | 中-低 |
| `class_574` | `pw.hachimi.client.\u0000ii\u200E` | `hidden#087` | `class_805 / pw.hachimi.client.\u0000kb\u200E` | 30/22 | — | 客户端功能模块实现；行为域：数据包/网络、物品/背包、方块/世界交互 | 中 |
| `class_575` | `pw.hachimi.client.\u0000nF\u200E` | `hidden#754` | `class_810 / pw.hachimi.client.\u0000aU\u200E` | 2/3 | — | 事件对象/事件上下文；领域：渲染/HUD | 中 |
| `class_576` | `pw.hachimi.client.\u0000b\u200E` | `hidden#521` | `class_919 / pw.hachimi.client.\u0000nZ\u200E` | 3/5 | — | 事件对象/事件上下文；领域：具体事件类型需运行时字符串/调用点确认 | 中-低 |
| `class_577` | `pw.hachimi.client.\u0000dF\u200E` | `hidden#192` | `class_805 / pw.hachimi.client.\u0000kb\u200E` | 35/43 | — | 客户端功能模块实现；行为域：数据包/网络、方块/世界交互、实体/战斗 | 中 |
| `class_578` | `pw.hachimi.client.\u0000kF\u200E` | `hidden#380` | `class_874 / pw.hachimi.client.\u0000re\u200E` | 6/10 | — | 客户端功能模块实现；行为域：具体功能名被字符串加密隐藏 | 中-低 |
| `class_579` | `pw.hachimi.client.\u0000gj\u200E` | `hidden#134` | `java.lang.Enum` | 5/8 | — | 枚举：模式/类别/状态选项（枚举常量名称多数已加密） | 中 |
| `class_580` | `pw.hachimi.client.\u0000oN\u200E` | `hidden#377` | `class_919 / pw.hachimi.client.\u0000nZ\u200E` | 3/5 | — | 事件对象/事件上下文；领域：物品/背包 | 中 |
| `class_581` | `pw.hachimi.client.\u0000lu\u200E` | `hidden#558` | `java.lang.Enum` | 7/9 | — | 枚举：模式/类别/状态选项（枚举常量名称多数已加密） | 中 |
| `class_582` | `pw.hachimi.client.\u0000dK\u200E` | `hidden#240` | `class_919 / pw.hachimi.client.\u0000nZ\u200E` | 2/3 | — | 事件对象/事件上下文；领域：具体事件类型需运行时字符串/调用点确认 | 中-低 |
| `class_583` | `pw.hachimi.client.\u0000mG\u200E` | `hidden#338` | `class_874 / pw.hachimi.client.\u0000re\u200E` | 31/27 | ID 8 / 1 methods | 客户端功能模块实现；行为域：数据包/网络、物品/背包、实体/战斗 | 中 |
| `class_584` | `pw.hachimi.client.\u0000ir\u200E` | `hidden#179` | `class_874 / pw.hachimi.client.\u0000re\u200E` | 11/28 | — | 客户端功能模块实现；行为域：命令 | 中 |
| `class_585` | `pw.hachimi.client.\u0000nL\u200E` | `hidden#779` | `java.lang.Record` | 8/13 | — | 数据载体/Record-like；组件：damageData、attackTarget、damage、selfDamage、blockPos、antiSurround、support | 高 |
| `class_586` | `pw.hachimi.client.\u0000gF\u200E` | `hidden#481` | `java.lang.Object` | 5/19 | — | 内部辅助/管理类；主要涉及：文件/IO、HTTP/网络 | 中 |
| `class_587` | `pw.hachimi.client.\u0000jo\u200E` | `hidden#488` | `class_874 / pw.hachimi.client.\u0000re\u200E` | 12/16 | — | 客户端功能模块实现；行为域：渲染/HUD | 中 |
| `class_588` | `pw.hachimi.client.\u0000qI\u200E` | `hidden#315` | `java.lang.Object` | 3/5 | — | 内部辅助类/值对象/适配器；精确业务名受混淆影响 | 低 |
| `class_589` | `pw.hachimi.client.\u0000pN\u200E` | `hidden#804` | `java.lang.Object` | 5/11 | — | 内部辅助/管理类；主要涉及：渲染/HUD、方块/世界交互、移动/玩家状态 | 中 |
| `class_590` | `pw.hachimi.client.\u0000bV\u200E` | `hidden#387` | `java.lang.Record` | 3/8 | — | 数据载体/Record-like；组件：pos、damage | 高 |
| `class_591` | `pw.hachimi.client.\u0000kO\u200E` | `hidden#478` | `java.lang.Enum` | 5/12 | — | 枚举：模式/类别/状态选项（枚举常量名称多数已加密） | 中 |
| `class_592` | `pw.hachimi.client.\u0000fs\u200E` | `hidden#637` | `class_919 / pw.hachimi.client.\u0000nZ\u200E` | 3/5 | — | 事件对象/事件上下文；领域：方块/世界交互 | 中 |
| `class_593` | `pw.hachimi.client.\u0000ag\u200E` | `hidden#255` | `java.lang.Enum` | 4/8 | — | 枚举：模式/类别/状态选项（枚举常量名称多数已加密） | 中 |
| `class_594` | `pw.hachimi.client.\u0000mF\u200E` | `hidden#324` | `java.lang.Enum` | 5/8 | — | 枚举：模式/类别/状态选项（枚举常量名称多数已加密） | 中 |
| `class_595` | `pw.hachimi.client.\u0000nn\u200E` | `hidden#392` | `java.lang.Enum` | 8/13 | — | 枚举：模式/类别/状态选项（枚举常量名称多数已加密） | 中 |
| `class_596` | `pw.hachimi.client.\u0000S\u200E` | `hidden#505` | `java.lang.Enum` | 5/8 | — | 枚举：模式/类别/状态选项（枚举常量名称多数已加密） | 中 |
| `class_597` | `pw.hachimi.client.\u0000bs\u200E` | `hidden#732` | `class_883 / pw.hachimi.client.\u0000mZ\u200E` | 5/12 | — | Brigadier 客户端命令实现 | 高 |
| `class_598` | `pw.hachimi.client.\u0000lF\u200E` | `hidden#797` | `java.lang.Enum` | 4/8 | — | 枚举：模式/类别/状态选项（枚举常量名称多数已加密） | 中 |
| `class_599` | `pw.hachimi.client.\u0000dr\u200E` | `hidden#674` | `class_919 / pw.hachimi.client.\u0000nZ\u200E` | 8/10 | — | 事件对象/事件上下文；领域：渲染/HUD | 中 |
| `class_600` | `pw.hachimi.client.\u0000dR\u200E` | `hidden#283` | `java.lang.Enum` | 5/8 | — | 枚举：模式/类别/状态选项（枚举常量名称多数已加密） | 中 |
| `class_601` | `pw.hachimi.client.\u0000gR\u200E` | `hidden#666` | `class_919 / pw.hachimi.client.\u0000nZ\u200E` | 2/3 | — | 事件对象/事件上下文；领域：具体事件类型需运行时字符串/调用点确认 | 中-低 |
| `class_602` | `pw.hachimi.client.\u0000fL\u200E` | `hidden#199` | `java.lang.Enum` | 9/16 | — | 枚举：模式/类别/状态选项（枚举常量名称多数已加密） | 中 |
| `class_603` | `pw.hachimi.client.\u0000cS\u200E` | `hidden#723` | `class_919 / pw.hachimi.client.\u0000nZ\u200E` | 4/6 | — | 事件对象/事件上下文；领域：具体事件类型需运行时字符串/调用点确认 | 中-低 |
| `class_604` | `pw.hachimi.client.z` | `visible` | `java.lang.Object` | 0/6 | — | 启动/注册分片：自身几乎无字段，按多个无参方法分批触发隐藏类初始化/注册 | 中-高 |
| `class_605` | `pw.hachimi.client.\u0000pG\u200E` | `hidden#716` | `class_874 / pw.hachimi.client.\u0000re\u200E` | 16/34 | ID 3 / 4 methods | 客户端功能模块实现；行为域：实体/战斗、移动/玩家状态 | 中 |
| `class_606` | `pw.hachimi.client.\u0000pd\u200E` | `hidden#263` | `java.lang.Object` | 3/9 | — | 内部辅助类/值对象/适配器；精确业务名受混淆影响 | 低 |
| `class_607` | `pw.hachimi.client.\u0000ah\u200E` | `hidden#210` | `class_608 / pw.hachimi.client.\u0000iG\u200E` | 2/3 | — | 事件对象/事件上下文；领域：具体事件类型需运行时字符串/调用点确认 | 中-低 |
| `class_608` | `pw.hachimi.client.\u0000iG\u200E` | `hidden#446` | `class_919 / pw.hachimi.client.\u0000nZ\u200E` | 3/5 | — | 事件对象/事件上下文；领域：具体事件类型需运行时字符串/调用点确认 | 中-低 |
| `class_609` | `pw.hachimi.client.\u0000eR\u200E` | `hidden#711` | `class_805 / pw.hachimi.client.\u0000kb\u200E` | 121/199 | — | 客户端功能模块实现；行为域：渲染/HUD、数据包/网络、物品/背包 | 中 |
| `class_610` | `pw.hachimi.client.\u0000ok\u200E` | `hidden#730` | `class_874 / pw.hachimi.client.\u0000re\u200E` | 7/20 | — | 客户端功能模块实现；行为域：渲染/HUD、移动/玩家状态 | 中 |
| `class_611` | `pw.hachimi.client.\u0000fB\u200E` | `hidden#090` | `class_874 / pw.hachimi.client.\u0000re\u200E` | 8/12 | — | 客户端功能模块实现；行为域：数据包/网络、物品/背包、方块/世界交互 | 中 |
| `class_612` | `pw.hachimi.client.\u0000d\u200E` | `hidden#299` | `class_874 / pw.hachimi.client.\u0000re\u200E` | 8/18 | — | 客户端功能模块实现；行为域：数据包/网络、物品/背包、实体/战斗 | 中 |
| `class_613` | `pw.hachimi.client.\u0000jB\u200E` | `hidden#799` | `class_874 / pw.hachimi.client.\u0000re\u200E` | 10/19 | — | 客户端功能模块实现；行为域：数据包/网络、物品/背包 | 中 |
| `class_614` | `pw.hachimi.client.\u0000D\u200E` | `hidden#713` | `java.lang.Enum` | 6/9 | — | 枚举：模式/类别/状态选项（枚举常量名称多数已加密） | 中 |
| `class_615` | `pw.hachimi.client.\u0000f\u200E` | `hidden#269` | `class_874 / pw.hachimi.client.\u0000re\u200E` | 15/16 | — | 客户端功能模块实现；行为域：物品/背包 | 中 |
| `class_616` | `pw.hachimi.client.\u0000nf\u200E` | `hidden#288` | `java.lang.Object` | 11/5 | — | 内部辅助/管理类；主要涉及：渲染/HUD | 中 |
| `class_617` | `pw.hachimi.client.\u0000a\u200E` | `hidden#556` | `java.lang.Object` | 5/9 | — | 内部辅助/管理类；主要涉及：渲染/HUD、方块/世界交互、移动/玩家状态 | 中 |
| `class_618` | `pw.hachimi.client.\u0000U\u200E` | `hidden#483` | `class_874 / pw.hachimi.client.\u0000re\u200E` | 5/8 | — | 客户端功能模块实现；行为域：具体功能名被字符串加密隐藏 | 中-低 |
| `class_619` | `pw.hachimi.client.\u0000hY\u200E` | `hidden#265` | `java.lang.Enum` | 6/11 | — | 枚举：模式/类别/状态选项（枚举常量名称多数已加密） | 中 |
| `class_620` | `pw.hachimi.client.f` | `visible` | `java.lang.Object` | 0/20 | — | 启动/注册分片：自身几乎无字段，按多个无参方法分批触发隐藏类初始化/注册 | 中-高 |
| `class_621` | `pw.hachimi.client.\u0000as\u200E` | `hidden#410` | `class_919 / pw.hachimi.client.\u0000nZ\u200E` | 12/4 | — | 事件对象/事件上下文；领域：渲染/HUD、实体/战斗 | 中 |
| `class_622` | `pw.hachimi.client.\u0000mg\u200E` | `hidden#725` | `class_805 / pw.hachimi.client.\u0000kb\u200E` | 53/56 | — | 客户端功能模块实现；行为域：渲染/HUD、物品/背包、方块/世界交互 | 中 |
| `class_623` | `pw.hachimi.client.\u0000qd\u200E` | `hidden#593` | `java.lang.Enum` | 5/8 | — | 枚举：模式/类别/状态选项（枚举常量名称多数已加密） | 中 |
| `class_624` | `pw.hachimi.client.\u0000en\u200E` | `hidden#238` | `class_919 / pw.hachimi.client.\u0000nZ\u200E` | 2/3 | — | 事件对象/事件上下文；领域：具体事件类型需运行时字符串/调用点确认 | 中-低 |
| `class_625` | `pw.hachimi.client.\u0000pO\u200E` | `hidden#816` | `class_805 / pw.hachimi.client.\u0000kb\u200E` | 23/32 | ID 6 / 1 methods | 客户端功能模块实现；行为域：方块/世界交互、实体/战斗、移动/玩家状态 | 中 |
| `class_626` | `pw.hachimi.client.\u0000lB\u200E` | `hidden#756` | `class_883 / pw.hachimi.client.\u0000mZ\u200E` | 5/11 | — | Brigadier 客户端命令实现 | 高 |
| `class_627` | `pw.hachimi.client.\u0000ml\u200E` | `hidden#788` | `java.lang.Record` | 3/8 | — | 数据载体/Record-like；组件：pos、soundEvent | 高 |
| `class_628` | `pw.hachimi.client.\u0000bS\u200E` | `hidden#397` | `class_874 / pw.hachimi.client.\u0000re\u200E` | 11/16 | — | 客户端功能模块实现；行为域：渲染/HUD、物品/背包 | 中 |
| `class_629` | `pw.hachimi.client.\u0000iw\u200E` | `hidden#259` | `java.lang.Object` | 9/15 | — | 内部辅助/管理类；主要涉及：物品/背包 | 中 |
| `class_630` | `pw.hachimi.client.\u0000jU\u200E` | `hidden#163` | `java.lang.Enum` | 5/8 | — | 枚举：模式/类别/状态选项（枚举常量名称多数已加密） | 中 |
| `class_631` | `pw.hachimi.client.\u0000P\u200E` | `hidden#761` | `class_919 / pw.hachimi.client.\u0000nZ\u200E` | 2/3 | — | 事件对象/事件上下文；领域：具体事件类型需运行时字符串/调用点确认 | 中-低 |
| `class_632` | `pw.hachimi.client.\u0000qU\u200E` | `hidden#448` | `class_874 / pw.hachimi.client.\u0000re\u200E` | 26/22 | ID 22 / 1 methods | 客户端功能模块实现；行为域：实体/战斗、移动/玩家状态 | 中 |
| `class_633` | `pw.hachimi.client.\u0000rk\u200E` | `hidden#300` | `class_874 / pw.hachimi.client.\u0000re\u200E` | 12/15 | — | 客户端功能模块实现；行为域：具体功能名被字符串加密隐藏 | 中-低 |
| `class_634` | `pw.hachimi.client.\u0000nU\u200E` | `hidden#058` | `java.lang.Enum` | 6/8 | — | 枚举：模式/类别/状态选项（枚举常量名称多数已加密） | 中 |
| `class_635` | `pw.hachimi.client.\u0000az\u200E` | `hidden#500` | `class_919 / pw.hachimi.client.\u0000nZ\u200E` | 2/3 | — | 事件对象/事件上下文；领域：具体事件类型需运行时字符串/调用点确认 | 中-低 |
| `class_636` | `pw.hachimi.client.\u0000K\u200E` | `hidden#802` | `java.lang.Enum` | 5/8 | — | 枚举：模式/类别/状态选项（枚举常量名称多数已加密） | 中 |
| `class_637` | `pw.hachimi.client.\u0000nJ\u200E` | `hidden#807` | `class_874 / pw.hachimi.client.\u0000re\u200E` | 6/10 | — | 客户端功能模块实现；行为域：具体功能名被字符串加密隐藏 | 中-低 |
| `class_638` | `pw.hachimi.client.\u0000gf\u200E` | `hidden#100` | `java.lang.Enum` | 5/8 | — | 枚举：模式/类别/状态选项（枚举常量名称多数已加密） | 中 |
| `class_639` | `pw.hachimi.client.\u0000jO\u200E` | `hidden#132` | `java.lang.Object` | 6/10 | — | 内部辅助/管理类；主要涉及：物品/背包 | 中 |
| `class_640` | `pw.hachimi.client.\u0000ed\u200E` | `hidden#076` | `java.lang.Enum` | 5/8 | — | 枚举：模式/类别/状态选项（枚举常量名称多数已加密） | 中 |
| `class_641` | `pw.hachimi.client.\u0000of\u200E` | `hidden#717` | `class_874 / pw.hachimi.client.\u0000re\u200E` | 17/29 | ID 63 / 1 methods | 客户端功能模块实现；行为域：物品/背包 | 中 |
| `class_642` | `pw.hachimi.client.\u0000fA\u200E` | `hidden#026` | `java.lang.Enum` | 5/8 | — | 枚举：模式/类别/状态选项（枚举常量名称多数已加密） | 中 |
| `class_643` | `pw.hachimi.client.\u0000lI\u200E` | `hidden#791` | `class_644 / pw.hachimi.client.\u0000iQ\u200E` | 2/3 | — | 事件对象/事件上下文；领域：具体事件类型需运行时字符串/调用点确认 | 中-低 |
| `class_644` | `pw.hachimi.client.\u0000iQ\u200E` | `hidden#597` | `class_919 / pw.hachimi.client.\u0000nZ\u200E` | 3/6 | — | 事件对象/事件上下文；领域：渲染/HUD | 中 |
| `class_645` | `pw.hachimi.client.\u0000aj\u200E` | `hidden#291` | `java.lang.Enum` | 8/8 | — | 枚举：模式/类别/状态选项（枚举常量名称多数已加密） | 中 |
| `class_646` | `pw.hachimi.client.\u0000pg\u200E` | `hidden#302` | `class_805 / pw.hachimi.client.\u0000kb\u200E` | 36/40 | — | 客户端功能模块实现；行为域：数据包/网络、物品/背包、实体/战斗 | 中 |
| `class_647` | `pw.hachimi.client.\u0000dQ\u200E` | `hidden#270` | `class_919 / pw.hachimi.client.\u0000nZ\u200E` | 3/4 | — | 事件对象/事件上下文；领域：具体事件类型需运行时字符串/调用点确认 | 中-低 |
| `class_648` | `pw.hachimi.client.\u0000bA\u200E` | `hidden#127` | `class_874 / pw.hachimi.client.\u0000re\u200E` | 24/26 | — | 客户端功能模块实现；行为域：物品/背包、方块/世界交互、实体/战斗 | 中 |
| `class_649` | `pw.hachimi.client.\u0000iV\u200E` | `hidden#665` | `java.lang.Object` | 2/55 | — | 内部辅助/管理类；主要涉及：数据包/网络、物品/背包、方块/世界交互、实体/战斗 | 中 |
| `class_650` | `pw.hachimi.client.\u0000jN\u200E` | `hidden#073` | `java.lang.Enum` | 8/15 | — | 枚举：模式/类别/状态选项（枚举常量名称多数已加密） | 中 |
| `class_651` | `pw.hachimi.client.\u0000fT\u200E` | `hidden#311` | `class_805 / pw.hachimi.client.\u0000kb\u200E` | 17/24 | ID 42 / 1 methods | 客户端功能模块实现；行为域：数据包/网络、物品/背包、方块/世界交互 | 中 |
| `class_652` | `pw.hachimi.client.\u0000kC\u200E` | `hidden#340` | `net.minecraft.class_4185` | 3/5 | — | 内部辅助/管理类；主要涉及：渲染/HUD | 中 |
| `class_653` | `pw.hachimi.client.mixin.m` | `visible` | `java.lang.Object` | 0/1 | — | Mixin 注入/拦截类 | 高 |
| `class_654` | `pw.hachimi.client.\u0000oE\u200E` | `hidden#316` | `java.lang.Enum` | 5/8 | — | 枚举：模式/类别/状态选项（枚举常量名称多数已加密） | 中 |
| `class_655` | `pw.hachimi.client.\u0000dX\u200E` | `hidden#416` | `class_919 / pw.hachimi.client.\u0000nZ\u200E` | 2/3 | — | 事件对象/事件上下文；领域：具体事件类型需运行时字符串/调用点确认 | 中-低 |
| `class_656` | `pw.hachimi.client.\u0000fx\u200E` | `hidden#746` | `class_805 / pw.hachimi.client.\u0000kb\u200E` | 17/32 | ID 48 / 1 methods | 客户端功能模块实现；行为域：物品/背包、方块/世界交互、移动/玩家状态 | 中 |
| `class_657` | `pw.hachimi.client.\u0000kx\u200E` | `hidden#161` | `java.lang.Record` | 6/11 | — | 数据载体/Record-like；组件：entity、ticks、playerPos、offset、speed | 高 |
| `class_658` | `pw.hachimi.client.\u0000ao\u200E` | `hidden#361` | `java.lang.Enum` | 6/8 | — | 枚举：模式/类别/状态选项（枚举常量名称多数已加密） | 中 |
| `class_659` | `pw.hachimi.client.m` | `visible` | `java.lang.Object` | 0/25 | — | 启动/注册分片：自身几乎无字段，按多个无参方法分批触发隐藏类初始化/注册 | 中-高 |
| `class_660` | `pw.hachimi.client.\u0000nu\u200E` | `hidden#509` | `class_908 / pw.hachimi.client.\u0000qt\u200E` | 5/18 | ID 55 / 3 methods | 内部辅助/管理类；主要涉及：方块/世界交互 | 中 |
| `class_661` | `pw.hachimi.client.\u0000g\u200E` | `hidden#280` | `java.lang.Enum` | 5/8 | — | 枚举：模式/类别/状态选项（枚举常量名称多数已加密） | 中 |
| `class_662` | `pw.hachimi.client.\u0000nd\u200E` | `hidden#317` | `class_883 / pw.hachimi.client.\u0000mZ\u200E` | 5/12 | — | Brigadier 客户端命令实现 | 高 |
| `class_663` | `pw.hachimi.client.\u0000nP\u200E` | `hidden#006` | `class_874 / pw.hachimi.client.\u0000re\u200E` | 10/14 | — | 客户端功能模块实现；行为域：方块/世界交互 | 中 |
| `class_664` | `pw.hachimi.client.\u0000gw\u200E` | `hidden#307` | `java.lang.Enum` | 3/8 | — | 枚举：模式/类别/状态选项（枚举常量名称多数已加密） | 中 |
| `class_665` | `pw.hachimi.client.\u0000gD\u200E` | `hidden#503` | `class_874 / pw.hachimi.client.\u0000re\u200E` | 8/11 | — | 客户端功能模块实现；行为域：具体功能名被字符串加密隐藏 | 中-低 |
| `class_666` | `pw.hachimi.client.\u0000I\u200E` | `hidden#624` | `class_698 / pw.hachimi.client.\u0000hE\u200E` | 1/3 | — | 事件对象/事件上下文；领域：具体事件类型需运行时字符串/调用点确认 | 中-低 |
| `class_667` | `pw.hachimi.client.\u0000dm\u200E` | `hidden#656` | `class_874 / pw.hachimi.client.\u0000re\u200E` | 7/10 | — | 客户端功能模块实现；行为域：数据包/网络、移动/玩家状态 | 中 |
| `class_668` | `pw.hachimi.client.\u0000eX\u200E` | `hidden#739` | `class_747 / pw.hachimi.client.\u0000in\u200E` | 9/20 | — | 内部辅助/管理类；主要涉及：渲染/HUD | 中 |
| `class_669` | `pw.hachimi.client.\u0000h\u200E` | `hidden#243` | `class_919 / pw.hachimi.client.\u0000nZ\u200E` | 3/5 | — | 事件对象/事件上下文；领域：具体事件类型需运行时字符串/调用点确认 | 中-低 |
| `class_670` | `pw.hachimi.client.\u0000bm\u200E` | `hidden#714` | `class_810 / pw.hachimi.client.\u0000aU\u200E` | 5/11 | — | 事件对象/事件上下文；领域：渲染/HUD | 中 |
| `class_671` | `pw.hachimi.client.\u0000hQ\u200E` | `hidden#153` | `class_919 / pw.hachimi.client.\u0000nZ\u200E` | 3/4 | — | 事件对象/事件上下文；领域：具体事件类型需运行时字符串/调用点确认 | 中-低 |
| `class_672` | `pw.hachimi.client.\u0000oF\u200E` | `hidden#274` | `class_810 / pw.hachimi.client.\u0000aU\u200E` | 2/3 | — | 事件对象/事件上下文；领域：渲染/HUD | 中 |
| `class_673` | `pw.hachimi.client.\u0000gm\u200E` | `hidden#120` | `Screen` | 12/21 | — | Minecraft GUI Screen/界面实现 | 高 |
| `class_674` | `pw.hachimi.client.\u0000hr\u200E` | `hidden#573` | `class_919 / pw.hachimi.client.\u0000nZ\u200E` | 2/3 | — | 事件对象/事件上下文；领域：具体事件类型需运行时字符串/调用点确认 | 中-低 |
| `class_675` | `pw.hachimi.client.\u0000z\u200E` | `hidden#008` | `java.lang.Enum` | 5/8 | — | 枚举：模式/类别/状态选项（枚举常量名称多数已加密） | 中 |
| `class_676` | `pw.hachimi.client.\u0000aE\u200E` | `hidden#648` | `class_919 / pw.hachimi.client.\u0000nZ\u200E` | 2/3 | — | 事件对象/事件上下文；领域：具体事件类型需运行时字符串/调用点确认 | 中-低 |
| `class_677` | `pw.hachimi.client.\u0000jg\u200E` | `hidden#458` | `class_919 / pw.hachimi.client.\u0000nZ\u200E` | 5/8 | — | 事件对象/事件上下文；领域：实体/战斗、移动/玩家状态 | 中 |
| `class_678` | `pw.hachimi.client.mixin.ae` | `visible` | `java.lang.Object` | 0/1 | — | Mixin 注入/拦截类 | 高 |
| `class_679` | `pw.hachimi.client.mixin.b` | `visible` | `java.lang.Object` | 0/4 | — | Mixin 注入/拦截类 | 高 |
| `class_680` | `pw.hachimi.client.mixin.aL` | `visible` | `java.lang.Object` | 0/4 | — | Mixin 注入/拦截类 | 高 |
| `class_681` | `pw.hachimi.client.\u0000fn\u200E` | `hidden#575` | `java.lang.Record` | 10/15 | — | 数据载体/Record-like；组件：x、y、r、g、b、glyph、matrix4f、a、mode | 高 |
| `class_682` | `pw.hachimi.client.\u0000bx\u200E` | `hidden#032` | `class_919 / pw.hachimi.client.\u0000nZ\u200E` | 2/3 | — | 事件对象/事件上下文；领域：具体事件类型需运行时字符串/调用点确认 | 中-低 |
| `class_683` | `pw.hachimi.client.\u0000aQ\u200E` | `hidden#795` | `java.lang.Enum` | 4/8 | — | 枚举：模式/类别/状态选项（枚举常量名称多数已加密） | 中 |
| `class_684` | `pw.hachimi.client.\u0000k\u200E` | `hidden#235` | `class_874 / pw.hachimi.client.\u0000re\u200E` | 14/20 | — | 客户端功能模块实现；行为域：数据包/网络 | 中 |
| `class_685` | `pw.hachimi.client.w` | `visible` | `java.lang.Object` | 0/8 | — | 启动/注册分片：自身几乎无字段，按多个无参方法分批触发隐藏类初始化/注册 | 中-高 |
| `class_686` | `pw.hachimi.client.\u0000N\u200E` | `hidden#786` | `class_874 / pw.hachimi.client.\u0000re\u200E` | 21/30 | — | 客户端功能模块实现；行为域：渲染/HUD、数据包/网络、物品/背包 | 中 |
| `class_687` | `pw.hachimi.client.\u0000kY\u200E` | `hidden#652` | `class_874 / pw.hachimi.client.\u0000re\u200E` | 12/14 | — | 客户端功能模块实现；行为域：具体功能名被字符串加密隐藏 | 中-低 |
| `class_688` | `pw.hachimi.client.\u0000pz\u200E` | `hidden#474` | `java.lang.Enum` | 5/8 | — | 枚举：模式/类别/状态选项（枚举常量名称多数已加密） | 中 |
| `class_689` | `pw.hachimi.client.\u0000l\u200E` | `hidden#404` | `class_908 / pw.hachimi.client.\u0000qt\u200E` | 5/18 | ID 49 / 3 methods | 内部辅助/管理类；主要涉及：物品/背包 | 中 |
| `class_690` | `pw.hachimi.client.\u0000ct\u200E` | `hidden#320` | `class_874 / pw.hachimi.client.\u0000re\u200E` | 13/20 | — | 客户端功能模块实现；行为域：具体功能名被字符串加密隐藏 | 中-低 |
| `class_691` | `pw.hachimi.client.ab` | `visible` | `java.lang.Object` | 0/4 | — | 启动/注册分片：自身几乎无字段，按多个无参方法分批触发隐藏类初始化/注册 | 中-高 |
| `class_692` | `pw.hachimi.client.\u0000qc\u200E` | `hidden#578` | `java.lang.Object` | 7/4 | — | 内部辅助类/值对象/适配器；精确业务名受混淆影响 | 低 |
| `class_693` | `pw.hachimi.client.\u0000oh\u200E` | `hidden#690` | `class_870 / pw.hachimi.client.\u0000hR\u200E` | 3/10 | — | 内部辅助类/值对象/适配器；精确业务名受混淆影响 | 低 |
| `class_694` | `pw.hachimi.client.\u0000kv\u200E` | `hidden#190` | `java.lang.Object` | 8/14 | — | 内部辅助/管理类；主要涉及：数据包/网络、移动/玩家状态 | 中 |
| `class_695` | `pw.hachimi.client.\u0000au\u200E` | `hidden#388` | `class_880 / pw.hachimi.client.\u0000by\u200E` | 6/18 | ID 47 / 1 methods | 内部辅助类/值对象/适配器；精确业务名受混淆影响 | 低 |
| `class_696` | `pw.hachimi.client.\u0000pH\u200E` | `hidden#680` | `java.lang.Object` | 11/22 | — | 内部辅助类/值对象/适配器；精确业务名受混淆影响 | 低 |
| `class_697` | `pw.hachimi.client.\u0000bv\u200E` | `hidden#774` | `java.lang.Enum` | 5/8 | — | 枚举：模式/类别/状态选项（枚举常量名称多数已加密） | 中 |
| `class_698` | `pw.hachimi.client.\u0000hE\u200E` | `hidden#025` | `class_919 / pw.hachimi.client.\u0000nZ\u200E` | 3/5 | — | 事件对象/事件上下文；领域：具体事件类型需运行时字符串/调用点确认 | 中-低 |
| `class_699` | `pw.hachimi.client.\u0000kR\u200E` | `hidden#560` | `class_919 / pw.hachimi.client.\u0000nZ\u200E` | 5/9 | — | 事件对象/事件上下文；领域：具体事件类型需运行时字符串/调用点确认 | 中-低 |
| `class_700` | `pw.hachimi.client.\u0000qB\u200E` | `hidden#158` | `java.lang.Enum` | 8/8 | — | 枚举：模式/类别/状态选项（枚举常量名称多数已加密） | 中 |
| `class_701` | `pw.hachimi.client.\u0000ex\u200E` | `hidden#328` | `class_874 / pw.hachimi.client.\u0000re\u200E` | 14/10 | — | 客户端功能模块实现；行为域：具体功能名被字符串加密隐藏 | 中-低 |
| `class_702` | `pw.hachimi.client.\u0000gN\u200E` | `hidden#608` | `class_919 / pw.hachimi.client.\u0000nZ\u200E` | 3/5 | — | 事件对象/事件上下文；领域：物品/背包 | 中 |
| `class_703` | `pw.hachimi.client.\u0000pK\u200E` | `hidden#767` | `java.lang.Enum` | 4/8 | — | 枚举：模式/类别/状态选项（枚举常量名称多数已加密） | 中 |
| `class_704` | `pw.hachimi.client.\u0000pa\u200E` | `hidden#159` | `java.lang.Enum` | 5/8 | — | 枚举：模式/类别/状态选项（枚举常量名称多数已加密） | 中 |
| `class_705` | `pw.hachimi.client.\u0000qK\u200E` | `hidden#286` | `class_747 / pw.hachimi.client.\u0000in\u200E` | 10/25 | — | 内部辅助/管理类；主要涉及：渲染/HUD | 中 |
| `class_706` | `pw.hachimi.client.\u0000cJ\u200E` | `hidden#659` | `java.lang.Record` | 3/8 | — | 数据载体/Record-like；组件：pos、time | 高 |
| `class_707` | `pw.hachimi.client.\u0000bj\u200E` | `hidden#622` | `class_919 / pw.hachimi.client.\u0000nZ\u200E` | 3/5 | — | 事件对象/事件上下文；领域：具体事件类型需运行时字符串/调用点确认 | 中-低 |
| `class_708` | `pw.hachimi.client.\u0000kp\u200E` | `hidden#059` | `class_908 / pw.hachimi.client.\u0000qt\u200E` | 7/34 | ID 69 / 3 methods | 内部辅助/管理类；主要涉及：渲染/HUD | 中 |
| `class_709` | `pw.hachimi.client.\u0000t\u200E` | `hidden#091` | `class_713 / pw.hachimi.client.\u0000jH\u200E` | 1/12 | — | 内部辅助/管理类；主要涉及：渲染/HUD | 中 |
| `class_710` | `pw.hachimi.client.\u0000x\u200E` | `hidden#038` | `class_713 / pw.hachimi.client.\u0000jH\u200E` | 1/12 | — | 内部辅助/管理类；主要涉及：渲染/HUD | 中 |
| `class_711` | `pw.hachimi.client.\u0000ds\u200E` | `hidden#686` | `class_713 / pw.hachimi.client.\u0000jH\u200E` | 1/11 | — | 内部辅助/管理类；主要涉及：渲染/HUD | 中 |
| `class_712` | `pw.hachimi.client.\u0000bI\u200E` | `hidden#211` | `class_805 / pw.hachimi.client.\u0000kb\u200E` | 20/23 | ID 33 / 1 methods | 客户端功能模块实现；行为域：渲染/HUD、方块/世界交互、实体/战斗 | 中 |
| `class_713` | `pw.hachimi.client.\u0000jH\u200E` | `hidden#012` | `java.lang.Enum` | 5/18 | — | 枚举：模式/类别/状态选项（枚举常量名称多数已加密） | 中 |
| `class_714` | `pw.hachimi.client.\u0000j\u200E` | `hidden#212` | `class_874 / pw.hachimi.client.\u0000re\u200E` | 17/20 | — | 客户端功能模块实现；行为域：实体/战斗、移动/玩家状态 | 中 |
| `class_715` | `pw.hachimi.client.\u0000eF\u200E` | `hidden#529` | `java.lang.Object` | 6/16 | — | 内部辅助类/值对象/适配器；精确业务名受混淆影响 | 低 |
| `class_716` | `pw.hachimi.client.\u0000lw\u200E` | `hidden#539` | `java.lang.Object` | 8/8 | — | 内部辅助/管理类；主要涉及：方块/世界交互 | 中 |
| `class_717` | `pw.hachimi.client.\u0000bX\u200E` | `hidden#466` | `class_874 / pw.hachimi.client.\u0000re\u200E` | 10/16 | — | 客户端功能模块实现；行为域：数据包/网络、物品/背包 | 中 |
| `class_718` | `pw.hachimi.client.\u0000pS\u200E` | `hidden#029` | `class_919 / pw.hachimi.client.\u0000nZ\u200E` | 3/5 | — | 事件对象/事件上下文；领域：具体事件类型需运行时字符串/调用点确认 | 中-低 |
| `class_719` | `pw.hachimi.client.\u0000jA\u200E` | `hidden#737` | `java.lang.Object` | 3/10 | — | 内部辅助/管理类；主要涉及：数据包/网络、实体/战斗 | 中 |
| `class_720` | `pw.hachimi.client.\u0000ef\u200E` | `hidden#149` | `class_919 / pw.hachimi.client.\u0000nZ\u200E` | 2/3 | — | 事件对象/事件上下文；领域：具体事件类型需运行时字符串/调用点确认 | 中-低 |
| `class_721` | `pw.hachimi.client.\u0000qA\u200E` | `hidden#203` | `class_874 / pw.hachimi.client.\u0000re\u200E` | 39/65 | ID 21 / 1 methods | 客户端功能模块实现；行为域：渲染/HUD | 中 |
| `class_722` | `pw.hachimi.client.\u0000cu\u200E` | `hidden#334` | `class_874 / pw.hachimi.client.\u0000re\u200E` | 8/13 | ID 18 / 1 methods | 客户端功能模块实现；行为域：数据包/网络、实体/战斗 | 中 |
| `class_723` | `pw.hachimi.client.\u0000qh\u200E` | `hidden#641` | `class_919 / pw.hachimi.client.\u0000nZ\u200E` | 2/3 | — | 事件对象/事件上下文；领域：具体事件类型需运行时字符串/调用点确认 | 中-低 |
| `class_724` | `pw.hachimi.client.\u0000qo\u200E` | `hidden#728` | `java.lang.Enum` | 4/8 | — | 枚举：模式/类别/状态选项（枚举常量名称多数已加密） | 中 |
| `class_725` | `pw.hachimi.client.\u0000am\u200E` | `hidden#285` | `class_743 / pw.hachimi.client.\u0000rc\u200E` | 1/4 | — | 内部辅助类/值对象/适配器；精确业务名受混淆影响 | 低 |
| `class_726` | `pw.hachimi.client.\u0000cM\u200E` | `hidden#695` | `class_743 / pw.hachimi.client.\u0000rc\u200E` | 1/4 | — | 内部辅助类/值对象/适配器；精确业务名受混淆影响 | 低 |
| `class_727` | `pw.hachimi.client.\u0000cR\u200E` | `hidden#758` | `class_743 / pw.hachimi.client.\u0000rc\u200E` | 1/5 | — | 内部辅助类/值对象/适配器；精确业务名受混淆影响 | 低 |
| `class_728` | `pw.hachimi.client.\u0000ba\u200E` | `hidden#531` | `class_743 / pw.hachimi.client.\u0000rc\u200E` | 1/4 | — | 内部辅助类/值对象/适配器；精确业务名受混淆影响 | 低 |
| `class_729` | `pw.hachimi.client.\u0000v\u200E` | `hidden#066` | `class_919 / pw.hachimi.client.\u0000nZ\u200E` | 2/3 | — | 事件对象/事件上下文；领域：具体事件类型需运行时字符串/调用点确认 | 中-低 |
| `class_730` | `pw.hachimi.client.\u0000oG\u200E` | `hidden#289` | `class_731 / pw.hachimi.client.\u0000E\u200E` | 2/3 | — | 事件对象/事件上下文；领域：具体事件类型需运行时字符串/调用点确认 | 中-低 |
| `class_731` | `pw.hachimi.client.\u0000E\u200E` | `hidden#675` | `class_919 / pw.hachimi.client.\u0000nZ\u200E` | 2/3 | — | 事件对象/事件上下文；领域：具体事件类型需运行时字符串/调用点确认 | 中-低 |
| `class_732` | `pw.hachimi.client.\u0000fu\u200E` | `hidden#709` | `java.lang.Enum` | 5/8 | — | 枚举：模式/类别/状态选项（枚举常量名称多数已加密） | 中 |
| `class_733` | `pw.hachimi.client.\u0000jc\u200E` | `hidden#400` | `class_883 / pw.hachimi.client.\u0000mZ\u200E` | 5/11 | — | Brigadier 客户端命令实现 | 高 |
| `class_734` | `pw.hachimi.client.\u0000gv\u200E` | `hidden#294` | `class_919 / pw.hachimi.client.\u0000nZ\u200E` | 2/3 | — | 事件对象/事件上下文；领域：具体事件类型需运行时字符串/调用点确认 | 中-低 |
| `class_735` | `pw.hachimi.client.\u0000om\u200E` | `hidden#805` | `class_874 / pw.hachimi.client.\u0000re\u200E` | 10/20 | — | 客户端功能模块实现；行为域：物品/背包、移动/玩家状态 | 中 |
| `class_736` | `pw.hachimi.client.\u0000aa\u200E` | `hidden#194` | `java.lang.Enum` | 5/8 | — | 枚举：模式/类别/状态选项（枚举常量名称多数已加密） | 中 |
| `class_737` | `pw.hachimi.client.\u0000iW\u200E` | `hidden#627` | `java.lang.Object` | 2/6 | — | 内部辅助类/值对象/适配器；精确业务名受混淆影响 | 低 |
| `class_738` | `pw.hachimi.client.\u0000fe\u200E` | `hidden#482` | `java.lang.Enum` | 6/13 | — | 枚举：模式/类别/状态选项（枚举常量名称多数已加密） | 中 |
| `class_739` | `pw.hachimi.client.\u0000fS\u200E` | `hidden#296` | `java.lang.Enum` | 5/8 | — | 枚举：模式/类别/状态选项（枚举常量名称多数已加密） | 中 |
| `class_740` | `pw.hachimi.client.\u0000jI\u200E` | `hidden#023` | `class_781 / pw.hachimi.client.\u0000pB\u200E` | 2/3 | — | 事件对象/事件上下文；领域：具体事件类型需运行时字符串/调用点确认 | 中-低 |
| `class_741` | `pw.hachimi.client.\u0000nW\u200E` | `hidden#129` | `java.lang.Enum` | 5/8 | — | 枚举：模式/类别/状态选项（枚举常量名称多数已加密） | 中 |
| `class_742` | `pw.hachimi.client.\u0000mN\u200E` | `hidden#427` | `java.lang.Enum` | 6/8 | — | 枚举：模式/类别/状态选项（枚举常量名称多数已加密） | 中 |
| `class_743` | `pw.hachimi.client.\u0000rc\u200E` | `hidden#187` | `java.lang.Enum` | 6/12 | — | 枚举：模式/类别/状态选项（枚举常量名称多数已加密） | 中 |
| `class_744` | `pw.hachimi.client.\u0000mX\u200E` | `hidden#540` | `class_919 / pw.hachimi.client.\u0000nZ\u200E` | 2/3 | — | 事件对象/事件上下文；领域：具体事件类型需运行时字符串/调用点确认 | 中-低 |
| `class_745` | `pw.hachimi.client.\u0000ou\u200E` | `hidden#016` | `class_919 / pw.hachimi.client.\u0000nZ\u200E` | 2/3 | — | 事件对象/事件上下文；领域：具体事件类型需运行时字符串/调用点确认 | 中-低 |
| `class_746` | `pw.hachimi.client.\u0000kk\u200E` | `hidden#010` | `class_747 / pw.hachimi.client.\u0000in\u200E` | 28/57 | ID 14 / 1 methods | 内部辅助/管理类；主要涉及：数据包/网络、方块/世界交互、实体/战斗、移动/玩家状态 | 中 |
| `class_747` | `pw.hachimi.client.\u0000in\u200E` | `hidden#133` | `class_880 / pw.hachimi.client.\u0000by\u200E` | 2/4 | — | 内部辅助类/值对象/适配器；精确业务名受混淆影响 | 低 |
| `class_748` | `pw.hachimi.client.\u0000be\u200E` | `hidden#611` | `java.lang.Object` | 2/5 | — | 内部辅助类/值对象/适配器；精确业务名受混淆影响 | 低 |
| `class_749` | `pw.hachimi.client.\u0000eM\u200E` | `hidden#645` | `class_874 / pw.hachimi.client.\u0000re\u200E` | 10/14 | — | 客户端功能模块实现；行为域：具体功能名被字符串加密隐藏 | 中-低 |
| `class_750` | `pw.hachimi.client.\u0000dt\u200E` | `hidden#747` | `class_908 / pw.hachimi.client.\u0000qt\u200E` | 5/11 | ID 60 / 3 methods | 内部辅助类/值对象/适配器；精确业务名受混淆影响 | 低 |
| `class_751` | `pw.hachimi.client.\u0000bu\u200E` | `hidden#813` | `class_919 / pw.hachimi.client.\u0000nZ\u200E` | 2/3 | — | 事件对象/事件上下文；领域：具体事件类型需运行时字符串/调用点确认 | 中-低 |
| `class_752` | `pw.hachimi.client.\u0000py\u200E` | `hidden#507` | `java.lang.Enum` | 5/8 | — | 枚举：模式/类别/状态选项（枚举常量名称多数已加密） | 中 |
| `class_753` | `pw.hachimi.client.\u0000dd\u200E` | `hidden#570` | `class_874 / pw.hachimi.client.\u0000re\u200E` | 13/16 | — | 客户端功能模块实现；行为域：实体/战斗 | 中 |
| `class_754` | `pw.hachimi.client.q` | `visible` | `java.lang.Object` | 0/10 | — | 启动/注册分片：自身几乎无字段，按多个无参方法分批触发隐藏类初始化/注册 | 中-高 |
| `class_755` | `pw.hachimi.client.\u0000bb\u200E` | `hidden#544` | `class_874 / pw.hachimi.client.\u0000re\u200E` | 42/50 | — | 客户端功能模块实现；行为域：实体/战斗、移动/玩家状态 | 中 |
| `class_756` | `pw.hachimi.client.\u0000gT\u200E` | `hidden#638` | `class_874 / pw.hachimi.client.\u0000re\u200E` | 10/29 | ID 68 / 2 methods | 客户端功能模块实现；行为域：数据包/网络、物品/背包、实体/战斗 | 中 |
| `class_757` | `pw.hachimi.client.r` | `visible` | `java.lang.Object` | 0/9 | — | 启动/注册分片：自身几乎无字段，按多个无参方法分批触发隐藏类初始化/注册 | 中-高 |
| `class_758` | `pw.hachimi.client.\u0000qq\u200E` | `hidden#803` | `java.util.concurrent.ConcurrentLinkedDeque` | 3/7 | — | 内部辅助类/值对象/适配器；精确业务名受混淆影响 | 低 |
| `class_759` | `pw.hachimi.client.\u0000nt\u200E` | `hidden#497` | `java.lang.Object` | 7/16 | — | 内部辅助类/值对象/适配器；精确业务名受混淆影响 | 低 |
| `class_760` | `pw.hachimi.client.\u0000nk\u200E` | `hidden#407` | `java.lang.Object` | 1/5 | — | 内部辅助类/值对象/适配器；精确业务名受混淆影响 | 低 |
| `class_761` | `pw.hachimi.client.\u0000nO\u200E` | `hidden#051` | `class_767 / pw.hachimi.client.\u0000bh\u200E` | 1/11 | — | 内部辅助类/值对象/适配器；精确业务名受混淆影响 | 低 |
| `class_762` | `pw.hachimi.client.\u0000dJ\u200E` | `hidden#170` | `class_767 / pw.hachimi.client.\u0000bh\u200E` | 1/11 | — | 内部辅助类/值对象/适配器；精确业务名受混淆影响 | 低 |
| `class_763` | `pw.hachimi.client.\u0000ey\u200E` | `hidden#342` | `class_767 / pw.hachimi.client.\u0000bh\u200E` | 1/11 | — | 内部辅助类/值对象/适配器；精确业务名受混淆影响 | 低 |
| `class_764` | `pw.hachimi.client.\u0000nM\u200E` | `hidden#789` | `class_874 / pw.hachimi.client.\u0000re\u200E` | 56/68 | — | 客户端功能模块实现；行为域：渲染/HUD、方块/世界交互、实体/战斗 | 中 |
| `class_765` | `pw.hachimi.client.\u0000qn\u200E` | `hidden#765` | `class_874 / pw.hachimi.client.\u0000re\u200E` | 7/13 | — | 客户端功能模块实现；行为域：具体功能名被字符串加密隐藏 | 中-低 |
| `class_766` | `pw.hachimi.client.\u0000oS\u200E` | `hidden#425` | `class_767 / pw.hachimi.client.\u0000bh\u200E` | 1/11 | — | 内部辅助类/值对象/适配器；精确业务名受混淆影响 | 低 |
| `class_767` | `pw.hachimi.client.\u0000bh\u200E` | `hidden#647` | `java.lang.Enum` | 6/19 | — | 枚举：模式/类别/状态选项（枚举常量名称多数已加密） | 中 |
| `class_768` | `pw.hachimi.client.\u0000dh\u200E` | `hidden#601` | `class_908 / pw.hachimi.client.\u0000qt\u200E` | 7/16 | ID 71 / 3 methods | 内部辅助类/值对象/适配器；精确业务名受混淆影响 | 低 |
| `class_769` | `pw.hachimi.client.\u0000oZ\u200E` | `hidden#566` | `java.lang.Object` | 13/15 | ID 25 / 2 methods | 内部辅助/管理类；主要涉及：文件/IO | 中 |
| `class_770` | `pw.hachimi.client.\u0000aJ\u200E` | `hidden#706` | `java.lang.Enum` | 5/10 | — | 枚举：模式/类别/状态选项（枚举常量名称多数已加密） | 中 |
| `class_771` | `pw.hachimi.client.\u0000eP\u200E` | `hidden#640` | `class_810 / pw.hachimi.client.\u0000aU\u200E` | 2/3 | — | 事件对象/事件上下文；领域：渲染/HUD | 中 |
| `class_772` | `pw.hachimi.client.\u0000rd\u200E` | `hidden#202` | `java.lang.Thread` | 5/9 | — | 后台工作线程 | 高 |
| `class_773` | `pw.hachimi.client.\u0000mE\u200E` | `hidden#365` | `java.lang.Enum` | 5/8 | — | 枚举：模式/类别/状态选项（枚举常量名称多数已加密） | 中 |
| `class_774` | `pw.hachimi.client.\u0000mo\u200E` | `hidden#007` | `class_908 / pw.hachimi.client.\u0000qt\u200E` | 9/24 | ID 30 / 3 methods | 内部辅助类/值对象/适配器；精确业务名受混淆影响 | 低 |
| `class_775` | `pw.hachimi.client.\u0000kE\u200E` | `hidden#413` | `class_810 / pw.hachimi.client.\u0000aU\u200E` | 2/3 | — | 事件对象/事件上下文；领域：渲染/HUD | 中 |
| `class_776` | `pw.hachimi.client.\u0000fE\u200E` | `hidden#077` | `class_874 / pw.hachimi.client.\u0000re\u200E` | 20/36 | — | 客户端功能模块实现；行为域：具体功能名被字符串加密隐藏 | 中-低 |
| `class_777` | `pw.hachimi.client.\u0000hX\u200E` | `hidden#308` | `class_883 / pw.hachimi.client.\u0000mZ\u200E` | 5/11 | — | Brigadier 客户端命令实现 | 高 |
| `class_778` | `pw.hachimi.client.d` | `visible` | `java.lang.Object` | 0/19 | — | 启动/注册分片：自身几乎无字段，按多个无参方法分批触发隐藏类初始化/注册 | 中-高 |
| `class_779` | `pw.hachimi.client.l` | `visible` | `java.lang.Object` | 0/17 | — | 启动/注册分片：自身几乎无字段，按多个无参方法分批触发隐藏类初始化/注册 | 中-高 |
| `class_780` | `pw.hachimi.client.\u0000cD\u200E` | `hidden#606` | `class_781 / pw.hachimi.client.\u0000pB\u200E` | 3/5 | — | 事件对象/事件上下文；领域：具体事件类型需运行时字符串/调用点确认 | 中-低 |
| `class_781` | `pw.hachimi.client.\u0000pB\u200E` | `hidden#650` | `class_919 / pw.hachimi.client.\u0000nZ\u200E` | 3/5 | — | 事件对象/事件上下文；领域：具体事件类型需运行时字符串/调用点确认 | 中-低 |
| `class_782` | `pw.hachimi.client.\u0000qw\u200E` | `hidden#043` | `java.lang.Object` | 8/8 | ID 11 / 1 methods | 内部辅助类/值对象/适配器；精确业务名受混淆影响 | 低 |
| `class_783` | `pw.hachimi.client.\u0000bJ\u200E` | `hidden#226` | `java.lang.Object` | 4/10 | — | 内部辅助类/值对象/适配器；精确业务名受混淆影响 | 低 |
| `class_784` | `pw.hachimi.client.\u0000fU\u200E` | `hidden#267` | `class_806 / pw.hachimi.client.\u0000ot\u200E` | 16/23 | ID 20 / 2 methods | 客户端功能模块实现；行为域：数据包/网络、物品/背包 | 中 |
| `class_785` | `pw.hachimi.client.\u0000cO\u200E` | `hidden#677` | `class_810 / pw.hachimi.client.\u0000aU\u200E` | 2/3 | — | 事件对象/事件上下文；领域：渲染/HUD | 中 |
| `class_786` | `pw.hachimi.client.\u0000eW\u200E` | `hidden#721` | `java.lang.Object` | 2/46 | — | 内部辅助/管理类；主要涉及：物品/背包、方块/世界交互、实体/战斗、移动/玩家状态 | 中 |
| `class_787` | `pw.hachimi.client.j` | `visible` | `java.lang.Object` | 0/17 | — | 启动/注册分片：自身几乎无字段，按多个无参方法分批触发隐藏类初始化/注册 | 中-高 |
| `class_788` | `pw.hachimi.client.\u0000qM\u200E` | `hidden#368` | `java.lang.Object` | 6/9 | — | 内部辅助类/值对象/适配器；精确业务名受混淆影响 | 低 |
| `class_789` | `pw.hachimi.client.\u0000pv\u200E` | `hidden#423` | `class_919 / pw.hachimi.client.\u0000nZ\u200E` | 2/3 | — | 事件对象/事件上下文；领域：具体事件类型需运行时字符串/调用点确认 | 中-低 |
| `class_790` | `pw.hachimi.client.\u0000bE\u200E` | `hidden#156` | `class_879 / pw.hachimi.client.\u0000kV\u200E` | 1/4 | — | 内部辅助类/值对象/适配器；精确业务名受混淆影响 | 低 |
| `class_791` | `pw.hachimi.client.a` | `visible` | `java.lang.Object` | 0/24 | — | 启动/注册分片：自身几乎无字段，按多个无参方法分批触发隐藏类初始化/注册 | 中-高 |
| `class_792` | `pw.hachimi.client.\u0000bF\u200E` | `hidden#172` | `class_879 / pw.hachimi.client.\u0000kV\u200E` | 1/4 | — | 内部辅助类/值对象/适配器；精确业务名受混淆影响 | 低 |
| `class_793` | `pw.hachimi.client.u` | `visible` | `java.lang.Object` | 0/8 | — | 启动/注册分片：自身几乎无字段，按多个无参方法分批触发隐藏类初始化/注册 | 中-高 |
| `class_794` | `pw.hachimi.client.\u0000ci\u200E` | `hidden#171` | `class_879 / pw.hachimi.client.\u0000kV\u200E` | 1/4 | — | 内部辅助类/值对象/适配器；精确业务名受混淆影响 | 低 |
| `class_795` | `pw.hachimi.client.\u0000aR\u200E` | `hidden#808` | `class_879 / pw.hachimi.client.\u0000kV\u200E` | 1/4 | — | 内部辅助类/值对象/适配器；精确业务名受混淆影响 | 低 |
| `class_796` | `pw.hachimi.client.\u0000gJ\u200E` | `hidden#564` | `class_879 / pw.hachimi.client.\u0000kV\u200E` | 1/4 | — | 内部辅助类/值对象/适配器；精确业务名受混淆影响 | 低 |
| `class_797` | `pw.hachimi.client.\u0000qz\u200E` | `hidden#079` | `class_879 / pw.hachimi.client.\u0000kV\u200E` | 1/4 | — | 内部辅助类/值对象/适配器；精确业务名受混淆影响 | 低 |
| `class_798` | `pw.hachimi.client.\u0000hi\u200E` | `hidden#480` | `class_879 / pw.hachimi.client.\u0000kV\u200E` | 1/4 | — | 内部辅助类/值对象/适配器；精确业务名受混淆影响 | 低 |
| `class_799` | `pw.hachimi.client.\u0000an\u200E` | `hidden#347` | `class_879 / pw.hachimi.client.\u0000kV\u200E` | 1/4 | — | 内部辅助类/值对象/适配器；精确业务名受混淆影响 | 低 |
| `class_800` | `pw.hachimi.client.n` | `visible` | `java.lang.Object` | 0/17 | — | 启动/注册分片：自身几乎无字段，按多个无参方法分批触发隐藏类初始化/注册 | 中-高 |
| `class_801` | `pw.hachimi.client.\u0000bl\u200E` | `hidden#696` | `class_810 / pw.hachimi.client.\u0000aU\u200E` | 2/3 | — | 事件对象/事件上下文；领域：渲染/HUD | 中 |
| `class_802` | `pw.hachimi.client.\u0000qV\u200E` | `hidden#462` | `class_805 / pw.hachimi.client.\u0000kb\u200E` | 22/18 | ID 56 / 1 methods | 客户端功能模块实现；行为域：具体功能名被字符串加密隐藏 | 中-低 |
| `class_803` | `pw.hachimi.client.\u0000pI\u200E` | `hidden#691` | `java.lang.Object` | 5/9 | — | 内部辅助类/值对象/适配器；精确业务名受混淆影响 | 低 |
| `class_804` | `pw.hachimi.client.\u0000ly\u200E` | `hidden#613` | `class_805 / pw.hachimi.client.\u0000kb\u200E` | 19/27 | — | 客户端功能模块实现；行为域：数据包/网络、物品/背包、实体/战斗 | 中 |
| `class_805` | `pw.hachimi.client.\u0000kb\u200E` | `hidden#762` | `class_806 / pw.hachimi.client.\u0000ot\u200E` | 6/22 | — | 面向玩家筛选/目标选择的模块二级基类，包含 PlayerEntity 选择与距离/条件判断 | 中-高 |
| `class_806` | `pw.hachimi.client.\u0000ot\u200E` | `hidden#002` | `class_874 / pw.hachimi.client.\u0000re\u200E` | 4/12 | — | 模块二级基类：继承 Module 基类并增加坐标/数值状态 | 中 |
| `class_807` | `pw.hachimi.client.\u0000lH\u200E` | `hidden#780` | `java.lang.Object` | 4/14 | ID 35 / 5 methods | 内部辅助类/值对象/适配器；精确业务名受混淆影响 | 低 |
| `class_808` | `pw.hachimi.client.\u0000mQ\u200E` | `hidden#498` | `class_874 / pw.hachimi.client.\u0000re\u200E` | 13/39 | — | 客户端功能模块实现；行为域：渲染/HUD | 中 |
| `class_809` | `pw.hachimi.client.\u0000bk\u200E` | `hidden#632` | `class_810 / pw.hachimi.client.\u0000aU\u200E` | 3/5 | — | 事件对象/事件上下文；领域：渲染/HUD | 中 |
| `class_810` | `pw.hachimi.client.\u0000aU\u200E` | `hidden#034` | `class_919 / pw.hachimi.client.\u0000nZ\u200E` | 3/5 | — | 带 DrawContext 的渲染事件基类 | 高 |
| `class_811` | `pw.hachimi.client.\u0000fc\u200E` | `hidden#504` | `java.lang.Object` | 5/19 | ID 7 / 1 methods | 内部辅助/管理类；主要涉及：渲染/HUD | 中 |
| `class_812` | `pw.hachimi.client.\u0000fj\u200E` | `hidden#527` | `class_910 / pw.hachimi.client.\u0000jl\u200E` | 11/18 | — | 内部辅助/管理类；主要涉及：移动/玩家状态、JSON/配置 | 中 |
| `class_813` | `pw.hachimi.client.\u0000hB\u200E` | `hidden#031` | `java.lang.Object` | 4/13 | — | 内部辅助/管理类；主要涉及：渲染/HUD | 中 |
| `class_814` | `pw.hachimi.client.\u0000hV\u200E` | `hidden#227` | `class_919 / pw.hachimi.client.\u0000nZ\u200E` | 4/6 | — | 事件对象/事件上下文；领域：具体事件类型需运行时字符串/调用点确认 | 中-低 |
| `class_815` | `pw.hachimi.client.\u0000lg\u200E` | `hidden#409` | `class_919 / pw.hachimi.client.\u0000nZ\u200E` | 4/6 | — | 事件对象/事件上下文；领域：具体事件类型需运行时字符串/调用点确认 | 中-低 |
| `class_816` | `pw.hachimi.client.\u0000cd\u200E` | `hidden#126` | `class_874 / pw.hachimi.client.\u0000re\u200E` | 5/7 | — | 客户端功能模块实现；行为域：具体功能名被字符串加密隐藏 | 中-低 |
| `class_817` | `pw.hachimi.client.\u0000oB\u200E` | `hidden#222` | `java.lang.Object` | 3/15 | — | 内部辅助类/值对象/适配器；精确业务名受混淆影响 | 低 |
| `class_818` | `pw.hachimi.client.y` | `visible` | `java.lang.Object` | 0/8 | — | 启动/注册分片：自身几乎无字段，按多个无参方法分批触发隐藏类初始化/注册 | 中-高 |
| `class_819` | `pw.hachimi.client.\u0000lx\u200E` | `hidden#604` | `java.lang.Object` | 24/3 | — | 内部辅助类/值对象/适配器；精确业务名受混淆影响 | 低 |
| `class_820` | `pw.hachimi.client.\u0000ls\u200E` | `hidden#487` | `java.lang.Object` | 9/12 | — | 内部辅助类/值对象/适配器；精确业务名受混淆影响 | 低 |
| `class_821` | `pw.hachimi.eventbus.annotation.EventListener` | `visible` | `java.lang.Object` | 0/2 | — | 事件总线监听器注解 | 高 |
| `class_822` | `pw.hachimi.client.\u0000cz\u200E` | `hidden#449` | `class_824 / pw.hachimi.client.\u0000lX\u200E` | 3/5 | — | 事件对象/事件上下文；领域：数据包/网络 | 中 |
| `class_823` | `pw.hachimi.client.\u0000pj\u200E` | `hidden#287` | `class_824 / pw.hachimi.client.\u0000lX\u200E` | 3/5 | — | 事件对象/事件上下文；领域：数据包/网络 | 中 |
| `class_824` | `pw.hachimi.client.\u0000lX\u200E` | `hidden#205` | `class_919 / pw.hachimi.client.\u0000nZ\u200E` | 3/5 | — | 事件对象/事件上下文；领域：数据包/网络 | 中 |
| `class_825` | `pw.hachimi.client.\u0000dH\u200E` | `hidden#201` | `class_919 / pw.hachimi.client.\u0000nZ\u200E` | 2/3 | — | 事件对象/事件上下文；领域：具体事件类型需运行时字符串/调用点确认 | 中-低 |
| `class_826` | `pw.hachimi.client.\u0000aq\u200E` | `hidden#336` | `class_919 / pw.hachimi.client.\u0000nZ\u200E` | 4/6 | — | 事件对象/事件上下文；领域：具体事件类型需运行时字符串/调用点确认 | 中-低 |
| `class_827` | `pw.hachimi.client.\u0000is\u200E` | `hidden#196` | `java.lang.Object` | 4/7 | — | 内部辅助类/值对象/适配器；精确业务名受混淆影响 | 低 |
| `class_828` | `pw.hachimi.client.\u0000gu\u200E` | `hidden#228` | `class_877 / pw.hachimi.client.\u0000lQ\u200E` | 1/5 | — | `lQ` 枚举常量的匿名专用实现 | 高 |
| `class_829` | `pw.hachimi.client.\u0000pM\u200E` | `hidden#742` | `class_877 / pw.hachimi.client.\u0000lQ\u200E` | 1/4 | — | `lQ` 枚举常量的匿名专用实现 | 高 |
| `class_830` | `pw.hachimi.client.\u0000aW\u200E` | `hidden#005` | `class_877 / pw.hachimi.client.\u0000lQ\u200E` | 1/4 | — | `lQ` 枚举常量的匿名专用实现 | 高 |
| `class_831` | `pw.hachimi.client.p` | `visible` | `java.lang.Object` | 0/9 | — | 启动/注册分片：自身几乎无字段，按多个无参方法分批触发隐藏类初始化/注册 | 中-高 |
| `class_832` | `pw.hachimi.client.\u0000cW\u200E` | `hidden#775` | `class_877 / pw.hachimi.client.\u0000lQ\u200E` | 1/5 | — | `lQ` 枚举常量的匿名专用实现 | 高 |
| `class_833` | `pw.hachimi.client.\u0000cY\u200E` | `hidden#033` | `class_877 / pw.hachimi.client.\u0000lQ\u200E` | 1/4 | — | `lQ` 枚举常量的匿名专用实现 | 高 |
| `class_834` | `pw.hachimi.client.\u0000ar\u200E` | `hidden#398` | `class_877 / pw.hachimi.client.\u0000lQ\u200E` | 1/4 | — | `lQ` 枚举常量的匿名专用实现 | 高 |
| `class_835` | `pw.hachimi.client.x` | `visible` | `java.lang.Object` | 0/8 | — | 启动/注册分片：自身几乎无字段，按多个无参方法分批触发隐藏类初始化/注册 | 中-高 |
| `class_836` | `pw.hachimi.client.\u0000ak\u200E` | `hidden#306` | `class_877 / pw.hachimi.client.\u0000lQ\u200E` | 1/5 | — | `lQ` 枚举常量的匿名专用实现 | 高 |
| `class_837` | `pw.hachimi.client.\u0000hj\u200E` | `hidden#491` | `class_877 / pw.hachimi.client.\u0000lQ\u200E` | 1/5 | — | `lQ` 枚举常量的匿名专用实现 | 高 |
| `class_838` | `pw.hachimi.client.k` | `visible` | `java.lang.Object` | 0/10 | — | 启动/注册分片：自身几乎无字段，按多个无参方法分批触发隐藏类初始化/注册 | 中-高 |
| `class_839` | `pw.hachimi.client.\u0000qD\u200E` | `hidden#250` | `class_877 / pw.hachimi.client.\u0000lQ\u200E` | 1/5 | — | `lQ` 枚举常量的匿名专用实现 | 高 |
| `class_840` | `pw.hachimi.client.\u0000Q\u200E` | `hidden#726` | `class_877 / pw.hachimi.client.\u0000lQ\u200E` | 1/5 | — | `lQ` 枚举常量的匿名专用实现 | 高 |
| `class_841` | `pw.hachimi.client.\u0000qg\u200E` | `hidden#629` | `class_877 / pw.hachimi.client.\u0000lQ\u200E` | 1/5 | — | `lQ` 枚举常量的匿名专用实现 | 高 |
| `class_842` | `pw.hachimi.client.aa` | `visible` | `java.lang.Object` | 0/4 | — | 启动/注册分片：自身几乎无字段，按多个无参方法分批触发隐藏类初始化/注册 | 中-高 |
| `class_843` | `pw.hachimi.client.\u0000io\u200E` | `hidden#146` | `class_877 / pw.hachimi.client.\u0000lQ\u200E` | 1/4 | — | `lQ` 枚举常量的匿名专用实现 | 高 |
| `class_844` | `pw.hachimi.client.\u0000qb\u200E` | `hidden#612` | `class_877 / pw.hachimi.client.\u0000lQ\u200E` | 1/4 | — | `lQ` 枚举常量的匿名专用实现 | 高 |
| `class_845` | `pw.hachimi.client.g` | `visible` | `java.lang.Object` | 0/14 | — | 启动/注册分片：自身几乎无字段，按多个无参方法分批触发隐藏类初始化/注册 | 中-高 |
| `class_846` | `pw.hachimi.client.\u0000fV\u200E` | `hidden#281` | `class_877 / pw.hachimi.client.\u0000lQ\u200E` | 1/5 | — | `lQ` 枚举常量的匿名专用实现 | 高 |
| `class_847` | `pw.hachimi.client.\u0000cy\u200E` | `hidden#386` | `class_877 / pw.hachimi.client.\u0000lQ\u200E` | 1/4 | — | `lQ` 枚举常量的匿名专用实现 | 高 |
| `class_848` | `pw.hachimi.client.c` | `visible` | `java.lang.Object` | 0/20 | — | 启动/注册分片：自身几乎无字段，按多个无参方法分批触发隐藏类初始化/注册 | 中-高 |
| `class_849` | `pw.hachimi.client.\u0000dM\u200E` | `hidden#209` | `class_877 / pw.hachimi.client.\u0000lQ\u200E` | 1/4 | — | `lQ` 枚举常量的匿名专用实现 | 高 |
| `class_850` | `pw.hachimi.client.\u0000cA\u200E` | `hidden#571` | `class_877 / pw.hachimi.client.\u0000lQ\u200E` | 1/4 | — | `lQ` 枚举常量的匿名专用实现 | 高 |
| `class_851` | `pw.hachimi.client.b` | `visible` | `java.lang.Object` | 0/20 | — | 启动/注册分片：自身几乎无字段，按多个无参方法分批触发隐藏类初始化/注册 | 中-高 |
| `class_852` | `pw.hachimi.client.\u0000pk\u200E` | `hidden#355` | `class_877 / pw.hachimi.client.\u0000lQ\u200E` | 1/4 | — | `lQ` 枚举常量的匿名专用实现 | 高 |
| `class_853` | `pw.hachimi.client.\u0000eZ\u200E` | `hidden#811` | `class_877 / pw.hachimi.client.\u0000lQ\u200E` | 1/4 | — | `lQ` 枚举常量的匿名专用实现 | 高 |
| `class_854` | `pw.hachimi.client.e` | `visible` | `java.lang.Object` | 0/21 | — | 启动/注册分片：自身几乎无字段，按多个无参方法分批触发隐藏类初始化/注册 | 中-高 |
| `class_855` | `pw.hachimi.client.\u0000kg\u200E` | `hidden#781` | `class_877 / pw.hachimi.client.\u0000lQ\u200E` | 1/4 | — | `lQ` 枚举常量的匿名专用实现 | 高 |
| `class_856` | `pw.hachimi.client.t` | `visible` | `java.lang.Object` | 0/7 | — | 启动/注册分片：自身几乎无字段，按多个无参方法分批触发隐藏类初始化/注册 | 中-高 |
| `class_857` | `pw.hachimi.client.\u0000gP\u200E` | `hidden#583` | `class_877 / pw.hachimi.client.\u0000lQ\u200E` | 1/4 | — | `lQ` 枚举常量的匿名专用实现 | 高 |
| `class_858` | `pw.hachimi.client.\u0000qx\u200E` | `hidden#000` | `class_877 / pw.hachimi.client.\u0000lQ\u200E` | 1/5 | — | `lQ` 枚举常量的匿名专用实现 | 高 |
| `class_859` | `pw.hachimi.client.\u0000hm\u200E` | `hidden#562` | `class_877 / pw.hachimi.client.\u0000lQ\u200E` | 1/4 | — | `lQ` 枚举常量的匿名专用实现 | 高 |
| `class_860` | `pw.hachimi.client.i` | `visible` | `java.lang.Object` | 0/19 | — | 启动/注册分片：自身几乎无字段，按多个无参方法分批触发隐藏类初始化/注册 | 中-高 |
| `class_861` | `pw.hachimi.client.\u0000jr\u200E` | `hidden#523` | `class_877 / pw.hachimi.client.\u0000lQ\u200E` | 1/4 | — | `lQ` 枚举常量的匿名专用实现 | 高 |
| `class_862` | `pw.hachimi.client.\u0000jy\u200E` | `hidden#663` | `class_877 / pw.hachimi.client.\u0000lQ\u200E` | 1/4 | — | `lQ` 枚举常量的匿名专用实现 | 高 |
| `class_863` | `pw.hachimi.client.\u0000mc\u200E` | `hidden#682` | `class_877 / pw.hachimi.client.\u0000lQ\u200E` | 1/4 | — | `lQ` 枚举常量的匿名专用实现 | 高 |
| `class_864` | `pw.hachimi.client.\u0000iY\u200E` | `hidden#698` | `class_877 / pw.hachimi.client.\u0000lQ\u200E` | 1/4 | — | `lQ` 枚举常量的匿名专用实现 | 高 |
| `class_865` | `pw.hachimi.client.h` | `visible` | `java.lang.Object` | 0/12 | — | 启动/注册分片：自身几乎无字段，按多个无参方法分批触发隐藏类初始化/注册 | 中-高 |
| `class_866` | `pw.hachimi.client.\u0000jS\u200E` | `hidden#180` | `class_877 / pw.hachimi.client.\u0000lQ\u200E` | 1/4 | — | `lQ` 枚举常量的匿名专用实现 | 高 |
| `class_867` | `pw.hachimi.client.o` | `visible` | `java.lang.Object` | 0/11 | — | 启动/注册分片：自身几乎无字段，按多个无参方法分批触发隐藏类初始化/注册 | 中-高 |
| `class_868` | `pw.hachimi.client.\u0000iX\u200E` | `hidden#636` | `class_870 / pw.hachimi.client.\u0000hR\u200E` | 2/8 | — | 内部辅助类/值对象/适配器；精确业务名受混淆影响 | 低 |
| `class_869` | `pw.hachimi.client.\u0000iI\u200E` | `hidden#515` | `java.lang.Object` | 5/6 | — | 内部辅助类/值对象/适配器；精确业务名受混淆影响 | 低 |
| `class_870` | `pw.hachimi.client.\u0000hR\u200E` | `hidden#168` | `class_908 / pw.hachimi.client.\u0000qt\u200E` | 5/13 | ID 58 / 3 methods | 内部辅助类/值对象/适配器；精确业务名受混淆影响 | 低 |
| `class_871` | `pw.hachimi.client.\u0000F\u200E` | `hidden#688` | `class_908 / pw.hachimi.client.\u0000qt\u200E` | 5/16 | ID 67 / 3 methods | 内部辅助类/值对象/适配器；精确业务名受混淆影响 | 低 |
| `class_872` | `pw.hachimi.client.\u0000nH\u200E` | `hidden#727` | `java.lang.Object` | 10/21 | ID 5 / 3 methods | 可执行动作型配置项（含 Runnable）/按钮式 Setting | 中-高 |
| `class_873` | `pw.hachimi.client.\u0000aX\u200E` | `hidden#019` | `class_874 / pw.hachimi.client.\u0000re\u200E` | 7/12 | — | 客户端功能模块实现；行为域：具体功能名被字符串加密隐藏 | 中-低 |
| `class_874` | `pw.hachimi.client.\u0000re\u200E` | `hidden#164` | `class_880 / pw.hachimi.client.\u0000by\u200E` | 13/32 | — | 模块系统核心基类（Module-like） | 高 |
| `class_875` | `pw.hachimi.client.\u0000lp\u200E` | `hidden#499` | `java.lang.Object` | 0/3 | — | 内部辅助类/值对象/适配器；精确业务名受混淆影响 | 低 |
| `class_876` | `pw.hachimi.client.\u0000rm\u200E` | `hidden#276` | `class_877 / pw.hachimi.client.\u0000lQ\u200E` | 1/5 | — | `lQ` 枚举常量的匿名专用实现 | 高 |
| `class_877` | `pw.hachimi.client.\u0000lQ\u200E` | `hidden#060` | `java.lang.Enum` | 31/38 | — | 大型枚举基类；其 29 个匿名子类是枚举常量专用实现 | 高 |
| `class_878` | `pw.hachimi.client.\u0000bO\u200E` | `hidden#348` | `java.lang.Object` | 9/31 | — | 内部辅助/管理类；主要涉及：数据包/网络 | 中 |
| `class_879` | `pw.hachimi.client.\u0000kV\u200E` | `hidden#616` | `java.lang.Enum` | 10/16 | — | 枚举：模式/类别/状态选项（枚举常量名称多数已加密） | 中 |
| `class_880` | `pw.hachimi.client.\u0000by\u200E` | `hidden#047` | `class_910 / pw.hachimi.client.\u0000jl\u200E` | 10/21 | — | 内部辅助类/值对象/适配器；精确业务名受混淆影响 | 低 |
| `class_881` | `pw.hachimi.client.\u0000eU\u200E` | `hidden#748` | `java.lang.Object` | 6/35 | — | 内部辅助/管理类；主要涉及：渲染/HUD | 中 |
| `class_882` | `pw.hachimi.client.\u0000nb\u200E` | `hidden#236` | `java.lang.Object` | 0/2 | — | 内部辅助/管理类；主要涉及：渲染/HUD | 中 |
| `class_883` | `pw.hachimi.client.\u0000mZ\u200E` | `hidden#614` | `java.lang.Object` | 6/16 | — | Brigadier 命令基类/命令注册抽象 | 高 |
| `class_884` | `pw.hachimi.client.\u0000mw\u200E` | `hidden#143` | `java.lang.Object` | 2/3 | — | 内部辅助类/值对象/适配器；精确业务名受混淆影响 | 低 |
| `class_885` | `pw.hachimi.client.\u0000ca\u200E` | `hidden#141` | `java.lang.Object` | 0/2 | — | 内部辅助/管理类；主要涉及：数据包/网络 | 中 |
| `class_886` | `pw.hachimi.client.mixin.x` | `visible` | `java.lang.Object` | 0/4 | — | Mixin 注入/拦截类 | 高 |
| `class_887` | `pw.hachimi.client.mixin.e` | `visible` | `java.lang.Object` | 0/4 | — | Mixin 注入/拦截类 | 高 |
| `class_888` | `pw.hachimi.client.mixin.ab` | `visible` | `java.lang.Object` | 0/1 | — | Mixin 注入/拦截类 | 高 |
| `class_889` | `pw.hachimi.client.mixin.O` | `visible` | `java.lang.Object` | 0/1 | — | Mixin 注入/拦截类 | 高 |
| `class_890` | `pw.hachimi.client.mixin.aA` | `visible` | `java.lang.Object` | 0/3 | — | Mixin 注入/拦截类 | 高 |
| `class_891` | `pw.hachimi.client.mixin.bi` | `visible` | `java.lang.Object` | 0/1 | — | Mixin 注入/拦截类 | 高 |
| `class_892` | `pw.hachimi.client.\u0000iZ\u200E` | `hidden#708` | `java.lang.Object` | 11/21 | ID 45 / 2 methods | 内部辅助类/值对象/适配器；精确业务名受混淆影响 | 低 |
| `class_893` | `pw.hachimi.satin.Checks` | `visible` | `java.lang.Object` | 0/5 | — | 运行权限/版本检查 | 高 |
| `class_894` | `pw.hachimi.client.\u0000lk\u200E` | `hidden#456` | `java.lang.Object` | 0/2 | — | 内部辅助类/值对象/适配器；精确业务名受混淆影响 | 低 |
| `class_895` | `pw.hachimi.client.mixin.bk` | `visible` | `java.lang.Object` | 0/1 | — | Mixin 注入/拦截类 | 高 |
| `class_896` | `pw.hachimi.client.mixin.aw` | `visible` | `java.lang.Object` | 0/1 | — | Mixin 注入/拦截类 | 高 |
| `class_897` | `pw.hachimi.client.mixin.c` | `visible` | `java.lang.Object` | 0/2 | — | Mixin 注入/拦截类 | 高 |
| `class_898` | `pw.hachimi.client.mixin.aZ` | `visible` | `java.lang.Object` | 0/7 | — | Mixin 注入/拦截类 | 高 |
| `class_899` | `pw.hachimi.client.\u0000fX\u200E` | `hidden#357` | `java.lang.Object` | 0/3 | — | 内部辅助类/值对象/适配器；精确业务名受混淆影响 | 低 |
| `class_900` | `pw.hachimi.client.mixin.F` | `visible` | `java.lang.Object` | 0/1 | — | Mixin 注入/拦截类 | 高 |
| `class_901` | `pw.hachimi.client.\u0000ad\u200E` | `hidden#157` | `java.lang.Object` | 0/5 | — | 内部辅助类/值对象/适配器；精确业务名受混淆影响 | 低 |
| `class_902` | `pw.hachimi.client.\u0000ja\u200E` | `hidden#326` | `java.lang.Object` | 0/2 | — | 内部辅助类/值对象/适配器；精确业务名受混淆影响 | 低 |
| `class_903` | `pw.hachimi.client.\u0000gZ\u200E` | `hidden#764` | `java.lang.Object` | 7/20 | — | 内部辅助类/值对象/适配器；精确业务名受混淆影响 | 低 |
| `class_904` | `pw.hachimi.client.\u0000eN\u200E` | `hidden#657` | `java.lang.Object` | 0/10 | — | 内部辅助类/值对象/适配器；精确业务名受混淆影响 | 低 |
| `class_905` | `pw.hachimi.client.\u0000W\u200E` | `hidden#454` | `java.lang.Object` | 0/1 | — | 内部辅助类/值对象/适配器；精确业务名受混淆影响 | 低 |
| `class_906` | `pw.hachimi.client.v` | `visible` | `java.lang.Object` | 0/10 | — | 启动/注册分片：自身几乎无字段，按多个无参方法分批触发隐藏类初始化/注册 | 中-高 |
| `class_907` | `pw.hachimi.client.\u0000kG\u200E` | `hidden#396` | `java.lang.Object` | 1/5 | — | 内部辅助类/值对象/适配器；精确业务名受混淆影响 | 低 |
| `class_908` | `pw.hachimi.client.\u0000qt\u200E` | `hidden#768` | `java.lang.Object` | 12/29 | ID 59 / 2 methods | 配置项/Setting 基类：持有名称、值、默认值、Supplier，并支持 Gson JSON 序列化 | 高 |
| `class_909` | `pw.hachimi.local.NativeBridge` | `visible` | `java.lang.Object` | 4/3 | — | Windows JNA 本地桥：定位 PhantomShield 模块、装载 512B key table，并修改本地代码页后刷新指令缓存 | 高 |
| `class_910` | `pw.hachimi.client.\u0000jl\u200E` | `hidden#514` | `java.lang.Object` | 10/24 | ID 1 / 4 methods | 配置项注册表/配置容器：管理多个 Setting，并负责 JSON 导入导出 | 高 |
| `class_911` | `pw.hachimi.client.\u0000ix\u200E` | `hidden#214` | `java.lang.Object` | 0/3 | — | JSON 可序列化/反序列化接口 | 高 |
| `class_912` | `pw.hachimi.client.\u0000hS\u200E` | `hidden#247` | `java.lang.Object` | 0/2 | — | 可命名对象接口（核心方法返回 String） | 中 |
| `class_913` | `pw.hachimi.client.s` | `visible` | `java.lang.Object` | 0/10 | — | 启动/注册分片：自身几乎无字段，按多个无参方法分批触发隐藏类初始化/注册 | 中-高 |
| `class_914` | `pw.hachimi.client.af` | `visible` | `java.lang.Object` | 6/25 | — | invokedynamic/反射解析与字符串解密运行时；负责字段/方法句柄与 CallSite | 高 |
| `class_915` | `pw.hachimi.client.ad` | `visible` | `java.lang.Object` | 6/17 | — | 混淆运行时状态组合器/缓存器，实现 ac | 高 |
| `class_916` | `pw.hachimi.client.ae` | `visible` | `java.lang.Object` | 14/23 | — | 混淆运行时密钥状态生成器；为每类静态解密种子提供 64-bit 派生值 | 高 |
| `class_917` | `pw.hachimi.client.ac` | `visible` | `java.lang.Object` | 0/6 | — | 混淆运行时接口：64-bit 状态/密钥调度抽象 | 高 |
| `class_918` | `pw.hachimi.client.\u0000my\u200E` | `hidden#123` | `class_919 / pw.hachimi.client.\u0000nZ\u200E` | 3/7 | — | 事件对象/事件上下文；领域：具体事件类型需运行时字符串/调用点确认 | 中-低 |
| `class_919` | `pw.hachimi.client.\u0000nZ\u200E` | `hidden#124` | `java.lang.Object` | 6/14 | — | 事件系统核心基类（Event-like），大量具体事件继承于此 | 高 |
| `class_920` | `pw.hachimi.client.mixin.bs` | `visible` | `java.lang.Object` | 0/3 | — | Mixin 注入/拦截类 | 高 |
| `class_921` | `pw.hachimi.client.mixin.ar` | `visible` | `java.lang.Object` | 0/1 | — | Mixin 注入/拦截类 | 高 |
| `class_922` | `pw.hachimi.client.\u0000bT\u200E` | `hidden#411` | `java.lang.Object` | 0/2 | — | 内部辅助/管理类；主要涉及：方块/世界交互、实体/战斗 | 中 |
| `class_923` | `pw.hachimi.client.mixin.I` | `visible` | `java.lang.Object` | 0/1 | — | Mixin 注入/拦截类 | 高 |
| `class_924` | `pw.hachimi.client.mixin.Q` | `visible` | `java.lang.Object` | 0/3 | — | Mixin 注入/拦截类 | 高 |
| `class_925` | `pw.hachimi.client.\u0000iq\u200E` | `hidden#119` | `java.lang.Object` | 0/3 | — | 内部辅助类/值对象/适配器；精确业务名受混淆影响 | 低 |
| `class_926` | `pw.hachimi.client.mixin.bD` | `visible` | `java.lang.Object` | 0/2 | — | Mixin 注入/拦截类 | 高 |
| `class_927` | `pw.hachimi.client.mixin.S` | `visible` | `java.lang.Object` | 0/1 | — | Mixin 注入/拦截类 | 高 |
| `class_928` | `pw.hachimi.client.mixin.az` | `visible` | `java.lang.Object` | 0/1 | — | Mixin 注入/拦截类 | 高 |

## 6. 可见 Mixin 类

下面单列正常文件名下的 Mixin。由于注入方法名和 target 也存在 intermediary/混淆，本文列出其直接引用的 Minecraft 类型作为定位线索。

| mapping | Mixin class | 主要 Minecraft 类型引用 |
|---:|---|---|
| `class_70` | `pw.hachimi.client.mixin.ba` | （未从常量池直接解析出） |
| `class_71` | `pw.hachimi.client.mixin.an` | （未从常量池直接解析出） |
| `class_73` | `pw.hachimi.client.mixin.ah` | （未从常量池直接解析出） |
| `class_74` | `pw.hachimi.client.mixin.aN` | （未从常量池直接解析出） |
| `class_76` | `pw.hachimi.client.mixin.bB` | （未从常量池直接解析出） |
| `class_77` | `pw.hachimi.client.mixin.bO` | （未从常量池直接解析出） |
| `class_78` | `pw.hachimi.client.mixin.bf` | （未从常量池直接解析出） |
| `class_88` | `pw.hachimi.client.mixin.aE` | （未从常量池直接解析出） |
| `class_94` | `pw.hachimi.client.mixin.bT` | （未从常量池直接解析出） |
| `class_95` | `pw.hachimi.client.mixin.q` | （未从常量池直接解析出） |
| `class_96` | `pw.hachimi.client.mixin.aB` | （未从常量池直接解析出） |
| `class_97` | `pw.hachimi.client.mixin.bK` | （未从常量池直接解析出） |
| `class_98` | `pw.hachimi.client.mixin.aP` | （未从常量池直接解析出） |
| `class_99` | `pw.hachimi.client.mixin.bh` | （未从常量池直接解析出） |
| `class_115` | `pw.hachimi.client.mixin.K` | （未从常量池直接解析出） |
| `class_116` | `pw.hachimi.client.mixin.R` | （未从常量池直接解析出） |
| `class_120` | `pw.hachimi.client.mixin.aI` | （未从常量池直接解析出） |
| `class_126` | `pw.hachimi.client.mixin.f` | （未从常量池直接解析出） |
| `class_131` | `pw.hachimi.client.mixin.ad` | （未从常量池直接解析出） |
| `class_140` | `pw.hachimi.client.mixin.al` | class_286 |
| `class_141` | `pw.hachimi.client.mixin.bN` | （未从常量池直接解析出） |
| `class_142` | `pw.hachimi.client.mixin.ap` | （未从常量池直接解析出） |
| `class_143` | `pw.hachimi.client.mixin.be` | （未从常量池直接解析出） |
| `class_146` | `pw.hachimi.client.mixin.br` | （未从常量池直接解析出） |
| `class_147` | `pw.hachimi.client.mixin.T` | （未从常量池直接解析出） |
| `class_148` | `pw.hachimi.client.mixin.aM` | （未从常量池直接解析出） |
| `class_149` | `pw.hachimi.client.mixin.bn` | （未从常量池直接解析出） |
| `class_150` | `pw.hachimi.client.mixin.bc` | （未从常量池直接解析出） |
| `class_163` | `pw.hachimi.client.mixin.s` | （未从常量池直接解析出） |
| `class_172` | `pw.hachimi.client.mixin.j` | （未从常量池直接解析出） |
| `class_198` | `pw.hachimi.client.mixin.bb` | （未从常量池直接解析出） |
| `class_223` | `pw.hachimi.client.mixin.aC` | （未从常量池直接解析出） |
| `class_653` | `pw.hachimi.client.mixin.m` | （未从常量池直接解析出） |
| `class_678` | `pw.hachimi.client.mixin.ae` | （未从常量池直接解析出） |
| `class_679` | `pw.hachimi.client.mixin.b` | （未从常量池直接解析出） |
| `class_680` | `pw.hachimi.client.mixin.aL` | （未从常量池直接解析出） |
| `class_886` | `pw.hachimi.client.mixin.x` | （未从常量池直接解析出） |
| `class_887` | `pw.hachimi.client.mixin.e` | （未从常量池直接解析出） |
| `class_888` | `pw.hachimi.client.mixin.ab` | （未从常量池直接解析出） |
| `class_889` | `pw.hachimi.client.mixin.O` | （未从常量池直接解析出） |
| `class_890` | `pw.hachimi.client.mixin.aA` | （未从常量池直接解析出） |
| `class_891` | `pw.hachimi.client.mixin.bi` | （未从常量池直接解析出） |
| `class_895` | `pw.hachimi.client.mixin.bk` | （未从常量池直接解析出） |
| `class_896` | `pw.hachimi.client.mixin.aw` | （未从常量池直接解析出） |
| `class_897` | `pw.hachimi.client.mixin.c` | （未从常量池直接解析出） |
| `class_898` | `pw.hachimi.client.mixin.aZ` | （未从常量池直接解析出） |
| `class_900` | `pw.hachimi.client.mixin.F` | （未从常量池直接解析出） |
| `class_920` | `pw.hachimi.client.mixin.bs` | （未从常量池直接解析出） |
| `class_921` | `pw.hachimi.client.mixin.ar` | （未从常量池直接解析出） |
| `class_923` | `pw.hachimi.client.mixin.I` | （未从常量池直接解析出） |
| `class_924` | `pw.hachimi.client.mixin.Q` | （未从常量池直接解析出） |
| `class_926` | `pw.hachimi.client.mixin.bD` | （未从常量池直接解析出） |
| `class_927` | `pw.hachimi.client.mixin.S` | （未从常量池直接解析出） |
| `class_928` | `pw.hachimi.client.mixin.az` | （未从常量池直接解析出） |

## 7. Native-backed Hachimi 类索引

下面只列 `pw.hachimi.client.*`。`mapping` 为 `—` 时，表示该类只在 native mapping 中出现，无法与当前 JPI workspace 的 `class_N` 一一对应。

| Reg ID | mapping | 原始类名 | native 方法数 | native 方法 | 额外功能线索 |
|---:|---|---|---:|---|---|
| 0 | `class_536` | `pw.hachimi.client.\u0000i\u200E` | 2 | `B\u200E(Lnet/minecraft/class_1297;DZJ)Z`<br>`C\u200E(Lpw/hachimi/client/\u0000jx\u200E;)V` | 实体/战斗回调 |
| 1 | `class_910` | `pw.hachimi.client.\u0000jl\u200E` | 4 | `OI\u200E(JLpw/hachimi/client/\u0000qt\u200E;)Lpw/hachimi/client/\u0000qt\u200E;`<br>`LT\u200E(J)Lcom/google/gson/JsonObject;`<br>`OM\u200E(Lcom/google/gson/JsonObject;J)Lpw/hachimi/client/\u0000qt\u200E;`<br>`LU\u200E(Lcom/google/gson/JsonObject;J)Ljava/lang/Object;` | JSON/配置序列化 |
| 2 | `class_17` | `pw.hachimi.client.\u0000V\u200E` | 3 | `LT\u200E(J)Lcom/google/gson/JsonObject;`<br>`bC\u200E(JLcom/google/gson/JsonObject;)Lpw/hachimi/client/\u0000V\u200E;`<br>`LU\u200E(Lcom/google/gson/JsonObject;J)Ljava/lang/Object;` | JSON/配置序列化 |
| 3 | `class_605` | `pw.hachimi.client.\u0000pG\u200E` | 4 | `aqz\u200E(Lpw/hachimi/client/\u0000mY\u200E;)V`<br>`aqB\u200E(JLnet/minecraft/class_243;Lnet/minecraft/class_243;)Lnet/minecraft/class_243;`<br>`aqE\u200E(Lpw/hachimi/client/\u0000nz\u200E;)V`<br>`aqF\u200E(Lpw/hachimi/client/\u0000pw\u200E;)V` | 向量/移动计算 |
| 4 | `class_558` | `pw.hachimi.client.\u0000ab\u200E` | 3 | `ca\u200E(Lpw/hachimi/client/\u0000jx\u200E;)V`<br>`cb\u200E(Lpw/hachimi/client/\u0000pj\u200E;)V`<br>`cc\u200E(Lnet/minecraft/class_1297;J)V` | 实体/战斗回调 |
| 5 | `class_872` | `pw.hachimi.client.\u0000nH\u200E` | 3 | `LT\u200E(J)Lcom/google/gson/JsonObject;`<br>`agy\u200E(Lcom/google/gson/JsonObject;J)Lpw/hachimi/client/\u0000nH\u200E;`<br>`LU\u200E(Lcom/google/gson/JsonObject;J)Ljava/lang/Object;` | JSON/配置序列化 |
| 6 | `class_625` | `pw.hachimi.client.\u0000pO\u200E` | 1 | `arA\u200E(Lpw/hachimi/client/\u0000jx\u200E;)V` | 内部事件/上下文回调 |
| 7 | `class_811` | `pw.hachimi.client.\u0000fc\u200E` | 1 | `wV\u200E(J)V` | native 核心逻辑（需结合调用图继续命名） |
| 8 | `class_583` | `pw.hachimi.client.\u0000mG\u200E` | 1 | `acf\u200E(Lpw/hachimi/client/\u0000jI\u200E;)V` | 内部事件/上下文回调 |
| 9 | `class_138` | `pw.hachimi.client.\u0000km\u200E` | 1 | `Tn\u200E(J)V` | native 核心逻辑（需结合调用图继续命名） |
| 10 | `class_42` | `pw.hachimi.client.\u0000he\u200E` | 3 | `EZ\u200E(J)V`<br>`Fg\u200E(Lnet/minecraft/class_2338;Lnet/minecraft/class_2350;JLnet/minecraft/class_1268;ZZ)V`<br>`Fq\u200E(Lnet/minecraft/class_1297;IDZZSS)Z` | 实体/战斗回调 |
| 11 | `class_782` | `pw.hachimi.client.\u0000qw\u200E` | 1 | `atK\u200E(J)V` | native 核心逻辑（需结合调用图继续命名） |
| 12 | `class_155` | `pw.hachimi.client.\u0000cN\u200E` | 1 | `aoS\u200E(CSI)V` | native 核心逻辑（需结合调用图继续命名） |
| 13 | `class_487` | `pw.hachimi.client.\u0000qe\u200E` | 1 | `asj\u200E(Lpw/hachimi/client/\u0000jx\u200E;)V` | 内部事件/上下文回调 |
| 14 | `class_746` | `pw.hachimi.client.\u0000kk\u200E` | 1 | `Sr\u200E(Lpw/hachimi/client/\u0000jI\u200E;)V` | 内部事件/上下文回调 |
| 15 | `class_383` | `pw.hachimi.client.\u0000lY\u200E` | 3 | `ZL\u200E(Lpw/hachimi/client/\u0000jx\u200E;)V`<br>`ZN\u200E(IJ)V`<br>`ZO\u200E(Lpw/hachimi/client/\u0000pH\u200E;)V` | 内部事件/上下文回调 |
| 16 | `class_398` | `pw.hachimi.client.\u0000ay\u200E` | 1 | `dN\u200E(Lpw/hachimi/client/\u0000jx\u200E;)V` | 内部事件/上下文回调 |
| 17 | `class_563` | `pw.hachimi.client.\u0000qY\u200E` | 1 | `avD\u200E(Lpw/hachimi/client/\u0000iy\u200E;)V` | native 核心逻辑（需结合调用图继续命名） |
| 18 | `class_722` | `pw.hachimi.client.\u0000cu\u200E` | 1 | `ld\u200E(Lpw/hachimi/client/\u0000mu\u200E;)V` | native 核心逻辑（需结合调用图继续命名） |
| 19 | `class_152` | `pw.hachimi.client.\u0000cc\u200E` | 1 | `aoS\u200E(CSI)V` | native 核心逻辑（需结合调用图继续命名） |
| 20 | `class_784` | `pw.hachimi.client.\u0000fU\u200E` | 2 | `AP\u200E(Lpw/hachimi/client/\u0000jx\u200E;)V`<br>`AQ\u200E(IILnet/minecraft/class_6880;)Z` | 内部事件/上下文回调 |
| 21 | `class_721` | `pw.hachimi.client.\u0000qA\u200E` | 1 | `awg\u200E(II)V` | native 核心逻辑（需结合调用图继续命名） |
| 22 | `class_632` | `pw.hachimi.client.\u0000qU\u200E` | 1 | `avq\u200E(Lpw/hachimi/client/\u0000jx\u200E;)V` | 内部事件/上下文回调 |
| 23 | `class_270` | `pw.hachimi.client.\u0000dq\u200E` | 3 | `nq\u200E(Lpw/hachimi/client/\u0000pj\u200E;)V`<br>`nr\u200E(Lpw/hachimi/client/\u0000eL\u200E;)V`<br>`nt\u200E(Lnet/minecraft/class_1799;)Z` | 物品/背包判断 |
| 24 | `class_296` | `pw.hachimi.client.\u0000oP\u200E` | 3 | `alK\u200E(Lnet/minecraft/class_2338;IJ)Z`<br>`alL\u200E(Lpw/hachimi/client/\u0000jI\u200E;)V`<br>`alM\u200E(Lpw/hachimi/client/\u0000pj\u200E;)V` | 方块/世界交互 |
| 25 | `class_769` | `pw.hachimi.client.\u0000oZ\u200E` | 2 | `aot\u200E(CIC)V`<br>`aou\u200E(IIS)V` | native 核心逻辑（需结合调用图继续命名） |
| 26 | `class_540` | `pw.hachimi.client.\u0000fM\u200E` | 1 | `Aq\u200E(Lpw/hachimi/client/\u0000jx\u200E;)V` | 内部事件/上下文回调 |
| 27 | `class_458` | `pw.hachimi.client.\u0000hT\u200E` | 1 | `Ko\u200E(Lpw/hachimi/client/\u0000jI\u200E;)V` | 内部事件/上下文回调 |
| 28 | `class_156` | `pw.hachimi.client.\u0000ft\u200E` | 1 | `aoS\u200E(CSI)V` | native 核心逻辑（需结合调用图继续命名） |
| 29 | `class_335` | `pw.hachimi.client.\u0000dc\u200E` | 3 | `LT\u200E(J)Lcom/google/gson/JsonObject;`<br>`mj\u200E(Lcom/google/gson/JsonObject;J)Ljava/util/List;`<br>`LU\u200E(Lcom/google/gson/JsonObject;J)Ljava/lang/Object;` | JSON/List Setting 序列化 |
| 30 | `class_774` | `pw.hachimi.client.\u0000mo\u200E` | 3 | `LT\u200E(J)Lcom/google/gson/JsonObject;`<br>`abu\u200E(Lcom/google/gson/JsonObject;J)Ljava/lang/Number;`<br>`LU\u200E(Lcom/google/gson/JsonObject;J)Ljava/lang/Object;` | JSON/Number Setting 序列化 |
| 31 | `class_370` | `pw.hachimi.client.\u0000oR\u200E` | 4 | `awh\u200E(J)V`<br>`anc\u200E(JLnet/minecraft/class_2664;)Z`<br>`and\u200E(Lnet/minecraft/class_2743;J)Z`<br>`anf\u200E(Lpw/hachimi/client/\u0000jx\u200E;)V` | 内部事件/上下文回调 |
| 32 | `class_309` | `pw.hachimi.client.\u0000bL\u200E` | 1 | `im\u200E(Lnet/minecraft/class_243;J)V` | 向量/移动计算 |
| 33 | `class_712` | `pw.hachimi.client.\u0000bI\u200E` | 1 | `hM\u200E(Lpw/hachimi/client/\u0000jx\u200E;)V` | 内部事件/上下文回调 |
| 34 | `class_343` | `pw.hachimi.client.\u0000et\u200E` | 1 | `qw\u200E(Lpw/hachimi/client/\u0000jx\u200E;)V` | 内部事件/上下文回调 |
| 35 | `class_807` | `pw.hachimi.client.\u0000lH\u200E` | 5 | `XI\u200E(J)V`<br>`XJ\u200E(IIC[Lpw/hachimi/client/\u0000by\u200E;)V`<br>`XK\u200E(J[Lpw/hachimi/client/\u0000mQ\u200E;)V`<br>`XL\u200E(JLpw/hachimi/client/\u0000by\u200E;)V`<br>`XM\u200E(Lpw/hachimi/client/\u0000by\u200E;J)V` | native 核心逻辑（需结合调用图继续命名） |
| 36 | `class_392` | `pw.hachimi.client.\u0000rl\u200E` | 4 | `awT\u200E(Lpw/hachimi/client/\u0000pH\u200E;)V`<br>`awU\u200E(Lpw/hachimi/client/\u0000pj\u200E;)V`<br>`awW\u200E(Lpw/hachimi/client/\u0000jI\u200E;)V`<br>`awX\u200E(Lpw/hachimi/client/\u0000pj\u200E;)V` | 内部事件/上下文回调 |
| 37 | `class_560` | `pw.hachimi.client.\u0000dk\u200E` | 1 | `mM\u200E(Lnet/minecraft/class_437;J)V` | GUI/Screen 回调 |
| 38 | `—` | `pw.hachimi.client.\u0000gS\u200E` | 1 | `aoS\u200E(CSI)V` | native 核心逻辑（需结合调用图继续命名） |
| 39 | `class_387` | `pw.hachimi.client.\u0000kM\u200E` | 2 | `Vz\u200E(J)Z`<br>`VA\u200E(Lpw/hachimi/client/\u0000nr\u200E;)V` | native 核心逻辑（需结合调用图继续命名） |
| 40 | `class_274` | `pw.hachimi.client.\u0000jE\u200E` | 2 | `PY\u200E(Lpw/hachimi/client/\u0000cz\u200E;)V`<br>`PZ\u200E(Lpw/hachimi/client/\u0000pH\u200E;)V` | 内部事件/上下文回调 |
| 41 | `class_300` | `pw.hachimi.client.\u0000hp\u200E` | 1 | `FI\u200E(Lpw/hachimi/client/\u0000jx\u200E;)V` | 内部事件/上下文回调 |
| 42 | `class_651` | `pw.hachimi.client.\u0000fT\u200E` | 1 | `AH\u200E(Lpw/hachimi/client/\u0000jx\u200E;)V` | 内部事件/上下文回调 |
| 43 | `class_154` | `pw.hachimi.client.\u0000cI\u200E` | 1 | `aoS\u200E(CSI)V` | native 核心逻辑（需结合调用图继续命名） |
| 44 | `class_348` | `pw.hachimi.client.\u0000qv\u200E` | 1 | `aoS\u200E(CSI)V` | native 核心逻辑（需结合调用图继续命名） |
| 45 | `class_892` | `pw.hachimi.client.\u0000iZ\u200E` | 2 | `NT\u200E()V`<br>`NU\u200E()V` | native 核心逻辑（需结合调用图继续命名） |
| 46 | `class_207` | `pw.hachimi.client.\u0000mR\u200E` | 2 | `adG\u200E(J[Lpw/hachimi/client/\u0000mZ\u200E;)V`<br>`adH\u200E(Lpw/hachimi/client/\u0000mZ\u200E;J)V` | 命令系统/命令数组管理 |
| 47 | `class_695` | `pw.hachimi.client.\u0000au\u200E` | 1 | `dc\u200E(J)V` | native 核心逻辑（需结合调用图继续命名） |
| 48 | `class_656` | `pw.hachimi.client.\u0000fx\u200E` | 1 | `yA\u200E(Lpw/hachimi/client/\u0000jx\u200E;)V` | 内部事件/上下文回调 |
| 49 | `class_689` | `pw.hachimi.client.\u0000l\u200E` | 3 | `LT\u200E(J)Lcom/google/gson/JsonObject;`<br>`am\u200E(Lcom/google/gson/JsonObject;J)Ljava/util/List;`<br>`LU\u200E(Lcom/google/gson/JsonObject;J)Ljava/lang/Object;` | JSON/List Setting 序列化 |
| 50 | `class_151` | `pw.hachimi.client.\u0000kZ\u200E` | 1 | `aoS\u200E(CSI)V` | native 核心逻辑（需结合调用图继续命名） |
| 51 | `class_341` | `pw.hachimi.client.\u0000qQ\u200E` | 3 | `main([Ljava/lang/String;)V`<br>`avl\u200E(Ljava/lang/String;J)Ljava/lang/String;`<br>`avm\u200E(ILjava/util/List;Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V` | Mixin 注入辅助入口 |
| 52 | `class_421` | `pw.hachimi.client.\u0000av\u200E` | 2 | `dl\u200E(Lpw/hachimi/client/\u0000jx\u200E;)V`<br>`dn\u200E(J)V` | 内部事件/上下文回调 |
| 53 | `class_272` | `pw.hachimi.client.\u0000lf\u200E` | 1 | `WC\u200E(Lpw/hachimi/client/\u0000jx\u200E;)V` | 内部事件/上下文回调 |
| 54 | `class_222` | `pw.hachimi.client.\u0000iz\u200E` | 4 | `LY\u200E(Lpw/hachimi/client/\u0000jI\u200E;)V`<br>`Ma\u200E(Lpw/hachimi/client/\u0000jx\u200E;)V`<br>`Mb\u200E(Lpw/hachimi/client/\u0000pH\u200E;)V`<br>`Mf\u200E(Lpw/hachimi/client/\u0000lM\u200E;)V` | 内部事件/上下文回调 |
| 55 | `class_660` | `pw.hachimi.client.\u0000nu\u200E` | 3 | `LT\u200E(J)Lcom/google/gson/JsonObject;`<br>`afZ\u200E(JLcom/google/gson/JsonObject;)Ljava/util/List;`<br>`LU\u200E(Lcom/google/gson/JsonObject;J)Ljava/lang/Object;` | JSON/List Setting 序列化 |
| 56 | `class_802` | `pw.hachimi.client.\u0000qV\u200E` | 1 | `avv\u200E(Lpw/hachimi/client/\u0000jx\u200E;)V` | 内部事件/上下文回调 |
| 57 | `class_454` | `pw.hachimi.client.\u0000hx\u200E` | 2 | `GF\u200E(J)V`<br>`GG\u200E(J)V` | native 核心逻辑（需结合调用图继续命名） |
| 58 | `class_870` | `pw.hachimi.client.\u0000hR\u200E` | 3 | `LT\u200E(J)Lcom/google/gson/JsonObject;`<br>`Km\u200E(Lcom/google/gson/JsonObject;J)Ljava/lang/Boolean;`<br>`LU\u200E(Lcom/google/gson/JsonObject;J)Ljava/lang/Object;` | JSON/Boolean Setting 序列化 |
| 59 | `class_908` | `pw.hachimi.client.\u0000qt\u200E` | 2 | `LT\u200E(J)Lcom/google/gson/JsonObject;`<br>`LU\u200E(Lcom/google/gson/JsonObject;J)Ljava/lang/Object;` | JSON/配置序列化 |
| 60 | `class_750` | `pw.hachimi.client.\u0000dt\u200E` | 3 | `LT\u200E(J)Lcom/google/gson/JsonObject;`<br>`nC\u200E(JLcom/google/gson/JsonObject;)Ljava/lang/String;`<br>`LU\u200E(Lcom/google/gson/JsonObject;J)Ljava/lang/Object;` | JSON/String Setting 序列化 |
| 61 | `—` | `pw.hachimi.client.\u0000ff\u200E` | 5 | `xq\u200E(BJ)V`<br>`xr\u200E(J)V`<br>`xs\u200E(J)V`<br>`xt\u200E(IBI)V`<br>`xu\u200E(J)Z` | native 核心逻辑（需结合调用图继续命名） |
| 62 | `class_448` | `pw.hachimi.client.\u0000jp\u200E` | 2 | `Ph\u200E(ZJ)V`<br>`Pj\u200E(BILjava/lang/String;I)V` | native 核心逻辑（需结合调用图继续命名） |
| 63 | `class_641` | `pw.hachimi.client.\u0000of\u200E` | 1 | `aiN\u200E(Lpw/hachimi/client/\u0000jI\u200E;)V` | 内部事件/上下文回调 |
| 64 | `class_542` | `pw.hachimi.client.\u0000cm\u200E` | 2 | `ku\u200E(Lpw/hachimi/client/\u0000pH\u200E;)V`<br>`kv\u200E(Lpw/hachimi/client/\u0000pj\u200E;)V` | 内部事件/上下文回调 |
| 65 | `class_157` | `pw.hachimi.client.\u0000oY\u200E` | 5 | `anY\u200E(J)V`<br>`anZ\u200E(J)V`<br>`aoa\u200E(JS)V`<br>`aob\u200E(J)V`<br>`aoe\u200E(JLjava/lang/String;)V` | native 核心逻辑（需结合调用图继续命名） |
| 66 | `class_349` | `pw.hachimi.client.\u0000pf\u200E` | 1 | `aoN\u200E(Ljava/lang/String;Ljava/lang/Class;J)Ljava/lang/Object;` | native 核心逻辑（需结合调用图继续命名） |
| 67 | `class_871` | `pw.hachimi.client.\u0000F\u200E` | 3 | `aU\u200E(JLcom/google/gson/JsonObject;)Lpw/hachimi/client/\u0000nH\u200E;`<br>`LT\u200E(J)Lcom/google/gson/JsonObject;`<br>`LU\u200E(Lcom/google/gson/JsonObject;J)Ljava/lang/Object;` | JSON/配置序列化 |
| 68 | `class_756` | `pw.hachimi.client.\u0000gT\u200E` | 2 | `Ed\u200E(Lpw/hachimi/client/\u0000jx\u200E;)V`<br>`Ei\u200E(Lpw/hachimi/client/\u0000ps\u200E;)V` | 内部事件/上下文回调 |
| 69 | `class_708` | `pw.hachimi.client.\u0000kp\u200E` | 3 | `LT\u200E(J)Lcom/google/gson/JsonObject;`<br>`TK\u200E(JLcom/google/gson/JsonObject;)Ljava/awt/Color;`<br>`LU\u200E(Lcom/google/gson/JsonObject;J)Ljava/lang/Object;` | JSON/Color Setting 序列化 |
| 70 | `—` | `pw.hachimi.client.\u0000lm\u200E` | 1 | `WQ\u200E(Lpw/hachimi/client/\u0000jx\u200E;)V` | 内部事件/上下文回调 |
| 71 | `class_768` | `pw.hachimi.client.\u0000dh\u200E` | 3 | `LT\u200E(J)Lcom/google/gson/JsonObject;`<br>`mw\u200E(Lcom/google/gson/JsonObject;J)Ljava/lang/Enum;`<br>`LU\u200E(Lcom/google/gson/JsonObject;J)Ljava/lang/Object;` | JSON/Enum Setting 序列化 |

### 7.1 可直接提高置信度的 native 证据

- `class_908 / \u0000qt\u200E`、`class_910 / \u0000jl\u200E`、`class_872 / \u0000nH\u200E` 以及 Boolean/Number/String/List/Enum/Color 派生类均出现 Gson `JsonObject` native 方法，进一步确认这一族是 Setting/配置序列化体系。
- `class_42 / \u0000he\u200E` 的 native 方法直接接受 `BlockPos`、`Direction`、`Entity` 等 Minecraft 类型，明确属于世界交互/实体行为逻辑，而非普通工具类。
- `class_270 / \u0000dq\u200E` 的 native 方法包含 `ItemStack -> boolean`，可确定至少包含物品条件判断。
- `class_296 / \u0000oP\u200E` 包含 `BlockPos -> boolean` 与多个内部事件参数，支持其属于方块/位置判定型模块或辅助逻辑。
- `class_560 / \u0000dk\u200E` 直接接受 Minecraft `Screen`，属于 GUI/界面生命周期相关逻辑。
- `class_605 / \u0000pG\u200E` 有 `Vec3d + Vec3d -> Vec3d` native 计算，说明存在明确的向量/移动变换逻辑。
- `class_341 / \u0000qQ\u200E` 的 native 方法包含 `main(String[])` 和 Mixin `CallbackInfo`，应视为特殊入口/注入辅助，而不是普通数据类。

## 8. `skidonion.vLZkx` Native Runtime 索引

| Reg ID | 类 | native 方法数 | 功能形态 | 代表 native 方法 |
|---:|---|---:|---|---|
| 72 | `skidonion.vLZkx.l1l` | 17 | 保护/运行时辅助 | `1lI()Lskidonion/vLZkx/l11;`<br>`1ll()Lskidonion/vLZkx/II1;`<br>`l()V`<br>`1I(Z)V`<br>`I1(Ljava/lang/String;)V`<br>`Il(Ljava/lang/String;)V`<br>… 共 17 个 |
| 73 | `skidonion.vLZkx.lII` | 17 | JSON tree/parser/writer 风格运行时 | `1(I)Lskidonion/vLZkx/11l;`<br>`I(J)Lskidonion/vLZkx/11l;`<br>`l(F)Lskidonion/vLZkx/11l;`<br>`11(D)Lskidonion/vLZkx/11l;`<br>`1I(Ljava/lang/String;)Lskidonion/vLZkx/11l;`<br>`1l(Z)Lskidonion/vLZkx/11l;`<br>… 共 17 个 |
| 74 | `skidonion.vLZkx.l` | 4 | 保护/运行时辅助 | `hasNext()Z`<br>`1()Lskidonion/vLZkx/11l;`<br>`remove()V`<br>`next()Ljava/lang/Object;` |
| 75 | `skidonion.vLZkx.l11` | 28 | JSON tree/parser/writer 风格运行时 | `IlIl(Ljava/io/Reader;)Lskidonion/vLZkx/l11;`<br>`Ill1(Ljava/lang/String;)Lskidonion/vLZkx/l11;`<br>`IllI(Lskidonion/vLZkx/l11;)Lskidonion/vLZkx/l11;`<br>`Illl(I)Lskidonion/vLZkx/l11;`<br>`l111(J)Lskidonion/vLZkx/l11;`<br>`l11I(F)Lskidonion/vLZkx/l11;`<br>… 共 28 个 |
| 76 | `skidonion.vLZkx.lI` | 19 | 保护/运行时辅助 | `1()Lskidonion/vLZkx/Il;`<br>`I()V`<br>`l()V`<br>`11()V`<br>`1I(Z)V`<br>`1l()V`<br>… 共 19 个 |
| 77 | `skidonion.vLZkx.IIl` | 9 | 保护/运行时辅助 | `l1I(Lskidonion/vLZkx/Il1;)V`<br>`toString()Ljava/lang/String;`<br>`hashCode()I`<br>`1II1()Z`<br>`1I1I()Z`<br>`1I1l()Z`<br>… 共 9 个 |
| 78 | `skidonion.vLZkx.ll` | 9 | 保护/运行时辅助 | `toString()Ljava/lang/String;`<br>`l1I(Lskidonion/vLZkx/Il1;)V`<br>`l1l()Z`<br>`lI1()I`<br>`lII()J`<br>`lIl()F`<br>… 共 9 个 |
| 79 | `skidonion.vLZkx.Ill` | 4 | 保护/运行时辅助 | `hasNext()Z`<br>`I()Lskidonion/vLZkx/I11;`<br>`remove()V`<br>`next()Ljava/lang/Object;` |
| 80 | `skidonion.vLZkx.1I1` | 4 | 保护/运行时辅助 | `1(Ljava/lang/String;I)V`<br>`I(I)V`<br>`l(Ljava/lang/Object;)I`<br>`11(Ljava/lang/Object;)I` |
| 81 | `skidonion.vLZkx.I11` | 6 | 保护/运行时辅助 | `1()Ljava/lang/String;`<br>`I()Lskidonion/vLZkx/11l;`<br>`hashCode()I`<br>`equals(Ljava/lang/Object;)Z`<br>`l(Lskidonion/vLZkx/I11;)Ljava/lang/String;`<br>`11(Lskidonion/vLZkx/I11;)Lskidonion/vLZkx/11l;` |
| 82 | `skidonion.vLZkx.II1` | 39 | JSON tree/parser/writer 风格运行时 | `1l1l(Ljava/io/Reader;)Lskidonion/vLZkx/II1;`<br>`1lI1(Ljava/lang/String;)Lskidonion/vLZkx/II1;`<br>`1lII(Lskidonion/vLZkx/II1;)Lskidonion/vLZkx/II1;`<br>`1lIl(Ljava/lang/String;I)Lskidonion/vLZkx/II1;`<br>`1ll1(Ljava/lang/String;J)Lskidonion/vLZkx/II1;`<br>`1llI(Ljava/lang/String;F)Lskidonion/vLZkx/II1;`<br>… 共 39 个 |
| 83 | `skidonion.vLZkx.1` | 31 | JSON tree/parser/writer 风格运行时 | `1(Ljava/lang/String;)V`<br>`I(Ljava/io/Reader;)V`<br>`l(Ljava/io/Reader;I)V`<br>`11()V`<br>`1I()V`<br>`1l()V`<br>… 共 31 个 |
| 84 | `skidonion.vLZkx.I1I` | 5 | 保护/运行时辅助 | `l1I(Lskidonion/vLZkx/Il1;)V`<br>`11ll()Z`<br>`1Il1()Ljava/lang/String;`<br>`hashCode()I`<br>`equals(Ljava/lang/Object;)Z` |
| 85 | `skidonion.vLZkx.11l` | 30 | JSON tree/parser/writer 风格运行时 | `llI(Ljava/io/Reader;)Lskidonion/vLZkx/11l;`<br>`lll(Ljava/lang/String;)Lskidonion/vLZkx/11l;`<br>`1111(I)Lskidonion/vLZkx/11l;`<br>`111I(J)Lskidonion/vLZkx/11l;`<br>`111l(F)Lskidonion/vLZkx/11l;`<br>`11I1(D)Lskidonion/vLZkx/11l;`<br>… 共 30 个 |
| 86 | `skidonion.vLZkx.Il1` | 13 | 保护/运行时辅助 | `Il(Ljava/lang/String;)V`<br>`l1(Ljava/lang/String;)V`<br>`lI(Ljava/lang/String;)V`<br>`1()V`<br>`I()V`<br>`l()V`<br>… 共 13 个 |
| 87 | `skidonion.vLZkx.Il` | 3 | 保护/运行时辅助 | `toString()Ljava/lang/String;`<br>`hashCode()I`<br>`equals(Ljava/lang/Object;)Z` |
| 88 | `skidonion.vLZkx.11` | 4 | 保护/运行时辅助 | `1()Lskidonion/vLZkx/Il;`<br>`I()I`<br>`l()I`<br>`11()I` |
| 89 | `skidonion.vLZkx.IlI` | 0 | 保护/运行时辅助 | — |
| 90 | `skidonion.vLZkx.I1l` | 8 | 保护/运行时辅助 | `1()V`<br>`I()V`<br>`l()V`<br>`11()V`<br>`1I()V`<br>`1l()V`<br>… 共 8 个 |
| 91 | `skidonion.vLZkx.l1I` | 4 | JSON tree/parser/writer 风格运行时 | `I()Lskidonion/vLZkx/l1I;`<br>`l(I)Lskidonion/vLZkx/l1I;`<br>`11()Lskidonion/vLZkx/l1I;`<br>`1(Ljava/io/Writer;)Lskidonion/vLZkx/Il1;` |
| 92 | `skidonion.vLZkx.I1` | 1 | JSON tree/parser/writer 风格运行时 | `1(Ljava/io/Writer;)Lskidonion/vLZkx/Il1;` |
| 93 | `skidonion.vLZkx.1ll` | 0 | 保护/运行时辅助 | — |
| 94 | `skidonion.vLZkx.1II` | 5 | 保护/运行时辅助 | `write(I)V`<br>`write([CII)V`<br>`write(Ljava/lang/String;II)V`<br>`flush()V`<br>`close()V` |
| 95 | `skidonion.vLZkx.111` | 1 | 保护/运行时辅助 | `1()I` |
| 96 | `skidonion.vLZkx.1l` | 1 | AWT GUI/输入事件辅助 | `keyPressed(Ljava/awt/event/KeyEvent;)V` |
| 97 | `skidonion.vLZkx.lIl` | 3 | AWT GUI/输入事件辅助 | `mouseClicked(Ljava/awt/event/MouseEvent;)V`<br>`mouseEntered(Ljava/awt/event/MouseEvent;)V`<br>`mouseExited(Ljava/awt/event/MouseEvent;)V` |
| 98 | `skidonion.vLZkx.I` | 3 | AWT GUI/输入事件辅助 | `mouseClicked(Ljava/awt/event/MouseEvent;)V`<br>`mouseEntered(Ljava/awt/event/MouseEvent;)V`<br>`mouseExited(Ljava/awt/event/MouseEvent;)V` |
| 99 | `skidonion.vLZkx.III` | 3 | AWT GUI/输入事件辅助 | `mouseClicked(Ljava/awt/event/MouseEvent;)V`<br>`mouseEntered(Ljava/awt/event/MouseEvent;)V`<br>`mouseExited(Ljava/awt/event/MouseEvent;)V` |
| 100 | `skidonion.vLZkx.l1` | 27 | AWT GUI/输入事件辅助 | `1I()V`<br>`1l()I`<br>`I1(Ljava/awt/event/ActionEvent;)V`<br>`II()V`<br>`Il(Ljava/awt/event/MouseEvent;)V`<br>`l1(Ljava/awt/event/MouseEvent;)V`<br>… 共 27 个 |
| 101 | `skidonion.vLZkx.1I` | 0 | 保护/运行时辅助 | — |
| 102 | `skidonion.vLZkx.11I` | 0 | 保护/运行时辅助 | — |
| 103 | `skidonion.vLZkx.lI1` | 1 | 保护/运行时辅助 | `1()Lskidonion/vLZkx/l11;` |
| 104 | `skidonion.vLZkx.1lI` | 2 | 线程/任务辅助 | `1(Ljava/lang/Runnable;)Ljava/lang/Thread;`<br>`I()V` |
| 105 | `skidonion.vLZkx.1l1` | 1 | 保护/运行时辅助 | `1(Ljava/lang/String;)Ljava/lang/String;` |
| 106 | `skidonion.vLZkx.1Il` | 9 | 保护/运行时辅助 | `1(II)I`<br>`I([BI)I`<br>`l(I[BI)V`<br>`11([IIIII)V`<br>`1I()V`<br>`1l([B)[B`<br>… 共 9 个 |
| 107 | `skidonion.vLZkx.II` | 6 | 字节/字符串编码变换 | `1([B)Ljava/lang/String;`<br>`I(Ljava/lang/String;)[B`<br>`l([B)[B`<br>`11([B)Ljava/lang/String;`<br>`1I(Ljava/lang/String;)[B`<br>`1l([B)[B` |
| — | `skidonion.vLZkx.___` | 1 | 统一 native registrar | `___(ILjava/lang/Class;)V` |

### 8.1 Registrar 关系

- Registrar：`skidonion.vLZkx.___.___(ILjava/lang/Class;)V`。native mapping 将 Registration ID `0..107` 分配给已注册类，registrar 自身没有 Registration ID。
- 几乎所有已注册类的 `registrationCaller` 都是 `<clinit>()V`，说明绑定发生在类初始化阶段。
- 因为本地实现可能承载业务判断、序列化、事件处理或算法，仅靠 Java decompiler 看到“空 native 声明”时，不应据此判断该类“没有功能”。


## 9. 分析边界

1. 本报告是 **静态** 分析，不运行 Minecraft 客户端，也不执行 native 模块；`native_mapping.json` 只作为已导出的注册关系与方法签名证据。
2. `native_mapping.json` 能确认哪些方法被本地化，但不能直接恢复 native 函数体；业务模块显示名/描述仍被动态字符串解密保护；在没有完整运行时 class-load 顺序、native 状态和游戏依赖的情况下，不能把每一个 `Module` 子类可靠还原成原作者命名。
3. 因此 README 对每个 class 给出的是“可证明的结构职责”；只有有直接明文、继承关系、接口、record component 或明确 API 调用时，才给出更具体的业务含义。
4. `class_N` 是 mapping 工作区的安全别名，不是原程序源码类名。原始内部名才是 `pw.hachimi.client.\u0000xx\u200E` 这一类值。

## 10. 建议后续逆向顺序

若要继续把“模块实现”精确恢复成具体功能名，优先顺序建议为：先用 `native_mapping.json` 的 Registration ID 将 Java class 与 native 实现建立稳定索引；再处理 `re` 模块基类构造器的字符串参数与 `invokedynamic` bootstrap；随后处理 146 个模块子类的 `super(...)` 调用，并把 `jx/jI/pH/pj` 等高频 native 回调参数沿调用图命名；再根据 Mixin -> Event -> Module 链条恢复事件语义；最后才处理授权/角色检查与更深层 native 函数体。这样比逐个反编译 928 个 class 更高效，也能避免被控制流垃圾和 Java 空 native 壳误导。

