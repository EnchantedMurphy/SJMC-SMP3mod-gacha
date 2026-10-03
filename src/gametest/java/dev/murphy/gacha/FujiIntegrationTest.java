package dev.murphy.gacha;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/** Opt-in, disposable dedicated-server test. This source set is never packaged in the mod JAR. */
public final class FujiIntegrationTest implements DedicatedServerModInitializer {
    private ServerPlayer player;
    private GachaConfig.Pool pool;
    private BigDecimal coffeeBefore, tongbaoBefore;
    private int elapsed;
    private boolean ended;
    @Override public void onInitializeServer() {
        if (!Boolean.getBoolean("gacha.fujiIntegration")) return;
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            try {
                if (!FabricLoader.getInstance().isModLoaded("fuji")) throw new IllegalStateException("Fuji is missing");
                var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "Fuji" + UUID.randomUUID().toString().substring(0, 6)), false);
                player = new ServerPlayer(server, server.overworld(), cookie.gameProfile(), cookie.clientInformation());
                Connection connection = new Connection(PacketFlow.SERVERBOUND); new EmbeddedChannel(connection);
                server.getPlayerList().placeNewPlayer(connection, player, cookie);
                player.setGameMode(GameType.CREATIVE); player.setPos(0.5, 81, 0.5); player.setNoGravity(true);
                server.overworld().getChunk(0, 0);
                pool = new GachaConfig.Pool(); pool.dimension = "minecraft:overworld";
                pool.minX = pool.minZ = 4; pool.maxX = pool.maxZ = 6; pool.minY = 80; pool.maxY = 82;
                coffeeBefore = balance(player, "fuji:coffee_stamp"); tongbaoBefore = balance(player, "fuji:tongbao");
                GachaConfig config = GachaConfig.defaults(); config.sProbability = 1; config.aProbability = 0;
                config.animationTicks = 20; config.resultTicks = 5; config.pools.put("fuji_test", pool);
                GachaMod.active().saveConfig(config); drop();
            } catch (Exception e) { finish(server, false, e); }
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (ended || player == null) return;
            try {
                if (++elapsed == 35) {
                    require(player.getInventory().contains(stack -> stack.is(Items.MACE)), "S bundle must give a mace");
                    require(balance(player, "fuji:coffee_stamp").subtract(coffeeBefore).compareTo(BigDecimal.valueOf(20)) == 0,
                            "Coffee stamp balance must increase by twenty");
                    GachaConfig config = GachaConfig.defaults(); config.sProbability = config.aProbability = 0;
                    config.animationTicks = 20; config.resultTicks = 5; config.pools.put("fuji_test", pool);
                    GachaMod.active().saveConfig(config); drop();
                }
                if (elapsed == 70) {
                    require(balance(player, "fuji:tongbao").subtract(tongbaoBefore).compareTo(BigDecimal.valueOf(20)) == 0,
                            "Tongbao balance must increase by twenty");
                    var data = GachaMod.active().store.load(player.getUUID());
                    require(data.totalDraws == 2, "Both real server draws must be recorded");
                    require(data.receipts.stream().flatMap(r -> r.actions.stream()).allMatch(a -> a.status == PlayerStore.Status.DONE),
                            "Every real Fuji action must be delivered");
                    finish(server, true, null);
                }
            } catch (Exception e) { finish(server, false, e); }
        });
    }
    private void drop() {
        var entity = player.drop(Tickets.create(1, 1), true, net.minecraft.util.Prediction.SERVER_ONLY);
        if (entity == null) throw new IllegalStateException("Ticket drop failed");
        entity.setPos(4.5, 80.5, 4.5); entity.setNoGravity(true); entity.setDeltaMovement(0, 0, 0); entity.setPickUpDelay(1000);
    }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
    private void finish(MinecraftServer server, boolean success, Exception error) {
        if (ended) return; ended = true;
        try {
            JsonFiles.write(FabricLoader.getInstance().getGameDir().resolve("fuji-test-result.json"), Map.of(
                    "success", success, "fujiVersion", "14.12.0", "coffeeStampDelta", success ? 20 : 0,
                    "tongbaoDelta", success ? 20 : 0, "maceGiven", success, "detail", error == null ? "All integration checks passed" : error.toString()));
            GachaMod.active().saveConfig(GachaConfig.defaults());
        } catch (Exception e) { GachaMod.LOGGER.error("Integration result could not be saved", e); }
        if (success) GachaMod.LOGGER.info("FUJI INTEGRATION PASSED: mace + coffee stamp 20; tongbao 20.");
        else GachaMod.LOGGER.error("FUJI INTEGRATION FAILED", error);
        server.halt(false);
    }
    /** Only tests reflect Fuji's API; production rewards use ordinary console commands. */
    private static BigDecimal balance(ServerPlayer player, String id) throws Exception {
        Class<?> identifiers = Class.forName("mod.fuji.core.structure.IdentifierIR");
        Object identifier = identifiers.getMethod("makeIdentifierOrThrow", String.class).invoke(null, id);
        Class<?> service = Class.forName("mod.fuji.module.initializer.economy.service.EconomyService");
        Object account = service.getMethod("tryGetEconomyAccount", net.minecraft.commands.CommandSourceStack.class, GameProfile.class, identifiers)
                .invoke(null, Rewards.source(player), player.getGameProfile(), identifier);
        Object raw = service.getMethod("getRawValue", Class.forName("eu.pb4.common.economy.api.EconomyAccount")).invoke(null, account);
        return (BigDecimal) service.getMethod("toFaceValue", java.math.BigInteger.class).invoke(null, raw);
    }
}
