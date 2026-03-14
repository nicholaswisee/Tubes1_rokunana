package sukuna;

import battlecode.common.*;
import java.util.Random;

public class BotUtils {
  static MapLocation lastPos = null;
  static int stuckCount = 0;
  static Direction exploreDir = null;

  static void buildWithFallback(RobotController rc, UnitType first,
      UnitType second, UnitType third, Direction[] directions)
      throws GameActionException {
    UnitType[] order = { first, second, third };
    for (UnitType type : order) {
      for (Direction dir : directions) {
        MapLocation loc = rc.getLocation().add(dir);
        if (rc.canBuildRobot(type, loc)) {
          rc.buildRobot(type, loc);
          return;
        }
      }
    }
  }

  static void tryRefill(RobotController rc, Direction[] directions)
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

    moveToward(rc, bestTower.location, directions);
    int toTake = Math.min(rc.getType().paintCapacity - rc.getPaint(), bestTower.paintAmount);
    if (toTake > 0 && rc.canTransferPaint(bestTower.location, -toTake)) {
      rc.transferPaint(bestTower.location, -toTake);
    }
  }

  static void moveToward(RobotController rc, MapLocation target,
      Direction[] directions) throws GameActionException {
    if (!rc.isMovementReady())
      return;

    MapLocation myLoc = rc.getLocation();
    Direction bestDir = null;
    int bestDist = Integer.MAX_VALUE;

    for (Direction dir : directions) {
      if (!rc.canMove(dir))
        continue;
      int dist = myLoc.add(dir).distanceSquaredTo(target);
      if (dist < bestDist) {
        bestDist = dist;
        bestDir = dir;
      }
    }

    if (bestDir != null)
      rc.move(bestDir);
  }

  static void explore(RobotController rc, Direction[] directions, Random rng)
      throws GameActionException {
    if (!rc.isMovementReady())
      return;

    if (exploreDir == null) {
      exploreDir = directions[rng.nextInt(directions.length)];
    }

    MapLocation currLoc = rc.getLocation();
    if (currLoc.equals(lastPos)) {
      if (++stuckCount >= 3) {
        exploreDir = directions[rng.nextInt(directions.length)];
        stuckCount = 0;
      }
    } else {
      stuckCount = 0;
    }
    lastPos = currLoc;

    if (rc.canMove(exploreDir)) {
      rc.move(exploreDir);
      return;
    }

    Direction origin = exploreDir;
    for (int i = 0; i < 4; i++) {
      exploreDir = exploreDir.rotateRight();
      if (rc.canMove(exploreDir)) {
        rc.move(exploreDir);
        return;
      }
    }
    exploreDir = origin;
  }

  static void randomMove(RobotController rc, Direction[] directions, Random rng)
      throws GameActionException {
    if (!rc.isMovementReady())
      return;

    for (int i = 0; i < directions.length; i++) {
      Direction dir = directions[rng.nextInt(directions.length)];
      if (rc.canMove(dir)) {
        rc.move(dir);
        return;
      }
    }
  }
}
