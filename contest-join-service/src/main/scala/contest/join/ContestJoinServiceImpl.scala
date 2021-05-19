package contest.join

import contest.join.proto.{ContestJoinRequests, GetJoinRequest, JoinRecord, Joins}

import scala.concurrent.Future
import org.slf4j.LoggerFactory

import scala.util.Random

class ContestJoinServiceImpl extends proto.ContestJoinService {

  private val logger = LoggerFactory.getLogger(getClass)

  override def joinContest(in: ContestJoinRequests): Future[Joins] = {

    logger.info("addItem {} to cart {}", in.contestJoinRequest)


    val joinRecords: Seq[JoinRecord] = in.contestJoinRequest.map{ e =>
      proto.JoinRecord(e.contestId,e.contestId,Random.nextInt(40000).toString)
    }
    Future.successful(
      proto.Joins(joinRecords = joinRecords)
    )

  }

  override def getCart(in: GetJoinRequest): Future[Joins] = ???
}
