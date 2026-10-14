package dev.murphy.gacha;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.UUID;
import static dev.murphy.gacha.GachaConfig.*;

/** Server-thread-only journal; each change reaches disk before an external reward is issued. */
final class PlayerStore {
    enum Status { READY, SENDING, DONE, FAILED }
    static final class Action {
        Reward reward;
        Status status = Status.READY;
        String detail = "";
    }
    static final class Draw {
        long number;
        Tier tier;
        String prizeId, label;
        boolean hardPity, tenGuarantee;
    }
    static final class Receipt {
        String id = UUID.randomUUID().toString();
        String ticketEntity;
        String timestamp = Instant.now().toString();
        String pool;
        boolean refund;
        List<Draw> draws = new ArrayList<>();
        List<Action> actions = new ArrayList<>();
    }
    static final class Data {
        int schemaVersion = 2;
        String uuid, lastName;
        Map<String, PoolData> pools = new LinkedHashMap<>();
        List<Receipt> receipts = new ArrayList<>();
    }
    static final class PoolData {
        int misses;
        long totalDraws;
        Map<String, Long> obtained = new LinkedHashMap<>();
    }
    static PoolData state(Data data, String pool) {
        return data.pools.computeIfAbsent(pool, ignored -> new PoolData());
    }
    private final Path directory;
    PlayerStore(Path directory) throws IOException { this.directory = directory; Files.createDirectories(directory); }
    private Path file(UUID uuid) { return directory.resolve(uuid + ".json"); }

    Data load(UUID uuid) throws IOException {
        if (!Files.exists(file(uuid))) { Data d = new Data(); d.uuid = uuid.toString(); return d; }
        Data d = JsonFiles.read(file(uuid), Data.class);
        try {
            if (d.schemaVersion != 2 || !uuid.toString().equals(d.uuid) || d.pools == null || d.receipts == null)
                throw new IllegalArgumentException("无效玩家数据");
            Map<String, PoolData> computed = new LinkedHashMap<>();
            var ids = new java.util.HashSet<String>();
            var tickets = new java.util.HashSet<String>();
            for (Receipt r : d.receipts) {
                UUID.fromString(r.id);
                UUID.fromString(r.ticketEntity);
                Instant.parse(r.timestamp);
                if (!ids.add(r.id) || !tickets.add(r.ticketEntity) || r.draws == null || r.actions == null || r.pool == null || !r.pool.matches("[A-Za-z0-9_-]{1,32}"))
                    throw new IllegalArgumentException("无效领取记录");
                if (r.refund && (!r.draws.isEmpty() || r.actions.isEmpty()) || !r.refund && r.draws.size() != 1 && r.draws.size() != 10)
                    throw new IllegalArgumentException("无效抽奖记录");
                PoolData pool = r.refund ? null : computed.computeIfAbsent(r.pool, ignored -> new PoolData());
                for (Draw draw : r.draws) {
                    if (draw.number != ++pool.totalDraws || draw.tier == null || draw.label == null)
                        throw new IllegalArgumentException("抽奖历史损坏");
                    pool.misses = draw.tier == Tier.S ? 0 : Math.addExact(pool.misses, 1);
                    if (draw.prizeId != null) pool.obtained.merge(prizeKey(draw.tier, draw.prizeId), 1L, Math::addExact);
                }
                for (Action a : r.actions) {
                    if (a == null || a.status == null) throw new IllegalArgumentException("领取状态损坏");
                    validateReward(a.reward);
                }
            }
            for (var entry : d.pools.entrySet()) {
                PoolData saved = entry.getValue(), expected = computed.getOrDefault(entry.getKey(), new PoolData());
                if (!entry.getKey().matches("[A-Za-z0-9_-]{1,32}") || saved == null || saved.obtained == null
                        || saved.totalDraws != expected.totalDraws || saved.misses != expected.misses
                        || !saved.obtained.equals(expected.obtained)) throw new IllegalArgumentException("奖池历史或限额计数不一致");
            }
            if (!d.pools.keySet().containsAll(computed.keySet())) throw new IllegalArgumentException("奖池历史计数缺失");
        } catch (RuntimeException e) { throw new IOException("玩家数据损坏，原文件未覆盖：" + file(uuid), e); }
        return d;
    }

    void save(Data d) throws IOException { JsonFiles.write(file(UUID.fromString(d.uuid)), d); }

    static Receipt findTicket(Data d, UUID entity) {
        return d.receipts.stream().filter(r -> r.ticketEntity.equals(entity.toString())).findFirst().orElse(null);
    }
    static Receipt receipt(Data d, String id) {
        return d.receipts.stream().filter(r -> r.id.equals(id)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("找不到领取记录：" + id));
    }
    static Action action(Reward reward) {
        Action action = new Action();
        action.reward = JsonFiles.GSON.fromJson(JsonFiles.GSON.toJson(reward), Reward.class);
        return action;
    }
    Receipt reserve(Data d, String playerName, UUID entity, String pool, DrawEngine.Batch batch, Reward remainder) throws IOException {
        if (findTicket(d, entity) != null) throw new IllegalArgumentException("该投掷物已经处理。");
        Receipt r = new Receipt(); r.ticketEntity = entity.toString(); r.pool = pool;
        PoolData poolData = state(d, pool);
        if (remainder != null) r.actions.add(action(remainder));
        for (var result : batch.results()) {
            Draw draw = new Draw(); draw.number = ++poolData.totalDraws;
            draw.tier = result.tier();
            List<String> labels = new ArrayList<>();
            if (result.prize() != null) {
                draw.prizeId = result.prize().id; labels.add(result.prize().label);
                poolData.obtained.merge(prizeKey(draw.tier, draw.prizeId), 1L, Math::addExact);
                result.prize().rewards.forEach(reward -> r.actions.add(action(reward)));
            }
            for (Prize fixed : result.fixedRewards()) {
                labels.add(fixed.label); fixed.rewards.forEach(reward -> r.actions.add(action(reward)));
            }
            draw.label = labels.isEmpty() ? "无奖励" : String.join(" + ", labels);
            draw.hardPity = result.hardPity(); draw.tenGuarantee = result.tenGuarantee();
            r.draws.add(draw);
        }
        poolData.misses = batch.missesAfter(); d.lastName = playerName; d.receipts.add(r); save(d);
        return r;
    }
    Receipt refund(Data d, UUID entity, String pool, Reward reward) throws IOException {
        Receipt existing = findTicket(d, entity);
        if (existing != null) return existing;
        Receipt r = new Receipt(); r.ticketEntity = entity.toString(); r.pool = pool; r.refund = true;
        r.actions.add(action(reward)); d.receipts.add(r); save(d); return r;
    }
}
