# Dex 编辑器（Dex Editor）

> by.吾爱破解52pojie.cn 空满水杯

一款基于 jadx 与 smali 3.0.9 的 Android 端 DEX/APK 编辑器，支持反汇编、反编译、编辑与重新编译，并集成 AndroidManifest 编辑、APK 资源替换、重签名、壳检测与脱壳、MCP AI 服务端等能力。

---

## 1. 软件介绍

Dex 编辑器是一款运行于 Android 设备上的 DEX/APK 文件查看与编辑工具。它整合了业界主流的反编译/反汇编引擎，让用户无需 PC 即可在手机上完成对 DEX 文件中类的浏览、Smali 代码查看、Java 源码反编译、Smali 代码在线编辑与重新编译等操作。

v1.1 起，进一步集成了 AndroidManifest.xml 二进制 ↔ 文本互转、APK 资源（图标/布局/字符串）替换、APK 重新打包与签名、加壳 APK 检测与静态脱壳，以及自定义缓存/脱壳/成品文件路径等能力，参考 Apktool 与 BlackDex 两个开源项目（均为 Apache License 2.0）。

v1.2 起，APK 资源页改为层级树展示（默认折叠，支持展开/折叠）；支持直接编辑 APK 内 XML 条目（二进制 AXML 自动解码/回写，含语法高亮）；打包签名仅使用 v2 + v3 方案；设置页新增官方网站链接（吾爱破解论坛）。

v1.3 起，新增 **MCP（Model Context Protocol）服务端**：手机或同局域网电脑上的 AI 软件（Claude、ChatMCP、Cursor 等）可通过 MCP 协议连接本应用，调用 15 个工具完成加载分析 DEX/APK、反编译看代码、读改 AndroidManifest、替换资源、检测壳、重签名等操作——让 AI 直接帮你做逆向。

本软件面向 Android 逆向爱好者与开发者，特别感谢 **吾爱破解（52pojie.cn）** 社区的支持。

### 应用诞生灵感

起初在安卓手机上编译某一APK时，找了很多手机反汇编软件（像MT文件管理器、NP文件管理器等），要么收费，要么没有想要的功能，在 `https://down.52pojie.cn/Tools/Android_Tools/` 上下载了电脑端的jadx-1.5.5.zip后，发现没有手机端的软件，于是有了这个Dex 编辑器（Dex Editor），欢迎各位使用，有任何bug及建议，回复本贴即可。

### 应用截图说明

- **浏览界面**：以包层级树形结构展示 DEX 中的所有类，包名后显示子类数量，支持展开/折叠
- **搜索界面**：按类名 / 方法名 / 字符串关键字快速检索
- **Manifest 界面**：二进制 AXML 反编译为文本 XML，资源 ID 自动转名称，语法高亮 + 自动补全 + 格式化
- **资源界面**：层级树展示 APK 内所有条目（默认折叠，可展开/折叠），替换图标/布局/字符串等资源，直接编辑 XML 条目
- **脱壳界面**：自动检测加壳 APK 并执行静态脱壳，输出可编辑的 DEX
- **信息界面**：显示文件名、大小、类/方法/字段/字符串/包数量等统计信息
- **设置界面**：自定义缓存 / 脱壳 / 成品 / 密钥库路径（默认 `Download/dex52pj`），含官方网站链接
- **MCP 服务界面**（v1.3）：一键启动/停止 MCP 服务端，显示局域网地址与实时日志，内置使用教程
- **代码界面**：Smali 反汇编视图与 Java 反编译视图一键切换，支持编辑与编译
- **关于对话框**：通过工具栏菜单"关于"打开，展示软件说明

### 应用图标

```
  ▶    DEX    ◀
```

两个实心三角形向中间聚焦，突出显示 "DEX" 字样，象征对 DEX 文件的聚焦解析。

---

## 2. 功能特性

### 文件加载
- 支持加载单个 `.dex` 文件
- 支持加载 `.apk` 文件（自动提取其中的 DEX）
- 支持选择文件夹批量加载其中所有 `.dex` 文件

### 类浏览
- 按包层级树形结构展示所有类
- 包节点可展开/折叠，包名后显示 `(N)` 子类数量
- 递归缩进显示层级关系

### 代码查看与编辑
- **Smali 反汇编**：基于 baksmali 引擎，输出格式化的 Smali 代码
- **Java 反编译**：基于 jadx 引擎，一键将 Smali 转换为可读的 Java 源码
- **在线编译**：编辑 Smali 代码后可重新编译为 DEX 文件
- **复制功能**：一键复制代码内容到剪贴板

