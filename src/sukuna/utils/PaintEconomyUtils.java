package sukuna.utils;

import battlecode.common.*;

public class PaintEconomyUtils {
  public static void tryRefill(RobotController rc, Direction[] directions)
      throws GameActionException {
    RobotInfo[] allies = rc.senseNearbyRobots(-1, rc.getTeam());
    RobotInfo bestTower = null;
    int bestDist = Integer.MAX_VALUE;
    MapLocation myLoc = rc.getLocation();

    for (RobotInfo ally : allies) {
      if (ally.type.isTowerType() && ally.paintAmount > 0) {
        int d = myLoc.distanceSquaredTo(ally.location);
        if (d < bestDist) {
          bestDist = d;
          bestTower = ally;
        }
      }
    }

    if (bestTower == null)
      return;

    MovementUtils.moveToward(rc, bestTower.location, directions);
    int toTake = Math.min(rc.getType().paintCapacity - rc.getPaint(),
        bestTower.paintAmount);
    if (toTake > 0 && rc.canTransferPaint(bestTower.location, -toTake)) {
      rc.transferPaint(bestTower.location, -toTake);
    }
  }
}
