package contest.join
import akka.actor.typed.ActorSystem
import akka.projection.eventsourced.EventEnvelope
import akka.projection.jdbc.scaladsl.JdbcHandler
import org.slf4j.LoggerFactory
import contest.join.repository.{ ContestJoinRepository, ScalikeJdbcSession }

class ContestJoinProjectionHandler(
                                    tag: String,
                                    system: ActorSystem[_],
                                    repo: ContestJoinRepository)
  extends JdbcHandler[
    EventEnvelope[ContestJoin.Event],
    ScalikeJdbcSession]() {

  private val log = LoggerFactory.getLogger(getClass)

  override def process(
                        session: ScalikeJdbcSession,
                        envelope: EventEnvelope[ContestJoin.Event]): Unit = {
    envelope.event match {
      case ContestJoin.UsersAddedToContest(contestId, joinConfirmationInfo) =>
        repo.update(session, contestId, joinConfirmationInfo)
        logUsersAddedToContest(session, contestId, joinConfirmationInfo)
      case _ =>
    }
  }

  private def logUsersAddedToContest(
                            session: ScalikeJdbcSession,
                            contestId: String,
                            joinConfirmationInfo: Seq[JoinConfirmationInfo]): Unit = {
    log.info(s"ContestJoinProjectionHandler is processing event for ${contestId}")
    joinConfirmationInfo.map{info =>
      log.info(s"join info :  ${info}")
    }
  }

}