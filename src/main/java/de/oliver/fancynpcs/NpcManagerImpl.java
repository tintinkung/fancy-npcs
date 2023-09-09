package de.oliver.fancynpcs;

import de.oliver.fancynpcs.api.Npc;
import de.oliver.fancynpcs.api.NpcAttribute;
import de.oliver.fancynpcs.api.NpcData;
import de.oliver.fancynpcs.api.NpcManager;
import de.oliver.fancynpcs.api.utils.NpcEquipmentSlot;
import de.oliver.fancynpcs.api.utils.SkinFetcher;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.function.Function;

public class NpcManagerImpl implements NpcManager {

    private final JavaPlugin plugin;
    private final Function<NpcData, Npc> npcAdapter;
    private final File npcConfigFile;
    private final HashMap<String, Npc> npcs; // npc id -> npc

    public NpcManagerImpl(JavaPlugin plugin, Function<NpcData, Npc> npcAdapter) {
        this.plugin = plugin;
        this.npcAdapter = npcAdapter;
        npcs = new HashMap<>();
        npcConfigFile = new File("plugins/FancyNpcs/npcs.yml");
    }

    public void registerNpc(Npc npc) {
        npcs.put(npc.getData().getId(), npc);
    }

    public void removeNpc(Npc npc) {
        npcs.remove(npc.getData().getId());

        YamlConfiguration npcConfig = YamlConfiguration.loadConfiguration(npcConfigFile);
        npcConfig.set("npcs." + npc.getData().getName(), null);
        try {
            npcConfig.save(npcConfigFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public Npc getNpc(int entityId) {
        for (Npc npc : npcs.values()) {
            if (npc.getEntityId() == entityId) {
                return npc;
            }
        }

        return null;
    }

    @Override
    public Npc getNpc(String name) {
        for (Npc npc : npcs.values()) {
            if (npc.getData().getName().equalsIgnoreCase(name)) {
                return npc;
            }
        }

        return null;
    }

    @Override
    public Npc getNpc(String name, UUID creator) {
        for (Npc npc : npcs.values()) {
            if (npc.getData().getCreator().equals(creator) && npc.getData().getName().equalsIgnoreCase(name)) {
                return npc;
            }
        }

        return null;
    }

    public Collection<Npc> getAllNpcs() {
        return npcs.values();
    }

    public void saveNpcs(boolean force) {
        if (!npcConfigFile.exists()) {
            try {
                npcConfigFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }
        }

        YamlConfiguration npcConfig = YamlConfiguration.loadConfiguration(npcConfigFile);
        npcConfig.set("npcs", null);

        for (Npc npc : npcs.values()) {
            if (!npc.isSaveToFile()) {
                continue;
            }

            boolean shouldSave = force || npc.isDirty();
            if (!shouldSave) {
                continue;
            }

            NpcData data = npc.getData();

            npcConfig.set("npcs." + data.getId() + ".name", data.getName());
            npcConfig.set("npcs." + data.getId() + ".creator", data.getCreator().toString());
            npcConfig.set("npcs." + data.getId() + ".displayName", data.getDisplayName());
            npcConfig.set("npcs." + data.getId() + ".type", data.getType().name());
            npcConfig.set("npcs." + data.getId() + ".location.world", data.getLocation().getWorld().getName());
            npcConfig.set("npcs." + data.getId() + ".location.x", data.getLocation().getX());
            npcConfig.set("npcs." + data.getId() + ".location.y", data.getLocation().getY());
            npcConfig.set("npcs." + data.getId() + ".location.z", data.getLocation().getZ());
            npcConfig.set("npcs." + data.getId() + ".location.yaw", data.getLocation().getYaw());
            npcConfig.set("npcs." + data.getId() + ".location.pitch", data.getLocation().getPitch());
            npcConfig.set("npcs." + data.getId() + ".showInTab", data.isShowInTab());
            npcConfig.set("npcs." + data.getId() + ".spawnEntity", data.isSpawnEntity());
            npcConfig.set("npcs." + data.getId() + ".glowing", data.isGlowing());
            npcConfig.set("npcs." + data.getId() + ".glowingColor", data.getGlowingColor().toString());
            npcConfig.set("npcs." + data.getId() + ".turnToPlayer", data.isTurnToPlayer());
            npcConfig.set("npcs." + data.getId() + ".message", data.getMessage());

            if (data.getSkin() != null) {
                npcConfig.set("npcs." + data.getId() + ".skin.identifier", data.getSkin().getIdentifier());
                npcConfig.set("npcs." + data.getId() + ".skin.value", data.getSkin().getValue());
                npcConfig.set("npcs." + data.getId() + ".skin.signature", data.getSkin().getSignature());
            }

            if (data.getEquipment() != null) {
                for (Map.Entry<NpcEquipmentSlot, ItemStack> entry : data.getEquipment().entrySet()) {
                    npcConfig.set("npcs." + data.getId() + ".equipment." + entry.getKey().name(), entry.getValue());
                }
            }

            if (data.getServerCommand() != null) {
                npcConfig.set("npcs." + data.getId() + ".serverCommand", data.getServerCommand());
            }

            if (data.getPlayerCommand() != null) {
                npcConfig.set("npcs." + data.getId() + ".playerCommand", data.getPlayerCommand());
            }

            for (NpcAttribute attribute : FancyNpcs.getInstance().getAttributeManager().getAllAttributesForEntityType(data.getType())) {
                String value = data.getAttributes().getOrDefault(attribute, null);
                npcConfig.set("npcs." + data.getId() + ".attributes." + attribute.getName(), value);
            }

            npc.setDirty(false);
        }

        try {
            npcConfig.save(npcConfigFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void loadNpcs() {
        YamlConfiguration npcConfig = YamlConfiguration.loadConfiguration(npcConfigFile);

        if (!npcConfig.isConfigurationSection("npcs")) {
            return;
        }

        for (String id : npcConfig.getConfigurationSection("npcs").getKeys(false)) {
            String name = npcConfig.getString("npcs." + id + ".name");
            if (name == null) name = id;

            String creatorStr = npcConfig.getString("npcs." + id + ".creator");
            UUID creator = creatorStr == null ? null : UUID.fromString(creatorStr);

            String displayName = npcConfig.getString("npcs." + id + ".displayName");
            EntityType type = EntityType.valueOf(npcConfig.getString("npcs." + id + ".type", "PLAYER").toUpperCase());

            Location location = null;

            try {
                location = npcConfig.getLocation("npcs." + id + ".location");
            } catch (Exception ignored) {
            }

            if (location == null) {
                String worldName = npcConfig.getString("npcs." + id + ".location.world");
                World world = Bukkit.getWorld(worldName);

                if (world == null) {
                    plugin.getLogger().info("Trying to load the world: '" + worldName + "'");
                    world = new WorldCreator(worldName).createWorld();
                }

                if (world == null) {
                    plugin.getLogger().info("Could not load npc '" + id + "', because the world '" + worldName + "' is not loaded");
                    continue;
                }

                double x = npcConfig.getDouble("npcs." + id + ".location.x");
                double y = npcConfig.getDouble("npcs." + id + ".location.y");
                double z = npcConfig.getDouble("npcs." + id + ".location.z");
                float yaw = (float) npcConfig.getDouble("npcs." + id + ".location.yaw");
                float pitch = (float) npcConfig.getDouble("npcs." + id + ".location.pitch");

                location = new Location(world, x, y, z, yaw, pitch);
            }

            String skinIdentifier = npcConfig.getString("npcs." + id + ".skin.identifier", npcConfig.getString("npcs." + id + ".skin.uuid", ""));
            String skinValue = npcConfig.getString("npcs." + id + ".skin.value");
            String skinSignature = npcConfig.getString("npcs." + id + ".skin.signature");
            SkinFetcher skin = null;
            if (skinIdentifier.length() > 0) {
                skin = new SkinFetcher(skinIdentifier, skinValue, skinSignature);
            }

            boolean showInTab = npcConfig.getBoolean("npcs." + id + ".showInTab");
            boolean spawnEntity = npcConfig.getBoolean("npcs." + id + ".spawnEntity");
            boolean glowing = npcConfig.getBoolean("npcs." + id + ".glowing");
            NamedTextColor glowingColor = NamedTextColor.NAMES.value(npcConfig.getString("npcs." + id + ".glowingColor", "white"));
            boolean turnToPlayer = npcConfig.getBoolean("npcs." + id + ".turnToPlayer");
            String serverCommand = npcConfig.getString("npcs." + id + ".serverCommand");
            String playerCommand = npcConfig.getString("npcs." + id + ".playerCommand");
            String message = npcConfig.getString("npcs." + id + ".message");

            Map<NpcAttribute, String> attributes = new HashMap<>();
            if (npcConfig.isConfigurationSection("npcs." + id + ".attributes")) {
                for (String attrName : npcConfig.getConfigurationSection("npcs." + id + ".attributes").getKeys(false)) {
                    NpcAttribute attribute = FancyNpcs.getInstance().getAttributeManager().getAttributeByName(type, attrName);
                    if (attribute == null) {
                        continue;
                    }

                    String value = npcConfig.getString("npcs." + id + ".attributes." + attrName);
                    if (!attribute.isValidValue(value)) {
                        continue;
                    }

                    attributes.put(attribute, value);
                }
            }

            NpcData data = new NpcData(id, name, creator, displayName, skin, location, showInTab, spawnEntity, glowing, glowingColor, type, new HashMap<>(), turnToPlayer, null, message, serverCommand, playerCommand, attributes);
            Npc npc = npcAdapter.apply(data);

            if (npcConfig.isConfigurationSection("npcs." + id + ".equipment")) {
                for (String equipmentSlotStr : npcConfig.getConfigurationSection("npcs." + id + ".equipment").getKeys(false)) {
                    NpcEquipmentSlot equipmentSlot = NpcEquipmentSlot.parse(equipmentSlotStr);
                    ItemStack item = npcConfig.getItemStack("npcs." + id + ".equipment." + equipmentSlotStr);
                    npc.getData().addEquipment(equipmentSlot, item);
                }
            }

            npc.create();
            registerNpc(npc);
            npc.spawnForAll();
        }
    }

    public void reloadNpcs() {
        for (Npc npc : new ArrayList<>(getAllNpcs())) {
            npc.removeForAll();
        }

        loadNpcs();
    }
}