### 搜索功能
- 按类名搜索
- 按方法名搜索
- 按字符串内容搜索
- 结果数量超过 500 条时自动截断，避免界面卡顿

### 统计信息
- 文件名、文件大小（自动格式化）
- DEX 数量、类总数、方法总数、字段总数、字符串总数、包总数

### AndroidManifest.xml 编辑（v1.1 新增）
- **反编译**：二进制 AXML → 文本 XML，内置原生 XML 格式器做缩进 / 对齐美化
- **重编译**：修改后的文本 XML → 二进制 AXML，可回写 APK
- **资源 ID 转名称**：同目录放置 `resources.arsc` 时，自动将 `@7F010000` 还原为 `@string/app_name`
- **语法高亮**：完整支持 XML 声明 `<?xml ...?>`、处理指令 `<?...?>`、DOCTYPE、CDATA、标签名、命名空间、属性名、属性值、实体引用（`&amp;` `&#123;` `&#x7f;`，错误实体标红）、注释 `<!-- -->`，以及括号配对 `<> {} [] ()`
- **颜色取色器**：在属性值中通过正则 `#([a-fA-F0-9]{3,8})` 识别颜色并实时预览
- **自动补全**：标签 / 属性自动补全，多选项组合值快速编辑

### APK 资源编辑（v1.1 新增，v1.2 增强）
- 浏览 APK 内的所有资源条目，**层级树展示**，默认折叠，支持展开/折叠（v1.2）
- 替换图标、布局、字符串等资源
- 重新打包并使用内置密钥签名 APK（**v2 + v3**，v1.2 起不再生成 v1/JAR 签名）

### APK 内 XML 直接编辑（v1.2 新增）
- 选中任意 `.xml` 条目后点击"编辑XML"即可在线编辑
- 自动识别二进制 AXML（前两字节 `0x0003`）并解码为文本
- 解码时自动加载 APK 内 `resources.arsc`，将 `@7F010000` 还原为 `@string/app_name`
- 编辑器内置 XML 语法高亮（标签 / 属性 / 值 / 注释 / 实体 / 颜色 / 括号配对）
- 保存时按原格式回写：二进制 AXML → `AxmlConverter.toBinary()`，文本 XML → UTF-8
- 修改写入 `_edited.apk`，可继续点"打包签名"生成最终 APK

### 加壳检测与脱壳（v1.1 新增）
- 自动识别 360 加固、腾讯乐固、爱加密、梆梆、百度、阿里 等常见加壳厂商
- 检测到加壳后执行静态脱壳，输出可编辑的 DEX 文件
- 脱壳后的 DEX 自动进入编辑流程

### 路径配置（v1.1 新增）
- 自定义缓存文件 / 脱壳文件 / 成品文件 / 密钥库文件的存放路径
- 默认路径：`Download/dex52pj`（参考 BlackDex 的存储约定）
- 通过设置界面（PreferenceFragment）配置

### 官方网站（v1.2 新增）
- 设置页"关于"分类下新增官方网站链接
- 点击跳转浏览器打开吾爱破解论坛帖子页

### MCP 服务端（v1.3 新增）

让 AI 成为你的逆向助手：本应用内置 MCP（Model Context Protocol）服务端，手机或同局域网电脑上的 AI 客户端（Claude / ChatMCP / Cursor 等）连接后，可调用以下 15 个工具：

| 工具 | 功能 |
|------|------|
| `get_status` | 获取当前状态（已加载文件、类/方法/字段统计、输出目录） |
| `load_file` | 加载手机上的 DEX/APK 文件 |
| `list_classes` | 分页列出类（支持前缀过滤） |
| `search_code` | 按类名/方法名/字符串搜索 |
| `get_smali` | 获取类的 Smali 反汇编代码 |
| `decompile_class` | 反编译类为 Java 源码（jadx） |
| `compile_smali` | 编译 Smali 代码为 DEX |
| `list_dir` | 浏览手机目录（查找 APK/DEX） |
| `list_apk_entries` | 列出 APK 内所有条目 |
| `read_apk_entry` | 读取 APK 条目（文本/base64/AXML 自动解码） |
| `read_manifest` | 读取 AndroidManifest.xml（资源 ID 还原名称） |
| `write_manifest` | 修改 Manifest 并回写新 APK |
| `replace_apk_entry` | 替换 APK 条目（文本/二进制，AXML 自动编码） |
| `detect_packer` | 检测加壳（360/腾讯乐固/爱加密等） |
| `sign_apk` | 内置密钥签名 APK（v2+v3） |

