package main_bot;

import java.util.Random;

import battlecode.common.Clock;
import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapInfo;
import battlecode.common.MapLocation;
import battlecode.common.Message;
import battlecode.common.PaintType;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;
import battlecode.common.UnitType;

public class RobotPlayer {
    static int turnCount = 0;
    static final Random rng = new Random(6147);
    static final Direction[] directions = {
        Direction.NORTH, Direction.NORTHEAST, Direction.EAST, Direction.SOUTHEAST,
        Direction.SOUTH, Direction.SOUTHWEST, Direction.WEST, Direction.NORTHWEST,
    };
    static final Direction[] mopperDirections = {
        Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST,
    };

    static int activedAllyTowers = 2;
    static int TOWERS_TRESHOLD = 5;
    static boolean faseLateGame = false;
    static int lastTowerGrowthRound = -1;
    static final int STAGNATION_LIMIT = 300;

    static Direction exploreDir = null;
    static MapLocation lastPosition = null;
    static int stuckCounter = 0;

    static MapLocation currentTargetRuin = null;
    static UnitType currentTowerBuildType = null;

    static final int MSG_RUIN_FOUND = 0;
    static final int MSG_ENEMY_CLUSTER = 1;
    static final int MSG_NEED_MOPPER = 2;
    static final int MSG_NEED_PAINT = 3;

    static MapLocation knownRuinLocation = null;
    static int knownRuinRound = -1;
    static MapLocation knownEnemyCluster = null;
    static int knownEnemyClusterRound = -1;
    static MapLocation needMopperLocation = null;
    static int needMopperRound = -1;

    public static int encodeMessage(int type, MapLocation loc, int extra) {
        return (type << 28) | (loc.x << 22) | (loc.y << 16) | (extra & 0xFFFF);
    }

    public static int getMsgType(int msg) {
        return (msg >>> 28) & 0xF;
    }

    public static MapLocation getMsgLocation(int msg) {
        int x = (msg >>> 22) & 0x3F;
        int y = (msg >>> 16) & 0x3F;
        return new MapLocation(x, y);
    }

    public static int getMsgExtra(int msg) {
        return msg & 0xFFFF;
    }

    public static void processMessages(RobotController rc) throws GameActionException {
        int currentRound = rc.getRoundNum();
        Message[] messages = rc.readMessages(currentRound - 5);

        for (Message msg : messages) {
            int data = msg.getBytes();
            int type = getMsgType(data);
            MapLocation loc = getMsgLocation(data);

            switch (type) {
                case MSG_RUIN_FOUND:
                    if (knownRuinRound < currentRound - 30) {
                        knownRuinLocation = loc;
                        knownRuinRound = currentRound;
                    }
                    break;
                case MSG_ENEMY_CLUSTER:
                    if (knownEnemyClusterRound < currentRound - 20) {
                        knownEnemyCluster = loc;
                        knownEnemyClusterRound = currentRound;
                    }
                    break;
                case MSG_NEED_MOPPER:
                    if (needMopperRound < currentRound - 15) {
                        needMopperLocation = loc;
                        needMopperRound = currentRound;
                    }
                    break;
                case MSG_NEED_PAINT:
                    break;
            }
        }

        if (knownRuinRound >= 0 && currentRound - knownRuinRound > 50) {
            knownRuinLocation = null;
        }
        if (knownEnemyClusterRound >= 0 && currentRound - knownEnemyClusterRound > 30) {
            knownEnemyCluster = null;
        }
        if (needMopperRound >= 0 && currentRound - needMopperRound > 20) {
            needMopperLocation = null;
        }
    }

    public static void sendToNearestTower(RobotController rc, int msgData) throws GameActionException {
        RobotInfo[] allies = rc.senseNearbyRobots(-1, rc.getTeam());
        for (RobotInfo ally : allies) {
            if (ally.type.isTowerType()) {
                if (rc.canSendMessage(ally.location)) {
                    rc.sendMessage(ally.location, msgData);
                    return;
                }
            }
        }
    }

