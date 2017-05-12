package jto.validation
package v3.tagless
package playjson

import play.api.libs.json.{JsValue, JsObject, JsNumber}
import cats.Semigroup

import jto.validation.playjson.Writes
import cats.arrow.Compose

import shapeless.tag, tag.@@
import shapeless.HNil

object WriteTypeAlias {
  // I should be able to use flip[Write]#λ instead of creating this ugly
  // type alias but somehow, not naming the type breaks inference...
  type FWrite[A, B] = Write[B, A]
}

trait WritesGrammar extends JsonGrammar[WriteTypeAlias.FWrite] with WriteConstraints {
  self =>

  type Out = JsObject
  type P = WritesGrammar

  def mapPath(f: Path => Path): P =
    new WritesGrammar {
      override def at[A](p: Path)(k: => Write[A,Option[_ >: JsObject <: JsValue]]): Write[A,JsObject] =
        self.at(f(p))(k)
    }

  def knil: Write[HNil, Out] = Write{ _ => JsObject(Nil) }

  def at[A](p: Path)(k: => Write[A, Option[_ >: JsObject <: JsValue]]): Write[A, JsObject] =
    Write { t =>
      val js = k.writes(t)
      val w2 = Writes.optionW(Write.zero[JsValue])(Writes.writeJson _)(p)
      w2.writes(js)
    }

  def opt[A](implicit K: Write[A, _ >: JsObject <: JsValue]): Write[Option[A], Option[_ >: JsObject <: JsValue]] =
    Write {
      _.map(K.writes)
    }

  def req[A](implicit K: Write[A, _ >: JsObject <: JsValue]): Write[A, Option[_ >: JsObject <: JsValue]] =
    Write { a =>
      Option(K.writes(a))
    }

  implicit def int = Writes.intW
  implicit def string = Writes.string
  implicit def bigDecimal = Writes.bigDecimalW
  implicit def boolean = Writes.booleanW
  implicit def double = Writes.doubleW
  implicit def float = Writes.floatW
  implicit def jBigDecimal = Write { (j: java.math.BigDecimal) => JsNumber(j) }
  implicit def long = Writes.longW
  implicit def short = Writes.shortW
  implicit def seq[A](implicit k: Write[A, _ >: Out <: JsValue]) = Writes.seqToJsArray(k)
  implicit def list[A](implicit k: Write[A, _ >: Out <: JsValue]) = Writes.seqToJsArray(k).contramap(_.toSeq)
  implicit def array[A: scala.reflect.ClassTag](implicit k: Write[A, _ >: Out <: JsValue]) = Writes.seqToJsArray(k).contramap(_.toSeq)
  implicit def map[A](implicit k: Write[A, _ >: Out <: JsValue]) = Writes.mapW(k)
  implicit def traversable[A](implicit k: Write[A, _ >: Out <: JsValue]) = Writes.seqToJsArray(k).contramap(_.toSeq)

  implicit def jsNull = Write.zero
  implicit def jsObject = Write.zero
  implicit def jsString = Write.zero
  implicit def jsNumber = Write.zero
  implicit def jsBoolean = Write.zero

  def toGoal[Repr, A]: Write[Repr, Out] => Write[Goal[Repr, A], Out] =
    _.contramap{ _.value }

  implicit def composeTC =
    new Compose[types.flip[Write]#λ] {
      def compose[A, B, C0](f: Write[C0, B], g: Write[B, A]): Write[C0, A] =
        f andThen g
    }

  import shapeless.{::, HList}

  implicit def mergeTC =
    new Merge[Write[?, Out]] {
      def merge[A, B <: HList](fa: Write[A, Out], fb: Write[B, Out]): Write[A :: B, Out] =
        Write { case a :: b =>
          val wa = fa.writes(a)
          val wb = fb.writes(b)
          Writes.jsonMonoid.combine(fa.writes(a), fb.writes(b))
        }
    }

  implicit def semigroupTC[I, O]: Semigroup[Write[O, I] @@ Root] =
    new Semigroup[Write[O, I] @@ Root] {
      def combine(x: Write[O, I] @@ Root, y: Write[O, I] @@ Root): Write[O, I] @@ Root = x
    }

}

object WritesGrammar extends WritesGrammar
