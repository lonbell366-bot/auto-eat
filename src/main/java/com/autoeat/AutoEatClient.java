package com.autoeat;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.block.AnvilBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.CraftingTableBlock;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.FenceGateBlock;
import net.minecraft.block.TrapdoorBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.FoodComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import org.lwjgl.glfw.GLFW;

import java.util.concurrent.ThreadLocalRandom;

public class AutoEatClient implements ClientModInitializer {
    public static AutoEatConfig config;

    private enum State { IDLE, PRE_EAT_DELAY, EATING, POST_EAT_DELAY }
    private State currentState = State.IDLE;

    private int savedSlot = -1;
    private int targetFoodSlot = -1;
    private boolean usingOffhand = false;
    private int delayTimer = 0;
    private int eatSessionCooldown = 0;
    private int maxEatTicks = 0;
    private int noFoodWarnCooldown = 0;
    private int combatCooldownTicks = 0;

    private KeyBinding toggleKey;
    private KeyBinding configGuiKey;

    @Override
    public void onInitializeClient() {
        config = AutoEatConfig.load();

        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "Bat/Tat Auto Eat", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_G, "Auto Eat (DonutSMP)"
        ));

        configGuiKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "Mo Menu Cai Dat Auto Eat", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_H, "Auto Eat (DonutSMP)"
        ));

        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (world.isClient) {
                combatCooldownTicks = 60;
            }
            return ActionResult.PASS;
        });

        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
    }

    private void onTick(MinecraftClient client) {
        PlayerEntity player = client.player;
        if (player == null || client.world == null) {
            resetState(client, false);
            return;
        }

        while (toggleKey.wasPressed()) {
            config.enabled = !config.enabled;
            config.save();
            if (!config.enabled && currentState != State.IDLE) {
                resetState(client, true);
            }
            String status = config.enabled ? "§aBAT" : "§cTAT";
            player.sendMessage(Text.literal("§6[AutoEat] §fTrang thai: " + status), true);
        }

        while (configGuiKey.wasPressed()) {
            client.setScreen(new AutoEatScreen(client.currentScreen));
        }

        if (!config.enabled) return;

        if (noFoodWarnCooldown > 0) noFoodWarnCooldown--;
        if (combatCooldownTicks > 0) combatCooldownTicks--;
        if (eatSessionCooldown > 0) eatSessionCooldown--;
        if (player.hurtTime > 0) combatCooldownTicks = 60;

        // Ngat ngay khi mo GUI (Tui do, Ruong, Chat...)
        if (client.currentScreen != null) {
            if (currentState != State.IDLE) {
                resetState(client, true);
            }
            return;
        }

        // ANTI-CHEAT + VUA CHEM VUA AN:
        // Khi ban bam chuot trai de chem/dao, tam nha chuot phai trong 1 tick do de khong bi GrimAC bat loi MultiActions,
        // nhung khong huy chu trinh an de nhan vat tiep tuc an ngay khi vua vung kiem xong.
        if (config.antiCheatBypass && client.options.attackKey.isPressed()) {
            if (currentState == State.EATING) {
                client.options.useKey.setPressed(false);
            }
            return;
        }

        // Neu nguoi choi tu lan chuot / bam phim so doi o
        if (currentState != State.IDLE && !usingOffhand) {
            if (player.getInventory().selectedSlot != targetFoodSlot) {
                client.options.useKey.setPressed(false);
                currentState = State.IDLE;
                savedSlot = -1;
                targetFoodSlot = -1;
                eatSessionCooldown = 10;
                player.sendMessage(Text.literal("§e[AutoEat] §fDa huy tu dong an do ban tu doi o tay."), true);
                return;
            }
        }

        switch (currentState) {
            case IDLE -> handleIdleState(client, player);
            case PRE_EAT_DELAY -> handlePreEatDelay(client, player);
            case EATING -> handleEatingState(client, player);
            case POST_EAT_DELAY -> handlePostEatDelay(client, player);
        }
    }

    private void handleIdleState(MinecraftClient client, PlayerEntity player) {
        if (eatSessionCooldown > 0) return;

        int currentHunger = player.getHungerManager().getFoodLevel();
        if (currentHunger > config.hungerThreshold || !player.canConsume(false)) {
            return;
        }

        if (config.pauseInCombat && combatCooldownTicks > 0) return;
        if (config.pauseWhenUsingItem && player.isUsingItem()) return;

        if (config.antiCheatBypass && isLookingAtInteractiveBlock(client)) {
            return;
        }

        // Uu tien 1: Neu tay phu (Offhand) co san do an hop le thi an luon ma khong can doi o kiem tren tay chinh
        if (isValidFood(player.getOffHandStack())) {
            usingOffhand = true;
            savedSlot = player.getInventory().selectedSlot;
            targetFoodSlot = savedSlot;
            delayTimer = getRandomizedDelay(config.preEatDelay);
            currentState = State.PRE_EAT_DELAY;
            return;
        }

        // Uu tien 2: Quet 9 o Hotbar
        int bestSlot = findFoodSlot(player);
        if (bestSlot == -1) {
            if (noFoodWarnCooldown == 0) {
                player.sendMessage(Text.literal("§c[AutoEat] Khong co do an hop le trong Hotbar!"), true);
                noFoodWarnCooldown = 100;
            }
            return;
        }

        usingOffhand = false;
        savedSlot = player.getInventory().selectedSlot;
        targetFoodSlot = bestSlot;

        if (savedSlot != targetFoodSlot) {
            player.getInventory().selectedSlot = targetFoodSlot;
        }

        delayTimer = getRandomizedDelay(config.preEatDelay);
        currentState = State.PRE_EAT_DELAY;
    }

    private void handlePreEatDelay(MinecraftClient client, PlayerEntity player) {
        if (delayTimer > 0) {
            delayTimer--;
            return;
        }
        client.options.useKey.setPressed(true);
        maxEatTicks = 120;
        currentState = State.EATING;
    }

    private void handleEatingState(MinecraftClient client, PlayerEntity player) {
        maxEatTicks--;
        int currentHunger = player.getHungerManager().getFoodLevel();
        ItemStack currentStack = usingOffhand ? player.getOffHandStack() : player.getInventory().getStack(targetFoodSlot);

        boolean reachedTarget = currentHunger >= config.targetHunger || !player.canConsume(false);
        boolean outOfFood = !isValidFood(currentStack);
        boolean interruptedByCombat = config.pauseInCombat && combatCooldownTicks > 0;
        boolean lookingAtContainer = config.antiCheatBypass && isLookingAtInteractiveBlock(client);

        if (reachedTarget || outOfFood || interruptedByCombat || lookingAtContainer || maxEatTicks <= 0) {
            client.options.useKey.setPressed(false);
            delayTimer = getRandomizedDelay(config.postEatDelay);
            currentState = State.POST_EAT_DELAY;
        } else {
            client.options.useKey.setPressed(true);
        }
    }

    private void handlePostEatDelay(MinecraftClient client, PlayerEntity player) {
        if (delayTimer > 0) {
            delayTimer--;
            return;
        }
        if (!usingOffhand && savedSlot >= 0 && savedSlot < 9 && player.getInventory().selectedSlot != savedSlot) {
            player.getInventory().selectedSlot = savedSlot;
        }
        eatSessionCooldown = config.humanJitter ? ThreadLocalRandom.current().nextInt(8, 15) : 6;
        resetState(client, false);
    }

    private int getRandomizedDelay(int baseDelay) {
        if (!config.humanJitter) return baseDelay;
        return baseDelay + ThreadLocalRandom.current().nextInt(1, 3);
    }

    private boolean isLookingAtInteractiveBlock(MinecraftClient client) {
        if (client.crosshairTarget == null || client.crosshairTarget.getType() != HitResult.Type.BLOCK) {
            return false;
        }
        BlockHitResult blockHit = (BlockHitResult) client.crosshairTarget;
        BlockState state = client.world.getBlockState(blockHit.getBlockPos());
        Block block = state.getBlock();

        return block instanceof BlockWithEntity
                || block instanceof DoorBlock
                || block instanceof TrapdoorBlock
                || block instanceof FenceGateBlock
                || block instanceof AnvilBlock
                || block instanceof CraftingTableBlock;
    }

    private void resetState(MinecraftClient client, boolean restoreSlot) {
        if (client.options != null) {
            client.options.useKey.setPressed(false);
        }
        if (restoreSlot && !usingOffhand && client.player != null && savedSlot >= 0 && savedSlot < 9) {
            client.player.getInventory().selectedSlot = savedSlot;
        }
        currentState = State.IDLE;
        savedSlot = -1;
        targetFoodSlot = -1;
        usingOffhand = false;
        delayTimer = 0;
    }

    private int findFoodSlot(PlayerEntity player) {
        if (config.selectionMode == AutoEatConfig.SelectionMode.PREFERRED_SLOT) {
            ItemStack preferredStack = player.getInventory().getStack(config.preferredSlot);
            if (isValidFood(preferredStack)) {
                return config.preferredSlot;
            }
        }

        int bestSlot = -1;
        int bestNutrition = (config.priorityMode == AutoEatConfig.PriorityMode.HIGHEST) ? -1 : Integer.MAX_VALUE;

        for (int i = 0; i < 9; i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (!isValidFood(stack)) continue;

            FoodComponent food = stack.get(DataComponentTypes.FOOD);
            if (food == null) continue;

            int nutrition = food.nutrition();

            if (config.priorityMode == AutoEatConfig.PriorityMode.HIGHEST) {
                if (nutrition > bestNutrition) {
                    bestNutrition = nutrition;
                    bestSlot = i;
                }
            } else {
                if (nutrition < bestNutrition) {
                    bestNutrition = nutrition;
                    bestSlot = i;
                }
            }
        }
        return bestSlot;
    }

    private boolean isValidFood(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (!stack.contains(DataComponentTypes.FOOD)) return false;
        return !config.isBlacklisted(stack.getItem());
    }
    }
