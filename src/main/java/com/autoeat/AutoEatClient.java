package com.autoeat;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.FoodComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import org.lwjgl.glfw.GLFW;

public class AutoEatClient implements ClientModInitializer {
    public static AutoEatConfig config;

    private enum State { IDLE, PRE_EAT_DELAY, EATING, POST_EAT_DELAY }
    private State currentState = State.IDLE;

    private int savedSlot = -1;
    private int targetFoodSlot = -1;
    private int delayTimer = 0;
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
        if (player.hurtTime > 0) combatCooldownTicks = 60;

        if (client.currentScreen != null) {
            if (currentState != State.IDLE) {
                resetState(client, true);
            }
            return;
        }

        if (currentState != State.IDLE) {
            if (player.getInventory().selectedSlot != targetFoodSlot) {
                client.options.useKey.setPressed(false);
                currentState = State.IDLE;
                savedSlot = -1;
                targetFoodSlot = -1;
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
        int currentHunger = player.getHungerManager().getFoodLevel();

        if (currentHunger > config.hungerThreshold || !player.canConsume(false)) {
            return;
        }

        if (config.pauseInCombat && combatCooldownTicks > 0) return;
        if (config.pauseWhenUsingItem && player.isUsingItem()) return;

        int bestSlot = findFoodSlot(player);
        if (bestSlot == -1) {
            if (noFoodWarnCooldown == 0) {
                player.sendMessage(Text.literal("§c[AutoEat] Khong co do an hop le trong Hotbar!"), true);
                noFoodWarnCooldown = 100;
            }
            return;
        }

        savedSlot = player.getInventory().selectedSlot;
        targetFoodSlot = bestSlot;

        player.getInventory().selectedSlot = targetFoodSlot;
        delayTimer = config.preEatDelay;
        currentState = State.PRE_EAT_DELAY;
    }

    private void handlePreEatDelay(MinecraftClient client, PlayerEntity player) {
        if (delayTimer > 0) {
            delayTimer--;
            return;
        }
        client.options.useKey.setPressed(true);
        currentState = State.EATING;
    }

    private void handleEatingState(MinecraftClient client, PlayerEntity player) {
        int currentHunger = player.getHungerManager().getFoodLevel();
        ItemStack currentStack = player.getInventory().getStack(targetFoodSlot);

        boolean reachedTarget = currentHunger >= config.targetHunger || !player.canConsume(false);
        boolean outOfFood = !isValidFood(currentStack);
        boolean interruptedByCombat = config.pauseInCombat && combatCooldownTicks > 0;

        if (reachedTarget || outOfFood || interruptedByCombat) {
            client.options.useKey.setPressed(false);
            delayTimer = config.postEatDelay;
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
        if (savedSlot >= 0 && savedSlot < 9) {
            player.getInventory().selectedSlot = savedSlot;
        }
        resetState(client, false);
    }

    private void resetState(MinecraftClient client, boolean restoreSlot) {
        if (client.options != null) {
            client.options.useKey.setPressed(false);
        }
        if (restoreSlot && client.player != null && savedSlot >= 0 && savedSlot < 9) {
            client.player.getInventory().selectedSlot = savedSlot;
        }
        currentState = State.IDLE;
        savedSlot = -1;
        targetFoodSlot = -1;
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
