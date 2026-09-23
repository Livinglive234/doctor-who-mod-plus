package net.drgmes.dwm;

import net.drgmes.dwm.enums.SonicDeviceMode;
import net.drgmes.dwm.enums.TardisTeleporterEntityTypes;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.joml.Vector2i;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

public class DWM {
    public static final String MODID = "dwm";
    public static final Logger LOGGER = LoggerFactory.getLogger(DWM.MODID);

    public static class COMMON {
        public static final int TARDIS_ROOMS_OFFSET = 1000;
    }

    public static class TIMINGS {
        public static final int DEMAT_DURATION = 240;
        public static final int REMAT_DURATION = 170;
        public static final int PULSE_LOOP = 48;
        public static final int FLIGHT_LOOP = 32;
        public static final int FLIGHT_DURATION_BASE = FLIGHT_LOOP * 2; // 64 ticks (3.2s) - minimum flight duration for very short hops
        public static final int FLIGHT_DURATION_MAX = 900; // 45 seconds - cap for the distance-scaled portion of a flight (dimension crossing bonus is added on top of this)
        public static final int FLIGHT_DISTANCE_FOR_MAX_DURATION = 10000; // blocks of Manhattan distance at which the distance-scaled duration reaches FLIGHT_DURATION_MAX
        public static final int FLIGHT_DIMENSION_CROSSING_BONUS = 300; // 15 seconds - flat extra time added whenever the flight crosses dimensions
        public static final int INSTANT_MATERIALIZATION_DURATION = 2; // demat / remat with the flyover lever on and open sky (see FLYOVER)

        public static final int RECONSTRUCTION_LOOP = 48;
        public static final int RECONSTRUCTION_DURATION = 200;
        public static final int RECONSTRUCTION_NOTIFICATION = 40;

        public static final int SONIC_DEVICE_TIMEOUT = 4;

        // The emergency return: one toll of the cloister bell, and how long it is until the next
        public static final int EMERGENCY_CHIME_INTERVAL = 65; // 3.25s, the length of the toll's sound
        public static final int EMERGENCY_HOME_RADIUS = 20; // how far from the owner's bed it may land

        public static final int PHONE_RING_TIMEOUT = 600; // 30s unanswered before a call gives up
        public static final int PHONE_RING_INTERVAL = 90; // 4.5s, the length of the ring sound including its trailing silence
        public static final int PHONE_RINGBACK_INTERVAL = 121; // 6.05s, one tone-and-silence cycle of the ringback sound
    }

    // The flying TARDIS shown while the flyover lever is on - the design is in CHANGELOG.md.
    // Durations are in ticks, distances in blocks, speeds in blocks per tick.
    public static class FLYOVER {
        // Short hops are one continuous flight
        public static final int FULL_ROUTE_MAX_DISTANCE = 256; // anything longer is a long hop
        public static final int FLIGHT_MIN_DURATION = 145;
        public static final int FLIGHT_LEAD = 85;
        public static final float COMFORT_SPEED = 4.0F;

        // Take-off and landing
        public static final int LIFTOFF_DURATION = 40; // heavy: it hardly moves at first
        public static final float LIFTOFF_SHAKE = 0.05F;
        public static final int FADE_IN_DURATION = 30; // after a take-off from underground
        public static final int SLAM_DURATION = 24;
        public static final int SLAM_LEAD = 3; // the plunge ends this long before the exterior appears, since clients see entities late
        public static final int SLAM_HOLD = 2; // and it rests on the spot this long after
        public static final int SLAM_THUD_LEAD = 5; // the thud is played this long before the exterior appears: its file opens with about a fifth of a second of near-silence
        public static final float SLAM_HEIGHT = 48.0F;
        public static final int FADE_OUT_DURATION = 30; // before a landing underground
        public static final int DEPARTURE_ASCENT_DURATION = 75; // straight-up departure of an interdimensional hop
        public static final int DROP_DURATION = 50; // straight-down arrival from another dimension
        public static final float DROP_HEIGHT = 128.0F;
        public static final int CLOUD_LEVEL = 192;
        public static final int ASCENT_CLOUD_CLEARANCE = 64;
        public static final float TERRAIN_CLEARANCE = 24.0F;

        // Long hops: fast, since most of the route is unwatched
        public static final float LONG_HOP_SPEED = 12.0F;
        public static final int LONG_HOP_LEAD = 60;
        public static final int LONG_HOP_MIN_DURATION = 130;
        public static final int LONG_HOP_PADDING = 100; // idle time so a flight with nobody around isn't near-instant; fly-bys use it up first
        public static final int STREAK_DURATION = 70;
        public static final float STREAK_DISTANCE = 160.0F;
        public static final float STREAK_CLIMB = 48.0F;
        public static final int ARRIVAL_DURATION = 100;
        public static final float ARRIVAL_DISTANCE = 144.0F;

