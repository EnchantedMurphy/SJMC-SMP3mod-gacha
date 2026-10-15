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
        for (String command : List.of("gacha", "gacha help", "gacha help 4", "gacha history main 2", "gacha pity main", "gacha pending main", "gacha give Steve single 2",
                "gacha give @a ten", "gacha pool set main 0 64 0 3 66 3", "gacha pool remove main",
                "gacha history main details", "gacha history main details 2", "gacha pool facing main +x", "gacha pool facing main -x",
                "gacha pool facing main +z", "gacha pool facing main -z",
                "gacha pool list", "gacha reward list main", "gacha reward sethand main S mace", "gacha reward sethand main A diamond 3",
                "gacha reward setdisplay main S money", "gacha reward cleardisplay main S money",
                "gacha reward setcommand main B money \"铁镐通宝 ×20\" economy give %player% fuji:tongbao 20",
                "gacha reward remove main S mace", "gacha reward limit main S mace 1", "gacha reward sethand main S mace 1 2", "gacha bonus sethand main S stamp", "gacha bonus remove main S stamp", "gacha bonus list main", "gacha bonus setcommand main S stamp \"money\" give %player% minecraft:paper 1", "gacha check main Steve", "gacha reload",
                "gacha recover main Steve abc 1 retry", "gacha recover main Steve abc 2 done")) {
            var parsed = dispatcher.parse(command, admin); assertFalse(parsed.getReader().canRead(), command);
            assertTrue(ContextChain.tryFlatten(parsed.getContext().build(command)).isPresent(), command);
        }
        var player = Commands.createCompilationContext(PermissionSet.NO_PERMISSIONS);
        assertTrue(dispatcher.parse("lottery history", player).getReader().canRead());
        for (String command : List.of("gacha give Steve single", "gacha pool facing main +x", "gacha reward list main", "gacha reload", "gacha check main Steve",
                "gacha pending main Steve", "gacha recover main Steve abc 1 retry", "gacha reward setdisplay main S money", "gacha reward cleardisplay main S money")) {
            assertTrue(dispatcher.parse(command, player).getReader().canRead(), command);
        }
        for (String command : List.of("gacha", "gacha history main 1", "gacha pity main", "gacha pending main"))
            assertFalse(dispatcher.parse(command, player).getReader().canRead(), command);
    }

    @Test void ticketAppearanceIsOptionalAndCannotForgeTickets() {
        for (int draws : List.of(1, 10)) {
            var ticket = Tickets.create(draws, 2);
            var appearance = ticket.get(DataComponents.CUSTOM_MODEL_DATA);
            assertNotNull(appearance);
            assertEquals(List.of(draws == 10 ? Tickets.TEN_MODEL : Tickets.SINGLE_MODEL), appearance.strings());
            assertEquals(appearance, ticket.copy().get(DataComponents.CUSTOM_MODEL_DATA));
            assertEquals(new ItemStack(Items.PAPER).get(DataComponents.ITEM_MODEL), ticket.get(DataComponents.ITEM_MODEL));
            var fake = new ItemStack(Items.PAPER);
            fake.set(DataComponents.CUSTOM_MODEL_DATA, appearance);
            assertEquals(0, Tickets.draws(fake));
            ticket.remove(DataComponents.CUSTOM_MODEL_DATA);
            assertEquals(draws, Tickets.draws(ticket));
        }
    }
}
