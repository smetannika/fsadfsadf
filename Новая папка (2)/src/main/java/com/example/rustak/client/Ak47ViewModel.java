package com.example.rustak.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.model.json.ModelTransformation;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.util.math.Vector3f;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Quaternion;
import net.minecraft.util.math.Vec3d;

/**
 * Вид от первого лица: автомат двумя руками по центру-справа, как в Rust.
 * Все числа ниже - позиции в пространстве камеры (x: вправо, y: вверх, z: вперёд = минус).
 * Если руки/ствол стоят не так, как хочется - подкрутите константы и перезапустите (или hot-swap в IDE).
 */
public final class Ak47ViewModel {
    // ===== Позиция и размер автомата =====
    static final double GUN_X = 0.13, GUN_Y = -0.30, GUN_Z = -0.78;
    static final float GUN_SCALE = 0.5f;
    static final float GUN_YAW = 1.5f;            // градусы: небольшой доворот ствола к центру

    // ===== Руки =====
    static final double ARM_REACH = 0.55;         // расстояние от "плеча" (оси руки) до ладони
    // Правая рука - на пистолетной рукояти (координаты в пикселях модели 0..16+)
    static final double[] R_HAND = {8, 6.6, 13.9};
    static final double[] R_DIR  = {-0.30, 0.50, -0.82};   // куда направлена рука
    static final float R_ROLL = 0f;                         // поворот руки вокруг своей оси (градусы)
    // Левая рука - под цевьём
    static final double[] L_HAND = {8, 7.4, 0.5};
    static final double[] L_DIR  = {0.55, 0.30, -0.78};
    static final float L_ROLL = 0f;

    private Ak47ViewModel() {}

    public static void render(AbstractClientPlayerEntity player, float tickDelta, ItemStack stack,
                              float equipProgress, MatrixStack m, VertexConsumerProvider vcp, int light) {
        MinecraftClient mc = MinecraftClient.getInstance();
        float k = MathHelper.lerp(tickDelta, RustAkClient.prevRecoil, RustAkClient.recoil);

        m.push();
        m.translate(0, -0.6 * equipProgress, 0);                     // анимация доставания
        m.translate(0, 0.012 * k, 0.07 * k);                         // откат назад/вверх
        m.multiply(Vector3f.POSITIVE_X.getDegreesQuaternion(2.2f * k)); // подброс ствола

        // --- автомат ---
        m.push();
        m.translate(GUN_X, GUN_Y, GUN_Z);
        m.multiply(Vector3f.POSITIVE_Y.getDegreesQuaternion(GUN_YAW));
        m.scale(GUN_SCALE, GUN_SCALE, GUN_SCALE);
        // Mode.NONE = без display-трансформации из json, позицией управляем мы
        mc.getItemRenderer().renderItem(player, stack, ModelTransformation.Mode.NONE, false, m, vcp,
                player.world, light, OverlayTexture.DEFAULT_UV);
        m.pop();

        // --- руки ---
        renderArm(player, m, vcp, light, true, R_HAND, R_DIR, R_ROLL);
        renderArm(player, m, vcp, light, false, L_HAND, L_DIR, L_ROLL);

        m.pop();
    }

    /** Точка модели (в пикселях) -> пространство камеры. */
    private static Vec3d gunPoint(double[] p) {
        double x = (p[0] - 8) / 16.0 * GUN_SCALE;
        double y = (p[1] - 8) / 16.0 * GUN_SCALE;
        double z = (p[2] - 8) / 16.0 * GUN_SCALE;
        double a = Math.toRadians(GUN_YAW);
        double rx = x * Math.cos(a) + z * Math.sin(a);
        double rz = -x * Math.sin(a) + z * Math.cos(a);
        return new Vec3d(GUN_X + rx, GUN_Y + y, GUN_Z + rz);
    }

    /**
     * Модель руки в ванили "висит" вдоль своей оси +Y (в сыром пространстве модели).
     * Мы выравниваем эту ось по нужному направлению и ставим плечо так, чтобы ладонь попала в точку хвата.
     */
    private static void renderArm(AbstractClientPlayerEntity player, MatrixStack m, VertexConsumerProvider vcp,
                                  int light, boolean right, double[] handPx, double[] dirIn, float roll) {
        double len = Math.sqrt(dirIn[0] * dirIn[0] + dirIn[1] * dirIn[1] + dirIn[2] * dirIn[2]);
        double dx = dirIn[0] / len, dy = dirIn[1] / len, dz = dirIn[2] / len;
        Vec3d hand = gunPoint(handPx);
        Vec3d shoulder = hand.subtract(dx * ARM_REACH, dy * ARM_REACH, dz * ARM_REACH);

        m.push();
        m.translate(shoulder.x, shoulder.y, shoulder.z);

        // поворот оси +Y в направление d: ось = Y x d, угол = acos(Y . d)
        double ax = dz, az = -dx;
        double al = Math.sqrt(ax * ax + az * az);
        if (al > 1e-6) {
            m.multiply(new Quaternion(new Vector3f((float) (ax / al), 0f, (float) (az / al)),
                    (float) Math.acos(dy), false));
        }
        m.multiply(Vector3f.POSITIVE_Y.getDegreesQuaternion(roll));

        // компенсируем pivot части модели руки (±5, 2, 0)/16, чтобы плечо стояло ровно в начале координат
        m.translate(right ? 0.3125 : -0.3125, -0.125, 0);

        PlayerEntityRenderer r = (PlayerEntityRenderer) MinecraftClient.getInstance()
                .getEntityRenderDispatcher().getRenderer(player);
        if (right) r.renderRightArm(m, vcp, light, player);
        else r.renderLeftArm(m, vcp, light, player);
        m.pop();
    }
}
