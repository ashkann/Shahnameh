package ir.ashkan.shahnameh

import cats.effect.std.Console
import cats.effect.{ExitCode, IO, IOApp}
import cats.instances.list._
import cats.syntax.traverse._
import org.apache.kafka.clients.consumer.{ConsumerConfig, KafkaConsumer}

import java.time.Duration
import java.util.Properties
import scala.jdk.CollectionConverters._

object HelloWorldKafkaConsumer extends IOApp {
  override def run(args: List[String]): IO[ExitCode] = {
    val config = Config.load

    val p = new Properties()
    p.setProperty(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, config.kafka.bootstrapServers)
    p.setProperty(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringDeserializer")
    p.setProperty(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringDeserializer")
    p.setProperty(ConsumerConfig.GROUP_ID_CONFIG, "consumer-group-1")
    p.setProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
    val consumer = new KafkaConsumer[String, String](p)

    val consume = for {
      records <- IO(consumer.poll(Duration.ofMillis(100)))
      _ <- records.asScala.toList.map { record =>
        Console[IO].println(s"Key: ${record.key} Value: ${record.value}") *>
          Console[IO].println(s"Partition: ${record.partition} Offset: ${record.offset}")
      }.sequence} yield ()

     IO(consumer.subscribe(List(config.kafka.topic).asJava)) *> consume.foreverM.as(ExitCode.Success)
  }
}