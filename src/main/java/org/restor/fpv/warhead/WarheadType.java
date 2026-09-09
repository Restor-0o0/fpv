package org.restor.fpv.warhead;

// Warhead families a strike drone or a carrier plane can be loaded with. Power drives the
// blast effects and mob damage; blockPower is the vanilla explosion power that actually
// eats terrain and is deliberately much smaller. Mass is what the airframe has to haul (kg).
public enum WarheadType {
    // HE is the yardstick: blockPower 4.0 is exactly a vanilla TNT crater. Everything
    // else is read against it, and mass decides which airframe can lift it at all.
    FRAG      ("frag",       3.0f, 1.30f,  1.2f, 2.4f, false, 0, 0.55f),
    SHAPED    ("shaped",     3.4f, 2.20f,  2.8f, 1.8f, false, 0, 2.4f),
    HE        ("he",         4.4f, 4.00f,  3.6f, 1.3f, false, 0, 1.0f),
    CLUSTER   ("cluster",    2.6f, 1.50f,  5.5f, 1.5f, false, 5, 0.5f),
    THERMOBARIC("thermo",    6.0f, 3.20f,  7.5f, 3.0f, true,  0, 0.7f),
    HEAVY     ("heavy",      8.0f, 6.60f, 14.0f, 1.6f, false, 0, 1.0f),
    // Airburst head for interceptors: light, weak against terrain, brutal against anything
    // flying or walking nearby. Detonates on approach instead of on contact.
    PROXIMITY ("proximity",  3.2f, 0.60f,  1.6f, 2.8f, false, 0, 0.35f),
    DROP      ("drop",       2.0f, 0.90f,  1.5f, 1.5f, false, 0, 0.6f);

    private final String id;
    private final float power;
    private final float blockPower;
    private final float mass;
    private final float mobDamageMul;
    private final boolean incendiary;
    private final int submunitions;
    private final float penetration;

    WarheadType(String id, float power, float blockPower, float mass, float mobDamageMul,
                boolean incendiary, int submunitions, float penetration) {
        this.id = id;
        this.power = power;
        this.blockPower = blockPower;
        this.mass = mass;
        this.mobDamageMul = mobDamageMul;
        this.incendiary = incendiary;
        this.submunitions = submunitions;
        this.penetration = penetration;
    }

    public String id() { return id; }
    public float power() { return power; }
    public float blockPower() { return blockPower; }
    public float mass() { return mass; }
    public float mobDamageMul() { return mobDamageMul; }
    public boolean incendiary() { return incendiary; }
    public int submunitions() { return submunitions; }
    public float penetration() { return penetration; }

    // DROP is the charge the drone kicks out of its payload bay, not a loadable warhead item.
    public boolean loadable() { return this != DROP; }

    /** Fires when a target comes close instead of waiting for contact (ProximityFuze). */
    public boolean proximity() { return this == PROXIMITY; }

    public String itemName() { return "warhead_" + id; }

    /** Warheads this airframe can lift, lightest first. */
    public static java.util.List<WarheadType> loadableUpTo(float capacityKg) {
        java.util.List<WarheadType> list = new java.util.ArrayList<>();
        for (WarheadType t : values()) if (t.loadable() && t.mass <= capacityKg) list.add(t);
        return list;
    }

    public static WarheadType byId(String id) {
        for (WarheadType t : values()) if (t.id.equals(id)) return t;
        return HE;
    }
}
