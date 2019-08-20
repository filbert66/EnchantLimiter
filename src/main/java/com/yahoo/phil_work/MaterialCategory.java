/****
* 30 Jul 2015 : PSW : Added isRepairable(), isAnvilRepairable()
* 28 Mar 2016 : PSW : 1.9- added ELYTRA, SHIELD
* 05 Aug 2019 : PSW : Updated for 1.14, adding TURTLE_HELMET, and changed ELYTRA repair element.
* 15 Aug 2019 : PSW: Added TRIDENT and CROSSBOW, and isBow()
*/

package com.yahoo.phil_work;

import org.bukkit.Material;

public class MaterialCategory {
	public static boolean isHelmet (Material type) {
		switch (type) {
			case LEATHER_HELMET:
			case IRON_HELMET:
			case GOLDEN_HELMET: 
			case DIAMOND_HELMET:
			case CHAINMAIL_HELMET:
			case TURTLE_HELMET:
				return true;
			default:
				return false;
		}
	}

	public static boolean isBoots (Material type) {
		switch (type) {
			case LEATHER_BOOTS:
			case IRON_BOOTS:
			case GOLDEN_BOOTS: 
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
			case GOLDEN_CHESTPLATE: 
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
			case GOLDEN_LEGGINGS: 
			case DIAMOND_LEGGINGS:
			case CHAINMAIL_LEGGINGS:
				return true;
			default:
				return false;
		}
	}
	public static boolean isBarding (Material type) {
		switch (type) {
			case IRON_HORSE_ARMOR:
			case GOLDEN_HORSE_ARMOR: 
			case DIAMOND_HORSE_ARMOR:
			case LEATHER_HORSE_ARMOR:
				return true;
			default:
				return false;
		}
	}
	public static boolean isArmor (Material type) {
		return isHelmet(type) || isChestplate (type) || isLeggings(type) || isBoots (type) || isBarding (type);
	}
	public static boolean isWeapon (Material type) {
		if (isSword (type) || type == Material.TRIDENT || isBow (type)) 
			return true;

		return false;
	}
	public static boolean isBow (Material type) {
		switch (type) {
			case BOW:
			case CROSSBOW:
				return true;
		} 
		return false;
	}
	public static boolean isTool (Material type) {
		if (isSpade (type) || isHoe (type) || isPick (type) || isAxe (type))
			return true;
			
		switch (type) {
			case SHEARS:
			case FLINT_AND_STEEL:
			case FISHING_ROD:
			case CARROT_ON_A_STICK:
			case SHIELD:
			case COMPASS:
			case CLOCK:
			case LEAD:
				return true;
			default:
				return false;
		}
	}
	
	public static boolean isSword (Material type) {
		switch (type) {
			case IRON_SWORD:
			case STONE_SWORD:
			case GOLDEN_SWORD: 
			case DIAMOND_SWORD:
			case WOODEN_SWORD:
				return true;
			default:
				return false;
		}
	}
	
	public static boolean isSpade (Material type) {
		switch (type) {
			case IRON_SHOVEL:
			case STONE_SHOVEL:
			case GOLDEN_SHOVEL: 
			case DIAMOND_SHOVEL:
			case WOODEN_SHOVEL:
				return true;
			default: 
				return false;
		}
	}
	
	public static boolean isHoe (Material type) {
		switch (type) {	
			case IRON_HOE:
			case STONE_HOE:
			case GOLDEN_HOE: 
			case DIAMOND_HOE:
			case WOODEN_HOE:
				return true;
			default: 
				return false;
		}
	}

	public static boolean isPick (Material type) {
		switch (type) {				
			case IRON_PICKAXE:
			case STONE_PICKAXE:
			case GOLDEN_PICKAXE: 
			case DIAMOND_PICKAXE:
			case WOODEN_PICKAXE:
				return true;
			default: 
				return false;
		}
	}
	public static boolean isAxe (Material type) {
		switch (type) {				
			case IRON_AXE:
			case STONE_AXE:
			case GOLDEN_AXE: 
			case DIAMOND_AXE:
			case WOODEN_AXE:
				return true;
			default: 
				return false;
		}
	}
	
