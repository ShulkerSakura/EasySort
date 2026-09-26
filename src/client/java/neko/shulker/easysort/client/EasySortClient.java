package neko.shulker.easysort.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

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
		sortKey = new KeyMapping("key.easysort.sort", InputConstants.Type.KEYBOARD, InputConstants.KEY_R, EASYSORT);
		KeyMappingHelper.registerKeyMapping(sortKey);
		LOG.info("[EasySort] KeyMapping registered: {}", sortKey.getName());
	}
}
