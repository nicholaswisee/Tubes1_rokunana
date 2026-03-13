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

    static final MapLocation[] sBufLocs = new MapLocation[100];
    static final int[] sBufWeights = new int[100];
    static final MapLocation[] sCandidates = new MapLocation[25];

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
            turnCount++;
            try {
                switch (rc.getType()) {
                    case SOLDIER:
                        runSoldier(rc);
                        break;
                    case MOPPER:
                        runMopper(rc);
                        break;
                    case SPLASHER:
                        runSplasher(rc);
                        break;
                    default:
                        runTower(rc);
                        break;
                }
            } catch (GameActionException e) {
                System.out.println("GameActionException"); e.printStackTrace();
            } catch (Exception e) {
                System.out.println("Exception"); e.printStackTrace();
            } finally {
                Clock.yield();
            }
        }
    }

    public static void runTower(RobotController rc) throws GameActionException {
        RobotInfo[] nearbyEnemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        if (nearbyEnemies.length > 0 && rc.canAttack(nearbyEnemies[0].location)) {
            rc.attack(nearbyEnemies[0].location);
        }

        MapInfo[] nearbyTiles = rc.senseNearbyMapInfos();
        int n = nearbyTiles.length;
        int enemyTileCount = 0, neutralTileCount = 0;
        for (int i = 0; i < n; i++) {
            PaintType paint = nearbyTiles[i].getPaint();
            if (paint.isEnemy()) { enemyTileCount++; }
            else if (paint == PaintType.EMPTY) { neutralTileCount++; }
        }

        UnitType typeToBuild;
        if (enemyTileCount > 2) { typeToBuild = UnitType.MOPPER;   }
        else if (neutralTileCount > 3) { typeToBuild = UnitType.SPLASHER; }
        else { typeToBuild = UnitType.SOLDIER;  }

        MapLocation myLoc = rc.getLocation();
        boolean spawned = false;
        for (int d = 0; d < 8; d++) {
            MapLocation spawnLoc = myLoc.add(directions[d]);
            if (rc.canBuildRobot(typeToBuild, spawnLoc)) {
                rc.buildRobot(typeToBuild, spawnLoc);
                spawned = true;
                break;
            }
        }
        if (!spawned && typeToBuild != UnitType.SOLDIER) {
            for (int d = 0; d < 8; d++) {
                MapLocation spawnLoc = myLoc.add(directions[d]);
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
        int nTiles = nearbyTiles.length;

        RobotInfo[] allies = rc.senseNearbyRobots(-1, rc.getTeam());

        MapLocation targetRuin = null;
        for (int i = 0; i < nTiles; i++) {
            MapInfo tile = nearbyTiles[i];
            if (tile.hasRuin()) {
                MapLocation ruinLoc = tile.getMapLocation();
                if (rc.senseRobotAtLocation(ruinLoc) == null) {
                    targetRuin = ruinLoc;
                    break;
                }
            }
        }

        if (targetRuin != null) {
            if (currentTargetRuin == null || !currentTargetRuin.equals(targetRuin)) {
                currentTargetRuin = targetRuin;
                currentTowerType = decideTowerType(rc, allies);
            }
            UnitType towerType = currentTowerType;

            if (rc.canMarkTowerPattern(towerType, targetRuin)) {
                rc.markTowerPattern(towerType, targetRuin);
            }

            if (rc.isActionReady()) {
                for (int i = 0; i < nTiles; i++) {
                    MapInfo pt = nearbyTiles[i];
                    PaintType mark = pt.getMark();
                    if (mark != PaintType.EMPTY && mark != pt.getPaint()) {
                        MapLocation ptLoc = pt.getMapLocation();
                        if (rc.canAttack(ptLoc)) {
                            rc.attack(ptLoc, mark == PaintType.ALLY_SECONDARY);
                            break;
                        }
                    }
                }
            }

            if (rc.canCompleteTowerPattern(towerType, targetRuin)) {
                rc.completeTowerPattern(towerType, targetRuin);
                currentTargetRuin = null;
                currentTowerType  = null;
            }

            tryUpgradeTower(rc, allies);

            if (rc.isMovementReady()) {
                Direction dir = myLoc.directionTo(targetRuin);
                if (rc.canMove(dir)) { rc.move(dir); }
                else if (rc.canMove(dir.rotateLeft())) { rc.move(dir.rotateLeft()); }
                else if (rc.canMove(dir.rotateRight())) { rc.move(dir.rotateRight()); }
            }
            return;
        }

        tryUpgradeTower(rc, allies);

        if (rc.getPaint() < 40) {
            RobotInfo nearestTower = null;
            int bestDist = Integer.MAX_VALUE;
            for (int i = 0; i < allies.length; i++) {
                RobotInfo ally = allies[i];
                if (ally.type.isTowerType() && ally.paintAmount > 0) {
                    int d = myLoc.distanceSquaredTo(ally.location);
                    if (d < bestDist) { bestDist = d; nearestTower = ally; }
                }
            }
            if (nearestTower != null) {
                if (rc.isMovementReady()) {
                    Direction dir = myLoc.directionTo(nearestTower.location);
                    if (rc.canMove(dir)) { rc.move(dir); }
                    else if (rc.canMove(dir.rotateLeft())) { rc.move(dir.rotateLeft()); }
                    else if (rc.canMove(dir.rotateRight())) { rc.move(dir.rotateRight()); }
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

        int bufCount = 0;
        for (int i = 0; i < nTiles; i++) {
            MapInfo   tile  = nearbyTiles[i];
            PaintType paint = tile.getPaint();
            if (paint.isAlly()) continue;
            sBufLocs[bufCount] = tile.getMapLocation();
            sBufWeights[bufCount] = paint.isEnemy() ? 2 : 1;
            bufCount++;
        }

        if (rc.isActionReady()) {
            MapLocation bestEnemyLoc = null, bestNeutralLoc = null;
            for (int i = 0; i < bufCount; i++) {
                MapLocation loc = sBufLocs[i];
                if (!rc.canAttack(loc)) continue;
                int w = sBufWeights[i];
                if (w == 2 && bestEnemyLoc == null) {
                    bestEnemyLoc = loc;
                    if (bestNeutralLoc != null) break;
                } else if (w == 1 && bestNeutralLoc == null) {
                    bestNeutralLoc = loc;
                    if (bestEnemyLoc != null) break;
                }
            }
            if (bestEnemyLoc != null) { rc.attack(bestEnemyLoc); }
            else if (bestNeutralLoc != null) { rc.attack(bestNeutralLoc); }
            else if (rc.canAttack(myLoc) && !rc.senseMapInfo(myLoc).getPaint().isAlly()) {
                rc.attack(myLoc);
            }
        }

        if (rc.isMovementReady()) {
            Direction bestDir   = null;
            int bestScore = 0;
            for (int d = 0; d < 8; d++) {
                Direction dir = directions[d];
                if (!rc.canMove(dir)) continue;
                MapLocation nextLoc = myLoc.add(dir);
                int score = 0;
                for (int i = 0; i < bufCount; i++) {
                    if (sBufLocs[i].distanceSquaredTo(nextLoc) <= 3) {
                        score += sBufWeights[i];
                    }
                }
                score += rng.nextInt(3);
                if (score > bestScore) { bestScore = score; bestDir = dir; }
            }
            if (bestDir != null) { rc.move(bestDir); }
            else { explore(rc); }
        }

        if (rc.isActionReady()) {
            MapLocation curLoc = rc.getLocation();
            if (rc.canAttack(curLoc) && !rc.senseMapInfo(curLoc).getPaint().isAlly()) {
                rc.attack(curLoc);
            }
        }
    }

    public static void runMopper(RobotController rc) throws GameActionException {
        MapLocation myLoc = rc.getLocation();

        if (rc.getPaint() < 20) {
            RobotInfo[] allies = rc.senseNearbyRobots(-1, rc.getTeam());
            for (int i = 0; i < allies.length; i++) {
                RobotInfo ally = allies[i];
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
            for (int i = 0; i < enemies.length; i++) {
                if (enemies[i].paintAmount > maxPaint) {
                    maxPaint = enemies[i].paintAmount;
                    richestEnemy = enemies[i];
                }
            }

            if (rc.isActionReady()) {
                Direction bestSwingDir = null;
                int bestSwingPaint = 0;
                for (int i = 0; i < 4; i++) {
                    Direction mopDir = mopperDirections[i];
                    if (!rc.canMopSwing(mopDir)) continue;
                    int tp = simSwingPaint(myLoc, mopDir, enemies);
                    if (tp > bestSwingPaint) { bestSwingPaint = tp; bestSwingDir = mopDir; }
                }
                if (bestSwingDir != null && bestSwingPaint > 0) {
                    rc.mopSwing(bestSwingDir);
                } else if (richestEnemy != null && rc.canAttack(richestEnemy.location)) {
                    rc.attack(richestEnemy.location);
                }
            }

            if (rc.isMovementReady() && richestEnemy != null) {
                Direction dir = myLoc.directionTo(richestEnemy.location);
                if (rc.canMove(dir)) { rc.move(dir); }
                else if (rc.canMove(dir.rotateLeft())) { rc.move(dir.rotateLeft()); }
                else if (rc.canMove(dir.rotateRight())) { rc.move(dir.rotateRight()); }
            }

        } else {
            if (rc.isMovementReady()) {
                MapInfo[] nearbyTiles = rc.senseNearbyMapInfos();
                int nTiles = nearbyTiles.length;
                Direction bestDir = null;
                int bestCount = -1;
                for (int d = 0; d < 8; d++) {
                    Direction dir = directions[d];
                    if (!rc.canMove(dir)) continue;
                    MapLocation nextLoc = myLoc.add(dir);
                    int count = 0;
                    for (int i = 0; i < nTiles; i++) {
                        MapInfo tile = nearbyTiles[i];
                        if (tile.getPaint().isEnemy() && tile.getMapLocation().distanceSquaredTo(nextLoc) <= 8) {
                            count++;
                        }
                    }
                    count += rng.nextInt(2);
                    if (count > bestCount) { bestCount = count; bestDir = dir; }
                }
                if (bestDir != null) { rc.move(bestDir); }
                else { explore(rc); }
            }
        }
    }

    public static void runSplasher(RobotController rc) throws GameActionException {
        MapLocation myLoc = rc.getLocation();

        if (rc.getPaint() < 60) {
            RobotInfo[] allies = rc.senseNearbyRobots(-1, rc.getTeam());
            RobotInfo nearestTower = null;
            int bestDist = Integer.MAX_VALUE;
            for (int i = 0; i < allies.length; i++) {
                RobotInfo ally = allies[i];
                if (ally.type.isTowerType() && ally.paintAmount > 0) {
                    int d = myLoc.distanceSquaredTo(ally.location);
                    if (d < bestDist) { bestDist = d; nearestTower = ally; }
                }
            }
            if (nearestTower != null) {
                if (rc.isMovementReady()) {
                    Direction dir = myLoc.directionTo(nearestTower.location);
                    if (rc.canMove(dir)) { rc.move(dir); }
                    else if (rc.canMove(dir.rotateLeft())) { rc.move(dir.rotateLeft()); }
                    else if (rc.canMove(dir.rotateRight())) { rc.move(dir.rotateRight()); }
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

        MapInfo[] allTiles = rc.senseNearbyMapInfos();
        int nTiles = allTiles.length;
        int bufCount = 0, candidateCount = 0;
        for (int i = 0; i < nTiles; i++) {
            MapInfo tile = allTiles[i];
            MapLocation loc = tile.getMapLocation();
            PaintType paint = tile.getPaint();
            if (!paint.isAlly()) {
                sBufLocs[bufCount] = loc;
                sBufWeights[bufCount] = paint.isEnemy() ? 2 : 1;
                bufCount++;
            }
            if (myLoc.distanceSquaredTo(loc) <= 4) {
                sCandidates[candidateCount++] = loc;
            }
        }

        if (rc.isActionReady()) {
            MapLocation bestCenter = null;
            int bestScore  = 1;
            for (int ci = 0; ci < candidateCount; ci++) {
                MapLocation centerLoc = sCandidates[ci];
                if (!rc.canAttack(centerLoc)) continue;
                int score = 0;
                for (int i = 0; i < bufCount; i++) {
                    if (sBufLocs[i].distanceSquaredTo(centerLoc) <= 2) {
                        score += sBufWeights[i];
                    }
                }
                if (score > bestScore) { bestScore = score; bestCenter = centerLoc; }
            }
            if (bestCenter != null) { rc.attack(bestCenter); }
        }

        if (rc.isMovementReady()) {
            Direction bestDir = null;
            int bestDensity = -1;
            for (int d = 0; d < 8; d++) {
                Direction dir = directions[d];
                if (!rc.canMove(dir)) continue;
                MapLocation nextLoc = myLoc.add(dir);
                int density = 0;
                for (int i = 0; i < bufCount; i++) {
                    if (sBufLocs[i].distanceSquaredTo(nextLoc) <= 16) {
                        density += sBufWeights[i];
                    }
                }
                density += rng.nextInt(2);
                if (density > bestDensity) { bestDensity = density; bestDir = dir; }
            }
            if (bestDir != null) { rc.move(bestDir); }
            else                 { explore(rc); }
        }
    }


    static UnitType decideTowerType(RobotController rc, RobotInfo[] allies) throws GameActionException {
        int chips = rc.getChips();
        int totalTowerPaint = 0, paintTowers = 0, moneyTowers = 0;
        for (int i = 0; i < allies.length; i++) {
            UnitType t = allies[i].type;
            if (t == UnitType.LEVEL_ONE_PAINT_TOWER || t == UnitType.LEVEL_TWO_PAINT_TOWER  || t == UnitType.LEVEL_THREE_PAINT_TOWER) {
                paintTowers++;
                totalTowerPaint += allies[i].paintAmount;
            } else if (t == UnitType.LEVEL_ONE_MONEY_TOWER || t == UnitType.LEVEL_TWO_MONEY_TOWER  || t == UnitType.LEVEL_THREE_MONEY_TOWER) {
                moneyTowers++;
            }
        }
        if (chips < 300) return UnitType.LEVEL_ONE_MONEY_TOWER;
        if (paintTowers == 0) return UnitType.LEVEL_ONE_PAINT_TOWER;
        if (totalTowerPaint < 100 && paintTowers <= moneyTowers) return UnitType.LEVEL_ONE_PAINT_TOWER;
        return UnitType.LEVEL_ONE_MONEY_TOWER;
    }

    static void tryUpgradeTower(RobotController rc, RobotInfo[] allies) throws GameActionException {
        for (int i = 0; i < allies.length; i++) {
            RobotInfo ally = allies[i];
            if (ally.type.isTowerType() && rc.canUpgradeTower(ally.location)) {
                rc.upgradeTower(ally.location);
                return;
            }
        }
    }

    static void explore(RobotController rc) throws GameActionException {
        MapLocation myLoc = rc.getLocation();
        if (myLoc.equals(lastPos)) {
            if (++stuckCount >= 3) {
                exploreDir = directions[rng.nextInt(directions.length)];
                stuckCount = 0;
            }
        } else {
            stuckCount = 0;
        }
        lastPos = myLoc;

        if (rc.canMove(exploreDir)) { rc.move(exploreDir); return; }

        Direction orig = exploreDir;
        for (int i = 0; i < 4; i++) {
            exploreDir = exploreDir.rotateRight();
            if (rc.canMove(exploreDir)) { rc.move(exploreDir); return; }
        }
        exploreDir = orig;
        for (int i = 0; i < 4; i++) {
            exploreDir = exploreDir.rotateLeft();
            if (rc.canMove(exploreDir)) { rc.move(exploreDir); return; }
        }
        exploreDir = orig;
    }

    static int simSwingPaint(MapLocation origin, Direction swingDir, RobotInfo[] enemies) {
        MapLocation s1 = origin.add(swingDir);
        MapLocation s2 = s1.add(swingDir);
        Direction sl = swingDir.rotateLeft();
        Direction sr = swingDir.rotateRight();
        MapLocation s1l = s1.add(sl), s1r = s1.add(sr);
        MapLocation s2l = s2.add(sl), s2r = s2.add(sr);
        int total = 0;
        for (int i = 0; i < enemies.length; i++) {
            MapLocation e = enemies[i].location;
            if (e.equals(s1) || e.equals(s1l) || e.equals(s1r) || e.equals(s2) || e.equals(s2l) || e.equals(s2r)) {
                total += enemies[i].paintAmount;
            }
        }
        return total;
    }
}