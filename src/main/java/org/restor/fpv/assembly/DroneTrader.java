package org.restor.fpv.assembly;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Unit;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.item.trading.TradeSet;
import net.minecraft.world.item.trading.TradeSets;
import net.minecraft.world.item.trading.VillagerTrade;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

import java.util.ArrayList;
import java.util.List;

/**
 * Trader-firmware brain: the drone answers a right-click with a wandering trader's
 * offers. Offers are rolled once per drone and kept in its NBT, so a courier build
 * keeps the stock it flew in with. Stock is finite — once every offer is sold out
 * the courier leaves, the same way a wandering trader packs up.
 */
public class DroneTrader implements Merchant {
    private static final int OFFER_COUNT = 8;
    private static final int USES_PER_OFFER = 3;
    private static final List<ResourceKey<TradeSet>> TIERS =
        List.of(TradeSets.WANDERING_TRADER_BUYING, TradeSets.WANDERING_TRADER_UNCOMMON, TradeSets.WANDERING_TRADER_COMMON);

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
        if (!(host.level() instanceof ServerLevel level)) return result;
        var registry = level.registryAccess().lookupOrThrow(Registries.TRADE_SET);
        LootContext context = new LootContext.Builder(new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, host.position())
                .withParameter(LootContextParams.THIS_ENTITY, host)
                .withParameter(LootContextParams.ADDITIONAL_COST_COMPONENT_ALLOWED, Unit.INSTANCE)
                .create(LootContextParamSets.VILLAGER_TRADE))
            .create(java.util.Optional.empty());
        for (ResourceKey<TradeSet> key : TIERS) {
            TradeSet set = registry.getOptional(key).orElse(null);
            if (set == null) continue;
            List<Holder<VillagerTrade>> pool = new ArrayList<>();
            set.getTrades().forEach(pool::add);
            if (pool.isEmpty()) continue;
            for (int i = 0; i < OFFER_COUNT * 2 && result.size() < OFFER_COUNT; i++) {
                MerchantOffer offer = pool.get(context.getRandom().nextInt(pool.size())).value().getOffer(context);
                if (offer == null || containsSame(result, offer)) continue;
                result.add(new MerchantOffer(offer.getItemCostA(), offer.getItemCostB(), offer.getResult(),
                    0, USES_PER_OFFER, offer.getXp(), offer.getPriceMultiplier()));
            }
        }
        return result;
    }

    private static boolean containsSame(MerchantOffers list, MerchantOffer candidate) {
        for (MerchantOffer o : list) {
            if (ItemStack.isSameItemSameComponents(o.getResult(), candidate.getResult())) return true;
        }
        return false;
    }

    /** True once every rolled offer is sold out — the courier's cue to leave. */
    public boolean isSoldOut() {
        if (offers == null || offers.isEmpty()) return false;
        for (MerchantOffer o : offers) {
            if (!o.isOutOfStock()) return false;
        }
        return true;
    }

    public void save(ValueOutput output) {
        output.storeNullable("Offers", MerchantOffers.CODEC, offers);
    }

    public void load(ValueInput input) {
        input.read("Offers", MerchantOffers.CODEC).ifPresent(o -> offers = o);
    }



    @Override public void setTradingPlayer(Player player) { this.tradingPlayer = player; }
    @Override public Player getTradingPlayer() { return tradingPlayer; }
    @Override public boolean stillValid(Player player) { return host.isAlive() && player.distanceToSqr(host) < 64.0; }
    @Override public void overrideOffers(MerchantOffers o) { this.offers = o; }
    @Override public void overrideXp(int xp) {}
    @Override public int getVillagerXp() { return 0; }
    @Override public boolean showProgressBar() { return false; }
    @Override public boolean isClientSide() { return host.level().isClientSide(); }
    @Override public SoundEvent getNotifyTradeSound() { return SoundEvents.WANDERING_TRADER_YES; }

    @Override
    public void notifyTrade(MerchantOffer offer) {
        offer.increaseUses();
        host.level().playSound(null, host.blockPosition(), getNotifyTradeSound(),
            host.getSoundSource(), 1.0f, 1.4f);
    }

    @Override
    public void notifyTradeUpdated(ItemStack stack) {
        if (host.level().isClientSide() || stack.isEmpty()) return;
        host.level().playSound(null, host.blockPosition(), SoundEvents.WANDERING_TRADER_YES,
            host.getSoundSource(), 0.6f, 1.6f);
    }
}
