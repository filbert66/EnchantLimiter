package com.yahoo.phil_work.enchlimiter;

import java.util.HashMap;
import java.util.Map;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.CommandExecutor;
import org.bukkit.entity.Player;

public class EnchLimiterCommandExecutor implements CommandExecutor {
	private final EnchLimiter plugin;
 
	public EnchLimiterCommandExecutor (EnchLimiter plugin) {
		this.plugin = plugin; // Store the plugin in situations where you need it.
	}
 
    void sendMsg (CommandSender requestor, String msg)
	{
		if (requestor == null)
			return;
			
		if (requestor instanceof Player) {
			requestor.sendMessage (msg);
		}
		else // Server console. Color codes may look nice on a terminal, but not on text file
		{
			requestor.sendMessage(ChatColor.stripColor (msg));
		}
	}

    @Override
    public boolean onCommand(CommandSender sender, Command command, String commandLabel, String[] args) {
        String commandName = command.getName().toLowerCase();
        String[] trimmedArgs = args;

        if (commandName.equals("el")) {
            return elCommands(sender, trimmedArgs);
		}
		return false;
	}

    private boolean elCommands(CommandSender sender, String[] args) {	
		if (args.length == 0) 
			return false; // print usage

		String commandName = args[0].toLowerCase();
		int nextArg = 1;
		
        if (commandName.equals("print")) {
			sendMsg (sender, "print not yet implemented");
            return true; // printConfig (sender);
		}
		else if (commandName.equals("reload")) {
			plugin.reloadConfig();
			plugin.checkConfig();
			return true;									
		}
		else if (commandName.equals("save")) {
			plugin.saveConfig();
			return true;									
		}
		else if (commandName.equals ("set")) {			
			if (nextArg + 1 > args.length) {
				return false;
			}
			String fieldName = args [nextArg++].toLowerCase();
			String configName = null;
			boolean invert = false;
			if (fieldName.equals ("message")) {
				HashMap<String, String> shortNameMap = new HashMap<String,String>();
				shortNameMap.put ("cancel", "Message on cancel");
				shortNameMap.put ("limit", "Message on limit");
				shortNameMap.put ("disallowed", "Message on disallowed");
				if (nextArg + 1 > args.length) 
					return false;
				configName = shortNameMap.get (args [nextArg].toLowerCase());
				if (configName == null) {
					sendMsg (sender, "message '" + args [nextArg] + "': unknown command");
					return false;
				}
				else nextArg++;
			} else {
				HashMap<String, String> shortNameMap = new HashMap<String,String>();
				shortNameMap.put ("multiples", "Limit Multiples");
				shortNameMap.put ("punish", "Restore levels");
				shortNameMap.put ("repairs", "Stop repairs");
				shortNameMap.put ("spawns", "Fix spawned items");
				shortNameMap.put ("pickup", "Stop pickup");

				shortNameMap.put ("hold", "Fix held items");				
				shortNameMap.put ("downgrade", "Downgrade repairs");
				shortNameMap.put ("infinite", "Infinite anvils");
				if ( !(fieldName.equals ("downgrade") || fieldName.equals ("infinite") || fieldName.equals ("hold")))
					invert = true;	// syntax above 'set multiples true' means don't limit
					
				configName = shortNameMap.get (fieldName);
				if (configName == null) {
					sendMsg (sender, "set '" + fieldName + "': unknown command");	
					return false;
				}
			}
			
			if (nextArg + 1 > args.length) {
				sendMsg (sender, ChatColor.BLUE + configName + ChatColor.DARK_BLUE + 
								" is currently " + ChatColor.GRAY + plugin.getConfig().getBoolean (configName));
				return true;
			}				
			// else have a param
			boolean turnOn = args[nextArg].toLowerCase().equals ("true") || args[nextArg].toLowerCase().equals ("on");
			plugin.getConfig().set (configName, invert ? !turnOn : turnOn);
			sendMsg (sender, ChatColor.BLUE + configName + ChatColor.DARK_BLUE + 
								" set to " + ChatColor.GRAY + plugin.getConfig().getBoolean (configName));
			return true;
		}				
		return false;
	}
}
		