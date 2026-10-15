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
    @Test void defaultsSeparateRandomAndFixedRewards() throws Exception {
        var config = GachaConfig.defaults(); config.validate();
        assertEquals(60, config.resultTicks);
        assertEquals(100, config.actionbarTicks);
        var example = JsonFiles.read(java.nio.file.Path.of("example-config.json"), GachaConfig.class);
        example.validate();
        var rules = config.poolDefaults;
        assertEquals(1, rules.rewards.get(Tier.S).size());
        assertEquals(1, rules.rewards.get(Tier.S).getFirst().rewards.size());
        assertEquals("minecraft:mace", rules.rewards.get(Tier.S).getFirst().rewards.getFirst().item.getAsJsonObject().get("id").getAsString());
        assertEquals("economy give %player% fuji:coffee_stamp 20", rules.fixedRewards.get(Tier.S).getFirst().rewards.getFirst().command);
    }
    @Test void probabilityBoundariesAreAbsoluteNotConditional() {
        var config = GachaConfig.defaultRules();
        for (double d : new double[]{0, 0.019999}) assertEquals(Tier.S, DrawEngine.roll(config, 0, java.util.Map.of(), 1, fixed(d)).results().getFirst().tier());
        for (double d : new double[]{0.02, 0.119999}) assertEquals(Tier.A, DrawEngine.roll(config, 0, java.util.Map.of(), 1, fixed(d)).results().getFirst().tier());
        for (double d : new double[]{0.120001, 0.999999}) assertEquals(Tier.B, DrawEngine.roll(config, 0, java.util.Map.of(), 1, fixed(d)).results().getFirst().tier());
    }
    @Test void eightiethDrawIsSAndResetsPity() {
        var config = GachaConfig.defaultRules(); int misses = 0;
        for (int i = 1; i < 80; i++) {
            var result = DrawEngine.roll(config, misses, java.util.Map.of(), 1, fixed(0.99));
            assertEquals(Tier.B, result.results().getFirst().tier()); misses = result.missesAfter();
        }
        assertEquals(79, misses);
        var result = DrawEngine.roll(config, misses, java.util.Map.of(), 1, fixed(0.99));
        assertEquals(Tier.S, result.results().getFirst().tier()); assertTrue(result.results().getFirst().hardPity());
        assertEquals(0, result.missesAfter());
    }
    @Test void tenthDrawIsAtLeastAEvenIfAnEarlierDrawWasAlreadyS() {
        var config = GachaConfig.defaultRules();
        var result = DrawEngine.roll(config, 79, java.util.Map.of(), 10, fixed(0.99));
        assertEquals(Tier.S, result.results().getFirst().tier());
        assertEquals(Tier.A, result.results().getLast().tier()); assertTrue(result.results().getLast().tenGuarantee());
        assertEquals(9, result.missesAfter());
    }
    @Test void pityTakesPriorityOnTenthDraw() {
        var result = DrawEngine.roll(GachaConfig.defaultRules(), 70, java.util.Map.of(), 10, fixed(0.99));
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
        var result = DrawEngine.roll(GachaConfig.defaultRules(), 60, java.util.Map.of(), 10, random);
        assertEquals(Tier.S, result.results().get(2).tier()); assertEquals(7, result.missesAfter());
    }
    @Test void zeroProbabilitiesAndPityOneRemainDefined() {
        var config = GachaConfig.defaultRules(); config.sProbability = 0; config.aProbability = 0;
        assertEquals(Tier.A, DrawEngine.roll(config, 0, java.util.Map.of(), 10, fixed(0)).results().getLast().tier());
        config.pity = 1;
        var result = DrawEngine.roll(config, 0, java.util.Map.of(), 10, fixed(0.99));
        assertTrue(result.results().stream().allMatch(r -> r.tier() == Tier.S && r.hardPity())); assertEquals(0, result.missesAfter());
    }
    @Test void weightedSelectionUsesTheWholeInterval() {
        var config = GachaConfig.defaultRules(); config.rewards.get(Tier.A).getFirst().weight = 2;
        List<String> ids = new ArrayList<>();
        for (int index = 0; index < 4; index++) {
            final int choice = index;
            RandomGenerator random = new RandomGenerator() {
                public long nextLong() { return 0; }
                public double nextDouble() { return 0.05; }
                public int nextInt(int bound) { assertEquals(4, bound); return choice; }
            };
            ids.add(DrawEngine.roll(config, 0, java.util.Map.of(), 1, random).results().getFirst().prize().id);
        }
        assertEquals(List.of("diamond", "diamond", "gold_ingot", "iron_ingot"), ids);
    }
    @Test void rejectsInvalidProbabilityLimitsAndOverlappingPoolsButAllowsEmptyTiers() {
        var config = GachaConfig.defaults(); config.poolDefaults.sProbability = Double.NaN;
        assertThrows(IllegalArgumentException.class, config::validate);
        config = GachaConfig.defaults(); config.poolDefaults.aProbability = 1;
        assertThrows(IllegalArgumentException.class, config::validate);
        config = GachaConfig.defaults(); config.poolDefaults.rewards.get(Tier.B).clear(); config.validate();
        config.poolDefaults.rewards.get(Tier.S).getFirst().limit = -2;
        assertThrows(IllegalArgumentException.class, config::validate);
        config = GachaConfig.defaults(); Pool p = config.newPool(); p.dimension = "minecraft:overworld";
        config.pools.put("a", p); config.pools.put("b", p);
        assertThrows(IllegalArgumentException.class, config::validate);
    }
    @Test void poolIncludesBothCornerBlocksAndExcludesNextBlock() {
        Pool p = new Pool(); p.minX = 1; p.maxX = 2; p.minY = 3; p.maxY = 4; p.minZ = 5; p.maxZ = 6;
        assertTrue(p.contains(1, 3, 5)); assertTrue(p.contains(2.999, 4.999, 6.999));
        assertFalse(p.contains(3, 4, 6)); assertFalse(p.contains(2, 5, 6)); assertFalse(p.contains(2, 4, 7));
    }
    @Test void fourFacingsRoundTrip() {
        var config = GachaConfig.defaults(); var pool = config.newPool(); pool.dimension = "minecraft:overworld";
        config.pools.put("main", pool);
        assertEquals(Facing.POS_Z, pool.facing());
        for (String id : List.of("+x", "-x", "+z", "-z")) {
            pool.displayFacing = id; config.validate();
            var restored = JsonFiles.GSON.fromJson(JsonFiles.GSON.toJson(config), GachaConfig.class);
            restored.validate(); assertEquals(id, restored.pools.get("main").facing().id);
        }
        pool.displayFacing = "north"; assertThrows(IllegalArgumentException.class, config::validate);
    }
    @Test void limitedPrizeIsRemovedImmediatelyWithinTenDrawsAndBonusRemains() {
        var rules = GachaConfig.defaultRules(); rules.sProbability = 1; rules.aProbability = 0;
        rules.rewards.get(Tier.S).getFirst().limit = 2;
        var obtained = new java.util.HashMap<String, Long>();
        obtained.put("S/mace", 1L);
        var batch = DrawEngine.roll(rules, 0, obtained, 10, fixed(0));
        assertNotNull(batch.results().getFirst().prize());
        assertTrue(batch.results().subList(1, 10).stream().allMatch(r -> r.prize() == null));
        assertTrue(batch.results().stream().allMatch(r -> r.fixedRewards().size() == 1));
        assertEquals(1L, obtained.get("S/mace"), "rolling does not mutate persisted state before reservation");
        assertEquals(0, batch.missesAfter(), "empty S draws still reset pity");
    }
    @Test void depletedAndDisabledPrizesAreExcludedBeforeWeighting() {
        var rules = GachaConfig.defaultRules();
        var prizes = rules.rewards.get(Tier.A);
        prizes.get(0).limit = 1; prizes.get(0).weight = 9999; prizes.get(1).limit = 0;
        var result = DrawEngine.roll(rules, 0, java.util.Map.of("A/diamond", 1L, "A/iron_ingot", 999L), 1, fixed(.05));
        assertEquals("iron_ingot", result.results().getFirst().prize().id, "unlimited prize remains eligible");
    }
    @Test void tierWithoutRandomOrFixedRewardsStillProducesAnOutcome() {
        var rules = GachaConfig.defaultRules(); rules.rewards.get(Tier.S).clear(); rules.fixedRewards.get(Tier.S).clear();
        var result = DrawEngine.roll(rules, 79, java.util.Map.of(), 1, fixed(.99));
        assertEquals(Tier.S, result.results().getFirst().tier());
        assertNull(result.results().getFirst().prize()); assertTrue(result.results().getFirst().fixedRewards().isEmpty());
        assertEquals(0, result.missesAfter());
    }
    @Test void newPoolsCopyIndependentTablesAndProbabilities() {
        var config = GachaConfig.defaults(); var first = config.newPool(); var second = config.newPool();
        first.rewards.get(Tier.S).clear(); first.fixedRewards.get(Tier.B).clear(); first.pity = 1;
        assertEquals(1, second.rewards.get(Tier.S).size()); assertEquals(1, second.fixedRewards.get(Tier.B).size());
        assertEquals(80, second.pity); assertEquals(1, config.poolDefaults.rewards.get(Tier.S).size());
    }
    @Test void animationFrequenciesFollowFiftyThirtyTwenty() {
        var random = new java.util.Random(127); int[] counts = new int[3];
        for (int i = 0; i < 100_000; i++) counts[GachaAnimation.animationColor(random)]++;
        assertTrue(counts[0] > 49_000 && counts[0] < 51_000); assertTrue(counts[1] > 29_000 && counts[1] < 31_000);
        assertTrue(counts[2] > 19_000 && counts[2] < 21_000);
    }
    @Test void displayOffsetsAreIndependentAndValidateFiniteCoordinates() {
        var config = GachaConfig.defaults(); config.poolDefaults.displayOffsetX = 1.25;
        var first = config.newPool(); var second = config.newPool();
        assertEquals(1.25, first.displayOffsetX); assertEquals(1.25, second.displayOffsetX);
        first.displayOffsetX = -2; assertEquals(1.25, second.displayOffsetX);
        config.poolDefaults.displayOffsetY = Double.NaN;
        assertThrows(IllegalArgumentException.class, config::validate);
        config.poolDefaults.displayOffsetY = 65;
        assertThrows(IllegalArgumentException.class, config::validate);
    }
    @Test void fireworkDistancesCopyPerPoolAndRejectInvalidValues() {
        var config = GachaConfig.defaults(); assertEquals(3.2, config.poolDefaults.fireworkDistance);
        config.poolDefaults.fireworkDistance = 5.5;
        var first = config.newPool(); var second = config.newPool();
        assertEquals(5.5, first.fireworkDistance); assertEquals(5.5, second.fireworkDistance);
        first.fireworkDistance = 2.25; assertEquals(5.5, second.fireworkDistance);
        var restored = JsonFiles.GSON.fromJson(JsonFiles.GSON.toJson(first), Pool.class);
        assertEquals(2.25, restored.fireworkDistance);
        for (double value : new double[]{-1, 65, Double.NaN, Double.POSITIVE_INFINITY}) {
            config.poolDefaults.fireworkDistance = value;
            assertThrows(IllegalArgumentException.class, config::validate);
        }
        for (double value : new double[]{0, 3.2, 64}) {
            config.poolDefaults.fireworkDistance = value; config.validate();
        }
    }
    @Test void actionbarDurationValidatesAndRoundTrips() {
        var config = GachaConfig.defaults();
        for (int ticks : new int[]{0, 1, 100, 1200}) {
            config.actionbarTicks = ticks; config.validate();
            assertEquals(ticks, JsonFiles.GSON.fromJson(JsonFiles.GSON.toJson(config), GachaConfig.class).actionbarTicks);
        }
        for (int ticks : new int[]{-1, 1201}) {
            config.actionbarTicks = ticks;
            assertThrows(IllegalArgumentException.class, config::validate);
        }
    }
    @Test void displayItemsAreIndependentCosmeticsAndOnlyBelongToRandomSPrizes() {
        var config = GachaConfig.defaults();
        var prize = config.poolDefaults.rewards.get(Tier.S).getFirst();
        prize.displayItem = com.google.gson.JsonParser.parseString("{\"id\":\"minecraft:clock\",\"count\":1}");
        config.validate();
        var first = config.newPool(); var second = config.newPool();
        assertEquals(prize.displayItem, first.rewards.get(Tier.S).getFirst().displayItem);
        first.rewards.get(Tier.S).getFirst().displayItem.getAsJsonObject().addProperty("id", "minecraft:paper");
        assertEquals("minecraft:clock", second.rewards.get(Tier.S).getFirst().displayItem.getAsJsonObject().get("id").getAsString());
        var batch = DrawEngine.roll(config.poolDefaults, 79, java.util.Map.of(), 1, fixed(.99));
        assertEquals("minecraft:mace", batch.results().getFirst().prize().rewards.getFirst().item.getAsJsonObject().get("id").getAsString());
        prize.displayItem = com.google.gson.JsonParser.parseString("[]");
        assertThrows(IllegalArgumentException.class, config::validate);
        prize.displayItem = null;
        config.poolDefaults.fixedRewards.get(Tier.S).getFirst().displayItem = new com.google.gson.JsonObject();
        assertThrows(IllegalArgumentException.class, config::validate);
        config.poolDefaults.fixedRewards.get(Tier.S).getFirst().displayItem = null;
        config.poolDefaults.rewards.get(Tier.A).getFirst().displayItem = new com.google.gson.JsonObject();
        assertThrows(IllegalArgumentException.class, config::validate);
    }
}
