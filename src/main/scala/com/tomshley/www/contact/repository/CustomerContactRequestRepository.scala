package com.tomshley.www.contact.repository

import com.tomshley.www.contact.models.CustomerContactRequest
import org.apache.pekko.projection.r2dbc.scaladsl.R2dbcSession

import java.time.Instant
import java.util.UUID
import scala.collection.mutable
import scala.collection.mutable.Stack
import scala.concurrent.Future

trait CustomerContactRequestRepository {
  protected final val tableName = "customer_contact_requests"

  def update(session: R2dbcSession, contactId: UUID, name: String, phone: String, email: String, message: String, inboundTime: Instant): Future[Int]

  def getContactRequest(session: R2dbcSession, contactId: UUID): Future[Option[CustomerContactRequest]]

  def listContactRequests(session: R2dbcSession, limit: Option[Int], offset: Option[Int]): Future[IndexedSeq[CustomerContactRequest]]

  def remove(session: R2dbcSession, contactId: UUID): Future[Int]
}

class CustomerContactRequestRepositoryImpl() extends CustomerContactRequestRepository {
  override def update(session: R2dbcSession, contactId: UUID, name: String, phone: String, email: String, message: String, inboundTime: Instant): Future[Int] =
    session.updateOne(
      session
        .createStatement(s"INSERT INTO ${tableName} (contact_id, name, phone, email, message, inbound_time_epochmilli) VALUES ($$1, $$2, $$3, $$4, $$5, $$6)")
        .bind(0, contactId.toString)
        .bind(1, name)
        .bind(2, phone)
        .bind(3, email)
        .bind(4, message)
        .bind(5, inboundTime.toEpochMilli)
    )

  override def listContactRequests(session: R2dbcSession, limit: Option[Int], offset: Option[Int]): Future[IndexedSeq[CustomerContactRequest]] =
    var args: mutable.Stack[(String, Int)] = mutable.Stack.empty
    if (limit.isDefined) {
      args.push(("LIMIT", limit.get))
    }
    if (offset.isDefined) {
      args.push(("OFFSET", offset.get))
    }

    val statement: String = (Seq(s"SELECT * FROM ${tableName} ORDER BY inbound_time_epochmilli DESC") ++ args.view.zipWithIndex.map {
      case ((arg: String, _), index) =>
        s"${arg.toUpperCase} $$${index + 1}"
    }.toSeq).mkString(" ")

    val createStatement = session
      .createStatement(statement)

    for (((_, value: Int), index) <- args.view.zipWithIndex) {
      createStatement.bind(index, value)
    }

    session.select(createStatement) {
      row =>
        CustomerContactRequest(
          row.get("contact_id", classOf[String]),
          row.get("name", classOf[String]),
          row.get("phone", classOf[String]),
          row.get("email", classOf[String]),
          row.get("message", classOf[String]),
          row.get("inbound_time_epochmilli", classOf[Long])
        )
    }

  override def getContactRequest(session: R2dbcSession, contactId: UUID): Future[Option[CustomerContactRequest]] =
    session.selectOne(
      session
        .createStatement(s"SELECT name, phone, email, message, inbound_time_epochmilli FROM ${tableName} WHERE contact_id = $$1")
        .bind(0, contactId.toString)
    ) { row =>
      CustomerContactRequest(
        contactId.toString,
        row.get("name", classOf[String]),
        row.get("phone", classOf[String]),
        row.get("email", classOf[String]),
        row.get("message", classOf[String]),
        row.get("inbound_time_epochmilli", classOf[Long])
      )
    }

  override def remove(session: R2dbcSession, contactId: UUID): Future[Int] = session.updateOne(
    session.createStatement(s"DELETE FROM ${tableName} WHERE contact_id = $$1").bind(0, contactId.toString)
  )
}
