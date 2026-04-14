package neko.shulker.easysort.client.mixin;

import neko.shulker.easysort.client.EasySortClient;
import neko.shulker.easysort.client.sort.ItemSorter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;



@Mixin(AbstractContainerScreen.class)
public class AbstractContainerScreenMixin {

	@Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
	private void onKeyPressed(KeyEvent keyEvent, CallbackInfoReturnable<Boolean> cir) {
		// 检查是否是排序键按下，使用 KeyMapping 的 matches 方法来检测
		if (EasySortClient.sortKey != null && EasySortClient.sortKey.matches(keyEvent)) {
			EasySortClient.LOG.info("[EasySort] Sort key pressed in container via keyPressed!");

			AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) (Object) this;
			Container container = getContainerFromScreen(screen);

			if (container != null) {
				EasySortClient.LOG.info("[EasySort] Sorting container...");
				// 使用新的排序方法，传递菜单和游戏模式以同步到服务器
				Minecraft mc = Minecraft.getInstance();
				if (mc.gameMode != null) {
					ItemSorter.sortContainer(container, screen.getMenu(), mc.gameMode);
				} else {
					// 单人游戏或本地游戏
					ItemSorter.sortContainer(container);
				}
			} else {
				EasySortClient.LOG.info("[EasySort] Container is null, cannot sort. Menu type: {}",
					screen.getMenu().getClass().getSimpleName());
			}

			cir.setReturnValue(true);
		}
	}

	/**
	 * 从容器屏幕获取容器对象
	 * 支持：箱子、潜影盒、玩家背包（仅当只打开背包时）、创造模式背包
	 */
	private Container getContainerFromScreen(AbstractContainerScreen<?> containerScreen) {
		AbstractContainerMenu menu = containerScreen.getMenu();

		// 1. 箱子菜单
		if (menu instanceof ChestMenu chestMenu) {
			return chestMenu.getContainer();
		}

		// 2. 潜影盒菜单 - 通过第一个槽位获取容器
		if (menu instanceof ShulkerBoxMenu) {
			// 潜影盒的槽位 0-26 是潜影盒的容器
			// 通过槽位获取容器
			for (int i = 0; i < menu.slots.size(); i++) {
				Slot slot = menu.getSlot(i);
				if (slot.container != null) {
					// 检查这个容器是否是潜影盒的容器（不是玩家背包）
					if (!(slot.container instanceof Inventory)) {
						return slot.container;
					}
				}
			}
		}

		// 3. 玩家背包菜单 - 仅当只打开背包时（即屏幕是 InventoryScreen 且没有其他容器）
		if (menu instanceof InventoryMenu inventoryMenu) {
			// 检查是否只打开了背包（不是合成台、箱子等其他界面）
			if (containerScreen instanceof InventoryScreen) {
				// 获取玩家背包（槽位 9-35）
				// 通过槽位获取容器
				for (int i = 0; i < menu.slots.size(); i++) {
					Slot slot = menu.getSlot(i);
					if (slot.container instanceof Inventory) {
						return slot.container;
					}
				}
			}
		}

		// 4. 创造模式背包 - 当处于创造模式且打开物品栏时
		if (containerScreen instanceof CreativeModeInventoryScreen creativeScreen) {
			// 检查是否显示的是玩家背包（物品栏界面底部的那几行）
			// 创造模式有两种视图：物品选择模式和背包模式
			// 只有在背包模式下才允许整理
			if (creativeScreen.isInventoryOpen()) {
				// 获取玩家背包容器
				// 在创造模式中，玩家的背包槽位在菜单的底部
				for (int i = 0; i < menu.slots.size(); i++) {
					Slot slot = menu.getSlot(i);
					if (slot.container instanceof Inventory) {
						return slot.container;
					}
				}
			}
		}

		return null;
	}
}
