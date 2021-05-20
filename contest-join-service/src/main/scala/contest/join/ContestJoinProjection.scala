package contest.join
import akka.actor.typed.ActorSystem
import akka.cluster.sharding.typed.ShardedDaemonProcessSettings
import akka.cluster.sharding.typed.scaladsl.ShardedDaemonProcess
import akka.persistence.jdbc.query.scaladsl.JdbcReadJournal
import akka.persistence.query.Offset
import akka.projection.eventsourced.EventEnvelope
import akka.projection.eventsourced.scaladsl.EventSourcedProvider
import akka.projection.jdbc.scaladsl.JdbcProjection
import akka.projection.scaladsl.{ExactlyOnceProjection, SourceProvider}
import akka.projection.{ProjectionBehavior, ProjectionId}
import contest.join.repository.{ContestJoinRepository, ScalikeJdbcSession}

object ContestJoinProjection {

  def init(
            system: ActorSystem[_],
            repository: ContestJoinRepository): Unit = {
    ShardedDaemonProcess(system).init(
      name = "ContestJoinProjection",
      ContestJoin.tags.size,
      index =>
        ProjectionBehavior(createProjectionFor(system, repository, index)),
      ShardedDaemonProcessSettings(system),
      Some(ProjectionBehavior.Stop))
  }

  private def createProjectionFor(
                                   system: ActorSystem[_],
                                   repository: ContestJoinRepository,
                                   index: Int): ExactlyOnceProjection[Offset, EventEnvelope[ContestJoin.Event]] = {
    val tag = ContestJoin.tags(index)

    val sourceProvider
    : SourceProvider[Offset, EventEnvelope[ContestJoin.Event]] =
      EventSourcedProvider.eventsByTag[ContestJoin.Event](
        system = system,
        readJournalPluginId = JdbcReadJournal.Identifier,
        tag = tag)

    JdbcProjection.exactlyOnce(
      projectionId = ProjectionId("ContestJoinProjection", tag),
      sourceProvider,
      handler = () =>
        new ContestJoinProjectionHandler(tag, system, repository),
      sessionFactory = () => new ScalikeJdbcSession())(system)
  }

}
