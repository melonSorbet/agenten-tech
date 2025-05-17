package de.dailab.jiacvi.aot.gridworld

import de.dailab.jiacvi.aot.gridworld.myAgents.EnvironmentAgent
import de.dailab.jiacvi.communication.LocalBroker
import de.dailab.jiacvi.dsl.agentSystem
import org.organicdesign.fp.StaticImports.set

fun main() {

    // you can create own grids and change the file here. Be sure to test with our grid as well.
    val gridfile = "/grids/benchmark.grid"
    println("moin")
    agentSystem("Gridworld") {
        enable(LocalBroker)
        agents {
            // you can set logGames=true, logFile="logs/<name>.log" here
            add(ServerAgent(gridfile, 40,true,true,"logs/yourlog.log"))

            // this is your Agent but don't change the ID
            add(EnvironmentAgent("env"))
        }
    }.start()
}
