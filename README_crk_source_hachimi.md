# Anchor-based Tree Expansion 与 Runtime Decryption Interception 实作指南

本指南说明如何在逆向工程或动态分析环境中实现 **Anchor-based Tree Expansion（基于锚点的树扩展）** 与 **Runtime Decryption Interception（运行时解密拦截）**。

这两个技术常用于逆向混改软件（如 Hachimi / B4 案例中的 Java + Native 混淆），目的是在执行时还原被加密的字符串和类结构。

---

## 第一阶段：Runtime Decryption Interception（运行时解密拦截）

**目标**：拦截 `invokedynamic` 指令并还原 DES/CBC 解密后的原始字符串。

### 实作步骤

#### 1. 定位解密入口

混改工具通常会使用 `MutableCallSite` 或 `MethodHandle` 在 `<clinit>`（类初始化）阶段注册解密逻辑。你需要找到调用 `BootstrapMethod` 的点。

- **分析指纹**：寻找 `java.lang.invoke.MutableCallSite`、`java.lang.invoke.MethodHandles$Lookup` 以及 `invokeDynamic` 指令。
- **关键点**：根据分析，解密逻辑通常位于类似 `pw.hachimi.client.ac/ad/ae/af` 的类中。优先检查这些类的 `<clinit>` 方法。

#### 2. 实作动态拦截机制（Java Agent / ASM）

有两种主要方式：

**方式 A：使用 ASM 字节码修改（推荐）**

在类加载前修改字节码，将 `invokedynamic` 直接替换为解密后的字符串赋值。

```java
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Handle;
import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;

public class DecryptionTransformer implements ClassFileTransformer {

    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
                            ProtectionDomain protectionDomain, byte[] classfileBuffer) {
        try {
            ClassReader reader = new ClassReader(classfileBuffer);
            ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);

            reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                 String signature, String[] exceptions) {
                    MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);

                    // 拦截 <clinit> 方法
                    if ("<clinit>".equals(name)) {
                        return new MethodVisitor(Opcodes.ASM9, mv) {
                            @Override
                            public void visitInvokeDynamicInsn(String name, String descriptor,
                                                               Handle bootstrapMethod, Object... args) {
                                // 1. 识别 BootstrapMethod
                                // 若 name 为 "decrypt" 或类似标识，且 args 包含加密字符串
                                if (args.length > 0 && args[0] instanceof String) {
                                    String encryptedString = (String) args[0];
                                    String decryptedString = decryptString(encryptedString);

                                    // 2. 替换为静态字符串加载
                                    mv.visitLdcInsn(decryptedString);
                                    // 如需写入静态字段，可继续：
                                    // mv.visitFieldInsn(Opcodes.PUTSTATIC, className, "DECRYPTED_CONSTANT", "Ljava/lang/String;");
                                    return;
                                }
                                // 非目标 invokedynamic 则原样保留
                                super.visitInvokeDynamicInsn(name, descriptor, bootstrapMethod, args);
                            }
                        };
                    }
                    return mv;
                }
            }, 0);

            return writer.toByteArray();
        } catch (Exception e) {
            e.printStackTrace();
            return null; // 返回 null 表示不修改
        }
    }

    private String decryptString(String encrypted) {
        // 在此实现 DES/CBC 解密逻辑
        // Key 与 IV 需从 native_mapping.json 或运行时动态获取
        try {
            // 示例伪代码：
            // Cipher cipher = Cipher.getInstance("DES/CBC/PKCS5Padding");
            // cipher.init(Cipher.DECRYPT_MODE, secretKey, ivParameterSpec);
            // return new String(cipher.doFinal(Base64.getDecoder().decode(encrypted)));
            return "DECRYPTED_" + encrypted; // 占位，实际替换为真实解密
        } catch (Exception e) {
            return encrypted;
        }
    }
}
```

**方式 B：使用 Java Agent Runtime Hooking**

如果无法修改字节码，可在 JVM 启动时挂载 Hook，拦截 `MethodHandle` 的调用。

