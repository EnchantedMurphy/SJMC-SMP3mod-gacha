# Gacha — Fabric 服务端抽奖模组

## 功能与特点

Gacha 为 Minecraft 服务器提供可配置的投券抽奖玩法。玩家把抽奖券投进指定区域，就能观看抽奖动画并获得奖励，适合活动奖池、商店抽奖和限定物品发放。

- **客户端无需安装模组**：使用原版物品和展示效果，可搭配服务端资源包为单抽券和十连券设置独立贴图。
- **多个独立奖池**：抽奖券通用，各池的奖励、概率、个人历史和保底独立。
- **单抽与十连**：支持 S／A／B 三个等级、S 保底和十连末抽 A 级及以上保障。
- **灵活配奖**：支持物品、后台指令、固定额外奖励，以及每名玩家独立的奖品获取次数限制。
- **可见的抽奖体验**：三色方块动画、音乐、烟花和 S 级物品旋转轮播；出 S 时个人大字幕与音效强调、全服播报，历史可翻页查看。
- **奖励记录可追踪**：断线后可继续领取，异常奖励可由管理员核查处理。

适用于 **Minecraft Java 26.3 · Fabric**，当前模组版本 **2.3.2**。各版本的改动与更新注意事项见 [版本更新记录](CHANGELOG.md)。

## 快速上手

### 普通玩家

从管理员或商店获得抽奖券后，**将券投掷到抽奖池区域内**，等待约 5 秒领取奖励。单抽券抽 1 次，十连券抽 10 次；所有奖池使用同样的券。

开始抽奖提示显示在快捷栏上方的 actionbar，默认持续到约 5 秒后的结果揭晓。抽到 S 级时，中奖玩家会看到红色大字幕并听到专属音效，全服聊天栏同时播报中奖玩家、奖池和 S 级奖品。

整叠投入只使用 1 张，其余退回；奖池或玩家正在抽奖时，新投入的券也会退回。

服务器提供资源包时，接受下载即可看到专属抽奖券贴图；未加载时显示为原版纸，使用方式相同。

| 想做什么 | 操作或指令 |
| --- | --- |
| 查看帮助 | `/gacha help` |
| 查看可用奖池 | `/gacha pool list` |
| 查看自己的历史 | `/gacha history main`，可点击翻页或切换详细历史 |
| 查看剩余保底 | `/gacha pity main` |
| 查看待领或异常奖励 | `/gacha pending main` |

`main` 是示例池名，换成实际想查询的奖池名称。历史和保底只显示该玩家在所选池中的记录。

### OP 常用操作

**先准备服务器：** 安装 Fabric 26.3、Java 25 和匹配的 Fabric API，把 `gacha-26.3-fabric-2.3.2.jar` 放入服务器 `mods` 目录后启动。

默认奖励中含 Fuji 货币指令，需要安装匹配的 Fuji、启用 `economy`，并配置 `fuji:coffee_stamp` 和 `fuji:tongbao`。不使用 Fuji 时，先将这些奖励改成物品或其他可执行指令，再开放抽奖。

**开一个奖池：** 在目标维度执行，替换坐标和玩家名：

```text
/gacha pool set main 100 64 100 103 66 103
/gacha check main
/gacha give Steve single 5
/gacha give Steve ten 2
```

两个坐标确定投券区域，包含两端的方块。玩家向该区域投券即可使用；要创建其他奖池，换一个池名再执行 `pool set`。

以下常用操作需要 **OP 2**；重载配置需要 **OP 4**。

| 想做什么 | 操作或指令 |
| --- | --- |
| 添加或替换随机物品 | 主手拿好奖品，执行 `/gacha reward sethand main S mace 1 -1`；权重为 1，每人不限次数 |
| 设置每人仅可获得一次 | `/gacha reward limit main S mace 1` |
| 添加固定额外物品 | 主手拿好物品，执行 `/gacha bonus sethand main A extra` |
| 为 S 级指令奖品设置旋转展示 | 先创建奖品，再主手拿好模型物品执行 `/gacha reward setdisplay main S money`，`money` 为奖品 ID |
| 清除独立展示物品 | `/gacha reward cleardisplay main S money` |
| 查看奖品 | `/gacha reward list main`；固定额外奖励用 `/gacha bonus list main` |
| 调整展示朝向 | `/gacha pool facing main +z`，也可填 `-z`、`+x`、`-x` |
| 删除奖池 | `/gacha pool remove main` |
| 应用配置文件修改 | `/gacha reload`（OP 4） |

