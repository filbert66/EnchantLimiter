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
 *  22 Jun 2014 : Added blocking of item repair beyond allowed level or item+book boost beyond allowed
 *              : Added fail-safe on picking up disallowed result; doesn't return XP currently.
 *  26 Jun 2014 : Added prevXP hashmap for anvilUndoer.
 *  07 Jul 2014 : Added better check on anvil MOVE_TO_OTHER_INVENTORY;
 *              : Added commands, only reload for now.
 *  12 Jul 2014 : Moved commands to separate class; fixed bugs with books.
 *              : Added 'Downgrade repairs', and 'Allow repairs'.
 *  16 Jul 2014 : craftMonitor: Added nullcheck on player.
 *  18 Jul 2014 : Fixed Shift-click on result; space in dirname; repair anvil on cancelled enchant; fixed couple NPEs.
 *              : Allow either BOOK or ENCHANTED_BOOK config entries to be equivalent.
 *              : Added 'Infinite anvils' feature.
 *  20 Jul 2014 : Fixed book+book > disallowed level in anvil.
 *  27 Jul 2014 : Added PlayerHeldEvent fixer; added Anvil & Table disallowed lists;
 *                Added Groups within disallowed lists; added "ALL_BARDING"
 *  06 Aug 2014 : Added blocking of armor equippage or fixing such when 'Fix held items' true, with BlockDispenseEvent.
 *                Added Apply_on_global_check, 'Downgrade in anvil'. 
 *                Fixed HOTBAR anvil duplication bugs.
 *
 * Bukkit BUG: Sometimes able to place items in Anvil & do restricted enchant; no ItemClickEvent!
 * Bukkit BUG: Sometimes able to hold an item with no itemHeldEvent!
 * 
 * TODO: 
 *   commands for modifying 'Disallowed enchants'
 */

package com.yahoo.phil_work.enchlimiter;

import com.yahoo.phil_work.LanguageWrapper;

import java.io.File;
import java.util.Collections;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.logging.Logger;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.io.FileInputStream;
import java.net.URLDecoder;
import java.util.zip.*;

import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Dispenser;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.HumanEntity;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.inventory.InventoryType.SlotType;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.material.MaterialData;
import org.bukkit.material.DirectionalContainer;
import org.bukkit.plugin.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.PluginLogger;
import org.bukkit.scheduler.BukkitRunnable;

import com.yahoo.phil_work.enchlimiter.EnchLimiterCommandExecutor;
import com.yahoo.phil_work.enchlimiter.EnchLimiterFixCommand;

public class EnchLimiter extends JavaPlugin implements Listener {
	public Logger log;
	public LanguageWrapper language;
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
		Map<Enchantment, Integer> disallowedEnchants = getDisallowedTableEnchants (item.getType(), player);
		log.config ("disallowed on " + item.getType() + disallowedEnchants);
		
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
						// avoid adding directly to item so we can avoid book details
						//   also allows another handler to cancel
						event.getEnchantsToAdd().put (ench, newLevel);
						int addedXP = (int)(0.5F + (XPperLevel * newLevel));
						event.setExpLevelCost (event.getExpLevelCost() + addedXP);
						returnedXP -= addedXP;
						enchants++;
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
	
	// Is set with ClickEvent, when placing in crafting slot of an anvil.
	private static Map <UUID, Integer> prevXP = new HashMap<UUID, Integer>();
	private static Map <UUID, Byte> prevAnvilData = new HashMap<UUID, Byte>();

	@EventHandler (ignoreCancelled = true) 
	void closeAnvilMonitor (InventoryCloseEvent event) {
		if (event.getInventory().getType()== InventoryType.ANVIL) {
			prevXP.remove (event.getPlayer().getUniqueId());
			prevAnvilData.remove (event.getPlayer().getUniqueId());
		}
	}
	
