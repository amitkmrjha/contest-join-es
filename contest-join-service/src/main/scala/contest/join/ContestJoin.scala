package contest.join

import akka.actor.typed.{ActorRef, ActorSystem, Behavior, SupervisorStrategy}
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity, EntityTypeKey}
import akka.pattern.StatusReply
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior, ReplyEffect, RetentionCriteria}

import scala.concurrent.duration._

object ContestJoin {

  final case class State(maxSeat : Int,occupiedSeatCount: Int) extends CborSerializable {

    def isVacant: Boolean = occupiedSeatCount < maxSeat

    def unOccupiedSeat: Int = ((maxSeat - occupiedSeatCount))

    def updateOccupiedSeatCount(size: Int): State = {
      copy(occupiedSeatCount = occupiedSeatCount + size)
    }

    def toSummary: Summary = Summary(maxSeat, occupiedSeatCount)
  }
  object State {
    def emptyInit(contestSize: Int) =
      State(contestSize, occupiedSeatCount = 0)
  }


  /**
   * This interface defines all the commands (messages) that the ContestJoin actor supports.
   */
  sealed trait Command extends CborSerializable

  /**
   * A command to join .
   *
   * It replies with `StatusReply[Summary]`, which is sent back to the caller when
   * all the events emitted by this command are successfully persisted.
   */
  final case class AddToContest(contestJoinInfo: Seq[JoinRequestInfo],
                                 replyTo: ActorRef[StatusReply[Summary]]) extends Command

  final case class Get(replyTo: ActorRef[Summary]) extends Command


  /**
   * Summary of the contest join state, used in reply messages.
   */
  final case class Summary(maxSeat : Int,occupiedSeatCount: Int) extends CborSerializable

  /**
   * This interface defines all the events that the ContestJoin supports.
   */
  sealed trait Event extends CborSerializable {
    def contestId: String
  }

  final case class UsersAddedToContest(contestId: String, joinConfirmationInfo: Seq[JoinConfirmationInfo])
    extends Event

  val EntityKey: EntityTypeKey[Command] =
    EntityTypeKey[Command]("ContestJoin")

  val tags = Vector.tabulate(5)(i => s"contest-join-$i")

  def init(system: ActorSystem[_],contestSize: Int): Unit = {
    ClusterSharding(system).init(Entity(EntityKey) { entityContext =>
      val i = math.abs(entityContext.entityId.hashCode % tags.size)
      val selectedTag = tags(i)
      ContestJoin(entityContext.entityId,contestSize,selectedTag)
    })
  }

  def apply(contestId: String,contestSize: Int,projectionTag: String): Behavior[Command] = {
    EventSourcedBehavior
      .withEnforcedReplies[Command, Event, State](
        persistenceId = PersistenceId(EntityKey.name, contestId),
        emptyState = State.emptyInit(contestSize),
        commandHandler =
          (state, command) => handleCommand(contestId, state, command),
        eventHandler = (state, event) => handleEvent(state, event))
      .withTagger(_ => Set(projectionTag))
      .withRetention(RetentionCriteria
        .snapshotEvery(numberOfEvents = 100, keepNSnapshots = 3))
      .onPersistFailure(
        SupervisorStrategy.restartWithBackoff(200.millis, 5.seconds, 0.1)
      )
  }



  private def handleCommand( contestId: String,
                             state: State,
                             command: Command): ReplyEffect[Event, State] = {
      fillContestJoinSeat(contestId, state, command)
  }

  private def fillContestJoinSeat( contestId: String,
                                   state: State,
                                   command: Command): ReplyEffect[Event, State] = {
    command match {
      case AddToContest(contestJoinInfo, replyTo) =>
        val vacantSeat = state.unOccupiedSeat
        if (vacantSeat <= 0  )
          Effect.reply(replyTo)(
            StatusReply.Error(
              s"Contest '$contestId' is full. No More join request can be processed"))
        else if (vacantSeat < contestJoinInfo.size)
          Effect.reply(replyTo)(
            StatusReply.Error(s"Contest '$contestId' do not have sufficient seat to  fill ${contestJoinInfo.size} seat. " +
              s"Currently only $vacantSeat seat left to occupy."))
        else {
          val joinConfirmationInfo : Seq[JoinConfirmationInfo] =
            contestJoinInfo.foldLeft(Seq.empty[JoinConfirmationInfo]) { (acc, e) =>
              acc :+ JoinConfirmationInfo(contestId,e.userId,e.joinMetaData,state.occupiedSeatCount+acc.size+1)
            }
          Effect
            .persist(
              UsersAddedToContest(
                contestId,joinConfirmationInfo
              )
            ).thenReply(replyTo) { updatedCart =>
              StatusReply.Success(updatedCart.toSummary)
            }
        }

      case Get(replyTo) =>
        Effect.reply(replyTo)(state.toSummary)
    }
  }



  private def handleEvent(state: State, event: Event): State = {
    event match {
      case UsersAddedToContest(contestId, joinConfirmationInfo) =>
        state.updateOccupiedSeatCount(joinConfirmationInfo.size)
    }
  }

}

final case class JoinRequestInfo(contestId: String,userId: String, joinMetaData: String) extends  CborSerializable
final case class JoinConfirmationInfo(contestId: String,userId: String, joinMetaData: String, positionId: Int) extends  CborSerializable
