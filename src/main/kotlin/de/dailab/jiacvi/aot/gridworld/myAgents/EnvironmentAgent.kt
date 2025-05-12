package de.dailab.jiacvi.aot.gridworld.myAgents
import java.time.Duration
import de.dailab.jiacvi.Agent
import de.dailab.jiacvi.aot.gridworld.*
import de.dailab.jiacvi.behaviour.act


/**
 * Stub for your EnvironmentAgent
 * */
class EnvironmentAgent(private val envId: String): Agent(overrideName=envId) {
    // TODO you might need to put some variables to save stuff here
    var antIds = listOf("ant1","ant2","ant3")
    var gridsize = Position(0,0)
    var nestPosition = Position(0,0)
    var obstacleList : List<Position>? = null
    var foodpheromones: List<Position>? = null


    override fun preStart() {
        // TODO if you want you can do something once before the normal lifecycle of your agent
        super.preStart()
        system.spawnAgent(AntAgent("ant1"))
        system.spawnAgent(AntAgent("ant2"))
        system.spawnAgent(AntAgent("ant3"))

        println("Sending StartGameMessage to ServerAgent...")
        val server = system.resolve(SERVER_NAME)
        server tell StartGameMessage(
            envId = "env",
            antIDs = listOf("ant1", "ant2", "ant3"),
        )



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
        on<StartGameResponse> { msg ->
            obstacleList = msg.obstacles
            nestPosition = msg.nestPosition
            gridsize = msg.size

            for (ant in antIds) {
                val antRef = system.resolve(ant)  // Get a reference to the ant agent
                antRef tell CurrentPosition(nestPosition,ant)
            }
        }
        on<DropPheromones>{

        }
        on<GameTurnInform> { msg ->
            for (ant in antIds) {
                val antRef = system.resolve(ant)
                antRef tell CurrentTurn(msg.gameTurn)
            }
        }
    }
}