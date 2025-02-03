package xyz.lawlietcache.pixiv;

import com.github.hanshsieh.pixivj.exception.APIException;
import com.github.hanshsieh.pixivj.exception.AuthException;
import com.github.hanshsieh.pixivj.model.APIError;
import com.github.hanshsieh.pixivj.model.SearchedIllusts;
import com.github.hanshsieh.pixivj.token.TokenProvider;
import com.github.hanshsieh.pixivj.util.JsonUtils;
import com.google.gson.GsonBuilder;
import org.checkerframework.checker.nullness.qual.NonNull;
import xyz.lawlietcache.core.HttpHeader;
import xyz.lawlietcache.core.HttpResponse;
import xyz.lawlietcache.core.WebCache;

import java.io.IOException;

public class CustomApiRequestSender {

    private final WebCache webCache;
    private final TokenProvider tokenProvider;

    public CustomApiRequestSender(WebCache webCache, TokenProvider tokenProvider) {
        this.webCache = webCache;
        this.tokenProvider = tokenProvider;
    }

    public <T> T send(@NonNull String url, @NonNull Class<T> respType) throws APIException, IOException, AuthException {
        HttpHeader authHeader = new HttpHeader("Authorization", "Bearer " + tokenProvider.getAccessToken());
        HttpHeader languageHeader = new HttpHeader("Accept-Language", "English");
        HttpResponse httpResponse = webCache.get(url, 14, authHeader, languageHeader);

        if (httpResponse.getCode() / 100 == 2) {
            if (respType == Void.class) {
                return null;
            }
            return new GsonBuilder()
                    .registerTypeAdapter(SearchedIllusts.class, new SearchedIllustsAdapter())
                    .create()
                    .fromJson(httpResponse.getBody(), respType);
        } else {
            throw createExceptionFromRespBody(httpResponse.getBody());
        }
    }

    private APIException createExceptionFromRespBody(@NonNull String respStr) {
        APIError error = JsonUtils.GSON.fromJson(respStr, APIError.class);
        return new APIException(error);
    }

}
