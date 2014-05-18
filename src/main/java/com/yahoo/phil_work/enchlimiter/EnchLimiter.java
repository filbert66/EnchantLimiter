/***
 * File EnchLimiter.java
 * 
 * History:
 *  27 Feb 2014 : New from Unbreakable.java
 *  09 Mar 2014 : Removed some debug messages
 *  09 Apr 2014 : Added Limit Multiples config and "Message on limit" config.
 *  10 Apr 2014 : Added new config for inhibited enchants
 *  22 Apr 2014 : Added "ALL" enchantment config, support disallowed on anvil.
 *  01 MAY 2014 : Changed XP return algorithm on enchant event.
 *              : Allow for "ALL_*" material types.
 *  08 May 2014 : On disallowed enchant, set to lower level if permitted
 *                Added PlayerPickupItemEvent
 *
 * BUG: Sometimes able to place items in Anvil & do restricted enchant; no apparent enchant event.
 * 
 * TODO: 
 *   Disallow equipping restricted item. But To block a player from wearing something is necessary to listen:
		InventoryClick (put it on classic way)
		PlayerInteract (righ click with it in hand)
		BlockDispense (put with dispenser)
	And of all of them with it's own problem (for dispensers is almost impossible to ensure a correct blocking).
 * Alternative: Use PlayerPickupItemEvent
 */

package com.yahoo.phil_work.enchlimiter;

import com.yahoo.phil_work.LanguageWrapper;

import java.io.File;
import java.io.FileInputStream;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;
import java.util.zip.*;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.HumanEntity;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType.SlotType;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.Material;
import org.bukkit.plugin.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.PluginLogger;
import org.bukkit.scheduler.BukkitRunnable;

public class EnchLimiter extends JavaPlugin implements Listener {
	public Logger log;
	private LanguageWrapper language;
    public String chatName;

	boolean hasAndDeductXP (final Player player, int levels) {
		if (player.getGameMode() == org.bukkit.GameMode.CREATIVE)
			return true;
			
		if (player.getLevel() < levels) {
			player.sendMessage (language.get (player, "needXP", chatName + ": Insufficient XP"));
			return false;
		}  
		else {
			player.setLevel (player.getLevel() - levels);
			return true;
		}	
	}

	@EventHandler (ignoreCancelled = true)
	void enchantMonitor (EnchantItemEvent event) {
		Player player = event.getEnchanter();
		boolean limitMultiples = getConfig().getBoolean ("Limit Multiples", true) && 
			! player.hasPermission ("enchlimiter.multiple");
		
		int enchants = 0;
		ItemStack item = event.getItem();
		
		// Get List of disallowed enchants for that item and for ALL items
		Map<Enchantment, Integer> disallowedEnchants = getDisallowedEnchants (item.getType());
		disallowedEnchants.putAll (getDisallowedEnchants ("ALL"));
		//*DEBUG*/log.info ("disallowed on " + item.getType() + disallowedEnchants);
		
		if ( !limitMultiples && disallowedEnchants.isEmpty())
			return;  // nothing to do; leave event alive for another plugin
				
		Map<Enchantment,Integer> toAdd = new HashMap<Enchantment, Integer>();
		toAdd.putAll (event.getEnchantsToAdd()); // to avoid modifying while iterating

		int totalLevels =0;
		for (Enchantment e : toAdd.keySet()) 
			totalLevels += toAdd.get(e);
		float XPperLevel = event.getExpLevelCost(); XPperLevel /= totalLevels;
		//log.info ("Total enchants: " + toAdd.size() + ". Total levels: " + totalLevels + ". XP/lvl = " + XPperLevel);
			
		for (Enchantment ench : toAdd.keySet()) {
			int level = toAdd.get(ench);
			int returnedXP = (int)(0.5F + (XPperLevel * level));

			if ( !limitMultiples || enchants == 0) {
				if (disallowedEnchants.containsKey (ench) && level >= disallowedEnchants.get (ench) &&
					! player.hasPermission ("enchlimiter.disallowed") ) 
				{
					event.setExpLevelCost (event.getExpLevelCost() - returnedXP);
					event.getEnchantsToAdd().remove (ench);
					
					// try to set lower enchant level, if permitted
					if (disallowedEnchants.get (ench) > 1) {
						int newLevel = disallowedEnchants.get (ench) - 1;
						item.addEnchantment (ench, newLevel);
						int addedXP = (int)(0.5F + (XPperLevel * newLevel));
						event.setExpLevelCost (event.getExpLevelCost() + addedXP);
						returnedXP -= addedXP;
					}
					
					if (getConfig().getBoolean ("Message on disallowed", true))
						player.sendMessage (language.get (player, "disallowed2", 
											chatName + " removed disallowed {0}-{1} &returned {2} XP", ench.getName(), level, returnedXP ));
				}
				else {
					enchants++;
					//*DEBUG*/log.info ("Added " + ench + "-" + level + " to " +item.getType());
				}
			} else {
				event.setExpLevelCost (event.getExpLevelCost() - returnedXP);
				event.getEnchantsToAdd().remove (ench);

				if (getConfig().getBoolean ("Message on limit", true))
					player.sendMessage (language.get (player, "limited2", 
										chatName + " removed multiple {0}-{1} & returned {2} XP", ench.getName(), level, returnedXP ));
			}
		} 

		if (enchants == 0)  {
			//log.info ("Removed all enchants attempted on " + item.getType());
			event.setCancelled (true);
		}
	}

