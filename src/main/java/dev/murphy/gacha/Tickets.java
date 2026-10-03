package dev.murphy.gacha;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import java.util.List;

public final class Tickets {
    private Tickets() {}

    public static ItemStack create(int draws, int count) {
        if (draws != 1 && draws != 10) throw new IllegalArgumentException("抽奖券类型必须为 single 或 ten。");
        ItemStack stack = new ItemStack(Items.PAPER, count);
        CompoundTag tag = new CompoundTag();
        tag.putInt("gacha_draws", draws);
        tag.putInt("gacha_version", 1);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(draws == 10 ? "十连抽奖券" : "抽奖券")
                .withStyle(style -> style.withColor(ChatFormatting.GOLD).withItalic(false)));
        stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        var usage = Component.literal("投掷到抽奖池内使用").withStyle(ChatFormatting.GRAY);
        stack.set(DataComponents.LORE, new ItemLore(draws == 10 ? List.of(usage,
                Component.literal("末抽必为 A 级及以上").withStyle(ChatFormatting.YELLOW)) : List.of(usage)));
        return stack;
    }

    /** Recognition uses server-issued custom data, so an anvil rename cannot mint tickets. */
    public static int draws(ItemStack stack) {
        if (stack.isEmpty() || !stack.is(Items.PAPER)) return 0;
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return 0;
        CompoundTag tag = data.copyTag();
        if (tag.getIntOr("gacha_version", 0) != 1) return 0;
        int count = tag.getIntOr("gacha_draws", 0);
        return count == 1 || count == 10 ? count : 0;
    }
}