- **双传输兼容**：同时支持 Streamable HTTP（`POST /mcp`，MCP 2025-03-26 规范）与传统 SSE（`GET /sse`，2024-11-05 规范），新旧客户端均可连接
- **前台服务保活**：切到 AI 应用后服务不中断
- **实时日志**：服务页实时显示请求日志（方法名、SSE 会话、异常）
- **状态共享**：与 UI 共享 DexLoader 单例，AI 可直接分析你在应用里打开的文件

### 其他特性
- 异步加载：文件加载、反编译、反汇编均在后台线程执行，不阻塞 UI
- 错误处理：加载失败、反编译失败均有友好提示
- 稳定性优化：修复了 TextWatcher 无限递归导致的 StackOverflowError

---

## 3. 开源地址

**GitHub 仓库**：`https://github.com/liewec/jadx/blob/main/jadx-android-app/`

**APK 下载**：`https://github.com/liewec/jadx/blob/main/jadx-android-app/releases/dex-editor-debug.apk`

**分支说明**：
- `main`：主分支，包含最新稳定代码与编译后的 APK

---

## 4. 操作流程

### 4.1 打开文件

1. 启动应用，进入"浏览"界面
2. 点击工具栏的 **打开文件**（文件图标）按钮
3. 在系统文件选择器中选中 `.dex` 或 `.apk` 文件
4. 等待加载完成，底部会弹出 Toast 提示"已加载 N 个类"

### 4.2 打开文件夹（批量加载）

1. 点击工具栏的 **打开文件夹**（文件夹图标）按钮
2. 在系统文件夹选择器中选中目标目录
3. 应用会自动扫描该目录下所有 `.dex` 文件并批量加载
4. 加载完成后提示"已加载 N 个文件，共 M 个类"

### 4.3 浏览类

1. 加载完成后，"浏览"界面以树形结构展示所有类
2. 点击包节点可展开/折叠
3. 点击类节点进入代码查看界面

### 4.4 查看 Smali / Java 代码

1. 在浏览界面点击某个类，进入代码界面
2. 默认显示 Smali 反汇编代码
3. 点击顶部的 **切换按钮** 可切换到 Java 反编译视图
4. 再次点击切回 Smali 视图

### 4.5 编辑并编译 Smali

1. 在 Smali 视图下直接编辑代码
2. 点击 **编译** 按钮
3. 编译结果以对话框形式展示（成功/失败 + 输出路径）

### 4.6 搜索

1. 切换到"搜索"界面
2. 在下拉框选择搜索类型（类 / 方法 / 字符串）
3. 输入关键字，点击 **搜索** 按钮
4. 结果列表中点击条目可直接跳转到对应类的代码界面

### 4.7 查看信息

1. 切换到"信息"界面
2. 查看文件名、大小、各类统计数量

### 4.8 编辑 AndroidManifest.xml（v1.1）

1. 切换到"Manifest"界面
2. 点击 **打开 APK**，选择目标 APK
3. 点击 **反编译**，将二进制 AXML 转为可读文本 XML
4. 如需还原资源 ID 为名称，将 `resources.arsc` 放在与 APK 同目录
5. 在编辑器中修改 XML，享受语法高亮、自动补全与颜色取色器
6. 点击 **格式化** 对 XML 做缩进 / 对齐美化
7. 点击 **保存回写**，将文本 XML 重新编码为二进制 AXML 并回写 APK

### 4.9 替换 APK 资源（v1.1，v1.2 增强）

1. 切换到"资源"界面
2. 浏览 APK 内的资源条目（图标 / 布局 / 字符串 等），层级树展示，默认折叠，点击目录展开/折叠
3. 选中条目后点击"替换资源"，选择本地替换文件
4. 点击"打包签名"，使用内置密钥签名 APK（**仅 v2 + v3**）

### 4.10 直接编辑 APK 内 XML（v1.2 新增）

