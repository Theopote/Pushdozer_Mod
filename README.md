# Pushdozer

让地形编辑像推土机一样简单高效的 Fabric 模组。它提供直观的多模式地形操作、丰富的笔刷几何体、灵活的标高系统，以及服务端权威的多人同步与撤销/重做。

[![Build](https://github.com/Theopote/pushdozer/actions/workflows/build.yml/badge.svg)](https://github.com/Theopote/pushdozer/actions/workflows/build.yml)

## 链接

| | |
|---|---|
| 源码 | [github.com/Theopote/pushdozer](https://github.com/Theopote/pushdozer) |
| 文档 | [Pushdozer-Introduction](https://theopote.github.io/Pushdozer-Introduction/) |
| 下载 | [Modrinth](https://modrinth.com/mod/pushdozer) |
| 社区 | [Discord](https://discord.gg/jjr8WmPZ) |

## 功能概览

- **8 种工作模式**（服务端执行）：挖掘、铺设、平滑、表面粗糙、表层转换、骨粉、批量种植、水岸处理
- **8 种笔刷几何体**：球体、长方体、正八面体、圆柱、圆锥、椭球体、正四面体、三棱柱
- **4 套标高系统**：不限制、跟随玩家、锁定标高、自定义标高
- **4 种预览模式**：线框、点云、表面（半透明面）、不显示
- **撤销/重做**：每名玩家 30 步，带冷却与批量同步优化
- **多人联机**：服务端权威执行，批处理网络广播

完整功能说明见 [在线文档](https://theopote.github.io/Pushdozer-Introduction/)。

## 适配与依赖

| 组件 | 版本 |
|------|------|
| Minecraft | 1.21.11 |
| Fabric Loader | ≥ 0.18.2 |
| Fabric API | 见 `gradle.properties` |
| Java | 21 |
| 可选 | ModMenu |

## 安装

1. 安装 [Fabric Loader](https://fabricmc.net/use/) 与 Fabric API
2. 从 [Modrinth](https://modrinth.com/mod/pushdozer) 或 [GitHub Releases](https://github.com/Theopote/pushdozer/releases) 下载 JAR
3. 放入 `.minecraft/mods`，启动游戏

## 快速上手

1. 创造模式 → `Tools` 分组 → 找到 **Pushdozer**
2. 按 `K` 打开配置界面，选择工作模式与笔刷
3. **右键**目标位置执行操作
4. 按 `V` 切换预览模式（线框 → 点云 → 表面 → 不显示）

常用快捷键：`G` 切换工作模式 · `U` 切换几何体 · `Ctrl+Z/Y` 撤销/重做 · `↑/↓` 操作距离

## 构建与开发

```bash
# 构建
./gradlew build          # Windows: gradlew.bat build

# 开发运行
./gradlew runClient
./gradlew runServer
```

环境要求 JDK 21。贡献指南见 [CONTRIBUTING.md](CONTRIBUTING.md)。

### 代码结构

| 包 | 说明 |
|----|------|
| `PushdozerMod` / `PushdozerClient` | 服务端/客户端入口 |
| `items.handlers.*` | 各工作模式处理器 |
| `operations.*` | 撤销/重做、批量方块操作 |
| `render.*` | 笔刷预览渲染 |
| `config.*` / `ui.*` | 配置与界面 |
| `network.*` | 多人同步 |

## 许可证

[MIT License](LICENSE) · 详见 [NOTICE](NOTICE) 中的第三方归属说明
