package mainbot;

import java.util.Random;

import battlecode.common.Clock;
import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapInfo;
import battlecode.common.MapLocation;
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

    @SuppressWarnings("unused")
    public static void run(RobotController rc) {
        exploreDir = directions[rc.getID() % directions.length];
        while (true) {
            turnCount++;
            try {
                updatePhase(rc);
                switch (rc.getType()) {
                    case SOLDIER: runSoldier(rc); break;
                    case MOPPER: runMopper(rc); break;
                    case SPLASHER: runSplasher(rc); break;
                    default: runTower(rc); break;
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
        boolean isStagnation = lastTowerGrowthRound >= 0 && (currentRound - lastTowerGrowthRound > STAGNATION_LIMIT);
        boolean timeForce = currentRound >= 1000;
        if (towerCukup || isStagnation || timeForce) {
            faseLateGame = true;
        }
    }

    public static void runTower(RobotController rc) throws GameActionException {
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        for (RobotInfo enemy : enemies) {
            if (rc.canAttack(enemy.location)) {
                rc.attack(enemy.location);
                break;
            }
        }

        UnitType toSpawn;
        if (!faseLateGame) {
            int roll = rng.nextInt(20);
            toSpawn = (roll < 15) ? UnitType.SOLDIER : UnitType.MOPPER;
        } else {
            int roll = rng.nextInt(20);
            if (roll < 5) toSpawn = UnitType.SOLDIER;
            else if (roll < 11) toSpawn = UnitType.MOPPER;
            else toSpawn = UnitType.SPLASHER;
        }

        for (Direction dir : directions) {
            MapLocation spawnLoc = rc.getLocation().add(dir);
            if (rc.canBuildRobot(toSpawn, spawnLoc)) {
                rc.buildRobot(toSpawn, spawnLoc);
                break;
            }
        }
    }

    public static void runSoldier(RobotController rc) throws GameActionException {
        MapInfo[] nearbyTiles = rc.senseNearbyMapInfos();

        tryUpgradeNearbyTower(rc);

        if (currentTargetRuin != null) {
            if (rc.canSenseLocation(currentTargetRuin)) {
                RobotInfo robotAtRuin = rc.senseRobotAtLocation(currentTargetRuin);
                if (robotAtRuin != null) {
                    currentTargetRuin = null;
                    currentTowerBuildType = null;
                }
            }
        }

        MapLocation bestRuin = null;
        int bestDist = Integer.MAX_VALUE;

        for (MapInfo tile : nearbyTiles) {
            if (tile.hasRuin()) {
                MapLocation ruinLoc = tile.getMapLocation();
                RobotInfo robotAtRuin = rc.senseRobotAtLocation(ruinLoc);
                if (robotAtRuin == null) {
                    int dist = rc.getLocation().distanceSquaredTo(ruinLoc);
                    if (dist < bestDist) {
                        bestDist = dist;
                        bestRuin = ruinLoc;
                    }
                }
            }
        }

        if (bestRuin != null) {
            if (currentTargetRuin == null || !currentTargetRuin.equals(bestRuin)) {
                currentTargetRuin = bestRuin;
                currentTowerBuildType = decideTowerType(rc);
            }

            MapLocation targetLoc = currentTargetRuin;
            UnitType towerType = currentTowerBuildType;

            // Mark pattern dulu
            if (rc.canMarkTowerPattern(towerType, targetLoc)) {
                rc.markTowerPattern(towerType, targetLoc);
            }

            // Cari tile pola yang BELUM diwarnai dan BISA kita attack dari posisi sekarang
            MapLocation tileToPaint = null;
            for (MapInfo patternTile : rc.senseNearbyMapInfos(targetLoc, 8)) {
                if (patternTile.getMark() != patternTile.getPaint()
                    && patternTile.getMark() != PaintType.EMPTY) {
                    if (rc.canAttack(patternTile.getMapLocation())) {
                        boolean useSecondary = patternTile.getMark() == PaintType.ALLY_SECONDARY;
                        rc.attack(patternTile.getMapLocation(), useSecondary);
                        tileToPaint = patternTile.getMapLocation();
                        break;
                    }
                    // Simpan tile yang perlu diwarnai tapi belum bisa reach
                    if (tileToPaint == null) {
                        tileToPaint = patternTile.getMapLocation();
                    }
                }
            }

            // Complete jika sudah selesai
            if (rc.canCompleteTowerPattern(towerType, targetLoc)) {
                rc.completeTowerPattern(towerType, targetLoc);
                currentTargetRuin = null;
                currentTowerBuildType = null;
                return;
            }

            // KUNCI: Move ke arah tile yang belum diwarnai agar bisa reach
            // Jika semua tile dalam reach sudah diwarnai, pindah posisi!
            if (tileToPaint != null) {
                Direction dir = rc.getLocation().directionTo(tileToPaint);
                if (rc.canMove(dir)) rc.move(dir);
            } else {
                // Tidak ada tile yang perlu diwarnai = mendekati ruin
                Direction dir = rc.getLocation().directionTo(targetLoc);
                if (rc.canMove(dir)) rc.move(dir);
            }
            return;
        }


        if (rc.getPaint() < 50) {
            if (tryRefillPaint(rc)) return;
        }

        if (!faseLateGame) {
            explore(rc);
        } else {
            Direction bestDir = getGreedyPaintDirection(rc, nearbyTiles);
            if (bestDir != null && rc.canMove(bestDir)) {
                rc.move(bestDir);
            } else {
                explore(rc);
            }

            MapInfo currentTile = rc.senseMapInfo(rc.getLocation());
            if (!currentTile.getPaint().isAlly() && rc.canAttack(rc.getLocation())) {
                rc.attack(rc.getLocation());
            }

            for (MapInfo tile : nearbyTiles) {
                if (!tile.getPaint().isAlly() && tile.isPassable()
                    && rc.canAttack(tile.getMapLocation())) {
                    rc.attack(tile.getMapLocation());
                    break;
                }
            }
        }
    }

    public static void runMopper(RobotController rc) throws GameActionException {
        RobotInfo[] nearbyEnemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        RobotInfo[] nearbyAllies = rc.senseNearbyRobots(-1, rc.getTeam());

        tryUpgradeNearbyTower(rc);

        if (nearbyEnemies.length > 0) {
            RobotInfo closest = getClosestRobot(rc, nearbyEnemies);
            Direction dirToEnemy = rc.getLocation().directionTo(closest.location);
            if (rc.canMove(dirToEnemy)) rc.move(dirToEnemy);
            if (rc.canMopSwing(dirToEnemy)) { rc.mopSwing(dirToEnemy); return; }
            if (rc.canAttack(closest.location)) { rc.attack(closest.location); return; }
        }

        for (RobotInfo ally : nearbyAllies) {
            if (ally.type.isTowerType()) continue;
            if (ally.paintAmount < ally.type.paintCapacity / 3) {
                if (!faseLateGame && ally.type == UnitType.SOLDIER) {
                    if (tryTransferPaint(rc, ally)) return;
                }
                if (faseLateGame && (ally.type == UnitType.SPLASHER || ally.type == UnitType.SOLDIER)) {
                    if (tryTransferPaint(rc, ally)) return;
                }
            }
        }

        MapInfo[] nearbyTiles = rc.senseNearbyMapInfos();
        MapLocation enemyPaintLoc = null;
        int bestDist = Integer.MAX_VALUE;
        for (MapInfo tile : nearbyTiles) {
            if (tile.getPaint().isEnemy()) {
                int d = rc.getLocation().distanceSquaredTo(tile.getMapLocation());
                if (d < bestDist) {
                    bestDist = d;
                    enemyPaintLoc = tile.getMapLocation();
                }
            }
        }

        if (enemyPaintLoc != null) {
            Direction dir = rc.getLocation().directionTo(enemyPaintLoc);
            if (rc.canMove(dir)) rc.move(dir);
            if (rc.canAttack(enemyPaintLoc)) rc.attack(enemyPaintLoc);
            return;
        }

        if (rc.getPaint() < 20) { if (tryRefillPaint(rc)) return; }

        if (!faseLateGame) {
            for (RobotInfo ally : nearbyAllies) {
                if (ally.type == UnitType.SOLDIER) {
                    Direction dir = rc.getLocation().directionTo(ally.location);
                    if (rc.canMove(dir)) rc.move(dir);
                    return;
                }
            }
        }

        explore(rc);
    }

    public static void runSplasher(RobotController rc) throws GameActionException {
        MapInfo[] nearbyTiles = rc.senseNearbyMapInfos();

        tryUpgradeNearbyTower(rc);

        if (rc.getPaint() < 60) { if (tryRefillPaint(rc)) return; }

        MapLocation bestTarget = null;
        int bestScore = 0;

        for (MapInfo tile : nearbyTiles) {
            MapLocation loc = tile.getMapLocation();
            if (rc.getLocation().distanceSquaredTo(loc) > 4) continue;
            int score = 0;
            for (MapInfo t : nearbyTiles) {
                if (t.getMapLocation().distanceSquaredTo(loc) <= 4) {
                    if (!t.getPaint().isAlly() && t.isPassable()) score += 2;
                    if (t.getPaint().isEnemy()) score += 1;
                }
            }
            if (score > bestScore) { bestScore = score; bestTarget = loc; }
        }

        if (bestTarget != null && bestScore >= 3 && rc.canAttack(bestTarget)) {
            rc.attack(bestTarget);
        }

        Direction bestDir = getGreedyPaintDirection(rc, nearbyTiles);
        if (bestDir != null && rc.canMove(bestDir)) {
            rc.move(bestDir);
        } else {
            explore(rc);
        }
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
        RobotInfo[] allies = rc.senseNearbyRobots(-1, rc.getTeam());
        int moneyTowers = 0, paintTowers = 0;
        for (RobotInfo ally : allies) {
            if (ally.type == UnitType.LEVEL_ONE_MONEY_TOWER || ally.type == UnitType.LEVEL_TWO_MONEY_TOWER
                || ally.type == UnitType.LEVEL_THREE_MONEY_TOWER) moneyTowers++;
            if (ally.type == UnitType.LEVEL_ONE_PAINT_TOWER || ally.type == UnitType.LEVEL_TWO_PAINT_TOWER
                || ally.type == UnitType.LEVEL_THREE_PAINT_TOWER) paintTowers++;
        }
        return (moneyTowers <= paintTowers)
            ? UnitType.LEVEL_ONE_MONEY_TOWER
            : UnitType.LEVEL_ONE_PAINT_TOWER;
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
            if (unpainted > bestUnpainted) { bestUnpainted = unpainted; bestDir = dir; }
        }
        return bestDir;
    }

    public static boolean tryRefillPaint(RobotController rc) throws GameActionException {
        RobotInfo[] allies = rc.senseNearbyRobots(-1, rc.getTeam());
        for (RobotInfo ally : allies) {
            if (ally.type.isTowerType()) {
                Direction dir = rc.getLocation().directionTo(ally.location);
                if (rc.canMove(dir)) rc.move(dir);
                int toTake = Math.min(rc.getType().paintCapacity - rc.getPaint(), ally.paintAmount);
                if (toTake > 0 && rc.canTransferPaint(ally.location, -toTake)) {
                    rc.transferPaint(ally.location, -toTake);
                }
                return true;
            }
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
}
