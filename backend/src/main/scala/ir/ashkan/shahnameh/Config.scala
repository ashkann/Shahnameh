package ir.ashkan.shahnameh

import cats.effect.kernel.Sync
import pureconfig.ConfigSource
import pureconfig.generic.auto._
import pureconfig.module.catseffect.syntax._


object Config {
  case class Kafka(bootstrapServers: String, sendTopic: String, receiveTopic: String)

  case class Http(webSocketPort: Int)

  case class Application(kafka: Kafka, http: Http)

  def load[F[_]: Sync]: F[Application] = ConfigSource.default.loadF()
}
