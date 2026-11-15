package com.igxxd.throwdisc.entity;

import com.igxxd.throwdisc.init.ModEntities;
import com.igxxd.throwdisc.init.ModItems;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ThrownDiscEntity extends ThrowableItemProjectile {
    private int remainingBounces;
    private float damage;
    private float knockback;
    private boolean isSplitCopy;
    private boolean hasSplitEnchant;
    private boolean returningToOwner;
    private final Set<Integer> visitedEntities = new HashSet<>();
    private int ticksInAir = 0;

    public ThrownDiscEntity(EntityType<? extends ThrowableItemProjectile> entityType, Level level) {
        super(entityType, level);
    }

    public ThrownDiscEntity(Level level, LivingEntity owner, ItemStack stack, float damage, float knockback, int bounces, boolean isSplitCopy, boolean hasSplitEnchant) {
        super(ModEntities.THROWN_DISC, owner, level, stack);
        this.damage = damage;
        this.knockback = knockback;
        this.remainingBounces = bounces;
        this.isSplitCopy = isSplitCopy;
        this.hasSplitEnchant = hasSplitEnchant;
    }

    @Override
    protected Item getDefaultItem() {
        return ModItems.IRON_THROWING_DISC;
    }

    @Override
    protected double getDefaultGravity() {
        return 0.015;
    }

    @Override
    protected float getAirDrag() {
        return 0.99f;
    }

    @Override
    public void tick() {
        super.tick();
        ticksInAir++;

        if (level().isClientSide()) {
            if (random.nextInt(2) == 0) {
                level().addParticle(ParticleTypes.CRIT, getX(), getY() + 0.1, getZ(), 0, 0, 0);
            }
            return;
        }

        if (returningToOwner) {
            Entity owner = getOwner();
            if (owner instanceof LivingEntity livingOwner && livingOwner.isAlive() && owner.level() == level()) {
                Vec3 toOwner = livingOwner.getEyePosition().subtract(position());
                double dist = toOwner.length();
                if (dist < 1.6) {
                    if (!isSplitCopy && owner instanceof Player player) {
                        ItemStack discStack = getItem().copy();
                        if (!player.getInventory().add(discStack) && level() instanceof ServerLevel serverLevel) {
                            player.spawnAtLocation(serverLevel, discStack);
                        }
                        level().playSound(null, player.getX(), player.getY(), player.getZ(),
                            SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.8f, 1.4f);
                    }
                    this.discard();
                    return;
                } else if (dist > 64.0) {
                    if (!isSplitCopy && level() instanceof ServerLevel serverLevel) {
                        spawnAtLocation(serverLevel, getItem());
                    }
                    this.discard();
                    return;
                } else {
                    setDeltaMovement(toOwner.normalize().scale(1.4));
                }
            } else {
                if (!isSplitCopy && level() instanceof ServerLevel serverLevel) {
                    spawnAtLocation(serverLevel, getItem());
                }
                this.discard();
                return;
            }
        }

        if (ticksInAir > 400) {
            if (!returningToOwner) {
                returningToOwner = true;
            } else {
                if (!isSplitCopy && level() instanceof ServerLevel serverLevel) {
                    spawnAtLocation(serverLevel, getItem());
                }
                this.discard();
            }
        }
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        Entity entity = result.getEntity();
        if (!(entity instanceof LivingEntity living) || !living.isAlive()) {
            return;
        }

        if (!level().isClientSide() && level() instanceof ServerLevel serverLevel) {
            DamageSource source = damageSources().thrown(this, getOwner());
            living.hurtServer(serverLevel, source, this.damage);

            Vec3 motion = getDeltaMovement();
            double len = Math.sqrt(motion.x * motion.x + motion.z * motion.z);
            if (len > 0.001) {
                living.knockback(this.knockback, -motion.x / len, -motion.z / len, source, 1.0f);
            }

            level().playSound(null, getX(), getY(), getZ(), SoundEvents.TRIDENT_HIT, SoundSource.PLAYERS, 1.0f, 1.2f + random.nextFloat() * 0.4f);
            serverLevel.sendParticles(ParticleTypes.CRIT, getX(), getY(), getZ(), 8, 0.2, 0.2, 0.2, 0.1);

            visitedEntities.add(living.getId());

            // Split enchantment: 50% chance per mob hit, allows split only main trajectory
            if (!this.isSplitCopy && this.hasSplitEnchant && this.random.nextFloat() < 0.5f) {
                LivingEntity ownerLiving = getOwner() instanceof LivingEntity l ? l : null;
                ThrownDiscEntity splitDisc = new ThrownDiscEntity(
                    level(),
                    ownerLiving,
                    getItem().copy(),
                    this.damage * 0.85f,
                    this.knockback,
                    Math.max(1, this.remainingBounces),
                    true,
                    false
                );
                splitDisc.setPos(getX(), getY(), getZ());
                Vec3 opp = motion.reverse().normalize().add(
                    (random.nextDouble() - 0.5) * 0.4,
                    (random.nextDouble() - 0.5) * 0.2,
                    (random.nextDouble() - 0.5) * 0.4
                ).normalize();
                splitDisc.setDeltaMovement(opp.scale(1.3));
                level().addFreshEntity(splitDisc);
                level().playSound(null, getX(), getY(), getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.1f, 1.6f);
            }

            // Jump from entity to entity
            if (this.remainingBounces > 0) {
                AABB searchBox = this.getBoundingBox().inflate(14.0);
                List<LivingEntity> potentialTargets = level().getEntitiesOfClass(LivingEntity.class, searchBox, e -> {
                    if (e == getOwner()) return false;
                    if (e == living) return false;
                    if (!e.isAlive() || e.isSpectator()) return false;
                    if (getOwner() instanceof Player player && e == player) return false;
                    return !visitedEntities.contains(e.getId());
                });

                if (potentialTargets.isEmpty()) {
                    potentialTargets = level().getEntitiesOfClass(LivingEntity.class, searchBox, e -> {
                        if (e == getOwner()) return false;
                        if (e == living) return false;
                        if (!e.isAlive() || e.isSpectator()) return false;
                        if (getOwner() instanceof Player player && e == player) return false;
                        return true;
                    });
                }

                LivingEntity nextTarget = null;
                double closestDistSq = Double.MAX_VALUE;
                for (LivingEntity candidate : potentialTargets) {
                    double d = candidate.distanceToSqr(this);
                    if (d < closestDistSq) {
                        closestDistSq = d;
                        nextTarget = candidate;
                    }
                }

                if (nextTarget != null) {
                    this.remainingBounces--;
                    Vec3 targetCenter = nextTarget.getEyePosition().subtract(0, 0.2, 0);
                    Vec3 toTarget = targetCenter.subtract(position()).normalize();
                    this.setDeltaMovement(toTarget.scale(1.4));
                    level().playSound(null, getX(), getY(), getZ(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.5f, 1.8f);
                    serverLevel.sendParticles(ParticleTypes.ENCHANTED_HIT, getX(), getY(), getZ(), 12, 0.2, 0.2, 0.2, 0.1);
                    return;
                }
            }

            // No remaining bounces or no target found
            this.returningToOwner = true;
        }
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
        if (!level().isClientSide() && level() instanceof ServerLevel) {
            if (this.remainingBounces > 0) {
                AABB searchBox = this.getBoundingBox().inflate(10.0);
                List<LivingEntity> potentialTargets = level().getEntitiesOfClass(LivingEntity.class, searchBox, e -> {
                    if (e == getOwner() || !e.isAlive() || e.isSpectator()) return false;
                    if (getOwner() instanceof Player player && e == player) return false;
                    return !visitedEntities.contains(e.getId());
                });
                if (!potentialTargets.isEmpty()) {
                    LivingEntity target = potentialTargets.get(0);
                    this.remainingBounces--;
                    Vec3 toTarget = target.getEyePosition().subtract(position()).normalize();
                    this.setDeltaMovement(toTarget.scale(1.3));
                    level().playSound(null, getX(), getY(), getZ(), SoundEvents.ARROW_HIT, SoundSource.PLAYERS, 0.8f, 1.5f);
                    return;
                }
            }
            this.returningToOwner = true;
        }
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("RemainingBounces", this.remainingBounces);
        output.putFloat("Damage", this.damage);
        output.putFloat("Knockback", this.knockback);
        output.putBoolean("IsSplitCopy", this.isSplitCopy);
        output.putBoolean("HasSplitEnchant", this.hasSplitEnchant);
        output.putBoolean("ReturningToOwner", this.returningToOwner);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.remainingBounces = input.getIntOr("RemainingBounces", 0);
        this.damage = input.getFloatOr("Damage", 5.0f);
        this.knockback = input.getFloatOr("Knockback", 0.5f);
        this.isSplitCopy = input.getBooleanOr("IsSplitCopy", false);
        this.hasSplitEnchant = input.getBooleanOr("HasSplitEnchant", false);
        this.returningToOwner = input.getBooleanOr("ReturningToOwner", false);
    }
}
