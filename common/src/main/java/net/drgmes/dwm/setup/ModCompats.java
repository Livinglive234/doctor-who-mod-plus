package net.drgmes.dwm.setup;

public class ModCompats {
    public static boolean clothConfig() {
        try {
            Class.forName("me.shedaniel.autoconfig.AutoConfig");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    public static boolean modMenu() {
        try {
            Class.forName("com.terraformersmc.modmenu.api.ModMenuApi");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    public static boolean dimLib() {
        try {
            Class.forName("qouteall.dimlib.api.DimensionAPI");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    public static boolean immersivePortals() {
        try {
            Class.forName("qouteall.imm_ptl.core.portal.Portal");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    public static boolean immersivePortalsUtils() {
        try {
            Class.forName("qouteall.q_misc_util.dimension.DimIntIdMap");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    public static boolean techReborn() {
        try {
            Class.forName("team.reborn.energy.api.base.SimpleEnergyStorage");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    public static boolean appliedEnergistics() {
        try {
            Class.forName("appeng.api.networking.energy.IEnergyService");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    public static boolean iris() {
        try {
            Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    // Not the voicechat-api dependency itself - that's bundled into this mod's own jar regardless (see
    // common/build.gradle), so its classes are always on the classpath. This checks for the actual Simple Voice
    // Chat mod, whose class of the same name only exists when it's really installed.
    public static boolean simpleVoiceChat() {
        try {
            Class.forName("de.maxhenkel.voicechat.Voicechat");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