        // Trip caps (demat + flight + remat); only fly-bys may go past them
        public static final int TRIP_CAP = 900; // 45 seconds
        public static final int TRIP_CAP_LONG_INTERDIMENSIONAL = 1200; // 60 seconds, for a trip that is both interdimensional and long distance

        // Fly-bys
        public static final int FLYBY_DURATION = ARRIVAL_DURATION + 20; // always longer than an arrival, so it is seen for at least as long
        public static final float FLYBY_HALF_LENGTH = 120.0F;
        public static final float FLYBY_MIN_HALF_LENGTH = 30.0F;
        public static final float FLYBY_SLOW_BIAS = 0.35F; // 0..1, lower is slower right in front of the bystander
        public static final float FLYBY_MAX_OFFSET = 80.0F; // furthest a player can be off the route and still get one
        public static final int FLYBY_CHECK_INTERVAL = 10;

        // Who sees what
        public static final float ARRIVAL_PLAYER_RADIUS = (float) Math.ceil(Math.hypot(ARRIVAL_DISTANCE + FLYBY_MIN_HALF_LENGTH, FLYBY_MAX_OFFSET)); // reaches every player a fly-by doesn't
        public static final float DROP_PLAYER_RADIUS = 128.0F;
        public static final float RENDER_DISTANCE = 256.0F; // vanilla would cull an entity this small at about 96

        // Sounds of the flyover: the takeoff's, which inside can't drown out the landing, and the flight's (a fly-by's loop, and the one whoosh of coming in or going out)
        public static final int TAKEOFF_SOUND_FADE = 30; // inside, the fade at the end (1.5 seconds)
        public static final int TAKEOFF_SOUND_LANDING_MARGIN = 10; // inside, it is gone this long before the flight ends
        // Outside they follow the flyover, quieter the further it is (range in blocks, down to nothing)
        public static final float TAKEOFF_SOUND_VOLUME = 1.0F;
        public static final float TAKEOFF_SOUND_RANGE = TERRAIN_CLEARANCE; // gone by the time it has risen to flying height
        public static final int SOUND_END_FADE = 20; // a sound with a set time fades out over its last ticks, so a takeoff's ends with the lift-off however far away the listener is, and can't ride along to the landing
        public static final float WHOOSH_STOP_SPEED = 0.1F; // blocks per tick: the flight sound of a landing or a short hop is only heard while the flyover moves faster than this
        public static final float FLIGHT_SOUND_VOLUME = 0.6F;
        public static final float FLIGHT_SOUND_RANGE = 40.0F; // it passes 24 up, so it is heard from about 30 to the side, loudest right overhead

        // Spin, only while flying horizontally
        public static final float SPIN_DEGREES_PER_TICK = 9.0F;
        public static final int SPIN_RAMP = 20;
        public static final int SPIN_SETTLE_DURATION = 30;
    }

    public static class TEXTS {
        public static final Function<Text, Text> SONIC_DEVICE_MODE_TITLE = (mode) -> Text.translatable("title.dwm.sonic_device.mode", mode);
        public static final Text SONIC_DEVICE_MODE_SCAN = Text.translatable("title.dwm.sonic_device.mode.scan");
        public static final Text SONIC_DEVICE_MODE_SCAN_DESCRIPTION = Text.translatable("title.dwm.sonic_device.mode.scan.description");
        public static final Text SONIC_DEVICE_MODE_SETTING = Text.translatable("title.dwm.sonic_device.mode.setting");
        public static final Text SONIC_DEVICE_MODE_SETTING_DESCRIPTION = Text.translatable("title.dwm.sonic_device.mode.setting.description");
        public static final Text SONIC_DEVICE_MODE_TARDIS_RELOCATION = Text.translatable("title.dwm.sonic_device.mode.tardis_relocation");
        public static final Text SONIC_DEVICE_MODE_TARDIS_RELOCATION_DESCRIPTION = Text.translatable("title.dwm.sonic_device.mode.tardis_relocation.description");

        public static final BiFunction<String, Formatting, Text> TARDIS_ID = (id, color) -> Text.translatable("title.dwm.tardis_id", Text.literal(id).formatted(color));
        public static final BiFunction<String, Formatting, Text> TARDIS_POS = (pos, color) -> Text.translatable("title.dwm.tardis_pos", Text.literal(pos).formatted(color));
        public static final BiFunction<String, Formatting, Text> TARDIS_LAST_POS = (pos, color) -> Text.translatable("title.dwm.tardis_last_pos", Text.literal(pos).formatted(color));

