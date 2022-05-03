package net.metacheck.website_parser

import com.google.api.core.ApiFuture
import com.google.api.core.ApiFutureCallback
import com.google.api.core.ApiFutures
import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.firestore.Firestore
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.cloud.FirestoreClient
import io.netty.handler.codec.http.HttpResponseStatus
import io.vertx.core.Promise
import io.vertx.core.WorkerExecutor
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.handler.BodyHandler
import io.vertx.kotlin.coroutines.CoroutineVerticle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.launch
import java.io.FileInputStream
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

class FirebaseVerticle : CoroutineVerticle() {
  lateinit var executor: WorkerExecutor;
  lateinit var firestore: Firestore;

  override fun start(startFuture: Promise<Void>?) {
    val router: Router = Router.router(vertx)

    executor = vertx.createSharedWorkerExecutor("FirebaseVerticle-pool")

    val serviceAccount =
      FileInputStream("resources/metacheckservice.json")
    val options = FirebaseOptions.builder()
      .setCredentials(GoogleCredentials.fromStream(serviceAccount))
      .build()

    val app = FirebaseApp.initializeApp(options)
    router.route().handler(BodyHandler.create())

    firestore = FirestoreClient.getFirestore(app)

    router.post("/startSession").consumes("application/json").handler {

      launch { handleStartSession(it) }
    }
    router.post("/saveResults").consumes("application/json").handler {

      launch { handleSaveResults(it) }
    }
    val server = vertx
      .createHttpServer()
      .requestHandler(router)

    server.listen(System.getenv("PORT")?.toIntOrNull() ?: 8888)
    println("<<< Server is running at http://localhost:${server.actualPort()}")
  }


  private suspend fun handleSaveResults(routingContext: RoutingContext) {


    val urls: MutableMap<String, Any> = routingContext.bodyAsJson.map
    val insertMap = mutableMapOf<String, Any?>(
      "id" to urls["id_instance"]!!,
      "user_id" to "leo",
      "scrape_results" to null

    )

    val future = firestore.collection("results").document(urls["id_instance"]!! as String).set(insertMap)

    future.await {


      val obj = GenericResponse(
        message = "Saved results",
      )
      routingContext.response().putHeader("content-type", "application/json")
        .setStatusCode(HttpResponseStatus.OK.code())
        .end(
          obj.encode()
        )
    }

  }

  private suspend fun handleStartSession(routingContext: RoutingContext) {


    val urls: MutableMap<String, Any> = routingContext.bodyAsJson.map
    val insertMap = mutableMapOf<String, Any>(
      "id" to urls["id_instance"]!!, "user_id" to "leo",
      "scrape_results" to urls["scrape_results"]!!

    )

    val future = firestore.collection("results").document(urls["id_instance"]!! as String).set(insertMap)
    print("")
    future.await {
      val obj = GenericResponse(
        message = "Saved results",
      )
      routingContext.response().putHeader("content-type", "application/json")
        .setStatusCode(HttpResponseStatus.OK.code())
        .end(
          obj.encode()
        )
    }


  }
}

suspend fun <F : Any?, R : Any?> ApiFuture<F>.await(
  successHandler: (F) -> R,
): R {
  return suspendCoroutine { cont ->
    ApiFutures.addCallback(this, object : ApiFutureCallback<F> {
      override fun onFailure(t: Throwable?) {
        cont.resumeWithException(t ?: IOException("Unknown error"))
      }

      override fun onSuccess(result: F) {
        cont.resume(successHandler(result))
      }
    }, Dispatchers.IO.asExecutor())
  }
}
