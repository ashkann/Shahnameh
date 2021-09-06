package ir.ashkan.shahnameh

import cats.effect.IO
import fs2.{Pipe, Stream}
import fs2.kafka.{AutoOffsetReset, ConsumerSettings, Deserializer, KafkaConsumer, KafkaProducer, ProducerRecord, ProducerRecords, ProducerSettings, Serializer}
import ir.ashkan.shahnameh.Connection.{Incoming, Outgoing}
import mouse.any._

object Kafka {
  def send(config: Config.Kafka): Stream[IO, Outgoing] = {
    import io.circe.generic.auto._
    import io.circe.syntax._

    implicit val des: Deserializer[IO, Outgoing] =
      Deserializer.lift[IO, Outgoing](_.asJson.as[Outgoing].fold(IO.raiseError, IO.pure))

    val settings = ConsumerSettings[IO, Unit, Outgoing]
      .withAutoOffsetReset(AutoOffsetReset.Latest)
      .withBootstrapServers(config.bootstrapServers)
      .withGroupId("outgoing-consumer-group")

    KafkaConsumer.stream(settings)
      .evalTap(_.subscribeTo(config.receiveTopic))
      .flatMap(_.stream)
      .map(_.record.value)
  }

  def receive(config: Config.Kafka): Pipe[IO, Incoming, Unit] = { incoming =>
    import io.circe.generic.auto.exportEncoder
    import io.circe.syntax.EncoderOps

    implicit val ser: Serializer[IO, Incoming] =
      Serializer.lift[IO, Incoming](_.asJson.noSpaces.getBytes |> IO.pure)

    val records = incoming.map(msg => ProducerRecords.one(ProducerRecord(config.receiveTopic, (), msg)))
    val settings = ProducerSettings[IO, Unit, Incoming].withBootstrapServers(config.bootstrapServers)
    KafkaProducer.stream(settings).flatMap(p => records.evalMap(p.produce(_).void))
  }
}