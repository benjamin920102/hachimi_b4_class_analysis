这份 `jni_trace(8).jsonl` 已经抓到不少实际内容：包括 native 注册关系、字符串解密相关调用、196 个模块的配置对象、JSON 解析、事件回调，以及 renderer 初始化。

最有价值的是「混淆方法 → RVA → 调用内容」之间的对应关系。下面按功能整理。

### 1. 整体统计

| 项目 | 结果 |
|---|---:|
| 记录时间跨度 | 约 239 秒 |
| JSONL 记录数 | 1,969,491 条，全部可解析 |
| `enter` 事件 | 981,542 次 |
| `exit` 事件 | 981,541 次 |
| 参与线程 | 4 个 |
| 实际进入的 native 方法 | 116 组，共 8,231 次 |
| `NativeMethodBind` 事件 | 214 次 |
| `RegisterNatives` 调用 | 142 次，记录 213 个方法注册项 |
| `NewStringUTF` | 5,582 次，1,869 个不同字符串 |

这些数字含义不同：注册的方法不一定在本次运行中执行，因此不能把 214 次绑定当成 214 个已执行功能。

主要目标模块在日志中标为 `lib4653887167948703362.zip`，记录的加载基址为 `0x10000000`。

### 2. 按功能分类

| 类别 | 抓到的证据 | 能确认什么 |
|---|---|---|
| Native 加载与注册 | `RegisterNatives`、`NativeMethodBind`、方法签名、函数地址和 RVA | 可以建立 Java 方法与 native 实现的对应表 |
| 字符串与解密 | `NewStringUTF`、`String.intern`、`Cipher.init/doFinal`、DES/AES 算法名 | 执行了字符串构造和解密相关流程 |
| 动态调用 | `CallSite.getTarget`、`MethodHandle.asFixedArity/invokeWithArguments` | 大量调用通过 `MethodHandle` 间接分发 |
| 模块配置 | 含 `name`、`id`、`configs`、`value` 的 JSON | 抓到了模块名称、配置结构和部分配置值 |
| 配置类型转换 | Gson 的 Boolean、Number、Enum、String、List、Color 相关处理 | 可以辨认部分混淆方法的用途 |
| 运行时回调 | 重复进入的 native 方法、Mixin `CallbackInfo.cancel()` | 除初始化外，也执行了部分运行时回调 |
| 图形与资源 | shader 文件名、`nglShaderSource`、字体路径 | 执行了 renderer/shader/字体相关初始化 |
| 登录验证线索 | `VerificationPanel.login.code.`、`password`、`verify-token`、站点 URL | 存在验证相关字符串，尚不足以证明实际联网验证 |

### 3. 模块配置：抓到了 196 个模块对象

日志返回的 JSON 中，能识别出 196 个不同的模块 ID。以下按名称含义归类，列出代表项目：

| 功能分类 | 模块示例 |
|---|---|
| 战斗与放置 | `Aura`、`AutoCrystal`、`AutoAnchor`、`Surround`、`SelfTrap`、`AutoTrap`、`AutoWeb`、`Criticals`、`Reach` |
| 移动与飞行 | `Speed`、`Flight`、`ElytraFly`、`PacketFly`、`NoFall`、`Step`、`Strafe`、`LongJump` |
| 视觉与渲染 | `ESP`、`StorageESP`、`HoleESP`、`Chams`、`Nametags`、`Fullbright`、`Shaders`、`NoRender` |
| 背包与物品 | `InventoryManager`、`AutoArmor`、`ChestStealer`、`Replenish`、`Offhand`、`AutoTool` |
| 网络与封包 | `PacketCanceler`、`PacketLogger`、`FakeLatency`、`Blink`、`NoPacketKick`、`Disabler` |
| 自动化 | `AutoFish`、`AutoMine`、`AutoEat`、`AutoReconnect`、`AutoRespawn`、`AntiAFK` |
| 界面与 HUD | `UI`、`HUD`、`HUDLegacy`、`Watermark`、`Coords`、`FPS`、`TPS`、`Ping` |
| 管理与扩展 | `Settings`、`Modules`、`Scripts`、`Kit`、`Socials`、`Debug` |

例如 `FakePlayer` 的配置对象包含：

```text
name: FakePlayer
id: fakeplayer-module
configs:
  Enabled = false
  Hidden = false
  Notify = true
  Name = FakePlayer
  Damage = true
  AssumeBestArmor = false
  Record = false
  Play = false
```

