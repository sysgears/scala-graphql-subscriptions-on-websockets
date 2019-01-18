package guice.bindings

import com.google.inject.AbstractModule
import monix.execution.Scheduler

class PostBindings extends AbstractModule {
  override def configure(): Unit = {
    bind(classOf[Scheduler]).toInstance(Scheduler.Implicits.global)
  }
}