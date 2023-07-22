package pixiv;

import com.github.hanshsieh.pixivj.exception.APIException;
import com.github.hanshsieh.pixivj.exception.AuthException;
import com.github.hanshsieh.pixivj.model.APIError;
import com.github.hanshsieh.pixivj.token.TokenProvider;
import com.github.hanshsieh.pixivj.util.JsonUtils;
import core.HttpHeader;
import core.HttpResponse;
import core.WebCache;
import org.checkerframework.checker.nullness.qual.NonNull;

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
        HttpResponse httpResponse = webCache.get(url, 30, authHeader);

        if (httpResponse.getCode() / 100 == 2) {
            if (respType == Void.class) {
                return null;
            }
            return JsonUtils.GSON.fromJson(httpResponse.getBody(), respType);
        } else {
            throw createExceptionFromRespBody(httpResponse.getBody());
        }
    }

    private APIException createExceptionFromRespBody(@NonNull String respStr) {
        APIError error = JsonUtils.GSON.fromJson(respStr, APIError.class);
        return new APIException(error);
    }

}