`sethand` 保存主手整叠物品及名称、附魔等完整信息，不消耗物品。等级填写大写 `S`、`A` 或 `B`；同一奖励表、同等级同 ID 会替换原奖品。配置中的物品数量表示一次发多少，个人限额表示最多抽中多少次。

### 后台关键配置

配置文件位于 **服务器的 `config/gacha/config.json`**。先用指令创建池，再修改 **`pools.<池名>`** 下的对应字段，保存后执行 `/gacha reload`。

| 常用字段 | 作用 | 示例 |
| --- | --- | --- |
| 池 `sProbability`、`aProbability` | S、A 的绝对概率；B 为剩余概率 | `0.02`、`0.10`：S 2%、A 10%、B 88% |
| 池 `pity` | 连续未获 S 时的必得 S 抽数 | `80`：第 80 抽必得 S |
| 池 `rewards.S/A/B` | 各等级的随机奖品表 | 同等级按 `weight` 随机选一项 |
| 池 `fixedRewards.S/A/B` | 各等级的固定额外奖励表 | 抽中等级时，表内奖励全部发放 |
| 随机奖品 `limit` | 每名玩家可抽中该奖品的次数 | `-1` 无限、`1` 每人一次、`0` 禁用 |
| 池 `displayOffsetX/Y/Z` | 相对默认位置的展示偏移 | Y 填 `0.5`，整体向上移动半格 |
| 池 `fireworkDistance` | 每侧烟花距展示中心的距离 | `5.0`：左右各距中心 5 格 |
| 顶层 `animationTicks`、`resultTicks` | 抽奖动画与结果保留时长 | `100`、`60`：约 5 秒、3 秒 |
| 顶层 `actionbarTicks` | 开始抽奖提示持续时间，0 为关闭，最晚在揭晓时结束 | 默认 `100`：约 5 秒 |
| S 随机奖品 `displayItem` | 独立旋转展示物品；支持指令奖品，仅展示不发放 | 物品 JSON，可用 `reward setdisplay` 保存 |

**`poolDefaults` 只用于创建新池时复制设置。** 修改已有池请改 `pools.<池名>`；修改模板不会改变已有池。完整结构可参考 [示例配置](example-config.json)，字段边界和奖励写法见下文。

**可选抽奖券材质：** 项目附带 `smp3-resourcepack-3.0-alpha-1` 资源包。先替换其中两张透明占位贴图，再打包上传，在服务器 `server.properties` 中设置 `resource-pack` 与 `resource-pack-sha1`，详见下文“抽奖券资源包”。这些字段属于原版服务器配置，不写入 Gacha 配置。

## 进阶配置与完整指令

### 抽奖券资源包

资源包源文件位于 [resourcepacks/smp3-resourcepack-3.0-alpha-1](resourcepacks/smp3-resourcepack-3.0-alpha-1/README.md)，适配 Minecraft Java **26.3**，资源包格式 **97.1**。模组仍只需安装在服务端，玩家通过原版客户端下载资源包即可使用贴图。

**先填写两张贴图：** 当前均为合法的 **32×32 全透明 PNG 占位图**，可替换为 32×32、64×64 等正方形 PNG，保留以下路径和文件名：

| 类型 | 相对资源包根目录的贴图路径 |
| --- | --- |
| 单抽券 | `assets/gacha/textures/item/ticket_single.png` |
| 十连券 | `assets/gacha/textures/item/ticket_ten.png` |

加载尚未填写贴图的资源包后，券图案会不可见，名称、说明和投券功能不变。正式开放前请先替换贴图。

**打包与配置：** 在项目根目录执行 `gradlew.bat resourcePackSha1`（完整 `build` 也会生成），得到：

