package utils
import org.slf4j
import org.slf4j.LoggerFactory

/**
  * Extend the interface for using logging
  */
trait Logger {
  val log: slf4j.Logger = LoggerFactory.getLogger(this.getClass.getName)
}