package org.zstack.expon.sdk.volume;

import org.springframework.http.HttpMethod;
import org.zstack.expon.sdk.ExponRequest;
import org.zstack.expon.sdk.ExponRestRequest;
import org.zstack.externalStorage.sdk.Param;

import java.util.HashMap;
import java.util.Map;

@ExponRestRequest(
        path = "/block/volumes/{volId}/lun_detail",
        method = HttpMethod.GET,
        responseClass = GetVolumeLunDetailResponse.class
)
public class GetVolumeLunDetailRequest extends ExponRequest {
    private static final HashMap<String, Parameter> parameterMap = new HashMap<>();

    @Param
    private String volId;

    public String getVolId() {
        return volId;
    }

    public void setVolId(String volId) {
        this.volId = volId;
    }

    @Override
    public Map<String, Parameter> getParameterMap() {
        return parameterMap;
    }
}
