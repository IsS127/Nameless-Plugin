package com.namelessmc.namelessplugin.spigot;

import java.lang.reflect.Field;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandMap;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import com.namelessmc.namelessplugin.spigot.API.NamelessAPI;
import com.namelessmc.namelessplugin.spigot.API.UpdateChecker;
import com.namelessmc.namelessplugin.spigot.API.utils.NamelessChat;
import com.namelessmc.namelessplugin.spigot.API.utils.NamelessMessages;
import com.namelessmc.namelessplugin.spigot.commands.CommandWithArgs;
import com.namelessmc.namelessplugin.spigot.commands.alone.GetNotificationsCommand;
import com.namelessmc.namelessplugin.spigot.commands.alone.GetUserCommand;
import com.namelessmc.namelessplugin.spigot.commands.alone.RegisterCommand;
import com.namelessmc.namelessplugin.spigot.commands.alone.ReportCommand;
import com.namelessmc.namelessplugin.spigot.commands.alone.SetGroupCommand;
import com.namelessmc.namelessplugin.spigot.hooks.MVdWPlaceholderUtil;
import com.namelessmc.namelessplugin.spigot.hooks.PAPIPlaceholderUtil;
import com.namelessmc.namelessplugin.spigot.player.PlayerEventListener;

import net.milkbowl.vault.permission.Permission;

public class NamelessPlugin extends JavaPlugin {

	/*
	 * Instance
	 */
	private static NamelessPlugin instance;

	/*
	 * Plugin API
	 */
	private NamelessAPI api;

	/*
	 * API URL
	 */
	private String apiURL = "";
	private boolean hasSetUrl = false;

	/*
	 * NamelessMC permission string.
	 */

	public static final String permission = "namelessmc";
	public static final String permissionAdmin = "namelessmc.admin";

	/*
	 * MCStats
	 */
	// private MCStats mcStats;

	/*
	 * Vault
	 */
	public boolean useVault = false;
	private Permission permissions = null;

	/*
	 * Groups Support
	 */
	public boolean useGroups = false;

	/*
	 * Spigot or Bukkit?
	 */
	private boolean spigot = true;

	/*
	 * Bukkit command maps
	 */
	Field bukkitCommandMap;
	CommandMap commandMap;

	@Override
	public void onLoad() {
		NamelessPlugin.instance = this;
	}

	/*
	 * OnEnable method
	 */
	@Override
	public void onEnable() {

		// Check Sofware (Spigot or Bukkit)
		checkSoftware();

		// Register the API
		api = new NamelessAPI(this);

		// Disabled for now
		/*
		 * try { mcStats = new MCStats(this); mcStats.start();
		 * NamelessChat.sendToLog(NamelessMessages.PREFIX_INFO,
		 * "&aMetrics Started!"); } catch (IOException e) { e.printStackTrace();
		 * }
		 */

		// Init config files.
		api.getConfigManager().initializeFiles();

		if (hasSetUrl) {
			registerListeners();
			detectVault();
			initHooks();
		}
		if (getAPI().getConfigManager().getConfig().getBoolean("update-checker")) {
			checkForUpdate();
		} else {
			NamelessChat.sendToLog(NamelessMessages.PREFIX_WARNING, "&CIt is recommended to enable update checker.");
		}
	}

	public void checkForUpdate() {
		UpdateChecker updateChecker = new UpdateChecker(this);
		if(updateChecker.updateNeeded()){
			for(String msg : updateChecker.getConsoleUpdateMessage()){
				NamelessChat.sendToLog(NamelessMessages.PREFIX_WARNING, msg);
			}
		}else{
			NamelessChat.sendToLog(NamelessMessages.PREFIX_INFO, "&aFound no new updates!");
		}
	}

