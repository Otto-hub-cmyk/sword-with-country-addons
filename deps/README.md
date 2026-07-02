# Local Dependencies

这个目录用于放本地编译需要的第三方依赖 jar，默认不要提交到 GitHub。

当前两个项目按下面这些文件名读取依赖：

- `minecolonies-1.20.1-1.1.1197.jar`
- `structurize-1.20.1-1.0.804.jar`
- `domum_ornamentum-1.20.1-1.0.296-universal.jar`

说明：

- 这些 jar 只用于本地编译和反混淆开发。
- 不建议把第三方模组 jar 直接塞进公开仓库。
- 如果你后续升级依赖版本，记得同步修改两个项目各自的 `build.gradle`。
