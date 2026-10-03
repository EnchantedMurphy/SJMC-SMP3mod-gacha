package dev.murphy.gacha;

import com.google.gson.JsonElement;
import com.mojang.brigadier.context.ContextChain;
import com.mojang.serialization.JsonOps;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

final class Rewards {
    private Rewards() {}
    static GachaConfig.Reward capture(ServerPlayer player) {
        if (player.getMainHandItem().isEmpty()) throw new IllegalArgumentException("请在主手拿着奖品再执行。");
        return encode(player, player.getMainHandItem());
    }
    static GachaConfig.Reward encode(ServerPlayer player, ItemStack stack) {
        return GachaConfig.Reward.item(ItemStack.CODEC.encodeStart(
                player.registryAccess().createSerializationContext(JsonOps.INSTANCE), stack).getOrThrow());
    }
    static ItemStack decode(ServerPlayer player, JsonElement item) {
        ItemStack stack = ItemStack.CODEC.parse(player.registryAccess().createSerializationContext(JsonOps.INSTANCE), item)
                .getOrThrow(message -> new IllegalArgumentException("奖品物品数据无效：" + message));
        if (stack.isEmpty()) throw new IllegalArgumentException("奖品物品不能为空。");
        return stack;
    }
    static CommandSourceStack source(ServerPlayer player) {
        return player.level().getServer().createCommandSourceStack().withLevel(player.level())
                .withPosition(player.position()).withSuppressedOutput();
    }
    static String expand(String command, ServerPlayer player) {
        String name = player.getGameProfile().name();
        if (command.contains("%player%") && !name.matches("[A-Za-z0-9_]{1,16}"))
            throw new IllegalArgumentException("玩家名无法安全用于指令，请将占位符改为 %uuid%。");
        return command.replace("%player%", name).replace("%uuid%", player.getUUID().toString());
    }
    static void validate(ServerPlayer player, GachaConfig.Reward reward) {
        GachaConfig.validateReward(reward);
        if (reward.item != null) { decode(player, reward.item); return; }
        String command = expand(reward.command, player);
        var parsed = player.level().getServer().getCommands().getDispatcher().parse(command, source(player));
        if (parsed.getReader().canRead() || ContextChain.tryFlatten(parsed.getContext().build(command)).isEmpty())
            throw new IllegalArgumentException("奖品指令无法执行，请检查 Fuji 模块/货币及配置：" + reward.command);
    }
    static void validateAll(ServerPlayer player, GachaConfig config) {
        config.rewards.values().forEach(list -> list.forEach(prize -> prize.rewards.forEach(r -> validate(player, r))));
    }
    static void give(ServerPlayer player, ItemStack original) {
        ItemStack stack = original.copy();
        var inventory = player.getInventory();
        // Inventory.add silently deletes overflow for creative players; insert explicitly for every game mode.
        for (int i = 0; i < net.minecraft.world.entity.player.Inventory.INVENTORY_SIZE && !stack.isEmpty(); i++) {
            ItemStack slot = inventory.getItem(i);
            if (!slot.isEmpty() && ItemStack.isSameItemSameComponents(slot, stack)) {
                int space = Math.max(0, Math.min(slot.getMaxStackSize(), inventory.getMaxStackSize()) - slot.getCount());
                int amount = Math.min(space, stack.getCount()); slot.grow(amount); stack.shrink(amount);
            }
        }
        for (int i = 0; i < net.minecraft.world.entity.player.Inventory.INVENTORY_SIZE && !stack.isEmpty(); i++) {
            if (inventory.getItem(i).isEmpty()) {
                int amount = Math.min(stack.getCount(), Math.min(stack.getMaxStackSize(), inventory.getMaxStackSize()));
                inventory.setItem(i, stack.copyWithCount(amount)); stack.shrink(amount);
            }
        }
        if (!stack.isEmpty()) {
            ItemEntity overflow = new ItemEntity(player.level(), player.getX(), player.getY() + 0.5, player.getZ(), stack);
            overflow.setTarget(player.getUUID());
            overflow.setNoPickUpDelay();
            // No thrower is assigned, so a returned ticket can never automatically trigger another draw.
            if (!player.level().addFreshEntity(overflow)) throw new IllegalStateException("无法生成背包溢出的物品。");
        }
        player.getInventory().setChanged();
        player.inventoryMenu.broadcastChanges();
        player.containerMenu.broadcastChanges();
    }
    static void deliver(ServerPlayer player, GachaConfig.Reward reward) {
        validate(player, reward);
        if (reward.item != null) { give(player, decode(player, reward.item)); return; }
        boolean[] succeeded = {false};
        var source = source(player).withCallback((ok, value) -> { if (ok) succeeded[0] = true; });
        player.level().getServer().getCommands().performPrefixedCommand(source, expand(reward.command, player));
        if (!succeeded[0]) throw new IllegalStateException("奖品指令未报告成功，需管理员核查。");
    }
}
