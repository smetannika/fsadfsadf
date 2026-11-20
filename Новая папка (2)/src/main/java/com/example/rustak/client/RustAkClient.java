package com.example.rustak.client;

import com.example.rustak.Ak47Item;
import com.example.rustak.RustAkMod;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;

import java.util.Random;

public class RustAkClient implements ClientModInitializer {
    /** Отдача модели оружия 0..1 (для анимации рук/автомата). */
    public static float recoil, prevRecoil;

    private static final Random RND = new Random();
    private static int cooldown;
    private static int shotsInBurst;
    private static int idleTicks;

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(RustAkClient::tick);
    }

    private static void tick(MinecraftClient mc) {
        prevRecoil = recoil;
        recoil *= 0.55f;
        if (recoil < 0.002f) recoil = 0f;
        if (cooldown > 0) cooldown--;
        if (++idleTicks > 8) shotsInBurst = 0;

        ClientPlayerEntity p = mc.player;
        if (p == null || mc.currentScreen != null || p.isSpectator()) return;
        if (!(p.getMainHandStack().getItem() instanceof Ak47Item)) return;
        if (!mc.options.keyAttack.isPressed() || cooldown > 0) return;

        cooldown = Ak47Item.FIRE_DELAY;
        ClientPlayNetworking.send(RustAkMod.SHOOT_PACKET, PacketByteBufs.create());

        if (Ak47Item.hasAmmo(p)) {
            idleTicks = 0;
            recoil = 1.0f;
            // Rust-подобная отдача: чем дольше очередь, тем сильнее уводит вверх, плюс случайный разброс по горизонтали
            float climb = (0.55f + Math.min(shotsInBurst, 10) * 0.07f) * (p.isSneaking() ? 0.6f : 1.0f);
            float sway = (RND.nextFloat() - 0.5f) * 0.7f;
            p.changeLookDirection(sway / 0.15, -climb / 0.15);
            shotsInBurst++;
        }
    }
}
