package org.restor.fpv.antidrone;

import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

public interface RadioInterferenceReceiver {
    void applyRadioInterference(long sourceId, float strength, int durationTicks);

    default ServerPlayer radioOperator() { return null; }

    default UUID radioOwnerId() { return null; }

    default int radioChannel() { return org.restor.fpv.component.RadioChannel.DEFAULT; }
}
