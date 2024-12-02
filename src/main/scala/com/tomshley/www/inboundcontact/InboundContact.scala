package com.tomshley.www.inboundcontact

import com.tomshley.www.inboundcontact.models.ContactCard
import org.apache.pekko.actor.typed.{ActorRef, ActorSystem, Behavior, SupervisorStrategy}
import org.apache.pekko.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity, EntityContext, EntityTypeKey}
import org.apache.pekko.pattern.StatusReply
import org.apache.pekko.persistence.typed.PersistenceId
import org.apache.pekko.persistence.typed.scaladsl.{Effect, EventSourcedBehavior, ReplyEffect, RetentionCriteria}

import java.time.Instant
import java.util.UUID
import scala.concurrent.duration.DurationInt

object InboundContact {

  // Command
  sealed trait Command extends CborSerializable

  // Event
  sealed trait Event extends CborSerializable with ContactCard

  final case class CreateCustomerContactRequest(
    name: String,
    phone: String,
    email: String,
    message: String,
    replyTo: ActorRef[StatusReply[Summary]]
  ) extends Command

  final case class TossContact(salespersonId: String,
                               replyTo: ActorRef[StatusReply[Summary]])
      extends Command
  final case class KeepContact(salespersonId: String,
                               replyTo: ActorRef[StatusReply[Summary]])
      extends Command
  final case class Get(replyTo: ActorRef[Summary]) extends Command

  final case class CustomerContactReceived(contactUUID: UUID,
                                                   name: String,
                                                   phone: String,
                                                   email: String,
                                                   message: String,
                                                   inboundTime: Instant)
      extends Event

  final case class ContactTossed(salespersonId: String,
                                         contactUUID: UUID,
                                         name: String,
                                         phone: String,
                                         email: String,
                                         message: String,
                                         tossTime: Instant)
      extends Event

  final case class ContactKept(salespersonId: String,
                                       contactUUID: UUID,
                                       name: String,
                                       phone: String,
                                       email: String,
                                       message: String,
                                       keptTime: Instant)
      extends Event

  final case class State(contactUUID: Option[UUID],
                         name: Option[String],
                         phone: Option[String],
                         email: Option[String],
                         message: Option[String],
                         inboundTime: Option[Instant],
                         tossTime: Option[Instant],
                         keptTime: Option[Instant])
      extends CborSerializable {
    protected[InboundContact] def contactRequestReceived: Boolean =
      contactUUID.isDefined
    protected[InboundContact] def tossContact: State = {
      copy(
        contactUUID = contactUUID,
        name = name,
        phone = phone,
        email = email,
        message = message,
        inboundTime = inboundTime,
        tossTime = Some(Instant.now()),
        keptTime = None
      )
    }
    protected[InboundContact] def keepContact: State = {
      copy(
        contactUUID = contactUUID,
        name = name,
        phone = phone,
        email = email,
        message = message,
        inboundTime = inboundTime,
        tossTime = None,
        keptTime = Some(Instant.now())
      )
    }
    protected[InboundContact] def receiveCustomerContact(
      uuid: UUID,
      name: String,
      phone: String,
      email: String,
      message: String,
    inboundTime: Instant
    ): State =
      copy(
        contactUUID = Some(uuid),
        name = Some(name),
        phone = Some(phone),
        email = Some(email),
        message = Some(message),
        inboundTime = Some(inboundTime),
        tossTime = tossTime,
        keptTime = keptTime
      )

    protected[InboundContact] def isTossed: Boolean = tossTime.isDefined
    protected[InboundContact] def isKept: Boolean = keptTime.isDefined
    protected[InboundContact] def isCreated: Boolean =
      contactUUID.isDefined && name.isDefined && phone.isEmpty && email.isDefined && message.isDefined

    def toSummary: Summary = Summary(contactUUID)
  }

  final case class Summary(contactUUID: Option[UUID]) extends CborSerializable

  private object State {
    val empty: State =
      State(
        contactUUID = None,
        name = None,
        phone = None,
        email = None,
        message = None,
        inboundTime = None,
        tossTime = None,
        keptTime = None
      )
  }

