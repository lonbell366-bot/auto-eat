package com.autoeat;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.HashSet;
import java.util.Set;

public class AutoEatConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File CONFIG_FILE = new File(FabricLoader.getInstance().getConfigDir().toFile(), "autoeat.json");

    public boolean enabled = true;
    public int hungerThreshold = 14;
    public int targetHunger = 20;

    public SelectionMode selectionMode = SelectionMode.AUTO;
    public int preferredSlot = 8;

    public PriorityMode priorityMode = PriorityMode.HIGHEST;

    public int preEatDelay = 3;
    public int postEatDelay = 4;

    public boolean pauseInCombat = true;
    public boolean pauseWhenUsingItem = true;

    // Tinh nang Ne Flag & Chong Sus tren DonutSMP
    public boolean antiCheatBypass = true; // Chong MultiTask, chong bam vao Ruong/Cua, dong bo packet
    public boolean humanJitter = true;     // Cong them do tre ngau nhien nhu nguoi that

    public Set<String> blacklistedFoods = new HashSet<>();

    public enum SelectionMode { AUTO, PREFERRED_SLOT }
    public enum PriorityMode { HIGHEST, LOWEST }

    public AutoEatConfig() {
        blacklistedFoods.add("minecraft:golden_apple");
        blacklistedFoods.add("minecraft:enchanted_golden_apple");
        blacklistedFoods.add("minecraft:chorus_fruit");
        blacklistedFoods.add("minecraft:rotten_flesh");
        blacklistedFoods.add("minecraft:spider_eye");
        blacklistedFoods.add("minecraft:pufferfish");
        blacklistedFoods.add("minecraft:poisonous_potato");
        blacklistedFoods.add("minecraft:suspicious_stew");
    }

    public boolean isBlacklisted(Item item) {
        Identifier id = Registries.ITEM.getId(item);
        return blacklistedFoods.contains(id.toString());
    }

    public void toggleBlacklist(Item item) {
        String id = Registries.ITEM.getId(item).toString();
        if (blacklistedFoods.contains(id)) {
            blacklistedFoods.remove(id);
        } else {
            blacklistedFoods.add(id);
        }
        save();
    }

    public static AutoEatConfig load() {
        if (CONFIG_FILE.exists()) {
            try (FileReader reader = new FileReader(CONFIG_FILE)) {
                return GSON.fromJson(reader, AutoEatConfig.class);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        AutoEatConfig config = new AutoEatConfig();
        config.save();
        return config;
    }

    public void save() {
        try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
            GSON.toJson(this, writer);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
