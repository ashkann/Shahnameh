package ir.ashkan.shahnameh.demo

import cats.effect.std.Console
import cats.effect.{ExitCode, IO, IOApp}
import fs2.kafka._
import ir.ashkan.shahnameh.Config

object ConsoleConsumer extends IOApp {
  override def run(args: List[String]): IO[ExitCode] =
    for {
      conf <- Config.load[IO]
      settings =
        ConsumerSettings[IO, Unit, String]
          .withAutoOffsetReset(AutoOffsetReset.Earliest)
          .withBootstrapServers(conf.kafka.bootstrapServers)
          .withGroupId("console-consumer-group")

      _ <- Console[IO].println("Started consuming ...")

      _ <- KafkaConsumer.stream(settings)
        .evalTap(_.subscribeTo(args.head))
        .flatMap(_.stream)
        .evalMap(r => Console[IO].println(r.record.value))
        .compile.drain
    } yield ExitCode.Success
}