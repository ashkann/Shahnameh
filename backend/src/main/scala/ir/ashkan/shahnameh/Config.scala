package ir.ashkan.shahnameh

import cats.effect.kernel.Sync
import pureconfig.ConfigSource
import pureconfig.generic.auto._
import pureconfig.module.catseffect.syntax._


object Config {
  case class Kafka(bootstrapServers: String, topic: String)

  case class Application(kafka: Kafka)

  def load[F[_]: Sync]: F[Application] = ConfigSource.default.loadF()
}