1. 在"资源"界面选中任意 `.xml` 条目（如 `AndroidManifest.xml`、`res/layout/main.xml`）
2. 点击"编辑XML"，弹出编辑对话框
3. 二进制 AXML 自动解码为文本（自动加载 `resources.arsc` 还原资源 ID 名称）
4. 在编辑器中修改，享受语法高亮（标签 / 属性 / 值 / 注释 / 实体 / 颜色 / 括号配对）
5. 点击"确定"保存，按原格式回写（二进制 AXML → `toBinary()`，文本 → UTF-8），输出 `_edited.apk`
6. 点击"打包签名"生成最终签名 APK

### 4.11 加壳检测与脱壳（v1.1）

1. 切换到"脱壳"界面
2. 选择待检测的 APK
3. 应用自动识别加壳厂商（360 / 腾讯乐固 / 爱加密 / 梆梆 / 百度 / 阿里 等）
4. 若检测到加壳，自动执行静态脱壳，输出 DEX 到自定义脱壳目录
5. 脱壳后的 DEX 可直接进入 Dex 编辑流程

### 4.12 自定义路径与官网（v1.1，v1.2 增强）

1. 通过工具栏溢出菜单进入 **设置**
2. 分别配置缓存文件、脱壳文件、成品文件、密钥库文件的存放路径
3. 默认路径为 `Download/dex52pj`
4. "关于"分类下点击"官方网站：点击查看"跳转浏览器打开吾爱破解论坛帖子页

### 4.13 关于

1. 点击工具栏右上角的溢出菜单（三个点）
2. 选择 **关于**
3. 查看软件说明、功能列表、技术栈、致谢等信息

### 4.14 MCP 服务 + 手机 AI 软件联动（v1.3 新增）

MCP（Model Context Protocol）是 AI 客户端与外部工具交互的标准协议。本应用作为 **MCP 服务端** 运行，把 DEX/APK 的分析与编辑能力开放给 AI——手机上的 AI 助手或同局域网的电脑 AI 都能连接，直接指挥本应用完成逆向任务。

#### 第一步：启动 MCP 服务

1. 点击工具栏溢出菜单（三个点）→ **MCP 服务**
2. 确认端口（默认 `33333`，可自定义 1024–65535）
3. 点击 **启动服务**，首次会请求通知权限（用于前台服务常驻）
4. 状态变为 "● MCP 服务运行中"，页面显示局域网地址，例如：
   - Streamable HTTP：`http://192.168.1.5:33333/mcp`
   - 传统 SSE：`http://192.168.1.5:33333/sse`

> 启动后可切换到其他应用，前台服务保持运行；通知栏常驻 "Dex 编辑器 MCP 服务运行中"。

#### 第二步：手机端 AI 软件连接

1. 在手机上安装任意支持 MCP 的 AI 客户端（如 Claude 移动版、ChatMCP、其他支持自定义 MCP Server 的 AI App）
2. 在其 **MCP 服务器 / MCP 设置** 中新增服务器：
   - 类型选择 **Streamable HTTP**（或 HTTP）：URL 填 `http://127.0.0.1:33333/mcp`（同一台手机）
   - 若 AI 软件与手机不在同一设备，改用手机的局域网 IP：`http://<手机IP>:33333/mcp`
   - 老版本客户端仅支持 **SSE** 类型：URL 填 `http://<手机IP>:33333/sse`
3. 连接成功后，AI 会发现 15 个工具（get_status / load_file / decompile_class …）

> 手机 IP 查看方式：系统设置 → WLAN → 点击当前网络 → 查看 IP 地址。MCP 服务页显示的地址即当前 IP。

#### 第三步：电脑端 AI 连接（可选）

电脑与手机连接同一 Wi-Fi，在 Claude Desktop / Cursor 等的 MCP 配置文件中添加：

```json
{
  "mcpServers": {
    "dex-editor": {
      "url": "http://192.168.1.5:33333/mcp"
    }
  }
}
```

（把 `192.168.1.5` 换成你手机的局域网 IP）

#### 第四步：开始对话

对 AI 直接下达逆向任务，例如：

- "加载 `/storage/emulated/0/Download/app.apk`，分析它的入口 Activity 和权限列表"
- "把 manifest 的 `targetSdkVersion` 改成 34，输出新 APK 并签名"
- "反编译 `com.example.MainActivity`，解释它的网络请求逻辑"
- "搜索字符串 `api.weixin` 看看哪些类在用"
- "检测这个 APK 有没有加壳"

AI 会自动组合调用工具：`load_file` → `read_manifest` / `search_code` → `decompile_class` → `write_manifest` → `sign_apk`，产物输出到 `Download/dex52pj/output`。

#### 第五步：用完关闭

