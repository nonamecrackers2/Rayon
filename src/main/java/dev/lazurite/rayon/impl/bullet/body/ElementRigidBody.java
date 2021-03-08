package dev.lazurite.rayon.impl.bullet.body;

import com.jme3.bounding.BoundingBox;
import com.jme3.math.Quaternion;
import dev.lazurite.rayon.api.element.PhysicsElement;
import dev.lazurite.rayon.impl.Rayon;
import dev.lazurite.rayon.impl.bullet.body.shape.BoundingBoxShape;
import dev.lazurite.rayon.impl.bullet.body.type.DebuggableBody;
import com.jme3.bullet.collision.shapes.CollisionShape;
import com.jme3.bullet.objects.PhysicsRigidBody;
import com.jme3.math.Vector3f;
import dev.lazurite.rayon.impl.bullet.world.MinecraftSpace;
import dev.lazurite.rayon.impl.util.debug.DebugLayer;
import dev.lazurite.rayon.impl.util.math.QuaternionHelper;
import dev.lazurite.rayon.impl.util.math.VectorHelper;
import dev.lazurite.rayon.impl.util.math.interpolate.Frame;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * This class represents how a {@link PhysicsElement} will interact with the {@link MinecraftSpace}. The user of Rayon
 * will be asked to provide one of these within their {@link PhysicsElement} implementation since when their entity is
 * spawned, the rigid body will be added to the physics space.<br>
 * Several properties can be modified and synced between the client and the server including:
 * <ul>
 *     <li>Drag</li>
 *     <li>Mass</li>
 *     <li>Friction</li>
 *     <li>Restitution</li>
 *     <li>Priority Player</li>
 * </ul>
 * as well as movement information:
 * <ul>
 *     <li>Location</li>
 *     <li>Rotation</li>
 *     <li>Linear Velocity</li>
 *     <li>Angular Velocity</li>
 * </ul>
 * @see PhysicsElement
 * @see MinecraftSpace
 */
public class ElementRigidBody extends PhysicsRigidBody implements DebuggableBody {
    private final PhysicsElement element;
    private final MinecraftSpace space;
    private boolean propertiesDirty;
    private int envLoadDistance;
    private float dragCoefficient;
    private boolean doFluidResistance;
    private PlayerEntity priorityPlayer;
    private Frame frame;

    public ElementRigidBody(PhysicsElement element, MinecraftSpace space, CollisionShape shape, float mass, float dragCoefficient, float friction, float restitution, boolean doFluidResistance) {
        super(shape, mass);
        this.element = element;
        this.space = space;
        this.setDragCoefficient(dragCoefficient);
        this.setFriction(friction);
        this.setRestitution(restitution);
        this.setEnvironmentLoadDistance(calculateLoadDistance());
        this.setDoFluidResistance(doFluidResistance);
    }

    public ElementRigidBody(PhysicsElement element, MinecraftSpace space, CollisionShape shape) {
        this(element, space, shape, 1.0f, 0.05f, 1.0f, 0.5f, true);
    }

    /**
     * The simplest way to create a new {@link ElementRigidBody}.
     * Only works if the {@link PhysicsElement} is an {@link Entity}.
     * @param entity the element to base this body around
     */
    public ElementRigidBody(Entity entity) {
        this((PhysicsElement) entity, Rayon.SPACE.get(entity.getEntityWorld()), new BoundingBoxShape(entity.getBoundingBox()));
    }

    /**
     * Calculates the distance away blocks should be loaded based
     * on the size of the collision bounding box.
     * @return the max distance to load blocks from
     */
    protected int calculateLoadDistance() {
        return (int) boundingBox(new BoundingBox()).getExtent(new Vector3f()).length() + 1;
    }

    public void fromTag(CompoundTag tag) {
        if (tag.getFloat("mass") == 0.0f) return;

        /* Movement Info */
        setPhysicsRotation(QuaternionHelper.fromTag(tag.getCompound("orientation")));
        setLinearVelocity(VectorHelper.fromTag(tag.getCompound("linear_velocity")));
        setAngularVelocity(VectorHelper.fromTag(tag.getCompound("angular_velocity")));

        /* Properties */
        setDragCoefficient(tag.getFloat("drag_coefficient"));
        setMass(tag.getFloat("mass"));
        setFriction(tag.getFloat("friction"));
        setRestitution(tag.getFloat("restitution"));
    }

