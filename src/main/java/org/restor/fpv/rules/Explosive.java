package org.restor.fpv.rules;

import org.restor.fpv.warhead.WarheadType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.restor.fpv.rules.ExplosiveParam.flag;
import static org.restor.fpv.rules.ExplosiveParam.integer;
import static org.restor.fpv.rules.ExplosiveParam.number;

public enum Explosive {
    MINI_ROCKET("mini_rocket", null,
        number(Stat.POWER, 1.4, 0, 10),
        number(Stat.IMPACT_DAMAGE, 5, 0, 100),
        integer(Stat.BLOCKS, 1, 16),
        number(Stat.HARDNESS, 6, 0, 100)),
    HOSTILE_DRONE("hostile_drone", null,
        number(Stat.POWER, 1.8, 0, 20),
        flag(Stat.BREAK_BLOCKS, true)),
    DROP_CHARGE("drop_charge", WarheadType.DROP),
    WARHEAD_FRAG("warhead_frag", WarheadType.FRAG),
    WARHEAD_SHAPED("warhead_shaped", WarheadType.SHAPED),
    WARHEAD_HE("warhead_he", WarheadType.HE),
    WARHEAD_CLUSTER("warhead_cluster", WarheadType.CLUSTER),
    WARHEAD_THERMO("warhead_thermo", WarheadType.THERMOBARIC),
    WARHEAD_HEAVY("warhead_heavy", WarheadType.HEAVY),
    WARHEAD_PROXIMITY("warhead_proximity", WarheadType.PROXIMITY);

    public enum Stat {
        POWER("power", "Blast strength: effect radius and mob damage. Mini rocket / hostile drone: vanilla explosion power"),
        BLOCK_POWER("blockPower", "Explosion power that destroys terrain (4 = TNT crater, 0 = no block damage)"),
        MOB_DAMAGE("mobDamage", "Mob damage multiplier"),
        SUBMUNITIONS("submunitions", "Extra bomblets scattered around the impact"),
        FIRE("fire", "Sets fire around the impact"),
        IMPACT_DAMAGE("impactDamage", "Damage to the entity hit directly"),
        BLOCKS("blocks", "Blocks punched through along the flight path (0 = none)"),
        HARDNESS("hardness", "Hardest block it can break (stone 1.5, obsidian 50)"),
        BREAK_BLOCKS("breakBlocks", "Explosion breaks blocks (still obeys the mobGriefing gamerule)");

        public final String id;
        public final String comment;

        Stat(String id, String comment) {
            this.id = id;
            this.comment = comment;
        }
    }

    static final List<ExplosiveParam> PARAMS;

    static {
        List<ExplosiveParam> all = new ArrayList<>();
        for (Explosive e : values()) {
            for (ExplosiveParam p : e.params.values()) {
                p.owner = e;
                p.index = all.size();
                all.add(p);
            }
        }
        PARAMS = Collections.unmodifiableList(all);
    }

    public final String id;
    public final WarheadType warhead;
    private final Map<Stat, ExplosiveParam> params = new EnumMap<>(Stat.class);

    Explosive(String id, WarheadType warhead, ExplosiveParam... params) {
        this.id = id;
        this.warhead = warhead;
        if (warhead != null) {
            params = new ExplosiveParam[] {
                number(Stat.POWER, round(warhead.basePower()), 0, 40),
                number(Stat.BLOCK_POWER, round(warhead.baseBlockPower()), 0, 40),
                number(Stat.MOB_DAMAGE, round(warhead.baseMobDamageMul()), 0, 20),
                integer(Stat.SUBMUNITIONS, warhead.baseSubmunitions(), 16),
                flag(Stat.FIRE, warhead.baseIncendiary())
            };
        }
        for (ExplosiveParam p : params) this.params.put(p.stat, p);
    }

    private static double round(float v) {
        return Math.round(v * 100.0) / 100.0;
    }

    public ExplosiveParam param(Stat stat) {
        return params.get(stat);
    }

    public ExplosiveParam param(String statId) {
        for (ExplosiveParam p : params.values()) {
            if (p.stat.id.equals(statId)) return p;
        }
        return null;
    }

    public Iterable<ExplosiveParam> params() {
        return params.values();
    }

    public static Explosive byId(String id) {
        for (Explosive e : values()) {
            if (e.id.equals(id)) return e;
        }
        return null;
    }

    public static Explosive of(WarheadType type) {
        for (Explosive e : values()) {
            if (e.warhead == type) return e;
        }
        return WARHEAD_HE;
    }
}
