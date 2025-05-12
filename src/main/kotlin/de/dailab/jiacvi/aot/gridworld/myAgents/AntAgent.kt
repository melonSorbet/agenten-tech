package de.dailab.jiacvi.aot.gridworld.myAgents

import de.dailab.jiacvi.Agent
import de.dailab.jiacvi.aot.gridworld.*
import de.dailab.jiacvi.behaviour.act
import java.time.Duration

/**
 * Stub for your AntAgent
 * */
class AntAgent(private val antId: String) : Agent(overrideName = antId) {
    private var currentPosition: Position = Position(0, 0)
    private var hasFood = false
    private var lastAction: AntAction? = null

    override fun behaviour() = act {
        on<CurrentPosition> { msg ->
            currentPosition = msg.position
            println("Ant $antId initialized at $currentPosition")
        }

        on<CurrentTurn> { msg ->
            val server = system.resolve(SERVER_NAME)
            val action = determineAction(msg.turn)
            lastAction = action

            // Send action request to server
            server tell AntActionRequest(antId, action)
            println("Ant $antId requested action: $action")
        }

        on<AntActionResponse> { response ->
            println("Ant $antId received response: $response")

            when {
                !response.state -> {
                    println("Action failed: ${response.flag}")
                    handleFailedAction()
                }
                response.flag == ActionFlag.HAS_FOOD && !hasFood -> {
                    // Found food - try to take it
                    system.resolve(SERVER_NAME) tell AntActionRequest(antId, AntAction.TAKE)
                }
                response.flag == ActionFlag.NO_FOOD && hasFood && isAtNest() -> {
                    // At nest with food - try to drop it
                    system.resolve(SERVER_NAME) tell AntActionRequest(antId, AntAction.DROP)
                }
                response.state -> {
                    // Update position if move was successful
                    lastAction?.let { action ->

                            currentPosition = currentPosition.applyMove(action)!!
                            println("Ant $antId moved to $currentPosition")

                    }
                }
            }
        }
    }

    private fun determineAction(turn: Int): AntAction {
        //TODO: Implement check to see what the nearest pheromone is
        // Simple alternating movement pattern
        return if (turn % 2 == 0) AntAction.NORTH else AntAction.EAST
    }

    private fun handleFailedAction() {
        // Try a different direction if movement failed
        val newAction = when (lastAction) {
            AntAction.NORTH -> AntAction.EAST
            AntAction.EAST -> AntAction.SOUTH
            AntAction.SOUTH -> AntAction.WEST
            else -> AntAction.NORTH
        }
        system.resolve(SERVER_NAME) tell AntActionRequest(antId, newAction)
    }

    private fun isAtNest(): Boolean {
        // You'll need to get the actual nest position from somewhere
        return currentPosition == Position(0, 0) // Replace with actual nest position
    }
}