	//  Listen to PrepareItemCraftEvent and return one of the books 
	@EventHandler (ignoreCancelled = true)
	void craftMonitor (InventoryClickEvent event) {
		ItemStack book = null;
		ItemStack tool = null;
		Inventory inv = event.getInventory();
		boolean limitMultiples = getConfig().getBoolean ("Limit Multiples", true);
	
		//log.info ("InventoryClickEvent " +event.getAction()+" in type " + inv.getType() + " in  slot " + event.getRawSlot() + "(raw " + event.getSlot()+ ") slotType "+ event.getSlotType());
		final InventoryAction action = event.getAction();
		boolean isPlace = false;
		switch (action) {
			case PLACE_ALL:
			case PLACE_SOME:
			case PLACE_ONE:
			case SWAP_WITH_CURSOR:
			case MOVE_TO_OTHER_INVENTORY: // could be.. 
			case HOTBAR_SWAP:
			case HOTBAR_MOVE_AND_READD:
				isPlace = true;
				break;
			case NOTHING:
				return;
			default:
				break;
		}
		final HumanEntity human = event.getWhoClicked();
		final Player player = (Player)human; 
		if (!(human instanceof Player)) {
			log.warning (human + " clicked on anvil, not a Player");
			return;
		}
		else if (human == null) {
			log.warning ("Null player; cannot run");
			return;
		}
		
		// Check if equipping armor
		if ((event.getSlotType() == SlotType.ARMOR && isPlace) ||
		    (inv.getType() == InventoryType.CRAFTING && event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY && isArmor (event.getCurrentItem().getType())) )
		{
			// log.info ("Armor change " + action + " in slot " + event.getSlot() + " curr item " + event.getCurrentItem() + " with " + event.getCursor() + " on cursor");
			// log.info ("Found in raw slot " + event.getRawSlot() + ": " + event.getView ().getItem(event.getRawSlot()));
			PlayerInventory pinv = player.getInventory();
			ItemStack item = null;
			
			// Get new armor item
			switch (action) {
				case PLACE_ALL:
				case PLACE_SOME:
				case PLACE_ONE:
				case SWAP_WITH_CURSOR:
					item = event.getCursor();
					break;
				case MOVE_TO_OTHER_INVENTORY: 
					item = event.getCurrentItem();
					Material m = item.getType();
					if (isHelmet(m) && pinv.getHelmet() != null)
						return;
					if (isChestplate(m) && pinv.getChestplate() != null)
						return;
					if (isLeggings(m) && pinv.getLeggings() != null)
						return;
					if (isBoots(m) && pinv.getBoots() != null)
						return;
					// log.info ("Confirmed moving " + item.getType() + " to open armor slot");
					break;
				case HOTBAR_SWAP:
				case HOTBAR_MOVE_AND_READD:	
					item = pinv.getItem (event.getHotbarButton());
					break;
				default:
					log.warning ("unprocessed armor move action: " + action);
					break;
			}
			//log.info ("Found new armor: " + item);
			
			if (item != null)	{
				// BUG on PLACE, HOT: duplicates fixed item in armor slot and on cursor, so try RunLater
				final Player p = player;
				final ItemStack fixMe = item;
			
				class armorFixer extends BukkitRunnable {
					@Override
					public void run() {
						if ( !player.isOnline()) {
							log.info (language.get (player, "loggedoff2", "{0} logged off before we could fix armor", player.getName()));
							return;
						}
						PlayerInventory pInventory = p.getInventory();
						boolean found = false;
						
						// Find item
						ItemStack armor[] = pInventory.getArmorContents();
						for (int j = 0; j < armor.length; j++) {
							ItemStack i = armor[j];
							if (i.isSimilar (fixMe)) { // apparently cursor is quantity 0 in this case
								if (getConfig().getBoolean ("Fix held items")) {
									fixItem (i, p);
									found = true;
								}
							}
						}
						if (!found) 
							log.warning ("Cannot find armor to fix: " + fixMe);
					}
				}
				
				if (getConfig().getBoolean ("Fix held items") && !player.hasPermission ("enchlimiter.useillegal"))
					(new armorFixer()).runTask(this);		
				else if (fixOrTestItem (item, player, /*test only=*/ true)) {
					event.setCancelled (true);
					if (getConfig().getBoolean ("Message on cancel"))
						player.sendMessage (language.get (player, "cancelled", chatName + ": You don't have permission to do that"));
				}
			}
			return; // armor equip
		}
		
		/* Sometimes, Bukkit client places item in crafting without giving server event.
		 * Hence, add double-check to see if they just crafted an illegal item. If this works well, could remove 
		 *  other code, but that is more disturbing to players since it appeared to work. 
		 * Conclusion: note in static hashmap Xp when they place item in anvil (or open it and erase when they close it)
		 *   and then restore that if they've crafted something illegal. 
		 */
		if (inv.getType()== InventoryType.ANVIL && event.getSlotType() == SlotType.RESULT) {
			//log.info ("Looks like you " + action + " " + event.getSlotType() + " " + event.getCurrentItem() + " with " + event.getCursor() + " on cursor");
			ItemStack[] anvilContents = inv.getContents();
			final ItemStack slot0 = anvilContents[0];
			final ItemStack slot1 = anvilContents[1];
			ItemStack result = event.getCurrentItem();

			//log.info ("Crafted from 0: " + slot0);
			//log.info ("Crafted from 1: " + slot1);

			// New feature: infinite anvil
			if (getConfig().getBoolean ("Infinite anvils")) {
				Block anvilBlock = player.getTargetBlock(null, 6);
				if (anvilBlock != null && anvilBlock.getType() == Material.ANVIL) {	
					log.info ("Current anvil data: " + anvilBlock.getData());			
					anvilBlock.setData ((byte)(anvilBlock.getData () & 0x03));  // 0=undamaged; bits 0-1 are compass orientation on Block
				} else
					log.warning ("Cannot find anvil to repair");
			}			
				
			if (hasIllegalAnvilEnchant (result, player))
			{
				Integer ti = prevXP.get (player.getUniqueId());
				if (ti == null)
					log.warning ("Cannot restore XP; didn't record on place");
				final int playerXP = (ti != null? ti : player.getLevel());
				final PlayerInventory pinv = player.getInventory();
				final ItemStack crafted = result;
				final boolean doDowngrade = getConfig ().getBoolean ("Downgrade in anvil");
				// Add null check for item naming
				final boolean isRepair = (slot0 != null && slot1 != null && slot0.getType() == slot1.getType() && slot0.getDurability() > 0 && !slot1.getItemMeta().hasEnchants());
				
				if (!doDowngrade) { // going to stop the result taking and put ingredients back
				
					if (isRepair) { // check to see if we should allow it.
						if ( !getConfig().getBoolean ("Stop repairs") || player.hasPermission ("enchlimiter.repairs")) {
							log.info ("Allowing repair by " + player.getName() + " of " + slot0.getType());
							return;
						}				
					}
					
					// Repair anvil before it is destroyed and can't return item in runLater task
					Block anvilBlock = player.getTargetBlock(null, 6);
					if (anvilBlock != null && anvilBlock.getType() == Material.ANVIL) {
						Byte pData = prevAnvilData.get (player.getUniqueId());
						if (pData != null) {
							anvilBlock.setData (pData);
							// log.info ("restored anvil data to " + pData);
						} else
							log.warning ("Don't have stored anvil repair state to restore");
					} else
						log.warning ("Cannot find anvil to repair");
				}
				
				class anvilUndoer extends BukkitRunnable {
					@Override
					public void run() {
						if ( !player.isOnline()) {
							log.info (language.get (player, "loggedoff", "{0} logged off before we could cancel anvil enchant", player.getName()));
							return;
						}
						if (player.getOpenInventory().getTopInventory().getType() != InventoryType.ANVIL) {
							log.warning (language.get (player, "theft", "{0} closed inventory or anvil died and got an illegal item: {1}", player.getName(), crafted));
							return;
						}
						AnvilInventory aInventory = (AnvilInventory)player.getOpenInventory().getTopInventory();
						InventoryView pInventory = player.getOpenInventory();

						// Execute cancel
						if (action == InventoryAction.MOVE_TO_OTHER_INVENTORY || 
							action == InventoryAction.HOTBAR_SWAP || 
							action == InventoryAction.HOTBAR_MOVE_AND_READD) 
						{
							// When shift-click or number, item is not on cursor but already in inventory/hotbar. 
							boolean found = false;
							// log.info ("Looking in pinv for: " + crafted + " in inv[" + pinv.getSize() + "]");
							for (int i = 0; i < pinv.getSize(); i++) {
								// Use isSimilar because result is quantity zero (0)!
								if (crafted.isSimilar (pinv.getItem (i))) {
									if (!doDowngrade)
										pinv.clear (i);
									else { // downgrade it
										ItemStack fixer = pinv.getItem(i);
										fixAnvilItem (fixer, player);
										pinv.setItem (i, fixer);										
										return;  // don't return ingredients or restore levels
									}
									//log.info ("Found and removed illegal item in raw slot " + i);		
									found = true;
									break;
								} else if (pinv.getItem(i) != null) {
								//	log.info (i + ": inv " + pinv.getItem(i) + " is not result ");
								}
							}
							if (!found)
								log.warning ("could not find illegal result in inventory on shift-click by " + player.getName());								
						} else if (doDowngrade) {
							ItemStack fixer = pInventory.getCursor() ;
							fixAnvilItem (fixer, player);
							pInventory.setCursor (fixer);	
							return;  // don't return ingredients or restore levels
						} else
							pInventory.setCursor (null); // take away illegal item in hand
						
						aInventory.setItem(0, slot0); // return craft ingredient 1
						aInventory.setItem(1, slot1); // return craft ingredient 2
						if (getConfig().getBoolean ("Restore levels"))
							player.setLevel (playerXP);
												
						if (getConfig().getBoolean ("Message on cancel"))
							player.sendMessage (language.get (player, "cancelled", chatName + ": You don't have permission to do that"));

						log.info (language.get (Bukkit.getConsoleSender(), "attempted3", "{0} almost took result of a disallowed anvil enchant", player.getName()));
					}
				}
				if (player != null)
					(new anvilUndoer()).runTask(this);				
			}
		}
		else if (inv.getType()== InventoryType.ANVIL && event.getSlotType() == SlotType.CRAFTING) {
			ItemStack[] anvilContents = inv.getContents();
			ItemStack slot0 = anvilContents[0];
			ItemStack slot1 = anvilContents[1];
			
			/*** DEBUG 
			Block anvilBlock = player.getTargetBlock(null, 6);
			if (anvilBlock != null && anvilBlock.getType() == Material.ANVIL) {	
				log.info ("Current anvil data: " + anvilBlock.getData());			
			} 
			***/

			if (isPlace) {
				// Remember for later, in case we need it. 
				Integer curXP = player.getLevel();	// need to get/set levels, not XP (progress to next)
				prevXP.put (player.getUniqueId(), curXP);
				Block anvil = player.getTargetBlock (null,6);
				if (anvil != null && anvil.getType() == Material.ANVIL)
					prevAnvilData.put (player.getUniqueId(), anvil.getData());
				//*DEBUG*/ log.info ("saved XP at " + curXP);
				
				// log.info ("Placed a " + event.getCursor() + " in slot " + event.getRawSlot());
				//log.info ("currentItem: " +  event.getCurrentItem());
				if (event.getRawSlot() == 1) {
					//log.info ("reset slot1 from " + slot1 + " to " + event.getCursor());
					slot1 = event.getCursor();
				} else if (event.getRawSlot() == 0) {
					//log.info ("reset slot0 from " + slot0 + " to " + event.getCursor());
					slot0 = event.getCursor();
				}
			}	
			// 1 is right slot of anvil
			if (slot1 != null /*&& slot1.getType() == Material.ENCHANTED_BOOK */)
				book = slot1;
			// 0 is left slot of Anvil
			if (slot0 != null /* && slot0.getType() == Material.ENCHANTED_BOOK */)
				tool = slot0;
			// log.info ("Found book: " + book + "; tool: " + tool);
		} 
		else if (inv.getType()== InventoryType.ANVIL && action == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
			ItemStack[] anvilContents = inv.getContents();
			ItemStack slot0 = anvilContents[0]; tool = slot0;
			ItemStack slot1 = anvilContents[1]; book = slot1;
			
			if (event.getSlotType() == SlotType.CONTAINER || event.getSlotType() == SlotType.QUICKBAR)
			/** DEBUG
			log.info ("Potential swap to Anvil. Currently in slot0: " + slot0);
			log.info ("slot1: " + slot1);
			log.info ("clicked: " + event.getCurrentItem());
			**/

			/** Find if one slot is open, then assume current item will become it. Start from slot 0 
			 *   Know that getCurrentItem() is not null bcs it is MOVE_TO_OTHER_INV action
			 */
			if (slot0 == null || slot0.getType() == Material.AIR)
				tool = event.getCurrentItem();
			else if (slot1 == null || slot1.getType() == Material.AIR)
				book = event.getCurrentItem();		
			//log.info ("Move check with tool: " + tool + " & book: " + book);
		}
		if (book != null && tool != null && isPlace)
		{ 
			Map<Enchantment, Integer> disallowedEnchants = getDisallowedAnvilEnchants (tool.getType(), player);
			boolean disallowed = false;
			boolean bookPlusBook = (book.getType() == Material.ENCHANTED_BOOK && tool.getType() == Material.ENCHANTED_BOOK);
			ArrayList<Enchantment> violators = new ArrayList<Enchantment>();
				
			ItemMeta meta = book.getItemMeta();
			if ( !(meta instanceof EnchantmentStorageMeta)) {
				if (tool.getType() == book.getType() && tool.getDurability() == 0 && book.getDurability() == 0) {
					// log.info ("Enchant combo attempt of " + tool.getType());
					for (Enchantment e: book.getEnchantments().keySet()) {
						if (disallowedEnchants.containsKey (e)) {
							if (disallowedEnchants.get(e) <= book.getEnchantmentLevel(e) ) {
							 	disallowed = true; // second item alone too high
							 	violators.add (e);
							} else if (tool.getEnchantmentLevel (e) >= disallowedEnchants.get(e) - 1) {
								disallowed = true;	// trying to boost enchant of existing item at limit
							 	violators.add (e);
							}
						}
					}
				}	
			} else {
				EnchantmentStorageMeta bookStore = (EnchantmentStorageMeta)meta;
				for (Enchantment e: bookStore.getStoredEnchants().keySet()) {
					//*DEBUG*/log.info ("testing for " + e + "-" + bookStore.getStoredEnchantLevel(e));
					if (disallowedEnchants.containsKey (e)) {
						if (disallowedEnchants.get(e) <= bookStore.getStoredEnchantLevel(e) ) {
							disallowed = true; // book too high
							violators.add (e);
						}
						else if (bookPlusBook) {
							EnchantmentStorageMeta book1 = (EnchantmentStorageMeta)tool.getItemMeta();	
							if (book1.getStoredEnchantLevel (e) >= disallowedEnchants.get(e) - 1) {
								disallowed = true;	// trying to boost one book with another
							 	violators.add (e);
							}
						} else if (tool.getEnchantmentLevel (e) >= disallowedEnchants.get(e) - 1) {
							disallowed = true;	// trying to boost enchant of existing item at limit
							violators.add (e);
						}
					}
				}
			}
			//*DEBUG*/log.info ("Found violations of " + violators);
			if (event.getWhoClicked().hasPermission ("enchlimiter.disallowed"))
				disallowed = false;
		
			if (disallowed || (limitMultiples && bookPlusBook))
			{
				final ItemStack slot0 = tool.clone(), slot1 = book;
				final boolean disallowedEntry = disallowed;
				
				if (!disallowed && player.hasPermission ("enchlimiter.books")) {
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
				if (player != null) {
					if (getConfig ().getBoolean ("Downgrade in anvil")) {
						for (Enchantment e : violators) {
							int level = disallowedEnchants.get (e);
							if (level == 1)								
								player.sendMessage (language.get (player, "removeWarn", chatName + ": {0} will be removed", e.getName()));
							else 
								player.sendMessage (language.get (player, "reduceWarn", chatName + ": {0} will be reduced to {1}", e.getName(), level));
						}
						player.sendMessage (language.get (player, "anvilWarn", chatName + ": Some enchants will be limited/removed; see above"));
					} else					 
						(new BookReturner()).runTask(this);	
				}
			}
		}
	}

	
	@EventHandler (ignoreCancelled = true)
	void itemMonitor (ItemSpawnEvent event) {
		if (getConfig().getBoolean ("Fix spawned items") && fixItem (event.getEntity().getItemStack(), null)) {
			// log.info ("Modified enchants on spawned " + event.getEntity().getItemStack().getType());
		}
	}
	
	static Map <UUID, Long> lastMsg = new HashMap<UUID, Long>();
	
	@EventHandler (ignoreCancelled = true)
	void pickupMonitor (PlayerPickupItemEvent event) {
		Player player = event.getPlayer();
		ItemStack item = event.getItem().getItemStack();

		if (fixOrTestItem (item, player, getConfig().getBoolean ("Stop pickup"))) {
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
	
	@EventHandler (ignoreCancelled = true)
	void heldMonitor (PlayerItemHeldEvent event) {
		final Player p = event.getPlayer();
		ItemStack item = p.getInventory().getItem (event.getNewSlot());

		if (item == null)
			return;
		else if (getConfig().getBoolean ("Fix held items"))		
			fixItem (item, p);
		else if (! p.hasPermission ("enchlimiter.useillegal"))
		{ 
			// stop hold
			/* Bukkit BUG: sometimes don't get the hold event (ex. when player spams the hotbutton key). 
			 *  Effect is that player can hold/use/shift-click an illegal item if they try hard enough.
			 *  Recommend "Fix held items"= true, since item is fixed and multiple attempts don't matter.
			 */
		    if (fixOrTestItem (item, p, /*test only=*/ true)) {
				event.setCancelled (true);
				if (getConfig().getBoolean ("Message on cancel hold"))
					p.sendMessage (language.get (p, "cancelledHold", chatName + ": You don't have permission to use that"));
			}
		}			
	}
	
	/** Written to handle automatic equipping of armor by a player 1 block away from dispenser. but could
	     be extended to include fixing all such acquired items. But that is not consistent with "Fix held items"
	     context which allows player to acquire them, just not use them.
	    On auto-equip of armor, supplied vector is zero.
    **/
	@EventHandler (ignoreCancelled = true)
	void transferMonitor (BlockDispenseEvent event) {
		ItemStack item = event.getItem();
		if (!isArmor (item.getType()) || event.getVelocity().length() != 0D)
			return; // nothing to do.
		
		Block b = event.getBlock();
		Location loc = b.getLocation();
		Player p = null;
		
		// Find nearby Player
		if ( !(b.getType() == Material.DISPENSER || b.getType() == Material.DROPPER)) {
			log.warning ("BlockDispenseEvent from a non-dispenser " + b);
			return;
		}
		// Bukkit bug: DirectionalContainer does not support UP/DOWN
		BlockFace facing = new DirectionalContainer (b.getType(), (byte)(b.getData() & 0x7)).getFacing();
		if ((b.getData() & 0x7) == 0)
			facing = BlockFace.DOWN;
		else if ((b.getData() & 0x7) == 1)
			facing = BlockFace.UP;
		loc.add (facing.getModX(), facing.getModY(), facing.getModZ());
		// log.info ("Checking " + facing + " (data " + b.getData() + ") location " + loc);
		for (Entity e: loc.getChunk().getEntities()) {
			if (loc.equals (e.getLocation().getBlock().getLocation())) { // within range
				// log.info ("Found adjacent entity " + e.getType());
				if (e instanceof Player) {
					p = (Player)e;
					break;
				}
			}
		}
		if (p != null) {
			final Player player = p;
			final ItemStack fixMe = item;
			
			class armorFixer extends BukkitRunnable {
				@Override
				public void run() {
					if ( !player.isOnline()) {
						log.info (language.get (player, "loggedoff2", "{0} logged off before we could fix armor", player.getName()));
						return;
					}
					PlayerInventory pInventory = player.getInventory();

					// Find item
					ItemStack armor[] = pInventory.getArmorContents();
					for (int j = 0; j < armor.length; j++) {
						ItemStack i = armor[j];
						if (i.equals (fixMe)) {
							if (getConfig().getBoolean ("Fix held items"))
								fixItem (i, player);
							else if (! player.hasPermission ("enchlimiter.useillegal")) { //stop equipage
								if ( !pInventory.addItem (i).isEmpty()) {
									// unable to store item									
									player.getWorld().dropItem (player.getLocation(), i);
									log.info (player.getName() + " inventory full; dropping illegal dispensed " + i.getType());
								}				
								armor [j] = null;
								pInventory.setArmorContents (armor);
								// no need to message since user may not have been expecting it.
							}	
						}
					}
				}
			}
			(new armorFixer()).runTask(this);		
		}
	}
	
	// returns true if has enchants on global or anvil disallowed list or multiple when not allowed
	boolean hasIllegalAnvilEnchant (final ItemStack item, final Player p) {
		boolean limitMultiples = getConfig().getBoolean ("Limit Multiples", true) && 
			(p == null || ! p.hasPermission ("enchlimiter.multiple"));
		if (item == null || !item.hasItemMeta())
			return false;
		
		// Get List of disallowed enchants for that item 
		Map<Enchantment, Integer> disallowedEnchants = getDisallowedAnvilEnchants (item.getType(), p);

		ItemMeta meta = item.getItemMeta();
		if ( meta instanceof EnchantmentStorageMeta) {
			if ( !((EnchantmentStorageMeta)meta).hasStoredEnchants())
				return false; // nothing to do
			else
			  	return fixOrTestBook (disallowedEnchants, item, p, true); // testonly
		} 		
		
		if ( !item.getItemMeta().hasEnchants() || (!limitMultiples && disallowedEnchants.isEmpty()))
			return false;  // nothing to do; leave event alive for another plugin
			
		// Check for multiples
		if (limitMultiples && item.getEnchantments().size() > 1)
			return true;
		else if (p != null && p.hasPermission ("enchlimiter.disallowed"))
			return false;	// no need to check
			
		// Check disallowed enchants
		for (Enchantment ench : item.getEnchantments().keySet()) {
			int level = item.getEnchantments().get(ench);

			if (disallowedEnchants.containsKey (ench) && level >= disallowedEnchants.get (ench) &&
				(p == null || ! p.hasPermission ("enchlimiter.disallowed")) ) 
			{
				return true;
			}						
		} 		
		return false;
	}	
	
	@SuppressWarnings("deprecation")
	private void updateInventory (Player p) {
		try {
			if (Player.class.getMethod ("updateInventory") != null)
				p.updateInventory();
		} catch (NoSuchMethodException ex)  {}
	}
	
	// returns true if item has enchants on disallowed list or multiple when not allowed
	boolean fixItem (ItemStack item, Player p) {
		return fixOrTestItem (item, p, false);
	}
	boolean fixAnvilItem (ItemStack item, Player p) {
		return fixOrTestAnvilItem (item, p, false);
	}
	boolean fixOrTestItem (ItemStack item, Player p, boolean testOnly) {	
		if (item == null) return false;
		
		Map<Enchantment, Integer> disallowedEnchants = getDisallowedEnchants (item.getType(), p);
		if (getConfig().getBoolean ("Apply_on_global_check.anvil"))
		{
			Map<Enchantment, Integer> disallowedNewEnchants = getDisallowedAnvilEnchants (item.getType(), p);
			
			for (Enchantment e : disallowedNewEnchants.keySet())
				if ( !disallowedEnchants.containsKey (e))
					disallowedEnchants.put (e, disallowedNewEnchants.get(e));
				else {
					if (getConfig().getBoolean ("Apply_on_global_check.restrictive")) {
						// apply highest level
						if (disallowedNewEnchants.get (e) > disallowedEnchants.get (e))
							disallowedEnchants.put (e, disallowedNewEnchants.get(e));
					} else { // set lowest level
						if (disallowedNewEnchants.get (e) < disallowedEnchants.get (e))
							disallowedEnchants.put (e, disallowedNewEnchants.get(e));
					}					
				}
		}
		if (getConfig().getBoolean ("Apply_on_global_check.table"))
		{
			Map<Enchantment, Integer> disallowedNewEnchants = getDisallowedTableEnchants (item.getType(), p);
			
			for (Enchantment e : disallowedNewEnchants.keySet())
				if ( !disallowedEnchants.containsKey (e))
					disallowedEnchants.put (e, disallowedNewEnchants.get(e));
				else {
					if (getConfig().getBoolean ("Apply_on_global_check.restrictive")) {
						// apply highest level
						if (disallowedNewEnchants.get (e) > disallowedEnchants.get (e))
							disallowedEnchants.put (e, disallowedNewEnchants.get(e));
					} else { // set lowest level
						if (disallowedNewEnchants.get (e) < disallowedEnchants.get (e))
							disallowedEnchants.put (e, disallowedNewEnchants.get(e));
					}					
				}
		}
		
		return fixOrTestItem (disallowedEnchants, item, p, testOnly);
	}
	boolean fixOrTestAnvilItem (ItemStack item, Player p, boolean testOnly) {	
		if (item == null) return false;
		Map<Enchantment, Integer> disallowedEnchants = getDisallowedAnvilEnchants (item.getType(), p);
		return fixOrTestItem (disallowedEnchants, item, p, testOnly);
	}
	// NOTE: PlayerPickup calls this: global only
	//  if testOnly is false, will remove disallowed enchants
	boolean fixOrTestItem (Map<Enchantment, Integer> disallowedEnchants, ItemStack item, Player p, boolean testOnly) {	
		boolean limitMultiples = getConfig().getBoolean ("Limit Multiples", true) && 
			(p == null || ! p.hasPermission ("enchlimiter.multiple"));
		
		int enchants = 0;
		
		ItemMeta meta = item.getItemMeta();
		if ( meta instanceof EnchantmentStorageMeta) {
			if ( !((EnchantmentStorageMeta)meta).hasStoredEnchants())
				return false; // nothing to do
			else
			  	return fixOrTestBook (disallowedEnchants, item, p, testOnly);
		} 
		
		if (meta == null || !meta.hasEnchants() || (!limitMultiples && disallowedEnchants.isEmpty()))
			return false;  // nothing to do; leave event alive for another plugin
		boolean modified = false;
						
		Map<Enchantment,Integer> onItem = new HashMap<Enchantment, Integer>();
		onItem.putAll (item.getEnchantments()); // to avoid modifying while iterating
			
		for (Enchantment ench : onItem.keySet()) {
			int level = onItem.get(ench);

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
	// returns true if book has stored enchants on disallowed list or multiple when not allowed
	//  if "Stop pickup" is NOT set, will remove disallowed enchants
	//  expects only to be called with an ENCHANTED_BOOK
	boolean fixOrTestBook (Map<Enchantment, Integer> disallowedEnchants, ItemStack item, Player p, boolean testOnly) {
		if (item.getType() != Material.ENCHANTED_BOOK) {
			log.warning ("fixOrTestBook called with " + item.getType());
			return false;
		}		
		boolean limitMultiples = getConfig().getBoolean ("Limit Multiples", true) && 
			(p == null || ! p.hasPermission ("enchlimiter.multiple"));
		
		int enchants = 0;
		EnchantmentStorageMeta meta = (EnchantmentStorageMeta)item.getItemMeta();
		
		if ( !meta.hasStoredEnchants() || (!limitMultiples && disallowedEnchants.isEmpty()))
			return false;  // nothing to do; leave event alive for another plugin
		boolean modified = false;
						
		Map<Enchantment,Integer> onItem = new HashMap<Enchantment, Integer>();
		onItem.putAll (meta.getStoredEnchants()); // to avoid modifying while iterating
			
		for (Enchantment ench : onItem.keySet()) {
			int level = onItem.get(ench);

			if ( !limitMultiples || enchants == 0) {
				if (disallowedEnchants.containsKey (ench) && level >= disallowedEnchants.get (ench) &&
					(p == null || ! p.hasPermission ("enchlimiter.disallowed")) ) 
				{
					if (testOnly) {
						return true;
					}
					else
						modified = true;
						
					meta.removeStoredEnchant (ench);
					// Add back at lower level, if possible
					if (disallowedEnchants.get (ench) > 1)
						meta.addStoredEnchant (ench, disallowedEnchants.get (ench) - 1, true);
					
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
					
				meta.removeStoredEnchant (ench);					

				if (getConfig().getBoolean ("Message on limit", true) && p != null)
					p.sendMessage (language.get (p, "limited3", 
									chatName + " removed multiple {0}-{1} from {2}", ench.getName(), level, item.getType() ));
			}
		} 
		if (modified)
			item.setItemMeta (meta);
					
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
	public static boolean isArmor (Material type) {
		return isHelmet(type) || isChestplate (type) || isLeggings(type) || isBoots (type) || isBarding(type);
	}
	public static boolean isBarding (Material type) {
		switch (type) {
			case IRON_BARDING:
			case GOLD_BARDING: 
			case DIAMOND_BARDING:
				return true;
			default:
				return false;
		}
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
	public void addNewLanguages (Plugin plugin) {
		final String Slash = plugin.getDataFolder().separator;
		final String pluginPath = "plugins"+ Slash + getDataFolder().getName() + Slash;
		Logger log = plugin.getLogger();

		try {  //iterate through added languages
			String classURL = plugin.getClass().getResource(plugin.getName()+".class").toString();
			//log.info ("classURL: " + classURL);
			ZipInputStream jar;
			String jarName = classURL.substring (classURL.lastIndexOf (':') + 1, classURL.indexOf ('!'));
			try {
				File f = new File (jarName.replaceAll("%20", " "));
				//log.info ("File " + f + " exists: " + f.exists());
				jar = new ZipInputStream (new FileInputStream (f));
			} catch (java.io.FileNotFoundException ex) {
				log.warning ("Cannot find jar file: '" + jarName + "'");						
				return;
			}
			if (jar != null) {
				ZipEntry e = jar.getNextEntry();
				while (e != null)   {
					String name = e.getName();
					if (name.startsWith ("languages" + Slash) && !new File (pluginPath + name).exists()) 
					{
						plugin.saveResource (name, false);
						log.info ("Adding language file: " + name);
					}
					e = jar.getNextEntry();
				}
			}
			else 
				log.warning ("Unable to open jar file: '" + jarName + "'");						
		} catch (Exception ex) {
			log.warning ("Unable to process language files: " + ex);		
		}	
	}				
	/*
	 * Following getDisallowedEnchants (1) returns only for the global "Disallowed enchants"
	 *   to find disallowed in an anvil, use getDisallowedAnvilEnchants()
	 *   to find disallowed in an enchantment table, use getDisallowedTableEnchants()
	 *     which each return the global *plus* their specific items.
	 */ 
	// Assumes correcting formed string, either a material or a permitted "ALL_"
	private Map<Enchantment, Integer> getDisallowedEnchants (String matString) 
	{
		return getDisallowedEnchants (getConfig().getConfigurationSection ("Disallowed enchants"), matString);
	}
	private Map<Enchantment, Integer> getDisallowedAnvilEnchants (String matString) 
	{
		HashMap<Enchantment, Integer> results = new HashMap<Enchantment, Integer>();
		results.putAll (getDisallowedEnchants (getConfig().getConfigurationSection ("Disallowed enchants"), matString));
		results.putAll (getDisallowedEnchants (getConfig().getConfigurationSection ("Disallowed anvil enchants"), matString));
		return results;
	}
	private Map<Enchantment, Integer> getDisallowedTableEnchants (String matString) 
	{
		HashMap<Enchantment, Integer> results = new HashMap<Enchantment, Integer>();
		results.putAll (getDisallowedEnchants (getConfig().getConfigurationSection ("Disallowed enchants"), matString));
		results.putAll (getDisallowedEnchants (getConfig().getConfigurationSection ("Disallowed table enchants"), matString));
		return results;
	}	
	private Map<Enchantment, Integer> getDisallowedEnchants (ConfigurationSection cs, String matString) 
	{
		HashMap<Enchantment, Integer> results = new HashMap<Enchantment, Integer>();

		if (cs == null || !cs.isConfigurationSection (matString))
			return results; // an empty list rather than null
		
		for (String enchantString : cs.getConfigurationSection (matString).getKeys (false)) {
			int level = cs.getInt (matString + "." + enchantString);
			if (level < 1) {
				log.warning (cs.getCurrentPath()+"."+matString + "." + enchantString + ": " + level + "<- Unsupported enchant level");
				continue;
			}
			Enchantment enchant;
			if (enchantString.equals ("ALL")) {
				for (Enchantment e : Enchantment.values()) {
					results.put (e, level);
				}
			}		
			else if ((enchant = Enchantment.getByName (enchantString)) == null)
				log.warning (cs.getCurrentPath()+"." +matString + ": Unknown enchantment '" + enchantString+ "'. Refer to http://bit.ly/HxVS58");
			else {
				results.put (enchant, level);
			}
		}	
		return results;
	}
	private Map<Enchantment, Integer> getDisallowedEnchants (Material m, Player p) 
	{
		return getDisallowedEnchants (getConfig().getConfigurationSection ("Disallowed enchants"), m, p);
	}
	private Map<Enchantment, Integer> getDisallowedAnvilEnchants (Material m, Player p) 
	{
		HashMap<Enchantment, Integer> results = new HashMap<Enchantment, Integer>();
		results.putAll (getDisallowedEnchants (getConfig().getConfigurationSection ("Disallowed enchants"), m, p));
		results.putAll (getDisallowedEnchants (getConfig().getConfigurationSection ("Disallowed anvil enchants"), m, p));
		return results;
	}
	private Map<Enchantment, Integer> getDisallowedTableEnchants (Material m, Player p) 
	{
		HashMap<Enchantment, Integer> results = new HashMap<Enchantment, Integer>();
		results.putAll (getDisallowedEnchants (getConfig().getConfigurationSection ("Disallowed enchants"), m, p));
		results.putAll (getDisallowedEnchants (getConfig().getConfigurationSection ("Disallowed table enchants"), m, p));
		return results;
	}		 
	// Calls previous by converting Material to string, but also adds special "ALL" cases
	private Map<Enchantment, Integer> getDisallowedEnchants (ConfigurationSection cs, Material m, Player p) 
	{
		HashMap<Enchantment, Integer> results = new HashMap<Enchantment, Integer>();
		String matString = m.toString();
		if (cs == null)
			return results; // empty, non-null

		results.putAll (getDisallowedEnchants (cs, "ALL"));
		
		// BOOK should apply to Enchanted_BOOK, and v.v.
		if (m == Material.BOOK)
			results.putAll (getDisallowedEnchants (cs, "ENCHANTED_BOOK"));
		else if (m == Material.ENCHANTED_BOOK)
			results.putAll (getDisallowedEnchants (cs, "BOOK"));
			
		else if (isSword (m))
			results.putAll (getDisallowedEnchants (cs, "ALL_SWORDS"));
		else if (isSpade (m)) {
			results.putAll (getDisallowedEnchants (cs, "ALL_SPADES"));
			results.putAll (getDisallowedEnchants (cs, "ALL_SHOVELS"));			
		} else if (isHoe (m))
			results.putAll (getDisallowedEnchants (cs, "ALL_HOES"));
		else if (isPick (m)) {
			results.putAll (getDisallowedEnchants (cs, "ALL_PICKS"));
			results.putAll (getDisallowedEnchants (cs, "ALL_PICKAXES"));
		} else if (isAxe (m))
			results.putAll (getDisallowedEnchants (cs, "ALL_AXES"));	
		else if (isArmor (m)) {
			results.putAll (getDisallowedEnchants (cs, "ALL_ARMOR"));	
			
			if (isHelmet (m))
				results.putAll (getDisallowedEnchants (cs, "ALL_HELMETS"));	
			else if (isBoots (m))
				results.putAll (getDisallowedEnchants (cs, "ALL_BOOTS"));	
			else if (isChestplate (m))
				results.putAll (getDisallowedEnchants (cs, "ALL_CHESTPLATES"));	
			else if (isLeggings (m)) {
				results.putAll (getDisallowedEnchants (cs, "ALL_LEGGINGS"));	
				results.putAll (getDisallowedEnchants (cs, "ALL_PANTS"));	
			} else if (isBarding (m)) 
				results.putAll (getDisallowedEnchants (cs, "ALL_BARDING"));					
		}

		results.putAll (getDisallowedEnchants (cs, matString));			

		// Determine what groups to read. Overrides non-grouped disallows including specifics
		HashSet<String> sectionGroups = Groups.get (cs.getCurrentPath());
		if (sectionGroups != null) {
			for (String groupName : sectionGroups.toArray(new String[0])) {
				if (p == null || !p.hasPermission ("enchlimiter." + groupName)) {
					log.fine ("No permission " + groupName + " loading section");
					results.putAll (getDisallowedEnchants (cs.getConfigurationSection (groupName), m, p));	
				}
			}
		}

		return results;
	}  	
	
	static private HashSet<String> Global_Groups = null;
	static private HashSet<String> Table_Groups = null;
	static private HashSet<String> Anvil_Groups = null;
	static private HashMap <String,HashSet<String> > Groups = new HashMap<String,HashSet<String> >();
	void checkConfig() {
		if (getConfig().isConfigurationSection ("Disallowed enchants")) {
			Global_Groups = new HashSet<String>();	// old data lost and rely on Java garbage collector
			Groups.put ("Disallowed enchants", Global_Groups);
			checkConfig (getConfig().getConfigurationSection ("Disallowed enchants"));
		}
		if (getConfig().isConfigurationSection ("Disallowed anvil enchants")) {
			Anvil_Groups = new HashSet<String>();
			Groups.put ("Disallowed anvil enchants", Anvil_Groups);
			checkConfig (getConfig().getConfigurationSection ("Disallowed anvil enchants"));
		}
		if (getConfig().isConfigurationSection ("Disallowed table enchants")) {
			Table_Groups = new HashSet<String>();
			Groups.put ("Disallowed table enchants", Table_Groups);
			checkConfig (getConfig().getConfigurationSection ("Disallowed table enchants"));
		}
	}		
	void checkConfig (ConfigurationSection cs) {
		//log.info ("Checking section " + cs.getCurrentPath());
		for (String itemString : cs.getKeys (false /*depth*/)) {
			Material m = Material.matchMaterial (itemString);
			if (itemString.equals("ALL") || itemString.equals ("ALL_ARMOR") || (itemString.startsWith ("ALL_") && itemString.endsWith ("S"))) {
				log.config (cs.getCurrentPath() +"."+ itemString + ":" + getDisallowedEnchants (cs, itemString));	
				continue;
			} else if (m == null && itemString.startsWith ("Group_")) {
				// remember for later searching
				HashSet<String> sectionGroups = Groups.get (cs.getCurrentPath());
				if (sectionGroups != null) {
					if (sectionGroups.add (itemString) == false) {	// remember
						log.warning (cs.getCurrentPath() + " contains duplicate " + itemString);							
						continue;  // doesn't trigger bcs getSection takes the last one
					}
					else 
						log.info ("Found " + cs.getCurrentPath() + "." + itemString);
				} else {
					log.warning ("Group_ names not allowed within " + cs.getCurrentPath());
					continue;
				}
				// recursive check of subgroup
				checkConfig (cs.getConfigurationSection (itemString));
				continue;
			}
			
			if (m == null)
				log.warning (cs.getCurrentPath() + ":Unknown item: " + itemString + ". Refer to http://bit.ly/EnchMat");
			else if (m.isBlock())
				log.warning (cs.getCurrentPath() + ":Do not support blocks in disallowed enchants: " + m);
			else {
				log.config (cs.getCurrentPath() +"." + itemString + ":" + getDisallowedEnchants (cs, m, null));
			}
		}
	}		

	public void onEnable()
	{
		log = this.getLogger();
		log.setLevel (java.util.logging.Level.CONFIG); // not working!

		chatName = ChatColor.BLUE + this.getName() + ChatColor.RESET;
		language = new LanguageWrapper(this, "eng"); // English locale
		final String pluginPath = "plugins"+ getDataFolder().separator + getDataFolder().getName() + getDataFolder().separator;

		if ( !getDataFolder().exists() || !(new File (pluginPath + "config.yml").exists()) )
		{
			getConfig().options().copyDefaults(true);
			log.info ("No config found in " + pluginPath + "; writing defaults");
			saveDefaultConfig();
		} 
		// Parse config for errors
		checkConfig();
		
		//language.addNewLanguages();	// do after creating plugin direoctory		
		addNewLanguages (this);
			
		getServer().getPluginManager().registerEvents ((Listener)this, this);
		getCommand("el").setExecutor(new EnchLimiterCommandExecutor(this));
		getCommand("elfix").setExecutor(new EnchLimiterFixCommand(this));

		log.info (language.get (Bukkit.getConsoleSender(), "enabled", "EnchantLimiter in force; by Filbert66"));
	}

	
	public void onDisable()
	{
		prevXP.clear();
		prevAnvilData.clear();
		lastMsg.clear();
		if (Global_Groups != null) Global_Groups.clear();
		if (Table_Groups != null) Table_Groups.clear();
		if (Anvil_Groups != null) Anvil_Groups.clear();
	}
}