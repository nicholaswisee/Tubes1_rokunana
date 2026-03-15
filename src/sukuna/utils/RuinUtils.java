package sukuna.utils;

import battlecode.common.*;

public class RuinUtils {
  public static MapLocation findPriorityRuinToFinish(RobotController rc,
      MapInfo[] nearby,
      RobotInfo[] enemies) throws GameActionException {
    MapLocation optimal = null;
    int optimalScore = Integer.MIN_VALUE;
    MapLocation currLoc = rc.getLocation();

    for (MapInfo tile : nearby) {
      if (!tile.hasRuin())
        continue;

      MapLocation ruinLoc = tile.getMapLocation();
      if (rc.senseRobotAtLocation(ruinLoc) != null)
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

      if (rc.canCompleteTowerPattern(UnitType.LEVEL_ONE_MONEY_TOWER, ruinLoc) ||
          rc.canCompleteTowerPattern(UnitType.LEVEL_ONE_PAINT_TOWER, ruinLoc))
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
        if (progress == 0)
          continue;

        int score = progress - currLoc.distanceSquaredTo(ruinLoc);
        if (score > optimalScore) {
          optimalScore = score;
          optimal = ruinLoc;
        }
      }
    }
    return optimal;
  }

  public static UnitType inferPreferredTowerType(RobotController rc,
      MapLocation ruinLoc) throws GameActionException {
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
    if (rc.canCompleteTowerPattern(UnitType.LEVEL_ONE_PAINT_TOWER, ruinLoc)) {
      return UnitType.LEVEL_ONE_PAINT_TOWER;
    }

    return UnitType.LEVEL_ONE_MONEY_TOWER;
  }
}