	//  Listen to PrepareItemCraftEvent and return one of the books 
	@EventHandler (ignoreCancelled = true)
	void craftMonitor (InventoryClickEvent event) {
		ItemStack book = null;
		ItemStack tool = null;
		Inventory inv = event.getInventory();
		boolean limitMultiples = getConfig().getBoolean ("Limit Multiples", true);
	
		// log.info ("InventoryClickEvent " +event.getAction()+" in type " + inv.getType() + " in  slot " + event.getRawSlot() + "(raw " + event.getSlot());
		InventoryAction action = event.getAction();
		boolean isPlace = false;
		switch (action) {
			case PLACE_ALL:
			case PLACE_SOME:
			case PLACE_ONE:
			case SWAP_WITH_CURSOR:
			case MOVE_TO_OTHER_INVENTORY: // could be.. 
				isPlace = true;
				break;
			default:
				break;
		}
	
		if (inv.getType()== InventoryType.ANVIL && event.getSlotType() == SlotType.CRAFTING) {
			ItemStack[] anvilContents = inv.getContents();
			ItemStack slot0 = anvilContents[0];
			ItemStack slot1 = anvilContents[1];
			
			if (isPlace) {
				//log.info ("Placed a " + event.getCursor() + " in slot " + event.getRawSlot());
				//log.info ("currentItem: " +  event.getCurrentItem());
				if (event.getRawSlot() == 1)
					slot1 = event.getCursor();
				else if (event.getRawSlot() == 0)
					slot0 = event.getCursor();
			}	
			// 1 is right slot of anvil
			if (slot1 != null && slot1.getType() == Material.ENCHANTED_BOOK)
				book = slot1;
			// 0 is left slot of Anvil
			if (slot0 != null /* && slot0.getType() == Material.ENCHANTED_BOOK */)
				tool = slot0;
			// log.info ("Found book: " + book + "; tool: " + tool);
		}
		if (book != null && tool != null && isPlace)
		{ // then might be using a book with another book
			Map<Enchantment, Integer> disallowedEnchants = getDisallowedEnchants (tool.getType());
			disallowedEnchants.putAll (getDisallowedEnchants ("ALL"));
			boolean disallowed = false;
			
			ItemMeta meta = book.getItemMeta();
			if ( !(meta instanceof EnchantmentStorageMeta))
				log.warning ("Book without storage meta: " + book);
			else {
				EnchantmentStorageMeta bookStore = (EnchantmentStorageMeta)meta;
				for (Enchantment e: bookStore.getStoredEnchants().keySet()) {
					//*DEBUG*/log.info ("testing for " + e + "-" + bookStore.getStoredEnchantLevel(e));
					if (disallowedEnchants.containsKey (e) && 
						disallowedEnchants.get(e) <= bookStore.getStoredEnchantLevel(e) )
						disallowed = true;
					else { 
						//*DEBUG*/log.info ("Allowing enchant bcs disallowed=" + disallowedEnchants.get(e));
					}
				}
			}
			if (event.getWhoClicked().hasPermission ("enchlimiter.disallowed"))
				disallowed = false;
		
			if (disallowed || 
			    (limitMultiples && 
				 (book.getType() == Material.ENCHANTED_BOOK && tool.getType() == Material.ENCHANTED_BOOK) ) )
			{
				final HumanEntity human = event.getWhoClicked();
				final Player player = (Player)human; 
				final ItemStack slot0 = tool.clone(), slot1 = book;
				final boolean disallowedEntry = disallowed;
				
				if (!(human instanceof Player)) {
					log.warning (human + " clicked on anvil, not a Player");
					return;
				}
				else if (!disallowed && player.hasPermission ("enchlimiter.books")) {
					log.info (language.get (Bukkit.getConsoleSender(), "bookEnch", "Permitting {0} to enchant book+book", player.getName()));
					return;
				}
				
				class BookReturner extends BukkitRunnable {
					@Override
					public void run() {
						if ( !player.isOnline()) {
							log.info (language.get (player, "loggedoff", "{0} logged off before we could cancel anvil enchant", player.getName()));
							return;
						}
						if (player.getOpenInventory().getTopInventory().getType() != InventoryType.ANVIL) {
							//*DEBUG*/log.info (player.getName() + " closed inventory before enchant occurred");
							return;
						}
						AnvilInventory aInventory = (AnvilInventory)player.getOpenInventory().getTopInventory();
						InventoryView pInventory = player.getOpenInventory();

						// Make sure we should still do this, that anvil still ready
						boolean stop = false;
						if (aInventory.getItem (0) == null || !(aInventory.getItem (0).isSimilar (slot0))) {
							//*DEBUG*/log.info ("removed " + slot0.getType() + " before enchant occurred; instead found "+ aInventory.getItem (0));
							//*DEBUG*/log.info ("which is " + (!(aInventory.getItem (0).isSimilar (slot0)) ? "NOT ":"") + "similar to "+ slot0);
							stop = true;
						}
						if (aInventory.getItem (1) == null || !aInventory.getItem (1).isSimilar (slot1)) {
							//*DEBUG*/log.info ("removed book before enchant occurred");
							stop = true;
						}
						if (pInventory.getCursor() != null && pInventory.getCursor().getType() != Material.AIR) {
							//*DEBUG*/log.info ("Non empty cursor slot " + pInventory.getCursor().getType()  + " for enchant result");
							stop = true;
						}		
						if (stop) {
							// log.info (player.getName() + " already stopped book+book enchant");
							return;
						}
						// Execute cancel
						pInventory.setCursor (slot0); // return to user
						aInventory.clear(0); // remove  book from tool slot
						
						if (getConfig().getBoolean ("Message on cancel"))
							player.sendMessage (language.get (player, "cancelled", chatName + ": You don't have permission to do that"));

						if ( !disallowedEntry)
							log.info (language.get (Bukkit.getConsoleSender(), "attempted", "{0} just tried to do a book+book enchant", player.getName()));
						else
							log.info (language.get (Bukkit.getConsoleSender(), "attempted2", "{0} just tried to do a disallowed anvil enchant", player.getName()));
					}
				}
				if (player != null)
					(new BookReturner()).runTask(this);	
			}
		}
	}

	
	@EventHandler (ignoreCancelled = true)
	void itemMonitor (ItemSpawnEvent event) {
		if (fixOrTestItem (event.getEntity().getItemStack(), null)) {
			// log.info ("Modified enchants on spawned " + event.getEntity().getItemStack().getType());
		}
	}
	
