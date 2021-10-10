package ir.ashkan.shahnameh.demo

import cats.effect.{ExitCode, IO, IOApp}
import ir.ashkan.shahnameh.Config

import java.math.BigInteger
import java.security.SecureRandom
import sttp.client3._

object GoogleOpenIdConnect extends IOApp {

  override def run(args: List[String]): IO[ExitCode] =
    for {
      config <- Config.load[IO]
      state = new BigInteger(130, new SecureRandom()).toString(32)
      params = Map(
        "scope" -> "openid email profile",
        "response_type" -> "code",
        "client_id" -> config.googleOpenidConnect.clientId,
        "redirect_uri" -> config.googleOpenidConnect.redirectUri,
        "state" -> state,
        "nonce" -> scala.util.Random.between(0, 1000).toString
      )
      _ <- cats.effect.std.Console[IO].println(uri"https://accounts.google.com/o/oauth2/v2/auth?$params")

    } yield ExitCode.Success
}
