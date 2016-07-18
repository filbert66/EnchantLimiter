/****
* 30 Jul 2015 : PSW : Added isRepairable(), isAnvilRepairable()
* 28 Mar 2016 : PSW : 1.9- added ELYTRA, SHIELD
*/

package com.yahoo.phil_work;

import org.bukkit.Material;

public class MaterialCategory {
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
	public static boolean isArmor (Material type) {
		return isHelmet(type) || isChestplate (type) || isLeggings(type) || isBoots (type) || isBarding (type);
	}
	public static boolean isWeapon (Material type) {
		return (type == Material.BOW || isSword (type));
	}
	public static boolean isTool (Material type) {
		if (isSpade (type) || isHoe (type) || isPick (type) || isAxe (type))
			return true;
			
		switch (type) {
			case SHEARS:
			case FLINT_AND_STEEL:
			case FISHING_ROD:
			case CARROT_STICK:
			case SHIELD:
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
	// Returns true if can be repaired in crafting window by combining
	public static boolean isRepairable (Material type) {
		return (type.getMaxDurability() > 0);
	}
	public static boolean isAnvilRepairable (Material type) {
		if (! isRepairable(type))
			return false;

		// now catch unrepairable items
		switch (type) {
			case BOW:
			case SHEARS:
			case FLINT_AND_STEEL:
			case FISHING_ROD:
			case CARROT_STICK:
				return false;
			default:
				return true;
		}
	}

	// Returns the raw material that can be used for repair of this item, or null
	public static Material getRawMaterial (Material item) {
		switch (item) {
			case DIAMOND_BARDING:
			case DIAMOND_BOOTS:
			case DIAMOND_CHESTPLATE:
			case DIAMOND_HELMET:
			case DIAMOND_LEGGINGS:
			case DIAMOND_AXE:
			case DIAMOND_HOE:
			case DIAMOND_PICKAXE:
			case DIAMOND_SPADE:
			case DIAMOND_SWORD:
				return Material.DIAMOND;
				
			case GOLD_BARDING: 
			case GOLD_BOOTS: 
			case GOLD_CHESTPLATE: 
			case GOLD_HELMET: 
			case GOLD_LEGGINGS: 
			case GOLD_AXE: 
			case GOLD_HOE: 
			case GOLD_PICKAXE: 
			case GOLD_SPADE: 
			case GOLD_SWORD: 
				return Material.GOLD_INGOT;
				
			case CHAINMAIL_BOOTS:
			case CHAINMAIL_CHESTPLATE:
			case CHAINMAIL_HELMET:
			case CHAINMAIL_LEGGINGS:
			case IRON_AXE:
			case IRON_HOE:
			case IRON_PICKAXE:
			case IRON_SPADE:
			case IRON_SWORD:
			case IRON_BARDING:
			case IRON_BOOTS:
			case IRON_CHESTPLATE:
			case IRON_HELMET:
			case IRON_LEGGINGS:
				return Material.IRON_INGOT;
				
			case STONE_AXE:
			case STONE_HOE:
			case STONE_PICKAXE:
			case STONE_SPADE:
			case STONE_SWORD:
				return Material.STONE;
				
			case WOOD_AXE:
			case WOOD_HOE:
			case WOOD_PICKAXE:
			case WOOD_SPADE:
			case WOOD_SWORD:
				return Material.WOOD;
				
			case LEATHER_BOOTS:
			case LEATHER_CHESTPLATE:
			case LEATHER_HELMET:
			case LEATHER_LEGGINGS:
				return Material.LEATHER;
				
			case ELYTRA:
				return Material.LEATHER;
		}
		return null;
	}
}