package contest.join

import contest.join.proto.{ContestJoinRequest, ContestJoinRequests, GetJoinRequest, JoinRecord, JoinState, Joins}

import scala.concurrent.Future
import org.slf4j.LoggerFactory
import akka.actor.typed.ActorSystem

import java.util.concurrent.TimeoutException
import scala.concurrent.Future
import akka.actor.typed.ActorSystem
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import akka.grpc.GrpcServiceException
import akka.util.Timeout
import io.grpc.Status

import scala.collection.immutable
import scala.util.Random

class ContestJoinServiceImpl(system: ActorSystem[_]) extends proto.ContestJoinService {

  import system.executionContext

  private val logger = LoggerFactory.getLogger(getClass)

  implicit private val timeout: Timeout =
    Timeout.create(
      system.settings.config.getDuration("shopping-cart-service.ask-timeout"))

  private val sharding = ClusterSharding(system)

  override def joinContest(in: ContestJoinRequests): Future[Joins] = {

    logger.info("join contest {}", in.contestJoinRequest)

    val grpcResponse: Seq[Future[Seq[JoinRecord]]] = in.contestJoinRequest.groupBy(_.contestId).map { e =>
      val cId = e._1
      val inputRequestInfo = e._2.map{e =>
        JoinRequestInfo(cId,e.userId,e.joinMetaData)
      }
      logger.info(s"Process request for contest ${cId} with request info ${inputRequestInfo}")

      val entityRef = sharding.entityRefFor(ContestJoin.EntityKey, cId)
      val reply: Future[ContestJoin.Summary] =
        entityRef.askWithStatus(ContestJoin.AddToContest(inputRequestInfo,_))

      val response = reply.map{_ =>
        toProtoJoins(inputRequestInfo)
      }
      convertError(response)
    }.toSeq
   Future.sequence(grpcResponse).map( p=>proto.Joins(p.flatten))
  }


  private def toProtoJoins(reqInfo: Seq[JoinRequestInfo]): Seq[proto.JoinRecord] = {
    reqInfo.map{req =>
      proto.JoinRecord(req.contestId,req.userId,"Confirmed")
    }
  }

  private def convertError[T](response: Future[T]): Future[T] = {
    response.recoverWith {
      case _: TimeoutException =>
        Future.failed(
          new GrpcServiceException(
            Status.UNAVAILABLE.withDescription("Operation timed out")))
      case exc =>
        Future.failed(
          new GrpcServiceException(
            Status.INVALID_ARGUMENT.withDescription(exc.getMessage)))
    }
  }

  override def getContestJoin(in: GetJoinRequest): Future[proto.JoinState] = {
    logger.info("getContestJoin {}", in.contestId)
    val entityRef = sharding.entityRefFor(ContestJoin.EntityKey, in.contestId)
    val response =
      entityRef.ask(ContestJoin.Get).map { summary =>
        proto.JoinState(in.contestId,summary.maxSeat,summary.occupiedSeatCount)
      }
    convertError(response)
  }
}
