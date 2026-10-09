package dev.murphy.gacha;

import com.mojang.serialization.JsonOps;
import dev.murphy.gacha.mixin.ThrowerAccess;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ServerboundCustomClickActionPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.random.RandomGenerator;
import static dev.murphy.gacha.GachaConfig.*;

public final class GachaMod implements DedicatedServerModInitializer {
    static final Logger LOGGER = LoggerFactory.getLogger("gacha");
    private static GachaMod active;
    GachaConfig config;
    PlayerStore store;
    private boolean delivering;
    private final RandomGenerator oddsRandom = new java.security.SecureRandom();
    private final RandomGenerator fxRandom = new java.util.Random();
    private final Map<UUID, Session> sessions = new HashMap<>();
    private final ArrayList<Finished> finished = new ArrayList<>();
    private final Set<UUID> pendingPlayers = new HashSet<>();
    private final Map<UUID, Integer> retryAfter = new HashMap<>();
    private final Map<UUID, Integer> lastUiClick = new HashMap<>();
    private static final class Session {
        UUID player;
        String pool, receipt;
        int elapsed, animationTicks, resultTicks;
        GachaAnimation animation;
        ArrayList<PlayerStore.Draw> draws;
    }
    private static final class Finished {
        String pool;
        int ticks, lifetime;
        GachaAnimation animation;
    }

