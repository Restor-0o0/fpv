package org.restor.fpv.component;

// Frame component: its FrameSpec (assembly.FrameSpecs) defines the motor count and
// where every component mounts. Extend here for frame-specific behavior.
public class FrameItem extends ComponentItem {
    public FrameItem(ComponentStats stats) {
        super(stats);
    }
}
