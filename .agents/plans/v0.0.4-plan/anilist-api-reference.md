# AniList API Reference for Sync

> Endpoint: `POST https://graphql.anilist.co`  
> Auth: `Authorization: Bearer {access_token}`  
> Content-Type: `application/json`  
> Rate limit: 90 requests per minute (we use 85 with OkHttp interceptor & 429 Retry-After backoff)  
> Main plan: [`plan.md`](plan.md) | Mihon reference: [`mihon-reference.md`](mihon-reference.md) | DB migration: [`db-migration.md`](db-migration.md)

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
query ($userId: Int, $type: MediaType, $chunk: Int, $perPage: Int) {
  MediaListCollection(userId: $userId, type: $type, chunk: $chunk, perPage: $perPage) {
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
          countryOfOrigin
        }
      }
    }
    hasNextChunk
  }
}
```

Variables: `{ "userId": 12345, "type": "MANGA", "chunk": 1, "perPage": 500 }`

- **Rate-limit Loop**: Loop while `hasNextChunk == true`, advancing `$chunk` count. Requests pass through `AnilistRateLimiter` (85 permits/min). On 429, back off for `Retry-After` seconds.
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
