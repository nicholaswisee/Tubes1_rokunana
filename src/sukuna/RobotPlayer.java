package sukuna;

import battlecode.common.*;
import sukuna.utils.MovementUtils;
import sukuna.utils.PaintEconomyUtils;
import sukuna.utils.RuinUtils;
import sukuna.utils.SoldierHeuristicUtils;
import sukuna.utils.SpawnUtils;
import java.util.Random;

public class RobotPlayer {
    public static final Random rng = new Random(6147);
    public static int turnCount = 0;
    public static final int SPLASHER_OPENING_ROUND = 800;
    public static final int SPLASHER_PAINT_COST = 300;
    public static final int SPLASHER_CHIP_COST = 400;
    public static final int SPLASHER_BANK_PAINT = 330;
    public static final int SPLASHER_BANK_CHIPS = 430;

    public static MapLocation currentTargetRuin = null;
    public static UnitType currentTowerType = null;

    public static final Direction[] directions = {
            Direction.NORTH, Direction.NORTHEAST, Direction.EAST,
            Direction.SOUTHEAST, Direction.SOUTH, Direction.SOUTHWEST,
            Direction.WEST, Direction.NORTHWEST,
    };

    @SuppressWarnings("unused")
    public static void run(RobotController rc) throws GameActionException {
        while (true) {
            turnCount++;
            try {
                RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
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
        boolean enemyThreatNear = opening ? (enemyPaintNearCount >= 3) : (enemyPaintNearCount >= 2);

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

        boolean canAffordSplasher = rc.getPaint() >= SPLASHER_PAINT_COST &&
                rc.getMoney() >= SPLASHER_CHIP_COST;

        if (opening && !canAffordSplasher) {
            boolean nearSplasherBank = rc.getPaint() >= SPLASHER_BANK_PAINT &&
                    rc.getMoney() >= SPLASHER_BANK_CHIPS;

            if (enemyThreatNear) {
                toSpawn = (roll < 5) ? UnitType.MOPPER : UnitType.SOLDIER;
            } else if (!nearSplasherBank) {
                if (roll < 7) {
                    toSpawn = null;
                } else {
                    toSpawn = UnitType.SOLDIER;
                }
            }
        }

        if (toSpawn == null) {
            // intentional no-spawn turn for splasher banking
        } else if (opening) {
            if (toSpawn == UnitType.SPLASHER) {
                SpawnUtils.buildWithFallback(rc, UnitType.SPLASHER,
                        UnitType.SOLDIER, UnitType.MOPPER,
                        directions);
            } else if (toSpawn == UnitType.SOLDIER) {
                SpawnUtils.buildWithFallback(rc, UnitType.SOLDIER,
                        UnitType.SPLASHER, UnitType.MOPPER,
                        directions);
            } else {
                SpawnUtils.buildWithFallback(rc, UnitType.MOPPER,
                        UnitType.SOLDIER, UnitType.SPLASHER,
                        directions);
            }
        } else {
            if (toSpawn == UnitType.SPLASHER) {
                SpawnUtils.buildWithFallback(rc, UnitType.SPLASHER,
                        UnitType.SOLDIER, UnitType.MOPPER,
                        directions);
            } else if (toSpawn == UnitType.SOLDIER) {
                SpawnUtils.buildWithFallback(rc, UnitType.SOLDIER,
                        UnitType.MOPPER, UnitType.SPLASHER,
                        directions);
            } else {
                SpawnUtils.buildWithFallback(rc, UnitType.MOPPER,
                        UnitType.SOLDIER, UnitType.SPLASHER,
                        directions);
            }
        }

        tryUpgradeTower(rc);
    }

    // Domain Expansion souljaahhh
    static void runSoldier(RobotController rc, RobotInfo[] enemies,
            MapInfo[] nearby) throws GameActionException {
        if (rc.getPaint() < 20) {
            PaintEconomyUtils.tryRefill(rc, directions);
            return;
        }

        MapLocation currLocation = rc.getLocation();

        // Ruin defense
        if (currentTargetRuin != null) {
            // clear target if built
            if (rc.canSenseLocation(currentTargetRuin) &&
                    rc.senseRobotAtLocation(currentTargetRuin) != null) {
                currentTargetRuin = null;
                currentTowerType = null;
            } else {
                // attack enemy in ruins
                for (RobotInfo e : enemies) {
                    if (e.location.distanceSquaredTo(
                            currentTargetRuin) <= 9 &&
                            rc.isActionReady() && rc.canAttack(e.location)) {
                        rc.attack(e.location);
                        break;
                    }
                }

                boolean completed = RuinUtils.executeBuildRoutine(rc, currentTargetRuin,
                        currentTowerType, directions);
                if (completed) {
                    currentTargetRuin = null;
                    currentTowerType = null;
                }
                return;
            }
        }

        // finish obviosu ruin
        if (currentTargetRuin == null) {
            MapLocation priorityRuin = RuinUtils.findPriorityRuinToFinish(rc, nearby, enemies);

            if (priorityRuin != null) {
                currentTargetRuin = priorityRuin;
                currentTowerType = RuinUtils.inferPreferredTowerType(rc, priorityRuin);
                boolean completed = RuinUtils.executeBuildRoutine(rc, currentTargetRuin,
                        currentTowerType, directions);
                if (completed) {
                    currentTargetRuin = null;
                    currentTowerType = null;
                }
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
                    boolean completed = RuinUtils.executeBuildRoutine(rc, currentTargetRuin,
                            currentTowerType, directions);
                    if (completed) {
                        currentTargetRuin = null;
                        currentTowerType = null;
                    }
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
                    int urgency = 50 + (100 - e.health);
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

        if (!mustDefend && rc.isMovementReady()) {
            Direction bestDir = SoldierHeuristicUtils.chooseDefensiveMoveDirection(
                    rc, currLocation, directions, rng);
            if (bestDir != null) {
                rc.move(bestDir);
            } else {
                MovementUtils.explore(rc, directions,
                        rng);
            }
        }

        if (rc.isActionReady()) {
            MapLocation target = SoldierHeuristicUtils.choosePaintActionTarget(rc,
                    currLocation, directions);
            if (target != null) {
                rc.attack(target);
            }
        }
    }

    static void runMopper(RobotController rc, RobotInfo[] enemies,
            MapInfo[] nearby) throws GameActionException {
        if (rc.getPaint() < 20) {
            PaintEconomyUtils.tryRefill(rc, directions);
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
                MovementUtils.moveToward(rc, bestTarget, directions);
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
                    MovementUtils.explore(rc, directions,
                            rng);
            }
        }
    }

    static void runSplasher(RobotController rc, MapInfo[] nearby)
            throws GameActionException {
        if (rc.getPaint() < 60) {
            PaintEconomyUtils.tryRefill(rc, directions);
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

    static void tryUpgradeTower(RobotController rc) throws GameActionException {
        for (RobotInfo ally : rc.senseNearbyRobots(-1, rc.getTeam())) {
            if (ally.type.isTowerType() && rc.canUpgradeTower(ally.location)) {
                rc.upgradeTower(ally.location);
                return;
            }
        }
    }
}
