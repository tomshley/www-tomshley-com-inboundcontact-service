package com.tomshley.www.contact

import com.tomshley.hexagonal.lib.ManagedClusterService
import com.tomshley.hexagonal.lib.http2.GrpcServerBoilerplate
import com.tomshley.www.contact.proto.{ContactService, ContactServiceHandler}
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.grpc.scaladsl.{ServerReflection, ServiceHandler, WebHandler}
import org.apache.pekko.http.scaladsl.model.{HttpRequest, HttpResponse}

import scala.concurrent.Future

@main def main(): Unit = {
  ManagedClusterService("www-tomshley-com-contact-service", (system: ActorSystem[?]) => {
    InboundContact.init(system)

    val implementation = new ContactServiceImpl(system)

    val contactService =
      ContactServiceHandler.partial(implementation)(system)
    val reflectionService =
      ServerReflection.partial(List(ContactService))(system)

    val serviceHandlers: HttpRequest => Future[HttpResponse] =
      ServiceHandler.concatOrNotFound(contactService, reflectionService)

    val grpcWebServiceHandlers =
      WebHandler.grpcWebHandler(contactService)(system)

    GrpcServerBoilerplate.start(
      system.settings.config
        .getString("www-tomshley-com-contact-service.grpc.interface"),
      system.settings.config
        .getInt("www-tomshley-com-contact-service.grpc.port"),
      system,
      serviceHandlers
    )

    GrpcServerBoilerplate.start(
      system.settings.config
        .getString("www-tomshley-com-contact-service.grpc-web.interface"),
      system.settings.config
        .getInt("www-tomshley-com-contact-service.grpc-web.port"),
      system,
      grpcWebServiceHandlers
    )
  })
}