返回 MCP 服务页点击 **停止服务**（或划掉通知），避免端口长期开放。

#### 安全提示

- 服务**无鉴权**，同一局域网内任何设备都可访问，请仅在可信网络使用
- AI 可读取被分析的 APK 内容（含其中的字符串/密钥），注意敏感样本
- 修改/签名产物均落在 `Download/dex52pj/output`，不会动原文件

---

## 5. 核心技术架构

### 5.1 整体架构

```
┌──────────────────────────────────────────────────────────┐
│                         UI 层                            │
│  MainActivity + 8 Fragment + Adapters                    │
│  Browse / Search / Manifest / Resource / Unshell /       │
│  Info / Settings / MCP                                    │
├──────────────────────────────────────────────────────────┤
│                         业务层                           │
│  DexLoader（单例）+ SmaliUtils + PathConfig              │
├──────────────────────────────────────────────────────────┤
│                    MCP 服务层（v1.3 新增）               │
│  McpServer（NanoHTTPD + JSON-RPC，Streamable HTTP + SSE）│
│  McpTools（15 个工具，复用业务层）+ McpService（前台服务）│
├──────────────────────────────────────────────────────────┤
│                       APK 处理层                         │
│  ApkBuilder（解包/打包/签名）+ Unpacker（脱壳）          │
│  BuiltinKey（内置密钥库）+ SelfSignedCertGen             │
├──────────────────────────────────────────────────────────┤
│                       AXML 层                            │
│  BinaryXml（二进制 AXML 解析/编码）+ AxmlConverter       │
│  ArscParser（resources.arsc → 资源 ID 名称映射）         │
│  XmlFormatter（文本 XML 格式化）                         │
├──────────────────────────────────────────────────────────┤
│                       编辑器组件                         │
│  XmlSyntaxHighlighter + XmlAutoComplete                  │
├──────────────────────────────────────────────────────────┤
│                      核心引擎层                          │
│  jadx-core（反编译）+ smali（反汇编/编译）+ apksig（签名）│
└──────────────────────────────────────────────────────────┘
```

### 5.2 关键类设计

#### DexLoader（单例模式）
- `getInstance()`：双重检查锁定获取单例
- `load(File)`：加载 DEX/APK，构建 `classes` 列表
- `loadMultiple(List<File>)`：批量加载多个 DEX 文件
- `loadFromUri(Uri)`：从 URI 加载（复制到缓存后调用 `load`）
- `buildTree()`：按 `Lcom/example/Class;` 拆分包层级，构建 `ClassNode` 树
- `search(int kind, String keyword)`：按类型搜索

#### ClassNode（树形数据模型）
- 区分包（`TYPE_PACKAGE`）和类（`TYPE_CLASS`）
- `expanded` 字段记录折叠状态
- `countClasses()` 递归统计子类数量

#### ClassTreeAdapter（树形适配器）
- `flatten()` 递归展平可见节点
- 包节点显示图标 + 名称 + `(N)`
- 类节点显示图标 + 名称
- 点击包切换 `expanded` 并 `rebuild`，点击类触发 `onClassClicked`

#### SmaliUtils（代码工具）
- `disassemble(ClassDef)`：baksmali 反汇编
- `decompileToJava(File, String)`：jadx 反编译为 Java（遍历 `getClasses()` + 内部类）
- `compile(String, String)`：smali 重新编译为 DEX

#### ApkBuilder（APK 处理，v1.1 新增）
- 解包 APK 到指定目录
- `replaceEntry(...)`：替换 APK 内指定条目
- `signWithBuiltinKey(...)`：使用内置密钥签名 APK（v1 + v2 + v3，基于 apksig）

#### Unpacker（脱壳，v1.1 新增）
- 检测加壳厂商（manifest 特征 / 文件特征 / 字符串特征）
- 静态脱壳：从原 APK 中提取被保护的 DEX

#### BinaryXml / AxmlConverter（AXML 互转，v1.1 新增）
- `BinaryXml.decode(byte[])`：二进制 AXML → Document
- `BinaryXml.encode(Document)`：Document → 二进制 AXML
- `AxmlConverter`：高阶 API，文本 XML ↔ 二进制 AXML

#### ArscParser（资源 ID 映射，v1.1 新增）
- 解析 `resources.arsc`，构建 `resourceId -> @type/name` 映射
- 配合 `BinaryXml` 将 `@7F010000` 还原为 `@string/app_name`

