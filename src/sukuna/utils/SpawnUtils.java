package sukuna.utils;

import battlecode.common.*;

public class SpawnUtils {
  public static void buildWithFallback(RobotController rc, UnitType first,
      UnitType second, UnitType third, Direction[] directions)
      throws GameActionException {
    UnitType[] order = { first, second, third };
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
