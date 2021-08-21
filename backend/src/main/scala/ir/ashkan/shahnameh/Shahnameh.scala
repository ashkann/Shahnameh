//package ir.ashkan.shahnameh
//
//import cats.Monad
//import cats.effect.std.Console
//import cats.syntax.apply._
//import cats.syntax.flatMap._
//import cats.syntax.foldable._
//import cats.syntax.functor._
//import monocle.Lens
//
//object Shahnameh {
//  type HitPoints = Int
//  type Attack = Int
//  type Initiative = Int
//
//  final case class Character(
//    name: String,
//    hp: HitPoints,
//    attack: Attack,
//    initiative: Initiative
//  ) {
//
//    def takeDamage(amount: HitPoints): Character = copy(hp = hp - amount)
//    def heal(amount: HitPoints): Character = copy(hp = hp + amount)
//    def isDead: Boolean = hp <= 0
//  }
//
//
//  sealed trait Members
//  object Members {
//    implicit val smallLens: List[Lens[Small, Character]] =
//      List(
//        monocle.macros.GenLens[Small](_.first),
//        monocle.macros.GenLens[Small](_.second),
//        monocle.macros.GenLens[Small](_.third)
//      )
//    implicit val mediumLens: List[Lens[Medium, Character]] =
//      monocle.macros.GenLens[Medium](_.fourth) :: smallLens.map(monocle.macros.GenLens[Medium](_.small).andThen)
//    implicit val largeLens: List[Lens[Large, Character]] =
//      monocle.macros.GenLens[Large](_.fifth) :: mediumLens.map(monocle.macros.GenLens[Large](_.medium).andThen)
//
//    final case class Small(first: Character, second: Character, third: Character) extends Members
//    final case class Medium(small: Small, fourth: Character) extends Members
//    final case class Large(medium: Medium, fifth: Character) extends Members
//  }
//
//  class Module[M <: Members](implicit toLens: List[Lens[M, Character]]) {
//    final case class Party(name: String, members: M)
//
//    case class Encounter(party1: Party, party2: Party) {
//      private def isOver: Boolean = {
//        def isDead(p: Party) = toLens.map(_.get(p.members)).forall(_.isDead)
//
//        isDead(party1) || isDead(party2)
//      }
//    }
//
//    type Ch = Lens[Encounter, Character]
//
//    sealed trait Action
//    object Action {
//      final case class Attack(target: Ch) extends Action
//      final case object Heal extends Action
//    }
//
//    sealed trait Effect
//    object Effect {
//      final case class TakeDamage(amount: HitPoints, target: Ch) extends Effect
//      final case class Heal(amount: HitPoints, target: Ch) extends Effect
//    }
//
//    trait Game[F[_]] {
//      def action: F[Action]
//    }
//
//    object Game {
//      def apply[F[_] : Game](implicit ev: Game[F]): Game[F] = ev
//    }
//
//
//    object Encounter {
//      def characters(enc: Encounter): List[Ch] = {
//        val party1 = monocle.macros.GenLens[Encounter](_.party1.members)
//        val party2 = monocle.macros.GenLens[Encounter](_.party2.members)
//
//        def ch(p: Lens[Encounter, M]): List[Ch] = toLens.map(p.andThen)
//        (ch(party1) ++ ch(party2)).map(ch => (ch, ch.get(enc).initiative)).sortBy(_._2).map(_._1)
//      }
//
//      def run[F[_] : Game](init: Encounter)(implicit L: Console[F], M: Monad[F]): F[Encounter] =
//        M.iterateUntilM(init) {
//          L.println("Round started ...") *> round(_) <* L.println("Round finished.")
//        }(_.isOver)
//
//      def round[F[_] : Monad : Game](init: Encounter)(implicit L: Console[F]): F[Encounter] =
//        characters(init).foldM(init) { (enc, character) =>
//          val (name, attack) = character.asGetter.map(ch => (ch.name, ch.attack)).get(enc)
//          for {
//            - <- L.println(s"Turn started for $name ...")
//            action <- Game[F].action
//            eff: Effect = action match {
//              case Action.Attack(target) => Effect.TakeDamage(attack, target)
//              case Action.Heal => Effect.Heal(5, character)
//            }
//            update: (Encounter => Encounter) = eff match {
//              case Effect.TakeDamage(amount, target) => target.modify(_.takeDamage(amount))
//              case Effect.Heal(amount, target) => target.modify(_.heal(amount))
//            }
//            enc2 = update(enc)
//            _ <- L.println(s"Turn ended for $name")
//            _ <- L.println(enc2)
//            _ <- L.readLine
//          } yield enc2
//        }
//    }
//
//    sealed trait Message
//    object Message {
//      case class Encounter(yourParty: Party, otherParty: Party) extends Message
//    }
//  }
//}