        public static final Text MONITOR_STATE_ON = Text.translatable("title.dwm.monitor.state.value.on");
        public static final Text MONITOR_STATE_OFF = Text.translatable("title.dwm.monitor.state.value.off");
        public static final Text MONITOR_STATE_YES = Text.translatable("title.dwm.monitor.state.value.yes");
        public static final Text MONITOR_STATE_NO = Text.translatable("title.dwm.monitor.state.value.no");

        public static final Text ARS_CATEGORIES_BACK = Text.translatable("title.dwm.ars.categories.back").formatted(Formatting.YELLOW);

        public static final Function<Float, Text> TARDIS_ARRIVE_TIMER = (time) -> Text.translatable("message.dwm.tardis.arrive.timer", Text.literal(String.valueOf(time)).formatted(Formatting.AQUA));
        public static final Function<String, Text> TARDIS_ARRIVE_FAILED = (pos) -> Text.translatable("message.dwm.tardis.arrive.failed", Text.literal(pos).formatted(Formatting.GOLD));

        public static final Text TARDIS_NO_FUEL = Text.translatable("message.dwm.tardis.no_fuel");
        public static final Text TARDIS_LOCKED = Text.translatable("message.dwm.tardis.locked");
        public static final Text TARDIS_BROKEN = Text.translatable("message.dwm.tardis.broken");
        public static final Text TARDIS_REPAIRED = Text.translatable("message.dwm.tardis.repaired");
        public static final Text TARDIS_NOT_ALLOWED = Text.translatable("message.dwm.tardis.not_allowed");
        public static final Text TARDIS_MUST_BE_MATERIALIZED = Text.translatable("message.dwm.tardis.must_be_materialized");
        public static final Text TARDIS_MUST_BE_LANDED = Text.translatable("message.dwm.tardis.must_be_landed");
        public static final Text TARDIS_ALREADY_MATERIALIZED = Text.translatable("message.dwm.tardis.already_materialized");
        public static final Text TARDIS_ALREADY_DEMATERIALIZED = Text.translatable("message.dwm.tardis.already_dematerialized");
        public static final Text TARDIS_ALREADY_IN_FLIGHT = Text.translatable("message.dwm.tardis.already_in_flight");
        public static final Text TARDIS_ALREADY_LANDED = Text.translatable("message.dwm.tardis.already_landed");
        public static final Text TARDIS_LEFT_BEHIND = Text.translatable("message.dwm.tardis.left_behind");
        public static final Text TARDIS_NOT_ENOUGH_FUEL = Text.translatable("message.dwm.tardis.fuel.not_enough");
        public static final Text TARDIS_HANDBRAKE_ACTIVATED = Text.translatable("message.dwm.tardis.handbrake.activated");
        public static final Text TARDIS_DOORS_LOCKED = Text.translatable("message.dwm.tardis.control.role.doors.locked");
        public static final Text TARDIS_DOORS_UNLOCKED = Text.translatable("message.dwm.tardis.control.role.doors.unlocked");
        public static final Function<String, Text> TARDIS_REMOVED = (id) -> Text.translatable("message.dwm.tardis.removed", Text.literal(id).formatted(Formatting.AQUA));

        public static final Text RESEARCH_SYSTEM_LEARNED = Text.translatable("message.dwm.tardis.system.research.learned");
        public static final Function<String, Text> RESEARCH_SYSTEM_DIMENSION_LEARNED = (name) -> Text.translatable("message.dwm.tardis.system.research.dimension.learned", Text.literal(name).formatted(Formatting.AQUA));
        public static final Function<String, Text> RESEARCH_SYSTEM_BIOME_LEARNED = (name) -> Text.translatable("message.dwm.tardis.system.research.biome.learned", Text.literal(name).formatted(Formatting.AQUA));
        public static final Function<String, Text> RESEARCH_SYSTEM_STRUCTURE_LEARNED = (name) -> Text.translatable("message.dwm.tardis.system.research.structure.learned", Text.literal(name).formatted(Formatting.AQUA));

        public static final Text MATERIALIZATION_SYSTEM_NOT_INSTALLED = Text.translatable("message.dwm.tardis.system.materialization.not_installed");
        public static final Text MATERIALIZATION_SYSTEM_SAFE_POSITION_NOT_FOUND = Text.translatable("message.dwm.tardis.system.materialization.safe_position_not_found");
        public static final Text MATERIALIZATION_SYSTEM_TARGET_SHIELDED = Text.translatable("message.dwm.tardis.system.materialization.target_shielded");