```java
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

public class CallSiteInterceptor {
    public static void installInterceptor() throws Exception {
        // 使用 privateLookupIn 获取内部 Lookup
        VarHandle lookupHandle = MethodHandles.privateLookupIn(
            MethodHandles.Lookup.class, MethodHandles.lookup())
            .findStaticVarHandle(MethodHandles.Lookup.class, "IMPL_LOOKUP", MethodHandles.Lookup.class);

        // 概念性代码：实际需根据目标 JDK 版本适配内部 API
        // 目标是 Hook java.lang.invoke.MutableCallSite::setTarget
        // 当 setTarget 被调用时，检查新的 target 是否指向解密函数，并记录/替换结果
    }
}
```

**Agent 入口示例**（`premain`）：

```java
public class Agent {
    public static void premain(String agentArgs, Instrumentation inst) {
        inst.addTransformer(new DecryptionTransformer(), true);
    }
}
```

---

## 第二阶段：Anchor-based Tree Expansion（基于锚点的树扩展）

**目标**：利用「高置信度锚点」反向推导并还原整个 Module 结构树。

这是一个数据结构与图论问题。将已知真实结构（如 Shoreline）作为「真实数据」，将混淆后的 class 作为「混淆节点」。

### 实作步骤

#### 1. 建立图数据结构

```python
from typing import List, Dict, Optional, Tuple
from collections import defaultdict

class ModuleNode:
    def __init__(self, class_id: int, hachimi_name: str, shoreline_name: str = "", confidence: str = "D"):
        self.id = class_id
        self.hachimi_name = hachimi_name          # 例如 'pw.hachimi.client.\u0000re\u200E'
        self.shoreline_name = shoreline_name      # 例如 'BaritoneModule'
        self.confidence = confidence              # A / B / C / D
        self.children: List['ModuleNode'] = []
        self.parent: Optional['ModuleNode'] = None
        self.config_count = 0                     # 设置项数量
        self.listener_count = 0                   # 监听器数量
        self.features: Dict[str, any] = {}        # 额外特征（方法签名、字段等）

class ModuleGraph:
    def __init__(self):
        self.nodes: Dict[int, ModuleNode] = {}
        self.edges: List[Tuple[int, int]] = []    # (parent_id, child_id)

    def add_node(self, node: ModuleNode):
        self.nodes[node.id] = node

    def add_edge(self, parent_id: int, child_id: int):
        parent = self.nodes.get(parent_id)
        child = self.nodes.get(child_id)
        if parent and child:
            child.parent = parent
            parent.children.append(child)
            self.edges.append((parent_id, child_id))
```

#### 2. 注入锚点数据

将高置信度（A 级 / B 级）模块作为种子节点（Roots）。

```python
# 示例锚点数据（根据实际 README / 分析结果填充）
anchors = [
    ModuleNode(232, "pw.hachimi.client.\u0000hb\u200E", "BaritoneModule", "A"),
    ModuleNode(667, "pw.hachimi.client.\u0000dm\u200e", "AntiHungerModule", "A"),
    ModuleNode(612, "pw.hachimi.client.\u0000d\u200e", "CrasherModule", "A"),
    # 继续添加更多 A/B 级锚点...
]

graph = ModuleGraph()
for node in anchors:
    graph.add_node(node)
```

#### 3. 扩展算法（树搜索）

对于每一个未命名的节点，计算其与已知节点的相似度分数。若分数超过阈值，则认为匹配并建立连接。

**相似度计算公式**：

$$
Score = w_1 \cdot ConfigSimilarity + w_2 \cdot ListenerSimilarity + w_3 \cdot StructureSimilarity
$$

