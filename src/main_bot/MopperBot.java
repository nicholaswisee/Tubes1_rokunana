package main_bot;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapInfo;
import battlecode.common.MapLocation;
import battlecode.common.PaintType;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;
import battlecode.common.UnitType;

public class MopperBot {

    public static void runMopper(RobotController rc) throws GameActionException {
        RobotInfo[] nearbyEnemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        RobotInfo[] nearbyAllies = rc.senseNearbyRobots(-1, rc.getTeam());
        MapLocation myLoc = rc.getLocation();
        MapInfo[] nearbyTiles = rc.senseNearbyMapInfos();

        RobotPlayer.tryUpgradeNearbyTower(rc);
        RobotPlayer.reportIntel(rc, nearbyTiles);

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
                for (Direction mopDir : RobotPlayer.mopperDirections) {
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
                if (!RobotPlayer.faseLateGame && ally.type == UnitType.SOLDIER) {
                    if (tryTransferPaint(rc, ally)) return;
                }
                if (RobotPlayer.faseLateGame && (ally.type == UnitType.SPLASHER || ally.type == UnitType.SOLDIER)) {
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
            } else if (RobotPlayer.needMopperLocation != null) {
                moveTarget = RobotPlayer.needMopperLocation;
            } else if (RobotPlayer.knownEnemyCluster != null) {
                moveTarget = RobotPlayer.knownEnemyCluster;
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

        if (!RobotPlayer.faseLateGame) {
            for (RobotInfo ally : nearbyAllies) {
                if (ally.type == UnitType.SOLDIER) {
                    Direction dir = myLoc.directionTo(ally.location);
                    if (rc.canMove(dir)) rc.move(dir);
                    return;
                }
            }
        }

        RobotPlayer.explore(rc);
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
