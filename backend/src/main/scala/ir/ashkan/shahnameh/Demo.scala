//package ir.ashkan.shahnameh
//
//import cats.effect.{ExitCode, IO}
//import cats.effect.std.Console
//
//object Demo extends cats.effect.IOApp {
//  import Shahnameh._
//
//  val jamshid = Character("Jamshid", 10, 2, 1)
//  val fereydoon = Character("Fereydoun", 10, 2, 1)
//  val kaveh = Character("Kaveh", 10, 2, 1)
//
//  val rostam = Character("Rostam", 10, 2, 1)
//  val sohrab = Character("Sohrab", 10, 2, 1)
//  val zal = Character("Zal", 10, 2, 1)
//
//  val module = new Module[Members.Small]
//
//  import module._
//
//  val pishdadian = Party("Pishdadian", Members.Small(jamshid, fereydoon, kaveh))
//  val kianian = Party("Kianian", Members.Small(rostam, sohrab, zal))
//
//  implicit val GameIO: Game[IO] = new Game[IO] {
//    override def action: IO[Action] = IO(Action.Attack(monocle.macros.GenLens[Encounter](_.party1.members.first)))
//  }
//
//  override def run(args: List[String]): IO[ExitCode] =
//    for {
//      _ <- Console[IO].println("Started ...")
//      e = new Encounter(pishdadian, kianian)
//      r <- Encounter.run(e)
//      _ <- Console[IO].println(r)
//    } yield ExitCode.Success
//}
