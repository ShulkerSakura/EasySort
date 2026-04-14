package neko.shulker.easysort.client.sort;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class ItemSorter {

	/**
	 * 对容器进行排序
	 * 使用容器点击操作来确保服务器同步
	 */
	public static void sortContainer(Container container, AbstractContainerMenu menu, MultiPlayerGameMode gameMode) {
		if (gameMode == null || menu == null) {
			return;
		}

		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) {
			return;
		}

		// 判断是否是玩家背包
		boolean isPlayerInventory = container instanceof Inventory;
		int startSlot;
		int endSlot;

		if (isPlayerInventory && menu instanceof InventoryMenu) {
			// 玩家背包：只整理主背包区域（槽位 9-35）
			// 排除快捷栏（0-8）、装备栏（36-39）、副手格（40）、合成格（41-44）
			startSlot = InventoryMenu.INV_SLOT_START; // 9
			endSlot = InventoryMenu.INV_SLOT_END;     // 36
		} else if (isPlayerInventory && !(menu instanceof InventoryMenu)) {
			// 创造模式背包：只整理主背包区域（槽位 9-35）
			// 创造模式背包槽位：0-8 快捷栏，9-35 主背包，36-39 装备，40 副手
			// 通过判断菜单不是 InventoryMenu 来识别创造模式
			startSlot = 9;
			endSlot = 36;
		} else {
			// 普通容器：整理全部
			startSlot = 0;
			endSlot = container.getContainerSize();
		}

		int containerSize = endSlot - startSlot;

		// 第一步：合并同种物品
		mergeAllStacks(menu, gameMode, startSlot, endSlot, mc);

		// 第二步：在下一tick执行排序
		final int finalStartSlot = startSlot;
		final int finalEndSlot = endSlot;
		mc.execute(() -> {
			performSort(menu, gameMode, finalStartSlot, finalEndSlot, mc);
		});
	}

	/**
	 * 合并所有可以合并的物品堆
	 */
	private static void mergeAllStacks(AbstractContainerMenu menu, MultiPlayerGameMode gameMode,
									   int startSlot, int endSlot, Minecraft mc) {
		Player player = (Player) mc.player;
		boolean merged;

		// 重复合并直到没有可以合并的物品
		do {
			merged = false;
			for (int i = startSlot; i < endSlot && i < menu.slots.size(); i++) {
				ItemStack stack1 = menu.getSlot(i).getItem();
				if (stack1.isEmpty() || stack1.getCount() >= stack1.getMaxStackSize()) {
					continue; // 空槽位或已满
				}

				// 寻找可以合并的物品
				for (int j = i + 1; j < endSlot && j < menu.slots.size(); j++) {
					ItemStack stack2 = menu.getSlot(j).getItem();
					if (stack2.isEmpty()) continue;

					// 检查是否可以合并
					if (ItemStack.isSameItemSameComponents(stack1, stack2)) {
						int space = stack1.getMaxStackSize() - stack1.getCount();
						if (space > 0) {
							// 将stack2移动到stack1
							moveItemPartial(menu, gameMode, j, i, mc);
							merged = true;
							break; // 重新从头开始
						}
					}
				}
				if (merged) break;
			}
		} while (merged);
	}

	/**
	 * 部分移动物品（用于合并）
	 */
	private static void moveItemPartial(AbstractContainerMenu menu, MultiPlayerGameMode gameMode,
										int sourceSlot, int targetSlot, Minecraft mc) {
		Player player = (Player) mc.player;

		// 拿起源槽位的物品
		gameMode.handleContainerInput(menu.containerId, sourceSlot, 0, ContainerInput.PICKUP, player);

		// 尝试放到目标槽位（会尽可能合并）
		gameMode.handleContainerInput(menu.containerId, targetSlot, 0, ContainerInput.PICKUP, player);

		// 如果还有剩余，放回源槽位
		ItemStack carried = menu.getCarried();
		if (!carried.isEmpty()) {
			gameMode.handleContainerInput(menu.containerId, sourceSlot, 0, ContainerInput.PICKUP, player);
		}
	}

	/**
	 * 执行排序操作
	 */
	private static void performSort(AbstractContainerMenu menu, MultiPlayerGameMode gameMode,
									int startSlot, int endSlot, Minecraft mc) {
		if (mc.player == null) {
			return;
		}

		// 收集当前容器中的物品
		List<SlotInfo> slotInfos = new ArrayList<>();
		for (int i = startSlot; i < endSlot && i < menu.slots.size(); i++) {
			ItemStack stack = menu.getSlot(i).getItem();
			if (!stack.isEmpty()) {
				slotInfos.add(new SlotInfo(i, stack));
			}
		}

		if (slotInfos.isEmpty()) {
			return;
		}

		// 按物品类型和数量排序
		slotInfos.sort((a, b) -> {
			int itemCompare = Item.getId(a.stack.getItem()) - Item.getId(b.stack.getItem());
			if (itemCompare != 0) {
				return itemCompare;
			}
			return b.stack.getCount() - a.stack.getCount();
		});

		// 执行排序操作
		executeSortMoves(menu, gameMode, slotInfos, startSlot, endSlot, mc, 0);
	}

	/**
	 * 执行排序移动操作
	 */
	private static void executeSortMoves(AbstractContainerMenu menu, MultiPlayerGameMode gameMode,
										 List<SlotInfo> sortedSlots, int startSlot, int endSlot,
										 Minecraft mc, int currentIndex) {
		if (mc.player == null) {
			return;
		}

		int processedCount = 0;
		int i = currentIndex;

		while (i < sortedSlots.size() && processedCount < 3) {
			SlotInfo info = sortedSlots.get(i);
			int targetSlot = startSlot + i;

			if (targetSlot >= endSlot || targetSlot >= menu.slots.size()) {
				break;
			}

			// 如果物品已经在正确的位置，跳过
			if (info.originalSlot == targetSlot) {
				i++;
				continue;
			}

			// 检查目标槽位当前是什么物品
			ItemStack targetStack = menu.getSlot(targetSlot).getItem();

			// 如果目标槽位已经有正确的物品类型，跳过
			if (!targetStack.isEmpty() && isSameItemType(targetStack, info.stack)) {
				i++;
				continue;
			}

			// 找到当前应该移动的物品在哪个槽位
			int currentSlot = findSlotWithItem(menu, info.stack, startSlot, endSlot, i);

			if (currentSlot >= 0 && currentSlot != targetSlot) {
				// 移动物品到目标位置
				moveItem(menu, gameMode, currentSlot, targetSlot, mc);
				processedCount++;
			}
			i++;
		}

		// 如果还有更多物品需要处理，安排下一tick继续
		if (i < sortedSlots.size()) {
			final int nextIndex = i;
			mc.execute(() -> {
				executeSortMoves(menu, gameMode, sortedSlots, startSlot, endSlot, mc, nextIndex);
			});
		}
	}

	/**
	 * 检查两个物品是否是同一种类型（不包括数量）
	 */
	private static boolean isSameItemType(ItemStack a, ItemStack b) {
		return ItemStack.isSameItemSameComponents(a, b);
	}

	/**
	 * 在容器中找到指定物品的槽位
	 */
	private static int findSlotWithItem(AbstractContainerMenu menu, ItemStack target,
										int startSlot, int endSlot, int startIndex) {
		// 首先尝试从startIndex开始查找
		for (int i = startSlot + startIndex; i < endSlot && i < menu.slots.size(); i++) {
			ItemStack stack = menu.getSlot(i).getItem();
			if (!stack.isEmpty() && isSameItemType(stack, target)) {
				return i;
			}
		}
		// 如果没找到，从startSlot开始查找
		for (int i = startSlot; i < startSlot + startIndex && i < endSlot && i < menu.slots.size(); i++) {
			ItemStack stack = menu.getSlot(i).getItem();
			if (!stack.isEmpty() && isSameItemType(stack, target)) {
				return i;
			}
		}
		return -1;
	}

	/**
	 * 移动物品从sourceSlot到targetSlot
	 */
	private static void moveItem(AbstractContainerMenu menu, MultiPlayerGameMode gameMode,
								 int sourceSlot, int targetSlot, Minecraft mc) {
		Player player = (Player) mc.player;

		// 拿起源槽位的物品
		gameMode.handleContainerInput(menu.containerId, sourceSlot, 0, ContainerInput.PICKUP, player);
		// 放到目标槽位
		gameMode.handleContainerInput(menu.containerId, targetSlot, 0, ContainerInput.PICKUP, player);

		// 如果目标槽位原来有物品，现在会在光标上，需要处理
		ItemStack carried = menu.getCarried();
		if (!carried.isEmpty()) {
			// 将光标上的物品放回源槽位
			gameMode.handleContainerInput(menu.containerId, sourceSlot, 0, ContainerInput.PICKUP, player);
		}
	}

	/**
	 * 槽位信息类
	 */
	private static class SlotInfo {
		final int originalSlot;
		final ItemStack stack;

		SlotInfo(int originalSlot, ItemStack stack) {
			this.originalSlot = originalSlot;
			this.stack = stack.copy();
		}
	}

	/**
	 * 简单的容器排序（用于本地游戏）
	 */
	public static void sortContainer(Container container) {
		// 本地游戏排序逻辑
		int size = container.getContainerSize();
		List<ItemStack> items = new ArrayList<>();

		// 收集物品
		for (int i = 0; i < size; i++) {
			ItemStack stack = container.getItem(i);
			if (!stack.isEmpty()) {
				items.add(stack.copy());
			}
		}

		// 排序
		items.sort((a, b) -> {
			int itemCompare = Item.getId(a.getItem()) - Item.getId(b.getItem());
			if (itemCompare != 0) {
				return itemCompare;
			}
			return b.getCount() - a.getCount();
		});

		// 清空容器
		for (int i = 0; i < size; i++) {
			container.setItem(i, ItemStack.EMPTY);
		}

		// 放回物品
		for (int i = 0; i < items.size(); i++) {
			container.setItem(i, items.get(i));
		}
	}
}
