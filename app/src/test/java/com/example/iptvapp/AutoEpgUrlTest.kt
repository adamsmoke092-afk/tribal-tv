// REMOVED (2026-10-31): the iptv-org country auto-pair
// (autoEpgUrlForPlaylist, ".../iptv/countries/za.m3u" -> ".../epg/guides/za.xml.gz")
// was withdrawn by user decision — its generated guide URLs proved unreliable,
// and inferring a guide from a playlist URL's shape is the kind of silent
// guessing the per-playlist EPG work removes.
//
// A playlist's guide now comes from its own `url-tvg` / `x-tvg-url` header
// (see M3uHeaderTest) or from that playlist's manual override. Precedence and
// origin tests live in EpgPrecedenceTest. This file is kept only so the
// removal is visible in history without a deletion; it contains no tests and
// runs nothing.