        public static final Text FLIGHT_SYSTEM_NOT_INSTALLED = Text.translatable("message.dwm.tardis.system.flight.not_installed");

        public static final Text SHIELDS_SYSTEM_NOT_INSTALLED = Text.translatable("message.dwm.tardis.system.shields.not_installed");

        public static final Text IMMERSIVE_PORTALS_NOT_INSTALLED = Text.translatable("message.dwm.tardis.compat.immersive_portals.not_installed");
        public static final Text SIMPLE_VOICE_CHAT_NOT_INSTALLED = Text.translatable("message.dwm.tardis.compat.simple_voice_chat.not_installed");
        public static final Text SHIELDS_SYSTEM_NOT_ACTIVE = Text.translatable("message.dwm.tardis.system.shields.not_active");

        public static final Text PHONE_CALLING = Text.translatable("message.dwm.tardis.phone.calling");
        public static final Function<String, Text> PHONE_RINGING = (caller) -> Text.translatable("message.dwm.tardis.phone.ringing", Text.literal(caller).formatted(Formatting.AQUA));
        public static final Text PHONE_CONNECTED = Text.translatable("message.dwm.tardis.phone.connected");
        public static final Text PHONE_NO_ANSWER = Text.translatable("message.dwm.tardis.phone.no_answer");
        public static final Text PHONE_DECLINED = Text.translatable("message.dwm.tardis.phone.declined");
        public static final Text PHONE_ENDED = Text.translatable("message.dwm.tardis.phone.ended");
        public static final Text PHONE_LINE_BUSY = Text.translatable("message.dwm.tardis.phone.busy");
        public static final Text PHONE_UNREACHABLE = Text.translatable("message.dwm.tardis.phone.unreachable");

        public static final Text ARS_CONSOLE_ROOM_REBUILD_LOCKED = Text.translatable("message.dwm.tardis.ars.console_room.rebuild.locked");
        public static final Text ARS_CONSOLE_ROOM_REBUILD_IN_PROGRESS = Text.translatable("message.dwm.tardis.ars.console_room.rebuild.in_progress");
        public static final Text ARS_CONSOLE_ROOM_REBUILD_PREPARE = Text.translatable("message.dwm.tardis.ars.console_room.rebuild.prepare");
        public static final Text ARS_CONSOLE_ROOM_REBUILD_STARTED = Text.translatable("message.dwm.tardis.ars.console_room.rebuild.started");
        public static final Text ARS_CONSOLE_ROOM_REBUILD_FINISHED = Text.translatable("message.dwm.tardis.ars.console_room.rebuild.finished");
        public static final Text ARS_CONSOLE_ROOM_REBUILD_FAILED = Text.translatable("message.dwm.tardis.ars.console_room.rebuild.failed");
        public static final Function<Float, Text> ARS_CONSOLE_ROOM_REBUILD_TIMER = (time) -> Text.translatable("message.dwm.tardis.ars.console_room.rebuild.timer", Text.literal(String.valueOf(time)).formatted(Formatting.AQUA));

        public static final Text ARS_SECONDARY_ROOM_BUILD_SUCCESS = Text.translatable("message.dwm.tardis.ars.secondary_room.build.success");
        public static final Text ARS_SECONDARY_ROOM_BUILD_FAILED = Text.translatable("message.dwm.tardis.ars.secondary_room.build.failed");
        public static final Function<String, Text> ARS_SECONDARY_ROOM_BUILD_FAILED_DETAILS = (pos) -> Text.translatable("message.dwm.tardis.ars.secondary_room.build.failed.details", Text.literal(pos).formatted(Formatting.YELLOW));

        public static final Text ARS_SECONDARY_ROOM_DESTROY_SUCCESS = Text.translatable("message.dwm.tardis.ars.secondary_room.destroy.success");
        public static final Text ARS_SECONDARY_ROOM_DESTROY_FAILED = Text.translatable("message.dwm.tardis.ars.secondary_room.destroy.failed");

        public static final Text MONITOR_HISTORY_CLEARED = Text.translatable("message.dwm.tardis.history.cleared");
        public static final Text MONITOR_HISTORY_LOADED = Text.translatable("message.dwm.tardis.history.loaded");
        public static final Text MONITOR_HISTORY_REMOVED = Text.translatable("message.dwm.tardis.history.removed");

        public static final Text MONITOR_WAYPOINT_LOADED = Text.translatable("message.dwm.tardis.waypoint.loaded");
        public static final Text MONITOR_WAYPOINT_CREATED = Text.translatable("message.dwm.tardis.waypoint.created");
        public static final Text MONITOR_WAYPOINT_REMOVED = Text.translatable("message.dwm.tardis.waypoint.removed");
        public static final Text MONITOR_WAYPOINT_UPDATED = Text.translatable("message.dwm.tardis.waypoint.updated");

