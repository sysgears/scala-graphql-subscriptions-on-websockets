package modules

import com.google.inject.{AbstractModule, Scopes}
import repositories.{PostRepository, PostRepositoryImpl}

class PostModule extends AbstractModule {
  override def configure(): Unit = {
    bind(classOf[PostRepository]).to(classOf[PostRepositoryImpl]).in(Scopes.SINGLETON)
  }
}