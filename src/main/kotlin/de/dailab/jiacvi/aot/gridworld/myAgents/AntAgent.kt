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
const val baseStrength: Int = 300 ;

class AntAgent(private val antId: String) : Agent(overrideName = antId) {
    private var currentPosition: Position = Position(0, 0)
    private var lastAction: AntAction? = null
    private var gotFood = false
    private var positions: List<Pair<Pair<Position, Int>?, AntAction>> = emptyList();
    private var nestPosition: Position = Position(0, 0)
    private var currentStrength = baseStrength
    private var stepsSincePheromoneReset = 0

    private fun calculateCurrentStrength(): Int {
        // Calculate a base value that decays more slowly
        val strength = when {
            gotFood -> baseStrength * 1.5.toInt() // 450 - Stronger food trail when returning with food
            else -> (baseStrength * exp(-0.008 * stepsSincePheromoneReset)).toInt().coerceAtLeast(80)
        }
        stepsSincePheromoneReset++
        return strength
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
            val antRef = system.resolve("env")
            val server = system.resolve(SERVER_NAME) as? AgentRef<AntActionRequest>

            // Handle pending actions first
            when (lastAction) {
                AntAction.TAKE -> handleTakeAction()
                else -> handleMovement(surroundingRef, antRef, server)
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
        antRef: AgentRef<*>?,
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
                if (response.state) {
                    // Update position after successful move
                    currentPosition = bestPair.first

                    // Handle food pickup (using response flag)
                    if (response.flag == ActionFlag.HAS_FOOD && !gotFood) {
                        lastAction = AntAction.TAKE
                    }


                }
                currentStrength = calculateCurrentStrength()
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
        // Filter out null positions and unwrap the remaining ones
        val validPositions = positions.filter { it.first != null }

        // If no valid positions, return a default
        if (validPositions.isEmpty()) {
            println("empty")
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
}