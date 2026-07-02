# Traveler Addon

Forge 1.20.1 的 MineColonies 附属模组。

## 功能概览

- 围绕市政厅刷新旅行者
- 白天 / 夜晚阶段刷新，旧旅行者会被替换或到时离场
- 右键直接打开独立交易界面
- 支持两类交易：
  - 满足当前殖民地需求
  - 自由市场购买单个物品
- 交易物资自动送入殖民地仓库
- 对 Domum Ornamentum 相关物品做了安全处理，尽量避免异常 NBT 导致的崩溃

## 当前规则摘要

- 需求补货价格：`35` 紫水晶碎片
- 自由市场价格：按代码内分层规则计算
- 购买次数、需求购买次数会跟随市政厅等级变化

## 构建依赖

本项目需要仓库根目录 `deps/` 下存在：

- `minecolonies-1.20.1-1.1.1197.jar`
- `structurize-1.20.1-1.0.804.jar`
- `domum_ornamentum-1.20.1-1.0.296-universal.jar`

## 构建

```powershell
gradle build
```

产物默认在：

`build/libs/`

## 说明

- 当前源码以 MineColonies `1.20.1-1.1.1197` 为开发基础。
- 如果你后面要兼容其他 1.20.1 小版本，建议在单独分支里做 API 对齐和回归测试。
- 当前导出包没有附带完整 Gradle wrapper jar，默认按本地 Gradle 8.8 使用。
