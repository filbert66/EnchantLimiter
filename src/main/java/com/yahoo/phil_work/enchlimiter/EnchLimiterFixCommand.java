package com.yahoo.phil_work.enchlimiter;

import java.util.HashMap;
import java.util.Map;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.CommandExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.Material;

public class EnchLimiterFixCommand implements CommandExecutor {
	private final EnchLimiter plugin;
 
	public EnchLimiterFixCommand (EnchLimiter plugin) {
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

        if (commandName.equals("elfix")) {
            return fixCommand(sender, trimmedArgs);
		}
		return false;
	}

    private boolean fixCommand(CommandSender sender, String[] args) {			
		if ( !(sender instanceof Player)) {
			sendMsg (sender, "Must be a player");
			return false;
		}
		Player p = (Player)sender;
		if (args.length == 0) {
			ItemStack item = p.getItemInHand();
			if (item == null || item.getType() == Material.AIR)
				return false;
			if ( !plugin.fixItem (item, p))
				sendMsg (sender, plugin.language.get (p, "nofix", plugin.chatName + ": nothing to fix"));
			return true;
		}
		int nextArg = 0;
	
		String fieldName = args [nextArg++].toLowerCase();
		PlayerInventory pinv = p.getInventory();
		if (fieldName.equals ("all")) {
			for (ItemStack i : pinv.getArmorContents()) 
				plugin.fixItem (i, p);
			for (ItemStack i : pinv.getContents())
				plugin.fixItem (i, p);
			return true;
		} else if (fieldName.equals ("books")) {
			for (int slot : pinv.all (Material.ENCHANTED_BOOK).keySet()) 
				plugin.fixItem (pinv.getItem(slot), p);
			return true;
		} else 	
			return false;
	}
}
		