#### XmlSyntaxHighlighter / XmlAutoComplete（编辑器组件，v1.1 新增）
- 完整 XML 语法高亮（声明/指令/DOCTYPE/CDATA/标签/命名空间/属性/实体/注释/颜色/括号配对）
- 错误实体标记为 error 颜色
- 标签 / 属性自动补全

#### PathConfig（路径配置，v1.1 新增）
- 管理缓存 / 脱壳 / 成品 / 密钥库文件路径
- 默认 `Download/dex52pj`
- 通过 `PreferenceFragment` 持久化

#### McpServer / McpTools / McpService（MCP 服务端，v1.3 新增）
- `McpServer`：基于 NanoHTTPD 的 MCP 协议实现，支持 Streamable HTTP（`POST /mcp` 无状态 JSON-RPC）与传统 SSE（`GET /sse` + `POST /message?sessionId=xxx`，队列+管道驱动 chunked 流）双传输；支持 `initialize` / `ping` / `tools/list` / `tools/call` / `resources/list` / `prompts/list` 与 JSON-RPC 批量请求
- `McpTools`：15 个工具的元数据（名称/描述/JSON Schema）与执行分发；复用 DexLoader / SmaliUtils / ApkBuilder / AxmlConverter / ArscParser / Unpacker / PathConfig，与 UI 共享状态；错误以 `isError=true` 的文本内容返回给 AI
- `McpService`：前台服务（`dataSync` 类型）承载 McpServer 生命周期，常驻通知显示访问地址

### 5.3 异步处理

所有耗时操作均通过 `ExecutorService`（单线程）+ `Handler`（主线程回调）实现异步：

- 文件加载：`MainActivity.loadThread`
- 反编译/反汇编：`SmaliFragment.executor`
- 搜索：`SearchFragment.executor`

### 5.4 稳定性设计

- **StackOverflowError 修复**：SearchFragment 的 TextWatcher 加 `clearing` 标志位，防止 `clearResults()` → `setText("")` → `afterTextChanged()` 无限递归
- **UI 卡顿修复**：错误堆栈文本截断至 6000 字符，`onLoaded()` 异步化
- **复制闪退修复**：改为保存到本地文件 `/sdcard/Android/data/com.jadx.dexeditor/files/error-logs/`

---

## 6. 技术栈

### 核心引擎

| 组件 | 版本 | 用途 | 来源 |
|------|------|------|------|
| jadx-core | 1.5.6 | Smali → Java 反编译 | `https://github.com/skylot/jadx` |
| jadx-dex-input | 1.5.6 | DEX 文件输入插件 | `https://github.com/skylot/jadx` |
| jadx-smali-input | 1.5.6 | Smali 文件输入插件 | `https://github.com/skylot/jadx` |
| jadx-java-convert | 1.5.6 | Java 转换支持 | `https://github.com/skylot/jadx` |
| smali | 3.0.9 | Smali 反汇编/编译 | `https://github.com/JesusFreke/smali` |
| smali-baksmali | 3.0.9 | baksmali 反汇编引擎 | `https://github.com/JesusFreke/smali` |
| apksig | 8.5.0 | APK 签名（v1/v2/v3） | `https://android.googlesource.com/platform/tools/base/` |
| NanoHTTPD | 2.3.1 | MCP 服务端 HTTP 服务器 | `https://github.com/NanoHttpd/nanohttpd` |
| Gson | 2.10.1 | JSON-RPC 序列化/反序列化 | `https://github.com/google/gson` |

### AXML / ARSC / 脱壳（v1.1 新增，自实现）

| 模块 | 用途 | 参考来源 |
|------|------|----------|
| BinaryXml | 二进制 AXML 解析/编码 | AOSP `ResourceTypes.h` |
| AxmlConverter | 文本 XML ↔ 二进制 AXML 高阶 API | Apktool |
| ArscParser | 解析 `resources.arsc`，资源 ID → 名称 | Apktool |
| XmlFormatter | 文本 XML 缩进 / 对齐美化 | 自实现 |
| XmlSyntaxHighlighter | XML 语法高亮 + 颜色取色器 + 括号配对 | 自实现 |
| XmlAutoComplete | XML 标签 / 属性自动补全 | 自实现 |
| ApkBuilder | APK 解包 / 打包 / 签名 | Apktool |
| BuiltinKey | 内置签名密钥库 | 自实现 |
| Unpacker | 加壳检测与静态脱壳 | BlackDex |

