import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dto.Author
import dto.Comment
import dto.Post
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.logging.HttpLoggingInterceptor
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine


private val gson = Gson()
private val BASE_URL = "http://127.0.0.1:9999"
private val client = OkHttpClient.Builder()
//    .addInterceptor(HttpLoggingInterceptor(::println).apply {
//        level = HttpLoggingInterceptor.Level.BODY
//    })
    .connectTimeout(30, TimeUnit.SECONDS)
    .build()


fun main() {
    with(CoroutineScope(EmptyCoroutineContext)) {
        launch {
           getPosts(client)
               .map { post ->
                   async {
                       val author = getAuthor(client, post.authorId)
                       println("${author.name} (authorId = ${author.id})")
                   }.await()
                   println(post)
                   async { getComments(client, post.id)
                       .map { comment  ->
                           async {
                               val author = getAuthor(client, comment.authorId)
                               println("\t${author.name} (authorId = ${author.id})")
                           }.await()
                           println("\t${comment}")
                       }
                   }.await()
               }
        }
    }
    Thread.sleep(30_000L)
}

suspend fun OkHttpClient.apiCall(url : String) : Response {
    return suspendCoroutine { continuation ->
        Request.Builder()
            .url(url)
            .build()
            .let(::newCall)
            .enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    continuation.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    continuation.resume(response)
                }

            })
    }
}

suspend fun <T> makeRequest(url: String, client: OkHttpClient, typeToken: TypeToken<T>) : T =
    withContext(Dispatchers.IO) {
        client.apiCall(url)
            .let { response ->
                if(!response.isSuccessful) {
                    response.close()
                    throw RuntimeException(response.message)
                }

                val body = response.body ?: throw RuntimeException("response body is null")
                gson.fromJson(body.string(), typeToken.type)
            }
    }

suspend fun getPosts(client: OkHttpClient) : List<Post> =
    makeRequest("$BASE_URL/api/posts", client, object : TypeToken<List<Post>>() {})

suspend fun getComments(client: OkHttpClient, id :Long) : List<Comment> =
    makeRequest("$BASE_URL/api/posts/$id/comments",client, object : TypeToken<List<Comment>>() {})

suspend fun getAuthor(client: OkHttpClient, id :Long) : Author =
    makeRequest("$BASE_URL/api/authors/$id",client, object : TypeToken<Author>() {})