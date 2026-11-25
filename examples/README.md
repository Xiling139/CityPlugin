# 示例建筑模板（CityPlugin）

这里是一组可直接使用的 WorldEdit `.schem` 模板，用于测试 CityPlugin 的生成功能。
模板本身是简单演示建筑，重点用于验证：地形贴合、地基填充、旋转、街区块状生成、撤销。

## 1. 目录内容

```
examples/buildings/
├── residential/
│   ├── house_small.schem     7×4×6   126 方块   单层小住宅
│   └── house_medium.schem    9×7×9   369 方块   两层住宅
├── commercial/
│   └── shop_1f.schem         11×4×8  242 方块   单层临街商铺（玻璃门面）
├── road/
│   └── street_lamp.schem     1×4×1   4 方块     路灯（可用 --spacing 密集排布）
└── park/
    └── plaza.schem           9×1×9   81 方块    小广场地面
```

模板名包含类别前缀，例如 `residential/house_small`。

## 2. 安装模板

把上面的目录整体复制到服务器的插件数据目录：

```
<服务器根目录>/plugins/CityPlugin/buildings/
├── residential/
├── commercial/
├── road/
└── park/
```

然后执行：

```
/city build reload
/city build list
```

`/city build list` 会显示每个模板的尺寸与方块数。

## 3. 测试流程（5 分钟）

1. 找一块**平坦的草地/泥土地**，面朝想放置的位置。
2. 先用预览确认地形与占地：

```
/city build at residential/house_small --preview
```

输出会显示占地范围、基准面高度、地基深度、方块总数。

3. 正式生成（准星 6 格内命中方块；没命中时用脚下方块）：

```
/city build at residential/house_small
```

4. 旋转 90° 再放一栋：

```
/city build at residential/house_small 90
```

5. 批量生成街区（以玩家为中心半径 30，建筑间距 7）：

```
/city build zone residential --radius 30 --spacing 7
```

6. 查看进度 / 撤销：

```
/city build status
/city build undo
```

7. 已标注为行政区的区块默认会被拦截，确认要覆盖时：

```
/city build at residential/house_small --force
```

## 4. 短命令

```
/buildat residential/house_small 90
/buildzone residential --radius 20 --spacing 5
```

## 5. 制作自己的模板

在游戏里用 WorldEdit 选中建筑（**不要选得太大多余的空气**）：

```
//wand                 （或 //pos1 / //pos2 手动选两点）
//copy
//schem save house_small
```

文件会保存到 `plugins/WorldEdit/schematics/house_small.schem`，把它移动到：

```
plugins/CityPlugin/buildings/<类别>/house_small.schem
```

然后 `/city build reload`。插件会自动按方块包围盒计算尺寸，不需要手动对齐坐标；
建筑的水平锚点是包围盒中心，垂直锚点是模板最低层。

## 6. 注意事项

- 需要服务器安装 **WorldEdit 7.4+**（CityPlugin 已声明依赖）。
- 模板中的**实体**（画、物品展示框、盔甲架等）不会被复制，只复制方块。
- 模板体积超过 `builder.max-template-blocks`（默认 200000）会被拒绝加载。
- 水面上默认不能生成；需要时在 `config.yml` 开启 `builder.fill-water: true`。
- 地形过陡（地基深度超过 `builder.max-foundation-depth`，默认 32）会被拒绝。
- 撤销历史只保存在内存中，服务器重启后清空；默认保留最近 5 次。
- 修改 `config.yml` 需要重启服务器；`/city build reload` 只重新扫描模板目录。