### Android UI 框架

| 组件 | 版本 |
|------|------|
| androidx.appcompat:appcompat | 1.6.1 |
| androidx.activity:activity-ktx | 1.9.0 |
| androidx.fragment:fragment-ktx | 1.7.1 |
| androidx.recyclerview:recyclerview | 1.3.2 |
| androidx.preference:preference | 1.2.1 |
| com.google.android.material:material | 1.12.0 |
| androidx.constraintlayout:constraintlayout | 2.1.4 |

### 构建工具

| 工具 | 版本 |
|------|------|
| Android Gradle Plugin | 8.5.0 |
| Gradle | 8.14 |
| JDK | 21（编译）/ 17（目标）|
| compileSdk / targetSdk | 34 |
| minSdk | 26（Android 8.0）|

### 开发环境

- 操作系统：Android 8.0 (API 26) 及以上
- 架构：纯 Java 实现，无 Native 代码

---

## 7. 更新日志

### v1.3（2026-08-15）

#### 新功能
- **MCP（Model Context Protocol）服务端**
  - 手机端 / 同局域网电脑端的 AI 软件（Claude、ChatMCP、Cursor 等）可通过 MCP 协议连接本应用，直接调用 DEX/APK 分析与编辑能力
  - 双传输兼容：Streamable HTTP（`POST /mcp`，MCP 2025-03-26 规范）+ 传统 SSE（`GET /sse`，2024-11-05 规范）
  - 完整 JSON-RPC 2.0：`initialize` / `ping` / `tools/list` / `tools/call` / `resources/list` / `prompts/list`，支持批量请求与通知
  - **15 个 MCP 工具**：get_status / load_file / list_classes / search_code / get_smali / decompile_class / compile_smali / list_dir / list_apk_entries / read_apk_entry / read_manifest / write_manifest / replace_apk_entry / detect_packer / sign_apk
  - 读取 APK 条目自动识别二进制 AXML 并解码为文本；文本方式替换 XML 条目时自动重新编码
  - 前台服务保活（dataSync 类型），切换应用后服务不中断；常驻通知显示访问地址
  - 与 UI 共享 DexLoader 单例状态：AI 可直接分析应用内已打开的文件
- **MCP 服务页**：工具栏菜单新增入口；端口可配置（默认 33333）；实时请求日志；点击复制连接地址；内置使用教程（手机端/电脑端连接配置、对话示例、安全提示）

#### 技术变更
- 新增依赖：NanoHTTPD 2.3.1（HTTP 服务器）、Gson 2.10.1（JSON 处理）
- AndroidManifest 新增 INTERNET / ACCESS_NETWORK_STATE / ACCESS_WIFI_STATE / FOREGROUND_SERVICE(_DATA_SYNC) / POST_NOTIFICATIONS 权限与 `McpService` 服务声明
- 服务器实现：`mcp` 包（McpServer / McpTools / McpService）

### v1.2（2026-07-27）

#### 新功能
- **APK 资源页层级树展示**
  - 资源条目按目录层级组织为树形结构
  - 默认折叠，支持点击展开/折叠
  - 目录节点显示图标 + 名称 + 子条目数量
- **APK 内 XML 直接编辑**
  - 在"资源"界面选中任意 `.xml` 条目（如 `AndroidManifest.xml`、`res/layout/*.xml`）后点击"编辑XML"即可在线编辑
  - 自动识别二进制 AXML（前两字节 `0x0003`）并解码为文本 XML
  - 解码时自动加载 APK 内 `resources.arsc`，将 `@7F010000` 还原为 `@string/app_name`
  - 编辑器内置 XML 语法高亮（标签 / 属性 / 值 / 注释 / 实体 / 颜色 / 括号配对）
  - 保存时按原格式回写：二进制 AXML → `AxmlConverter.toBinary()`，文本 XML → UTF-8
  - 修改写入 `_edited.apk`，可继续点"打包签名"生成最终 APK
- **官方网站链接**：设置页"关于"分类下新增官方网站入口，点击跳转浏览器打开吾爱破解论坛帖子页

#### 功能变更
- **签名方案调整**：打包签名仅使用 v2 + v3 方案，不再生成 v1 / JAR 签名（基于 apksig）
- **关于对话框**：更新至 v1.2，补充 XML 编辑、层级树、官网链接等功能说明

