package xyz.lawlietcache.core;

import org.json.JSONObject;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import xyz.lawlietcache.booru.BooruChoice;
import xyz.lawlietcache.booru.BooruDownloader;
import xyz.lawlietcache.booru.BooruImage;
import xyz.lawlietcache.booru.BooruRequest;
import xyz.lawlietcache.booru.exception.BooruException;
import xyz.lawlietcache.pixiv.PixivChoice;
import xyz.lawlietcache.pixiv.PixivDownloader;
import xyz.lawlietcache.pixiv.PixivImage;
import xyz.lawlietcache.pixiv.PixivRequest;
import xyz.lawlietcache.pixiv.exception.PixivException;
import xyz.lawlietcache.reddit.RedditDownloader;
import xyz.lawlietcache.reddit.RedditPost;
import xyz.lawlietcache.reddit.exception.RedditException;
import xyz.lawlietcache.twitch.TwitchDownloader;
import xyz.lawlietcache.twitch.TwitchStream;

import java.io.IOException;
import java.time.Duration;
import java.util.Collections;
import java.util.List;

@RestController
@RequestMapping("/api/")
public class ApiController {

    private final BooruDownloader booruDownloader;
    private final RedditDownloader redditDownloader;
    private final PixivDownloader pixivDownloader;
    private final TwitchDownloader twitchDownloader;
    private final WebCache webCache;
    private final RandomPicker randomPicker;

    public ApiController(BooruDownloader booruDownloader,
                         RedditDownloader redditDownloader,
                         PixivDownloader pixivDownloader,
                         TwitchDownloader twitchDownloader,
                         WebCache webCache,
                         RandomPicker randomPicker) {
        this.booruDownloader = booruDownloader;
        this.redditDownloader = redditDownloader;
        this.pixivDownloader = pixivDownloader;
        this.twitchDownloader = twitchDownloader;
        this.webCache = webCache;
        this.randomPicker = randomPicker;
    }

    @GetMapping(value = "/ping", produces = MediaType.TEXT_PLAIN_VALUE)
    public String ping() {
        return "Pong!";
    }

