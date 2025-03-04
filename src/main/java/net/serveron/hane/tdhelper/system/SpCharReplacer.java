package net.serveron.hane.tdhelper.system;

import net.serveron.hane.tdhelper.CustomConfig;
import net.serveron.hane.tdhelper.TDhelper;
import net.serveron.hane.tdhelper.system.ServerPinger.PingResult;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ExecutionException;

public class SpCharReplacer extends BukkitRunnable {
    private final JavaPlugin plugin;
    private final FileConfiguration CONFIG;

    public SpCharReplacer(JavaPlugin plugin){
        this.plugin = plugin;
        CONFIG = plugin.getConfig();
    }

    @Override
    public void run() {
        YamlConfiguration yml = CustomConfig.getYmlByID(TDhelper.SPECIAL_DATA);
        Map<String, PingResult> pingResultMap = new HashMap<>();
        Set<String> uuidSet = yml.getKeys(false);
        Map<String, Entity> tdMap = new HashMap<>();
        //非同期ではgetEntityが使えない
        uuidSet.forEach(uuid-> tdMap.put(uuid, Bukkit.getEntity(UUID.fromString(uuid))));

        Bukkit.getScheduler().runTaskAsynchronously(plugin,()-> {
            ConfigurationSection serverCS =CONFIG.getConfigurationSection("server");
            if(serverCS==null)return;
            serverCS.getKeys(false).forEach(server -> {
                String ip = CONFIG.getString("server." + server);
                if (ip == null) return;
                String address = ip.substring(0, ip.indexOf(":"));
                int port = Integer.parseInt(ip.substring(ip.indexOf(":") + 1));

                try {
                    pingResultMap.put(server, ServerPinger.ping(address, port).get());
                }catch (InterruptedException | ExecutionException e) {
                    pingResultMap.put(server, new PingResult(-1,-1,"§c§lタイムアウト"));
                    return;
                }
            });


            uuidSet.forEach(uuidStr -> {
                if(!tdMap.containsKey(uuidStr))return;
                ConfigurationSection uuidCS = yml.getConfigurationSection(uuidStr + ".sp_status");
                if(uuidCS==null){
                    SpCharManager.deleteFromYml(uuidStr);
                    return;
                }
                uuidCS.getKeys(false).forEach(type -> {
                    List<String> serverList = yml.getStringList(uuidStr + ".sp_status." + type);
                    serverList.forEach(server -> {
                        if (!pingResultMap.containsKey(server)) return;
                        PingResult pr = pingResultMap.get(server);
                        String defStr = yml.getString(uuidStr + ".def_text");
                        String replaceAfter = switch (type) {
                            case "players" -> String.valueOf(pr.players());
                            case "max_players" -> String.valueOf(pr.maxPlayers());
                            case "motd" -> String.valueOf(pr.motd());
                            default -> null;
                        };
                        ((TextDisplay) tdMap.get(uuidStr)).setText(defStr.replaceAll(SpCharManager.ENCLOSE_CHAR + type + ":" + server + SpCharManager.ENCLOSE_CHAR, replaceAfter));

                    });
                });
            });
        });
    }


}
