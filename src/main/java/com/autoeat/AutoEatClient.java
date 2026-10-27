package com.autoeat;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.block.AnvilBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.CraftingTableBlock;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.EnderChestBlock;
import net.minecraft.block.FenceGateBlock;
import net.minecraft.block.ShulkerBoxBlock;
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
import net.minecraft.util.Hand;
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
    private int interactRetryCooldown = 0;
    private int maxEatTicks = 0;
    private int noFoodWarnCooldown = 0;
    private int combatCooldownTicks = 0;
    private boolean openGuiNextTick = false;

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

        // Ho tro PojavLauncher: Bam nut T (Chat) go ".ae" de mo Menu hoac ".aet" de Bat/Tat
        ClientSendMessageEvents.ALLOW_CHAT.register(message -> {
            String msg = message.trim();
            if (msg.equalsIgnoreCase(".ae") || msg.equalsIgnoreCase("#ae")) {
                openGuiNextTick = true;
                return false;
            }
            if (msg.equalsIgnoreCase(".aet") || msg.equalsIgnoreCase("#aet")) {
                config.enabled = !config.enabled;
                config.save();
                MinecraftClient client = MinecraftClient.getInstance();
                if (!config.enabled && currentState != State.IDLE) {
                    resetState(client, true);
                }
                if (client.player != null) {
                    String status = config.enabled ? "§aBAT" : "§cTAT";
                    client.player.sendMessage(Text.literal("§6[AutoEat] §fTrang thai: " + status), true);
                }
                return false;
            }
            return true;
        });

        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (world.isClient) {
                combatCooldownTicks = 40;
            }
            return ActionResult.PASS;
        });

        // Chay o START_CLIENT_TICK de giu chuot phai truoc khi PojavLauncher/Minecraft xu ly input
        ClientTickEvents.START_CLIENT_TICK.register(this::onTick);
    }

    private void onTick(MinecraftClient client) {
        PlayerEntity player = client.player;
        if (player == null || client.world == null) {
            resetState(client, false);
            return;
        }

        if (openGuiNextTick && client.currentScreen == null) {
            openGuiNextTick = false;
            client.setScreen(new AutoEatScreen(null));
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
        if (interactRetryCooldown > 0) interactRetryCooldown--;

        // Ngat khi mo GUI (Tui do, Ruong, Chat...)
        if (client.currentScreen != null) {
            if (currentState != State.IDLE) {
                resetState(client, true);
            }
            return;
        }

        // Neu nguoi choi tu doi o tren thanh Hotbar trong luc dang an -> Huy va giu nguyen o moi
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
        if (currentHunger > config.hungerThreshold || currentHunger >= 20 || !player.canConsume(false)) {
            return;
        }

        if (config.pauseInCombat && combatCooldownTicks > 0) return;
        if (config.pauseWhenUsingItem && player.isUsingItem()) return;
        if (config.antiCheatBypass && client.options.attackKey.isPressed()) return;
        if (config.antiCheatBypass && isLookingAtInteractiveBlock(client, player)) return;

        // 1. Kiem tra tay phu (Offhand) truoc
        if (isValidFood(player.getOffHandStack())) {
            usingOffhand = true;
            savedSlot = player.getInventory().selectedSlot;
            targetFoodSlot = savedSlot;
            delayTimer = getRandomizedDelay(config.preEatDelay);
            currentState = State.PRE_EAT_DELAY;
            return;
        }

        // 2. Quet 9 o Hotbar (0 - 8)
        int bestSlot = findFoodSlot(player);
        if (bestSlot == -1) {
            if (noFoodWarnCooldown == 0) {
                player.sendMessage(Text.literal("§c[AutoEat] Khong co do an hop le trong Hotbar! (Go .ae trong chat T de chinh)"), true);
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
        maxEatTicks = 100;
        interactRetryCooldown = 0;
        currentState = State.EATING;
        startOrContinueEating(client, player);
    }

    private void handleEatingState(MinecraftClient client, PlayerEntity player) {
        maxEatTicks--;
        int currentHunger = player.getHungerManager().getFoodLevel();
        ItemStack currentStack = usingOffhand ? player.getOffHandStack() : player.getInventory().getStack(targetFoodSlot);

        boolean reachedTarget = currentHunger >= config.targetHunger || currentHunger >= 20 || !player.canConsume(false);
        boolean outOfFood = !isValidFood(currentStack);
        boolean interruptedByCombat = config.pauseInCombat && combatCooldownTicks > 0;
        boolean lookingAtContainer = config.antiCheatBypass && isLookingAtInteractiveBlock(client, player);

        if (reachedTarget || outOfFood || interruptedByCombat || lookingAtContainer || maxEatTicks <= 0) {
            client.options.useKey.setPressed(false);
            if (player.isUsingItem() && client.interactionManager != null) {
                client.interactionManager.stopUsingItem(player);
            }
            delayTimer = getRandomizedDelay(config.postEatDelay);
            currentState = State.POST_EAT_DELAY;
            return;
        }

        // Vua chem vua an: Neu ban bam PRI (chem) thi tam nha chuot phai 1 tick de chem xong roi an tiep
        if (config.antiCheatBypass && client.options.attackKey.isPressed()) {
            client.options.useKey.setPressed(false);
            return;
        }

        startOrContinueEating(client, player);
    }

    private void startOrContinueEating(MinecraftClient client, PlayerEntity player) {
        client.options.useKey.setPressed(true);
        // Goi truc tiep interactItem de bat dau nhai ngay tren PojavLauncher
        if (!player.isUsingItem() && interactRetryCooldown == 0 && client.interactionManager != null) {
            Hand hand = usingOffhand ? Hand.OFF_HAND : Hand.MAIN_HAND;
            client.interactionManager.interactItem(player, hand);
            interactRetryCooldown = 5;
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
        eatSessionCooldown = config.humanJitter ? ThreadLocalRandom.current().nextInt(6, 12) : 5;
        resetState(client, false);
    }

    private int getRandomizedDelay(int baseDelay) {
        if (!config.humanJitter) return baseDelay;
        return baseDelay + ThreadLocalRandom.current().nextInt(0, 3);
    }

    private boolean isLookingAtInteractiveBlock(MinecraftClient client, PlayerEntity player) {
        if (player.isSneaking()) return false;
        if (client.crosshairTarget == null || client.crosshairTarget.getType() != HitResult.Type.BLOCK) {
            return false;
        }
        BlockHitResult blockHit = (BlockHitResult) client.crosshairTarget;
        BlockState state = client.world.getBlockState(blockHit.getBlockPos());
        Block block = state.getBlock();

        return block instanceof ChestBlock
                || block instanceof EnderChestBlock
                || block instanceof ShulkerBoxBlock
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
