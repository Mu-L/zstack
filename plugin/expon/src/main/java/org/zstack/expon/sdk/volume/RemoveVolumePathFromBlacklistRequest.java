package org.zstack.expon.sdk.volume;

import org.springframework.http.HttpMethod;
import org.zstack.expon.sdk.ExponRequest;
import org.zstack.expon.sdk.ExponRestRequest;
import org.zstack.externalStorage.sdk.Param;

import java.util.HashMap;
import java.util.Map;

@ExponRestRequest(
        path = "/failure_domain/black_list",
        method = HttpMethod.DELETE,
        responseClass = RemoveVolumePathFromBlacklistResponse.class,
        hasBody = true,
        sync = false
)
public class RemoveVolumePathFromBlacklistRequest extends ExponRequest {
    private static final HashMap<String, Parameter> parameterMap = new HashMap<>();

    @Param
    private String path;

    @Override
    public Map<String, Parameter> getParameterMap() {
        return parameterMap;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getPath() {
        return path;
    }
}
