package org.restor.fpv.component;

import net.minecraft.world.item.Item;
// Frame component: its FrameSpec (assembly.FrameSpecs) defines the motor count and
// where every component mounts. Extend here for frame-specific behavior.
public class FrameItem extends ComponentItem {
    public FrameItem(Item.Properties props, ComponentStats stats) {
        super(props, stats);
    }
}
