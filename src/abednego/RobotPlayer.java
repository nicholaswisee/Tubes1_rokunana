package abednego;

import battlecode.common.*;
import java.util.Random;

public class RobotPlayer {
    static int turnCount = 0;
    static final Random rng = new Random(6147);
    static MapLocation currentTargetRuin = null;
    static UnitType currentTowerType = null;
    static Direction exploreDir = null;
    static MapLocation lastPos = null;
    static int stuckCount = 0;

    static final Direction[] directions = {
            Direction.NORTH,
            Direction.NORTHEAST,
            Direction.EAST,
            Direction.SOUTHEAST,
            Direction.SOUTH,
            Direction.SOUTHWEST,
            Direction.WEST,
            Direction.NORTHWEST,
    };

    static final Direction[] mopperDirections = {
            Direction.NORTH,
            Direction.EAST,
            Direction.SOUTH,
            Direction.WEST,
    };

    @SuppressWarnings("unused")
    public static void run(RobotController rc) throws GameActionException {
        exploreDir = directions[rc.getID() % directions.length];
        while (true) {
            turnCount += 1;
            try {
                switch (rc.getType()) {
                    case SOLDIER:  runSoldier(rc);  break;
                    case MOPPER:   runMopper(rc);   break;
                    case SPLASHER: runSplasher(rc); break;
                    default:       runTower(rc);    break;
                }
            } catch (GameActionException e) {
                System.out.println("GameActionException");
                e.printStackTrace();
            } catch (Exception e) {
                System.out.println("Exception");
                e.printStackTrace();
            } finally {
                Clock.yield();
            }
        }
    }

