## Pushdozer

让地形编辑像推土机一样简单高效的 Fabric 模组。它提供直观的多模式地形操作、丰富的笔刷几何体、灵活的标高系统，以及服务端权威的多人同步与撤销/重做。

- Modrinth（历史页/下载）：[https://modrinth.com/mod/pushdozer](https://modrinth.com/mod/pushdozer)
- 旧版介绍/Wiki（历史文档）：[https://theopote.github.io/Pushdozer-Introduction/#/en/README.md](https://theopote.github.io/Pushdozer-Introduction/#/en/README.md)

### 功能概览

- 多种工作模式（服务端执行）
  - 挖掘/清理（EXCAVATE）
  - 批量铺设（PLACE）：自适应生物群系或指定自然方块
  - 平滑（SMOOTH + 变体）：自适应/提升/降低；兼容旧的独立模式（SMOOTH_RAISE、SMOOTH_LOWER、ADAPTIVE_SMOOTH）
  - 表面粗糙（SURFACE_ROUGHEN）
  - 表层转换（SURFACE_CONVERT）：可配置多方块百分比混铺
  - 骨粉生长（BONE_MEAL）
  - 批量种植（BATCH_PLANT）：树木/花朵/草丛/自定义列表
  - 水岸处理（SHORELINE_PROCESS）：沙滩/堤岸/泥泞/岩岸，含宽度与植被参数
- 8 种笔刷几何体
  - Sphere、Box、Octahedron、Cylinder、Cone、Ellipsoid、Tetrahedron、Triangular Prism
- 4 套标高系统
  - NO_LIMIT、FOLLOW_PLAYER、LOCKED_ONCE、CUSTOM
- 显示模式
  - 线框（Wireframe）、点云（Point Cloud）、隐藏（None）
- 撤销/重做
  - 每名玩家 30 步；带冷却与批量同步优化
- 高性能/多人优化
  - 服务端权威执行；批处理网络广播；光照与邻居批更新；大操作分块同步

### 适配与依赖

- Minecraft: 1.21.11（Fabric）
- Fabric Loader >= 0.18.2
- Fabric API
- Java 21（开发与运行均需 JDK 21）
- 可选：ModMenu（用于在模组列表中快速跳转链接）

### 安装

1. 安装 Fabric Loader 与 Fabric API
2. 将模组 JAR 放入 `.minecraft/mods`
3. 启动游戏（客户端或专用服务器）即可

### 快速上手

- 在创造模式的 `Tools` 分组中找到 `Pushdozer`，或根据配方合成
- 手持工具，使用右键触发当前工作模式
- 在控制/按键设置中为工具功能绑定热键
- 通过界面面板调整：
  - 工作模式与其子参数（如平滑强度、粗糙强度、表层转换比例、种植密度等）
  - 笔刷几何体与半径/高度
  - 标高模式（不限制、跟随、一次锁定、自定义）
  - 铺设模式（自适应生物群系 / 指定自然方块）

配置默认保存在客户端（`config/pushdozer_config.json`），更改会即时生效。

### 多人/服务器

- 架构
  - 操作在服务端线程执行，服务端为权威
  - 通过批处理网络广播到所有在线玩家（小操作即时发送；大操作分批/延迟聚合）
  - 撤销/重做为服务端执行，并向客户端同步光照/邻居更新
- 权限/限制
  - 默认所有玩家可用；内置半径上限（默认最大 100）
  - 可与区域保护/权限系统集成（需自行扩展）
- 配置
  - 配置文件默认保存在客户端；服务器不强制全局配置同步

提示：大范围操作请适当降低半径或分批执行，以兼顾服务器与客户端性能。

### 配置要点

- 文件：`config/pushdozer_config.json`
- 主要项（节选）：
  - `workMode`、`geometryType`、`radius/height/...`
  - 标高：`heightMode`（NO_LIMIT/FOLLOW_PLAYER/LOCKED_ONCE/CUSTOM）、`lockedHeight`
  - 铺设：`placeMode`（ADAPTIVE_BIOME/NATURAL_BLOCK）、`selectedNaturalBlockId`
  - 平滑/粗糙：`smoothStrength`、`smoothingIntensity`、`roughnessStrength`
  - 表层转换（多方块列表及百分比）
  - 批量种植：`plantType`、`customPlantBlockIds`、`plantDensity`
  - 水岸：`shorelineType`、`shorelineWidth`、`plantVegetationEnabled`、`vegetationDensity`

### 构建与开发

- 环境要求
  - JDK 21（Gradle Java Toolchain 会自动校验；未安装时 Gradle 可尝试自动下载）
  - 版本号以 `gradle.properties` 为准（Minecraft、Loader、Fabric API、Loom）
- 构建
  - Windows: `gradlew.bat build`
  - macOS/Linux: `./gradlew build`
- 运行（开发）
  - 客户端：`gradlew runClient`
  - 服务端：`gradlew runServer`
- 代码结构
  - 服务端入口：`com.pushdozer.PushdozerMod`
  - 客户端入口：`com.pushdozer.PushdozerClient`
  - 网络：`com.pushdozer.network.*`
  - 操作/撤销：`com.pushdozer.items.handlers.*`、`com.pushdozer.operations.*`
  - 配置与 UI：`com.pushdozer.config.*`、`com.pushdozer.ui.*`

### 许可证

- MIT License

### 参考链接（历史页面）

- Modrinth（旧版简介与版本下载）：[https://modrinth.com/mod/pushdozer](https://modrinth.com/mod/pushdozer)
- 旧版介绍/Wiki（历史文档）：[https://theopote.github.io/Pushdozer-Introduction/#/en/README.md](https://theopote.github.io/Pushdozer-Introduction/#/en/README.md)


