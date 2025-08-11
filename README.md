# Pushdozer Mod

Pushdozer 是一个功能强大的 Minecraft 地形编辑模组，它提供了直观的界面和高效的工具，让玩家能够轻松地进行大规模地形修改和建筑工作。

## ✨ 主要功能

### 🛠️ 工作模式
- **破坏模式**
  - 快速清除大范围区域
  - 智能过滤系统，可选择性地保留特定方块
  - 支持长方体和球形两种破坏形状

- **铺设模式**
  - 快速放置大量方块
  - 自动获取生物群落地形
  - 智能填充算法

- **平滑模式**
  - 自动平滑地形起伏
  - 保留地形特征的智能算法
  - 适用于自然地形修整

### 🎯 形状系统
- **长方体模式**
  - 可自定义长度（1-32格）
  - 可自定义宽度（1-32格）
  - 可自定义高度（1-32格）

- **球形模式**
  - 可调节半径（1-32格）
  - 完美球形算法

### 🎨 显示系统
- **线框模式**：以线框方式预览操作范围
- **表面模式**：清晰显示操作范围边界
- **无显示模式**：适用于无干扰操作

### 📏 标高控制系统
- **自由标高模式**
  - 无高度限制
  - 适合复杂地形修改

- **锁定标高模式**
  - 精确控制操作高度
  - 适合创建平整地形
  - 支持Y坐标锁定

### 🔄 撤销/重做系统
- 支持最多30步撤销操作
- 实时保存操作历史
- 支持重做已撤销的操作

## 🚀 性能优化

- 多线程处理大规模操作
- 智能区块加载机制
- 内存使用优化
- 支持撤销/重做的高效缓存系统
- 异步渲染预览
- 智能方块更新

## ⚙️ 系统要求

- **Minecraft**: 1.21.1
- **Fabric Loader**: >=0.15.0
- **Fabric API**: 必需
- **Java**: 17或更高版本
- **推荐配置**:
  - CPU: 4核心及以上
  - 内存: 4GB及以上

## 📥 安装步骤

1. 安装 [Fabric 模组加载器](https://fabricmc.net/use/) (版本 >= 0.15.0)
2. 下载并安装 [Fabric API](https://www.curseforge.com/minecraft/mc-mods/fabric-api)
3. 从[发布页面](https://github.com/yourusername/pushdozer/releases)下载最新版本的 Pushdozer
4. 将下载的 JAR 文件放入 `.minecraft/mods` 文件夹
5. 启动 Minecraft 并享受 Pushdozer！

### 兼容性说明
- ✅ 完全兼容 Fabric API
- ✅ 支持大多数地形生成模组
- ✅ 支持多人游戏
- ❌ 不支持 Forge 加载器

## 🎮 快捷操作

### 键盘快捷键
| 快捷键         | 功能描述 |
|-------------|----------|
| `K`         | 打开配置界面 |
| `V`         | 切换显示模式 |
| `G`         | 切换工作模式 |
| `U`         | 切换笔刷形状 |
| `Ctrl+Z`    | 撤销操作 |
| `Ctrl+Y`    | 重做操作 |
| `↑`         | 增加操作范围 |
| `↓`         | 减少操作范围 |

### 鼠标操作
- **左键**: 执行单个方块破坏操作
- **右键**: 执行pushdozer主要操作
- **手持Pushdozer+K键: 打开快速设置菜单

### 操作限制
- 撤销步数上限：30步
- 操作范围上限：32格
- 需要手持Pushdozer工具才能使用快捷键

## 🔧 配置文件

配置文件位置：`.minecraft/config/pushdozer_config.json`

```json
{
  "workMode": "DESTROY",
  "displayMode": "WIREFRAME",
  "maxOperationDistance": 30,
  "breakableBlockIds": [],
  "ignoredBlockIds": [
    "minecraft:grass",
    "minecraft:fern",
    "minecraft:dead_bush",
    "minecraft:sapling",
    "minecraft:seagrass",
    "minecraft:tall_seagrass",
    "minecraft:tall_grass"
  ],
  "placeableBlockId": "minecraft:stone",
  "shape": "Box",
  "radius": 5,
  "length": 5,
  "width": 5,
  "height": 5,
  "placeableBlockIds": ["minecraft:stone", "minecraft:dirt"],
  "heightLocked": false,
  "lockedHeight": 0
}
```

### 配置项说明

#### 基础设置
- `workMode`: 工作模式
  - `"DESTROY"`: 破坏模式
  - `"PLACE"`: 铺设模式
  - `"SMOOTH"`: 平滑模式

- `displayMode`: 显示模式
  - `"NONE"`: 不显示预览
  - `"WIREFRAME"`: 线框预览
  - `"SURFACE"`: 表面预览

- `maxOperationDistance`: 最大操作距离 (1-99)

#### 形状设置
- `shape`: 笔刷形状
  - `"Box"`: 长方体
  - `"Sphere"`: 球形

- `length`: 长方体长度 (1-32)
- `width`: 长方体宽度 (1-32)
- `height`: 长方体高度 (1-32)
- `radius`: 球形半径 (1-32)

#### 高度控制
- `heightLocked`: 是否锁定高度
  - `true`: 锁定
  - `false`: 不锁定
- `lockedHeight`: 锁定的高度值 (Y坐标)

#### 方块过滤
- `breakableBlockIds`: 可破坏的方块ID列表
- `ignoredBlockIds`: 忽略的方块ID列表
- `placeableBlockId`: 默认放置的方块ID
- `placeableBlockIds`: 可放置的方块ID列表

### 默认配置
- 工作模式: `DESTROY`
- 显示模式: `WIREFRAME`
- 最大操作距离: `20`
- 默认形状: `Box`
- 默认尺寸: 长度=5, 宽度=5, 高度=5, 半径=5
- 默认放置方块: `minecraft:stone`
- 默认可放置方块: `minecraft:stone`, `minecraft:dirt`
- 默认忽略方块:
  - `minecraft:grass`
  - `minecraft:fern`
  - `minecraft:dead_bush`
  - `minecraft:sapling`
  - `minecraft:seagrass`
  - `minecraft:tall_seagrass`
  - `minecraft:tall_grass`

### 注意事项
1. 配置文件修改后会自动保存
2. 部分设置可以通过游戏内界面修改
3. 修改配置文件后需要重启游戏生效
4. 建议使用游戏内配置界面进行修改，而不是直接编辑配置文件

## 🐛 问题反馈

如果您遇到任何问题或有改进建议：
1. 检查[常见问题解答](https://github.com/yourusername/pushdozer/wiki/FAQ)
2. 在 [GitHub Issues](https://github.com/yourusername/pushdozer/issues) 提交问题
3. 加入我们的 [Discord 社区](https://discord.gg/yourdiscord)获取帮助

## 📝 开发文档

详细的开发文档请访问我们的 [Wiki](https://github.com/yourusername/pushdozer/wiki)

## 📜 许可证

本项目采用 [CC0 1.0 通用许可证](LICENSE)。

## 📞 联系方式

- **GitHub Issues**: [提交 issue](https://github.com/Theopote/pushdozer-1.21.1-1.0.0-fabric/issues)
- **电子邮件**: hillpote@gmail.com
- **Discord**: [Pushdozer Discord](https://discord.gg/yourdiscord)
