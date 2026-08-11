# 妙记（Android 自动记账）

妙记是一款数据完全保存在手机本地的 Android 记账 App。它可以监听用户主动授权的支付通知，从微信支付、支付宝及常见银行交易提醒中提取金额、商户、收支类型和分类，也支持手动补记与 CSV 导出。

## 已实现

- 本月支出、收入、结余与分类占比
- 账单明细、分类筛选、长按删除
- 手动记录收入和支出
- Android 通知访问授权引导
- 支付通知本地解析、去重和自动分类
- CSV 文件导出（UTF-8 BOM，可直接用 Excel 打开）
- 账本不做云备份；无网络权限

## 构建与运行

1. 安装 Android Studio，确保 SDK 35 和 JDK 11 可用。
2. 用 Android Studio 打开本目录，等待 Gradle 同步完成。
3. 连接 Android 8.0 或更高版本的手机，运行 `app`。
4. 首次进入后点击首页的“去开启”，在系统设置中授权“妙记自动记账”的通知访问权限。

命令行构建需要 Gradle 7.5.1；执行 `gradle assembleDebug` 后，APK 位于 `app/build/outputs/apk/debug/app-debug.apk`。

## 无需本机 SDK 的云端构建

项目已包含 `.github/workflows/build-apk.yml`。把项目上传到 GitHub 后：

1. 打开仓库的 **Actions** 页面。
2. 选择 **Build Android APK**，点击 **Run workflow**。
3. 构建完成后，在运行页面底部下载 `miaoji-debug-apk`。
4. 解压后得到可安装的 `app-debug.apk`。

该流程使用 GitHub 托管的 Android 环境，不需要在当前电脑安装 Android Studio 或 Android SDK。

## 自动记账边界

自动记账只处理授权之后新出现的通知，无法补录历史账单。支付应用升级或通知文案变化时，可能需要补充解析规则；不确定的通知会被忽略，以减少误记。普通聊天通知不会入账。

## 隐私设计

项目未声明网络权限。通知内容在本机内存中解析，只把识别后的账单字段写入本地 SQLite 数据库；无关通知不落盘。数据库明确排除在 Android 云备份之外，但换机直连迁移时允许随设备数据转移。
