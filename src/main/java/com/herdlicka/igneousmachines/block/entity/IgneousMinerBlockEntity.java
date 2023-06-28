package com.herdlicka.igneousmachines.block.entity;

import com.herdlicka.igneousmachines.IgneousMachinesMod;
import com.herdlicka.igneousmachines.block.IgneousCrafterBlock;
import com.herdlicka.igneousmachines.block.IgneousMinerBlock;
import com.herdlicka.igneousmachines.inventory.ImplementedInventory;
import com.herdlicka.igneousmachines.screen.IgneousMinerScreenHandler;
import com.herdlicka.igneousmachines.util.ItemStackUtils;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.SidedInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.context.LootContextParameterSet;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPointerImpl;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class IgneousMinerBlockEntity extends BlockEntity implements NamedScreenHandlerFactory, ImplementedInventory, SidedInventory {

    private final DefaultedList<ItemStack> inventory = DefaultedList.ofSize(11, ItemStack.EMPTY);

    private static final int[] TOP_SLOTS = new int[]{10};
    private static final int[] BOTTOM_SLOTS = new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8};
    private static final int[] SIDE_SLOTS = new int[]{9};

    public static final int BURN_TIME_PROPERTY_INDEX = 0;
    public static final int FUEL_TIME_PROPERTY_INDEX = 1;
    public static final int BREAK_TIME_PROPERTY_INDEX = 2;
    public static final int BREAK_TIME_TOTAL_PROPERTY_INDEX = 3;
    public static final int PROPERTY_COUNT = 4;
    public static final int DEFAULT_BREAK_TIME = 100;
    public static final float FUEL_MULTIPLIER = 1f;
    int burnTime;
    int fuelTime;
    int breakTime;
    int breakTimeTotal = DEFAULT_BREAK_TIME;

    protected final PropertyDelegate propertyDelegate = new PropertyDelegate(){

        @Override
        public int get(int index) {
            switch (index) {
                case BURN_TIME_PROPERTY_INDEX: {
                    return burnTime;
                }
                case FUEL_TIME_PROPERTY_INDEX: {
                    return fuelTime;
                }
                case BREAK_TIME_PROPERTY_INDEX: {
                    return breakTime;
                }
                case BREAK_TIME_TOTAL_PROPERTY_INDEX: {
                    return breakTimeTotal;
                }
            }
            return 0;
        }

        @Override
        public void set(int index, int value) {
            switch (index) {
                case BURN_TIME_PROPERTY_INDEX: {
                    burnTime = value;
                    break;
                }
                case FUEL_TIME_PROPERTY_INDEX: {
                    fuelTime = value;
                    break;
                }
                case BREAK_TIME_PROPERTY_INDEX: {
                    breakTime = value;
                    break;
                }
                case BREAK_TIME_TOTAL_PROPERTY_INDEX: {
                    breakTimeTotal = value;
                    break;
                }
            }
        }

        @Override
        public int size() {
            return PROPERTY_COUNT;
        }
    };

    public IgneousMinerBlockEntity(BlockPos pos, BlockState state) {
        super(IgneousMachinesMod.IGNEOUS_MINER_BLOCK_ENTITY, pos, state);
    }

    @Override
    public DefaultedList<ItemStack> getItems() {
        return inventory;
    }

    private boolean isBurning() {
        return this.burnTime > 0;
    }

    protected int getFuelTime(ItemStack fuel) {
        if (fuel.isEmpty()) {
            return 0;
        }
        Item item = fuel.getItem();
        return (int) (AbstractFurnaceBlockEntity.createFuelTimeMap().getOrDefault(item, 0) * FUEL_MULTIPLIER);
    }

    private static int getBreakTime() {
        return DEFAULT_BREAK_TIME;
    }

    @Override
    public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
        //We provide *this* to the screenHandler as our class Implements Inventory
        //Only the Server has the Inventory at the start, this will be synced to the client in the ScreenHandler
        return new IgneousMinerScreenHandler(syncId, playerInventory, this, propertyDelegate);
    }

    @Override
    public Text getDisplayName() {
        // for 1.19+
        return Text.translatable(getCachedState().getBlock().getTranslationKey());
        // for earlier versions
        // return new TranslatableText(getCachedState().getBlock().getTranslationKey());
    }

    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
        Inventories.readNbt(nbt, this.inventory);
        this.burnTime = nbt.getShort("BurnTime");
        this.breakTime = nbt.getShort("BreakTime");
        this.breakTimeTotal = nbt.getShort("BreakTimeTotal");
        this.fuelTime = this.getFuelTime(this.inventory.get(9));
    }

    @Override
    public void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
        nbt.putShort("BurnTime", (short)this.burnTime);
        nbt.putShort("BreakTime", (short)this.breakTime);
        nbt.putShort("BreakTimeTotal", (short)this.breakTimeTotal);
        Inventories.writeNbt(nbt, this.inventory);
    }

    public static void tick(World world, BlockPos pos, BlockState state, IgneousMinerBlockEntity blockEntity) {
        ServerWorld serverWorld = (ServerWorld) world;
        boolean hasFuel;
        boolean wasBurning = blockEntity.isBurning();
        boolean stateChanged = false;
        if (blockEntity.isBurning()) {
            --blockEntity.burnTime;
        }
        ItemStack fuelStack = blockEntity.inventory.get(9);
        hasFuel = !fuelStack.isEmpty();
        if (blockEntity.isBurning() || hasFuel) {
            BlockPointerImpl pointer = new BlockPointerImpl(serverWorld, pos);
            Direction direction = pointer.getBlockState().get(IgneousMinerBlock.FACING);
            BlockPos blockPos = pointer.getPos().offset(direction);
            if (!blockEntity.isBurning() && canAcceptBlockOutput(serverWorld, blockPos, blockEntity.inventory)) {
                blockEntity.fuelTime = blockEntity.burnTime = blockEntity.getFuelTime(fuelStack);
                if (blockEntity.isBurning()) {
                    stateChanged = true;
                    if (hasFuel) {
                        Item item = fuelStack.getItem();
                        fuelStack.decrement(1);
                        if (fuelStack.isEmpty()) {
                            Item item2 = item.getRecipeRemainder();
                            blockEntity.inventory.set(9, item2 == null ? ItemStack.EMPTY : new ItemStack(item2));
                        }
                    }
                }
            }
            if (blockEntity.isBurning() && canAcceptBlockOutput(serverWorld, blockPos, blockEntity.inventory)) {
                ++blockEntity.breakTime;
                if (blockEntity.breakTime == blockEntity.breakTimeTotal) {
                    blockEntity.breakTime = 0;
                    blockEntity.breakTimeTotal = getBreakTime();
                    collectBlock(serverWorld, blockPos, blockEntity.inventory);
                    stateChanged = true;
                }
            } else {
                blockEntity.breakTime = 0;
            }
        } else if (!blockEntity.isBurning() && blockEntity.breakTime > 0) {
            blockEntity.breakTime = MathHelper.clamp(blockEntity.breakTime - 2, 0, blockEntity.breakTimeTotal);
        }
        if (wasBurning != blockEntity.isBurning()) {
            stateChanged = true;
            state = state.with(IgneousCrafterBlock.LIT, blockEntity.isBurning());
            world.setBlockState(pos, state, Block.NOTIFY_ALL);
        }
        if (stateChanged) {
            markDirty(world, pos, state);
        }
    }


    private static boolean canAcceptBlockOutput(ServerWorld world, BlockPos blockPos, DefaultedList<ItemStack> slots) {

        var blockState = world.getBlockState(blockPos);

        if (blockState == null || blockState.isAir()) {
            return false;
        }

        LootContextParameterSet.Builder builder = new LootContextParameterSet.Builder(world).add(LootContextParameters.ORIGIN, Vec3d.ofCenter(blockPos)).add(LootContextParameters.TOOL, ItemStack.EMPTY).addOptional(LootContextParameters.BLOCK_ENTITY, null);
        List<ItemStack> resultStacks = blockState.getDroppedStacks(builder);

        for (ItemStack resultStack : resultStacks) {
            ItemStack outputStack = getFirstAvailable(resultStack, slots);
            if (outputStack == null) {
                return false;
            }
        }
        return true;
    }

    private static boolean collectBlock(ServerWorld world, BlockPos blockPos, DefaultedList<ItemStack> slots) {
        if (blockPos == null || !canAcceptBlockOutput(world, blockPos, slots)) {
            return false;
        }

        var blockState = world.getBlockState(blockPos);

        LootContextParameterSet.Builder builder = new LootContextParameterSet.Builder(world).add(LootContextParameters.ORIGIN, Vec3d.ofCenter(blockPos)).add(LootContextParameters.TOOL, ItemStack.EMPTY).addOptional(LootContextParameters.BLOCK_ENTITY, null);
        List<ItemStack> resultStacks = blockState.getDroppedStacks(builder);

        for (ItemStack resultStack : resultStacks) {
            ItemStack outputStack = getFirstAvailable(resultStack, slots);
            if (outputStack == null) {
                return false;
            }
            outputStack.increment(resultStack.getCount());
            if (outputStack.isEmpty()) {
                slots.set(slots.indexOf(outputStack), resultStack.copy());
            } else if (outputStack.isOf(resultStack.getItem())) {
                outputStack.increment(resultStack.getCount());
            }
        }

        world.breakBlock(blockPos, false);

        return true;
    }

    private static ItemStack getFirstAvailable(ItemStack lookingToInsert, DefaultedList<ItemStack> slots) {
        for (int i = 0; i < 9; i++) {
            var currentStack = slots.get(i);
            if (currentStack.isEmpty()) {
                return currentStack;
            } else if (currentStack.isOf(lookingToInsert.getItem())) {
                if (currentStack.getCount() + lookingToInsert.getCount() <= lookingToInsert.getMaxCount()) {
                    return currentStack;
                }
            }
        }
        return null;
    }

    @Override
    public int[] getAvailableSlots(Direction side) {
        if (side == Direction.DOWN) {
            return BOTTOM_SLOTS;
        }
        if (side == Direction.UP) {
            return TOP_SLOTS;
        }
        return SIDE_SLOTS;
    }

    @Override
    public boolean canInsert(int slot, ItemStack stack, @Nullable Direction dir) {
        if (slot == 9) {
            return ItemStackUtils.isFuel(stack);
        }
        else if (slot == 10) {
            return ItemStackUtils.isTool(stack);
        }

        return false;
    }

    @Override
    public boolean canExtract(int slot, ItemStack stack, Direction dir) {
        if (slot < 9) {
            return true;
        }

        return false;
    }
}
