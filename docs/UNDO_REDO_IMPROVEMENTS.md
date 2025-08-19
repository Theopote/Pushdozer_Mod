# Pushdozer 撤销/重做功能改进

## 问题描述

在之前的版本中，Pushdozer的撤销/重做功能存在以下问题：

1. **边界方块问题**：铺设操作后，有些区域边界方块不能被正确撤销
2. **挖掘边界问题**：挖掘操作后，有些边界方块不能通过重做恢复
3. **状态不一致**：大量操作后可能出现状态不一致的情况

## 解决方案

### 1. 边界扩展机制

新增了边界扩展功能，在创建撤销操作时自动收集边界位置：

```java
// 收集边界扩展信息
BlockOperation.BoundaryExtension boundaryExtension = BlockOperation.collectBoundaryExtension(placedBlocks, world);

UndoAction undoAction = new UndoAction(
    UndoAction.ActionType.PLACE,
    placedBlocks,
    originalStates,
    newStates,
    boundaryExtension.getPositions(),
    boundaryExtension.getOriginalStates(),
    boundaryExtension.getNewStates()
);
```

### 2. 改进的UndoAction类

- 添加了边界位置和状态的存储
- 增加了数据验证功能
- 提供了获取所有位置（包括边界）的方法

### 3. 批量处理优化

- 使用分批处理避免性能问题
- 添加了错误处理和重试机制
- 改进了光照更新逻辑

### 4. 位置验证

- 添加了位置有效性检查
- 确保操作在已加载的区块内进行
- 防止越界操作

## 主要改进

### UndoRedoManager
- 添加了边界扩展处理
- 改进了批量方块设置
- 增强了错误处理
- 优化了光照更新

### BlockOperation工具类
- 提供边界扩展收集功能
- 批量方块状态设置
- 数据验证工具

### UndoAction
- 支持边界扩展信息
- 数据完整性验证
- 更好的状态管理

## 使用方法

### 创建带边界扩展的撤销操作

```java
// 1. 收集边界扩展信息
BlockOperation.BoundaryExtension boundaryExtension = 
    BlockOperation.collectBoundaryExtension(positions, world);

// 2. 创建撤销操作
UndoAction undoAction = new UndoAction(
    actionType,
    positions,
    originalStates,
    newStates,
    boundaryExtension.getPositions(),
    boundaryExtension.getOriginalStates(),
    boundaryExtension.getNewStates()
);

// 3. 推入撤销栈
PushdozerMod.pushUndoAction(player, undoAction);
```

### 批量处理方块

```java
// 使用批量处理功能
BlockOperation.batchSetBlockStates(positions, states, world, flags);
```

## 配置参数

- `BOUNDARY_EXTENSION_RADIUS`: 边界扩展半径（默认2格）
- `MAX_BATCH_SIZE`: 最大批量处理大小（默认100个方块）
- `UNDO_COOLDOWN_MS`: 撤销冷却时间（默认500毫秒）

## 性能优化

1. **分批处理**：避免一次性处理过多方块导致的性能问题
2. **延迟处理**：在批次间添加短暂延迟，避免服务器过载
3. **错误恢复**：单个方块操作失败不影响整体操作
4. **缓存优化**：减少重复的位置验证

## 兼容性

- 向后兼容：旧的UndoAction构造函数仍然可用
- 渐进式升级：可以逐步为现有处理器添加边界扩展功能
- 配置灵活：可以根据需要调整边界扩展参数

## 测试建议

1. **边界测试**：在形状边界附近进行铺设和挖掘操作
2. **大量操作测试**：测试大量方块操作的撤销/重做
3. **性能测试**：验证批量处理的性能表现
4. **错误恢复测试**：测试部分操作失败时的恢复能力
