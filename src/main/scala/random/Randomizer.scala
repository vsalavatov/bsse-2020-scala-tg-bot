package random

trait Randomizer {
  def randomElem[T](list: List[T]) : Option[T]
}
