package de.dailab.jiacvi.aot.gridworld.myAgents

import de.dailab.jiacvi.Agent
import de.dailab.jiacvi.AgentRef
import de.dailab.jiacvi.aot.gridworld.*
import de.dailab.jiacvi.behaviour.act
import java.time.Duration

/**
 * Stub for your AntAgent
 * */
const val baseStrength: Int = 10;

class AntAgent(private val antId: String) : Agent(overrideName = antId) {
    private var currentPosition: Position = Position(0, 0)
    private var canPickUpFood = false
    private var lastAction: AntAction? = null
    private var gotFood = false
    private var positions: List<Pair<Pair<Position, Int>?, AntAction>> = emptyList();
    override fun behaviour() = act {
        on<CurrentPosition> { msg ->
            currentPosition = msg.position
            println("Ant $antId initialized at $currentPosition")
        }
        //ant trying to do their moves
        on<CurrentTurn> { msg ->
            val surroundingRef = system.resolve("env") as? AgentRef<GetSurrounding>
            val antRef = system.resolve("env")
            val server = system.resolve(SERVER_NAME) as? AgentRef<AntActionRequest>
            val action = AntAction.TAKE
            //drop pheromones
            val pheromoneType = Pheromones.NEST
            println("dropped pheromoneType $pheromoneType")
            //position

            surroundingRef?.ask<GetSurrounding, GetSurroundingResponse>(
                GetSurrounding(pheromoneType,currentPosition)
            ){ response ->
                positions = response.positions
            }
            val bestPair = chooseBestPosition()

            server?.ask<AntActionRequest, AntActionResponse>(
                AntActionRequest(antId = antId, action = bestPair.second),
            ) { response ->

                if (response.state) {
                    currentPosition = bestPair.first
                }
                //TODO: add lastMove check if there is Food to take then Take it Instead of moving
                antRef tell DropPheromones(pheromoneType, currentPosition, baseStrength)

                println("this is the request response=${response.state}")
            }


        }
    }
    private fun chooseBestPosition(): Pair<Position, AntAction> {
        // Filter out null positions and unwrap the remaining ones
        val validPositions = positions.filter { it.first != null }

        // If no valid positions, return a default
        if (validPositions.isEmpty()) {
            return Position(0, 0) to AntAction.NORTH
        }

        // Find the maximum pheromone value manually
        var maxValue = Int.MIN_VALUE
        val maxCandidates = mutableListOf<Pair<Position, AntAction>>()

        for ((pheromonePair, action) in validPositions) {
            val currentValue = pheromonePair!!.second
            when {
                currentValue > maxValue -> {
                    maxValue = currentValue
                    maxCandidates.clear()
                    maxCandidates.add(pheromonePair.first to action)
                }
                currentValue == maxValue -> {
                    maxCandidates.add(pheromonePair.first to action)
                }
            }
        }

        // Return random if multiple max values, or the single best if unique
        return maxCandidates.random()
    }



    private fun isAtNest(): Boolean {
        // You'll need to get the actual nest position from somewhere
        return currentPosition == Position(0, 0) // Replace with actual nest position
    }
}