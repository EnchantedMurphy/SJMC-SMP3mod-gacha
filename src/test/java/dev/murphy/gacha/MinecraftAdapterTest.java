package dev.murphy.gacha;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.ContextChain;
import net.minecraft.SharedConstants;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class MinecraftAdapterTest {
    @BeforeAll static void bootstrap() {
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
        var lookup = net.minecraft.data.registries.VanillaRegistries.createWorldLookup();
        BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(lookup).forEach(p -> p.apply());
    }
    @Test void survivalRenameCannotForgeTicketAndShopCopyPreservesType() {
        ItemStack forged = new ItemStack(Items.PAPER); forged.set(DataComponents.CUSTOM_NAME, Component.literal("抽奖券"));
        assertEquals(0, Tickets.draws(forged));
        assertEquals(1, Tickets.draws(Tickets.create(1, 64).copy()));
        assertEquals(10, Tickets.draws(Tickets.create(10, 1).copy()));
        ItemStack renamed = Tickets.create(10, 1); renamed.set(DataComponents.CUSTOM_NAME, Component.literal("已改名"));
        assertEquals(10, Tickets.draws(renamed));
        for (int type : List.of(1, 10)) {
            var ticket = Tickets.create(type, 1);
            assertTrue(ticket.get(DataComponents.LORE).lines().stream().noneMatch(c -> c.getString().contains("个人 S 级保底")));
        }
        var legacy = new ItemStack(Items.PAPER);
        var tag = new net.minecraft.nbt.CompoundTag(); tag.putInt("lottery_version", 1); tag.putInt("lottery_draws", 10);
        legacy.set(DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(tag));
        assertEquals(0, Tickets.draws(legacy));
    }
    @Test void managementGrammarParsesAndOrdinaryPlayersCannotUseIt() {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>(); new GachaCommands(new GachaMod()).register(dispatcher);
        var admin = Commands.createCompilationContext(PermissionSet.ALL_PERMISSIONS);
        for (String command : List.of("gacha", "gacha help", "gacha help 4", "gacha history 2", "gacha pity", "gacha pending", "gacha give Steve single 2",
                "gacha give @a ten", "gacha pool set main 0 64 0 3 66 3", "gacha pool axis main z", "gacha pool remove main",
                "gacha pool list", "gacha reward list", "gacha reward sethand S mace", "gacha reward sethand A diamond 3",
                "gacha reward setcommand B money \"铁镐通宝 ×20\" economy give %player% fuji:tongbao 20",
                "gacha reward remove S mace", "gacha check Steve", "gacha reload",
                "gacha recover Steve abc 1 retry", "gacha recover Steve abc 2 done")) {
            var parsed = dispatcher.parse(command, admin); assertFalse(parsed.getReader().canRead(), command);
            assertTrue(ContextChain.tryFlatten(parsed.getContext().build(command)).isPresent(), command);
        }
        var player = Commands.createCompilationContext(PermissionSet.NO_PERMISSIONS);
        assertTrue(dispatcher.parse("lottery history", player).getReader().canRead());
        for (String command : List.of("gacha give Steve single", "gacha pool list", "gacha reward list", "gacha reload", "gacha check Steve",
                "gacha pending Steve", "gacha recover Steve abc 1 retry")) {
            assertTrue(dispatcher.parse(command, player).getReader().canRead(), command);
        }
        for (String command : List.of("gacha", "gacha history 1", "gacha pity", "gacha pending"))
            assertFalse(dispatcher.parse(command, player).getReader().canRead(), command);
    }
}
