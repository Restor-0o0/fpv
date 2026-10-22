package org.restor.fpv.client;

import net.neoforged.fml.common.EventBusSubscriber;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import org.restor.fpv.Fpv;
import org.restor.fpv.entity.DroneEntity;

/** Flying by eye: the pilot keeps their own body and camera, so the sticks live on the
 *  same keys as the FPV view and the player must not walk off while using them. */
@EventBusSubscriber(modid = Fpv.MODID, value = net.neoforged.api.distmarker.Dist.CLIENT)
public final class LineOfSightSession {
    private static int entityId = -1;

    private LineOfSightSession() {}

    public static void set(int id) { entityId = id; }

    public static void clear() { entityId = -1; }

    public static boolean active() { return entityId >= 0; }

    public static DroneEntity drone() {
        if (entityId < 0) return null;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return null;
        Entity e = mc.level.getEntity(entityId);
        if (!(e instanceof DroneEntity d) || d.isRemoved()) {
            entityId = -1;
            return null;
        }
        return d;
    }

    @SubscribeEvent
    public static void onMovementInput(MovementInputUpdateEvent event) {
        if (!active() || event.getEntity() != Minecraft.getInstance().player) return;
        var input = event.getInput();
        input.forwardImpulse = 0f;
        input.leftImpulse = 0f;
        input.up = false;
        input.down = false;
        input.left = false;
        input.right = false;
        input.jumping = false;
    }
}
