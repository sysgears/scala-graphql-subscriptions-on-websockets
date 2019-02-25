package modules

import com.google.inject.{AbstractModule, Scopes}
import repositories.{PostRepository, PostRepositoryImpl}

/**
  * Contains bind of post repository to its implementation in order to use it in DI.
  */
class PostModule extends AbstractModule {

  /** @inheritdoc */
  override def configure(): Unit = {
    bind(classOf[PostRepository]).to(classOf[PostRepositoryImpl]).in(Scopes.SINGLETON)
  }
}