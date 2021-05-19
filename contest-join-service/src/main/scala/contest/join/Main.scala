package contest.join

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.grpc.GrpcClientSettings
import akka.management.cluster.bootstrap.ClusterBootstrap
import akka.management.scaladsl.AkkaManagement
import contest.join.repository.ScalikeJdbcSetup
import org.slf4j.LoggerFactory

import scala.util.control.NonFatal

object Main {

  val logger = LoggerFactory.getLogger("contest.join.Main")

  def main(args: Array[String]): Unit = {
    val system =
      ActorSystem[Nothing](Behaviors.empty, "ContestJoinService")
    try {
      init(system)
    } catch {
      case NonFatal(e) =>
        logger.error("Terminating due to initialization failure.", e)
        system.terminate()
    }
  }

  def init(system: ActorSystem[_]): Unit = {
    AkkaManagement(system).start()
    ClusterBootstrap(system).start()

    ContestJoin.init(system,20)


    val grpcInterface =
      system.settings.config.getString("contest-join-service.grpc.interface")
    val grpcPort =
      system.settings.config.getInt("contest-join-service.grpc.port")
    val grpcService = new ContestJoinServiceImpl
    ContestJoinServer.start(
      grpcInterface,
      grpcPort,
      system,
      grpcService
    )
  }
}
