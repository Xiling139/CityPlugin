# CityPlugin 插件设计文档

> Minecraft 城市群管理与建筑生成插件（Region + Builder 统合）
> 版本：v1.0 设计稿（实施版）
> 目标平台：Paper / Spigot 1.21.11
> 文档语言：中文

---

## 0. 变更说明

原 **RegionPlugin**（玩家行政区域显示：`/region info|list|tag|cleanup` + BossBar）与计划中的 **BuilderPlugin**（`/buildat`、`/buildzone` 建筑生成）功能互补、共用同一张城市地图，现合并为单一插件 **CityPlugin**：

- 一个 jar、一个数据目录（`plugins/CityPlugin/`）、一套权限。
- 统一主命令 `/city`，保留 `/region`、`/rg` 作为兼容别名。
- 区域数据库自动从 `plugins/RegionPlugin/regions.db` 迁移一次（旧目录保留不动）。
- 原有区域功能行为不变，仅包名与主类改变。

---

## 1. 项目概述

### 1.1 背景

用户拥有一张**现代城市群**主题的 Minecraft 世界地图，地形**非超平坦**（有起伏、水域、已建区域）。手工搭建城市扩展速度慢，需要通过插件以**指令方式快速生成建筑**，并保证建筑能**自动贴合地形**、不悬空、不埋地、不破坏已有城市。

### 1.2 目标

- 通过命令一键生成预设建筑
- 建筑能自动探测地面高度并补齐地基
- 支持朝向旋转，适配城市道路网格
- 支持批量生成街区（`/buildzone`）
- 支持精确坐标放置（`/buildat`）
- 生成前做地形与占位检查，避免覆盖城市
- 大批量方块分 tick 放置，防止卡服
- 区域（行政区）标注与展示功能保持可用

---

## 2. 插件标识与迁移

| 项目 | 旧值 | 新值 |
| --- | --- | --- |
| 插件名 | RegionPlugin | CityPlugin |
| 主类 | `com.xiling.RegionPlugin` | `com.xiling.city.CityPlugin` |
| 包前缀 | `com.xiling.*` | `com.xiling.city.*` |
| 数据目录 | `plugins/RegionPlugin/` | `plugins/CityPlugin/` |
| 数据库 | `regions.db` | `regions.db`（首次启动自动复制旧库） |
| jar 名 | `RegionPlugin-<ver>.jar` | `CityPlugin-<ver>.jar` |
| 版本 | 1.5.1 | 2.0.0 |
| 主命令 | `/region` | `/city`（别名 `/region`、`/rg`） |
| WorldEdit | 未声明（运行时隐式依赖） | `depend: [WorldEdit]`（7.4+） |

**迁移规则**

1. `onEnable()` 时若 `plugins/CityPlugin/regions.db` 不存在、而 `plugins/RegionPlugin/regions.db` 存在，则复制（不删除旧文件）并记录日志。
2. 服务器上必须移除旧 `RegionPlugin.jar`，否则 `/region` 指令会重复注册。启动时若检测到旧插件加载，输出警告。
3. 旧权限节点继续生效（见 §4）。

---

## 3. 命令总览

### 3.1 主命令 `/city`（别名 `/region`、`/rg`）

| 命令 | 说明 | 权限 |
| --- | --- | --- |
| `/city info` | 显示当前区块所属行政区 | `city.user` |
| `/city list [province]` | 列出全部区域 / 某省区域 | `city.user` |
| `/city tag [-f] <province\|-> <city\|-> <district\|-> <village\|->` | 用 WorldEdit 选区标注区域 | `city.admin` |
| `/city cleanup` | 列出 0 区块的空区域 | `city.admin` |
| `/city cleanupconfirm` | 确认删除空区域 | `city.admin` |
| `/city help` | 帮助 | 无 |

`/city tag` 与 `/city region tag` 等价；旧式 `/region info` 等直接可用（`/city` 会把 `info/list/tag/cleanup` 识别为区域子命令）。

