// REMOVED (2026-10-29): this wrapper unsafely retried DataSource.open/read
// with Thread.sleep() inside the media path. Retrying read() after the
// delegate's HTTP response was already consumed cannot resume from the correct
// position. Retry behaviour is now provided by
// BoundedLoadErrorHandlingPolicy, wired through
// DefaultMediaSourceFactory.setLoadErrorHandlingPolicy() in PlayerFactory.
// No other code should call or import this file.
