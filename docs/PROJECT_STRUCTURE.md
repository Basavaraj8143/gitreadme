# Project structure

```
app/src/main/java/com/byteharvest/mdreader/
  App.kt, AppContainer.kt, MainActivity.kt   manual DI, Coil SVG loader, edge-to-edge host
  core/
    RepoInput.kt            owner/repo + URL parser (pure, unit-tested)
    markdown/               MarkdownEngine interface, block/inline model, commonmark-java implementation
    pdf/PdfExporter.kt      local PDF via android.graphics.pdf
    security/TokenStore.kt  Android Keystore AES-GCM encrypted token
  domain/MarkdownTree.kt    directory listing/filtering, LinkResolver (pure, unit-tested)
  data/
    local/                  Room entities + DAO + database, DataStore settings
    remote/GitHubApi.kt     read-only REST client, error mapping, URL builders
    repository/AppRepository.kt  cache policy, sync, auth
  ui/                       Compose screens + ViewModels (home, browser, reader, repositories, bookmarks, settings)
```

## Cache policy
- Tree (Markdown path + blob SHA) per `owner/name@branch`, replaced in one transaction after a successful fetch.
- Document text fetched lazily, reused while its SHA matches the tree.
- Cached text is deleted only when its file vanished from a successfully fetched tree. Failed refreshes change nothing.
- `@Upsert` everywhere (REPLACE would cascade-delete children). No destructive migration fallback.
