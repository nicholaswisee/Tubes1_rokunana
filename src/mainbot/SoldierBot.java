package mainbot;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapInfo;
import battlecode.common.MapLocation;
import battlecode.common.PaintType;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;
import battlecode.common.UnitType;

public class SoldierBot {

    public static void runSoldier(RobotController rc) throws GameActionException {
        MapInfo[] nearbyTiles = rc.senseNearbyMapInfos();

        RobotPlayer.tryUpgradeNearbyTower(rc);

        RobotPlayer.reportIntel(rc, nearbyTiles);

        if (RobotPlayer.currentTargetRuin != null) {
            if (rc.canSenseLocation(RobotPlayer.currentTargetRuin)) {
                RobotInfo robotAtRuin = rc.senseRobotAtLocation(RobotPlayer.currentTargetRuin);
                if (robotAtRuin != null) {
                    RobotPlayer.currentTargetRuin = null;
                    RobotPlayer.currentTowerBuildType = null;
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

        if (bestRuin == null && RobotPlayer.knownRuinLocation != null) {
            bestRuin = RobotPlayer.knownRuinLocation;
        }

        if (bestRuin != null) {
            if (RobotPlayer.currentTargetRuin == null || !RobotPlayer.currentTargetRuin.equals(bestRuin)) {
                RobotPlayer.currentTargetRuin = bestRuin;
                RobotPlayer.currentTowerBuildType = decideTowerType(rc);
            }

            MapLocation targetLoc = RobotPlayer.currentTargetRuin;
            UnitType towerType = RobotPlayer.currentTowerBuildType;

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
                RobotPlayer.currentTargetRuin = null;
                RobotPlayer.currentTowerBuildType = null;
                RobotPlayer.knownRuinLocation = null;
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

        if (!RobotPlayer.faseLateGame) {
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
            RobotPlayer.explore(rc);
            if (rc.isActionReady()) {
                MapLocation curLoc = rc.getLocation();
                MapInfo currentTile = rc.senseMapInfo(curLoc);
                if (!currentTile.getPaint().isAlly() && !currentTile.getPaint().isEnemy()
                    && rc.canAttack(curLoc)) {
                    rc.attack(curLoc);
                }
            }
        } else {
            Direction bestDir = RobotPlayer.getGreedyPaintDirection(rc, nearbyTiles);
            if (bestDir != null && rc.canMove(bestDir)) {
                rc.move(bestDir);
            } else {
                RobotPlayer.explore(rc);
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
}
