package org.restor.fpv.util;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class ListenableContainer extends SimpleContainer {
    private final List<Consumer<Container>> listeners = new ArrayList<>();

    public ListenableContainer(int size) {
        super(size);
    }

    public void addListener(Consumer<Container> listener) {
        listeners.add(listener);
    }

    public void removeListener(Consumer<Container> listener) {
        listeners.remove(listener);
    }

    @Override
    public void setChanged() {
        super.setChanged();
        for (Consumer<Container> listener : List.copyOf(listeners)) listener.accept(this);
    }
}
