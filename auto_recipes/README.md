# Auto Recipes Patch

Forge 1.20.1 的 MineColonies 自动学配方补丁。

## 功能概览

- 普通工坊到指定建筑等级后，自动学习兼容的普通配方
- 对 DO 自定义配方做自动导入
- DO 配方采用分批导入，减少一次性卡顿
- 已导入的 DO 输入组合会持久化，重进世界后恢复，不再从零重复扫一遍
- 通过 config 调整自动解锁等级，默认是 2 级

## 当前重点

- 当前源码重点解决的是“减少手动教学”和“让自定义配方自动落地”
- DO 配方系统本身非常复杂，极少数特殊物品仍可能需要继续观察和补充

## 配置

默认配置项：

- `unlockLevel=2`

## 构建依赖

本项目需要仓库根目录 `deps/` 下存在：

- `minecolonies-1.20.1-1.1.1197.jar`
- `structurize-1.20.1-1.0.804.jar`
- `domum_ornamentum-1.20.1-1.0.296-universal.jar`

## 构建

```powershell
gradle build
```

## 说明

- 当前源码以 MineColonies `1.20.1-1.1.1197` 为开发基础。
- 这个项目包含 mixin，排查兼容性问题时要优先检查目标类版本是否一致。
- 当前导出包没有附带完整 Gradle wrapper jar，默认按本地 Gradle 8.8 使用。
