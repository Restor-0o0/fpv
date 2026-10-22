package org.restor.fpv.client.ui.screen.settings;

import com.mojang.blaze3d.platform.InputConstants;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.restor.fpv.Config;
import org.restor.fpv.client.LineOfSightSession;
import org.restor.fpv.client.ModKeyBindings;
import net.neoforged.neoforge.network.PacketDistributor;
import org.restor.fpv.controller.RCController;
import org.restor.fpv.entity.DroneEntity;
import org.restor.fpv.network.DroneRatesPacket;
import org.restor.fpv.network.SetServerRulesPacket;
import org.restor.fpv.rules.Explosive;
import org.restor.fpv.rules.ExplosiveParam;
import org.restor.fpv.rules.ServerRule;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class FpvSettingsScreen extends Screen {
    private static final int SIDEBAR_WIDE = 128;
    private static final int SIDEBAR_NARROW = 36;
    private static final int TAB_HEIGHT = 24;
    private static final int HEADER_HEIGHT = 46;

    private static Tab lastTab = Tab.CONTROLS;

    private final Screen parent;
    private final List<SettingRow> rows = new ArrayList<>();
    private Tab tab;
    private SettingRow listening;
    private SettingRow pressed;
    private static final long TOOLTIP_DELAY_MS = 1500;
    private static final int TOOLTIP_WIDTH = 220;
    private SettingRow tipRow;
    private long tipSince;
    private double scroll;
    private double scrollTarget;
    private boolean draggingScrollbar;
    private double scrollbarGrab;
    private float openAnim;
    private double[] rules = ServerRule.currentValues();
    private final boolean[] dirtyRules = new boolean[ServerRule.values().length];
    private double[] explosives = ExplosiveParam.currentValues();
    private final boolean[] dirtyExplosives = new boolean[ExplosiveParam.all().size()];

    private int wx, wy, ww, wh, sidebar;

    public FpvSettingsScreen(Screen parent) {
        super(Component.translatable("screen.fpv.settings"));
        this.parent = parent;
        this.tab = lastTab;
    }

    @Override
    protected void init() {
        ww = Math.min(width - 24, 600);
        wh = Math.min(height - 24, 380);
        wx = (width - ww) / 2;
        wy = (height - wh) / 2;
        sidebar = ww < 440 ? SIDEBAR_NARROW : SIDEBAR_WIDE;
        if (tab == Tab.CREATIVE && !creativeAllowed()) tab = Tab.CONTROLS;
        buildRows();
    }

    private boolean creativeAllowed() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && mc.player.isCreative() && (mc.hasSingleplayerServer() || mc.player.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER));
    }

    private List<Tab> visibleTabs() {
        List<Tab> tabs = new ArrayList<>();
        for (Tab t : Tab.values()) {
            if (t != Tab.CREATIVE || creativeAllowed()) tabs.add(t);
        }
        return tabs;
    }

    private void selectTab(Tab next) {
        if (next == tab) return;
        stopListening();
        tab = next;
        lastTab = next;
        scroll = scrollTarget = 0;
        buildRows();
        SettingRow.click();
    }

    private void buildRows() {
        rows.clear();
        switch (tab) {
            case CONTROLS -> buildControls();
            case KEYS -> buildKeys();
            case HUD -> buildHud();
            case CREATIVE -> buildCreative();
        }
        clampScroll();
    }

    private static Component tr(String key) {
        return Component.translatable("settings.fpv." + key);
    }

    private static Component hint(String key) {
        return Component.translatable("settings.fpv." + key + ".hint");
    }

    private void section(String key) {
        rows.add(new SettingRow.Section(tr("section." + key), null));
    }

    private void section(String key, boolean withHint) {
        rows.add(new SettingRow.Section(tr("section." + key), withHint ? hint("section." + key) : null));
    }

    private void toggle(String key, boolean withHint, ModConfigSpec.BooleanValue value) {
        rows.add(new SettingRow.Toggle(tr(key), withHint ? hint(key) : null, value::get, v -> {
            value.set(v);
            Config.save();
        }));
    }

    private void slider(String key, boolean withHint, ModConfigSpec.DoubleValue value, double min, double max,
                        double step, SettingRow.Slider.Format format) {
        rows.add(new SettingRow.Slider(tr(key), withHint ? hint(key) : null, min, max, step, value::get, value::set, format));
    }

    private static SettingRow.Slider.Format decimals(int places) {
        String pattern = "%." + places + "f";
        return v -> String.format(java.util.Locale.ROOT, pattern, v);
    }

    private static SettingRow.Slider.Format multiplier() {
        return v -> String.format(java.util.Locale.ROOT, "%.2fx", v);
    }

    private static SettingRow.Slider.Format percent(double scale) {
        return v -> Math.round(v * scale) + "%";
    }

    private static DroneEntity controlledDrone() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return null;
        if (mc.player.getVehicle() instanceof DroneEntity d) return d;
        if (mc.getCameraEntity() instanceof DroneEntity d) return d;
        return LineOfSightSession.drone();
    }

    private void buildControls() {
        DroneEntity drone = controlledDrone();
        section("rates", true);
        rate("rc_pitch_rate", Config.RC_PITCH_RATE, 0, drone);
        rate("rc_roll_rate", Config.RC_ROLL_RATE, 1, drone);
        rate("rc_yaw_rate", Config.RC_YAW_RATE, 2, drone);
        slider("rc_expo", true, Config.RC_EXPO, 0.0, 0.8, 0.01, decimals(2));

        section("mouse");
        slider("mouse_pitch", false, Config.MOUSE_PITCH_SENSITIVITY, 0.005, 0.2, 0.005, decimals(3));
        slider("mouse_roll", false, Config.MOUSE_ROLL_SENSITIVITY, 0.005, 0.2, 0.005, decimals(3));
        slider("mouse_smoothing", true, Config.MOUSE_SMOOTHING, 0.0, 0.85, 0.01, percent(100));
        slider("mouse_return", true, Config.MOUSE_RETURN, 0.0, 1.0, 0.01, percent(100));
        slider("mouse_deadband", false, Config.MOUSE_DEADBAND, 0.0, 0.2, 0.005, percent(100));
        slider("keyboard_yaw", true, Config.KEYBOARD_YAW_SENSITIVITY, 0.05, 1.0, 0.01, decimals(2));

        section("radio", true);
        rows.add(new ControllerStatusRow());
        axis("axis_throttle", Config.RC_AXIS_THROTTLE, Config.RC_INVERT_THROTTLE);
        axis("axis_yaw", Config.RC_AXIS_YAW, Config.RC_INVERT_YAW);
        axis("axis_pitch", Config.RC_AXIS_PITCH, Config.RC_INVERT_PITCH);
        axis("axis_roll", Config.RC_AXIS_ROLL, Config.RC_INVERT_ROLL);
        axis("axis_arm", Config.RC_AXIS_ARM, Config.RC_INVERT_ARM);
        slider("rc_deadband", true, Config.RC_DEADBAND, 0.0, 0.25, 0.005, percent(100));
        slider("rc_throttle_mid", true, Config.RC_THROTTLE_MID, 0.1, 0.9, 0.01, percent(100));

        rows.add(new SettingRow.Action(tr("reset_tab"), null, tr("reset"), true, () -> {
            resetAll(Config.RC_PITCH_RATE, Config.RC_ROLL_RATE, Config.RC_YAW_RATE, Config.RC_EXPO,
                Config.MOUSE_PITCH_SENSITIVITY, Config.MOUSE_ROLL_SENSITIVITY, Config.MOUSE_SMOOTHING,
                Config.MOUSE_RETURN, Config.MOUSE_DEADBAND, Config.KEYBOARD_YAW_SENSITIVITY,
                Config.RC_DEADBAND, Config.RC_THROTTLE_MID);
            DroneEntity d = controlledDrone();
            if (d != null) {
                setDroneRate(d, 0, Config.RC_PITCH_RATE.get().floatValue());
                setDroneRate(d, 1, Config.RC_ROLL_RATE.get().floatValue());
                setDroneRate(d, 2, Config.RC_YAW_RATE.get().floatValue());
            }
        }));
    }

    private void rate(String key, ModConfigSpec.DoubleValue value, int axis, DroneEntity drone) {
        rows.add(new SettingRow.Slider(tr(key), null, 0.1, 3.0, 0.05, value::get, v -> {
            value.set(v);
            DroneEntity d = drone != null ? drone : controlledDrone();
            if (d != null) setDroneRate(d, axis, (float) v);
        }, multiplier()));
    }

    private static void setDroneRate(DroneEntity drone, int axis, float value) {
        drone.setRateMultiplier(axis, value);
        ClientPacketDistributor.sendToServer(new DroneRatesPacket(drone.getId(), axis, value));
    }

    private void axis(String key, ModConfigSpec.IntValue axis, ModConfigSpec.BooleanValue invert) {
        rows.add(new AxisRow(tr(key), axis, invert, this::setListening));
    }

    private final List<KeyMapping> keyPeers = new ArrayList<>();

    private void buildKeys() {
        Options o = Minecraft.getInstance().options;
        keyPeers.clear();
        keyPeers.addAll(List.of(
            ModKeyBindings.ARM_SWITCH, ModKeyBindings.CYCLE_MODE, ModKeyBindings.MOUSE_MODE,
            ModKeyBindings.DRONE_YAW_LEFT, ModKeyBindings.DRONE_YAW_RIGHT,
            ModKeyBindings.CAMERA_UP, ModKeyBindings.CAMERA_DOWN, ModKeyBindings.PLANE_RESET_CAM,
            ModKeyBindings.FIRE_WEAPON, ModKeyBindings.FIRE_PAYLOAD,
            ModKeyBindings.PLANE_ORBIT, ModKeyBindings.PLANE_CALL_STRIKE,
            ModKeyBindings.OPEN_SETTINGS, ModKeyBindings.TOGGLE_HUD,
            ModKeyBindings.SAVE_REPLAY, ModKeyBindings.PLAY_REPLAY, ModKeyBindings.REPLAY_PAUSE,
            ModKeyBindings.REPLAY_SPEED, ModKeyBindings.REPLAY_BACK, ModKeyBindings.REPLAY_FORWARD,
            o.keyUp, o.keyDown, o.keyLeft, o.keyRight, o.keyJump, o.keySprint, o.keyShift));

        section("keys_flight");
        modKey(ModKeyBindings.ARM_SWITCH);
        modKey(ModKeyBindings.CYCLE_MODE);
        vanillaKey("key_throttle_up", o.keyUp);
        vanillaKey("key_throttle_down", o.keyDown);
        vanillaKey("key_yaw_left", o.keyLeft);
        vanillaKey("key_yaw_right", o.keyRight);
        vanillaKey("key_exit", o.keyShift);

        section("keys_heavy", true);
        vanillaKey("key_climb", o.keyJump);
        vanillaKey("key_descend", o.keySprint);
        modKey(ModKeyBindings.DRONE_YAW_LEFT);
        modKey(ModKeyBindings.DRONE_YAW_RIGHT);
        modKey(ModKeyBindings.MOUSE_MODE);

        section("keys_camera");
        modKey(ModKeyBindings.CAMERA_UP);
        modKey(ModKeyBindings.CAMERA_DOWN);
        modKey(ModKeyBindings.PLANE_RESET_CAM);
        modKey(ModKeyBindings.TOGGLE_HUD);

        section("keys_weapons");
        modKey(ModKeyBindings.FIRE_WEAPON);
        modKey(ModKeyBindings.FIRE_PAYLOAD);

        section("keys_plane");
        modKey(ModKeyBindings.PLANE_ORBIT);
        modKey(ModKeyBindings.PLANE_CALL_STRIKE);

        section("keys_replay");
        modKey(ModKeyBindings.SAVE_REPLAY);
        modKey(ModKeyBindings.PLAY_REPLAY);
        modKey(ModKeyBindings.REPLAY_PAUSE);
        modKey(ModKeyBindings.REPLAY_SPEED);
        modKey(ModKeyBindings.REPLAY_BACK);
        modKey(ModKeyBindings.REPLAY_FORWARD);

        section("keys_interface");
        modKey(ModKeyBindings.OPEN_SETTINGS);

        rows.add(new SettingRow.Action(tr("reset_keys"), hint("reset_keys"), tr("reset"), true, () -> {
            for (KeyMapping key : keyPeers) {
                if (key.getName().startsWith("key.fpv.")) key.setToDefault();
            }
            KeyMapping.resetMapping();
            Minecraft.getInstance().options.save();
        }));
    }

    private void modKey(KeyMapping key) {
        rows.add(new KeyBindRow(Component.translatable(key.getName()), null, key, keyPeers, this::setListening));
    }

    private void vanillaKey(String labelKey, KeyMapping key) {
        rows.add(new KeyBindRow(tr(labelKey), Component.translatable("settings.fpv.key_shared",
            Component.translatable(key.getName())), key, keyPeers, this::setListening));
    }

    private void buildHud() {
        section("hud_osd");
        toggle("hud_telemetry", true, Config.HUD_TELEMETRY);
        toggle("hud_throttle_bar", false, Config.HUD_THROTTLE_BAR);
        toggle("hud_sticks", false, Config.HUD_STICKS);
        toggle("hud_crosshair", false, Config.HUD_CROSSHAIR);
        toggle("hud_heading", false, Config.HUD_HEADING);
        toggle("hud_roll_line", false, Config.HUD_ROLL_LINE);
        toggle("hud_pitch_ladder", false, Config.HUD_PITCH_LADDER);
        toggle("hud_damage", true, Config.HUD_DAMAGE);

        section("hud_extra");
        toggle("hud_hints", true, Config.HUD_HINTS);
        toggle("hud_netstats", true, Config.HUD_NETSTATS);
        toggle("fpv_props", true, Config.FPV_PROPS_IN_VIEW);
        toggle("slim_frames", true, Config.SLIM_FRAMES);

        section("sound", true);
        slider("sound_motor", true, Config.SOUND_MOTOR_VOLUME, 0.0, 1.0, 0.05, percent(100));
        slider("sound_effects", true, Config.SOUND_EFFECTS_VOLUME, 0.0, 1.0, 0.05, percent(100));

        rows.add(new SettingRow.Action(tr("reset_tab"), null, tr("reset"), true, () -> resetAll(
            Config.HUD_TELEMETRY, Config.HUD_THROTTLE_BAR, Config.HUD_STICKS, Config.HUD_CROSSHAIR,
            Config.HUD_HEADING, Config.HUD_ROLL_LINE, Config.HUD_PITCH_LADDER, Config.HUD_DAMAGE,
            Config.HUD_HINTS, Config.HUD_NETSTATS, Config.FPV_PROPS_IN_VIEW, Config.SLIM_FRAMES,
            Config.SOUND_MOTOR_VOLUME, Config.SOUND_EFFECTS_VOLUME)));
    }

    private void buildCreative() {
        if (!Minecraft.getInstance().hasSingleplayerServer()) {
            rows.add(new NoticeRow(tr("creative_server")));
        }

        section("creative_rules");
        ruleToggle("realistic_battery", ServerRule.BATTERY);
        ruleToggle("infinite_ammo", ServerRule.INFINITE_AMMO);
        ruleToggle("dev_no_damage", ServerRule.NO_DAMAGE);
        ruleToggle("realistic_radio", ServerRule.RADIO);
        ruleToggle("require_transmitter", ServerRule.REQUIRE_TRANSMITTER);
        ruleToggle("require_goggles", ServerRule.REQUIRE_GOGGLES);

        section("creative_explosions", true);
        ruleSlider("dev_explosion", ServerRule.EXPLOSION_POWER, 0.2,
            v -> String.format(java.util.Locale.ROOT, "%.1fx", v / 4.0));
        ruleSlider("dev_explosion_break", ServerRule.BLOCK_BREAK, 1, percent(1));
        ruleSlider("dev_explosion_fling", ServerRule.DEBRIS, 1, percent(1));
        ruleSlider("dev_explosion_damage", ServerRule.MOB_DAMAGE, 0.05, multiplier());
        ruleSlider("dev_explosion_cam_time", ServerRule.BLAST_CAMERA, 0.5,
            v -> String.format(java.util.Locale.ROOT, "%.1fs", v));

        section("creative_ordnance", true);
        rows.add(new NoticeRow(tr("creative_ordnance_command")));
        explosiveRow(Explosive.MINI_ROCKET, Explosive.Stat.IMPACT_DAMAGE);
        explosiveRow(Explosive.MINI_ROCKET, Explosive.Stat.POWER);
        explosiveRow(Explosive.MINI_ROCKET, Explosive.Stat.BLOCKS);
        explosiveRow(Explosive.DROP_CHARGE, Explosive.Stat.POWER);
        explosiveRow(Explosive.DROP_CHARGE, Explosive.Stat.BLOCK_POWER);
        explosiveRow(Explosive.WARHEAD_HE, Explosive.Stat.POWER);
        explosiveRow(Explosive.WARHEAD_HE, Explosive.Stat.BLOCK_POWER);
        explosiveRow(Explosive.HOSTILE_DRONE, Explosive.Stat.POWER);
        explosiveRow(Explosive.HOSTILE_DRONE, Explosive.Stat.BREAK_BLOCKS);

        section("creative_flight");
        ruleSlider("dev_power", ServerRule.MOTOR_POWER, 0.05, multiplier());
        ruleSlider("dev_maxspeed", ServerRule.MAX_SPEED, 1, v -> Math.round(v) + " m/s");

        rows.add(new SettingRow.Action(tr("reset_tab"), null, tr("reset"), true, () -> {
            for (ServerRule rule : ServerRule.values()) {
                rules[rule.ordinal()] = rule.defaultValue();
                dirtyRules[rule.ordinal()] = true;
            }
            for (ExplosiveParam param : ExplosiveParam.all()) {
                int i = ExplosiveParam.all().indexOf(param);
                explosives[i] = param.defaultValue();
                dirtyExplosives[i] = true;
            }
            sendRules();
        }));
    }

    private void explosiveRow(Explosive explosive, Explosive.Stat stat) {
        ExplosiveParam param = explosive.param(stat);
        int i = ExplosiveParam.all().indexOf(param);
        Component label = Component.translatable("settings.fpv.explosive_row",
            tr("explosive." + explosive.id), tr("explosive_stat." + stat.id));
        Component hint = hint("explosive_stat." + stat.id);
        if (param.flag()) {
            rows.add(new SettingRow.Toggle(label, hint, () -> explosives[i] >= 0.5, v -> {
                explosives[i] = v ? 1 : 0;
                dirtyExplosives[i] = true;
                sendRules();
            }));
            return;
        }
        boolean whole = param.kind == ExplosiveParam.Kind.INTEGER;
        double step = whole ? 1 : param.max > 20 ? 0.2 : 0.1;
        rows.add(new SettingRow.Slider(label, hint, param.min, param.max, step, () -> explosives[i], v -> {
            explosives[i] = param.clamp(v);
            dirtyExplosives[i] = true;
        }, v -> whole ? Long.toString(Math.round(v)) : String.format(java.util.Locale.ROOT, "%.1f", v)));
    }

    private void ruleToggle(String key, ServerRule rule) {
        int i = rule.ordinal();
        rows.add(new SettingRow.Toggle(tr(key), hint(key), () -> rules[i] >= 0.5, v -> {
            rules[i] = v ? 1 : 0;
            dirtyRules[i] = true;
            sendRules();
        }));
    }

    private void ruleSlider(String key, ServerRule rule, double step, SettingRow.Slider.Format format) {
        int i = rule.ordinal();
        rows.add(new SettingRow.Slider(tr(key), hint(key), rule.min, rule.max, step, () -> rules[i], v -> {
            rules[i] = v;
            dirtyRules[i] = true;
        }, format));
    }

    private void sendRules() {
        Map<String, Double> changes = new LinkedHashMap<>();
        for (ServerRule rule : ServerRule.values()) {
            if (!dirtyRules[rule.ordinal()]) continue;
            dirtyRules[rule.ordinal()] = false;
            changes.put(rule.id, rules[rule.ordinal()]);
        }
        List<ExplosiveParam> params = ExplosiveParam.all();
        for (int i = 0; i < params.size(); i++) {
            if (!dirtyExplosives[i]) continue;
            dirtyExplosives[i] = false;
            changes.put(params.get(i).id(), explosives[i]);
        }
        if (!changes.isEmpty() && minecraft != null && minecraft.getConnection() != null) {
            ClientPacketDistributor.sendToServer(new SetServerRulesPacket(changes));
        }
    }

    public void rulesChanged() {
        if (pressed instanceof SettingRow.Slider) return;
        rules = ServerRule.currentValues();
        explosives = ExplosiveParam.currentValues();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void resetAll(ModConfigSpec.ConfigValue<?>... values) {
        for (ModConfigSpec.ConfigValue value : values) {
            value.set(value.getDefault());
        }
        Config.save();
    }

    private void setListening(SettingRow row) {
        if (row == null) {
            listening = null;
            return;
        }
        if (listening != null && listening != row) {
            SettingRow previous = listening;
            listening = null;
            previous.stopListening();
        }
        listening = row;
    }

    private void stopListening() {
        if (listening != null) listening.stopListening();
        listening = null;
    }

    private int contentX() {
        return wx + sidebar + 14;
    }

    private int contentW() {
        return ww - sidebar - 28;
    }

    private int listTop() {
        return wy + HEADER_HEIGHT;
    }

    private int listBottom() {
        return wy + wh - 8;
    }

    private int contentHeight() {
        int h = 0;
        for (SettingRow row : rows) h += row.height();
        return h + 10;
    }

    private int maxScroll() {
        return Math.max(0, contentHeight() - (listBottom() - listTop()));
    }

    private void clampScroll() {
        scrollTarget = Math.max(0, Math.min(maxScroll(), scrollTarget));
        scroll = Math.max(0, Math.min(maxScroll(), scroll));
    }

    @Override
    public void tick() {
        if (tab == Tab.CREATIVE && !creativeAllowed()) {
            tab = Tab.CONTROLS;
            buildRows();
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        float delta = minecraft.getDeltaTracker().getRealtimeDeltaTicks();
        openAnim = Math.min(1f, openAnim + delta * 0.25f);
        scroll += (scrollTarget - scroll) * Math.min(1f, delta * 0.6f);
        if (Math.abs(scrollTarget - scroll) < 0.25) scroll = scrollTarget;

        extractBackground(g, mouseX, mouseY, partialTick);
        g.fill(0, 0, width, height, (int) (openAnim * 0x60) << 24);

        g.pose().pushMatrix();
        float scale = 0.97f + 0.03f * easeOut(openAnim);
        g.pose().translate(width / 2f, height / 2f);
        g.pose().scale(scale, scale);
        g.pose().translate(-width / 2f, -height / 2f);

        g.fill(wx - 1, wy - 1, wx + ww + 1, wy + wh + 1, 0x60000000);
        SettingsUi.rect(g, wx, wy, ww, wh, SettingsUi.WINDOW);
        SettingsUi.outline(g, wx, wy, ww, wh, SettingsUi.WINDOW_EDGE);
        g.fill(wx + 1, wy + 1, wx + sidebar, wy + wh - 1, SettingsUi.SIDEBAR);
        g.fill(wx + sidebar, wy + 1, wx + sidebar + 1, wy + wh - 1, SettingsUi.WINDOW_EDGE);

        renderSidebar(g, mouseX, mouseY);
        renderHeader(g);
        renderList(g, mouseX, mouseY, delta);

        g.pose().popMatrix();
        renderRowTooltip(g, mouseX, mouseY);
    }

    private void renderRowTooltip(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        if (tipRow == null || pressed != null || listening != null) return;
        if (net.minecraft.util.Util.getMillis() - tipSince < TOOLTIP_DELAY_MS) return;
        List<net.minecraft.util.FormattedCharSequence> lines = new ArrayList<>();
        int wrap = Math.min(TOOLTIP_WIDTH, width - 24);
        if (!tipRow.label.getString().isEmpty()) lines.addAll(font.split(tipRow.label, wrap));
        int labelLines = lines.size();
        if (tipRow.hint != null) lines.addAll(font.split(tipRow.hint, wrap));
        if (lines.isEmpty()) return;
        int tw = 0;
        for (var line : lines) tw = Math.max(tw, font.width(line));
        int bw = tw + 12;
        int bh = lines.size() * 10 + 8 + (labelLines > 0 && labelLines < lines.size() ? 3 : 0);
        int bx = Math.min(mouseX + 12, width - bw - 4);
        int by = mouseY + 14;
        if (by + bh > height - 4) by = Math.max(4, mouseY - bh - 6);
        g.nextStratum();
        g.fill(bx - 1, by - 1, bx + bw + 1, by + bh + 1, 0x60000000);
        SettingsUi.rect(g, bx, by, bw, bh, 0xF8111317);
        SettingsUi.outline(g, bx, by, bw, bh, SettingsUi.WINDOW_EDGE);
        int ly = by + 5;
        for (int i = 0; i < lines.size(); i++) {
            if (i == labelLines && i > 0) ly += 3;
            g.text(font, lines.get(i), bx + 6, ly, i < labelLines ? SettingsUi.TEXT : SettingsUi.MUTED, false);
            ly += 10;
        }
    }

    private static float easeOut(float t) {
        return 1f - (1f - t) * (1f - t) * (1f - t);
    }

    private void renderSidebar(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        boolean wide = sidebar == SIDEBAR_WIDE;
        int bx = wx + (wide ? 12 : (sidebar - 12) / 2);
        SettingsUi.droneIcon(g, bx, wy + 13, SettingsUi.ACCENT);
        if (wide) {
            g.text(font, Component.literal("FPV").withStyle(s -> s.withBold(true)), bx + 18, wy + 11, SettingsUi.TEXT, false);
            g.text(font, tr("brand_sub"), bx + 18, wy + 21, SettingsUi.FAINT, false);
        }

        int ty = wy + 44;
        for (Tab t : visibleTabs()) {
            boolean active = t == tab;
            boolean hot = SettingsUi.inside(mouseX, mouseY, wx + 6, ty, sidebar - 12, TAB_HEIGHT);
            if (active) {
                SettingsUi.rect(g, wx + 6, ty, sidebar - 12, TAB_HEIGHT, SettingsUi.TAB_ACTIVE);
                g.fill(wx + 6, ty + 5, wx + 8, ty + TAB_HEIGHT - 5, SettingsUi.ACCENT);
            } else if (hot) {
                SettingsUi.rect(g, wx + 6, ty, sidebar - 12, TAB_HEIGHT, SettingsUi.TAB_HOVER);
            }
            int color = active ? SettingsUi.ACCENT : hot ? SettingsUi.TEXT : SettingsUi.MUTED;
            int iconX = wide ? wx + 16 : wx + (sidebar - font.width(t.icon)) / 2;
            g.text(font, t.icon, iconX, ty + 8, color, false);
            if (wide) {
                g.text(font, Component.translatable(t.labelKey), wx + 30, ty + 8,
                    active ? SettingsUi.TEXT : color, false);
            }
            ty += TAB_HEIGHT + 2;
        }

        int doneY = wy + wh - 30;
        int doneX = wx + 8;
        int doneW = sidebar - 16;
        boolean doneHot = SettingsUi.inside(mouseX, mouseY, doneX, doneY, doneW, 20);
        SettingsUi.rect(g, doneX, doneY, doneW, 20, doneHot ? 0xFFFFC555 : SettingsUi.ACCENT);
        Component done = wide ? Component.translatable("gui.done") : Component.literal("✔");
        g.text(font, done, doneX + (doneW - font.width(done)) / 2, doneY + 6, 0xFF1A1A1F, false);
    }

    private void renderHeader(GuiGraphicsExtractor g) {
        int x = contentX();
        g.text(font, Component.translatable(tab.labelKey).withStyle(s -> s.withBold(true)), x, wy + 12, SettingsUi.TEXT, false);
        SettingRow.drawClipped(g, font, Component.translatable(tab.labelKey + ".sub"), x, wy + 25, contentW(), SettingsUi.MUTED);
        g.fill(x, wy + HEADER_HEIGHT - 5, x + contentW(), wy + HEADER_HEIGHT - 4, SettingsUi.WINDOW_EDGE);
    }

    private void renderList(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        int top = listTop();
        int bottom = listBottom();
        int x = contentX();
        int w = contentW() - 8;
        boolean mouseInList = mouseY >= top && mouseY < bottom;

        g.enableScissor(x - 2, top, x + contentW() + 2, bottom);
        int y = top - (int) Math.round(scroll);
        SettingRow hovered = null;
        for (SettingRow row : rows) {
            int h = row.height();
            if (y + h >= top && y <= bottom) {
                boolean hot = mouseInList && row.hoverable() && listening == null
                    && SettingsUi.inside(mouseX, mouseY, x, y, w, h);
                if (hot) hovered = row;
                if (hot || row == listening) SettingsUi.rect(g, x, y + 1, w, h - 2, SettingsUi.ROW_HOVER);
                row.render(g, font, x, y, w, mouseInList ? mouseX : -1, mouseInList ? mouseY : -1, delta);
            }
            y += h;
        }
        g.disableScissor();
        if (hovered != tipRow) {
            tipRow = hovered;
            tipSince = net.minecraft.util.Util.getMillis();
        }

        if (scroll > 0.5) g.fillGradient(x - 2, top, x + contentW() + 2, top + 8, 0x40000000, 0x00000000);
        if (scroll < maxScroll() - 0.5) g.fillGradient(x - 2, bottom - 8, x + contentW() + 2, bottom, 0x00000000, 0x40000000);

        int max = maxScroll();
        if (max > 0) {
            int trackX = x + contentW() - 3;
            int trackH = bottom - top;
            int thumbH = Math.max(20, trackH * trackH / (trackH + max));
            int thumbY = top + (int) Math.round((trackH - thumbH) * (scroll / max));
            boolean hot = draggingScrollbar || SettingsUi.inside(mouseX, mouseY, trackX - 3, top, 9, trackH);
            g.fill(trackX, top, trackX + 3, bottom, 0x20FFFFFF);
            SettingsUi.rect(g, trackX, thumbY, 3, thumbH, hot ? SettingsUi.ACCENT : 0x80FFFFFF);
        }
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x(), my = event.y();
        int button = event.button();
        tipSince = net.minecraft.util.Util.getMillis() + 60_000;
        SettingRow cancelled = null;
        if (listening != null) {
            if (listening.listenMouse(button)) return true;
            cancelled = listening;
            stopListening();
        }

        int ty = wy + 44;
        for (Tab t : visibleTabs()) {
            if (SettingsUi.inside(mx, my, wx + 6, ty, sidebar - 12, TAB_HEIGHT)) {
                selectTab(t);
                return true;
            }
            ty += TAB_HEIGHT + 2;
        }
        if (SettingsUi.inside(mx, my, wx + 8, wy + wh - 30, sidebar - 16, 20)) {
            SettingRow.click();
            onClose();
            return true;
        }

        int top = listTop();
        int bottom = listBottom();
        int x = contentX();
        int w = contentW() - 8;
        int max = maxScroll();
        if (max > 0 && SettingsUi.inside(mx, my, x + contentW() - 6, top, 9, bottom - top)) {
            int trackH = bottom - top;
            int thumbH = Math.max(20, trackH * trackH / (trackH + max));
            int thumbY = top + (int) Math.round((trackH - thumbH) * (scroll / max));
            if (my < thumbY || my > thumbY + thumbH) {
                scrollTarget = scroll = Math.max(0, Math.min(max, (my - top - thumbH / 2.0) / (trackH - thumbH) * max));
                thumbY = top + (int) Math.round((trackH - thumbH) * (scroll / max));
            }
            draggingScrollbar = true;
            scrollbarGrab = my - thumbY;
            return true;
        }

        if (my >= top && my < bottom) {
            int y = top - (int) Math.round(scroll);
            for (SettingRow row : rows) {
                int h = row.height();
                if (my >= y && my < y + h && mx >= x && mx < x + w) {
                    if (row == cancelled && button == 0) return true;
                    if (row.mouseClicked(mx, my, button, x, y, w)) {
                        pressed = row;
                        return true;
                    }
                    break;
                }
                y += h;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent event, double dx, double dy) {
        double mx = event.x(), my = event.y();
        if (draggingScrollbar) {
            int top = listTop();
            int trackH = listBottom() - top;
            int max = maxScroll();
            int thumbH = Math.max(20, trackH * trackH / (trackH + max));
            if (trackH > thumbH) {
                scrollTarget = scroll = Math.max(0, Math.min(max, (my - top - scrollbarGrab) / (trackH - thumbH) * max));
            }
            return true;
        }
        if (pressed != null && pressed.mouseDragged(mx, my, contentX(), contentW() - 8)) return true;
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event) {
        draggingScrollbar = false;
        if (pressed != null) {
            pressed.mouseReleased();
            if (pressed instanceof SettingRow.Slider) {
                if (tab == Tab.CREATIVE) sendRules();
                else Config.save();
            }
            pressed = null;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double amount) {
        tipRow = null;
        if (listening != null) return true;
        if (mx < wx + sidebar) {
            List<Tab> tabs = visibleTabs();
            int i = tabs.indexOf(tab) + (amount < 0 ? 1 : -1);
            if (i >= 0 && i < tabs.size()) selectTab(tabs.get(i));
            return true;
        }
        scrollTarget = Math.max(0, Math.min(maxScroll(), scrollTarget - amount * 28));
        return true;
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        int keyCode = event.key(), scanCode = event.keycode();
        if (listening != null) {
            listening.keyPressed(keyCode, scanCode);
            return true;
        }
        if (keyCode == InputConstants.KEY_TAB) {
            List<Tab> tabs = visibleTabs();
            int step = event.hasShiftDown() ? -1 : 1;
            selectTab(tabs.get(Math.floorMod(tabs.indexOf(tab) + step, tabs.size())));
            return true;
        }
        if (keyCode == InputConstants.KEY_PAGEDOWN || keyCode == InputConstants.KEY_PAGEUP) {
            int page = listBottom() - listTop() - 30;
            scrollTarget = Math.max(0, Math.min(maxScroll(), scrollTarget + (keyCode == InputConstants.KEY_PAGEDOWN ? page : -page)));
            return true;
        }
        if (ModKeyBindings.OPEN_SETTINGS.matches(event)) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return listening == null;
    }

    @Override
    public void onClose() {
        stopListening();
        sendRules();
        Config.save();
        minecraft.gui.setScreen(parent);
    }

    private enum Tab {
        CONTROLS("settings.fpv.tab_controls", "✥"),
        KEYS("settings.fpv.tab_keys", "⌨"),
        HUD("settings.fpv.tab_hud", "▣"),
        CREATIVE("settings.fpv.tab_creative", "✦");

        final String labelKey;
        final String icon;

        Tab(String labelKey, String icon) {
            this.labelKey = labelKey;
            this.icon = icon;
        }
    }

    private static final class NoticeRow extends SettingRow {
        NoticeRow(Component text) {
            super(text, null);
        }

        @Override
        int height() {
            return 28;
        }

        @Override
        boolean hoverable() {
            return false;
        }

        @Override
        void render(GuiGraphicsExtractor g, net.minecraft.client.gui.Font font, int x, int y, int w, int mouseX, int mouseY, float delta) {
            SettingsUi.rect(g, x, y + 4, w, 20, 0x22FFB733);
            g.fill(x, y + 4, x + 2, y + 24, SettingsUi.ACCENT);
            drawClipped(g, font, label, x + 8, y + 10, w - 14, SettingsUi.ACCENT);
        }

        @Override
        void renderControl(GuiGraphicsExtractor g, net.minecraft.client.gui.Font font, int x, int y, int w, int mouseX, int mouseY, float delta) {}
    }

    private static final class ControllerStatusRow extends SettingRow {
        ControllerStatusRow() {
            super(Component.empty(), null);
        }

        @Override
        void render(GuiGraphicsExtractor g, net.minecraft.client.gui.Font font, int x, int y, int w, int mouseX, int mouseY, float delta) {
            boolean connected = RCController.isConnected();
            int cy = y + height() / 2;
            SettingsUi.pill(g, x + 8, cy - 3, 6, 6, connected ? 0xFF5FD37A : SettingsUi.FAINT);
            Component status = connected
                ? Component.literal(RCController.getControllerName())
                : Component.translatable("settings.fpv.controller_none");
            drawClipped(g, font, status, x + 20, cy - 4, w - controlWidth(w) - 30, connected ? SettingsUi.TEXT : SettingsUi.MUTED);
            renderControl(g, font, x + w - controlWidth(w) - 8, y, controlWidth(w), mouseX, mouseY, delta);
        }

        @Override
        void renderControl(GuiGraphicsExtractor g, net.minecraft.client.gui.Font font, int x, int y, int w, int mouseX, int mouseY, float delta) {
            Component text = Component.translatable("settings.fpv.controller_rescan");
            int bw = Math.min(w, font.width(text) + 20);
            int bx = x + w - bw;
            int by = y + (height() - 16) / 2;
            boolean hot = SettingsUi.inside(mouseX, mouseY, bx, by, bw, 16);
            SettingsUi.rect(g, bx, by, bw, 16, hot ? SettingsUi.CHIP_HOVER : SettingsUi.CHIP);
            SettingsUi.outline(g, bx, by, bw, 16, SettingsUi.WINDOW_EDGE);
            g.text(font, text, bx + (bw - font.width(text)) / 2, by + 4, SettingsUi.TEXT, false);
        }

        @Override
        boolean mouseClicked(double mx, double my, int button, int x, int y, int w) {
            if (button != 0 || mx < x + w - controlWidth(w) - 8) return false;
            RCController.scan();
            click();
            return true;
        }
    }
}
