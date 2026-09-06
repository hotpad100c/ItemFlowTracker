package ml.mypals.ift.mixin.Inventory;

import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import ml.mypals.ift.track.HighlightManager;
import ml.mypals.ift.track.Nesting;
import ml.mypals.ift.track.TrackMark;
import ml.mypals.ift.track.Tracking;
import net.minecraft.core.NonNullList;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;

@Mixin(AbstractContainerMenu.class)
public abstract class AbstractContainerMenuMixin {
	@Shadow
	@Final
	public NonNullList<Slot> slots;

	@Shadow
	public abstract ItemStack getCarried();

	@Unique
	@Nullable
	private ItemStack[] itemflowtracker$before;

	@Unique
	@Nullable
	private ItemStack itemflowtracker$beforeCarried;

	@Inject(method = "clicked", at = @At("HEAD"))
	private void itemflowtracker$snapshot(int slotId, int button, ContainerInput input, Player player, CallbackInfo ci) {
		if (player.level().isClientSide()) {
			return;
		}

		ItemStack[] snapshot = new ItemStack[this.slots.size()];

		for (int i = 0; i < snapshot.length; i++) {
			snapshot[i] = this.slots.get(i).getItem().copy();
		}

		this.itemflowtracker$before = snapshot;
		this.itemflowtracker$beforeCarried = this.getCarried().copy();
	}

	@Inject(method = "clicked", at = @At("RETURN"))
	private void itemflowtracker$afterClick(int slotIndex, int buttonNum, ContainerInput input, Player player, CallbackInfo ci) {
		ItemStack[] before = this.itemflowtracker$before;
		ItemStack beforeCarried = this.itemflowtracker$beforeCarried;
		this.itemflowtracker$before = null;
		this.itemflowtracker$beforeCarried = null;

		if (before == null || player.level().isClientSide()) {
			return;
		}

		DyeColor dye = Tracking.dyeOf(player.getOffhandItem());
		Container playerInventory = player.getInventory();

		for (int i = 0; i < this.slots.size() && i < before.length; i++) {
			Slot slot = this.slots.get(i);

			if (slot.container == playerInventory) {
				continue;
			}

			ItemStack now = slot.getItem();

			if (now.isEmpty()) {
				continue;
			}

			ItemStack old = before[i];
			boolean sameStack = !old.isEmpty() && ItemStack.isSameItemSameComponents(old, now);
			int arrived = sameStack ? now.getCount() - old.getCount() : now.getCount();

			if (arrived <= 0) {
				continue;
			}

			TrackMark mark = Tracking.get(now);

			if (mark != null) {
				if (old.isEmpty() || Tracking.getRaw(old) == mark) {
					Tracking.arrive(now, mark, arrived);
				}

				HighlightManager.onEnterContainer(slot.container);
				continue;
			}

			mark = dye != null
					? Tracking.newMark(dye, arrived)
					: itemflowtracker$incomingMark(before, beforeCarried, now);

			if (mark != null) {
				Tracking.arrive(now, mark, arrived);
				HighlightManager.onEnterContainer(slot.container);
			} else if (Nesting.carriesMark(now)) {
				HighlightManager.onEnterContainer(slot.container);
			}
		}
	}

	@Unique
	@Nullable
	private TrackMark itemflowtracker$incomingMark(ItemStack[] before, @Nullable ItemStack beforeCarried, ItemStack destination) {
		TrackMark mark = Tracking.get(beforeCarried);

		if (mark != null && ItemStack.isSameItemSameComponents(beforeCarried, destination)) {
			return mark;
		}

		for (ItemStack candidate : before) {
			mark = Tracking.get(candidate);

			if (mark != null && ItemStack.isSameItemSameComponents(candidate, destination)) {
				return mark;
			}
		}

		return null;
	}
}
