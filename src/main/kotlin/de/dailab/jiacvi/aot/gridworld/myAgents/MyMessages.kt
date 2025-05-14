package de.dailab.jiacvi.aot.gridworld.myAgents

import de.dailab.jiacvi.aot.gridworld.AntAction
import de.dailab.jiacvi.aot.gridworld.Position
enum class Pheromones {
    NEST, FOOD
}
data class CurrentPosition(var position: Position,var antId: String)
// TODO you can define your own messages in here if you want to

data class CurrentTurn(val turn: Int)
// Ant -> Environment
data class DropPheromones(val type: Pheromones,val position: Position, val strength: Int)

data class GetSurrounding(val type: Pheromones,val position: Position)

data class GetSurroundingResponse(val positions: List<Pair<Pair<Position, Int>?, AntAction>>)