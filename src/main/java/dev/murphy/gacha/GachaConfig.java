package dev.murphy.gacha;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class GachaConfig {
    public enum Tier { S, A, B }
    public enum Facing {
        POS_X("+x", 0, -1), NEG_X("-x", 0, 1), POS_Z("+z", 1, 0), NEG_Z("-z", -1, 0);
        final String id;
        final int rightX, rightZ;
        Facing(String id, int rightX, int rightZ) { this.id = id; this.rightX = rightX; this.rightZ = rightZ; }
        static Facing parse(String id) {
            for (Facing facing : values()) if (facing.id.equals(id)) return facing;
            throw new IllegalArgumentException("面朝方向应为 +x、-x、+z 或 -z。");
        }
    }
    public int schemaVersion = 2;
    public int animationTicks = 100;
    public int resultTicks = 40;
    public PoolRules poolDefaults = defaultRules();
    public Map<String, Pool> pools = new LinkedHashMap<>();

    public static class PoolRules {
        public double sProbability = 0.02;
        public double aProbability = 0.10;
        public int pity = 80;
        public double displayOffsetX, displayOffsetY, displayOffsetZ;
        public double fireworkDistance = 3.2;
        public Map<Tier, List<Prize>> rewards = new EnumMap<>(Tier.class);
        public Map<Tier, List<Prize>> fixedRewards = new EnumMap<>(Tier.class);
    }
    public static final class Pool extends PoolRules {
        public String dimension;
        public int minX, minY, minZ, maxX, maxY, maxZ;
        public String displayFacing = "+z";
        Facing facing() { return Facing.parse(displayFacing); }
        double displayX() { return (minX + (double) maxX + 1) / 2 + displayOffsetX; }
        double displayY(int draws) { return maxY + (draws == 10 ? 2.0 : 2.5) + displayOffsetY; }
        double displayZ() { return (minZ + (double) maxZ + 1) / 2 + displayOffsetZ; }
        public boolean contains(double x, double y, double z) {
            return x >= minX && x < (double) maxX + 1 && y >= minY && y < (double) maxY + 1
                    && z >= minZ && z < (double) maxZ + 1;
        }
        boolean overlaps(Pool p) {
            return dimension.equals(p.dimension) && minX <= p.maxX && maxX >= p.minX
                    && minY <= p.maxY && maxY >= p.minY && minZ <= p.maxZ && maxZ >= p.minZ;
        }
    }

    public static final class Reward {
        public JsonElement item;
        public String command;
        public static Reward item(JsonElement item) {
            Reward r = new Reward(); r.item = item.deepCopy(); return r;
        }
        public static Reward command(String command) {
            Reward r = new Reward(); r.command = command; return r;
        }
    }

    public static final class Prize {
        public String id;
        public String label;
        public int weight = 1;
        public int limit = -1;
        public List<Reward> rewards = new ArrayList<>();
        public static Prize of(String id, String label, Reward... rewards) {
            Prize p = new Prize(); p.id = id; p.label = label; p.rewards.addAll(List.of(rewards)); return p;
        }
    }

    public static GachaConfig defaults() { return new GachaConfig(); }

    static PoolRules defaultRules() {
        PoolRules c = new PoolRules();
        for (Tier tier : Tier.values()) {
            c.rewards.put(tier, new ArrayList<>());
            c.fixedRewards.put(tier, new ArrayList<>());
        }
        c.rewards.get(Tier.S).add(Prize.of("mace", "重锤 ×1", vanilla("minecraft:mace", 1)));
        c.fixedRewards.get(Tier.S).add(Prize.of("stamps", "咖啡印章 ×20",
                Reward.command("economy give %player% fuji:coffee_stamp 20")));
        c.rewards.get(Tier.A).addAll(List.of(
                Prize.of("diamond", "钻石", vanilla("minecraft:diamond", 1)),
                Prize.of("gold_ingot", "金锭", vanilla("minecraft:gold_ingot", 1)),
                Prize.of("iron_ingot", "铁锭", vanilla("minecraft:iron_ingot", 1))));
        c.fixedRewards.get(Tier.B).add(Prize.of("tongbao", "铁镐通宝 ×20",
                Reward.command("economy give %player% fuji:tongbao 20")));
        return c;
    }

    Pool newPool() {
        return JsonFiles.GSON.fromJson(JsonFiles.GSON.toJson(poolDefaults), Pool.class);
    }

    Pool pool(String name) {
        Pool pool = pools.get(name);
        if (pool == null) throw new IllegalArgumentException("抽奖池不存在：" + name);
        return pool;
    }

    static String prizeKey(Tier tier, String id) { return tier.name() + "/" + id; }
    private static Reward vanilla(String id, int count) {
        JsonObject item = new JsonObject(); item.addProperty("id", id); item.addProperty("count", count);
        return Reward.item(item);
    }

    public void validate() {
        if (schemaVersion != 2) throw new IllegalArgumentException("配置需要使用 2.0 版格式，请替换旧版配置。");
        if (animationTicks < 20 || animationTicks > 1200 || resultTicks < 0 || resultTicks > 1200)
            throw new IllegalArgumentException("动画时长应为 20–1200 tick，结果展示为 0–1200 tick。");
        if (pools == null || poolDefaults == null) throw new IllegalArgumentException("缺少 pools 或 poolDefaults。");
        validateRules(poolDefaults);
        for (var entry : pools.entrySet()) {
            if (!entry.getKey().matches("[A-Za-z0-9_-]{1,32}")) throw new IllegalArgumentException("池名只能使用字母数字、_、-，最多 32 字符。");
            Pool p = entry.getValue();
            if (p == null || p.dimension == null || !p.dimension.matches("[a-z0-9_.-]+:[a-z0-9/_.-]+")
                    || p.minX > p.maxX || p.minY > p.maxY || p.minZ > p.maxZ
                    || Math.abs((long) p.minX) > 30_000_000 || Math.abs((long) p.maxX) > 30_000_000
                    || Math.abs((long) p.minZ) > 30_000_000 || Math.abs((long) p.maxZ) > 30_000_000
                    || p.minY < -2048 || p.maxY > 2048
                    || (long) p.maxX - p.minX > 64 || (long) p.maxY - p.minY > 64 || (long) p.maxZ - p.minZ > 64)
                throw new IllegalArgumentException("抽奖池区域/维度无效，每边最多 65 格：" + entry.getKey());
            p.facing(); validateRules(p);
        }
        var values = new ArrayList<>(pools.values());
        for (int i = 0; i < values.size(); i++) for (int j = i + 1; j < values.size(); j++)
            if (values.get(i).overlaps(values.get(j))) throw new IllegalArgumentException("同一维度的抽奖池不可重叠。");
    }

    static void validateRules(PoolRules rules) {
        if (!Double.isFinite(rules.fireworkDistance) || rules.fireworkDistance < 0 || rules.fireworkDistance > 64)
            throw new IllegalArgumentException("烟花左右距离必须为 0 至 64 格内的有限数值。");
        for (double offset : new double[]{rules.displayOffsetX, rules.displayOffsetY, rules.displayOffsetZ})
            if (!Double.isFinite(offset) || Math.abs(offset) > 64)
                throw new IllegalArgumentException("展示偏移量必须为 -64 至 64 格内的有限数值。");
        if (!Double.isFinite(rules.sProbability) || !Double.isFinite(rules.aProbability)
                || rules.sProbability < 0 || rules.aProbability < 0 || rules.sProbability + rules.aProbability > 1)
            throw new IllegalArgumentException("S/A 概率必须非负且总和不超过 1。");
        if (rules.pity < 1 || rules.pity > 1_000_000) throw new IllegalArgumentException("保底应为 1–1000000。");
        if (rules.rewards == null || rules.fixedRewards == null) throw new IllegalArgumentException("缺少奖励表。");
        for (Tier tier : Tier.values()) {
            for (var table : List.of(rules.rewards, rules.fixedRewards)) {
                List<Prize> prizes = table.get(tier);
                if (prizes == null) throw new IllegalArgumentException("缺少 " + tier + " 级奖励表（可为空数组）。");
                long total = 0; var ids = new HashSet<String>();
                for (Prize p : prizes) {
                    if (p == null || p.id == null || !p.id.matches("[A-Za-z0-9_-]{1,64}") || !ids.add(p.id)
                            || p.label == null || p.label.isBlank() || p.label.length() > 256 || p.weight < 1 || p.limit < -1)
                        throw new IllegalArgumentException(tier + " 级奖品 ID、名称、权重或限额无效。");
                    total += p.weight;
                    if (p.rewards == null || p.rewards.isEmpty()) throw new IllegalArgumentException("奖品没有奖励：" + p.id);
                    p.rewards.forEach(GachaConfig::validateReward);
                }
                if (total > Integer.MAX_VALUE) throw new IllegalArgumentException("奖池总权重过大。");
            }
        }
    }

    static void validateReward(Reward reward) {
        if (reward == null || (reward.item == null) == (reward.command == null))
            throw new IllegalArgumentException("奖励必须仅包含 item 或 command 之一。");
        if (reward.item != null && !reward.item.isJsonObject()) throw new IllegalArgumentException("item 必须是物品 JSON 对象。");
        if (reward.command != null && (reward.command.isBlank() || reward.command.startsWith("/")
                || reward.command.length() > 8192 || reward.command.contains("\n") || reward.command.contains("\r")))
            throw new IllegalArgumentException("command 应为单条指令，不带 /，最多 8192 字符。");
    }
}
