package dev.murphy.gacha;

import com.mojang.math.Transformation;
import dev.murphy.gacha.mixin.BlockDisplayAccess;
import dev.murphy.gacha.mixin.DisplayAccess;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Brightness;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;
import static dev.murphy.gacha.GachaConfig.*;

final class GachaAnimation {
    static final String FX_TAG = "gacha_temporary_fx";
    private static final Block[] COLORS = {Blocks.CONCRETE.blue(), Blocks.CONCRETE.yellow(), Blocks.CONCRETE.red()};
    private static final int[] NOTES = {0, 4, 7, 12, 7, 4, 2, 5, 9, 14, 9, 5, 4, 7, 11, 16};
    final ServerLevel level;
    final List<Display.BlockDisplay> displays = new ArrayList<>();
    private final List<FireworkRocketEntity> rockets = new ArrayList<>();
    private final double x, y, z;
    private final String axis;
    private int rocketTicks;

    /** Uses the vanilla entity type and packets, but cannot execute server-side flight/collision damage. */
    private static final class CosmeticFirework extends FireworkRocketEntity {
        CosmeticFirework(ServerLevel level, ItemStack stack, double x, double y, double z) {
            super(level, stack, x, y, z, true);
        }
        @Override public void tick() { }
    }

    GachaAnimation(ServerLevel level, Pool pool, int draws) {
        this.level = level;
        this.x = (pool.minX + (double) pool.maxX + 1) / 2;
        this.z = (pool.minZ + (double) pool.maxZ + 1) / 2;
        this.y = pool.maxY + 2.0;
        this.axis = pool.displayAxis;
        try {
            for (int i = 0; i < draws; i++) {
                double offset = draws == 1 ? 0 : (i % 5 - 2) * 0.9;
                double rowY = y + (draws == 10 && i < 5 ? 0.9 : 0);
                Display.BlockDisplay display = new Display.BlockDisplay(EntityTypes.BLOCK_DISPLAY, level);
                display.setPos(x + (axis.equals("x") ? offset : 0), rowY, z + (axis.equals("z") ? offset : 0));
                display.addTag(FX_TAG);
                ((DisplayAccess) display).gacha$transform(new Transformation(new Vector3f(-0.3f, -0.3f, -0.3f),
                        new Quaternionf(), new Vector3f(0.6f), new Quaternionf()));
                ((DisplayAccess) display).gacha$brightness(new Brightness(15, 15));
                ((BlockDisplayAccess) display).gacha$block(Blocks.CONCRETE.blue().defaultBlockState());
                if (!level.addFreshEntity(display)) throw new IllegalStateException("无法生成抽奖展示实体。");
                displays.add(display);
            }
        } catch (RuntimeException e) { clear(); throw e; }
    }
    void tick(int elapsed, RandomGenerator random) {
        if (elapsed % 4 == 0) {
            for (var display : displays) ((BlockDisplayAccess) display).gacha$block(COLORS[random.nextInt(COLORS.length)].defaultBlockState());
            int note = NOTES[(elapsed / 4) % NOTES.length];
            level.playSound(null, x, y, z, SoundEvents.NOTE_BLOCK_HARP, SoundSource.BLOCKS, 0.8f,
                    (float) Math.pow(2, (note - 12) / 12.0));
            if (elapsed % 8 == 0) level.playSound(null, x, y, z, SoundEvents.NOTE_BLOCK_BELL, SoundSource.BLOCKS,
                    0.45f, (float) Math.pow(2, (note - 12) / 12.0));
        }
    }
    void reveal(List<PlayerStore.Draw> draws) {
        for (int i = 0; i < displays.size(); i++) {
            Block block = switch (draws.get(i).tier) { case S -> Blocks.CONCRETE.red(); case A -> Blocks.CONCRETE.yellow(); case B -> Blocks.CONCRETE.blue(); };
            ((BlockDisplayAccess) displays.get(i)).gacha$block(block.defaultBlockState());
        }
        Tier highest = draws.stream().map(d -> d.tier).min(java.util.Comparator.naturalOrder()).orElse(Tier.B);
        int color = switch (highest) { case S -> 0xFF3333; case A -> 0xFFD43B; case B -> 0x3388FF; };
        ItemStack firework = new ItemStack(Items.FIREWORK_ROCKET);
        firework.set(DataComponents.FIREWORKS, new Fireworks(0, List.of(new FireworkExplosion(
                FireworkExplosion.Shape.LARGE_BALL, IntArrayList.of(color), IntArrayList.of(color), true, true))));
        for (int side : new int[]{-1, 1}) {
            FireworkRocketEntity rocket = new CosmeticFirework(level, firework.copy(),
                    x + (axis.equals("x") ? side * 3.2 : 0), y, z + (axis.equals("z") ? side * 3.2 : 0));
            rocket.addTag(FX_TAG);
            if (level.addFreshEntity(rocket)) rockets.add(rocket);
        }
        level.playSound(null, x, y, z, SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.BLOCKS, 1f, 0.9f);
        rocketTicks = 0;
    }
    void tickRevealed() {
        if (++rocketTicks == 2) {
            // Broadcast vanilla cosmetic explosion metadata, then remove without invoking damage/explosion logic.
            for (var rocket : rockets) {
                if (!rocket.isRemoved()) { level.broadcastEntityEvent(rocket, (byte) 17); rocket.discard(); }
            }
            rockets.clear();
        }
    }
    void clear() {
        displays.forEach(Display.BlockDisplay::discard); displays.clear();
        rockets.forEach(FireworkRocketEntity::discard); rockets.clear();
    }
}