```text
build/resourcepacks/smp3-resourcepack-3.0-alpha-1.zip
build/resourcepacks/smp3-resourcepack-3.0-alpha-1.zip.sha1
```

将 ZIP 上传到玩家可直接下载的 HTTP／HTTPS 地址。编辑服务端 `server.properties`，例如：

```properties
resource-pack=https://example.com/smp3-resourcepack-3.0-alpha-1.zip
resource-pack-sha1=<替换为生成的 .zip.sha1 文件中的 40 位校验值>
require-resource-pack=false
```

示例域名和校验值需换成自己的实际内容，不保留尖括号。`require-resource-pack=false` 允许玩家拒绝资源包；设为 `true` 则必须接受才能游玩。ZIP 根目录应直接包含 `pack.mcmeta` 和 `assets/`，不要再套一层文件夹。资源包不放入 `mods` 或世界的 `datapacks`。

重启服务器并让玩家重新连接以应用设置，`/gacha reload` 只重载 Gacha 配置。每次替换贴图后，都重新打包、上传 ZIP 并更新 SHA-1；校验值针对整个 ZIP，而非某一张图片。需要手动压缩时，也请重新计算最终 ZIP 的 SHA-1。

**新旧券与商店：** 2.3.0 起新发放的券自动携带材质标识。旧券仍能抽奖，但显示原版纸；需要新外观时重新发券。商店复制券时同时保留 `minecraft:custom_data` 和 `minecraft:custom_model_data`；后者仅控制外观，单独添加它不能生成有效券。未加载此包时，新券也显示原版纸。

**与已有资源包合并：** 此包使用 `assets/minecraft/items/paper.json` 按 `minecraft:custom_model_data` 的 `strings[0]` 选择模型；单抽标识为 `gacha:ticket_single`，十连为 `gacha:ticket_ten`，普通纸回退到原版模型。如果服务器已有资源包，将 `assets/gacha/` 合入原包，并合并纸模型中的选择分支与回退规则，避免覆盖其他纸类道具的模型；最终发布一个 ZIP，并使用其新的 SHA-1。

### 抽奖规则、固定奖励与限额

每名玩家按 UUID、池名分别保存历史、连续未获 S 的次数和随机奖品获取次数。单抽和十连在同一池内共用计数，换池不会继承保底。

新池使用内置模板时的默认设置：

| 项目 | 默认内容 |
| --- | --- |
| S／A／B 概率 | 2%／10%／88% |
| S 保底 | 连续 79 抽未获 S 时，第 80 抽必得 S |
| S 随机奖励 | 重锤 ×1，不限次数 |
| S 固定额外奖励 | 咖啡印章余额 +20 |
| A 随机奖励 | 钻石、金锭、铁锭 ×1，等权随机一项 |
| A 固定额外奖励 | 无 |
| B 随机奖励 | 无 |
| B 固定额外奖励 | 铁镐通宝余额 +20 |

十连第十抽始终为 A 或 S。先判断 S 保底和自然 S；末抽原本为 B 时改为 A。任何 S 都重置本池保底，包括随机奖品已经耗尽的 S。

每次确定等级后，从该玩家仍可获得的随机奖品中按权重选择一项，再发放该等级的**全部固定额外奖励**。十连内逐抽扣减限额，耗尽的奖品立即退出该玩家的随机表。

随机表为空或全部耗尽时，只发固定额外奖励；固定表也为空时，结果显示“无奖励”，仍记录等级、历史和保底。固定额外奖励不受随机奖品的权重或个人限额影响。

同池同等级的奖品 ID 用于追踪个人获取次数。删除后再加入相同 ID，已有次数保留；删除后重新使用相同池名，已有个人计数也保留。限额按已确定的抽奖结果计数，重试发奖不会再次扣减。

### 奖励的 JSON 写法

一个随机奖品可以包含多个同时发放的子奖励。例如：

```json
{
  "id": "mace",
  "label": "重锤 ×1",
  "weight": 1,
  "limit": 1,
  "rewards": [
    {"item": {"id": "minecraft:mace", "count": 1}}
  ]
}
```