### 3.2 建筑子命令 `/city build ...`

| 命令 | 说明 | 权限 |
| --- | --- | --- |
| `/city build at <模板> [rotation] [--force] [--preview]` | 在准星/脚下位置生成一栋建筑 | `city.build` |
| `/city build zone <模板\|random\|<类别>> [--spacing N] [--radius N] [--rotation 0\|90\|180\|270\|auto] [--force] [--preview]` | 在选区内批量生成街区 | `city.build.zone` |
| `/city build list [类别]` | 列出可用建筑模板（含尺寸） | `city.build` |
| `/city build reload` | 重新扫描建筑目录 | `city.build.admin` |
| `/city build status` | 查看当前建造任务进度 | `city.build` |
| `/city build cancel` | 取消自己的建造任务（已放方块保留） | `city.build` |
| `/city build undo` | 撤销自己最近一次建造 | `city.build.undo` |

### 3.3 计划书中的短命令（别名）

| 命令 | 等价于 |
| --- | --- |
| `/buildat <模板> [rotation] ...` | `/city build at ...` |
| `/buildzone <模板\|random\|类别> ...` | `/city build zone ...` |

`/buildat`、`/buildzone` 在 plugin.yml 中单独注册（别名 `/ba`、`/bz`），执行器内部转交 `BuildCommand`，参数解析与 `/city build` 完全一致。

### 3.4 参数约定

- `rotation`：`0` / `90` / `180` / `270`，绕 Y 轴顺时针旋转；`auto` 仅用于 `zone`（按单元格坐标哈希选一个方向）。
- `--force`：跳过占位检查与区域保护（仍做地形与高度合法性检查）。
- `--preview`：只做检查与规划，输出统计（占地、地基深度、方块总数），不实际放置。
- `--radius N`：`zone` 未定义 WorldEdit 选区时，以玩家为中心、半径 N 的方形区域（1–64）。
- `--spacing N`：建筑之间的空隙（道路宽度），0–32，默认取配置 `zone.spacing`。

---

## 4. 权限

| 节点 | 默认 | 说明 |
| --- | --- | --- |
| `city.user` | true | 查看区域信息、列表、建筑列表 |
| `city.admin` | op | 区域标注、清理 |
| `city.build` | op | `/city build at`、status、cancel |
| `city.build.zone` | op | `/city build zone` |
| `city.build.undo` | op | 撤销 |
| `city.build.admin` | op | `--force`、`reload`、取消他人任务 |

旧节点 `region.admin` / `region.user` 在代码中作为 `city.admin` / `city.user` 的等价节点接受（`hasPermission` 任一通过即可），保证旧权限组配置不失效。

---

## 5. 配置文件 `config.yml`

首次启动生成；`config.yml` 缺失或键缺失时使用内置默认值（不做整文件覆盖）。

