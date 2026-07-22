# Dex 编辑器（Dex Editor）

> by.吾爱破解52pojie.cn 空满水杯

一款基于 jadx 与 smali 3.0.9 的 Android 端 DEX/APK 编辑器，支持反汇编、反编译、编辑与重新编译。

---

## 1. 软件介绍

Dex 编辑器是一款运行于 Android 设备上的 DEX/APK 文件查看与编辑工具。它整合了业界主流的反编译/反汇编引擎，让用户无需 PC 即可在手机上完成对 DEX 文件中类的浏览、Smali 代码查看、Java 源码反编译、Smali 代码在线编辑与重新编译等操作。

本软件面向 Android 逆向爱好者与开发者，特别感谢 **吾爱破解（52pojie.cn）** 社区的支持。

### 应用截图说明

- **浏览界面**：以包层级树形结构展示 DEX 中的所有类，包名后显示子类数量，支持展开/折叠
- **搜索界面**：按类名 / 方法名 / 字符串关键字快速检索
- **信息界面**：显示文件名、大小、类/方法/字段/字符串/包数量等统计信息
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

### 其他特性
- 异步加载：文件加载、反编译、反汇编均在后台线程执行，不阻塞 UI
- 错误处理：加载失败、反编译失败均有友好提示
- 稳定性优化：修复了 TextWatcher 无限递归导致的 StackOverflowError

---

## 3. 开源地址

**GitHub 仓库**：[https://github.com/liewec/jadx](https://github.com/liewec/jadx)

**APK 下载**：[releases/dex-editor-debug.apk](releases/dex-editor-debug.apk)

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

### 4.8 关于

1. 点击工具栏右上角的溢出菜单（三个点）
2. 选择 **关于**
3. 查看软件说明、功能列表、技术栈、致谢等信息

---

## 5. 核心技术架构

### 5.1 整体架构

```
┌─────────────────────────────────────────────┐
│                  UI 层                       │
│  MainActivity + 4 Fragment + Adapters       │
├─────────────────────────────────────────────┤
│                业务层                        │
│  DexLoader（单例）+ SmaliUtils              │
├─────────────────────────────────────────────┤
│              核心引擎层                      │
│  jadx-core（反编译）+ smali（反汇编/编译）   │
└─────────────────────────────────────────────┘
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
| jadx-core | 1.5.6 | Smali → Java 反编译 | [skylot/jadx](https://github.com/skylot/jadx) |
| jadx-dex-input | 1.5.6 | DEX 文件输入插件 | [skylot/jadx](https://github.com/skylot/jadx) |
| jadx-smali-input | 1.5.6 | Smali 文件输入插件 | [skylot/jadx](https://github.com/skylot/jadx) |
| jadx-java-convert | 1.5.6 | Java 转换支持 | [skylot/jadx](https://github.com/skylot/jadx) |
| smali | 3.0.9 | Smali 反汇编/编译 | [JesusFreke/smali](https://github.com/JesusFreke/smali) |
| smali-baksmali | 3.0.9 | baksmali 反汇编引擎 | [JesusFreke/smali](https://github.com/JesusFreke/smali) |

### Android UI 框架

| 组件 | 版本 |
|------|------|
| androidx.appcompat:appcompat | 1.6.1 |
| androidx.activity:activity-ktx | 1.9.0 |
| androidx.fragment:fragment-ktx | 1.7.1 |
| androidx.recyclerview:recyclerview | 1.3.2 |
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

### v1.0（2026-07-22）

#### 初始版本
- **2026-07-21**：初始上传，按旧版架构完整重建，移除手机端调试输出
  - DexLoader 单例模式，内部同步构建 ClassNode 树
  - ClassTreeAdapter 包可展开/折叠，包名后显示 `(N)` 子类数量
  - BrowseFragment 同步 setRoot、setLoading 进度条控制
  - SmaliFragment ToggleButton 切换 Smali/Java 视图，异步反编译/反汇编
  - SearchFragment Spinner + Button 触发搜索，ExecutorService 异步搜索
  - InfoFragment 单 TextView 显示所有统计信息

#### Bug 修复
- **2026-07-22**：修复信息页 `%1$d` 乱码 + 添加 `by.吾爱破解52pojie.cn` 小字 + 关于对话框
  - 重新编译 APK（之前 GitHub 上的 APK 是用旧版 strings.xml 编译的）
  - toolbar 添加副标题
  - 新增"关于"菜单项与 AlertDialog

- **2026-07-22**：彻底修复信息页乱码 + 关于页换行 + 标题副标题 + Java 反编译
  - 删除 `values-zh-rCN/strings.xml`（含 `%1$s`/`%1$d` 占位符，覆盖默认值导致中文系统乱码）
  - 关于页面改用 `\n` 显式换行（CDATA 换行在 Android 中不生效），作者改为"空满水杯"
  - 标题副标题改为 `by.吾爱破解52pojie.cn 空满水杯`，字体设为 10sp
  - 修复 Java 反编译 bug：添加 `jadx-dex-input`/`jadx-smali-input`/`jadx-java-convert` 依赖
  - 改进 `decompileToJava`：遍历 `getClasses()` + 内部类，返回具体错误信息

- **2026-07-22**：副标题颜色微调 + 文件选择器支持取消退出
  - 副标题字体颜色改为 `#E0E0E0`（白色微灰）

- **2026-07-22**：还原文件选择器为 GetContent

#### 功能优化
- **2026-07-22**：应用图标改为 DEX 字母
- **2026-07-22**：图标布局改为 `> DEX <`，DEX 字母瘦小居中
- **2026-07-22**：图标两侧 `<` `>` 改为实心三角形，聚焦显示 DEX
- **2026-07-22**：图标左右三角形位置调换为 `▶ DEX ◀`

---

## 致谢

- [jadx](https://github.com/skylot/jadx) - 强大的 Dex 到 Java 反编译器
- [smali](https://github.com/JesusFreke/smali) - Smali 反汇编/编译工具
- [吾爱破解](https://www.52pojie.cn/) - 技术交流社区

## 作者

**空满水杯** · by.吾爱破解52pojie.cn

## License

本项目仅供学习交流使用，相关引擎遵循其原始 License：
- jadx: Apache License 2.0
- smali: MIT License
