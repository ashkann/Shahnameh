package ir.ashkan.shahnameh

import pureconfig.ConfigSource
import pureconfig.generic.auto._

object Config {
  case class Kafka(bootstrapServers: String, topic: String)

  case class Application(kafka: Kafka)

  def load: Application = ConfigSource.default.loadOrThrow[Config.Application]
}
