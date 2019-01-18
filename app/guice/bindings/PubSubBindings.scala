package guice.bindings

import com.google.inject.{AbstractModule, Provides, Singleton}
import models.Post
import monix.execution.Scheduler
import services.{Event, PubSubService, PubSubServiceImpl}

class PubSubBindings extends AbstractModule {
  override def configure(): Unit = ()

  @Provides
  @Singleton
  def pubSubService(implicit schedule: Scheduler): PubSubService[Event[Post]] = {
    new PubSubServiceImpl[Event[Post]]
  }
}