	static Map <UUID, Long> lastMsg = new HashMap<UUID, Long>();
	
	@EventHandler (ignoreCancelled = true)
	void pickupMonitor (PlayerPickupItemEvent event) {
		Player player = event.getPlayer();
		ItemStack item = event.getItem().getItemStack();

		if (fixOrTestItem (item, player)) {
			if (getConfig().getBoolean ("Stop pickup")) {
				event.setCancelled (true);
				
				Long lastTime = lastMsg.get (player.getUniqueId());
				long currTime = System.currentTimeMillis();

				// not if in last 2 seconds
				if (lastTime == null || currTime - lastTime > 2000) {
					lastMsg.put (player.getUniqueId(), currTime);
					player.sendMessage (language.get (player, "disallowedPickup1", 
										chatName + ": can't pickup {0} with disallowed enchant(s)", 
										item.getType() ));
				}
			} 
		}
	}
	
	// returns true if item has enchants on disallowed list
	//  if "Stop pickup" is NOT set, will remove disallowed enchants
	boolean fixOrTestItem (ItemStack item, Player p) {
		boolean testOnly = getConfig().getBoolean ("Stop pickup", true);
		
		boolean limitMultiples = getConfig().getBoolean ("Limit Multiples", true) && 
			(p == null || ! p.hasPermission ("enchlimiter.multiple"));
		
		int enchants = 0;
		
		// Get List of disallowed enchants for that item and for ALL items
		Map<Enchantment, Integer> disallowedEnchants = getDisallowedEnchants (item.getType());
		disallowedEnchants.putAll (getDisallowedEnchants ("ALL"));
		//*DEBUG*/log.info ("disallowed on " + item.getType() + disallowedEnchants);
		
		if ( !item.getItemMeta().hasEnchants() || (!limitMultiples && disallowedEnchants.isEmpty()))
			return false;  // nothing to do; leave event alive for another plugin
		boolean modified = false;
						
		Map<Enchantment,Integer> toAdd = new HashMap<Enchantment, Integer>();
		toAdd.putAll (item.getEnchantments()); // to avoid modifying while iterating
			
		for (Enchantment ench : toAdd.keySet()) {
			int level = toAdd.get(ench);

			if ( !limitMultiples || enchants == 0) {
				if (disallowedEnchants.containsKey (ench) && level >= disallowedEnchants.get (ench) &&
					(p == null || ! p.hasPermission ("enchlimiter.disallowed")) ) 
				{
					if (testOnly) {
						return true;
					}
					else
						modified = true;
						
					item.removeEnchantment (ench);
					// Add back at lower level, if possible
					if (disallowedEnchants.get (ench) > 1)
						item.addEnchantment (ench, disallowedEnchants.get (ench) - 1);
					
					if (getConfig().getBoolean ("Message on disallowed", true) && p != null)
						p.sendMessage (language.get (p, "disallowed3", 
									   chatName + " removed disallowed {0}-{1} from {2}", ench.getName(), level, item.getType() ));
				}
				else {
					enchants++;
					//*DEBUG*/log.info ("Added " + ench + "s-" + level + " to " +item.getType());
				}
			} else {
				if (testOnly) {
					return true;
				}
				else
					modified = true;
					
				item.removeEnchantment (ench);					

				if (getConfig().getBoolean ("Message on limit", true) && p != null)
					p.sendMessage (language.get (p, "limited3", 
									chatName + " removed multiple {0}-{1} from {2}", ench.getName(), level, item.getType() ));
			}
		} 		
		return modified;
	}
	
