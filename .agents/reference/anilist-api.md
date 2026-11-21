# AniList API Reference for Sync

> Endpoint: `POST https://graphql.anilist.co`  
> Auth: `Authorization: Bearer {access_token}`  
> Content-Type: `application/json`  
> Related: [`mihon-sync.md`](mihon-sync.md)

---

## 1. Authentication & Security

### OAuth URL (Implicit Grant with CSRF State)

```
https://anilist.co/api/v2/oauth/authorize?client_id={CLIENT_ID}&response_type=token&redirect_uri=lazydex://anilist-auth&state={STATE}
```

- `response_type=token` (Implicit Grant)
- `state`: Cryptographically generated 256-bit random UUID string saved in `AnilistTokenStore` before opening Custom Tab, validated upon redirect to `TrackLoginActivity`.
- Redirect URI: `lazydex://anilist-auth`
- Response comes as URI fragment: `lazydex://anilist-auth#access_token=TOKEN&token_type=Bearer&expires_in=31536000&state=STATE`

---

## 2. Paginated MediaListCollection Query

```graphql
query ($userId: Int, $type: MediaType, $chunk: Int, $perChunk: Int) {
  MediaListCollection(userId: $userId, type: $type, chunk: $chunk, perChunk: $perChunk) {
    lists {
      name
      isCustomList
      status
      entries {
        id
        mediaId
        status
        scoreRaw: score(format: POINT_100)
        progress
        progressVolumes
        repeat
        private
        notes
        startedAt { year month day }
        completedAt { year month day }
        updatedAt
        createdAt
        media {
          id
          title { romaji english native userPreferred }
          coverImage { large medium }
          format
          status
          chapters
          volumes
          description
          averageScore
          genres
          tags {
            name
            isMediaSpoiler
          }
          countryOfOrigin
        }
      }
    }
    hasNextChunk
  }
}
```

Variables: `{ "userId": 12345, "type": "MANGA", "chunk": 1, "perChunk": 150 }`

- **Chunk Sizing**: Uses `perChunk = 150` to keep individual batch latency under ~9s and JSON payload ~300 KB, preventing timeout truncations.
- **Client Timeouts**: Handled by dedicated `OkHttpClient` (`connectTimeout: 30s`, `readTimeout: 60s`, `writeTimeout: 30s`).
- **Rate-limit Loop**: Loop while `hasNextChunk == true`, advancing `$chunk` count. Requests pass through `TokenBucketLimiter` (`BACKGROUND` priority, 60 permits/min). On 429, back off for `Retry-After` seconds.
- **Null-Guard**: Filter out entries where `entry.media == null`.
- **Deduplication**: Deduplicate entries by `distinctBy { it.id }` (MediaListEntry.id).

---

## 3. Mutations

### Save Entry (`SaveMediaListEntry`)

```graphql
mutation ($id: Int, $mediaId: Int, $status: MediaListStatus, $progress: Int, $progressVolumes: Int, $scoreRaw: Int, $private: Boolean, $startedAt: FuzzyDateInput, $completedAt: FuzzyDateInput) {
  SaveMediaListEntry(
    id: $id
    mediaId: $mediaId
    status: $status
    progress: $progress
    progressVolumes: $progressVolumes
    scoreRaw: $scoreRaw
    private: $private
    startedAt: $startedAt
    completedAt: $completedAt
  ) {
    id
    mediaId
    status
    score
    progress
    progressVolumes
    updatedAt
  }
}
```

- Pass `scoreRaw` (0–100 integer) for standardized rating exchange. AniList automatically formats `scoreRaw` on the server according to the user's profile `scoreFormat`.
