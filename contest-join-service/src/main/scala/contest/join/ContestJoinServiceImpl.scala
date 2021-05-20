package contest.join

import akka.actor.typed.{ActorSystem, DispatcherSelector}
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import akka.grpc.GrpcServiceException
import akka.util.Timeout
import contest.join.proto._
import contest.join.repository.{ContestJoinRepository, ScalikeJdbcSession}
import io.grpc.Status
import org.slf4j.LoggerFactory

import java.util.concurrent.TimeoutException
import scala.concurrent.{ExecutionContext, Future}

class ContestJoinServiceImpl(system: ActorSystem[_],contestJoinRepository: ContestJoinRepository) extends proto.ContestJoinService {

  import system.executionContext

  private val logger = LoggerFactory.getLogger(getClass)

  implicit private val timeout: Timeout =
    Timeout.create(
      system.settings.config.getDuration("shopping-cart-service.ask-timeout"))

  private val sharding = ClusterSharding(system)

  private val blockingJdbcExecutor: ExecutionContext =
    system.dispatchers.lookup(
      DispatcherSelector
        .fromConfig("akka.projection.jdbc.blocking-jdbc-dispatcher")
    )

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

  override def getJoinByContest(in: proto.JoinByContestRequest): Future[proto.ContestUserResponse] = {
    Future {
      ScalikeJdbcSession.withSession { session =>
        contestJoinRepository.getContestJoin(session, in.contestId)
      }
    }(blockingJdbcExecutor).map { response =>
      val userinfo: Seq[ContestUserInfo] = response.map { info =>
          proto.ContestUserInfo(info.contestId,info.userId,info.joinMetaData,info.positionId)
      }
      proto.ContestUserResponse(userinfo)
    }
  }

  override def getJoinByUser(in: proto.JoinByUserRequest): Future[proto.ContestUserResponse] = {
    Future {
      ScalikeJdbcSession.withSession { session =>
        contestJoinRepository.getUserJoin(session, in.userId)
      }
    }(blockingJdbcExecutor).map { response =>
      val userinfo: Seq[ContestUserInfo] = response.map { info =>
        proto.ContestUserInfo(info.contestId,info.userId,info.joinMetaData,info.positionId)
      }
      proto.ContestUserResponse(userinfo)
    }
  }

  override def getAllJoin(in: EmptyParameter): Future[ContestUserResponse] = {
    Future {
      ScalikeJdbcSession.withSession { session =>
        contestJoinRepository.getAll(session)
      }
    }(blockingJdbcExecutor).map { response =>
      val userinfo: Seq[ContestUserInfo] = response.map { info =>
        proto.ContestUserInfo(info.contestId,info.userId,info.joinMetaData,info.positionId)
      }
      proto.ContestUserResponse(userinfo)
    }
  }


}