	public static boolean isHelmet (Material type) {
		switch (type) {
			case LEATHER_HELMET:
			case IRON_HELMET:
			case GOLD_HELMET: 
			case DIAMOND_HELMET:
			case CHAINMAIL_HELMET:
				return true;
			default:
				return false;
		}
	}

	public static boolean isBoots (Material type) {
		switch (type) {
			case LEATHER_BOOTS:
			case IRON_BOOTS:
			case GOLD_BOOTS: 
			case DIAMOND_BOOTS:
			case CHAINMAIL_BOOTS:
				return true;
			default:
				return false;
		}
	}
	public static boolean isChestplate (Material type) {
		switch (type) {
			case LEATHER_CHESTPLATE:
			case IRON_CHESTPLATE:
			case GOLD_CHESTPLATE: 
			case DIAMOND_CHESTPLATE:
			case CHAINMAIL_CHESTPLATE:
				return true;
			default:
				return false;
		}
	}
	public static boolean isLeggings (Material type) {
		switch (type) {
			case LEATHER_LEGGINGS:
			case IRON_LEGGINGS:
			case GOLD_LEGGINGS: 
			case DIAMOND_LEGGINGS:
			case CHAINMAIL_LEGGINGS:
				return true;
			default:
				return false;
		}
	}
	// Should check BARDING (horse armor) but don't today
	public static boolean isArmor (Material type) {
		return isHelmet(type) || isChestplate (type) || isLeggings(type) || isBoots (type);
	}
	public static boolean isSword (Material type) {
		switch (type) {
			case IRON_SWORD:
			case STONE_SWORD:
			case GOLD_SWORD: 
			case DIAMOND_SWORD:
			case WOOD_SWORD:
				return true;
			default:
				return false;
		}
	}
	
