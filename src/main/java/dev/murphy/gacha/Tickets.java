package dev.murphy.gacha;

import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import java.util.List;

public final class Tickets {
    public static final Item SINGLE = register("ticket_single", false);
    public static final Item TEN = register("ticket_ten", true);
    public static final CreativeModeTab SMP3 = Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB,
            Identifier.fromNamespaceAndPath("smp3", "gacha"), FabricCreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.smp3"))
                    .icon(() -> new ItemStack(SINGLE))
                    .displayItems((parameters, output) -> { output.accept(SINGLE); output.accept(TEN); }).build());
    private Tickets() {}

    /** Called during common initialization, before registries freeze on either side. */
    public static void initialize() { }

    private static Item register(String name, boolean ten) {
        var id = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("smp3", name));
        var usage = Component.translatable("tooltip.smp3.ticket.usage").withStyle(ChatFormatting.GRAY);
        var properties = new Item.Properties().setId(id)
                .component(DataComponents.ITEM_NAME, Component.translatable("item.smp3." + name).withStyle(ChatFormatting.GOLD))
                .component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true)
                .component(DataComponents.LORE, new ItemLore(ten ? List.of(usage,
                        Component.translatable("tooltip.smp3.ticket.ten_guarantee").withStyle(ChatFormatting.YELLOW)) : List.of(usage)));
        return Registry.register(BuiltInRegistries.ITEM, id, new Item(properties));
    }

    public static ItemStack create(int draws, int count) {
        if (draws != 1 && draws != 10) throw new IllegalArgumentException("抽奖券类型必须为 single 或 ten。");
        return new ItemStack(draws == 10 ? TEN : SINGLE, count);
    }

    /** Item identity is authoritative; paper tickets from earlier versions are rejected. */
    public static int draws(ItemStack stack) {
        if (stack.isEmpty()) return 0;
        return stack.is(SINGLE) ? 1 : stack.is(TEN) ? 10 : 0;
    }
}