```yaml
# CityPlugin 配置
region:
  # 是否启用区域标注与 BossBar 显示
  enabled: true
  # BossBar 刷新节流（毫秒）
  bossbar-refresh-ms: 0

builder:
  enabled: true
  # 建筑模板目录（相对 plugins/CityPlugin/）
  buildings-folder: buildings
  # 单 tick 最多放置的方块数（全局）
  blocks-per-tick: 2000
  # 单个任务的最大方块数
  max-queue-blocks: 300000
  # 单个模板的最大方块数（超出拒绝加载）
  max-template-blocks: 200000
  # 地基最大深度（超过则判定地形过陡，拒绝生成）
  max-foundation-depth: 32
  # 建筑最大高度（相对基准面）
  max-build-height: 160
  # 默认地基方块
  foundation-block: minecraft:stone_bricks
  # 按类别覆盖地基方块，例如 { residential: minecraft:stone }
  foundation-block-per-category: {}
  # 是否允许在水面上生成（false 时，足迹内有水则拒绝）
  fill-water: false
  # 是否遵守区域标注（true 时，足迹区块已被标注则拒绝，除非 --force）
  respect-regions: true
  # 地形扫描时不计为"地面"的方块（水、岩浆、树叶等）
  ground-ignore:
    - minecraft:water
    - minecraft:lava
    - '#leaves'
    - '#logs'
    - minecraft:short_grass
    - minecraft:tall_grass
    - minecraft:fern
    - minecraft:large_fern
    - minecraft:dead_bush
    - minecraft:snow
    - minecraft:vine
  # 允许被直接覆盖/清除的自然方块（占位检查白名单）
  # 建筑体积、地基夹层、以及"地面方块本身"都必须在此名单内
  natural-blocks:
    - minecraft:air
    - minecraft:cave_air
    - minecraft:void_air
    - '#leaves'
    - '#logs'
    - '#flowers'
    - '#saplings'
    - '#crops'
    - '#replaceable_by_trees'
    - '#base_stone_overworld'
    - '#base_stone_nether'
    - '#dirt'
    - '#sand'
    - '#snow'
    - '#ice'
    - minecraft:short_grass
    - minecraft:tall_grass
    - minecraft:fern
    - minecraft:large_fern
    - minecraft:dead_bush
    - minecraft:vine
    - minecraft:water
    - minecraft:lava
    - minecraft:gravel
    - minecraft:clay
    - minecraft:moss_block
    - minecraft:podzol
    - minecraft:mycelium
    - minecraft:mud
    - minecraft:sandstone
    - minecraft:red_sandstone
    - minecraft:terracotta
    - minecraft:netherrack
    - minecraft:soul_sand
    - minecraft:soul_soil
    - minecraft:basalt
    - minecraft:blackstone
    - minecraft:end_stone
    - minecraft:obsidian
    - minecraft:bedrock
    - minecraft:magma_block
    - minecraft:dripstone_block
    - minecraft:moss_carpet
    - minecraft:crimson_nylium
    - minecraft:warped_nylium
  # 撤销历史保留的建造次数（按玩家）
  undo-history: 5
  zone:
    # 批量生成的最大区域面积（方块数，长×宽）
    max-area: 16384
    # 单次最大建筑数量
    max-buildings: 64
    # 建筑之间默认空隙（道路宽度）
    spacing: 7
  messages:
    prefix: '§6[City] §r'
```

**实现说明**

- `ground-ignore` / `natural-blocks` 支持三种写法：`#tag`（Bukkit `Tag<Material>`）、`minecraft:xxx`、`xxx`。
- 无效方块名在加载时记录警告并忽略，不阻断启动。
- `config.yml` 仅读取，不自动改写（避免注释丢失）；`reload` 命令重载建筑目录，不重载 config（重启生效，文档注明）。

---

## 6. 建筑模板规范

### 6.1 目录结构

```
plugins/CityPlugin/buildings/
├── residential/
│   ├── house_small.schem
│   └── house_medium.schem
├── commercial/
│   ├── shop_1f.schem
│   └── tower_12f.schem
├── industrial/
│   └── warehouse.schem
├── park/
│   └── plaza.schem
└── road/
    └── street_lamp.schem
```

- 格式：**WorldEdit `.schem`**（Sponge Schematic，WE 7.x 默认格式）。`.schematic`（旧版 MCEdit）也接受，由 WE 自动识别。
- 模板名 = 相对 `buildings/` 的路径去掉扩展名，例如 `residential/house_small`。
- 模板名只允许 `[a-zA-Z0-9_\-\/]`；中文名可用但需注意输入法，建议英文。
- 类别名 = 模板所在的第一级目录；直接放在 `buildings/` 根下的模板类别为 `default`。
- 同名不同格式（`.schem` 与 `.schematic`）时优先 `.schem`。
- 示例模板：`examples/buildings/`（5 个，含住宅/商铺/路灯/广场），可直接复制到服务器测试，说明见 `examples/README.md`。

### 6.2 锚点

