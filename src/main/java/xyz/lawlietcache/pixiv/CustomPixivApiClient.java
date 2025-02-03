package xyz.lawlietcache.pixiv;

import com.github.hanshsieh.pixivj.exception.PixivException;
import com.github.hanshsieh.pixivj.model.SearchedIllusts;
import com.github.hanshsieh.pixivj.model.SearchedIllustsFilter;
import com.github.hanshsieh.pixivj.token.TokenProvider;
import com.github.hanshsieh.pixivj.util.QueryParamConverter;
import xyz.lawlietcache.core.WebCache;
import okhttp3.HttpUrl;
import org.checkerframework.checker.nullness.qual.NonNull;

import java.io.IOException;

public class CustomPixivApiClient {

    private static final String BASE_URL = "https://app-api.pixiv.net";

    private final HttpUrl baseUrl;
    private final QueryParamConverter queryParamConverter;
    private final CustomApiRequestSender requestSender;

    public CustomPixivApiClient(TokenProvider tokenProvider, WebCache webCache) {
        this.baseUrl = HttpUrl.parse(BASE_URL);
        this.queryParamConverter = new QueryParamConverter();
        this.requestSender = new CustomApiRequestSender(webCache, tokenProvider);
    }

    /**
     * Searches illustrations.
     *
     * @param filter The filter to use.
     * @return Search Illustration Results.
     * @throws PixivException PixivException Error
     * @throws IOException    IOException Error
     */
    @NonNull
    public SearchedIllusts searchIllusts(@NonNull SearchedIllustsFilter filter)
            throws PixivException, IOException {
        return sendGetRequest("v1/search/illust", filter, SearchedIllusts.class);
    }

    /**
     * Sends a HTTP GET request.
     *
     * @param path     The relative URL path.
     * @param filter   The filter to used to generate the query parameters.
     * @param respType Type of the response.
     * @param <T>      Type of the serialized response.
     * @param <F>      Type of the filter.
     * @return Serialized response.
     * @throws PixivException The server returns an error.
     * @throws IOException    IO error.
     */
    private <T, F> T sendGetRequest(@NonNull String path, @NonNull F filter, Class<T> respType) throws PixivException, IOException {
        HttpUrl.Builder urlBuilder = baseUrl.newBuilder()
                .addEncodedPathSegments(path);
        queryParamConverter.toQueryParams(filter, urlBuilder);
        HttpUrl url = urlBuilder
                .build();
        return requestSender.send(url.toString(), respType);
    }

}