这里要区分：抓到模块配置，表示程序读取或处理了该模块的数据，不代表模块已启用，也不代表对应功能完整执行。

### 4. 字符串、加密与动态分发

字符串部分抓到了：

- UI 文本、配置键名、类名、方法描述符。
- 大量《创世记》英文段落，以及不可读字符串；它们可能参与混淆或字符串处理，具体用途仍需逐条关联。
- shader 文件名、字体路径和验证相关字符串。

实际 `Cipher.getInstance()` 调用如下：

| 算法 | 次数 |
|---|---:|
| `DES/CBC/PKCS5Padding` | 65 |
| `DES/CBC/NoPadding` | 2 |
| `AES/CBC/PKCS5Padding` | 3 |

`Cipher.doFinal()` 共调用 1,216 次；捕获到带参数的 `Cipher.init()` 使用模式值 `2`，对应解密模式。结合字符串处理流程，支持其用于运行时解密的判断，但不能据此认定是在解密网络通信。

动态调用尤其明显：

| 调用 | 次数 |
|---|---:|
| `CallSite.getTarget()` | 33,235 |
| `MethodHandle.asFixedArity()` | 33,235 |
| `MethodHandle.invokeWithArguments()` | 33,235 |

因此，看到 `invokeWithArguments` 时，还需要继续追踪其 target，才能确定最终调用的业务方法。

### 5. 最值得关注的 native 热点

下面省略名称中的 `\u0000`、`\u200e` 等不可见字符；正式定位时必须保留完整名称与签名。

| 类／方法简写 | RVA | 进入次数 | 从签名与调用内容判断 |
|---|---|---:|---|
| `jl.OI` | `0x585f0` | 2,566 | 配置对象相关处理，具体职责待确认 |
| `hR.Km` | `0x1d5bc70` | 1,469 | Boolean 配置读取 |
| `iz.LY` | `0x1adede0` | 456 | 运行时回调，业务含义待确认 |
| `kk.Sr` | `0x69a960` | 456 | 运行时回调，业务含义待确认 |
| `mo.abu` | `0xf58f30` | 449 | Number 配置读取与转换 |
| `iz.Mf` | `0x1b18270` | 428 | 运行时回调，业务含义待确认 |
| `dh.mw` | `0x23395e0` | 235 | Enum 配置解析 |
| `iz.Ma` | `0x1adfaf0` | 214 | 含浮点字段处理及异常处理路径 |
| `pf.aoN` | `0x215cda0` | 201 | 调用 `Gson.fromJson`，负责反序列化 |
| `jl.OM` | `0x6d5b0` | 196 | JSON 到配置对象的转换线索 |

次数只是入口次数，不是耗时；RVA 也必须配合同一版本的模块使用。

### 6. 渲染与资源

抓到的具体资源包括：

```text
pos_color.vert / pos_color.frag
pos_tex_color.vert / pos_tex_color.frag
text.vert / text.frag

assets/hachimiclient/font/default.ttf
assets/hachimiclient/font/icon.ttf
default.kit
```

同时观察到：

- `Initializing Renderer ...`
- `Loaded fonts!`
- `GL20C.nglShaderSource` 调用 3 次。
- LWJGL 的内存分配、释放和 `MemoryStack` 调用。
- Mixin `CallbackInfo.cancel()` 调用 3 次。

这证明执行已推进到图形资源初始化和部分回调阶段；shader source 被提交不等于 shader 编译或绘制一定成功。

### 7. 异常与日志结束状态

`ExceptionCheck` 调用了 561,947 次，但返回 `true` 的只有 214 次。异常集中于 `iz.Ma` 下的 `MethodHandle.invokeWithArguments`，随后执行 `ExceptionOccurred` 和 `ExceptionClear`。

这表示程序反复进入了一条会产生并清除异常的路径；日志没有提供异常类型和 message，暂时无法判断根因。

文件结束时，`oY.anZ` 的一次 native 调用有入口、没有出口。这只能说明采集结束时调用尚未闭合，不能单凭这一点认定崩溃。

这份 trace 对还原配置系统最有帮助：已经能把 Boolean、Number、Enum 和 JSON 反序列化入口对应到 native RVA；对于战斗、移动等模块，目前更多是配置与回调线索，还没有足够证据还原完整算法。
