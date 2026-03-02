# ComposeXPOS

ComposeXPOS 是一个基于 **Kotlin Multiplatform + Compose Multiplatform** 的多终端 POS 套件仓库，包含点餐端、收银端、叫号端三套应用与共享库。

## 当前项目状态

- 开源模式默认启用：**支付与打印为 Mock 流程**（不接真实支付网关/打印机）。
- 仓库内已移除 Firebase 相关插件、依赖与配置文件。
- 设备协同以局域网为核心：HTTP + WebSocket + NSD。

## 模块说明

- `:orderingMachine`：点餐端（Kiosk）
  - 菜单展示、购物车、下单、支付流程（当前为 Mock）
- `:cashRegister`：收银端（中枢）
  - 接收订单、菜单同步、叫号管理、局域网服务
- `:callingMachine`：叫号屏
  - 展示备餐中/可取餐队列，接收实时状态推送
- `:shared`：共享库
  - 跨模块模型、网络协议、开源安全默认配置

## 仓库结构

```text
.
├── callingMachine/
├── cashRegister/
├── orderingMachine/
├── shared/
├── iosApp/
├── docs/
├── build.gradle.kts
├── settings.gradle.kts
└── README.md
```

各应用模块的主要 SourceSet：

- `androidMain`
- `commonMain`
- `iosMain`
- `webMain`
- 部分模块包含 `jsMain` / `wasmJsMain`

## 构建与运行

### Android

```bash
./gradlew :callingMachine:assembleDebug :cashRegister:assembleDebug :orderingMachine:assembleDebug
```

### Web

开发模式：

```bash
./gradlew :callingMachine:jsBrowserDevelopmentRun
./gradlew :cashRegister:jsBrowserDevelopmentRun
./gradlew :orderingMachine:jsBrowserDevelopmentRun
```

产物构建：

```bash
./gradlew :callingMachine:jsBrowserDistribution
./gradlew :cashRegister:jsBrowserDistribution
./gradlew :orderingMachine:jsBrowserDistribution
```

### iOS Framework（Simulator）

```bash
./gradlew :callingMachine:linkDebugFrameworkIosSimulatorArm64
./gradlew :cashRegister:linkDebugFrameworkIosSimulatorArm64
./gradlew :orderingMachine:linkDebugFrameworkIosSimulatorArm64
```

### iOS Host App（Xcode）

可直接使用 `iosApp/iosApp.xcodeproj` 中共享 Scheme：

- `Calling`（`Debug-Calling`）
- `Cash`（`Debug-Cash`）
- `Ordering`（`Debug-Ordering`）

对应配置文件：

- `iosApp/Configuration/Config-Calling.xcconfig`
- `iosApp/Configuration/Config-Cash.xcconfig`
- `iosApp/Configuration/Config-Ordering.xcconfig`

## 设备间协议（当前实现）

### CashRegister 局域网 API

- `GET /health`
- `GET /dishes`
- `GET /menu`
- `POST /orders`

### OrderingMachine 配置 API

- `GET /health`
- `GET /cashregister`
- `POST /cashregister`
- Header 鉴权：`X-Posroid-Key: <POSROID_LINK_SHARED_KEY>`

### CallingMachine WebSocket

- Viewer 模式：`?mode=viewer`
- Source 模式：
  - `?mode=source&key=<CALLING_WS_SHARED_KEY>`
  - 或 `?ts=<millis>&sig=<sha256>`

默认开源占位密钥定义在：

- `shared/src/commonMain/kotlin/com/cofopt/shared/network/PosroidLinkProtocol.kt`

## 开源安全说明

- 请勿在仓库提交真实密钥、证书、商户号、生产地址。
- 生产环境请通过安全注入（如 Keystore/后端下发）替换占位配置。
- 支付/打印真实接入请参考：
  - `docs/OPEN_SOURCE_PAYMENT_PRINTING.md`

## 最小环境建议

- JDK 17+
- Android SDK（本地 `local.properties` 配置 `sdk.dir`）
- Xcode（需要构建 iOS Host 时）
