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
        return DrawEngine.roll(config, misses, 10, new java.util.Random(923));
    }
    @Test void journalSurvivesRestartAndPlayerPityIsIndependent() throws Exception {
        var store = new PlayerStore(directory); UUID one = UUID.randomUUID(), two = UUID.randomUUID();
        var data = store.load(one); var config = GachaConfig.defaults();
        var rolled = result(config, 0);
        var receipt = store.reserve(data, "One", UUID.randomUUID(), "main", rolled, null);
        var restored = new PlayerStore(directory).load(one);
        assertEquals(10, restored.totalDraws); assertEquals(rolled.missesAfter(), restored.misses);
        assertEquals(receipt.id, restored.receipts.getFirst().id); assertEquals(10, restored.receipts.getFirst().draws.size());
        assertEquals(0, store.load(two).misses); assertEquals(0, store.load(two).totalDraws);
    }
    @Test void uncertainDeliverySurvivesWithoutAutomaticRetry() throws Exception {
        var store = new PlayerStore(directory); UUID uuid = UUID.randomUUID(); var data = store.load(uuid);
        var receipt = store.reserve(data, "Player", UUID.randomUUID(), "main", result(GachaConfig.defaults(), 0), null);
        receipt.actions.getFirst().status = PlayerStore.Status.SENDING; store.save(data);
        assertEquals(PlayerStore.Status.SENDING, new PlayerStore(directory).load(uuid).receipts.getFirst().actions.getFirst().status);
    }
    @Test void refundsAreIdempotentAndDoNotChangePityOrHistory() throws Exception {
        var store = new PlayerStore(directory); UUID uuid = UUID.randomUUID(), entity = UUID.randomUUID(); var data = store.load(uuid);
        store.refund(data, entity, Reward.command("give %player% minecraft:paper 2"));
        store.refund(data, entity, Reward.command("give %player% minecraft:paper 2"));
        var restored = store.load(uuid);
        assertEquals(1, restored.receipts.size()); assertTrue(restored.receipts.getFirst().refund);
        assertEquals(0, restored.totalDraws); assertEquals(0, restored.misses);
        assertNotNull(PlayerStore.findTicket(restored, entity));
    }
    @Test void reservationRejectsReplayedTicketEntityAndSnapshotsPrizes() throws Exception {
        var store = new PlayerStore(directory); UUID uuid = UUID.randomUUID(), entity = UUID.randomUUID(); var data = store.load(uuid);
        var config = GachaConfig.defaults(); var rolled = result(config, 0);
        store.reserve(data, "Player", entity, "main", rolled, null);
        String json = Files.readString(directory.resolve(uuid + ".json"));
        config.rewards.values().forEach(ps -> ps.forEach(p -> p.rewards.clear()));
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
}
