package ir.ashkan.shahnameh

import cats.Applicative
import cats.effect.Async
import cats.syntax.either._
import cats.syntax.flatMap._
import cats.syntax.functor._
import org.http4s
import org.http4s.headers.Location
import org.http4s.{Response, Uri}

import java.math.BigInteger
import java.security.SecureRandom
//import cats.syntax.monadError._
//import cats.syntax.applicativeError._
import io.circe.Decoder
import io.circe.generic.auto._
import io.circe.parser.decode
//import ir.ashkan.shahnameh.Config.GoogleOIDC
import org.http4s.dsl.{Http4sDsl => Dsl}
import pdi.jwt.{Jwt, JwtOptions}
import sttp.client3.asynchttpclient.cats.AsyncHttpClientCatsBackend
import sttp.client3.{SttpBackend, UriContext, asString, basicRequest}
import sttp.model.{Uri => SttpUri}

import scala.util.Try

//import java.math.BigInteger
//import java.security.spec.RSAPublicKeySpec
//import java.security.{KeyFactory, PublicKey}

class GoogleOIDC(tokenUri: SttpUri, authUri: Uri, config: Config.GoogleOIDC) {
  import GoogleOIDC._

  def authenticationUri: http4s.Uri = {
    val state = new BigInteger(130, new SecureRandom()).toString(32)
    val params = Map(
      "scope" -> "openid email profile",
      "response_type" -> "code",
      "client_id" -> config.clientId,
      "redirect_uri" -> config.redirectUri.toString,
      "state" -> state,
      "nonce" -> scala.util.Random.between(0, 1000).toString
    )
    org.http4s.Uri.unsafeFromString(authUri.toString()).withQueryParams(params)
  }

  def authenticate[F[_] : Async](code: String, state: String): F[User] = {
    for {
      backend <- AsyncHttpClientCatsBackend[F]()
      params = Map(
        "code" -> code,
        "client_id" -> config.clientId,
        "client_secret" -> config.clientSecret,
        "redirect_uri" -> config.redirectUri.toString,
        "grant_type" -> "authorization_code"
      )
      request = basicRequest.body(params).post(tokenUri)
      response <- request.response(asString.getRight).send(backend)
      IdToken(token) <- decode[IdToken](response.body).liftTo
      //      _ <- Console[F].println(token)
      Certs(keys) <- certs(backend)
      //      _ <- Console[F].println(keys)
      claim <- decodeJwtToken(token, keys).liftTo
      user <- decode[User](claim).liftTo
      userVerified <- Either.cond(user.email_verified, user, new RuntimeException("email not verified")).liftTo
    } yield  userVerified
  }

  //  private def key(n: String, e: String): PublicKey = {
  //    def decode(c: String): BigInteger = { println(c); BigInteger.valueOf(1) }
  //
  //    val spec = new RSAPublicKeySpec(decode(n), decode(e))
  //    KeyFactory.getInstance("RSA").generatePublic(spec)
  //  }

  private def decodeJwtToken(token: String, keys: List[Key]) = {
    for {
      (header, claim , _) <- Jwt.decodeAll(token, JwtOptions.DEFAULT.copy(signature = false)).toEither
      //      alg_ <- header.algorithm.map(_.name)
      //      kid_ <- header.keyId
      //      _ = println( (alg_, kid_) )
      //      key <- keys.collectFirst { case Key(alg, kid, n, e) if kid == kid_ && alg == alg_ => key(n, e) }
      //      json <- JwtCirce.decodeJson(token, key).toOption
      _ = keys
    } yield claim.content
  }

  private def certs[F[_] : Async](backend: SttpBackend[F, Any]): F[Certs] = {
    val req = basicRequest.get(uri"https://www.googleapis.com/oauth2/v3/certs").response(asString.getRight)
    for {
      response <- backend.send(req)
      cert <- decode[Certs](response.body).liftTo
    } yield cert
  }

  def loginRoute[F[_]: Applicative](dsl: Dsl[F]): F[Response[F]] = {
    import dsl._
    SeeOther.headers(Location(authenticationUri))
  }
}

object GoogleOIDC {
  private final case class Key(alg: String, kid: String, n: String, e: String)
  private final case class Certs(keys: List[Key])
  private final case class IdToken(id_token: String)
  private final case class DiscoveryDocument(token_endpoint: SttpUri, authorization_endpoint: Uri)

  final case class User(name: String, email: String, picture: String, email_verified: Boolean)

  implicit val uriDecoder: Decoder[SttpUri] = Decoder.decodeString.emapTry(str => Try(SttpUri.unsafeParse(str)))

  def fromConfig[F[_] : Async](config: Config.GoogleOIDC): F[GoogleOIDC] =
    AsyncHttpClientCatsBackend.resource().use { backend =>
      val asDiscoveryDoc = asString.getRight.map(decode[DiscoveryDocument]).getRight
      val getDiscoveryDoc = basicRequest.get(config.discoveryDocumentUri).response(asDiscoveryDoc)

      backend
        .send(getDiscoveryDoc)
        .map(_.body match {
          case DiscoveryDocument(tokenUri, authUri) => new GoogleOIDC(tokenUri, authUri, config)
        })
    }
}