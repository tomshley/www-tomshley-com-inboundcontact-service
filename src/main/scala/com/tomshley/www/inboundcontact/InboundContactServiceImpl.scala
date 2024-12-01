package com.tomshley.www.inboundcontact

import com.tomshley.hexagonal.lib.reqreply.Idempotency
import com.tomshley.www.inboundcontact.proto.{
  GetInboundContactRequest,
  InboundContactResponse,
  InitiateInboundContactRequest,
  KeepInboundContactRequest,
  TossInboundContactRequest
}
import io.grpc.Status
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.cluster.sharding.typed.scaladsl.ClusterSharding
import org.apache.pekko.grpc.GrpcServiceException
import org.apache.pekko.util.Timeout
import org.slf4j.{Logger, LoggerFactory}

import java.util.concurrent.TimeoutException
import scala.concurrent.Future

class InboundContactServiceImpl(system: ActorSystem[?]) extends proto.InboundContactService {
  import system.executionContext

  private val logger: Logger = LoggerFactory.getLogger(getClass)

  given timeout: Timeout =
    Timeout.create(
      system.settings.config
        .getDuration("www-tomshley-com-inboundcontact-service.ask-timeout")
    )

  private val inboundContactCluster = ClusterSharding(system)

  override def inboundContact(
    in: InitiateInboundContactRequest
  ): Future[InboundContactResponse] = {
    val contactUUID = InboundContact.generateEntityUUID()

    val entityRef = inboundContactCluster
      .entityRefFor(InboundContact.EntityKey, contactUUID.toString)

    val reply: Future[InboundContact.Summary] = {
      entityRef.askWithStatus(
        InboundContact
          .CreateCustomerContactRequest(in.name, in.phone, in.email, in.message, _)
      )
    }
    val response = reply.map(summary => {
      logger.info("contactUUID {};", summary.contactUUID)

      InboundContactResponse(
        replyMessage = "Thank you for submitting a contact request!",
        inboundContact =
          Some(proto.InboundContact(inboundContactUUID = summary.contactUUID.get.toString))
      )
    })
    convertError(response)
  }

  private def convertError[T](response: Future[T]): Future[T] = {
    response.recoverWith {
      case _: TimeoutException =>
        Future.failed(
          new GrpcServiceException(
            Status.UNAVAILABLE.withDescription("Operation timed out")
          )
        )
      case exc =>
        Future.failed(
          new GrpcServiceException(
            Status.UNKNOWN.withDescription(exc.getMessage)
          )
        )
    }
  }

  override def getInboundContact(
    in: GetInboundContactRequest
  ): Future[InboundContactResponse] = {
    val contactUUID = InboundContact.generateEntityUUID(Some(in.inboundContactUUID))
    val entityRef = inboundContactCluster.entityRefFor(
      InboundContact.EntityKey,
      contactUUID.toString
    )
    val response =
      entityRef.ask(InboundContact.Get).map { contact =>
        if (contact.contactUUID.isEmpty)
          throw new GrpcServiceException(
            Status.NOT_FOUND
              .withDescription(s"Contact ${in.inboundContactUUID} not found")
          )
        else {
          logger.info("response {};", contact.contactUUID)
          InboundContactResponse(
            replyMessage = "You found a contact, thank you!",
            inboundContact =
              Some(proto.InboundContact(inboundContactUUID = contact.contactUUID.get.toString))
          )
        }

      }
    convertError(response)
  }

  override def tossInboundContact(
    in: TossInboundContactRequest
  ): Future[InboundContactResponse] = {
    val contactUUID = InboundContact.generateEntityUUID(Some(in.inboundContactUUID))
    val entityRef = inboundContactCluster
      .entityRefFor(InboundContact.EntityKey, contactUUID.toString)
    val reply: Future[InboundContact.Summary] =
      entityRef.askWithStatus(
        InboundContact
          .TossContact(in.salespersonId, _)
      )
    val response = reply.map(summary => {
      logger.info("contactUUID {};", summary.contactUUID)

      InboundContactResponse(
        replyMessage = "You tossed a contact, hope it was worth it!",
        inboundContact =
          Some(proto.InboundContact(inboundContactUUID = summary.contactUUID.get.toString))
      )
    })
    convertError(response)
  }

  override def keepInboundContact(
    in: KeepInboundContactRequest
  ): Future[InboundContactResponse] = {

    val contactUUID = InboundContact.generateEntityUUID(Some(in.inboundContactUUID))
    val entityRef = inboundContactCluster
      .entityRefFor(InboundContact.EntityKey, contactUUID.toString)
    val reply: Future[InboundContact.Summary] =
      entityRef.askWithStatus(
        InboundContact
          .KeepContact(in.salespersonId, _)
      )
    val response = reply.map(summary => {
      logger.info("contactUUID {};", summary.contactUUID)

      InboundContactResponse(
        replyMessage = "You kept a contact, hope it was worth it!",
        inboundContact =
          Some(proto.InboundContact(inboundContactUUID = summary.contactUUID.get.toString))
      )
    })
    convertError(response)
  }
}
