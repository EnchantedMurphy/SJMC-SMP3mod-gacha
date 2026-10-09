package dev.murphy.gacha;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import java.util.List;
import static dev.murphy.gacha.GachaConfig.*;

final class GachaCommands {
    private final GachaMod mod;
    GachaCommands(GachaMod mod) { this.mod = mod; }
    private static boolean admin(CommandSourceStack source) {
        return source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
    }
    private static boolean owner(CommandSourceStack source) {
        return source.permissions().hasPermission(Permissions.COMMANDS_OWNER);
    }
    private static String text(CommandContext<CommandSourceStack> c, String name) { return StringArgumentType.getString(c, name); }
    @FunctionalInterface private interface Action { int run() throws Exception; }
    private static int safe(CommandContext<CommandSourceStack> c, Action action) {
        try { return action.run(); }
        catch (Exception e) {
            c.getSource().sendFailure(Component.literal(e.getMessage() == null ? "操作失败，请查看服务端日志。" : e.getMessage()));
            if (!(e instanceof IllegalArgumentException) && !(e instanceof com.mojang.brigadier.exceptions.CommandSyntaxException))
                GachaMod.LOGGER.error("抽奖指令失败", e);
            return 0;
        }
    }
    private static void tell(CommandSourceStack source, String message) { source.sendSuccess(() -> Component.literal(message), false); }

