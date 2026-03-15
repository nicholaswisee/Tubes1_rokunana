package sukuna.utils;

import battlecode.common.*;
import java.util.Random;

public class SpawnUtils {
    public static UnitType
    decideTowerSpawn(RobotController rc, RobotInfo[] enemies,
                     RobotInfo[] allies, MapInfo[] nearby, boolean opening,
                     Random rng, int splasherPaintCost, int splasherChipCost,
                     int splasherBankPaint, int splasherBankChips)
        throws GameActionException {
        int enemyPaintNearCount = 0;
        MapLocation towerLoc = rc.getLocation();
        for (MapInfo tile : nearby) {
            if (tile.getPaint().isEnemy() &&
                towerLoc.distanceSquaredTo(tile.getMapLocation()) <= 16) {
                enemyPaintNearCount++;
            }
        }

        int enemyMopperNear = 0;
        for (RobotInfo enemy : enemies) {
            if (enemy.type == UnitType.MOPPER &&
                towerLoc.distanceSquaredTo(enemy.location) <= 25) {
                enemyMopperNear++;
            }
        }

        int allyMopperNear = 0;
        for (RobotInfo ally : allies) {
            if (ally.type == UnitType.MOPPER &&
                towerLoc.distanceSquaredTo(ally.location) <= 25) {
                allyMopperNear++;
            }
        }

        boolean nearEnemySpace =
            enemyPaintNearCount >= 2 || enemyMopperNear > 0;
        boolean heavyEnemySpace =
            enemyPaintNearCount >= 4 || enemyMopperNear > 0;

        UnitType toSpawn = UnitType.SOLDIER;
        int roll = rng.nextInt(100);

        // Keep splasher logic as strategic expander (opening-biased +
        // economy-aware).
        if (opening) {
            toSpawn = (roll < 75) ? UnitType.SPLASHER : UnitType.SOLDIER;
        } else {
            if (nearEnemySpace) {
                toSpawn = (roll < 70) ? UnitType.SOLDIER : UnitType.SPLASHER;
            } else {
                toSpawn = (roll < 55) ? UnitType.SPLASHER : UnitType.SOLDIER;
            }
        }

        boolean canAffordSplasher = rc.getPaint() >= splasherPaintCost &&
                                    rc.getMoney() >= splasherChipCost;

        if (opening && !canAffordSplasher) {
            boolean nearSplasherBank = rc.getPaint() >= splasherBankPaint &&
                                       rc.getMoney() >= splasherBankChips;

            if (!nearSplasherBank) {
                toSpawn = (roll < 70) ? null : UnitType.SOLDIER;
            } else {
                toSpawn = UnitType.SOLDIER;
            }
        }

        // Affordability-aware fallback for non-opening rounds as well.
        if (!opening && toSpawn == UnitType.SPLASHER && !canAffordSplasher) {
            toSpawn = nearEnemySpace ? UnitType.SOLDIER : null;
        }

        // Minimal mopper spawn, cleanup
        if (nearEnemySpace && heavyEnemySpace && allyMopperNear == 0) {
            int mopperRoll = rng.nextInt(100);
            int mopperChance = opening ? 10 : 15;
            if (mopperRoll < mopperChance) {
                toSpawn = UnitType.MOPPER;
            }
        }

        return toSpawn;
    }

    public static void buildWithFallback(RobotController rc, UnitType first,
                                         UnitType second, UnitType third,
                                         Direction[] directions)
        throws GameActionException {
        UnitType[] order = {first, second, third};
        for (UnitType type : order) {
            for (Direction dir : directions) {
                MapLocation loc = rc.adjacentLocation(dir);
                if (rc.canBuildRobot(type, loc)) {
                    rc.buildRobot(type, loc);
                    return;
                }
            }
        }
    }

    public static void
    buildWithPrimarySecondary(RobotController rc, UnitType first,
                              UnitType second, Direction[] directions)
        throws GameActionException {
        UnitType[] order = {first, second};
        for (UnitType type : order) {
            for (Direction dir : directions) {
                MapLocation loc = rc.adjacentLocation(dir);
                if (rc.canBuildRobot(type, loc)) {
                    rc.buildRobot(type, loc);
                    return;
                }
            }
        }
    }
}
