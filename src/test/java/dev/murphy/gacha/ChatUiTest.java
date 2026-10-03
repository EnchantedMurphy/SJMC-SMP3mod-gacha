package dev.murphy.gacha;

import com.mojang.serialization.JsonOps;
import net.minecraft.commands.Commands;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.protocol.common.ServerboundCustomClickActionPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.permissions.PermissionSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import static dev.murphy.gacha.GachaConfig.Tier.*;
import static org.junit.jupiter.api.Assertions.*;

class ChatUiTest {
    @BeforeAll static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }
    private static PlayerStore.Draw draw(long number, GachaConfig.Tier tier) {
        var draw = new PlayerStore.Draw(); draw.number = number; draw.tier = tier; draw.label = "奖品" + number;
        return draw;
    }
    private static PlayerStore.Data historyData() {
        var data = new PlayerStore.Data();
        var receipt = new PlayerStore.Receipt(); receipt.pool = "main";
        List<GachaConfig.Tier> tiers = List.of(B, B, S, A, B, B, S, B, A, B, B, S);
        for (int i = 0; i < tiers.size(); i++) receipt.draws.add(draw(i + 1, tiers.get(i)));
        receipt.draws.get(8).tenGuarantee = true; receipt.draws.getLast().hardPity = true;
        data.receipts.add(receipt); data.totalDraws = tiers.size();
        var refund = new PlayerStore.Receipt(); refund.refund = true; data.receipts.add(refund);
        return data;
    }
    @Test void historyCountsAcrossPagesResetOnEverySAndIgnoreRefunds() {
        var data = historyData();
        assertEquals(List.of(1L, 2L, 3L, 1L, 2L, 3L, 4L, 1L, 2L, 3L, 4L, 5L),
                ChatUi.historyRows(data).stream().map(ChatUi.History::sinceS).toList());
        var first = ChatUi.history(data, 1); var second = ChatUi.history(data, 2);
        assertEquals(10, first.stream().filter(c -> c.getString().startsWith("#")).count());
        assertEquals(2, second.stream().filter(c -> c.getString().startsWith("#")).count());
        assertTrue(first.get(2).getString().contains("距上次 S：第 5 抽"));
        assertTrue(second.get(2).getString().contains("距上次 S：第 2 抽"));
        assertTrue(first.stream().anyMatch(c -> c.getString().contains("S 保底")));
        assertFalse(first.stream().anyMatch(c -> c.getString().contains("十连末抽保底")));
        assertThrows(IllegalArgumentException.class, () -> ChatUi.history(data, 3));
        assertNull(first.getLast().getSiblings().getFirst().getStyle().getClickEvent());
        assertInstanceOf(ClickEvent.Custom.class, first.getLast().getSiblings().getLast().getStyle().getClickEvent());
        assertNull(second.getLast().getSiblings().getLast().getStyle().getClickEvent());
    }
    private record Span(String text, int color) {}
    private static List<Span> spans(Component component) {
        List<Span> spans = new ArrayList<>();
        component.visit((style, text) -> {
            if (!text.isEmpty()) spans.add(new Span(text, style.getColor().getValue()));
            return Optional.empty();
        }, Style.EMPTY);
        return spans;
    }
    @Test void pityUsesYellowWordsOrangeNumbersAndResultsUseIndividualTierColors() {
        var data = new PlayerStore.Data(); data.misses = 12; data.totalDraws = 123;
        var pity = ChatUi.pity(data, 80, true);
        var spans = spans(pity);
        assertEquals(List.of("12", "68", "123"), spans.stream().filter(s -> s.color == ChatUi.ORANGE).map(Span::text).toList());
        assertTrue(spans.stream().filter(s -> !s.text.matches("\\d+")).allMatch(s -> s.color == 0xFFFF55));
        var result = spans(ChatUi.result(List.of(draw(1, S), draw(2, A), draw(3, B))));
        assertEquals(List.of(0xFF5555, 0xFFFF55, 0x5555FF),
                result.stream().filter(s -> s.text.startsWith("[")).map(Span::color).toList());
    }
    @Test void navigationSurvivesVanillaTextAndPacketCodecsWithoutRunCommand() {
        for (var action : List.of(UiActions.history(2), UiActions.history(32768), UiActions.help(4))) {
            var json = ClickEvent.CODEC.encodeStart(JsonOps.INSTANCE, action).getOrThrow();
            assertFalse(json.toString().contains("run_command"));
            var decoded = assertInstanceOf(ClickEvent.Custom.class, ClickEvent.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
            var packet = new ServerboundCustomClickActionPacket(decoded.id(), decoded.payload());
            var buffer = io.netty.buffer.Unpooled.buffer();
            try {
                ServerboundCustomClickActionPacket.STREAM_CODEC.encode(buffer, packet);
                var received = ServerboundCustomClickActionPacket.STREAM_CODEC.decode(buffer);
                assertEquals(UiActions.decode(new ServerboundCustomClickActionPacket(action.id(), action.payload())), UiActions.decode(received));
            } finally { buffer.release(); }
        }
    }
    @Test void navigationRejectsCommandsInvalidNumbersAndOtherNamespaces() {
        var history = UiActions.history(1);
        for (var payload : List.of(IntTag.valueOf(0), IntTag.valueOf(-1), DoubleTag.valueOf(1.5), LongTag.valueOf(4294967297L), StringTag.valueOf("give @s diamond")))
            assertThrows(IllegalArgumentException.class, () -> UiActions.decode(new ServerboundCustomClickActionPacket(history.id(), Optional.of(payload))));
        assertThrows(IllegalArgumentException.class, () -> UiActions.decode(new ServerboundCustomClickActionPacket(history.id(), Optional.empty())));
        var help = UiActions.help(5);
        assertThrows(IllegalArgumentException.class, () -> UiActions.decode(new ServerboundCustomClickActionPacket(help.id(), help.payload())));
        assertFalse(UiActions.isOurs(Identifier.fromNamespaceAndPath("other", "history")));
        assertFalse(UiActions.isOurs(Identifier.fromNamespaceAndPath("gacha", "give")));
    }
    @Test void helpSeparatesPersonalAndAdminPagesAndRechecksAccess() {
        var player = Commands.createCompilationContext(PermissionSet.NO_PERMISSIONS);
        var owner = Commands.createCompilationContext(PermissionSet.ALL_PERMISSIONS);
        assertTrue(ChatUi.help(player, 1).getFirst().getString().contains("玩法与个人查询"));
        assertFalse(ChatUi.help(player, 1).stream().anyMatch(c -> c.getString().contains("give")));
        assertThrows(IllegalArgumentException.class, () -> ChatUi.help(player, 2));
        assertTrue(ChatUi.help(owner, 2).getFirst().getString().contains("发券与抽奖池"));
        assertTrue(ChatUi.help(owner, 3).getFirst().getString().contains("奖品管理"));
        assertTrue(ChatUi.help(owner, 4).getFirst().getString().contains("高级管理"));
        assertTrue(ChatUi.help(owner, 4).getLast().getSiblings().stream()
                .map(c -> c.getStyle().getClickEvent()).filter(e -> e != null).allMatch(e -> e instanceof ClickEvent.Custom));
    }
}