    @SuppressWarnings("unused")
    public static void run(RobotController rc) {
        exploreDir = directions[rc.getID() % directions.length];
        while (true) {
            turnCount++;
            try {
                updatePhase(rc);
                processMessages(rc);
                switch (rc.getType()) {
                    case SOLDIER: SoldierBot.runSoldier(rc); break;
                    case MOPPER: MopperBot.runMopper(rc); break;
                    case SPLASHER: SplasherBot.runSplasher(rc); break;
                    default: TowerBot.runTower(rc); break;
                }
            } catch (GameActionException e) {
                e.printStackTrace();
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                Clock.yield();
            }
        }
    }

    public static void explore(RobotController rc) throws GameActionException {
        if (rc.getLocation().equals(lastPosition)) {
            stuckCounter++;
        } else {
            stuckCounter = 0;
        }
        lastPosition = rc.getLocation();

        if (stuckCounter >= 3) {
            exploreDir = directions[rng.nextInt(directions.length)];
            stuckCounter = 0;
        }

        if (rc.canMove(exploreDir)) {
            rc.move(exploreDir);
            return;
        }

        Direction original = exploreDir;
        for (int i = 0; i < 4; i++) {
            exploreDir = exploreDir.rotateRight();
            if (rc.canMove(exploreDir)) {
                rc.move(exploreDir);
                return;
            }
        }

        exploreDir = original;
        for (int i = 0; i < 4; i++) {
            exploreDir = exploreDir.rotateLeft();
            if (rc.canMove(exploreDir)) {
                rc.move(exploreDir);
                return;
            }
        }
    }

    public static void updatePhase(RobotController rc) {
        int currentRound = rc.getRoundNum();
        RobotInfo[] allies = null;
        try {
            allies = rc.senseNearbyRobots(-1, rc.getTeam());
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }

        int numTowers = 0;
        for (RobotInfo ally : allies) {
            if (ally.type.isTowerType()) {
                numTowers++;
            }
        }

        if (numTowers > activedAllyTowers) {
            lastTowerGrowthRound = currentRound;
            activedAllyTowers = numTowers;
        }

        boolean towerCukup = activedAllyTowers >= TOWERS_TRESHOLD;
        boolean isStagnation = lastTowerGrowthRound >= 0
            && (currentRound - lastTowerGrowthRound > STAGNATION_LIMIT);
        boolean timeForce = currentRound >= 1000;
        if (towerCukup || isStagnation || timeForce) {
            faseLateGame = true;
        }
    }

    public static void reportIntel(RobotController rc, MapInfo[] nearbyTiles) throws GameActionException {
        for (MapInfo tile : nearbyTiles) {
            if (tile.hasRuin()) {
                MapLocation ruinLoc = tile.getMapLocation();
                RobotInfo occupant = rc.senseRobotAtLocation(ruinLoc);
                if (occupant == null) {
                    int msg = encodeMessage(MSG_RUIN_FOUND, ruinLoc, 0);
                    sendToNearestTower(rc, msg);
                    return; 
                }
            }
        }

        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        if (enemies.length >= 3) {
            int sumX = 0, sumY = 0;
            for (RobotInfo e : enemies) {
                sumX += e.location.x;
                sumY += e.location.y;
            }
            MapLocation centroid = new MapLocation(sumX / enemies.length, sumY / enemies.length);
            int msg = encodeMessage(MSG_ENEMY_CLUSTER, centroid, enemies.length);
            sendToNearestTower(rc, msg);
            return;
        }

        int enemyPaintOnRuin = 0;
        for (MapInfo tile : nearbyTiles) {
            if (tile.hasRuin()) {
                MapLocation ruinLoc = tile.getMapLocation();
                RobotInfo occupant = rc.senseRobotAtLocation(ruinLoc);
                if (occupant != null) continue;
                for (MapInfo pt : rc.senseNearbyMapInfos(ruinLoc, 8)) {
                    if (pt.getMark() != PaintType.EMPTY && pt.getPaint().isEnemy()) {
                        enemyPaintOnRuin++;
                    }
                }
                if (enemyPaintOnRuin >= 3) {
                    int msg = encodeMessage(MSG_NEED_MOPPER, ruinLoc, enemyPaintOnRuin);
                    sendToNearestTower(rc, msg);
                    return;
                }
            }
        }
    }


