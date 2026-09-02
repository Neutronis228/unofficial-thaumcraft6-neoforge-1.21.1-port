package thaumcraft.common.items.curios;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import thaumcraft.Thaumcraft;
import thaumcraft.common.items.TCRechargeHelper;
import thaumcraft.common.registry.TCItems;

/**
 * Runtime bridge for TC6 Baubles behaviour while the NeoForge 1.21.1 port still
 * uses split item ids and data driven Curios slots.
 *
 * Implemented legacy behaviour:
 * - crafted Vis Amulet: every 5 ticks it recharges the first eligible item;
 * - Cloud Ring: one mid-air jump per airtime, using the original 0.75 vertical
 *   impulse, Jump Boost bonus, sprint push, fall-distance reset and sweep sound.
 */
@EventBusSubscriber(modid = Thaumcraft.MODID)
public final class TCBaubleEvents {
    private static final int CRAFTED_VIS_AMULET_INTERVAL_TICKS = 5;
    private static final int HOTBAR_SIZE = 9;
    private static final double CLOUD_RING_JUMP_VELOCITY = 0.75D;
    private static final double CLOUD_RING_SPRINT_PUSH = 0.2D;
    private static final Set<UUID> CLOUD_RING_AIRBORNE_JUMPS = new HashSet<>();

    private TCBaubleEvents() {
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (!player.level().isClientSide && (player.onGround() || player.isInWater())) {
            CLOUD_RING_AIRBORNE_JUMPS.remove(player.getUUID());
        }

        if (player.level().isClientSide || player.tickCount % CRAFTED_VIS_AMULET_INTERVAL_TICKS != 0) {
            return;
        }

        ItemStack visAmulet = firstMatching(player, TCItems.VIS_AMULET.get());
        if (visAmulet.isEmpty()) {
            return;
        }

        rechargeFirstEligibleStack(player, visAmulet);
    }

    public static boolean tryCloudRingJump(ServerPlayer player) {
        if (!canAttemptCloudRingJump(player) || !hasCloudRing(player)) {
            return false;
        }

        UUID uuid = player.getUUID();
        if (CLOUD_RING_AIRBORNE_JUMPS.contains(uuid)) {
            return false;
        }

        CLOUD_RING_AIRBORNE_JUMPS.add(uuid);
        applyCloudRingImpulse(player);
        player.level().playSound(
                player,
                player.getX(),
                player.getY(),
                player.getZ(),
                SoundEvents.PLAYER_ATTACK_SWEEP,
                SoundSource.PLAYERS,
                0.1F,
                1.0F + (float) player.level().random.nextGaussian() * 0.05F
        );
        return true;
    }

    public static boolean canAttemptCloudRingJump(Player player) {
        return player != null
                && !player.onGround()
                && !player.isInWater()
                && !player.isFallFlying()
                && !player.isShiftKeyDown();
    }

    public static boolean hasCloudRing(Player player) {
        return !firstMatching(player, TCItems.CLOUD_RING.get()).isEmpty();
    }

    public static void applyCloudRingImpulse(Player player) {
        Vec3 current = player.getDeltaMovement();
        double motionX = current.x;
        double motionY = CLOUD_RING_JUMP_VELOCITY;
        double motionZ = current.z;

        MobEffectInstance jumpBoost = player.getEffect(MobEffects.JUMP);
        if (jumpBoost != null) {
            motionY += (jumpBoost.getAmplifier() + 1) * 0.1D;
        }

        if (player.isSprinting()) {
            float yaw = player.getYRot() * ((float) Math.PI / 180.0F);
            motionX -= Mth.sin(yaw) * CLOUD_RING_SPRINT_PUSH;
            motionZ += Mth.cos(yaw) * CLOUD_RING_SPRINT_PUSH;
        }

        player.setDeltaMovement(motionX, motionY, motionZ);
        player.hasImpulse = true;
        player.resetFallDistance();
    }

    private static boolean rechargeFirstEligibleStack(Player player, ItemStack source) {
        Inventory inventory = player.getInventory();
        int hotbarEnd = Math.min(HOTBAR_SIZE, inventory.items.size());

        if (rechargeFrom(inventory.items, source, 0, hotbarEnd)) {
            return true;
        }
        if (rechargeFrom(inventory.items, source, hotbarEnd, inventory.items.size())) {
            return true;
        }
        if (rechargeFrom(inventory.armor, source, 0, inventory.armor.size())) {
            return true;
        }
        return rechargeFrom(inventory.offhand, source, 0, inventory.offhand.size());
    }

    private static boolean rechargeFrom(List<ItemStack> stacks, ItemStack source, int startInclusive, int endExclusive) {
        int start = Math.max(0, startInclusive);
        int end = Math.min(stacks.size(), endExclusive);
        for (int i = start; i < end; i++) {
            ItemStack target = stacks.get(i);
            if (target.isEmpty() || target == source) {
                continue;
            }
            if (TCRechargeHelper.recharge(target, 1) > 0) {
                return true;
            }
        }
        return false;
    }

    private static ItemStack firstMatching(Player player, Item item) {
        Inventory inventory = player.getInventory();
        ItemStack stack = firstMatching(inventory.items, item);
        if (!stack.isEmpty()) {
            return stack;
        }
        stack = firstMatching(inventory.armor, item);
        if (!stack.isEmpty()) {
            return stack;
        }
        return firstMatching(inventory.offhand, item);
    }

    private static ItemStack firstMatching(List<ItemStack> stacks, Item item) {
        for (ItemStack stack : stacks) {
            if (!stack.isEmpty() && stack.getItem() == item) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }
}
