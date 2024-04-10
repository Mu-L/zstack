package org.zstack.network.l2;

import org.springframework.beans.factory.annotation.Autowired;
import org.zstack.core.cloudbus.CloudBus;
import org.zstack.core.db.DatabaseFacade;
import org.zstack.core.db.Q;
import org.zstack.core.db.SQL;
import org.zstack.core.db.SimpleQuery;
import org.zstack.core.db.SimpleQuery.Op;
import org.zstack.core.errorcode.ErrorFacade;
import org.zstack.header.apimediator.ApiMessageInterceptionException;
import org.zstack.header.apimediator.ApiMessageInterceptor;
import org.zstack.header.apimediator.StopRoutingException;
import org.zstack.header.host.HostStatus;
import org.zstack.header.host.HostVO;
import org.zstack.header.host.HostVO_;
import org.zstack.header.message.APIMessage;
import org.zstack.header.network.l2.*;
import org.zstack.utils.network.NetworkUtils;

import java.util.List;
import java.util.stream.Collectors;

import static org.zstack.core.Platform.argerr;
import static org.zstack.core.Platform.operr;

/**
 * Created with IntelliJ IDEA.
 * User: frank
 * Time: 4:52 PM
 * To change this template use File | Settings | File Templates.
 */
public class L2NetworkApiInterceptor implements ApiMessageInterceptor {
    @Autowired
    private CloudBus bus;
    @Autowired
    private DatabaseFacade dbf;
    @Autowired
    private ErrorFacade errf;

    private void setServiceId(APIMessage msg) {
        if (msg instanceof L2NetworkMessage) {
            L2NetworkMessage l2msg = (L2NetworkMessage)msg;
            bus.makeTargetServiceIdByResourceUuid(msg, L2NetworkConstant.SERVICE_ID, l2msg.getL2NetworkUuid());
        }
    }

    @Override
    public APIMessage intercept(APIMessage msg) throws ApiMessageInterceptionException {
        if (msg instanceof APICreateL2NetworkMsg) {
            validate((APICreateL2NetworkMsg)msg);
        } else if (msg instanceof APIDeleteL2NetworkMsg) {
            validate((APIDeleteL2NetworkMsg)msg);
        } else if (msg instanceof APIDetachL2NetworkFromClusterMsg) {
            validate((APIDetachL2NetworkFromClusterMsg)msg);
        } else if (msg instanceof APIAttachL2NetworkToClusterMsg) {
            validate((APIAttachL2NetworkToClusterMsg) msg);
        } else if (msg instanceof APIChangeL2NetworkVlanIdMsg) {
            validate((APIChangeL2NetworkVlanIdMsg) msg);
        }

        setServiceId(msg);
        return msg;
    }

    private void validate(final APIAttachL2NetworkToClusterMsg msg) {
        SimpleQuery<L2NetworkClusterRefVO> q = dbf.createQuery(L2NetworkClusterRefVO.class);
        q.add(L2NetworkClusterRefVO_.clusterUuid, Op.EQ, msg.getClusterUuid());
        q.add(L2NetworkClusterRefVO_.l2NetworkUuid, Op.EQ, msg.getL2NetworkUuid());
        if (q.isExists()) {
            throw new ApiMessageInterceptionException(operr("l2Network[uuid:%s] has attached to cluster[uuid:%s], can't attach again", msg.getL2NetworkUuid(), msg.getClusterUuid()));
        }

        /* current ovs only support vlan, vxlan*/
        L2NetworkVO l2 = dbf.findByUuid(msg.getL2NetworkUuid(), L2NetworkVO.class);
        /* find l2 network with same physical interface, but different vswitch Type */
        List<String> otherL2s = Q.New(L2NetworkVO.class).select(L2NetworkVO_.uuid)
                .eq(L2NetworkVO_.physicalInterface, l2.getPhysicalInterface())
                .notEq(L2NetworkVO_.vSwitchType, l2.getvSwitchType()).listValues();
        if (!otherL2s.isEmpty()) {
            if (Q.New(L2NetworkClusterRefVO.class).eq(L2NetworkClusterRefVO_.clusterUuid, msg.getClusterUuid())
                    .in(L2NetworkClusterRefVO_.l2NetworkUuid, otherL2s).isExists()) {
                throw new ApiMessageInterceptionException(argerr("could not attach l2 network, because there "+
                                "is another network [uuid:%s] on physical interface [%s] with different vswitch type",
                        otherL2s.get(0), l2.getPhysicalInterface()));
            }
        }
    }

    private void validate(APIDetachL2NetworkFromClusterMsg msg) {
        SimpleQuery<L2NetworkClusterRefVO> q = dbf.createQuery(L2NetworkClusterRefVO.class);
        q.add(L2NetworkClusterRefVO_.clusterUuid, Op.EQ, msg.getClusterUuid());
        q.add(L2NetworkClusterRefVO_.l2NetworkUuid, Op.EQ, msg.getL2NetworkUuid());
        if (!q.isExists()) {
            throw new ApiMessageInterceptionException(operr("l2Network[uuid:%s] has not attached to cluster[uuid:%s]", msg.getL2NetworkUuid(), msg.getClusterUuid()));
        }
    }