```python
class TreeExpander:
    def __init__(self, graph: ModuleGraph, threshold: float = 0.7):
        self.graph = graph
        self.visited = set()
        self.expansion_threshold = threshold
        # 权重可按实际特征重要性调整
        self.weights = {
            "config": 0.4,
            "listener": 0.3,
            "structure": 0.3
        }

    def _config_similarity(self, a: ModuleNode, b: ModuleNode) -> float:
        if a.config_count == 0 and b.config_count == 0:
            return 1.0
        max_c = max(a.config_count, b.config_count, 1)
        return 1.0 - abs(a.config_count - b.config_count) / max_c

    def _listener_similarity(self, a: ModuleNode, b: ModuleNode) -> float:
        if a.listener_count == 0 and b.listener_count == 0:
            return 1.0
        max_l = max(a.listener_count, b.listener_count, 1)
        return 1.0 - abs(a.listener_count - b.listener_count) / max_l

    def _structure_similarity(self, a: ModuleNode, b: ModuleNode) -> float:
        # 可扩展：比较方法签名、字段名哈希、继承关系等
        # 此处为简单占位实现
        common_features = set(a.features.keys()) & set(b.features.keys())
        total = len(set(a.features.keys()) | set(b.features.keys())) or 1
        return len(common_features) / total

    def _calculate_score(self, candidate: ModuleNode, known: ModuleNode) -> float:
        s1 = self._config_similarity(candidate, known)
        s2 = self._listener_similarity(candidate, known)
        s3 = self._structure_similarity(candidate, known)
        return (self.weights["config"] * s1 +
                self.weights["listener"] * s2 +
                self.weights["structure"] * s3)

    def _find_match(self, node: ModuleNode) -> Optional[ModuleNode]:
        best_score = 0.0
        best_match = None
        for known in self.graph.nodes.values():
            if known.confidence not in ("A", "B"):
                continue
            if known.id == node.id:
                continue
            score = self._calculate_score(node, known)
            if score > best_score and score >= self.expansion_threshold:
                best_score = score
                best_match = known
        return best_match

    def expand(self, unknown_nodes: List[ModuleNode]):
        """主扩展循环"""
        for node in unknown_nodes:
            self.graph.add_node(node)

        changed = True
        while changed:
            changed = False
            for node_id, node in list(self.graph.nodes.items()):
                if node_id in self.visited or node.confidence in ("A", "B"):
                    continue

                matched = self._find_match(node)
                if matched:
                    # 建立父子关系（可根据实际业务决定方向）
                    self.graph.add_edge(matched.id, node.id)
                    node.shoreline_name = matched.shoreline_name + "_Child"  # 或更精确的命名逻辑
                    node.confidence = "C"  # 降级为推断结果
                    self.visited.add(node_id)
                    changed = True
                    print(f"[Expanded] {node.hachimi_name} -> {matched.shoreline_name} (score based)")

    def export_tree(self, root_id: int = None) -> str:
        """导出树结构为可读文本"""
        lines = []
        def dfs(node: ModuleNode, depth: int = 0):
            prefix = "  " * depth
            lines.append(f"{prefix}- [{node.confidence}] {node.shoreline_name or 'Unknown'} "
                         f"({node.hachimi_name}) id={node.id}")
            for child in node.children:
                dfs(child, depth + 1)

        if root_id and root_id in self.graph.nodes:
            dfs(self.graph.nodes[root_id])
        else:
            # 输出所有根节点
            roots = [n for n in self.graph.nodes.values() if n.parent is None]
            for root in roots:
                dfs(root)
        return "\n".join(lines)
```

#### 4. 使用示例

```python
if __name__ == "__main__":
    # 1. 初始化图并注入锚点
    graph = ModuleGraph()
    anchors = [
        ModuleNode(232, "pw.hachimi.client.\u0000hb\u200E", "BaritoneModule", "A"),
        ModuleNode(667, "pw.hachimi.client.\u0000dm\u200e", "AntiHungerModule", "A"),
        # ... 更多锚点
    ]
    for a in anchors:
        graph.add_node(a)

    # 2. 准备未知节点（从混淆后的 class 列表提取）
    unknown = [
        ModuleNode(1001, "pw.hachimi.client.\u0000xx\u200E", confidence="D"),
        # ... 更多未命名节点，并填充 config_count / listener_count / features
    ]

    # 3. 执行扩展
    expander = TreeExpander(graph, threshold=0.65)
    expander.expand(unknown)

    # 4. 导出结果
    print(expander.export_tree())
```

---

## 注意事项与建议

1. **解密 Key / IV 获取**：优先从 Native 层（JNI / SO）或运行时内存中提取，避免硬编码。
2. **阈值调优**：`expansion_threshold` 建议从 0.65~0.8 开始，结合人工验证调整权重。
3. **特征工程**：`StructureSimilarity` 可进一步加入方法描述符哈希、常量池特征、控制流图相似度等，提升准确率。
4. **合法合规**：仅用于授权的安全研究、自身软件分析或合法逆向场景。请遵守相关法律法规与软件许可协议。
5. **工具链推荐**：
   - 字节码分析：ASM、ByteBuddy、Javassist
   - 动态分析：Frida、JDWP、自定义 Java Agent
   - 图可视化：NetworkX + Graphviz 或 Cytoscape

