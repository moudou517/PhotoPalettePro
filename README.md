# 📸 PhotoPalettePro v2.0

![Version](https://img.shields.io/badge/Version-2.0-blue)
![Android](https://img.shields.io/badge/Android-API%2026%2B-green)
![License](https://img.shields.io/badge/License-MIT-orange)
![Java](https://img.shields.io/badge/Java-17-red)

> 一个把摄影作品做成**可收藏纸品**的 Android 工具：既能出 4K 摄影海报、Zine 风格明信片正反面，也能给照片套上一条 135 胶片边框。

**仓库地址**：<https://github.com/moudou517/PhotoPalettePro>

---

## 🆕 胶片边框（本轮新增）

主界面现在是**以摄影海报为中心的左右分向**结构：

```
            往右滑 ←            （默认页）             → 往左滑
      Zine 明信片          摄影海报              胶片边框
   （继续用取色逻辑）   （取色逻辑的出处）   （与取色逻辑完全无关）
```

这样分向的理由：明信片要接着用主页面的取色逻辑，**胶片边框则完全不碰取色**——
它只是给照片套一层片基，所以放在另一侧，两类功能不会互相干扰。

**进 App 默认落在中间那一页**，也就是取色逻辑的主页面（摄影海报）。三页固定、全部常驻，
滑到哪页才渲染哪页。

三页的版式统一：**最上面显示渲染效果的预览卡片固定在顶部**，配置区在下面滚动。
明信片和胶片边框都是「边调边看」的东西，预览跟着滚走就没法对着调了。
（胶片页的预览固定用 4:3 窗口 + `fitCenter`：胶片成品的长宽比会随原图在横幅与竖幅之间
大幅变化，若让 ImageView 自己撑高度，一张竖幅照片就会把固定区顶掉半屏。）

### 懒渲染：只有「完全滑进某一页」才动手

三个页面各有一套很贵的渲染（明信片要跑边缘检测 + 线稿重绘，胶片要解十几张照片，
海报是 4K 渲染），所以渲染统一挂在**页面停稳**之后，而不是 `onPageSelected`：

```java
public void onPageSelected(int position) {
    updateModeIndicator(position);     // 指示点跟着拖动实时走
}
public void onPageScrollStateChanged(int state) {
    if (state != ViewPager2.SCROLL_STATE_IDLE) return;   // 手指还在屏幕上，先不渲
    ...renderSettledPage(binding.vpModes.getCurrentItem());
}
```

`onPageSelected` 在拖动刚过半就会触发，那时手指还在屏幕上，接着跑几秒的渲染
会直接把滑动卡住。改到 `SCROLL_STATE_IDLE` 之后，页面真的停稳了才开始。

### 导入只解一张预览图

导入这一趟原来是**两张全尺寸解码**：

| | 旧 | 新 |
|---|---|---|
| 底片 | `loadSourceBitmap(4000,4000)` → 4000×3000 ≈ **48MB** | **不解**，等真正要出图时再解 |
| 预览图 | `loadPreviewBitmap(2000,2000)` → 采样条件写得不巧，**同样解出 4000×3000 ≈ 48MB** | 按最长边 1600 采样 → 1600×1200 ≈ **7.7MB** |

两个坑叠起来，光导入一张 12MP 手机照就占掉近 100MB，后面明信片、胶片再一动就是 OOM 退出。

- **底片懒解码**：`ensureSourceBitmap()` 只在「预览海报 / 生成明信片 / 导出」时解，
  一次一张、用完由 `recycleSourceBitmap()` 收掉；`onTrimMemory` 且不在取色页时也放掉。
  判断「有没有导入过照片」因此改看 `currentImageUri`，不能再拿位图当标志
  （`hasPhoto()`）。
- **预览图按最长边算采样率**：`calculateInSampleSize` 要求两个方向都超标才降采样，
  4000×3000 求 2000 时会返回 1，等于把原图整张解出来当预览。
  新增 `calculateInSampleSizeForMaxDimension`，再用 `fitWithin()` 精确收到目标尺寸。
- **导入不再自动渲 4K 海报**：历史记录回填后只记脏位。原来这一步会跑一整趟
  底片解码 + KMeans + 3840×2160 渲染（33MB）只为缩成预览图——同一张照片反复导入
  （历史记录必然存在）时就是必崩路径。
- **明信片背面改为翻面时才渲**：正面已经要跑线稿，背面同样贵而用户不一定翻，
  留到真翻面时再渲；导出时两边都渲，不受影响。

### 交互震动

`HapticHelper` 两处反馈，力度不同：

| 时机 | 反馈 |
|---|---|
| 换页停稳 | `CLOCK_TICK` —— 短促的轻点，起「到位了」的确认作用 |
| 下拉刷新触发 | `LONG_PRESS` —— 重一档，像机械开关落到底 |

刻意**不**传 `FLAG_IGNORE_GLOBAL_SETTING`：用户在系统里关掉触感反馈就该是关掉的，
App 不该越过这个设置。整个调用包一层 try/catch——触感失败绝不该让操作跟着挂掉。

### 诊断日志（以及一次严重的教训）

真机崩溃只靠读代码定位不了，所以 `CrashLogger` 做两件事：

- **崩溃堆栈**写进 `getExternalFilesDir/session.log`，连同时间、线程、机型；
- **面包屑**记录每一步操作（点导入 → 读尺寸 → 预览解码 → EXIF → …）。
  像 native 崩溃这种「连 Java 堆栈都没有」的情况，靠面包屑才知道它死在哪一步。

下次启动时判断「上一场是不是正常结束」，需要时弹出来，可**复制**或**分享**。
判定覆盖了「刚退到后台就没了」这一类——系统相册盖住 App 时崩掉正是这样，
早先的版本把它误当成「正常回收」，于是表现成「崩了却没有弹窗」。

> #### ⚠️ 一个必须记住的教训：诊断代码不许碰媒体库
>
> 早先为了让文件管理器能看到日志，这一版会把日志通过 **MediaStore 发布到公共「下载」目录**，
> 而且是在 **每次 `onCreate` 同步执行**。
>
> 代价是灾难性的：媒体库（MediaProvider）是全系统共用的，一旦它忙或状态异常，
> App 会卡在启动，而**整个相册与截图功能也跟着不正常**——一个诊断功能影响了用户的手机。
> 重启手机后恢复，也印证问题出在系统媒体库那一层。
>
> 现在的做法：日志只写应用自己的目录，靠 **FileProvider + `ACTION_SEND`** 分享出去。
> 范围最小，不碰任何公共媒体数据。**诊断永远不该有副作用。**

### 导入链路的验证

导入照片是这套东西里最容易出问题的一段，所以对它做了两件事：

**1. 端到端测试**（`MainActivityImportTest`）。把 Activity 真的建起来，喂一张
**12MP 真照片**（正是那条「两张全尺寸解码」老问题的触发尺寸），把整条链路跑完：
单张导入、实际解码确认、同一张导入两次、一次 6 张、一次 12 张、坏 URI、空输入。
任何确定性的异常都会在这里炸出来。

> 测试里照片走 `file://` 而不是 `content://`：Robolectric 里 provider 支撑的内容流
> 喂不进本地解码器，那样测试会「通过」但其实根本没解码。`file://` 能真实走完
> `BitmapFactory` 那条路，而 App 自己的代码对两者是同一条路径。

**2. 真机上的自证**（`CrashLogger`）。真机崩溃只靠读代码定位不了，于是：

- **崩溃堆栈**落进 `getExternalFilesDir/last-crash.txt`，连同时间、线程、机型；
- **面包屑**记录导入流程的每一步（读尺寸 → 预览解码 → EXIF → 完成），
  像 OOM 这种「堆栈里全是不认识的帧」的崩溃，靠面包屑才知道卡在哪一步；
- 下次启动时自动弹出来，**一键复制**就能发出来；读过即删，不会反复打扰。

记录完仍把异常交还给系统原本的处理器——否则系统收不到崩溃，统计和「应用已停止」提示都会失效。

### 解码的 OOM 兜底

`decodeWithRetry()`：解码撞上 `OutOfMemoryError` 时把采样率翻倍重试（最多两次）。

**OOM 是 `Error` 不是 `Exception`**，调用方那些 `catch (Exception)` 一个都拦不住，
App 直接没了。同样一张 12MP 照片在高低端机上的可用堆差别很大，
降一档采样换来的是「图糊一点」，而不是「App 没了」。

| | 胶片边框 |
|---|---|
| 画布 | 宽度随排布自适应、高度随行数增长；预览 1080 / 导出基准 2000（格子太小会等比放大） |
| 片基 | 5 种预设：经典白框 / 柯达金 / 富士绿 / 暗夜黑 / 银盐黑白 |
| 边框 | 窄 / 标准 / 宽 三档；齿孔与帧号可单独开关 |
| 照片 | 按原始比例**完整嵌入，不裁切、不拉伸**；支持一次导入多张 |
| 齿孔 | **按 135 胶片的真实规格推算**，孔的大小自动跟着照片缩放（见下） |
| 片边文字 | 上身机 / 镜头 / 曝光 / 日期由 **EXIF 自动预填**，留空或删空则回落到装饰文案 |
| 手机照片 | 镜头一律给 **35mm 等效焦段**（与主页面信息栏同一套判断），不印 5mm 这种物理焦距 |

### 齿孔不是画上去的花纹，是按真实规格算出来的

135 胶片的 KS 片孔是 **1.98 × 2.79mm**、节距 **4.75mm**，画幅 36×24mm ——
也就是**一格 8 个孔**（36mm 画幅 + 2mm 片间空档 = 8 个节距）。
这些数一比就定死了整个齿孔层：

```
节距 = 4.75/24 × 帧高 ≈ 0.198 × 帧高
孔长 = 1.98/4.75 × 节距 ≈ 0.417 × 节距
孔宽 = 2.79/1.98 × 孔长 ≈ 1.409 × 孔长
片基留边 = ((35−24)/2)/24 × 帧高 ≈ 0.229 × 帧高
```

于是**孔的大小自动跟着照片缩放**：一个 3:2 画幅摊到 7~8 个孔，
半格（18×24mm）自然只摊到 4 个。

同一个「一个孔长」（≈ 0.0825 帧高）还派了两个用场，都从上面那条式子推出来，
不是手感定的：

| 用在哪 | 为什么 |
|---|---|
| **截与截之间的缝** | 真实的剪口就落在片孔之间，一个孔长是最自然的留白 |
| **每一截两端各留的边** | 真实胶片上画幅不会顶着剪口，前面总还有一段片基。没有这段留白，照片就和切边齐平，看着像「把照片裁成了胶片形状」而不是「一条胶片里嵌着照片」 |

> 第二项是补上的：早先照片直接顶到剪口，两边没有留白，一眼就假。
> 现在横条两侧各让出一个孔长（竖条是上下），齿孔也顺势铺满整截、一直排到切边。
> `FilmMultiFrameTest` 里有两条断言钉住它：单张两侧的留白正好等于
> `perfLengthFor(帧高)`，以及**任何排布下画幅都不会压在剪口上**。

> 这一条修的是一个真实的手感问题：早期版本的留边是「画布宽度的固定比例」，
> 单张时恰好接近正确，**多张时帧变小、留边和孔却不变**，孔宽占到帧高的 30%
> （真实值是 11.6%），一眼就假。现在留边和孔都挂在帧高上，与照片数量无关。

### 有没有照片 ≠ 有没有主照片

`hasPhoto()` 判断「用户手上有没有东西可导出」，这里踩过两次同一个形状的坑，
值得单列：

| 错误写法 | 后果 |
|---|---|
| `sourceBitmap != null` | 底片是懒解码的，刚导入时它还是 null——导入后的第一次操作全误报「请先导入照片」 |
| `currentImageUri != null` | 多张胶片合成时「主照片」被**刻意放掉**（那两页已锁住），于是明明有 12 张照片，**导出按钮拒绝工作** |

正确写法是两者都看：`currentImageUri != null || !filmUris.isEmpty()`。
至于「能不能渲出海报」是另一个门槛，由 `ensureSourceBitmap()` 单独判断——
**「有没有东西」和「能不能做这件事」不是同一个问题**，混在一起就会漏。

### 多张合成（半格胶片）

胶片页有一个自己的 **「选择照片（可多选）」**，一次最多 **18 张**。
底部的「导入」仍是单张语义——海报要的是「一张底片出一个 4K 成品」，
胶片要的是「一组照片拼成一条片」，两者本来就不是一回事。

排布规则不是等宽网格，而是**半格胶片**那一套。
**每一行都是一截独立的胶片**：不是「一整块底板上挖几排洞」，而是
一条胶卷剪成几截摊在一起——每截自带上下两条齿孔带，截与截之间留一道缝、
缝两侧压一道极淡的裁切边（否则相邻两截同色同质，会糊成一整块）。

**缝宽 = 一个片孔的长度**（≈ 0.0825 帧高），不是随手定的数：真实的剪口本来就落在
片孔之间，一个孔长是最自然的留白。这个值由片孔规格直接推出来
（`PERF_PITCH_RATIO × PERF_LENGTH_RATIO`），规格一改它跟着走。

| 规则 | 说明 |
|---|---|
| **所有格子等高** | 这个高度就是「横幅的高度」，由最宽的一行反推出来 |
| **竖幅变窄，不旋转** | 每格宽度 = 帧高 × 该照片宽高比。一张 **3:4 竖幅的宽度恰好是 3:2 横幅的一半**——这正是半格（18×24mm 对 36×24mm） |
| **整体排成横版** | 见下：行数由「整张稿子要是横着的长方形」反推 |
| **按总宽高比均衡切分** | 不是简单地每行塞满，否则会出现「最后一行只剩一格」，整张片子头重脚轻 |
| **画布随排布变大** | 行数增加就变高；格子被压到小于 620px 时，整张画布**等比放大**，而不是让照片糊掉 |

### 行数由「整张稿子要是横版」反推

`MAX_COLUMNS = 8` 只是安全上限，**实际列数是算出来的**（`chooseRows`）。

两个方向是矛盾的：行数越多，每行的格子越少、格子越大，但整张稿子越高。
所以规则是——在「仍然是横版（高/宽 ≤ 0.85）」且「各行宽度相差不超过 1.6 倍」的
前提下，**取行数最多的方案**，因为它的格子最大：

| 张数 | 排布 | 宽高比 |
|---|---|---|
| 3 | 一行 3 格 | 1 : 0.42 |
| 6 | 3+3 | 1 : 0.70 |
| 12 | 4+4+4 | 1 : 0.83 |
| **18** | **6+6+6** | **1 : 0.63** |

18 张如果按「每行三格」硬塞会变成六行，宽高比 1 : 1.83 —— 一张竖长条，
而不是用户要的横着的长方形。

> 计算宽高比时**上边留白也要算进去**。早先漏了它，选行数时会高估「横版」的程度，
> 选完加上留白就超标了——18 张因此从「4 行 5+4+5+4」变成「3 行 6+6+6」。
> 阈值是硬约束，参与判断的量就必须和最终画出来的一致。

### 片边文字：两行之间的空白 = 参数行的高度

型号行与参数行的基线位置不是拍比例定的，而是按字形算的：

```
型号行墨迹下沿 + 空白(= 参数行墨迹高度) + 参数行墨迹上沿 = 参数行基线
```

早先第二行固定在「文字块高度的 80%」处——也就是 `0.36 × 0.15 × 画布宽`，
**一个和字号毫无关系的比例**：字号一小空隙就显得特别大，画布越宽拉得越开。
现在空隙恒等于参数那一行自己的高度，字号怎么变都成立
（`FilmBorderSmokeTest` 里有断言钉住：空白等于参数行墨迹高度，且随画布宽等比缩放）。

### 胶片四周都要留白

胶片不能顶着画布边缘。**这一条在三条分支上各漏过一次**，值得单列：

| 分支 | 漏在哪 | 后果 |
|---|---|---|
| 单张横幅 / 多张 | 上边 | 台面在读图时等于不存在，胶片变回"贴在纸边上的一张图" |
| **单张竖幅** | **左右** | 那边直接把帧宽解成 `1/(1+2×留边系数)`，整截正好占满画布宽度 |

现在三条分支共用一个规则：先扣掉 `edgeRatio × 2` 得到可用宽度，再在里面解帧宽与齿孔带。
`FilmVerticalMarginTest` 断言的不只是"有留白"，而是**四周那档留白是同一个值**——
只要有一条边漏了，比例立刻对不上。


导入多张时参数往往不是一套（不同时间、不同镜头、甚至不同机器），所以
**只保留第一张的机身**，其余回落到装饰文案——硬拼成一行「机身 · 镜头 · 曝光 · 日期」反而处处是错的。

### 多张时锁死左右翻页

海报的**取色**要从一张图上聚类色板，明信片的版式也只放得下一张。选着多张的时候翻过去，
那两页要么算的是「第一张」而用户以为是全部，要么干脆没意义。所以：

- 胶片页选了 **2 张及以上**时，`ViewPager2.setUserInputEnabled(false)` 直接锁死翻页；
- 用户仍然会去滑，所以滑的时候**弹窗解释**为什么翻不动，而不是让人以为界面卡了；
- 胶片页的张数标签也会写明「多张合成期间左右翻页已锁定」；
- 减回 1 张（或点底部「导入」重选一张）立即解锁。

这里有个坑：`ViewPager2` 是 `final` 的，没法继承；而它一旦 `setUserInputEnabled(false)`，
就**不再处理触摸**，也就无从得知「用户试图翻页」。所以加了一层
[`SwipeWatchLayout`](app/src/main/java/com/example/photopalettepro/helper/SwipeWatchLayout.java)：
一个只旁观、不拦截的容器，覆写 `dispatchTouchEvent` 观察手势（选它而不是
`onInterceptTouchEvent`，是因为前者对本子树里**每一个**事件都会被调用，不依赖
「有子视图吃掉 ACTION_DOWN」这个前提）。判定上要求横向位移超过 `touchSlop`
**且大于纵向的 1.5 倍**——否则用户每滚一下配置就会弹一次窗。每次手势最多报一次。

顺带：多张时不再加载主照片。那两页既然到不了，就没必要为它留一张 4K 位图；
减回 1 张时会自动补上。

内存上做了四件事，否则十几张一定炸：

1. **先读尺寸、后读像素**——只用 `inJustDecodeBounds` 拿宽高比就能算出版面；
2. **按格子尺寸解码**——`FilmBorderRenderer.cellSizesFor()` 给出每一格在成品里占多大，
   按它解码而不是一律按原图；
3. **再收一道**——`inSampleSize` 只能取 2 的幂，解码结果常是目标的 1~2 倍，
   这个「富余 × 张数」正是最容易 OOM 的地方，所以用 `fitWithin()` 精确收缩；
4. **多张时放掉主照片**——海报页与明信片页这时被锁住，主照片根本到不了，
   而它是一张 4K 位图加一张预览图（高像素手机上合起来上百 MB），
   正好把内存让给胶片这十几张。减回 1 张时自动补上。

除此之外，不在胶片页时（`onTrimMemory`）会直接放掉这批解码结果，滑回来再按需解一次。

### 读取照片全在后台线程

选完照片那一趟（读尺寸 → 算版面 → 逐张解码）**整体跑在后台线程**上，
主线程只负责快照配置、更新提示、把结果贴回界面。

原因是这条路径原本全在主线程：十几张照片的 IO 加解码就是几秒的卡死，
界面先冻住，紧接着往往就是 ANR 或者直接 OOM 退出。

用「代次」（`filmLoadGeneration`）作废旧结果——用户在读取途中又选了一批，
先回来的那批直接丢掉并回收，不会覆盖新的。

### 两批导入不会同时解码（「导入进程冲突」）

一个真实踩到的问题：点「选择照片（可多选）」选了十几张，解码要几秒；
这时从底部「导入」再选一张，就变成**两批解码同时占内存**——内存直接翻倍。
现象是「点导入多张卡住，再点导入就闪退」。

三处修正：

| 措施 | 说明 |
|---|---|
| **解码循环里逐步查代次** | 发现被取代就立刻停手、回收已解出来的图，什么也不往界面上贴 |
| **单线程排队解码** | `filmDecoder` 是一个单线程 `ExecutorService`，两批照片永远排在一条线上，物理上不可能同时解码 |
| **忙时点击给回应** | 原本忙的时候按钮是 `setEnabled(false)`——点下去毫无反应，看起来就是「卡死」；改成保持可点 + 提示「正在处理上一批照片」 |

`loadFilmPhotos` 的每一步之前都有一句 `if (isFilmLoadCancelled(generation)) break;`。
这一条是必须的，不是优化：它决定了两次导入是「排队」还是「抢内存」。

另外 `decodeTarget()` 的兜底也改过：算不出格子尺寸时**不能**退回「按导出宽度解码」，
十几张那样子就是几百 MB，兜底改成把一批 800 万像素的预算均分给每一张。

几个刻意的取舍：

- **手机必须换算等效焦段**。手机 EXIF 里的 `FocalLength` 是 5~7mm 的物理焦距，
  直接印到胶片上会被读成「5mm 超广角」，跟眼睛看到的视角完全对不上。
  所以手机走等效焦段、真相机才用原始镜头型号（`FE 50MM F1.8` 比一个等效数字信息量大得多）。
  等效值的来源按可靠性排序：先读 `FocalLengthIn35mmFilm`（这也是主页面在用的值），
  标签缺失时再用**焦距平面分辨率 → 传感器对角线 → 裁切系数**反推。
  两条都走不通就留空回落装饰文案——**宁可不显示，也不印一个错的视角**。
- **齿孔不是装饰花纹**。孔宽固定占孔距的 41.7%（135 胶片真实值），孔的大小由边框档位反推，
  所以三档边框只是「厚薄」变化，孔距不会跟着乱跑。
- **片边文字绝不编造参数**。海报那边的 EXIF 为了让信息栏不空，读不到时会兜底成
  `Unknown Device` / `1/100s` / `f/2.8` / `ISO 100`；这些值印到胶片上就是**伪造拍摄参数**，
  所以胶片边框另走一条 `ExifUtil.getFilmEdgeInfo()`，**读不到就返回空串**，
  再由 `FilmBorderConfig` 回落到装饰文案（如 `35MM · 135 · COLOR NEGATIVE`）。
- **片基是半透明的，齿孔是真的镂空**。照片外面那圈片基按 84% 不透明度绘制，
  台面从它下面透上来一层，读起来才像一片塑料底片；齿孔则是用 `Path` 的
  `EVEN_ODD` 规则**真正挖掉**的，台面原样透出——孔与片基的明度差就是这么来的。
  （早先是拿半透明色块去"画"孔，在深色片基上还勉强，浅色片基上就是几块灰斑。）
- **胶片摆在台面上，不是一个颜色铺满整张画布**。画布底色是「台面」，
  每截胶片的颜色是「片基」，两者必须拉开明度差，否则胶片会消失成几根描边。
  所以每个预设都挑过台面色：暖白片基配深暖炭灰，近黑的电影卷配亮台面
  （暗底负片压在灯箱上，也是最经典的观看方式）。片边文字落在台面上，
  因此它有独立的一组 `captionInk` / `captionAccent`，不沿用片基那套。
- **片基上按四个尺度叠不匀**：大面积浓淡（冲洗液流动）、涂层条痕（涂布/干燥沿走片方向）、
  银盐团块、单颗银盐的细颗粒；四周再压一层极淡的光衰减。
  再往上是只落在片基上、不碰照片也不碰台面的**灰尘与划痕**——
  照片是用户的内容，不能给人家划花。每一截用**各自的 seed**：
  剪开的几截本来就不是同一段，纹理不该一模一样。

---

## 🆕 v2.0 更新了什么

v2.0 从「单一海报工具」变成了**双模式翻页应用**（此后又扩到三模式，见上一节）：

| | 海报模式（原有） | Zine 明信片（v2.0 新增） |
|---|---|---|
| 画布 | 4K 横版 3840×2160 | 4:3 横版，预览 1080×810 / 导出 2000×1500 |
| 取色 | 默认渲染 / 取反差色 / 突出原色 | **跟随上述取色逻辑**，并按画面权重取前 6 色 |
| 输出 | 单张海报 | **正面 + 背面**两张 |
| 切换 | — | 左右滑动翻页（顶部有指示点） |

另外还做了：

- **iOS 玻璃拟态 UI**：柔和氛围渐变背景 + 半透明磨砂玻璃卡片/悬浮栏，API 31+ 使用真实背景模糊（`RenderEffect`），低版本自动降级
- **滑动圆角修复**：用 `ViewOutlineProvider` + `clipToOutline` 保证滚动内容不会露出直角
- **明信片懒加载**：导入照片时只渲染海报，**滑到明信片页才渲染明信片**，省时省内存
- **EXIF 预填**：自动把拍摄日期与 GPS 坐标填进明信片的 `DATE` / `LOCATION`
- **Room 持久化升级到 v3**：导出后把明信片信息一并落库，再次导入同一张照片可恢复上次填写的内容

---

## 🙏 灵感来源 · Skill 致谢

> **这一节请重点阅读。** v2.0 的 Zine 明信片模式，版式与美术方向来自下面两个开源的 **Agent Skill（提示词规范）**。它们是为「AI 出图」写的设计规范，不是代码；本应用是**按其规范用原生 Java / Canvas 做的离线实现**。

### 1. [Whiplashzeb/photo-to-zine-postcard](https://github.com/Whiplashzeb/photo-to-zine-postcard)

提供了明信片的**固定版式**：

- 纵向 2:3、暖象牙纸、细微纸纹
- 原图嵌入上方（保持原始比例、不裁切不拉伸）
- 左侧紧凑元数据块：小序号 + 短横线 / 衬线标题 / 斜体副标题 / `LOCATION` / `DATE`
- 右下：一个主元素 + 可选一个更小的辅助元素
- 取自原图的色块
- 背面：细外框 / 偏右分割线 / 右上邮票框 / 地址线 / 左侧留言区

### 2. [kwhi6693-web/photo-abstract-editorial](https://github.com/kwhi6693-web/photo-abstract-editorial)

提供了**美术方向**（`references/art-direction.md`）：

- 「先读作克制的抽象，再想起原图」——锥形笔触 / 短横带 / 结构轴线
- 保留原图的方向、比例、节奏、重心与不对称
- **不发明对称、不做完整插画、不做可辨识的矢量描摹**
- 暖象牙面板、克制的编辑级留白、高对比衬线标题

### 3. [Zeejay0/gathered-scenes-zine-skill](https://github.com/Zeejay0/gathered-scenes-zine-skill)

提供了**写实那一半**的语言（`scenes-gathered-zine-v1-3` 实景拼贴）：

- **真景为锚** —— 照片部分必须**如实**，不做滤镜、不丢细节；
  摄影提供事实，插画只解释事实
- **插画成场** —— 插画扩展照片没说完的空间，而不是描摹照片
- **色彩成结构** —— 引入**一个高纯度色**承担视觉重心，不是当装饰
- **纸面会呼吸** —— 留白参与叙事

> **与规范的三处有意偏离**（都以实际观感为准）：
> 1. 规范要求照片与纸面之间做「手撕纤维边」。实测硬撕边在明信片这种小尺寸下**太生硬**，
>    因此改为**大比例羽化**的柔和过渡（`REALITY_EDGE_FEATHER = 0.28`）。
> 2. 规范要求把照片作为固定锚点常驻。实测常驻会**盖掉插画**、失去原有风格，
>    因此改为**默认关闭的可选开关**，见下文「增强现实」。
> 3. 规范要求引入「一个高纯度色」作为构成结构。实测那束横跨接缝的高纯度色带
>    在拼接处会读成一块**突兀的色团**，因此去掉；改由对整条色板做适度提纯
>    （`SATURATION_GAIN = 1.25`）来承担「色彩成结构」的职责。

### ⚠️ 关于「手绘」的如实说明

三个 Skill 都要求插画部分**优先手绘二创**（水彩 / 墨线 / 拼贴）。**离线安卓端无法本地生成手绘插画**，因此本应用实现的是它们都允许的**降级路径**：

| 层 | 做法 |
|---|---|
| **插画场（默认）** | 先狠降采样把细节合并成**少量大形**，再量化到原图色板形成平涂；其上叠加低分辨率提取结构边缘、经非极大值抑制细化后重绘的抖动短笔触（程序化简笔线稿） |
| **增强现实（可选）** | 用**拉普拉斯算子**估算信息密度，在细节最密集处嵌入**原始照片像素**（如实、不过滤），替换边缘用大比例羽化柔化过渡 |

所以它是「**程序化简笔插画 + 可选的原照片嵌入**」，而不是真正的 AI 手绘。这一点在 [About 页面](app/src/main/java/com/example/photopalettepro/AboutActivity.java) 里也做了标注。

---

## ✨ 核心功能

### 📷 图片处理
- **高清导入**：支持 50MP+，按需采样（`calculateInSampleSize`）避免 OOM
- **4K 海报渲染**：3840×2160
- **EXIF 提取**：相机/镜头/快门/光圈/ISO + 拍摄日期 + GPS 坐标
- **设备识别**：500+ 相机与手机型号映射库

### 🎨 配色
- **三种取色逻辑**：默认渲染 / 取反差色 / 突出原色（KMeans 聚类 + 高级感调色）
- **权重排序**：`ColorExtractor.getTopWeightedColors()` 按「颜色实际覆盖的像素数」降序，明信片与海报共用同一套取色逻辑
- **自适应背景**：由色板最亮色推导

### 🖼️ 三种成品
- **海报**：水印签名开关、机身与镜头信息、横竖构图自动适配
- **Zine 明信片**：4:3 横版；左侧元数据 + 右下简笔插画主元素 + 6 个方形色块；背面为可书写的标准明信片版式（左侧铺满极淡的原图线条水印）
- **增强现实开关**（默认关闭）：开启后按**拉普拉斯信息密度**找到细节最密集处，把原照片软边嵌进插画——插画必然丢失的高频细节由它补回来
- **胶片边框**：5 种片基预设 × 3 档边框宽度；齿孔沿长边按 135 胶片真实孔距排布；片边文字由 EXIF 预填、留空则落装饰文案；照片按原比例完整嵌入不裁切

### 🌓 主题与交互
- 日间 / 夜间双主题（`values-night` + `drawable-night`），适配系统深色模式
- 玻璃拟态（Glassmorphism）UI
- 按钮按压缩放动画、下拉刷新物理阻尼（Overshoot 插值）
- 长按标题 5 秒跳转 About（分阶段震动反馈）
- 明信片卡片可点按翻面查看正/背面

---

## 🚀 快速开始

### 系统要求
- **minSdk**: 26（Android 8.0+）
- **targetSdk / compileSdk**: 35（Android 15）
- **Java**: 17
- **IDE**: Android Studio Flamingo 或更新

### 构建

```bash
git clone https://github.com/moudou517/PhotoPalettePro.git
cd PhotoPalettePro
./gradlew assembleDebug        # Windows: gradlew.bat assembleDebug
```

> 仓库已带 **Gradle Wrapper**（`gradlew` / `gradlew.bat` / `gradle/wrapper/`），
> 首次运行会自动下载 Gradle 8.11.1，不需要本机预装 Gradle。
> `local.properties` 里的 `sdk.dir` 需要指向你自己的 Android SDK。

单元测试：

```bash
./gradlew testDebugUnitTest
```

> 测试用 Robolectric 跑**真实 Skia 渲染**并导出 PNG 到 `app/build/film-out/`、`app/build/zine-out/`、
> `app/build/ui-out/`，可以直接肉眼看版式对不对；另有 Room 内存库测试覆盖导出历史的读写与迁移。

<details>
<summary>本机跑不了 Gradle 测试 worker 时的替代跑法</summary>

这台机器的沙箱环境里 Gradle 的测试 worker 起不来（命名管道被限制，
报 `GradleWorkerMain ClassNotFoundException` / 管道正在被关闭）。
`tools/run-unit-tests.ps1` 绕开 Gradle，直接用 `java.exe` 调 `JUnitCore`：

```powershell
pwsh tools/run-unit-tests.ps1              # 跑全部
pwsh tools/run-unit-tests.ps1 UiLayoutScreenshotTest
pwsh tools/run-unit-tests.ps1 -Prepare     # 只重新生成 classpath 与资源包
```

它依赖 Gradle 先生成三样东西（`build/test-cp.txt`、`build/aar-classpath.txt`、
`apk-for-local-test.ap_`），`-Prepare` 会一并搞定。

**测试类是自动扫描的**（`src/test/java/**/*Test.java`），不维护硬编码列表。
第一版是手写的列表，结果新加的测试类被静默跳过、「跑全量」跑的是旧集合——
这种事不该靠人记得改。改成扫描之后立刻发现还有 3 个测试类从来没被执行过。

</details>

### 界面出图：把「真机上长什么样」变成文件

`UiLayoutScreenshotTest` 把真实的 Activity 画成 PNG 到 `app/build/ui-out/`：
三个页面各一张，外加弹窗的一行。

> 这不是花架子。真机截图里「卡片白色没包住内容」那个 bug，
> **光读 XML 判断不出来**——运行时还有 `applyRoundedClip` 在改 outline 与裁剪。
> 把 decorView 量好、布局好、画出来之后，问题就变成一个可以反复比对的文件了。

同一类测试还有 `FilmBorderSmokeTest` / `FilmMultiFrameTest` 出的胶片样张，
它们一起构成了这套东西的「肉眼回归」。

### 使用流程

```
1. 启动 → 同意隐私政策（默认落在中间的「摄影海报」页）
2. [导入] 选择照片（自动读取 EXIF，预填明信片的日期/地点与胶片的机身/镜头/曝光/日期）
3. 海报模式：选渲染模式 + 排列方式 → [预览渲染效果] → [保存到相册]
4. 往右滑 → 明信片模式：填标题/副标题/地点/日期/序号 → [生成 Zine 明信片]
   → [翻面查看背面] → [保存明信片]（正面与背面各存一张）
5. 往左滑 → 胶片边框：选胶片风格 + 边框宽度，齿孔/帧号可开关 → 填胶片型号
   → [生成胶片边框] → [保存胶片边框]
   （胶片页的 [选择照片（可多选）] 可一次导入多张，最多 12 张，竖幅自动按半格收窄）
```

---

## 🎨 界面：一套玻璃，一套留白

### 玻璃面只用「一个 shape」

`bg_glass_card` / `bg_glass_bar` 都是**一个三段渐变的 shape** 加一层发丝描边：
顶边那一档是玻璃上沿的折射高光（`centerY="0.10"` 把它压在最上面 10%），
中间到下面才是真正的「面」。

> **为什么强调"一个"**：早先是「渐变底 + 半透明高光」两层叠出来的。
> 两层白在真机 GPU 上合成出了色差——中间内容区是 255、四周内边距一圈是 248，
> 一块方形亮斑嵌在灰白里，用户看到的就是「**白色没有把内容包进去**」。
> 软件渲染复现不出来（我出的对比图里整片都是 255），所以这个 bug 只在真机上看得见。
> 少一层，这条缝就不可能再出现。

### 卡片不做 `clipToOutline`

`applyRoundedClip` 会把整张卡片丢进一个离屏层再合成——而卡片的玻璃面本身是半透明的，
这一层的合成正是上面那个色差的来源。**圆角交给背景 drawable 自己负责**，
裁剪只留给真正需要的内容（预览图）。

### 卡片 8dp + 每一行 12dp

卡片内边距只留 8dp，视觉留白交给每一行自己的 12dp：

| | 结果 |
|---|---|
| 行的涟漪是圆角、有边界的 | 缩在卡片圆角之内，不会切出一块方形色斑 |
| 分割线用同样的 12dp 缩进 | 和文字左对齐，不会有线"跑到卡片外面"的观感 |
| 输入框文字（8+12）与设置行文字（8+12） | 落在同一条竖线上 |

以前是「卡片 20dp 内边距 + 行没有自己的表面」：行的可点区域和卡片留白对不齐，
看上去就像白色没把内容包住。

### 片边文字开关：关掉就只剩胶片

胶片页的开关从「显示胶片齿孔」换成了「**显示片边文字**」。

齿孔开关没什么用——孔本来就该有，关掉反而更假；而"要不要下面那行文字"是常调的：
想要纯胶片效果时，画面里就该只剩胶片。关掉之后：

| | 处理 |
|---|---|
| 文字块 | 连同它占的高度一起从画布上拿掉（`outHRatio` 里不再加 `TEXT_BLOCK_RATIO`） |
| 上下留白 | 改成**对称**的——上面留多少下面就留多少 |
| 画布 | 相应变矮 |

> 对称这条不能省。只把文字去掉、高度照旧的话，底边会多出一截没有内容的空白，
> 整张图看着像裁歪了。`FilmCaptionToggleTest` 用「两个只差胶片型号的配置各渲一张」
> 来验证字是真没了（还带一条反向对照：开关打开时两张必须不同，
> 否则那条断言证明不了任何东西）。

### 海报：超长画幅要缩回来，四周始终留 5%

`PosterRenderer.fitPhotoSize()` 是唯一的尺寸入口：

```java
int targetH = baseH;                     // 期望画幅高（H 的 0.618 / 0.62）
int targetW = imgW * targetH / imgH;     // 等比
if (targetW > W * 0.9) { targetW = W * 0.9; targetH = 等比跟着降; }
```

**超长画幅是关键**：一张 4000×600 的全景，按画幅高反推宽度会得到 8900px，
而画布只有 3840——照片横穿出去、两边被裁掉，看上去就像"贴歪了"。
现在宽度突破安全区时按宽度回撤，高度等比跟着降。

`SAFE_MARGIN = 0.05`：无论照片多宽多长，四周都留得住 5%，照片永远贴不到出血线。
两条都由 `PosterSizingTest` 拿 3:2 / 16:9 / 4:1 / 10:1 / 竖幅各种画幅钉住
（含「缩回来也必须保持原比例，不能被拉伸」）。

### 玻璃：真实半透明 + 背后得有东西 + 边缘折射

液态玻璃不是"一层白色的卡片"，它是**折射**。三件事缺一不可：

| | 做法 |
|---|---|
| **本体半透明** | 卡片顶 0.72 → 底 0.52。为了修色差一度刷到 0.9 以上，玻璃就没了——一层近乎不透明的白，后面的东西全被盖住 |
| **背后有东西** | `BackdropDrawable`：渐变 + 三团按屏幕比例摆的色雾。**没有它，玻璃透上去还是纯色**——真机截图里"玻璃效果全没了"就是这个原因 |
| **边缘折射** | `LiquidGlassDrawable`，见下 |

照片背景另外做两件事：`RenderEffect` 模糊（API 31+），以及用
`ColorMatrixColorFilter` 把**饱和度提到 1.35**——参考实现
（[rdev/liquid-glass-react](https://github.com/rdev/liquid-glass-react)）
里 `saturation` 默认 140，那不是调色偏好，是液态玻璃的物理前提。

> 色雾为什么不用 XML 写：layer-list 的 item 内边距**只收尺寸**
> （写 `52%` 直接编译失败），椭圆又会被矩形拉伸、渐变半径够不到边，
> 屏幕上就是几个能看见轮廓的圆。自己画之后位置按比例、半径按短边、
> **半径末端 alpha 归零**，怎么摆都是软的。

### 边缘折射：圆角矩形 SDF → 位移贴图 → AGSL

照搬参考实现的思路（作者说明改编自 shuding/liquid-glass）：

```
d      = 圆角矩形 SDF(u, v)             // 负数在形状内，0 在形状边上
bend   = smoothstep(0.8, 0, d - 0.15)   // 越靠形状外越大
scaled = smoothstep(0, 1, bend)          // 0 = 弯折最强，1 = 不折
采样点  = (uv - 0.5) * scaled + 0.5       // 朝中心收缩 —— 这就是折射
```

参考实现把位移**预计算成一张贴图**（R 存 x 位移、G 存 y 位移）交给
`feDisplacementMap`；这里同样预计算，消费者换成 AGSL 的 `RuntimeShader`，
R/G/B 三通道用略有差别的收缩量 → 边缘有色散彩边。

**分工是刻意的**：真正决定观感的数学跑在 Java 里（`buildDisplacementMap()`），
着色器只剩"采样 + 偏移"几行。于是那套数学可以被单测钉死，
GPU 那边几乎没有单独出错的空间。

> #### ⚠️ 形状必须「内切」，不能取元素本身
>
> 第一版我把 SDF 的形状取成元素本身（半宽 0.5、圆角 0.22），结果元素内部
> 任何一点都到不了 `d > 0.15` 这条弯折阈值——**整张位移贴图几乎是常数**，
> 归一化之后只剩噪声，屏幕上就是"没有折射"。
>
> 参考实现用的是 `roundedRectSDF(x, y, 0.3, 0.2, 0.6)`：形状的半宽半高比元素小，
> 圆角又比半宽大，于是形状缩在正中间，**它和元素边缘之间留出一圈弯折带**，
> 折射就发生在那一圈里。
>
> 这个 bug 是 `LiquidGlassTest` 抓出来的——`theWholeMapIsNotConstant` 直接断言
> 位移贴图必须有 >120 的变化范围。**纯数学是可测的，这正是把数学留在 Java 里的原因。**

**分层与性能**：折射只发生在边缘一圈（物理如此，也是必须的——整卡跑着色器
在滚动时要逐帧重算上百万像素）。所以中间大片直接贴背景快照，只有边缘那一圈
（`EDGE_BAND_RATIO = 0.18`）跑着色器。背景快照是全屏的 1/6，先盒式模糊三遍。

**API 33 以下**没有 AGSL，自动退回"半透明 + 高光 + 描边"，
观感差一档但不会缺东西。

### 选择弹窗：key 与显示名分开

弹窗不再是 `ListPopupWindow` + 系统默认的一行小字，而是自己搭的面板：
每项有**名字 + 一句说明 + 当前项打勾**。

> #### ⚠️ 尺寸必须跟着机型算，不能写死
>
> 原来（`ListPopupWindow`）宽度就是 `anchorView.getWidth()`——**跟着触发它的那一行**。
> 重做时我改成了 `Math.max(anchorView.getWidth(), 220dp)`，
> 多出来的下限会让弹窗比锚点行还宽，**窄屏上直接戳出屏幕**。
>
> 「锚点行宽」本来就是动态算的（行宽 = 屏宽 − 卡片外边距 − 卡片内边距），
> 用常量顶替它就等于放弃适配。现在：
>
> | | 规则 |
> |---|---|
> | **宽度** | 优先锚点那一行的实际宽度，夹在可视区内；锚点还没量出来（`getWidth()==0`）时退回可视宽度 |
> | **高度** | 夹在「锚点下沿 → 屏幕可见区底部」之间，超出部分由内部 `ScrollView` 兜 |
>
> 五个胶片风格各两行字，小屏上不夹高度会有一半点不到。这两条都由
> `PopupSizingTest` 用 320dp / 411dp / 800dp 三种几何钉住。

命名上做了一件重要的事——**逻辑标识和显示名拆开**：

```java
new PopupMenuHelper.Option("默认渲染", "标准取色", "聚类取色，最稳，适合大多数照片")
//                          ↑ key        ↑ 给人看的名字   ↑ 一句话说明
```

`key` 会写进历史记录、喂给 `ColorExtractor`，**不能改**；`title` 随便改。
以前这两件事是同一个字符串，于是「默认渲染」这种不知所云的名字既改不动
（一改就没有分支接得住，历史记录里存的老值也会失配），也没法解释它做什么。

| 原来的名字 | 现在 |
|---|---|
| 默认渲染 | **标准取色** |
| 取反差色 | **反差色** |
| 突出原色 | **原色还原** |
| 默认格式 | **常规排版** |
| 马赛克化 | **马赛克色块** |

### 震动是一套词汇，不是同一下

| 时机 | 力度 |
|---|---|
| 换页停稳 | 最轻，一下短促的「到位了」 |
| 选择弹窗弹出 | 轻，确认「有东西出现」 |
| 选中某一项 / 下拉刷新触发 | 中，像机械开关落到底 |
| 长按进度 | 渐强（25% / 50% / 75% / 100% 四档） |
| 长按达成 | 最重，一个明确的「成了」 |

全部走 `performHapticFeedback`，**刻意不传** `FLAG_IGNORE_GLOBAL_SETTING`：
用户在系统里关掉触感反馈就该是关掉的。

### 标题长按彩蛋：5 秒 → 1.2 秒

两个真问题：

1. **时间太长。**长按的通用心理预期是 0.5~1 秒，按满 5 秒已经超出「按一下」的范畴——
   用户按到 1 秒没反应就松手了，这个入口等于不存在。
2. **震动节奏根本不会响。**进度震动原来写在 `ACTION_MOVE` 里：手指只要不动，
   系统就不投递 MOVE 事件，那几个 1s / 2s / 3s 的节点**一个都不会命中**。
   现在改成按时间自己轮询，手指一动不动也照常给反馈。

另外原来用的是裸 `Vibrator`，绕开了系统的「触感反馈」开关——关掉了也照震；
现在统一走 `HapticHelper`。标题缓缓缩小到 0.92 就是蓄满，等于自带进度条。

## 📁 项目结构

```
app/src/main/java/com/example/photopalettepro/
├── MainActivity.java                  # 三模式编排：翻页、渲染、导出、懒加载
├── AboutActivity.java                 # 关于页（含 Skill 灵感来源）
├── ColorExtractor.java                # 取色算法（含按权重取前 N 色）
├── PosterRenderer.java                # 海报渲染引擎（4K）
├── PosterUtils.java                   # 海报工具（排序 / 空间映射 / 自适应底色）
├── ZinePostcardRenderer.java          # ★ Zine 明信片渲染（正反面 + 简笔线稿）
├── ZinePostcardConfig.java            # 明信片元数据（标题/副标题/地点/日期/序号）
├── FilmBorderRenderer.java            # ★ 胶片边框渲染（单张 / 多张排版 + 齿孔 + 片边文字）
├── FilmBorderConfig.java              # 胶片边框配置 + 装饰文案兜底规则（含多张的文案规则）
├── ExifUtil.java                      # EXIF 提取（500+ 型号映射 + 日期 + GPS + 片边参数 + 手机等效焦段）
│
├── film/                              # 胶片边框的「材料」层
│   ├── FilmStock.java                 # ★ 5 种片基预设（片基 + 台面 + 装饰文案）
│   └── FilmBase.java                  # ★ 台面 / 半透明片基 / 表面磨损
│
├── zine/                              # Zine 明信片的「材料」层
│   ├── PostcardPaper.java             # 暖象牙纸（云斑 / 纤维 / 颗粒）
│   ├── SceneMotifRenderer.java        # 简笔插画主元素
│   ├── SketchLineRenderer.java        # 抖动短笔触
│   ├── TornEdgeMask.java              # 程序化溶解软边
│   ├── PostcardPalette.java           # 色板提纯
│   └── InformationDensity.java        # 拉普拉斯信息密度（增强现实锚点）
│
├── helper/
│   ├── GlassEffectHelper.java         # ★ API 31+ 真实背景模糊
│   ├── UIInteractionHelper.java       # 按压动画 + 圆角裁剪
│   ├── PullRefreshHelper.java         # 下拉刷新（三个页面共用，触发时给一下震动）
│   ├── HapticHelper.java              # ★ 换页 / 下拉刷新的震动反馈
│   ├── CrashLogger.java               # ★ 崩溃堆栈 + 操作面包屑，下次启动可一键复制
│   ├── SwipeWatchLayout.java          # ★ 只旁观不拦截的容器：发现「用户想翻页」
│   ├── ImageProcessingHelper.java     # 采样、预览图生成、精确收缩（流全部 try-with-resources）
│   ├── ImageSaveHelper.java           # 异步存相册（MediaStore / 传统 API）
│   ├── ExifInfoManager.java           # EXIF 数据管理
│   ├── PopupMenuHelper.java           # 弹出菜单
│   └── TitleLongPressHelper.java      # 标题长按彩蛋
│
└── data/                              # Room 持久化
    ├── AppDatabase.java               # v4，含 2→3 / 3→4 两次正式 Migration
    ├── ExportHistory.java             # 导出历史实体（含 zineJson、filmJson 列）
    ├── ExportHistoryDao.java
    └── ExportHistoryRepository.java

app/src/main/res/
├── layout/
│   ├── activity_main.xml              # 外壳：顶栏 + ViewPager2 + 3 个指示点 + 底栏
│   ├── page_zine.xml                  # 第 1 页：明信片
│   ├── page_poster.xml                # 第 2 页：海报（默认）
│   ├── page_film.xml                  # 第 3 页：胶片边框
│   ├── activity_about.xml
│   └── dialog_privacy.xml
├── drawable{,-night}/                 # 玻璃材质、圆角、指示点
└── values{,-night}/                   # 颜色、主题、样式

app/src/test/java/.../ZineRenderSmokeTest.java    # ★ Robolectric + 真实 Skia 渲染出 PNG，用于肉眼校验
app/src/test/java/.../FilmBorderSmokeTest.java    # ★ 同上，校验胶片边框的版面、兜底文案与像素上限
app/src/test/java/.../FilmMultiFrameTest.java     # ★ 多张排布：横版形态、均衡分行、半格等高、画布放大
app/src/test/java/.../ExifFilmEdgeTest.java       # ★ 手机等效焦段换算（含各类护栏）
app/src/test/java/.../SwipeWatchLayoutTest.java   # ★ 翻页旁观：认得出横向拖动，不误判上下滚
app/src/test/java/.../ImageProcessingHelperTest.java  # ★ 解码尺寸收口（十几张不炸内存的最后一道闸）
app/src/test/java/.../FilmBorderConfigTest.java    # ★ 胶片配置的 JSON 往返与容错
app/src/test/java/.../ExportHistoryStoreTest.java  # ★ Room v4 读写、迁移与「合并而非覆盖」
app/src/test/java/.../MainActivityImportTest.java  # ★ 端到端导入：12MP 真照片跑完整条链路```

---

## 📊 技术亮点

### 1. 简笔线稿主元素

```java
// 低分辨率做边缘检测 → NMS 细化 → 输出分辨率上重绘抖动短笔触
Bitmap motif = ZinePostcardRenderer.renderFront(photo, palette, config, 2000);
```

关键点：**先做非极大值抑制把边缘细化成 1 像素**，否则一条 2~3px 厚的边缘会被画成好几道平行笔触，看起来毛躁。

### 2. 程序化溶解软边

不依赖 `BlurMaskFilter`（各版本表现不一致），改为在低分辨率上按「到矩形边距离 + 平滑噪声」计算 alpha 场，再双线性放大，得到线条与色块自然消散进纸面的边缘。

### 3. 取色与主页面同步

```java
// 先按当前取色逻辑拿色板，再按"实际覆盖像素数"排序取前 6 色
List<Integer> palette = ColorExtractor.getTopWeightedColors(bitmap, mode, 6);
```

`getTopWeightedColors(bitmap, count)` 只按簇内像素数排序。

### 4. 大图内存管理

```
50MP 原图
  ├─ 分析/导出用: 4000×4000（按需采样）
  └─ UI 预览用:   2000×2000
```

明信片侧还做了：预览 1080×810 / 导出 2000×1500；每次渲染的临时位图全部显式回收；`onTrimMemory` 且不在明信片页时主动释放预览位图。

### 5. 懒渲染

```
导入照片 → 只渲染海报 + zineDirty / filmDirty = true
左右滑动 → 落到哪一页才真正渲染哪一页
切换取色逻辑 → 只标记明信片脏位；若当前不在明信片页则等滑过去再渲染
```

---

## 💾 数据持久化

Room 数据库 `photo_palette_pro_db`，当前 **version 4**。

| 版本 | 变更 |
|---|---|
| 1 | 初始：`originalUri` / `optionsJson` / `sign` / `outputPath` / `timestamp` |
| 2 | 新增手动修改后的 EXIF：`device` / `lens` / `shutter` / `aperture` / `iso` |
| 3 | 新增 `zineJson`：明信片的标题/副标题/地点/日期/序号 |
| 4 | 新增 `filmJson`：胶片边框的风格/宽度/齿孔与帧号开关/片边文字 |

每一次都提供**正式 `Migration`** 而不是走破坏性迁移，老用户的导出历史不会丢。
再次导入同一张照片时，会自动回填上次的渲染模式、EXIF、明信片信息与胶片设置。

### 胶片导出：合并而不是覆盖

胶片页导出时**不会**直接插一行新记录，而是先读回该照片的上一条，把 `filmJson` 并进去：

```
读上一条 → 沿用它的 optionsJson / EXIF / zineJson → 只替换 filmJson → 写回
```

原因是 `getLastConfigByUri` 取的是 `ORDER BY timestamp DESC LIMIT 1` 的**最新一行**。
直接插一行只有 `filmJson` 的新记录，会把这张照片海报那一半的设置一起顶掉，
下次导入就恢复不出渲染模式了。这一段由 `ExportHistoryStoreTest` 里的
`mergingKeepsThePosterHalfOfTheRecord` 钉住。

多张胶片合成时「主照片」已被放掉（那两页被锁住），历史改以**胶片的第一张**为键——
再次导入它即可拿回上次的胶片设置。注意恢复的是**设置**而不是照片集合：
一组照片没法从一行历史里还原出来，照片还得自己重选。

---

## 🔐 隐私

- **完全离线**：图片处理全在本地，不上传任何数据
- **EXIF**：只读取摄影参数；GPS 只用于预填明信片的地点栏，**不联网反查地名**
- **首次启动**需确认隐私政策（存 SharedPreferences）

---

## 🐛 已知限制

| 限制 | 说明 |
|---|---|
| 主元素是程序化线稿 | 见上文「关于手绘的如实说明」，非 AI 手绘插画 |
| 地点只填坐标 | EXIF 里只有经纬度，离线不做地名反查（填入形如 `31.23°N 121.47°E`，可手动改） |
| 取反差色偶尔少于 6 色 | 该模式的色板本身可能产生重复色，去重后即为 5 色 |
| 胶片只记住设置，不记住照片 | 多张合成没法从一行历史里还原，再次导入只能拿回风格/宽度/片边文字，照片要重选 |
| 胶片边框一次最多 18 张 | 再多就不是「一条胶片」而是一面照片墙了，且导出时整张画布会逼近像素上限 |
| 胶片边框有像素上限 | 单张输出上限 2600 万像素，超长全景 / 竖幅或格子过密时会回撤输出宽度而不是硬渲染 |
| 单元测试依赖 Robolectric | 仅在 `testImplementation`，不进 APK；不需要可删掉测试与依赖 |

---

## 📚 文档导航

| 文档 | 描述 |
|------|------|
| **README.md** | 项目总览（本文档） |
| **MODULARIZATION_GUIDE.md** | 模块化设计详解 |
| **NIGHT_MODE_DESIGN.md** | 深色模式 WCAG 标准设计 |
| **COLOR_REFERENCE.md** | 色板参考 |
| **OPTIMIZATION_SUMMARY.md** | 性能优化总结 |
| **MIGRATION_CHECKLIST.md** | 升级迁移清单 |

---

## 🙏 致谢

**Skill / 设计规范**

- [Whiplashzeb/photo-to-zine-postcard](https://github.com/Whiplashzeb/photo-to-zine-postcard) — 明信片版式规范
- [kwhi6693-web/photo-abstract-editorial](https://github.com/kwhi6693-web/photo-abstract-editorial) — 抽象编辑美术方向

**开源库**

- [AndroidX](https://developer.android.com/jetpack) — ViewPager2 / Room / RecyclerView / SwipeRefreshLayout
- [Material Design](https://material.io/) — 组件与配色规范
- [EXIF Interface](https://developer.android.com/jetpack/androidx/releases/exifinterface) — EXIF 解析
- [Palette](https://developer.android.com/jetpack/androidx/releases/palette) — 配色提取
- [Robolectric](https://robolectric.org/) — 本地渲染校验

---

## 📜 许可证

MIT License © 2026 PhotoPalettePro

> 注意：上面两个 Skill 仓库中，`photo-to-zine-postcard` 为 MIT；`photo-abstract-editorial` 为 **AGPL-3.0**。本项目只参考了其**设计规范/美术方向**并用原生代码独立实现，未复制其代码或素材。如果你的使用场景涉及 AGPL 的传染性要求，请自行评估。

```
Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.
```

---

**Last Updated**: 2026-09
**Current Version**: 2.0（versionCode 5）

---

## 应用图标

自适应图标，两层：

| 文件 | 作用 |
|---|---|
| `drawable/ic_launcher_background.xml` | 背景：极淡的冷白渐变（前景满幅时基本看不到） |
| `drawable/ic_launcher_foreground.xml` | 前景：8×8 色块 + 灰网格线 + 三个玻璃 P |
| `mipmap-anydpi-v26/ic_launcher{,_round}.xml` | 把上面两层组装成自适应图标 |

### ⚠️ 三个踩过的坑

**一、mipmap 与 drawable 同名会互相顶掉**

`mipmap-xxhdpi/ic_launcher_foreground.webp` 和
`drawable/ic_launcher_foreground.xml` **同名**时，`@drawable/ic_launcher_foreground`
可能解析到 mipmap 里那张位图。症状极具迷惑性：

- 改矢量"没有效果"（桌面显示的还是位图）
- 出图工具反复"滞后"（同一个名字解析到了别处）
- 用户看到的图标和预览**始终不一致**

**现在那 20 张历史位图已经删掉**，图标只有矢量。加新图标资源时，
**不要和 mipmap 里的既有文件重名**。

**二、vector 的渐变只有三个色标，且运行期解析**

`<gradient>` 的 `startColor` / `centerColor` / `endColor` 撑不出完整色相环；
拼多段内嵌 `<aapt:attr>` 又会踩到运行期解析——试过三版，分别渲染成
**整片空白**和**黑块**。而 `pathData` 是字符串，**编译期不校验**：
编译通过 ≠ 能画出来。

所以色块配色是用纯 `fillColor` 逐格写的，**没有用渐变**。
要真正的连续渐变，正确载体是位图（`drawable-nodpi/`），
不是同一个 vector 里堆几十个渐变。

**三、改了资源必须出图确认**

`LauncherIconTest` 会渲染成 PNG（含圆角方形遮罩预览）：
`app/build/ui-out/icon.png` 与 `icon-squircle.png`。
只看代码判断不出图标好不好看，也判断不出它是不是根本没画出来。

### 配色

八列竖条，亮度**全平**（159.5~160.4），靠色相拉开：

```
#FF7A66  #BF9587  #7FAFA9  #3FCACA  #3FCACA  #7FAFA9  #BF9587  #FF7A66
```

两边是荧光珊瑚、中间是鲜青。**亮度持平是关键**——早期版本中间用
高饱和的青（亮度 161 vs 红 119），中间那一列会"跳"出来。

三个 P 用玻璃质感（对应 `LiquidGlassDrawable` 的逻辑）：
半透明白 `#D9FFFFFF` + 偏移投影 `#42000000` + 上沿高光 `#59FFFFFF`。
半透明让底色透上来，所以 P 不会和背景割裂。

---

## 源码结构

按**职责**分包，不再全平铺在根包：

```
com.example.photopalettepro/
├── MainActivity.java          入口（留在根包，避免动 manifest）
├── AboutActivity.java
├── render/    渲染器     PosterRenderer / ZinePostcardRenderer / FilmBorderRenderer / ColorExtractor
├── config/    配置模型   FilmBorderConfig / ZinePostcardConfig
├── util/      无状态工具 ExifUtil / PosterUtils
├── ui/        交互与视觉 AppDialog / PopupMenuHelper / HapticHelper / PullRefreshHelper /
│                        TitleLongPressHelper / SwipeWatchLayout / CrashLogger / ExifInfoManager /
│                        ImageProcessingHelper / ImageSaveHelper / GlassEffectHelper /
│                        LiquidGlassDrawable / BackdropDrawable / UiInteractionHelper
├── data/      Room       AppDatabase / ExportHistory / ExportHistoryDao / ExportHistoryRepository
├── film/      胶片素材   FilmBase / FilmStock
└── zine/      明信片     PostcardPaper / PostcardPalette / SceneMotifRenderer / …
```

（`helper/` 目录名保留——它同时装了 Helper、Drawable、Layout、Logger 四类东西，
改成 `ui/` 更贴切，但那是纯改名、收益不大，暂不动。）

### 拆分时踩到的两件事

**一、包级私有方法跨包就不可见了**

`ExifUtil` 里有几个 `static` 方法没写修饰符（默认包级私有），
测试原来和它同包所以能直接调；移进 `util/` 之后立刻编译失败。
**处理**：把这 8 个 `static` 放宽为 `public`（放宽访问权限不会破坏任何调用方）。

**二、批量正则别把 `static {` 也改了**

用正则把 `static` 提升为 `public static` 时，**静态初始化块** `static { … }`
也被改成了 `public static { … }` —— 非法语法，编译直接报
「非法的类型开始」。**处理**：正则加行尾 `{` 的排除，改完必须编译验证。

---

## 仓库导航

### 根目录

| 文件 | 说明 |
|---|---|
| `README.md` | 主文档（本文件）：功能、设计决策、踩过的坑 |
| `docs/` | 历史文档：配色参考、迁移清单、模块化指南、夜间模式设计、优化总结、v2.0 发布说明 |
| `tools/run-unit-tests.ps1` | **开发工具**，不是应用代码 |
| `gradlew` / `gradlew.bat` / `gradle/` | Gradle wrapper |

`local.properties`（本机 SDK 路径）和 `build/`、`.gradle/`、`.idea/` 都在 `.gitignore` 里，**不入库**。

### `tools/run-unit-tests.ps1` 是干什么的

它**不是应用的一部分**，是给开发环境用的：某些受限环境下 Gradle 的 test worker
起不来（共享内存 / 进程创建被限制），直接跑 `:app:testDebugUnitTest` 会失败。
这个脚本绕过 Gradle，用 `java + org.junit.runner.JUnitCore` 直接执行同一批用例：

```powershell
pwsh -File tools/run-unit-tests.ps1
```

它会自动发现 `app/src/test/java/**/*Test.java`，所以**加了新测试类不用改脚本**
（早期版本写死了测试类列表，结果新加的三个测试类被静默跳过）。

**不需要它的话可以整个删掉**，不影响 App 构建和发布。

### 构建

```bash
./gradlew :app:assembleDebug        # 产物 app/build/outputs/apk/debug/app-debug.apk
```
