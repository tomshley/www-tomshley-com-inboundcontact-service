/*

    val kafkaTopic = system.settings.config.getString("www-tomshley-com-contact-service.kafka.topic")
    val sendProducer = ProducerBoilerplate.init(kafkaTopic, system)
 */

package com.tomshley.www.contact

import com.tomshley.hexagonal.lib.kafka.util.ProducerBoilerplate
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.cluster.sharding.typed.scaladsl.ShardedDaemonProcess
import org.apache.pekko.persistence.r2dbc.query.scaladsl.R2dbcReadJournal
import org.apache.pekko.projection.eventsourced.scaladsl.EventSourcedProvider
import org.apache.pekko.projection.r2dbc.scaladsl.R2dbcProjection
import org.apache.pekko.projection.{ProjectionBehavior, ProjectionId, eventsourced}

object PublishEventsProjection {
  def init(system: ActorSystem[?]): Unit = {
    /* Note:
    There are alternative ways of running the ProjectionBehavior as described in Running a Projection, but note that when using the R2DBC plugin as SourceProvider it is recommended to use eventsBySlices and not eventsByTag.
     */
    val kafkaTopic = system.settings.config.getString("www-tomshley-com-contact-service.kafka.topic")
    val sendProducer = ProducerBoilerplate.init(system)
    
    val numberOfSliceRanges: Int = system.settings.config.getInt("pekko.management.cluster.bootstrap.required-contact-point-nr")
    val sliceRanges: Seq[Range] = EventSourcedProvider.sliceRanges(system, R2dbcReadJournal.Identifier, numberOfSliceRanges)

    def sourceProvider(sliceRange: Range) =
      EventSourcedProvider
        .eventsBySlices[InboundContact.Event](
          system,
          readJournalPluginId = R2dbcReadJournal.Identifier,
          "InboundContact",
          sliceRange.min,
          sliceRange.max)


    def projection(
                    sliceRange: Range)= {
      val minSlice = sliceRange.min
      val maxSlice = sliceRange.max

      val projectionId =
        ProjectionId("PublishEventsProjection", s"contact-service-inboundcontact-$minSlice-$maxSlice")


      R2dbcProjection.atLeastOnceAsync(
        projectionId,
        settings = None,
        sourceProvider(sliceRange),
        handler = () => new PublishEventsProjectionHandler(system, kafkaTopic, sendProducer))(system)
    }

    ShardedDaemonProcess(system).init(
      name = "PublishEventsProjection",
      numberOfInstances = sliceRanges.size,
      behaviorFactory = index =>
        ProjectionBehavior(projection(sliceRanges(index))),
      stopMessage = ProjectionBehavior.Stop
    )
  }
}
