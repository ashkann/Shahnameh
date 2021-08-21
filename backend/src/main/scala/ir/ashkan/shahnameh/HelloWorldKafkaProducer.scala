package ir.ashkan.shahnameh

import cats.Monad
import cats.effect.{ExitCode, IO, IOApp}
import org.apache.kafka.clients.producer.{KafkaProducer, ProducerConfig, ProducerRecord}
import scala.concurrent.duration.DurationInt

import java.util.Properties

object HelloWorldKafkaProducer extends IOApp {
  override def run(args: List[String]): IO[ExitCode] = {
    val config = Config.load

    val p = new Properties()
    p.setProperty(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, config.kafka.bootstrapServers)
    p.setProperty(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer")
    p.setProperty(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer")
    val producer = new KafkaProducer[String, String](p)

    for {
      p <- Monad[IO].iterateForeverM(0) { a =>
        val r = new ProducerRecord[String, String](config.kafka.topic, s"Message #$a")
        IO {
          producer.send(r)
          producer.flush()
        } *> IO.sleep(1.second) as (a + 1)
      }.start
      _ <- cats.effect.std.Console[IO].println("Press Enter to finish ...")
      _ <- cats.effect.std.Console[IO].readLine
      _ <- p.cancel
      _ <- IO(producer.close())
    } yield ExitCode.Success
  }
}
