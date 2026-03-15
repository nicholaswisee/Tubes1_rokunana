package sukuna.utils;

import battlecode.common.*;
import java.util.Random;

public class MovementUtils {
    private static MapLocation lastPos = null;
    private static int stuckCount = 0;
    private static Direction exploreDir = null;

    public static void moveToward(RobotController rc, MapLocation target,
                                  Direction[] directions)
        throws GameActionException {
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

    public static void explore(RobotController rc, Direction[] directions,
                               Random rng) throws GameActionException {
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
}
