package net.metacheck.website_parser

import io.github.cdimascio.essence.Essence
import io.github.cdimascio.essence.EssenceResult
import io.netty.handler.codec.http.HttpResponseStatus
import io.vertx.core.*
import io.vertx.core.http.HttpHeaders
import io.vertx.core.http.HttpServerResponse
import io.vertx.core.json.Json
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import io.vertx.kotlin.core.json.json
import io.vertx.kotlin.core.json.obj
import io.vertx.kotlin.coroutines.CoroutineVerticle
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.select.Elements
import sun.security.util.Cache
import java.util.concurrent.TimeUnit


object VertxVerticleMain {
  @JvmStatic
  fun main(args: Array<String>) {
    val options = VertxOptions()
//    options.maxEventLoopExecuteTime = 20000000000;

    val vertx: Vertx = Vertx.vertx(options)
    val options2 = DeploymentOptions().setWorker(true)
    println(MainVerticle::class.java.canonicalName);
    vertx.deployVerticle(MainVerticle::class.java.canonicalName, options2)
    vertx.deployVerticle(TestVericle::class.java.canonicalName, options2)
    //vertx.deployVerticle(MyVerticle.class);
  }
}

class TestVericle : AbstractVerticle() {
  override fun start(startFuture: Promise<Void>?) {
    val router: Router = Router.router(vertx)
    router.get("/test").handler {
      handleGet(it)
    }
    val server = vertx
      .createHttpServer()
      .requestHandler(router)

    server.listen(8887)
    println("<<< Server is running at port ${server.actualPort()}")
  }

  private fun handleGet(routingContext: RoutingContext) {
    println("  called")
    val name: String? = routingContext.queryParam("block").firstOrNull()
    vertx.executeBlocking({ promise: Promise<Any> ->
      if (name
        == "true"
      ) {
        println("  blocking")
        TimeUnit.SECONDS.sleep(15L)
      } else {
        println("not blocking")
      }

      promise.complete("result")

    }, false, fun(res: AsyncResult<Any>) {
      println("Completed sleep")
      routingContext.response()
        .setStatusCode(HttpResponseStatus.OK.code())
        .end(
          "done"
        )
    })
  }
}

class MainVerticle : CoroutineVerticle() {
  lateinit var executor: WorkerExecutor;
  lateinit var cache: Cache<String, ScrapeResult?>
  var counter = 0;

  override fun start(startFuture: Promise<Void>?) {
    val router: Router = Router.router(vertx)

    executor = vertx.createSharedWorkerExecutor("my-worker-pool")
    cache = Cache.newHardMemoryCache(0, 3600)
    router.route().handler(globalHandler)

    router.get("/scrape").handler {

      handleGet(it)

    }
    val server = vertx
      .createHttpServer()
      .requestHandler(router)

    server.listen(8888)
    println("<<< Server is running at port ${server.actualPort()}")
  }

  private fun handleGet(routingContext: RoutingContext) {
    counter++;
//    println("  called $counter times")

    when (val name: String? = routingContext.queryParam("url").firstOrNull()) {
      null -> {
        routingContext.response()
          .setStatusCode(HttpResponseStatus.BAD_REQUEST.code())
          .end(
            json {
              obj(
                "error_message" to "You must provide 'url' query string parameter"
              ).encode()
            }
          )
      }
      else -> {
        executor.executeBlocking({ promise: Promise<ScrapeResult?> ->

          val fresh: String? = routingContext.queryParam("fresh").firstOrNull()
          if (fresh == "true") {
            cache.remove(name);
          }
          val cacheResult = cache.get(name);
          val result: ScrapeResult? = cacheResult ?: scrapeUrl(name)

          if (cacheResult == null) {
            cache.put(name, result)
          } else {
            println("got from cache")
          }

          promise.complete(result)

        }, false, fun(result: AsyncResult<ScrapeResult?>) {

          val obj = GenericResponse(
            message = "Parsed url $name",
            data = JsonObject(Json.encode(result.result())).map
          )

          routingContext.response()
            .setStatusCode(HttpResponseStatus.OK.code())
            .end(
              obj.encode()
            )
        })
      }
    }

  }


  private val globalHandler: Handler<RoutingContext> = Handler { routingContext: RoutingContext ->
    val response: HttpServerResponse = routingContext.response()
    response.putHeader(HttpHeaders.CONTENT_TYPE.toString(), "application/json")
    routingContext.next()
  }


  private fun scrapeUrl(url: String): ScrapeResult? {

    try {
      val document: Document = Jsoup.connect(url).get()
      println("got doc for $url")

      val essence: EssenceResult = Essence.extract(document.html())

      val dangerousElements = arrayListOf<String>(
        "dropdown",
        "header",
        "sidebar",
        "navigation",
        "nav",
        "footer",
        "menu",
      )

      for (el in dangerousElements) {
        val hTags: Elements? = document.select("h2, h3, h4, h5, h6")


        hTags?.forEach {
          if (it.className().contains(el)) {
            it.remove()
          }
        }


      }


      val hTags: Elements? =
        document.select("[class~=${essence.topNode?.className().toString()}]").select("h2, h3, h4, h5, h6")

      val mappedTags = ArrayList<ScrapedHeading>();
      if (hTags?.isNotEmpty() == true) {
        for (tag in hTags) {
          mappedTags.add(ScrapedHeading(value = tag.tagName(), name = tag.text()))
        }
      }
      val hasDuplicates: Boolean = mappedTags.size != mappedTags.toSet().size;

      return ScrapeResult(
        hasDuplicates = hasDuplicates,
        title = document.title(),
        description = essence.description,
        heading = essence.softTitle,
        featuredImage = essence.image,
        headings = ArrayList(mappedTags.toSet()),
        wordCount = essence.text.trim().split("\\s+".toRegex()).size
        //      document.select("body").first()!!.getElementsByClass("blog-post-body").select("p")
        //        .map {
        //          it.text().toString().trim().split("\\s+".toRegex()).size
        //        }.toList().sum()
      );
    } catch (e: Exception) {
      return null;
    }
  }
}


data class GenericResponse(
  val message: String,
  val data: Map<String, Any>,
)

fun GenericResponse.encode(): String {
  return (Json.encode(this));
}
