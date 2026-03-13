package sukuna;

import battlecode.common.*;
import java.util.Random;

public class RobotPlayer {
  static final Random rng = new Random(6147);
  static int turnCount = 0;

  static final Direction[] directions = {
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

  static void runTower(RobotController rc, RobotInfo[] enemies, MapInfo[] nearby)
      throws GameActionException {
    for (RobotInfo e : enemies) {
      if (rc.canAttack(e.location)) {
        rc.attack(e.location);
        break;
      }
    }

    boolean enemyPaintNear = false;
    for (MapInfo t : nearby) {
      if (t.getPaint().isEnemy() && rc.getLocation().distanceSquaredTo(t.getMapLocation()) <= 16) {
        enemyPaintNear = true;
        break;
      }
    }

    UnitType pick;
    int roll = rng.nextInt(10);
    if (enemyPaintNear) {
      // defensive mix: 40% mopper, 30% soldier, 30% splasher
      if (roll < 4)
        pick = UnitType.MOPPER;
      else if (roll < 7)
        pick = UnitType.SOLDIER;
      else
        pick = UnitType.SPLASHER;
    } else {
      // expansion mix: 60% splasher, 30% soldier, 10% mopper
      if (roll < 6)
        pick = UnitType.SPLASHER;
      else if (roll < 9)
        pick = UnitType.SOLDIER;
      else
        pick = UnitType.MOPPER;
    }

    buildWithFallback(rc, pick, UnitType.SOLDIER, UnitType.MOPPER);
  }

  static void runSoldier(RobotController rc, RobotInfo[] enemies, MapInfo[] nearby)
      throws GameActionException {
    if (rc.getPaint() < 20) {
      tryRefill(rc);
      return;
    }

    // simple defense: attack first enemy in range
    if (rc.isActionReady()) {
      for (RobotInfo e : enemies) {
        if (rc.canAttack(e.location)) {
          rc.attack(e.location);
          return;
        }
      }
    }

    // simple ruin build
    for (MapInfo tile : nearby) {
      if (!tile.hasRuin())
        continue;
      MapLocation ruin = tile.getMapLocation();
      if (rc.senseRobotAtLocation(ruin) != null)
        continue;

      if (rc.canMarkTowerPattern(UnitType.LEVEL_ONE_MONEY_TOWER, ruin))
        rc.markTowerPattern(UnitType.LEVEL_ONE_MONEY_TOWER, ruin);

      if (rc.isActionReady()) {
        for (MapInfo p : rc.senseNearbyMapInfos(ruin, 8)) {
          if (p.getMark() != PaintType.EMPTY && p.getMark() != p.getPaint() && rc.canAttack(p.getMapLocation())) {
            rc.attack(p.getMapLocation(), p.getMark() == PaintType.ALLY_SECONDARY);
            break;
          }
        }
      }

      if (rc.canCompleteTowerPattern(UnitType.LEVEL_ONE_MONEY_TOWER, ruin)) {
        rc.completeTowerPattern(UnitType.LEVEL_ONE_MONEY_TOWER, ruin);
        return;
      }

      moveToward(rc, ruin);
      return;
    }

    // move greedily: prefer empty, avoid enemy paint
    Direction best = null;
    int bestScore = -9999;
    MapLocation my = rc.getLocation();
    for (Direction d : directions) {
      if (!rc.canMove(d))
        continue;
      MapLocation next = my.add(d);
      PaintType p = rc.senseMapInfo(next).getPaint();
      int score = (p == PaintType.EMPTY) ? 8 : (p.isEnemy() ? -10 : 2);
      if (score > bestScore) {
        bestScore = score;
        best = d;
      }
    }
    if (best != null)
      rc.move(best);

    if (rc.isActionReady() && rc.canAttack(rc.getLocation())
        && !rc.senseMapInfo(rc.getLocation()).getPaint().isAlly()) {
      rc.attack(rc.getLocation());
    }
  }

  static void runMopper(RobotController rc, RobotInfo[] enemies, MapInfo[] nearby)
      throws GameActionException {
    if (rc.getPaint() < 20) {
      tryRefill(rc);
      return;
    }

    MapLocation my = rc.getLocation();
    MapLocation best = null;
    int bestDist = Integer.MAX_VALUE;

    for (MapInfo t : nearby) {
      if (t.getPaint().isEnemy()) {
        int d = my.distanceSquaredTo(t.getMapLocation());
        if (d < bestDist) {
          bestDist = d;
          best = t.getMapLocation();
        }
      }
    }

    if (best != null) {
      moveToward(rc, best);
      if (rc.isActionReady() && rc.canAttack(best))
        rc.attack(best);
      return;
    }

    // no paint target: pressure enemy unit if possible
    for (RobotInfo e : enemies) {
      if (rc.isActionReady() && rc.canAttack(e.location)) {
        rc.attack(e.location);
        return;
      }
    }

    randomMove(rc);
  }

  static void runSplasher(RobotController rc, MapInfo[] nearby) throws GameActionException {
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

  static void buildWithFallback(RobotController rc, UnitType first, UnitType second, UnitType third)
      throws GameActionException {
    UnitType[] order = { first, second, third };
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
    int take = Math.min(rc.getType().paintCapacity - rc.getPaint(), tower.paintAmount);
    if (take > 0 && rc.canTransferPaint(tower.location, -take))
      rc.transferPaint(tower.location, -take);
  }

  static void moveToward(RobotController rc, MapLocation target) throws GameActionException {
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
