@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE")

package net.metacheck.website_parser

import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.firestore.Firestore
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.cloud.FirestoreClient
import io.github.cdimascio.essence.Essence
import io.github.cdimascio.essence.EssenceResult
import io.netty.handler.codec.http.HttpResponseStatus
import io.vertx.core.*
import io.vertx.core.http.HttpHeaders
import io.vertx.core.http.HttpServerResponse
import io.vertx.core.json.Json
import io.vertx.core.json.JsonArray
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.handler.BodyHandler
import io.vertx.kotlin.core.json.get
import io.vertx.kotlin.coroutines.CoroutineVerticle
import io.vertx.kotlin.coroutines.await
import kotlinx.coroutines.launch
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.select.Elements
import sun.security.util.Cache
import java.io.FileInputStream
import java.util.*
import java.util.concurrent.TimeUnit


object VertxVerticleMain {
  @JvmStatic
  fun main(args: Array<String>) {
    val options = VertxOptions()
//    options.maxEventLoopExecuteTime = 20000000000;

    val vertx: Vertx = Vertx.vertx(options)
    val options2 = DeploymentOptions().setWorker(true)

    vertx.deployVerticle(ScrapeVerticle::class.java.canonicalName, options2)
//    vertx.deployVerticle(FirebaseVerticle::class.java.canonicalName, options2)
  }
}


class ScrapeVerticle : CoroutineVerticle() {
  lateinit var executor: WorkerExecutor;
  lateinit var cache: Cache<String, ScrapeResult?>
  lateinit var firestore: Firestore;

  var counter = 0;

  override fun start(startFuture: Promise<Void>?) {
    val router: Router = Router.router(vertx)

    executor = vertx.createSharedWorkerExecutor("my-worker-pool")

    cache = Cache.newHardMemoryCache(11110, 3600)
    router.route().handler(BodyHandler.create())


    val serviceAccount =
      FileInputStream("resources/metacheckservice.json")
    val options = FirebaseOptions.builder()
      .setCredentials(GoogleCredentials.fromStream(serviceAccount))
      .build()

    val app = FirebaseApp.initializeApp(options)
    router.route().handler(BodyHandler.create())

    firestore = FirestoreClient.getFirestore(app)

    router.post("/startSession").consumes("application/json").handler {

      handleStartSession(it)
    }
    router.post("/saveResults").consumes("application/json").handler {

      handleSaveResults(it)
    }


    router.post("/scrape").consumes("application/json").handler {

      launch { handleScrape(it) }
//      vertx.setTimer(1000) { a -> if(!it.response().ended())it.end() }
    }
    router.get("/test").handler {
      it.response()
        .setStatusCode(HttpResponseStatus.BAD_REQUEST.code())
        .end(
          GenericResponse(
            message = "Response ",
            data = mapOf(),
            error = mapOf("message" to "response")
          ).encode()
        )
    }

    val server = vertx
      .createHttpServer()
      .requestHandler(router)

    server.listen(System.getenv("PORT")?.toIntOrNull() ?: 8888)
    println("<<< Server is running at http://localhost:${server.actualPort()}")
  }

  private fun handleSaveResults(routingContext: RoutingContext) {


    val urls: MutableMap<String, Any> = routingContext.bodyAsJson.map
    val insertMap = mutableMapOf<String, Any?>(
      "id" to urls["id_instance"]!!,
      "user_id" to "leo",
      "scrape_results" to urls["scrape_results"]

    )


    firestore.collection("results").document(urls["id_instance"]!! as String).set(insertMap)

    val obj = GenericResponse(
      message = "Saved results",
    )
    routingContext.response().putHeader("content-type", "application/json")
      .setStatusCode(HttpResponseStatus.OK.code())
      .end(
        obj.encode()
      )

  }

  private fun handleStartSession(routingContext: RoutingContext) {


    val urls: MutableMap<String, Any> = routingContext.bodyAsJson.map
    val insertMap = mutableMapOf<String, Any?>(
      "id" to urls["id_instance"]!!,
      "user_id" to "leo",
      "scrape_results" to urls["scrape_results"]

    )


    firestore.collection("results").document(urls["id_instance"]!! as String).set(insertMap)

    val obj = GenericResponse(
      message = "Saved results",
    )
    routingContext.response().putHeader("content-type", "application/json")
      .setStatusCode(HttpResponseStatus.OK.code())
      .end(
        obj.encode()
      )


  }

