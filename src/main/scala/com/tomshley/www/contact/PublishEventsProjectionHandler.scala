package com.tomshley.www.contact

import com.tomshley.hexagonal.lib.kafka.util.KafkaKeyMessageEnvelope
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.pekko.Done
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.kafka.scaladsl.SendProducer
import org.apache.pekko.persistence.query.typed
import org.apache.pekko.projection.scaladsl.Handler
import org.slf4j.LoggerFactory

import scala.concurrent.{ExecutionContext, Future}

class PublishEventsProjectionHandler(system: ActorSystem[?], kafkaTopic:String, sendProducer: SendProducer[String, Array[Byte]])
  extends org.apache.pekko.projection.scaladsl.Handler[org.apache.pekko.persistence.query.typed.EventEnvelope[com.tomshley.www.contact.InboundContact.Event]]() {
  given ec:ExecutionContext = system.executionContext

  private val logger = LoggerFactory.getLogger(getClass)

  private final val serviceName = "www-tomshley-com-contact-service"


  private def serializeRequestToEvent(envelope: typed.EventEnvelope[InboundContact.Event]): Option[KafkaKeyMessageEnvelope] = {
    envelope.event match {
      case InboundContact.CustomerContactReceived(contactUUID, name, phone, email, message, inboundTime) =>
        Some(KafkaKeyMessageEnvelope(
          serviceName,
          envelope, proto.CustomerContactReceived(contactUUID.toString, name, phone, email, message, inboundTime.toEpochMilli.intValue)
        ))
      case InboundContact.ContactKept(salespersonId, contactUUID, name, phone, email, message, keptTime) =>
        Some(KafkaKeyMessageEnvelope(
          serviceName,
          envelope, proto.ContactKept(salespersonId, contactUUID.toString, name, phone, email, message, keptTime.toEpochMilli.intValue)
        ))
      case InboundContact.ContactTossed(salespersonId, contactUUID, name, phone, email, message, tossTime) =>
        Some(KafkaKeyMessageEnvelope(
          serviceName,
          envelope, proto.ContactTossed(salespersonId, contactUUID.toString, name, phone, email, message, tossTime.toEpochMilli.intValue)
        ))

      case _ => None
    }
  }

  private def sendProducerRecord(kafkaKeyMessageEnvelope: KafkaKeyMessageEnvelope): Future[Done] = {
    val producerRecord = new ProducerRecord(kafkaTopic, kafkaKeyMessageEnvelope.key, kafkaKeyMessageEnvelope.messageBytes)
    val result = sendProducer.send(producerRecord)
    result.map(_ => {
      logger.info(s"PublishEventsProjectionHandler ${kafkaTopic} ${kafkaKeyMessageEnvelope.key}, ${kafkaKeyMessageEnvelope.messageBytes.mkString("Array(", ", ", ")")}")
      Done
    })
  }
  override def process(envelope: typed.EventEnvelope[InboundContact.Event]): Future[Done] = {
    val kafkaKeyMessageEnvelope = serializeRequestToEvent(envelope)
    kafkaKeyMessageEnvelope match
      case Some(value) => sendProducerRecord(value)
      case None => Future.successful(Done)
  }
}