    @PostMapping(value = "/booru_v2", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public List<BooruImage> booruV2(@RequestBody BooruRequest booruRequest) throws BooruException {
        try (AsyncTimer timer = new AsyncTimer(Duration.ofMillis(14_500))) {
            return booruDownloader.getImages(
                    booruRequest.getGuildId(),
                    booruRequest.getPremium(),
                    booruRequest.getDomain(),
                    booruRequest.getSearchTerm(),
                    booruRequest.getAnimatedOnly(),
                    booruRequest.getMustBeExplicit(),
                    booruRequest.getCanBeVideo(),
                    booruRequest.getFilters(),
                    booruRequest.getStrictFilters() != null ? booruRequest.getStrictFilters() : Collections.emptyList(),
                    booruRequest.getSkippedResults(),
                    booruRequest.getTest(),
                    booruRequest.getNumber()
            );
        }
    }

    @GetMapping(value = "/booru_autocomplete/{domain}/{search}", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<BooruChoice> booruAutoComplete(@PathVariable("domain") String domain,
                                               @PathVariable("search") String search) throws BooruException {
        try (AsyncTimer timer = new AsyncTimer(Duration.ofMillis(14_500))) {
            return booruDownloader.getTags(domain, search);
        }
    }

    @GetMapping(value = "/reddit/single/{guild_id}/{nsfw_allowed}/{subreddit}/{order_by}", produces = MediaType.APPLICATION_JSON_VALUE)
    public RedditPost redditSingle(@PathVariable("guild_id") long guildId,
                                   @PathVariable("nsfw_allowed") boolean nsfwAllowed,
                                   @PathVariable("subreddit") String subreddit,
                                   @PathVariable("order_by") String orderBy) throws RedditException {
        try (AsyncTimer timer = new AsyncTimer(Duration.ofMillis(14_500))) {
            return redditDownloader.retrievePost(guildId, subreddit, orderBy, nsfwAllowed);
        }
    }

    @GetMapping(value = "/reddit/bulk/{subreddit}/{order_by}", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<RedditPost> redditBulk(@PathVariable("subreddit") String subreddit,
                                       @PathVariable("order_by") String orderBy) throws RedditException {
        try (AsyncTimer timer = new AsyncTimer(Duration.ofMillis(14_500))) {
            return redditDownloader.retrievePostsBulk(subreddit, orderBy);
        }
    }

    @PostMapping(value = "/pixiv_single", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public PixivImage pixivSingle(@RequestBody PixivRequest pixivRequest) throws PixivException {
        try (AsyncTimer timer = new AsyncTimer(Duration.ofMillis(14_500))) {
            return pixivDownloader.retrieveImage(
                    pixivRequest.getGuildId(),
                    pixivRequest.getWord(),
                    pixivRequest.isNsfwAllowed(),
                    pixivRequest.getFilters(),
                    pixivRequest.getStrictFilters()
            );
        }
    }

    @PostMapping(value = "/pixiv_bulk", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public List<PixivImage> pixivBulk(@RequestBody PixivRequest pixivRequest) throws PixivException {
        try (AsyncTimer timer = new AsyncTimer(Duration.ofMillis(14_500))) {
            return pixivDownloader.retrieveImagesBulk(
                    pixivRequest.getWord(),
                    pixivRequest.getFilters(),
                    pixivRequest.getStrictFilters()
            );
        }
    }

    @GetMapping(value = "/pixiv_autocomplete/{search}", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<PixivChoice> pixivAutoComplete(@PathVariable("search") String search) {
        try (AsyncTimer timer = new AsyncTimer(Duration.ofMillis(14_500))) {
            return pixivDownloader.getTags(search);
        }
    }

    @GetMapping(value = "/twitch/{name}", produces = MediaType.APPLICATION_JSON_VALUE)
    public TwitchStream twitch(@PathVariable("name") String name) throws IOException {
        try (AsyncTimer timer = new AsyncTimer(Duration.ofMillis(14_500))) {
            return twitchDownloader.retrieveStream(name);
        }
    }

    @PostMapping(value = "/webcache", consumes = MediaType.TEXT_PLAIN_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public HttpResponse webcache(@RequestBody String url) {
        try (AsyncTimer timer = new AsyncTimer(Duration.ofMillis(14_500))) {
            return webCache.get(url, 5);
        }
    }

    @GetMapping("/cached_proxy")
    public ResponseEntity<String> cachedProxyGet(@RequestHeader("X-Proxy-Url") String url,
                                                 @RequestHeader("X-Proxy-Minutes") int minutes) {
        return requestCachedProxy("GET", url, minutes, null, null);
    }

    @PostMapping("/cached_proxy")
    public ResponseEntity<String> cachedProxyPost(@RequestHeader("X-Proxy-Url") String url,
                                                  @RequestHeader("X-Proxy-Minutes") int minutes,
                                                  @RequestHeader("Content-Type") String contentType,
                                                  @RequestBody String body) {
        return requestCachedProxy("POST", url, minutes, contentType, body);
    }

    @PostMapping(value = "/random", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.TEXT_PLAIN_VALUE)
    public String randomPicker(@RequestBody RandomPickerRequest randomPickerRequest) {
        try (AsyncTimer timer = new AsyncTimer(Duration.ofMillis(14_500))) {
            int number = randomPicker.pick(
                    randomPickerRequest.getTag(),
                    randomPickerRequest.getGuildId(),
                    randomPickerRequest.getSize()
            );
            return String.valueOf(number);
        }
    }

    private ResponseEntity<String> requestCachedProxy(String method, String url, int minutes, String contentType, String body) {
        try (AsyncTimer timer = new AsyncTimer(Duration.ofMillis(14_500))) {
            HttpResponse httpResponse = webCache.request(method, url, body, contentType, minutes);
            if (httpResponse.getCode() / 100 == 2) {
                if (url.startsWith("https://realbooru.com/") && url.contains("&json=1")) {
                    JSONObject jsonObject = new JSONObject(httpResponse.getBody());
                    httpResponse.setBody(jsonObject.getJSONArray("post").toString());
                }
                return ResponseEntity.ok(httpResponse.getBody());
            } else {
                return ResponseEntity.status(httpResponse.getCode()).build();
            }
        }
    }

}