---

**版本**：1.0  
**适用场景**：Java 混改客户端（invokedynamic + DES/CBC 字符串加密）的运行时还原与模块树重建。

如需针对特定类名、Key 提取逻辑或更精细的相似度特征进行扩展，可继续提供更多分析数据。

---

# 第三阶段：Hachimi Native 依赖剥离与纯 Java 重构策略

本阶段目标：将 `hachimi_b4_pw_1006.jar` 中重度依赖 Native 的类转换为纯 Java 实现，彻底移除对 `skidonion.vLZkx` 与 PhantomShield Native Bridge 的依赖。

## 1. 目标理解

用户希望逆向 Hachimi 模组（`hachimi_b4_pw_1006.jar`），移除其对 Native 方法的重度依赖，将当前依赖 Native 实现的类转换为纯 Java 类。

输入数据包括：
- 详细静态分析报告（README.md）
- 与开源模组 Shoreline 的交叉对照表（Shoreline_Module_Cross-Reference.md）

## 2. 输入数据分析摘要

### README.md 关键信息

| 项目 | 数值 / 说明 |
|------|-------------|
| 总类数 | 928（107 可见 + 821 隐藏伪目录） |
| 保护手段 | 字符串加密（DES/CBC + invokedynamic）、控制流平坦化、Native Bridge（VirtualProtect + PhantomShield） |
| Native 注册器 | `skidonion.vLZkx.___.___(ILjava/lang/Class;)V` |
| Native 注册 ID | ≈108 个 |
| Native 方法总数 | 464 |
| `pw.hachimi.client.*` Native 类 | 72 |
| `skidonion.vLZkx.*` Native 类 | 37 |

**核心类映射**：

| 混淆类 | 推测功能 |
|--------|----------|
| class_874 (`\u0000re\u200e`) | Module 系统核心 |
| class_919 (`\u0000nZ\u200E`) | Event 系统 |
| class_883 (`\u0000mZ\u200E`) | Command 系统 |
| class_908 (`\u0000qt\u200E`) | Setting 基类（含 Native JSON 方法） |
| class_910 (`\u0000jl\u200e`) | Setting 注册表 |
| class_806 (`\u0000ot\u200e`) | Module 二级基类（坐标/数值） |
| class_805 (`\u0000kb\u200e`) | 玩家选择基类 |

**Native 支持的 Hachimi 类**（Section 7）：class_908 / class_910 / class_872（Settings）、class_42（Combat）、class_270（Inventory）等。

**skidonion.vLZkx**：提供自定义 JSON 解析/写入、AWT GUI 事件、字节编码的 Native Runtime。

### Shoreline_Module_Cross-Reference.md 关键信息

- 目的：将 Hachimi 类映射到已知开源模组 Shoreline，以理解功能。
- 置信度：A（高）、B（中）、C（低）。
- 高置信映射示例：
  - class_232 → BaritoneModule（高，API 使用特征）
  - class_286 → FastPlaceModule（高，Mixin accessor）
  - class_478 → AntiSpamModule（高，Regex）
  - class_908 → Settings（Native JSON 方法）

此交叉对照表是后续逻辑还原的「规格说明书」。

## 3. 最有效策略（Leverage Shoreline as Spec）

Hachimi 极可能是 Shoreline 的重度保护版本（或高度相似实现）。最有效路径不是从零静态逆向所有 Native 逻辑，而是：

1. 以 Shoreline 源码作为「规格」。
2. 用交叉对照表将 Hachimi class_N 映射到 Shoreline 对应类。
3. 将 `skidonion.vLZkx` 调用替换为标准 Java 库（Gson / Jackson + 标准 AWT）。
4. 用 Shoreline 的纯 Java 实现直接移植替代 Native 方法体。

### 阶段一：恢复与命名（Foundation）

- 工具：CFR / Procyon / FernFlower（优先 CFR 处理 invokedynamic）。
- 使用 `native_mapping.json` 与 `jpi-mappings_pw.json` 辅助理解字符串与方法。
- 以 Shoreline 交叉对照表作为主命名依据，不要仅依赖静态分析。

### 阶段二：识别 Native 依赖

