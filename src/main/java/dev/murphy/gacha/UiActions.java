package dev.murphy.gacha;

import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.protocol.common.ServerboundCustomClickActionPacket;
import net.minecraft.resources.Identifier;
import java.util.Optional;

/** Vanilla custom clicks request read-only pages, never execute client-supplied commands. */
public final class UiActions {
    private static final Identifier HISTORY = Identifier.fromNamespaceAndPath("gacha", "history");
    private static final Identifier HISTORY_DETAILS = Identifier.fromNamespaceAndPath("gacha", "history_details");
    private static final Identifier HELP = Identifier.fromNamespaceAndPath("gacha", "help");
    enum Action { HISTORY, HISTORY_DETAILS, HELP }
    record Request(Action action, String pool, int page) {}
    private UiActions() {}

    static ClickEvent.Custom history(String pool, int page) { return historyAction(HISTORY, pool, page); }
    static ClickEvent.Custom historyDetails(String pool, int page) { return historyAction(HISTORY_DETAILS, pool, page); }
    private static ClickEvent.Custom historyAction(Identifier id, String pool, int page) {
        CompoundTag tag = new CompoundTag(); tag.putString("pool", pool); tag.putInt("page", page);
        return new ClickEvent.Custom(id, Optional.of(tag));
    }
    static ClickEvent.Custom help(int page) { return new ClickEvent.Custom(HELP, Optional.of(IntTag.valueOf(page))); }
    public static boolean isOurs(Identifier id) { return HISTORY.equals(id) || HISTORY_DETAILS.equals(id) || HELP.equals(id); }

    static Request decode(ServerboundCustomClickActionPacket packet) {
        if (!isOurs(packet.id())) throw new IllegalArgumentException("未知的抽奖界面操作。");
        var payload = packet.payload().orElseThrow(() -> new IllegalArgumentException("界面操作缺少页码。"));
        String pool = null;
        if (!HELP.equals(packet.id())) {
            if (!(payload instanceof CompoundTag tag)) throw new IllegalArgumentException("界面操作缺少奖池。");
            pool = tag.getString("pool").orElseThrow(() -> new IllegalArgumentException("缺少池名。"));
            if (!pool.matches("[A-Za-z0-9_-]{1,32}")) throw new IllegalArgumentException("池名无效。");
            payload = tag.get("page");
        }
        // Text codecs may normalize small integers to byte/short tags.
        if (!(payload instanceof NumericTag page) || payload instanceof FloatTag || payload instanceof DoubleTag
                || page.longValue() < 1 || page.longValue() > Integer.MAX_VALUE
                || HELP.equals(packet.id()) && page.longValue() > 4)
            throw new IllegalArgumentException("界面页码无效。");
        return new Request(HELP.equals(packet.id()) ? Action.HELP
                : HISTORY_DETAILS.equals(packet.id()) ? Action.HISTORY_DETAILS : Action.HISTORY, pool, page.intValue());
    }
}
