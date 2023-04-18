package im.molly.unifiedpush.model

enum class FetchStrategy {
  POLLING,
  REQUEST,
}

fun FetchStrategy.toInt(): Int = when (this) {
  FetchStrategy.POLLING -> 0
  FetchStrategy.REQUEST -> 1
}

fun Int.toFetchStrategy(): FetchStrategy = if (this == 1) {
  FetchStrategy.REQUEST
} else {
  FetchStrategy.POLLING
}