    public static void runTower(RobotController rc) throws GameActionException {
        RobotInfo[] nearbyEnemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        if (nearbyEnemies.length > 0) {
            if (rc.canAttack(nearbyEnemies[0].location)) {
                rc.attack(nearbyEnemies[0].location);
            }
        }

        MapInfo[] nearbyTiles = rc.senseNearbyMapInfos();
        int enemyTileCount = 0;
        int neutralTileCount = 0;
        for (MapInfo tile : nearbyTiles) {
            PaintType paint = tile.getPaint();
            if (paint.isEnemy()) { enemyTileCount++; }
            else if (paint == PaintType.EMPTY) { neutralTileCount++; }
        }

        UnitType typeToBuild;
        if (enemyTileCount > 2) { typeToBuild = UnitType.MOPPER; }
        else if (neutralTileCount > 3) { typeToBuild = UnitType.SPLASHER; }
        else { typeToBuild = UnitType.SOLDIER; }

        boolean spawned = false;
        for (Direction dir : directions) {
            MapLocation spawnLoc = rc.getLocation().add(dir);
            if (rc.canBuildRobot(typeToBuild, spawnLoc)) {
                rc.buildRobot(typeToBuild, spawnLoc);
                spawned = true;
                break;
            }
        }
        if (!spawned && typeToBuild != UnitType.SOLDIER) {
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
        MapLocation myLoc = rc.getLocation();
        MapInfo[] nearbyTiles = rc.senseNearbyMapInfos();

        MapLocation targetRuin = null;
        for (MapInfo tile : nearbyTiles) {
            if (tile.hasRuin()) {
                MapLocation ruinLoc = tile.getMapLocation();
                RobotInfo ruinOccupant = rc.senseRobotAtLocation(ruinLoc);
                if (ruinOccupant == null) {
                    targetRuin = ruinLoc;
                    break;
                }
            }
        }

        if (targetRuin != null) {
            if (currentTargetRuin == null || !currentTargetRuin.equals(targetRuin)) {
                currentTargetRuin = targetRuin;
                currentTowerType = decideTowerType(rc);
            }
            UnitType towerTypeToBuild = currentTowerType;

            if (rc.canMarkTowerPattern(towerTypeToBuild, targetRuin)) {
                rc.markTowerPattern(towerTypeToBuild, targetRuin);
            }

            if (rc.isActionReady()) {
                for (MapInfo patternTile : rc.senseNearbyMapInfos(targetRuin, 8)) {
                    if (patternTile.getMark() != PaintType.EMPTY && patternTile.getMark() != patternTile.getPaint()) {
                        boolean useSecondary = (patternTile.getMark() == PaintType.ALLY_SECONDARY);
                        if (rc.canAttack(patternTile.getMapLocation())) {
                            rc.attack(patternTile.getMapLocation(), useSecondary);
                            break;
                        }
                    }
                }
            }

            if (rc.canCompleteTowerPattern(towerTypeToBuild, targetRuin)) {
                rc.completeTowerPattern(towerTypeToBuild, targetRuin);
                currentTargetRuin = null;
                currentTowerType = null;
            }

            tryUpgradeTower(rc);

            if (rc.isMovementReady()) {
                Direction dirToRuin = myLoc.directionTo(targetRuin);
                if (rc.canMove(dirToRuin)) {
                    rc.move(dirToRuin);
                } else {
                    Direction left = dirToRuin.rotateLeft();
                    Direction right = dirToRuin.rotateRight();
                    if (rc.canMove(left)) { rc.move(left); }
                    else if (rc.canMove(right)) { rc.move(right); }
                }
            }
            return;
        }

        tryUpgradeTower(rc);

        if (rc.getPaint() < 40) {
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
                    if (rc.canMove(dirToTower)) { rc.move(dirToTower);}
                    else if (rc.canMove(dirToTower.rotateLeft())) { rc.move(dirToTower.rotateLeft());}
                    else if (rc.canMove(dirToTower.rotateRight())) { rc.move(dirToTower.rotateRight());}
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
            MapInfo bestEnemyTile   = null;
            MapInfo bestNeutralTile = null;

            for (MapInfo tile : nearbyTiles) {
                if (!rc.canAttack(tile.getMapLocation())) { continue;}
                PaintType paint = tile.getPaint();
                if (paint.isEnemy() && bestEnemyTile == null) {
                    bestEnemyTile = tile;
                } else if (paint == PaintType.EMPTY && bestNeutralTile == null) {
                    bestNeutralTile = tile;
                }
            }

            if (bestEnemyTile != null) {
                rc.attack(bestEnemyTile.getMapLocation());
            } else if (bestNeutralTile != null) {
                rc.attack(bestNeutralTile.getMapLocation());
            } else if (rc.canAttack(myLoc)) {
                MapInfo currentTile = rc.senseMapInfo(myLoc);
                if (!currentTile.getPaint().isAlly()) {
                    rc.attack(myLoc);
                }
            }
        }

        if (rc.isMovementReady()) {
            Direction bestDir = null;
            int bestScore = 0;

            for (Direction dir : directions) {
                if (!rc.canMove(dir)) continue;
                MapLocation nextLoc = myLoc.add(dir);
                int score = 0;
                for (MapInfo tile : nearbyTiles) {
                    PaintType paint = tile.getPaint();
                    if (paint.isAlly()) { continue; }
                    int dist = tile.getMapLocation().distanceSquaredTo(nextLoc);
                    if (dist <= 3) { score += getDeltaScore(paint); }
                }
                score += rng.nextInt(3);
                if (score > bestScore) {
                    bestScore = score;
                    bestDir   = dir;
                }
            }

            if (bestDir != null) {
                rc.move(bestDir);
            } else {
                explore(rc);
            }
        }

        if (rc.isActionReady()) {
            MapLocation curLoc = rc.getLocation();
            MapInfo currentTile = rc.senseMapInfo(curLoc);
            if (!currentTile.getPaint().isAlly() && rc.canAttack(curLoc)) {
                rc.attack(curLoc);
            }
        }
    }

    public static void runMopper(RobotController rc) throws GameActionException {
        MapLocation myLoc = rc.getLocation();

        if (rc.getPaint() < 20) {
            RobotInfo[] allies = rc.senseNearbyRobots(-1, rc.getTeam());
            for (RobotInfo ally : allies) {
                if (ally.type.isTowerType() && ally.paintAmount > 0) {
                    if (rc.isMovementReady()) {
                        Direction d = myLoc.directionTo(ally.location);
                        if (rc.canMove(d)) { rc.move(d); }
                        else if (rc.canMove(d.rotateLeft())) { rc.move(d.rotateLeft()); }
                        else if (rc.canMove(d.rotateRight())) { rc.move(d.rotateRight()); }
                    }
                    if (rc.isActionReady()) {
                        int toTake = rc.getType().paintCapacity - rc.getPaint();
                        if (toTake > 0 && rc.canTransferPaint(ally.location, -toTake)) {
                            rc.transferPaint(ally.location, -toTake);
                        }
                    }
                    return;
                }
            }
        }

        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());

        if (enemies.length > 0) {
            RobotInfo richestEnemy = null;
            int maxPaint = -1;
            for (RobotInfo enemy : enemies) {
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
                    int totalPaint = simSwingPaint(rc, myLoc, mopDir, enemies);
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
                if (rc.canMove(dirToEnemy)) {
                    rc.move(dirToEnemy);
                } else {
                    Direction left = dirToEnemy.rotateLeft();
                    Direction right = dirToEnemy.rotateRight();
                    if (rc.canMove(left)) { rc.move(left); }
                    else if (rc.canMove(right)) { rc.move(right); }
                }
            }

        } else {
            if (rc.isMovementReady()) {
                Direction bestDir = null;
                int bestCount = -1;

                for (Direction dir : directions) {
                    if (!rc.canMove(dir)) continue;
                    MapLocation nextLoc = myLoc.add(dir);
                    int count = 0;
                    for (MapInfo tile : rc.senseNearbyMapInfos()) {
                        if (tile.getPaint().isEnemy()) {
                            if (tile.getMapLocation().distanceSquaredTo(nextLoc) <= 8) { count++; }
                        }
                    }
                    count += rng.nextInt(2); // tiebreaker acak
                    if (count > bestCount) {
                        bestCount = count;
                        bestDir = dir;
                    }
                }

                if (bestDir != null) {
                    rc.move(bestDir);
                } else {
                    explore(rc);
                }
            }
        }
    }

