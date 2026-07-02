# MineColonies Addons Source Pack

这个文件夹是给 GitHub 上传准备的源码导出包，包含两个 Forge 1.20.1 模组项目：

- `traveleraddon`
  - 旅行者交易模组
  - 核心功能：旅行者刷新、独立交易 GUI、满足殖民地当前需求、自由市场购买、仓库自动入库、DO 物品安全处理
- `auto_recipes`
  - MineColonies 自动学配方补丁
  - 核心功能：普通工坊在指定等级自动学兼容配方；DO 自定义配方分批导入并持久化恢复

## 目录结构

```text
minecolonies-addons-github/
├─ traveleraddon/
├─ auto_recipes/
└─ deps/
```

## 上传前你需要知道

- 这个导出包不包含第三方依赖 jar。
- `deps/` 目录只保留说明文档，避免把 MineColonies、Structurize、Domum Ornamentum 这些第三方文件直接传上 GitHub。
- 两个项目的 `build.gradle` 都已经改成从仓库根目录下的 `deps/` 读取依赖。

## 本地构建前需要手动放入 `deps/`

按当前源码环境，至少需要这 3 个文件：

- `minecolonies-1.20.1-1.1.1197.jar`
- `structurize-1.20.1-1.0.804.jar`
- `domum_ornamentum-1.20.1-1.0.296-universal.jar`

如果你后面要适配别的 MineColonies 1.20.1 版本，可以继续改源码和依赖文件名，但当前导出包默认还是基于这套版本编译。

## 构建方法

默认建议用本地 Gradle 8.8，在各自项目目录执行：

```powershell
gradle build
```

当前导出包没有附带完整的 Gradle wrapper jar，所以不要默认指望 `gradlew` 开箱即用。

## 建议上传内容

建议直接把整个 `minecolonies-addons-github` 作为一个仓库上传，这样两个项目和依赖说明集中管理，后续维护更简单。

## 许可证说明

当前导出包保留了项目里原本的 `All Rights Reserved` 配置，没有擅自替你改成 MIT / GPL。

如果你准备真正开源：

1. 自己确定许可证。
2. 替换仓库根目录的许可证文件。
3. 同步修改两个项目 `gradle.properties` 里的 `mod_license`。
