package mainbot;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapInfo;
import battlecode.common.MapLocation;
import battlecode.common.PaintType;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;

public class SplasherBot {

    public static void runSplasher(RobotController rc) throws GameActionException {
        MapInfo[] nearbyTiles = rc.senseNearbyMapInfos();
        MapLocation myLoc = rc.getLocation();

        RobotPlayer.tryUpgradeNearbyTower(rc);
        RobotPlayer.reportIntel(rc, nearbyTiles);

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

            for (Direction dir : RobotPlayer.directions) {
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

                if (RobotPlayer.knownEnemyCluster != null) {
                    int currDist = myLoc.distanceSquaredTo(RobotPlayer.knownEnemyCluster);
                    int newDist = nextLoc.distanceSquaredTo(RobotPlayer.knownEnemyCluster);
                    if (newDist < currDist) density += 3;
                }

                density += RobotPlayer.rng.nextInt(2);
                if (density > bestDensity) {
                    bestDensity = density;
                    bestDir = dir;
                }
            }
            if (bestDir != null) rc.move(bestDir);
            else RobotPlayer.explore(rc);
        }
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
}