    public static void runSplasher(RobotController rc) throws GameActionException {
        MapLocation myLoc = rc.getLocation();

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
                    if (rc.canMove(dirToTower)) { rc.move(dirToTower); }
                    else if (rc.canMove(dirToTower.rotateLeft())) { rc.move(dirToTower.rotateLeft()); }
                    else if (rc.canMove(dirToTower.rotateRight())) { rc.move(dirToTower.rotateRight()); }
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
            MapLocation bestCenter = null;
            int bestScore = 1;

            for (MapInfo candidate : rc.senseNearbyMapInfos(2)) {
                MapLocation centerLoc = candidate.getMapLocation();
                if (!rc.canAttack(centerLoc)) { continue; }

                int score = 0;
                for (MapInfo splashTile : rc.senseNearbyMapInfos(centerLoc, 2)) {
                    PaintType paint = splashTile.getPaint();
                    if (paint.isEnemy()) { score += 2; }
                    else if (paint == PaintType.EMPTY) { score += 1; }
                }

                if (score > bestScore) {
                    bestScore = score;
                    bestCenter = centerLoc;
                }
            }

            if (bestCenter != null) {
                rc.attack(bestCenter);
            }
        }

        if (rc.isMovementReady()) {
            Direction bestDir = null;
            int bestDensity = -1;
            MapInfo[] allTiles = rc.senseNearbyMapInfos();

            for (Direction dir : directions) {
                if (!rc.canMove(dir)) { continue; }
                MapLocation nextLoc = myLoc.add(dir);
                int density = 0;
                for (MapInfo tile : allTiles) {
                    if (tile.getPaint().isAlly()) { continue; }
                    if (tile.getMapLocation().distanceSquaredTo(nextLoc) <= 16) {
                        PaintType paint = tile.getPaint();
                        if (paint.isEnemy()) { density += 2; }
                        else if (paint == PaintType.EMPTY) { density += 1; }
                    }
                }
                density += rng.nextInt(2);
                if (density > bestDensity) {
                    bestDensity = density;
                    bestDir = dir;
                }
            }

            if (bestDir != null) { rc.move(bestDir); }
            else { explore(rc); }
        }
    }

    static UnitType decideTowerType(RobotController rc) throws GameActionException {
        int chips = rc.getChips();

        RobotInfo[] allies = rc.senseNearbyRobots(-1, rc.getTeam());
        int totalTowerPaint = 0;
        int paintTowers = 0;
        int moneyTowers = 0;
        for (RobotInfo ally : allies) {
            UnitType t = ally.type;
            if (t == UnitType.LEVEL_ONE_PAINT_TOWER || t == UnitType.LEVEL_TWO_PAINT_TOWER || t == UnitType.LEVEL_THREE_PAINT_TOWER) {
                paintTowers++;
                totalTowerPaint += ally.paintAmount;
            } else if (t == UnitType.LEVEL_ONE_MONEY_TOWER || t == UnitType.LEVEL_TWO_MONEY_TOWER || t == UnitType.LEVEL_THREE_MONEY_TOWER) {
                moneyTowers++;
            }
        }

        if (chips < 300) { return UnitType.LEVEL_ONE_MONEY_TOWER; }
        if (paintTowers == 0) { return UnitType.LEVEL_ONE_PAINT_TOWER; }
        if (totalTowerPaint < 100 && paintTowers <= moneyTowers) { return UnitType.LEVEL_ONE_PAINT_TOWER; }
        return UnitType.LEVEL_ONE_MONEY_TOWER;
    }

    static void tryUpgradeTower(RobotController rc) throws GameActionException {
        RobotInfo[] allies = rc.senseNearbyRobots(-1, rc.getTeam());
        for (RobotInfo ally : allies) {
            if (ally.type.isTowerType() && rc.canUpgradeTower(ally.location)) {
                rc.upgradeTower(ally.location);
                return;
            }
        }
    }

    static void explore(RobotController rc) throws GameActionException {
        MapLocation myLoc = rc.getLocation();

        if (myLoc.equals(lastPos)) {
            stuckCount++;
            if (stuckCount >= 3) {
                exploreDir = directions[rng.nextInt(directions.length)];
                stuckCount = 0;
            }
        } else {
            stuckCount = 0;
        }
        lastPos = myLoc;

        if (rc.canMove(exploreDir)) {
            rc.move(exploreDir);
            return;
        }

        Direction orig = exploreDir;
        for (int i = 0; i < 4; i++) {
            exploreDir = exploreDir.rotateRight();
            if (rc.canMove(exploreDir)) {
                rc.move(exploreDir);
                return;
            }
        }
        exploreDir = orig;
        for (int i = 0; i < 4; i++) {
            exploreDir = exploreDir.rotateLeft();
            if (rc.canMove(exploreDir)) {
                rc.move(exploreDir);
                return;
            }
        }
        exploreDir = orig;
    }

    static int getDeltaScore(PaintType paint) {
        if (paint.isEnemy()) { return 2; }
        else if (paint == PaintType.EMPTY) { return 1; }
        else { return 0; }
    }

    static int simSwingPaint(RobotController rc, MapLocation origin, Direction swingDir, RobotInfo[] enemies) {
        int totalPaint = 0;
        MapLocation step1 = origin.add(swingDir);
        MapLocation step2 = step1.add(swingDir);

        MapLocation[] step1Targets = {
                step1,
                step1.add(swingDir.rotateLeft()),
                step1.add(swingDir.rotateRight())
        };

        MapLocation[] step2Targets = {
                step2,
                step2.add(swingDir.rotateLeft()),
                step2.add(swingDir.rotateRight())
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