	/*
	 * Register Commands/Events
	 */
	public void registerListeners() {

		// Register commands & listeners if url has been set

		try {
			bukkitCommandMap = Bukkit.getServer().getClass().getDeclaredField("commandMap");
			bukkitCommandMap.setAccessible(true);
			commandMap = (CommandMap) bukkitCommandMap.get(Bukkit.getServer());
		} catch (IllegalArgumentException | IllegalAccessException | NoSuchFieldException | SecurityException e) {
			e.printStackTrace();
		}

		String namelessMC = this.getName();
		if (api.getConfigManager().getCommandsConfig().getBoolean("Commands.Alone.Use")
				&& api.getConfigManager().getCommandsConfig().getBoolean("Commands.SubCommand.Use")) {
			NamelessChat.sendToLog(NamelessMessages.PREFIX_WARNING,
					"&4ERROR REGISTERING COMMANDS! BOUTH IS SET TO TRUE!");
		} else if (api.getConfigManager().getCommandsConfig().getBoolean("Commands.Alone.Use")) {
			String register = api.getConfigManager().getCommandsConfig().getString("Commands.Alone.Register");
			String getUser = api.getConfigManager().getCommandsConfig().getString("Commands.Alone.GetUser");
			String getNotifications = api.getConfigManager().getCommandsConfig()
					.getString("Commands.Alone.GetNotifications");
			String setGroup = api.getConfigManager().getCommandsConfig().getString("Commands.Alone.SetGroup");
			String report = api.getConfigManager().getCommandsConfig().getString("Commands.Alone.Report");

			commandMap.register(namelessMC, new RegisterCommand(this, register));
			commandMap.register(namelessMC, new GetUserCommand(this, getUser));
			commandMap.register(namelessMC, new GetNotificationsCommand(this, getNotifications));
			commandMap.register(namelessMC, new SetGroupCommand(this, setGroup));
			if (api.getConfigManager().getConfig().getBoolean("enable-reports")) {
				commandMap.register(namelessMC, new ReportCommand(this, report));
			}
		} else if (api.getConfigManager().getCommandsConfig().getBoolean("Commands.SubCommand.Use")) {
			String subCommand = api.getConfigManager().getCommandsConfig().getString("Commands.SubCommand.Main");
			commandMap.register(namelessMC, new CommandWithArgs(subCommand));
		} else {
			NamelessChat.sendToLog(NamelessMessages.PREFIX_WARNING, "&4ERROR REGISTERING COMMANDS!");
		}

		// Register events
		getServer().getPluginManager().registerEvents(new PlayerEventListener(this), this);

	}

	public void checkSoftware() {

		try {
			Class.forName("org.spigotmc.Metrics");
		} catch (Exception e) {
			spigot = false;
			e.printStackTrace();
		}

	}

	/*
	 * Check if Vault is Activated
	 */
	public void detectVault() {
		if (getServer().getPluginManager().getPlugin("Vault") != null) {
			// Enable Vault integration and setup Permissions.
			useVault = true;
			initPermissions();
			// Check if the permissions plugin has groups.
			if (permissions.hasGroupSupport()) {
				useGroups = true;
			} else {
				NamelessChat.sendToLog(NamelessMessages.PREFIX_WARNING,
						"&4Permissions plugin does NOT support groups! Disabling NamelessMC Vault integration.");
				useGroups = false;
			}
		} else {
			NamelessChat.sendToLog(NamelessMessages.PREFIX_WARNING,
					"&4Couldn't detect Vault, disabling NamelessMC Vault integration.");
		}
	}

	/*
	 * Initialize hooks
	 */
	private void initHooks() {
		if (Bukkit.getPluginManager().isPluginEnabled("MVdWPlaceholderAPI")) {
			MVdWPlaceholderUtil placeholders = new MVdWPlaceholderUtil(this);
			placeholders.hook();
		}
		if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
			PAPIPlaceholderUtil placeholders = new PAPIPlaceholderUtil(this);
			placeholders.hook();
		}
	}

	/*
	 * Initialise Vault permissions integration for group sync
	 */
	private boolean initPermissions() {

		if (useVault) {
			RegisteredServiceProvider<Permission> rsp = getServer().getServicesManager()
					.getRegistration(Permission.class);
			permissions = rsp.getProvider();
		}

		return permissions != null;
	}

	/*
	 * Get / Has / Set
	 */

	// Gets the instance
	public static NamelessPlugin getInstance() {
		return instance;
	}

	// Gets the website api url.
	public String getAPIUrl() {
		return apiURL;
	}

	// Gets the Plugin API
	public NamelessAPI getAPI() {
		return api;
	}

	// Checks if hasSetUrl
	public boolean hasSetUrl() {
		return hasSetUrl;
	}

	// Sets HasSetUrl
	public void setHasSetUrl(boolean value) {
		hasSetUrl = value;
	}

	// Sets api url
	public void setAPIUrl(String value) {
		apiURL = value;
	}

	// Check if Spigot
	public boolean isSpigot() {
		return spigot;
	}

	// Check if Bukkit
	public boolean isBukkit() {
		return !spigot;
	}

	// Set Spigot (true or false)
	public void setSpigot(boolean value) {
		spigot = value;
	}

}