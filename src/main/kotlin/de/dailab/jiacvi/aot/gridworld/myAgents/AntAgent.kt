package de.dailab.jiacvi.aot.gridworld.myAgents

import de.dailab.jiacvi.Agent
import de.dailab.jiacvi.AgentRef
import de.dailab.jiacvi.aot.gridworld.*
import de.dailab.jiacvi.behaviour.act
import java.time.Duration
import kotlin.math.exp

/**
 * Stub for your AntAgent
 * */
const val baseStrength: Int = 100 ;

class AntAgent(private val antId: String) : Agent(overrideName = antId) {
    private var lastFailedPosition: Position? = null
    private var currentPosition: Position = Position(0, 0)
    private var lastAction: AntAction? = null
    private var gotFood = false
    private var positions: List<Pair<Pair<Position, Int>?, AntAction>> = emptyList();
    private var nestPosition: Position = Position(0, 0)
    private var currentStrength = baseStrength
    private var stepsSincePheromoneReset = 0

    private fun getCurrentStrength(): Int {
        return (baseStrength * exp(-stepsSincePheromoneReset * 0.1)).toInt()
    }


    override fun behaviour() = act {
        on<CurrentPosition> { msg ->
            currentPosition = msg.position
            val antRef = system.resolve("env")

            nestPosition = msg.position
            antRef tell DropPheromones(
                Pheromones.NEST,
                currentPosition,
                9998888
            )
        }
        on<CurrentTurn> { msg ->
            val surroundingRef = system.resolve("env") as? AgentRef<GetSurrounding>
            val server = system.resolve(SERVER_NAME) as? AgentRef<AntActionRequest>

            // Handle pending actions first
            when (lastAction) {
                AntAction.TAKE -> handleTakeAction()
                else -> handleMovement(surroundingRef,  server)
            }
        }



    }
    private fun handleTakeAction(){
        val antRef = system.resolve("env")
        val server = system.resolve(SERVER_NAME) as? AgentRef<AntActionRequest>


            // Complete the TAKE action before proceeding
            server?.ask<AntActionRequest, AntActionResponse>(
                AntActionRequest(antId = antId, action = AntAction.TAKE)
            ) { response ->

                    gotFood = true
                    lastAction = null
                    currentStrength = baseStrength
                    stepsSincePheromoneReset = 0
                    antRef tell DropPheromones(
                        if (!gotFood) Pheromones.NEST else Pheromones.FOOD,
                        currentPosition,
                        baseStrength
                    )

            }
    }
    private fun handleDropAction(){
        val antRef = system.resolve("env")
        val server = system.resolve(SERVER_NAME) as? AgentRef<AntActionRequest>

        // Complete the TAKE action before proceeding
        server?.ask<AntActionRequest, AntActionResponse>(
            AntActionRequest(antId = antId, action = AntAction.DROP)
        ) { response ->


            gotFood = false
            lastAction = null
            currentStrength = baseStrength
            stepsSincePheromoneReset = 0

            antRef tell DropPheromones(
                if (!gotFood) Pheromones.NEST else Pheromones.FOOD,
                currentPosition,
                baseStrength
            )

        }

    }
    private fun handleMovement(
        surroundingRef: AgentRef<GetSurrounding>?,
        server: AgentRef<AntActionRequest>?
    ) {
        // Get surrounding info
        surroundingRef?.ask<GetSurrounding, GetSurroundingResponse>(
            GetSurrounding(if (gotFood) Pheromones.NEST else Pheromones.FOOD, currentPosition)
        ) { response ->
            positions = response.positions

            // Choose best position
            val bestPair = chooseBestPosition()


            // Check for nest BEFORE moving (using currentPosition)
            val atNestBeforeMove = nestPosition.x == currentPosition.x &&
                    nestPosition.y == currentPosition.y
            if(atNestBeforeMove && gotFood) {
                handleDropAction()
                return@ask
            }
            // Execute movement
            server?.ask<AntActionRequest, AntActionResponse>(
                AntActionRequest(antId = antId, action = bestPair.second)
            ) { response ->
                if(response.flag == ActionFlag.OBSTACLE) {
                    println("didnt work")
                    lastFailedPosition = bestPair.first
                    chooseBestPosition() // Retry the action
                    return@ask
                }

                if (response.state) {
                    // Update position after successful move
                    currentPosition = bestPair.first

                    // Handle food pickup (using response flag)
                    if (response.flag == ActionFlag.HAS_FOOD && !gotFood) {
                        lastAction = AntAction.TAKE
                    }


                }
                stepsSincePheromoneReset++
                currentStrength = getCurrentStrength()
                // Always drop pheromones
                system.resolve("env") tell(DropPheromones(
                    if (gotFood) Pheromones.FOOD else Pheromones.NEST,
                    currentPosition,
                    currentStrength
                ))
            }

        }
    }
    private fun chooseBestPosition(): Pair<Position, AntAction> {

        val validPositions = positions.filter { it.first != null }
            .mapNotNull { (pheromonePair, action) ->
                pheromonePair?.let { it.first to action }
            }
            .filterNot { (position, _) ->
                position == lastFailedPosition
            }

        // If no valid positions, return a default
        if (validPositions.isEmpty()) {
            lastFailedPosition = null // Reset since we have no options
            return Position(0, 0) to AntAction.NORTH
        }

        // Create a list of positions with their weights
        val weightedChoices = mutableListOf<Triple<Position, AntAction, Int>>()
        var totalWeight = 0

        for ((position, action) in validPositions) {
            // Find the pheromone value for this position
            val pheromoneValue = positions.first { it.second == action }.first?.second ?: 0
            val weight = if (pheromoneValue <= 0) 1 else pheromoneValue

            weightedChoices.add(Triple(position, action, weight))
            totalWeight += weight
        }

        // Select randomly with weighting
        if (totalWeight > 0) {
            var random = (0 until totalWeight).random()
            for ((position, action, weight) in weightedChoices) {
                random -= weight
                if (random < 0) {
                    lastFailedPosition = null // Reset after successful selection
                    return position to action
                }
            }
        }

        // Fallback to random selection if weighting failed
        lastFailedPosition = null
        return validPositions.random()
    }
}