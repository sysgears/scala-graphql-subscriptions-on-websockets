package guice.module

import com.google.inject.{AbstractModule, Provides, Singleton}
import models.PostEvent
import monix.execution.Scheduler
import services.{PubSubService, PubSubServiceImpl}

/**
  * Contains binds of services to their implementations in order to use it in DI.
  */
class PubSubModule extends AbstractModule {

  /** @inheritdoc */
  override def configure(): Unit = {
    bind(classOf[Scheduler]).toInstance(Scheduler.Implicits.global)
  }

  /**
    * Bind of 'PubSubService' to an implementation
    *
    * @param scheduler is an `scala.concurrent.ExecutionContext` that additionally can
    *                  schedule the execution of units of work to run with a delay or periodically
    * @return an instance of PubSubService implementation
    */
  @Provides
  @Singleton
  def pubSubService(implicit scheduler: Scheduler): PubSubService[PostEvent] = {
    new PubSubServiceImpl[PostEvent]
  }
}