- 水平：**建筑足迹的中心**（取模板方块包围盒的 X/Z 中心，向下取整）。
- 垂直：**模板最低非空气方块所在的 Y** 作为建筑底面（通常为 0）。
- 模板内的空气方块不放置（保留原地形），"结构空位"（structure_void）同样跳过。
- 因此 `.schem` 导出时无需对齐坐标，插件自动按包围盒修正。

### 6.3 旋转

- 支持 `0/90/180/270`，绕建筑中心旋转。
- 方块状态随旋转调整（`BlockData#rotate(StructureRotation)`），覆盖楼梯、门、栅栏、告示牌朝向等。
- 含实体（画、盔甲架、物品展示框）的模板：实体不复制（v1 限制，文档注明）。
- 旋转后包围盒尺寸可能交换（X↔Z）。

### 6.4 加载

- 启动时（WorldEdit 存在时）扫描目录并读入 `BuildingTemplate`；`/city build reload` 重新扫描。
- 读取失败（损坏文件、超大）记录警告并跳过该模板。
- 超过 `max-template-blocks` 的模板拒绝加载。

---

## 7. 地形适配算法

### 7.1 输入

- 建筑模板（旋转后）：尺寸 `w × h × d`，方块列表（相对坐标）。
- 目标位置：由 `/buildat` 或 `/buildzone` 给出足迹矩形 `[x1..x2] × [z1..z2]`（X/Z 由建筑中心与尺寸决定）。

### 7.2 地面扫描

对足迹内每个柱 `(x, z)`：

```
for y = world.maxHeight-1 down to world.minHeight:
    if isGround(block(x, y, z)):
        groundY[x][z] = y
        break
```

- `isGround`：非空气、且不在 `ground-ignore` 中（水、岩浆、树叶、草丛等不算地面）。
- 若整列无地面（虚空/水域且海底低于 minHeight）→ 拒绝生成，提示"该位置下方没有可用地面"。
- 扫描范围限制：`max-foundation-depth + max-build-height` 内的向下搜索；超出即判为过深。

### 7.3 基准面与地基

```
baseY = max(groundY) + 1          // 建筑底面
minGround = min(groundY)
foundationDepth = baseY - minGround - 1
```

- `foundationDepth > max-foundation-depth` → 拒绝（地形过陡），提示实际深度与上限。
- 每个柱：从 `groundY+1` 到 `baseY-1` 填充 `foundation-block`（按类别覆盖）。
- 若柱下方为水（`groundY` 是海底，水面高于 groundY）：
  - `fill-water: false` → 拒绝，提示"足迹内有水域（可开启 fill-water）"；
  - `fill-water: true` → 照常填地基，水被替换。

### 7.4 清场

- 清场范围：足迹内 `baseY .. baseY+h-1`（建筑体积）。
- 仅清除 `natural-blocks` 中的方块（树、草、雪等）以及模板中非空气方块将要占用的位置。
- 占位检查（§8）保证该范围内不存在玩家建造的方块，因此清场不会误删建筑。

### 7.5 放置顺序

1. 地基方块（自下而上）。
2. 建筑方块（按 Y 从低到高，便于观感与光照更新）。
3. 清除多余植被（可并入第 2 步逐个位置处理）。

全部进入放置队列（§9），由队列统一分 tick 执行。

### 7.6 高度与边界校验

- `baseY + h - 1 > world.maxHeight - 1` → 拒绝，提示高度超限。
- 足迹任一方块超出世界边界（WorldBorder）→ 拒绝。
- 目标区块未加载时按需加载（`world.getChunkAt(x, z)` 同步加载）；单次任务涉及区块数超过 `zone.max-area` 对应上限时拒绝。

---

## 8. 占位检查与保护

生成前依次执行，任一失败即拒绝并给出原因（`--force` 可跳过 8.2、8.3）：

### 8.1 地形合法性（不可跳过）

- 无地面、地基过深、高度超限、超出世界边界。

