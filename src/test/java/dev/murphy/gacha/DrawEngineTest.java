package dev.murphy.gacha;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;
import static dev.murphy.gacha.GachaConfig.*;
import static org.junit.jupiter.api.Assertions.*;

class DrawEngineTest {
    private static RandomGenerator fixed(double value) {
        return new RandomGenerator() {
            public long nextLong() { return 0; }
            public double nextDouble() { return value; }
            public int nextInt(int bound) { return 0; }
        };
    }
    @Test void defaultSPrizeIsTheRequestedBundle() throws Exception {
        var config = GachaConfig.defaults(); config.validate();
        assertEquals(JsonFiles.GSON.toJsonTree(config), com.google.gson.JsonParser.parseString(
                java.nio.file.Files.readString(java.nio.file.Path.of("example-config.json"))), "documented defaults must match runtime defaults");
        assertEquals(1, config.rewards.get(Tier.S).size());
        assertEquals(2, config.rewards.get(Tier.S).getFirst().rewards.size());
        assertEquals("minecraft:mace", config.rewards.get(Tier.S).getFirst().rewards.getFirst().item.getAsJsonObject().get("id").getAsString());
        assertEquals("economy give %player% fuji:coffee_stamp 20", config.rewards.get(Tier.S).getFirst().rewards.getLast().command);
    }
    @Test void probabilityBoundariesAreAbsoluteNotConditional() {
        var config = GachaConfig.defaults();
        for (double d : new double[]{0, 0.019999}) assertEquals(Tier.S, DrawEngine.roll(config, 0, 1, fixed(d)).results().getFirst().tier());
        for (double d : new double[]{0.02, 0.119999}) assertEquals(Tier.A, DrawEngine.roll(config, 0, 1, fixed(d)).results().getFirst().tier());
        for (double d : new double[]{0.120001, 0.999999}) assertEquals(Tier.B, DrawEngine.roll(config, 0, 1, fixed(d)).results().getFirst().tier());
    }
    @Test void eightiethDrawIsSAndResetsPity() {
        var config = GachaConfig.defaults(); int misses = 0;
        for (int i = 1; i < 80; i++) {
            var result = DrawEngine.roll(config, misses, 1, fixed(0.99));
            assertEquals(Tier.B, result.results().getFirst().tier()); misses = result.missesAfter();
        }
        assertEquals(79, misses);
        var result = DrawEngine.roll(config, misses, 1, fixed(0.99));
        assertEquals(Tier.S, result.results().getFirst().tier()); assertTrue(result.results().getFirst().hardPity());
        assertEquals(0, result.missesAfter());
    }
    @Test void tenthDrawIsAtLeastAEvenIfAnEarlierDrawWasAlreadyS() {
        var config = GachaConfig.defaults();
        var result = DrawEngine.roll(config, 79, 10, fixed(0.99));
        assertEquals(Tier.S, result.results().getFirst().tier());
        assertEquals(Tier.A, result.results().getLast().tier()); assertTrue(result.results().getLast().tenGuarantee());
        assertEquals(9, result.missesAfter());
    }
    @Test void pityTakesPriorityOnTenthDraw() {
        var result = DrawEngine.roll(GachaConfig.defaults(), 70, 10, fixed(0.99));
        assertEquals(Tier.S, result.results().getLast().tier()); assertTrue(result.results().getLast().hardPity());
        assertFalse(result.results().getLast().tenGuarantee()); assertEquals(0, result.missesAfter());
    }
    @Test void naturalSResetsMidBatchAndRemainingDrawsCount() {
        var values = new java.util.ArrayDeque<>(List.of(0.9, 0.9, 0.001, 0.9, 0.9, 0.9, 0.9, 0.9, 0.9, 0.9));
        RandomGenerator random = new RandomGenerator() {
            public long nextLong() { return 0; }
            public double nextDouble() { return values.removeFirst(); }
            public int nextInt(int bound) { return 0; }
        };
        var result = DrawEngine.roll(GachaConfig.defaults(), 60, 10, random);
        assertEquals(Tier.S, result.results().get(2).tier()); assertEquals(7, result.missesAfter());
    }
    @Test void zeroProbabilitiesAndPityOneRemainDefined() {
        var config = GachaConfig.defaults(); config.sProbability = 0; config.aProbability = 0;
        assertEquals(Tier.A, DrawEngine.roll(config, 0, 10, fixed(0)).results().getLast().tier());
        config.pity = 1;
        var result = DrawEngine.roll(config, 0, 10, fixed(0.99));
        assertTrue(result.results().stream().allMatch(r -> r.tier() == Tier.S && r.hardPity())); assertEquals(0, result.missesAfter());
    }
    @Test void weightedSelectionUsesTheWholeInterval() {
        var config = GachaConfig.defaults(); config.rewards.get(Tier.A).getFirst().weight = 2;
        List<String> ids = new ArrayList<>();
        for (int index = 0; index < 4; index++) {
            final int choice = index;
            RandomGenerator random = new RandomGenerator() {
                public long nextLong() { return 0; }
                public double nextDouble() { return 0.05; }
                public int nextInt(int bound) { assertEquals(4, bound); return choice; }
            };
            ids.add(DrawEngine.roll(config, 0, 1, random).results().getFirst().prize().id);
        }
        assertEquals(List.of("diamond", "diamond", "gold_ingot", "iron_ingot"), ids);
    }
    @Test void rejectsInvalidProbabilityEmptyTierAndOverlappingPools() {
        var config = GachaConfig.defaults(); config.sProbability = Double.NaN;
        assertThrows(IllegalArgumentException.class, config::validate);
        config = GachaConfig.defaults(); config.aProbability = 1;
        assertThrows(IllegalArgumentException.class, config::validate);
        config = GachaConfig.defaults(); config.rewards.get(Tier.B).clear();
        assertThrows(IllegalArgumentException.class, config::validate);
        config = GachaConfig.defaults(); Pool p = new Pool(); p.dimension = "minecraft:overworld";
        config.pools.put("a", p); config.pools.put("b", p);
        assertThrows(IllegalArgumentException.class, config::validate);
    }
    @Test void poolIncludesBothCornerBlocksAndExcludesNextBlock() {
        Pool p = new Pool(); p.minX = 1; p.maxX = 2; p.minY = 3; p.maxY = 4; p.minZ = 5; p.maxZ = 6;
        assertTrue(p.contains(1, 3, 5)); assertTrue(p.contains(2.999, 4.999, 6.999));
        assertFalse(p.contains(3, 4, 6)); assertFalse(p.contains(2, 5, 6)); assertFalse(p.contains(2, 4, 7));
    }
}