	public static boolean isSpade (Material type) {
		switch (type) {
			case IRON_SPADE:
			case STONE_SPADE:
			case GOLD_SPADE: 
			case DIAMOND_SPADE:
			case WOOD_SPADE:
				return true;
			default: 
				return false;
		}
	}
	
	public static boolean isHoe (Material type) {
		switch (type) {	
			case IRON_HOE:
			case STONE_HOE:
			case GOLD_HOE: 
			case DIAMOND_HOE:
			case WOOD_HOE:
				return true;
			default: 
				return false;
		}
	}

	public static boolean isPick (Material type) {
		switch (type) {				
			case IRON_PICKAXE:
			case STONE_PICKAXE:
			case GOLD_PICKAXE: 
			case DIAMOND_PICKAXE:
			case WOOD_PICKAXE:
				return true;
			default: 
				return false;
		}
	}
	public static boolean isAxe (Material type) {
		switch (type) {				
			case IRON_AXE:
			case STONE_AXE:
			case GOLD_AXE: 
			case DIAMOND_AXE:
			case WOOD_AXE:
				return true;
			default: 
				return false;
		}
	}
	
		
	private void addNewLanguages () {
		final String pluginPath = "plugins"+ getDataFolder().separator + getDataFolder().getName() + getDataFolder().separator;

		try {  //iterate through added languages
			String classURL = this.getClass().getResource(this.getName()+".class").toString();
			String jarName = classURL.substring (classURL.lastIndexOf (':') + 1, classURL.indexOf ('!'));
			ZipInputStream jar = new ZipInputStream (new FileInputStream (jarName));
			if (jar != null) {
				ZipEntry e = jar.getNextEntry();
				while (e != null)   {
					String name = e.getName();
					if (name.startsWith ("languages/") && !new File (pluginPath + name).exists()) 
					{
						saveResource (name, false);
						log.info ("Adding language file: " + name);
					}
					e = jar.getNextEntry();
				}
			}
			else 
				log.warning ("Unable to open jar file");						
		} catch (Exception ex) {
			log.warning ("Unable to process language files: " + ex);		
		}	
	}		
	