    public static MapLocation findEnemyPaintOnRuinPattern(RobotController rc, MapInfo[] nearbyTiles)
            throws GameActionException {
        MapLocation bestTarget = null;
        int bestDist = Integer.MAX_VALUE;
        for (MapInfo tile : nearbyTiles) {
            if (tile.hasRuin()) {
                MapLocation ruinLoc = tile.getMapLocation();
                RobotInfo occupant = rc.senseRobotAtLocation(ruinLoc);
                if (occupant != null) continue;
                for (MapInfo patternTile : rc.senseNearbyMapInfos(ruinLoc, 8)) {
                    if (patternTile.getMark() != PaintType.EMPTY
                        && patternTile.getPaint().isEnemy()) {
                        int d = rc.getLocation().distanceSquaredTo(patternTile.getMapLocation());
                        if (d < bestDist) {
                            bestDist = d;
                            bestTarget = patternTile.getMapLocation();
                        }
                    }
                }
            }
        }
        return bestTarget;
    }

    public static MapLocation findBestSplashOnRuinPattern(RobotController rc, MapInfo[] nearbyTiles)
            throws GameActionException {
        MapLocation bestCenter = null;
        int bestScore = 0;
        for (MapInfo tile : nearbyTiles) {
            if (tile.hasRuin()) {
                MapLocation ruinLoc = tile.getMapLocation();
                RobotInfo occupant = rc.senseRobotAtLocation(ruinLoc);
                if (occupant != null) continue;
                boolean hasEnemyInPattern = false;
                for (MapInfo pt : rc.senseNearbyMapInfos(ruinLoc, 8)) {
                    if (pt.getMark() != PaintType.EMPTY && pt.getPaint().isEnemy()) {
                        hasEnemyInPattern = true;
                        break;
                    }
                }
                if (!hasEnemyInPattern) continue;
                for (MapInfo candidate : rc.senseNearbyMapInfos(2)) {
                    MapLocation centerLoc = candidate.getMapLocation();
                    if (!rc.canAttack(centerLoc)) continue;
                    int score = 0;
                    for (MapInfo splashTile : rc.senseNearbyMapInfos(centerLoc, 2)) {
                        MapLocation sLoc = splashTile.getMapLocation();
                        if (sLoc.distanceSquaredTo(ruinLoc) <= 8
                            && splashTile.getMark() != PaintType.EMPTY
                            && splashTile.getPaint().isEnemy()) {
                            score += 10;
                        } else if (splashTile.getPaint().isEnemy()) {
                            score += 2;
                        } else if (splashTile.getPaint() == PaintType.EMPTY
                                   && splashTile.isPassable()) {
                            score += 1;
                        }
                    }
                    if (score > bestScore) {
                        bestScore = score;
                        bestCenter = centerLoc;
                    }
                }
            }
        }
        return bestCenter;
    }

    public static void tryUpgradeNearbyTower(RobotController rc) throws GameActionException {
        RobotInfo[] allies = rc.senseNearbyRobots(-1, rc.getTeam());
        for (RobotInfo ally : allies) {
            if (ally.type.isTowerType() && rc.canUpgradeTower(ally.location)) {
                rc.upgradeTower(ally.location);
                return;
            }
        }
    }

    public static UnitType decideTowerType(RobotController rc) throws GameActionException {
        int chips = rc.getChips();
        RobotInfo[] allies = rc.senseNearbyRobots(-1, rc.getTeam());
        int moneyTowers = 0, paintTowers = 0, totalTowerPaint = 0;
        for (RobotInfo ally : allies) {
            if (ally.type == UnitType.LEVEL_ONE_MONEY_TOWER || ally.type == UnitType.LEVEL_TWO_MONEY_TOWER
                || ally.type == UnitType.LEVEL_THREE_MONEY_TOWER) moneyTowers++;
            if (ally.type == UnitType.LEVEL_ONE_PAINT_TOWER || ally.type == UnitType.LEVEL_TWO_PAINT_TOWER
                || ally.type == UnitType.LEVEL_THREE_PAINT_TOWER) {
                paintTowers++;
                totalTowerPaint += ally.paintAmount;
            }
        }
        if (chips < 300) return UnitType.LEVEL_ONE_MONEY_TOWER;
        if (paintTowers == 0) return UnitType.LEVEL_ONE_PAINT_TOWER;
        if (totalTowerPaint < 100 && paintTowers <= moneyTowers) return UnitType.LEVEL_ONE_PAINT_TOWER;
        return UnitType.LEVEL_ONE_MONEY_TOWER;
    }