把它放入相应池的 `rewards.S`，表示该奖品每名玩家最多获得一次。保存附魔、名称、潜影盒内容或其他模组物品时，优先使用 `reward sethand` 捕获完整物品。

固定额外奖励放入 `fixedRewards.S/A/B`，例如：

```json
{
  "id": "stamps",
  "label": "咖啡印章 ×20",
  "rewards": [
    {"command": "economy give %player% fuji:coffee_stamp 20"}
  ]
}
```

每个子奖励仅包含 `item` 或 `command` 之一。指令不带开头的 `/`，支持 `%player%` 和 `%uuid%`；以后台身份在中奖玩家的位置和维度执行。`@s` 仍为后台来源，要指定中奖玩家请使用占位符。

Fuji 货币标识属于经济账户 ID。发实体货币道具时，用 `sethand` 保存实际道具；增加账户余额时，使用对应经济指令。

### S 级指令奖品的旋转展示

在 `pools.<池名>.rewards.S` 中，每个随机奖品可以单独增加 `displayItem`，与 `id`、`label`、`rewards` 同级。例如，通过指令发放咖啡印章余额，同时在池上方展示一张纸：

```json
{
  "id": "money",
  "label": "咖啡印章 ×20",
  "weight": 1,
  "limit": -1,
  "displayItem": {"id": "minecraft:paper", "count": 1},
  "rewards": [
    {"command": "economy give %player% fuji:coffee_stamp 20"}
  ]
}
```

`displayItem` 使用原版物品 JSON 格式，支持名称、附魔和材质等完整组件。推荐主手拿好模型物品，执行 `/gacha reward setdisplay main S money` 自动保存；此指令需要 OP 2，不消耗物品。实际奖励由 `rewards` 决定，展示物品不会额外发给玩家。

设置后，该奖品只轮播指定的展示物品，数量固定显示为 1；不再从这个奖品的子奖励中提取物品。未设置时，继续轮播子奖励中的实际物品；纯指令奖品没有指定展示物品则不展示。`displayItem` 仅允许用于 S 级随机奖品，固定奖励和 A／B 级不使用此字段；`limit: 0` 的奖品仍不参与轮播。

执行 `/gacha reward cleardisplay main S money` 或移除该字段，可恢复自动提取实际物品。`reward list` 会标出设置了独立展示的奖品，`/gacha check main` 同时校验展示物品。手动编辑配置后使用 `/gacha reload`；用 `sethand`／`setcommand` 替换整个奖品后，需要重新设置展示物品。`poolDefaults.rewards.S` 中的设置仍只作为新池模板。

### actionbar 持续时间

在配置顶层设置：

```json
{
  "animationTicks": 100,
  "resultTicks": 60,
  "actionbarTicks": 100
}
```

以上为默认值，每秒约 20 tick。`actionbarTicks` 控制“开始抽奖，请等待结果……”的持续时间，范围 0–1200，0 为关闭。显示期间每 20 tick 刷新一次，避免原版提示提前淡出；达到设置时长时主动清除。如果配置时长超过本轮动画时长，则在结果揭晓时提前清除，避免中奖后继续显示“等待结果”。

保存后执行 `/gacha reload`，对下一轮生效。每轮开始时保存时长快照，修改配置不会改变进行中的提示。`resultTicks` 控制混凝土结果展示与恢复空闲轮播的间隔，默认 60 tick（3 秒），与 actionbar 时长分开设置。

### 字段范围与展示位置

