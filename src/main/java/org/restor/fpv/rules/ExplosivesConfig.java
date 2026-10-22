package org.restor.fpv.rules;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class ExplosivesConfig {
    public static final ModConfigSpec SPEC;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.comment("Per-explosive tuning. Change in game with /fpv explosives <id> <param> <value>.")
            .push("explosives");
        for (Explosive e : Explosive.values()) {
            builder.push(e.id);
            for (ExplosiveParam p : e.params()) {
                builder.comment(p.stat.comment);
                p.config = p.flag()
                    ? builder.define(p.stat.id, p.defaultValue() >= 0.5)
                    : builder.defineInRange(p.stat.id, p.defaultValue(), p.min, p.max);
            }
            builder.pop();
        }
        builder.pop();
        SPEC = builder.build();
    }

    private ExplosivesConfig() {}
}