    @Override public void onInitializeServer() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) -> new GachaCommands(this).register(dispatcher));
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            active = this;
            try {
                loadConfiguration();
                store = new PlayerStore(directory().resolve("players"));
                LOGGER.info("抽奖模组已加载，数据目录：{}", directory());
            } catch (Exception e) {
                store = null; config = null;
                LOGGER.error("抽奖配置无法加载，已停止抽奖；原文件已保留。修复后使用 /gacha reload。", e);
            }
        });
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (entity.isLoadedFromDisk() && entity.entityTags().contains(GachaAnimation.FX_TAG)) entity.discard();
        });
        ServerLifecycleEvents.SYNC_DATA_PACK_CONTENTS.register((player, joined) -> { if (joined) pendingPlayers.add(player.getUUID()); });
        ServerTickEvents.END_SERVER_TICK.register(this::tick);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            sessions.values().forEach(s -> { if (s.animation != null) s.animation.clear(); });
            finished.forEach(f -> f.animation.clear());
            // Outcomes are already journaled. Complete online deliveries before players disconnect.
            sessions.clear(); finished.clear();
            for (ServerPlayer player : server.getPlayerList().getPlayers()) deliverPending(player);
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            active = null; config = null; store = null; pendingPlayers.clear(); retryAfter.clear(); lastUiClick.clear(); delivering = false;
        });
    }

    static GachaMod active() {
        if (active == null) throw new IllegalStateException("抽奖服务尚未启动。");
        return active;
    }
    static Path directory() { return FabricLoader.getInstance().getConfigDir().resolve("gacha"); }
    public static void handleUiClick(ServerPlayer player, ServerboundCustomClickActionPacket packet) {
        if (!UiActions.isOurs(packet.id())) return;
        try {
            GachaMod mod = active();
            int tick = player.level().getServer().getTickCount();
            Integer previous = mod.lastUiClick.get(player.getUUID());
            if (previous != null && tick - previous < 2) return;
            mod.lastUiClick.put(player.getUUID(), tick);
            mod.lastUiClick.entrySet().removeIf(e -> tick - e.getValue() > 1200);
            var request = UiActions.decode(packet);
            var source = player.createCommandSourceStack();
            if (request.action() != UiActions.Action.HELP) {
                mod.available();
                // Always use the actual sender's UUID, never a client-selected player.
                var data = mod.store.load(player.getUUID());
                var lines = request.action() == UiActions.Action.HISTORY_DETAILS
                        ? ChatUi.historyDetails(data, request.page()) : ChatUi.history(data, request.page());
                lines.forEach(player::sendSystemMessage);
            } else ChatUi.help(source, request.page()).forEach(player::sendSystemMessage);
        } catch (Exception e) {
            player.sendSystemMessage(Component.literal(e.getMessage() == null ? "界面操作失败，请查看日志。" : e.getMessage()).withStyle(ChatFormatting.RED));
            if (!(e instanceof IllegalArgumentException)) LOGGER.error("抽奖界面操作失败", e);
        }
    }
    void available() {
        if (store == null || config == null) throw new IllegalArgumentException("抽奖服务不可用，请检查日志或执行 /gacha reload。");
        if (delivering) throw new IllegalArgumentException("奖品指令不能嵌套执行抽奖指令。");
    }
    private void loadConfiguration() throws IOException {
        Path path = directory().resolve("config.json");
        GachaConfig next = Files.exists(path) ? JsonFiles.read(path, GachaConfig.class) : GachaConfig.defaults();
        next.validate();
        if (!Files.exists(path)) JsonFiles.write(path, next);
        config = next;
    }
    void reload() throws IOException {
        if (delivering) throw new IllegalArgumentException("发奖时不可重载配置。");
        loadConfiguration();
        if (store == null) store = new PlayerStore(directory().resolve("players"));
    }
    GachaConfig editable() {
        available();
        return JsonFiles.GSON.fromJson(JsonFiles.GSON.toJson(config), GachaConfig.class);
    }
    void saveConfig(GachaConfig next) throws IOException {
        next.validate(); JsonFiles.write(directory().resolve("config.json"), next); config = next;
    }
    boolean busyPlayer(UUID uuid) { return sessions.containsKey(uuid); }
    boolean busyPool(String pool) { return sessions.values().stream().anyMatch(s -> s.pool.equals(pool)); }
    void requireIdlePool(String pool) {
        if (busyPool(pool)) throw new IllegalArgumentException("此抽奖池正在播放动画，结束后再修改。");
    }

    private void tick(MinecraftServer server) {
        if (config == null || store == null) return;
        retryAfter.entrySet().removeIf(e -> server.getTickCount() >= e.getValue());
        for (var entry : config.pools.entrySet()) {
            Pool pool = entry.getValue();
            ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, Identifier.parse(pool.dimension)));
            if (level == null) continue;
            AABB box = new AABB(pool.minX, pool.minY, pool.minZ, pool.maxX + 1.0, pool.maxY + 1.0, pool.maxZ + 1.0);
            for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, box, e -> Tickets.draws(e.getItem()) != 0)) {
                if (!item.isRemoved() && pool.contains(item.getX(), item.getY(), item.getZ()) && !retryAfter.containsKey(item.getUUID()))
                    handleTicket(server, level, entry.getKey(), item);
            }
        }
        var iterator = sessions.values().iterator();
        while (iterator.hasNext()) {
            Session session = iterator.next();
            session.elapsed++;
            if (session.elapsed < session.animationTicks) {
                if (session.animation != null) try { session.animation.tick(session.elapsed, fxRandom); }
                catch (RuntimeException e) { LOGGER.warn("抽奖动画异常，奖励仍会按时发放。", e); session.animation.clear(); session.animation = null; }
                continue;
            }
            if (session.animation != null) {
                try { session.animation.reveal(session.draws); }
                catch (RuntimeException e) { LOGGER.warn("结果特效异常，奖励仍会发放。", e); }
                Finished f = new Finished(); f.pool = session.pool; f.animation = session.animation;
                f.lifetime = Math.max(3, session.resultTicks); finished.add(f);
            }
            iterator.remove();
            ServerPlayer player = server.getPlayerList().getPlayer(session.player);
            pendingPlayers.add(session.player);
            if (player != null) {
                player.sendSystemMessage(ChatUi.result(session.draws));
                deliverPending(player);
            }
        }
        finished.removeIf(f -> {
            f.animation.tickRevealed();
            if (++f.ticks >= f.lifetime) { f.animation.clear(); return true; }
            return false;
        });
        if (server.getTickCount() % 20 == 0) {
            for (UUID uuid : Set.copyOf(pendingPlayers)) {
                ServerPlayer player = server.getPlayerList().getPlayer(uuid);
                if (player != null) deliverPending(player);
            }
        }
    }

    private void handleTicket(MinecraftServer server, ServerLevel level, String pool, ItemEntity entity) {
        var thrower = ((ThrowerAccess) entity).gacha$thrower();
        if (thrower == null) return; // Droppers, death drops and overflow rewards are not player throws.
        UUID uuid = thrower.getUUID();
        if (entity.getOwner() != null && !(entity.getOwner() instanceof ServerPlayer)) return;
        ServerPlayer player = server.getPlayerList().getPlayer(uuid);
        try {
            PlayerStore.Data data = store.load(uuid);
            if (PlayerStore.findTicket(data, entity.getUUID()) != null) {
                entity.discard(); pendingPlayers.add(uuid); return;
            }
            if (player == null || busyPlayer(uuid) || busyPool(pool)) {
                refund(level, entity, data);
                if (player != null) {
                    deliverPending(player);
                    player.sendSystemMessage(Component.literal("抽奖尚未结束，抽奖券已退回。").withStyle(ChatFormatting.YELLOW));
                }
                return;
            }
            try { Rewards.validateAll(player, config); }
            catch (RuntimeException e) {
                refund(level, entity, data); deliverPending(player);
                player.sendSystemMessage(Component.literal("奖池配置未就绪，抽奖券已退回，请联系管理员。").withStyle(ChatFormatting.RED));
                LOGGER.warn("拒绝抽奖：{}", e.getMessage()); return;
            }
            int draws = Tickets.draws(entity.getItem());
            GachaConfig.Reward remainder = entity.getItem().getCount() > 1
                    ? Rewards.encode(player, entity.getItem().copyWithCount(entity.getItem().getCount() - 1)) : null;
            var results = DrawEngine.roll(config, data.misses, draws, oddsRandom);
            PlayerStore.Receipt receipt = store.reserve(data, player.getGameProfile().name(), entity.getUUID(), pool, results, remainder);
            // The whole entity is consumed; only one ticket is used and the remainder is journaled for return.
            entity.discard();
            Session session = new Session(); session.player = uuid; session.pool = pool; session.receipt = receipt.id;
            session.animationTicks = config.animationTicks; session.resultTicks = config.resultTicks;
            session.draws = new ArrayList<>(receipt.draws); sessions.put(uuid, session);
            finished.removeIf(f -> { if (f.pool.equals(pool)) { f.animation.clear(); return true; } return false; });
            try { session.animation = new GachaAnimation(level, config.pools.get(pool), draws); }
            catch (RuntimeException e) { LOGGER.warn("展示实体无法创建，抽奖结果仍会在动画时长后发放。", e); }
            if (remainder != null) deliverAction(player, data, receipt, 0);
            player.sendSystemMessage(Component.literal("开始" + (draws == 10 ? "十连抽奖" : "抽奖") + "，请等待结果……").withStyle(ChatFormatting.GOLD));
        } catch (Exception e) {
            retryAfter.put(entity.getUUID(), server.getTickCount() + 100);
            LOGGER.error("处理抽奖券失败，玩家 {}，投掷物 {}。未消费的投掷物保留。", uuid, entity.getUUID(), e);
            if (player != null) player.sendSystemMessage(Component.literal("抽奖数据读写异常，请联系管理员。已确定的奖品会保留在领取记录中。").withStyle(ChatFormatting.RED));
        }
    }
    private void refund(ServerLevel level, ItemEntity entity, PlayerStore.Data data) throws IOException {
        var item = ItemStack.CODEC.encodeStart(level.registryAccess().createSerializationContext(JsonOps.INSTANCE), entity.getItem()).getOrThrow();
        store.refund(data, entity.getUUID(), Reward.item(item));
        entity.discard(); pendingPlayers.add(UUID.fromString(data.uuid));
    }
    private void deliverAction(ServerPlayer player, PlayerStore.Data data, PlayerStore.Receipt receipt, int index) throws IOException {
        PlayerStore.Action action = receipt.actions.get(index);
        if (action.status != PlayerStore.Status.READY) return;
        // Persist before touching inventories or executing commands; uncertain actions require manual review.
        action.status = PlayerStore.Status.SENDING; store.save(data);
        try {
            delivering = true;
            Rewards.deliver(player, action.reward);
            action.status = PlayerStore.Status.DONE; action.detail = "已发放";
        } catch (RuntimeException e) {
            action.status = PlayerStore.Status.FAILED; action.detail = e.getMessage();
            LOGGER.error("发奖异常：玩家 {}，记录 {}，动作 {}，请核查后使用 /gacha recover。", player.getUUID(), receipt.id, index + 1, e);
            player.sendSystemMessage(Component.literal("部分奖励发放异常，请联系管理员；记录 " + receipt.id + "，动作 " + (index + 1)).withStyle(ChatFormatting.RED));
        } finally { delivering = false; }
        store.save(data);
    }
    void deliverPending(ServerPlayer player) {
        if (store == null) return;
        // A dead player's inventory may be cleared during respawn. Leave READY actions for the living replacement.
        if (!player.isAlive() || player.level().getServer().getPlayerList().getPlayer(player.getUUID()) != player) return;
        try {
            var data = store.load(player.getUUID());
            for (var receipt : data.receipts) {
                if (sessions.values().stream().anyMatch(s -> s.receipt.equals(receipt.id))) continue;
                for (int i = 0; i < receipt.actions.size(); i++) {
                    if (!player.isAlive() || player.level().getServer().getPlayerList().getPlayer(player.getUUID()) != player) return;
                    deliverAction(player, data, receipt, i);
                }
            }
            boolean ready = data.receipts.stream().flatMap(r -> r.actions.stream()).anyMatch(a -> a.status == PlayerStore.Status.READY);
            if (!ready) pendingPlayers.remove(player.getUUID());
        } catch (Exception e) {
            pendingPlayers.remove(player.getUUID());
            LOGGER.error("待领取奖励处理失败：{}。请检查玩家文件，修复后用 /gacha pending 重试。", player.getUUID(), e);
            player.sendSystemMessage(Component.literal("待领取奖励数据异常，请联系管理员；原数据已保留。").withStyle(ChatFormatting.RED));
        }
    }
    void queueDelivery(UUID uuid) { pendingPlayers.add(uuid); }
}