| 字段 | 规则 |
| --- | --- |
| 顶层 `schemaVersion` | 当前为 `2` |
| 顶层 `animationTicks` | 20–1200 tick，默认 100 |
| 顶层 `resultTicks` | 0–1200 tick，默认 60；烟花会保留完成效果所需的几 tick |
| 顶层 `actionbarTicks` | 0–1200 tick，默认 100；0 关闭，最多显示至本轮揭晓 |
| 池 `dimension`、区域坐标 | 在目标维度设置；单边最多 65 格，同维度投券区域不可重叠 |
| 池 `sProbability`、`aProbability` | 非负有限数值，总和不超过 1 |
| 池 `pity` | 1–1000000 |
| 池 `displayFacing` | `+x`、`-x`、`+z`、`-z`，默认 `+z` |
| 池 `displayOffsetX/Y/Z` | 默认 0，范围 -64 至 64 格，可为小数 |
| 池 `fireworkDistance` | 默认 3.2，范围 0–64 格，可为小数 |
| 池名 | 1–32 位字母、数字、下划线或短横线 |
| 奖品 `id` | 同表同等级内唯一，1–64 位字母、数字、下划线或短横线 |
| 奖品 `label` | 非空显示名称，最多 256 字符 |
| 随机奖品 `weight` | 正整数，默认 1；同等级按剩余可抽奖品的权重计算 |
| 随机奖品 `limit` | `-1` 或非负整数，默认 `-1` |
| S 随机奖品 `displayItem` | 可省略；非空物品 JSON，只影响空闲轮播，优先于实际子奖励物品 |
| `rewards.S/A/B`、`fixedRewards.S/A/B` | 都需提供三个等级的数组，可为空 |
| 单个奖品的 `rewards` | 非空子奖励数组 |

展示默认以区域水平中心为中心，S 级物品轮播和单抽位于区域最高 Y 坐标上方 2.5 格；十连下排上方 2 格，上排上方 2.9 格。十连从正面看按上排左到右、下排左到右排列。

`displayOffsetX/Y/Z` 沿世界坐标轴叠加，同时移动轮播、混凝土和烟花，不改变投券区域。烟花沿展示的左右方向生成，`fireworkDistance` 为每侧到中心的距离，两侧间距为其两倍。

例如，在已有池对象中合并下面的字段，可整体向东移动 1 格、向上移动半格，并让烟花左右各距中心 5 格：

```json
{
  "displayOffsetX": 1.0,
  "displayOffsetY": 0.5,
  "displayOffsetZ": 0.0,
  "fireworkDistance": 5.0
}
```

这些设置也可写入 `poolDefaults`，作为之后创建的新池模板。重新执行已有池的 `pool set` 会保留它的奖励和展示配置。

### 完整指令参考

`<池名>`、`<ID>` 等为必填参数，方括号表示可省略，`<等级>` 填大写 `S`、`A` 或 `B`。发券、帮助和重载为全局操作；奖池查询、奖品管理和领取恢复均指定池名。

普通玩家：

| 指令 | 功能 |
| --- | --- |
| `/gacha`、`/gacha help [页码]` | 分类帮助，管理页按权限开放 |
| `/gacha pool list` | 查看池名、区域、维度和朝向 |
| `/gacha history <池名> [页码]` | 方块历史 |
| `/gacha history <池名> details [页码]` | 详细历史 |
| `/gacha pity <池名>` | 个人保底和累计抽数 |
| `/gacha pending <池名>` | 待领与异常动作 |

OP 2 或后台：

| 指令 | 功能 |
| --- | --- |
| `/gacha give <玩家或选择器> single或ten [数量]` | 发券，数量默认 1，范围 1–2304 |
| `/gacha pool set <池名> <坐标1> <坐标2>` | 创建池或更新已有区域，支持相对坐标 |
| `/gacha pool facing <池名> +x或-x或+z或-z` | 调整朝向 |
| `/gacha pool remove <池名>` | 删除池 |
| `/gacha reward list <池名>` | 查看随机奖励 |
| `/gacha reward sethand <池名> <等级> <ID> [权重] [限额]` | 保存主手随机奖品；默认权重 1、限额 -1 |
| `/gacha reward limit <池名> <等级> <ID> <次数>` | 修改个人限额，已有次数保留 |
| `/gacha reward setdisplay <池名> S <ID>` | 将主手完整物品保存为该随机奖品的独立展示；需游戏内执行 |
| `/gacha reward cleardisplay <池名> S <ID>` | 清除独立展示，恢复自动提取实际物品；可从后台执行 |
| `/gacha reward remove <池名> <等级> <ID>` | 删除随机奖品 |
| `/gacha bonus list <池名>` | 查看固定额外奖励 |
| `/gacha bonus sethand <池名> <等级> <ID>` | 保存主手固定额外物品 |
| `/gacha bonus remove <池名> <等级> <ID>` | 删除固定额外奖励 |
| `/gacha check <池名> [在线玩家]` | 验证物品和指令；后台需指定玩家 |
| `/gacha pending <池名> <在线玩家>` | 查看指定玩家的领取记录 |

