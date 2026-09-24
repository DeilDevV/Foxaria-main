package com.foxaria.regions;

public final class RegionFlags {

    public static final int MEMBER_INVITE = 1;
    public static final int MEMBER_UPGRADE = 2;
    /** Частицы границы для участников (если у них не выключено лично). */
    public static final int BOUNDARY_PARTICLES = 4;
    /** Если снят — участники не могут выключать границу у себя. */
    public static final int FORBID_MEMBER_BOUNDARY_TOGGLE = 8;
    /** Уведомления о входе чужаков (только при достаточном уровне региона). */
    public static final int INTRUSION_ALERT = 16;

    private RegionFlags() {
    }

    public static boolean allowMemberInvite(int flags) {
        return (flags & MEMBER_INVITE) != 0;
    }

    public static boolean allowMemberUpgrade(int flags) {
        return (flags & MEMBER_UPGRADE) != 0;
    }

    public static boolean boundaryParticles(int flags) {
        return (flags & BOUNDARY_PARTICLES) != 0;
    }

    /** Участникам разрешено скрывать у себя частицы границы. */
    public static boolean allowMemberBoundaryToggle(int flags) {
        return (flags & FORBID_MEMBER_BOUNDARY_TOGGLE) == 0;
    }

    public static boolean intrusionAlert(int flags) {
        return (flags & INTRUSION_ALERT) != 0;
    }

    public static int with(int flags, int mask, boolean on) {
        if (on) {
            return flags | mask;
        }
        return flags & ~mask;
    }
}
