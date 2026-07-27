<!-- pico-cli:plugin-context:pico-spatial-agentic-tools:start -->
## Plugin Context

Also read `./PICO-SPATIAL-AGENTIC-TOOLS.AGENTS.md` for PICO Spatial plugin guidance.
<!-- pico-cli:plugin-context:pico-spatial-agentic-tools:end -->

## Drawer Spatial Onboarding

- 项目名：`Drawer`
- 包名：`com.example.myspatial`
- 当前形态：PICO Spatial SDK `Planar` 默认窗口，入口使用 `DefaultWindowContainer { PicoTheme { ... } }`
- 关键入口：
  - `app/src/main/java/com/example/myspatial/app/DrawerApplication.kt`：`Application` 启动并调用 `launch(::mainApp)`
  - `app/src/main/java/com/example/myspatial/app/LaunchActivity.kt`：继承 `SpatialLaunchActivity`
  - `app/src/main/java/com/example/myspatial/Main.kt`：声明默认 Planar 窗口
  - `app/src/main/java/com/example/myspatial/ui/DrawerHomeScreen.kt`：首页 SpatialUI 内容
- UI 规则：仅允许 SpatialUI + `PicoTheme`，禁止引入 Material / Material3
- 构建方式：在项目根目录执行 `./gradlew assembleDebug`
- 运行目标：优先 PICO Emulator；若模拟器链路不可用，先保证 Gradle 构建通过，再排查环境
- 当前验证状态：
  - `./gradlew assembleDebug --no-daemon` 已通过
  - `./gradlew installDebug --no-daemon` 已安装到在线设备 `B3110 (swan)`
  - `adb shell am start -n com.example.myspatial/.app.LaunchActivity` 已成功拉起，`LaunchActivity` 处于前台
- PICO Connect:
  - 本机已安装 `PICO Connect TMP.app`
  - 配置文件 `/Users/bytedance/Library/Application Support/PICO Connect TMP/settings.json` 中 `general.autoConnect = true`
  - 日志中可见 `ReconnectToServer` 与 `auto_link:1`，说明自动重连链路已启用
- 快速部署：
  - 运行 `./scripts/deploy_drawer.sh`
  - 该脚本会尝试拉起 `PICO Connect TMP`，等待 `adb` 设备在线，随后安装并启动 `Drawer`
