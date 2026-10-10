# Release notes — next release (DRAFT)

Not released yet. Merge these lines into the notes of the release that ships
`feature/tv-cast-and-sync` (TV cast, send to TV, device limit, sync). Keep each language block
under 500 characters once the feature lines are added.

Background: before this release, two playlists with the same channels could take each other's
channels when the second was imported (channel ids were shared across playlists; fixed by namespacing,
see `docs/handoff-tv-cast-and-sync.md`, "QC round 3/4 fixes"). Lost rows cannot be restored from the
database: a URL playlist gets them back on its next refresh (manual or automatic); a file playlist has
no source to refresh from and must be imported again.

<en-US>
• Fixed: playlists with the same channels no longer take each other's channels or guide. If a playlist showed fewer channels than it should, refresh it; a playlist added from a file needs to be added again.
</en-US>

<vi-VN>
• Đã sửa: các danh sách phát có kênh giống nhau không còn lấy mất kênh hoặc lịch phát sóng của nhau. Nếu một danh sách phát hiển thị thiếu kênh, hãy làm mới danh sách đó; danh sách phát thêm từ tệp cần được thêm lại.
</vi-VN>