### 8.2 占位检查（`--force` 跳过）

检查三个部分：建筑体积 `baseY..baseY+h-1`、地基间层 `groundY+1..baseY-1`（每柱），以及**地面方块本身** `groundY`（每柱）。

- 建筑体积中模板要占用的方块若不在 `natural-blocks` 白名单 → 视为已有建筑/玩家方块，拒绝。
  - 提示：`该位置已有非自然方块（可能为玩家建筑）：x,y,z=material …。如确认覆盖请加 --force`。
- 地基间层中的方块若不在白名单（水另按 `fill-water` 规则）→ 拒绝。
- 地面方块若不在白名单 → 拒绝。
  - 原因：地表放置的方块（如玩家放的钻石块）会被地形扫描当作"地面"，若不单独检查，建筑会直接盖在它上面而不是被拦截。
- 统计被占用方块，最多报告前 5 个坐标；坐标后带 `ground:` 前缀的表示地面方块。

### 8.3 区域保护（`--force` 跳过）

- `respect-regions: true` 时，若足迹覆盖的任一区块已在 `regions.db` 中被标注 → 拒绝，提示"该位置属于已标注区域：<区域显示名>"。
- 目的：城市已建成区不能被建筑生成意外覆盖；新区扩展在未标注区块进行。

### 8.4 其他保护（可选，v1 不实现）

- WorldGuard 区域、领地插件：v1 不集成；如需可在 `PlacementService` 前置钩子中扩展。

---

## 9. 放置队列（分 tick）

### 9.1 结构

- `PlacementQueue`：全局单例，`BukkitRunnable` 每 tick 执行。
- 每 tick 从队首任务取最多 `blocks-per-tick` 个方块放置（全局预算，所有玩家共享）。
- 任务 `PlacementJob`：`world`、`List<BlockChange>`、进度、所属玩家、状态、撤销记录。

### 9.2 执行

```
每 tick:
  budget = blocks-per-tick
  while (budget > 0 && queue 非空):
      change = job.next()
      old = block.getBlockData()          // 记录用于撤销
      block.setBlockData(change.data, false)   // 不触发物理
      job.undo.add(change.xyz, old)
      budget--
      if job.done: 完成通知、写入玩家撤销历史
```

- 使用 `setBlockData(data, false)`：不触发邻接更新，避免红石/水流连锁与卡顿；光照由服务器异步处理。
- 任务完成后按 `undo-history` 截断玩家历史（保留最近 N 次）。

### 9.3 进度与反馈

- 每 10 tick 向任务所属玩家发送 ActionBar：`建造中 45%（1,234 / 2,700 方块）`。
- 完成：`§a建造完成：house_small × 1，2,700 方块，用时 3.2s`。
- 被取消：`§e建造已取消（已完成 60%）`。
- BossBar 只用于区域显示，建造进度用 ActionBar，避免冲突。

### 9.4 限制

- 每玩家同时只能有 1 个进行中任务；已有任务时新命令提示"请等待当前任务完成或 /city build cancel"。
- 服务器关闭时队列直接丢弃（未放完的部分保留，已放部分可通过 undo 或手动处理）；文档注明。
- 队列任务总方块数超过 `max-queue-blocks` 拒绝。

---

## 10. `/buildat` 流程

1. 解析模板、旋转、`--force`、`--preview`。
2. 目标点：玩家准星 6 格内命中方块；未命中则取脚下方块（`player.getLocation().getBlock().getRelative(DOWN)`）。
3. 计算足迹：以目标方块 X/Z 为中心，尺寸取旋转后模板 `w × d`（中心向下取整，保证整数对齐）。
4. 依次执行 §7 地形扫描、§8 检查；失败输出原因。
5. `--preview`：输出规划摘要并结束（不放置）。
6. 生成 `PlacementJob` 入队，返回"已加入建造队列（N 方块）"。

---

## 11. `/buildzone` 流程

### 11.1 区域来源