  private suspend fun handleScrape(routingContext: RoutingContext) {
    counter++;
    val startTime = System.currentTimeMillis()
    val urls: List<String> = routingContext.bodyAsJson.get<List<String>>("urls").toList()
    var fresh: Boolean = routingContext.bodyAsJson.get<Any>("fresh")?.toString() == "true"
    val futures: MutableList<Future<ScrapeResult?>> = mutableListOf()
    fresh = true;

    var y = mutableListOf<ScrapeResult?>()


    for (name in urls) {


      val result: Future<ScrapeResult?> = executor.executeBlocking(
        { promise: Promise<ScrapeResult?> ->
          try {
            if (fresh) {
              cache.remove(name)
            }
            val cacheResult = cache.get(name)

            val result: ScrapeResult? = cacheResult ?: scrapeUrl(name)


            if (cacheResult == null) {
              cache.put(name, result)
            }



            promise.complete(result)

          } catch (e: Exception) {
            promise.fail(e)

          }


        },
        false,
      )
      futures.add(result)

    }
    val x: CompositeFuture = CompositeFuture.join(futures.toList())


    try {
      y = x.await().list()
    } catch (e: Exception) {
      y = x.list()
    }

    y = y.filterNotNull().toMutableList()


    if (routingContext.response().ended()) return
    if (y.isEmpty()) {
      routingContext.response()
        .setStatusCode(HttpResponseStatus.BAD_REQUEST.code())
        .end(
          GenericResponse(
            message = "Error ",
            data = mapOf(),
            error = mapOf("message" to "error")
          ).encode()
        )
      return
    }

    val obj = GenericResponse(
      message = "Parsed urls ${y.map { it?.url + ", " }}",
      data = mapOf<String, Any>("results" to JsonArray(Json.encode(y)))
    )



    routingContext.response().putHeader("content-type", "application/json")
      .setStatusCode(HttpResponseStatus.OK.code())
      .end(
        obj.encode()
      )

    val endTime = System.currentTimeMillis()
    println("req took ${endTime - startTime}ms")
  }


  private val globalHandler: Handler<RoutingContext> = Handler { routingContext: RoutingContext ->
    val response: HttpServerResponse = routingContext.response()
    response.putHeader(HttpHeaders.CONTENT_TYPE.toString(), "application/json")
    routingContext.next()
  }


  private fun scrapeUrl(url: String): ScrapeResult? {
    try {

      val document: Document = Jsoup.connect(url).get()

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
          mappedTags.add(ScrapedHeading(value = tag.text(), name = tag.tagName()))
        }
      }
      val unique: List<ScrapedHeading> =
        mappedTags.toSet().map { it.copy(occurences = mappedTags.count { other -> other == it }) }.sortedBy { it.name }
      val hasDuplicates: Boolean = mappedTags.size != unique.size;

      var headerString: String = ""
      headerString = mappedTags.map { it.value }.joinToString(separator = " ") { it -> it }


      val obj = ScrapeResult(
        id = UUID.randomUUID().toString(),
        url = url,
        text = essence.text.replace("\\R+".toRegex(), " "),
        hasDuplicates = hasDuplicates,
        title = document.title(),
        description = essence.description,
        heading = essence.softTitle,
        featuredImage = essence.image,
        headings = ArrayList(unique),
        wordCount = (essence.text.trim()
          + " " + headerString.trim()).replace("\\R+", "")
          .split("\\s+".toRegex()).size
        //      document.select("body").first()!!.getElementsByClass("blog-post-body").select("p")
        //        .map {
        //          it.text().toString().trim().split("\\s+".toRegex()).size
        //        }.toList().sum()
      );

      return obj
    } catch (e: Exception) {

      return null;
    }
  }
}


class TestVericle : CoroutineVerticle() {
  override fun start(startFuture: Promise<Void>?) {
    val router: Router = Router.router(vertx)
    router.get("/test").handler {
      launch {
        handleGet(it)
      }
    }
    val server = vertx
      .createHttpServer()
      .requestHandler(router)

    server.listen(8887)
    println("<<< Server is running at port ${server.actualPort()}")
  }

  val futureList = ArrayList<Future<Int>>();

  private suspend fun handleGet(routingContext: RoutingContext) {
    val name: String? = routingContext.queryParam("block").firstOrNull()

    val futureResponse: String = vertx.executeBlocking(
      { promise: Promise<String> ->
        if (name
          == "true"
        ) {
          println("  blocking")
          TimeUnit.SECONDS.sleep(10L)
        } else {
          println("not blocking")
        }

        promise.complete("result")

      },
      false,
    ).await()

    println(futureResponse)
    routingContext.response()
      .setStatusCode(HttpResponseStatus.OK.code())
      .end(
        "done"
      )
  }
}


data class GenericResponse(
  val message: String,
  val data: Map<String, Any>? = null,
  val error: Map<String, Any>? = null,
)

fun GenericResponse.encode(): String {
  return (Json.encode(this));
}
