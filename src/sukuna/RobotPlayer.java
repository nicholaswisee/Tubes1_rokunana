package sukuna;

import battlecode.common.*;
import java.util.Random;
import org.apache.lucene.search.Explanation;
import org.hibernate.dialect.MySQLInnoDBDialect;

public class RobotPlayer {

    static final Random rng = new Random(6147);
    static int turnCount = 0;
    static final int SPLASHER_OPENING_ROUND = 800;
    static MapLocation currentTargetRuin = null;
    static UnitType currentTowerType = null;
    static MapLocation lastPos = null;
    static int stuckCount = 0;
    static Direction exploreDir = null;

    static final Direction[] directions = {
        Direction.NORTH,     Direction.NORTHEAST, Direction.EAST,
        Direction.SOUTHEAST, Direction.SOUTH,     Direction.SOUTHWEST,
        Direction.WEST,      Direction.NORTHWEST,
    };

    @SuppressWarnings("unused")
    public static void run(RobotController rc) throws GameActionException {
        while (true) {
            turnCount++;
            try {
                RobotInfo[] enemies =
                    rc.senseNearbyRobots(-1, rc.getTeam().opponent());
                MapInfo[] nearby = rc.senseNearbyMapInfos();

                switch (rc.getType()) {
                case SOLDIER:
                    runSoldier(rc, enemies, nearby);
                    break;
                case MOPPER:
                    runMopper(rc, enemies, nearby);
                    break;
                case SPLASHER:
                    runSplasher(rc, nearby);
                    break;
                default:
                    runTower(rc, enemies, nearby);
                    break;
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                Clock.yield();
            }
        }
    }

    // Attack weakest enemy, spam soldiers and defend with moppers
    static void runTower(RobotController rc, RobotInfo[] enemies,
                         MapInfo[] nearby) throws GameActionException {

        RobotInfo weakest = null;
        int minHealth = Integer.MAX_VALUE;

        for (RobotInfo e : enemies) {
            if (e.health < minHealth) {
                minHealth = e.health;
                weakest = e;
            }
        }

        if (weakest != null && rc.canAttack(weakest.location)) {
            rc.attack(weakest.location);
        }

        // Spawn against enemy
        int enemyPaintNearCount = 0;
        for (MapInfo t : nearby) {
            if (t.getPaint().isEnemy() &&
                rc.getLocation().distanceSquaredTo(t.getMapLocation()) <= 16) {
                enemyPaintNearCount++;
            }
        }

        boolean opening = rc.getRoundNum() <= SPLASHER_OPENING_ROUND;
        boolean enemyThreatNear =
            opening ? (enemyPaintNearCount >= 3) : (enemyPaintNearCount >= 2);

        // spawning
        UnitType toSpawn = UnitType.SPLASHER;
        int roll = rng.nextInt(10);
        if (opening) {
            if (enemyThreatNear) {
                if (roll < 4)
                    toSpawn = UnitType.MOPPER;
                else if (roll < 8)
                    toSpawn = UnitType.SPLASHER;
            } else {
                if (roll < 7)
                    toSpawn = UnitType.SPLASHER;
                else if (roll == 9)
                    toSpawn = UnitType.MOPPER;
            }
        } else {
            if (enemyThreatNear) {
                if (roll < 4)
                    toSpawn = UnitType.SOLDIER;
                else if (roll < 8)
                    toSpawn = UnitType.MOPPER;
                else
                    toSpawn = UnitType.SPLASHER;
            } else {
                if (roll < 5)
                    toSpawn = UnitType.SPLASHER;
                else if (roll == 9)
                    toSpawn = UnitType.MOPPER;
            }
        }
    }

    // Domain Expansion souljaahhh
    static void runSoldier(RobotController rc, RobotInfo[] enemies,
                           MapInfo[] nearby) throws GameActionException {
        if (rc.getPaint() < 20) {
            tryRefill(rc);
            return;
        }

        MapLocation currLocation = rc.getLocation();

        // Ruin defense
        if (currentTargetRuin == null) {
            // clear target if built
            if (rc.canSenseLocation(currentTargetRuin) &&
                rc.senseRobotAtLocation(currentTargetRuin) == null) {
                currentTargetRuin = null;
                currentTowerType = null;
            } else {
                // attack enemy in ruins
                for (RobotInfo e : enemies) {
                    if (e.location.distanceSquaredTo(currentTargetRuin) <= 9 &&
                        rc.isActionReady() && rc.canAttack(e.location)) {
                        rc.attack(e.location);
                        break;
                    }
                }
            }
        }

        // finish obviosu ruin
        if (currentTargetRuin == null) {
            MapLocation priorityRuin =
                findPriorityRuinToFinish(rc, nearby, enemies);

            if (priorityRuin != null) {
                currentTargetRuin = priorityRuin;
                currentTowerType = inferPreferredTowerType(rc, priorityRuin);

                return;
            }
        }

        // Claim ruin when safe, opportunistic if you would
        if (currentTargetRuin == null) {
            for (MapInfo tile : nearby) {
                if (!tile.hasRuin())
                    continue;

                MapLocation ruinLoc = tile.getMapLocation();
                if (rc.senseRobotAtLocation(ruinLoc) != null)
                    continue;
                if (currLocation.distanceSquaredTo(ruinLoc) > 8)
                    continue;

                boolean contested = false;
                for (RobotInfo e : enemies) {
                    if (e.location.distanceSquaredTo(ruinLoc) <= 16) {
                        contested = true;
                        break;
                    }
                }

                if (!contested) {
                    currentTargetRuin = ruinLoc;
                    currentTowerType = UnitType.LEVEL_ONE_MONEY_TOWER;

                    return;
                }
            }
        }

        // defensive strategy defense
        boolean mustDefend = false;
        if (rc.isActionReady() && enemies.length > 0) {
            RobotInfo target = null;
            int highestUrgency = -1;

            for (RobotInfo e : enemies) {
                if (!rc.canAttack(e.location))
                    continue;
                MapInfo t = rc.senseMapInfo(e.location);

                if (t.getPaint().isAlly()) {
                    mustDefend = true;
                    int urgency = 50 + (100 - e.health) // lowest health
                                  if (urgency > highestUrgency) {
                        highestUrgency = urgency;
                        target = e;
                    }
                }
            }
            if (mustDefend && target != null) {
                rc.attack(target.location);
            }
        }
    }

    static MapLocation findPriorityRuinToFinish(RobotController rc,
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

    static UnitType inferPreferredTowerType(RobotController rc,
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

    static void runMopper(RobotController rc, RobotInfo[] enemies,
                          MapInfo[] nearby) throws GameActionException {
        if (rc.getPaint() < 20) {
            tryRefill(rc);
            return;
        }

        MapLocation currLoc = rc.getLocation();

        // determine target w/ scoring
        MapLocation bestTarget = null;
        int bestScore = -1;
        boolean isEnemyUnit = false;

        // fight enemy moppers
        for (RobotInfo e : enemies) {
            if (e.type == UnitType.MOPPER) {
                int score = 80 - e.location.distanceSquaredTo(currLoc);
                if (score > bestScore) {
                    bestScore = score;
                    bestTarget = e.location;
                    isEnemyUnit = true;
                }
            }
        }

        // delete pockets inside territory
        for (MapInfo tile : nearby) {
            if (!tile.getPaint().isEnemy())
                continue;
            MapLocation tileLoc = tile.getMapLocation();

            int allyNeighbors = 0;
            for (Direction d : directions) {
                MapLocation neighbor = tileLoc.add(d);
                if (rc.canSenseLocation(neighbor) &&
                    rc.senseMapInfo(neighbor).getPaint().isAlly()) {
                    allyNeighbors++;
                }
            }

            int score = 0;
            if (allyNeighbors >= 3) {
                score = 100 + allyNeighbors * 5;
            } else if (allyNeighbors > 0) {
                score = 50;
            }

            // tiebreaker
            score -= Math.max(0, tileLoc.distanceSquaredTo(currLoc));

            if (score > bestScore) {
                bestScore = score;
                bestTarget = tileLoc;
                isEnemyUnit = false;

                if (score >= 120) // ideal score, early exit
                    break;
            }
        }

        if (bestTarget != null) {
            if (rc.isMovementReady() &&
                bestTarget.distanceSquaredTo(currLoc) > 2) {
                moveToward(rc, bestTarget);
            }
            if (rc.isActionReady()) {
                if (isEnemyUnit) {
                    // Try to swing if adjacent, otherwise attack
                    boolean swung = false;
                    for (Direction d : directions) {
                        if (currLoc.add(d).equals(bestTarget) &&
                            rc.canMopSwing(d)) {
                            rc.mopSwing(d);
                            swung = true;
                            break;
                        }
                    }
                    if (!swung && rc.canAttack(bestTarget))
                        rc.attack(bestTarget);
                } else {
                    // Reclaim tile
                    if (rc.canAttack(bestTarget))
                        rc.attack(bestTarget);
                }
            } else {
                if (rc.isMovementReady())
                    explore(rc);
            }
        }
    }

    static void explore(RobotController rc) throws GameActionException {
        if (!rc.isMovementReady())
            return;

        MapLocation currLoc = rc.getLocation();
        if (currLoc.equals(lastPos)) {
            if (++stuckCount >= 3) {
                exploreDir = directions[rng.nextInt(8)];
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

    static void runSplasher(RobotController rc, MapInfo[] nearby)
        throws GameActionException {
        if (rc.getPaint() < 60) {
            tryRefill(rc);
            return;
        }

        MapLocation bestCenter = null;
        int bestScore = -1;

        for (MapInfo c : rc.senseNearbyMapInfos(4)) {
            MapLocation center = c.getMapLocation();
            if (!rc.canAttack(center))
                continue;
            int score = 0;
            for (MapInfo t : rc.senseNearbyMapInfos(center, 2)) {
                PaintType p = t.getPaint();
                if (p == PaintType.EMPTY)
                    score += 2;
                else if (p.isEnemy())
                    score += 1;
            }
            if (score > bestScore) {
                bestScore = score;
                bestCenter = center;
            }
        }

        if (rc.isActionReady() && bestCenter != null && bestScore >= 3) {
            rc.attack(bestCenter);
        }

        Direction best = null;
        int bestMoveScore = -999;
        MapLocation my = rc.getLocation();
        for (Direction d : directions) {
            if (!rc.canMove(d))
                continue;
            PaintType p = rc.senseMapInfo(my.add(d)).getPaint();
            int score = (p == PaintType.EMPTY) ? 6 : (p.isEnemy() ? 1 : -2);
            if (score > bestMoveScore) {
                bestMoveScore = score;
                best = d;
            }
        }
        if (best != null)
            rc.move(best);
    }

    static void buildWithFallback(RobotController rc, UnitType first,
                                  UnitType second, UnitType third)
        throws GameActionException {
        UnitType[] order = {first, second, third};
        for (UnitType t : order) {
            for (Direction d : directions) {
                MapLocation loc = rc.getLocation().add(d);
                if (rc.canBuildRobot(t, loc)) {
                    rc.buildRobot(t, loc);
                    return;
                }
            }
        }
    }

    static void tryRefill(RobotController rc) throws GameActionException {
        RobotInfo[] allies = rc.senseNearbyRobots(-1, rc.getTeam());
        RobotInfo tower = null;
        int best = Integer.MAX_VALUE;
        MapLocation my = rc.getLocation();
        for (RobotInfo a : allies) {
            if (a.type.isTowerType() && a.paintAmount > 0) {
                int d = my.distanceSquaredTo(a.location);
                if (d < best) {
                    best = d;
                    tower = a;
                }
            }
        }
        if (tower == null)
            return;
        moveToward(rc, tower.location);
        int take = Math.min(rc.getType().paintCapacity - rc.getPaint(),
                            tower.paintAmount);
        if (take > 0 && rc.canTransferPaint(tower.location, -take))
            rc.transferPaint(tower.location, -take);
    }

    static void moveToward(RobotController rc, MapLocation target)
        throws GameActionException {
        if (!rc.isMovementReady())
            return;
        MapLocation my = rc.getLocation();
        Direction best = null;
        int bestDist = Integer.MAX_VALUE;
        for (Direction d : directions) {
            if (!rc.canMove(d))
                continue;
            int dist = my.add(d).distanceSquaredTo(target);
            if (dist < bestDist) {
                bestDist = dist;
                best = d;
            }
        }
        if (best != null)
            rc.move(best);
    }

    static void randomMove(RobotController rc) throws GameActionException {
        if (!rc.isMovementReady())
            return;
        for (int i = 0; i < directions.length; i++) {
            Direction d = directions[rng.nextInt(directions.length)];
            if (rc.canMove(d)) {
                rc.move(d);
                return;
            }
        }
    }
}
