package ir.ashkan.shahnameh.demo

import cats.effect.{ExitCode, IO, IOApp}
import ir.ashkan.shahnameh.Config
import cats.effect.std.Console
import scala.concurrent.duration.DurationInt
import fs2.kafka._
import fs2.Stream

object HelloWorldKafkaProducer extends IOApp {
  override def run(args: List[String]): IO[ExitCode] =
    for {
      config <- Config.load[IO]

      records = Stream.iterate(0)(_ + 1)
        .map(n => ProducerRecord(config.kafka.topic, (), s"Message #$n"))
        .map(r => ProducerRecords.one(r))
        .zipLeft(Stream.awakeEvery[IO](1.second))

      settings = ProducerSettings[IO, Unit, String].withBootstrapServers(config.kafka.bootstrapServers)
      producer = KafkaProducer.stream(settings)

      fiber <- producer.flatMap(p => records.evalMap(x => p.produce(x))).compile.drain.start

      _ <- Console[IO].println("Press Enter to finish ...")
      _ <- Console[IO].readLine
      _ <- fiber.cancel
    } yield ExitCode.Success
}