# 虚拟定位测试 App

一个用于**技术测试**的虚拟定位（Mock Location）安卓工具。在地图上选点或输入经纬度，一键把坐标注入系统 GPS，高德地图 / M3 等读取系统定位的 App 即可读到虚拟坐标。

- **技术原理**：Android 官方模拟位置 API（`LocationManager.addTestProvider` + `setTestProviderLocation`）。系统内置开发者功能，不需要 Root。
- **地图选点**：使用 OpenStreetMap（OSMDroid），**无需任何 API Key**，开箱即用。
- **边界**：本工具**不包含任何绕过模拟位置检测的功能**，仅用于你自己的技术验证。

---

## 一、使用前准备（真机）

1. 手机开启「开发者选项」：设置 → 关于手机 → 连续点击「版本号」7 次。
2. 开发者选项 → **模拟位置信息应用** → 选择本应用「虚拟定位测试」。
3. 授予本应用定位权限（首次打开会弹窗请求）。

> 若未把本应用设为模拟位置应用，点击「开启虚拟定位」会提示并自动跳转开发者选项。

---

## 二、获取安装包（无需本机安卓环境）

本工程已配置 **GitHub Actions 云端构建**，你不需要在电脑上安装 Android Studio。

1. 在 GitHub 新建一个**空**仓库（如 `mock-location-app`）。
2. 把本工程所有文件推送到该仓库：
   ```bash
   git init
   git add .
   git commit -m "init"
   git branch -M main
   git remote add origin https://github.com/<你的用户名>/<仓库名>.git
   git push -u origin main
   ```
3. 打开仓库的 **Actions** 页面，等待 `Build Debug APK` 任务完成（约 3–6 分钟）。
4. 任务完成后，在右侧 **Artifacts** 下载 `app-debug-apk`，得到 `app-debug.apk`。
5. 把 APK 传到手机安装（需允许「未知来源」安装）。

> 说明：CI 每次 `push` 或手动 `workflow_dispatch` 都会重新构建。debug 签名由构建自动生成，可直接安装测试。

---

## 三、本地构建（可选）

如果你本机有 Android Studio / Android SDK + Gradle 8.7：

```bash
gradle assembleDebug        # 或 ./gradlew assembleDebug（Android Studio 会自动生成 wrapper）
```

产物位于 `app/build/outputs/apk/debug/app-debug.apk`。

---

## 四、使用步骤

1. 打开 App，在地图上**单击**选点，或在顶部输入框输入 `纬度,经度`（如 `39.9042,116.4074`）后点「定位」。
2. 点「**开启虚拟定位**」：状态栏出现常驻通知，表示正在注入。
3. 打开高德地图 / M3，查看其定位是否跳到虚拟点。
4. 点「**停止虚拟定位**」恢复真实定位。

---

## 五、已知限制

- 模拟位置仅在本应用被设为「模拟位置信息应用」后生效（系统白名单机制）。
- 坐标的**注入与传播**是可靠的；但**主动校验** `Location.isFromMockProvider()` 的 App（多为考勤 / 打卡类）会识别并拦截相应操作。本工具不提供绕过该检测的能力。
- 高德地图 App 的「我的位置」蓝点通常能显示虚拟坐标（部分版本会提示“疑似模拟”）。

---

## 六、可选：替换为高德地图

若你日后申请到高德 Key，可将 `OSMDroid` 地图模块替换为高德 Android SDK（`com.amap.api:3dmap` + `com.amap.api:search`），在 `AndroidManifest.xml` 加入 `com.amap.api.v2.apikey` meta-data。模拟位置的核心逻辑（`MockLocationProvider` / `MockLocationService`）无需改动。