  val EntityKey: EntityTypeKey[Command] =
    EntityTypeKey[Command]("InboundContact")

  val tags = Vector.tabulate(5)(i => s"inboundcontact-service-inboundcontact-$i")

  def init(system: ActorSystem[?]): Unit = {
    val behaviorFactory: EntityContext[Command] => Behavior[Command] = {
      entityContext =>
        val i = math.abs(entityContext.entityId.hashCode % tags.size)
        val selectedTag = tags(i)
        InboundContact(
          generateEntityUUID(Some(entityContext.entityId)),
          selectedTag
        )
    }
    ClusterSharding(system).init(Entity(EntityKey)(behaviorFactory))
  }

  def generateEntityUUID(existingUUIDString: Option[String] = None): UUID = {
    if (existingUUIDString.isDefined) {
      UUID.fromString(existingUUIDString.get)
    } else {
      UUID.randomUUID()
    }
  }

  def apply(contactUUID: UUID, projectionTag: String): Behavior[Command] = {
    EventSourcedBehavior
      .withEnforcedReplies(
        PersistenceId(EntityKey.name, contactUUID.toString),
        State.empty,
        (state, event) => handleCommand(contactUUID, state, event),
        (state, event) => handleEvent(state, event)
      )
      .withTagger(_ => Set(projectionTag))
      .withRetention(
        RetentionCriteria
          .snapshotEvery(numberOfEvents = 100, keepNSnapshots = 3)
      )
      .onPersistFailure(
        SupervisorStrategy.restartWithBackoff(200.millis, 5.seconds, 0.1)
      )
  }

  private def handleCommand(contactUUID: UUID,
                            state: State,
                            command: Command): ReplyEffect[Event, State] = {
    command match {
      case Get(replyTo) =>
        Effect.reply(replyTo)(state.toSummary)

      case CreateCustomerContactRequest(name, phone, email, message, replyTo) =>
        if (state.contactRequestReceived) {
          Effect.reply(replyTo)(
            StatusReply.Error(s"Contact request id already initiated")
          )
        } else {
          Effect
            .persist(
              CustomerContactReceived(contactUUID, name, phone, email, message, Instant.now())
            )
            .thenReply(replyTo) { stateResult =>
              StatusReply.Success(stateResult.toSummary)
            }
        }
      case TossContact(salespersonId, replyTo) =>
        if (state.isTossed) {
          Effect.reply(replyTo)(StatusReply.Error(s"Contact already tossed"))
        } else if (!state.isCreated) {
          Effect.reply(replyTo)(StatusReply.Error(s"Contact already tossed"))
        } else {
          Effect
            .persist(
              ContactTossed(
                salespersonId,
                contactUUID,
                state.name.get,
                state.phone.get,
                state.email.get,
                state.message.get, 
                Instant.now()
              )
            )
            .thenReply(replyTo) { stateResult =>
              StatusReply.Success(stateResult.toSummary)
            }
        }
      case KeepContact(salespersonId, replyTo) =>
        if (state.isKept) {
          Effect.reply(replyTo)(StatusReply.Error(s"Contact already kept"))
        } else if (!state.isCreated) {
          Effect.reply(replyTo)(StatusReply.Error(s"Contact not created"))
        } else {
          Effect
            .persist(
              ContactKept(
                salespersonId,
                contactUUID,
                state.name.get,
                state.phone.get,
                state.email.get,
                state.message.get, 
                Instant.now()
              )
            )
            .thenReply(replyTo) { stateResult =>
              StatusReply.Success(stateResult.toSummary)
            }
        }
    }
  }

  private def handleEvent(state: State, event: Event): State = {
    event match {
      case CustomerContactReceived(contactUUID, name, phone, email, message, inboundTime) =>
        state.receiveCustomerContact(contactUUID, name, phone, email, message, inboundTime)
      case ContactTossed(_, _, _, _, _, _, _) =>
        state.tossContact
      case ContactKept(_, _, _, _, _, _, _) =>
        state.keepContact
    }
  }
}
