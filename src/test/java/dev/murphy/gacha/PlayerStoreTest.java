package dev.murphy.gacha;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import static dev.murphy.gacha.GachaConfig.*;
import static org.junit.jupiter.api.Assertions.*;

class PlayerStoreTest {
    @TempDir Path directory;
    private DrawEngine.Batch result(GachaConfig config, int misses) {
        return DrawEngine.roll(config.poolDefaults, misses, java.util.Map.of(), 10, new java.util.Random(923));
    }
    @Test void journalSurvivesRestartAndPlayerPityIsIndependent() throws Exception {
        var store = new PlayerStore(directory); UUID one = UUID.randomUUID(), two = UUID.randomUUID();
        var data = store.load(one); var config = GachaConfig.defaults();
        var rolled = result(config, 0);
        var receipt = store.reserve(data, "One", UUID.randomUUID(), "main", rolled, null);
        var restored = new PlayerStore(directory).load(one);
        assertEquals(10, PlayerStore.state(restored, "main").totalDraws); assertEquals(rolled.missesAfter(), PlayerStore.state(restored, "main").misses);
        assertEquals(receipt.id, restored.receipts.getFirst().id); assertEquals(10, restored.receipts.getFirst().draws.size());
        assertEquals(0, PlayerStore.state(store.load(two), "main").misses); assertEquals(0, PlayerStore.state(store.load(two), "main").totalDraws);
    }
    @Test void uncertainDeliverySurvivesWithoutAutomaticRetry() throws Exception {
        var store = new PlayerStore(directory); UUID uuid = UUID.randomUUID(); var data = store.load(uuid);
        var receipt = store.reserve(data, "Player", UUID.randomUUID(), "main", result(GachaConfig.defaults(), 0), null);
        receipt.actions.getFirst().status = PlayerStore.Status.SENDING; store.save(data);
        assertEquals(PlayerStore.Status.SENDING, new PlayerStore(directory).load(uuid).receipts.getFirst().actions.getFirst().status);
    }
    @Test void refundsAreIdempotentAndDoNotChangePityOrHistory() throws Exception {
        var store = new PlayerStore(directory); UUID uuid = UUID.randomUUID(), entity = UUID.randomUUID(); var data = store.load(uuid);
        store.refund(data, entity, "main", Reward.command("give %player% minecraft:paper 2"));
        store.refund(data, entity, "main", Reward.command("give %player% minecraft:paper 2"));
        var restored = store.load(uuid);
        assertEquals(1, restored.receipts.size()); assertTrue(restored.receipts.getFirst().refund);
        assertEquals(0, PlayerStore.state(restored, "main").totalDraws); assertEquals(0, PlayerStore.state(restored, "main").misses);
        assertNotNull(PlayerStore.findTicket(restored, entity));
    }
    @Test void reservationRejectsReplayedTicketEntityAndSnapshotsPrizes() throws Exception {
        var store = new PlayerStore(directory); UUID uuid = UUID.randomUUID(), entity = UUID.randomUUID(); var data = store.load(uuid);
        var config = GachaConfig.defaults(); var rolled = result(config, 0);
        store.reserve(data, "Player", entity, "main", rolled, null);
        String json = Files.readString(directory.resolve(uuid + ".json"));
        config.poolDefaults.rewards.values().forEach(ps -> ps.forEach(p -> p.rewards.clear()));
        assertEquals(json, Files.readString(directory.resolve(uuid + ".json")));
        assertFalse(store.load(uuid).receipts.getFirst().actions.isEmpty());
        assertThrows(IllegalArgumentException.class, () -> store.reserve(data, "Player", entity, "main", rolled, null));
    }
    @Test void corruptFileIsPreservedAndCannotSilentlyResetHistory() throws Exception {
        var store = new PlayerStore(directory); UUID uuid = UUID.randomUUID(); Path file = directory.resolve(uuid + ".json");
        Files.writeString(file, "{broken");
        assertThrows(IOException.class, () -> store.load(uuid)); assertEquals("{broken", Files.readString(file));
        Files.writeString(file, "{\"schemaVersion\":1,\"uuid\":\"" + uuid + "\",\"totalDraws\":1,\"receipts\":[]}");
        assertThrows(IOException.class, () -> store.load(uuid));
    }
    @Test void quotasPityAndHistoryAreIndependentPerPoolAndPlayerAcrossRestart() throws Exception {
        var store = new PlayerStore(directory); var one = store.load(UUID.randomUUID()); var two = store.load(UUID.randomUUID());
        var rules = GachaConfig.defaultRules(); rules.sProbability = 1; rules.aProbability = 0;
        rules.rewards.get(Tier.S).getFirst().limit = 1;
        for (var data : java.util.List.of(one, two)) for (String pool : java.util.List.of("main", "other")) {
            var state = PlayerStore.state(data, pool);
            var batch = DrawEngine.roll(rules, state.misses, state.obtained, 10, new java.util.Random(1));
            var receipt = store.reserve(data, "Player", UUID.randomUUID(), pool, batch, null);
            assertEquals(11, receipt.actions.size(), "one random item and ten fixed bonuses");
            assertEquals(1, receipt.draws.stream().filter(d -> d.prizeId != null).count());
        }
        var restored = new PlayerStore(directory).load(UUID.fromString(one.uuid));
        assertEquals(10, PlayerStore.state(restored, "main").totalDraws);
        assertEquals(10, PlayerStore.state(restored, "other").totalDraws);
        assertEquals(1L, PlayerStore.state(restored, "main").obtained.get("S/mace"));
        var state = PlayerStore.state(restored, "main");
        var depleted = DrawEngine.roll(rules, state.misses, state.obtained, 1, new java.util.Random(1));
        assertNull(depleted.results().getFirst().prize());
        rules.sProbability = 0; rules.aProbability = 0;
        store.reserve(restored, "Player", UUID.randomUUID(), "other", DrawEngine.roll(rules, 0,
                PlayerStore.state(restored, "other").obtained, 1, new java.util.Random(1)), null);
        restored = store.load(UUID.fromString(one.uuid));
        assertEquals(0, PlayerStore.state(restored, "main").misses);
        assertEquals(1, PlayerStore.state(restored, "other").misses);
        assertEquals(10, ChatUi.historyRows(restored, "main").size());
        assertEquals(11, ChatUi.historyRows(restored, "other").size());
    }
    @Test void noRewardOutcomesSurviveRestartAndSnapshotsIncludeFixedBonus() throws Exception {
        var store = new PlayerStore(directory); UUID uuid = UUID.randomUUID(); var data = store.load(uuid);
        var rules = GachaConfig.defaultRules(); rules.sProbability = 1; rules.aProbability = 0;
        rules.rewards.get(Tier.S).clear(); rules.fixedRewards.get(Tier.S).clear();
        var receipt = store.reserve(data, "Player", UUID.randomUUID(), "main", DrawEngine.roll(rules, 0, java.util.Map.of(), 1, new java.util.Random(2)), null);
        assertTrue(receipt.actions.isEmpty());
        assertEquals("无奖励", store.load(uuid).receipts.getFirst().draws.getFirst().label);
        rules.fixedRewards.get(Tier.S).add(Prize.of("bonus", "固定奖励", Reward.command("give %player% minecraft:diamond 3")));
        receipt = store.reserve(data, "Player", UUID.randomUUID(), "main", DrawEngine.roll(rules, 0, java.util.Map.of(), 1, new java.util.Random(2)), null);
        rules.fixedRewards.get(Tier.S).getFirst().rewards.clear();
        assertEquals("give %player% minecraft:diamond 3", store.load(uuid).receipts.getLast().actions.getFirst().reward.command);
    }
}
