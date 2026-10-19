# TODO：液态玻璃效果实施规划

> 基于 Backdrop 库（Kyant0/AndroidLiquidGlass）的调研结论制定，作为独立个性化设置落地。
> 调研时间：2026-10-14。实施前先重读本文件与「参考」一节。

## 0. 目标与范围

- 为**悬浮工具栏（Dock）**实现真实液态玻璃效果（背景折射 + 高光 + 色差），作为独立个性化开关。
- 主页卡片暂不实施（见阶段二的前置条件）。

## 1. 硬约束（实施前必读）

| 约束 | 说明 | 对策 |
|---|---|---|
| 纯 Compose 库 | Backdrop 只有 Compose API，无 View 体系支持 | Dock 已是 Compose ✔；卡片是 View 体系 ✗ |
| API 版本 | 效果基于 RenderEffect，**仅 API 31+**；lens 折射/色差需 **API 33+**（RuntimeShader） | `SDK_INT >= 31` 才启用，低版本回退现有纯色 Dock；lens 单独按 33+ 判断 |
| 采样范围 | backdrop 只能看到**同一 Compose 树内**的内容；系统壁纸在 window 层采样不到 | 玻璃组件的 backdrop 必须先绘制壁纸/底色再 `drawContent()` |
| 循环捕获 | 玻璃上叠玻璃（如 Dock 上再有玻璃徽标）用 `layerBackdrop` 会形成绘制循环 → RenderThread SIGSEGV 崩溃 | 内层玻璃的宿主改用 `drawBackdrop(exportedBackdrop = ...)` |

## 2. 阶段一：Dock 液态玻璃（本期范围）

### 2.1 引入依赖

- [ ] `app/build.gradle` 添加 `implementation("io.github.kyant0:backdrop:<latest>")`（Maven Central，注意联网环境；版本查 https://github.com/Kyant0/AndroidLiquidGlass/releases）
- [ ] 确认 Compose 编译器与库要求的 Kotlin/Compose BOM 版本兼容（编译前查版本兼容链，避免工具链不匹配）

### 2.2 结构改造（Activity 层）

- [ ] `activity_main.xml`：将 `main_pager`（ViewPager2）包裹进一个 `ComposeView` 容器，形成 Compose `Box` 结构：
  ```
  ComposeView(root)
    └─ Box
        ├─ val backdrop = rememberLayerBackdrop { drawRect(壁纸底色或壁纸图); drawContent() }
        ├─ AndroidView(ViewPager2) with Modifier.layerBackdrop(backdrop)
        └─ FloatingDock（现有 ComposeView 内容） with Modifier.drawBackdrop(backdrop, ...)
  ```
- [ ] 保持现有越界绘制能力：主页顶栏负 margin 越界依赖父链 `clipChildren=false`（HomeFragment.setupTopBarImmersion + pager.post 兜底）。AndroidView 包裹后验证顶栏仍正确延伸进状态栏（横屏也要测，参考 Lessons：关裁剪必须在 doOnAttach）
- [ ] insets 分发：EdgeToEdge padding 目前设在 pager 上，包 Compose 层后确认 insets 仍正确到达 fragment（必要时改设在 Compose 容器上）

### 2.3 Dock 效果应用

- [ ] Dock 根组件（胶囊形）应用：
  ```kotlin
  Modifier.drawBackdrop(
      backdrop = backdrop,
      shape = { 圆角胶囊形状（与现有一致） },
      effects = {
          vibrancy()
          blur(4f.dp.toPx())
          if (SDK_INT >= 33) lens(16f.dp.toPx(), 32f.dp.toPx())
      },
      onDrawSurface = { drawRect(Color.White.copy(alpha = 0.5f)) } // 可读性平衡，可做成滑块
  )
  ```