	public static boolean isLog (Material type) {
		switch (type) {				
			case ACACIA_LOG:
			case BIRCH_LOG:
			case DARK_OAK_LOG:
			case JUNGLE_LOG: 
			case OAK_LOG:
			case SPRUCE_LOG:
				return true;
			default: 
				return false;
		}
	}	
	public static boolean isWood (Material type) {
		switch (type) {				
			case ACACIA_WOOD:
			case BIRCH_WOOD:
			case DARK_OAK_WOOD:
			case JUNGLE_WOOD: 
			case OAK_WOOD :
			case SPRUCE_WOOD:
				return true;
			default: 
				return false;
		}
	}	
	public static boolean isStrippedLog (Material type) {
		switch (type) {				
			case STRIPPED_ACACIA_LOG:
			case STRIPPED_BIRCH_LOG:
			case STRIPPED_DARK_OAK_LOG:
			case STRIPPED_JUNGLE_LOG: 
			case STRIPPED_OAK_LOG:
			case STRIPPED_SPRUCE_LOG:
				return true;
			default: 
				return false;
		}
	}
	public static boolean isStrippedWood (Material type) {
		switch (type) {				
			case STRIPPED_ACACIA_WOOD:
			case STRIPPED_BIRCH_WOOD:
			case STRIPPED_DARK_OAK_WOOD:
			case STRIPPED_JUNGLE_WOOD: 
			case STRIPPED_OAK_WOOD:
			case STRIPPED_SPRUCE_WOOD:
				return true;
			default: 
				return false;
		}
	}
	public static boolean isBurnableWood (Material type) {
		return isLog(type) || isStrippedLog (type) || isWood(type) || isStrippedWood (type);
	}
	
	public static boolean isRepairable (Material type) {
		return (type.getMaxDurability() > 0);
	}
	public static boolean isAnvilRepairable (Material type) {
		if (! isRepairable(type))
			return false;

		// now catch unrepairable items
		switch (type) {
		    case CROSSBOW:
			case BOW:   // only with another bow
			case SHEARS: // only with another shear
			case FLINT_AND_STEEL:
			case FISHING_ROD: // only another rod
			case CARROT_ON_A_STICK:
			case COMPASS:
			case CLOCK:
			case LEAD:
			case TRIDENT:
				return false;
			default:
				return true;
		}
	}

	// Returns the raw material that can be used for repair of this item, or null
	public static Material getRawMaterial (Material item) {
		switch (item) {
			case DIAMOND_HORSE_ARMOR:
			case DIAMOND_BOOTS:
			case DIAMOND_CHESTPLATE:
			case DIAMOND_HELMET:
			case DIAMOND_LEGGINGS:
			case DIAMOND_AXE:
			case DIAMOND_HOE:
			case DIAMOND_PICKAXE:
			case DIAMOND_SHOVEL:
			case DIAMOND_SWORD:
				return Material.DIAMOND;
				
			case GOLDEN_HORSE_ARMOR: 
			case GOLDEN_BOOTS: 
			case GOLDEN_CHESTPLATE: 
			case GOLDEN_HELMET: 
			case GOLDEN_LEGGINGS: 
			case GOLDEN_AXE: 
			case GOLDEN_HOE: 
			case GOLDEN_PICKAXE: 
			case GOLDEN_SHOVEL: 
			case GOLDEN_SWORD: 
				return Material.GOLD_INGOT;
				
			case CHAINMAIL_BOOTS:
			case CHAINMAIL_CHESTPLATE:
			case CHAINMAIL_HELMET:
			case CHAINMAIL_LEGGINGS:
			case IRON_AXE:
			case IRON_HOE:
			case IRON_PICKAXE:
			case IRON_SHOVEL:
			case IRON_SWORD:
			case IRON_HORSE_ARMOR:
			case IRON_BOOTS:
			case IRON_CHESTPLATE:
			case IRON_HELMET:
			case IRON_LEGGINGS:
				return Material.IRON_INGOT;
				
			case STONE_AXE:
			case STONE_HOE:
			case STONE_PICKAXE:
			case STONE_SHOVEL:
			case STONE_SWORD:
				return Material.STONE;
				
			case WOODEN_AXE:
			case WOODEN_HOE:
			case WOODEN_PICKAXE:
			case WOODEN_SHOVEL:
			case WOODEN_SWORD:
				return Material.OAK_PLANKS;
				
			case LEATHER_BOOTS:
			case LEATHER_CHESTPLATE:
			case LEATHER_HELMET:
			case LEATHER_LEGGINGS:
			case LEATHER_HORSE_ARMOR:
				return Material.LEATHER;
				
			case ELYTRA:
				return Material.PHANTOM_MEMBRANE;
				
			case TURTLE_HELMET:
				return Material.SCUTE;
		}
		return null;
	}
}