# 配置说明

## 配置文件位置

配置文件位于 `.minecraft/config/pushdozer.json`

## 基础配置项

### 工作模式配置
| 配置项 | 说明 | 可选值 | 默认值 |
|-------|------|--------|--------|
| workMode | 工作模式 | "DESTROY"/"PLACE"/"SMOOTH" | "DESTROY" |
| displayMode | 显示模式 | "WIREFRAME"/"SURFACE"/"NONE" | "WIREFRAME" |

### 形状配置
| 配置项 | 说明 | 可选值 | 默认值 |
|-------|------|--------|--------|
| shape | 形状类型 | "Box"/"Sphere" | "Box" |
| radius | 球形半径 | 1-32 | 5 |
| length | 长方体长度 | 1-32 | 5 |
| width | 长方体宽度 | 1-32 | 5 |
| height | 长方体高度 | 1-32 | 5 |

### 操作配置
| 配置项 | 说明 | 可选值 | 默认值 |
|-------|------|--------|--------|
| maxUndoSteps | 最大撤销步数 | 1-30 | 30 |

## 高级配置

### 方块过滤
```json
{
  "breakableBlockIds": [
    "minecraft:stone",
    "minecraft:dirt",
    "minecraft:grass_block"
  ],
  "ignoredBlockIds": [
    "minecraft:water",
    "minecraft:lava",
    "minecraft:air"
  ]
}
```

### 快捷键配置
```json
{
  "keyBindings": {
    "openConfig": "K",
    "toggleDisplay": "V",
    "changeMode": "G",
    "changeShape": "U",
    "undo": "CTRL+Z",
    "redo": "CTRL+Y",
    "increaseRange": "UP",
    "decreaseRange": "DOWN"
  }
}
```

## 配置示例

### 完整配置文件示例
```json
{
  "workMode": "DESTROY",
  "displayMode": "WIREFRAME",
  "maxOperationDistance": 30,
  "shape": "Box",
  "radius": 5,
  "length": 5,
  "width": 5,
  "height": 5,
  "maxUndoSteps": 30,
  "breakableBlockIds": ["minecraft:stone", "minecraft:dirt"],
  "ignoredBlockIds": ["minecraft:water", "minecraft:air"],
}
```

## 注意事项

1. 修改配置文件时：
   - 确保JSON格式正确
   - 保存后需要重启游戏
   - 建议通过游戏内界面修改

2. 性能优化建议：
   - 适当降低几何体范围半径
   - 合理设置撤销步数

3. 常见问题：
   - 配置文件损坏会自动重置为默认值
   - 非法值会被自动纠正
   - 可以删除配置文件重新生成