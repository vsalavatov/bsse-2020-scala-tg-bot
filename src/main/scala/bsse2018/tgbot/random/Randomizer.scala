package bsse2018.tgbot.random

trait Randomizer {
  def randomElem[T](list: List[T]) : Option[T]
}