    public void toTag(CompoundTag tag) {
        /* Movement Info */
        tag.put("orientation", QuaternionHelper.toTag(getPhysicsRotation(new Quaternion())));
        tag.put("linear_velocity", VectorHelper.toTag(getLinearVelocity(new Vector3f())));
        tag.put("angular_velocity", VectorHelper.toTag(getAngularVelocity(new Vector3f())));

        /* Properties */
        tag.putFloat("drag_coefficient", getDragCoefficient());
        tag.putFloat("mass", getMass());
        tag.putFloat("friction", getFriction());
        tag.putFloat("restitution", getRestitution());
    }

    @Override
    public void setCollisionShape(CollisionShape collisionShape) {
        super.setCollisionShape(collisionShape);
        this.setEnvironmentLoadDistance(calculateLoadDistance());
    }

    public void setFrame(Frame frame) {
        this.frame = frame;
    }

    public Frame getFrame() {
        return this.frame;
    }

    public PhysicsElement getElement() {
        return this.element;
    }

    public MinecraftSpace getSpace() {
        return this.space;
    }

    public boolean isInNoClip() {
        return getElement().isInNoClip();
    }

    public void setPropertiesDirty(boolean propertiesDirty) {
        this.propertiesDirty = propertiesDirty;
    }

    public boolean arePropertiesDirty() {
        return this.propertiesDirty;
    }

    @Override
    public Vector3f getOutlineColor() {
        return new Vector3f(1.0f, 0.6f, 0);
    }

    @Override
    public DebugLayer getDebugLayer() {
        return DebugLayer.ENTITY;
    }

    public void applyDrag() {
        if (shouldDoFluidResistance()) {
            World world = getSpace().getWorld();
            float drag;

            BlockPos blockPos = new BlockPos(VectorHelper.vector3fToVec3d(
                    boundingBox(new BoundingBox())
                            .getMax(new Vector3f())));

            BlockView chunk = world.getChunkManager().getChunk(blockPos.getX() >> 4, blockPos.getZ() >> 4);
            Block block = Blocks.AIR;

            if (chunk != null) {
                block = chunk.getBlockState(blockPos).getBlock();
            }

            if (Blocks.LAVA.equals(block)) {
                drag = space.getLavaDensity();
            } else if (Blocks.WATER.equals(block)) {
                drag = space.getWaterDensity();
            } else {
                drag = space.getAirDensity();
            }

            float dragCoefficient = getDragCoefficient();
            float gravitationalForce = getMass() * space.getGravity(new Vector3f()).length();
            float area = (float) Math.pow(boundingBox(new BoundingBox()).getExtent(new Vector3f()).lengthSquared(), 2);
            float k = (drag * dragCoefficient * area) / 2.0f;

            Vector3f force = new Vector3f()
                    .set(getLinearVelocity(new Vector3f()))
                    .multLocal(-getLinearVelocity(new Vector3f()).lengthSquared())
                    .multLocal(k);

            if (drag != space.getAirDensity() && force.y > -gravitationalForce) {
                /* Makes the object stop when it collides with a more dense liquid */
                applyCentralImpulse(getLinearVelocity(new Vector3f()).multLocal(-getMass()));
            } else if (Float.isFinite(force.length()) && force.length() > 0.1f) {
                applyCentralForce(force);
            }
        }
    }

    /* Property Setters */

    @Override
    public void setMass(float mass) {
        super.setMass(mass);
        this.setPropertiesDirty(true);
    }

    public void setDragCoefficient(float dragCoefficient) {
        this.dragCoefficient = dragCoefficient;
        this.setPropertiesDirty(true);
    }

    @Override
    public void setFriction(float friction) {
        super.setFriction(friction);
        this.setPropertiesDirty(true);
    }

    @Override
    public void setRestitution(float restitution) {
        super.setRestitution(restitution);
        this.setPropertiesDirty(true);
    }

    public void setEnvironmentLoadDistance(int envLoadDistance) {
        this.envLoadDistance = envLoadDistance;
        this.setPropertiesDirty(true);
    }

    public void setDoFluidResistance(boolean doFluidResistance) {
        this.doFluidResistance = doFluidResistance;
        this.setPropertiesDirty(true);
    }

    public void prioritize(@Nullable PlayerEntity player) {
        priorityPlayer = player;
        this.setPropertiesDirty(true);
    }

    /*
     * Property Getters
     *     getMass()
     *     getFriction()
     *     getRestitution()
     */

    public PlayerEntity getPriorityPlayer() {
        return this.priorityPlayer;
    }

    public float getDragCoefficient() {
        return dragCoefficient;
    }

    public int getEnvironmentLoadDistance() {
        return this.envLoadDistance;
    }

    public boolean shouldDoFluidResistance() {
        return this.doFluidResistance;
    }
}
