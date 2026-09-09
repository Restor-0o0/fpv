package org.restor.fpv;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.List;

public final class LogQuieter {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static boolean applied;

    private LogQuieter() {}

    public static void apply() {
        if (applied || !CommonConfig.SPEC.isLoaded()) return;
        List<? extends String> entries = CommonConfig.QUIET_LOGGERS.get();
        if (entries == null || entries.isEmpty()) {
            applied = true;
            return;
        }
        for (String entry : entries) {
            int eq = entry.indexOf('=');
            String name = (eq < 0 ? entry : entry.substring(0, eq)).trim();
            String level = eq < 0 ? "WARN" : entry.substring(eq + 1).trim();
            if (name.isEmpty()) continue;
            if (setLevel(name, level)) {
                LOGGER.info("Quieted logger {} to {}", name, level);
            }
        }
        applied = true;
    }

    // Done reflectively so a missing or shaded log4j-core never breaks startup.
    private static boolean setLevel(String name, String level) {
        try {
            Class<?> levelClass = Class.forName("org.apache.logging.log4j.Level");
            Object lvl = levelClass.getMethod("toLevel", String.class, levelClass)
                .invoke(null, level, levelClass.getField("WARN").get(null));
            Class<?> configurator = Class.forName("org.apache.logging.log4j.core.config.Configurator");
            configurator.getMethod("setAllLevels", String.class, levelClass).invoke(null, name, lvl);
            return true;
        } catch (Throwable e) {
            LOGGER.warn("Could not quiet logger {}: {}", name, e.toString());
            return false;
        }
    }
}
