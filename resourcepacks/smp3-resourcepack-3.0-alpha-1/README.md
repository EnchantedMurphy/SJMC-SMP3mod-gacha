# smp3-resourcepack-3.0-alpha-1

Minecraft Java 26.3 的 Gacha 抽奖券资源包，适配 Gacha 2.3.0 起发放的单抽券和十连券。客户端无需安装模组，未加载此包时券显示为原版纸。

两张 PNG 目前均为 **32×32 全透明占位图**。请替换成正式贴图并保留路径；加载空白占位图时，券图案不可见，名称、说明和使用方式不变。

| 类型 | 待替换贴图 |
| --- | --- |
| 单抽券 | `assets/gacha/textures/item/ticket_single.png` |
| 十连券 | `assets/gacha/textures/item/ticket_ten.png` |

在模组项目根目录运行 `gradlew.bat resourcePackSha1`，生成 `build/resourcepacks/smp3-resourcepack-3.0-alpha-1.zip` 及 `.zip.sha1`。ZIP 根目录直接包含 `pack.mcmeta` 和 `assets/`。

将 ZIP 上传至玩家可直接下载的位置，在服务端 `server.properties` 中配置 `resource-pack` 下载地址、`resource-pack-sha1` 校验值，并按需设置 `require-resource-pack`。替换贴图后重新打包、上传并更新 SHA-1，重启服务器并让玩家重新连接。

旧券仍可使用，但需重新发放才带有新材质标识。商店复制物品时同时保留 `minecraft:custom_data` 和 `minecraft:custom_model_data`。

此包通过原版纸的模型选择器识别 `custom_model_data.strings[0]` 中的 `gacha:ticket_single` 与 `gacha:ticket_ten`；普通纸回退至原版模型。与其他修改纸模型的资源包合并时，请合并 `assets/minecraft/items/paper.json` 的分支和回退规则。

许可证：GPL-3.0-only，发布 ZIP 内附 LICENSE。更完整的安装和配置说明见模组项目 README。