        public static final Text TELEPATHIC_INTERFACE_MAP_COORDS_LOADED = Text.translatable("message.dwm.tardis.telepathic_interface.map.loaded");
        public static final Function<String, Text> TELEPATHIC_INTERFACE_MAP_BANNER_LOADED = (color) -> Text.translatable("message.dwm.tardis.telepathic_interface.map.loaded.banner", Text.literal(color).formatted(Formatting.GOLD));

        public static final Text ARS_DESTROYER_MUST_BE_SNEAKING = Text.translatable("message.dwm.ars_destroyer.must_be_sneaking");
        public static final Text ARS_DESTROYER_INVALID_ROOM = Text.translatable("message.dwm.ars_destroyer.invalid_room");

        public static final Text TARDIS_TELEPORTER_MUST_BE_SNEAKING = Text.translatable("message.dwm.tardis_teleporter.must_be_sneaking");

        public static final Text SONIC_DEVICE_LOCATIONS_DISCOVERED = Text.translatable("message.dwm.sonic_device.locations.discovered");
        public static final Function<String, Text> SONIC_DEVICE_DIMENSION_DISCOVERED = (name) -> Text.translatable("message.dwm.sonic_device.dimension.discovered", Text.literal(name).formatted(Formatting.AQUA));
        public static final Function<String, Text> SONIC_DEVICE_BIOME_DISCOVERED = (name) -> Text.translatable("message.dwm.sonic_device.biome.discovered", Text.literal(name).formatted(Formatting.AQUA));
        public static final Function<String, Text> SONIC_DEVICE_STRUCTURE_DISCOVERED = (name) -> Text.translatable("message.dwm.sonic_device.structure.discovered", Text.literal(name).formatted(Formatting.AQUA));
        public static final Function<List<Text>, Text> SONIC_DEVICE_TARDIS_RELOCATED = (coords) -> Text.translatable("message.dwm.sonic_device.tardis_relocated", coords.get(0), coords.get(1), coords.get(2));

        public static final Text ARS_INTERFACE_TITLE = Text.translatable("screen.dwm.ars_interface.title");
        public static final Text ARS_INTERFACE_SEARCH = Text.translatable("screen.dwm.ars_interface.search");
        public static final Text ARS_INTERFACE_MESSAGE = Text.translatable("screen.dwm.ars_interface.message");
        public static final Text ARS_INTERFACE_BTN_CANCEL = Text.translatable("screen.dwm.ars_interface.button.cancel");
        public static final Text ARS_INTERFACE_BTN_GENERATE = Text.translatable("screen.dwm.ars_interface.button.generate");
        public static final Text ARS_INTERFACE_BTN_DESTROY = Text.translatable("screen.dwm.ars_interface.button.destroy");

        public static final Text MONITOR_TITLE = Text.translatable("screen.dwm.monitor.title");
        public static final Text MONITOR_DATA_ID = Text.translatable("screen.dwm.monitor.data.id");
        public static final Text MONITOR_DATA_OWNER = Text.translatable("screen.dwm.monitor.data.owner");
        public static final Text MONITOR_DATA_EXTERIOR = Text.translatable("screen.dwm.monitor.data.exterior");
        public static final Text MONITOR_ACTION_EXTERIOR = Text.translatable("screen.dwm.monitor.action.exterior");
        public static final Text MONITOR_ACTION_CONSOLE_ROOMS = Text.translatable("screen.dwm.monitor.action.console_rooms");
        public static final Text MONITOR_ACTION_WAYPOINTS = Text.translatable("screen.dwm.monitor.action.waypoints");
        public static final Text MONITOR_ACTION_HISTORY = Text.translatable("screen.dwm.monitor.action.history");

        public static final Text MONITOR_EXTERNAL_SHELLS_TITLE = Text.translatable("screen.dwm.monitor.external_shells.title");
        public static final Text MONITOR_EXTERNAL_SHELLS_CANCEL = Text.translatable("screen.dwm.monitor.external_shells.cancel");
        public static final Text MONITOR_EXTERNAL_SHELLS_ACCEPT = Text.translatable("screen.dwm.monitor.external_shells.accept");

        public static final Text MONITOR_CONSOLE_ROOMS_TITLE = Text.translatable("screen.dwm.monitor.console_rooms.title");
        public static final Text MONITOR_CONSOLE_ROOMS_CANCEL = Text.translatable("screen.dwm.monitor.console_rooms.cancel");
        public static final Text MONITOR_CONSOLE_ROOMS_ACCEPT = Text.translatable("screen.dwm.monitor.console_rooms.accept");
        public static final Text MONITOR_CONSOLE_ROOMS_CONFIRMATION_TEXT_1 = Text.translatable("screen.dwm.monitor.console_rooms.confirmation.text.1");
        public static final Text MONITOR_CONSOLE_ROOMS_CONFIRMATION_TEXT_2 = Text.translatable("screen.dwm.monitor.console_rooms.confirmation.text.2");

