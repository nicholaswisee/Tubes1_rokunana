package alternative_bots_1.utils;

import battlecode.common.*;
import java.util.Random;

public class SoldierHeuristicUtils {
    public static Direction chooseDefensiveMoveDirection(
        RobotController rc, MapLocation currentLocation, Direction[] directions,
        Random rng) throws GameActionException {
        Direction bestDir = null;
        int bestScore = -Integer.MAX_VALUE;

        for (Direction dir : directions) {
            if (!rc.canMove(dir))
                continue;
            MapLocation nextLoc = currentLocation.add(dir);
            MapInfo nextTile = rc.senseMapInfo(nextLoc);

            int score = scoreBaseTile(nextTile.getPaint()) +
                        scoreLocalNeighborhood(rc, nextLoc, directions);

            score += rng.nextInt(3);

            if (score > bestScore) {
                bestScore = score;
                bestDir = dir;
            }
        }

        if (bestDir != null && bestScore > -20) {
            return bestDir;
        }
        return null;
    }

    public static MapLocation
    choosePaintActionTarget(RobotController rc, MapLocation currentLocation,
                            Direction[] directions) throws GameActionException {
        MapInfo currentTile = rc.senseMapInfo(currentLocation);
        if (currentTile.getPaint().isEnemy() && rc.canAttack(currentLocation)) {
            return currentLocation;
        }

        MapLocation bestEnemy = null;
        MapLocation bestEmpty = null;

        for (Direction d : directions) {
            MapLocation next = currentLocation.add(d);
            if (!rc.canSenseLocation(next) || !rc.canAttack(next))
                continue;
            PaintType p = rc.senseMapInfo(next).getPaint();
            if (p.isEnemy()) {
                bestEnemy = next;
                break;
            }
            if (p == PaintType.EMPTY && bestEmpty == null)
                bestEmpty = next;
        }

        if (bestEnemy != null) {
            return bestEnemy;
        }
        if (bestEmpty != null) {
            return bestEmpty;
        }
        if (!currentTile.getPaint().isAlly() && rc.canAttack(currentLocation)) {
            return currentLocation;
        }
        return null;
    }

    private static int scoreBaseTile(PaintType paint) {
        if (paint.isEnemy()) {
            return -70;
        }
        if (paint.isAlly()) {
            return 12;
        }
        return 2;
    }

    private static int scoreLocalNeighborhood(RobotController rc,
                                              MapLocation nextLocation,
                                              Direction[] directions)
        throws GameActionException {
        int score = 0;

        for (Direction subDir : directions) {
            MapLocation sightTile = nextLocation.add(subDir);
            if (!rc.canSenseLocation(sightTile))
                continue;

            MapInfo sightInfo = rc.senseMapInfo(sightTile);
            PaintType p = sightInfo.getPaint();

            if (p.isEnemy()) {
                int allyNeighbors = 0;
                for (Direction d2 : directions) {
                    MapLocation n = sightTile.add(d2);
                    if (rc.canSenseLocation(n) &&
                        rc.senseMapInfo(n).getPaint().isAlly()) {
                        allyNeighbors++;
                    }
                }
                if (allyNeighbors >= 2)
                    score += 8;
            } else if (p == PaintType.EMPTY && sightInfo.isPassable()) {
                score += 2;
            } else if (p.isAlly()) {
                score += 1;
            }
        }

        return score;
    }
}
