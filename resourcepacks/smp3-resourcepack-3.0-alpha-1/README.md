# smp3-resourcepack-3.0-alpha-1

Minecraft Java 26.3 / Gacha 3.0.0 的可选抽奖券材质覆盖包。客户端与服务端均需安装 Gacha；模组已内置贴图，此包可用于替换外观。

- 单抽券：`smp3:ticket_single`，贴图 `assets/smp3/textures/item/ticket_single.png`。
- 十连券：`smp3:ticket_ten`，贴图 `assets/smp3/textures/item/ticket_ten.png`。

保留路径与文件名后直接替换 PNG，支持 16×16、32×32 等尺寸。模型与物品定义同样位于 `assets/smp3/`；不修改纸模型。此包仅适用于新版注册物品，不包含旧纸质券的模型选择器。

在模组项目根目录运行 `gradlew.bat resourcePackSha1`，生成 `build/resourcepacks/smp3-resourcepack-3.0-alpha-1.zip` 及 `.zip.sha1`。ZIP 根目录直接包含 `pack.mcmeta` 和 `assets/`。

通过服务器分发时，上传最终 ZIP，并在 `server.properties` 中填写 `resource-pack` 下载地址、`resource-pack-sha1` 校验值；按需设置 `require-resource-pack`。每次修改后重新打包、上传、更新校验值，重启服务器并重新连接。资源包不能替代客户端模组。

与其他包合并时可直接合入 `assets/smp3/`；旧版本的 `assets/minecraft/items/paper.json` 与 `assets/gacha/` 不再需要。

许可证：GPL-3.0-only，发布 ZIP 内附 LICENSE。完整配置说明见项目 README。
