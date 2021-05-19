package contest.join

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.pattern.StatusReply
import akka.persistence.testkit.scaladsl.EventSourcedBehaviorTestKit
import com.typesafe.config.ConfigFactory
import org.scalatest.BeforeAndAfterEach
import org.scalatest.wordspec.AnyWordSpecLike
import org.slf4j.LoggerFactory

object ContestJoinSpec {

  val config = ConfigFactory
    .parseString("""
      akka.actor.serialization-bindings {
        "contest.join.CborSerializable" = jackson-cbor
      }
      """)
    .withFallback(EventSourcedBehaviorTestKit.config)

}

class ContestJoinSpec
  extends ScalaTestWithActorTestKit(ContestJoinSpec.config)
    with AnyWordSpecLike
    with BeforeAndAfterEach {

  private val logger = LoggerFactory.getLogger(getClass)

  private val contestId = "MegaContestPlayoff"
  private val contestSize = 15
  private def eventSourcedTestKit =
    EventSourcedBehaviorTestKit[
      ContestJoin.Command,
      ContestJoin.Event,
      ContestJoin.State](system, ContestJoin(contestId,contestSize))

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    eventSourcedTestKit.clear()
  }

  "The Contest Join" should {

    "add to contest" in {
      val contestJoinInfo: Seq[JoinRequestInfo] = Seq(
        JoinRequestInfo(contestId, "amit1231", "BLRRAJ1"),
        JoinRequestInfo(contestId, "amit1232", "BLRRAJ2"),
        JoinRequestInfo(contestId, "amit1233", "BLRRAJ3"),
        JoinRequestInfo(contestId, "amit1234", "BLRRAJ4"),
        JoinRequestInfo(contestId, "amit1235", "BLRRAJ5"),
        JoinRequestInfo(contestId, "amit1236", "BLRRAJ6"),
        JoinRequestInfo(contestId, "amit1237", "BLRRAJ7"),
        JoinRequestInfo(contestId, "amit1238", "BLRRAJ8"),
        JoinRequestInfo(contestId, "amit1239", "BLRRAJ8"),
        JoinRequestInfo(contestId, "amit1230", "BLRRAJ0")
      )

      val result1 =
        eventSourcedTestKit.runCommand[StatusReply[ContestJoin.Summary]](
          replyTo => ContestJoin.AddToContest(contestJoinInfo, replyTo)
        )
      result1.reply should ===(
        StatusReply.Success(ContestJoin.Summary(15, 10)))

      val joinConfirmationInfo = result1.event match {
        case ContestJoin.UsersAddedToContest(_, joinConfirmationInfo) => joinConfirmationInfo
        case _ => Seq.empty
      }
      joinConfirmationInfo.map(e => logger.info(s"${e}"))
      joinConfirmationInfo.size shouldEqual 10

    }

    "get" in {
      val contestJoinInfo: Seq[JoinRequestInfo] = Seq(
        JoinRequestInfo(contestId, "amit1231", "BLRRAJ1"),
        JoinRequestInfo(contestId, "amit1232", "BLRRAJ2"),
        JoinRequestInfo(contestId, "amit1233", "BLRRAJ3"),
        JoinRequestInfo(contestId, "amit1234", "BLRRAJ4"),
        JoinRequestInfo(contestId, "amit1235", "BLRRAJ5"),
        JoinRequestInfo(contestId, "amit1236", "BLRRAJ6"),
        JoinRequestInfo(contestId, "amit1237", "BLRRAJ7"),
        JoinRequestInfo(contestId, "amit1238", "BLRRAJ8"),
        JoinRequestInfo(contestId, "amit1239", "BLRRAJ8"),
        JoinRequestInfo(contestId, "amit1230", "BLRRAJ0")
      )

      val result1 =
        eventSourcedTestKit.runCommand[StatusReply[ContestJoin.Summary]](
          replyTo => ContestJoin.AddToContest(contestJoinInfo, replyTo)
        )
      result1.reply should ===(
        StatusReply.Success(ContestJoin.Summary(contestSize, 10)))

      val result2 = eventSourcedTestKit.runCommand[ContestJoin.Summary](
        ContestJoin.Get(_))
      result2.reply should ===(
        ContestJoin.Summary(contestSize,10))
    }
    "reject Join on in-sufficient vacant seat" in {
      val contestJoinInfo: Seq[JoinRequestInfo] = Seq(
        JoinRequestInfo(contestId,"amit12311","BLRRAJ11"),
        JoinRequestInfo(contestId,"amit12322","BLRRAJ22"),
        JoinRequestInfo(contestId,"amit12333","BLRRAJ33"),
        JoinRequestInfo(contestId,"amit12344","BLRRAJ44"),
        JoinRequestInfo(contestId,"amit12355","BLRRAJ55"),
        JoinRequestInfo(contestId,"amit12366","BLRRAJ66"),
        JoinRequestInfo(contestId,"amit12377","BLRRAJ77"),
        JoinRequestInfo(contestId,"amit12388","BLRRAJ88"),
        JoinRequestInfo(contestId,"amit12399","BLRRAJ99"),
        JoinRequestInfo(contestId,"amit12300","BLRRAJ00"),
        JoinRequestInfo(contestId,"amit123111","BLRRAJ11"),
        JoinRequestInfo(contestId,"amit123222","BLRRAJ22"),
        JoinRequestInfo(contestId,"amit123333","BLRRAJ33"),
        JoinRequestInfo(contestId,"amit123444","BLRRAJ44"),
        JoinRequestInfo(contestId,"amit123555","BLRRAJ55"),
        JoinRequestInfo(contestId,"amit123666","BLRRAJ66"),
        JoinRequestInfo(contestId,"amit123777","BLRRAJ77"),
        JoinRequestInfo(contestId,"amit123888","BLRRAJ88"),
        JoinRequestInfo(contestId,"amit123999","BLRRAJ99"),
        JoinRequestInfo(contestId,"amit123000","BLRRAJ00")
      )
      val result1 =
        eventSourcedTestKit.runCommand[StatusReply[ContestJoin.Summary]](
          replyTo => ContestJoin.AddToContest(contestJoinInfo,replyTo))
      logger.info(s"${result1.reply}")
      result1.reply.isError should ===(true)
    }

  }

}