OP 4：

```text
/gacha reward setcommand <池名> <等级> <ID> <名称> <完整奖励指令>
/gacha bonus setcommand <池名> <等级> <ID> <名称> <完整奖励指令>
/gacha reload
/gacha recover <池名> <在线玩家> <记录UUID> <动作序号> retry|done
```

`setcommand` 需在游戏内执行以验证玩家上下文，名称有空格时加双引号。随机指令奖品的限额用 `reward limit` 设置。正在抽奖的池需等本轮揭晓后再用指令修改。

后台指定维度设置奖池的示例：

```text
execute in minecraft:overworld run gacha pool set main 100 64 100 103 66 103
```

### 动画、历史与投券细节

- 空闲时轮播 S 随机奖品的 `displayItem`，未设置则提取其实际子奖励物品，缩放 0.8，每 12 秒旋转一圈，每 4 秒切换一件。固定额外奖励、未指定展示的纯指令奖品和 `limit: 0` 的奖品不参与轮播。
- 轮播面向所有玩家，不随某名玩家的限额耗尽而消失；所在展示区块未加载时不生成，也不会强制加载区块。抽奖和结果展示期间隐藏，清理后恢复。
- 混凝土随机闪色为蓝 50%、黄 30%、红 20%；实际中奖概率使用奖池配置。揭晓时 S／A／B 分别显示红／黄／蓝，音乐和两侧烟花配合揭晓；烟花不造成伤害。
- 开始抽奖只向投券者发送 actionbar 提示。S 级结果揭晓时，全服聊天播报玩家名、奖池和奖品；十连中的多个 S 合并为一条，相同奖励合并显示次数。在线中奖者另有红色加粗大字幕（淡入 0.5 秒、停留 3 秒、淡出 1 秒）和原版挑战完成音效，其他玩家不会收到该字幕或专属音效。
- 中奖者在揭晓时离线，仍会正常全服播报，奖励继续暂存；重连领取、异常重试和补发不会重复播报或补播字幕音效。A／B 结果不触发 S 级提示，原有个人聊天抽奖结果保留。
- 同一玩家、同一池同时只进行一轮；不同玩家可在不同池同时抽奖。揭晓后可开始下一轮，新动画会替换尚未清除的旧结果。
- 只有玩家主动投掷的有效券触发抽奖。普通纸、仅改名的纸、投掷器物品、死亡掉落和发奖溢出掉落均不触发；券实体不互相合并。
- 背包放不下的奖励在玩家身边掉落，设置为该玩家的拾取目标。

方块历史按时间从左到右、从下到上由早到晚排列，每行最多 20 抽；出红后显示本次出红抽数并换行。每页约 100 抽，保留完整行，最多多出 19 抽。第 1 页为最近记录，后续页更早。

详细历史每页 10 抽、最新在前，显示奖品、北京时间及距上次 S 的逐抽计数。两种视图可翻页和互相切换，始终保留当前池名；每名玩家只能查询自己的历史。

## 技术与维护

### 安装、版本与数据目录

环境要求：Minecraft Java **26.3**、**Java 25**、Fabric Loader **0.19.5 或更新**、匹配的 Fabric API。当前构建使用 Fabric API `0.161.0+26.3`、Loom `1.17.21`、Gradle `9.7.1`；客户端无需安装本模组。

服务器目录：

```text
config/gacha/
├── config.json
└── players/
    └── <玩家UUID>.json
```

首次启动自动生成配置。常规更新停服替换旧 gacha JAR，保留有效的配置和玩家数据；安装包使用主 JAR，`-sources.jar` 为开发源码包。

