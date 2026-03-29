package alternative_bots_1.utils;

import battlecode.common.GameActionException;
import battlecode.common.MapInfo;
import battlecode.common.MapLocation;
import battlecode.common.Message;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;

public class CommunicationUtils {
    public static final int MSG_RUIN_FOUND = 0;

    private static MapLocation knownSharedRuin = null;
    private static int knownSharedRuinRound = -1;
    private static MapLocation lastReportedRuin = null;
    private static int lastReportedRuinRound = -1000;

    private static final int RUIN_STALE_ROUNDS = 60;
    private static final int REPORT_COOLDOWN_ROUNDS = 8;

    private CommunicationUtils() {}

    public static int encodeMessage(int type, MapLocation loc, int extra) {
        return (type << 28) | (loc.x << 22) | (loc.y << 16) | (extra & 0xFFFF);
    }

    public static int getMsgType(int msg) { return (msg >>> 28) & 0xF; }

    public static MapLocation getMsgLocation(int msg) {
        int x = (msg >>> 22) & 0x3F;
        int y = (msg >>> 16) & 0x3F;
        return new MapLocation(x, y);
    }

    public static void processMessages(RobotController rc)
        throws GameActionException {
        int currentRound = rc.getRoundNum();
        Message[] messages = rc.readMessages(currentRound - 5);
        for (Message msg : messages) {
            int data = msg.getBytes();
            int type = getMsgType(data);
            if (type == MSG_RUIN_FOUND) {
                MapLocation loc = getMsgLocation(data);
                if (knownSharedRuinRound < currentRound - 12) {
                    knownSharedRuin = loc;
                    knownSharedRuinRound = currentRound;
                }
            }
        }

        if (knownSharedRuinRound >= 0 &&
            currentRound - knownSharedRuinRound > RUIN_STALE_ROUNDS) {
            knownSharedRuin = null;
        }
    }

    public static void sendToNearestTower(RobotController rc, int msgData)
        throws GameActionException {
        RobotInfo[] allies = rc.senseNearbyRobots(-1, rc.getTeam());
        for (RobotInfo ally : allies) {
            if (ally.type.isTowerType() && rc.canSendMessage(ally.location)) {
                rc.sendMessage(ally.location, msgData);
                return;
            }
        }
    }

    public static void reportNearbyRuin(RobotController rc, MapInfo[] nearby)
        throws GameActionException {
        int currentRound = rc.getRoundNum();
        for (MapInfo tile : nearby) {
            if (!tile.hasRuin()) {
                continue;
            }
            MapLocation ruinLoc = tile.getMapLocation();
            if (rc.senseRobotAtLocation(ruinLoc) != null) {
                continue;
            }

            boolean recentlyReportedSameRuin =
                lastReportedRuin != null && lastReportedRuin.equals(ruinLoc) &&
                currentRound - lastReportedRuinRound <= REPORT_COOLDOWN_ROUNDS;
            if (recentlyReportedSameRuin) {
                return;
            }

            int msg = encodeMessage(MSG_RUIN_FOUND, ruinLoc, 0);
            sendToNearestTower(rc, msg);
            lastReportedRuin = ruinLoc;
            lastReportedRuinRound = currentRound;
            return;
        }
    }

    public static void towerRelayMessages(RobotController rc)
        throws GameActionException {
        if (!rc.getType().isTowerType()) {
            return;
        }

        int currentRound = rc.getRoundNum();
        Message[] incoming = rc.readMessages(currentRound - 5);
        int forwarded = 0;
        final int maxForward = 8;

        for (Message msg : incoming) {
            if (forwarded >= maxForward) {
                break;
            }

            int data = msg.getBytes();
            RobotInfo[] allies = rc.senseNearbyRobots(-1, rc.getTeam());
            for (RobotInfo ally : allies) {
                if (forwarded >= maxForward) {
                    break;
                }
                if (!ally.type.isTowerType() &&
                    ally.getID() != msg.getSenderID() &&
                    rc.canSendMessage(ally.location)) {
                    rc.sendMessage(ally.location, data);
                    forwarded++;
                }
            }

            if (forwarded < maxForward && rc.canBroadcastMessage()) {
                rc.broadcastMessage(data);
                forwarded++;
            }
        }
    }

    public static MapLocation getKnownSharedRuin(RobotController rc)
        throws GameActionException {
        if (knownSharedRuin == null) {
            return null;
        }

        if (rc.canSenseLocation(knownSharedRuin) &&
            rc.senseRobotAtLocation(knownSharedRuin) != null) {
            knownSharedRuin = null;
            knownSharedRuinRound = -1;
            return null;
        }

        return knownSharedRuin;
    }

    public static void clearKnownSharedRuin(MapLocation location) {
        if (knownSharedRuin != null && knownSharedRuin.equals(location)) {
            knownSharedRuin = null;
            knownSharedRuinRound = -1;
        }
    }
}
