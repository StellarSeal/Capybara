package me.stella;

import java.io.File;
import me.stella.command.dispatch.CommandDiscovery;
import me.stella.duplication.config.DuplicationDetectionConfig;
import me.stella.duplication.engine.DuplicationDetectionListener;
import me.stella.duplication.engine.DuplicationDetectionModule;
import me.stella.scan.engine.ScanService;
import me.stella.ui.Theme;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

public final class Capybara extends JavaPlugin {

   private static final boolean IS_FOLIA = detectFolia();
   private static Capybara main;
   private static DuplicationDetectionModule duplicationModule;
   private static ScanService scanService;

   private CommandDiscovery commands;

   public static Capybara get() {
      return main;
   }

   public static DuplicationDetectionModule getDuplicationModule() {
      return duplicationModule;
   }

   public static ScanService scans() {
      return scanService;
   }

   @Override
   public void onEnable() {
      main = this;
      bindNativeLayer();
      if (!getDataFolder().exists()) {
         getDataFolder().mkdirs();
      }
      scanService = new ScanService(this);
      commands = new CommandDiscovery(this);
      commands.discoverAndRegister("me.stella.command");
      initializeDuplicationDetection();
      System.gc();
   }

   private void bindNativeLayer() {
      try {
         String[] bukkitVersion = getServer().getBukkitVersion().split("\\.");
         int nativeKey = Integer.parseInt(bukkitVersion[1]) * 100
               + Integer.parseInt(bukkitVersion[2].substring(0, bukkitVersion[2].indexOf("-")));
         String nativeClass = "me.stella.nativelib.Capybara" + nativeKey;
         if (IS_FOLIA) {
            nativeClass = nativeClass + "Folia";
            Bukkit.getConsoleSender().sendMessage(
                  Component.text("Folia detected - Attempting to resolve class name", Theme.HIGHLIGHT));
         }
         Class<?> nativeImplClass = Class.forName(nativeClass, true, getClass().getClassLoader());
         CapybaraScan.load((CapybaraNative) nativeImplClass.getConstructor().newInstance());
         Bukkit.getConsoleSender().sendMessage(Component.text(
               "Binding process [Capybara] to native access level " + nativeKey + " at " + nativeImplClass.getName(),
               Theme.PRIMARY));
      } catch (Throwable t) {
         t.printStackTrace();
      }
   }

   @Override
   public void saveResource(@NotNull String path, boolean replace) {
      File location = new File(getDataFolder(), path);
      if (replace || !location.exists()) {
         super.saveResource(path, replace);
      }
   }

   private void initializeDuplicationDetection() {
      saveResource("duplication_detection.yml", false);
      File configFile = new File(getDataFolder(), "duplication_detection.yml");
      FileConfiguration configuration = YamlConfiguration.loadConfiguration(configFile);
      File logsDir = new File(getDataFolder(), "logs");
      DuplicationDetectionConfig config = DuplicationDetectionConfig.fromYaml(configuration);
      duplicationModule = new DuplicationDetectionModule(config, logsDir);
      duplicationModule.initialize();
      Bukkit.getPluginManager().registerEvents(new DuplicationDetectionListener(duplicationModule), this);
   }

   @Override
   public void onDisable() {
      if (scanService != null) {
         scanService.shutdown();
      }
      if (commands != null) {
         commands.unregister();
      }
      if (duplicationModule != null) {
         duplicationModule.shutdown();
      }
      CapybaraScan.shutdown();
   }

   private static boolean detectFolia() {
      try {
         Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
         return true;
      } catch (ClassNotFoundException e) {
         return false;
      }
   }
}