        public static final Text MONITOR_HISTORY_TITLE = Text.translatable("screen.dwm.monitor.history.title");
        public static final Text MONITOR_HISTORY_CANCEL = Text.translatable("screen.dwm.monitor.history.cancel");
        public static final Text MONITOR_HISTORY_ACCEPT = Text.translatable("screen.dwm.monitor.history.accept");
        public static final Text MONITOR_HISTORY_SAVE = Text.translatable("screen.dwm.monitor.history.save");
        public static final Text MONITOR_HISTORY_REMOVE = Text.translatable("screen.dwm.monitor.history.remove");
        public static final Text MONITOR_HISTORY_REMOVE_CONFIRMATION_TEXT = Text.translatable("screen.dwm.monitor.history.remove.confirmation.text");
        public static final Text MONITOR_HISTORY_REMOVE_CONFIRMATION_ACCEPT = Text.translatable("screen.dwm.monitor.history.remove.confirmation.accept");
        public static final Text MONITOR_HISTORY_CLEAR = Text.translatable("screen.dwm.monitor.history.clear");
        public static final Text MONITOR_HISTORY_CLEAR_CONFIRMATION_TEXT = Text.translatable("screen.dwm.monitor.history.clear.confirmation.text");
        public static final Text MONITOR_HISTORY_CLEAR_CONFIRMATION_ACCEPT = Text.translatable("screen.dwm.monitor.history.clear.confirmation.accept");

        public static final Text MONITOR_WAYPOINTS_TITLE = Text.translatable("screen.dwm.monitor.waypoints.title");
        public static final Text MONITOR_WAYPOINTS_NAME = Text.translatable("screen.dwm.monitor.waypoints.name");
        public static final Text MONITOR_WAYPOINTS_COORDS = Text.translatable("screen.dwm.monitor.waypoints.coords");
        public static final Text MONITOR_WAYPOINTS_CANCEL = Text.translatable("screen.dwm.monitor.waypoints.cancel");
        public static final Text MONITOR_WAYPOINTS_ACCEPT = Text.translatable("screen.dwm.monitor.waypoints.accept");
        public static final Text MONITOR_WAYPOINTS_REMOVE = Text.translatable("screen.dwm.monitor.waypoints.remove");
        public static final Text MONITOR_WAYPOINTS_REMOVE_CONFIRMATION_TEXT = Text.translatable("screen.dwm.monitor.waypoints.remove.confirmation.text");
        public static final Text MONITOR_WAYPOINTS_REMOVE_CONFIRMATION_ACCEPT = Text.translatable("screen.dwm.monitor.waypoints.remove.confirmation.accept");
        public static final Text MONITOR_WAYPOINTS_UPDATE = Text.translatable("screen.dwm.monitor.waypoints.update");
        public static final Text MONITOR_WAYPOINTS_CREATE = Text.translatable("screen.dwm.monitor.waypoints.create");

        public static final Text MONITOR_WAYPOINTS_CREATE_TITLE = Text.translatable("screen.dwm.monitor.waypoints.create.title");
        public static final Text MONITOR_WAYPOINTS_CREATE_NAME = Text.translatable("screen.dwm.monitor.waypoints.create.name");
        public static final Text MONITOR_WAYPOINTS_CREATE_BTN_CANCEL = Text.translatable("screen.dwm.monitor.waypoints.create.button.cancel");
        public static final Text MONITOR_WAYPOINTS_CREATE_BTN_ACCEPT = Text.translatable("screen.dwm.monitor.waypoints.create.button.accept");
        public static final Text MONITOR_WAYPOINTS_CREATE_BTN_RESET = Text.translatable("screen.dwm.monitor.waypoints.create.button.reset");

