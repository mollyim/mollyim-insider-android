package im.molly.unifiedpush.model

enum class FetchStrategy {
  WEBSOCKET,
  REST,
}

fun FetchStrategy.toInt(): Int = when(this) {
  FetchStrategy.WEBSOCKET -> 0
  FetchStrategy.REST -> 1
}

fun Int.toFetchStrategy(): FetchStrategy = if (this == 1) {
  FetchStrategy.REST
} else {
  FetchStrategy.WEBSOCKET
}