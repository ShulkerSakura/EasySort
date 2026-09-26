package neko.shulker.easysort.client.sort;

import neko.shulker.easysort.client.EasySortClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BundleItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class ItemSorter {

	/** 每个 tick 最多执行的移动次数，避免一次性发送过多点击包 */
	private static final int MOVES_PER_TICK = 3;
	/** 整次整理的步数上限，防止极端情况下无限循环 */
	private static final int MAX_STEPS = 300;

	/** 是否有整理正在进行中。用于忽略重复触发（例如按键自动重复） */
	private static boolean sorting = false;

	/**
	 * 对容器进行排序
	 * 使用容器点击操作来确保服务器同步
	 */
	public static void sortContainer(Container container, AbstractContainerMenu menu, MultiPlayerGameMode gameMode) {
		if (gameMode == null || menu == null) {
			return;
		}

		Minecraft mc = Minecraft.getInstance();
		Player player = mc.player;
		if (player == null) {
			return;
		}

		// 已有整理在进行：忽略重复触发，避免多条点击链互相干扰导致状态错乱
		if (sorting) {
			return;
		}
		// 光标上有物品时状态不确定，直接放弃
		if (!menu.getCarried().isEmpty()) {
			return;
		}
		// 只作用于当前真正打开的菜单
		if (menu != player.containerMenu) {
			return;
		}

		int startSlot;
		int endSlot;

		if (container instanceof Inventory) {
			// 玩家背包：只整理主背包区域（槽位 9-35）
			// 排除快捷栏、装备栏、副手格与合成格
			startSlot = 9;
			endSlot = 36;
		} else {
			// 普通容器：整理全部容器槽位
			startSlot = 0;
			endSlot = container.getContainerSize();
		}

		startSlot = Math.max(0, startSlot);
		endSlot = Math.min(endSlot, menu.slots.size());
		if (endSlot - startSlot < 2) {
			return;
		}

		sorting = true;
		Session session = new Session(mc, menu, gameMode, player, startSlot, endSlot);
		mc.execute(() -> run(session));
	}

	/**
	 * 整理主循环：每个 tick 执行若干次移动，直到完成或中止
	 */
	private static void run(Session s) {
		if (!stillValid(s)) {
			sorting = false;
			return;
		}

		int ops = 0;
		boolean aborted = false;

		while (ops < MOVES_PER_TICK && s.steps < MAX_STEPS) {
			// 每次移动前后光标都必须为空，否则说明状态已经错乱
			if (!s.menu.getCarried().isEmpty()) {
				aborted = true;
				break;
			}

			int[] pair = findMergePair(s);
			if (pair != null) {
				if (!mergeStacks(s, pair[0], pair[1])) {
					aborted = true;
					break;
				}
				s.steps++;
				ops++;
				continue;
			}

			int result = sortStep(s);
			if (result == 1) {
				s.steps++;
				ops++;
				continue;
			}
			if (result < 0) {
				aborted = true;
			}
			break;
		}

		if (!aborted && s.steps < MAX_STEPS && ops >= MOVES_PER_TICK) {
			// 还有工作要做，下一 tick 继续
			s.mc.execute(() -> run(s));
			return;
		}

		sorting = false;
		if (aborted || !isSorted(s)) {
			EasySortClient.LOG.warn("[EasySort] 整理中止或未完成 (steps={}, carried={})",
				s.steps, !s.menu.getCarried().isEmpty());
		} else {
			EasySortClient.LOG.info("[EasySort] 整理完成 (steps={})", s.steps);
		}
	}

	/**
	 * 检查整理会话是否仍然有效（玩家、菜单没有变化）
	 */
	private static boolean stillValid(Session s) {
		return s.mc.player != null
			&& s.mc.player == s.player
			&& s.mc.player.containerMenu == s.menu
			&& s.menu.slots.size() == s.slotCount;
	}

	/**
	 * 查找一对可以合并的同类物品
	 * 收纳袋 stacksTo(1)，永远无法合并，因此跳过
	 */
	private static int[] findMergePair(Session s) {
		for (int i = s.startSlot; i < s.endSlot; i++) {
			ItemStack a = s.menu.getSlot(i).getItem();
			if (a.isEmpty() || a.getItem() instanceof BundleItem) {
				continue;
			}
			if (a.getCount() >= a.getMaxStackSize()) {
				continue;
			}
			for (int j = i + 1; j < s.endSlot; j++) {
				ItemStack b = s.menu.getSlot(j).getItem();
				if (b.isEmpty() || b.getItem() instanceof BundleItem) {
					continue;
				}
				if (ItemStack.isSameItemSameComponents(a, b)) {
					return new int[]{i, j};
				}
			}
		}
		return null;
	}

	/**
	 * 把 source 槽位的物品尽量合并进 target 槽位，剩余的放回 source
	 */
	private static boolean mergeStacks(Session s, int target, int source) {
		if (!s.menu.getCarried().isEmpty()) {
			return false;
		}
		click(s, source);
		if (s.menu.getCarried().isEmpty()) {
			return false;
		}
		click(s, target);
		if (!s.menu.getCarried().isEmpty()) {
			// 有剩余，放回 source
			click(s, source);
			if (!s.menu.getCarried().isEmpty()) {
				return false;
			}
		}
		return true;
	}

	/**
	 * 执行一次排序移动，返回 1 表示完成了一次移动，0 表示已经有序，-1 表示无法继续
	 */
	private static int sortStep(Session s) {
		List<ItemStack> desired = collectSorted(s);
		int n = desired.size();

		for (int k = 0; k < n; k++) {
			int target = s.startSlot + k;
			ItemStack want = desired.get(k);
			ItemStack cur = s.menu.getSlot(target).getItem();

			if (isSameStack(cur, want)) {
				continue;
			}

			// 多重集守恒保证 want 一定存在于 target 及其之后的位置
			int source = findStack(s, want, target, s.endSlot);
			if (source < 0) {
				return -1;
			}

			if (cur.isEmpty()) {
				return moveToEmpty(s, source, target) ? 1 : -1;
			}

			// 目标槽已被占用：优先用空槽做中转，这样对收纳袋同样安全
			int buffer = findBuffer(s, target, source, cur);
			if (buffer >= 0) {
				return swapViaBuffer(s, target, source, buffer) ? 1 : -1;
			}

			// 没有空槽可用时，退化为直接交换（收纳袋与同类物品不可直接交换）
			if (canDirectSwap(cur, want)) {
				return directSwap(s, target, source) ? 1 : -1;
			}

			return -1;
		}
		return 0;
	}

	/**
	 * 收集整理范围内的物品，按物品 ID 升序、数量降序排列
	 */
	private static List<ItemStack> collectSorted(Session s) {
		List<ItemStack> stacks = new ArrayList<>();
		for (int i = s.startSlot; i < s.endSlot; i++) {
			ItemStack stack = s.menu.getSlot(i).getItem();
			if (!stack.isEmpty()) {
				stacks.add(stack.copy());
			}
		}
		stacks.sort((a, b) -> {
			int byId = Item.getId(a.getItem()) - Item.getId(b.getItem());
			if (byId != 0) {
				return byId;
			}
			return b.getCount() - a.getCount();
		});
		return stacks;
	}

	/**
	 * 在 [from, end) 范围内查找与 want 完全相同的物品堆
	 */
	private static int findStack(Session s, ItemStack want, int from, int end) {
		for (int i = Math.max(from, s.startSlot); i < end; i++) {
			if (isSameStack(s.menu.getSlot(i).getItem(), want)) {
				return i;
			}
		}
		return -1;
	}

	/**
	 * 查找一个可用于中转的空槽位
	 */
	private static int findBuffer(Session s, int avoidA, int avoidB, ItemStack toHold) {
		// 优先使用整理范围内的空槽
		for (int i = s.startSlot; i < s.endSlot; i++) {
			if (i == avoidA || i == avoidB) {
				continue;
			}
			if (s.menu.getSlot(i).getItem().isEmpty()) {
				return i;
			}
		}

		// 范围内没有空槽时，借用玩家背包中的空槽（快捷栏、副手等）
		// 用 container instanceof Inventory 排除合成结果槽与创造模式的销毁槽
		for (int i = 0; i < s.menu.slots.size(); i++) {
			if (i == avoidA || i == avoidB) {
				continue;
			}
			Slot slot = s.menu.getSlot(i);
			if (!slot.getItem().isEmpty()) {
				continue;
			}
			if (!(slot.container instanceof Inventory)) {
				continue;
			}
			if (!slot.mayPlace(toHold)) {
				continue;
			}
			return i;
		}
		return -1;
	}

	/**
	 * 把 source 槽位的物品移动到空的目标槽位
	 * 目标槽为空时不存在收纳袋吸物品、同类合并等副作用
	 */
	private static boolean moveToEmpty(Session s, int source, int target) {
		if (!s.menu.getCarried().isEmpty()) {
			return false;
		}
		click(s, source);
		if (s.menu.getCarried().isEmpty()) {
			return false;
		}
		click(s, target);
		if (!s.menu.getCarried().isEmpty()) {
			// 目标槽没有完全接收，回滚
			click(s, source);
			return false;
		}
		return true;
	}

	/**
	 * 借助一个空的中转槽，交换 target 与 source 两个槽位的内容
	 * 共 6 次点击，每一次"放入"都发生在空槽上，光标在每个边界都为空，
	 * 因此不会触发收纳袋的吸入行为，也不会触发同类合并
	 */
	private static boolean swapViaBuffer(Session s, int target, int source, int buffer) {
		if (!s.menu.getCarried().isEmpty()) {
			return false;
		}

		click(s, target);                          // 光标 = 目标槽物品
		click(s, buffer);                          // 存入空的中转槽
		if (!s.menu.getCarried().isEmpty()) {
			return false;
		}

		click(s, source);                          // 光标 = 待移动物品
		click(s, target);                          // 放入已腾空的目标槽
		if (!s.menu.getCarried().isEmpty()) {
			return false;
		}

		click(s, buffer);                          // 取回原目标槽物品
		click(s, source);                          // 放回已腾空的源槽
		return s.menu.getCarried().isEmpty();
	}

	/**
	 * 直接交换两个槽位（仅在完全没有空槽可用时使用）
	 * 同一物品的不同数量堆会走合并分支，收纳袋会吸物品，因此都被排除
	 */
	private static boolean directSwap(Session s, int target, int source) {
		if (!s.menu.getCarried().isEmpty()) {
			return false;
		}
		ItemStack targetBefore = s.menu.getSlot(target).getItem().copy();
		ItemStack sourceBefore = s.menu.getSlot(source).getItem().copy();

		click(s, source);
		if (s.menu.getCarried().isEmpty()) {
			return false;
		}
		click(s, target);
		if (!s.menu.getCarried().isEmpty()) {
			click(s, source);
		}
		if (!s.menu.getCarried().isEmpty()) {
			return false;
		}

		// 槽位可能因 mayPlace 失败而拒绝放置，这里确认交换确实发生了
		return isSameStack(s.menu.getSlot(target).getItem(), sourceBefore)
			&& isSameStack(s.menu.getSlot(source).getItem(), targetBefore);
	}

	private static boolean canDirectSwap(ItemStack a, ItemStack b) {
		return !(a.getItem() instanceof BundleItem)
			&& !(b.getItem() instanceof BundleItem)
			&& !ItemStack.isSameItemSameComponents(a, b);
	}

	/**
	 * 检查整理范围内是否已经完全有序
	 */
	private static boolean isSorted(Session s) {
		List<ItemStack> desired = collectSorted(s);
		for (int k = 0; k < desired.size(); k++) {
			if (!isSameStack(s.menu.getSlot(s.startSlot + k).getItem(), desired.get(k))) {
				return false;
			}
		}
		return true;
	}

	/**
	 * 判断两个物品堆是否完全相同（类型、组件与数量）
	 */
	private static boolean isSameStack(ItemStack a, ItemStack b) {
		return !a.isEmpty() && !b.isEmpty()
			&& a.getCount() == b.getCount()
			&& ItemStack.isSameItemSameComponents(a, b);
	}

	private static void click(Session s, int slot) {
		s.gameMode.handleContainerInput(s.menu.containerId, slot, 0, ContainerInput.PICKUP, s.player);
	}

	/**
	 * 一次整理的会话状态
	 */
	private static class Session {
		final Minecraft mc;
		final AbstractContainerMenu menu;
		final MultiPlayerGameMode gameMode;
		final Player player;
		final int startSlot;
		final int endSlot;
		final int slotCount;
		int steps;

		Session(Minecraft mc, AbstractContainerMenu menu, MultiPlayerGameMode gameMode, Player player,
				int startSlot, int endSlot) {
			this.mc = mc;
			this.menu = menu;
			this.gameMode = gameMode;
			this.player = player;
			this.startSlot = startSlot;
			this.endSlot = endSlot;
			this.slotCount = menu.slots.size();
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
