package sukuna.utils;

import battlecode.common.*;

public class RuinUtils {
    public static MapLocation findPriorityRuinToFinish(RobotController rc,
                                                       MapInfo[] nearby,
                                                       RobotInfo[] enemies)
        throws GameActionException {
        MapLocation optimal = null;
        int optimalScore = Integer.MIN_VALUE;
        MapLocation currLoc = rc.getLocation();

        for (MapInfo tile : nearby) {
            if (!tile.hasRuin())
                continue;

            MapLocation ruinLoc = tile.getMapLocation();
            if (rc.canSenseRobotAtLocation(ruinLoc))
                continue;

            boolean contested = false;
            for (RobotInfo e : enemies) {
                if (e.location.distanceSquaredTo(ruinLoc) <= 16) {
                    contested = true;
                    break;
                }
            }

            if (contested)
                continue;

            if (rc.canCompleteTowerPattern(UnitType.LEVEL_ONE_MONEY_TOWER,
                                           ruinLoc) ||
                rc.canCompleteTowerPattern(UnitType.LEVEL_ONE_PAINT_TOWER,
                                           ruinLoc))
                return ruinLoc;

            int progress = 0;
            for (MapInfo patternTile : rc.senseNearbyMapInfos(ruinLoc, 8)) {
                PaintType mark = patternTile.getMark();
                if (mark != PaintType.EMPTY) {
                    if (mark == patternTile.getPaint()) {
                        progress += 3;
                    } else {
                        progress += 1;
                    }
                }
            }

            if (progress == 0)
                continue;

            int score = progress - currLoc.distanceSquaredTo(ruinLoc);
            if (score > optimalScore) {
                optimalScore = score;
                optimal = ruinLoc;
            }
        }
        return optimal;
    }

    public static UnitType inferPreferredTowerType(RobotController rc,
                                                   MapLocation ruinLoc)
        throws GameActionException {
        int mismatchMarks = 0;
        for (MapInfo tile : rc.senseNearbyMapInfos(ruinLoc, 8)) {
            PaintType mark = tile.getMark();
            if (mark != PaintType.EMPTY) {
                mismatchMarks++;
            }
        }

        if (mismatchMarks == 0) {
            return UnitType.LEVEL_ONE_MONEY_TOWER;
        }
        if (rc.canCompleteTowerPattern(UnitType.LEVEL_ONE_PAINT_TOWER,
                                       ruinLoc)) {
            return UnitType.LEVEL_ONE_PAINT_TOWER;
        }

        return UnitType.LEVEL_ONE_MONEY_TOWER;
    }

    public static boolean
    executeBuildRoutine(RobotController rc, MapLocation ruinLoc,
                        UnitType towerType, Direction[] directions)
        throws GameActionException {
        if (towerType == null)
            towerType = UnitType.LEVEL_ONE_MONEY_TOWER;

        if (rc.canMarkTowerPattern(towerType, ruinLoc)) {
            rc.markTowerPattern(towerType, ruinLoc);
        }
        if (rc.isActionReady()) {
            for (MapInfo tile : rc.senseNearbyMapInfos(ruinLoc, 8)) {
                if (tile.getMark() != PaintType.EMPTY &&
                    tile.getMark() != tile.getPaint()) {
                    boolean sec = (tile.getMark() == PaintType.ALLY_SECONDARY);
                    if (rc.canAttack(tile.getMapLocation())) {
                        rc.attack(tile.getMapLocation(), sec);
                        break;
                    }
                }
            }
        }
        if (tryCompleteAnyLevelOnePattern(rc, ruinLoc, towerType)) {
            return true;
        }

        if (rc.isMovementReady()) {
            MovementUtils.moveToward(rc, ruinLoc, directions);
        }

        return false;
    }

    public static boolean tryCompleteAnyLevelOnePattern(RobotController rc,
                                                        MapLocation ruinLoc,
                                                        UnitType preferredType)
        throws GameActionException {
        if (preferredType != null &&
            rc.canCompleteTowerPattern(preferredType, ruinLoc)) {
            rc.completeTowerPattern(preferredType, ruinLoc);
            return true;
        }

        UnitType alt = (preferredType == UnitType.LEVEL_ONE_PAINT_TOWER)
                           ? UnitType.LEVEL_ONE_MONEY_TOWER
                           : UnitType.LEVEL_ONE_PAINT_TOWER;
        if (rc.canCompleteTowerPattern(alt, ruinLoc)) {
            rc.completeTowerPattern(alt, ruinLoc);
            return true;
        }
        return false;
    }
}