- 重点查看 `native_mapping.json`。
- 优先处理 README Section 7 列出的高 Native 密度类。
- 完整列出 `skidonion.vLZkx.*`（Section 8）作为 Runtime 替换目标。

### 阶段三：替换 Native Runtime（skidonion.vLZkx）

`skidonion.vLZkx` 本质上是自定义 JSON 解析器 + GUI 事件处理 + 字节编码 Runtime。

**替换方案**：

| 原 Native 功能 | 纯 Java 替代 |
|----------------|--------------|
| JSON 解析 / 写入 | Gson 或 Jackson |
| AWT 事件处理 | 标准 `java.awt` / `javax.swing` |
| 字节编码 / 转换 | `java.nio.ByteBuffer` / `java.util.Base64` |

实现步骤：
1. 在新项目中引入 Gson（或 Jackson）。
2. 将所有 `skidonion.vLZkx` 相关调用点替换为对应 Gson 方法。
3. 删除对 `skidonion.vLZkx` 包的依赖。

### 阶段四：重新实现 Native 方法体

这是核心工作量。推荐流程：

```
A. 在 native_mapping.json 中定位目标 native 方法签名
B. 查看 Hachimi 中对应 Java 类（即使方法体为空 / native）
C. 在 Shoreline 源码中找到功能等价的纯 Java 实现
D. 将 Shoreline 逻辑移植到新类，并正确映射参数
   （$jx / jI / pH / pj$ → 标准 Minecraft 对象）
```

**参数映射提示**：
- 常见 Native 参数多为混淆后的 Minecraft 对象引用（Entity、Player、World、BlockPos 等）。
- 使用交叉对照表与方法调用上下文推断真实类型。

### 阶段五：字符串解密（辅助）

DES/CBC 强度较低。Key / IV 通常位于：
- `ac` / `ad` / `ae` / `af` 相关类
- JAR 资源区中的查找表

可编写小型 Java / C 工具：
1. 提取加密用 byte[]。
2. 对常量池或字段值进行批量解密。
3. 将解密结果写回类文件或生成映射表。

（可结合本 README 第一阶段的 Runtime Decryption Interception 技术。）

### 阶段六：重建模块树与项目

1. 新建 Fabric / Quilt Minecraft 模组项目。
2. 使用交叉对照表将 class_N 重命名为有意义名称（class_232 → BaritoneModule 等）。
3. 重建继承层次（参考 class_874、class_806、class_805 等基类）。
4. 复制 Mixin 签名。
5. 将移植后的纯 Java 逻辑填入。
6. 使用本 README 第二阶段的 Anchor-based Tree Expansion 算法验证与补全模块树。

## 4. 推荐工作流总结

```
1. 加载 Shoreline 源码 + Hachimi 交叉对照表 → 建立「功能规格」
2. 使用 native_mapping.json 列出所有需替换的 native 方法
3. 替换 skidonion.vLZkx → Gson + 标准 AWT
4. 对每个 native 方法：
     找到 Shoreline 等价实现 → 移植 → 修正参数映射
5. 解密字符串（第一阶段技术）并恢复有意义字段名
6. 用 Anchor-based Tree Expansion 重建完整模块树
7. 在新 Fabric 项目中重新编译与测试
```

## 5. 工具链建议

| 用途 | 工具 |
|------|------|
| 反编译 | CFR（优先）、Procyon、FernFlower |
| 字节码修改 | ASM / ByteBuddy |
| Native 分析（可选深入） | Ghidra / IDA（PhantomShield） |
| JSON 替代 | Gson / Jackson |
| 项目框架 | Fabric Loom |
| 字符串解密 | 自定义 Java Agent 或独立解密工具 |
| 模块树验证 | 本 README 第二阶段 Python 脚本 |

## 6. 注意事项

- 仅用于授权的安全研究或自身软件分析。
- Shoreline 作为开源参考，可大幅降低逆向成本，但请遵守其许可证。
- Native 方法中可能存在反调试 / 完整性校验，移植时需同步处理。
- 控制流平坦化会增加静态阅读难度，建议结合动态调试（JDWP / Frida）确认关键路径。

---

**版本**：2.0  
**更新内容**：新增「Hachimi Native 依赖剥离与纯 Java 重构策略」完整阶段。
