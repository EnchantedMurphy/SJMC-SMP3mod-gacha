Minecraft Java 26.3 的 Gacha 抽奖券资源包，适配 Gacha 2.3.0 起发放的单抽券和十连券。客户端无需安装模组，未加载此包时券显示为原版纸。

两张 PNG 目前均为 **32×32 全透明占位图**。请替换成正式贴图并保留路径；加载空白占位图时，券图案不可见，名称、说明和使用方式不变。

| 类型 | 待替换贴图 |
| --- | --- |
| 单抽券 | `assets/gacha/textures/item/ticket_single.png` |
| 十连券 | `assets/gacha/textures/item/ticket_ten.png` |

此包通过原版纸的模型选择器识别 `custom_model_data.strings[0]` 中的 `gacha:ticket_single` 与 `gacha:ticket_ten`；普通纸回退至原版模型。与其他修改纸模型的资源包合并时，请合并 `assets/minecraft/items/paper.json` 的分支和回退规则。
