package com.example.bot.bot;

import org.bukkit.entity.Player;

import java.util.UUID;

public class Bot {
    private final String name;
    private final UUID uuid;
    private final UUID ownerUuid;
    private Player entity;
    private boolean persistent;
    private boolean sneaking;
    private BotAction currentAction = BotAction.IDLE;
    private BotActionMode actionMode = BotActionMode.SINGLE;
    private int actionIntervalTicks = 1;
    private int actionTicksRemaining = 0;
    private final boolean original;
    private long ownerOfflineSinceMillis;
    private String miningTarget;
    private float miningProgress;

    public Bot(String name, UUID uuid, Player entity, boolean persistent) {
        this(name, uuid, entity, persistent, null);
    }

    public Bot(String name, UUID uuid, Player entity, boolean persistent, UUID ownerUuid) {
        this(name, uuid, entity, persistent, ownerUuid, false);
    }

    public Bot(String name, UUID uuid, Player entity, boolean persistent, UUID ownerUuid, boolean original) {
        this.name = name;
        this.uuid = uuid;
        this.entity = entity;
        this.persistent = persistent;
        this.ownerUuid = ownerUuid;
        this.original = original;
    }

    public String getName() { return name; }
    public UUID getUuid() { return uuid; }
    public UUID getOwnerUuid() { return ownerUuid; }
    public Player getEntity() { return entity; }
    public void setEntity(Player entity) { this.entity = entity; }
    public boolean isPersistent() { return persistent; }
    public boolean isOriginal() { return original; }
    public long getOwnerOfflineSinceMillis() { return ownerOfflineSinceMillis; }
    public void setOwnerOfflineSinceMillis(long value) { ownerOfflineSinceMillis = Math.max(0L, value); }
    public void setPersistent(boolean persistent) { this.persistent = persistent; }
    public BotAction getCurrentAction() { return currentAction; }
    public boolean isSneaking() { return sneaking; }
    public BotActionMode getActionMode() { return actionMode; }
    public int getActionIntervalTicks() { return actionIntervalTicks; }
    public int getActionTicksRemaining() { return actionTicksRemaining; }

    public void startRepeatingAction(BotAction action, BotActionMode mode, int intervalTicks) {
        this.currentAction = action == null ? BotAction.IDLE : action;
        this.miningTarget = null;
        this.miningProgress = 0.0f;
        this.actionMode = mode == null ? BotActionMode.SINGLE : mode;
        this.actionIntervalTicks = Math.max(1, intervalTicks);
        this.actionTicksRemaining = 0;
    }

    public void tickActionTimer() {
        if (actionMode == BotActionMode.SINGLE || currentAction == BotAction.IDLE) return;
        if (actionTicksRemaining > 0) actionTicksRemaining--;
    }

    public boolean actionReady() {
        return actionMode != BotActionMode.SINGLE && currentAction != BotAction.IDLE && actionTicksRemaining <= 0;
    }

    public void markActionExecuted() {
        actionTicksRemaining = actionMode == BotActionMode.CONTINUOUS ? 0 : actionIntervalTicks;
    }

    public void setCurrentAction(BotAction action) {
        this.currentAction = action == null ? BotAction.IDLE : action;
    }

    public void clearAction() {
        currentAction = BotAction.IDLE;
        miningTarget = null;
        miningProgress = 0.0f;
        actionMode = BotActionMode.SINGLE;
        actionIntervalTicks = 1;
        actionTicksRemaining = 0;
    }


    public String getMiningTarget() { return miningTarget; }
    public float getMiningProgress() { return miningProgress; }

    public void setMiningState(String target, float progress) {
        this.miningTarget = target;
        this.miningProgress = Math.max(0.0f, Math.min(1.0f, progress));
    }

    public void clearMiningState() {
        this.miningTarget = null;
        this.miningProgress = 0.0f;
    }

    public void setSneaking(boolean sneaking) {
        this.sneaking = sneaking;
    }

    public boolean isValid() {
        return entity != null && entity.isValid() && !entity.isDead() && entity.getHealth() > 0.0;
    }
}
