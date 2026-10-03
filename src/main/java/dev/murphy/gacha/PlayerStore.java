package dev.murphy.gacha;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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
        int schemaVersion = 1;
        String uuid, lastName;
        int misses;
        long totalDraws;
        List<Receipt> receipts = new ArrayList<>();
    }
    private final Path directory;
    PlayerStore(Path directory) throws IOException { this.directory = directory; Files.createDirectories(directory); }
    private Path file(UUID uuid) { return directory.resolve(uuid + ".json"); }

    Data load(UUID uuid) throws IOException {
        if (!Files.exists(file(uuid))) { Data d = new Data(); d.uuid = uuid.toString(); return d; }
        Data d = JsonFiles.read(file(uuid), Data.class);
        try {
            if (d.schemaVersion != 1 || !uuid.toString().equals(d.uuid) || d.misses < 0 || d.totalDraws < 0 || d.receipts == null)
                throw new IllegalArgumentException("无效玩家数据");
            long total = 0;
            var ids = new java.util.HashSet<String>();
            var tickets = new java.util.HashSet<String>();
            for (Receipt r : d.receipts) {
                UUID.fromString(r.id);
                UUID.fromString(r.ticketEntity);
                Instant.parse(r.timestamp);
                if (!ids.add(r.id) || !tickets.add(r.ticketEntity) || r.draws == null || r.actions == null || r.actions.isEmpty())
                    throw new IllegalArgumentException("无效领取记录");
                if (r.refund && !r.draws.isEmpty() || !r.refund && r.draws.size() != 1 && r.draws.size() != 10)
                    throw new IllegalArgumentException("无效抽奖记录");
                for (Draw draw : r.draws) {
                    if (draw.number != ++total || draw.tier == null || draw.label == null || draw.prizeId == null)
                        throw new IllegalArgumentException("抽奖历史损坏");
                }
                for (Action a : r.actions) {
                    if (a == null || a.status == null) throw new IllegalArgumentException("领取状态损坏");
                    validateReward(a.reward);
                }
            }
            if (total != d.totalDraws) throw new IllegalArgumentException("历史计数不一致");
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
        if (remainder != null) r.actions.add(action(remainder));
        for (var result : batch.results()) {
            Draw draw = new Draw(); draw.number = ++d.totalDraws;
            draw.tier = result.tier(); draw.prizeId = result.prize().id; draw.label = result.prize().label;
            draw.hardPity = result.hardPity(); draw.tenGuarantee = result.tenGuarantee();
            r.draws.add(draw);
            result.prize().rewards.forEach(reward -> r.actions.add(action(reward)));
        }
        d.misses = batch.missesAfter(); d.lastName = playerName; d.receipts.add(r); save(d);
        return r;
    }
    Receipt refund(Data d, UUID entity, Reward reward) throws IOException {
        Receipt existing = findTicket(d, entity);
        if (existing != null) return existing;
        Receipt r = new Receipt(); r.ticketEntity = entity.toString(); r.refund = true;
        r.actions.add(action(reward)); d.receipts.add(r); save(d); return r;
    }
}
