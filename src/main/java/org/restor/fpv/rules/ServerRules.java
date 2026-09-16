package org.restor.fpv.rules;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.restor.fpv.CommonConfig;
import org.restor.fpv.Fpv;
import net.neoforged.neoforge.network.PacketDistributor;
import org.restor.fpv.network.ServerRulesPacket;

import java.util.LinkedHashMap;
import java.util.Map;

@EventBusSubscriber(modid = Fpv.MODID)
public final class ServerRules {
    private ServerRules() {}

    public static boolean canEdit(ServerPlayer player) {
        return player.hasPermissions(2) || player.server.isSingleplayerOwner(player.getGameProfile());
    }

    public static void apply(Map<ServerRule, Double> changes) {
        apply(changes, Map.of());
    }

    public static void apply(Map<ServerRule, Double> rules, Map<ExplosiveParam, Double> explosives) {
        if (rules.isEmpty() && explosives.isEmpty()) return;
        if (!rules.isEmpty()) {
            rules.forEach(ServerRule::setLocal);
            CommonConfig.SPEC.save();
        }
        if (!explosives.isEmpty()) {
            explosives.forEach(ExplosiveParam::setLocal);
            ExplosivesConfig.SPEC.save();
        }
        broadcast();
    }

    public static void broadcast() {
        if (ServerLifecycleHooks.getCurrentServer() == null) return;
        PacketDistributor.sendToAllPlayers(snapshot());
    }

    private static ServerRulesPacket snapshot() {
        return new ServerRulesPacket(ServerRule.localValues(), ExplosiveParam.localValues());
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PacketDistributor.sendToPlayer(player, snapshot());
        }
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> rules = Commands.literal("rules")
            .executes(ctx -> list(ctx.getSource()))
            .then(Commands.literal("reset").executes(ctx -> resetAll(ctx.getSource())));

        for (ServerRule rule : ServerRule.values()) {
            LiteralArgumentBuilder<CommandSourceStack> node = Commands.literal(rule.id)
                .executes(ctx -> show(ctx.getSource(), rule))
                .then(Commands.literal("reset").executes(ctx -> set(ctx.getSource(), rule, rule.defaultValue(), true)));
            if (rule.flag()) {
                node.then(Commands.argument("value", BoolArgumentType.bool())
                    .executes(ctx -> set(ctx.getSource(), rule, BoolArgumentType.getBool(ctx, "value") ? 1 : 0, false)));
            } else {
                node.then(Commands.argument("value", DoubleArgumentType.doubleArg(rule.min, rule.max))
                    .executes(ctx -> set(ctx.getSource(), rule, DoubleArgumentType.getDouble(ctx, "value"), false)));
            }
            rules.then(node);
        }