    private void validate(APIDeleteL2NetworkMsg msg) {
        if (!dbf.isExist(msg.getUuid(), L2NetworkVO.class)) {
            APIDeleteL2NetworkEvent evt = new APIDeleteL2NetworkEvent(msg.getId());
            bus.publish(evt);
            throw new StopRoutingException();
        }
    }

    private void validate(APICreateL2NetworkMsg msg) {
        if (!L2NetworkType.hasType(msg.getType())) {
            throw new ApiMessageInterceptionException(argerr("unsupported l2Network type[%s]", msg.getType()));
        }

        if (!VSwitchType.hasType(msg.getvSwitchType())) {
            throw new ApiMessageInterceptionException(argerr("unsupported vSwitch type[%s]", msg.getvSwitchType()));
        }
    }

    private void validate(APIChangeL2NetworkVlanIdMsg msg) {
        L2NetworkVO l2 = dbf.findByUuid(msg.getL2NetworkUuid(), L2NetworkVO.class);
        l2.getAttachedClusterRefs().forEach(ref -> {
            if (Q.New(HostVO.class).eq(HostVO_.clusterUuid, ref.getClusterUuid())
                    .notEq(HostVO_.status, HostStatus.Connected).isExists()) {
                throw new ApiMessageInterceptionException(operr("cannot change vlan for l2Network[uuid:%s]" +
                        " because there are hosts status in Connecting or Disconnected", l2.getUuid()));
            }
        });
        if (msg.getType().equals(L2NetworkConstant.L2_VLAN_NETWORK_TYPE)) {
            if (msg.getVlan() == null) {
                throw new ApiMessageInterceptionException(argerr("vlan is required for " +
                        "ChangeL2NetworkVlanId with type[%s]", msg.getType()));
            }
            if (!NetworkUtils.isValidVlan(msg.getVlan())) {
                throw new ApiMessageInterceptionException(argerr("vlan[%s] is invalid", msg.getVlan()));
            }
            List<String> attachedClusters = l2.getAttachedClusterRefs().stream()
                    .map(L2NetworkClusterRefVO::getClusterUuid).collect(Collectors.toList());
            List<L2NetworkVO> l2s = SQL.New("select l2" +
                            " from L2NetworkVO l2, L2NetworkClusterRefVO ref" +
                            " where l2.virtualNetworkId = :virtualNetworkId" +
                            " and l2.physicalInterface = :physicalInterface" +
                            " and ref.clusterUuid in (:clusterUuids)" +
                            " and l2.type = 'L2VlanNetwork'")
                    .param("virtualNetworkId", msg.getVlan())
                    .param("physicalInterface", l2.getPhysicalInterface())
                    .param("clusterUuids", attachedClusters).list();
            l2s = l2s.stream().filter(l -> !l.getUuid().equals(msg.getUuid())).collect(Collectors.toList());
            if (!l2s.isEmpty()) {
                throw new ApiMessageInterceptionException(argerr("There has been a l2Network attached to cluster with virtual network id[%s] and physical interface[%s]. Failed to change L2 network[uuid:%s]",
                        msg.getVlan(), l2.getPhysicalInterface(), l2.getUuid()));
            }
        } else if (msg.getType().equals(L2NetworkConstant.L2_NO_VLAN_NETWORK_TYPE)) {
            if (msg.getVlan() != null) {
                throw new ApiMessageInterceptionException(argerr("vlan is not allowed for " +
                        "ChangeL2NetworkVlanId with type[%s]", msg.getType()));
            }
            List<String> attachedClusters = l2.getAttachedClusterRefs().stream()
                    .map(L2NetworkClusterRefVO::getClusterUuid).collect(Collectors.toList());
            List<L2NetworkVO> l2s = SQL.New("select l2" +
                            " from L2NetworkVO l2, L2NetworkClusterRefVO ref" +
                            " where l2.uuid = ref.l2NetworkUuid" +
                            " and l2.physicalInterface = :physicalInterface" +
                            " and ref.clusterUuid in (:clusterUuids)" +
                            " and type = 'L2NoVlanNetwork'")
                    .param("physicalInterface", l2.getPhysicalInterface())
                    .param("clusterUuids", attachedClusters).list();
            l2s = l2s.stream().filter(l -> !l.getUuid().equals(msg.getUuid())).collect(Collectors.toList());
            if (!l2s.isEmpty()) {
                throw new ApiMessageInterceptionException(argerr("There has been a l2Network attached to cluster that has physical interface[%s]. Failed to change l2Network[uuid:%s]",
                        l2.getPhysicalInterface(), l2.getUuid()));
            }
        }
    }
}