    void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var root = Commands.literal("gacha").executes(c -> safe(c, () -> help(c.getSource(), 1)));
        root.then(Commands.literal("help").executes(c -> safe(c, () -> help(c.getSource(), 1)))
                .then(Commands.argument("page", IntegerArgumentType.integer(1, 4))
                        .executes(c -> safe(c, () -> help(c.getSource(), IntegerArgumentType.getInteger(c, "page"))))));
        root.then(Commands.literal("history").executes(c -> safe(c, () -> history(c.getSource(), 1)))
                .then(Commands.argument("page", IntegerArgumentType.integer(1))
                        .executes(c -> safe(c, () -> history(c.getSource(), IntegerArgumentType.getInteger(c, "page")))))
                .then(Commands.literal("details").executes(c -> safe(c, () -> historyDetails(c.getSource(), 1)))
                        .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                .executes(c -> safe(c, () -> historyDetails(c.getSource(), IntegerArgumentType.getInteger(c, "page")))))));
        root.then(Commands.literal("pity").executes(c -> safe(c, () -> {
            mod.available(); ServerPlayer player = c.getSource().getPlayerOrException();
            var data = mod.store.load(player.getUUID());
            c.getSource().sendSuccess(() -> ChatUi.pity(data, mod.config.pity, mod.busyPlayer(player.getUUID())), false);
            return 1;
        })));
        root.then(Commands.literal("pending").executes(c -> safe(c, () -> pending(c.getSource(), c.getSource().getPlayerOrException())))
                .then(Commands.argument("player", EntityArgument.player()).requires(GachaCommands::admin)
                        .executes(c -> safe(c, () -> pending(c.getSource(), EntityArgument.getPlayer(c, "player"))))));
        var give = Commands.literal("give").requires(GachaCommands::admin);
        for (String kind : List.of("single", "ten")) {
            int draws = kind.equals("ten") ? 10 : 1;
            give.then(Commands.argument("players", EntityArgument.players()).then(Commands.literal(kind)
                    .executes(c -> safe(c, () -> give(c, draws, 1)))
                    .then(Commands.argument("count", IntegerArgumentType.integer(1, 2304))
                            .executes(c -> safe(c, () -> give(c, draws, IntegerArgumentType.getInteger(c, "count")))))));
        }
        root.then(give);
        var facing = Commands.literal("facing").then(facingDirections());
        var axis = Commands.literal("axis");
        var axisName = facingDirections();
        for (String legacy : List.of("x", "z"))
            axisName.then(Commands.literal(legacy).executes(c -> safe(c, () -> axis(c, legacy))));
        axis.then(axisName);
        root.then(Commands.literal("pool").requires(GachaCommands::admin)
                .then(Commands.literal("set").then(Commands.argument("name", StringArgumentType.word())
                        .then(Commands.argument("from", BlockPosArgument.blockPos()).then(Commands.argument("to", BlockPosArgument.blockPos())
                                .executes(c -> safe(c, () -> setPool(c)))))))
                .then(Commands.literal("remove").then(Commands.argument("name", StringArgumentType.word()).executes(c -> safe(c, () -> {
                    mod.requireIdlePool(text(c, "name")); var config = mod.editable();
                    if (config.pools.remove(text(c, "name")) == null) throw new IllegalArgumentException("抽奖池不存在。");
                    mod.saveConfig(config); tell(c.getSource(), "抽奖池已删除。"); return 1;
                }))))
                .then(axis).then(facing)
                .then(Commands.literal("list").executes(c -> safe(c, () -> {
                    mod.available();
                    if (mod.config.pools.isEmpty()) tell(c.getSource(), "尚未设置抽奖池。用 /gacha pool set main <坐标1> <坐标2> 设置。");
                    mod.config.pools.forEach((name, p) -> tell(c.getSource(), name + " · " + p.dimension + " · "
                            + p.minX + " " + p.minY + " " + p.minZ + " → " + p.maxX + " " + p.maxY + " " + p.maxZ
                            + " · 面朝 " + p.facing().id));
                    return 1;
                }))));
        var reward = Commands.literal("reward").requires(GachaCommands::admin);
        reward.then(Commands.literal("list").executes(c -> safe(c, () -> {
            mod.available(); mod.config.rewards.forEach((tier, list) -> list.forEach(p -> tell(c.getSource(),
                    "[" + tier + "] " + p.id + " · " + p.label + " · 权重 " + p.weight + " · 子奖励 " + p.rewards.size()))); return 1;
        })));
        var hand = Commands.literal("sethand");
        var command = Commands.literal("setcommand").requires(GachaCommands::owner);
        var remove = Commands.literal("remove");
        for (Tier tier : Tier.values()) {
            hand.then(Commands.literal(tier.name()).then(Commands.argument("id", StringArgumentType.word())
                    .executes(c -> safe(c, () -> setHand(c, tier, 1)))
                    .then(Commands.argument("weight", IntegerArgumentType.integer(1, 1_000_000))
                            .executes(c -> safe(c, () -> setHand(c, tier, IntegerArgumentType.getInteger(c, "weight")))))));
            command.then(Commands.literal(tier.name()).then(Commands.argument("id", StringArgumentType.word())
                    .then(Commands.argument("label", StringArgumentType.string()).then(Commands.argument("command", StringArgumentType.greedyString())
                            .executes(c -> safe(c, () -> {
                                var player = c.getSource().getPlayerOrException();
                                Reward r = Reward.command(text(c, "command")); Rewards.validate(player, r);
                                replace(tier, Prize.of(text(c, "id"), text(c, "label"), r));
                                tell(c.getSource(), "指令奖品已保存：" + text(c, "id")); return 1;
                            }))))));
            remove.then(Commands.literal(tier.name()).then(Commands.argument("id", StringArgumentType.word()).executes(c -> safe(c, () -> {
                var config = mod.editable();
                if (!config.rewards.get(tier).removeIf(p -> p.id.equals(text(c, "id")))) throw new IllegalArgumentException("奖品不存在。");
                mod.saveConfig(config); tell(c.getSource(), "奖品已移除。"); return 1;
            }))));
        }
        root.then(reward.then(hand).then(command).then(remove));
        root.then(Commands.literal("reload").requires(GachaCommands::owner).executes(c -> safe(c, () -> {
            mod.reload(); tell(c.getSource(), "配置已重载，进行中的抽奖仍使用原结果。"); return 1;
        })));
        root.then(Commands.literal("check").requires(GachaCommands::admin)
                .executes(c -> safe(c, () -> check(c.getSource(), c.getSource().getPlayerOrException())))
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(c -> safe(c, () -> check(c.getSource(), EntityArgument.getPlayer(c, "player"))))));
        var recovery = Commands.argument("player", EntityArgument.player()).then(Commands.argument("receipt", StringArgumentType.word())
                .then(Commands.argument("action", IntegerArgumentType.integer(1))
                        .then(Commands.literal("retry").executes(c -> safe(c, () -> recover(c, false))))
                        .then(Commands.literal("done").executes(c -> safe(c, () -> recover(c, true))))));
        root.then(Commands.literal("recover").requires(GachaCommands::owner).then(recovery));
        dispatcher.register(root);
    }
    private int give(CommandContext<CommandSourceStack> c, int draws, int count) throws Exception {
        mod.available(); var players = EntityArgument.getPlayers(c, "players");
        for (var player : players) {
            int left = count;
            while (left > 0) { int n = Math.min(64, left); Rewards.give(player, Tickets.create(draws, n)); left -= n; }
        }
        tell(c.getSource(), "已向 " + players.size() + " 名玩家分别发放 " + count + " 张" + (draws == 10 ? "十连抽奖券。" : "抽奖券。"));
        return players.size();
    }
    private int setPool(CommandContext<CommandSourceStack> c) throws Exception {
        String name = text(c, "name"); mod.requireIdlePool(name);
        BlockPos a = BlockPosArgument.getBlockPos(c, "from"), b = BlockPosArgument.getBlockPos(c, "to");
        Pool pool = new Pool(); pool.dimension = c.getSource().getLevel().dimension().identifier().toString();
        pool.minX = Math.min(a.getX(), b.getX()); pool.maxX = Math.max(a.getX(), b.getX());
        pool.minY = Math.min(a.getY(), b.getY()); pool.maxY = Math.max(a.getY(), b.getY());
        pool.minZ = Math.min(a.getZ(), b.getZ()); pool.maxZ = Math.max(a.getZ(), b.getZ());
        var config = mod.editable(); config.pools.put(name, pool); mod.saveConfig(config);
        tell(c.getSource(), "抽奖池 " + name + " 已保存，区域含两个端点方块。展示位于区域顶部上方 2 格。"); return 1;
    }
    private int axis(CommandContext<CommandSourceStack> c, String axis) throws Exception {
        mod.requireIdlePool(text(c, "name")); var config = mod.editable(); Pool p = config.pools.get(text(c, "name"));
        if (p == null) throw new IllegalArgumentException("抽奖池不存在。");
        p.displayAxis = axis; p.displayFacing = null;
        mod.saveConfig(config); tell(c.getSource(), "展示横排方向已改为 " + axis.toUpperCase() + " 轴，面朝 " + p.facing().id + "。"); return 1;
    }
    private com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> facingDirections() {
        var name = Commands.argument("name", StringArgumentType.word());
        for (Facing facing : Facing.values())
            name.then(Commands.literal(facing.id).executes(c -> safe(c, () -> facing(c, facing))));
        return name;
    }
    private int facing(CommandContext<CommandSourceStack> c, Facing facing) throws Exception {
        mod.requireIdlePool(text(c, "name")); var config = mod.editable(); Pool p = config.pools.get(text(c, "name"));
        if (p == null) throw new IllegalArgumentException("抽奖池不存在。");
        p.displayFacing = facing.id; p.displayAxis = facing.rightX == 0 ? "z" : "x";
        mod.saveConfig(config); tell(c.getSource(), "展示面朝方向已改为 " + facing.id + "，正面看按上排左至右、下排左至右揭晓。"); return 1;
    }
    private int setHand(CommandContext<CommandSourceStack> c, Tier tier, int weight) throws Exception {
        var player = c.getSource().getPlayerOrException();
        Reward r = Rewards.capture(player);
        Prize prize = Prize.of(text(c, "id"), player.getMainHandItem().getHoverName().getString() + " ×" + player.getMainHandItem().getCount(), r);
        prize.weight = weight; replace(tier, prize); tell(c.getSource(), "主手奖品已保存：" + prize.id + "（数量及完整物品组件）。"); return 1;
    }
    private void replace(Tier tier, Prize prize) throws Exception {
        var config = mod.editable(); config.rewards.get(tier).removeIf(p -> p.id.equals(prize.id)); config.rewards.get(tier).add(prize);
        mod.saveConfig(config);
    }
    private int check(CommandSourceStack source, ServerPlayer player) {
        mod.available(); Rewards.validateAll(player, mod.config);
        tell(source, "所有物品和奖品指令解析通过。Fuji 货币的实际增额请在安装 Fuji 的测试服验证。"); return 1;
    }
    private int pending(CommandSourceStack source, ServerPlayer player) throws Exception {
        mod.available(); var data = mod.store.load(player.getUUID()); int count = 0;
        for (var r : data.receipts) for (int i = 0; i < r.actions.size(); i++) {
            var a = r.actions.get(i);
            if (a.status != PlayerStore.Status.DONE) {
                tell(source, "记录 " + r.id + " · 动作 " + (i + 1) + " · " + a.status + " · " + a.detail); count++;
            }
        }
        if (count == 0) tell(source, "没有待领取或异常奖励。");
        else { mod.queueDelivery(player.getUUID()); tell(source, "未发放的正常奖励将在抽奖结束或下一秒自动处理；异常动作需要管理员核查。"); }
        return 1;
    }
    private int recover(CommandContext<CommandSourceStack> c, boolean done) throws Exception {
        mod.available(); ServerPlayer player = EntityArgument.getPlayer(c, "player");
        if (mod.busyPlayer(player.getUUID())) throw new IllegalArgumentException("该玩家正在抽奖，请等结果揭晓。");
        var data = mod.store.load(player.getUUID()); var receipt = PlayerStore.receipt(data, text(c, "receipt"));
        int index = IntegerArgumentType.getInteger(c, "action") - 1;
        if (index >= receipt.actions.size()) throw new IllegalArgumentException("动作序号超出范围。");
        var a = receipt.actions.get(index);
        if (a.status != PlayerStore.Status.FAILED && a.status != PlayerStore.Status.SENDING)
            throw new IllegalArgumentException("只能处理 FAILED 或 SENDING 动作。");
        a.status = done ? PlayerStore.Status.DONE : PlayerStore.Status.READY;
        a.detail = done ? "管理员核实已发放" : "管理员核实未发放并请求重试";
        mod.store.save(data); mod.queueDelivery(player.getUUID());
        tell(c.getSource(), done ? "该动作已标记为发放完成。" : "该动作将在下一秒重试，请确保此前未发放以免重复。"); return 1;
    }
    private int history(CommandSourceStack source, int page) throws Exception {
        mod.available();
        var data = mod.store.load(source.getPlayerOrException().getUUID());
        ChatUi.history(data, page).forEach(line -> source.sendSuccess(() -> line, false));
        return 1;
    }
    private int historyDetails(CommandSourceStack source, int page) throws Exception {
        mod.available();
        var data = mod.store.load(source.getPlayerOrException().getUUID());
        ChatUi.historyDetails(data, page).forEach(line -> source.sendSuccess(() -> line, false));
        return 1;
    }
    private static int help(CommandSourceStack source, int page) {
        ChatUi.help(source, page).forEach(line -> source.sendSuccess(() -> line, false));
        return 1;
    }
}