当前配置和玩家数据格式均为版本 2。由旧版本升级时，按 [版本更新记录](CHANGELOG.md) 的对应说明处理配置与目录：旧格式配置不迁移，旧 `players-v2/` 不自动读取；若当前 `players/` 中已有旧格式文件，先备份并移出该目录。有效的新格式配置无需在每次更新时覆盖。

配置重载失败会保留当前生效配置。已开始的抽奖继续使用事先确定并保存的结果、时长和奖励快照。

### 发奖记录与异常恢复

开始抽奖时先保存本轮结果、限额、保底、历史和领取动作，再消费投券实体；实体 UUID 用于防止同一投掷物重复处理。断线后奖励保留，重连继续领取；死亡期间暂存，复活后发放。正常停服会在玩家断开前完成可发动作，并清理展示实体。

| 动作状态 | 含义 |
| --- | --- |
| `READY` | 等待发放 |
| `SENDING` | 已开始发放，结果尚未保存完成 |
| `DONE` | 已发放完成 |
| `FAILED` | 执行失败或未取得可靠的成功确认，需核查 |

遇到 `FAILED` 或遗留的 `SENDING`，先核对实际物品或余额，再处理对应动作：

```text
/gacha recover main Steve <记录UUID> <动作序号> done
/gacha recover main Steve <记录UUID> <动作序号> retry
```

`done` 用于确认已发放，`retry` 用于确认未发放。恢复时校验记录属于指定池。已兼容 Fuji advice／cooldown 对成功回调的替换；更新不会自动重试此前的异常记录，避免重复发奖。

玩家文件损坏时停止该玩家抽奖和发奖，保留原文件。备份时同时保存 `config/gacha`、世界玩家数据和 Fuji 经济数据；背包与外部经济系统的保存无法作为一个跨系统事务提交。

### 接入商店

购买成功后，让商店以后台身份执行：

```text
gacha give <商店的玩家名占位符> single 1
gacha give <商店的玩家名占位符> ten 1
```

替换为商店支持的实际占位符。商店复制券物品时必须保留 `minecraft:custom_data`，使用专属贴图时也需保留 `minecraft:custom_model_data`；仅保存显示名称无法生成有效券。

### 开发与验证

```text
gradlew.bat build
```

构建包含 JUnit 和 Fabric 服务端游戏测试，覆盖概率与保底、限额与固定奖励、玩家和奖池隔离、持久化、历史翻页及原版点击包、回调兼容、失败指令识别、展示高度与偏移、烟花距离及四种朝向、轮播、退券、断线重连、死亡暂存和背包溢出。

服务端游戏测试还检查原版 actionbar 的刷新、到期清除、关闭和揭晓上限，大字幕与音效包的接收范围、S 级全服播报、十连合并及离线重领不重复提示，以及指令奖品的独立轮播、主手设置／清除和展示不参与发奖。

构建同时生成主 JAR、源码 JAR 和独立资源包 ZIP／SHA-1。资源包打包使用固定时间戳与文件顺序；另有测试校验 Minecraft 原生资源包元数据、券材质标识、普通纸回退及模型和贴图引用。

真实 Fuji 集成测试使用其官方 `14.12.0` 的 26.3 JAR，放入 `integration/` 后运行：

```powershell
.\integration\run-fuji-test.ps1 -Offline
```

默认同时开启 advice 和 cooldown。可分别使用 `-CommandHooks none`、`advice`、`cooldown`、`both` 测试不同组合。测试服只监听本机临时端口，数据位于 `build/run/fujiIntegration`，结果写入 `fuji-test-result.json`；验证实际余额、物品、领取状态及失败/取消指令。

生产 JAR 不包含测试入口和测试服务器数据。服务端测试验证实体与网络效果；客户端画面和声音听感仍需进服验收。

### 许可证

本项目采用 GNU General Public License v3.0（仅此版本，`GPL-3.0-only`），完整条款见 [LICENSE](LICENSE)。发布 JAR、源码 JAR 和资源包 ZIP 均附带许可证。