        dispatcher.register(Commands.literal("fpv")
            .requires(src -> src.hasPermission(2))
            .then(rules)
            .then(explosivesCommand()));
    }

    private static int list(CommandSourceStack source) {
        source.sendSuccess(() -> Component.translatable("commands.fpv.rules.header").withStyle(ChatFormatting.GOLD), false);
        for (ServerRule rule : ServerRule.values()) {
            MutableComponent line = Component.literal("  " + rule.id + ": ").withStyle(ChatFormatting.GRAY)
                .append(valueText(rule.flag(), rule.format(rule.local()), rule.local()))
                .withStyle(style -> style
                    .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/fpv rules " + rule.id + " "))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        Component.translatable("commands.fpv.rules.hover", rule.format(rule.defaultValue()), range(rule)))));
            source.sendSuccess(() -> line, false);
        }
        return ServerRule.values().length;
    }

    private static int show(CommandSourceStack source, ServerRule rule) {
        source.sendSuccess(() -> Component.translatable("commands.fpv.rules.show",
            rule.id, valueText(rule.flag(), rule.format(rule.local()), rule.local()),
            rule.format(rule.defaultValue()), range(rule)), false);
        return 1;
    }

    private static int set(CommandSourceStack source, ServerRule rule, double value, boolean reset) {
        apply(Map.of(rule, value));
        String key = reset ? "commands.fpv.rules.reset" : "commands.fpv.rules.set";
        source.sendSuccess(() -> Component.translatable(key, rule.id,
            valueText(rule.flag(), rule.format(rule.local()), rule.local())), true);
        return 1;
    }

    private static int resetAll(CommandSourceStack source) {
        Map<ServerRule, Double> defaults = new java.util.EnumMap<>(ServerRule.class);
        for (ServerRule rule : ServerRule.values()) defaults.put(rule, rule.defaultValue());
        apply(defaults);
        source.sendSuccess(() -> Component.translatable("commands.fpv.rules.reset_all"), true);
        return defaults.size();
    }

    private static LiteralArgumentBuilder<CommandSourceStack> explosivesCommand() {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("explosives")
            .executes(ctx -> listExplosives(ctx.getSource()))
            .then(Commands.literal("reset").executes(ctx -> resetExplosives(ctx.getSource(), null)));

        for (Explosive explosive : Explosive.values()) {
            LiteralArgumentBuilder<CommandSourceStack> node = Commands.literal(explosive.id)
                .executes(ctx -> showExplosive(ctx.getSource(), explosive))
                .then(Commands.literal("reset").executes(ctx -> resetExplosives(ctx.getSource(), explosive)));
            for (ExplosiveParam param : explosive.params()) {
                LiteralArgumentBuilder<CommandSourceStack> p = Commands.literal(param.stat.id)
                    .executes(ctx -> showParam(ctx.getSource(), param))
                    .then(Commands.literal("reset").executes(ctx -> setParam(ctx.getSource(), param, param.defaultValue(), true)));
                if (param.flag()) {
                    p.then(Commands.argument("value", BoolArgumentType.bool())
                        .executes(ctx -> setParam(ctx.getSource(), param, BoolArgumentType.getBool(ctx, "value") ? 1 : 0, false)));
                } else {
                    p.then(Commands.argument("value", DoubleArgumentType.doubleArg(param.min, param.max))
                        .executes(ctx -> setParam(ctx.getSource(), param, DoubleArgumentType.getDouble(ctx, "value"), false)));
                }
                node.then(p);
            }
            root.then(node);
        }
        return root;
    }

    private static int listExplosives(CommandSourceStack source) {
        source.sendSuccess(() -> Component.translatable("commands.fpv.explosives.header").withStyle(ChatFormatting.GOLD), false);
        for (Explosive explosive : Explosive.values()) {
            MutableComponent line = Component.literal("  " + explosive.id).withStyle(ChatFormatting.AQUA);
            MutableComponent summary = Component.literal(" ");
            for (ExplosiveParam param : explosive.params()) {
                summary.append(Component.literal(param.stat.id + "=").withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.literal(param.format(param.local()) + " ").withStyle(ChatFormatting.GRAY));
            }
            MutableComponent full = line.append(summary).withStyle(style -> style
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/fpv explosives " + explosive.id))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                    Component.translatable("commands.fpv.explosives.hover"))));
            source.sendSuccess(() -> full, false);
        }
        return Explosive.values().length;
    }

    private static int showExplosive(CommandSourceStack source, Explosive explosive) {
        source.sendSuccess(() -> Component.translatable("commands.fpv.explosives.params", explosive.id)
            .withStyle(ChatFormatting.GOLD), false);
        int count = 0;
        for (ExplosiveParam param : explosive.params()) {
            MutableComponent line = Component.literal("  " + param.stat.id + ": ").withStyle(ChatFormatting.GRAY)
                .append(valueText(param.flag(), param.format(param.local()), param.local()))
                .withStyle(style -> style
                    .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND,
                        "/fpv explosives " + explosive.id + " " + param.stat.id + " "))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        Component.literal(param.stat.comment + "\n")
                            .append(Component.translatable("commands.fpv.rules.hover",
                                param.format(param.defaultValue()), param.range())))));
            source.sendSuccess(() -> line, false);
            count++;
        }
        return count;
    }

    private static int showParam(CommandSourceStack source, ExplosiveParam param) {
        source.sendSuccess(() -> Component.translatable("commands.fpv.explosives.show",
            param.id(), valueText(param.flag(), param.format(param.local()), param.local()),
            param.format(param.defaultValue()), param.range()), false);
        return 1;
    }

    private static int setParam(CommandSourceStack source, ExplosiveParam param, double value, boolean reset) {
        apply(Map.of(), Map.of(param, value));
        String key = reset ? "commands.fpv.explosives.reset" : "commands.fpv.explosives.set";
        source.sendSuccess(() -> Component.translatable(key, param.id(),
            valueText(param.flag(), param.format(param.local()), param.local())), true);
        return 1;
    }

    private static int resetExplosives(CommandSourceStack source, Explosive only) {
        Map<ExplosiveParam, Double> defaults = new LinkedHashMap<>();
        for (ExplosiveParam param : ExplosiveParam.all()) {
            if (only == null || param.owner() == only) defaults.put(param, param.defaultValue());
        }
        apply(Map.of(), defaults);
        if (only == null) {
            source.sendSuccess(() -> Component.translatable("commands.fpv.explosives.reset_all"), true);
        } else {
            source.sendSuccess(() -> Component.translatable("commands.fpv.explosives.reset_one", only.id), true);
        }
        return defaults.size();
    }

    private static Component valueText(boolean flag, String text, double value) {
        ChatFormatting color = flag ? (value >= 0.5 ? ChatFormatting.GREEN : ChatFormatting.RED) : ChatFormatting.AQUA;
        return Component.literal(text).withStyle(color);
    }

    private static String range(ServerRule rule) {
        return rule.flag() ? "true / false" : rule.format(rule.min) + " .. " + rule.format(rule.max);
    }

    @EventBusSubscriber(modid = Fpv.MODID, bus = EventBusSubscriber.Bus.MOD)
    public static final class ConfigListener {
        @SubscribeEvent
        public static void onReload(ModConfigEvent.Reloading event) {
            var spec = event.getConfig().getSpec();
            if (spec != CommonConfig.SPEC && spec != ExplosivesConfig.SPEC) return;
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server != null) server.execute(ServerRules::broadcast);
        }
    }
}
