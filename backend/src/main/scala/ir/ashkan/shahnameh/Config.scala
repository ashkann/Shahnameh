package ir.ashkan.shahnameh

import cats.effect.kernel.Sync
import pureconfig.{ConfigReader, ConfigSource}
import pureconfig.generic.auto._
import pureconfig.module.catseffect.syntax._
import sttp.model.Uri
import scala.util.Try


object Config {
  case class Kafka(bootstrapServers: String, sendTopic: String, receiveTopic: String)

  case class Http(webSocketPort: Int)

  case class GoogleOIDC(clientId: String, clientSecret: String, redirectUri: Uri, discoveryDocumentUri: Uri)

  case class Database(url: String, username: String, password: String)

  case class Application(
    database: Database,
    kafka: Kafka,
    http: Http,
    googleOpenidConnect: GoogleOIDC
  )

  implicit val uriReader: ConfigReader[Uri] = ConfigReader.fromStringTry(str => Try(Uri.unsafeParse(str)))
  def load[F[_]: Sync]: F[Application] = ConfigSource.default.loadF[F, Application]()
}
