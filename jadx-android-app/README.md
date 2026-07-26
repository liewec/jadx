# Dex 编辑器（Dex Editor）

> by.吾爱破解52pojie.cn 空满水杯

一款基于 jadx 与 smali 3.0.9 的 Android 端 DEX/APK 编辑器，支持反汇编、反编译、编辑与重新编译，并集成 AndroidManifest 编辑、APK 资源替换、重签名、壳检测与脱壳等能力。

---

## 1. 软件介绍

Dex 编辑器是一款运行于 Android 设备上的 DEX/APK 文件查看与编辑工具。它整合了业界主流的反编译/反汇编引擎，让用户无需 PC 即可在手机上完成对 DEX 文件中类的浏览、Smali 代码查看、Java 源码反编译、Smali 代码在线编辑与重新编译等操作。

v1.1 起，进一步集成了 AndroidManifest.xml 二进制 ↔ 文本互转、APK 资源（图标/布局/字符串）替换、APK 重新打包与签名（v1+v2+v3）、加壳 APK 检测与静态脱壳，以及自定义缓存/脱壳/成品文件路径等能力，参考 Apktool 与 BlackDex 两个开源项目（均为 Apache License 2.0）。

本软件面向 Android 逆向爱好者与开发者，特别感谢 **吾爱破解（52pojie.cn）** 社区的支持。

### 应用诞生灵感

起初在安卓手机上编译某一APK时，找了很多手机反汇编软件（像MT文件管理器、NP文件管理器等），要么收费，要么没有想要的功能，在 `https://down.52pojie.cn/Tools/Android_Tools/` 上下载了电脑端的jadx-1.5.5.zip后，发现没有手机端的软件，于是有了这个Dex 编辑器（Dex Editor），欢迎各位使用，有任何bug及建议，回复本贴即可。

### 应用截图说明

- **浏览界面**：以包层级树形结构展示 DEX 中的所有类，包名后显示子类数量，支持展开/折叠
- **搜索界面**：按类名 / 方法名 / 字符串关键字快速检索
- **Manifest 界面**：二进制 AXML 反编译为文本 XML，资源 ID 自动转名称，语法高亮 + 自动补全 + 格式化
- **资源界面**：浏览并替换 APK 内的图标、布局、字符串等资源
- **脱壳界面**：自动检测加壳 APK 并执行静态脱壳，输出可编辑的 DEX
- **信息界面**：显示文件名、大小、类/方法/字段/字符串/包数量等统计信息
- **设置界面**：自定义缓存 / 脱壳 / 成品 / 密钥库路径（默认 `Download/dex52pj`）
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

### APK 资源编辑（v1.1 新增）
- 浏览 APK 内的所有资源条目
- 替换图标、布局、字符串等资源
- 重新打包并使用内置密钥签名 APK（v1 + v2 + v3）

### 加壳检测与脱壳（v1.1 新增）
- 自动识别 360 加固、腾讯乐固、爱加密、梆梆、百度、阿里 等常见加壳厂商
- 检测到加壳后执行静态脱壳，输出可编辑的 DEX 文件
- 脱壳后的 DEX 自动进入编辑流程

### 路径配置（v1.1 新增）
- 自定义缓存文件 / 脱壳文件 / 成品文件 / 密钥库文件的存放路径
- 默认路径：`Download/dex52pj`（参考 BlackDex 的存储约定）
- 通过设置界面（PreferenceFragment）配置

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

### 4.9 替换 APK 资源（v1.1）

1. 切换到"资源"界面
2. 浏览 APK 内的资源条目（图标 / 布局 / 字符串 等）
3. 选中条目后选择本地替换文件
4. 应用会重新打包并使用内置密钥签名 APK（v1 + v2 + v3）

### 4.10 加壳检测与脱壳（v1.1）

1. 切换到"脱壳"界面
2. 选择待检测的 APK
3. 应用自动识别加壳厂商（360 / 腾讯乐固 / 爱加密 / 梆梆 / 百度 / 阿里 等）
4. 若检测到加壳，自动执行静态脱壳，输出 DEX 到自定义脱壳目录
5. 脱壳后的 DEX 可直接进入 Dex 编辑流程

### 4.11 自定义路径（v1.1）

1. 通过工具栏溢出菜单进入 **设置**
2. 分别配置缓存文件、脱壳文件、成品文件、密钥库文件的存放路径
3. 默认路径为 `Download/dex52pj`

### 4.12 关于

1. 点击工具栏右上角的溢出菜单（三个点）
2. 选择 **关于**
3. 查看软件说明、功能列表、技术栈、致谢等信息

---

## 5. 核心技术架构

### 5.1 整体架构

```
┌──────────────────────────────────────────────────────────┐
│                         UI 层                            │
│  MainActivity + 7 Fragment + Adapters                    │
│  Browse / Search / Manifest / Resource / Unshell /       │
│  Info / Settings                                          │
├──────────────────────────────────────────────────────────┤
│                         业务层                           │
│  DexLoader（单例）+ SmaliUtils + PathConfig              │
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
