package dev.murphy.gacha;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.permissions.Permissions;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;
import static dev.murphy.gacha.GachaConfig.*;

final class ChatUi {
    static final int ORANGE = 0xFFA500;
    static final int HISTORY_PAGE_SIZE = 100;
    static final int DETAILS_PAGE_SIZE = 10;
    static final int HISTORY_ROW_SIZE = 20;
    record History(PlayerStore.Draw draw, PlayerStore.Receipt receipt, long sinceS) {}
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss").withZone(ZoneId.of("Asia/Shanghai"));
    private ChatUi() {}

    static ChatFormatting color(Tier tier) {
        return switch (tier) { case S -> ChatFormatting.RED; case A -> ChatFormatting.YELLOW; case B -> ChatFormatting.BLUE; };
    }
    private static Component number(long n) { return Component.literal(Long.toString(n)).withStyle(s -> s.withColor(ORANGE)); }
    static Component pity(PlayerStore.Data data, int pity, boolean busy) {
        return Component.literal("连续未获S：").withStyle(ChatFormatting.YELLOW)
                .append(number(data.misses)).append(" 抽；最多再抽 ").append(number(Math.max(1, pity - data.misses)))
                .append(" 次必得S。累计 ").append(number(data.totalDraws)).append(" 抽。")
                .append(busy ? "（包含当前已确定的结果）" : "");
    }
    static Component result(List<PlayerStore.Draw> draws) {
        MutableComponent message = Component.literal("抽奖结果：").withStyle(ChatFormatting.GOLD);
        for (int i = 0; i < draws.size(); i++) {
            if (i > 0) message.append(Component.literal("；").withStyle(ChatFormatting.GRAY));
            var draw = draws.get(i);
            message.append(Component.literal("[" + draw.tier + "] " + draw.label).withStyle(color(draw.tier)));
        }
        return message;
    }
    static List<History> historyRows(PlayerStore.Data data) {
        List<History> rows = new ArrayList<>();
        long sinceS = 0;
        for (var receipt : data.receipts) for (var draw : receipt.draws) {
            rows.add(new History(draw, receipt, ++sinceS));
            if (draw.tier == Tier.S) sinceS = 0;
        }
        return rows;
    }
    static List<Component> history(PlayerStore.Data data, int page) {
        var rows = historyRows(data);
        int pages = (int) Math.max(1, (rows.size() + (long) HISTORY_PAGE_SIZE - 1) / HISTORY_PAGE_SIZE);
        if (page < 1 || page > pages) throw new IllegalArgumentException("页码超出范围，共 " + pages + " 页。");
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal("个人抽奖历史 · 第 " + page + "/" + pages + " 页 · 共 " + data.totalDraws + " 抽").withStyle(ChatFormatting.GOLD));
        lines.add(Component.literal("■ B  ").withStyle(color(Tier.B)).append(Component.literal("■ A  ").withStyle(color(Tier.A)))
                .append(Component.literal("■ S").withStyle(color(Tier.S)))
                .append(Component.literal(" · 最新在前，每行最多20抽；红后数字为本次出红抽数。").withStyle(ChatFormatting.GRAY)));
        int offset = (page - 1) * HISTORY_PAGE_SIZE;
        MutableComponent line = Component.empty();
        int columns = 0;
        for (int i = offset; i < Math.min(rows.size(), offset + (long) HISTORY_PAGE_SIZE); i++) {
            var row = rows.get(rows.size() - 1 - i);
            line.append(Component.literal("■").withStyle(color(row.draw().tier)));
            columns++;
            if (row.draw().tier == Tier.S) line.append(Component.literal(" " + row.sinceS() + "抽").withStyle(color(Tier.S)));
            if (columns == HISTORY_ROW_SIZE || row.draw().tier == Tier.S) {
                lines.add(line); line = Component.empty(); columns = 0;
            }
        }
        if (columns > 0) lines.add(line);
        if (rows.isEmpty()) lines.add(Component.literal("尚无抽奖记录。").withStyle(ChatFormatting.GRAY));
        lines.add(navigation(page, pages, UiActions::history).copy().append("  ")
                .append(button("[详细历史]", UiActions.historyDetails(offset / DETAILS_PAGE_SIZE + 1), "查看此页最新一抽起的详细记录")));
        return lines;
    }
    static List<Component> historyDetails(PlayerStore.Data data, int page) {
        var rows = historyRows(data);
        int pages = (int) Math.max(1, (rows.size() + (long) DETAILS_PAGE_SIZE - 1) / DETAILS_PAGE_SIZE);
        if (page < 1 || page > pages) throw new IllegalArgumentException("页码超出范围，共 " + pages + " 页。");
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal("个人抽奖历史 · 第 " + page + "/" + pages + " 页 · 共 " + data.totalDraws + " 抽（时间：北京时间）").withStyle(ChatFormatting.GOLD));
        lines.add(Component.literal("“距上次S”从上次获得S后计数，获S当抽显示本轮抽数。").withStyle(ChatFormatting.GRAY));
        int offset = (page - 1) * DETAILS_PAGE_SIZE;
        for (int i = offset; i < Math.min(rows.size(), offset + (long) DETAILS_PAGE_SIZE); i++) {
            var row = rows.get(rows.size() - 1 - i); var draw = row.draw();
            String flags = "";
            if (row.receipt().actions.stream().anyMatch(a -> a.status == PlayerStore.Status.FAILED || a.status == PlayerStore.Status.SENDING)) flags += " · 发奖需核查";
            else if (row.receipt().actions.stream().anyMatch(a -> a.status == PlayerStore.Status.READY)) flags += " · 待发放";
            lines.add(Component.literal("#" + draw.number + " [" + draw.tier + "] " + draw.label).withStyle(color(draw.tier))
                    .append(Component.literal(flags + " · 距上次S第" + row.sinceS() + "抽 · "
                            + TIME.format(Instant.parse(row.receipt().timestamp)) + " · " + row.receipt().pool).withStyle(ChatFormatting.GRAY)));
        }
        if (rows.isEmpty()) lines.add(Component.literal("尚无抽奖记录。").withStyle(ChatFormatting.GRAY));
        lines.add(navigation(page, pages, UiActions::historyDetails).copy().append("  ")
                .append(button("[方块历史]", UiActions.history(offset / HISTORY_PAGE_SIZE + 1), "返回包含当前记录的方块历史")));
        return lines;
    }
    private static Component button(String label, ClickEvent action, String hover) {
        return Component.literal(label).withStyle(s -> s.withColor(ChatFormatting.AQUA)
                .withClickEvent(action).withHoverEvent(new HoverEvent.ShowText(Component.literal(hover))));
    }
    private static Component navigation(int page, int pages, IntFunction<ClickEvent.Custom> action) {
        return Component.empty().append(page > 1 ? button("[上一页]", action.apply(page - 1), "查看上一页")
                        : Component.literal("[上一页]").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal("  " + page + " / " + pages + "  ").withStyle(ChatFormatting.YELLOW))
                .append(page < pages ? button("[下一页]", action.apply(page + 1), "查看下一页")
                        : Component.literal("[下一页]").withStyle(ChatFormatting.DARK_GRAY));
    }
    private static void command(List<Component> lines, String syntax, String description) {
        lines.add(Component.literal("/gacha " + syntax).withStyle(ChatFormatting.AQUA)
                .append(Component.literal("  → " + description).withStyle(ChatFormatting.GRAY)));
    }
    static List<Component> help(CommandSourceStack source, int page) {
        boolean owner = source.permissions().hasPermission(Permissions.COMMANDS_OWNER);
        boolean admin = source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
        int pages = owner ? 4 : admin ? 3 : 1;
        if (page < 1 || page > pages) throw new IllegalArgumentException("该帮助页不可访问，可查看 1–" + pages + " 页。");
        List<Component> lines = new ArrayList<>();
        String title = switch (page) { case 1 -> "玩法与个人查询"; case 2 -> "发券与抽奖池 · OP 2"; case 3 -> "奖品管理 · OP 2"; default -> "高级管理 · OP 4"; };
        lines.add(Component.literal("Gacha 帮助 · " + title + " · " + page + "/" + pages).withStyle(ChatFormatting.GOLD));
        switch (page) {
            case 1 -> {
                lines.add(Component.literal("将券投掷到抽奖池，等待约 5 秒即可领取奖品。").withStyle(ChatFormatting.YELLOW));
                lines.add(Component.literal("整叠仅用 1 张，余券退回；抽奖期间投入的券也会退回。").withStyle(ChatFormatting.GRAY));
                command(lines, "history [页码]", "三色方块历史，每页100抽，可翻页或切换详细历史");
                command(lines, "history details [页码]", "详细历史，每页10抽");
                command(lines, "pity", "查看距离必得 S 的抽数");
                command(lines, "pending", "查看待领取或异常奖励");
            }
            case 2 -> {
                lines.add(Component.literal("发放抽奖券（数量指券数，省略为 1）").withStyle(ChatFormatting.YELLOW));
                command(lines, "give <玩家> single [数量]", "单抽券");
                command(lines, "give <玩家> ten [数量]", "十连券");
                lines.add(Component.literal("设置抽奖池（在目标维度执行）").withStyle(ChatFormatting.YELLOW));
                command(lines, "pool set <池名> <坐标1> <坐标2>", "两个角确定长方体区域");
                command(lines, "pool list", "查看所有抽奖池");
                command(lines, "pool facing <池名> +x|-x|+z|-z", "设置展示面朝方向，正面看从左到右");
                command(lines, "pool remove <池名>", "删除抽奖池");
            }
            case 3 -> {
                lines.add(Component.literal("等级填写 S、A 或 B；同 ID 会替换原奖品。").withStyle(ChatFormatting.YELLOW));
                command(lines, "reward list", "查看奖品 ID、等级与权重");
                command(lines, "reward sethand <等级> <ID> [权重]", "保存主手整叠物品，不消耗物品");
                command(lines, "reward remove <等级> <ID>", "删除奖品，每个等级至少保留一个");
                command(lines, "check [玩家]", "检查奖品配置，后台需指定玩家");
                command(lines, "pending <玩家>", "查看该玩家待领取或异常记录");
            }
            case 4 -> {
                command(lines, "reward setcommand <等级> <ID> <名称> <指令>", "添加指令奖品，需游戏内执行");
                lines.add(Component.literal("名称有空格时加双引号；指令不带 /，玩家名用 %player%。").withStyle(ChatFormatting.GRAY));
                command(lines, "reload", "重载 config/gacha/config.json");
                lines.add(Component.literal("异常奖励：先核对实际物品或余额，再选择恢复方式。").withStyle(ChatFormatting.YELLOW));
                command(lines, "recover <玩家> <记录UUID> <动作序号> retry", "确认未发放后重试");
                command(lines, "recover <玩家> <记录UUID> <动作序号> done", "确认已发放后标记完成");
            }
        }
        if (pages > 1) {
            MutableComponent tabs = Component.empty();
            String[] labels = {"[个人]", "[券与池]", "[奖品]", "[高级]"};
            for (int i = 1; i <= pages; i++) {
                if (i > 1) tabs.append("  ");
                tabs.append(i == page ? Component.literal(labels[i - 1]).withStyle(ChatFormatting.YELLOW)
                        : button(labels[i - 1], UiActions.help(i), "查看该分类帮助"));
            }
            lines.add(tabs);
        }
        return lines;
    }
}
