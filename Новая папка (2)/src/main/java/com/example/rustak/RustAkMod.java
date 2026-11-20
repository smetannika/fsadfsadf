package com.example.rustak;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;

public class RustAkMod implements ModInitializer {
    public static final String MODID = "rustak";

    /** Клиент шлёт этот пакет каждый тик, пока зажата ЛКМ с АК в руке. */
    public static final Identifier SHOOT_PACKET = new Identifier(MODID, "shoot");

    public static final SoundEvent AK_SHOT = new SoundEvent(new Identifier(MODID, "ak47_shot"));

    public static Item AK47;
    public static Item AMMO_762;

    @Override
    public void onInitialize() {
        Registry.register(Registry.SOUND_EVENT, AK_SHOT.getId(), AK_SHOT);

        AMMO_762 = Registry.register(Registry.ITEM, new Identifier(MODID, "ammo_762"),
                new Item(new Item.Settings().group(ItemGroup.COMBAT)));
        AK47 = Registry.register(Registry.ITEM, new Identifier(MODID, "ak47"),
                new Ak47Item(new Item.Settings().group(ItemGroup.COMBAT).maxCount(1)));

        // Стрельба на сервере
        ServerPlayNetworking.registerGlobalReceiver(SHOOT_PACKET,
                (server, player, handler, buf, responseSender) -> server.execute(() -> Ak47Item.tryShoot(player)));

        // ЛКМ с автоматом не ломает блоки и не бьёт врукопашную (как в Rust ЛКМ = огонь)
        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) ->
                player.getMainHandStack().getItem() instanceof Ak47Item ? ActionResult.FAIL : ActionResult.PASS);
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) ->
                player.getMainHandStack().getItem() instanceof Ak47Item ? ActionResult.FAIL : ActionResult.PASS);
    }
}
