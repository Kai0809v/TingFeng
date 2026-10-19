# 待办：服务保活（前台静默常驻 + 兜底重连）

> 2026-10-19 记录。背景：真机（ColorOS）上通知使用权有时被系统收回，需反复跳设置重新授权。

## 需求结论（已论证）

- **前台静默常驻通知**：把进程优先级提到 Perceptible，防 LMK 低内存回收、防后台速冻冻结；**防不了划卡清理/force-stop**（ColorOS 划卡 = force-stop，前台服务照杀、通知使用权照撤，无解，只能靠系统白名单引导缓解）
- **被杀自动重启**：普通后台被杀（LMK/省电清理）系统本来就支持自动 rebind NLS，前台服务让重连更快更可靠；**force-stop 后无法自动重启**（Android 硬性规则：广播/Alarm/Job/rebind 全失效，唯一拉起方式是用户手动打开应用）

## 最小改动集

1. **恢复 startForeground**（Ting.kt 中被注释的 `startForegroundService` 段，236-261 行附近）：
   - 时机：`onListenerConnected()` 里调用（进程已被系统绑定提升，无需 startForegroundService）
   - 通知形态：`setOngoing(true) + setSilent(true) + IMPORTANCE_MIN`（不发声、不弹横幅、折叠在通知栏底部）
   - 与服务暂停状态联动：`TingConfig.servicePaused` 时 `stopForeground`（暂停时不需要常驻）
2. **Manifest**：service 加 `android:foregroundServiceType="specialUse"`，声明 `FOREGROUND_SERVICE_SPECIAL_USE` 权限（Android 14+ 要求）
3. **onTaskRemoved 钩子**：任务被移除时异步 `requestRebind` + `toggleNotificationListenerService`（组件 disable→enable 强制重绑，机制已有）
4. **AlarmManager 兜底巡检**（可选）：定时检查 listener 连接状态，断开则 rebind（仅进程存活时有效，作为前台服务的补充）

## 相关现状

- `Ting.kt`：已有 `bindNotificationService`（requestRebind）、`toggleNotificationListenerService`（组件切换强制重连）；onStartCommand 返回 START_STICKY（对 NLS 无实际作用，NLS 重连靠系统 rebind 机制）
- 已有：服务监控开关检测未授权 → 跳转授权页；权限撤销无法拦截，检测+引导是正确做法
- 用户引导文案可加到"使用提示"对话框：自启动白名单、电池不优化、关闭应用速冻、锁定后台卡片

## 验证要点

- 常驻通知出现后：划卡清理 → 进程被杀（预期内）；重新打开应用 → 服务自动重连
- 后台挂机（不划卡）→ 进程不被杀、通知记录持续
- `adb shell am force-stop` 后服务与权限状态
