package dev.murphy.gacha;

import com.mojang.authlib.GameProfile;
import dev.murphy.gacha.mixin.BlockDisplayAccess;
import io.netty.channel.embedded.EmbeddedChannel;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import static dev.murphy.gacha.GachaConfig.*;

public final class GachaGameTest {
    private static final class RecordingPlayer extends ServerPlayer {
        final List<Component> messages = new ArrayList<>();
        RecordingPlayer(MinecraftServer server, ServerLevel level, CommonListenerCookie cookie) {
            super(server, level, cookie.gameProfile(), cookie.clientInformation());
        }
        @Override public PermissionSet permissions() { return PermissionSet.NO_PERMISSIONS; }
        @Override public void sendSystemMessage(Component message) { messages.add(message); }
        @Override public void sendSystemMessage(Component message, boolean overlay) { messages.add(message); }
        boolean saw(String text) { return messages.stream().anyMatch(m -> m.getString().contains(text)); }
    }
    private static RecordingPlayer join(GameTestHelper helper, GameProfile profile) {
        var cookie = CommonListenerCookie.createInitial(profile, false);
        var player = new RecordingPlayer(helper.getLevel().getServer(), helper.getLevel(), cookie);
        Connection connection = new Connection(PacketFlow.SERVERBOUND); new EmbeddedChannel(connection);
        helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        return player;
    }
    private static GameProfile profile(String prefix) { return new GameProfile(UUID.randomUUID(), prefix + UUID.randomUUID().toString().substring(0, 6)); }
    private static void leave(RecordingPlayer player) {
        player.level().getServer().getPlayerList().remove(player);
        player.connection.disconnect(Component.literal("Gacha test disconnect"));
    }
    private static int count(RecordingPlayer player, Item item) {
        int total = 0; for (int i = 0; i < 36; i++) if (player.getInventory().getItem(i).is(item)) total += player.getInventory().getItem(i).getCount();
        return total;
    }
    private static int tickets(RecordingPlayer player, int draws) {
        int total = 0; for (int i = 0; i < 36; i++) {
            var stack = player.getInventory().getItem(i); if (Tickets.draws(stack) == draws) total += stack.getCount();
        }
        return total;
    }
    private static ItemEntity drop(RecordingPlayer player, Pool pool, ItemStack stack) {
        ItemEntity entity = player.drop(stack, true, net.minecraft.util.Prediction.SERVER_ONLY);
        if (entity == null) throw new IllegalStateException("Player.drop returned null");
        entity.setPos(pool.minX + 0.5, pool.minY + 0.5, pool.minZ + 0.5);
        entity.setNoGravity(true); entity.setDeltaMovement(0, 0, 0); entity.setPickUpDelay(1000);
        return entity;
    }
    private static List<Display.BlockDisplay> displays(ServerLevel level) {
        List<Display.BlockDisplay> list = new ArrayList<>();
        for (var entity : level.getAllEntities()) if (entity instanceof Display.BlockDisplay display && display.entityTags().contains(GachaAnimation.FX_TAG)) list.add(display);
        return list;
    }
    private static void click(RecordingPlayer player, net.minecraft.network.chat.ClickEvent.Custom action) {
        player.messages.clear();
        var json = net.minecraft.network.chat.ClickEvent.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE, action).getOrThrow();
        var received = (net.minecraft.network.chat.ClickEvent.Custom) net.minecraft.network.chat.ClickEvent.CODEC
                .parse(com.mojang.serialization.JsonOps.INSTANCE, json).getOrThrow();
        player.connection.handleCustomClickAction(new net.minecraft.network.protocol.common.ServerboundCustomClickActionPacket(received.id(), received.payload()));
    }
    @FunctionalInterface private interface Checked { void run() throws Exception; }
    private static void checked(Checked action) {
        try { action.run(); } catch (RuntimeException e) { throw e; } catch (Exception e) { throw new IllegalStateException(e); }
    }

    @GameTest(maxTicks = 400)
    public void ticketsAnimationRefundHistoryAndReconnect(GameTestHelper helper) {
        var mod = GachaMod.active(); var server = helper.getLevel().getServer();
        var original = mod.config;
        var config = GachaConfig.defaults(); config.sProbability = 1; config.aProbability = 0; config.pools.clear();
        config.resultTicks = 20;
        var diamond = GachaConfig.defaults().rewards.get(Tier.A).getFirst().rewards.getFirst();
        config.rewards.put(Tier.S, new ArrayList<>(List.of(Prize.of("bundle", "钻石+绿宝石", diamond, Reward.command("give %player% minecraft:emerald 1")))));
        config.rewards.put(Tier.B, new ArrayList<>(List.of(Prize.of("iron", "铁锭", GachaConfig.defaults().rewards.get(Tier.A).getLast().rewards.getFirst()))));
        checked(() -> mod.saveConfig(config));
        BlockPos anchor = helper.absolutePos(new BlockPos(3, 2, 3));
        String bounds = anchor.getX() + " " + anchor.getY() + " " + anchor.getZ() + " "
                + (anchor.getX() + 2) + " " + (anchor.getY() + 1) + " " + (anchor.getZ() + 2);
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withLevel(helper.getLevel()), "gacha pool set test " + bounds);
        Pool pool = mod.config.pools.get("test"); helper.assertTrue(pool != null, "operator can save cuboid pool with coordinates");
        var firstProfile = profile("Lotto"); RecordingPlayer[] first = {join(helper, firstProfile)};
        RecordingPlayer second = join(helper, profile("Other"));
        first[0].setPos(anchor.getX() - 3, anchor.getY(), anchor.getZ()); second.setPos(anchor.getX() - 4, anchor.getY(), anchor.getZ());
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "gacha give " + second.getGameProfile().name() + " ten 1");
        helper.assertTrue(tickets(second, 10) == 1, "admin gives vanilla ticket through command");
        ItemEntity stack = drop(first[0], pool, Tickets.create(1, 3));
        ItemEntity[] extra = new ItemEntity[2];
        float health = first[0].getHealth();
        helper.onEachTick(() -> {
            for (var display : displays(helper.getLevel())) {
                var state = ((BlockDisplayAccess) display).gacha$state();
                helper.assertTrue(state.is(Blocks.CONCRETE.blue()) || state.is(Blocks.CONCRETE.yellow()) || state.is(Blocks.CONCRETE.red()),
                        "every animation frame only uses blue, gold/yellow or red concrete");
            }
        });

        helper.runAtTickTime(6, () -> checked(() -> {
            helper.assertTrue(stack.isRemoved(), "ticket entity consumed after persistent reservation");
            helper.assertTrue(tickets(first[0], 1) == 2, "stack throws consume one and refund the other two");
            helper.assertTrue(count(first[0], Items.DIAMOND) == 0, "prizes wait for reveal");
            helper.assertTrue(displays(helper.getLevel()).size() == 1, "single draw displays one miniature vanilla entity");
            extra[0] = drop(first[0], pool, Tickets.create(10, 1));
            extra[1] = drop(second, pool, Tickets.create(1, 2));
        }));
        helper.runAtTickTime(14, () -> checked(() -> {
            helper.assertTrue(extra[0].isRemoved() && extra[1].isRemoved(), "busy tickets disappear from region");
            helper.assertTrue(tickets(first[0], 10) == 1 && tickets(second, 1) == 2, "busy refunds go to both owners");
            helper.assertTrue(mod.store.load(firstProfile.id()).totalDraws == 1, "busy returns do not increment draw counter");
            helper.assertTrue(mod.store.load(second.getUUID()).totalDraws == 0, "player histories remain independent");
            // No throwing owner means a hopper or dropper cannot trigger a draw.
            ItemEntity unowned = new ItemEntity(helper.getLevel(), pool.minX + 0.5, pool.minY + 0.5, pool.minZ + 0.5, Tickets.create(1, 1));
            unowned.setNoGravity(true); unowned.setPickUpDelay(1000); helper.getLevel().addFreshEntity(unowned);
            helper.runAfterDelay(4, () -> { helper.assertTrue(!unowned.isRemoved(), "unowned item is ignored"); unowned.discard(); });
        }));
        helper.runAtTickTime(108, () -> checked(() -> {
            helper.assertTrue(count(first[0], Items.DIAMOND) == 1 && count(first[0], Items.EMERALD) == 1, "item and command bundle deliver at reveal");
            var visible = displays(helper.getLevel()); helper.assertTrue(visible.size() == 1, "result remains briefly visible");
            helper.assertTrue(((BlockDisplayAccess) visible.getFirst()).gacha$state().is(Blocks.CONCRETE.red()), "S display metadata is red concrete");
            helper.assertTrue(first[0].getHealth() == health, "cosmetic fireworks never damage player");
            var data = mod.store.load(firstProfile.id());
            helper.assertTrue(data.receipts.stream().flatMap(r -> r.actions.stream()).allMatch(a -> a.status == PlayerStore.Status.DONE), "all rewards and returned tickets journaled done");
        }));
        helper.runAtTickTime(128, () -> {
            helper.assertTrue(displays(helper.getLevel()).isEmpty(), "old result entities cleaned up");
            drop(first[0], pool, Tickets.create(10, 1));
        });
        helper.runAtTickTime(136, () -> {
            helper.assertTrue(displays(helper.getLevel()).size() == 10, "ten draws use two rows of five entities");
            leave(first[0]);
        });
        helper.runAtTickTime(240, () -> checked(() -> {
            var data = new PlayerStore(GachaMod.directory().resolve("players")).load(firstProfile.id());
            helper.assertTrue(data.totalDraws == 11, "all outcomes persist before offline reveal");
            helper.assertTrue(data.receipts.getLast().actions.stream().allMatch(a -> a.status == PlayerStore.Status.READY), "offline prizes remain claimable");
            first[0] = join(helper, firstProfile);
            first[0].setPos(anchor.getX() - 3, anchor.getY(), anchor.getZ());
        }));
        helper.runAtTickTime(270, () -> checked(() -> {
            var data = mod.store.load(firstProfile.id());
            helper.assertTrue(data.receipts.getLast().actions.stream().allMatch(a -> a.status == PlayerStore.Status.DONE), "reconnect completes offline deliveries");
            helper.assertTrue(count(first[0], Items.DIAMOND) >= 10 && count(first[0], Items.EMERALD) >= 10, "ten item and command prizes restored");
            first[0].messages.clear();
            server.getCommands().performPrefixedCommand(first[0].createCommandSourceStack(), "gacha history");
            helper.assertTrue(first[0].saw("第 1/2 页"), "history has ten rows per page");
            helper.assertTrue(first[0].messages.stream().filter(m -> m.getString().startsWith("#")).count() == 10, "first page has ten draw rows");
            var footer = first[0].messages.getLast();
            var next = footer.getSiblings().getLast().getStyle().getClickEvent();
            helper.assertTrue(next instanceof net.minecraft.network.chat.ClickEvent.Custom, "next page uses custom click, avoiding command confirmation");
            click(first[0], (net.minecraft.network.chat.ClickEvent.Custom) next);
            helper.assertTrue(first[0].messages.stream().filter(m -> m.getString().startsWith("#")).count() == 1, "second page has the remaining draw");
            helper.assertTrue(first[0].saw("第 2/2 页") && first[0].saw("距上次S第1抽"), "real custom packet opens sender history with pity-cycle count");
        }));
        helper.runAtTickTime(274, () -> {
            var previous = first[0].messages.getLast().getSiblings().getFirst().getStyle().getClickEvent();
            helper.assertTrue(previous instanceof net.minecraft.network.chat.ClickEvent.Custom, "previous page uses custom click");
            click(first[0], (net.minecraft.network.chat.ClickEvent.Custom) previous);
            helper.assertTrue(first[0].saw("第 1/2 页"), "previous page packet navigates back");
        });
        helper.runAtTickTime(278, () -> {
            click(first[0], UiActions.help(2));
            helper.assertTrue(first[0].saw("该帮助页不可访问"), "forged admin help click rechecks current player permissions");
        });
        helper.runAtTickTime(282, () -> {
            click(second, UiActions.history(1));
            helper.assertTrue(second.saw("共 0 抽") && second.saw("尚无抽奖记录"), "history click cannot read another player's history");
            server.getCommands().performPrefixedCommand(first[0].createCommandSourceStack(), "gacha pity");
            helper.assertTrue(first[0].saw("最多再抽 80"), "normal player can see pity reset");
            helper.assertTrue(displays(helper.getLevel()).isEmpty(), "ten animation cleanup completes");
            leave(first[0]); leave(second); checked(() -> mod.saveConfig(original)); helper.succeed();
        });
    }

    @GameTest(maxTicks = 20)
    public void fullInventoryDropsOverflowWithoutThrower(GameTestHelper helper) {
        var player = join(helper, profile("Full"));
        BlockPos position = helper.absolutePos(new BlockPos(1, 2, 1));
        player.setPos(position.getX(), position.getY(), position.getZ());
        for (int i = 0; i < 36; i++) player.getInventory().setItem(i, new ItemStack(Items.STONE, 64));
        Rewards.give(player, Tickets.create(10, 1));
        var drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(2), e -> Tickets.draws(e.getItem()) == 10);
        helper.assertTrue(drops.size() == 1, "full inventory gives returned ticket as overflow");
        helper.assertTrue(drops.getFirst().getOwner() == null, "overflow ticket has no thrower and cannot retrigger");
        helper.assertTrue(count(player, Items.STONE) == 36 * 64, "overflow does not overwrite existing inventory");
        drops.getFirst().discard();
        checked(() -> {
            var mod = GachaMod.active(); var data = mod.store.load(player.getUUID());
            var receipt = mod.store.refund(data, UUID.randomUUID(), Rewards.encode(player, new ItemStack(Items.DIAMOND)));
            player.setHealth(0); mod.deliverPending(player);
            helper.assertTrue(mod.store.load(player.getUUID()).receipts.getLast().actions.getFirst().status == PlayerStore.Status.READY,
                    "dead player retains pending reward instead of losing it on respawn");
            player.setHealth(20); mod.deliverPending(player);
            helper.assertTrue(mod.store.load(player.getUUID()).receipts.getLast().actions.getFirst().status == PlayerStore.Status.DONE,
                    "living player receives preserved pending reward");
            helper.getLevel().getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(2), e -> e.getItem().is(Items.DIAMOND)).forEach(ItemEntity::discard);
        });
        leave(player); helper.succeed();
    }
}
