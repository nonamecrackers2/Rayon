package dev.lazurite.rayon;

import dev.lazurite.rayon.physics.composition.DynamicPhysicsComposition;
import dev.lazurite.thimble.Thimble;
import dev.lazurite.thimble.composition.Composition;
import net.minecraft.entity.Entity;

public class Rayon {
    public static DynamicPhysicsComposition getPhysics(Entity entity) {
        for (Composition composition : Thimble.getStitches(entity)) {
            if (composition instanceof DynamicPhysicsComposition) {
                return (DynamicPhysicsComposition) composition;
            }
        }

        return null;
    }
}
