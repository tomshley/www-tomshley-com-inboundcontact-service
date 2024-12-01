package com.tomshley.www.inboundcontact

import com.tomshley.www.inboundcontact.repository.CustomerContactRequestRepository
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.cluster.sharding.typed.scaladsl.ShardedDaemonProcess
import org.apache.pekko.persistence.query.Offset
import org.apache.pekko.persistence.query.typed.EventEnvelope
import org.apache.pekko.persistence.r2dbc.query.scaladsl.R2dbcReadJournal
import org.apache.pekko.projection.eventsourced.scaladsl.EventSourcedProvider
import org.apache.pekko.projection.r2dbc.scaladsl.R2dbcProjection
import org.apache.pekko.projection.scaladsl.SourceProvider
import org.apache.pekko.projection.{Projection, ProjectionBehavior, ProjectionId}

object CustomerContactRequestProjection {
  def init(
            system: ActorSystem[?],
            repository: CustomerContactRequestRepository): Unit = {

    val numberOfSliceRanges: Int = system.settings.config.getInt("pekko.management.cluster.bootstrap.required-contact-point-nr")
    val sliceRanges: Seq[Range] = EventSourcedProvider.sliceRanges(system, R2dbcReadJournal.Identifier, numberOfSliceRanges)

    def sourceProvider(sliceRange: Range)
    : SourceProvider[Offset, EventEnvelope[InboundContact.Event]] =
      EventSourcedProvider
        .eventsBySlices[InboundContact.Event](
          system,
          readJournalPluginId = R2dbcReadJournal.Identifier,
          "InboundContact",
          sliceRange.min,
          sliceRange.max)

    def projection(
                    sliceRange: Range): Projection[EventEnvelope[InboundContact.Event]] = {
      val minSlice = sliceRange.min
      val maxSlice = sliceRange.max
      val projectionId =
        ProjectionId("CustomerContactRequestProjection", s"inboundcontact-service-inboundcontact-$minSlice-$maxSlice")

      R2dbcProjection.exactlyOnce(
        projectionId,
        settings = None,
        sourceProvider(sliceRange),
        handler = () =>
          new CustomerContactRequestProjectionHandler(
            system,
            repository))(system)
    }
    ShardedDaemonProcess(system).init(
      name = "CustomerContactRequestProjection",
      numberOfInstances = sliceRanges.size,
      behaviorFactory = index =>
        ProjectionBehavior(projection(sliceRanges(index))),
      stopMessage = ProjectionBehavior.Stop
    )
  }
}