package random

import scala.util.Random

object RandomRest extends Randomizer {
  override def randomElem[T](ls: List[T]): Option[T] = Random.shuffle(ls).headOption
}
