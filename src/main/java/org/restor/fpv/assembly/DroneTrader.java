package org.restor.fpv.assembly;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

/**
 * Trader-firmware brain: the drone answers a right-click with a wandering trader's
 * offers. Offers are rolled once per drone and kept in its NBT, so a courier build
 * keeps the stock it flew in with.
 */
public class DroneTrader implements Merchant {
    private static final int OFFER_COUNT = 5;

    private final Entity host;
    private MerchantOffers offers;
    private Player tradingPlayer;

    public DroneTrader(Entity host) {
        this.host = host;
    }

    @Override
    public MerchantOffers getOffers() {
        if (offers == null) offers = roll();
        return offers;
    }

    private MerchantOffers roll() {
        MerchantOffers result = new MerchantOffers();
        RandomSource random = host.level().getRandom();
        for (int tier : new int[]{1, 2}) {
            VillagerTrades.ItemListing[] pool = VillagerTrades.WANDERING_TRADER_TRADES.get(tier);
            if (pool == null || pool.length == 0) continue;
            for (int i = 0; i < OFFER_COUNT && result.size() < OFFER_COUNT; i++) {
                MerchantOffer offer = pool[random.nextInt(pool.length)].getOffer(host, random);
                if (offer != null) result.add(offer);
            }
        }
        return result;
    }

    public void save(CompoundTag tag) {
        if (offers == null) return;
        MerchantOffers.CODEC.encodeStart(ops(), offers).result().ifPresent(t -> tag.put("Offers", t));
    }

    public void load(CompoundTag tag) {
        if (!tag.contains("Offers")) return;
        offers = MerchantOffers.CODEC.parse(ops(), tag.get("Offers"))
            .resultOrPartial(err -> {})
            .orElse(null);
    }

    private com.mojang.serialization.DynamicOps<net.minecraft.nbt.Tag> ops() {
        return host.level().registryAccess().createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE);
    }

    @Override public void setTradingPlayer(Player player) { this.tradingPlayer = player; }
    @Override public Player getTradingPlayer() { return tradingPlayer; }
    @Override public void overrideOffers(MerchantOffers o) { this.offers = o; }
    @Override public void overrideXp(int xp) {}
    @Override public int getVillagerXp() { return 0; }
    @Override public boolean showProgressBar() { return false; }
    @Override public boolean isClientSide() { return host.level().isClientSide; }
    @Override public SoundEvent getNotifyTradeSound() { return SoundEvents.WANDERING_TRADER_YES; }

    @Override
    public void notifyTrade(MerchantOffer offer) {
        offer.increaseUses();
        host.level().playSound(null, host.blockPosition(), getNotifyTradeSound(),
            host.getSoundSource(), 1.0f, 1.4f);
    }

    @Override
    public void notifyTradeUpdated(ItemStack stack) {
        if (host.level().isClientSide || stack.isEmpty()) return;
        host.level().playSound(null, host.blockPosition(), SoundEvents.WANDERING_TRADER_YES,
            host.getSoundSource(), 0.6f, 1.6f);
    }
}