- 若 WorldEdit 已定义选区（CuboidRegion）：取选区 X/Z 范围（忽略 Y）。
- 否则：以玩家位置为中心、`--radius`（默认 16）的方形区域。
- 校验面积 ≤ `zone.max-area`，否则拒绝并提示。

### 11.2 网格与建筑选择

- 选定的模板集合：
  - 具体模板名 → 只用该模板；
  - `random` → 目录下全部模板随机；
  - `<类别>` → 该类别全部模板随机。
- 单元格尺寸 = `max(候选模板旋转后 w) + spacing` × `max(候选模板旋转后 d) + spacing`。
- 网格原点 = 区域最小角向内偏移 `spacing/2`，按单元格尺寸铺满区域；每个单元格建筑居中放置。
- 每格独立执行 §7/§8 检查；失败计入跳过统计，不影响其他格。
- 预期建筑数超过 `zone.max-buildings` → 拒绝，提示减小区域或加大间距。

### 11.3 旋转

- `--rotation 0|90|180|270`：全部同一朝向。
- `--rotation auto`：`(cellX * 31 + cellZ * 17) % 4` 选方向，保证同一位置结果稳定、整体有变化。
- 默认 `0`。

### 11.4 结果

- 全部格子的方块合并为**一个** `PlacementJob` 入队。
- 完成后输出：`街区生成完成：成功 12 格，跳过 3 格（地形过陡 1、被占用 2），共 34,560 方块`。

---

## 12. 撤销

- 每次建造（`buildat` / `buildzone`）完成后，把执行时记录的 `(位置, 旧 BlockData)` 逆序保存到该玩家的撤销历史。
- `/city build undo`：取最近一次记录，逆序恢复旧方块（`setBlockData(old, false)`），随后删除该记录。
- 历史条数 `undo-history`（默认 5）；超出丢弃最旧。
- 只保存在内存，服务器重启后清空（文档注明）。
- 撤销自身也受 `city.build.undo` 权限与玩家在线状态约束；控制台不可用。

---

## 13. 边界情况与错误处理

| 情况 | 处理 |
| --- | --- |
| WorldEdit 未安装 | 插件不会启用（plugin.yml 声明 `depend: [WorldEdit]`），需先安装 WorldEdit 7.4+。 |
| `buildings/` 为空 | `/city build at` 提示"没有可用模板，请将 .schem 放入 plugins/CityPlugin/buildings/<类别>/"。 |
| 模板读取失败 | 跳过并警告，不影响其他模板。 |
| 目标区块未加载 | 同步加载（受 `zone.max-area` 限制），生成过程中不卸载。 |
| 水面上生成 | 默认拒绝；`fill-water: true` 允许。 |
| 地形过陡 | 拒绝并给出实际地基深度与上限。 |
| 玩家中途退出 | 任务继续执行（按 UUID 通知），完成后不通知离线玩家。 |
| 服务器关闭 | 任务丢弃，已放置方块保留。 |
| 重复执行 | 每玩家单任务限制。 |
| 命令参数错误 | 输出用法提示，不抛异常。 |

---

## 14. 类结构（实现）