    public static Direction getGreedyPaintDirection(RobotController rc, MapInfo[] nearbyTiles)
            throws GameActionException {
        Direction bestDir = null;
        int bestUnpainted = -1;
        for (Direction dir : directions) {
            if (!rc.canMove(dir)) continue;
            MapLocation newLoc = rc.getLocation().add(dir);
            int unpainted = 0;
            for (MapInfo tile : nearbyTiles) {
                if (tile.getMapLocation().distanceSquaredTo(newLoc) <= 4
                    && !tile.getPaint().isAlly() && tile.isPassable()) unpainted++;
            }
            unpainted += rng.nextInt(2);
            if (unpainted > bestUnpainted) { bestUnpainted = unpainted; bestDir = dir; }
        }
        return bestDir;
    }

    public static boolean tryRefillPaint(RobotController rc) throws GameActionException {
        RobotInfo[] allies = rc.senseNearbyRobots(-1, rc.getTeam());
        RobotInfo nearestTower = null;
        int bestDist = Integer.MAX_VALUE;
        for (RobotInfo ally : allies) {
            if (ally.type.isTowerType() && ally.paintAmount > 0) {
                int d = rc.getLocation().distanceSquaredTo(ally.location);
                if (d < bestDist) {
                    bestDist = d;
                    nearestTower = ally;
                }
            }
        }
        if (nearestTower != null) {
            Direction dir = rc.getLocation().directionTo(nearestTower.location);
            if (rc.canMove(dir)) rc.move(dir);
            else if (rc.canMove(dir.rotateLeft())) rc.move(dir.rotateLeft());
            else if (rc.canMove(dir.rotateRight())) rc.move(dir.rotateRight());
            int toTake = Math.min(rc.getType().paintCapacity - rc.getPaint(), nearestTower.paintAmount);
            if (toTake > 0 && rc.canTransferPaint(nearestTower.location, -toTake)) {
                rc.transferPaint(nearestTower.location, -toTake);
            }
            return true;
        }
        return false;
    }

    public static boolean tryTransferPaint(RobotController rc, RobotInfo ally)
            throws GameActionException {
        Direction dirToAlly = rc.getLocation().directionTo(ally.location);
        if (rc.canMove(dirToAlly)) rc.move(dirToAlly);
        int toGive = Math.min(rc.getPaint() / 2, ally.type.paintCapacity - ally.paintAmount);
        if (toGive > 0 && rc.canTransferPaint(ally.location, toGive)) {
            rc.transferPaint(ally.location, toGive);
        }
        return true;
    }

    public static RobotInfo getClosestRobot(RobotController rc, RobotInfo[] robots) {
        RobotInfo closest = null;
        int closestDist = Integer.MAX_VALUE;
        for (RobotInfo r : robots) {
            int d = rc.getLocation().distanceSquaredTo(r.location);
            if (d < closestDist) { closestDist = d; closest = r; }
        }
        return closest;
    }

    public static int simSwingPaint(RobotController rc, MapLocation origin,
                                     Direction swingDir, RobotInfo[] enemies) {
        int totalPaint = 0;
        MapLocation step1 = origin.add(swingDir);
        MapLocation step2 = step1.add(swingDir);
        MapLocation[] step1Targets = {
            step1, step1.add(swingDir.rotateLeft()), step1.add(swingDir.rotateRight())
        };
        MapLocation[] step2Targets = {
            step2, step2.add(swingDir.rotateLeft()), step2.add(swingDir.rotateRight())
        };
        for (RobotInfo enemy : enemies) {
            MapLocation eLoc = enemy.location;
            for (MapLocation t : step1Targets) {
                if (eLoc.equals(t)) { totalPaint += enemy.paintAmount; break; }
            }
            for (MapLocation t : step2Targets) {
                if (eLoc.equals(t)) { totalPaint += enemy.paintAmount; break; }
            }
        }
        return totalPaint;
    }
}
