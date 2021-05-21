package contest.join.repository

import contest.join.JoinConfirmationInfo
import scalikejdbc._

import scala.collection.IndexedSeq.iterableFactory


trait ContestJoinRepository {
  def update(session: ScalikeJdbcSession, contestId: String, joinConfirmationInfo: Seq[JoinConfirmationInfo]): Unit
  def getContestJoin(session: ScalikeJdbcSession, contestId: String): Seq[JoinConfirmationInfo]
  def getUserJoin(session: ScalikeJdbcSession, contestId: String): Seq[JoinConfirmationInfo]
  def getAll(session: ScalikeJdbcSession): Seq[JoinConfirmationInfo]
}

//contestId: String,userId: String, joinMetaData: String, positionId: Int

class ContestJoinRepositoryImpl() extends ContestJoinRepository {

  override def update(session: ScalikeJdbcSession,
                      contestId: String,
                      joinConfirmationInfo: Seq[JoinConfirmationInfo]): Unit = {
    session.db.withinTx { implicit dbSession =>
      // This uses the PostgreSQL `ON CONFLICT` feature
      // Alternatively, this can be implemented by first issuing the `UPDATE`
      // and checking for the updated rows count. If no rows got updated issue
      // the `INSERT` instead.
      val batchValues: Seq[Seq[(String, Any)]] = joinConfirmationInfo.map { info =>
        Seq(("contest_id" -> info.contestId),
          ("user_id" -> info.userId),
          ("join_meta_data" -> info.joinMetaData),
          ("position_id" -> info.positionId)
        )
      }

      sql"""
           INSERT INTO contest_join_meta (contest_id, user_id, join_meta_data, position_id)
           VALUES ( {contest_id}, {user_id}, {join_meta_data}, {position_id} )""".batchByName(batchValues:_*)
         .apply()
      //below insert multiple time instead of one go
      /*joinConfirmationInfo.map{info =>
        sql"""
           INSERT INTO contest_join_meta (contest_id, user_id, join_meta_data, position_id)
           VALUES (${info.contestId}, ${info.userId}, ${info.joinMetaData}, ${info.positionId})
         """.executeUpdate().apply()
      }*/
    }

  }

  override def getContestJoin(session: ScalikeJdbcSession, contestId: String): Seq[JoinConfirmationInfo] = {
    if (session.db.isTxAlreadyStarted) {
      session.db.withinTx { implicit dbSession =>
        selectByContest(contestId)
      }
    } else {
      session.db.readOnly { implicit dbSession =>
        selectByContest(contestId)
      }
    }
  }

  override def getUserJoin(session: ScalikeJdbcSession, userId: String): Seq[JoinConfirmationInfo] = {
    if (session.db.isTxAlreadyStarted) {
      session.db.withinTx { implicit dbSession =>
        selectByUser(userId)
      }
    } else {
      session.db.readOnly { implicit dbSession =>
        selectByUser(userId)
      }
    }
  }

  override def getAll(session: ScalikeJdbcSession): Seq[JoinConfirmationInfo] = {
    if (session.db.isTxAlreadyStarted) {
      session.db.withinTx { implicit dbSession =>
        selectAll()
      }
    } else {
      session.db.readOnly { implicit dbSession =>
        selectAll()
      }
    }
  }

  private def selectByContest(contestId: String)(implicit dbSession: DBSession): Seq[JoinConfirmationInfo] = {
    sql"SELECT * FROM contest_join_meta WHERE contest_id = $contestId"
      .map(result =>
        JoinConfirmationInfo(
          result.string("contest_id"),
          result.string("user_id"),
          result.string("join_meta_data"),
          result.int("position_id")
        )
      ).toList().apply()
  }
  private def selectByUser(userId: String)(implicit dbSession: DBSession): Seq[JoinConfirmationInfo] = {
    sql"SELECT * FROM contest_join_meta WHERE user_id = $userId"
      .map(result =>
        JoinConfirmationInfo(
          result.string("contest_id"),
          result.string("user_id"),
          result.string("join_meta_data"),
          result.int("position_id")
        )
      ).toList().apply()
  }

  private def selectAll()(implicit dbSession: DBSession): Seq[JoinConfirmationInfo] = {
    sql"SELECT * FROM contest_join_meta"
      .map(result =>
        JoinConfirmationInfo(
          result.string("contest_id"),
          result.string("user_id"),
          result.string("join_meta_data"),
          result.int("position_id")
        )
      ).toList().apply()
  }
}
