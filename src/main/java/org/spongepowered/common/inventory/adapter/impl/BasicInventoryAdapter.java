/*
 * This file is part of Sponge, licensed under the MIT License (MIT).
 *
 * Copyright (c) SpongePowered <https://www.spongepowered.org>
 * Copyright (c) contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.spongepowered.common.inventory.adapter.impl;

import static com.google.common.base.Preconditions.checkNotNull;

import net.minecraft.item.ItemStack;
import org.spongepowered.api.item.inventory.Inventory;
import org.spongepowered.api.item.inventory.Slot;
import org.spongepowered.api.plugin.PluginContainer;
import org.spongepowered.api.text.translation.Translation;
import org.spongepowered.common.bridge.inventory.InventoryBridge;
import org.spongepowered.common.bridge.inventory.LensProviderBridge;
import org.spongepowered.common.inventory.adapter.InventoryAdapter;
import org.spongepowered.common.inventory.fabric.Fabric;
import org.spongepowered.common.inventory.lens.Lens;
import org.spongepowered.common.inventory.lens.impl.DefaultEmptyLens;
import org.spongepowered.common.inventory.lens.impl.DefaultIndexedLens;
import org.spongepowered.common.inventory.lens.impl.ReusableLens;
import org.spongepowered.common.inventory.lens.slots.SlotLens;
import org.spongepowered.common.inventory.lens.impl.collections.SlotLensCollection;
import org.spongepowered.common.inventory.lens.impl.slot.SlotLensProvider;
import org.spongepowered.common.inventory.lens.slots.SlotLens;
import org.spongepowered.common.text.translation.SpongeTranslation;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.annotation.Nullable;

/**
 * Base Adapter implementation for {@link ItemStack} based Inventories.
 */
public class BasicInventoryAdapter implements InventoryAdapter, DefaultImplementedAdapterInventory, InventoryBridge, Inventory {

    public static final Translation DEFAULT_NAME = new SpongeTranslation("inventory.default.title");

    private final Fabric fabric;
    protected final SlotLensProvider slotLenses;
    protected final Lens lens;
    protected final List<Inventory> children = new ArrayList<>();

    protected Inventory parent;
    @Nullable protected Inventory next;
    @Nullable private Iterable<Slot> slotIterator;

    public BasicInventoryAdapter(final Fabric fabric) {
        this(fabric, null, null);
    }

    @Override
    public Inventory root() {
        return this.parent() == this ? this : this.parent().root();
    }

    @SuppressWarnings("unchecked")
    public <T extends Lens> BasicInventoryAdapter(final Fabric fabric, final Class<T> lensType) {
        this.fabric = fabric;
        this.parent = this;
        if (fabric.fabric$getSize() == 0) {
            this.slotLenses = new SlotLensCollection(0);
            this.lens = new DefaultEmptyLens(this);
        } else {
            final ReusableLens<T> lens = ReusableLens.getLens(lensType, this, () -> this.initSlots(fabric, this.parent),
                    (slots) -> (T) new DefaultIndexedLens(0, fabric.fabric$getSize(), this.getClass(), slots));
            this.slotLenses = lens.getSlots();
            this.lens = lens.getLens();
        }
    }

    public BasicInventoryAdapter(final Fabric fabric, @Nullable final Lens root, @Nullable final Inventory parent) {
        this.fabric = fabric;
        this.parent = parent == null ? this : parent;
        this.slotLenses = this.initSlots(fabric, parent);
        this.lens = root != null ? root : checkNotNull(this.initRootLens(), "root lens");
    }

    // Constructs inventory with given list of inventories
    // TODO check if this is correct
    public BasicInventoryAdapter(Fabric fabric, List<Inventory> children, Inventory parent) {
        this.fabric = fabric;
        this.parent = parent == null ? this : parent;
        this.slotLenses = this.initSlots(fabric, parent);

        this.lens = new QueryLens(
                children.stream()
                        .map(InventoryBridge.class::cast)
                        .map(InventoryBridge::bridge$getAdapter)
                        .map(InventoryAdapter::bridge$getRootLens).collect(Collectors.toList()));
        this.children = children; // Init cached children
    }

    private SlotLensProvider initSlots(final Fabric fabric, @Nullable final Inventory parent) {
        if (parent instanceof InventoryAdapter) {
            return ((InventoryAdapter) parent).bridge$getSlotProvider();
        }
        return new SlotLensCollection(fabric.fabric$getSize());
    }

    @Override
    public Inventory parent() {
        return this.parent;
    }

    protected Lens initRootLens() {
        if (this instanceof LensProviderBridge) {
            return ((LensProviderBridge) this).bridge$rootLens(this.fabric, this);
        }
        final int size = this.fabric.fabric$getSize();
        if (size == 0) {
            return new DefaultEmptyLens(this);
        }
        return new DefaultIndexedLens(0, size, this.getClass(), this.slots);
    }

    @Override
    public SlotLensProvider bridge$getSlotProvider() {
        return this.slotLenses;
    }

    @Override
    public Lens bridge$getRootLens() {
        return this.lens;
    }

    @Override
    public Fabric bridge$getFabric() {
        return this.fabric;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T extends Inventory> Iterable<T> slots() {
        if (this.slotIterator == null) {
            this.slotIterator = SlotCollection.of(this, this);
        }
        return (Iterable<T>) this.slotIterator;
    }

    public static Optional<Slot> forSlot(final Fabric fabric, final SlotLens slotLens, final Inventory parent) {
        return slotLens == null ? Optional.empty() : Optional.ofNullable((Slot) slotLens.getAdapter(fabric, parent));
    }

    @Override
    public void clear() {
        // TODO clear without generating SlotAdapters
        this.slots().forEach(Inventory::clear);
    }

    @Override
    public PluginContainer getPlugin() {
        if (this.parent != this) {
            return this.parent.getPlugin();
        }
        return null; // TODO - this should never return null.
    }
}
