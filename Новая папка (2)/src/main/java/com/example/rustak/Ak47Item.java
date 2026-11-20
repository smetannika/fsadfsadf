package com.example.rustak;

import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.Map;
import java.util.Random;
import java.util.WeakHashMap;

public class Ak47Item extends Item {
    /** Тиков между выстрелами: 3 тика ≈ 400 выстрелов/мин (в Rust у АК ~450). */
    public static final int FIRE_DELAY = 3;
    public static final float DAMAGE = 7.0f;          // 3.5 сердца
    public static final float HEADSHOT_MULT = 2.0f;
    public static final double RANGE = 120.0;

    private static final Map<ServerPlayerEntity, Long> LAST_SHOT = new WeakHashMap<>();

    public Ak47Item(Settings settings) {
        super(settings);
    }

    // ---------------- патроны ----------------

    public static boolean hasAmmo(PlayerEntity player) {
        return player.isCreative() || countAmmo(player) > 0;
    }

    public static int countAmmo(PlayerEntity player) {
        int total = 0;
        PlayerInventory inv = player.inventory;
        for (int i = 0; i < inv.size(); i++) {
            ItemStack s = inv.getStack(i);
            if (s.getItem() == RustAkMod.AMMO_762) total += s.getCount();
        }
        return total;
    }

    private static boolean consumeAmmo(PlayerEntity player) {
        if (player.isCreative()) return true;
        PlayerInventory inv = player.inventory;
        for (int i = 0; i < inv.size(); i++) {
            ItemStack s = inv.getStack(i);
            if (s.getItem() == RustAkMod.AMMO_762) {
                s.decrement(1);
                return true;
            }
        }
        return false;
    }

    // ---------------- выстрел (только сервер) ----------------

    public static void tryShoot(ServerPlayerEntity player) {
        if (!(player.getMainHandStack().getItem() instanceof Ak47Item) || player.isSpectator()) return;

        ServerWorld world = player.getServerWorld();
        long now = world.getTime();
        Long last = LAST_SHOT.get(player);
        if (last != null && now - last < FIRE_DELAY) return;
        LAST_SHOT.put(player, now);

        if (!consumeAmmo(player)) {
            world.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.BLOCK_DISPENSER_FAIL, SoundCategory.PLAYERS, 0.8f, 1.7f);
            player.sendMessage(new TranslatableText("message.rustak.no_ammo"), true);
            return;
        }

        Random rnd = player.getRandom();

        // звук выстрела (слышно далеко, как в Rust)
        world.playSound(null, player.getX(), player.getEyeY(), player.getZ(),
                RustAkMod.AK_SHOT, SoundCategory.PLAYERS, 4.0f, 0.97f + rnd.nextFloat() * 0.06f);

        // разброс: в прицеле присядя - точнее, в прыжке - хуже
        double spread = player.isSneaking() ? 0.004 : 0.012;
        if (!player.isOnGround()) spread *= 3.0;

        Vec3d eye = player.getCameraPosVec(1.0f);
        Vec3d dir = player.getRotationVec(1.0f)
                .add(rnd.nextGaussian() * spread, rnd.nextGaussian() * spread, rnd.nextGaussian() * spread)
                .normalize();
        Vec3d end = eye.add(dir.multiply(RANGE));

        BlockHitResult blockHit = world.raycast(new RaycastContext(eye, end,
                RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player));
        Vec3d clipEnd = blockHit.getType() == HitResult.Type.MISS ? end : blockHit.getPos();

        Box box = player.getBoundingBox().stretch(dir.multiply(RANGE)).expand(1.0);
        EntityHitResult entityHit = ProjectileUtil.raycast(player, eye, clipEnd, box,
                e -> !e.isSpectator() && e.isAlive() && e.collides(), RANGE * RANGE);

        // дульное пламя и дым
        Vec3d muzzle = eye.add(dir.multiply(1.1)).add(0, -0.1, 0);
        world.spawnParticles(ParticleTypes.FLAME, muzzle.x, muzzle.y, muzzle.z, 1, 0.02, 0.02, 0.02, 0.0);
        world.spawnParticles(ParticleTypes.SMOKE, muzzle.x, muzzle.y, muzzle.z, 2, 0.03, 0.03, 0.03, 0.005);

        if (entityHit != null) {
            Entity target = entityHit.getEntity();
            Vec3d hp = entityHit.getPos();
            boolean head = hp.y > target.getEyeY() - 0.25;
            float dmg = head ? DAMAGE * HEADSHOT_MULT : DAMAGE;

            target.timeUntilRegen = 0;                    // чтобы автоматный огонь не глушился кадрами неуязвимости
            target.damage(DamageSource.player(player), dmg);

            world.spawnParticles(new DustParticleEffect(0.75f, 0.03f, 0.03f, 1.0f),
                    hp.x, hp.y, hp.z, 6, 0.08, 0.08, 0.08, 0.02);
            // "хитмаркер" только стрелку
            player.networkHandler.sendPacket(new PlaySoundS2CPacket(SoundEvents.ENTITY_ARROW_HIT_PLAYER,
                    SoundCategory.PLAYERS, player.getX(), player.getY(), player.getZ(), 0.5f, head ? 1.9f : 1.3f));
        } else if (blockHit.getType() == HitResult.Type.BLOCK) {
            BlockState state = world.getBlockState(blockHit.getBlockPos());
            Vec3d p = blockHit.getPos();
            world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, state),
                    p.x, p.y, p.z, 8, 0.05, 0.05, 0.05, 0.1);
            world.playSound(null, p.x, p.y, p.z, state.getSoundGroup().getHitSound(),
                    SoundCategory.BLOCKS, 0.6f, 1.2f);
        }

        if (!player.isCreative()) {
            player.sendMessage(new TranslatableText("message.rustak.ammo", countAmmo(player)), true);
        }
    }
}