- [ ] Dock 选中态高亮按钮如需玻璃叠加，宿主用 `exportedBackdrop` 防循环捕获
- [ ] 背景为纯色（无壁纸/无内容）时 backdrop 需垫底色，避免玻璃出现透明像素（官方教程 Step2）

### 2.4 版本降级与开关

- [ ] `TingConfig` 新增 `glassDockEnabled`（Boolean，默认关闭）；设置页「个性化」区新增独立开关（与卡片样式并列，不复用卡片样式面板）
- [ ] 生效条件：`glassDockEnabled && SDK_INT >= 31`；不满足时 Dock 保持现有纯色样式
- [ ] 设置开关切换实时生效（参考卡片样式实时刷新链路：设置页 → MainActivity → Dock 组件重组刷新）

### 2.5 与壁纸系统的配合

- [ ] 应用内壁纸模式：壁纸 ImageView 在 View 层级底层，不在 Compose 树内。将壁纸绘制进 backdrop（`rememberLayerBackdrop { drawImage(壁纸bitmap); drawContent() }`）或把壁纸层迁入 Compose Box 背景——二选一，优先前者（改动小）
- [ ] 系统壁纸模式（`windowShowWallpaper`）：backdrop 内容里没有壁纸，玻璃必须垫主题色底色，否则透明。此模式下玻璃效果视觉降级，文档中注明

## 3. 阶段二：主页卡片液态玻璃（远期，暂不实施）

前置条件（全部满足才启动）：

- [ ] 通知列表由 RecyclerView 迁移到 Compose LazyColumn（需重写：StackedCardLayoutManager 堆叠布局、ItemTouchHelper 拖拽、内嵌搜索过滤）
- [ ] 卡片样式引擎改造：CardStyleEngine 保留配置存储与预览，样式应用改为 Compose 侧 `drawBackdrop`
- [ ] 性能评估：列表长时多个玻璃卡片的每帧采样成本（预期 GPU 可承受，需实测 `dumpsys gfxinfo`）

## 4. 验收标准

- [ ] Dock 背后滚动的主页列表内容在玻璃中实时折射/模糊，滚动跟手无跳变
- [ ] `dumpsys gfxinfo` 滚动帧率与开启前对比无显著劣化（90th 帧时间涨幅 <20%）
- [ ] API <31 设备/模拟器：开关可见但置灰或自动回退，Dock 显示纯色样式，无崩溃
- [ ] 主页顶栏越界绘制不受 Compose 包裹影响（竖屏+横屏像素采样验证状态栏融合）
- [ ] 设置开关即时切换（无需重启），关掉后完全回到现状

## 5. 已知风险清单

1. AndroidView 内部越界绘制（顶栏负 margin）可能不被 layerBackdrop 捕获或被裁剪——Dock 采样区在列表/底部，影响可控，但需实测确认无新裁剪问题
2. 库为 KMP 项目、版本迭代快（2026-08 仍在更新），锁定版本号，升级前查 changelog
3. SIGSEGV 循环捕获崩溃：任何「玻璃上放玻璃」的场景必须用 exportedBackdrop（官方 FAQ 已确认此崩溃形态）
4. 模拟器软件渲染下 RenderEffect 性能数据失真，性能结论以真机为准

## 6. 参考

- 文档：https://kyant.gitbook.io/backdrop （页面 URL 加 `.md` 可得 Markdown 版）
- 典型用法：tutorials/glass-bottom-bar（Dock 场景完全同构）、tutorials/glass-bottom-sheet（exportedBackdrop 防循环）
- 效果 API：api/backdrop-effects（顺序：colorFilter ⇒ blur ⇒ lens）
- 相关代码：`MainActivity.java`（pager 装配/insets）、`ui/navigation/FloatingDock.kt` + `FloatingDockInstaller.kt`（Dock Compose 组件）、`service/TingConfig.kt`（开关持久化）、`ui/settings/SettingsFragment.kt`（开关 UI，参考 refreshHomeCards 实时刷新模式）