#### License 合规
- 继续遵循 Apktool / BlackDex / jadx / smali / apksig 的 Apache License 2.0 归属声明
- AXML 静态检测（文件头特征识别）、XML 编辑流程均为自实现

### v1.1（2026-07-26）

#### 新功能
- **AndroidManifest.xml 编辑**
  - 反编译：二进制 AXML → 文本 XML，内置原生 XML 格式器做缩进 / 对齐美化
  - 重编译：修改后的文本 XML → 二进制 AXML，可回写 APK
  - 资源 ID 转名称：同目录放置 `resources.arsc` 时，自动将 `@7F010000` 还原为 `@string/app_name`
  - 语法高亮：完整支持 XML 声明 / 处理指令 / DOCTYPE / CDATA / 标签 / 命名空间 / 属性 / 实体引用 / 注释 / 颜色常量 / 括号配对
  - 颜色取色器：属性值中通过正则 `#([a-fA-F0-9]{3,8})` 识别颜色并实时预览
  - 自动补全：标签 / 属性自动补全，多选项组合值快速编辑
- **APK 资源编辑**：替换 APK 内的图标、布局、字符串等资源
- **重新打包并签名 APK**：使用内置密钥签名 APK（v1 + v2 + v3，基于 apksig）
- **加壳检测与脱壳**：自动识别 360 加固 / 腾讯乐固 / 爱加密 / 梆梆 / 百度 / 阿里 等加壳厂商，并执行静态脱壳
- **路径配置**：自定义缓存 / 脱壳 / 成品 / 密钥库文件路径，默认 `Download/dex52pj`（参考 BlackDex）
- **关于页面**：更新至 v1.1

#### License 合规
- 在 `NOTICE` 文件补充 Apktool 与 BlackDex 的 Apache License 2.0 归属声明
- AXML / ARSC 解析、APK 处理、脱壳逻辑均为自实现，仅参考对应开源项目的实现思路

### v1.0（2026-07-22）

#### 初始版本
- **2026-07-21**：初始上传，按架构完整重建，移除手机端调试输出
  - DexLoader 单例模式，内部同步构建 ClassNode 树
  - ClassTreeAdapter 包可展开/折叠，包名后显示 `(N)` 子类数量
  - BrowseFragment 同步 setRoot、setLoading 进度条控制
  - SmaliFragment ToggleButton 切换 Smali/Java 视图，异步反编译/反汇编
  - SearchFragment Spinner + Button 触发搜索，ExecutorService 异步搜索
  - InfoFragment 单 TextView 显示所有统计信息

#### Bug 修复
- **2026-07-22**：修复信息页 `%1$d` 乱码  + 关于对话框
  - 重新编译 APK（之前 GitHub 上的 APK 是用旧版 strings.xml 编译的）
  - toolbar 添加副标题
  - 新增"关于"菜单项与 AlertDialog

- **2026-07-22**：彻底修复信息页乱码 + 关于页换行  + Java 反编译
  - 删除 `values-zh-rCN/strings.xml`（含 `%1$s`/`%1$d` 占位符，覆盖默认值导致中文系统乱码）
  - 关于页面改用 `\n` 显式换行（CDATA 换行在 Android 中不生效），作者改为"空满水杯"
    - 修复 Java 反编译 bug：添加 `jadx-dex-input`/`jadx-smali-input`/`jadx-java-convert` 依赖
  - 改进 `decompileToJava`：遍历 `getClasses()` + 内部类，返回具体错误信息

#### 功能优化
- **2026-07-22**：应用图标优化

---

## 致谢

- `https://github.com/skylot/jadx` - 强大的 Dex 到 Java 反编译器（Apache License 2.0）
- `https://github.com/JesusFreke/smali` - Smali 反汇编/编译工具（Apache License 2.0）
- `https://github.com/iBotPeaches/Apktool` - AXML / ARSC 解析与 APK 处理参考实现（Apache License 2.0）
- `https://github.com/codinggay/blackdex` - 脱壳思路与默认输出路径参考（Apache License 2.0）
- `https://www.52pojie.cn/` - 技术交流社区

## 作者

**空满水杯** · by.吾爱破解52pojie.cn

## License

本项目仅供学习交流使用，相关引擎与参考项目遵循其原始 License：
- jadx: Apache License 2.0
- smali: Apache License 2.0
- Apktool: Apache License 2.0
- BlackDex: Apache License 2.0
- apksig: Apache License 2.0

完整的第三方归属声明请参见仓库根目录的 `NOTICE` 文件。
