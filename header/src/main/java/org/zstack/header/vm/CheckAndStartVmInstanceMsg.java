package org.zstack.header.vm;

import org.zstack.header.message.NeedReplyMessage;

@SkipVmTracer(replyClass = CheckAndStartVmInstanceReply.class)
public class CheckAndStartVmInstanceMsg extends NeedReplyMessage implements VmInstanceMessage {
    private String vmInstanceUuid;

    @Override
    public String getVmInstanceUuid() {
        return vmInstanceUuid;
    }

    public void setVmInstanceUuid(String vmInstanceUuid) {
        this.vmInstanceUuid = vmInstanceUuid;
    }
}
