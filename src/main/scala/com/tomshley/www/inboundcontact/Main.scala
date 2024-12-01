package com.tomshley.www.inboundcontact

import com.tomshley.hexagonal.lib.ManagedPekkoClusterMain
import com.tomshley.hexagonal.lib.http2.GrpcServerBoilerplate
import com.tomshley.www.inboundcontact.proto.{InboundContactService, InboundContactServiceHandler}
import com.tomshley.www.inboundcontact.repository.CustomerContactRequestRepositoryImpl
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.grpc.scaladsl.{ServerReflection, ServiceHandler, WebHandler}
import org.apache.pekko.http.scaladsl.model.{HttpRequest, HttpResponse}

import scala.concurrent.Future

@main def main(): Unit = {
  ManagedPekkoClusterMain("www-tomshley-com-inboundcontact-service", (system: ActorSystem[?]) => {
    InboundContact.init(system)

    val customerContactRequestRepository = new CustomerContactRequestRepositoryImpl()
    CustomerContactRequestProjection.init(system, customerContactRequestRepository)
    PublishEventsProjection.init(system)

    val implementation = new InboundContactServiceImpl(system)

    val contactService =
      InboundContactServiceHandler.partial(implementation)(system)
    val reflectionService =
      ServerReflection.partial(List(InboundContactService))(system)

    val serviceHandlers: HttpRequest => Future[HttpResponse] =
      ServiceHandler.concatOrNotFound(contactService, reflectionService)

    val grpcWebServiceHandlers =
      WebHandler.grpcWebHandler(contactService)(system)

    GrpcServerBoilerplate.start(
      system.settings.config
        .getString("www-tomshley-com-inboundcontact-service.grpc.interface"),
      system.settings.config
        .getInt("www-tomshley-com-inboundcontact-service.grpc.port"),
      system,
      serviceHandlers
    )

    GrpcServerBoilerplate.start(
      system.settings.config
        .getString("www-tomshley-com-inboundcontact-service.grpc-web.interface"),
      system.settings.config
        .getInt("www-tomshley-com-inboundcontact-service.grpc-web.port"),
      system,
      grpcWebServiceHandlers
    )
  })
}