	// Assumes correcting formed string
	private Map<Enchantment, Integer> getDisallowedEnchants (String matString) 
	{
		HashMap<Enchantment, Integer> results = new HashMap<Enchantment, Integer>();

		if ( !getConfig().isConfigurationSection ("Disallowed enchants." + matString))
			return results; // an empty list rather than null
		
		for (String enchantString : getConfig().getConfigurationSection ("Disallowed enchants." + matString).getKeys (false)) {
			int level = getConfig().getInt ("Disallowed enchants." + matString + "." + enchantString);
			if (level < 1) {
				log.warning ("Unsupported " + matString + "." + enchantString + " enchant level: " + level);
				continue;
			}
			Enchantment enchant;
			if (enchantString.equals ("ALL")) {
				for (Enchantment e : Enchantment.values()) {
					results.put (e, level);
				}
			}		
			else if ((enchant = Enchantment.getByName (enchantString)) == null)
				log.warning ("Unknown enchantment: " + enchantString+ ". Refer to http://bit.ly/HxVS58");
			else {
				results.put (enchant, level);
			}
		}	
		return results;
	}
	private Map<Enchantment, Integer> getDisallowedEnchants (Material m) 
	{
		HashMap<Enchantment, Integer> results = new HashMap<Enchantment, Integer>();
		String matString = m.toString();

		if (isSword (m))
			results.putAll (getDisallowedEnchants ("ALL_SWORDS"));
		else if (isSpade (m)) {
			results.putAll (getDisallowedEnchants ("ALL_SPADES"));
			results.putAll (getDisallowedEnchants ("ALL_SHOVELS"));			
		} else if (isHoe (m))
			results.putAll (getDisallowedEnchants ("ALL_HOES"));
		else if (isPick (m)) {
			results.putAll (getDisallowedEnchants ("ALL_PICKS"));
			results.putAll (getDisallowedEnchants ("ALL_PICKAXES"));
		} else if (isAxe (m))
			results.putAll (getDisallowedEnchants ("ALL_AXES"));	
		else if (isArmor (m)) {
			results.putAll (getDisallowedEnchants ("ALL_ARMOR"));	
			
			if (isHelmet (m))
				results.putAll (getDisallowedEnchants ("ALL_HELMETS"));	
			if (isBoots (m))
				results.putAll (getDisallowedEnchants ("ALL_BOOTS"));	
			if (isChestplate (m))
				results.putAll (getDisallowedEnchants ("ALL_CHESTPLATES"));	
			if (isLeggings (m)) {
				results.putAll (getDisallowedEnchants ("ALL_LEGGINGS"));	
				results.putAll (getDisallowedEnchants ("ALL_PANTS"));	
			}
		}

		if (getConfig().isConfigurationSection ("Disallowed enchants." + matString))
			results.putAll (getDisallowedEnchants (matString));			

		return results;
	}   
			
	public void onEnable()
	{
		log = this.getLogger();
		chatName = ChatColor.BLUE + this.getName() + ChatColor.RESET;
		language = new LanguageWrapper(this, "eng"); // English locale
		final String pluginPath = "plugins"+ getDataFolder().separator + getDataFolder().getName() + getDataFolder().separator;

		if ( !getDataFolder().exists() || !(new File (pluginPath + "config.yml").exists()) )
		{
			getConfig().options().copyDefaults(true);
			log.info ("No config found in " + pluginPath + "; writing defaults");
			saveDefaultConfig();
		} else {
			// Parse config for errors
			if (getConfig().isConfigurationSection ("Disallowed enchants")) 
			{
				for (String itemString : getConfig().getConfigurationSection ("Disallowed enchants").getKeys (false /*depth*/)) {
					Material m = Material.matchMaterial (itemString);
					if (itemString.equals("ALL") || itemString.equals ("ALL_ARMOR") || (itemString.startsWith ("ALL_") && itemString.endsWith ("S"))) {
						log.config ("Disallowed enchants." + itemString + ":" + getDisallowedEnchants (itemString));	
						continue;
					}
					
					if (m == null)
						log.warning ("Unknown item: " + itemString + ". Refer to http://bit.ly/EnchMat");
					else if (m.isBlock())
						log.warning ("Do not support blocks in 'Disallowed enchants': " + m);
					else {
						log.config ("Disallowed enchants." + itemString + ":" + getDisallowedEnchants (m));
						// getDisallowedEnchants (m); 	// just for error checking
					}
				}
			}
		}
		addNewLanguages();		
			
		getServer().getPluginManager().registerEvents ((Listener)this, this);
		log.info (language.get (Bukkit.getConsoleSender(), "enabled", "EnchantLimiter in force; by Filbert66"));
	}
	
	public void onDisable()
	{
	}
}