        public static final Text PHONE_DIAL_TITLE = Text.translatable("screen.dwm.phone.dial.title");
        public static final Text PHONE_DIAL_SEARCH = Text.translatable("screen.dwm.phone.dial.search");
        public static final Text PHONE_DIAL_EMPTY = Text.translatable("screen.dwm.phone.dial.empty");
        public static final Text PHONE_DIAL_CANCEL = Text.translatable("screen.dwm.phone.dial.cancel");
        public static final Text PHONE_DIAL_CALL = Text.translatable("screen.dwm.phone.dial.call");
        public static final Function<String, Text> PHONE_DIAL_ENTRY = (ownerName) -> Text.translatable("screen.dwm.phone.dial.entry", ownerName);
        public static final Text PHONE_CALL_ANSWER = Text.translatable("screen.dwm.phone.call.answer");
        public static final Text PHONE_CALL_DECLINE = Text.translatable("screen.dwm.phone.call.decline");
        public static final Function<String, Text> PHONE_CALL_STATE_RINGING = (name) -> Text.translatable("screen.dwm.phone.call.state.ringing", Text.literal(name).formatted(Formatting.AQUA));

        public static final Text TELEPATHIC_INTERFACE_TITLE_LOCATIONS = Text.translatable("screen.dwm.telepathic_interface.title.locations");
        public static final Text TELEPATHIC_INTERFACE_TITLE_BANNERS = Text.translatable("screen.dwm.telepathic_interface.title.banners");
        public static final Text TELEPATHIC_INTERFACE_SEARCH = Text.translatable("screen.dwm.telepathic_interface.search");
        public static final Text TELEPATHIC_INTERFACE_NO_RESULTS = Text.translatable("screen.dwm.telepathic_interface.no_results");
        public static final Text TELEPATHIC_INTERFACE_BTN_CANCEL = Text.translatable("screen.dwm.telepathic_interface.button.cancel");
        public static final Text TELEPATHIC_INTERFACE_BTN_ACCEPT = Text.translatable("screen.dwm.telepathic_interface.button.accept");

        public static final Text TARDIS_TELEPORTER_INTERFACE_TITLE = Text.translatable("screen.dwm.tardis_teleporter_interface.title");
        public static final Text TARDIS_TELEPORTER_INTERFACE_ENTITY_TYPES = Text.translatable("screen.dwm.tardis_teleporter_interface.entity_types");
        public static final Text TARDIS_TELEPORTER_INTERFACE_COORDS = Text.translatable("screen.dwm.tardis_teleporter_interface.coords");
        public static final Text TARDIS_TELEPORTER_INTERFACE_BTN_CANCEL = Text.translatable("screen.dwm.tardis_teleporter_interface.button.cancel");
        public static final Text TARDIS_TELEPORTER_INTERFACE_BTN_ACCEPT = Text.translatable("screen.dwm.tardis_teleporter_interface.button.accept");
        public static final Function<TardisTeleporterEntityTypes, Text> TARDIS_TELEPORTER_INTERFACE_ENTITY_TYPE = (entityType) -> Text.translatable("screen.dwm.tardis_teleporter_interface.entity_type." + entityType.name().toLowerCase());

        public static final Text SONIC_DEVICE_INTERFACE_TITLE = Text.translatable("screen.dwm.sonic_device_interface.title");
        public static final Function<SonicDeviceMode, Text> SONIC_DEVICE_INTERFACE_BTN_MODE = (mode) -> Text.translatable("screen.dwm.sonic_device_interface.button.mode", mode.getTitle().copy().formatted(Formatting.AQUA));
        public static final Text SONIC_DEVICE_INTERFACE_SCAN_DIMENSIONS_TITLE = Text.translatable("screen.dwm.sonic_device_interface.scan.dimensions.title");
        public static final Text SONIC_DEVICE_INTERFACE_SCAN_BIOMES_TITLE = Text.translatable("screen.dwm.sonic_device_interface.scan.biomes.title");
        public static final Text SONIC_DEVICE_INTERFACE_SCAN_STRUCTURES_TITLE = Text.translatable("screen.dwm.sonic_device_interface.scan.structures.title");

        public static final Text ARGUMENT_PLAYER_PRESENT = Text.translatable("argument.dwm.tardis.player_present");
        public static final Text ARGUMENT_INVALID_TARDIS = Text.translatable("argument.dwm.tardis.invalid");
        public static final Function<String, Text> ARGUMENT_INVALID_DIMENSION = (id) -> Text.translatable("argument.dimension.invalid", id);
    }

    public static class LOCS {
        public static final Identifier TARDIS = DWM.getIdentifier("tardis");
        public static final Identifier ENTITY_BLOCK_ITEM = DWM.getIdentifier("item/templates/entity_block_item");
    }