```
com.xiling.city
├── CityPlugin                     插件入口：初始化 DB、BossBar、指令、监听器、迁移
├── config
│   └── CityConfig                 配置读取（builder、region 各节，标签/material 解析）
├── commands
│   ├── CityCommand                主分发：region 子命令 / build 子命令 / 帮助
│   ├── RegionCommand              区域子命令实现（原逻辑）
│   ├── RegionTabCompleter         区域补全
│   ├── BuildCommand               build 子命令实现（at/zone/list/reload/status/cancel/undo）
│   └── BuildTabCompleter          build 补全（模板名、类别、参数）
├── database
│   └── DatabaseManager            SQLite（regions/chunks），迁移逻辑
├── listeners
│   ├── ChunkListener              区块切换刷新 BossBar
│   ├── PlayerListener             登录/退出
│   └── VehicleListener            载具内刷新
├── managers
│   └── BossBarManager             区域 BossBar 显示
├── models
│   └── RegionModel                行政区模型
└── builder
    ├── BuildingTemplate           模板（方块数组、尺寸、名称、类别，含旋转缓存）
    ├── BuildingRegistry           扫描/加载/重载模板，按名称/类别查询
    ├── TemplateRotation           0/90/180/270 与 StructureRotation 映射
    ├── TerrainProfile             足迹每柱地面高度、基准面、地基深度、水域标记
    ├── TerrainScanner             地面扫描（高度图起步向下找地面）
    ├── PlacementValidator         占位检查（体积/夹层/地面）、区域保护、高度/边界校验
    ├── PlacementPlanner           单栋规划：地形扫描 + 校验 + 地基/建筑方块列表
    ├── ZonePlanner                街区网格规划：单元格、间距、随机模板/朝向、跳过统计
    ├── PlacementPlan              单栋规划结果（方块列表、基准面、足迹）
    ├── BuildException             生成失败（消息 + 短原因，用于统计）
    ├── BlockChange                单方块变更（坐标 + 新 BlockData）
    ├── UndoEntry                  单方块旧状态（用于撤销）
    ├── PlacementJob               单个建造任务（方块列表、进度、撤销记录）
    ├── PlacementQueue             全局分 tick 队列
    └── BuildUndoManager           按玩家撤销历史
```

**线程模型**：所有 Bukkit 世界读写均在主线程（命令执行与队列 tick 任务）；数据库为同步 SQLite（沿用原实现）。

**数据库表不变**：`regions(id, province, city, district, village, created_at)`、`chunks(world, x, z, region_id)`，无需迁移表结构。

---

## 15. 验收标准

1. `gradle build` 通过，产出 `CityPlugin-2.0.0.jar`。
2. 放入服务器后 `/city info`、`/region list`、BossBar 显示与旧版一致。
3. 首次启动自动复制旧 `regions.db`（存在时），旧数据可见。
4. `/city build list` 能列出 `buildings/` 下模板及尺寸。
5. `/buildat house_small 90` 在起伏地形上：地基填到最低地面、建筑底面高于最高地面、不悬空不埋地。
6. `/city build zone random --radius 20 --spacing 7`：按网格放置、建筑间有道路空隙、跳过非法格并统计。
7. 对已标注区域或已有建筑执行生成：被拒绝并提示坐标；`--force` 可通过。
8. 大批量生成时主线程无明显卡顿（`blocks-per-tick` 生效，TPS 稳定）。
9. `/city build undo` 能完整恢复上次建造前的方块状态。
10. 断线/重启/取消任务不产生异常报错。

**实测结果（Paper 1.21.11-132 + WorldEdit 7.4.6）**

- 插件启用、配置生成、`.schem` 模板扫描与 `/city build reload` 通过。
- 7×3×5 / 90 方块模板加载正确；90° 旋转后尺寸交换为 5×3×7，方块数不变。
- 坡地（地基深度 2）生成后，地基按配置填充为 `stone_bricks`。
- 占位检查能拦截玩家放置的方块（包括被地形扫描误判为"地面"的地表方块）。
- 区域保护能拦截已标注区块，`--force` 可覆盖。
- 街区网格成功 3/4 格、共 286 方块，跳过原因统计正确。
- 分 tick 队列完成放置；`undo` 逆序恢复原方块。
- 测试用临时服务器与测试插件未包含在交付物中。

---

## 16. 后续可扩展（不在 v1 范围）

- 世界守卫/领地插件保护集成。
- 模板侧车配置（`<name>.yml`：自定义地基、锚点、可生成条件）。
- 道路自动连接（按网格朝向生成道路模板）。
- 建筑内部随机变体（同模板多个版本）。
- 异步地形预扫描（避免大区域主线程扫描）。
- 撤销持久化（写入 SQLite）。
