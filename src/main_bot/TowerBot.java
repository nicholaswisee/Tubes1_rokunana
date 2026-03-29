package main_bot;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapInfo;
import battlecode.common.MapLocation;
import battlecode.common.Message;
import battlecode.common.PaintType;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;
import battlecode.common.UnitType;

public class TowerBot {

    public static void runTower(RobotController rc) throws GameActionException {
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        for (RobotInfo enemy : enemies) {
            if (rc.canAttack(enemy.location)) {
                rc.attack(enemy.location);
                break;
            }
        }

        towerRelayMessages(rc);

        MapInfo[] nearbyTiles = rc.senseNearbyMapInfos();
        int enemyTileCount = 0;
        int neutralTileCount = 0;
        for (MapInfo tile : nearbyTiles) {
            PaintType paint = tile.getPaint();
            if (paint.isEnemy()) enemyTileCount++;
            else if (paint == PaintType.EMPTY) neutralTileCount++;
        }

        UnitType toSpawn;
        if (!RobotPlayer.faseLateGame) {
            if (RobotPlayer.needMopperLocation != null && enemyTileCount > 0) {
                toSpawn = UnitType.MOPPER;
            } else if (enemyTileCount > 2) {
                toSpawn = UnitType.MOPPER;
            } else {
                toSpawn = UnitType.SOLDIER;
            }
        } else {
            if (enemyTileCount > 2) {
                toSpawn = UnitType.MOPPER;
            } else if (neutralTileCount > 3) {
                toSpawn = UnitType.SPLASHER;
            } else {
                int roll = RobotPlayer.rng.nextInt(20);
                if (roll < 5) toSpawn = UnitType.SOLDIER;
                else if (roll < 11) toSpawn = UnitType.MOPPER;
                else toSpawn = UnitType.SPLASHER;
            }
        }

        boolean spawned = false;

        Direction preferredDir = null;
        if (RobotPlayer.knownRuinLocation != null && toSpawn == UnitType.SOLDIER) {
            preferredDir = rc.getLocation().directionTo(RobotPlayer.knownRuinLocation);
        }
        if (RobotPlayer.knownEnemyCluster != null && (toSpawn == UnitType.MOPPER || toSpawn == UnitType.SPLASHER)) {
            preferredDir = rc.getLocation().directionTo(RobotPlayer.knownEnemyCluster);
        }

        if (preferredDir != null) {
            MapLocation spawnLoc = rc.getLocation().add(preferredDir);
            if (rc.canBuildRobot(toSpawn, spawnLoc)) {
                rc.buildRobot(toSpawn, spawnLoc);
                spawned = true;
            }
            if (!spawned) {
                spawnLoc = rc.getLocation().add(preferredDir.rotateLeft());
                if (rc.canBuildRobot(toSpawn, spawnLoc)) {
                    rc.buildRobot(toSpawn, spawnLoc);
                    spawned = true;
                }
            }
            if (!spawned) {
                spawnLoc = rc.getLocation().add(preferredDir.rotateRight());
                if (rc.canBuildRobot(toSpawn, spawnLoc)) {
                    rc.buildRobot(toSpawn, spawnLoc);
                    spawned = true;
                }
            }
        }

        if (!spawned) {
            for (Direction dir : RobotPlayer.directions) {
                MapLocation spawnLoc = rc.getLocation().add(dir);
                if (rc.canBuildRobot(toSpawn, spawnLoc)) {
                    rc.buildRobot(toSpawn, spawnLoc);
                    spawned = true;
                    break;
                }
            }
        }
        if (!spawned && toSpawn != UnitType.SOLDIER) {
            for (Direction dir : RobotPlayer.directions) {
                MapLocation spawnLoc = rc.getLocation().add(dir);
                if (rc.canBuildRobot(UnitType.SOLDIER, spawnLoc)) {
                    rc.buildRobot(UnitType.SOLDIER, spawnLoc);
                    break;
                }
            }
        }
    }

    public static void towerRelayMessages(RobotController rc) throws GameActionException {
        int currentRound = rc.getRoundNum();
        Message[] incoming = rc.readMessages(currentRound - 5);
        int msgSent = 0;
        final int MAX_TOWER_MSGS = 18; 

        for (Message msg : incoming) {
            if (msgSent >= MAX_TOWER_MSGS) break;
            int data = msg.getBytes();

            RobotInfo[] nearbyAllies = rc.senseNearbyRobots(-1, rc.getTeam());
            for (RobotInfo ally : nearbyAllies) {
                if (msgSent >= MAX_TOWER_MSGS) break;
                if (!ally.type.isTowerType() && ally.getID() != msg.getSenderID()) {
                    if (rc.canSendMessage(ally.location)) {
                        rc.sendMessage(ally.location, data);
                        msgSent++;
                    }
                }
            }

            if (msgSent < MAX_TOWER_MSGS && rc.canBroadcastMessage()) {
                rc.broadcastMessage(data);
                msgSent++;
            }
        }
    }
}
