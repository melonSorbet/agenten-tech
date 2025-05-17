package de.dailab.jiacvi.aot.gridworld.myAgents

import de.dailab.jiacvi.Agent
import de.dailab.jiacvi.AgentRef
import de.dailab.jiacvi.aot.gridworld.*
import de.dailab.jiacvi.behaviour.act


/**
 * Stub for your EnvironmentAgent
 * */
class EnvironmentAgent(private val envId: String) : Agent(overrideName = envId) {
    // TODO you might need to put some variables to save stuff here
    val antIds = (1..40).map { "ant-$it" }
    var gridsize = Position(0, 0)
    var nestPosition = Position(0, 0)
    var obstacleList: List<Position>? = null
    var foodpheromones: MutableList<Pair<Position, Int>> = mutableListOf()
    var nestpheromones: MutableList<Pair<Position, Int>> = mutableListOf()


    override fun preStart() {
        // TODO if you want you can do something once before the normal lifecycle of your agent
        super.preStart()
        antIds.forEach { id ->
            system.spawnAgent(AntAgent(id))
        }
        println("Sending StartGameMessage to ServerAgent...")

        val server = system.resolve(SERVER_NAME) as? AgentRef<StartGameMessage>
        server?.ask<StartGameMessage, StartGameResponse>(
            StartGameMessage("env",antIds)
        ){ response ->
            gridsize = response.size
            nestPosition = response.nestPosition
            println("nestPosition: $nestPosition")
            obstacleList = response.obstacles
            for (ant in antIds) {
                val antRef = system.resolve(ant)  // Get a reference to the ant agent
                antRef tell CurrentPosition(nestPosition, ant)
            }
            // Fill the lists with positions based on gridsize (for example, all positions within the grid)
            for (x in 0 until gridsize.x) {
                for (y in 0 until gridsize.y) {
                    val position = Position(x, y)
                    foodpheromones.add(Pair(position, 0))  // Default float value for food pheromones
                    nestpheromones.add(Pair(position, 0))  // Default float value for nest pheromones
                }
            }
        }




    }


    override fun behaviour() = act {
        /* TODO here belongs most of your agents logic. Everything in here needs to be inside an every(){}, on(){} or respond<>{} call.
        *   - Check the readme "Reactive Behaviour" part and see the Server for some examples
        *   - you can create Ants with system.spawnAgent(AntAgent(<String>))
        *   - try to start a game with the StartGameMessage, don't forget to tell the server about your ants
        *   - you need to initialize your ants, they don't know where they start
        *   - here you should manage the pheromones dropped by your ants
        *   - REMEMBER: pheromones should transpire, so old routes get lost
        *   - adjust your parameters to get better results, i.e. amount of ants (capped at 40)
        */

        on<EndGameMessage> { msg ->

            println("total score = ${msg.score}, food collected = ${msg.foodCollected}/${msg.totalFood}")
        }
        on<GameTurnInform> { msg ->
            foodpheromones = foodpheromones.map { (pos, value) ->
                pos to  (value * 0.30).toInt()
            }.toMutableList()

            nestpheromones = nestpheromones.map { (pos, value) ->
                pos to (value * 0.90).toInt()
            }.toMutableList()

            for (ant in antIds) {
                val antRef = system.resolve(ant)
                antRef tell CurrentTurn(msg.gameTurn)
            }
        }
        on<DropPheromones> { msg ->
            when (msg.type) {
                Pheromones.FOOD -> {
                    updatePheromone(foodpheromones,msg.position, msg.strength)
                }
                Pheromones.NEST -> {
                    updatePheromone(nestpheromones,msg.position,  msg.strength)
                }
            }
        }
        respond<GetSurrounding, GetSurroundingResponse> { msg ->

            //send response
            return@respond GetSurroundingResponse(getPositionDirections(msg.type,msg.position))
        }

    }
    private fun updatePheromone(pheromoneList: MutableList<Pair<Position, Int>>, position: Position, value: Int) {
        // Find the index by comparing x and y coordinates individually
        val index = pheromoneList.indexOfFirst {
            it.first.x == position.x && it.first.y == position.y
        }

        if (index != -1) {
            // Found the position, update the value
            val currentValue = pheromoneList[index].second
            pheromoneList[index] = Pair(position, currentValue + value)
        } else {
            // If position not found (shouldn't happen if grid properly initialized)
            println("WARNING: Position (${position.x}, ${position.y}) not found in pheromone list! Adding it now.")
            pheromoneList.add(Pair(position, value))
        }

        // Double-check that the update worked by reading the value back
        val verifyValue = pheromoneList.find {
            it.first.x == position.x && it.first.y == position.y
        }?.second

        if (verifyValue == null || verifyValue <= 0) {
            println("ERROR: Failed to update pheromone at (${position.x}, ${position.y}). Value is still $verifyValue")
        }
    }
    fun getPositionDirections(pheromoneType: Pheromones, position: Position): List<Pair<Pair<Position, Int>?, AntAction>> {
        val list = mutableListOf<Pair<Pair<Position, Int>?, AntAction>>()

        // Define all eight possible directions correctly
        list.add(Pair(getPositionFromPheromone(pheromoneType,Position(position.x, position.y + 1)), AntAction.NORTH))
        list.add(Pair(getPositionFromPheromone(pheromoneType,Position(position.x + 1, position.y + 1)), AntAction.NORTHEAST))
        list.add(Pair(getPositionFromPheromone(pheromoneType,Position(position.x + 1, position.y)), AntAction.EAST))
        list.add(Pair(getPositionFromPheromone(pheromoneType,Position(position.x + 1, position.y - 1)), AntAction.SOUTHEAST))
        list.add(Pair(getPositionFromPheromone(pheromoneType,Position(position.x, position.y - 1)), AntAction.SOUTH))
        list.add(Pair(getPositionFromPheromone(pheromoneType,Position(position.x - 1, position.y - 1)), AntAction.SOUTHWEST))
        list.add(Pair(getPositionFromPheromone(pheromoneType,Position(position.x - 1, position.y)), AntAction.WEST))
        list.add(Pair(getPositionFromPheromone(pheromoneType,Position(position.x - 1, position.y + 1)), AntAction.NORTHWEST))
        return list
    }
    fun getPositionFromPheromone(pheromoneType: Pheromones, position: Position): Pair<Position, Int>? {
        if (pheromoneType == Pheromones.FOOD){
            return getPosition(foodpheromones, position)
        }else{
            return getPosition(nestpheromones, position)
        }

    }
}
fun getPosition(pheromoneList: MutableList<Pair<Position, Int>>, position: Position): Pair<Position, Int>? {
    pheromoneList.let { pheromones ->
        for (pheromone in pheromones) {

            if (pheromone.first.x == position.x && pheromone.first.y == position.y) {

                return Pair(pheromone.first, pheromone.second)
            }
        }
    }
    return null
}
