package mainbot;

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

    public static void towerRelayMessages(RobotController rc) throws GameActionException {
        int currentRound = rc.getRoundNum();
        Message[] incoming = rc.readMessages(currentRound - 5);
        int msgSent = 0;
        final int MAX_TOWER_MSGS = 18; 

        for (Message msg : incoming) {
            if (msgSent >= MAX_TOWER_MSGS) break;
            int data = msg.getBytes();

            RobotInfo[] nearbyAllies = rc.senseNearbyRobots(-1, rc.getTeam());
            for (RobotInfo ally : nearbyAllies) {
                if (msgSent >= MAX_TOWER_MSGS) break;
                if (!ally.type.isTowerType() && ally.getID() != msg.getSenderID()) {
                    if (rc.canSendMessage(ally.location)) {
                        rc.sendMessage(ally.location, data);
                        msgSent++;
                    }
                }
            }

            if (msgSent < MAX_TOWER_MSGS && rc.canBroadcastMessage()) {
                rc.broadcastMessage(data);
                msgSent++;
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
        boolean isStagnation = lastTowerGrowthRound >= 0
            && (currentRound - lastTowerGrowthRound > STAGNATION_LIMIT);
        boolean timeForce = currentRound >= 1000;
        if (towerCukup || isStagnation || timeForce) {
            faseLateGame = true;
        }
    }

    public static void runTower(RobotController rc) throws GameActionException {
        // Attack enemies
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        for (RobotInfo enemy : enemies) {
            if (rc.canAttack(enemy.location)) {
                rc.attack(enemy.location);
                break;
            }
        }

        towerRelayMessages(rc);

        MapInfo[] nearbyTiles = rc.senseNearbyMapInfos();
        int enemyTileCount = 0;
        int neutralTileCount = 0;
        for (MapInfo tile : nearbyTiles) {
            PaintType paint = tile.getPaint();
            if (paint.isEnemy()) enemyTileCount++;
            else if (paint == PaintType.EMPTY) neutralTileCount++;
        }

        // Smart spawn based on communication intel
        UnitType toSpawn;
        if (!faseLateGame) {
            if (needMopperLocation != null && enemyTileCount > 0) {
                toSpawn = UnitType.MOPPER;
            } else if (enemyTileCount > 2) {
                toSpawn = UnitType.MOPPER;
            } else {
                toSpawn = UnitType.SOLDIER;
            }
        } else {
            if (enemyTileCount > 2) {
                toSpawn = UnitType.MOPPER;
            } else if (neutralTileCount > 3) {
                toSpawn = UnitType.SPLASHER;
            } else {
                int roll = rng.nextInt(20);
                if (roll < 5) toSpawn = UnitType.SOLDIER;
                else if (roll < 11) toSpawn = UnitType.MOPPER;
                else toSpawn = UnitType.SPLASHER;
            }
        }

        boolean spawned = false;

        Direction preferredDir = null;
        if (knownRuinLocation != null && toSpawn == UnitType.SOLDIER) {
            preferredDir = rc.getLocation().directionTo(knownRuinLocation);
        }
        if (knownEnemyCluster != null && (toSpawn == UnitType.MOPPER || toSpawn == UnitType.SPLASHER)) {
            preferredDir = rc.getLocation().directionTo(knownEnemyCluster);
        }

        if (preferredDir != null) {
            MapLocation spawnLoc = rc.getLocation().add(preferredDir);
            if (rc.canBuildRobot(toSpawn, spawnLoc)) {
                rc.buildRobot(toSpawn, spawnLoc);
                spawned = true;
            }
            if (!spawned) {
                spawnLoc = rc.getLocation().add(preferredDir.rotateLeft());
                if (rc.canBuildRobot(toSpawn, spawnLoc)) {
                    rc.buildRobot(toSpawn, spawnLoc);
                    spawned = true;
                }
            }
            if (!spawned) {
                spawnLoc = rc.getLocation().add(preferredDir.rotateRight());
                if (rc.canBuildRobot(toSpawn, spawnLoc)) {
                    rc.buildRobot(toSpawn, spawnLoc);
                    spawned = true;
                }
            }
        }

        if (!spawned) {
            for (Direction dir : directions) {
                MapLocation spawnLoc = rc.getLocation().add(dir);
                if (rc.canBuildRobot(toSpawn, spawnLoc)) {
                    rc.buildRobot(toSpawn, spawnLoc);
                    spawned = true;
                    break;
                }
            }
        }
        if (!spawned && toSpawn != UnitType.SOLDIER) {
            for (Direction dir : directions) {
                MapLocation spawnLoc = rc.getLocation().add(dir);
                if (rc.canBuildRobot(UnitType.SOLDIER, spawnLoc)) {
                    rc.buildRobot(UnitType.SOLDIER, spawnLoc);
                    break;
                }
            }
        }
    }

    public static void runSoldier(RobotController rc) throws GameActionException {
        MapInfo[] nearbyTiles = rc.senseNearbyMapInfos();

        tryUpgradeNearbyTower(rc);

        reportIntel(rc, nearbyTiles);

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

        if (bestRuin == null && knownRuinLocation != null) {
            bestRuin = knownRuinLocation;
        }

        if (bestRuin != null) {
            if (currentTargetRuin == null || !currentTargetRuin.equals(bestRuin)) {
                currentTargetRuin = bestRuin;
                currentTowerBuildType = decideTowerType(rc);
            }

            MapLocation targetLoc = currentTargetRuin;
            UnitType towerType = currentTowerBuildType;

            if (rc.canMarkTowerPattern(towerType, targetLoc)) {
                rc.markTowerPattern(towerType, targetLoc);
            }

            MapLocation tileToPaint = null;
            if (rc.canSenseLocation(targetLoc)) {
                for (MapInfo patternTile : rc.senseNearbyMapInfos(targetLoc, 8)) {
                    if (patternTile.getMark() != PaintType.EMPTY
                        && patternTile.getMark() != patternTile.getPaint()) {
                        MapLocation ptLoc = patternTile.getMapLocation();
                        if (patternTile.getPaint().isEnemy()) {
                            continue;
                        }
                        if (rc.canAttack(ptLoc)) {
                            boolean useSecondary =
                                patternTile.getMark() == PaintType.ALLY_SECONDARY;
                            rc.attack(ptLoc, useSecondary);
                            tileToPaint = ptLoc;
                            break;
                        }
                        if (tileToPaint == null) {
                            tileToPaint = ptLoc;
                        }
                    }
                }
            }

            if (rc.canCompleteTowerPattern(towerType, targetLoc)) {
                rc.completeTowerPattern(towerType, targetLoc);
                currentTargetRuin = null;
                currentTowerBuildType = null;
                knownRuinLocation = null; // clear communicated ruin
                return;
            }

            if (tileToPaint != null) {
                Direction dir = rc.getLocation().directionTo(tileToPaint);
                if (rc.canMove(dir)) rc.move(dir);
                else if (rc.canMove(dir.rotateLeft())) rc.move(dir.rotateLeft());
                else if (rc.canMove(dir.rotateRight())) rc.move(dir.rotateRight());
            } else {
                Direction dir = rc.getLocation().directionTo(targetLoc);
                if (rc.canMove(dir)) rc.move(dir);
                else if (rc.canMove(dir.rotateLeft())) rc.move(dir.rotateLeft());
                else if (rc.canMove(dir.rotateRight())) rc.move(dir.rotateRight());
            }
            return;
        }

        if (rc.getPaint() < 50) {
            if (tryRefillPaint(rc)) return;
        }

        if (!faseLateGame) {
            if (rc.isActionReady()) {
                for (MapInfo tile : nearbyTiles) {
                    if (!rc.canAttack(tile.getMapLocation())) continue;
                    PaintType paint = tile.getPaint();
                    if (paint == PaintType.EMPTY && tile.isPassable()) {
                        rc.attack(tile.getMapLocation());
                        break;
                    }
                }
            }
            explore(rc);
            if (rc.isActionReady()) {
                MapLocation curLoc = rc.getLocation();
                MapInfo currentTile = rc.senseMapInfo(curLoc);
                if (!currentTile.getPaint().isAlly() && !currentTile.getPaint().isEnemy()
                    && rc.canAttack(curLoc)) {
                    rc.attack(curLoc);
                }
            }
        } else {
            Direction bestDir = getGreedyPaintDirection(rc, nearbyTiles);
            if (bestDir != null && rc.canMove(bestDir)) {
                rc.move(bestDir);
            } else {
                explore(rc);
            }
            if (rc.isActionReady()) {
                MapInfo currentTile = rc.senseMapInfo(rc.getLocation());
                if (!currentTile.getPaint().isAlly() && !currentTile.getPaint().isEnemy()
                    && rc.canAttack(rc.getLocation())) {
                    rc.attack(rc.getLocation());
                }
            }
            if (rc.isActionReady()) {
                for (MapInfo tile : nearbyTiles) {
                    if (tile.getPaint() == PaintType.EMPTY && tile.isPassable()
                        && rc.canAttack(tile.getMapLocation())) {
                        rc.attack(tile.getMapLocation());
                        break;
                    }
                }
            }
        }
    }

    public static void runMopper(RobotController rc) throws GameActionException {
        RobotInfo[] nearbyEnemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        RobotInfo[] nearbyAllies = rc.senseNearbyRobots(-1, rc.getTeam());
        MapLocation myLoc = rc.getLocation();
        MapInfo[] nearbyTiles = rc.senseNearbyMapInfos();

        tryUpgradeNearbyTower(rc);
        reportIntel(rc, nearbyTiles);

        if (rc.getPaint() < 20) {
            for (RobotInfo ally : nearbyAllies) {
                if (ally.type.isTowerType() && ally.paintAmount > 0) {
                    if (rc.isMovementReady()) {
                        Direction d = myLoc.directionTo(ally.location);
                        if (rc.canMove(d)) rc.move(d);
                        else if (rc.canMove(d.rotateLeft())) rc.move(d.rotateLeft());
                        else if (rc.canMove(d.rotateRight())) rc.move(d.rotateRight());
                    }
                    int toTake = rc.getType().paintCapacity - rc.getPaint();
                    if (toTake > 0 && rc.canTransferPaint(ally.location, -toTake)) {
                        rc.transferPaint(ally.location, -toTake);
                    }
                    return;
                }
            }
        }

        MapLocation ruinEnemyTile = findEnemyPaintOnRuinPattern(rc, nearbyTiles);
        if (ruinEnemyTile != null) {
            if (rc.canAttack(ruinEnemyTile)) {
                rc.attack(ruinEnemyTile);
            } else {
                Direction dir = myLoc.directionTo(ruinEnemyTile);
                if (rc.canMove(dir)) rc.move(dir);
                else if (rc.canMove(dir.rotateLeft())) rc.move(dir.rotateLeft());
                else if (rc.canMove(dir.rotateRight())) rc.move(dir.rotateRight());
                if (rc.canAttack(ruinEnemyTile)) {
                    rc.attack(ruinEnemyTile);
                }
            }
            return;
        }

        if (nearbyEnemies.length > 0) {
            RobotInfo richestEnemy = null;
            int maxPaint = -1;
            for (RobotInfo enemy : nearbyEnemies) {
                if (enemy.paintAmount > maxPaint) {
                    maxPaint = enemy.paintAmount;
                    richestEnemy = enemy;
                }
            }

            if (rc.isActionReady()) {
                Direction bestSwingDir = null;
                int bestSwingPaint = 0;
                for (Direction mopDir : mopperDirections) {
                    if (!rc.canMopSwing(mopDir)) continue;
                    int totalPaint = simSwingPaint(rc, myLoc, mopDir, nearbyEnemies);
                    if (totalPaint > bestSwingPaint) {
                        bestSwingPaint = totalPaint;
                        bestSwingDir = mopDir;
                    }
                }
                if (bestSwingDir != null && bestSwingPaint > 0) {
                    rc.mopSwing(bestSwingDir);
                } else if (richestEnemy != null && rc.canAttack(richestEnemy.location)) {
                    rc.attack(richestEnemy.location);
                }
            }

            if (rc.isMovementReady() && richestEnemy != null) {
                Direction dirToEnemy = myLoc.directionTo(richestEnemy.location);
                if (rc.canMove(dirToEnemy)) rc.move(dirToEnemy);
                else if (rc.canMove(dirToEnemy.rotateLeft())) rc.move(dirToEnemy.rotateLeft());
                else if (rc.canMove(dirToEnemy.rotateRight())) rc.move(dirToEnemy.rotateRight());
            }
            return;
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

        if (rc.isActionReady()) {
            MapLocation closestEnemyTile = null;
            int closestDist = Integer.MAX_VALUE;
            for (MapInfo tile : nearbyTiles) {
                if (tile.getPaint().isEnemy() && rc.canAttack(tile.getMapLocation())) {
                    int d = myLoc.distanceSquaredTo(tile.getMapLocation());
                    if (d < closestDist) {
                        closestDist = d;
                        closestEnemyTile = tile.getMapLocation();
                    }
                }
            }
            if (closestEnemyTile != null) {
                rc.attack(closestEnemyTile);
            }
        }

        if (rc.isMovementReady()) {
            MapLocation moveTarget = null;

            MapLocation nearestEnemyPaint = null;
            int nearestDist = Integer.MAX_VALUE;
            for (MapInfo tile : nearbyTiles) {
                if (tile.getPaint().isEnemy()) {
                    int d = myLoc.distanceSquaredTo(tile.getMapLocation());
                    if (d < nearestDist) {
                        nearestDist = d;
                        nearestEnemyPaint = tile.getMapLocation();
                    }
                }
            }

            if (nearestEnemyPaint != null) {
                moveTarget = nearestEnemyPaint;
            } else if (needMopperLocation != null) {
                moveTarget = needMopperLocation;
            } else if (knownEnemyCluster != null) {
                moveTarget = knownEnemyCluster;
            }

            if (moveTarget != null) {
                Direction dir = myLoc.directionTo(moveTarget);
                if (rc.canMove(dir)) rc.move(dir);
                else if (rc.canMove(dir.rotateLeft())) rc.move(dir.rotateLeft());
                else if (rc.canMove(dir.rotateRight())) rc.move(dir.rotateRight());

                if (rc.isActionReady()) {
                    myLoc = rc.getLocation();
                    for (MapInfo tile : rc.senseNearbyMapInfos()) {
                        if (tile.getPaint().isEnemy() && rc.canAttack(tile.getMapLocation())) {
                            rc.attack(tile.getMapLocation());
                            break;
                        }
                    }
                }
                return;
            }
        }

        if (!faseLateGame) {
            for (RobotInfo ally : nearbyAllies) {
                if (ally.type == UnitType.SOLDIER) {
                    Direction dir = myLoc.directionTo(ally.location);
                    if (rc.canMove(dir)) rc.move(dir);
                    return;
                }
            }
        }

        explore(rc);
    }

    public static void runSplasher(RobotController rc) throws GameActionException {
        MapInfo[] nearbyTiles = rc.senseNearbyMapInfos();
        MapLocation myLoc = rc.getLocation();

        tryUpgradeNearbyTower(rc);
        reportIntel(rc, nearbyTiles);

        if (rc.getPaint() < 60) {
            RobotInfo[] allies = rc.senseNearbyRobots(-1, rc.getTeam());
            RobotInfo nearestTower = null;
            int bestDist = Integer.MAX_VALUE;
            for (RobotInfo ally : allies) {
                if (ally.type.isTowerType() && ally.paintAmount > 0) {
                    int d = myLoc.distanceSquaredTo(ally.location);
                    if (d < bestDist) {
                        bestDist = d;
                        nearestTower = ally;
                    }
                }
            }
            if (nearestTower != null) {
                if (rc.isMovementReady()) {
                    Direction dirToTower = myLoc.directionTo(nearestTower.location);
                    if (rc.canMove(dirToTower)) rc.move(dirToTower);
                    else if (rc.canMove(dirToTower.rotateLeft())) rc.move(dirToTower.rotateLeft());
                    else if (rc.canMove(dirToTower.rotateRight())) rc.move(dirToTower.rotateRight());
                }
                if (rc.isActionReady()) {
                    int toTake = rc.getType().paintCapacity - rc.getPaint();
                    if (toTake > 0 && rc.canTransferPaint(nearestTower.location, -toTake)) {
                        rc.transferPaint(nearestTower.location, -toTake);
                    }
                }
                return;
            }
        }

        if (rc.isActionReady()) {
            MapLocation ruinSplashTarget = findBestSplashOnRuinPattern(rc, nearbyTiles);
            if (ruinSplashTarget != null && rc.canAttack(ruinSplashTarget)) {
                rc.attack(ruinSplashTarget);
            } else {
                MapLocation bestTarget = null;
                int bestScore = 1;
                for (MapInfo candidate : rc.senseNearbyMapInfos(2)) {
                    MapLocation loc = candidate.getMapLocation();
                    if (!rc.canAttack(loc)) continue;
                    int score = 0;
                    for (MapInfo t : rc.senseNearbyMapInfos(loc, 2)) {
                        PaintType paint = t.getPaint();
                        if (paint.isEnemy()) score += 2;
                        else if (paint == PaintType.EMPTY && t.isPassable()) score += 1;
                    }
                    if (score > bestScore) {
                        bestScore = score;
                        bestTarget = loc;
                    }
                }
                if (bestTarget != null) {
                    rc.attack(bestTarget);
                }
            }
        }

        if (rc.isMovementReady()) {
            Direction bestDir = null;
            int bestDensity = -1;

            for (Direction dir : directions) {
                if (!rc.canMove(dir)) continue;
                MapLocation nextLoc = myLoc.add(dir);
                int density = 0;
                for (MapInfo tile : nearbyTiles) {
                    if (tile.getPaint().isAlly()) continue;
                    if (tile.getMapLocation().distanceSquaredTo(nextLoc) <= 16) {
                        PaintType paint = tile.getPaint();
                        if (paint.isEnemy()) density += 2;
                        else if (paint == PaintType.EMPTY) density += 1;
                    }
                    if (tile.getMark() != PaintType.EMPTY && tile.getPaint().isEnemy()
                        && tile.getMapLocation().distanceSquaredTo(nextLoc) <= 4) {
                        density += 5;
                    }
                }

                // COMMS: Bias toward known enemy cluster
                if (knownEnemyCluster != null) {
                    int currDist = myLoc.distanceSquaredTo(knownEnemyCluster);
                    int newDist = nextLoc.distanceSquaredTo(knownEnemyCluster);
                    if (newDist < currDist) density += 3;
                }

                density += rng.nextInt(2);
                if (density > bestDensity) {
                    bestDensity = density;
                    bestDir = dir;
                }
            }
            if (bestDir != null) rc.move(bestDir);
            else explore(rc);
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
