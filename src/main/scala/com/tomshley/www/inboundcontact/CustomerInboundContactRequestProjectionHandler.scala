package com.tomshley.www.inboundcontact

import com.tomshley.www.inboundcontact.repository.CustomerContactRequestRepository
import org.apache.pekko.Done
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.persistence.query.typed.EventEnvelope
import org.apache.pekko.projection.r2dbc.scaladsl.{R2dbcHandler, R2dbcSession}
import org.slf4j.LoggerFactory

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

class CustomerContactRequestProjectionHandler(
                                               system: ActorSystem[?],
                                               repository: CustomerContactRequestRepository)
  extends R2dbcHandler[EventEnvelope[InboundContact.Event]]() {
  private val logger = LoggerFactory.getLogger(getClass)

  given ec:ExecutionContext = system.executionContext

  override def process(session: R2dbcSession, envelope: EventEnvelope[InboundContact.Event]): Future[Done] = {
    envelope.event match {
      case InboundContact.CustomerContactReceived(contactUUID, name, phone, email, message, inboundTime) => repository.update(session, contactUUID, name, phone, email, message, inboundTime).flatMap(_ => logResult("CustomerContactReceived", contactUUID))
      case InboundContact.ContactTossed(_, contactUUID, _, _, _, _, _) => repository.remove(session, contactUUID).flatMap(_ => logResult("ContactTossed", contactUUID))
      case InboundContact.ContactKept(_, contactUUID, _, _, _, _, _) => repository.remove(session, contactUUID).flatMap(_ => logResult("ContactKept", contactUUID))
      case _ => Future.successful(Done)
    }
  }

  private def logResult(eventName: String, contactUUID: UUID): Future[Done] = {
    logger.info(s"CustomerContactRequestProjectionHandler ${eventName} ${contactUUID}")

    Future.successful(Done)
  }
}