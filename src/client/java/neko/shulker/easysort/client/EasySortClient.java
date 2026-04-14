package neko.shulker.easysort.client;

import neko.shulker.easysort.client.sort.ItemSorter;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.resources.Identifier;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.ChestMenu;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mojang.blaze3d.platform.InputConstants;

public class EasySortClient implements ClientModInitializer {
	public static final KeyMapping.Category EASYSORT = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("easysort", "tools"));
	public static KeyMapping sortKey;
	public static final Logger LOG = LoggerFactory.getLogger("easysort");

	@Override
	public void onInitializeClient() {
		LOG.info("[EasySort] Client mod initializing...");
		sortKey = new KeyMapping("key.easysort.sort", InputConstants.Type.KEYSYM, InputConstants.KEY_R, EASYSORT);
		KeyMappingHelper.registerKeyMapping(sortKey);
		LOG.info("[EasySort] KeyMapping registered: {}", sortKey.getName());
	}
}