    public static class MODELS {
        public static final Identifier BUILTIN_ENTITY = Identifier.ofVanilla("builtin/entity");
        public static final Identifier ITEM_GENERATED = Identifier.ofVanilla("item/generated");
        public static final Identifier BLOCK_CUBE_ALL = Identifier.ofVanilla("block/cube_all");
        public static final Identifier BLOCK_CUBE_BOTTOM_TOP = Identifier.ofVanilla("block/cube_bottom_top");
        public static final Identifier BLOCK_ORIENTABLE = Identifier.ofVanilla("block/orientable");
        public static final Identifier BLOCK_SLAB = Identifier.ofVanilla("block/slab");
        public static final Identifier BLOCK_SLAB_TOP = Identifier.ofVanilla("block/slab_top");
        public static final Identifier BLOCK_STAIRS = Identifier.ofVanilla("block/stairs");
        public static final Identifier BLOCK_STAIRS_INNER = Identifier.ofVanilla("block/inner_stairs");
        public static final Identifier BLOCK_STAIRS_OUTER = Identifier.ofVanilla("block/outer_stairs");
        public static final Identifier BLOCK_WALL_POST = Identifier.ofVanilla("block/template_wall_post");
        public static final Identifier BLOCK_WALL_SIDE = Identifier.ofVanilla("block/template_wall_side");
        public static final Identifier BLOCK_WALL_SIDE_TALL = Identifier.ofVanilla("block/template_wall_side_tall");
        public static final Identifier BLOCK_WALL_INVENTORY = Identifier.ofVanilla("block/wall_inventory");

        public static final Identifier BLOCK_INVISIBLE = DWM.getIdentifier("block/templates/invisible");
        public static final Identifier BLOCK_WALL_XY_POST = DWM.getIdentifier("block/templates/template_wall_xy_post");
        public static final Identifier BLOCK_WALL_XY_SIDE = DWM.getIdentifier("block/templates/template_wall_xy_side");
        public static final Identifier BLOCK_WALL_XY_SIDE_TALL = DWM.getIdentifier("block/templates/template_wall_xy_side_tall");
        public static final Identifier BLOCK_WALL_XY_INVENTORY = DWM.getIdentifier("block/templates/wall_xy_inventory");
    }

    public static class TEXTURES {
        public static class GUI {
            public static class COMMON {
                public static class ELEMENTS {
                    public static final Identifier ACCEPT = DWM.getIdentifier("textures/gui/common/elements/accept.png");
                    public static final Identifier CANCEL = DWM.getIdentifier("textures/gui/common/elements/cancel.png");
                    public static final Identifier CLEAR = DWM.getIdentifier("textures/gui/common/elements/clear.png");
                    public static final Identifier CROSS = DWM.getIdentifier("textures/gui/common/elements/cross.png");
                    public static final Identifier PLUS = DWM.getIdentifier("textures/gui/common/elements/plus.png");
                    public static final Identifier RESET = DWM.getIdentifier("textures/gui/common/elements/reset.png");
                    public static final Identifier SAVE = DWM.getIdentifier("textures/gui/common/elements/save.png");
                }
            }

            public static class TARDIS {
                public static class ARS {
                    public static final Identifier CREATOR_INTERFACE = DWM.getIdentifier("textures/gui/tardis/ars/creator_interface.png");
                    public static final Vector2i CREATOR_INTERFACE_SIZE = new Vector2i(371, 297);

                    public static final Identifier DESTROYER_INTERFACE = DWM.getIdentifier("textures/gui/tardis/ars/destroyer_interface.png");
                    public static final Vector2i DESTROYER_INTERFACE_SIZE = new Vector2i(403, 303);
                }

                public static class CONSOLE {
                    public static final Identifier MONITOR = DWM.getIdentifier("textures/gui/tardis/console_unit/monitor.png");
                    public static final Vector2i MONITOR_SIZE = new Vector2i(403, 303);

                    public static final Identifier TELEPATHIC_INTERFACE = DWM.getIdentifier("textures/gui/tardis/console_unit/telepathic_interface.png");
                    public static final Vector2i TELEPATHIC_INTERFACE_SIZE = new Vector2i(403, 303);
                }

                public static class ENGINE {
                    public static final Identifier SYSTEMS_INTERFACE = DWM.getIdentifier("textures/gui/tardis/engine/systems_interface.png");
                    public static final Vector2i SYSTEMS_INTERFACE_SIZE = new Vector2i(176, 166);
                }

                public static class TELEPORTER {
                    public static final Identifier INTERFACE = DWM.getIdentifier("textures/gui/tardis/teleporter/interface.png");
                    public static final Vector2i INTERFACE_SIZE = new Vector2i(371, 297);
                }
            }

            public static class SONIC_DEVICE {
                public static final Identifier INTERFACE_MAIN = DWM.getIdentifier("textures/gui/sonic_device/interface/main.png");
                public static final Vector2i INTERFACE_MAIN_SIZE = new Vector2i(403, 303);
            }
        }
    }

    public static Identifier getIdentifier(String path) {
        return Identifier.of(DWM.MODID, path);
    }
}
