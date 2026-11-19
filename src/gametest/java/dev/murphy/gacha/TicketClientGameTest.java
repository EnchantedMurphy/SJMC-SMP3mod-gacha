package dev.murphy.gacha;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.flag.FeatureFlags;

/** Physical-client coverage; never included in the production mod. */
public final class TicketClientGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        try (var world = context.worldBuilder().create()) {
            world.getServer().runCommand("give @a smp3:ticket_single 2");
            world.getServer().runCommand("gacha give @a ten 1");
            context.waitFor(client -> client.player != null && client.player.getInventory().contains(stack -> stack.is(Tickets.TEN)));
            context.runOnClient(client -> {
                if (!client.player.getInventory().contains(stack -> stack.is(Tickets.SINGLE))) throw new AssertionError("Vanilla give must use the registered ticket");
                Tickets.SMP3.buildContents(new CreativeModeTab.ItemDisplayParameters(FeatureFlags.DEFAULT_FLAGS, true, client.player.registryAccess()));
                if (Tickets.SMP3.getDisplayItems().size() != 2 || !Tickets.SMP3.contains(new ItemStack(Tickets.SINGLE))
                        || !Tickets.SMP3.contains(new ItemStack(Tickets.TEN))) throw new AssertionError("SMP3 tab must contain both tickets");
                var missing = client.getModelManager().getItemModel(Identifier.fromNamespaceAndPath("smp3", "missing_test_model"));
                for (int draws : new int[]{1, 10}) {
                    var ticket = Tickets.create(draws, 1);
                    if (client.getModelManager().getItemModel(ticket.get(DataComponents.ITEM_MODEL)) == missing)
                        throw new AssertionError("Ticket item model must load");
                    var state = new ItemStackRenderState();
                    client.getItemModelResolver().updateForTopItem(state, ticket, ItemDisplayContext.GUI, client.level, client.player, 0);
                    if (state.isEmpty()) throw new AssertionError("Ticket must have renderable geometry");
                }
                client.player.getInventory().setItem(0, Tickets.create(1, 1));
                client.player.getInventory().setItem(1, Tickets.create(10, 1));
            });
            context.waitTicks(5);
            context.takeScreenshot("smp3-tickets");
        }
    }
}
